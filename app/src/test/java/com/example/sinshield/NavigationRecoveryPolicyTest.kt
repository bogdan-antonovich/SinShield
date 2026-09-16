package com.example.sinshield

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRecoveryPolicyTest {
    @Test
    fun unknownSurfaceIsNotAcceptedAfterHomeTabClick() {
        assertFalse(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.facebook.katana",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = false
            )
        )
    }

    @Test
    fun feedEvidenceCompletesOrdinaryNavigation() {
        assertTrue(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.facebook.katana",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.FEED,
                taskRestarted = false
            )
        )
    }

    @Test
    fun launcherRestartCompletesUnknownFacebookSurface() {
        assertTrue(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.facebook.katana",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = true
            )
        )
    }

    @Test
    fun anotherForegroundPackageNeverCompletesNavigation() {
        assertFalse(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.android.launcher",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.FEED,
                taskRestarted = true
            )
        )
    }

    @Test
    fun homeTabWindowTransitionCompletesUnknownFacebookNavigation() {
        assertTrue(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.facebook.katana",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = false,
                homeTabClicked = true,
                startingWindowId = 1382,
                resultingWindowId = 1368
            )
        )
    }

    @Test
    fun unchangedUnknownWindowDoesNotCompleteHomeNavigation() {
        assertFalse(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.facebook.katana",
                appPackage = "com.facebook.katana",
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = false,
                homeTabClicked = true,
                startingWindowId = 1382,
                resultingWindowId = 1382
            )
        )
    }

    @Test
    fun instagramHomeClickCompletesNavigationInsideUnchangedWindow() {
        assertTrue(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = "com.instagram.android",
                appPackage = "com.instagram.android",
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = false,
                homeTabClicked = true,
                homeTabClickIsConclusive = true,
                startingWindowId = 218,
                resultingWindowId = 218
            )
        )
    }

    @Test
    fun xHomeClickCompletesNavigationInsideUnchangedWindow() {
        assertTrue(
            NavigationRecoveryPolicy.reachedHomeFeed(
                foregroundPackage = ShieldedApp.X.packageName,
                appPackage = ShieldedApp.X.packageName,
                resultingMode = ShieldedScreenMode.UNKNOWN,
                taskRestarted = false,
                homeTabClicked = true,
                homeTabClickIsConclusive = ShieldedApp.X.homeTabClickIsConclusive,
                startingWindowId = 451,
                resultingWindowId = 451
            )
        )
    }

    @Test
    fun currentSafeFrameClearsAnOlderWindowBlockForSameApp() {
        assertTrue(
            NavigationRecoveryPolicy.shouldClearAppBlockOnSafeFrame(
                blockedPackage = "com.facebook.katana",
                safeFramePackage = "com.facebook.katana"
            )
        )
    }

    @Test
    fun safeFrameFromAnotherAppDoesNotClearTheBlock() {
        assertFalse(
            NavigationRecoveryPolicy.shouldClearAppBlockOnSafeFrame(
                blockedPackage = "com.facebook.katana",
                safeFramePackage = "com.instagram.android"
            )
        )
    }
}
