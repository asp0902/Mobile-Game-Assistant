package com.asp0902.mobilegameassistant.artisans

import com.asp0902.mobilegameassistant.analysis.NormalizedRect
import com.asp0902.mobilegameassistant.analysis.OcrBlock
import kotlin.math.abs
import kotlin.math.max

enum class ArtisansAction { SELECT, CONSIDER, SKIP, CHECK }

data class ArtisansCard(
    val name: String,
    val theme: String,
    val inputs: Set<String>,
    val outputs: Set<String>,
    val highValueOutput: Boolean = false,
)

data class ArtisansCandidate(
    val slotIndex: Int,
    val name: String,
    val theme: String,
    val ownedCount: Int?,
    val frameTone: String,
    val inputItems: Set<String>,
    val outputItems: Set<String>,
    val inputRange: NormalizedRect? = null,
    val outputRange: NormalizedRect? = null,
    val fullCardBounds: NormalizedRect,
    val confidence: Float,
    val source: String,
)

data class ArtisansRecommendation(
    val cardName: String,
    val action: ArtisansAction,
    val reason: String,
    val slotIndex: Int? = null,
    val fullCardBounds: NormalizedRect? = null,
    val source: String = "RULE_BASED",
)

data class ArtisansPathAnalysis(
    val round: Int?,
    val score: Int?,
    val scoreConfidence: Float?,
    val nextCheckpoint: Int?,
    val recommendations: List<ArtisansRecommendation>,
    val reasons: List<String>,
    val candidates: List<ArtisansCandidate>,
)

object ArtisansPathAdvisor {
    private const val UNKNOWN_FRAME = "UNKNOWN_FRAME"
    private const val SCORE_FROM_CANDIDATE = 0.92f
    private const val SCORE_FROM_TEXT = 0.78f

    private val cards = listOf(
        ArtisansCard("광산", "제작", emptySet(), setOf("광석")),
        ArtisansCard("광석 제련소", "제작", setOf("광석"), setOf("금속")),
        ArtisansCard("장원 수레", "제작", setOf("광석", "금속"), setOf("제작품"), true),
        ArtisansCard("원소 수집장", "연금술", emptySet(), setOf("원소")),
        ArtisansCard("원소 제련소", "연금술", setOf("원소"), setOf("물약")),
        ArtisansCard("연금술 공방", "연금술", setOf("원소", "물약"), setOf("연금책"), true),
        ArtisansCard("축제 수정 구슬", "연금술", setOf("원소"), setOf("수정"), true),
        ArtisansCard("농지", "요리", emptySet(), setOf("옥수수")),
        ArtisansCard("농장", "요리", setOf("옥수수"), setOf("요리재료")),
        ArtisansCard("주방", "요리", setOf("옥수수"), setOf("고급요리"), true),
        ArtisansCard("영롱한 트롤리", "요리", setOf("고급요리"), setOf("요리완성품"), true),
        ArtisansCard("벌목장", "목공", emptySet(), setOf("원목")),
        ArtisansCard("목재 가공소", "목공", setOf("원목"), setOf("판재")),
        ArtisansCard("나무집 노점", "목공", setOf("원목", "판재"), setOf("목공품"), true),
        ArtisansCard("장식 공방", "목공", setOf("판재"), setOf("목공품"), true),
        ArtisansCard("들판 이젤", "목공", setOf("목공품"), setOf("고급목공품"), true),
        ArtisansCard("장원 우물", "복합", setOf("제작품", "연금책", "요리완성품", "고급목공품"), setOf("복합완성품"), true),
    )

    fun analyze(text: String): ArtisansPathAnalysis? = analyze(text, emptyList())

