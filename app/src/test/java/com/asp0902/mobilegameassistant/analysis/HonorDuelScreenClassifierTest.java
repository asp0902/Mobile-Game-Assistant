package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HonorDuelScreenClassifierTest {
    @Test
    public void confirmsShopOnlyWithMultipleSignals() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classify(true, true, 2, 8);

        assertEquals(ScreenType.HONOR_DUEL_SHOP, result.getType());
    }

    @Test
    public void marksPartialShopEvidenceUnknown() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classify(false, true, null, 0);

        assertEquals(ScreenType.UNKNOWN, result.getType());
    }
}
