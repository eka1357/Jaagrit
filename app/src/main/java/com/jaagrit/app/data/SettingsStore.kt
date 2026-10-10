package com.jaagrit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val settingsCorruptionHandler = ReplaceFileCorruptionHandler {
    emptyPreferences()
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jaagrit_settings",
    corruptionHandler = settingsCorruptionHandler
)

/** Validation errors for an emergency contact input field. */
enum class ContactInputError { INVALID, DUPLICATE }

/**
 * Driver profile & emergency contact preferences persisted locally in Android DataStore.
 * Requirements: LAD-4, D6, D9. Stored only on phone, never transmitted.
 */
class SettingsStore(private val context: Context) {

    private val safePrefsFlow: Flow<Preferences> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val driverNameFlow: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[KEY_DRIVER_NAME] ?: DEFAULT_DRIVER_NAME
    }

    val emergencyContactFlow: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[KEY_EMERGENCY_CONTACT] ?: ""
    }

    /** All configured emergency contacts (1..MAX_EMERGENCY_CONTACTS), primary first, blanks removed. */
    val emergencyContactsFlow: Flow<List<String>> = safePrefsFlow.map { prefs ->
        CONTACT_KEYS.mapNotNull { key -> prefs[key]?.trim()?.takeIf { it.isNotEmpty() } }
    }

    val demoTimersFlow: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[KEY_DEMO_TIMERS] ?: false
    }

    val quickCalibrationFlow: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[KEY_QUICK_CALIBRATION] ?: false
    }

    val companionEnabledFlow: Flow<Boolean> = safePrefsFlow.map { prefs ->
        prefs[KEY_COMPANION_ENABLED] ?: false
    }

    val appLanguageFlow: Flow<String> = safePrefsFlow.map { prefs ->
        prefs[KEY_APP_LANGUAGE] ?: DEFAULT_LANGUAGE
    }

    suspend fun getDriverName(): String = driverNameFlow.first()

    suspend fun getEmergencyContact(): String = emergencyContactFlow.first()

    suspend fun getEmergencyContacts(): List<String> = emergencyContactsFlow.first()

    suspend fun isDemoTimersEnabled(): Boolean = demoTimersFlow.first()

    suspend fun isQuickCalibrationEnabled(): Boolean = quickCalibrationFlow.first()

    suspend fun isCompanionEnabled(): Boolean = companionEnabledFlow.first()

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

    /**
     * Saves up to [MAX_EMERGENCY_CONTACTS] contacts. Input is compacted (blanks and
     * duplicates removed) so slot 1 is always the primary contact. Caller validates first.
     */
    suspend fun saveEmergencyContacts(phones: List<String>) {
        val compacted = compactContacts(phones)
        context.settingsDataStore.edit { prefs ->
            CONTACT_KEYS.forEachIndexed { i, key ->
                val value = compacted.getOrNull(i)
                if (value != null) prefs[key] = value else prefs.remove(key)
            }
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

    suspend fun setCompanionEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_COMPANION_ENABLED] = enabled
        }
    }

    companion object {
        const val DEFAULT_DRIVER_NAME = "Driver"
        const val DEFAULT_LANGUAGE = "hi"
        const val LANG_HINDI = "hi"
        const val LANG_ENGLISH = "en"

        val KEY_DRIVER_NAME = stringPreferencesKey("driver_name")
        val KEY_EMERGENCY_CONTACT = stringPreferencesKey("emergency_contact")
        val KEY_EMERGENCY_CONTACT_2 = stringPreferencesKey("emergency_contact_2")
        val KEY_EMERGENCY_CONTACT_3 = stringPreferencesKey("emergency_contact_3")
        private val CONTACT_KEYS = listOf(KEY_EMERGENCY_CONTACT, KEY_EMERGENCY_CONTACT_2, KEY_EMERGENCY_CONTACT_3)

        /** Max contacts that receive the L5 SMS (product decision: 2-3 trusted people). */
        const val MAX_EMERGENCY_CONTACTS = 3
        // E.164 allows at most 15 digits; 7 is the shortest realistic subscriber number.
        private const val MIN_PHONE_DIGITS = 7
        private const val MAX_PHONE_DIGITS = 15
        private val PHONE_ALLOWED_CHARS = Regex("^\\+?[0-9 ()\\-]+$")

        /** Digits-only form, used to detect the same number typed with different spacing. */
        fun phoneDigits(phone: String): String = phone.filter { it.isDigit() }

        /** True if [phone] looks like a dialable number: optional leading +, 7-15 digits, spaces/dashes/brackets allowed. */
        fun isValidPhoneNumber(phone: String): Boolean {
            val trimmed = phone.trim()
            if (!PHONE_ALLOWED_CHARS.matches(trimmed)) return false
            return phoneDigits(trimmed).length in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS
        }

        /** Trims, drops blanks and duplicate numbers, and caps at [MAX_EMERGENCY_CONTACTS]. */
        fun compactContacts(phones: List<String>): List<String> =
            phones.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { phoneDigits(it) }
                .take(MAX_EMERGENCY_CONTACTS)

        /**
         * Per-field validation for the Settings form. Blank fields are allowed (optional slots).
         * Returns one entry per input: null if OK, otherwise the error.
         */
        fun validateContacts(phones: List<String>): List<ContactInputError?> {
            val seen = mutableSetOf<String>()
            return phones.map { raw ->
                val trimmed = raw.trim()
                when {
                    trimmed.isEmpty() -> null
                    !isValidPhoneNumber(trimmed) -> ContactInputError.INVALID
                    !seen.add(phoneDigits(trimmed)) -> ContactInputError.DUPLICATE
                    else -> null
                }
            }
        }
        val KEY_DEMO_TIMERS = booleanPreferencesKey("demo_timers")
        val KEY_QUICK_CALIBRATION = booleanPreferencesKey("quick_calibration")
        val KEY_COMPANION_ENABLED = booleanPreferencesKey("companion_enabled")
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
