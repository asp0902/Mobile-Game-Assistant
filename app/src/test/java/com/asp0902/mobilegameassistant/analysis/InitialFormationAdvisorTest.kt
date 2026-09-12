package com.asp0902.mobilegameassistant.analysis

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test

class InitialFormationAdvisorTest {
    private val unit = NormalizedRect(0f, 0f, 1f, 1f)
    private fun sourceImage() = ImageIO.read(javaClass.getResource("/initial_formation/selection.png"))
    // Transcribed OCR coordinates, NOT an assertion about ML Kit OCR accuracy.
    private fun blocks(): List<OcrBlock> = listOf(648f, 888f, 1133f, 1370f).flatMapIndexed { index, y ->
        listOf(
            OcrBlock("선택", 665f / 833, (y - 15) / 1822, 718f / 833, (y + 15) / 1822),
            OcrBlock(listOf("신성한 소환", "고블린 가면", "평정의 샘물", "랜덤 진형 리스크 UP")[index],
                40f / 833, (y + 14) / 1822, 150f / 833, (y + 30) / 1822),
        )
    }
    private fun cards() = InitialFormationLayout.cards(blocks(), GameViewport(0f, 49f / 1822, 1f, 1f))
    private fun imageOffers(): List<InitialFormationOffer> {
        val image = sourceImage()
        val verified = InitialFormationKnowledge.heroes.map { hero ->
            val portrait = ImageIO.read(File("src/main/assets/hero_recognition/initial_formation/${hero.id}.png"))
            HeroReference(hero.id, hero.name, hero.faction) to
                FormationPortraitFingerprint.sample(portrait.width, portrait.height, unit, portrait::getRGB)
        }
        val existing = File("src/main/assets/hero_recognition/hero_manifest.csv").readLines().drop(1).mapNotNull { row ->
            val values = row.split(',')
            val file = File("src/main/assets/hero_recognition/portraits/${values[0]}.png")
            if (!file.isFile) null else {
                val portrait = ImageIO.read(file)
                HeroReference(values[0], values[1], values[7]) to
                    FormationPortraitFingerprint.sample(portrait.width, portrait.height, unit, portrait::getRGB)
            }
        }
        val references = (verified + existing).distinctBy { it.first.koreanName }
        return cards().mapIndexed { index, card ->
            val rowText = blocks().filter { card.bounds.contains(it.centerX, it.centerY) }.joinToString(" ") { it.text }
            val (artifact, source) = InitialFormationLayout.artifact(rowText)
            val hidden = rowText.contains("랜덤")
            val heroes = if (hidden) emptyList() else card.portraits.mapIndexed { position, bounds ->
                val signature = FormationPortraitFingerprint.sample(image.width, image.height, bounds, image::getRGB)
                val scores = references.map { (hero, ref) -> hero to FormationPortraitFingerprint.similarity(signature, ref) }
                val recognition = HeroIdentityResolver.resolveScores(scores)
                println("image row=$index portrait=$position: ${scores.sortedByDescending { it.second }.take(2).map { it.first.koreanName to it.second }} status=${recognition.status}")
                InitialFormationHeroSlot(position, recognition.heroId, recognition.koreanName, recognition.faction,
                    InitialFormationKnowledge.hero(recognition.koreanName)?.role, recognition.confidence,
                    recognition.status, bounds)
            }
            InitialFormationOffer(index, artifact, .95f, source, heroes, card.bounds, card.button, hidden)
        }
    }

    @Test fun popupReferencesRecognizeNineSeparateSmallPortraitsAndRecommend() {
        val offers = imageOffers()
        assertEquals(4, offers.size)
        assertEquals(listOf("신성한 소환", "고블린 가면", "평정의 샘물", null), offers.map { it.artifactName })
        assertEquals(InitialFormationKnowledge.heroes.map { it.name }, offers.flatMap { it.heroSlots }.map { it.heroName })
        assertTrue(offers.flatMap { it.heroSlots }.all { it.status == HeroRecognitionStatus.CONFIRMED })
        assertTrue(offers.last().isRandom)
        assertTrue(offers.last().heroSlots.isEmpty())
        val recommendations = InitialFormationAdvisor.recommend(offers)
        assertEquals(1, recommendations.count { it.action == InitialFormationAction.SELECT })
        val chosen = recommendations.single { it.action == InitialFormationAction.SELECT }
        assertEquals(2, chosen.slotIndex)
        println("Example recommendation: ${chosen.reason}")
        val reordered = offers.reversed().mapIndexed { index, offer -> offer.copy(slotIndex = index) }
        val reorderedChoice = InitialFormationAdvisor.recommend(reordered).single { it.action == InitialFormationAction.SELECT }
        assertEquals("평정의 샘물", reordered[reorderedChoice.slotIndex].artifactName)
    }

