package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.analysis.ScreenType

/**
 * 관측 하나의 출처.
 *
 * 추론과 실제 관측을 같은 필드에 담지 않기 위한 것이다.
 * [CONFIRMED_BY_NEXT_SCREEN] 은 [INFERRED] 를 덮어쓸 수 있지만 그 반대는 안 된다.
 */
enum class LabyrinthEvidence {
    /** 화면에 값이 직접 보임 */
    DIRECTLY_READ,

    /** 다음 화면에서 결과로 확인됨 */
    CONFIRMED_BY_NEXT_SCREEN,

    /** 사용자가 직접 정정 */
    USER_CORRECTED,

    /** 추론. 관측으로 덮어쓸 수 있다 */
    INFERRED,

    /** 보였으나 확정되지 않음 */
    UNVERIFIED,
    ;

    /** 신뢰 순서. 낮은 근거가 높은 근거를 덮어쓰지 못하게 한다. */
    val priority: Int
        get() = when (this) {
            UNVERIFIED -> 0
            INFERRED -> 1
            DIRECTLY_READ -> 2
            CONFIRMED_BY_NEXT_SCREEN -> 3
            USER_CORRECTED -> 4
        }
}

enum class LabyrinthRunStatus {
    ACTIVE,

    /** 사용자가 중도 퇴장으로 미리 결산 */
    ENDED_BY_USER,

    /** 주간 갱신으로 자동 결산. Run 을 지우지 않고 이 상태로 보존한다 */
    AUTO_SETTLED_BY_WEEKLY_RESET,
}

/**
 * 선택 지점.
 *
 * 네 값을 **반드시 서로 다른 필드**로 둔다. 앱 추천을 실제 선택으로 저장하면 안 된다.
 */
data class LabyrinthChoicePoint(
    val floorLabel: String,
    val screen: ScreenType,
    /** 1. 화면에 등장한 후보 */
    val offered: List<String>,
    /** 2. 앱이 추천한 후보 */
    val recommended: String? = null,
    /** 3. 사용자가 실제로 선택한 것 */
    val actualChoice: String? = null,
    /** 4. 다음 화면에서 확인된 결과 */
    val confirmedResult: String? = null,
    val evidence: LabyrinthEvidence = LabyrinthEvidence.UNVERIFIED,
    val confidence: Float = 0f,
) {
    /** 선택지가 하나뿐이면 추천 경쟁을 만들지 않는다. */
    val isSingleOption: Boolean get() = offered.size == 1
}

data class LabyrinthHero(
    val name: String,
    val heroId: String? = null,
    /** 탐색 등록과 별개. 실제 전투에 나간 영웅만 true */
    val deployed: Boolean = false,
    val alive: Boolean = true,
    val hpPercent: Int? = null,
    val accountTier: LabyrinthRules.HeroTier? = null,
    val adjustedTier: LabyrinthRules.HeroTier? = null,
    val accountLevel: Int? = null,
    val adjustedLevel: Int? = null,
    /** 발밑 anchor 로 얻은 실제 점유 타일 */
    val tileId: String? = null,
    val diedAtFloor: String? = null,
    /** 검증된 초상화가 있는가. 없으면 UI 는 `이미지 미검증` 을 표시한다 */
    val portraitVerified: Boolean = false,
)

data class LabyrinthBattle(
    val floorLabel: String,
    val victory: Boolean? = null,
    val deployed: List<String> = emptyList(),
    val damage: Map<String, Long> = emptyMap(),
    val healing: Map<String, Long> = emptyMap(),
    val damageTaken: Map<String, Long> = emptyMap(),
    val deaths: List<String> = emptyList(),
)

/** 구매는 회수 불가능한 지출이다. 추천만 한 상품과 구분한다. */
data class LabyrinthPurchase(
    val floorLabel: String,
    val item: String,
    val price: Int,
    val crystalsBefore: Int? = null,
    val crystalsAfter: Int? = null,
)

