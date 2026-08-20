package com.asp0902.mobilegameassistant.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HeroRecognitionStatus { CONFIRMED, NEEDS_CONFIRMATION, UNKNOWN }

data class HeroReference(
    val id: String,
    val koreanName: String,
    val faction: String,
    val portraitAsset: String? = null,
)

data class HeroRecognitionResult(
    val heroId: String? = null,
    val koreanName: String? = null,
    val faction: String? = null,
    val confidence: Float = 0f,
    val status: HeroRecognitionStatus = HeroRecognitionStatus.UNKNOWN,
    val reasons: List<String> = emptyList(),
)

object HeroIdentityResolver {
    fun resolveText(text: String, heroes: List<HeroReference>): HeroRecognitionResult {
        val matches = heroes.filter { text.contains(it.koreanName) }
        return if (matches.size == 1) confirmed(matches.single(), "이름 OCR") else unknown("이름 OCR 불충분")
    }

    fun resolveScores(scores: List<Pair<HeroReference, Float>>): HeroRecognitionResult {
        val ranked = scores.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return unknown("초상화 템플릿 없음")
        val margin = best.second - (ranked.getOrNull(1)?.second ?: 0f)
        return when {
            best.second >= .88f && margin >= .05f -> confirmed(best.first, "초상화 유사도", "진영 템플릿")
            best.second >= .76f && margin >= .025f -> candidate(best.first, .75f, "초상화 후보 — 확인 필요")
            else -> unknown("초상화 구분 불충분")
        }
    }

    private fun confirmed(hero: HeroReference, vararg reasons: String) = HeroRecognitionResult(
        hero.id, hero.koreanName, hero.faction, .95f, HeroRecognitionStatus.CONFIRMED, reasons.toList(),
    )

    private fun candidate(hero: HeroReference, confidence: Float, reason: String) = HeroRecognitionResult(
        hero.id, hero.koreanName, hero.faction, confidence, HeroRecognitionStatus.NEEDS_CONFIRMATION, listOf(reason),
    )

    private fun unknown(reason: String) = HeroRecognitionResult(reasons = listOf(reason))
}

@Singleton
class HeroRecognitionCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val heroes by lazy { loadManifest() + SCREEN_CONFIRMED_HEROES }
    private val templates by lazy { heroes.mapNotNull(::template) }

    fun recognize(bitmap: Bitmap, bounds: NormalizedRect, text: String): HeroRecognitionResult {
        HeroIdentityResolver.resolveText(text, heroes).takeIf { it.status == HeroRecognitionStatus.CONFIRMED }?.let { return it }
        if (!SlotVisualEvidence.from(bitmap, bounds, bounds.bottom).likelyHeroPortrait) {
            return HeroRecognitionResult(reasons = listOf("영웅 초상화 증거 없음"))
        }
        return HeroIdentityResolver.resolveScores(templates.map { it.hero to similarity(signature(bitmap, bounds), it.signature) })
    }

    private fun loadManifest(): List<HeroReference> = context.assets.open("hero_recognition/hero_manifest.csv")
        .bufferedReader()
        .readLines()
        .drop(1)
        .mapNotNull { row ->
            val values = row.removePrefix("\uFEFF").split(',')
            if (values.size < 11) null else HeroReference(values[0], values[1], values[7], "hero_recognition/portraits/${values[0]}.png")
        }

    private fun template(hero: HeroReference): HeroTemplate? = hero.portraitAsset?.let { asset ->
        context.assets.open(asset).use(BitmapFactory::decodeStream)?.let { HeroTemplate(hero, signature(it, null)) }
    }

    private fun signature(bitmap: Bitmap, bounds: NormalizedRect?): IntArray {
        val crop = bounds?.let { crop(bitmap, it) } ?: bitmap
        val scaled = Bitmap.createScaledBitmap(crop, 16, 16, true)
        return IntArray(16 * 16) { index ->
            val color = scaled.getPixel(index % 16, index / 16)
            (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) / 3
        }
    }

    private fun crop(bitmap: Bitmap, bounds: NormalizedRect): Bitmap {
        val left = (bounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
        val top = (bounds.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
        val right = (bounds.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bounds.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun similarity(left: IntArray, right: IntArray): Float =
        1f - left.indices.sumOf { kotlin.math.abs(left[it] - right[it]) }.toFloat() / (left.size * 255)

    private data class HeroTemplate(val hero: HeroReference, val signature: IntArray)

    private companion object {
        // README/manifest lacks these older Honor Duel portraits; direct screen-confirmed name only.
        val SCREEN_CONFIRMED_HEROES = listOf(
            HeroReference("valen", "발렌", "레오프론"),
            HeroReference("guinness", "귀네스", "레오프론"),
            HeroReference("tiloa", "틸로아", "와일더스"),
            HeroReference("perseus", "페르세우스", "레오프론"),
        )
    }
}
