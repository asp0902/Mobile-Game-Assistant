package com.asp0902.mobilegameassistant.rules;

import static org.junit.Assert.assertEquals;

import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader;
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis;
import com.asp0902.mobilegameassistant.analysis.ScreenType;
import com.asp0902.mobilegameassistant.analysis.ShopItemState;
import com.asp0902.mobilegameassistant.analysis.ShopItemType;
import com.asp0902.mobilegameassistant.tracking.RunStatus;
import java.util.Collections;
import org.junit.Test;

public class HonorDuelRunRecommendationTest {
    @Test public void stopsWhenThreeCurrencyCannotFundFuturePurchase() {
        HonorDuelShopAnalysis analysis = analysis(3, 5, 9, 3, new ShopItemState(0, ShopItemType.HERO, 15, null, .95f));
        assertEquals(RunRecommendationAction.STOP_REFRESHING,
                new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommendRunActions(analysis).get(0).getAction());
    }

    @Test public void refreshesEarlyWhenBudgetAllowsPurchaseAfterRefresh() {
        HonorDuelShopAnalysis analysis = analysis(60, 2, 9, 3, new ShopItemState(0, ShopItemType.UNKNOWN, 15, null, .95f));
        assertEquals(RunRecommendationAction.REFRESH,
                new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommendRunActions(analysis).get(0).getAction());
    }

    @Test public void stopsAfterCompletion() {
        HonorDuelShopAnalysis analysis = analysis(60, 9, 9, 3, new ShopItemState(0, ShopItemType.UNKNOWN, 15, null, .95f));
        assertEquals(RunRecommendationAction.STOP_REFRESHING,
                new HonorDuelRuleEngine(new MidasGoldenPolicy()).recommendRunActions(analysis, RunStatus.COMPLETED).get(0).getAction());
    }

    private static HonorDuelShopAnalysis analysis(int currency, int wins, int target, int refresh, ShopItemState item) {
        return new HonorDuelShopAnalysis(ScreenType.HONOR_DUEL_SHOP,
                new HonorDuelHeader(currency, 1, target, null, wins, null, refresh, null, null, Collections.emptyMap()),
                Collections.singletonList(item), Collections.emptyList());
    }
}
