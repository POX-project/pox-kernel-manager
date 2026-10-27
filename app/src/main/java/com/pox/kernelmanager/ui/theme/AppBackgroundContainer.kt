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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pox.kernelmanager.R

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
                        // Fall back to built-in sunset wallpaper
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
 * Bundled "Sunset" backdrop using the official Pox sunset wallpaper asset.
 */
@Composable
private fun BuiltInSunsetBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.bg_pox_default),
        contentDescription = "Built-in Sunset Background",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
