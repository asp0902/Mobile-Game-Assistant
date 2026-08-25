package com.asp0902.mobilegameassistant.analysis

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HonorDuelShopAnalyzer @Inject constructor(
    private val heroCatalog: HeroRecognitionCatalog,
    private val correctionRepository: HeroCorrectionRepository,
) {
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    fun analyze(bitmap: Bitmap, runId: Long? = null): HonorDuelShopAnalysis {
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
        val viewport = GameViewportDetector.detect(bitmap)
        val shopLevel = Regex("결투\\s*상점\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val shopScreen = HonorDuelScreenClassifier.classify(
            hasShopTitle = allText.contains("결투 상점"),
            hasPartialShopTitle = allText.contains("결투") || allText.contains("상점"),
            shopLevel = shopLevel,
            visibleSlotCount = countVisibleSlots(blocks, viewport),
        )
        val screen = if (shopScreen.type == ScreenType.HONOR_DUEL_SHOP) {
            shopScreen
        } else {
            HonorDuelScreenClassifier.classifyNonShop(allText, shopScreen)
        }
        val header = HonorDuelHeaderParser.parse(blocks, allText, shopLevel)
        val rosterGauges = if (screen.type in OWNED_HERO_SCREENS) extractRosterGauges(bitmap, blocks, viewport) else emptyList()
        return HonorDuelShopAnalysis(
            screenType = screen.type,
            screenConfidence = screen.confidence,
            screenReasons = screen.reasons,
            header = header,
            shopItems = if (screen.type == ScreenType.HONOR_DUEL_SHOP) extractSlots(bitmap, blocks, viewport, runId) else emptyList(),
            ownedHeroGauges = rosterGauges,
            ownedHeroes = if (screen.type in OWNED_HERO_SCREENS) extractOwnedHeroes(bitmap, blocks, viewport, rosterGauges, runId) else emptyList(),
            ocrBlocks = blocks,
            viewport = viewport,
            heroDetail = if (screen.type == ScreenType.HERO_DETAIL_POPUP) HeroDetailPopupParser.parse(allText) else null,
        )
    }

    private fun countVisibleSlots(blocks: List<OcrBlock>, viewport: GameViewport): Int = SHOP_SLOT_BOUNDS.count { bounds ->
        val frameBounds = viewport.toFrame(bounds)
        blocks.any { frameBounds.contains(it.centerX, it.centerY) }
    }

    private fun extractSlots(
        bitmap: Bitmap,
        blocks: List<OcrBlock>,
        viewport: GameViewport,
        runId: Long?,
    ): List<ShopItemState> = SHOP_SLOT_BOUNDS.mapIndexed { index, localBounds ->
        val bounds = viewport.toFrame(localBounds)
        val priceTop = viewport.top + (viewport.bottom - viewport.top) * localBounds.priceTop
        val slotBlocks = blocks.filter { bounds.contains(it.centerX, it.centerY) }
        val text = slotBlocks.joinToString(" ") { it.text }
        val price = slotBlocks
            .filter { it.centerY > priceTop }
            .flatMap { Regex("\\d+").findAll(it.text).map { match -> match.value.toInt() }.toList() }
            .lastOrNull()
        val visual = SlotVisualEvidence.from(bitmap, bounds, priceTop)
        val classification = NonHeroShopItemClassifier.classify(
            text,
            visual,
        )
        val hero = if (classification.type == ShopItemType.UNKNOWN && visual.likelyHeroPortrait) {
            heroCatalog.recognize(bitmap, bounds, text, runId?.let(correctionRepository::forRun).orEmpty())
        } else {
            HeroRecognitionResult()
        }
        val offer = if (classification.type == ShopItemType.UNKNOWN && visual.likelyHeroPortrait) {
            HeroOfferClassifier.classify(text, price, visual)
        } else {
            HeroOfferClassification()
        }
        ShopItemState(
            slotIndex = index,
            itemType = if (offer.type == ShopItemType.UNKNOWN) classification.type else offer.type,
            price = price,
            artifactXpAmount = classification.artifactXpAmount,
            confidence = if (hero.status == HeroRecognitionStatus.UNKNOWN) classification.confidence else hero.confidence,
            bounds = bounds,
            classificationReasons = classification.reasons + hero.reasons + offer.reasons,
            heroId = hero.heroId,
            heroName = hero.koreanName,
            faction = hero.faction,
            heroRecognitionStatus = hero.status,
            quantity = offer.quantity,
            heroRarity = offer.rarity,
            isTrialCard = offer.isTrialCard,
            equipmentName = offer.equipmentName,
            portraitSignature = if (visual.likelyHeroPortrait) heroCatalog.portraitSignature(bitmap, bounds) else null,
        )
    }

    private fun extractRosterGauges(
        bitmap: Bitmap,
        blocks: List<OcrBlock>,
        viewport: GameViewport,
    ): List<OwnedHeroGaugeSlot> = ROSTER_SLOT_BOUNDS.mapIndexed { index, localBounds ->
        val bounds = viewport.toFrame(localBounds)
        val slotText = blocks.filter { bounds.contains(it.centerX, it.centerY) }.joinToString(" ") { it.text }
        OwnedHeroGaugeSlot(index, bounds, PromotionGaugeRecognizer.recognize(bitmap, bounds, slotText))
    }

    private fun extractOwnedHeroes(
        bitmap: Bitmap,
        blocks: List<OcrBlock>,
        viewport: GameViewport,
        gauges: List<OwnedHeroGaugeSlot>,
        runId: Long?,
    ): List<OwnedHeroState> = gauges.map { gaugeSlot ->
        val text = blocks.filter { gaugeSlot.bounds.contains(it.centerX, it.centerY) }.joinToString(" ") { it.text }
        val hero = heroCatalog.recognize(bitmap, gaugeSlot.bounds, text, runId?.let(correctionRepository::forRun).orEmpty())
        OwnedHeroRecognizer.state(gaugeSlot.slotIndex, text, hero, gaugeSlot.gauge)
    }

    data class SlotBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val priceTop: Float,
    ) {
        fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    }

    private companion object {
        val OWNED_HERO_SCREENS = setOf(
            ScreenType.HONOR_DUEL_SHOP,
            ScreenType.HONOR_DUEL_HERO_MANAGEMENT,
            ScreenType.HONOR_DUEL_HERO_SELL,
        )
        // ponytail: fixed 4x2 shop grid. Add anchor-based slot detection when viewport crop varies.
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
        // ponytail: six visible first-row roster cards. Add second-row anchors with a matching Golden sample.
        val ROSTER_SLOT_BOUNDS = (0 until 6).map { index ->
            val left = .04f + index * .155f
            SlotBounds(left, .75f, left + .14f, .92f, .9f)
        }
    }
}

