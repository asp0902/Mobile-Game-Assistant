package com.asp0902.mobilegameassistant.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.asp0902.mobilegameassistant.analysis.HeroRarity;
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader;
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis;
import com.asp0902.mobilegameassistant.analysis.OwnedHeroState;
import com.asp0902.mobilegameassistant.analysis.PromotionGaugeObservation;
import com.asp0902.mobilegameassistant.analysis.ScreenType;
import java.util.Collections;
import org.junit.Test;

public class PurchasePredictorTest {
    @Test
    public void valenTwoOfFourPlusOnePredictsThreeOfFourAndCurrency() {
        PurchasePrediction result = PurchasePredictor.INSTANCE.apply(analysis("valen", 2), new HeroPurchaseAction("a", "valen", 1, 15));

        assertEquals(Integer.valueOf(45), result.getAnalysis().getHeader().getCurrency());
        assertEquals(Integer.valueOf(3), result.getExpectedProgress().getProgress());
        assertEquals(Integer.valueOf(4), result.getExpectedProgress().getRequired());
    }

    @Test
    public void bonnieOnePlusTwoReachesFourOfFourAndHighConfidenceObservationWinsConflict() {
        PurchasePrediction first = PurchasePredictor.INSTANCE.apply(analysis("bonnie", 1), new HeroPurchaseAction("a", "bonnie", 1, 15));
        PurchasePrediction second = PurchasePredictor.INSTANCE.apply(first.getAnalysis(), new HeroPurchaseAction("b", "bonnie", 2, 30));
        OwnedHeroState observed = hero("bonnie", 3, .95f);

        assertEquals(Integer.valueOf(4), second.getExpectedProgress().getProgress());
        assertTrue(PurchasePredictor.INSTANCE.conflicts(observed, second.getExpectedProgress()));
        assertFalse(PurchasePredictor.INSTANCE.conflicts(hero("bonnie", 4, .95f), second.getExpectedProgress()));
    }

    private static HonorDuelShopAnalysis analysis(String id, int progress) {
        return new HonorDuelShopAnalysis(
                ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(60, 1, 9, null),
                Collections.emptyList(),
                Collections.emptyList(),
                1f, Collections.emptyList(), null, null, Collections.emptyList(),
                Collections.singletonList(hero(id, progress, .9f)));
    }

    private static OwnedHeroState hero(String id, int progress, float confidence) {
        return new OwnedHeroState(0, id, id, "레오프론", HeroRarity.LEGENDARY,
                new PromotionGaugeObservation(progress, 4, false, confidence, Collections.emptyList()),
                null, null, confidence);
    }
}
