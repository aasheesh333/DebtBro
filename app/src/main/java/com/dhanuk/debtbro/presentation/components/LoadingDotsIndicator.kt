package com.dhanuk.debtbro.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun LoadingDotsIndicator(modifier: Modifier = Modifier, color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val y by transition.animateFloat(initialValue = 0f, targetValue = -8f, animationSpec = infiniteRepeatable(tween(450, delayMillis = index * 140), RepeatMode.Reverse), label = "dot$index")
            Box(Modifier.offset { IntOffset(0, y.toInt()) }.size(8.dp).background(color, CircleShape))
        }
    }
}
