package com.asp0902.mobilegameassistant.labyrinth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabyrinthChoiceAdvisorTest {
    @Test
    fun keepsLearnedRunDecisionsStable() {
        val mark = requireNotNull(LabyrinthChoiceAdvisor.analyze(
            "난이도 5 15/15 가속 중첩 속결 표시 보호의 예술 낙인 선택",
        ))
        assertEquals("보호의 예술", mark.choice)
        assertTrue(mark.message.contains("새로고침 25 크리스탈: 사용하지 않음"))

        val spell = requireNotNull(LabyrinthChoiceAdvisor.analyze(
            "난이도 5 16/20 성운술 계몽술 소생술 구속술 수정 방어전",
        ))
        assertEquals("구속술", spell.choice)

        val formation = requireNotNull(LabyrinthChoiceAdvisor.analyze("수정 방어전"))
        assertTrue(formation.message.contains("데이먼"))
        assertTrue(formation.message.contains("스모키와 미르키 후열 중앙"))

        val boss = requireNotNull(LabyrinthChoiceAdvisor.analyze("난이도 5 15/15 보스 탐색 종료"))
        assertEquals("단일 선택지: 보스\n비교 추천 없이 진행", boss.message)
    }
}
