package com.jaagrit.app.sms

import android.telephony.TelephonyManager
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.engine.FakeClock
import com.jaagrit.app.engine.FatigueEngine
import com.jaagrit.app.engine.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests SMS status honesty, availability evaluation, and the no-SIM path.
 * Requirements:
 * 1. Status honesty: Derive L5 label only from real SmsResult / availability.
 *    Never show "sent" or "dispatched" unless RESULT_OK.
 * 2. SIM state checking: TelephonyManager SIM state evaluated correctly.
 * 3. Unavailable SMS: No fake countdown; returns "Emergency SMS unavailable (reason)".
 */
class SmsStatusHonestyTest {

    // --- 1. SmsAvailability Evaluation Tests ---

    @Test
    fun testEvaluateAvailability_airplaneModeTakesPrecedence() {
        val result = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = true,
            hasSmsPermission = false,
            simState = TelephonyManager.SIM_STATE_ABSENT,
            emergencyContact = ""
        )
        assertEquals(SmsAvailability.AIRPLANE_MODE, result)
        assertEquals("airplane mode", result.reason)
    }

    @Test
    fun testEvaluateAvailability_missingPermissionTakesPrecedenceOverNoSim() {
        val result = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = false,
            simState = TelephonyManager.SIM_STATE_ABSENT,
            emergencyContact = "9876543210"
        )
        assertEquals(SmsAvailability.NO_PERMISSION, result)
        assertEquals("no permission", result.reason)
    }

    @Test
    fun testEvaluateAvailability_noSimWhenPermissionGranted() {
        val resultAbsent = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_ABSENT,
            emergencyContact = "9876543210"
        )
        assertEquals(SmsAvailability.NO_SIM, resultAbsent)
        assertEquals("no SIM", resultAbsent.reason)

        val resultUnknown = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_UNKNOWN,
            emergencyContact = "9876543210"
        )
        assertEquals(SmsAvailability.NO_SIM, resultUnknown)
        assertEquals("no SIM", resultUnknown.reason)
    }

    @Test
    fun testEvaluateAvailability_noContactWhenSimReady() {
        val result = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_READY,
            emergencyContact = ""
        )
        assertEquals(SmsAvailability.NO_CONTACT, result)
        assertEquals("emergency contact not set", result.reason)
    }

    @Test
    fun testEvaluateAvailability_readyWhenAllConditionsMet() {
        val result = SmsNotifier.evaluateSmsAvailability(
            isAirplaneMode = false,
            hasSmsPermission = true,
            simState = TelephonyManager.SIM_STATE_READY,
            emergencyContact = "+919876543210"
        )
        assertEquals(SmsAvailability.READY, result)
        assertEquals("ready", result.reason)
    }

    // --- 2. L5 Status Label Formatting & Honesty Tests ---

    @Test
    fun testFormatL5StatusLabel_noSimReturnsUnavailableWithNoCountdown() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L4,
            l5CountdownSeconds = 15,
            smsNotificationStatus = null,
            smsAvailability = SmsAvailability.NO_SIM
        )
        assertEquals("Emergency SMS unavailable (no SIM)", label)
        assertFalse(label!!.contains("sent", ignoreCase = true))
        assertFalse(label.contains("dispatched", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_airplaneModeReturnsUnavailable() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L5,
            l5CountdownSeconds = null,
            smsNotificationStatus = null,
            smsAvailability = SmsAvailability.AIRPLANE_MODE
        )
        assertEquals("Emergency SMS unavailable (airplane mode)", label)
        assertFalse(label!!.contains("sent", ignoreCase = true))
        assertFalse(label.contains("dispatched", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_noPermissionReturnsUnavailable() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L5,
            l5CountdownSeconds = null,
            smsNotificationStatus = null,
            smsAvailability = SmsAvailability.NO_PERMISSION
        )
        assertEquals("Emergency SMS unavailable (no permission)", label)
        assertFalse(label!!.contains("sent", ignoreCase = true))
        assertFalse(label.contains("dispatched", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_countdownWhenReady() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L4,
            l5CountdownSeconds = 8,
            smsNotificationStatus = null,
            smsAvailability = SmsAvailability.READY
        )
        assertEquals("Emergency SMS in 8s (tap to cancel)", label)
        assertFalse(label!!.contains("sent", ignoreCase = true))
        assertFalse(label.contains("dispatched", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_sendingWhenReady() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L5,
            l5CountdownSeconds = null,
            smsNotificationStatus = "Sending SMS to ***10...",
            smsAvailability = SmsAvailability.READY
        )
        assertEquals("Sending SMS to ***10...", label)
        assertFalse(label!!.contains("dispatched", ignoreCase = true))
        assertFalse(label.contains("SMS sent", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_sentConfirmedOnlyOnResultOk() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L5,
            l5CountdownSeconds = null,
            smsNotificationStatus = "SMS sent to ***10",
            smsAvailability = SmsAvailability.READY
        )
        assertEquals("SMS sent to ***10", label)
        assertTrue(label!!.contains("SMS sent"))
    }

    @Test
    fun testFormatL5StatusLabel_failureShowsNotSentReason() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L5,
            l5CountdownSeconds = null,
            smsNotificationStatus = "SMS not sent: Generic carrier failure",
            smsAvailability = SmsAvailability.READY
        )
        assertEquals("SMS not sent: Generic carrier failure", label)
        assertFalse(label!!.contains("dispatched", ignoreCase = true))
    }

    @Test
    fun testFormatL5StatusLabel_returnsNullWhenNoAlertOrL5() {
        val label = SmsNotifier.formatL5StatusLabel(
            level = Level.L0,
            l5CountdownSeconds = null,
            smsNotificationStatus = null,
            smsAvailability = SmsAvailability.READY
        )
        assertNull(label)
    }

    // --- 3. Engine Output Reason Honesty Test ---

    @Test
    fun testFatigueEngine_l5ReasonDoesNotClaimSmsDispatched() {
        val clock = FakeClock(100_000L)
        val engine = FatigueEngine(
            config = Config(demoTimers = true),
            clock = clock,
            baseline = Baseline.DEFAULT.copy(isValid = true)
        )
        engine.resetDrive(clock.nowMs())

        // Trigger L3 via eye closure
        var output = engine.onFrame(FaceFrame.EMPTY.copy(tsMs = clock.nowMs(), faceFound = true, earL = 0.02f, earR = 0.02f))
        while (output.level != Level.L3) {
            clock.advance(100L)
            output = engine.onFrame(FaceFrame.EMPTY.copy(tsMs = clock.nowMs(), faceFound = true, earL = 0.02f, earR = 0.02f))
        }

        // Advance until L5 fires
        var ticks = 0
        while (output.level != Level.L5 && ticks < 200) {
            clock.advance(100L)
            output = engine.onTick()
            ticks++
        }

        assertEquals(Level.L5, output.level)
        assertNotNull(output.reasons)
        assertTrue(output.reasons.isNotEmpty())

        val l5Reason = output.reasons.first()
        assertEquals("L5: Driver unresponsive", l5Reason)
        // Must NEVER claim "SMS dispatched" in engine domain
        assertFalse("Engine must not assume SMS dispatched", l5Reason.contains("SMS dispatched", ignoreCase = true))
        assertFalse("Engine must not assume SMS sent", l5Reason.contains("sent", ignoreCase = true))
    }
}
