package com.asp0902.mobilegameassistant.rules

import android.content.Context
import androidx.annotation.VisibleForTesting
import org.json.JSONObject

data class MidasGoldenPolicy @JvmOverloads constructor(
    val artifactName: String = "마이다스의 재물",
    val minimumXpAmount: Int = 1,
    val trialMinimumConfidence: Float = .9f,
) {
    companion object {
        fun fromAssets(context: Context): MidasGoldenPolicy = runCatching {
            context.assets.open("rules/midas_golden_policy.json").bufferedReader().use(::parse)
        }.getOrDefault(MidasGoldenPolicy())

        @VisibleForTesting
        fun parse(reader: java.io.Reader): MidasGoldenPolicy {
            val root = JSONObject(reader.readText())
            return MidasGoldenPolicy(
                artifactName = root.optString("artifactName", "마이다스의 재물"),
                minimumXpAmount = root.optJSONObject("artifactXp")?.optInt("minimumAmount", 1) ?: 1,
                trialMinimumConfidence = root.optJSONObject("trialCard")?.optDouble("minimumConfidence", .9)?.toFloat() ?: .9f,
            )
        }
    }
}
