package com.jaagrit.app.sms

import android.telephony.TelephonyManager
import com.jaagrit.app.data.ContactInputError
import com.jaagrit.app.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for multi-contact configuration, validation, compaction,
 * and SMS dispatch status aggregation (OFF-1, LAD-4, D6).
 */
class MultiContactTest {

    @Test
    fun testIsValidPhoneNumber() {
        // Valid international and national numbers
        assertTrue(SettingsStore.isValidPhoneNumber("+919876543210"))
        assertTrue(SettingsStore.isValidPhoneNumber("9876543210"))
        assertTrue(SettingsStore.isValidPhoneNumber("+1 555-234-5678"))
        assertTrue(SettingsStore.isValidPhoneNumber("(022) 2412-3456"))

        // Invalid numbers
        assertFalse(SettingsStore.isValidPhoneNumber(""))
        assertFalse(SettingsStore.isValidPhoneNumber("   "))
        assertFalse(SettingsStore.isValidPhoneNumber("12345")) // Too short (< 7 digits)
        assertFalse(SettingsStore.isValidPhoneNumber("abcdefghij")) // Non-numeric
        assertFalse(SettingsStore.isValidPhoneNumber("123456789012345678")) // Too long (> 15 digits)
    }

    @Test
    fun testCompactContacts() {
        val input = listOf("+919876543210", "", "  ", "+919876543211", "  +919876543212  ")
        val compacted = SettingsStore.compactContacts(input)

        assertEquals(3, compacted.size)
        assertEquals("+919876543210", compacted[0])
        assertEquals("+919876543211", compacted[1])
        assertEquals("+919876543212", compacted[2])
    }

    @Test
    fun testCompactContacts_limitsToMax() {
        val input = listOf("1111111111", "2222222222", "3333333333", "4444444444")
        val compacted = SettingsStore.compactContacts(input)
        assertEquals(SettingsStore.MAX_EMERGENCY_CONTACTS, compacted.size)
        assertEquals("1111111111", compacted[0])
        assertEquals("2222222222", compacted[1])
        assertEquals("3333333333", compacted[2])
    }

    @Test
    fun testValidateContacts_detectsErrors() {
        // Primary valid, second invalid, third duplicate of first
        val input = listOf("+91 9876543210", "bad_number", "+919876543210")
        val errors = SettingsStore.validateContacts(input)

        assertEquals(3, errors.size)
        assertNull(errors[0]) // valid
        assertEquals(ContactInputError.INVALID, errors[1]) // invalid format
        assertEquals(ContactInputError.DUPLICATE, errors[2]) // duplicate of first
    }

    @Test
    fun testValidateContacts_emptySlotsIgnored() {
        val input = listOf("+919876543210", "", "")
        val errors = SettingsStore.validateContacts(input)

        assertEquals(3, errors.size)
        assertNull(errors[0])
        assertNull(errors[1])
        assertNull(errors[2])
    }

    @Test
    fun testMaskPhoneNumber() {
        assertEquals("******10", SettingsStore.maskPhoneNumber("+919876543210"))
        assertEquals("***65", SettingsStore.maskPhoneNumber("98765"))
        assertEquals("**", SettingsStore.maskPhoneNumber("99"))
        assertEquals("Not configured", SettingsStore.maskPhoneNumber(""))
    }

    @Test
    fun testSummarizeResults_allSuccess() {
        val results = listOf(
            SmsResult.Success(maskedNumber = "***10", message = "SMS sent to ***10"),
            SmsResult.Success(maskedNumber = "***11", message = "SMS sent to ***11")
        )
        val summary = SmsNotifier.summarizeResults(results)

        assertTrue(summary is SmsResult.Success)
        val success = summary as SmsResult.Success
        assertEquals("***10, ***11", success.maskedNumber)
        assertEquals("SMS sent to ***10, ***11", success.message)
    }

    @Test
    fun testSummarizeResults_mixedPartialDelivery() {
        val results = listOf(
            SmsResult.Success(maskedNumber = "***10", message = "SMS sent to ***10"),
            SmsResult.Failure(reason = "Carrier failure")
        )
        val summary = SmsNotifier.summarizeResults(results)

        // Status honesty: When at least one recipient receives the SMS, reports partial success
        assertTrue(summary is SmsResult.Success)
        val success = summary as SmsResult.Success
        assertTrue(success.maskedNumber.contains("1 of 2 contacts"))
    }

    @Test
    fun testSummarizeResults_allFailed() {
        val results = listOf(
            SmsResult.Failure(reason = "Carrier failure"),
            SmsResult.Failure(reason = "Radio off")
        )
        val summary = SmsNotifier.summarizeResults(results)

        assertTrue(summary is SmsResult.Failure)
        val failure = summary as SmsResult.Failure
        assertTrue(failure.reason.contains("all 2 contacts failed"))
    }

    @Test
    fun testSummarizeResults_emptyList() {
        val summary = SmsNotifier.summarizeResults(emptyList())
        assertTrue(summary is SmsResult.Failure)
        assertEquals("No recipients", (summary as SmsResult.Failure).reason)
    }

    @Test
    fun testEvaluateAvailability_withMultipleContacts() {
        val ready = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_READY,
            emergencyContact = "+919876543210,+919876543211"
        )
        assertEquals(SmsAvailability.READY, ready)

        val noContact = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_READY,
            emergencyContact = ""
        )
        assertEquals(SmsAvailability.NO_CONTACT, noContact)
    }
}
