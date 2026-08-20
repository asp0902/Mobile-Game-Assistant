package com.asp0902.mobilegameassistant.analysis

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HonorDuelShopAnalyzer @Inject constructor() {
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    fun analyze(bitmap: Bitmap): HonorDuelShopAnalysis {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val blocks = result.textBlocks.mapNotNull { block ->
            block.boundingBox?.let { box ->
                OcrBlock(
                    text = block.text.replace("\\s+".toRegex(), " ").trim(),
                    left = box.left.toFloat() / bitmap.width,
                    top = box.top.toFloat() / bitmap.height,
                    right = box.right.toFloat() / bitmap.width,
                    bottom = box.bottom.toFloat() / bitmap.height,
                )
            }
        }
        val allText = blocks.joinToString(" ") { it.text }
        val shopLevel = Regex("결투\\s*상점\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val shopScreen = HonorDuelScreenClassifier.classify(
            hasShopTitle = allText.contains("결투 상점"),
            hasPartialShopTitle = allText.contains("결투") || allText.contains("상점"),
            shopLevel = shopLevel,
            visibleSlotCount = countVisibleSlots(blocks),
        )
        val screen = if (shopScreen.type == ScreenType.HONOR_DUEL_SHOP) {
            shopScreen
        } else {
            HonorDuelScreenClassifier.classifyNonShop(allText, shopScreen)
        }
        val header = HonorDuelHeader(
            currency = parseCurrency(blocks),
            shopLevel = shopLevel,
            targetWins = Regex("목표\\s*:?\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull(),
            artifactXp = Regex("(\\d+)\\s*/\\s*(\\d+)").find(allText)?.let {
                ArtifactXp(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            },
        )
        return HonorDuelShopAnalysis(
            screenType = screen.type,
            screenConfidence = screen.confidence,
            screenReasons = screen.reasons,
            header = header,
            shopItems = if (screen.type == ScreenType.HONOR_DUEL_SHOP) extractSlots(blocks) else emptyList(),
            ocrBlocks = blocks,
        )
    }

    private fun countVisibleSlots(blocks: List<OcrBlock>): Int = SHOP_SLOT_BOUNDS.count { bounds ->
        blocks.any { bounds.contains(it.centerX, it.centerY) }
    }

    private fun parseCurrency(blocks: List<OcrBlock>): Int? = blocks
        .filter { it.centerX > 0.72f && it.centerY < 0.32f }
        .mapNotNull { Regex("\\d+").find(it.text)?.value?.toIntOrNull() }
        .maxOrNull()

    private fun extractSlots(blocks: List<OcrBlock>): List<ShopItemState> = SHOP_SLOT_BOUNDS.mapIndexed { index, bounds ->
        val slotBlocks = blocks.filter { bounds.contains(it.centerX, it.centerY) }
        val text = slotBlocks.joinToString(" ") { it.text }
        val isSoldOut = text.contains("품절")
        val xpAmount = Regex("\\+(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val price = slotBlocks
            .filter { it.centerY > bounds.priceTop }
            .flatMap { Regex("\\d+").findAll(it.text).map { match -> match.value.toInt() }.toList() }
            .lastOrNull()
        val type = when {
            isSoldOut -> ShopItemType.SOLD_OUT
            xpAmount != null -> ShopItemType.ARTIFACT_XP
            else -> ShopItemType.UNKNOWN
        }
        ShopItemState(
            slotIndex = index,
            itemType = type,
            price = price,
            artifactXpAmount = if (type == ShopItemType.ARTIFACT_XP) xpAmount else null,
            confidence = when (type) {
                ShopItemType.ARTIFACT_XP -> 0.95f
                ShopItemType.SOLD_OUT -> 0.95f
                ShopItemType.UNKNOWN -> if (price != null) 0.55f else 0.1f
            },
        )
    }

    private data class SlotBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val priceTop: Float,
    ) {
        fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    }

    private companion object {
        // ponytail: fixed 4x2 Honor Duel shop grid. Add viewport detection when real-device samples prove variation.
        val SHOP_SLOT_BOUNDS = listOf(
            SlotBounds(.03f, .28f, .25f, .47f, .38f),
            SlotBounds(.26f, .28f, .49f, .47f, .38f),
            SlotBounds(.51f, .28f, .74f, .47f, .38f),
            SlotBounds(.75f, .28f, .98f, .47f, .38f),
            SlotBounds(.03f, .47f, .25f, .67f, .58f),
            SlotBounds(.26f, .47f, .49f, .67f, .58f),
            SlotBounds(.51f, .47f, .74f, .67f, .58f),
            SlotBounds(.75f, .47f, .98f, .67f, .58f),
        )
    }
}

enum class ScreenType {
    HONOR_DUEL_SHOP,
    HONOR_DUEL_HERO_MANAGEMENT,
    HONOR_DUEL_HERO_SELL,
    HONOR_DUEL_BATTLE_DEPLOYMENT,
    HONOR_DUEL_BATTLE_RESULT,
    HERO_DETAIL_POPUP,
    EQUIPMENT_DETAIL_POPUP,
    OTHER,
    UNKNOWN,
}

data class ScreenClassification(
    val type: ScreenType,
    val confidence: Float,
    val reasons: List<String>,
)

object HonorDuelScreenClassifier {
    private val factions = listOf("레오프론", "와일더스", "그레이브본", "트라이브", "기타")
    private val roles = listOf("전사", "탱커", "사수", "마법사", "서포터", "레인저")
    private val knownEquipment = listOf("간이 활", "밀림 후드", "생엽", "침묵의 투구")

    fun classify(
        hasShopTitle: Boolean,
        hasPartialShopTitle: Boolean,
        shopLevel: Int?,
        visibleSlotCount: Int,
    ): ScreenClassification {
        if (hasShopTitle && shopLevel != null && visibleSlotCount >= 4) {
            return ScreenClassification(
                ScreenType.HONOR_DUEL_SHOP,
                0.95f,
                listOf("결투 상점 제목", "상점 레벨", "상품 슬롯 ${visibleSlotCount}개"),
            )
        }
        if (hasShopTitle && visibleSlotCount >= 2) {
            return ScreenClassification(
                ScreenType.HONOR_DUEL_SHOP,
                0.75f,
                listOf("결투 상점 제목", "상품 슬롯 ${visibleSlotCount}개"),
            )
        }
        if (hasPartialShopTitle || shopLevel != null || visibleSlotCount >= 4) {
            return ScreenClassification(ScreenType.UNKNOWN, 0.5f, listOf("상점 증거 불충분"))
        }
        return ScreenClassification(ScreenType.OTHER, 0.9f, listOf("상점 증거 없음"))
    }

    fun classifyNonShop(text: String, shopScreen: ScreenClassification): ScreenClassification {
        val heroDetail = factions.any(text::contains) && roles.any(text::contains) && text.contains("사정거리")
        if (heroDetail) {
            return ScreenClassification(ScreenType.HERO_DETAIL_POPUP, 0.95f, listOf("진영", "직업", "사정거리"))
        }
        if (knownEquipment.any(text::contains)) {
            return ScreenClassification(ScreenType.EQUIPMENT_DETAIL_POPUP, 0.9f, listOf("확정 장비명"))
        }
        if (Regex("확인\\s*\\(\\s*\\d+초\\s*\\)").containsMatchIn(text)) {
            return ScreenClassification(ScreenType.HONOR_DUEL_BATTLE_DEPLOYMENT, 0.9f, listOf("배치 확인 타이머"))
        }
        if (text.contains("전투 승리") || text.contains("전투 패배") || text.contains("명예의 결투 종료")) {
            return ScreenClassification(ScreenType.HONOR_DUEL_BATTLE_RESULT, 0.9f, listOf("전투 결과 문구"))
        }
        if (text.contains("판매가") || text.contains("영웅 판매")) {
            return ScreenClassification(ScreenType.HONOR_DUEL_HERO_SELL, 0.9f, listOf("영웅 판매 문구"))
        }
        if (text.contains("보유 영웅") || text.contains("진형 관리")) {
            return ScreenClassification(ScreenType.HONOR_DUEL_HERO_MANAGEMENT, 0.75f, listOf("영웅 관리 문구"))
        }
        return shopScreen
    }
}

enum class ShopItemType { ARTIFACT_XP, SOLD_OUT, UNKNOWN }

data class ArtifactXp(val current: Int, val required: Int)

data class HonorDuelHeader(
    val currency: Int?,
    val shopLevel: Int?,
    val targetWins: Int?,
    val artifactXp: ArtifactXp?,
)

data class ShopItemState(
    val slotIndex: Int,
    val itemType: ShopItemType,
    val price: Int?,
    val artifactXpAmount: Int?,
    val confidence: Float,
)

data class OcrBlock(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

data class HonorDuelShopAnalysis @JvmOverloads constructor(
    val screenType: ScreenType,
    val header: HonorDuelHeader,
    val shopItems: List<ShopItemState>,
    val ocrBlocks: List<OcrBlock>,
    val screenConfidence: Float = if (screenType == ScreenType.HONOR_DUEL_SHOP) 1f else 0.9f,
    val screenReasons: List<String> = emptyList(),
)
