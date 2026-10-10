// Added by skofqq in 2026. Part of a modified version of j-hc/zygisk-detach-app (Apache-2.0).
package com.jhc.detach.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jhc.detach.R

enum class ThemeMode(@StringRes val label: Int, val nightMode: Int) {
    SYSTEM(R.string.theme_system, UiModeManager.MODE_NIGHT_AUTO),
    LIGHT(R.string.theme_light, UiModeManager.MODE_NIGHT_NO),
    DARK(R.string.theme_dark, UiModeManager.MODE_NIGHT_YES),
}

/** Appearance settings, persisted in SharedPreferences and observed by Compose. */
object AppTheme {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private lateinit var prefs: SharedPreferences
    private lateinit var uiModeManager: UiModeManager

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var dynamicColor by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        uiModeManager = context.getSystemService(UiModeManager::class.java)
        mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) }
            ?: ThemeMode.SYSTEM
        dynamicColor = prefs.getBoolean("dynamic_color", true)
    }

    fun updateMode(value: ThemeMode) {
        mode = value
        prefs.edit().putString("theme", value.name).apply()
        // Android 12+ also applies it to the splash screen and system UI; this recreates the activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            uiModeManager.setApplicationNightMode(value.nightMode)
        }
    }

    fun updateDynamicColor(value: Boolean) {
        dynamicColor = value
        prefs.edit().putBoolean("dynamic_color", value).apply()
    }

    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
