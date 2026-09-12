package com.asp0902.mobilegameassistant.analysis

import kotlin.math.abs

// Verified user captures, documented in initial-formation-learning-20260907.md.
// Roles are identity metadata. Rarity belongs to the current offer, never this table.
object InitialFormationKnowledge {
    data class Hero(val id: String, val name: String, val faction: String, val role: String)
    val heroes = listOf(
        Hero("perseus", "페르세우스", "레오프론", "전사"),
        Hero("dilgrey", "딜그레이", "레오프론", "마법사"),
        Hero("borasia", "보라시아", "트라이브", "마법사"),
        Hero("cassadia", "카세디아", "레오프론", "마법사"),
        Hero("kazim", "카짐", "트라이브", "사수"),
        Hero("smokey_mirky", "스모키와 미르키", "트라이브", "서포터"),
        Hero("mei", "메이", "와일더스", "레인저"),
        Hero("lubomir", "루보미르", "그레이브본", "서포터"),
        Hero("florabelle", "프라벨", "와일더스", "전사"),
    )
    val artifactHeadlines = mapOf(
        "신성한 소환" to "반신영웅을소환해함께전투",
        "고블린 가면" to "속전속결전술",
        "평정의 샘물" to "와일더스연맹영웅중심",
    )
    fun hero(name: String?) = heroes.firstOrNull { it.name == name }
    fun compact(text: String) = text.replace(Regex("[^가-힣A-Za-z0-9]"), "")
}

object InitialFormationLayout {
    data class Card(val bounds: NormalizedRect, val button: NormalizedRect, val portraits: List<NormalizedRect>)

    fun cards(blocks: List<OcrBlock>, viewport: GameViewport): List<Card> {
        val width = viewport.right - viewport.left
        if (width <= 0f) return emptyList()
        val anchors = blocks.filter {
            InitialFormationKnowledge.compact(it.text) == "선택" &&
                it.centerX > viewport.left + width * .65f && it.top >= viewport.top &&
                it.bottom <= viewport.bottom && it.right > it.left && it.bottom > it.top
        }.sortedBy { it.centerY }.fold(mutableListOf<OcrBlock>()) { list, block ->
            if (list.lastOrNull()?.let { abs(it.centerY - block.centerY) > .015f } != false) list.add(block)
            list
        }
        if (anchors.size !in 2..4) return emptyList()
        val gaps = anchors.zipWithNext { a, b -> b.centerY - a.centerY }.sorted()
        val gap = gaps[gaps.size / 2]
        if (gap !in .06f.. .23f || gaps.any { abs(it - gap) > gap * .2f }) return emptyList()
        // ponytail: measured vertical offer layout, anchored to buttons and viewport.
        // Add another layout only with a capture demonstrating a different arrangement.
        return anchors.mapNotNull { anchor ->
            val top = anchor.centerY - gap * .65f
            val bottom = anchor.centerY + gap * .24f
            if (top < viewport.top || bottom > viewport.bottom) return@mapNotNull null
            Card(
                NormalizedRect(viewport.left + width * .03f, top, viewport.left + width * .96f, bottom),
                NormalizedRect(anchor.left, anchor.top, anchor.right, anchor.bottom),
                (0..2).map { index ->
                    val left = viewport.left + width * (.312f + index * .092f)
                    NormalizedRect(left, anchor.centerY - gap * .26f, left + width * .086f, anchor.centerY + gap * .15f)
                },
            )
        }
    }

    fun artifact(text: String): Pair<String?, String> {
        val compact = InitialFormationKnowledge.compact(text)
        val exact = InitialFormationKnowledge.artifactHeadlines.keys.filter {
            compact.contains(InitialFormationKnowledge.compact(it))
        }
        if (exact.size == 1) return exact.single() to "OCR_MATCH"
        if (exact.size > 1) return null to "CONFLICT"
        val inferred = InitialFormationKnowledge.artifactHeadlines.filterValues(compact::contains).keys
        return if (inferred.size == 1) inferred.single() to "TITLE_INFERENCE" else null to "UNMATCHED"
    }
}

// Identical sampling on Android and desktop image regression tests; no image decoding per frame.
object FormationPortraitFingerprint {
    fun sample(width: Int, height: Int, bounds: NormalizedRect, pixel: (Int, Int) -> Int): IntArray {
        require(width > 0 && height > 0 && bounds.right > bounds.left && bounds.bottom > bounds.top)
        return IntArray(16 * 24) { index ->
            // Ignore rounded borders, the faction badge and the tier ribbon.
            val x = bounds.left + (bounds.right - bounds.left) * (.08f + (index % 16 + .5f) / 16f * .84f)
            val y = bounds.top + (bounds.bottom - bounds.top) * (.04f + (index / 16 + .5f) / 24f * .79f)
            pixel((x * width).toInt().coerceIn(0, width - 1), (y * height).toInt().coerceIn(0, height - 1))
        }
    }

    fun similarity(a: IntArray, b: IntArray): Float {
        require(a.size == 384 && b.size == a.size)
        val distance = a.indices.sumOf { index ->
            intArrayOf(0, 8, 16).sumOf { shift -> abs((a[index] shr shift and 255) - (b[index] shr shift and 255)) }
        }
        return 1f - distance.toFloat() / (a.size * 3 * 255)
    }
}

