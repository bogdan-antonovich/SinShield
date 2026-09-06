package com.example.sinshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAnalysisFlowSelectorTest {
    @Test
    fun routesEachSpecializedAppToItsOwnFlow() {
        assertEquals(
            AppAnalysisFlowKind.X,
            AppAnalysisFlowSelector.select(ShieldedApp.X.packageName)
        )
        assertEquals(
            AppAnalysisFlowKind.INSTAGRAM,
            AppAnalysisFlowSelector.select(ShieldedApp.INSTAGRAM.packageName)
        )
        assertEquals(
            AppAnalysisFlowKind.REDDIT,
            AppAnalysisFlowSelector.select(ShieldedApp.REDDIT.packageName)
        )
    }

    @Test
    fun otherPackagesUseTheConservativeFullScreenFlow() {
        assertEquals(
            AppAnalysisFlowKind.FULL_SCREEN_ONLY,
            AppAnalysisFlowSelector.select(ShieldedApp.FACEBOOK.packageName)
        )
        assertEquals(
            AppAnalysisFlowKind.FULL_SCREEN_ONLY,
            AppAnalysisFlowSelector.select("com.example.unknown")
        )
    }

    @Test
    fun localizedStageOnlyRunsAfterASafeWholeScreen() {
        assertTrue(
            LocalizedStageGate.shouldRun(
                StageOneResult(ContentVerdict.SAFE, ContentVerdict.SAFE, emptyList())
            )
        )
        ContentVerdict.entries.filterNot { it == ContentVerdict.SAFE }.forEach { verdict ->
            assertFalse(
                LocalizedStageGate.shouldRun(
                    StageOneResult(verdict, ContentVerdict.EXPLICIT, listOf("test"))
                )
            )
        }
    }
}
