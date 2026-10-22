package com.pox.kernelmanager.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class ThemeVisualTokens(
    val cardShape: Shape,
    val cardBackground: Color,
    val cardBorder: BorderStroke?,
    val pillShape: Shape,
    val topBarBackground: Color,
    val navBarBackground: Color,
    val heroGradient: Brush,
    val accentBrush: Brush,
    val badgeBackground: Color,
    val isGlass: Boolean,
    val themeAccent: Color
)

val material3Tokens: ThemeVisualTokens
    @Composable
    get() {
        val colorScheme = MaterialTheme.colorScheme
        return ThemeVisualTokens(
            cardShape = RoundedCornerShape(20.dp),
            cardBackground = colorScheme.surfaceContainer,
            cardBorder = null,
            pillShape = RoundedCornerShape(16.dp),
            topBarBackground = Color.Transparent,
            navBarBackground = colorScheme.surfaceContainer,
            heroGradient = Brush.verticalGradient(
                listOf(
                    colorScheme.primaryContainer.copy(alpha = 0.90f),
                    colorScheme.surfaceContainerHigh
                )
            ),
            accentBrush = Brush.horizontalGradient(
                listOf(
                    colorScheme.primary,
                    colorScheme.tertiary
                )
            ),
            badgeBackground = colorScheme.secondaryContainer,
            isGlass = false,
            themeAccent = colorScheme.primary
        )
    }

// Backward-compatibility aliases mapped strictly to Material 3
val material3ExpressiveTokens: ThemeVisualTokens @Composable get() = material3Tokens
val appleLiquidGlassTokens: ThemeVisualTokens @Composable get() = material3Tokens
val miuiTokens: ThemeVisualTokens @Composable get() = material3Tokens
val hyperosTokens: ThemeVisualTokens @Composable get() = material3Tokens

val LocalThemeTokens = staticCompositionLocalOf {
    ThemeVisualTokens(
        cardShape = RoundedCornerShape(20.dp),
        cardBackground = Color(0xFF1D201F),
        cardBorder = null,
        pillShape = RoundedCornerShape(16.dp),
        topBarBackground = Color.Transparent,
        navBarBackground = Color(0xFF1D201F),
        heroGradient = Brush.verticalGradient(
            listOf(Color(0xFF005138), Color(0xFF1D201F))
        ),
        accentBrush = Brush.horizontalGradient(
            listOf(Color(0xFF6EDCAC), Color(0xFFA5CCDF))
        ),
        badgeBackground = Color(0xFF354B40),
        isGlass = false,
        themeAccent = Color(0xFF6EDCAC)
    )
}

@Composable
fun PoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    border: BorderStroke? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isWallpaper = ThemeManager.backgroundModeState != BackgroundMode.PURE_SURFACE
    val defaultColor = if (isWallpaper) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val defaultBorder = if (isWallpaper && border == null) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    } else border

    val effectiveColor = containerColor ?: defaultColor

    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            border = defaultBorder,
            colors = CardDefaults.cardColors(containerColor = effectiveColor),
            modifier = modifier,
            content = content
        )
    } else {
        Card(
            shape = shape,
            border = defaultBorder,
            colors = CardDefaults.cardColors(containerColor = effectiveColor),
            modifier = modifier,
            content = content
        )
    }
}
