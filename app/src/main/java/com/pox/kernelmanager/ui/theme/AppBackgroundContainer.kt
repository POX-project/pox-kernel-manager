package com.pox.kernelmanager.ui.theme

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppBackgroundContainer(
    content: @Composable () -> Unit
) {
    val bgConfig = ThemeManager.backgroundConfigState
    val currentTheme = ThemeManager.currentThemeState
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        when (bgConfig.type) {
            BackgroundType.SOLID_COLOR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(bgConfig.colorHex))
                )
            }
            BackgroundType.CUSTOM_IMAGE -> {
                val bitmap = remember(bgConfig.imageUri) {
                    if (!bgConfig.imageUri.isNullOrBlank()) {
                        try {
                            val uri = Uri.parse(bgConfig.imageUri)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Custom Background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(bgConfig.blurRadiusDp.dp)
                    )
                } else {
                    DefaultAestheticGradient(currentTheme = currentTheme, blurDp = bgConfig.blurRadiusDp)
                }
            }
            BackgroundType.THEME_DEFAULT -> {
                DefaultAestheticGradient(currentTheme = currentTheme, blurDp = bgConfig.blurRadiusDp)
            }
        }

        // Tint / Opacity Scrim to ensure complete readability across all themes
        val scrimAlpha = (1f - bgConfig.opacity).coerceIn(0.1f, 0.95f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha))
        )

        content()
    }
}

@Composable
private fun DefaultAestheticGradient(currentTheme: AppTheme, blurDp: Float) {
    val gradientColors = when (currentTheme) {
        AppTheme.APPLE_LIQUID_GLASS -> listOf(
            Color(0xFF0A192F),
            Color(0xFF1E3A8A),
            Color(0xFF0F172A)
        )
        AppTheme.MATERIAL3_EXPRESSIVE -> listOf(
            Color(0xFF1B1B22),
            Color(0xFF2E243D),
            Color(0xFF1B1B22)
        )
        AppTheme.MIUI -> listOf(
            Color(0xFF1A0B05),
            Color(0xFF331608),
            Color(0xFF101424)
        )
        AppTheme.HYPEROS -> listOf(
            Color(0xFF030712),
            Color(0xFF06202A),
            Color(0xFF0B1424)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(blurDp.dp)
            .background(Brush.verticalGradient(gradientColors))
    )
}
