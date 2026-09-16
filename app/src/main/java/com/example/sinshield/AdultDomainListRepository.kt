package com.example.sinshield

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

internal data class DomainListUpdateResult(
    val matcher: AdultDomainMatcher?,
    val errorMessage: String? = null
)

internal enum class AddCustomDomainResult {
    ADDED,
    INVALID,
    ALREADY_BLOCKED
}

/** Loads the bundled seed and atomically replaces it with a validated maintained blocklist. */
internal object AdultDomainListRepository {
    const val SOURCE_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"

    private const val ASSET_NAME = "adult_domains_seed.txt"
    private const val STORED_NAME = "adult_domains.txt"
    private const val TEMP_NAME = "adult_domains.download"
    private const val PREFERENCES = "adult_domain_list"
    private const val DOMAIN_COUNT = "domain_count"
    private const val LAST_UPDATED_AT = "last_updated_at"
    private const val CUSTOM_DOMAINS = "custom_domains"
    // A healthy maintained list has tens of thousands of entries; anything smaller is treated as a
    // truncated or wrong download and is rejected in favor of the last good list or the seed.
    private const val MINIMUM_REMOTE_DOMAINS = 10_000
    // Upper bound on the download so a misbehaving or hostile server cannot exhaust storage.
    private const val MAXIMUM_DOWNLOAD_BYTES = 12 * 1024 * 1024
    private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    // Guards the coalescing state below so overlapping refresh() calls share one download instead
    // of racing: the first starts the work, later callers just add their callback to the batch.
    private val refreshLock = Any()
    private val pendingCallbacks = mutableListOf<(DomainListUpdateResult) -> Unit>()
    private var refreshInFlight = false
    // The parsed matcher is immutable and identical for every caller, but parsing the 70k-line
    // blocklist allocates heavily. Cache it so repeated load() calls (service start, staleness
    // checks, the UI domain count) reuse one parse instead of re-reading and re-parsing the file.
    // Only a successful download invalidates it, by publishing its own freshly built matcher.
    @Volatile private var cached: AdultDomainMatcher? = null
    private val loadLock = Any()

    fun load(context: Context): AdultDomainMatcher {
        cached?.let { return it }
        return synchronized(loadLock) {
            cached ?: parseFromDisk(context).also { cached = it }
        }
    }

    private fun parseFromDisk(context: Context): AdultDomainMatcher {
        // Prefer a previously downloaded list, but only if it still passes the size floor; a
        // corrupt or partially written file falls through to the bundled seed rather than leaving
        // the user with a near-empty blocklist.
        val stored = File(context.filesDir, STORED_NAME)
        if (stored.isFile) {
            runCatching {
                stored.bufferedReader().use(AdultDomainMatcher::fromReader)
            }.getOrNull()?.takeIf { it.size >= MINIMUM_REMOTE_DOMAINS }?.let {
                return it.withDomains(customDomains(context))
            }
        }
        val seed = context.assets.open(ASSET_NAME).bufferedReader().use(AdultDomainMatcher::fromReader)
        return seed.withDomains(customDomains(context))
    }

    fun storedDomainCount(context: Context): Int {
        val storedPreferences = preferences(context)
        return if (storedPreferences.contains(DOMAIN_COUNT)) {
            storedPreferences.getInt(DOMAIN_COUNT, 0) + customDomains(context).size
        } else {
            load(context).size
        }
    }

    fun customDomains(context: Context): List<String> =
        preferences(context).getStringSet(CUSTOM_DOMAINS, emptySet()).orEmpty().sorted()

    fun addCustomDomain(context: Context, input: String): AddCustomDomainResult {
        val domain = AdultDomainMatcher.normalizeUserDomain(input)
            ?: return AddCustomDomainResult.INVALID
        val currentMatcher = load(context)
        if (currentMatcher.isBlocked(domain)) return AddCustomDomainResult.ALREADY_BLOCKED

        val updated = customDomains(context).toMutableSet().apply { add(domain) }
        preferences(context).edit().putStringSet(CUSTOM_DOMAINS, updated).apply()
        cached = currentMatcher.withDomains(listOf(domain))
        return AddCustomDomainResult.ADDED
    }

    fun removeCustomDomain(context: Context, domain: String) {
        val updated = customDomains(context).toMutableSet()
        if (!updated.remove(domain)) return
        preferences(context).edit().putStringSet(CUSTOM_DOMAINS, updated).apply()
        cached = null
    }

    fun refreshIfStale(
        context: Context,
        callback: (DomainListUpdateResult) -> Unit
    ) {
        val lastUpdate = preferences(context).getLong(LAST_UPDATED_AT, 0L)
        if (System.currentTimeMillis() - lastUpdate < REFRESH_INTERVAL_MS) {
            callback(DomainListUpdateResult(load(context)))
        } else {
            refresh(context, callback)
        }
    }

    fun refresh(
        context: Context,
        callback: (DomainListUpdateResult) -> Unit
    ) {
        val appContext = context.applicationContext
        val shouldStart = synchronized(refreshLock) {
            pendingCallbacks += callback
            if (refreshInFlight) {
                false
            } else {
                refreshInFlight = true
                true
            }
        }
        if (!shouldStart) return
        executor.execute {
            val result = runCatching { downloadAndInstall(appContext) }
                .fold(
                    onSuccess = { DomainListUpdateResult(it) },
                    onFailure = {
                        DomainListUpdateResult(
                            matcher = null,
                            errorMessage = it.message ?: "The list could not be updated."
                        )
                    }
                )
            mainHandler.post {
                val callbacks = synchronized(refreshLock) {
                    refreshInFlight = false
                    pendingCallbacks.toList().also { pendingCallbacks.clear() }
                }
                callbacks.forEach { it(result) }
            }
        }
    }

    private fun downloadAndInstall(context: Context): AdultDomainMatcher {
        val destination = File(context.filesDir, STORED_NAME)
        val temporary = File(context.cacheDir, TEMP_NAME)
        val connection = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "SinSheld Android domain filter")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Blocklist server returned HTTP ${connection.responseCode}")
            }
            val announcedLength = connection.contentLengthLong
            require(announcedLength <= 0L || announcedLength <= MAXIMUM_DOWNLOAD_BYTES) {
                "Downloaded blocklist is unexpectedly large"
            }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(temporary, false).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAXIMUM_DOWNLOAD_BYTES) {
                            "Downloaded blocklist is unexpectedly large"
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }

            // Validate before installing so a successful HTTP response carrying garbage never
            // replaces a good list; the caller keeps using whatever load() last returned.
            val matcher = temporary.bufferedReader().use(AdultDomainMatcher::fromReader)
            require(matcher.size >= MINIMUM_REMOTE_DOMAINS) {
                "Downloaded blocklist failed validation (${matcher.size} domains)"
            }
            // Swap the validated file into place atomically so a concurrent load() sees either the
            // old list or the new one, never a half-written file. Fall back to a plain move on
            // filesystems that cannot rename atomically (e.g. across cache/files device boundaries).
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            preferences(context).edit()
                .putInt(DOMAIN_COUNT, matcher.size)
                .putLong(LAST_UPDATED_AT, System.currentTimeMillis())
                .apply()
            // Publish the new list so subsequent load() calls return it instead of the now-stale
            // cached parse; this is the only path that invalidates the cache.
            val matcherWithCustomDomains = matcher.withDomains(customDomains(context))
            cached = matcherWithCustomDomains
            return matcherWithCustomDomains
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
