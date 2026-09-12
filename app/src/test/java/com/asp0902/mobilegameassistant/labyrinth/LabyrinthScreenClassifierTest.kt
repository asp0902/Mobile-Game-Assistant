package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.analysis.ScreenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #39 화면 판별 회귀.
 *
 * 지문은 모두 사용자 캡처에서 직접 읽은 문구다. 확인하지 못한 화면 제목은 여기 없다.
 */
class LabyrinthScreenClassifierTest {

    /** 5-1 첫 화면. HUD 에 `난이도 5` / `1/15`, 하단에 `탐색 종료`, 노드는 아이템 게이트 하나. */
    private val itemGateScreen =
        "161K +0% 난이도 5 1/15 50 아이템 게이트 적 처치 시 획득 가능한 아이템: 부활 포션 " +
            "효과: 사용 후 지정 영웅 1명을 부활시키고, 에너지를 전부 회복한다. 영웅 탐색 종료"

    @Test
    fun readsFloorAndDifficultyFromHud() {
        val signals = LabyrinthScreenClassifier.classify(itemGateScreen).signals
        assertEquals(Integer.valueOf(1), signals.floor)
        assertEquals(Integer.valueOf(15), signals.totalFloors)
        assertEquals(Integer.valueOf(5), signals.difficulty)
    }

    @Test
    fun classifiesItemGateWithHighConfidence() {
        val result = LabyrinthScreenClassifier.classify(itemGateScreen)
        assertEquals(ScreenType.LABYRINTH_ITEM_GATE, result.classification.type)
        assertTrue(result.classification.confidence > LabyrinthScreenClassifier.LOW_CONFIDENCE)
        // 판별 근거를 디버그에서 볼 수 있어야 한다.
        assertTrue(result.classification.reasons.any { it.contains("난이도") })
        assertTrue(result.classification.reasons.any { it.contains("탐색 종료") })
    }

    /** 선택지가 하나뿐인 화면은 후보도 하나로 남아야 경쟁 추천이 만들어지지 않는다. */
    @Test
    fun singleOptionScreenOffersExactlyOneChoice() {
        val signals = LabyrinthScreenClassifier.classify(itemGateScreen).signals
        assertEquals(listOf("아이템 게이트"), signals.offeredChoices)
    }

    @Test
    fun twoNodesBecomePathSelect() {
        val result = LabyrinthScreenClassifier.classify(
            "난이도 5 3/15 213 퓨어 크리스탈의 문 피츠 상점 탐색 종료",
        )
        assertEquals(ScreenType.LABYRINTH_PATH_SELECT, result.classification.type)
        assertEquals(listOf("퓨어 크리스탈의 문", "피츠 상점"), result.classification.let {
            LabyrinthScreenClassifier.classify("난이도 5 3/15 213 퓨어 크리스탈의 문 피츠 상점 탐색 종료").signals.offeredChoices
        })
    }

    @Test
    fun recognizesFitzShopByProducts() {
        val result = LabyrinthScreenClassifier.classify(
            "난이도 5 3/15 157 건강 50 수호 50 저금통 75 부활 포션 75 탐색 종료",
        )
        assertEquals(ScreenType.LABYRINTH_FITZ_SHOP, result.classification.type)
    }

    @Test
    fun recognizesSigilSelect() {
        val result = LabyrinthScreenClassifier.classify(
            "난이도 5 5/15 제국 팔찌 힘의 조류 부패의 대가 탐색 종료",
        )
        assertEquals(ScreenType.LABYRINTH_SIGIL_SELECT, result.classification.type)
    }

    @Test
    fun recognizesRelicGate() {
        val result = LabyrinthScreenClassifier.classify(
            "난이도 5 1/15 칼날 공격 속도 10 증가 수호 물리·마법 방어력 10% 증가 탐색 종료",
        )
        assertEquals(ScreenType.LABYRINTH_RELIC_GATE, result.classification.type)
    }

    /** 전투 결과 화면에는 미궁 HUD 가 없다. 문맥 신호가 없으면 확인 필요 수준으로 낮춘다. */
    @Test
    fun battleResultWithoutContextStaysLowConfidence() {
        val result = LabyrinthScreenClassifier.classify("전투 승리 출전 영웅 100 레벨 리플레이 상세 정보 재시도")
        assertEquals(ScreenType.LABYRINTH_BATTLE_RESULT, result.classification.type)
        assertEquals(LabyrinthScreenClassifier.LOW_CONFIDENCE, result.classification.confidence, 0.001f)
    }

    // ---- 미궁이 아닌 화면 ----

    @Test
    fun honorDuelScreenIsNotLabyrinth() {
        val result = LabyrinthScreenClassifier.classify("결투 상점 3 휘장 1200 영웅 구매")
        assertEquals(ScreenType.OTHER, result.classification.type)
        assertNull(result.signals.floor)
    }

    @Test
    fun artisansScreenIsNotLabyrinth() {
        val result = LabyrinthScreenClassifier.classify("장인의 길 라운드 6/24 현재 포인트 2922 보상 1개 선택 벌목장")
        assertEquals(ScreenType.OTHER, result.classification.type)
        // 6/24 는 15층 표시가 아니므로 층으로 읽지 않는다.
        assertNull(result.signals.floor)
    }

    @Test
    fun emptyScreenIsOther() {
        assertEquals(ScreenType.OTHER, LabyrinthScreenClassifier.classify("").classification.type)
    }

    /** 미궁 문맥은 있는데 아는 화면이 아니면 확정하지 않는다. */
    @Test
    fun unknownLabyrinthScreenStaysUnconfirmed() {
        val result = LabyrinthScreenClassifier.classify("난이도 5 7/15 탐색 종료")
        assertEquals(ScreenType.LABYRINTH_UNKNOWN, result.classification.type)
        assertTrue(result.classification.confidence <= LabyrinthScreenClassifier.LOW_CONFIDENCE)
    }

    /** Golden 의 노드 이름이 실제로 판별되는지 확인한다. */
    @Test
    fun goldenNodesAreClassifiable() {
        val nodes = LabyrinthGolden.DIFFICULTY5_RUN
            .filter { it.screen == "경로 선택" }
            .flatMap { it.offered }
            .distinct()
        nodes.forEach { node ->
            val result = LabyrinthScreenClassifier.classify("난이도 5 1/15 $node 탐색 종료")
            assertTrue("$node 판별 실패", result.classification.type != ScreenType.LABYRINTH_UNKNOWN)
        }
    }
}
