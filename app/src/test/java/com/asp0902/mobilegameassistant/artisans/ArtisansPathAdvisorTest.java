package com.asp0902.mobilegameassistant.artisans;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArtisansPathAdvisorTest {
    @Test public void prefersConnectedHighValueChain() {
        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze("장인의 길 12라운드 점수 5000 광산 광석 제련소 장원 수레");
        assertEquals(Integer.valueOf(6000), analysis.getNextCheckpoint());
        assertTrue(analysis.getRecommendations().stream().anyMatch(it -> it.getCardName().equals("장원 수레") && it.getAction() == ArtisansAction.SELECT));
    }

    @Test public void readsCurrentPointAndRoundFraction() {
        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze("장인의 길 라운드 1/24 현재 포인트 51 광산 원소 수집장 연금술 공방");
        assertEquals(Integer.valueOf(1), analysis.getRound());
        assertEquals(Integer.valueOf(51), analysis.getScore());
        assertEquals(3, analysis.getRecommendations().size());
    }
}
