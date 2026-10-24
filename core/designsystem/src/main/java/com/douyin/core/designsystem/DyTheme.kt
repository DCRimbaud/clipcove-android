package com.douyin.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DyBg = Color(0xFF141518)
val DyCard = Color(0xFF1E1E22)
val DyInput = Color(0xFF26262B)
val DyRed = Color(0xFFFE2C55)
val DyCyan = Color(0xFF25F4EE)
val DyText = Color(0xFFFFFFFF)
val DyTextSecondary = Color(0xFF8A8A93)
val DyGreen = Color(0xFF22C55E)
val DyAmber = Color(0xFFF59E0B)

private val DyColorScheme = darkColorScheme(
    primary = DyRed,
    onPrimary = Color.White,
    secondary = DyCyan,
    background = DyBg,
    surface = DyCard,
    surfaceVariant = DyInput,
    onBackground = DyText,
    onSurface = DyText,
    onSurfaceVariant = DyTextSecondary,
)

@Composable
fun DyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DyColorScheme,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content
    )
}