    fun analyze(text: String, blocks: List<OcrBlock>, scoreHint: Int? = null): ArtisansPathAnalysis? {
        val candidates = parseCandidates(text, blocks)
        val hasArtisansSignal = text.contains("장인의 길")
        val recognized = candidates.mapNotNull { cardByName(it.name) }.toList()
        if (!hasArtisansSignal && recognized.isEmpty()) return null

        val normalizedText = text.replace("\\s+".toRegex(), " ").trim()
        val round = parseRound(normalizedText)
        val parsedScore = parseCurrentScore(normalizedText, blocks, round)
        val score = parsedScore?.value ?: scoreHint
        val scoreConfidence = parsedScore?.confidence
        val checkpoint = checkpointFor(round)
        val baseRecommendations = candidates.map { candidate ->
            val card = cardByName(candidate.name) ?: return@map null
            recommend(card, candidate, recognized, normalizedText).copy(slotIndex = candidate.slotIndex, fullCardBounds = candidate.fullCardBounds)
        }.filterNotNull()

        val recommendations = if (candidates.size >= 3) {
            ensureSingleSelection(baseRecommendations, candidates)
        } else {
            baseRecommendations.map {
                it.copy(
                    action = ArtisansAction.CHECK,
                    reason = "후보 3개 미인지로 확정 SELECT 보류",
                )
            }
        }

        val reasons = buildList {
            add("후보 카드 ${candidates.size}개")
            if (candidates.size < 3) add("슬롯별 후보 전체 판독 필요")
            if (score == null) add("현재 포인트 미확인")
            if (round == null) add("라운드 미확인")
            if (score != null && score >= 1000) add("점수 1000 이상 — 고득점 라운드 진행 추정")
        }

        return ArtisansPathAnalysis(
            round,
            score,
            scoreConfidence,
            checkpoint,
            recommendations,
            reasons,
            candidates,
        )
    }

    private fun parseCandidates(text: String, blocks: List<OcrBlock>): List<ArtisansCandidate> {
        if (blocks.isNotEmpty()) {
            val byRows = parseCandidatesFromRows(blocks)
            if (byRows.isNotEmpty()) return byRows

            val namedBlocks = blocks.mapNotNull { block ->
                val card = cards.firstOrNull { containsCardName(block.text, it.name) } ?: return@mapNotNull null
                block to card
            }.groupBy { it.second.name }.mapNotNull { (_, values) ->
                values.maxByOrNull { area(it.first) }?.let { it.first to it.second }
            }
            val ordered = namedBlocks
                .sortedBy { it.first.centerY }
                .mapIndexed { index, pair -> toCandidate(index, pair.second, pair.first, blocks) }
            if (ordered.isNotEmpty()) return ordered
        }

        val normalized = normalizeText(text)
        return cards.mapNotNull { card ->
            val normalizedIndex = normalized.indexOf(normalizeText(card.name))
            if (normalizedIndex < 0) null else card to normalizedIndex
        }.sortedBy { it.second }.mapIndexed { index, (card, _) ->
            ArtisansCandidate(
                slotIndex = index,
                name = card.name,
                theme = card.theme,
                ownedCount = null,
                frameTone = UNKNOWN_FRAME,
                inputItems = card.inputs,
                outputItems = card.outputs,
                fullCardBounds = fallbackCandidateBounds(index),
                confidence = 0.2f,
                source = "TEXT",
            )
        }
    }

    private fun parseCandidatesFromRows(
        blocks: List<OcrBlock>,
    ): List<ArtisansCandidate> {
        return groupByRows(blocks)
            .asSequence()
            .mapNotNull { row ->
                val card = resolveCardFromRow(row) ?: return@mapNotNull null
                row to card
            }
            .distinctBy { it.second.name }
            .take(3)
            .mapIndexed { slotIndex, (row, card) ->
                val normalizedCardName = normalizeCardText(card.name)
                val anchor = row.firstOrNull { normalizeCardText(it.text).contains(normalizedCardName) }
                    ?: row.maxByOrNull { area(it) }!!
                toCandidate(slotIndex, card, anchor, blocks)
            }
            .toList()
    }

