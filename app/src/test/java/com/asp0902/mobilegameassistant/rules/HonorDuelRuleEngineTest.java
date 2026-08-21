package com.asp0902.mobilegameassistant.rules;

import static org.junit.Assert.assertEquals;

import com.asp0902.mobilegameassistant.analysis.ArtifactXp;
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader;
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis;
import com.asp0902.mobilegameassistant.analysis.ScreenType;
import com.asp0902.mobilegameassistant.analysis.ShopItemState;
import com.asp0902.mobilegameassistant.analysis.ShopItemType;
import java.util.Collections;
import java.util.Arrays;
import org.junit.Test;

public class HonorDuelRuleEngineTest {
    @Test
    public void buysArtifactExperienceThatCompletesThreshold() {
        HonorDuelShopAnalysis analysis = new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(30, 2, 9, new ArtifactXp(20, 24)),
                Collections.singletonList(new ShopItemState(0, ShopItemType.ARTIFACT_XP, 15, 4, .95f)),
                Collections.emptyList());

        ShopRecommendation recommendation = new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommend(analysis).get(0);

        assertEquals(RecommendationAction.BUY, recommendation.getAction());
    }

    @Test
    public void buysBothAffordableMidasExperienceItemsBeforeThreshold() {
        HonorDuelShopAnalysis analysis = new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(345, 4, 9, new ArtifactXp(28, 46), null, null, null, null, "마이다스의 재물", Collections.emptyMap()),
                Arrays.asList(
                        new ShopItemState(0, ShopItemType.ARTIFACT_XP, 15, 4, .95f),
                        new ShopItemState(1, ShopItemType.ARTIFACT_XP, 15, 4, .95f)),
                Collections.emptyList());

        assertEquals(RecommendationAction.BUY, new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommend(analysis).get(0).getAction());
        assertEquals(RecommendationAction.BUY, new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommend(analysis).get(1).getAction());
    }

    @Test
    public void keepsMidasTrialCardForConfirmationInsteadOfSkipping() {
        HonorDuelShopAnalysis analysis = new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(60, 1, 9, new ArtifactXp(0, 24), null, null, null, null, "마이다스의 재물", Collections.emptyMap()),
                Collections.singletonList(new ShopItemState(0, ShopItemType.TRIAL_HERO_CARD, 12, null, .75f)),
                Collections.emptyList());

        assertEquals(RecommendationAction.CONSIDER, new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommend(analysis).get(0).getAction());
    }
}
