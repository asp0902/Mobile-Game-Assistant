package com.asp0902.mobilegameassistant.artisans

enum class ArtisansAction { SELECT, CONSIDER, SKIP, CHECK }

data class ArtisansCard(
    val name: String,
    val inputs: Set<String>,
    val outputs: Set<String>,
    val highValueOutput: Boolean = false,
)

data class ArtisansRecommendation(
    val cardName: String,
    val action: ArtisansAction,
    val reason: String,
)

data class ArtisansPathAnalysis(
    val round: Int?,
    val score: Int?,
    val nextCheckpoint: Int?,
    val recommendations: List<ArtisansRecommendation>,
    val reasons: List<String>,
)

object ArtisansPathAdvisor {
    private val cards = listOf(
        ArtisansCard("광산", emptySet(), setOf("광석")),
        ArtisansCard("광석 제련소", setOf("광석"), setOf("금속")),
        ArtisansCard("장원 수레", setOf("광석", "금속"), setOf("제작품"), true),
        ArtisansCard("원소 수집장", emptySet(), setOf("원소")),
        ArtisansCard("원소 제련소", setOf("원소"), setOf("물약")),
        ArtisansCard("연금술 공방", setOf("원소", "물약"), setOf("연금책"), true),
        ArtisansCard("축제 수정 구슬", setOf("원소"), setOf("수정"), true),
        ArtisansCard("농지", emptySet(), setOf("옥수수")),
        ArtisansCard("농장", setOf("옥수수"), setOf("요리재료")),
        ArtisansCard("주방", setOf("옥수수"), setOf("고급요리"), true),
        ArtisansCard("영롱한 트롤리", setOf("고급요리"), setOf("요리완성품"), true),
        ArtisansCard("벌목장", emptySet(), setOf("원목")),
        ArtisansCard("목재 가공소", setOf("원목"), setOf("판재")),
        ArtisansCard("나무집 노점", setOf("원목", "판재"), setOf("목공품"), true),
        ArtisansCard("장식 공방", setOf("판재"), setOf("목공품"), true),
        ArtisansCard("들판 이젤", setOf("목공품"), setOf("고급목공품"), true),
        ArtisansCard("장원 우물", setOf("제작품", "연금책", "요리완성품", "고급목공품"), setOf("복합완성품"), true),
    )

    fun analyze(text: String): ArtisansPathAnalysis? {
        val recognized = cards.filter { text.contains(it.name) }
        if (!text.contains("장인의 길") && recognized.isEmpty()) return null
        val round = Regex("(?:라운드\\s*)?(\\d+)\\s*/\\s*\\d+").find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)\\s*(?:라운드|R)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val score = Regex("(?:현재\\s*)?(?:점수|포인트|스코어)\\s*[:：]?\\s*([\\d,]+)").find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
        val checkpoint = checkpointFor(round)
        val recommendations = ensureSingleSelection(
            recognized.map { card -> recommend(card, recognized, text) },
            recognized,
        )
        val reasons = buildList {
            add("확인 카드 ${recognized.size}개")
            if (checkpoint != null && score != null && score < checkpoint) add("체크포인트 ${checkpoint}점 미달 — 즉시 점수 우선")
            if (recognized.isEmpty()) add("후보 카드명 또는 소비→생산 OCR 확인 필요")
        }
        return ArtisansPathAnalysis(round, score, checkpoint, recommendations, reasons)
    }

    private fun recommend(card: ArtisansCard, recognized: List<ArtisansCard>, text: String): ArtisansRecommendation {
        val produced = recognized.flatMap { it.outputs }.toSet()
        val consumed = recognized.flatMap { it.inputs }.toSet()
        val hasInput = card.inputs.all(produced::contains)
        val feedsKnownCard = card.outputs.any(consumed::contains)
        val upgraded = Regex("${Regex.escape(card.name)}.{0,20}승급").containsMatchIn(text)
        val score = (if (upgraded) 45 else 0) + (if (card.highValueOutput) 35 else 0) +
            (if (hasInput) 25 else 0) + (if (feedsKnownCard) 20 else 0) + (if (card.inputs.isEmpty()) 5 else 0)
        return when {
            score >= 55 -> ArtisansRecommendation(card.name, ArtisansAction.SELECT, reason(card, upgraded, hasInput, feedsKnownCard))
            score >= 30 -> ArtisansRecommendation(card.name, ArtisansAction.CONSIDER, reason(card, upgraded, hasInput, feedsKnownCard))
            else -> ArtisansRecommendation(card.name, ArtisansAction.SKIP, "현재 화면에서 입력 공급 또는 후속 소비처가 확인되지 않음")
        }
    }

    private fun ensureSingleSelection(
        recommendations: List<ArtisansRecommendation>,
        recognized: List<ArtisansCard>,
    ): List<ArtisansRecommendation> {
        if (recommendations.any { it.action == ArtisansAction.SELECT }) return recommendations
        val produced = recognized.flatMap { it.outputs }.toSet()
        val consumed = recognized.flatMap { it.inputs }.toSet()
        val best = recognized.maxByOrNull { card ->
            when {
                card.inputs.isNotEmpty() && card.inputs.all(produced::contains) -> 100
                card.outputs.any(consumed::contains) -> 80
                card.inputs.isEmpty() -> 40
                card.highValueOutput -> 20
                else -> 0
            }
        } ?: return recommendations
        return recommendations.map {
            if (it.cardName == best.name) it.copy(
                action = ArtisansAction.SELECT,
                reason = "현재 세 후보 중 생산망 연결 우선순위 1위 — ${it.reason}",
            ) else it
        }
    }

    private fun reason(card: ArtisansCard, upgraded: Boolean, hasInput: Boolean, feedsKnownCard: Boolean): String = buildList {
        if (upgraded) add("보유 카드 승급")
        if (card.highValueOutput) add("고가 후단")
        if (hasInput && card.inputs.isNotEmpty()) add("입력 공급 확인")
        if (feedsKnownCard) add("후속 카드 연결")
        if (card.inputs.isEmpty()) add("원료 생산")
    }.ifEmpty { listOf("생산망 확인 필요") }.joinToString(" · ")

    private fun checkpointFor(round: Int?): Int? = when {
        round == null -> null
        round <= 6 -> 1_000
        round <= 12 -> 6_000
        round <= 18 -> 30_000
        else -> null
    }
}
