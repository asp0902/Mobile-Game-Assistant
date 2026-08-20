package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import org.junit.Test;

public class OwnedHeroRecognizerTest {
    @Test
    public void parsesOnlyDisplayedSellValues() {
        for (int value : new int[] {2, 6, 8, 12, 14, 16}) {
            assertEquals(Integer.valueOf(value), SellValueParser.INSTANCE.parse("판매 +" + value));
        }
        assertNull(SellValueParser.INSTANCE.parse("에픽 영웅"));
    }

    @Test
    public void keepsEquipmentSeparateFromSellReserve() {
        HeroRecognitionResult hero = new HeroRecognitionResult(
                "zorua", "조르아", "그레이브본", .95f,
                HeroRecognitionStatus.CONFIRMED, Collections.emptyList());
        PromotionGaugeObservation gauge = PromotionGaugeRecognizer.INSTANCE.fromSegments(2, 4, false);

        OwnedHeroState result = OwnedHeroRecognizer.INSTANCE.state(0, "조르아 밀림 후드 +2", hero, gauge);

        assertEquals("밀림 후드", result.getEquipmentName());
        assertEquals(Integer.valueOf(2), result.getSellValue());
        assertEquals(Integer.valueOf(2), result.getPromotion().getProgress());
    }
}
