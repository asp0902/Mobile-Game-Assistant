package com.asp0902.mobilegameassistant.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class RemoteLearningSafetyTest {
    @Test fun rejectsTraversalAndExecutableFilesAndBoundsDownloads() {
        assertTrue(safeLearningPath("learning/portraits/hero.png"))
        assertTrue(safeLearningPath("learning/observations_20260821.jsonl"))
        listOf("../token.txt", "learning/../token.txt", "/learning/a.json", "learning//a.json",
            "learning\\a.json", "learning/a.dex", "learning/a.apk", "learning/a.js", "learning/C:a.json")
            .forEach { assertFalse(it, safeLearningPath(it)) }
        val output = ByteArrayOutputStream()
        assertEquals(3L, copyLearningBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, 3))
        assertTrue(runCatching { copyLearningBytes(ByteArrayInputStream(ByteArray(4)), output, 3) }.isFailure)
        val file = File.createTempFile("learning-hash", ".txt")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", learningHash(file))
        } finally { file.delete() }
    }
}
