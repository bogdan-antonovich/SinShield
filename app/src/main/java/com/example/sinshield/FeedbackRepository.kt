package com.example.sinshield

import android.content.Context
import org.json.JSONObject
import java.io.File

internal data class FeedbackEvidence(
    val capturedAtEpochMillis: Long,
    val packageName: String,
    val windowId: Int,
    val frameHash: Long,
    val verdict: String,
    val protectionLevel: String,
    val diagnostics: String,
    val imageJpeg: ByteArray
)

/** Stores user-confirmed false positives privately and outside Android's automatic backup. */
internal object FeedbackRepository {
    fun saveFalsePositive(context: Context, evidence: FeedbackEvidence): File {
        val root = File(context.noBackupFilesDir, DIRECTORY_NAME).apply {
            check(exists() || mkdirs()) { "Could not create feedback directory" }
        }
        val hash = evidence.frameHash.toULong().toString(16)
        val record = File(root, "${evidence.capturedAtEpochMillis}_$hash").apply {
            check(exists() || mkdirs()) { "Could not create feedback record" }
        }
        File(record, IMAGE_FILE_NAME).writeBytes(evidence.imageJpeg)
        File(record, ANALYSIS_FILE_NAME).writeText(
            JSONObject()
                .put("schemaVersion", 1)
                .put("feedback", "not_disturbing")
                .put("capturedAtEpochMillis", evidence.capturedAtEpochMillis)
                .put("packageName", evidence.packageName)
                .put("windowId", evidence.windowId)
                .put("frameHash", hash)
                .put("verdict", evidence.verdict)
                .put("protectionLevel", evidence.protectionLevel)
                .put("diagnostics", evidence.diagnostics)
                .toString(2)
        )
        rememberFalsePositive(context, evidence.packageName, evidence.frameHash)
        return record
    }

    /** Remembers a correction immediately so the same or a near-identical frame is not blocked. */
    @Synchronized
    fun rememberFalsePositive(context: Context, packageName: String, frameHash: Long) {
        val entry = encodeMemoryEntry(packageName, frameHash)
        val retained = (listOf(entry) + loadMemoryEntries(context))
            .distinct()
            .take(MAX_REMEMBERED_FALSE_POSITIVES)
        preferences(context).edit().putString(MEMORY_KEY, retained.joinToString("\n")).apply()
    }

    @Synchronized
    fun isKnownFalsePositive(context: Context, packageName: String, frameHash: Long): Boolean =
        loadMemoryEntries(context).any { entry ->
            val separator = entry.lastIndexOf('|')
            if (separator <= 0 || entry.substring(0, separator) != packageName) return@any false
            val rememberedHash = runCatching {
                entry.substring(separator + 1).toULong(16).toLong()
            }.getOrNull() ?: return@any false
            java.lang.Long.bitCount(rememberedHash xor frameHash) < MEMORY_HASH_DISTANCE
        }

    fun falsePositiveCount(context: Context): Int =
        File(context.noBackupFilesDir, DIRECTORY_NAME)
            .listFiles()
            ?.count(File::isDirectory)
            ?: 0

    private fun loadMemoryEntries(context: Context): List<String> {
        val saved = preferences(context).getString(MEMORY_KEY, null)
        if (saved != null) return saved.lineSequence().filter(String::isNotBlank).toList()

        // Seed the new memory from evidence saved by older builds. Directory names contain the
        // hash, while the analysis file supplies the package so corrections remain app-scoped.
        val migrated = File(context.noBackupFilesDir, DIRECTORY_NAME)
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { record ->
                runCatching {
                    val analysis = JSONObject(File(record, ANALYSIS_FILE_NAME).readText())
                    val packageName = analysis.getString("packageName")
                    val hash = analysis.getString("frameHash").toULong(16).toLong()
                    encodeMemoryEntry(packageName, hash)
                }.getOrNull()
            }
            .take(MAX_REMEMBERED_FALSE_POSITIVES)
            .toList()
        preferences(context).edit().putString(MEMORY_KEY, migrated.joinToString("\n")).apply()
        return migrated
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(MEMORY_PREFERENCES, Context.MODE_PRIVATE)

    private fun encodeMemoryEntry(packageName: String, frameHash: Long): String =
        "$packageName|${frameHash.toULong().toString(16)}"

    private const val DIRECTORY_NAME = "false_positive_feedback"
    private const val IMAGE_FILE_NAME = "screen.jpg"
    private const val ANALYSIS_FILE_NAME = "analysis.json"
    private const val MEMORY_PREFERENCES = "false_positive_memory"
    private const val MEMORY_KEY = "entries"
    private const val MAX_REMEMBERED_FALSE_POSITIVES = 100
    private const val MEMORY_HASH_DISTANCE = 10
}
