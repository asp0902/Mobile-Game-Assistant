package com.asp0902.mobilegameassistant.formation

enum class FormationTemplateId(val displayName: String) {
    CURVED_WALL("곡선 장벽"),
    SPLIT_LANES("분리 통로"),
}

data class FormationTile(
    val id: String,
    val x: Float,
    val y: Float,
)

data class FormationTemplate(
    val id: FormationTemplateId,
    val tiles: List<FormationTile>,
)

/**
 * 제공된 전장 캡처에서 읽은 아군 타일 중심점이다.
 * 좌표는 게임 viewport 기준 정규화 값이며, 캡처 해상도와 무관하다.
 */
object FormationTemplates {
    private val templates = listOf(
        FormationTemplate(
            id = FormationTemplateId.CURVED_WALL,
            tiles = listOf(
                FormationTile("A1", 0.11f, 0.55f),
                FormationTile("A2", 0.21f, 0.54f),
                FormationTile("A3", 0.32f, 0.56f),
                FormationTile("B1", 0.16f, 0.60f),
                FormationTile("B2", 0.27f, 0.59f),
                FormationTile("B3", 0.38f, 0.59f),
                FormationTile("B4", 0.50f, 0.58f),
                FormationTile("C1", 0.33f, 0.65f),
                FormationTile("C2", 0.44f, 0.64f),
                FormationTile("C3", 0.55f, 0.64f),
            ),
        ),
        FormationTemplate(
            id = FormationTemplateId.SPLIT_LANES,
            tiles = listOf(
                FormationTile("L1", 0.10f, 0.49f),
                FormationTile("L2", 0.11f, 0.55f),
                FormationTile("L3", 0.13f, 0.61f),
                FormationTile("L4", 0.12f, 0.68f),
                FormationTile("L5", 0.18f, 0.73f),
                FormationTile("M1", 0.43f, 0.55f),
                FormationTile("M2", 0.39f, 0.68f),
                FormationTile("M3", 0.49f, 0.73f),
                FormationTile("M4", 0.58f, 0.70f),
                FormationTile("R1", 0.74f, 0.57f),
                FormationTile("R2", 0.80f, 0.63f),
                FormationTile("R3", 0.85f, 0.69f),
                FormationTile("R4", 0.74f, 0.75f),
            ),
        ),
    )

    fun fromId(id: FormationTemplateId): FormationTemplate =
        templates.first { it.id == id }
}
