package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.analysis.OcrBlock
import com.asp0902.mobilegameassistant.analysis.ScreenClassification
import com.asp0902.mobilegameassistant.analysis.ScreenType

/**
 * 이계의 미궁 화면 판별 (#39).
 *
 * **제목 하나로 확정하지 않는다.** 미궁 문맥 신호(층 표시 · 난이도 · 탐색 종료 버튼)와
 * 화면별 어휘를 함께 본다. 신호가 하나뿐이면 confidence 를 낮춰 공격적인 추천을 막는다.
 *
 * 여기 쓰인 앵커는 모두 사용자 캡처에서 직접 읽은 것이다. 확인하지 못한 화면 제목은 넣지 않았고,
 * 그런 화면은 [ScreenType.LABYRINTH_UNKNOWN] 으로 남는다.
 */
object LabyrinthScreenClassifier {

    /** 화면에서 같이 뽑아 두는 값. #40 RunState 가 그대로 받는다. */
    data class Signals(
        val floor: Int? = null,
        val totalFloors: Int? = null,
        val difficulty: Int? = null,
        /** 화면에 보인 선택지. **추천도 실제 선택도 아니다.** */
        val offeredChoices: List<String> = emptyList(),
    )

    data class Result(
        val classification: ScreenClassification,
        val signals: Signals,
    )

    /** `1/15` 처럼 정규 구간 층 표시. 심층은 15를 넘는다. */
    private val floorPattern = Regex("(\\d{1,2})\\s*/\\s*(\\d{1,2})")
    private val difficultyPattern = Regex("난이도\\s*(\\d{1,2})")

    /** 캡처에서 이름을 직접 읽은 노드. 확인하지 못한 노드는 넣지 않는다. */
    private val nodeNames = listOf("아이템 게이트", "퓨어 크리스탈의 문", "피츠 상점")

    /** 5-1 유물의 문에서 이름과 효과를 함께 읽은 유물. */
    private val relicNames = listOf("칼날", "수호")

    /** 5-5 낙인 후보. */
    private val sigilNames = listOf("제국 팔찌", "힘의 조류", "부패의 대가")

    /** 5-3 피츠 상점 상품. */
    private val shopItems = listOf("건강", "저금통", "부활 포션")

    fun classify(text: String, blocks: List<OcrBlock> = emptyList()): Result {
        val compact = text.replace(" ", "")
        val signals = signals(text, compact)
        val context = contextSignals(compact, signals)

        val offered = signals.offeredChoices
        val screen = when {
            compact.contains("전투승리") || compact.contains("전투패배") ->
                decide(ScreenType.LABYRINTH_BATTLE_RESULT, context, "전투 결과 문구", battleEvidence(compact))

            compact.contains("탐색종료") && compact.contains("결산") ->
                decide(ScreenType.LABYRINTH_RUN_SETTLEMENT, context, "결산 문구")

            sigilNames.count { compact.contains(it.replace(" ", "")) } >= 2 ->
                decide(ScreenType.LABYRINTH_SIGIL_SELECT, context, "낙인 후보 2종 이상")

            shopItems.count { compact.contains(it.replace(" ", "")) } >= 2 ->
                decide(ScreenType.LABYRINTH_FITZ_SHOP, context, "상점 상품 2종 이상")

            relicNames.count { compact.contains(it) } >= 2 ->
                decide(ScreenType.LABYRINTH_RELIC_GATE, context, "유물 후보 2종 이상")

            offered.size >= 2 -> decide(ScreenType.LABYRINTH_PATH_SELECT, context, "노드 후보 ${offered.size}개")

            offered.size == 1 -> decide(nodeScreen(offered.single()), context, "노드 ${offered.single()}")

            compact.contains("출전영웅") -> decide(ScreenType.LABYRINTH_BATTLE_DEPLOY, context, "출전 영웅 표기")

            context.isEmpty() -> ScreenClassification(ScreenType.OTHER, 0.9f, listOf("미궁 신호 없음"))

            else -> ScreenClassification(ScreenType.LABYRINTH_UNKNOWN, 0.4f, context + "미궁 문맥은 있으나 화면 미확정")
        }
        return Result(screen, signals)
    }

    private fun nodeScreen(node: String): ScreenType = when (node) {
        "아이템 게이트" -> ScreenType.LABYRINTH_ITEM_GATE
        "퓨어 크리스탈의 문" -> ScreenType.LABYRINTH_CRYSTAL_GATE
        "피츠 상점" -> ScreenType.LABYRINTH_FITZ_SHOP
        else -> ScreenType.LABYRINTH_UNKNOWN
    }

    /**
     * 신호 개수로 confidence 를 정한다.
     *
     * 미궁 문맥 없이 화면 어휘만 잡히면 다른 콘텐츠일 수 있다. 그때는 확인 필요 수준으로 낮춘다.
     */
    private fun decide(type: ScreenType, context: List<String>, vararg reasons: String): ScreenClassification {
        val evidence = context + reasons.filter { it.isNotBlank() }
        val confidence = if (context.isEmpty()) LOW_CONFIDENCE else if (evidence.size >= 3) 0.92f else 0.8f
        return ScreenClassification(type, confidence, evidence)
    }

    private fun battleEvidence(compact: String): String = if (compact.contains("출전영웅")) "출전 영웅 목록" else ""

    /** 미궁임을 가리키는 문맥 신호. 하나도 없으면 다른 콘텐츠로 본다. */
    private fun contextSignals(compact: String, signals: Signals): List<String> = buildList {
        if (signals.difficulty != null) add("난이도 ${signals.difficulty}")
        if (signals.floor != null) add("층 ${signals.floor}/${signals.totalFloors}")
        if (compact.contains("탐색종료")) add("탐색 종료 버튼")
    }

    private fun signals(text: String, compact: String): Signals {
        val floor = floorPattern.find(text)
            ?.takeIf { it.groupValues[2].toIntOrNull() == LabyrinthRules.REGULAR_FLOORS }
        return Signals(
            floor = floor?.groupValues?.get(1)?.toIntOrNull(),
            totalFloors = floor?.groupValues?.get(2)?.toIntOrNull(),
            difficulty = difficultyPattern.find(text)?.groupValues?.get(1)?.toIntOrNull(),
            offeredChoices = nodeNames.filter { compact.contains(it.replace(" ", "")) },
        )
    }

    /** 이 값 이하이면 추천을 만들지 않고 확인 필요로 표시한다. */
    const val LOW_CONFIDENCE = 0.45f
}
