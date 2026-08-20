package com.asp0902.mobilegameassistant.analysis

data class OwnedHeroState(
    val slotIndex: Int,
    val heroId: String? = null,
    val heroName: String? = null,
    val faction: String? = null,
    val rarity: HeroRarity = HeroRarity.UNKNOWN,
    val promotion: PromotionGaugeObservation = PromotionGaugeObservation(),
    val equipmentName: String? = null,
    val sellValue: Int? = null,
    val confidence: Float = 0f,
)

object SellValueParser {
    fun parse(text: String): Int? = Regex("\\+\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    fun total(heroes: List<OwnedHeroState>): Int = heroes.sumOf { it.sellValue ?: 0 }
}

object OwnedHeroRecognizer {
    private val equipment = listOf("간이 활", "밀림 후드", "생엽", "침묵의 투구")

    fun state(
        slotIndex: Int,
        text: String,
        hero: HeroRecognitionResult,
        gauge: PromotionGaugeObservation,
    ): OwnedHeroState = OwnedHeroState(
        slotIndex = slotIndex,
        heroId = hero.heroId,
        heroName = hero.koreanName,
        faction = hero.faction,
        rarity = when {
            text.contains("신화") -> HeroRarity.MYTHIC
            text.contains("레전드") -> HeroRarity.LEGENDARY
            text.contains("에픽") -> HeroRarity.EPIC
            else -> HeroRarity.UNKNOWN
        },
        promotion = gauge,
        equipmentName = equipment.firstOrNull(text::contains),
        // Screen text only. Do not derive sell value from rank, history, or equipment.
        sellValue = SellValueParser.parse(text),
        confidence = maxOf(hero.confidence, gauge.confidence, if (SellValueParser.parse(text) == null) 0f else .95f),
    )
}
