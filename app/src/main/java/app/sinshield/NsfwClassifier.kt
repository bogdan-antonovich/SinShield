package app.sinshield

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NsfwClassifier(
    context: Context,
    filePath: String,
    numThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
) : AutoCloseable {

    private val interpreter: Interpreter
    private val inputSize = 224 // image input size for MobileNetV2
    private val pixels = IntArray(inputSize * inputSize)

    // The interpreter's input tensor is resized to the current batch size and its backing buffer
    // has to match that size exactly, so the single-image and multi-crop buffers are kept apart.
    // The single buffer is allocated once; the multi-crop buffer is reallocated only when the crop
    // count changes between frames, so a steady feed stops reallocating after the first scan.
    private val singleImageBuffer = allocateInputBuffer(1)
    private var batchBuffer: ByteBuffer? = null
    private var batchBufferCount = 0
    private var configuredBatchSize = -1
    private var batchingUnavailable = false

    init {
        // loading models from the assets folder
        val model: MappedByteBuffer = loadModelFile(context, filePath)
        val options = Interpreter.Options().apply {
            setNumThreads(numThreads.coerceIn(1, 4))
        }
        interpreter = Interpreter(model, options)
    }

    /**
     * Returns an array of possibilities within 5 categories:
     * [0] Drawings, [1] Hentai, [2] Neutral, [3] Porn, [4] Sexy
     */
    @Synchronized
    fun classify(bitmap: Bitmap): FloatArray = runInference(listOf(bitmap))[0]

    /**
     * Scores every bitmap in a single interpreter invocation and returns the results in the same
     * order as [bitmaps]. A feed screenshot plans many crops, and batching them collapses that many
     * sequential inferences into one call, which is the bulk of the per-frame latency. If the model
     * rejects a batched input tensor the classifier degrades to one call per crop for the rest of
     * its life, mirroring how the verifier fails open rather than taking detection down with it.
     */
    @Synchronized
    fun classify(bitmaps: List<Bitmap>): Array<FloatArray> {
        require(bitmaps.isNotEmpty()) { "classify requires at least one bitmap" }
        if (!batchingUnavailable && bitmaps.size > 1) {
            try {
                return runInference(bitmaps)
            } catch (error: Throwable) {
                batchingUnavailable = true
                Log.w(TAG, "Batched classify failed; scoring crops one at a time", error)
            }
        }
        return Array(bitmaps.size) { runInference(listOf(bitmaps[it]))[0] }
    }

    private fun runInference(bitmaps: List<Bitmap>): Array<FloatArray> {
        val batch = bitmaps.size
        val buffer = prepareInput(batch)
        buffer.clear()
        for (bitmap in bitmaps) appendPixels(bitmap, buffer)
        buffer.rewind()
        val output = Array(batch) { FloatArray(5) }
        interpreter.run(buffer, output)
        return output
    }

    /** Resizes the input tensor to [batch] when it changes and returns the exact-sized buffer. */
    private fun prepareInput(batch: Int): ByteBuffer {
        if (batch != configuredBatchSize) {
            interpreter.resizeInput(0, intArrayOf(batch, inputSize, inputSize, 3))
            interpreter.allocateTensors()
            configuredBatchSize = batch
        }
        if (batch == 1) return singleImageBuffer
        var buffer = batchBuffer
        if (buffer == null || batchBufferCount != batch) {
            buffer = allocateInputBuffer(batch)
            batchBuffer = buffer
            batchBufferCount = batch
        }
        return buffer
    }

    private fun allocateInputBuffer(batch: Int): ByteBuffer =
        ByteBuffer.allocateDirect(batch * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

    /**
     * Resizes [bitmap] to [inputSize]x[inputSize] and appends it to [buffer] as float32 RGB.
     * The model's input tensor is FLOAT32 [batch,224,224,3]; pixels are normalized to [0,1]
     * (the preprocessing used by GantMan's nsfw_model MobileNetV2). If scores look off,
     * the other common convention is [-1,1] via (v / 127.5f - 1f).
     */
    private fun appendPixels(bitmap: Bitmap, buffer: ByteBuffer) {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, /* filter = */ true)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
            buffer.putFloat((pixel and 0xFF) / 255f)          // B
        }
        if (resized !== bitmap) resized.recycle()
    }

    /** Memory-maps the .tflite model straight out of the (uncompressed) assets. */
    private fun loadModelFile(context: Context, path: String): MappedByteBuffer {
        context.assets.openFd(path).use { afd ->
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        private const val TAG = "SinSheld"
    }
}
