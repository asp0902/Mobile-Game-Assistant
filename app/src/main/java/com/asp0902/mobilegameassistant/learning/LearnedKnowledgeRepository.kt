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

data class SkyTrialWarning(
    val message: String,
    val severity: String, // HIGH, MEDIUM, LOW
    val type: String,
)

data class SkyTrialData(
    val towers: List<SkyTrialTower>,
    val floorRewards: Map<String, Any>,
    val warnings: List<SkyTrialWarning> = emptyList(),
    val phantomMapping: Map<String, String> = emptyMap(),
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

data class HeroInfo(
    val id: String,
    val name: String,
    val title: String? = null,
    val rank: String,
    val rarity: String? = null,
    val role: String,
    val attackType: String? = null,
    val range: Int? = null,
    val owned: Boolean = false,
    val asOf: String? = null,
)

data class LearnedSnapshot(
    val date: String,
    val entries: List<LearningEntry>,
    val conversation: List<LearningEntry>,
    val bossOverlay: String,
    val skyTrial: SkyTrialData? = null,
    val dreamRealm: Map<String, Any>? = null,
    val heroes: List<HeroInfo> = emptyList(),
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
                    val warnings = st.optJSONArray("warnings")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val w = arr.getJSONObject(i)
                            SkyTrialWarning(w.getString("message"), w.getString("severity"), w.getString("type"))
                        }
                    } ?: emptyList()
                    val phantomMapping = st.optJSONObject("phantomMapping")?.let { map { it.key to it.value as String } }.toMap() ?: emptyMap()
                    SkyTrialData(towers, rewards, warnings, phantomMapping)
                }.getOrNull()
            }
            fun parseDreamRealm(): Map<String, Any>? {
                return runCatching {
                    root.optJSONObject("dreamRealm")?.let { dr ->
                        val result = mutableMapOf<String, Any>()
                        result["mode"] = dr.optString("mode", "꿈의 추격")
                        result["season"] = dr.optString("season", "")
                        result["conclusions"] = dr.optJSONArray("conclusions")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList<String>()
                        result["bosses"] = dr.optJSONArray("bosses")?.let { arr ->
                            (0 until arr.length()).map { i ->
                                val b = arr.getJSONObject(i)
                                mapOf(
                                    "id" to b.getString("id"),
                                    "name" to b.getString("name"),
                                    "level" to b.getInt("level"),
                                    "job" to b.getString("job"),
                                    "attack" to b.getString("attack"),
                                    "range" to b.getInt("range"),
                                    "faction" to b.getString("faction"),
                                    "counters" to b.getString("counters"),
                                    "bestResult" to (b.optJSONObject("bestResult")?.let { res ->
                                        mapOf<String, Any>(
                                            "killed" to res.optBoolean("killed", false),
                                            "percentage" to res.optDouble("percentage", 0.0),
                                            "time" to res.optString("time", ""),
                                            "attempt" to res.optInt("attempt", 0),
                                            "composition" to (res.optJSONArray("composition")?.let { comp ->
                                                (0 until comp.length()).map { comp.getString(it) }
                                            } ?: emptyList()),
                                            "echo" to res.optString("echo", ""),
                                            "note" to res.optString("note", "")
                                        )
                                    } ?: emptyMap<String, Any>())
                                )
                            }
                        } ?: emptyList<Map<String, Any>>()
                        result as Map<String, Any>
                    }
                }.getOrNull()
            }
            fun parseHeroes(): List<HeroInfo> {
                return runCatching {
                    val arr = root.optJSONArray("heroes") ?: return@runCatching emptyList()
                    (0 until arr.length()).map { i ->
                        val h = arr.getJSONObject(i)
                        HeroInfo(h.getString("id"), h.getString("name"), h.optString("title").takeIf { it.isNotBlank() },
                            h.getString("rank"), h.optString("rarity").takeIf { it.isNotBlank() },
                            h.getString("role"), h.optString("attackType").takeIf { it.isNotBlank() },
                            h.optInt("range").takeIf { it >= 0 }, h.optBoolean("owned", false), h.optString("asOf").takeIf { it.isNotBlank() })
                    }
                }.getOrDefault(emptyList())
            }
            LearnedSnapshot(root.getString("snapshotDate"), entries("entries"), entries("conversationIndex"),
                root.getJSONObject("boss").getString("overlay"), parseSkyTrial(), parseDreamRealm(), parseHeroes())
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
