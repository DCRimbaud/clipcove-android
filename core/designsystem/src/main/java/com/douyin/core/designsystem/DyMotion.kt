/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Central motion tokens and accessible press feedback shared by Compose screens.
 */
package com.douyin.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Central motion durations and easing curves shared by every screen. */
object DyMotion {
    const val QuickMs = 140
    const val StandardMs = 240
    const val ExpressiveMs = 360
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

/** Subtle press feedback for cards and navigation targets. */
@Composable
fun Modifier.dyPressable(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dyPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        enabled = enabled,
        role = role,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
    )
}