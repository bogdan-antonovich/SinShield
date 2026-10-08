package app.sinshield

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import androidx.core.content.edit

internal enum class ProtectionPreviewStage {
    IDLE,
    ACCESSIBILITY,
    OVERLAY,
    BATTERY,
    VPN,
    READY,
    WAITING_SITE_BLOCK,
    SITE_EXPLANATION,
    WAITING_CARS_SEARCH,
    WAITING_IMAGES,
    IMAGE_EXPLANATION,
    TOUR_INTRO,
    TOUR_STREAK,
    TOUR_PROTECTION_MODE,
    TOUR_ADVANCED_TUNING,
    TOUR_DOMAIN_BLOCK_LIST,
    TOUR_OPTIONAL_PERMISSIONS
}

/** Opens the new-tab screen in the user's default browser without showing an app chooser. */
internal fun newPreviewBrowserTabIntent(context: Context): Intent? {
    val defaultBrowser = defaultBrowserComponent(context) ?: return null
    if (defaultBrowser.className.contains("chrome.IntentDispatcher")) {
        return Intent(Intent.ACTION_VIEW, Uri.parse("chrome://newtab"))
            .setComponent(defaultBrowser)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
    }

    val defaultBrowserPackage = defaultBrowser.packageName
    val newTabIntent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_APP_BROWSER)
        .setPackage(defaultBrowserPackage)
    val browserEntryPoint = newTabIntent
        .resolveActivity(context.packageManager)
        ?: context.packageManager.getLaunchIntentForPackage(defaultBrowserPackage)?.component
        ?: return null

    return Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_APP_BROWSER)
        .setComponent(browserEntryPoint)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
}

/** Opens a URL in a fresh tab in the user's default browser without showing an app chooser. */
internal fun newPreviewBrowserUrlIntent(context: Context, url: String): Intent? {
    val defaultBrowser = defaultBrowserComponent(context) ?: return null
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setComponent(defaultBrowser)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
        .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
}

