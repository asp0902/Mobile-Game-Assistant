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
        val confirmedByLayoutId = mapOf("honor-start-20260918-3offers" to confirmed)
        val result = InitialFormationKnowledge.applyConfirmedStartLayout(offers, confirmedByLayoutId)
        assertTrue(result.take(3).flatMap { it.heroSlots }.all { it.status == HeroRecognitionStatus.CONFIRMED })
        assertEquals(.85f, result.first().heroSlots.first().confidence, .0001f)
        assertTrue(result.last().heroSlots.isEmpty())
        val changed = offers.map { if (it.slotIndex == 0) it.copy(artifactSource = "TITLE_INFERENCE") else it }
        assertEquals(changed, InitialFormationKnowledge.applyConfirmedStartLayout(changed, confirmedByLayoutId))
    }

    private fun offer20260928(
        artifact: String, names: List<String>, index: Int,
        confirmed: Set<String>, status: HeroRecognitionStatus = HeroRecognitionStatus.NEEDS_CONFIRMATION,
    ) = InitialFormationOffer(index, artifactName = artifact, artifactSource = "OCR_MATCH", heroSlots = names.mapIndexed { slot, name ->
        val hero = InitialFormationKnowledge.hero(name)!!
        InitialFormationHeroSlot(slot, hero.id, name, hero.faction, hero.role, .87f,
            if (name in confirmed) status else HeroRecognitionStatus.CONFIRMED,
            NormalizedRect(0f, 0f, 1f, 1f)).copy(rarity = HeroRarity.EPIC)
    })

    @Test fun confirms20260928LayoutAndUnblocksRecommendation() {
        val confirmed = setOf("인듀어", "사리에", "이사벨라")
        val offers = listOf(
            offer20260928("고블린 가면", listOf("카세디아", "카짐", "스모키와 미르키"), 0, emptySet()),
            offer20260928("달그림자 활", listOf("인듀어", "사리에", "이사벨라"), 1, confirmed),
            offer20260928("평정의 샘물", listOf("사리에", "갈라하드", "튜더"), 2, emptySet()),
        ) + InitialFormationOffer(3, isRandom = true)
        val confirmedByLayoutId = mapOf("honor-start-20260928-3offers" to confirmed)
        val promoted = InitialFormationKnowledge.applyConfirmedStartLayout(offers, confirmedByLayoutId)
        assertTrue(promoted.take(3).flatMap { it.heroSlots }.all { it.status == HeroRecognitionStatus.CONFIRMED })

        val recommendations = InitialFormationAdvisor.recommend(promoted)
        assertTrue(recommendations.none { it.reason.contains("고정 선택지 전체 인식 확인 전 단독 추천 보류") })
    }

    @Test fun mismatched20260928LayoutDoesNotPromote() {
        val confirmed = setOf("인듀어", "사리에", "이사벨라")
        val confirmedByLayoutId = mapOf("honor-start-20260928-3offers" to confirmed)

        // Wrong hero name in card2 slot0 (should be 인듀어).
        val wrongName = listOf(
            offer20260928("고블린 가면", listOf("카세디아", "카짐", "스모키와 미르키"), 0, emptySet()),
            offer20260928("달그림자 활", listOf("사리에", "인듀어", "이사벨라"), 1, confirmed),
            offer20260928("평정의 샘물", listOf("사리에", "갈라하드", "튜더"), 2, emptySet()),
        ) + InitialFormationOffer(3, isRandom = true)
        assertEquals(wrongName, InitialFormationKnowledge.applyConfirmedStartLayout(wrongName, confirmedByLayoutId))

        // UNKNOWN slot in card2 (not just NEEDS_CONFIRMATION).
        val unknownSlot = listOf(
            offer20260928("고블린 가면", listOf("카세디아", "카짐", "스모키와 미르키"), 0, emptySet()),
            offer20260928("달그림자 활", listOf("인듀어", "사리에", "이사벨라"), 1, confirmed, HeroRecognitionStatus.UNKNOWN),
            offer20260928("평정의 샘물", listOf("사리에", "갈라하드", "튜더"), 2, emptySet()),
        ) + InitialFormationOffer(3, isRandom = true)
        assertEquals(unknownSlot, InitialFormationKnowledge.applyConfirmedStartLayout(unknownSlot, confirmedByLayoutId))

        // Artifact not OCR-matched.
        val unmatchedArtifact = listOf(
            offer20260928("고블린 가면", listOf("카세디아", "카짐", "스모키와 미르키"), 0, emptySet()).copy(artifactSource = "TITLE_INFERENCE"),
            offer20260928("달그림자 활", listOf("인듀어", "사리에", "이사벨라"), 1, confirmed),
            offer20260928("평정의 샘물", listOf("사리에", "갈라하드", "튜더"), 2, emptySet()),
        ) + InitialFormationOffer(3, isRandom = true)
        assertEquals(unmatchedArtifact, InitialFormationKnowledge.applyConfirmedStartLayout(unmatchedArtifact, confirmedByLayoutId))
    }

    @Test fun layout20260918StillWorksAlongside20260928() {
        val confirmed918 = setOf("페르세우스", "퀸", "발리카")
        val offers918 = listOf(
            offer20260928("성상의 조각", listOf("페르세우스", "오리안", "틸로아"), 0, confirmed918),
            offer20260928("불멸의 불꽃", listOf("귀네스", "퀸", "발리카"), 1, confirmed918),
            offer20260928("마이다스의 재물", listOf("발리카", "카렌", "스모키와 미르키"), 2, confirmed918),
        ) + InitialFormationOffer(3, isRandom = true)
        val bothLayouts = mapOf(
            "honor-start-20260918-3offers" to confirmed918,
            "honor-start-20260928-3offers" to setOf("인듀어", "사리에", "이사벨라"),
        )
        val promoted = InitialFormationKnowledge.applyConfirmedStartLayout(offers918, bothLayouts)
        assertTrue(promoted.take(3).flatMap { it.heroSlots }.all { it.status == HeroRecognitionStatus.CONFIRMED })
    }
}
