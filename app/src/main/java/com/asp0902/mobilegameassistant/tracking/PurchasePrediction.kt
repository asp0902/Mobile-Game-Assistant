package com.asp0902.mobilegameassistant.tracking

import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.OwnedHeroState
import com.asp0902.mobilegameassistant.analysis.PromotionGaugeObservation

data class HeroPurchaseAction(
    val actionId: String,
    val heroId: String,
    val quantity: Int,
    val cost: Int,
)

data class PurchasePrediction(
    val analysis: HonorDuelShopAnalysis,
    val expectedProgress: PromotionGaugeObservation?,
)

object PurchasePredictor {
    fun apply(analysis: HonorDuelShopAnalysis, action: HeroPurchaseAction): PurchasePrediction {
        val owned = analysis.ownedHeroes.map { hero ->
            if (hero.heroId != action.heroId || hero.promotion.isMaxRank) hero else hero.copy(
                promotion = progressAfter(hero.promotion, action.quantity),
            )
        }
        val expected = owned.firstOrNull { it.heroId == action.heroId }?.promotion
        return PurchasePrediction(
            analysis.copy(header = analysis.header.copy(currency = analysis.header.currency?.minus(action.cost)), ownedHeroes = owned),
            expected,
        )
    }

    fun progressAfter(current: PromotionGaugeObservation, quantity: Int): PromotionGaugeObservation {
        val required = current.required ?: return current
        val progress = current.progress ?: return current
        return current.copy(progress = (progress + quantity).coerceAtMost(required), confidence = .75f, reasons = current.reasons + "구매 예상 +$quantity")
    }

    fun conflicts(observed: OwnedHeroState?, expected: PromotionGaugeObservation?): Boolean =
        observed != null && expected != null && observed.confidence >= .9f &&
            observed.promotion.progress != null && expected.progress != null && observed.promotion.progress != expected.progress
}
