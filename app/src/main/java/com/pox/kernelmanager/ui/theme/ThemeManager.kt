package com.pox.kernelmanager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.pox.kernelmanager.PoxApplication

enum class AppTheme(val displayName: String, val description: String) {
    APPLE_LIQUID_GLASS(
        displayName = "Apple Liquid Glass",
        description = "Frosted translucent glassmorphism with delicate blur and iOS aesthetics"
    ),
    MATERIAL3_EXPRESSIVE(
        displayName = "Material 3 Expressive",
        description = "Google Pixel M3 dynamic tonal elevation, pill shapes and expressive palettes"
    ),
    MIUI(
        displayName = "MIUI Classic",
        description = "Vibrant MIUI card styling, energetic accents and elevated contrast"
    ),
    HYPEROS(
        displayName = "HyperOS Sleek",
        description = "Dark futuristic minimalism, neon cyan highlights and crisp outlines"
    )
}

enum class BackgroundType(val displayName: String) {
    THEME_DEFAULT("Theme Default"),
    SOLID_COLOR("Solid Color"),
    CUSTOM_IMAGE("Custom Image")
}

data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.THEME_DEFAULT,
    val colorHex: Long = 0xFF0F172A,
    val imageUri: String? = null,
    val opacity: Float = 0.85f,
    val blurRadiusDp: Float = 16f
)

object ThemeManager {
    private const val PREFS_NAME = "pox_theme_prefs"
    private const val KEY_THEME = "app_theme"
    private const val KEY_BG_TYPE = "bg_type"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_BG_IMAGE_URI = "bg_image_uri"
    private const val KEY_BG_OPACITY = "bg_opacity"
    private const val KEY_BG_BLUR = "bg_blur"

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var currentThemeState by mutableStateOf(loadTheme())
        private set

    var backgroundConfigState by mutableStateOf(loadBackgroundConfig())
        private set

    private fun loadTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.APPLE_LIQUID_GLASS.name)
        return try {
            AppTheme.valueOf(name ?: AppTheme.APPLE_LIQUID_GLASS.name)
        } catch (e: Exception) {
            AppTheme.APPLE_LIQUID_GLASS
        }
    }

    private fun loadBackgroundConfig(): BackgroundConfig {
        val typeName = prefs.getString(KEY_BG_TYPE, BackgroundType.THEME_DEFAULT.name)
        val type = try {
            BackgroundType.valueOf(typeName ?: BackgroundType.THEME_DEFAULT.name)
        } catch (e: Exception) {
            BackgroundType.THEME_DEFAULT
        }
        val colorHex = prefs.getLong(KEY_BG_COLOR, 0xFF0B132B)
        val imageUri = prefs.getString(KEY_BG_IMAGE_URI, null)
        val opacity = prefs.getFloat(KEY_BG_OPACITY, 0.88f)
        val blur = prefs.getFloat(KEY_BG_BLUR, 16f)

        return BackgroundConfig(
            type = type,
            colorHex = colorHex,
            imageUri = imageUri,
            opacity = opacity,
            blurRadiusDp = blur
        )
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        currentThemeState = theme
    }

    fun setBackgroundType(type: BackgroundType) {
        prefs.edit().putString(KEY_BG_TYPE, type.name).apply()
        backgroundConfigState = backgroundConfigState.copy(type = type)
    }

    fun setBackgroundColor(colorHex: Long) {
        prefs.edit().putLong(KEY_BG_COLOR, colorHex).apply()
        backgroundConfigState = backgroundConfigState.copy(colorHex = colorHex)
    }

    fun setBackgroundImageUri(uri: String?) {
        prefs.edit().putString(KEY_BG_IMAGE_URI, uri).apply()
        backgroundConfigState = backgroundConfigState.copy(imageUri = uri)
    }

    fun setBackgroundOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.1f, 1.0f)
        prefs.edit().putFloat(KEY_BG_OPACITY, clamped).apply()
        backgroundConfigState = backgroundConfigState.copy(opacity = clamped)
    }

    fun setBackgroundBlur(blurDp: Float) {
        val clamped = blurDp.coerceIn(0f, 50f)
        prefs.edit().putFloat(KEY_BG_BLUR, clamped).apply()
        backgroundConfigState = backgroundConfigState.copy(blurRadiusDp = clamped)
    }
}
