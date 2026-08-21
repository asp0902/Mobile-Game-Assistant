package com.asp0902.mobilegameassistant.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.ScreenType
import com.asp0902.mobilegameassistant.analysis.ShopItemType
import javax.inject.Inject

class HonorDuelRuleEngine private constructor(
    private val midasPolicyProvider: () -> MidasGoldenPolicy,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ MidasGoldenPolicy.fromAssets(context) })

    constructor(midasPolicy: MidasGoldenPolicy) : this({ midasPolicy })

    fun recommend(analysis: HonorDuelShopAnalysis): List<ShopRecommendation> {
        if (analysis.screenType != ScreenType.HONOR_DUEL_SHOP) return emptyList()
        val currency = analysis.header.currency
        val xp = analysis.header.artifactXp
        val midasPolicy = midasPolicyProvider()
        val isMidas = analysis.header.artifactName == midasPolicy.artifactName
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
                ShopItemType.EQUIPMENT,
                ShopItemType.HERO,
                ShopItemType.HERO_BUNDLE,
                ShopItemType.RANDOM_HERO_PACK,
                ShopItemType.FACTION_HERO_PACK,
                ShopItemType.RANDOM_HERO_UPGRADE,
                ShopItemType.UNKNOWN -> ShopRecommendation(
                    item.slotIndex,
                    RecommendationAction.CONSIDER,
                    "상품 유형 미확정 — 상세 확인 필요",
                )
                ShopItemType.TRIAL_HERO_CARD -> when {
                    !isMidas -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "체험 카드 상세 확인 필요")
                    item.confidence < midasPolicy.trialMinimumConfidence -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "체험 카드 인식 신뢰도 확인 필요")
                    else -> ShopRecommendation(item.slotIndex, RecommendationAction.CONSIDER, "마이다스 체험 카드 시너지 — 상세 후 구매 판단")
                }
            }
        }
    }
}

enum class RecommendationAction { BUY, CONSIDER, SKIP }

data class ShopRecommendation(
    val slotIndex: Int,
    val action: RecommendationAction,
    val reason: String,
)
