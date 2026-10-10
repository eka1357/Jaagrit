package com.jaagrit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jaagrit_settings")

/**
 * Driver profile & emergency contact preferences persisted locally in Android DataStore.
 * Requirements: LAD-4, D6, D9. Stored only on phone, never transmitted.
 */
class SettingsStore(private val context: Context) {

    val driverNameFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DRIVER_NAME] ?: DEFAULT_DRIVER_NAME
    }

    val emergencyContactFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_EMERGENCY_CONTACT] ?: ""
    }

    val demoTimersFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_DEMO_TIMERS] ?: false
    }

    val quickCalibrationFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_QUICK_CALIBRATION] ?: false
    }

    val appLanguageFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_APP_LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    suspend fun getDriverName(): String = driverNameFlow.first()

    suspend fun getEmergencyContact(): String = emergencyContactFlow.first()

    suspend fun isDemoTimersEnabled(): Boolean = demoTimersFlow.first()

    suspend fun isQuickCalibrationEnabled(): Boolean = quickCalibrationFlow.first()

    suspend fun getAppLanguage(): String = appLanguageFlow.first()

    suspend fun setAppLanguage(lang: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = if (lang == LANG_ENGLISH) LANG_ENGLISH else LANG_HINDI
        }
    }

    suspend fun saveDriverName(name: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DRIVER_NAME] = name.trim().ifEmpty { DEFAULT_DRIVER_NAME }
        }
    }

    suspend fun saveEmergencyContact(phone: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_EMERGENCY_CONTACT] = phone.trim()
        }
    }

    suspend fun setDemoTimers(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEMO_TIMERS] = enabled
        }
    }

    suspend fun setQuickCalibration(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_QUICK_CALIBRATION] = enabled
        }
    }

    companion object {
        const val DEFAULT_DRIVER_NAME = "Driver"
        const val DEFAULT_LANGUAGE = "hi"
        const val LANG_HINDI = "hi"
        const val LANG_ENGLISH = "en"

        val KEY_DRIVER_NAME = stringPreferencesKey("driver_name")
        val KEY_EMERGENCY_CONTACT = stringPreferencesKey("emergency_contact")
        val KEY_DEMO_TIMERS = booleanPreferencesKey("demo_timers")
        val KEY_QUICK_CALIBRATION = booleanPreferencesKey("quick_calibration")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

        /**
         * Masks phone number showing only the last 2 digits for privacy in logs & UI.
         */
        fun maskPhoneNumber(phone: String): String {
            val trimmed = phone.trim()
            if (trimmed.isEmpty()) return "Not configured"
            if (trimmed.length <= 2) return "**"
            val last2 = trimmed.takeLast(2)
            val maskedPart = "*".repeat((trimmed.length - 2).coerceAtMost(6))
            return "$maskedPart$last2"
        }
    }
}
