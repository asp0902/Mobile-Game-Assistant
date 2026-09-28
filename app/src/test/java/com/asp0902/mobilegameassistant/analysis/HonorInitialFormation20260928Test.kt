package com.asp0902.mobilegameassistant.analysis

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

// Golden capture: app/src/test/assets/golden/honor-duel/honor-initial-20260928-device.png
// adb screencap, 1080x2316, 2026-09-28, no status bar/notification/overlay (see golden/honor-duel/README.md).
// Anchor ("선택" button) coordinates below are transcribed from the AFKTracker device log
// (3-0d/3-0h, versionCode 23-24, commit a2ddf0b), NOT an assertion about ML Kit OCR accuracy --
// JVM unit tests cannot run ML Kit. This only verifies recognition/hold behavior, never a pick.
class HonorInitialFormation20260928Test {
    private val unit = NormalizedRect(0f, 0f, 1f, 1f)
    private fun sourceImage() = ImageIO.read(File("src/test/assets/golden/honor-duel/honor-initial-20260928-device.png"))

    // Device-measured selectButton bounds (AFKTracker log) reversed to anchor center points:
    // card1 L0.7167 T0.3204 R0.9343 B0.3554, card2 L0.7176 T0.4560 R0.9343 B0.4905,
    // card3 L0.7167 T0.5950 R0.9343 B0.6300, card4 L0.7176 T0.7284 R0.9343 B0.7634.
    private fun anchor(centerX: Float, centerY: Float) =
        OcrBlock("선택", centerX - .02f, centerY - .008f, centerX + .02f, centerY + .008f)

    private fun blocks(): List<OcrBlock> = listOf(
        anchor(.8255f, .3379f),
        anchor(.82595f, .47325f),
        anchor(.8255f, .6125f),
        anchor(.82595f, .7459f),
        // Artifact title text only needs to land inside the card's rowBounds; exact position unmeasured.
        OcrBlock("고블린 가면", .05f, .30f, .25f, .32f),
        OcrBlock("달그림자 활", .05f, .44f, .25f, .46f),
        OcrBlock("평정의 샘물", .05f, .57f, .25f, .59f),
        OcrBlock("랜덤 진형 리스크 UP", .05f, .70f, .30f, .72f),
    )

    private fun cards() = InitialFormationLayout.cards(blocks(), GameViewport(0f, 0f, 1f, 1f))

    private fun references(): List<Pair<HeroReference, IntArray>> {
        val folders = listOf("initial_formation", "initial_formation_20260918", "initial_formation_20260928")
        return InitialFormationKnowledge.heroes.flatMap { hero ->
            folders.mapNotNull { folder ->
                val file = File("src/main/assets/hero_recognition/$folder/${hero.id}.png")
                if (!file.isFile) return@mapNotNull null
                val portrait = ImageIO.read(file)
                HeroReference(hero.id, hero.name, hero.faction) to
                    FormationPortraitFingerprint.sample(portrait.width, portrait.height, unit, portrait::getRGB)
            }
        }.distinctBy { it.first.koreanName }
    }

    private fun offers(): List<InitialFormationOffer> {
        val image = sourceImage()
        val refs = references()
        return cards().mapIndexed { index, card ->
            val rowText = blocks().filter { card.bounds.contains(it.centerX, it.centerY) }.joinToString(" ") { it.text }
            val (artifact, source) = InitialFormationLayout.artifact(rowText)
            val hidden = rowText.contains("랜덤")
            val heroes = if (hidden) emptyList() else card.portraits.mapIndexed { position, bounds ->
                val signature = FormationPortraitFingerprint.sample(image.width, image.height, bounds, image::getRGB)
                val scores = refs.map { (hero, ref) -> hero to FormationPortraitFingerprint.similarity(signature, ref) }
                val ranked = scores.sortedByDescending { it.second }
                val recognition = HeroIdentityResolver.resolveScores(scores)
                println("card${index + 1} slot$position: top3=${ranked.take(3).map { "${it.first.koreanName}:${"%.1f".format(it.second * 100)}%" }} " +
                    "margin=${"%.1f".format((ranked[0].second - ranked[1].second) * 100)}% status=${recognition.status}")
                InitialFormationHeroSlot(position, recognition.heroId, recognition.koreanName, recognition.faction,
                    InitialFormationKnowledge.hero(recognition.koreanName)?.role, recognition.confidence,
                    recognition.status, bounds)
            }
            InitialFormationOffer(index, artifact, .95f, source, heroes, card.bounds, card.button, hidden)
        }
    }

    @Test fun `2026-09-28 offer artifacts OCR-match and hero recognition matches confirmed layout`() {
        val result = offers()
        assertEquals(4, result.size)

        // Card-level artifact matching: all three fixed offers must resolve via OCR_MATCH.
        assertEquals(listOf("고블린 가면", "달그림자 활", "평정의 샘물", null), result.map { it.artifactName })
        assertEquals(listOf("OCR_MATCH", "OCR_MATCH", "OCR_MATCH", "UNMATCHED"), result.map { it.artifactSource })
        assertTrue(result.last().isRandom)
        assertTrue(result.last().heroSlots.isEmpty())

        // Hero-slot top-1 identity must match the user-confirmed 2026-09-28 layout (3-0f),
        // regardless of whether similarity clears the .88/.05 confirmation threshold.
        val expectedNames = listOf(
            listOf("카세디아", "카짐", "스모키와 미르키"),
            listOf("인듀어", "사리에", "이사벨라"),
            listOf("사리에", "갈라하드", "튜더"),
        )
        result.take(3).forEachIndexed { cardIndex, offer ->
            assertEquals("card${cardIndex + 1} hero names", expectedNames[cardIndex], offer.heroSlots.map { it.heroName })
        }

        // Never invent confidence: an UNKNOWN/NEEDS_CONFIRMATION slot must carry status != CONFIRMED
        // rather than a fabricated identity, and the recommend() safety gate must reflect that.
        val recommendations = InitialFormationAdvisor.recommend(result)
        assertEquals(4, recommendations.size)
        recommendations.forEach { rec ->
            println("card${rec.slotIndex + 1}: ${rec.action} score=${rec.score} reason=${rec.reason.take(80)}")
        }
    }
}
