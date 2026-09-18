package com.asp0902.mobilegameassistant.learning

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LearningEntry(
    val id: String,
    val title: String,
    val text: String,
    val asset: String? = null,
    val portrait: String? = null,
)

data class LearnedSnapshot(
    val date: String,
    val entries: List<LearningEntry>,
    val conversation: List<LearningEntry>,
    val bossOverlay: String,
)

@Singleton
class LearnedKnowledgeRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val rules by lazy {
        LearningFiles.open(context, "learning/initial_formation_rules.json").bufferedReader().use {
            com.asp0902.mobilegameassistant.analysis.InitialFormationRules.parse(JSONObject(it.readText()))
        }
    }
    fun initialFormationRules() = rules

    private val snapshot by lazy {
        runCatching {
            val root = LearningFiles.open(context, "learning/game_knowledge_20260917.json")
                .bufferedReader().use { JSONObject(it.readText()) }
            fun entries(key: String): List<LearningEntry> {
                val array = root.getJSONArray(key)
                return (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    LearningEntry(item.getString("id"), item.getString("title"), item.optString("text"),
                        item.optString("asset").takeIf { it.isNotBlank() },
                        item.optString("portrait").takeIf { it.isNotBlank() })
                }
            }
            LearnedSnapshot(root.getString("snapshotDate"), entries("entries"), entries("conversationIndex"),
                root.getJSONObject("boss").getString("overlay"))
        }
    }

    suspend fun load(): Result<LearnedSnapshot> = withContext(Dispatchers.IO) { snapshot }

    suspend fun text(entry: LearningEntry): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            entry.asset?.let { asset -> LearningFiles.open(context, asset).bufferedReader().use { it.readText() } }
                ?: entry.text
        }
    }
}
