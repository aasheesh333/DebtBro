package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

@Composable
fun ConfettiOverlay(showConfetti: Boolean) {
    if (showConfetti) {
        Canvas(Modifier.fillMaxSize()) {
            val colors = listOf(Color(0xFF00E5A0), Color(0xFFFFD700), Color(0xFFFF4757), Color.White)
            repeat(80) { i ->
                rotate(i * 19f) {
                    drawCircle(colors[i % colors.size], radius = (4 + i % 8).toFloat(), center = center.copy(x = center.x + (i % 17) * 18f, y = center.y - (i % 23) * 22f))
                }
            }
        }
    }
}
