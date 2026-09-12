package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.labyrinth.LabyrinthRules.HeroTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #38 로 승인된 규칙을 고정한다. 여기 없는 수치는 정본에서 확인되지 않은 것이다. */
class LabyrinthRulesTest {

    // ---- 유물과 낙인 ----

    @Test
    fun sigilOpensAtTwoFourSixEight() {
        assertEquals(0, LabyrinthRules.activeSigilTiers(1))
        assertEquals(1, LabyrinthRules.activeSigilTiers(2))
        assertEquals(1, LabyrinthRules.activeSigilTiers(3))
        assertEquals(2, LabyrinthRules.activeSigilTiers(4))
        assertEquals(3, LabyrinthRules.activeSigilTiers(6))
        assertEquals(4, LabyrinthRules.activeSigilTiers(8))
    }

    /** 8개를 넘어도 추가 낙인은 없다. */
    @Test
    fun noSigilBeyondEight() {
        assertEquals(4, LabyrinthRules.activeSigilTiers(9))
        assertEquals(4, LabyrinthRules.activeSigilTiers(20))
        assertNull(LabyrinthRules.nextSigilThreshold(8))
        assertEquals(Integer.valueOf(4), LabyrinthRules.nextSigilThreshold(3))
    }

    // ---- 피츠 상점 ----

    /** 검증 사례: `(173 - 50) × 2 = 246`. 가격을 차감한 뒤 잔액을 두 배로 한다. */
    @Test
    fun doubleCrystalDoublesRemainderAfterPrice() {
        assertEquals(246, LabyrinthRules.crystalAfterPurchase(before = 173, price = 50, doublesRemainder = true))
    }

    @Test
    fun plainPurchaseJustSubtracts() {
        // 저금통 구매 관측: 157 - 75 = 82
        assertEquals(82, LabyrinthRules.crystalAfterPurchase(before = 157, price = 75, doublesRemainder = false))
    }

    /** `전체 평균 가격` 획득 후 후속 상품 가격은 모두 50이다. */
    @Test
    fun averagePriceFixesEveryPriceToFifty() {
        assertEquals(75, LabyrinthRules.shopPrice(basePrice = 75, hasAveragePrice = false))
        assertEquals(50, LabyrinthRules.shopPrice(basePrice = 75, hasAveragePrice = true))
        assertEquals(50, LabyrinthRules.shopPrice(basePrice = 120, hasAveragePrice = true))
    }

    // ---- 난이도 5 편성 ----

    /** 탐색 등록 10명과 실제 전투 출전 5명은 다른 개념이다. */
    @Test
    fun rosterAndDeploymentAreSeparate() {
        assertEquals(10, LabyrinthRules.EXPLORATION_ROSTER_LIMIT)
        assertEquals(5, LabyrinthRules.OBSERVED_BATTLE_DEPLOY_LIMIT)
        assertTrue(LabyrinthRules.OBSERVED_BATTLE_DEPLOY_LIMIT < LabyrinthRules.EXPLORATION_ROSTER_LIMIT)
    }

    /** 엘리트+ 는 레전드로 올라가고, 레전드를 넘은 영웅은 강등되지 않는다. */
    @Test
    fun elitePlusIsPromotedButAboveLegendIsNotDemoted() {
        assertEquals(HeroTier.LEGEND, LabyrinthRules.adjustedTier(HeroTier.ELITE_PLUS))
        assertEquals(HeroTier.LEGEND, LabyrinthRules.adjustedTier(HeroTier.LEGEND))
        assertEquals(HeroTier.ABOVE_LEGEND, LabyrinthRules.adjustedTier(HeroTier.ABOVE_LEGEND))
        assertEquals(HeroTier.ELITE, LabyrinthRules.adjustedTier(HeroTier.ELITE))
    }

    // ---- 정규와 심층 ----

    @Test
    fun deepFloorsContinuePastFifteen() {
        assertFalse(LabyrinthRules.isDeepFloor(15))
        assertTrue(LabyrinthRules.isDeepFloor(16))
        assertTrue(LabyrinthRules.isDeepFloor(20))
    }

    // ---- Golden 자료 무결성 ----

    /** 후보와 실제 선택은 다른 필드다. 확정되지 않은 선택을 추론으로 채우지 않는다. */
    @Test
    fun goldenKeepsUnconfirmedChoicesNull() {
        val unverified = LabyrinthGolden.DIFFICULTY5_RUN
            .filter { it.evidence == LabyrinthGolden.Evidence.UNVERIFIED }
        assertTrue(unverified.isNotEmpty())
        assertTrue(unverified.all { it.actualChoice == null })
    }

    @Test
    fun goldenConfirmedChoicesAreAmongOfferedOptions() {
        LabyrinthGolden.DIFFICULTY5_RUN
            .mapNotNull { node -> node.actualChoice?.let { node to it } }
            .forEach { (node, choice) ->
                assertTrue("${node.floor} ${node.screen}", choice in node.offered)
            }
    }

    /** 선택지가 하나뿐인 화면이 Golden 에 있어야 단일 선택지 회귀를 검증할 수 있다. */
    @Test
    fun goldenContainsSingleOptionNode() {
        val single = LabyrinthGolden.DIFFICULTY5_RUN.first { it.offered.size == 1 && it.screen == "경로 선택" }
        assertEquals(listOf("아이템 게이트"), single.offered)
    }

    /** 상점 관측 가격이 규칙 계산과 맞아떨어진다. */
    @Test
    fun goldenShopMatchesRules() {
        assertEquals(Integer.valueOf(75), LabyrinthGolden.FITZ_SHOP_5_3["저금통"])
        val after = LabyrinthRules.crystalAfterPurchase(157, LabyrinthGolden.FITZ_SHOP_5_3.getValue("저금통"), false)
        assertEquals(82, after)
    }

    /** 소환 화면에 등장했다는 이유로 보유 영웅을 늘리지 않는다. */
    @Test
    fun ownedHeroesExcludeSummonScreenCandidates() {
        assertTrue("휴윈" in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
        assertTrue("후긴" in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
        assertTrue("에이론" in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
        assertFalse("테미시아" in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
        assertFalse("브라이언" in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
        // 토란은 Run 종료 후 획득이라 시작 보유 목록에 없다.
        assertFalse(LabyrinthGolden.ACQUIRED_AFTER_D5_RUN in LabyrinthGolden.OWNED_HEROES_AT_D5_START)
    }
}
