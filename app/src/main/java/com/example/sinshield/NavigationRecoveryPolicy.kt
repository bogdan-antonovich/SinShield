package com.example.sinshield

/** Pure success criteria for navigation that is otherwise driven by Android accessibility. */
internal object NavigationRecoveryPolicy {
    /**
     * An ordinary Home-tab click needs either positive FEED evidence or a real app-window
     * transition. Apps whose explicit Home target is known to navigate within one window may opt
     * into treating that click as conclusive. A successful task restart is independently strong
     * evidence because it clears the route stack and starts the app's launcher activity.
     */
    fun reachedHomeFeed(
        foregroundPackage: String?,
        appPackage: String,
        resultingMode: ShieldedScreenMode,
        taskRestarted: Boolean,
        homeTabClicked: Boolean = false,
        homeTabClickIsConclusive: Boolean = false,
        startingWindowId: Int = UNKNOWN_WINDOW_ID,
        resultingWindowId: Int = UNKNOWN_WINDOW_ID
    ): Boolean = foregroundPackage == appPackage &&
        (
            taskRestarted ||
                resultingMode == ShieldedScreenMode.FEED ||
                (homeTabClicked &&
                    (homeTabClickIsConclusive ||
                        windowTransitioned(startingWindowId, resultingWindowId)))
            )

    /**
     * A full-screen block belongs to the app, not to one short-lived accessibility window. Once a
     * current scan proves that app's visible frame safe, an older window id must not strand it.
     */
    fun shouldClearAppBlockOnSafeFrame(
        blockedPackage: String,
        safeFramePackage: String
    ): Boolean = blockedPackage == safeFramePackage

    private fun windowTransitioned(startingWindowId: Int, resultingWindowId: Int): Boolean =
        startingWindowId != UNKNOWN_WINDOW_ID &&
            resultingWindowId != UNKNOWN_WINDOW_ID &&
            startingWindowId != resultingWindowId

    private const val UNKNOWN_WINDOW_ID = -1
}
