package com.asp0902.mobilegameassistant.labyrinth

/**
 * 이계의 미궁에서 **화면으로 검증된 규칙만** 담는다.
 *
 * 수치가 확인되지 않은 것은 넣지 않는다. 예를 들어 낙인 단계별 정확한 증가치,
 * 진영 버프 임계치, 레전드 위의 등급 이름은 정본 자료에 없으므로 여기 없다.
 *
 * 이 파일은 순수 Kotlin 이다. Android 의존이 없어야 단위 테스트로 규칙을 고정할 수 있다.
 */
object LabyrinthRules {
    /** 정규 구간 마지막 층. 이후는 심층이며 같은 Run 으로 이어진다. */
    const val REGULAR_FLOORS = 15

    /** 탐색 진형에 등록할 수 있는 인원. 실제 전투 출전 인원과 다른 개념이다. */
    const val EXPLORATION_ROSTER_LIMIT = 10

    /** 관측된 전투 화면의 실제 출전 인원. 10명이 동시에 배치되지 않는다. */
    const val OBSERVED_BATTLE_DEPLOY_LIMIT = 5

    /** 난이도 5 에서 영웅 레벨이 조정되는 값. */
    const val DIFFICULTY5_ADJUSTED_LEVEL = 100

    /** 같은 유물 개수가 이 값에 도달할 때마다 낙인 단계가 열린다. 8 을 넘어도 추가 단계는 없다. */
    val SIGIL_THRESHOLDS = listOf(2, 4, 6, 8)

    /** `전체 평균 가격` 획득 후 피츠 상점 상품 가격은 모두 이 값이 된다. */
    const val AVERAGE_PRICE_FIXED = 50

    /** 활성화된 낙인 단계 수. 0 이면 아직 아무 단계도 열리지 않았다. */
    fun activeSigilTiers(relicCount: Int): Int = SIGIL_THRESHOLDS.count { relicCount >= it }

    /** 다음 낙인이 열리는 유물 개수. 8 단계까지 다 열렸으면 null. */
    fun nextSigilThreshold(relicCount: Int): Int? = SIGIL_THRESHOLDS.firstOrNull { relicCount < it }

    fun shopPrice(basePrice: Int, hasAveragePrice: Boolean): Int =
        if (hasAveragePrice) AVERAGE_PRICE_FIXED else basePrice

    /**
     * 구매 후 남는 퓨어 크리스탈.
     *
     * `2배 퓨어 크리스탈` 은 상품 가격을 **차감한 뒤** 잔액을 두 배로 한다.
     * 검증 사례: `(173 - 50) × 2 = 246`.
     */
    fun crystalAfterPurchase(before: Int, price: Int, doublesRemainder: Boolean): Int {
        val remainder = before - price
        return if (doublesRemainder) remainder * 2 else remainder
    }

    /**
     * 계정 등급 사다리 중 **정본 자료로 확인된 구간만** 표현한다.
     *
     * 레전드 위의 등급 이름은 미궁 자료에서 확인되지 않았으므로 [ABOVE_LEGEND] 로 묶는다.
     * 이름을 지어내지 않기 위한 선택이다.
     */
    enum class HeroTier { ELITE, ELITE_PLUS, LEGEND, ABOVE_LEGEND }

    /** 난이도 5 조정 등급. 엘리트+ 는 레전드로 올라가고, 레전드를 넘은 영웅은 강등되지 않는다. */
    fun adjustedTier(accountTier: HeroTier): HeroTier =
        if (accountTier == HeroTier.ELITE_PLUS) HeroTier.LEGEND else accountTier

    /** 15층을 넘어선 층. 심층에서도 같은 Run 의 유물·사망·재화 상태가 이어진다. */
    fun isDeepFloor(floor: Int): Boolean = floor > REGULAR_FLOORS
}