data class InitialFormationRecommendation(
    val slotIndex: Int,
    val score: Int?,
    val action: InitialFormationAction,
    val reason: String,
    val hasUncertainty: Boolean = true,
)
enum class InitialFormationAction { SELECT, CONSIDER, SKIP }

object InitialFormationAdvisor {
    fun recommend(offers: List<InitialFormationOffer>): List<InitialFormationRecommendation> {
        val evaluated = offers.map(::evaluate)
        val eligible = evaluated.filter { it.score != null }
        val best = eligible.maxOfOrNull { it.score!! }
        val winners = eligible.filter { it.score == best }
        return evaluated.map { candidate ->
            when {
                candidate.score == null -> candidate
                winners.size == 1 && candidate.slotIndex == winners.single().slotIndex -> candidate.copy(
                    action = InitialFormationAction.SELECT,
                    reason = "확인된 후보 중 조건부 추천: ${candidate.reason} / 랜덤·미확인 후보와의 우열은 미정",
                )
                winners.size > 1 && candidate.score == best -> candidate.copy(reason = "동률·조건부 선택: ${candidate.reason}")
                else -> candidate
            }
        }
    }

    fun evaluate(offer: InitialFormationOffer): InitialFormationRecommendation {
        if (offer.isRandom) return InitialFormationRecommendation(offer.slotIndex, null, InitialFormationAction.CONSIDER,
            "숨겨진 랜덤 선택지: 영웅·아티팩트·기대값 미확인")
        val known = offer.heroSlots.filter { it.status == HeroRecognitionStatus.CONFIRMED }
        val names = known.mapNotNull { it.heroName }.toSet()
        val reasons = mutableListOf<String>()
        // Developer heuristic points for evidenced compatibility, NOT win rates or official stats.
        // Unknown entries are excluded from ranking rather than treated as weak zero-score heroes.
        var score = 0
        val healer = names.intersect(setOf("스모키와 미르키", "루보미르"))
        if (healer.isNotEmpty()) { score += 2; reasons.add("${healer.joinToString()}의 확인된 치료") }
        val roles = known.mapNotNull { InitialFormationKnowledge.hero(it.heroName)?.role ?: it.roleHint }.toSet()
        if (roles.contains("서포터") && roles.any { it != "서포터" }) {
            score++; reasons.add("피해 역할과 지원 역할 보완")
        }
        when (offer.artifactName) {
            "평정의 샘물" -> {
                val targets = known.filter { it.faction == "와일더스" }.mapNotNull { it.heroName }
                score += targets.size * 2
                reasons.add("와일더스 회복 대상 ${targets.size}명: ${targets.joinToString().ifBlank { "없음" }}")
                val sustained = targets.intersect(setOf("메이", "프라벨"))
                if (sustained.isNotEmpty()) { score++; reasons.add("${sustained.joinToString()}의 전투 중 성장·지속 피해와 회복의 보완 가능성") }
                reasons.add("5초 후 첫 회복, 이후 10초마다; 시작 5초 생존 필요")
                reasons.add("등급별 8/10/20% 대응 미확인; 소환수는 회복 대상에 포함하지 않음")
            }
            "고블린 가면" -> {
                reasons.add("적 현재 HP 50% 일시 감소, 20초 동안 반환; 초반 처치 보장 없음")
                if ("카세디아" in names) { score += 2; reasons.add("카세디아의 피해 증가 버프가 초반 공격 기회를 보조할 가능성") }
                if ("카짐" in names) reasons.add("카짐의 에어본 조건을 제공하는 아군은 미확인")
                if ("스모키와 미르키" in names) reasons.add("스모키의 치료는 주변 위치·전투 시간 의존")
            }
            "신성한 소환" -> {
                reasons.add("최후열 아군 최대 HP 75%·공격력 80% 손실을 대가로 랜덤 반신 소환")
                reasons.add("동일 장비와 초기 에너지 +400; 희생 대상·소환 결과·계승 공식 미확인")
                reasons.add("실제 배치가 없으므로 카드 순서·사정거리로 희생 영웅을 지정하지 않음")
            }
            else -> reasons.add("아티팩트 효과 미확인")
        }
        if ("메이" in names) reasons.add("메이의 적 궁극기 차단 확인; 상세 성능 수치 미확인")
        if (offer.artifactName in InitialFormationKnowledge.artifactHeadlines) {
            reasons.add("초기 기본 효과만 평가; 경험치 24·46 잠금 효과 제외")
        }
        if (known.size != 3) reasons.add("영웅 ${3 - known.size}명 미확정: 성능 비교 보류")
        if (offer.heroSlots.any { it.rarity == HeroRarity.UNKNOWN }) reasons.add("현재 영웅 등급 미확인")
        if (offer.artifactSource == "TITLE_INFERENCE") reasons.add("아티팩트는 제목 기반 추정")
        val comparable = known.size == 3 && names.size == 3 && offer.artifactName in InitialFormationKnowledge.artifactHeadlines
        return InitialFormationRecommendation(offer.slotIndex, score.takeIf { comparable },
            InitialFormationAction.CONSIDER, reasons.joinToString(" / "))
    }
}
