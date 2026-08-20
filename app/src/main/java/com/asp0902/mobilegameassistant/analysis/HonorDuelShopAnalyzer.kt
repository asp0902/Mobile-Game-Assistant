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
        val screenType = if (allText.contains("결투 상점")) ScreenType.HONOR_DUEL_SHOP else ScreenType.OTHER
        val header = HonorDuelHeader(
            currency = parseCurrency(blocks),
            shopLevel = Regex("결투\\s*상점\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull(),
            targetWins = Regex("목표\\s*:?\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull(),
            artifactXp = Regex("(\\d+)\\s*/\\s*(\\d+)").find(allText)?.let {
                ArtifactXp(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            },
        )
        return HonorDuelShopAnalysis(
            screenType = screenType,
            header = header,
            shopItems = if (screenType == ScreenType.HONOR_DUEL_SHOP) extractSlots(blocks) else emptyList(),
            ocrBlocks = blocks,
        )
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

enum class ScreenType { HONOR_DUEL_SHOP, OTHER }

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

data class HonorDuelShopAnalysis(
    val screenType: ScreenType,
    val header: HonorDuelHeader,
    val shopItems: List<ShopItemState>,
    val ocrBlocks: List<OcrBlock>,
)
