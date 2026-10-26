/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * ClipCove color, typography, and shape tokens applied through Material 3.
 */
package com.douyin.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val DyBg = Color(0xFF0B0D11)
val DyBgGlow = Color(0xFF1A1118)
val DyCard = Color(0xFF151820)
val DySurfaceHigh = Color(0xFF1C202A)
val DyInput = Color(0xFF12151B)
val DyBorder = Color(0xFF2A2F3A)
val DyRed = Color(0xFFFF466C)
val DyRedSoft = Color(0x26FF466C)
val DyCyan = Color(0xFF66E3DF)
val DyGreen = Color(0xFF4ADE80)
val DyAmber = Color(0xFFF6C453)
val DyText = Color(0xFFF7F8FA)
val DyTextSecondary = Color(0xFF9AA1AE)
val DyTextTertiary = Color(0xFF676F7D)

private val DyColorScheme = darkColorScheme(
    primary = DyRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1721),
    onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = DyCyan,
    onSecondary = Color(0xFF062825),
    background = DyBg,
    onBackground = DyText,
    surface = DyCard,
    onSurface = DyText,
    surfaceVariant = DyInput,
    onSurfaceVariant = DyTextSecondary,
    outline = DyBorder,
    outlineVariant = Color(0xFF20252E),
    error = Color(0xFFFF7A8D),
)

private val DyTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
/**
 * Applies ClipCove's dark Material 3 color, typography, and shape system.
 *
 * @param content Composable tree rendered inside the theme.
 */
fun DyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DyColorScheme,
        typography = DyTypography,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}