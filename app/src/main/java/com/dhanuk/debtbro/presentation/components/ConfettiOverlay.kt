package com.dhanuk.debtbro.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ConfettiPiece(
    val offsetX: Float = Random.nextFloat(),
    val offsetY: Float = Random.nextFloat(),
    val color: Color = listOf(Color(0xFF00E5A0), Color(0xFFFFD700), Color(0xFFFF4757), Color(0xFF7C4DFF), Color(0xFF448AFF), Color.White).random(),
    val size: Float = (6 + Random.nextInt(10)).toFloat(),
    val rotation: Float = Random.nextFloat() * 360f,
    val speed: Float = 0.3f + Random.nextFloat() * 0.7f,
    val angle: Float = Random.nextFloat() * 360f
)

@Composable
fun ConfettiOverlay(showConfetti: Boolean, onAnimationEnd: () -> Unit = {}) {
    var visible by remember { mutableStateOf(false) }
    val pieces = remember { List(60) { ConfettiPiece() } }

    LaunchedEffect(showConfetti) {
        if (showConfetti) {
            visible = true
            delay(2500)
            visible = false
            onAnimationEnd()
        }
    }

    if (visible) {
        val infiniteTransition = rememberInfiniteTransition(label = "confetti")
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "confetti_progress"
        )

        Canvas(Modifier.fillMaxSize()) {
            pieces.forEach { piece ->
                val fall = (progress * 1.5f + piece.offsetY) % 1.2f
                val sway = sin(progress * 4f + piece.offsetX * 6f) * 30f
                val x = (piece.offsetX * size.width + sway) % size.width
                val y = fall * size.height - 50f
                rotate(piece.rotation + progress * 180f, pivot = Offset(x, y)) {
                    drawRect(
                        color = piece.color,
                        topLeft = Offset(x - piece.size / 2, y - piece.size / 2),
                        size = androidx.compose.ui.geometry.Size(piece.size * 0.6f, piece.size)
                    )
                }
            }
        }
    }
}
