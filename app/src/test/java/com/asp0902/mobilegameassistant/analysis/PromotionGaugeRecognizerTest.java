package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PromotionGaugeRecognizerTest {
    @Test
    public void recognizesConfirmedValenAndBonnieProgress() {
        PromotionGaugeObservation valen = PromotionGaugeRecognizer.INSTANCE.fromSegments(2, 4, false);
        PromotionGaugeObservation bonnie = PromotionGaugeRecognizer.INSTANCE.fromSegments(1, 4, false);

        assertEquals(Integer.valueOf(2), valen.getProgress());
        assertEquals(Integer.valueOf(4), valen.getRequired());
        assertEquals(Integer.valueOf(1), bonnie.getProgress());
        assertEquals(Integer.valueOf(4), bonnie.getRequired());
    }

    @Test
    public void maxRankOverridesGaugeAndUnknownStaysUnknown() {
        PromotionGaugeObservation max = PromotionGaugeRecognizer.INSTANCE.fromSegments(2, 4, true);
        PromotionGaugeObservation unknown = PromotionGaugeRecognizer.INSTANCE.fromSegments(5, 4, false);

        assertTrue(max.isMaxRank());
        assertNull(max.getProgress());
        assertFalse(unknown.isMaxRank());
        assertNull(unknown.getProgress());
    }

    @Test
    public void gaugesRemainBoundToTheirOwnSlotsWithoutHeroGuessing() {
        OwnedHeroGaugeSlot valenSlot = new OwnedHeroGaugeSlot(
                0, new NormalizedRect(0f, 0f, .1f, .1f),
                PromotionGaugeRecognizer.INSTANCE.fromSegments(2, 4, false), null);
        OwnedHeroGaugeSlot perseusSlot = new OwnedHeroGaugeSlot(
                1, new NormalizedRect(.1f, 0f, .2f, .1f),
                PromotionGaugeRecognizer.INSTANCE.fromSegments(0, 4, false), null);

        assertEquals(0, valenSlot.getSlotIndex());
        assertEquals(Integer.valueOf(2), valenSlot.getGauge().getProgress());
        assertEquals(1, perseusSlot.getSlotIndex());
        assertNull(perseusSlot.getHeroId());
    }
}
