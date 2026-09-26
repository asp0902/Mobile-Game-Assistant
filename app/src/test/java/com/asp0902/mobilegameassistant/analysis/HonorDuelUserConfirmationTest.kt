package com.asp0902.mobilegameassistant.analysis

import org.junit.Assert.*
import org.junit.Test

class HonorDuelUserConfirmationTest {
    @Test fun explicitConfirmationIsScopedAndPreservesImageSimilarity() {
        val rows = listOf("성상의 조각" to listOf("페르세우스", "오리안", "틸로아"),
            "불멸의 불꽃" to listOf("귀네스", "퀸", "발리카"),
            "마이다스의 재물" to listOf("발리카", "카렌", "스모키와 미르키"))
        val confirmed = setOf("페르세우스", "퀸", "발리카")
        val offers = rows.mapIndexed { index, (artifact, names) ->
            InitialFormationOffer(index, artifactName = artifact, artifactSource = "OCR_MATCH", heroSlots = names.mapIndexed { slot, name ->
                val hero = InitialFormationKnowledge.hero(name)!!
                InitialFormationHeroSlot(slot, hero.id, name, hero.faction, hero.role, .85f,
                    if (name in confirmed) HeroRecognitionStatus.NEEDS_CONFIRMATION else HeroRecognitionStatus.CONFIRMED,
                    NormalizedRect(0f, 0f, 1f, 1f)).copy(rarity = HeroRarity.EPIC)
            })
        } + InitialFormationOffer(3, isRandom = true)
        val result = InitialFormationKnowledge.applyConfirmedStartLayout(offers, confirmed)
        assertTrue(result.take(3).flatMap { it.heroSlots }.all { it.status == HeroRecognitionStatus.CONFIRMED })
        assertEquals(.85f, result.first().heroSlots.first().confidence, .0001f)
        assertTrue(result.last().heroSlots.isEmpty())
        val changed = offers.map { if (it.slotIndex == 0) it.copy(artifactSource = "TITLE_INFERENCE") else it }
        assertEquals(changed, InitialFormationKnowledge.applyConfirmedStartLayout(changed, confirmed))
    }
}
