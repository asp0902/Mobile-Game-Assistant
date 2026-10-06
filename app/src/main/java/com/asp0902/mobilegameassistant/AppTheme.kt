package com.asp0902.mobilegameassistant

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object AppColors {
    val SkyTop = Color(0xFF0F314B)
    val SkyMiddle = Color(0xFF07243C)
    val SkyBottom = Color(0xFF050A14)
    val GoldText = Color(0xFFE4C672)
    val GoldBand = Color(0xFF9F7F50)
    val GoldBandDark = Color(0xFF6A532D)
    val Body = Color(0xFFEDE6D6)
    val Panel = Color(0xCC0B1A2C)
    val Background = Brush.verticalGradient(listOf(SkyTop, SkyMiddle, SkyBottom))
    val GoldBandBrush = Brush.horizontalGradient(listOf(GoldBand, GoldBandDark))
}

private val AppColorScheme = darkColorScheme(
    primary = AppColors.GoldBandDark,
    onPrimary = AppColors.GoldText,
    secondary = AppColors.GoldBand,
    onSecondary = AppColors.SkyBottom,
    background = AppColors.SkyMiddle,
    onBackground = AppColors.Body,
    surface = Color(0xFF0B1A2C),
    onSurface = AppColors.Body,
    onSurfaceVariant = AppColors.Body.copy(alpha = 0.7f),
    outline = AppColors.GoldBand,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme) {
        CompositionLocalProvider(LocalContentColor provides AppColors.Body, content = content)
    }
}

fun Modifier.appBackground(): Modifier = background(AppColors.Background)
