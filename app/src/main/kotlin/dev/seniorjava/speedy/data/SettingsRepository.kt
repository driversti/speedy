package dev.seniorjava.speedy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.seniorjava.speedy.domain.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted user preferences.
 *
 * Reads return sensible defaults on first run (service disabled, display mode BOTH).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val isEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.IS_ENABLED] ?: false }

    val displayMode: Flow<DisplayMode> = dataStore.data.map { prefs ->
        prefs[Keys.DISPLAY_MODE]
            ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
            ?: DisplayMode.BOTH
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun setDisplayMode(mode: DisplayMode) {
        dataStore.edit { it[Keys.DISPLAY_MODE] = mode.name }
    }

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
    }
}
