package com.asp0902.mobilegameassistant.analysis

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

private val Context.heroCorrectionStore by preferencesDataStore(name = "hero_corrections")

@Singleton
class HeroCorrectionRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val correctionsKey = stringPreferencesKey("records")
    private val mutableCorrections = MutableStateFlow<List<HeroCorrection>>(emptyList())
    val corrections = mutableCorrections.asStateFlow()

    init {
        scope.launch {
            context.heroCorrectionStore.data.map { it[correctionsKey].orEmpty() }.collect { raw ->
                mutableCorrections.value = raw.lineSequence().mapNotNull(::decode).toList()
            }
        }
    }

    suspend fun save(correction: HeroCorrection) {
        context.heroCorrectionStore.edit { preferences ->
            val updated = (preferences[correctionsKey].orEmpty().lineSequence().mapNotNull(::decode)
                .filterNot { it.runId == correction.runId && it.snapshotId == correction.snapshotId && it.slotIndex == correction.slotIndex }
                .toList() + correction)
            preferences[correctionsKey] = updated.joinToString("\n", transform = ::encode)
        }
    }

    fun forRun(runId: Long): List<HeroCorrection> = corrections.value.filter { it.runId == runId }

    private fun encode(value: HeroCorrection) = listOf(
        value.runId, value.snapshotId, value.slotIndex, value.originalHeroId.orEmpty(), value.correctedHeroId,
        value.correctedName, value.portraitSignature.orEmpty(),
    ).joinToString("\t")

    private fun decode(line: String): HeroCorrection? {
        val values = line.split('\t')
        return if (values.size == 7) HeroCorrection(
            values[0].toLongOrNull() ?: return null,
            values[1].toLongOrNull() ?: return null,
            values[2].toIntOrNull() ?: return null,
            values[3].ifBlank { null }, values[4], values[5], values[6].ifBlank { null },
        ) else null
    }
}
