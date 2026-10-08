package app.sinshield

import org.junit.Assert.assertEquals
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
        assertEquals(
            AppAnalysisFlowKind.BROWSER,
            AppAnalysisFlowSelector.select(SupportedBrowsers.CHROME)
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

}
