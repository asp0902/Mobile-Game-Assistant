package com.asp0902.mobilegameassistant.analysis

import org.junit.Assert.*
import org.junit.Test

class HonorDuelStartingRarityTest {
    @Test fun initialRuleSetsEpicWithoutInventingRandomIdentityOrMutatingInput() {
        val hero = InitialFormationHeroSlot(0, "valka", "발리카", "그레이브본", "전사", .95f,
            HeroRecognitionStatus.CONFIRMED, NormalizedRect(0f, 0f, 1f, 1f))
        val offered = InitialFormationOffer(0, heroSlots = listOf(hero))
        val random = InitialFormationOffer(1, isRandom = true)
        val updated = InitialFormationKnowledge.applyStartingRarity(listOf(offered, random))
        assertEquals(HeroRarity.EPIC, updated.first().heroSlots.single().rarity)
        assertEquals(HeroRarity.UNKNOWN, offered.heroSlots.single().rarity)
        assertEquals("valka", updated.first().heroSlots.single().heroId)
        assertTrue(updated.last().heroSlots.isEmpty())
        assertNull(InitialFormationAdvisor.evaluate(updated.last()).score)
    }
}