    @Test fun unknownAndRandomPixelsAreNotForcedToKnownIdentity() {
        val image = sourceImage()
        val randomPortrait = cards().last().portraits.first()
        val signature = FormationPortraitFingerprint.sample(image.width, image.height, randomPortrait, image::getRGB)
        val scores = InitialFormationKnowledge.heroes.map { hero ->
            val portrait = ImageIO.read(File("src/main/assets/hero_recognition/initial_formation/${hero.id}.png"))
            HeroReference(hero.id, hero.name, hero.faction) to FormationPortraitFingerprint.similarity(signature,
                FormationPortraitFingerprint.sample(portrait.width, portrait.height, unit, portrait::getRGB))
        }
        assertNotEquals(HeroRecognitionStatus.CONFIRMED, HeroIdentityResolver.resolveScores(scores).status)
        assertNull(InitialFormationLayout.artifact("알 수 없는 장치").first)
        assertNull(InitialFormationLayout.artifact("신성한 소환 고블린 가면").first)
        assertEquals("TITLE_INFERENCE", InitialFormationLayout.artifact("속전속결 전술").second)
        assertNull(InitialFormationAdvisor.evaluate(InitialFormationOffer(0)).score)
    }

    private fun offer(artifact: String, vararg names: String): InitialFormationOffer = InitialFormationOffer(
        slotIndex = 0, artifactName = artifact, artifactSource = "OCR_MATCH", artifactConfidence = .95f,
        heroSlots = names.mapIndexed { index, name ->
            val hero = InitialFormationKnowledge.hero(name)!!
            InitialFormationHeroSlot(index, hero.id, name, hero.faction, hero.role, .95f,
                HeroRecognitionStatus.CONFIRMED, unit)
        },
    )

    @Test fun effectsAreBaseOnlyAndExplainRisksWithoutInventingStats() {
        val spring = InitialFormationAdvisor.evaluate(offer("평정의 샘물", "메이", "루보미르", "프라벨"))
        assertTrue(spring.reason.contains("와일더스 회복 대상 2명: 메이, 프라벨"))
        assertTrue(spring.reason.contains("경험치 24·46 잠금 효과 제외"))
        assertTrue(spring.reason.contains("등급별 8/10/20% 대응 미확인"))
        val changed = InitialFormationAdvisor.evaluate(offer("평정의 샘물", "페르세우스", "딜그레이", "보라시아"))
        assertTrue(spring.score!! > changed.score!!)
        val mask = InitialFormationAdvisor.evaluate(offer("고블린 가면", "카세디아", "카짐", "스모키와 미르키"))
        assertTrue(mask.reason.contains("20초 동안 반환"))
        assertTrue(mask.reason.contains("에어본 조건을 제공하는 아군은 미확인"))
        val summon = InitialFormationAdvisor.evaluate(offer("신성한 소환", "페르세우스", "딜그레이", "보라시아"))
        assertTrue(summon.reason.contains("HP 75%·공격력 80% 손실"))
        assertTrue(summon.reason.contains("희생 대상·소환 결과·계승 공식 미확인"))
    }

    @Test fun tiesAndMissingIdentitiesDoNotProduceArbitraryWinner() {
        val candidate = offer("평정의 샘물", "메이", "루보미르", "프라벨")
        assertTrue(InitialFormationAdvisor.recommend(listOf(candidate, candidate.copy(slotIndex = 1)))
            .all { it.action != InitialFormationAction.SELECT && it.reason.contains("동률") })
        val partial = candidate.copy(heroSlots = candidate.heroSlots.dropLast(1))
        assertNull(InitialFormationAdvisor.evaluate(partial).score)
    }

    @Test fun popupsOverrideVisibleInitialSelectionTitle() {
        val background = "초기 진형을 선택하세요! 선택 선택 선택 선택"
        val other = ScreenClassification(ScreenType.OTHER, .9f, emptyList())
        assertEquals(ScreenType.HERO_DETAIL_POPUP, HonorDuelScreenClassifier.classifyNonShop(
            "$background 페르세우스 레오프론 전사 사정거리 1 에픽", other).type)
        assertEquals(ScreenType.EQUIPMENT_DETAIL_POPUP, HonorDuelScreenClassifier.classifyNonShop(
            "$background 평정의 샘물 경험치 0/24 46", other).type)
        assertEquals(ScreenType.HONOR_DUEL_INITIAL_FORMATION_SELECTION,
            HonorDuelScreenClassifier.classifyNonShop(background, other).type)
        assertEquals(0, InitialFormationLayout.cards(listOf(blocks().first()), GameViewport(0f, 0f, 1f, 1f)).size)
    }
}
