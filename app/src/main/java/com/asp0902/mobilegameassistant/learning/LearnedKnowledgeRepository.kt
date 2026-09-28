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

data class SkyTrialTower(
    val name: String,
    val faction: String,
    val openDays: List<String>,
    val currentFloor: Int,
    val status: String,
    val phantom: String,
    val note: String? = null,
)

data class SkyTrialData(
    val towers: List<SkyTrialTower>,
    val floorRewards: Map<String, Any>,
)

data class DreamRealmBossSkill(
    val name: String,
    val effect: String,
)

data class DreamRealmBossInfo(
    val name: String,
    val title: String,
    val type: String,
    val level: Int,
    val estimatedHP: String,
)

data class DreamRealmData(
    val boss: DreamRealmBossInfo,
    val skills: List<String>,
    val bestComposition: Map<String, Any>,
)

data class LearnedSnapshot(
    val date: String,
    val entries: List<LearningEntry>,
    val conversation: List<LearningEntry>,
    val bossOverlay: String,
    val skyTrial: SkyTrialData? = null,
    val dreamRealm: DreamRealmData? = null,
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
    private val confirmedStartNames by lazy {
        runCatching {
            LearningFiles.open(context, "learning/honor_initial_confirmations.json").bufferedReader().use {
                val root = JSONObject(it.readText())
                require(root.getInt("schemaVersion") == 1 && root.getString("evidence") == "USER_CONFIRMED" &&
                    root.getString("layoutId") == "honor-start-20260918-3offers")
                val names = root.getJSONArray("confirmedNames")
                (0 until names.length()).map { index -> names.getString(index) }.toSet()
            }
        }.getOrDefault(emptySet())
    }
    fun confirmInitialOffers(offers: List<com.asp0902.mobilegameassistant.analysis.InitialFormationOffer>) =
        com.asp0902.mobilegameassistant.analysis.InitialFormationKnowledge.applyConfirmedStartLayout(offers, confirmedStartNames)

    private val snapshot by lazy {
        runCatching {
            val root = LearningFiles.open(context, "learning/game_knowledge_20260926.json")
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
            fun parseSkyTrial(): SkyTrialData? {
                return runCatching {
                    val st = root.optJSONObject("skyTrial") ?: return@runCatching null
                    val towers = st.getJSONArray("towers").let { arr ->
                        (0 until arr.length()).map { i ->
                            val t = arr.getJSONObject(i)
                            val days = t.getJSONArray("openDays").let { d -> (0 until d.length()).map { d.getString(it) } }
                            SkyTrialTower(t.getString("name"), t.getString("faction"), days,
                                t.getInt("currentFloor"), t.getString("status"), t.getString("phantom"),
                                t.optString("note").takeIf { it.isNotBlank() })
                        }
                    }
                    val rewards = st.getJSONObject("floorRewards").let { map { it.key to it.value } }.toMap()
                    SkyTrialData(towers, rewards)
                }.getOrNull()
            }
            fun parseDreamRealm(): DreamRealmData? {
                return runCatching {
                    val dr = root.optJSONObject("dreamRealm") ?: return@runCatching null
                    val boss = dr.getJSONObject("boss").let {
                        DreamRealmBossInfo(it.getString("name"), it.getString("title"), it.getString("type"),
                            it.getInt("level"), it.getString("estimatedHP"))
                    }
                    val skills = dr.getJSONArray("bossSkills").let { arr ->
                        (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
                    }
                    val comp = dr.getJSONObject("bestComposition").let { map { it.key to it.value } }.toMap()
                    DreamRealmData(boss, skills, comp)
                }.getOrNull()
            }
            LearnedSnapshot(root.getString("snapshotDate"), entries("entries"), entries("conversationIndex"),
                root.getJSONObject("boss").getString("overlay"), parseSkyTrial(), parseDreamRealm())
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
