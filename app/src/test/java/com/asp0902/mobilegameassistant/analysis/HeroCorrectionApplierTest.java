package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class HeroCorrectionApplierTest {
    @Test
    public void keepsUserCorrectedValenOverPerseusRecognition() {
        ShopItemState mistaken = new ShopItemState(
                0, ShopItemType.HERO, 15, null, .73f, null, java.util.Collections.emptyList(),
                "perseus", "페르세우스", "레오프론", HeroRecognitionStatus.NEEDS_CONFIRMATION);
        HeroReference valen = new HeroReference("valen", "발렌", "레오프론", null);

        ShopItemState corrected = HeroCorrectionApplier.INSTANCE.apply(mistaken, valen);

        assertEquals("valen", corrected.getHeroId());
        assertNotEquals("perseus", corrected.getHeroId());
        assertEquals(RecognitionSource.USER_CONFIRMED, corrected.getRecognitionSource());
        assertEquals(1f, corrected.getConfidence(), 0f);
    }
}
