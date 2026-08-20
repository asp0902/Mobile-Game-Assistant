package com.asp0902.mobilegameassistant.rules

import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis
import com.asp0902.mobilegameassistant.analysis.ScreenType
import com.asp0902.mobilegameassistant.analysis.ShopItemType
import javax.inject.Inject

class HonorDuelRuleEngine @Inject constructor() {
    fun recommend(analysis: HonorDuelShopAnalysis): List<ShopRecommendation> {
        if (analysis.screenType != ScreenType.HONOR_DUEL_SHOP) return emptyList()
        val currency = analysis.header.currency
        val xp = analysis.header.artifactXp
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
                        else ->
                            ShopRecommendation(item.slotIndex, RecommendationAction.SKIP, "해금 임계치와 거리가 있음")
                    }
                }
                ShopItemType.UNKNOWN -> ShopRecommendation(
                    item.slotIndex,
                    RecommendationAction.CONSIDER,
                    "상품 유형 미확정 — 상세 확인 필요",
                )
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
