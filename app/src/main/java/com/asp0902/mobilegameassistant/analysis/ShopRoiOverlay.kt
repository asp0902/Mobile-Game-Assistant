package com.asp0902.mobilegameassistant.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun ShopRoiOverlay(items: List<ShopItemState>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        items.forEach { item -> item.bounds?.let { bounds ->
            drawRect(
                color = Color.Cyan,
                topLeft = Offset(size.width * bounds.left, size.height * bounds.top),
                size = Size(size.width * (bounds.right - bounds.left), size.height * (bounds.bottom - bounds.top)),
                style = Stroke(width = 2f),
            )
        } }
    }
}
