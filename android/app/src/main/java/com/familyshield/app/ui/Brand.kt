package com.familyshield.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyshield.app.ui.theme.Cyan
import com.familyshield.app.ui.theme.Indigo
import com.familyshield.app.ui.theme.Blue
import com.familyshield.app.ui.theme.Ok

// Soft avatar palette ג€” picked deterministically from the name.
private val avatarColors = listOf(
    Color(0xFF2F73FF) to Color(0xFFE6EEFF),
    Color(0xFF12B886) to Color(0xFFDFF7EF),
    Color(0xFFFF9F1C) to Color(0xFFFFF1DC),
    Color(0xFFF5524B) to Color(0xFFFFE5E4),
    Color(0xFF7C5CFC) to Color(0xFFEEE8FF),
    Color(0xFF18AEE6) to Color(0xFFE0F5FE),
)

data class ChildAvatarOption(val key: String, val emoji: String, val label: String)

val childAvatarOptions = listOf(
    ChildAvatarOption("fox", "\uD83E\uDD8A", "Fox"),
    ChildAvatarOption("panda", "\uD83D\uDC3C", "Panda"),
    ChildAvatarOption("tiger", "\uD83D\uDC2F", "Tiger"),
    ChildAvatarOption("unicorn", "\uD83E\uDD84", "Unicorn"),
    ChildAvatarOption("bunny", "\uD83D\uDC30", "Bunny"),
    ChildAvatarOption("koala", "\uD83D\uDC28", "Koala"),
    ChildAvatarOption("monkey", "\uD83D\uDC35", "Monkey"),
    ChildAvatarOption("frog", "\uD83D\uDC38", "Frog"),
    ChildAvatarOption("dog", "\uD83D\uDC36", "Dog"),
    ChildAvatarOption("cat", "\uD83D\uDC31", "Cat"),
    ChildAvatarOption("bear", "\uD83D\uDC3B", "Bear"),
    ChildAvatarOption("penguin", "\uD83D\uDC27", "Penguin"),
    ChildAvatarOption("lion", "\uD83E\uDD81", "Lion"),
    ChildAvatarOption("duck", "\uD83E\uDD86", "Duck"),
    ChildAvatarOption("chick", "\uD83D\uDC24", "Chick"),
    ChildAvatarOption("hamster", "\uD83D\uDC39", "Hamster"),
    ChildAvatarOption("mouse", "\uD83D\uDC2D", "Mouse"),
    ChildAvatarOption("pig", "\uD83D\uDC37", "Pig"),
    ChildAvatarOption("cow", "\uD83D\uDC2E", "Cow"),
    ChildAvatarOption("horse", "\uD83D\uDC34", "Horse"),
    ChildAvatarOption("wolf", "\uD83D\uDC3A", "Wolf"),
    ChildAvatarOption("owl", "\uD83E\uDD89", "Owl"),
    ChildAvatarOption("turtle", "\uD83D\uDC22", "Turtle"),
    ChildAvatarOption("dolphin", "\uD83D\uDC2C", "Dolphin"),
)

private fun pick(name: String, size: Int): Int = (name.hashCode() % size + size) % size

@Composable
fun Avatar(name: String, size: Dp = 44.dp, online: Boolean? = null, avatar: String? = null) {
    val (fg, bg) = avatarColors[pick(name, avatarColors.size)]
    val emoji = childAvatarOptions.firstOrNull { it.key == avatar }?.emoji
        ?: childAvatarOptions[pick(name + "x", childAvatarOptions.size)].emoji
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(Brush.linearGradient(listOf(bg, fg.copy(alpha = 0.22f))))
                .then(if (online == true) Modifier.border(2.dp, Ok, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = (size.value * 0.5f).sp)
        }
        if (online != null) {
            val badge = if (online) Ok else Color(0xFFB6C2D6)
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.30f)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp).clip(CircleShape).background(badge),
            )
        }
    }
}

/** A gently "breathing" dot ג€” used for live/online indicators. */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha",
    )
    Box(Modifier.size(size).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(color))
}

@Composable
fun StatusChip(
    text: String,
    color: Color,
    leadingIcon: ImageVector? = null,
    dot: Boolean = false,
    pulse: Boolean = false,
) {
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot && pulse) PulsingDot(color)
            else if (dot) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            if (leadingIcon != null) Icon(leadingIcon, null, tint = color, modifier = Modifier.size(15.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A slowly drifting version of the brand gradient ג€” a subtle living background. */
@Composable
fun rememberAnimatedBrandBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "brand")
    val shift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
        label = "brandShift",
    )
    return Brush.linearGradient(
        colors = listOf(Blue, Indigo, Cyan),
        start = Offset(shift * 260f, 0f),
        end = Offset(900f + shift * 260f, 1100f),
    )
}

/** Full-width gradient call-to-action button with a press-scale effect. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    gradient: Brush = Brush.linearGradient(listOf(Blue, Indigo, Cyan)),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnScale")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) gradient else Brush.linearGradient(listOf(Color(0xFFB8C4DE), Color(0xFFB8C4DE))))
            .then(if (enabled && !loading) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/** Decorative translucent blobs for hero headers ג€” adds depth over a flat gradient. */
@Composable
fun BoxScope.HeroBlobs() {
    Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-50).dp).size(180.dp)
        .clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
    Box(Modifier.align(Alignment.BottomStart).offset(x = (-30).dp, y = 40.dp).size(120.dp)
        .clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
}
