package com.asp0902.mobilegameassistant.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.asp0902.mobilegameassistant.analysis.ArtifactXp;
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader;
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis;
import com.asp0902.mobilegameassistant.analysis.HeroRarity;
import com.asp0902.mobilegameassistant.analysis.HeroRecognitionStatus;
import com.asp0902.mobilegameassistant.analysis.OwnedHeroState;
import com.asp0902.mobilegameassistant.analysis.PromotionGaugeObservation;
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

    @Test
    public void valuesValenFuturePromotion() {
        ShopRecommendation recommendation = recommendations(hero("valen", "발렌", 2, false),
                Collections.singletonList(offer(0, "valen", 1, 15)), 60).get(0);

        assertEquals(RecommendationAction.CONSIDER, recommendation.getAction());
        assertTrue(recommendation.getReason().contains("2/4 → 3/4"));
    }

    @Test
    public void buysBonnieOneAndTwoForConfirmedMaxRankPromotion() {
        java.util.List<ShopRecommendation> recommendations = recommendations(hero("bonnie", "보니", 1, false), Arrays.asList(
                offer(0, "bonnie", 1, 15), offer(1, "bonnie", 2, 30)), 45);

        assertEquals(RecommendationAction.BUY, recommendations.get(0).getAction());
        assertEquals(RecommendationAction.BUY, recommendations.get(1).getAction());
        assertTrue(recommendations.get(0).getReason().contains("BUY BOTH"));
        assertTrue(recommendations.get(0).getReason().contains("최대 등급"));
    }

    @Test
    public void skipsDuplicateForMaxRankHero() {
        ShopRecommendation recommendation = recommendations(hero("valen", "발렌", 0, true),
                Collections.singletonList(offer(0, "valen", 1, 15)), 60).get(0);

        assertEquals(RecommendationAction.SKIP, recommendation.getAction());
    }

    private static java.util.List<ShopRecommendation> recommendations(OwnedHeroState hero, java.util.List<ShopItemState> items, int currency) {
        HonorDuelShopAnalysis analysis = new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP, new HonorDuelHeader(currency, 1, 9, null), items,
                Collections.emptyList(), 1f, Collections.emptyList(), null, null, Collections.emptyList(), Collections.singletonList(hero));
        return new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommend(analysis);
    }

    private static ShopItemState offer(int slot, String heroId, int quantity, int price) {
        return new ShopItemState(slot, quantity > 1 ? ShopItemType.HERO_BUNDLE : ShopItemType.HERO, price, null, .95f,
                null, Collections.emptyList(), heroId, heroId, "레오프론", HeroRecognitionStatus.CONFIRMED,
                quantity, HeroRarity.EPIC, false, null, null, null);
    }

    private static OwnedHeroState hero(String id, String name, int progress, boolean maxRank) {
        return new OwnedHeroState(0, id, name, "레오프론", HeroRarity.LEGENDARY,
                new PromotionGaugeObservation(progress, 4, maxRank, .95f, Collections.emptyList()), null, null, .95f);
    }
}