    private fun toCandidate(
        slotIndex: Int,
        card: ArtisansCard,
        anchor: OcrBlock,
        blocks: List<OcrBlock>,
    ): ArtisansCandidate {
        val owned = parseOwnedCount(anchor, blocks)
        val local = inferCandidateBounds(anchor, blocks)
        return ArtisansCandidate(
            slotIndex = slotIndex,
            name = card.name,
            theme = card.theme,
            ownedCount = owned,
            frameTone = UNKNOWN_FRAME,
            inputItems = card.inputs,
            outputItems = card.outputs,
            inputRange = null,
            outputRange = null,
            fullCardBounds = local,
            confidence = 0.9f,
            source = "OCR_TEXT",
        )
    }

    private fun inferCandidateBounds(anchor: OcrBlock, blocks: List<OcrBlock>): NormalizedRect {
        val row = blocks.filter { abs(it.centerY - anchor.centerY) <= 0.06f }
        if (row.isNotEmpty()) {
            val left = row.minOf { it.left }.coerceIn(0f, 1f)
            val right = row.maxOf { it.right }.coerceIn(0f, 1f)
            val top = row.minOf { it.top }.coerceIn(0f, 1f)
            val bottom = row.maxOf { it.bottom }.coerceIn(0f, 1f)
            val expanded = NormalizedRect(
                left = (left - 0.015f).coerceIn(0f, 1f),
                top = (top - 0.01f).coerceIn(0f, 1f),
                right = (right + 0.015f).coerceIn(0f, 1f),
                bottom = (bottom + 0.02f).coerceIn(0f, 1f),
            )
            return expanded
        }
        return fallbackCandidateBounds(0)
    }

    private fun parseOwnedCount(anchor: OcrBlock, blocks: List<OcrBlock>): Int? {
        val nearby = blocks.filter { abs(it.centerY - anchor.centerY) <= 0.05f }
            .flatMap { block ->
                Regex("보유\\s*[:：]?\\s*(\\d+)").findAll(block.text).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            }
        return nearby.minOrNull()
    }

    private fun fallbackCandidateBounds(slotIndex: Int): NormalizedRect = when (slotIndex % 3) {
        0 -> NormalizedRect(.12f, .30f + slotIndex * .16f, .88f, .36f + slotIndex * .16f)
        1 -> NormalizedRect(.12f, .30f + slotIndex * .16f, .88f, .36f + slotIndex * .16f)
        else -> NormalizedRect(.12f, .30f + slotIndex * .16f, .88f, .36f + slotIndex * .16f)
    }

    private fun cardByName(name: String): ArtisansCard? = cards.firstOrNull { it.name == name }

    private fun normalizeText(text: String): String = text.replace("\\s+".toRegex(), "").replace("[^가-힣0-9]".toRegex(), "")

    private fun containsCardName(blockText: String, cardName: String): Boolean {
        val normalizedCard = normalizeText(cardName)
        val normalizedText = normalizeText(blockText)
        return normalizedText.contains(normalizedCard)
    }

