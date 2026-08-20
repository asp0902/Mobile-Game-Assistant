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

    @Test
    public void prioritizesHeroDetailPopup() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "발렌 레오프론 전사 물리 공격 사정거리 1",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));

        assertEquals(ScreenType.HERO_DETAIL_POPUP, result.getType());
    }

    @Test
    public void detectsDeploymentTimer() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "확인 (56초)",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));

        assertEquals(ScreenType.HONOR_DUEL_BATTLE_DEPLOYMENT, result.getType());
    }
}
