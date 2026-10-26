package com.pox.kernelmanager.ui.theme

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
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
    val bgMode = ThemeManager.backgroundModeState
    val scrimAlpha = ThemeManager.wallpaperScrimAlphaState
    val blurRadius = ThemeManager.wallpaperBlurRadiusState
    val customUri = ThemeManager.customWallpaperUriState
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (bgMode == BackgroundMode.PURE_SURFACE) {
            content()
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val blurModifier = if (blurRadius > 0.5f) {
                    Modifier
                        .fillMaxSize()
                        .blur(blurRadius.dp)
                } else {
                    Modifier.fillMaxSize()
                }

                if (bgMode == BackgroundMode.BUILTIN_SUNSET) {
                    BuiltInSunsetBackground(modifier = blurModifier)
                } else if (bgMode == BackgroundMode.CUSTOM_IMAGE) {
                    val bitmap = remember(customUri) {
                        if (customUri != null) {
                            runCatching {
                                context.contentResolver.openInputStream(Uri.parse(customUri))?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                            }.getOrNull()
                        } else null
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Custom Background Wallpaper",
                            contentScale = ContentScale.Crop,
                            modifier = blurModifier
                        )
                    } else {
                        // No usable custom image: fall back to the built-in
                        // sunset instead of an empty (solid scrim) backdrop.
                        BuiltInSunsetBackground(modifier = blurModifier)
                    }
                }

                // Scrim overlay layer: tints with MaterialTheme background color to guarantee optimal text contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha.coerceIn(0.05f, 0.98f)))
                )

                content()
            }
        }
    }
}

/**
 * Bundled "Sunset" backdrop, drawn as a gradient instead of a bitmap.
 *
 * The previous implementation pointed at `R.drawable.bg_pox_default`, an asset
 * that was never committed, so the module did not compile. A generated brush
 * keeps the APK small, scales to any aspect ratio without cropping artefacts
 * and can never go missing from `res/drawable` again.
 */
@Composable
private fun BuiltInSunsetBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF120C24), // night sky
                    Color(0xFF3B1B4D),
                    Color(0xFF7E2F4B),
                    Color(0xFFC4553A),
                    Color(0xFFF0A65C)  // horizon glow
                )
            )
        )
    )
}