    private fun parseRound(text: String): Int? = Regex("(?:라운드\\s*)?(\\d+)\\s*/\\s*\\d+").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: Regex("(\\d+)\\s*(?:라운드|R)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private data class ParsedScore(val value: Int, val confidence: Float)

    private fun parseCurrentScore(text: String, blocks: List<OcrBlock>, round: Int?): ParsedScore? {
        val candidate = parseScoreFromBlocks(blocks)
        if (candidate != null && candidate.value != round) return candidate
        return parseScoreFromSentence(text)?.takeIf { it.value != round } ?: if (candidate != null && round == null) candidate else null
    }

    private fun parseScoreFromBlocks(blocks: List<OcrBlock>): ParsedScore? {
        if (blocks.isEmpty()) return null
        val anchors = blocks.filter { isCurrentPointAnchor(it.text) }
        if (anchors.isEmpty()) return null
        anchors.mapNotNull { parseStrictNumber(it.text) }.firstOrNull()?.let {
            return ParsedScore(it, SCORE_FROM_CANDIDATE)
        }
        val scoreBlock = blocks
            .filter { parseStrictNumber(it.text) != null }
            .filter { block ->
                val value = parseStrictNumber(block.text) ?: return@filter false
                !isForbiddenScoreNumber(block.text, value) && !isAdjacentToForbiddenHint(block, blocks)
            }
            .mapNotNull { block ->
                val distance = anchors.minOf { anchor ->
                    abs(anchor.centerX - block.centerX) * 0.25f + abs(anchor.centerY - block.centerY)
                }
                if (distance > 0.18f) return@mapNotNull null
                val value = parseStrictNumber(block.text) ?: return@mapNotNull null
                ParsedScoreCandidate(value, distance)
            }
            .minByOrNull { it.distance }
            ?.value
        if (scoreBlock == null) return null
        return ParsedScore(scoreBlock, SCORE_FROM_CANDIDATE)
    }

    private fun parseScoreFromSentence(text: String): ParsedScore? {
        val pattern = Regex("(?:현재\\s*)?(?:점수|포인트|스코어)\\s*[:：]?\\s*([\\d,]+)")
        val value = pattern.find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull() ?: return null
        if (isForbiddenScoreNumber(value.toString(), value)) return null
        return ParsedScore(value, SCORE_FROM_TEXT)
    }

    private fun isScoreBlock(text: String): Boolean {
        val normalized = text.replace(",", "").trim()
        return normalized.matches(Regex("\\d+"))
    }

    private fun isForbiddenScoreNumber(text: String, value: Int): Boolean {
        if (value == 1000) return true
        if (text.contains("/")) return true
        if (text.contains("보유") && value in 0..9) return true
        if (text.contains("포인트") || text.contains("점")) return true
        return false
    }

    private fun isCurrentPointAnchor(text: String): Boolean {
        val normalized = normalizeCardText(text)
        return when {
            normalized.isEmpty() -> false
            normalized == "현재" -> true
            normalized == "포인트" -> true
            normalized == "스코어" -> true
            normalized.contains("현재") && (normalized.contains("점수") || normalized.contains("포인트") || normalized.contains("스코어")) -> true
            else -> false
        }
    }

    private fun parseStrictNumber(text: String): Int? {
        val trimmed = text.trim()
        if (!trimmed.matches(Regex("[\\d,]+"))) return null
        return trimmed.replace(",", "").toIntOrNull()
    }

    private fun isAdjacentToForbiddenHint(block: OcrBlock, blocks: List<OcrBlock>): Boolean {
        val forbidden = listOf("보유", "보유수", "덱", "덱수", "라운드", "카드", "보유 수", "덱 수")
        return blocks.any {
            it != block &&
                abs(it.centerY - block.centerY) <= 0.04f &&
                forbidden.any { forbiddenWord -> normalizeCardText(it.text).contains(forbiddenWord) }
        }
    }

    private fun normalizeCardText(text: String): String =
        text.replace(Regex("\\s+"), "").replace(Regex("[^가-힣0-9]"), "")

    private fun groupByRows(blocks: List<OcrBlock>): List<List<OcrBlock>> {
        val ordered = blocks.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrBlock>>()
        val tolerance = 0.028f
        for (block in ordered) {
            val target = rows.lastOrNull { row ->
                abs(row.averageCenterY() - block.centerY) <= tolerance
            }
            if (target == null) rows.add(mutableListOf(block)) else target.add(block)
        }
        return rows.map { it.sortedBy { it.left } }
    }

    private fun resolveCardFromRow(row: List<OcrBlock>): ArtisansCard? {
        val mergedText = row.joinToString("") { normalizeCardText(it.text) }
        if (mergedText.isBlank()) return null
        val exactMatch = cards
            .filter { it.name.length > 1 }
            .filter { mergedText.contains(normalizeCardText(it.name)) }
            .maxByOrNull { it.name.length }
        if (exactMatch != null) return exactMatch

        val cookingCandidates = cards
            .filter { it.theme == "요리" }
            .map { card -> card to levenshtein(mergedText, normalizeCardText(card.name)) }
            .filter { it.second == 1 }
            .map { it.first }
        return if (cookingCandidates.size == 1) cookingCandidates.first() else null
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun MutableList<OcrBlock>.averageCenterY(): Float = map { it.centerY }.average().toFloat()

    private fun recommend(
        card: ArtisansCard,
        selectedCandidate: ArtisansCandidate,
        recognized: List<ArtisansCard>,
        text: String,
    ): ArtisansRecommendation {
        val produced = recognized.flatMap { it.outputs }.toSet()
        val consumed = recognized.flatMap { it.inputs }.toSet()
        val hasInput = card.inputs.all { produced.contains(it) }
        val feedsKnownCard = card.outputs.any(consumed::contains)
        val upgraded = Regex("${Regex.escape(card.name)}.{0,20}승급").containsMatchIn(text)
        val score = score(card, recognized, text, selectedCandidate.confidence, hasInput, feedsKnownCard)
        return ArtisansRecommendation(
            cardName = card.name,
            action = when {
                score >= 55 -> ArtisansAction.SELECT
                score >= 30 -> ArtisansAction.CONSIDER
                else -> ArtisansAction.SKIP
            },
            reason = reason(card, upgraded, hasInput, feedsKnownCard),
            source = "RULE_BASED",
        )
    }

    private fun score(
        card: ArtisansCard?,
        recognized: List<ArtisansCard>,
        text: String,
        candidateConfidence: Float,
        hasInput: Boolean,
        feedsKnownCard: Boolean,
    ): Int {
        if (card == null) return 0
        val produced = recognized.flatMap { it.outputs }.toSet()
        val consumed = recognized.flatMap { it.inputs }.toSet()
        val upgraded = Regex("${Regex.escape(card.name)}.{0,20}승급").containsMatchIn(text)
        return (if (upgraded) 45 else 0) +
            (if (card.highValueOutput) 35 else 0) +
            (if (hasInput && card.inputs.isNotEmpty()) 25 else 0) +
            (if (feedsKnownCard) 20 else 0) +
            (if (card.inputs.isEmpty()) 5 else 0) +
            (if (candidateConfidence > 0.8f) 5 else 0)
    }

    private fun ensureSingleSelection(
        recommendations: List<ArtisansRecommendation>,
        candidates: List<ArtisansCandidate>,
    ): List<ArtisansRecommendation> {
        val selected = recommendations.filter { it.action == ArtisansAction.SELECT }
        if (selected.isEmpty()) {
            val best = candidates
                .maxByOrNull { recommendationScore(it, recommendations) } ?: return recommendations
            val cardName = best.name
            return recommendations.map {
                if (it.cardName == cardName) it else it.copy(action = ArtisansAction.CHECK, reason = "근거 충돌 — 후보 비교 필요")
            }.map {
                if (it.cardName == cardName) it.copy(
                    action = ArtisansAction.SELECT,
                    reason = "현재 세 후보 후보 1위 — ${it.reason}",
                ) else it
            }
        }
        if (selected.size == 1) return recommendations
        val best = selected.maxByOrNull { recommendationScoreByName(it.cardName, candidates, recommendations) } ?: return recommendations
        return recommendations.map { recommendation ->
            if (recommendation.action == ArtisansAction.SELECT && recommendation.cardName != best.cardName) {
                recommendation.copy(
                    action = ArtisansAction.CONSIDER,
                    reason = "중복 SELECT 보정: ${recommendation.reason}",
                )
            } else {
                recommendation
            }
        }
    }

    private fun recommendationScore(candidate: ArtisansCandidate, recommendations: List<ArtisansRecommendation>): Int {
        return recommendations.firstOrNull { it.cardName == candidate.name }?.let {
            if (it.action == ArtisansAction.SELECT) 100
            else if (it.action == ArtisansAction.CONSIDER) 30
            else 0
        } ?: 0
    }

    private fun recommendationScoreByName(cardName: String, candidates: List<ArtisansCandidate>, recommendations: List<ArtisansRecommendation>): Int {
        val candidate = candidates.firstOrNull { it.name == cardName } ?: return 0
        return recommendationScore(candidate, recommendations) + (candidate.ownedCount ?: 0) * 2
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

    private fun area(block: OcrBlock): Float = max(block.right - block.left, 0f) * max(block.bottom - block.top, 0f)

    private data class ParsedScoreCandidate(val value: Int, val distance: Float)
}
