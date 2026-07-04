package com.familyshield.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.dp

/** Animated shimmer fill for skeleton placeholders while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    background(
        brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x, 0f),
            end = Offset(x + 300f, 0f),
        ),
    )
}

/** A rounded skeleton block. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .shimmer(),
    )
}

/** Subtle scale-down while pressed for a tactile feel on cards/rows. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fade + slide-up entrance; pass a staggered [delayMillis] for a cascade. */
fun Modifier.appear(delayMillis: Int = 0): Modifier = composed {
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong()); shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 460,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "appear",
    )
    graphicsLayer { alpha = progress; translationY = (1f - progress) * 46f }
}
