package com.asp0902.mobilegameassistant.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class HonorDuelScreenClassifierTest {
    @Test
    public void recognizesStartScreenWithoutConfusingOtherScreens() {
        ScreenClassification fallback = HonorDuelScreenClassifier.INSTANCE.classify(false, true, null, 4);
        String[] captures = {
            "시즌 영웅 랭킹 대전 기록 명예의 결투 누적 포인트(랭킹 81) 235 축복 열쇠 지금 시작 1",
            "명예의\n결투 지금\n시작",
            "대전 기록 누적 포인트 235 축복 열쇠 지금 시작"
        };
        for (String capture : captures) {
            assertEquals(ScreenType.HONOR_DUEL_START,
                    HonorDuelScreenClassifier.INSTANCE.classifyNonShop(capture, fallback).getType());
        }
        for (String unrelated : new String[] {"지금 시작", "명예의 결투 대전 기록", "랭킹 지금 시작"}) {
            assertEquals(ScreenType.UNKNOWN,
                    HonorDuelScreenClassifier.INSTANCE.classifyNonShop(unrelated, fallback).getType());
        }
        assertEquals(ScreenType.HONOR_DUEL_BATTLE_RESULT,
                HonorDuelScreenClassifier.INSTANCE.classifyNonShop("명예의 결투 종료 전투 승리", fallback).getType());
    }

    @Test
    public void detectsInitialFormationByTitle() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "초기 진형을 선택하세요!",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION, result.getType());
    }

    @Test
    public void detectsInitialFormationByTitleWithNoisyWhitespace() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "초 기 진 형 을  선 택  하 세 요 !",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION, result.getType());
    }

    @Test
    public void detectsInitialFormationBySelectionStructureWithoutTitle() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "반신 영웅을 소환해 함께 전투 영웅 초상화 60 선택 신성한 소환 영웅 초상화 60 선택 랜덤 진형 물음표 카드 70 선택",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION, result.getType());
    }

    @Test
    public void confirmsShopOnlyWithMultipleSignals() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classify(true, true, 2, 8);

        assertEquals(ScreenType.HONOR_DUEL_SHOP, result.getType());
    }

    @Test
    public void marksPartialShopEvidenceUnknown() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classify(false, true, null, 0);

        assertEquals(ScreenType.UNKNOWN, result.getType());
    }

    @Test
    public void prioritizesHeroDetailPopup() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "발렌 레오프론 전사 물리 공격 사정거리 1",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));

        assertEquals(ScreenType.HERO_DETAIL_POPUP, result.getType());
    }

    @Test
    public void detectsExactArtisansTitle() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "장인의 길",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.ARTISANS_PATH_CARD_SELECTION, result.getType());
    }

    @Test
    public void detectsTitlelessEmptyBoardAsArtisansByStructure() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "라운드 1/24 현재 포인트 0 카드 덱 0 시작 1000포인트 달성 시 6라운드에 추가 건물 획득",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.ARTISANS_PATH_CARD_SELECTION, result.getType());
    }

    @Test
    public void detectsArtisansWithWhitespaceVariation() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "라운드 1 / 24 카드 덱 0",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.ARTISANS_PATH_CARD_SELECTION, result.getType());
    }

    @Test
    public void doesNotClassifyRoundOnlyAsArtisans() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "라운드 1/24",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.OTHER, result.getType());
        assertFalse(result.getReasons().contains("장인의 길 구조 신호"));
    }

    @Test
    public void doesNotClassifyOnlySelectionSignalForOtherScreens() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "진형 관리",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertNotEquals(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION, result.getType());
    }

    @Test
    public void doesNotClassifyCurrentPointOnlyAsArtisans() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "현재 포인트 0",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));
        assertEquals(ScreenType.OTHER, result.getType());
        assertFalse(result.getReasons().contains("장인의 길 구조 신호"));
    }

    @Test
    public void artisansSignalsOverrideShopFallback() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "라운드 1/24 현재 포인트 0 카드 덱 0",
                new ScreenClassification(ScreenType.HONOR_DUEL_SHOP, 0.85f, java.util.Collections.singletonList("상점 증거 불충분")));
        assertEquals(ScreenType.ARTISANS_PATH_CARD_SELECTION, result.getType());
    }

    @Test
    public void detectsDeploymentTimer() {
        ScreenClassification result = HonorDuelScreenClassifier.INSTANCE.classifyNonShop(
                "확인 (56초)",
                new ScreenClassification(ScreenType.OTHER, .9f, java.util.Collections.emptyList()));

        assertEquals(ScreenType.HONOR_DUEL_BATTLE_DEPLOYMENT, result.getType());
    }
}
