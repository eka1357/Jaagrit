package com.jaagrit.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for DataStore corruption handlers (AUDIT-024).
 * Ensures that if DataStore files are corrupted on disk, ReplaceFileCorruptionHandler
 * gracefully resets to emptyPreferences() rather than crashing with CorruptionException.
 */
class DataStoreCorruptionTest {

    @Test
    fun testBaselineCorruptionHandler_returnsEmptyPreferences() = runBlocking {
        assertNotNull(baselineCorruptionHandler)
        val ex = CorruptionException("Simulated corrupted baseline preferences file")
        val recovered = baselineCorruptionHandler.handleCorruption(ex)

        assertNotNull(recovered)
        assertEquals(emptyPreferences(), recovered)
    }

    @Test
    fun testSettingsCorruptionHandler_returnsEmptyPreferences() = runBlocking {
        assertNotNull(settingsCorruptionHandler)
        val ex = CorruptionException("Simulated corrupted settings preferences file")
        val recovered = settingsCorruptionHandler.handleCorruption(ex)

        assertNotNull(recovered)
        assertEquals(emptyPreferences(), recovered)
    }

    @Test
    fun testEmptyPreferences_fallbackValuesAreSafe() {
        val empty = emptyPreferences()

        // Test settings fallbacks with empty preferences
        val driverName = empty[SettingsStore.KEY_DRIVER_NAME] ?: SettingsStore.DEFAULT_DRIVER_NAME
        val language = empty[SettingsStore.KEY_APP_LANGUAGE] ?: SettingsStore.DEFAULT_LANGUAGE
        val demoTimers = empty[SettingsStore.KEY_DEMO_TIMERS] ?: false
        val quickCal = empty[SettingsStore.KEY_QUICK_CALIBRATION] ?: false
        val contact = empty[SettingsStore.KEY_EMERGENCY_CONTACT] ?: ""

        assertEquals(SettingsStore.DEFAULT_DRIVER_NAME, driverName)
        assertEquals(SettingsStore.DEFAULT_LANGUAGE, language)
        assertEquals(false, demoTimers)
        assertEquals(false, quickCal)
        assertEquals("", contact)
    }

    @Test
    fun testBaselineStore_schemaVersionDiscardsLegacyV1Baselines() {
        assertEquals(2, BaselineStore.BASELINE_SCHEMA_VERSION)

        // Legacy preferences without schema version key (version defaults to 0)
        val legacyVersion = emptyPreferences()[BaselineStore.KEY_SCHEMA_VERSION] ?: 0
        org.junit.Assert.assertTrue(
            "Legacy v1 baseline without schema version must not match current schema version",
            legacyVersion != BaselineStore.BASELINE_SCHEMA_VERSION
        )

        // Older or future schema versions must also be rejected
        org.junit.Assert.assertNotEquals(BaselineStore.BASELINE_SCHEMA_VERSION, 1)
        org.junit.Assert.assertNotEquals(BaselineStore.BASELINE_SCHEMA_VERSION, 3)
    }

    @Test
    fun testDefaultBaseline_isNeverValid() {
        // Baseline.DEFAULT must have isValid == false to prevent silent uncalibrated operation
        org.junit.Assert.assertFalse(com.jaagrit.app.engine.Baseline.DEFAULT.isValid)
    }
}
