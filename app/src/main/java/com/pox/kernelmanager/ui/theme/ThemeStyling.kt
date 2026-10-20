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

val appleLiquidGlassTokens: ThemeVisualTokens
    get() {
        val opacity = ThemeManager.backgroundConfigState.opacity.coerceIn(0.25f, 0.95f)
        return ThemeVisualTokens(
            cardShape = RoundedCornerShape(26.dp),
            cardBackground = Color(0xFF131D31).copy(alpha = opacity * 0.70f),
            cardBorder = BorderStroke(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f),
                        Color(0xFF0A84FF).copy(alpha = 0.35f)
                    )
                )
            ),
            pillShape = RoundedCornerShape(50.dp),
            topBarBackground = Color(0xFF0B1120).copy(alpha = 0.75f),
            navBarBackground = Color(0xFF0B1120).copy(alpha = 0.85f),
            heroGradient = Brush.linearGradient(
                listOf(
                    Color(0xFF0A84FF).copy(alpha = 0.40f),
                    Color(0xFF5E5CE6).copy(alpha = 0.25f),
                    Color(0xFF0F172A).copy(alpha = 0.70f)
                )
            ),
            accentBrush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF0A84FF),
                    Color(0xFF64D2FF)
                )
            ),
            badgeBackground = Color(0xFF0A84FF).copy(alpha = 0.20f),
            isGlass = true,
            themeAccent = Color(0xFF0A84FF)
        )
    }

val material3ExpressiveTokens: ThemeVisualTokens
    get() {
        return ThemeVisualTokens(
            cardShape = RoundedCornerShape(32.dp),
            cardBackground = Color(0xFF272330),
            cardBorder = BorderStroke(1.dp, Color(0xFF4E465A).copy(alpha = 0.65f)),
            pillShape = RoundedCornerShape(18.dp),
            topBarBackground = Color(0xFF1A1820).copy(alpha = 0.96f),
            navBarBackground = Color(0xFF1A1820).copy(alpha = 0.96f),
            heroGradient = Brush.linearGradient(
                listOf(
                    Color(0xFF4F378B),
                    Color(0xFF381E72),
                    Color(0xFF211D2A)
                )
            ),
            accentBrush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFD0BCFF),
                    Color(0xFFCCC2DC)
                )
            ),
            badgeBackground = Color(0xFF4F378B).copy(alpha = 0.45f),
            isGlass = false,
            themeAccent = Color(0xFFD0BCFF)
        )
    }

val miuiTokens: ThemeVisualTokens
    get() {
        return ThemeVisualTokens(
            cardShape = RoundedCornerShape(20.dp),
            cardBackground = Color(0xFF1B1D26),
            cardBorder = BorderStroke(1.dp, Color(0xFF34384B)),
            pillShape = RoundedCornerShape(14.dp),
            topBarBackground = Color(0xFF13141B).copy(alpha = 0.98f),
            navBarBackground = Color(0xFF13141B).copy(alpha = 0.98f),
            heroGradient = Brush.linearGradient(
                listOf(
                    Color(0xFF6E2400),
                    Color(0xFF2E1205),
                    Color(0xFF1B1D26)
                )
            ),
            accentBrush = Brush.horizontalGradient(
                listOf(
                    Color(0xFFFF6700),
                    Color(0xFFFF8533)
                )
            ),
            badgeBackground = Color(0xFFFF6700).copy(alpha = 0.22f),
            isGlass = false,
            themeAccent = Color(0xFFFF6700)
        )
    }

val hyperosTokens: ThemeVisualTokens
    get() {
        return ThemeVisualTokens(
            cardShape = RoundedCornerShape(12.dp),
            cardBackground = Color(0xFF070B13),
            cardBorder = BorderStroke(
                1.3.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = 0.90f),
                        Color(0xFF7C4DFF).copy(alpha = 0.65f),
                        Color(0xFF00E5FF).copy(alpha = 0.35f)
                    )
                )
            ),
            pillShape = RoundedCornerShape(8.dp),
            topBarBackground = Color(0xFF04070D).copy(alpha = 0.98f),
            navBarBackground = Color(0xFF04070D).copy(alpha = 0.98f),
            heroGradient = Brush.linearGradient(
                listOf(
                    Color(0xFF004D5A),
                    Color(0xFF1B0B3E),
                    Color(0xFF070B13)
                )
            ),
            accentBrush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF00E5FF),
                    Color(0xFF7C4DFF)
                )
            ),
            badgeBackground = Color(0xFF00E5FF).copy(alpha = 0.18f),
            isGlass = false,
            themeAccent = Color(0xFF00E5FF)
        )
    }

val LocalThemeTokens = staticCompositionLocalOf {
    appleLiquidGlassTokens
}

@Composable
fun PoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape? = null,
    border: BorderStroke? = null,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalThemeTokens.current
    val effectiveShape = shape ?: tokens.cardShape
    val effectiveBorder = border ?: tokens.cardBorder
    val effectiveColor = containerColor ?: tokens.cardBackground

    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            shape = effectiveShape,
            border = effectiveBorder,
            colors = CardDefaults.cardColors(containerColor = effectiveColor),
            modifier = modifier,
            content = content
        )
    } else {
        Card(
            shape = effectiveShape,
            border = effectiveBorder,
            colors = CardDefaults.cardColors(containerColor = effectiveColor),
            modifier = modifier,
            content = content
        )
    }
}
