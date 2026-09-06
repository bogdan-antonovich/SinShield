package com.example.sinshield

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max

/** Independent Marqo ViT verifier. Output index 0 is NSFW and index 1 is SFW. */
internal class NsfwVerifier(context: Context, modelPath: String) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val inputBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(INPUT_SIZE * INPUT_SIZE * CHANNELS * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    init {
        val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
        OrtSession.SessionOptions().use { options ->
            session = environment.createSession(modelBytes, options)
        }
        inputName = session.inputNames.single()
    }

    @Synchronized
    fun verify(
        bitmap: Bitmap,
        candidate: DetectionBox?,
        debugObserver: ((Bitmap, VerificationResult) -> Unit)? = null
    ): VerificationResult {
        val crop = createCandidateCrop(bitmap, candidate)
        val resized = Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
        try {
            resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            inputBuffer.clear()
            // The model expects planar NCHW layout: all red values, then all green, then all blue
            // (bit shifts 16/8/0 of the ARGB pixels). This differs from the interleaved HWC packing
            // NsfwClassifier uses, which is why the channels are written as three separate passes.
            putChannel(16)
            putChannel(8)
            putChannel(0)
            inputBuffer.rewind()

            OnnxTensor.createTensor(
                environment,
                inputBuffer,
                longArrayOf(1, CHANNELS.toLong(), INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            ).use { input ->
                session.run(mapOf(inputName to input)).use { output ->
                    val logits = extractLogits(output[0].value)
                    require(logits.size >= 2) { "Verifier returned fewer than two logits" }
                    // Softmax over the [NSFW, SFW] logits, subtracting the max first so exp() cannot
                    // overflow. The result is the NSFW probability, i.e. its share of the two.
                    val maximum = max(logits[0], logits[1])
                    val nsfw = exp((logits[0] - maximum).toDouble())
                    val sfw = exp((logits[1] - maximum).toDouble())
                    val result = VerificationResult((nsfw / (nsfw + sfw)).toFloat())
                    // The observer sees the exact 384x384 spatial input before normalization.
                    debugObserver?.invoke(resized, result)
                    return result
                }
            }
        } finally {
            if (resized !== crop) resized.recycle()
            if (crop !== bitmap) crop.recycle()
        }
    }

    // Writes one color plane, normalizing bytes to [-1, 1] as this ViT verifier expects (unlike the
    // MobileNet classifier's [0, 1]).
    private fun putChannel(shift: Int) {
        for (pixel in pixels) {
            val channel = (pixel shr shift) and 0xFF
            inputBuffer.put(channel / 127.5f - 1f)
        }
    }

    // Crops to the candidate box (normalized coordinates) so the verifier judges the flagged region
    // rather than the whole screen. A null candidate means verify the full frame. The box is padded
    // by CROP_MARGIN_RATIO and a minimum viewport share so a tight skin-heavy detection still gives
    // the model enough clothing and scene context to distinguish a person from explicit content.
    private fun createCandidateCrop(bitmap: Bitmap, candidate: DetectionBox?): Bitmap {
        candidate ?: return bitmap
        val expandedWidth = max(
            (candidate.right - candidate.left) * (1f + 2f * CROP_MARGIN_RATIO),
            MIN_CROP_WIDTH
        ).coerceAtMost(1f)
        val expandedHeight = max(
            (candidate.bottom - candidate.top) * (1f + 2f * CROP_MARGIN_RATIO),
            MIN_CROP_HEIGHT
        ).coerceAtMost(1f)
        val centerX = (candidate.left + candidate.right) / 2f
        val centerY = (candidate.top + candidate.bottom) / 2f
        val leftNormalized = (centerX - expandedWidth / 2f)
            .coerceIn(0f, 1f - expandedWidth)
        val topNormalized = (centerY - expandedHeight / 2f)
            .coerceIn(0f, 1f - expandedHeight)
        val left = (leftNormalized * bitmap.width)
            .toInt().coerceIn(0, bitmap.width - 1)
        val top = (topNormalized * bitmap.height)
            .toInt().coerceIn(0, bitmap.height - 1)
        val right = ((leftNormalized + expandedWidth) * bitmap.width)
            .toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ((topNormalized + expandedHeight) * bitmap.height)
            .toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun extractLogits(value: Any): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.firstOrNull() as? FloatArray
            ?: error("Unexpected verifier output array")
        else -> error("Unexpected verifier output type ${value::class.java.name}")
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val INPUT_SIZE = 384
        private const val CHANNELS = 3
        private const val CROP_MARGIN_RATIO = 0.25f
        private const val MIN_CROP_WIDTH = 0.50f
        private const val MIN_CROP_HEIGHT = 0.35f
    }
}

internal data class VerificationResult(val nsfwScore: Float)
