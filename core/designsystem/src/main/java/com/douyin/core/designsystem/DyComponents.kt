/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (c) 2026 DCRimbaud
 * Author: DCRimbaud
 *
 * Shared Compose surfaces, headers, pills, and empty states for the ClipCove design system.
 */
package com.douyin.core.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
/**
 * Paints the static ClipCove ambient background and hosts page content.
 *
 * The background uses only static gradients; it does not animate or allocate
 * per-frame state. Callers remain responsible for sizing and safe insets.
 */
fun DyAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(DyBgGlow, DyBg, DyBg),
                        endY = size.height * 0.58f,
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(DyRed.copy(alpha = 0.085f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.03f),
                        radius = size.maxDimension * 0.72f,
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(DyCyan.copy(alpha = 0.045f), Color.Transparent),
                        center = Offset(size.width * 0.96f, size.height * 0.26f),
                        radius = size.maxDimension * 0.62f,
                    )
                )
            },
        content = content,
    )
}

@Composable
/**
 * Renders the primary page title, optional eyebrow, and trailing action.
 *
 * Text is constrained to avoid crowding compact screens. The trailing slot is
 * intentionally caller-owned so navigation actions remain screen-specific.
 */
fun DyPageHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 2.dp)
                            .background(
                                Brush.horizontalGradient(listOf(DyRed, DyCyan)),
                                RoundedCornerShape(50),
                            )
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        eyebrow.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = DyRed,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(7.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = DyText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DyTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
/**
 * Renders a section heading with the shared accent rail and optional trailing slot.
 */
fun DySectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(
                            Brush.verticalGradient(listOf(DyRed, DyRed.copy(alpha = 0.25f))),
                            RoundedCornerShape(50),
                        )
                )
                Spacer(Modifier.size(7.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = DyText)
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DyTextSecondary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
/**
 * Shared elevated panel with content-size animation for changing state.
 *
 * The panel performs no business logic and accepts arbitrary content actions.
 */
fun DyPanel(
    modifier: Modifier = Modifier,
    color: Color = DyCard,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = DyMotion.StandardMs,
                    easing = DyMotion.StandardEasing,
                )
            ),
        shape = RoundedCornerShape(22.dp),
        color = color,
        border = BorderStroke(1.dp, DyBorder.copy(alpha = 0.72f)),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
/**
 * Compact status pill with an optional leading icon.
 *
 * @param text User-visible label.
 * @param color Accent color used for background, border, and icon.
 * @param icon Optional semantic icon.
 */
fun DyPill(
    text: String,
    color: Color = DyRed,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
    }
}

@Composable
/**
 * Circular accent tile used consistently for metadata, actions, and empty states.
 *
 * @param icon Vector icon to render.
 * @param tint Accent color applied to the gradient, border, and icon.
 * @param size Tile size in density-independent pixels.
 */
fun DyIconTile(
    icon: ImageVector,
    tint: Color = DyRed,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.06f)),
                ),
                CircleShape,
            )
            .border(1.dp, tint.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
/**
 * Shared empty-state block for lists and unavailable content.
 *
 * @param icon Visual anchor for the state.
 * @param title Short state title.
 * @param description Action-oriented explanation.
 * @param action Optional recovery action owned by the caller.
 */
fun DyEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DyIconTile(icon = icon, tint = DyRed, size = 58.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = DyText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = DyTextSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}