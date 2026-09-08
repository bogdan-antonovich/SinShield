package com.example.sinshield

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import androidx.core.graphics.createBitmap

/**
 * Master switch for the on-device model/OpenCV debug dumps ([ModelAnalysisDebugWriter] and
 * [OpenCvDetectionDebugWriter]). These write a JPEG per classifier crop, verifier crop, and OpenCV
 * overlay into the app's Pictures debug folders — synchronous encodes on the inference thread that
 * add-on the order of ~1.5s per frame. Kept off by default so normal runs are fast; flip [ENABLED]
 * to true (on a debuggable build) when you need to inspect exactly what the models saw.
 */
internal object ModelDebugDumps {
    const val ENABLED = false
}

/** Creates bounded debug captures of actual model inputs and their final localized-region map. */
internal class ModelAnalysisDebugWriter(context: Context) {
    private val outputDirectory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        DIRECTORY_NAME
    )
    private var lastCaptureElapsedMs = Long.MIN_VALUE

    @Synchronized
    fun beginFrame(frameHash: Long): ModelAnalysisDebugSession? {
        val now = SystemClock.elapsedRealtime()
        if (lastCaptureElapsedMs != Long.MIN_VALUE &&
            now - lastCaptureElapsedMs < CAPTURE_INTERVAL_MS
        ) {
            return null
        }
        lastCaptureElapsedMs = now

        return runCatching {
            check(outputDirectory.exists() || outputDirectory.mkdirs()) {
                "Could not create ${outputDirectory.absolutePath}"
            }
            val timestamp = FILE_TIMESTAMP.format(Date())
            val hash = frameHash.toULong().toString(16).takeLast(8)
            val captureDirectory = File(outputDirectory, "model_${timestamp}_$hash")
            check(captureDirectory.mkdir()) {
                "Could not create ${captureDirectory.absolutePath}"
            }
            pruneOldCaptures()
            Log.d(TAG, "Model analysis debug flow: ${captureDirectory.absolutePath}")
            ModelAnalysisDebugSession(captureDirectory)
        }.onFailure { error ->
            Log.w(TAG, "Could not create model analysis debug capture", error)
        }.getOrNull()
    }

    private fun pruneOldCaptures() {
        outputDirectory.listFiles { file ->
            file.isDirectory && file.name.startsWith(FILE_PREFIX)
        }?.sortedByDescending(File::lastModified)
            .orEmpty()
            .drop(MAX_CAPTURE_RUNS)
            .forEach { oldCapture ->
                if (!oldCapture.deleteRecursively()) {
                    Log.w(TAG, "Could not delete ${oldCapture.absolutePath}")
                }
            }
    }

    private companion object {
        private const val TAG = "SinShield"
        private const val DIRECTORY_NAME = "model-analysis-debug"
        private const val FILE_PREFIX = "model_"
        private const val CAPTURE_INTERVAL_MS = 1_000L
        private const val MAX_CAPTURE_RUNS = 8
        private val FILE_TIMESTAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}

/** One frame's model inputs. Methods are synchronized because localized models finish in parallel. */
internal class ModelAnalysisDebugSession(private val captureDirectory: File) {
    @Synchronized
    fun saveFinalRegionMap(source: Bitmap, regions: List<DetectionRegion>) {
        val mapped = source.copy(Bitmap.Config.ARGB_8888, true)
            ?: return
        try {
            val canvas = Canvas(mapped)
            val scale = max(1f, source.width / 1080f)
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 255, 80)
                style = Paint.Style.STROKE
                strokeWidth = 5f * scale
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                textSize = 26f * scale
                typeface = Typeface.DEFAULT_BOLD
            }
            val labelBackground = Paint().apply {
                color = Color.argb(210, 0, 0, 0)
                style = Paint.Style.FILL
            }
            regions.forEachIndexed { index, region ->
                val bounds = RectF(
                    region.left * source.width,
                    region.top * source.height,
                    region.right * source.width,
                    region.bottom * source.height
                )
                canvas.drawRect(bounds, boxPaint)
                val label = "#${index + 1} ${bounds.left.toInt()},${bounds.top.toInt()} " +
                    "${bounds.width().toInt()}x${bounds.height().toInt()} ${region.source}"
                val labelTop = (bounds.top - labelPaint.textSize - 8f).coerceAtLeast(0f)
                canvas.drawRect(
                    bounds.left,
                    labelTop,
                    bounds.left + labelPaint.measureText(label) + 12f,
                    labelTop + labelPaint.textSize + 8f,
                    labelBackground
                )
                canvas.drawText(label, bounds.left + 6f, labelTop + labelPaint.textSize, labelPaint)
            }
            saveAnnotated(
                fileStem = "00-final-model-regions-NOT-MODEL-INPUT",
                source = mapped,
                lines = listOf(
                    "FINAL LOCALIZED REGIONS: ${regions.size}",
                    "green rectangle #N = NN-localized.jpg"
                )
            )
        } finally {
            mapped.recycle()
        }
    }

    @Synchronized
    fun saveClassifierInput(
        fileStem: String,
        bitmap: Bitmap,
        scores: FloatArray,
        verdict: ContentVerdict,
        details: String? = null
    ) {
        if (scores.size < CLASS_NAMES.size) return
        val lines = buildList {
            add("$fileStem  |  verdict: $verdict")
            details?.lines()?.forEach(::add)
            add("Drawings ${score(scores[0])}   Hentai ${score(scores[1])}")
            add("Neutral ${score(scores[2])}   Porn ${score(scores[3])}")
            add("Sexy ${score(scores[4])}")
        }
        saveAnnotated(fileStem, bitmap, lines)
    }

    @Synchronized
    fun saveVerifierInput(bitmap: Bitmap, result: VerificationResult, details: String? = null) {
        val lines = buildList {
            add("VERIFIER  |  NSFW ${score(result.nsfwScore)}   SFW ${score(1f - result.nsfwScore)}")
            details?.lines()?.forEach(::add)
            add("actual verifier tensor image: ${bitmap.width}x${bitmap.height}")
        }
        saveAnnotated("99-verifier", bitmap, lines)
    }

    private fun saveAnnotated(fileStem: String, source: Bitmap, lines: List<String>) {
        runCatching {
            val safeStem = fileStem.replace(Regex("[^A-Za-z0-9._-]"), "-")
            // Localized tiles can be narrow, so keep each score readable without covering pixels.
            val textSize = (source.width / 32f).coerceIn(9f, 28f)
            val padding = max(8f, textSize * 0.55f)
            val lineHeight = textSize * 1.3f
            val panelHeight = (padding * 2 + lineHeight * lines.size).toInt()
            val annotated = createBitmap(source.width, source.height + panelHeight)
            try {
                val canvas = Canvas(annotated)
                canvas.drawColor(Color.rgb(14, 17, 21))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    this.textSize = textSize
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, padding, padding + textSize + index * lineHeight, paint)
                }
                canvas.drawBitmap(source, 0f, panelHeight.toFloat(), null)
                FileOutputStream(File(captureDirectory, "$safeStem.jpg")).use { stream ->
                    check(annotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream))
                }
            } finally {
                annotated.recycle()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not save model input $fileStem", error)
        }
    }

    private companion object {
        private const val TAG = "SinShield"
        private const val JPEG_QUALITY = 94
        private val CLASS_NAMES = arrayOf("Drawings", "Hentai", "Neutral", "Porn", "Sexy")

        private fun score(value: Float): String = String.format(Locale.US, "%.3f", value)
    }
}
