package com.asp0902.mobilegameassistant.analysis

import org.json.JSONObject

// Data only: no downloaded expressions, scripts, classes or confidence-gate overrides.
data class InitialFormationRules(
    val weights: Map<String, Int> = emptyMap(),
    val artifactReasons: Map<String, List<String>> = emptyMap(),
    val disabledArtifacts: Set<String> = emptySet(),
) {
    fun weight(key: String, fallback: Int) = weights[key] ?: fallback

    companion object {
        private val supportedWeights = setOf("healer", "support", "statueLightbearer", "flame", "midas",
            "midasTankHealer", "springPerWilder", "springSustain", "goblinCassadia")
        fun parse(json: JSONObject): InitialFormationRules {
            require(json.getInt("schemaVersion") == 1) { "추천 규칙 형식 변경: APK 업데이트 필요" }
            require(json.keys().asSequence().all { it in setOf("schemaVersion", "weights", "artifactReasons", "disabledArtifacts") })
            val weights = json.getJSONObject("weights").let { values ->
                values.keys().asSequence().associateWith { key ->
                    require(key in supportedWeights) { "새 추천 조건: APK 업데이트 필요" }
                    val value = values.get(key)
                    require(value is Int && value in -20..20)
                    value
                }
            }
            val reasons = json.getJSONObject("artifactReasons").let { values ->
                values.keys().asSequence().associateWith { artifact ->
                    require(artifact in InitialFormationKnowledge.artifactHeadlines)
                    val items = values.getJSONArray(artifact)
                    require(items.length() in 1..8)
                    (0 until items.length()).map { items.getString(it).also { reason -> require(reason.length in 1..300) } }
                }
            }
            val disabled = json.getJSONArray("disabledArtifacts").let { items ->
                require(items.length() <= InitialFormationKnowledge.artifactHeadlines.size)
                (0 until items.length()).map { items.getString(it).also { name -> require(name in InitialFormationKnowledge.artifactHeadlines) } }.toSet()
            }
            return InitialFormationRules(weights, reasons, disabled)
        }
    }
}
