package dev.seniorjava.speedy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted user preferences.
 *
 * Only `isEnabled` is tracked — it drives whether the service should re-launch
 * after boot. Reads return sensible defaults on first run (service disabled).
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val isEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.IS_ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
    }
}
