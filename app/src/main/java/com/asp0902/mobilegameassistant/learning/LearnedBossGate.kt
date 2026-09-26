package com.asp0902.mobilegameassistant.learning

object LearnedBossGate {
    // Historical reference only. Never infer a party, remaining attempts or a clear from OCR.
    fun matches(rawText: String): Boolean {
        val text = rawText.replace(Regex("\\s+"), "")
        if ("이계의미궁" in text || "명예의결투" in text) return false
        return "망령의소굴" in text &&
            ("엘리트몬스터" in text || ("아군" in text && "적군" in text))
    }
}