/** 추가 도전은 조건·기회·보상·결과를 각각 저장한다. 사례가 여럿이라 하나로 합치지 않는다. */
data class LabyrinthExtraChallenge(
    val floorLabel: String,
    val condition: String,
    val attempts: Int? = null,
    val reward: String? = null,
    val cleared: Boolean? = null,
)

data class LabyrinthRunState(
    val runId: Long,
    val startedAt: Long,
    val nextWeeklyResetAt: Long,
    val status: LabyrinthRunStatus = LabyrinthRunStatus.ACTIVE,
    val difficulty: Int? = null,
    /** 정규 구간 층 (1..15) */
    val floor: Int? = null,
    /** 15층을 넘어선 심층 층. 같은 Run 으로 이어진다 */
    val deepFloor: Int? = null,
    val currentScreen: ScreenType = ScreenType.UNKNOWN,
    val screenConfidence: Float = 0f,
    /** 탐색 진형 등록 영웅. 최대 10명 */
    val roster: List<LabyrinthHero> = emptyList(),
    val crystals: Int? = null,
    val revivePotions: Int? = null,
    /** 유물 이름 → 보유 개수 */
    val relics: Map<String, Int> = emptyMap(),
    val activeSigils: List<String> = emptyList(),
    val choices: List<LabyrinthChoicePoint> = emptyList(),
    val battles: List<LabyrinthBattle> = emptyList(),
    val purchases: List<LabyrinthPurchase> = emptyList(),
    val extraChallenges: List<LabyrinthExtraChallenge> = emptyList(),
    /** `전체 평균 가격` 획득 후 상품 가격은 모두 50이 된다 */
    val hasAveragePrice: Boolean = false,
    val userCorrections: List<String> = emptyList(),
) {
    /** 실제 전투에 나간 영웅. 등록 인원과 다른 개념이다. */
    val deployedHeroes: List<LabyrinthHero> get() = roster.filter { it.deployed }

    val diedHeroes: List<LabyrinthHero> get() = roster.filter { !it.alive }

    /** 최초 사망 시점. 부활 포션 가치 판단에 쓴다. */
    val firstDeathFloor: String? get() = roster.mapNotNull { it.diedAtFloor }.minOrNull()

    /** 같은 유물 2/4/6/8 에서 열린 낙인 단계 수. 8 초과 추가 단계는 없다. */
    fun sigilTiers(relic: String): Int = LabyrinthRules.activeSigilTiers(relics[relic] ?: 0)

    val isDeep: Boolean get() = deepFloor != null || (floor?.let { LabyrinthRules.isDeepFloor(it) } == true)

    companion object {
        private const val HOUR = 3_600_000L
        private const val DAY = 24 * HOUR
        private const val WEEK = 7 * DAY

        /** UTC+9 */
        private const val KST_OFFSET = 9 * HOUR

        /** 1970-01-01 은 목요일. 첫 월요일은 1970-01-05 다. */
        private const val FIRST_MONDAY_0900_KST = 4 * DAY + 9 * HOUR

        /**
         * 다음 주간 갱신 시각. 매주 월요일 09:00 UTC+9.
         *
         * `java.time` 은 minSdk 23 에서 desugaring 없이 못 쓰므로 epoch 산술로 계산한다.
         */
        fun nextWeeklyReset(nowMillis: Long): Long {
            val kstNow = nowMillis + KST_OFFSET
            val elapsed = kstNow - FIRST_MONDAY_0900_KST
            val periods = Math.floorDiv(elapsed, WEEK)
            return FIRST_MONDAY_0900_KST + (periods + 1) * WEEK - KST_OFFSET
        }

        fun start(runId: Long, nowMillis: Long): LabyrinthRunState = LabyrinthRunState(
            runId = runId,
            startedAt = nowMillis,
            nextWeeklyResetAt = nextWeeklyReset(nowMillis),
        )
    }
}
