package app.sinshield

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** The real matrices used by one detector pass; the writer reads them synchronously. */
internal data class OpenCvDebugStages(
    val analysis: Mat,
    val grayscale: Mat,
    val gaussianBlur: Mat,
    val canny: Mat,
    val closedEdges: Mat,
    val textureDensity: Mat,
    val textureMask: Mat,
    val houghLines: List<DetectedLineSegment>
)

/** Writes bounded, rate-limited, timestamped visualizations of the complete OpenCV flow. */
internal class OpenCvDetectionDebugWriter(context: Context) {
    private val applicationContext = context.applicationContext
    private val outputDirectory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
        DIRECTORY_NAME
    )
    private var lastCaptureElapsedMs = Long.MIN_VALUE

    fun save(
        bitmap: Bitmap,
        regions: List<DetectionRegion>,
        stages: OpenCvDebugStages
    ) {
        if (!DebugSettings.photoDumps(applicationContext)) return
        val now = SystemClock.elapsedRealtime()
        if (lastCaptureElapsedMs != Long.MIN_VALUE && now - lastCaptureElapsedMs < CAPTURE_INTERVAL_MS) {
            return
        }
        lastCaptureElapsedMs = now

        runCatching {
            check(outputDirectory.exists() || outputDirectory.mkdirs()) {
                "Could not create ${outputDirectory.absolutePath}"
            }
            val timestamp = FILE_TIMESTAMP.format(Date())
            val captureDirectory = File(outputDirectory, "opencv_$timestamp")
            check(captureDirectory.mkdir()) {
                "Could not create ${captureDirectory.absolutePath}"
            }

            writeJpeg(bitmap, File(captureDirectory, "00-original.jpg"))
            writeMat(stages.analysis, File(captureDirectory, "01-analysis.jpg"), jpeg = true)
            writeMat(stages.grayscale, File(captureDirectory, "02-grayscale.png"))
            writeMat(stages.gaussianBlur, File(captureDirectory, "03-gaussian-blur.png"))
            writeMat(stages.canny, File(captureDirectory, "04-canny.png"))
            writeMat(stages.closedEdges, File(captureDirectory, "05-closed-edges.png"))
            writeMat(stages.textureDensity, File(captureDirectory, "06-texture-density.png"))
            writeMat(stages.textureMask, File(captureDirectory, "07-texture-mask.png"))
            writeHoughLines(
                stages.analysis,
                stages.houghLines,
                File(captureDirectory, "08-hough-lines.jpg")
            )

            val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                ?: error("Could not copy screenshot for OpenCV debug output")
            try {
                drawRegions(annotated, regions)
                writeJpeg(
                    annotated,
                    File(captureDirectory, "09-result-${regions.size}-regions.jpg")
                )
                pruneOldCaptures()
                Log.d(TAG, "OpenCV debug flow: ${captureDirectory.absolutePath}")
            } finally {
                annotated.recycle()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not write OpenCV debug screenshot", error)
        }
    }

    private fun writeJpeg(bitmap: Bitmap, output: File) {
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                "Could not encode ${output.absolutePath}"
            }
        }
    }

    private fun writePng(bitmap: Bitmap, output: File) {
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not encode ${output.absolutePath}"
            }
        }
    }

    private fun writeMat(mat: Mat, output: File, jpeg: Boolean = false) {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(mat, bitmap)
            if (jpeg) writeJpeg(bitmap, output) else writePng(bitmap, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeHoughLines(
        analysis: Mat,
        lines: List<DetectedLineSegment>,
        output: File
    ) {
        val bitmap = Bitmap.createBitmap(analysis.cols(), analysis.rows(), Bitmap.Config.ARGB_8888)
        try {
            Utils.matToBitmap(analysis, bitmap)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 40, 40)
                style = Paint.Style.STROKE
                strokeWidth = max(2f, bitmap.width / 360f)
            }
            lines.forEach { line ->
                canvas.drawLine(
                    line.x1.toFloat(),
                    line.y1.toFloat(),
                    line.x2.toFloat(),
                    line.y2.toFloat(),
                    paint
                )
            }
            writeJpeg(bitmap, output)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawRegions(bitmap: Bitmap, regions: List<DetectionRegion>) {
        val canvas = Canvas(bitmap)
        val densityScale = max(1f, bitmap.width / 1080f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 255, 80)
            style = Paint.Style.STROKE
            strokeWidth = 5f * densityScale
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 26f * densityScale
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val labelBackgroundPaint = Paint().apply {
            color = Color.argb(210, 0, 0, 0)
            style = Paint.Style.FILL
        }

        regions.forEachIndexed { index, region ->
            val bounds = RectF(
                region.left * bitmap.width,
                region.top * bitmap.height,
                region.right * bitmap.width,
                region.bottom * bitmap.height
            )
            canvas.drawRect(bounds, boxPaint)

            val coordinates = "#${index + 1} ${bounds.left.toInt()},${bounds.top.toInt()} " +
                "${bounds.width().toInt()}x${bounds.height().toInt()}"
            val textWidth = labelPaint.measureText(coordinates)
            val labelTop = (bounds.top - labelPaint.textSize - 8f).coerceAtLeast(0f)
            canvas.drawRect(
                bounds.left,
                labelTop,
                (bounds.left + textWidth + 12f).coerceAtMost(bitmap.width.toFloat()),
                labelTop + labelPaint.textSize + 8f,
                labelBackgroundPaint
            )
            canvas.drawText(coordinates, bounds.left + 6f, labelTop + labelPaint.textSize, labelPaint)
        }

        if (regions.isEmpty()) {
            val message = "OpenCV regions: 0"
            val padding = 12f * densityScale
            canvas.drawRect(
                0f,
                0f,
                labelPaint.measureText(message) + padding * 2,
                labelPaint.textSize + padding * 2,
                labelBackgroundPaint
            )
            canvas.drawText(message, padding, labelPaint.textSize + padding, labelPaint)
        }
    }

    private fun pruneOldCaptures() {
        val captureDirectories = outputDirectory.listFiles { file ->
            file.isDirectory && file.name.startsWith(FILE_PREFIX)
        }?.sortedByDescending(File::lastModified).orEmpty()
        captureDirectories.drop(MAX_CAPTURE_RUNS).forEach { oldCapture ->
            if (!oldCapture.deleteRecursively()) {
                Log.w(TAG, "Could not delete ${oldCapture.absolutePath}")
            }
        }
        val legacyFiles = outputDirectory.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX)
        }?.sortedByDescending(File::lastModified).orEmpty()
        legacyFiles.drop(MAX_LEGACY_CAPTURES).forEach { oldCapture ->
            if (!oldCapture.delete()) Log.w(TAG, "Could not delete ${oldCapture.absolutePath}")
        }
    }

    private companion object {
        private const val TAG = "SinSheld"
        private const val DIRECTORY_NAME = "opencv-debug"
        private const val FILE_PREFIX = "opencv_"
        private const val CAPTURE_INTERVAL_MS = 1_000L
        private const val MAX_CAPTURE_RUNS = 4
        private const val MAX_LEGACY_CAPTURES = 4
        private const val JPEG_QUALITY = 92
        private val FILE_TIMESTAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
