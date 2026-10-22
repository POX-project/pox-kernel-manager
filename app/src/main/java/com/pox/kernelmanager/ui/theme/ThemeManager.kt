package com.pox.kernelmanager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import com.pox.kernelmanager.PoxApplication

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class BackgroundMode(val displayName: String) {
    PURE_SURFACE("Pure Surface"),
    BUILTIN_SUNSET("Sunset Built-in"),
    CUSTOM_IMAGE("Custom Wallpaper")
}

// Retained for backward-compatibility with any lingering references
enum class AppTheme(val displayName: String, val description: String) {
    MATERIAL3_EXPRESSIVE(
        displayName = "Material 3 Expressive",
        description = "Pure Material 3 Design with tonal elevation, pill shapes and expressive palettes"
    )
}

object ThemeManager {
    private const val PREFS_NAME = "pox_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_BG_MODE = "background_mode"
    private const val KEY_SCRIM_ALPHA = "wallpaper_scrim_alpha"
    private const val KEY_BLUR_RADIUS = "wallpaper_blur_radius"
    private const val KEY_CUSTOM_URI = "custom_wallpaper_uri"

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var themeModeState by mutableStateOf(loadThemeMode())
        private set

    var dynamicColorState by mutableStateOf(loadDynamicColor())
        private set

    var backgroundModeState by mutableStateOf(loadBackgroundMode())
        private set

    var wallpaperScrimAlphaState by mutableFloatStateOf(loadScrimAlpha())
        private set

    var wallpaperBlurRadiusState by mutableFloatStateOf(loadBlurRadius())
        private set

    var customWallpaperUriState by mutableStateOf(loadCustomUri())
        private set

    // Retained for backward compatibility
    val currentThemeState: AppTheme
        get() = AppTheme.MATERIAL3_EXPRESSIVE

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.DARK.name)
        } catch (_: Exception) {
            ThemeMode.DARK
        }
    }

    private fun loadDynamicColor(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    private fun loadBackgroundMode(): BackgroundMode {
        val name = prefs.getString(KEY_BG_MODE, BackgroundMode.BUILTIN_SUNSET.name)
        return try {
            BackgroundMode.valueOf(name ?: BackgroundMode.BUILTIN_SUNSET.name)
        } catch (_: Exception) {
            BackgroundMode.BUILTIN_SUNSET
        }
    }

    private fun loadScrimAlpha(): Float {
        return prefs.getFloat(KEY_SCRIM_ALPHA, 0.65f)
    }

    private fun loadBlurRadius(): Float {
        return prefs.getFloat(KEY_BLUR_RADIUS, 10.0f)
    }

    private fun loadCustomUri(): String? {
        return prefs.getString(KEY_CUSTOM_URI, null)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        themeModeState = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        dynamicColorState = enabled
    }

    fun setBackgroundMode(mode: BackgroundMode) {
        prefs.edit().putString(KEY_BG_MODE, mode.name).apply()
        backgroundModeState = mode
    }

    fun setWallpaperScrimAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.10f, 0.95f)
        prefs.edit().putFloat(KEY_SCRIM_ALPHA, clamped).apply()
        wallpaperScrimAlphaState = clamped
    }

    fun setWallpaperBlurRadius(radius: Float) {
        val clamped = radius.coerceIn(0f, 35f)
        prefs.edit().putFloat(KEY_BLUR_RADIUS, clamped).apply()
        wallpaperBlurRadiusState = clamped
    }

    fun setCustomWallpaperUri(uri: String?) {
        prefs.edit().putString(KEY_CUSTOM_URI, uri).apply()
        customWallpaperUriState = uri
    }

    fun resetBackgroundToDefaults() {
        setWallpaperScrimAlpha(0.65f)
        setWallpaperBlurRadius(10.0f)
    }

    // Retained for backward-compat
    fun setTheme(theme: AppTheme) {
        // Material 3 is strictly preserved
    }
}
