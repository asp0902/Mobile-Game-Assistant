package com.asp0902.mobilegameassistant.labyrinth

/**
 * #38 Golden 자료.
 *
 * 사용자가 직접 관측한 난이도 5 시계열이다. **역사적 Run fixture** 이며 진행 중 Run 이 아니다.
 *
 * 각 항목은 확인된 값과 확인되지 않은 값을 다른 필드로 나눠 담는다.
 * `actualChoice = null` 은 "다음 화면으로 확정되지 않음" 이라는 뜻이며,
 * 추론으로 채우지 않는다.
 */
object LabyrinthGolden {

    /** 관측의 출처와 신뢰도. 추론과 실제 관측을 같은 필드에 담지 않기 위한 것이다. */
    enum class Evidence {
        /** 이름·수치가 화면에 직접 보임 */
        DIRECTLY_READ,

        /** 다음 화면에서 결과로 확인됨 */
        CONFIRMED_BY_NEXT_SCREEN,

        /** 화면에 보였으나 선택 여부가 확정되지 않음 */
        UNVERIFIED,
    }

    data class Node(
        val floor: String,
        val screen: String,
        /** 화면에 보인 후보 */
        val offered: List<String>,
        /** 사용자가 실제로 고른 것. 확정되지 않았으면 null */
        val actualChoice: String?,
        val evidence: Evidence,
        val note: String = "",
    )

    /** 난이도 5, 2026-08-25 사용자 관측. 수치가 흐려 확인 못 한 값은 넣지 않았다. */
    val DIFFICULTY5_RUN: List<Node> = listOf(
        Node("5-1", "경로 선택", listOf("아이템 게이트"), "아이템 게이트", Evidence.DIRECTLY_READ,
            "선택지가 하나뿐인 화면. 추천 경쟁을 만들면 안 되는 사례"),
        Node("5-1", "전투 결과", listOf("승리"), "승리", Evidence.DIRECTLY_READ),
        Node("5-1", "보상", listOf("부활 포션"), "부활 포션", Evidence.CONFIRMED_BY_NEXT_SCREEN,
            "우하단 아이콘에서 보유량 1 확인"),
        Node("5-1", "유물의 문", listOf("칼날", "수호"), "칼날", Evidence.CONFIRMED_BY_NEXT_SCREEN,
            "다음 화면의 칼날 1/2 로 확정"),
        Node("5-2", "전투 결과", listOf("승리"), "승리", Evidence.DIRECTLY_READ),
        Node("5-2", "경로 선택", listOf("퓨어 크리스탈의 문", "아이템 게이트"), null, Evidence.UNVERIFIED),
        Node("5-3", "전투 결과", listOf("승리"), "승리", Evidence.DIRECTLY_READ),
        Node("5-3", "경로 선택", listOf("퓨어 크리스탈의 문", "피츠 상점"), "피츠 상점", Evidence.CONFIRMED_BY_NEXT_SCREEN),
        Node("5-3", "피츠 상점", listOf("건강", "수호", "저금통", "부활 포션"), "저금통", Evidence.CONFIRMED_BY_NEXT_SCREEN,
            "퓨어 크리스탈 157 → 82 전이로 확정"),
        Node("5-5", "전투 결과", listOf("승리"), "승리", Evidence.DIRECTLY_READ),
        Node("5-5", "낙인 선택", listOf("제국 팔찌", "힘의 조류", "부패의 대가"), null, Evidence.UNVERIFIED,
            "실제 선택이 다음 화면으로 확정되지 않음"),
        Node("5-6", "전투 결과", listOf("승리"), "승리", Evidence.DIRECTLY_READ,
            "일부 영웅 HP 크게 감소"),
        Node("5-6", "경로 선택", listOf("아이템 게이트", "퓨어 크리스탈의 문"), null, Evidence.UNVERIFIED),
    )

    /** 화면에서 직접 읽은 피츠 상점 상품. 가격은 `전체 평균 가격` 획득 전 값이다. */
    val FITZ_SHOP_5_3: Map<String, Int> = mapOf(
        "건강" to 50,
        "수호" to 50,
        "저금통" to 75,
        "부활 포션" to 75,
    )

    /** 5-1 유물의 문에서 이름과 함께 읽은 효과. 단계별 증가치는 화면에 없어 담지 않는다. */
    val RELIC_EFFECTS_5_1: Map<String, String> = mapOf(
        "칼날" to "공격 속도 10 증가",
        "수호" to "물리·마법 방어력 10% 증가",
    )

    /** 5-5 낙인 후보 효과. 어느 것을 골랐는지는 확정되지 않았다. */
    val SIGIL_CANDIDATES_5_5: Map<String, String> = mapOf(
        "제국 팔찌" to "출전 레오프론 영웅 1명당 아군 공격력·물리 방어력·마법 방어력·HP 각 5% 증가",
        "힘의 조류" to "HP 95% 초과 아군의 치명타 30 증가",
        "부패의 대가" to "전투 시작 3초 후 아군 현재 HP 10% 손실, 적군 현재 HP 35% 손실",
    )

    /** 난이도 5 시작 시 사용자가 제공한 탐색 후보. 소환 화면에 등장했다는 이유로 추가하지 않는다. */
    val OWNED_HEROES_AT_D5_START: List<String> = listOf(
        "퀸", "아로라", "실벤", "윙", "고르단", "울머스", "보니", "칸티아스", "솔리스", "세미라",
        "판도라", "후긴", "팔라모르", "신태일&아구몬", "휴윈", "에이론", "카세디아", "캐롤라이나",
        "베라", "샤키르", "로완", "갈라하드", "스모키와 미르키", "세시아", "매튜&파피몬", "데이먼",
        "안탄드라", "게르다", "워커", "발리카", "레미아", "루시우스", "아든", "카이", "에디",
        "소렌", "발렌",
    )

    /** 이후 계정에서 새로 획득. 과거 Run 의 시작 보유 목록에는 소급하지 않는다. */
    const val ACQUIRED_AFTER_D5_RUN = "토란"

    /** 사용자 재확인 전 확정하지 않는 항목. */
    val UNRESOLVED: List<String> = listOf(
        "3-18 실제 출전자 — 부활 포션 복귀와 대체 영웅 투입 기록이 충돌",
        "5-5 실제 선택 낙인",
        "5-2 / 5-6 경로 분기의 실제 선택",
        "에이론 초상화 자산 — 원본 캡처는 존재하나 저장소 crop 없음",
        "낙인 단계별(2/4/6/8) 정확한 수치",
        "진영 버프의 정확한 임계치와 수치",
    )
}
