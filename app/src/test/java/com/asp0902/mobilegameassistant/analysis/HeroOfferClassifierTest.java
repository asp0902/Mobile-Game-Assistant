package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.junit.Test;

public class HeroOfferClassifierTest {
    @Test
    public void confirmsLegendaryTrialOnlyWithAllKnownSignals() {
        HeroOfferClassification result = HeroOfferClassifier.INSTANCE.classify(
                "체험 카드", 12, new SlotVisualEvidence(0f, true, .12f, true, true));

        assertEquals(ShopItemType.TRIAL_HERO_CARD, result.getType());
        assertEquals(HeroRarity.LEGENDARY, result.getRarity());
        assertTrue(result.isTrialCard());
    }

    @Test
    public void priceTwelveAloneDoesNotConfirmLegendaryTrial() {
        HeroOfferClassification result = HeroOfferClassifier.INSTANCE.classify(
                "", 12, new SlotVisualEvidence(0f, true, 0f, false, false));

        assertEquals(HeroRarity.UNKNOWN, result.getRarity());
        assertFalse(result.isTrialCard());
    }

    @Test
    public void recognizesBundleQuantityAndPopupOverridesCardInference() {
        HeroOfferClassification offer = HeroOfferClassifier.INSTANCE.classify(
                "발렌 2장", 30, new SlotVisualEvidence(0f, true, 0f, false, false));
        assertEquals(ShopItemType.HERO_BUNDLE, offer.getType());
        assertEquals(Integer.valueOf(2), offer.getQuantity());

        ShopItemState card = new ShopItemState(0, ShopItemType.HERO, 6, null, .5f);
        HeroDetailPopup popup = new HeroDetailPopup("매혹의 세이렌", HeroRarity.EPIC, true, "밀림 후드", .95f);
        ShopItemState merged = ShopDetailReconciler.INSTANCE.apply(Collections.singletonList(card), 0, popup).get(0);
        assertEquals(ShopItemType.TRIAL_HERO_CARD, merged.getItemType());
        assertEquals("매혹의 세이렌", merged.getHeroName());
        assertEquals(HeroRarity.EPIC, merged.getHeroRarity());
    }
}
