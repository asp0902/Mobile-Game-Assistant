package com.asp0902.mobilegameassistant.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedBossGateTest {
    @Test fun requiresBossAndBattleEvidenceWithoutCrossModeLeakage() {
        assertTrue(LearnedBossGate.matches("망령의 소굴 엘리트 몬스터 전투"))
        assertTrue(LearnedBossGate.matches("망령의 소굴 아군 적군"))
        assertFalse(LearnedBossGate.matches("망령의 소굴"))
        assertFalse(LearnedBossGate.matches("전투 종료 98.0% 505M"))
        assertFalse(LearnedBossGate.matches("이계의 미궁 망령의 소굴 아군 적군"))
        assertFalse(LearnedBossGate.matches("명예의 결투 망령의 소굴 엘리트 몬스터"))
    }
}
