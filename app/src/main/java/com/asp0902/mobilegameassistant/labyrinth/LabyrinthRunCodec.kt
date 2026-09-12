package com.asp0902.mobilegameassistant.labyrinth

import com.asp0902.mobilegameassistant.analysis.ScreenType

/**
 * RunState 를 한 덩어리 텍스트로 저장한다.
 *
 * 저장소의 기존 `SnapshotCodec` 과 같은 줄 기반 방식이다.
 * 새 직렬화 의존성을 넣지 않으려는 것이고, 순수 Kotlin 이라 왕복 테스트가 가능하다.
 */
object LabyrinthRunCodec {
    private const val SEP = ""

    fun encode(state: LabyrinthRunState): String = buildList {
        add("run${SEP}${state.runId}${SEP}${state.startedAt}${SEP}${state.nextWeeklyResetAt}${SEP}${state.status.name}")
        add("screen${SEP}${state.currentScreen.name}${SEP}${state.screenConfidence}")
        add("progress${SEP}${state.difficulty.orDash()}${SEP}${state.floor.orDash()}${SEP}${state.deepFloor.orDash()}")
        add("wallet${SEP}${state.crystals.orDash()}${SEP}${state.revivePotions.orDash()}${SEP}${state.hasAveragePrice}")
        state.relics.forEach { (name, count) -> add("relic${SEP}$name${SEP}$count") }
        state.activeSigils.forEach { add("sigil${SEP}$it") }
        state.roster.forEach { hero ->
            add(
                "hero${SEP}${hero.name}${SEP}${hero.heroId.orDash()}${SEP}${hero.deployed}${SEP}${hero.alive}" +
                    "${SEP}${hero.hpPercent.orDash()}${SEP}${hero.tileId.orDash()}${SEP}${hero.diedAtFloor.orDash()}" +
                    "${SEP}${hero.accountTier?.name.orDash()}${SEP}${hero.adjustedTier?.name.orDash()}" +
                    "${SEP}${hero.accountLevel.orDash()}${SEP}${hero.adjustedLevel.orDash()}${SEP}${hero.portraitVerified}",
            )
        }
        state.choices.forEach { choice ->
            add(
                "choice${SEP}${choice.floorLabel}${SEP}${choice.screen.name}${SEP}${choice.offered.joinToString("|")}" +
                    "${SEP}${choice.recommended.orDash()}${SEP}${choice.actualChoice.orDash()}" +
                    "${SEP}${choice.confirmedResult.orDash()}${SEP}${choice.evidence.name}${SEP}${choice.confidence}",
            )
        }
        state.purchases.forEach { purchase ->
            add(
                "buy${SEP}${purchase.floorLabel}${SEP}${purchase.item}${SEP}${purchase.price}" +
                    "${SEP}${purchase.crystalsBefore.orDash()}${SEP}${purchase.crystalsAfter.orDash()}",
            )
        }
        state.userCorrections.forEach { add("fix${SEP}$it") }
    }.joinToString("\n")

    fun decode(payload: String): LabyrinthRunState? {
        val rows = payload.lines().filter { it.isNotBlank() }.map { it.split(SEP) }
        val run = rows.firstOrNull { it.firstOrNull() == "run" } ?: return null
        var state = LabyrinthRunState(
            runId = run.getOrNull(1)?.toLongOrNull() ?: return null,
            startedAt = run.getOrNull(2)?.toLongOrNull() ?: 0L,
            nextWeeklyResetAt = run.getOrNull(3)?.toLongOrNull() ?: 0L,
            status = run.getOrNull(4)?.let { name -> LabyrinthRunStatus.entries.firstOrNull { it.name == name } }
                ?: LabyrinthRunStatus.ACTIVE,
        )
        rows.forEach { row ->
            when (row.firstOrNull()) {
                "screen" -> state = state.copy(
                    currentScreen = row.screenType(1),
                    screenConfidence = row.getOrNull(2)?.toFloatOrNull() ?: 0f,
                )

                "progress" -> state = state.copy(
                    difficulty = row.int(1), floor = row.int(2), deepFloor = row.int(3),
                )

                "wallet" -> state = state.copy(
                    crystals = row.int(1),
                    revivePotions = row.int(2),
                    hasAveragePrice = row.getOrNull(3).toBoolean(),
                )

                "relic" -> row.getOrNull(1)?.let { name ->
                    state = state.copy(relics = state.relics + (name to (row.int(2) ?: 0)))
                }

                "sigil" -> row.getOrNull(1)?.let { state = state.copy(activeSigils = state.activeSigils + it) }

                "hero" -> state = state.copy(roster = state.roster + LabyrinthHero(
                    name = row.getOrNull(1).orEmpty(),
                    heroId = row.str(2),
                    deployed = row.getOrNull(3).toBoolean(),
                    alive = row.getOrNull(4).toBoolean(),
                    hpPercent = row.int(5),
                    tileId = row.str(6),
                    diedAtFloor = row.str(7),
                    accountTier = row.tier(8),
                    adjustedTier = row.tier(9),
                    accountLevel = row.int(10),
                    adjustedLevel = row.int(11),
                    portraitVerified = row.getOrNull(12).toBoolean(),
                ))

                "choice" -> state = state.copy(choices = state.choices + LabyrinthChoicePoint(
                    floorLabel = row.getOrNull(1).orEmpty(),
                    screen = row.screenType(2),
                    offered = row.getOrNull(3)?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                    recommended = row.str(4),
                    actualChoice = row.str(5),
                    confirmedResult = row.str(6),
                    evidence = row.getOrNull(7)
                        ?.let { name -> LabyrinthEvidence.entries.firstOrNull { it.name == name } }
                        ?: LabyrinthEvidence.UNVERIFIED,
                    confidence = row.getOrNull(8)?.toFloatOrNull() ?: 0f,
                ))

                "buy" -> state = state.copy(purchases = state.purchases + LabyrinthPurchase(
                    floorLabel = row.getOrNull(1).orEmpty(),
                    item = row.getOrNull(2).orEmpty(),
                    price = row.int(3) ?: 0,
                    crystalsBefore = row.int(4),
                    crystalsAfter = row.int(5),
                ))

                "fix" -> row.getOrNull(1)?.let { state = state.copy(userCorrections = state.userCorrections + it) }
            }
        }
        return state
    }

    private const val DASH = "-"

    private fun Any?.orDash(): String = this?.toString() ?: DASH
    private fun List<String>.str(index: Int): String? = getOrNull(index)?.takeIf { it != DASH && it.isNotBlank() }
    private fun List<String>.int(index: Int): Int? = str(index)?.toIntOrNull()
    private fun String?.toBoolean(): Boolean = this == "true"

    private fun List<String>.screenType(index: Int): ScreenType =
        str(index)?.let { name -> ScreenType.entries.firstOrNull { it.name == name } } ?: ScreenType.UNKNOWN

    private fun List<String>.tier(index: Int): LabyrinthRules.HeroTier? =
        str(index)?.let { name -> LabyrinthRules.HeroTier.entries.firstOrNull { it.name == name } }
}
