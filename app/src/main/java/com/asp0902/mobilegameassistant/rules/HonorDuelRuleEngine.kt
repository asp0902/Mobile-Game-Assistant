package com.asp0902.mobilegameassistant.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.OwnedHeroState
import com.asp0902.mobilegameassistant.analysis.ScreenType
import com.asp0902.mobilegameassistant.analysis.ShopItemState
import com.asp0902.mobilegameassistant.analysis.ShopItemType
import javax.inject.Inject

class HonorDuelRuleEngine private constructor(
    private val midasPolicyProvider: () -> MidasGoldenPolicy,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ MidasGoldenPolicy.fromAssets(context) })

    constructor(midasPolicy: MidasGoldenPolicy) : this({ midasPolicy })

    @JvmOverloads
    fun spendableState(
        analysis: HonorDuelShopAnalysis,
        purchaseCost: Int? = null,
        reservedPurchaseBudget: Int = DEFAULT_RESERVED_PURCHASE_BUDGET,
    ): SpendableState = HonorDuelEconomy.state(analysis, purchaseCost, reservedPurchaseBudget)

    fun recommend(analysis: HonorDuelShopAnalysis): List<ShopRecommendation> {
        if (analysis.screenType != ScreenType.HONOR_DUEL_SHOP) return emptyList()
        val currency = analysis.header.currency
        val xp = analysis.header.artifactXp
        val midasPolicy = midasPolicyProvider()
        val isMidas = analysis.header.artifactName == midasPolicy.artifactName
        val promotionOffers = promotionOffers(analysis, currency)
        return analysis.shopItems.map { item ->
            when (item.itemType) {
                ShopItemType.SOLD_OUT -> ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "품절")
                ShopItemType.ARTIFACT_XP -> {
                    val completesThreshold = xp != null && item.artifactXpAmount != null &&
                        xp.current + item.artifactXpAmount >= xp.required
                    when {
                        item.price == null || currency == null ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "재화 인식 확인 필요")
                        currency < item.price ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "현재 휘장 부족")
                        completesThreshold ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.BUY, "아티팩트 해금 임계치 도달")
                        isMidas && (item.artifactXpAmount ?: 0) >= midasPolicy.minimumXpAmount ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.BUY, "마이다스 EXP 성장 — ${item.artifactXpAmount} EXP 확보")
                        else ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "해금 임계치와 거리가 있음")
                    }
                }
                ShopItemType.EQUIPMENT -> equipmentRecommendation(item, analysis)
                ShopItemType.RANDOM_HERO_PACK,
                ShopItemType.FACTION_HERO_PACK,
                ShopItemType.RANDOM_HERO_UPGRADE,
                ShopItemType.UNKNOWN -> ShopRecommendation(
                    item.slotIndex,
                    RecommendationAction.CONSIDER,
                    "상품 유형 미확정 — 상세 확인 필요",
                )
                ShopItemType.HERO,
                ShopItemType.HERO_BUNDLE -> promotionOffers[item.slotIndex] ?: ShopRecommendation(
                    item.slotIndex,
                    RecommendationAction.CONSIDER,
                    "영웅·진급 게이지 확인 필요",
                )
                ShopItemType.TRIAL_HERO_CARD -> when {
                    !isMidas -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "체험 카드 상세 확인 필요")
                    item.confidence < midasPolicy.trialMinimumConfidence -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "체험 카드 인식 신뢰도 확인 필요")
                    else -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "마이다스 체험 카드 시너지 — 상세 후 구매 판단")
                }
            }
        }
    }

    private fun equipmentRecommendation(item: ShopItemState, analysis: HonorDuelShopAnalysis): ShopRecommendation {
        val price = item.price ?: return ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "장비 가격 확인 필요")
        val currency = analysis.header.currency
        val state = spendableState(analysis, price)
        val afterPurchase = state.currencyAfterPurchase
        return when {
            currency == null -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "현재 휘장 확인 필요")
            currency < price -> ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "현재 휘장 부족 — 장비 회수액 0")
            afterPurchase != null && afterPurchase < state.reservedPurchaseBudget ->
                ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "장비 판매 불가 — 구매 후 $afterPurchase, 보존 휘장 ${state.reservedPurchaseBudget} 미만")
            else -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "장비 판매 불가 — 비용 $price 전액 비가역 지출")
        }
    }

    private fun promotionOffers(
        analysis: HonorDuelShopAnalysis,
        currency: Int?,
    ): Map<Int, ShopRecommendation> {
        val offers = analysis.shopItems.filter {
            it.itemType in setOf(ShopItemType.HERO, ShopItemType.HERO_BUNDLE) && it.heroId != null
        }.groupBy { it.heroId!! }
        return offers.flatMap { (heroId, items) ->
            val hero = analysis.ownedHeroes.firstOrNull { it.heroId == heroId }
            promotionRecommendation(hero, items, currency).let { recommendation ->
                items.map { it.slotIndex to recommendation.copy(slotIndex = it.slotIndex) }
            }
        }.toMap()
    }

    private fun promotionRecommendation(
        hero: OwnedHeroState?,
        items: List<ShopItemState>,
        currency: Int?,
    ): ShopRecommendation {
        val first = items.first()
        val gauge = hero?.promotion
        if (hero == null || gauge?.progress == null || gauge.required == null) {
            return ShopRecommendation(first.slotIndex, RecommendationAction.CONSIDER, "보유 영웅 진급 게이지 확인 필요")
        }
        if (gauge.isMaxRank) return ShopRecommendation(first.slotIndex, RecommendationAction.SKIP, "최대 등급 — 중복 진급 가치 0")

        val quantity = items.sumOf { it.quantity ?: 1 }
        val next = (gauge.progress + quantity).coerceAtMost(gauge.required)
        val totalCost = items.sumOf { it.price ?: 0 }
        val allPricesKnown = items.all { it.price != null }
        val completesPromotion = next == gauge.required
        return when {
            !allPricesKnown || currency == null -> ShopRecommendation(first.slotIndex, RecommendationAction.CONSIDER, "진급 조합 재화 확인 필요")
            completesPromotion && currency < totalCost -> ShopRecommendation(first.slotIndex, RecommendationAction.CONSIDER, "진급 가능 — 필요 휘장 $totalCost")
            completesPromotion -> ShopRecommendation(first.slotIndex, RecommendationAction.BUY, promotionReason(hero, gauge.required, next, items.size > 1))
            currency < first.price!! -> ShopRecommendation(first.slotIndex, RecommendationAction.SKIP, "현재 휘장 부족")
            else -> ShopRecommendation(first.slotIndex, RecommendationAction.CONSIDER, "미래 진급 가치 — ${hero.heroName ?: hero.heroId} ${gauge.progress}/${gauge.required} → $next/${gauge.required}")
        }
    }

    private fun promotionReason(hero: OwnedHeroState, required: Int, next: Int, isBundle: Boolean): String {
        val heroName = hero.heroName ?: hero.heroId ?: "영웅"
        val knownMaxAfterPromotion = required == 4 && hero.heroId in setOf("valen", "bonnie")
        return if (knownMaxAfterPromotion) {
            "${if (isBundle) "BUY BOTH — " else ""}$heroName $next/$required → 신화 → 최대 등급"
        } else {
            "${if (isBundle) "BUY BOTH — " else ""}$heroName $next/$required → 진급"
        }
    }

    private companion object {
        const val DEFAULT_RESERVED_PURCHASE_BUDGET = 30
    }
}

