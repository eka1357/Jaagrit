package com.jaagrit.app

import android.app.Activity
import android.telephony.SmsManager
import com.jaagrit.app.sms.SmsNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates privacy, manifest backup rules, and security guarantees.
 * Ensures the emergency contact number and driver telemetry never leak to cloud backup.
 */
class ManifestSecurityTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find file $relativePath in search candidates: $candidates")
    }

    @Test
    fun verifyManifest_disallowsCloudBackup() {
        val manifestFile = findFile("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        // 1. allowBackup must be strictly false
        assertTrue(
            "AndroidManifest.xml must set android:allowBackup=\"false\"",
            content.contains("android:allowBackup=\"false\"")
        )
        assertFalse(
            "AndroidManifest.xml must NOT set android:allowBackup=\"true\"",
            content.contains("android:allowBackup=\"true\"")
        )

        // 2. INTERNET permission must never be granted
        assertFalse(
            "INTERNET permission must not be requested as standard permission",
            content.contains("<uses-permission android:name=\"android.permission.INTERNET\" />")
        )
        assertTrue(
            "INTERNET permission must be stripped with tools:node=\"remove\"",
            content.contains("android.permission.INTERNET\" tools:node=\"remove\"")
        )
    }

    @Test
    fun verifyBackupRules_excludeAllCloudData() {
        val dataExtractionFile = findFile("src/main/res/xml/data_extraction_rules.xml")
        val content = dataExtractionFile.readText()

        assertTrue(
            "data_extraction_rules.xml must exclude root data from cloud-backup",
            content.contains("<exclude domain=\"root\" path=\".\" />")
        )

        val legacyBackupFile = findFile("src/main/res/xml/backup_rules.xml")
        val legacyContent = legacyBackupFile.readText()

        assertTrue(
            "backup_rules.xml must exclude root data from full-backup-content",
            legacyContent.contains("<exclude domain=\"root\" path=\".\" />")
        )
    }

    @Test
    fun verifySmsResultCodeDescriptions() {
        assertEquals("OK", SmsNotifier.getSmsResultCodeDescription(Activity.RESULT_OK))
        assertEquals("Generic carrier failure", SmsNotifier.getSmsResultCodeDescription(SmsManager.RESULT_ERROR_GENERIC_FAILURE))
        assertEquals("Radio off / Airplane mode", SmsNotifier.getSmsResultCodeDescription(SmsManager.RESULT_ERROR_RADIO_OFF))
        assertEquals("Null PDU", SmsNotifier.getSmsResultCodeDescription(SmsManager.RESULT_ERROR_NULL_PDU))
        assertEquals("No cellular service", SmsNotifier.getSmsResultCodeDescription(SmsManager.RESULT_ERROR_NO_SERVICE))
    }
}
