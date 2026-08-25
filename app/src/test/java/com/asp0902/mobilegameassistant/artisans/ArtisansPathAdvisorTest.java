package com.asp0902.mobilegameassistant.artisans;

import com.asp0902.mobilegameassistant.analysis.OcrBlock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.asp0902.mobilegameassistant.analysis.OcrBlock;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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
        assertEquals(1, analysis.getRecommendations().stream().filter(it -> it.getAction() == ArtisansAction.SELECT).count());
        assertTrue(analysis.getRecommendations().stream().anyMatch(it -> it.getCardName().equals("원소 수집장") && it.getAction() == ArtisansAction.SELECT));
    }

    @Test public void parsesScoreAndCandidatesFromSeparateBlocks() {
        String text = "장인의 길 라운드 1/24 현재 포인트 광산 원소 수집장 연금술 공방";
        List<OcrBlock> blocks = Arrays.asList(
            new OcrBlock("현재 포인트", 0.32f, 0.05f, 0.58f, 0.09f),
            new OcrBlock("51", 0.61f, 0.05f, 0.66f, 0.09f),
            new OcrBlock("1/24", 0.02f, 0.03f, 0.20f, 0.09f),
            new OcrBlock("1000포인트", 0.74f, 0.03f, 0.95f, 0.09f),
            new OcrBlock("광산", 0.18f, 0.32f, 0.40f, 0.36f),
            new OcrBlock("보유:1", 0.05f, 0.37f, 0.16f, 0.40f),
            new OcrBlock("원소 수집장", 0.18f, 0.49f, 0.72f, 0.53f),
            new OcrBlock("보유:1", 0.05f, 0.54f, 0.16f, 0.56f),
            new OcrBlock("연금술 공방", 0.18f, 0.66f, 0.75f, 0.70f),
            new OcrBlock("보유:0", 0.05f, 0.71f, 0.16f, 0.74f)
        );

        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze(text, blocks, null);
        assertEquals(Integer.valueOf(1), analysis.getRound());
        assertEquals(Integer.valueOf(51), analysis.getScore());
        assertEquals(3, analysis.getCandidates().size());
        assertEquals(3, analysis.getRecommendations().size());
        assertEquals("원소 수집장", analysis.getRecommendations().stream()
            .filter(it -> it.getAction() == ArtisansAction.SELECT)
            .findFirst()
            .map(ArtisansRecommendation::getCardName)
            .orElse(""));
        assertEquals(0, analysis.getCandidates().get(0).getSlotIndex());
        assertEquals(1, analysis.getCandidates().get(1).getSlotIndex());
        assertEquals(2, analysis.getCandidates().get(2).getSlotIndex());
        assertEquals(Integer.valueOf(1), analysis.getCandidates().get(0).getOwnedCount());
        assertEquals(Integer.valueOf(1), analysis.getCandidates().get(1).getOwnedCount());
        assertEquals(Integer.valueOf(0), analysis.getCandidates().get(2).getOwnedCount());
        assertTrue(analysis.getCandidates().get(0).getFullCardBounds().getBottom() <= analysis.getCandidates().get(1).getFullCardBounds().getTop());
        assertTrue(analysis.getCandidates().get(1).getFullCardBounds().getBottom() <= analysis.getCandidates().get(2).getFullCardBounds().getTop());
        assertNotNull(analysis.getRecommendations().stream().filter(it -> it.getAction() == ArtisansAction.SELECT).findFirst().get().getFullCardBounds());
    }

    @Test public void keepsOnlyOneSelectWhenMultipleCandidatesQualify() {
        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze("장인의 길 라운드 1/24 현재 포인트 51 광산 광석 제련소 장원 수레 원소 수집장 원소 제련소 연금술 공방");
        assertEquals(5, analysis.getRecommendations().size());
        assertEquals(1, analysis.getRecommendations().stream().filter(it -> it.getAction() == ArtisansAction.SELECT).count());
    }

    @Test public void parsesRound2Score99AndSplitCookingCardNameFromSeparateBlocks() {
        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze(
            "장인의 길 2/24 현재 포인트 99",
            java.util.List.of(
                new OcrBlock("2/24", 0.05f, 0.05f, 0.10f, 0.09f),
                new OcrBlock("현재", 0.52f, 0.05f, 0.58f, 0.09f),
                new OcrBlock("포인트", 0.59f, 0.05f, 0.67f, 0.09f),
                new OcrBlock("99", 0.70f, 0.05f, 0.74f, 0.09f),
                new OcrBlock("1000", 0.76f, 0.05f, 0.82f, 0.09f),
                new OcrBlock("보유", 0.05f, 0.12f, 0.12f, 0.16f),
                new OcrBlock("수", 0.13f, 0.12f, 0.17f, 0.16f),
                new OcrBlock("3", 0.18f, 0.12f, 0.21f, 0.16f),
                new OcrBlock("덱", 0.23f, 0.12f, 0.26f, 0.16f),
                new OcrBlock("수", 0.27f, 0.12f, 0.31f, 0.16f),
                new OcrBlock("2", 0.32f, 0.12f, 0.35f, 0.16f),
                new OcrBlock("벌목장", 0.16f, 0.30f, 0.30f, 0.34f),
                new OcrBlock("나무집", 0.16f, 0.38f, 0.28f, 0.42f),
                new OcrBlock("노점", 0.29f, 0.38f, 0.40f, 0.42f),
                new OcrBlock("영롱한", 0.16f, 0.45f, 0.28f, 0.49f),
                new OcrBlock("트롤리", 0.29f, 0.45f, 0.42f, 0.49f)
            )
        );
        assertEquals(Integer.valueOf(2), analysis.getRound());
        assertEquals(Integer.valueOf(99), analysis.getScore());
        assertEquals(3, analysis.getRecommendations().size());
        assertEquals(1, analysis.getRecommendations().stream().filter(it -> it.getAction() == ArtisansAction.SELECT).count());
        assertTrue(analysis.getRecommendations().stream().anyMatch(it -> it.getCardName().equals("벌목장")));
        assertTrue(analysis.getRecommendations().stream().anyMatch(it -> it.getCardName().equals("나무집 노점")));
        assertTrue(analysis.getRecommendations().stream().anyMatch(it -> it.getCardName().equals("영롱한 트롤리")));
    }

    @Test public void requiresThreeCandidatesToIssueSelect() {
        ArtisansPathAnalysis analysis = ArtisansPathAdvisor.INSTANCE.analyze("장인의 길 라운드 1/24 현재 포인트 51 광산 원소 수집장");
        assertEquals(Integer.valueOf(1), analysis.getRound());
        assertEquals(Integer.valueOf(51), analysis.getScore());
        assertEquals(2, analysis.getRecommendations().size());
        assertEquals(0, analysis.getRecommendations().stream().filter(it -> it.getAction() == ArtisansAction.SELECT).count());
        assertTrue(analysis.getRecommendations().stream().allMatch(it -> it.getAction() == ArtisansAction.SKIP));
    }
}
