package com.jaagrit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import com.jaagrit.app.engine.Baseline
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val baselineCorruptionHandler = ReplaceFileCorruptionHandler {
    emptyPreferences()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jaagrit_baseline",
    corruptionHandler = baselineCorruptionHandler
)

/**
 * Persists and retrieves driver's calibrated [Baseline] via Jetpack DataStore (CAL-4).
 * Single source of truth for personal eye/fatigue baselines.
 */
class BaselineStore(private val context: Context) {

    val baselineFlow: Flow<Baseline?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val schemaVersion = prefs[KEY_SCHEMA_VERSION] ?: 0
            if (schemaVersion != BASELINE_SCHEMA_VERSION) {
                // Incompatible schema version (e.g., legacy normalized v1 baselines vs pixel-space v2)
                return@map null
            }
            val calibratedAt = prefs[KEY_CALIBRATED_AT_MS] ?: return@map null
            val openEar = prefs[KEY_OPEN_EAR] ?: return@map null
            val closedEar = prefs[KEY_CLOSED_EAR] ?: return@map null
            val threshold = prefs[KEY_THRESHOLD] ?: return@map null
            val mar = prefs[KEY_MAR] ?: 0f
            val blinkRate = prefs[KEY_BLINK_RATE] ?: 16.0f
            val latency = prefs[KEY_RESPONSE_LATENCY_MS] ?: 1500L
            val isValid = prefs[KEY_IS_VALID] ?: true

            Baseline(
                openEar = openEar,
                closedEar = closedEar,
                threshold = threshold,
                mar = mar,
                blinkRate = blinkRate,
                responseLatencyMs = latency,
                calibratedAtMs = calibratedAt,
                isValid = isValid
            )
        }

    suspend fun getBaseline(): Baseline? {
        return baselineFlow.first()
    }

    /**
     * Checks whether a saved baseline exists in DataStore with the current schema version and isValid == true.
     */
    suspend fun hasSavedBaselineWithCurrentSchema(): Boolean {
        val baseline = getBaseline()
        return baseline != null && baseline.isValid
    }

    suspend fun saveBaseline(baseline: Baseline) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCHEMA_VERSION] = BASELINE_SCHEMA_VERSION
            prefs[KEY_OPEN_EAR] = baseline.openEar
            prefs[KEY_CLOSED_EAR] = baseline.closedEar
            prefs[KEY_THRESHOLD] = baseline.threshold
            prefs[KEY_MAR] = baseline.mar
            prefs[KEY_BLINK_RATE] = baseline.blinkRate
            prefs[KEY_RESPONSE_LATENCY_MS] = baseline.responseLatencyMs
            prefs[KEY_CALIBRATED_AT_MS] = baseline.calibratedAtMs
            prefs[KEY_IS_VALID] = baseline.isValid
        }
    }

    suspend fun clearBaseline() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SCHEMA_VERSION)
            prefs.remove(KEY_OPEN_EAR)
            prefs.remove(KEY_CLOSED_EAR)
            prefs.remove(KEY_THRESHOLD)
            prefs.remove(KEY_MAR)
            prefs.remove(KEY_BLINK_RATE)
            prefs.remove(KEY_RESPONSE_LATENCY_MS)
            prefs.remove(KEY_CALIBRATED_AT_MS)
            prefs.remove(KEY_IS_VALID)
        }
    }

    companion object {
        const val BASELINE_SCHEMA_VERSION = 2
        val KEY_SCHEMA_VERSION = intPreferencesKey("baseline_schema_version")
        val KEY_OPEN_EAR = floatPreferencesKey("baseline_open_ear")
        val KEY_CLOSED_EAR = floatPreferencesKey("baseline_closed_ear")
        val KEY_THRESHOLD = floatPreferencesKey("baseline_threshold")
        val KEY_MAR = floatPreferencesKey("baseline_mar")
        val KEY_BLINK_RATE = floatPreferencesKey("baseline_blink_rate")
        val KEY_RESPONSE_LATENCY_MS = longPreferencesKey("baseline_response_latency_ms")
        val KEY_CALIBRATED_AT_MS = longPreferencesKey("baseline_calibrated_at_ms")
        val KEY_IS_VALID = booleanPreferencesKey("baseline_is_valid")
    }
}
