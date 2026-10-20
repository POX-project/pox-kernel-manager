package com.pox.kernelmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 1. Apple Liquid Glass (iOS-style glassmorphism palette)
private val AppleLiquidGlassColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),             // Apple System Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1E293B),    // Frosted Translucent Slate
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = Color(0xFF5E5CE6),           // Apple System Indigo
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = Color(0xFF64D2FF),            // Apple System Cyan
    onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF0C4A6E),
    onTertiaryContainer = Color(0xFFBAE6FD),
    background = Color(0xFF090D16),          // Deep Obsidian
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF38BDF8).copy(alpha = 0.35f),
    outlineVariant = Color(0xFF334155)
)

// 2. Material 3 Expressive (Google Pixel M3 palette)
private val Material3ExpressiveColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// 3. MIUI Classic (Vibrant energetic MIUI Orange & Blue)
private val MiuiColorScheme = darkColorScheme(
    primary = Color(0xFFFF6700),             // Iconic MIUI Orange
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5A1E00),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF0075FF),           // MIUI Electric Blue
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF003875),
    onSecondaryContainer = Color(0xFFCCE4FF),
    tertiary = Color(0xFF00C48C),            // MIUI Mint
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF004430),
    onTertiaryContainer = Color(0xFFB5FFDE),
    background = Color(0xFF101014),
    onBackground = Color(0xFFEEEEF2),
    surface = Color(0xFF181920),
    onSurface = Color(0xFFEEEEF2),
    surfaceVariant = Color(0xFF262732),
    onSurfaceVariant = Color(0xFFA5A7B8),
    outline = Color(0xFFFF6700).copy(alpha = 0.4f),
    outlineVariant = Color(0xFF363845)
)

// 4. HyperOS Sleek (Dark futuristic minimalism, neon cyan highlights)
private val HyperosColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),             // HyperOS Neon Cyan
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F59),
    onPrimaryContainer = Color(0xFF70F5FF),
    secondary = Color(0xFF7C4DFF),           // HyperOS Violet
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2B008A),
    onSecondaryContainer = Color(0xFFD6C7FF),
    tertiary = Color(0xFFFF2A6D),            // HyperOS Cyber Pink
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF630022),
    onTertiaryContainer = Color(0xFFFFD8E1),
    background = Color(0xFF06090F),          // Void Black
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0D121D),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF172033),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF00E5FF).copy(alpha = 0.45f),
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun PoxTheme(
    theme: AppTheme = ThemeManager.currentThemeState,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        AppTheme.APPLE_LIQUID_GLASS -> AppleLiquidGlassColorScheme
        AppTheme.MATERIAL3_EXPRESSIVE -> Material3ExpressiveColorScheme
        AppTheme.MIUI -> MiuiColorScheme
        AppTheme.HYPEROS -> HyperosColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            AppBackgroundContainer(content = content)
        }
    )
}
