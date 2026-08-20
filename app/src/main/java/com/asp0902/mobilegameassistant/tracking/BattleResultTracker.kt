package com.asp0902.mobilegameassistant.tracking

import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis

enum class RunStatus { ACTIVE, COMPLETED, FAILED, UNKNOWN }
enum class BattleOutcome { WIN, LOSS }

data class BattleResultObservation(val outcome: BattleOutcome?, val confidence: Float, val signature: String?)

object BattleResultRecognizer {
    fun recognize(text: String): BattleResultObservation = when {
        text.contains("전투 승리") -> BattleResultObservation(BattleOutcome.WIN, .95f, "WIN")
        text.contains("전투 패배") -> BattleResultObservation(BattleOutcome.LOSS, .95f, "LOSS")
        else -> BattleResultObservation(null, 0f, null)
    }
}

data class RunProgress(
    val wins: Int?,
    val targetWins: Int?,
    val status: RunStatus = RunStatus.ACTIVE,
    val lastResultSignature: String? = null,
)

object BattleResultTracker {
    fun apply(progress: RunProgress, observation: BattleResultObservation): RunProgress = when {
        observation.outcome == null -> progress.copy(lastResultSignature = null)
        observation.confidence < .9f || observation.signature == progress.lastResultSignature -> progress
        observation.outcome == BattleOutcome.LOSS -> progress.copy(lastResultSignature = observation.signature)
        else -> {
            val wins = (progress.wins ?: 0) + 1
            progress.copy(
                wins = wins,
                status = if (progress.targetWins != null && wins >= progress.targetWins) RunStatus.COMPLETED else RunStatus.ACTIVE,
                lastResultSignature = observation.signature,
            )
        }
    }

    fun apply(analysis: HonorDuelShopAnalysis, progress: RunProgress): HonorDuelShopAnalysis =
        analysis.copy(header = analysis.header.copy(wins = progress.wins, targetWins = progress.targetWins))
}
