package com.asp0902.mobilegameassistant.tracking

import com.asp0902.mobilegameassistant.analysis.ArtifactXp
import com.asp0902.mobilegameassistant.analysis.HeaderField
import com.asp0902.mobilegameassistant.analysis.HonorDuelHeader
import com.asp0902.mobilegameassistant.analysis.HonorDuelShopAnalysis

enum class ReconciliationSource(val priority: Int) {
    OBSERVED_LOW(1),
    ACTION_PREDICTION(2),
    PREVIOUS_SNAPSHOT(3),
    OBSERVED_HIGH(4),
    DETAIL_POPUP(5),
    USER_CORRECTION(6),
}

data class ObservedValue<T>(
    val value: T?,
    val source: ReconciliationSource,
    val confidence: Float,
)

data class ReconciledHonorDuelState(
    val runId: Long,
    val analysis: HonorDuelShopAnalysis,
    val headerSources: Map<HeaderField, ReconciliationSource>,
)

object HonorDuelStateReconciler {
    fun <T> select(previous: ObservedValue<T>, current: ObservedValue<T>): ObservedValue<T> = when {
        current.value == null -> previous
        previous.value == null -> current
        current.source.priority > previous.source.priority -> current
        current.source.priority < previous.source.priority -> previous
        current.confidence >= previous.confidence -> current
        else -> previous
    }

    fun reconcile(
        runId: Long,
        previous: ReconciledHonorDuelState?,
        observed: HonorDuelShopAnalysis,
    ): ReconciledHonorDuelState {
        val old = previous?.analysis?.header
        val sources = mutableMapOf<HeaderField, ReconciliationSource>()
        fun <T> field(field: HeaderField, oldValue: T?, newValue: T?): T? {
            val prior = ObservedValue(oldValue, ReconciliationSource.PREVIOUS_SNAPSHOT, old?.confidence?.get(field) ?: 0f)
            val confidence = observed.header.confidence[field] ?: 0f
            val current = ObservedValue(
                newValue,
                if (confidence >= .9f) ReconciliationSource.OBSERVED_HIGH else ReconciliationSource.OBSERVED_LOW,
                confidence,
            )
            return select(prior, current).also { sources[field] = it.source }.value
        }

        val header = HonorDuelHeader(
            currency = field(HeaderField.CURRENCY, old?.currency, observed.header.currency),
            shopLevel = field(HeaderField.SHOP_LEVEL, old?.shopLevel, observed.header.shopLevel),
            targetWins = field(HeaderField.TARGET_WINS, old?.targetWins, observed.header.targetWins),
            artifactXp = field(HeaderField.ARTIFACT_XP, old?.artifactXp, observed.header.artifactXp),
            wins = field(HeaderField.WINS, old?.wins, observed.header.wins),
            hp = field(HeaderField.HP, old?.hp, observed.header.hp),
            refreshCost = field(HeaderField.REFRESH_COST, old?.refreshCost, observed.header.refreshCost),
            currentRound = field(HeaderField.ROUND, old?.currentRound, observed.header.currentRound),
            artifactName = field(HeaderField.ARTIFACT, old?.artifactName, observed.header.artifactName),
            confidence = observed.header.confidence,
        )
        return ReconciledHonorDuelState(runId, observed.copy(header = header), sources)
    }
}