data class SellCandidate(
    val heroId: String,
    val heroName: String,
    val expectedCurrency: Int,
)

data class SpendableState(
    val currentCurrency: Int?,
    val sellableReserve: Int,
    val reservedPurchaseBudget: Int,
    val purchaseCost: Int? = null,
    val irreversibleSpend: Int = 0,
    val sellCandidates: List<SellCandidate> = emptyList(),
) {
    val liquidPotential: Int? get() = currentCurrency?.plus(sellableReserve)
    val currencyAfterPurchase: Int? get() = currentCurrency?.minus(purchaseCost ?: 0)
}

object HonorDuelEconomy {
    private val coreHeroIds = setOf("valen", "bonnie")

    fun state(
        analysis: HonorDuelShopAnalysis,
        purchaseCost: Int? = null,
        reservedPurchaseBudget: Int = 30,
    ): SpendableState {
        val candidates = analysis.ownedHeroes.mapNotNull { hero ->
            val sellValue = hero.sellValue ?: return@mapNotNull null
            val heroId = hero.heroId ?: return@mapNotNull null
            if (!hero.isSellCandidate || heroId in coreHeroIds || hero.promotion.isMaxRank || hero.equipmentName != null) return@mapNotNull null
            SellCandidate(heroId, hero.heroName ?: heroId, sellValue)
        }
        return SpendableState(
            currentCurrency = analysis.header.currency,
            sellableReserve = candidates.sumOf(SellCandidate::expectedCurrency),
            reservedPurchaseBudget = reservedPurchaseBudget,
            purchaseCost = purchaseCost,
            irreversibleSpend = if (purchaseCost == null) 0 else purchaseCost,
            sellCandidates = candidates,
        )
    }
}

enum class RecommendationAction { BUY, CONSIDER, SKIP }

data class ShopRecommendation(
    val slotIndex: Int,
    val action: RecommendationAction,
    val reason: String,
)
