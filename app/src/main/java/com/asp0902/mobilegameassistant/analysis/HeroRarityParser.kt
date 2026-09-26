package com.asp0902.mobilegameassistant.analysis

object HeroRarityParser {
    fun parse(text: String): HeroRarity {
        val compact = text.replace(Regex("\\s+"), "")
            .replace("＋", "+")
        return when {
            compact.contains("레전드+") || compact.contains("엘리트+") -> HeroRarity.LEGENDARY_PLUS
            compact.contains("레전드") -> HeroRarity.LEGENDARY
            compact.contains("엘리트") -> HeroRarity.LEGENDARY
            compact.contains("에픽+") -> HeroRarity.EPIC_PLUS
            compact.contains("에픽") -> HeroRarity.EPIC
            compact.contains("신화") -> HeroRarity.MYTHIC
            else -> HeroRarity.UNKNOWN
        }
    }

    fun koreanLabel(rarity: HeroRarity): String = when (rarity) {
        HeroRarity.EPIC -> "에픽"
        HeroRarity.EPIC_PLUS -> "에픽+"
        HeroRarity.LEGENDARY -> "레전드"
        HeroRarity.LEGENDARY_PLUS -> "레전드+"
        HeroRarity.MYTHIC -> "신화"
        HeroRarity.UNKNOWN -> "등급 미확인"
    }
}
