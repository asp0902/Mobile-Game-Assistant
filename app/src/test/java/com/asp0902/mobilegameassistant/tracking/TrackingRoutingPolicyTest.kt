package com.asp0902.mobilegameassistant.tracking

import com.asp0902.mobilegameassistant.analysis.ScreenType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingRoutingPolicyTest {
    @Test
    fun honorDuelScreensRunHonorDuelPipeline() {
        assertTrue(shouldRunHonorDuelPipeline(ScreenType.HONOR_DUEL_SHOP))
        assertTrue(shouldRunHonorDuelPipeline(ScreenType.HONOR_DUEL_HERO_MANAGEMENT))
        assertTrue(shouldRunHonorDuelPipeline(ScreenType.HERO_DETAIL_POPUP))
        assertTrue(shouldRunHonorDuelPipeline(ScreenType.EQUIPMENT_DETAIL_POPUP))
    }

    @Test
    fun artisansAndUnknownScreensDoNotRunHonorDuelPipeline() {
        assertFalse(shouldRunHonorDuelPipeline(ScreenType.ARTISANS_PATH_CARD_SELECTION))
        assertFalse(shouldRunHonorDuelPipeline(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION))
        assertFalse(shouldRunHonorDuelPipeline(ScreenType.OTHER))
        assertFalse(shouldRunHonorDuelPipeline(ScreenType.UNKNOWN))
        assertTrue(isNeutralScreen(ScreenType.OTHER))
        assertFalse(isNeutralScreen(ScreenType.HONOR_DUEL_SHOP))
    }
}