enum class ScreenType {
    ARTISANS_PATH_CARD_SELECTION,
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
        if (text.contains("장인의 길")) {
            return ScreenClassification(ScreenType.ARTISANS_PATH_CARD_SELECTION, 0.95f, listOf("장인의 길 제목"))
        }
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

enum class ShopItemType {
    ARTIFACT_XP,
    HERO,
    HERO_BUNDLE,
    TRIAL_HERO_CARD,
    EQUIPMENT,
    RANDOM_HERO_PACK,
    FACTION_HERO_PACK,
    RANDOM_HERO_UPGRADE,
    SOLD_OUT,
    UNKNOWN,
}

data class ArtifactXp(val current: Int, val required: Int)

enum class HeaderField {
    WINS,
    TARGET_WINS,
    HP,
    CURRENCY,
    SHOP_LEVEL,
    REFRESH_COST,
    ROUND,
    ARTIFACT,
    ARTIFACT_XP,
}

data class HonorDuelHeader @JvmOverloads constructor(
    val currency: Int?,
    val shopLevel: Int?,
    val targetWins: Int?,
    val artifactXp: ArtifactXp?,
    val wins: Int? = null,
    val hp: Int? = null,
    val refreshCost: Int? = null,
    val currentRound: Int? = null,
    val artifactName: String? = null,
    val confidence: Map<HeaderField, Float> = emptyMap(),
)

object HonorDuelHeaderParser {
    fun parse(blocks: List<OcrBlock>, allText: String, shopLevel: Int?): HonorDuelHeader {
        val currency = blocks
            .filter { it.centerX > .72f && it.centerY < .22f }
            .mapNotNull { Regex("\\d+").find(it.text)?.value?.toIntOrNull() }
            .maxOrNull()
        val refreshCost = blocks
            .filter { it.centerX > .72f && it.centerY in .22f.. .34f }
            .mapNotNull { Regex("\\d+").find(it.text)?.value?.toIntOrNull() }
            .filter { it in 1..10 }
            .minOrNull()
        val targetWins = Regex("목표\\s*:?\\s*(\\d+)").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val wins = Regex("현재\\s*(\\d+)\\s*승").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val round = Regex("(\\d+)\\s*라운드").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val artifactName = KNOWN_ARTIFACTS.firstOrNull(allText::contains)
        val artifactXp = blocks
            .filter { it.centerY > .65f }
            .mapNotNull { Regex("(\\d+)\\s*/\\s*(\\d+)").find(it.text)?.let { match ->
                ArtifactXp(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            } }
            .firstOrNull()
        return HonorDuelHeader(
            currency = currency,
            shopLevel = shopLevel,
            targetWins = targetWins,
            artifactXp = artifactXp,
            wins = wins,
            hp = null,
            refreshCost = refreshCost,
            currentRound = round,
            artifactName = artifactName,
            confidence = mapOf(
                HeaderField.CURRENCY to if (currency == null) 0f else .9f,
                HeaderField.SHOP_LEVEL to if (shopLevel == null) 0f else .95f,
                HeaderField.TARGET_WINS to if (targetWins == null) 0f else .9f,
                HeaderField.WINS to if (wins == null) 0f else .9f,
                HeaderField.HP to 0f,
                HeaderField.REFRESH_COST to if (refreshCost == null) 0f else .75f,
                HeaderField.ROUND to if (round == null) 0f else .9f,
                HeaderField.ARTIFACT to if (artifactName == null) 0f else .95f,
                HeaderField.ARTIFACT_XP to if (artifactXp == null) 0f else .8f,
            ),
        )
    }

    private val KNOWN_ARTIFACTS = listOf("마이다스의 재물")
}

data class ShopItemState @JvmOverloads constructor(
    val slotIndex: Int,
    val itemType: ShopItemType,
    val price: Int?,
    val artifactXpAmount: Int?,
    val confidence: Float,
    val bounds: NormalizedRect? = null,
    val classificationReasons: List<String> = emptyList(),
    val heroId: String? = null,
    val heroName: String? = null,
    val faction: String? = null,
    val heroRecognitionStatus: HeroRecognitionStatus = HeroRecognitionStatus.UNKNOWN,
    val quantity: Int? = null,
    val heroRarity: HeroRarity = HeroRarity.UNKNOWN,
    val isTrialCard: Boolean = false,
    val equipmentName: String? = null,
    val recognitionSource: RecognitionSource = RecognitionSource.AUTO,
    val portraitSignature: String? = null,
)

data class NonHeroItemClassification(
    val type: ShopItemType,
    val confidence: Float,
    val reasons: List<String>,
    val artifactXpAmount: Int? = null,
)

data class SlotVisualEvidence @JvmOverloads constructor(
    val yellowIconRatio: Float = 0f,
    val likelyHeroPortrait: Boolean = false,
    val redBackgroundRatio: Float = 0f,
    val hasEquipmentBadge: Boolean = false,
    val hasTrialCardBadge: Boolean = false,
) {
    companion object {
        fun from(bitmap: Bitmap, bounds: NormalizedRect, contentBottom: Float): SlotVisualEvidence {
            // ponytail: color ratios only gate OCR. Replace with templates when false positives appear.
            val left = (bounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val right = (bounds.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
            val top = (bounds.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            val bottom = (contentBottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
            var yellow = 0
            var orange = 0
            var redBackground = 0
            var sampled = 0
            val step = ((right - left).coerceAtMost(bottom - top) / 24).coerceAtLeast(1)
            for (y in top until bottom step step) for (x in left until right step step) {
                val color = bitmap.getPixel(x, y)
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                if (red > 165 && green > 120 && blue < 105) yellow++
                if (red > 165 && green in 65..195 && blue < 100) orange++
                if (red > 145 && green < 105 && blue < 105) redBackground++
                sampled++
            }
            return SlotVisualEvidence(
                yellowIconRatio = yellow.toFloat() / sampled.coerceAtLeast(1),
                likelyHeroPortrait = orange.toFloat() / sampled.coerceAtLeast(1) > .08f,
                redBackgroundRatio = redBackground.toFloat() / sampled.coerceAtLeast(1),
            )
        }
    }
}

enum class HeroRarity { EPIC, LEGENDARY, MYTHIC, UNKNOWN }

data class HeroOfferClassification(
    val type: ShopItemType = ShopItemType.UNKNOWN,
    val quantity: Int? = null,
    val isTrialCard: Boolean = false,
    val rarity: HeroRarity = HeroRarity.UNKNOWN,
    val equipmentName: String? = null,
    val reasons: List<String> = emptyList(),
)

object HeroOfferClassifier {
    private val equipment = listOf("간이 활", "밀림 후드", "생엽", "침묵의 투구")

    fun classify(text: String, price: Int?, visual: SlotVisualEvidence): HeroOfferClassification {
        val quantity = Regex("(?:x|X|×)\\s*(\\d+)|(\\d+)\\s*장").find(text)?.groupValues
            ?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        val trial = text.contains("체험 카드") || visual.hasTrialCardBadge
        val equipmentName = equipment.firstOrNull(text::contains).takeIf { visual.hasEquipmentBadge || text.contains("체험 카드") }
        val explicitRarity = when {
            text.contains("레전드") -> HeroRarity.LEGENDARY
            text.contains("에픽") -> HeroRarity.EPIC
            else -> HeroRarity.UNKNOWN
        }
        val legendaryTrial = trial && price == 12 && visual.redBackgroundRatio >= .08f &&
            (visual.hasEquipmentBadge || equipmentName != null)
        val rarity = if (legendaryTrial) HeroRarity.LEGENDARY else explicitRarity
        val type = when {
            trial -> ShopItemType.TRIAL_HERO_CARD
            quantity != null && quantity > 1 -> ShopItemType.HERO_BUNDLE
            else -> ShopItemType.HERO
        }
        val reasons = buildList {
            if (trial) add("체험 카드 표시")
            if (quantity != null) add("수량 ${quantity}장")
            if (legendaryTrial) add("12휘장·빨간 배경·체험 카드·장비")
            if (explicitRarity != HeroRarity.UNKNOWN) add("등급 텍스트")
        }
        return HeroOfferClassification(type, quantity ?: if (type == ShopItemType.HERO) 1 else null, trial, rarity, equipmentName, reasons)
    }
}

data class HeroDetailPopup(
    val heroName: String? = null,
    val rarity: HeroRarity = HeroRarity.UNKNOWN,
    val isTrialCard: Boolean = false,
    val equipmentName: String? = null,
    val confidence: Float = 0f,
)

object HeroDetailPopupParser {
    private val names = listOf("매혹의 세이렌", "사리에", "메이", "퀸", "루카", "발렌", "귀네스", "페르세우스")
    private val equipment = listOf("간이 활", "밀림 후드", "생엽", "침묵의 투구")

    fun parse(text: String): HeroDetailPopup = HeroDetailPopup(
        heroName = names.firstOrNull(text::contains),
        rarity = when {
            text.contains("레전드") -> HeroRarity.LEGENDARY
            text.contains("에픽") -> HeroRarity.EPIC
            else -> HeroRarity.UNKNOWN
        },
        isTrialCard = text.contains("체험 카드"),
        equipmentName = equipment.firstOrNull(text::contains),
        confidence = if (text.contains("사정거리")) .95f else 0f,
    )
}

object ShopDetailReconciler {
    fun apply(items: List<ShopItemState>, slotIndex: Int, detail: HeroDetailPopup): List<ShopItemState> = items.map { item ->
        if (item.slotIndex != slotIndex || detail.confidence < .9f) item else item.copy(
            itemType = if (detail.isTrialCard) ShopItemType.TRIAL_HERO_CARD else item.itemType,
            heroName = detail.heroName ?: item.heroName,
            heroRarity = if (detail.rarity == HeroRarity.UNKNOWN) item.heroRarity else detail.rarity,
            isTrialCard = detail.isTrialCard || item.isTrialCard,
            equipmentName = detail.equipmentName ?: item.equipmentName,
            confidence = detail.confidence,
            classificationReasons = item.classificationReasons + "상세 팝업 우선",
        )
    }
}

object NonHeroShopItemClassifier {
    private val factions = listOf("레오프론", "와일더스", "그레이브본", "트라이브", "기타")
    private val equipment = listOf("간이 활", "밀림 후드", "생엽", "침묵의 투구")

    @JvmOverloads
    fun classify(text: String, visual: SlotVisualEvidence = SlotVisualEvidence()): NonHeroItemClassification {
        if (text.contains("품절")) return fixed(ShopItemType.SOLD_OUT, "품절 텍스트")
        val xp = Regex("\\+(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (xp != null && visual.yellowIconRatio >= .01f) {
            return NonHeroItemClassification(ShopItemType.ARTIFACT_XP, .95f, listOf("+EXP 텍스트", "노란 EXP 아이콘"), xp)
        }
        if (equipment.any(text::contains)) return fixed(ShopItemType.EQUIPMENT, "확정 장비명")
        if (visual.likelyHeroPortrait) return unknown("영웅 초상화 가능성")
        if (text.contains("랜덤") && text.contains("승급")) return fixed(ShopItemType.RANDOM_HERO_UPGRADE, "랜덤", "승급")
        if (text.contains("랜덤") && factions.any(text::contains)) return fixed(ShopItemType.FACTION_HERO_PACK, "랜덤", "진영명")
        if (text.contains("랜덤") && text.contains("영웅")) return fixed(ShopItemType.RANDOM_HERO_PACK, "랜덤", "영웅")
        return unknown(if (xp != null) "+수치만 감지" else "확정 증거 없음")
    }

    private fun fixed(type: ShopItemType, vararg reasons: String) =
        NonHeroItemClassification(type, .95f, reasons.toList())

    private fun unknown(reason: String) = NonHeroItemClassification(ShopItemType.UNKNOWN, 0f, listOf(reason))
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class GameViewport(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun toFrame(local: HonorDuelShopAnalyzer.SlotBounds): NormalizedRect =
        toFrame(local.left, local.top, local.right, local.bottom)

    private fun toFrame(left: Float, top: Float, right: Float, bottom: Float) = NormalizedRect(
        left = this.left + (this.right - this.left) * left,
        top = this.top + (this.bottom - this.top) * top,
        right = this.left + (this.right - this.left) * right,
        bottom = this.top + (this.bottom - this.top) * bottom,
    )
}

object GameViewportDetector {
    fun detect(bitmap: Bitmap): GameViewport {
        // ponytail: trims only light top chrome. Add anchor-based bounds when side/bottom overlays appear.
        val top = detectLightSystemBar(bitmap)
        return GameViewport(0f, top, 1f, 1f)
    }

    private fun detectLightSystemBar(bitmap: Bitmap): Float {
        val first = rowBrightness(bitmap, 0)
        if (first < 180) return 0f
        val limit = (bitmap.height * .12f).toInt()
        for (y in 1 until limit) {
            if (rowBrightness(bitmap, y) < first - 60) return y.toFloat() / bitmap.height
        }
        return 0f
    }

    private fun rowBrightness(bitmap: Bitmap, y: Int): Int {
        val samples = 8
        return (0 until samples).sumOf { index ->
            val color = bitmap.getPixel(index * (bitmap.width - 1) / (samples - 1), y)
            (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
        } / samples
    }
}

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
    val viewport: GameViewport? = null,
    val heroDetail: HeroDetailPopup? = null,
    val ownedHeroGauges: List<OwnedHeroGaugeSlot> = emptyList(),
    val ownedHeroes: List<OwnedHeroState> = emptyList(),
)
