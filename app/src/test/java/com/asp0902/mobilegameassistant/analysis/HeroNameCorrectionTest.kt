package com.asp0902.mobilegameassistant.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 이름 보정 회귀.
 *
 * `휴윈`은 독립된 와일더스 서포터다. 저장소에 남아 있던 `휴윈 → 후긴` 보정은 알려진 결함이었고,
 * `류윈`도 재확인 전에는 자동 별칭으로 쓰지 않는다. 허용되는 오타 보정은 `휴긴 → 후긴` 하나뿐이다.
 */
class HeroNameCorrectionTest {

    private val huwin = HeroReference("huwin", "휴윈", "와일더스")
    private val hugin = HeroReference("hugin", "후긴", "레오프론")
    private val heroes = listOf(huwin, hugin)

    // ---- 자산 자체를 고정한다 ----

    @Test
    fun assetKeepsOnlyConfirmedCorrections() {
        // 휴긴 → 후긴 은 오타 보정, 류윈 → 휴윈 은 사용자가 확정한 OCR 오독 보정이다.
        // 금지된 `휴윈 → 후긴` 과 `류윈 → 후긴` 이 다시 들어오면 여기서 걸린다.
        assertEquals(listOf("휴긴" to "후긴", "류윈" to "휴윈"), corrections())
        assertFalse(corrections().any { it.first == "휴윈" })
        assertFalse(corrections().any { it.second == "후긴" && it.first == "류윈" })
    }

    @Test
    fun assetKnowsHuwinAsItsOwnHero() {
        val names = heroNames()
        assertTrue("휴윈" in names)
        assertTrue("후긴" in names)
    }

    // ---- 해석 동작 ----

    @Test
    fun huwinIsNotRewrittenToHugin() {
        val result = HeroIdentityResolver.resolveText("휴윈", heroes, corrections())
        assertEquals("휴윈", result.koreanName)
        assertEquals("와일더스", result.faction)
        assertEquals(HeroRecognitionStatus.CONFIRMED, result.status)
    }

    @Test
    fun onlyHuginTypoIsNormalized() {
        val result = HeroIdentityResolver.resolveText("휴긴", heroes, corrections())
        assertEquals("후긴", result.koreanName)
        assertEquals(HeroRecognitionStatus.CONFIRMED, result.status)
    }

    /** 사용자 확정: `류윈`은 휴윈의 OCR 오독이다. 후긴으로 보내면 안 된다. */
    @Test
    fun ryuwinResolvesToHuwinNotHugin() {
        val result = HeroIdentityResolver.resolveText("류윈", heroes, corrections())
        assertEquals("휴윈", result.koreanName)
        assertEquals("와일더스", result.faction)
        assertEquals(HeroRecognitionStatus.CONFIRMED, result.status)
    }

    // ---- 초상화 연결 ----

    @Test
    fun ceciaPortraitBelongsToCecia() {
        val row = manifest().single { it["name_ko"] == "세시아" }
        assertEquals("그레이브본", row["faction"])
        assertEquals("사수", row["role"])
        assertEquals("물리 공격", row["attack_type"])
        assertEquals("5", row["range"])
        assertTrue(row.getValue("portrait_crop").contains("세시아"))
    }

    /** 휴윈 항목도 사용자 Ground Truth 와 일치해야 한다. */
    @Test
    fun huwinManifestMatchesGroundTruth() {
        val row = manifest().single { it["name_ko"] == "휴윈" }
        assertEquals("와일더스", row["faction"])
        assertEquals("서포터", row["role"])
        assertEquals("마법 공격", row["attack_type"])
        assertEquals("4", row["range"])
    }

    /** 초상화 crop 이 두 영웅에게 공유되면 잘못된 얼굴이 표시된다. */
    @Test
    fun noPortraitIsSharedBetweenHeroes() {
        val crops = manifest().map { it.getValue("portrait_crop") }
        assertEquals(crops.size, crops.distinct().size)
    }

    /** 에이론 초상화는 사용자 원본 캡처에서 잘라낸 것이며 다른 영웅과 공유하지 않는다. */
    @Test
    fun eironHasItsOwnVerifiedPortrait() {
        val row = manifest().single { it["name_ko"] == "에이론" }
        assertEquals("와일더스", row["faction"])
        assertEquals("레인저", row["role"])
        assertEquals("마법 공격", row["attack_type"])
        assertEquals("1", row["range"])
        assertEquals("hero_eiron", row["hero_id"])

        // 런타임은 hero_id 로 자산 경로를 만든다.
        val portrait = asset("hero_recognition/portraits/${row["hero_id"]}.png")
        assertTrue("초상화 파일이 있어야 한다", portrait.exists())
        assertTrue("빈 파일이면 안 된다", portrait.length() > 1000)
    }

    /** 캡처 시점 전투력은 스냅샷이며 영구 스탯이 아니다. 그 사실이 행에 남아 있어야 한다. */
    @Test
    fun capturedPowerIsMarkedAsSnapshot() {
        val row = manifest().single { it["name_ko"] == "에이론" }
        assertEquals("16304", row["captured_power"])
        assertTrue(row.getValue("usage_note").contains("session-specific"))
    }

    // ---- helpers ----

    // org.json 은 Android 프레임워크 제공이라 JVM 테스트에서 쓸 수 없다.
    // 확인할 필드가 두 개뿐이라 의존성을 늘리지 않고 문자열로 뽑는다.
    private fun knowledge(): String =
        asset("learning/game_knowledge_20260821.json").readText(Charsets.UTF_8)

    private fun corrections(): List<Pair<String, String>> {
        val block = knowledge().substringAfter("\"nameCorrections\"").substringBefore("]")
        return Regex("\"wrong\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"correct\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(block)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
    }

    private fun heroesBlock(): String = knowledge().substringAfter("\"heroes\"").substringBefore("]")

    private fun heroNames(): List<String> =
        Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").findAll(heroesBlock()).map { it.groupValues[1] }.toList()

    private fun manifest(): List<Map<String, String>> {
        val lines = asset("hero_recognition/hero_manifest.csv")
            .readText(Charsets.UTF_8)
            .removePrefix("﻿")   // 런타임 로더와 같은 BOM 처리
            .lines()
            .filter { it.isNotBlank() }
        val header = lines.first().split(",")
        return lines.drop(1).map { line -> header.zip(line.split(",")).toMap() }
    }

    /** Gradle 과 직접 실행 모두에서 모듈 assets 를 찾는다. */
    private fun asset(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/$relative")
            if (candidate.exists()) return candidate
            val inModule = File(dir, "src/main/assets/$relative")
            if (inModule.exists()) return inModule
            dir = dir.parentFile
        }
        throw AssertionError("assets 를 찾지 못했다: $relative")
    }
}
