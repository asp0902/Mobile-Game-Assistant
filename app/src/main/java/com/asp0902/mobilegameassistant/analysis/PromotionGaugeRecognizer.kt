package com.asp0902.mobilegameassistant.analysis

import android.graphics.Bitmap
import android.graphics.Color

data class PromotionGaugeObservation(
    val progress: Int? = null,
    val required: Int? = null,
    val isMaxRank: Boolean = false,
    val confidence: Float = 0f,
    val reasons: List<String> = emptyList(),
)

data class OwnedHeroGaugeSlot(
    val slotIndex: Int,
    val bounds: NormalizedRect,
    val gauge: PromotionGaugeObservation,
    val heroId: String? = null,
)

object PromotionGaugeRecognizer {
    private const val CONFIRMED_SEGMENT_COUNT = 4

    fun fromSegments(active: Int, total: Int, maxRankText: Boolean = false): PromotionGaugeObservation = when {
        maxRankText -> PromotionGaugeObservation(isMaxRank = true, confidence = .95f, reasons = listOf("최대 등급 텍스트"))
        total <= 0 || active !in 0..total -> PromotionGaugeObservation(reasons = listOf("게이지 segment 불충분"))
        else -> PromotionGaugeObservation(active, total, confidence = .9f, reasons = listOf("활성 $active/$total segment"))
    }

    fun recognize(bitmap: Bitmap, heroBounds: NormalizedRect, slotText: String): PromotionGaugeObservation {
        if (slotText.contains("최대 등급")) return fromSegments(0, 0, maxRankText = true)
        val left = (heroBounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 2)
        val right = (heroBounds.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val top = ((heroBounds.top + (heroBounds.bottom - heroBounds.top) * .82f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 2)
        val bottom = ((heroBounds.top + (heroBounds.bottom - heroBounds.top) * .98f) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        val active = (0 until CONFIRMED_SEGMENT_COUNT).count { segment ->
            val start = left + (right - left) * segment / CONFIRMED_SEGMENT_COUNT
            val end = left + (right - left) * (segment + 1) / CONFIRMED_SEGMENT_COUNT
            greenRatio(bitmap, start, end, top, bottom) >= .08f
        }
        return if (active == 0 && !hasGaugeContrast(bitmap, left, right, top, bottom)) {
            PromotionGaugeObservation(reasons = listOf("게이지 시각 증거 부족"))
        } else {
            fromSegments(active, CONFIRMED_SEGMENT_COUNT)
        }
    }

    private fun greenRatio(bitmap: Bitmap, left: Int, right: Int, top: Int, bottom: Int): Float {
        var green = 0
        var sampled = 0
        val step = ((right - left).coerceAtMost(bottom - top) / 8).coerceAtLeast(1)
        for (y in top until bottom step step) for (x in left until right step step) {
            val color = bitmap.getPixel(x, y)
            if (Color.green(color) > Color.red(color) + 20 && Color.green(color) > Color.blue(color) + 10) green++
            sampled++
        }
        return green.toFloat() / sampled.coerceAtLeast(1)
    }

    private fun hasGaugeContrast(bitmap: Bitmap, left: Int, right: Int, top: Int, bottom: Int): Boolean {
        val values = (0 until CONFIRMED_SEGMENT_COUNT).map { segment ->
            val start = left + (right - left) * segment / CONFIRMED_SEGMENT_COUNT
            val end = left + (right - left) * (segment + 1) / CONFIRMED_SEGMENT_COUNT
            brightness(bitmap, start, end, top, bottom)
        }
        return (values.maxOrNull() ?: 0) - (values.minOrNull() ?: 0) >= 12
    }

    private fun brightness(bitmap: Bitmap, left: Int, right: Int, top: Int, bottom: Int): Int {
        var total = 0
        var sampled = 0
        val step = ((right - left).coerceAtMost(bottom - top) / 8).coerceAtLeast(1)
        for (y in top until bottom step step) for (x in left until right step step) {
            val color = bitmap.getPixel(x, y)
            total += (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
            sampled++
        }
        return total / sampled.coerceAtLeast(1)
    }
}