private fun defaultBrowserComponent(context: Context): ComponentName? {
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val resolved = context.packageManager
        .resolveActivity(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?: return null
    val isInstalledBrowserActivity = context.packageManager
        .queryIntentActivities(webIntent, PackageManager.MATCH_DEFAULT_ONLY)
        .any { candidate ->
            candidate.activityInfo.packageName == resolved.packageName &&
                candidate.activityInfo.name == resolved.name
        }
    if (!isInstalledBrowserActivity) return null
    return ComponentName(resolved.packageName, resolved.name)
}

/** Durable state for the opt-in, cross-app protection preview. */
internal object ProtectionPreviewRepository {
    const val ACTION_STATE_CHANGED = "app.sinshield.action.PREVIEW_STATE_CHANGED"
    const val TEST_DOMAIN = "neverssl.com"
    const val TEST_URL = "http://$TEST_DOMAIN"
    private const val PREFERENCES = "protection_preview"
    private const val STAGE = "stage"
    private const val COMPLETED = "completed"
    private const val DECLINED = "declined"
    private const val OFFER_AFTER = "offer_after"
    private const val PREVIEW_STARTED_AT = "preview_started_at"
    private const val BROWSER_PACKAGE = "browser_package"
    private const val SITE_BLOCK_ARMED = "site_block_armed"
    private const val DAY_MS = 24L * 60L * 60L * 1_000L
    private const val ACTIVE_PREVIEW_TTL_MS = 30L * 60L * 1_000L

    fun stage(context: Context): ProtectionPreviewStage {
        expireStalePreview(context)
        val preferences = preferences(context)
        val saved = preferences.getString(STAGE, null)
        val restored = ProtectionPreviewStage.entries.firstOrNull { it.name == saved }
            ?: ProtectionPreviewStage.IDLE
        if (restored == ProtectionPreviewStage.BATTERY) {
            // Battery optimization moved back to optional settings. Users updating during the
            // former required step should continue directly to website protection.
            preferences.edit { putString(STAGE, ProtectionPreviewStage.VPN.name) }
            return ProtectionPreviewStage.VPN
        }
        return restored
    }

    fun shouldOffer(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val preferences = preferences(context)
        return !preferences.getBoolean(COMPLETED, false) &&
            !preferences.getBoolean(DECLINED, false) &&
            stage(context) == ProtectionPreviewStage.IDLE &&
            now >= preferences.getLong(OFFER_AFTER, 0L)
    }

    fun start(context: Context) {
        preferences(context).edit {
            putString(STAGE, ProtectionPreviewStage.ACCESSIBILITY.name)
            putLong(PREVIEW_STARTED_AT, System.currentTimeMillis())
            remove(OFFER_AFTER)
            remove(SITE_BLOCK_ARMED)
        }
        broadcast(context)
    }

    fun moveTo(context: Context, stage: ProtectionPreviewStage) {
        preferences(context).edit {
            putString(STAGE, stage.name)
            if (stage == ProtectionPreviewStage.WAITING_SITE_BLOCK) {
                // The tutorial adds exactly one temporary DNS rule. Arming it before the browser
                // step means both copying and typing the address exercise the real VPN block path.
                putBoolean(SITE_BLOCK_ARMED, true)
            } else if (stage != ProtectionPreviewStage.SITE_EXPLANATION) {
                remove(SITE_BLOCK_ARMED)
            }
        }
        broadcast(context)
    }

    /** Arms the safe test domain only after the user explicitly starts the navigation. */
    fun armSiteBlockTest(context: Context) {
        if (stage(context) != ProtectionPreviewStage.WAITING_SITE_BLOCK) return
        preferences(context).edit { putBoolean(SITE_BLOCK_ARMED, true) }
    }

    /** Removes the tutorial-only DNS rule after the real website-block overlay is on screen. */
    fun releaseSiteBlockTest(context: Context) {
        preferences(context).edit { remove(SITE_BLOCK_ARMED) }
    }

    fun maybeLater(context: Context) {
        preferences(context).edit {
            putString(STAGE, ProtectionPreviewStage.IDLE.name)
            putLong(OFFER_AFTER, System.currentTimeMillis() + DAY_MS)
            remove(PREVIEW_STARTED_AT)
            remove(SITE_BLOCK_ARMED)
        }
        broadcast(context)
    }

    fun decline(context: Context) {
        preferences(context).edit {
            putBoolean(DECLINED, true)
            putString(STAGE, ProtectionPreviewStage.IDLE.name)
            remove(OFFER_AFTER)
            remove(PREVIEW_STARTED_AT)
            remove(SITE_BLOCK_ARMED)
        }
        broadcast(context)
    }

    fun complete(context: Context) {
        preferences(context).edit {
            putBoolean(COMPLETED, true)
            putString(STAGE, ProtectionPreviewStage.IDLE.name)
            remove(OFFER_AFTER)
            remove(PREVIEW_STARTED_AT)
            remove(SITE_BLOCK_ARMED)
        }
        broadcast(context)
    }

    /** Debug/testing entry point: forget every prior choice so the complete offer can be replayed. */
    fun restartForTesting(context: Context) {
        preferences(context).edit {
            clear()
            putString(STAGE, ProtectionPreviewStage.IDLE.name)
        }
        broadcast(context)
    }

    fun isBrowserStep(context: Context): Boolean = when (stage(context)) {
        ProtectionPreviewStage.WAITING_SITE_BLOCK,
        ProtectionPreviewStage.SITE_EXPLANATION,
        ProtectionPreviewStage.WAITING_CARS_SEARCH,
        ProtectionPreviewStage.WAITING_IMAGES,
        ProtectionPreviewStage.IMAGE_EXPLANATION -> true
        else -> false
    }

    fun setBrowserPackage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank() || packageName == "android") return
        preferences(context).edit { putString(BROWSER_PACKAGE, packageName) }
    }

    fun browserPackage(context: Context): String? =
        preferences(context).getString(BROWSER_PACKAGE, null)

    fun shouldTemporarilyBlock(context: Context, domain: String): Boolean {
        val normalized = domain.trim().trimEnd('.').lowercase()
        val active = preferences(context).getBoolean(SITE_BLOCK_ARMED, false) && when (stage(context)) {
            ProtectionPreviewStage.WAITING_SITE_BLOCK,
            ProtectionPreviewStage.SITE_EXPLANATION -> true
            else -> false
        }
        return active && (normalized == TEST_DOMAIN || normalized == "www.$TEST_DOMAIN")
    }

    fun recordSiteBlocked(context: Context): Boolean {
        if (stage(context) == ProtectionPreviewStage.SITE_EXPLANATION) return false
        moveTo(context, ProtectionPreviewStage.SITE_EXPLANATION)
        return true
    }

    private fun expireStalePreview(context: Context) {
        val preferences = preferences(context)
        val saved = preferences.getString(STAGE, ProtectionPreviewStage.IDLE.name)
        if (saved == ProtectionPreviewStage.IDLE.name) return
        val startedAt = preferences.getLong(PREVIEW_STARTED_AT, 0L)
        if (startedAt > 0L && System.currentTimeMillis() - startedAt >= ACTIVE_PREVIEW_TTL_MS) {
            preferences.edit {
                putString(STAGE, ProtectionPreviewStage.IDLE.name)
                putLong(OFFER_AFTER, System.currentTimeMillis() + DAY_MS)
                remove(PREVIEW_STARTED_AT)
                remove(SITE_BLOCK_ARMED)
            }
        }
    }

    private fun broadcast(context: Context) {
        context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

internal data class BrowserPageEvidence(
    val editableTexts: List<String>,
    val selectedTexts: List<String>,
    val addressBarTexts: List<String>,
    val visibleTexts: List<String>
)

internal object PreviewBrowserPageDetector {
    fun isAtDomain(evidence: BrowserPageEvidence, domain: String): Boolean {
        val expected = normalizeHost(domain)
        return expected != null && evidence.addressBarTexts.any { value ->
            val host = normalizeHost(value)
            host == expected || host == "www.$expected"
        }
    }

    /**
     * DNS traffic is device-wide and may belong to an ad, image, or background tab. A website
     * block is relevant to the visible page only when its address-bar host is the queried domain,
     * a subdomain of it, or its parent (for example example.com and www.example.com).
     */
    fun isAtBlockedDomain(evidence: BrowserPageEvidence, blockedDomain: String): Boolean {
        val blockedHost = normalizeHost(blockedDomain) ?: return false
        return evidence.addressBarTexts.any { value ->
            val visibleHost = normalizeHost(value) ?: return@any false
            visibleHost == blockedHost ||
                visibleHost.endsWith(".$blockedHost") ||
                blockedHost.endsWith(".$visibleHost")
        }
    }

    private fun normalizeHost(value: String): String? {
        val host = value
            .trim()
            .lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .trimEnd('.')
        return host.takeIf { it.isNotBlank() && '.' in it && ' ' !in it }
    }
}

internal object GooglePreviewPageDetector {
    fun isSearchFor(evidence: BrowserPageEvidence, query: String): Boolean {
        val expected = normalize(query)
        val queryVisible = evidence.editableTexts.any { normalize(it) == expected } ||
            evidence.addressBarTexts.any { address ->
                val normalized = normalize(address)
                "q=$expected" in normalized || "q=${expected.replace(".", "%2e")}" in normalized
            }
        val googleVisible = evidence.addressBarTexts.any { "google." in normalize(it) } ||
            evidence.visibleTexts.any { normalize(it) == "google" }
        return queryVisible && googleVisible
    }

    fun isCarsImages(evidence: BrowserPageEvidence): Boolean {
        if (!isSearchFor(evidence, "cars")) return false
        val imageUrl = evidence.addressBarTexts.any {
            val value = normalize(it)
            "tbm=isch" in value || "udm=2" in value
        }
        val selectedImages = evidence.selectedTexts.any {
            normalize(it).let { text -> text == "images" || text.contains("images") }
        }
        return imageUrl || selectedImages
    }

    private fun normalize(value: String): String = value.trim().lowercase()
}
