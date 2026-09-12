package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.analysis.ScreenType

/**
 * 판별된 화면 하나를 RunState 에 누적한다 (#40).
 *
 * 규칙 두 가지를 지킨다.
 * - 낮은 신뢰도 화면은 상태를 바꾸지 않는다. 잘못 인식한 화면이 Run 을 오염시키면 되돌릴 수 없다.
 * - 앱 추천은 여기서 절대 `actualChoice` 로 들어가지 않는다. 그건 다음 화면 관측이 확인해 준다.
 */
object LabyrinthRunAccumulator {

    fun apply(
        state: LabyrinthRunState,
        result: LabyrinthScreenClassifier.Result,
        nowMillis: Long,
    ): LabyrinthRunState {
        val screen = result.classification
        val settled = settleIfWeeklyResetPassed(state, nowMillis)
        if (settled.status != LabyrinthRunStatus.ACTIVE) return settled

        // 확인 필요 수준이면 화면 종류만 기록하고 값은 건드리지 않는다.
        if (screen.confidence <= LabyrinthScreenClassifier.LOW_CONFIDENCE) {
            return settled.copy(currentScreen = screen.type, screenConfidence = screen.confidence)
        }

        val signals = result.signals
        val floor = signals.floor
        val next = settled.copy(
            currentScreen = screen.type,
            screenConfidence = screen.confidence,
            difficulty = signals.difficulty ?: settled.difficulty,
            floor = floor ?: settled.floor,
            deepFloor = if (floor != null && LabyrinthRules.isDeepFloor(floor)) floor else settled.deepFloor,
        )
        return recordChoicePoint(next, screen.type, signals, screen.confidence)
    }

    /**
     * 화면에 보인 후보를 선택 지점으로 남긴다.
     *
     * `actualChoice` 는 비워 둔다. 실제로 무엇을 골랐는지는 이 화면이 알려주지 않는다.
     */
    private fun recordChoicePoint(
        state: LabyrinthRunState,
        screen: ScreenType,
        signals: LabyrinthScreenClassifier.Signals,
        confidence: Float,
    ): LabyrinthRunState {
        if (signals.offeredChoices.isEmpty()) return state
        val label = floorLabel(state)
        val point = LabyrinthChoicePoint(
            floorLabel = label,
            screen = screen,
            offered = signals.offeredChoices,
            evidence = LabyrinthEvidence.DIRECTLY_READ,
            confidence = confidence,
        )
        // 같은 층에서 같은 후보를 다시 본 것이면 중복으로 쌓지 않는다.
        val duplicate = state.choices.lastOrNull()
            ?.let { it.floorLabel == label && it.offered == point.offered && it.screen == screen } == true
        return if (duplicate) state else state.copy(choices = state.choices + point)
    }

    /**
     * 다음 화면에서 확인된 결과를 직전 선택 지점에 연결한다.
     *
     * 예: 유물의 문에서 칼날·수호를 봤고 다음 화면에 `칼날 1/2` 가 나오면 그때 실제 선택이 확정된다.
     * 확인된 결과만 [LabyrinthEvidence.CONFIRMED_BY_NEXT_SCREEN] 으로 올린다.
     */
    fun confirmLastChoice(state: LabyrinthRunState, confirmed: String): LabyrinthRunState {
        val index = state.choices.indexOfLast { it.actualChoice == null && confirmed in it.offered }
        if (index < 0) return state
        val updated = state.choices[index].copy(
            actualChoice = confirmed,
            confirmedResult = confirmed,
            evidence = LabyrinthEvidence.CONFIRMED_BY_NEXT_SCREEN,
        )
        return state.copy(choices = state.choices.toMutableList().also { it[index] = updated })
    }

    /** 앱 추천을 기록한다. 실제 선택과 다른 필드다. */
    fun recordRecommendation(state: LabyrinthRunState, recommended: String): LabyrinthRunState {
        val index = state.choices.indexOfLast { recommended in it.offered }
        if (index < 0) return state
        val updated = state.choices[index].copy(recommended = recommended)
        return state.copy(choices = state.choices.toMutableList().also { it[index] = updated })
    }

    /** 사용자 정정. 가장 높은 신뢰 근거로 덮어쓴다. */
    fun correctChoice(state: LabyrinthRunState, index: Int, choice: String): LabyrinthRunState {
        val point = state.choices.getOrNull(index) ?: return state
        val updated = point.copy(actualChoice = choice, evidence = LabyrinthEvidence.USER_CORRECTED)
        return state.copy(
            choices = state.choices.toMutableList().also { it[index] = updated },
            userCorrections = state.userCorrections + "${point.floorLabel} ${point.screen} → $choice",
        )
    }

    /**
     * 주간 갱신을 지났으면 자동 결산 상태로 보존한다.
     *
     * Run 을 지우지 않는다. 지난 Run 도 분석할 수 있어야 한다.
     */
    fun settleIfWeeklyResetPassed(state: LabyrinthRunState, nowMillis: Long): LabyrinthRunState =
        if (state.status == LabyrinthRunStatus.ACTIVE && nowMillis >= state.nextWeeklyResetAt) {
            state.copy(status = LabyrinthRunStatus.AUTO_SETTLED_BY_WEEKLY_RESET)
        } else {
            state
        }

    /** 사용자가 중도 퇴장으로 미리 결산. 앱은 게임을 대신 결산하지 않고 로컬 상태만 바꾼다. */
    fun endByUser(state: LabyrinthRunState): LabyrinthRunState =
        state.copy(status = LabyrinthRunStatus.ENDED_BY_USER)

    private fun floorLabel(state: LabyrinthRunState): String {
        val difficulty = state.difficulty?.toString() ?: "?"
        val floor = state.floor?.toString() ?: "?"
        return "$difficulty-$floor"
    }
}
