package com.asp0902.mobilegameassistant.formation

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

@Composable
fun FormationOverlay(
    template: FormationTemplate,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = size.minDimension * 0.045f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.62f
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
        }
        template.tiles.forEach { tile ->
            val center = Offset(tile.x * size.width, tile.y * size.height)
            drawCircle(Color(0xCC1B8E5A), radius, center)
            drawCircle(Color.White, radius, center, style = Stroke(2.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(
                tile.id,
                center.x,
                center.y - (textPaint.ascent() + textPaint.descent()) / 2,
                textPaint,
            )
        }
    }
}
