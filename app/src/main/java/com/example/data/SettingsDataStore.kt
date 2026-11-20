package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "corepulse_settings")

data class AnalyzerSettings(
    val samplingIntervalMs: Long = 1000L,
    val targetFps: Int = 60,
    val useFahrenheit: Boolean = false,
    val enableLiveNotification: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK_PRO
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SAMPLING_INTERVAL_MS = intPreferencesKey("sampling_interval_ms")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val ENABLE_LIVE_NOTIF = booleanPreferencesKey("enable_live_notif")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<AnalyzerSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK_PRO.name
        val theme = runCatching { AppThemeMode.valueOf(themeStr) }.getOrDefault(AppThemeMode.DARK_PRO)
        AnalyzerSettings(
            samplingIntervalMs = (prefs[Keys.SAMPLING_INTERVAL_MS] ?: 1000).toLong(),
            targetFps = prefs[Keys.TARGET_FPS] ?: 60,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            enableLiveNotification = prefs[Keys.ENABLE_LIVE_NOTIF] ?: true,
            themeMode = theme
        )
    }

    suspend fun setSamplingIntervalMs(intervalMs: Int) {
        context.dataStore.edit { it[Keys.SAMPLING_INTERVAL_MS] = intervalMs }
    }

    suspend fun setTargetFps(fps: Int) {
        context.dataStore.edit { it[Keys.TARGET_FPS] = fps }
    }

    suspend fun setUseFahrenheit(useFahrenheit: Boolean) {
        context.dataStore.edit { it[Keys.USE_FAHRENHEIT] = useFahrenheit }
    }

    suspend fun setEnableLiveNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_LIVE_NOTIF] = enabled }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
