package com.asp0902.mobilegameassistant.labyrinth

data class LabyrinthAdvice(
    val choice: String?,
    val message: String,
)

object LabyrinthChoiceAdvisor {
    private data class Rule(val name: String, val score: Int, val reason: String)

    // ponytail: Learned-run keyword scoring is intentionally static; add live run state only when a captured choice changes these priorities.
    private val marks = listOf(
        Rule("보호의 예술", 100, "궁극기 누적으로 받는 피해를 최대 50% 줄여 생존을 가장 크게 보강"),
        Rule("첫 보상", 98, "첫 처치 때 HP 30%와 에너지 300을 회복"),
        Rule("욕망 흡수", 96, "일반 공격으로 적의 남은 HP를 흡수해 유지력을 보강"),
        Rule("돌격의 방패", 92, "적진 진입 때 최대 HP 30% 보호막을 획득"),
        Rule("분노의 속공", 88, "공격 속도 100 증가가 주력 딜러와 잘 맞음"),
        Rule("분노 흡수", 84, "치명타에 생명 흡수를 더해 유지력을 보강"),
        Rule("흡수의 칼날", 82, "HP 70% 미만에서 생명 흡수를 보강"),
        Rule("집중 조준", 74, "안전한 후열 영웅의 치명타를 보강"),
        Rule("끓어오르는 분노", 72, "치명타를 직접 보강"),
        Rule("가속 중첩", 60, "이미 공격 속도 보강이 있어 생존 낙인보다 우선도가 낮음"),
        Rule("속결 표시", 55, "발동이 늦고 생존 안정성이 낮음"),
        Rule("부패의 대가", 50, "현재 조합의 생존 문제를 직접 해결하지 못함"),
        Rule("신의 군대", 35, "전열을 던져 스모키의 회복 범위에서 이탈시킬 수 있음"),
        Rule("제국 팔찌", 20, "현재 출전 조합에 레오프론 제국 영웅이 없음"),
    )
    private val spells = listOf(
        Rule("구속술", 100, "수정 방어전은 고정 제어가 우선이며 에너지 회복 감소와 충돌하지 않음"),
        Rule("소생술", 80, "회복은 유효하지만 수정에 접근하는 적을 막지는 못함"),
        Rule("성운술", 65, "피해보다 수정 보호를 위한 제어가 우선"),
        Rule("계몽술", 55, "분노의 속공으로 에너지 회복 효율이 낮아짐"),
    )
    private val supplies = listOf(
        Rule("가호", 100, "활력 10 증가로 전원 생존을 보강"),
        Rule("건장", 98, "HP 10% 증가로 전원 생존을 보강"),
        Rule("건강", 98, "HP 보강으로 전원 생존을 안정화"),
        Rule("수호", 95, "물리·마법 방어를 함께 보강"),
        Rule("부활 포션", 93, "사망 영웅 복구와 에너지 완충 수단을 확보"),
        Rule("칼날", 88, "공격 속도 10 증가로 데이먼·세미라의 화력을 보강"),
        Rule("정복", 82, "공격력 5% 증가로 화력을 보강"),
        Rule("추가 퓨어 크리스탈", 40, "후반에는 재화보다 생존 보강이 우선"),
        Rule("저금통", 30, "즉시 전투력을 올리지 못함"),
    )
    private val routes = listOf(
        Rule("유물의 문", 100, "후반 생존 유물을 확보할 수 있음"),
        Rule("아이템 게이트", 92, "부활 포션 등 즉시 생존 수단을 확보할 수 있음"),
        Rule("낙인의 수정", 90, "생존 낙인을 추가할 기회가 있음"),
        Rule("피츠 상점", 84, "50 크리스탈 상품 중 생존 보강을 고를 수 있음"),
        Rule("선물의 문", 78, "보상 배수를 노릴 수 있으나 전투력 보강보다 변동성이 큼"),
        Rule("퓨어 크리스탈의 문", 70, "현재는 재화보다 생존 전력이 우선"),
        Rule("심층 도전", 60, "단일 진행 경로"),
        Rule("보스", 60, "단일 진행 경로"),
    )
    private val floorHud = Regex("\\d{1,2}/(?:15|20)")

    fun analyze(rawText: String): LabyrinthAdvice? {
        val text = rawText.replace(Regex("\\s+"), "")
        if (!looksLikeLabyrinth(text)) return null

        detected(text, spells).takeIf { it.size >= 2 }?.let {
            return recommend("심층 마법", it)
        }
        if ("수정방어전" in text) {
            return LabyrinthAdvice(
                choice = null,
                message = "수정 방어전 배치\n안탄드라 전열 중앙 · 데이먼 전열 인접\n" +
                    "스모키와 미르키 후열 중앙 · 세미라/세시아 후열 양옆\n수정 주변에 밀집",
            )
        }

        val markChoices = detected(text, marks)
        if (markChoices.size >= 2) return recommend("낙인", markChoices, showRefresh = true)
        if ("낙인선택" in text) {
            return LabyrinthAdvice(null, "낙인 선택지 OCR 확인 필요\n우측 상단 새로고침: 25 크리스탈")
        }

        val routeChoices = detected(text, routes)
        if (routeChoices.size >= 2) return recommend("경로", routeChoices)

        val supplyChoices = detected(text, supplies)
        if (("상품리스트" in text || "피츠상점" in text) && supplyChoices.size >= 2) {
            return recommend("피츠 상점", supplyChoices)
        }
        if (("유물의문" in text || "아이템게이트" in text) && supplyChoices.size >= 2) {
            return recommend("유물", supplyChoices)
        }
        if (routeChoices.size == 1) {
            val only = routeChoices.single()
            return LabyrinthAdvice(only.name, "단일 선택지: ${only.name}\n비교 추천 없이 진행")
        }
        return null
    }

    private fun recommend(title: String, choices: List<Rule>, showRefresh: Boolean = false): LabyrinthAdvice {
        val best = choices.maxBy { it.score }
        val refresh = if (showRefresh) {
            val action = if (best.score >= 85) "사용하지 않음" else "사용 고려"
            "\n우측 상단 새로고침 25 크리스탈: $action"
        } else ""
        return LabyrinthAdvice(best.name, "$title 추천: ${best.name}\n${best.reason}$refresh")
    }

    private fun detected(text: String, rules: List<Rule>): List<Rule> =
        rules.filter { it.name.replace(" ", "") in text }

    private fun looksLikeLabyrinth(text: String): Boolean =
        ("난이도5" in text && floorHud.containsMatchIn(text)) ||
            listOf("탐색종료", "낙인선택", "퓨어크리스탈", "수정방어전", "심층도전", "모험여정")
                .any { it in text }
}
