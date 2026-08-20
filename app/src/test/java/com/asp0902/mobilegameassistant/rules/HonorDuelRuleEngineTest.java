package com.asp0902.mobilegameassistant.rules;

import static org.junit.Assert.assertEquals;

import com.asp0902.mobilegameassistant.analysis.ArtifactXp;
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader;
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis;
import com.asp0902.mobilegameassistant.analysis.ScreenType;
import com.asp0902.mobilegameassistant.analysis.ShopItemState;
import com.asp0902.mobilegameassistant.analysis.ShopItemType;
import java.util.Collections;
import org.junit.Test;

public class HonorDuelRuleEngineTest {
    @Test
    public void buysArtifactExperienceThatCompletesThreshold() {
        HonorDuelShopAnalysis analysis = new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(30, 2, 9, new ArtifactXp(20, 24)),
                Collections.singletonList(new ShopItemState(0, ShopItemType.ARTIFACT_XP, 15, 4, .95f)),
                Collections.emptyList());

        ShopRecommendation recommendation = new HonorDuelRuleEngine().recommend(analysis).get(0);

        assertEquals(RecommendationAction.BUY, recommendation.getAction());
    }
}
