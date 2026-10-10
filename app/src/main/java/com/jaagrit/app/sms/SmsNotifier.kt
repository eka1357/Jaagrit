package com.jaagrit.app.sms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jaagrit.app.data.SettingsStore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

import android.content.ClipData
import android.content.ClipboardManager
import android.telephony.TelephonyManager
import com.jaagrit.app.engine.Level

/**
 * High-level availability states for Emergency SMS (LAD-4, D6, Audit M6.1).
 * Checklist states: "Emergency SMS: ready / no SIM / no permission / airplane mode"
 */
enum class SmsAvailability(val reason: String) {
    READY("ready"),
    NO_SIM("no SIM"),
    NO_PERMISSION("no permission"),
    AIRPLANE_MODE("airplane mode"),
    NO_CONTACT("emergency contact not set")
}

/**
 * Result of attempting to send an L5 emergency SMS (LAD-4, D6).
 */
sealed interface SmsResult {
    data class Success(val maskedNumber: String, val message: String) : SmsResult
    data class Failure(val reason: String) : SmsResult
}

/**
 * Handles L5 emergency SMS dispatching to driver's designated emergency contact.
 * Requirements: LAD-4, D6, PHRASES.md Section 3.
 *
 * Offline guarantee: Uses Android SmsManager and cellular radio only. Zero network/cloud dependencies.
 * Never hardcodes phone numbers; logs always mask destination number.
 */
class SmsNotifier(
    private val context: Context,
    private val settingsStore: SettingsStore = SettingsStore(context)
) {
    private val tag = "JAAGRIT"

    /**
     * Checks current readiness of cellular SMS subsystem using TelephonyManager,
     * permissions, airplane mode settings, and configured emergency contact.
     */
    fun checkAvailability(emergencyContact: String? = null): SmsAvailability {
        val isAirplaneMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simState = telephonyManager?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        val contact = emergencyContact ?: try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                settingsStore.getEmergencyContacts().joinToString(",")
            }
        } catch (_: Exception) {
            ""
        }

        return evaluateSmsAvailability(
            isAirplaneMode = isAirplaneMode,
            hasSmsPermission = hasSmsPermission,
            simState = simState,
            emergencyContact = contact
        )
    }

    /**
     * Formats the prepared emergency message body per docs/PHRASES.md Section 3.
     */
    suspend fun prepareEmergencyMessage(totalAlerts: Int): String {
        val driverName = try {
            settingsStore.getDriverName()
        } catch (_: Exception) {
            SettingsStore.DEFAULT_DRIVER_NAME
        }
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val locationStr = getLastKnownLocationString(context)
        return formatSmsMessage(
            driverName = driverName,
            timestamp = timeStr,
            alertCount = totalAlerts,
            location = locationStr
        )
    }

    /**
     * Copies the emergency alert message to the system clipboard when SMS delivery is unavailable.
     */
    fun copyMessageToClipboard(message: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Jaagrit Emergency Alert", message)
            clipboard?.setPrimaryClip(clip)
            Log.i(tag, "SmsNotifier: Emergency message copied to clipboard")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy emergency message to clipboard", e)
            false
        }
    }

    /**
     * Dispatch emergency SMS to every configured emergency contact (up to 3) with last known location.
     *
     * Intermediate status: Calls [onStatusUpdate] with "Sending SMS..." before each transmission.
     * Success: Returns [SmsResult.Success] if at least one recipient's sent PendingIntent reported RESULT_OK.
     * [SmsResult.Success.maskedNumber] lists only the confirmed recipients (plus "k of n" on partial delivery).
     * Failure: Returns [SmsResult.Failure] only if no recipient was confirmed.
     */
    suspend fun sendEmergencyAlert(
        totalAlerts: Int,
        onStatusUpdate: ((String) -> Unit)? = null
    ): SmsResult {
        val availability = checkAvailability()
        if (availability != SmsAvailability.READY) {
            val reason = when (availability) {
                SmsAvailability.AIRPLANE_MODE -> "Airplane mode active (no mobile signal)"
                SmsAvailability.NO_PERMISSION -> "SEND_SMS permission not granted"
                SmsAvailability.NO_SIM -> "No SIM card detected"
                SmsAvailability.NO_CONTACT -> "Emergency contact not configured in Settings"
                SmsAvailability.READY -> "Unknown error"
            }
            Log.w(tag, "SmsNotifier: Emergency SMS blocked ($reason)")
            return SmsResult.Failure(reason)
        }

        // Re-validate stored numbers so a malformed entry can't abort delivery to the others
        val stored = SettingsStore.compactContacts(settingsStore.getEmergencyContacts())
        val recipients = stored.filter { SettingsStore.isValidPhoneNumber(it) }
        stored.filterNot { SettingsStore.isValidPhoneNumber(it) }.forEach {
            Log.w(tag, "SmsNotifier: Skipping invalid stored contact ${SettingsStore.maskPhoneNumber(it)}")
        }
        if (recipients.isEmpty()) {
            return SmsResult.Failure("No valid emergency contact number configured")
        }

        val messageBody = prepareEmergencyMessage(totalAlerts)
        val results = recipients.mapIndexed { index, number ->
            val masked = SettingsStore.maskPhoneNumber(number)
            val sendingStatus = if (recipients.size > 1) {
                "Sending SMS to $masked (${index + 1}/${recipients.size})..."
            } else {
                "Sending SMS to $masked..."
            }
            Log.i(tag, "SmsNotifier: $sendingStatus")
            onStatusUpdate?.invoke(sendingStatus)
            sendToRecipient(number, messageBody, index)
        }
        return summarizeResults(results)
    }

    /**
     * Sends [messageBody] to one recipient and suspends until its sent PendingIntent reports.
     * [recipientIndex] keeps broadcast actions and request codes unique per recipient.
     */
    private suspend fun sendToRecipient(
        emergencyContact: String,
        messageBody: String,
        recipientIndex: Int
    ): SmsResult {
        val maskedNumber = SettingsStore.maskPhoneNumber(emergencyContact)

        // 5. Send via SmsManager and await sent PendingIntent RESULT_OK
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()

            val parts = smsManager.divideMessage(messageBody)
            val action = "${context.packageName}.SMS_SENT_${System.currentTimeMillis()}_$recipientIndex"
            val requestCodeBase = recipientIndex * REQUEST_CODE_STRIDE

            val timeoutMs = 20_000L
            val transmissionResult = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<SmsResult> { continuation ->
                    val completedParts = AtomicInteger(0)
                    val totalParts = parts.size
                    val hasCompleted = AtomicBoolean(false)

                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(recvContext: Context?, recvIntent: Intent?) {
                            val code = resultCode
                            Log.d(tag, "SmsNotifier: Sent PendingIntent reported resultCode=$code")

                            if (code == Activity.RESULT_OK) {
                                if (completedParts.incrementAndGet() >= totalParts) {
                                    if (hasCompleted.compareAndSet(false, true)) {
                                        try {
                                            context.unregisterReceiver(this)
                                        } catch (_: Exception) {}
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                SmsResult.Success(
                                                    maskedNumber = maskedNumber,
                                                    message = "SMS sent to $maskedNumber"
                                                )
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (hasCompleted.compareAndSet(false, true)) {
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (_: Exception) {}
                                    val errDesc = getSmsResultCodeDescription(code)
                                    Log.w(tag, "SmsNotifier: Sent PendingIntent error: $errDesc (code=$code)")
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            SmsResult.Failure("Transmission failed ($errDesc)")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(action),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )

                    continuation.invokeOnCancellation {
                        try {
                            context.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                    }

                    try {
                        if (parts.size <= 1) {
                            val sentIntent = PendingIntent.getBroadcast(
                                context,
                                requestCodeBase,
                                Intent(action).setPackage(context.packageName),
                                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                            )
                            smsManager.sendTextMessage(emergencyContact, null, messageBody, sentIntent, null)
                        } else {
                            val sentIntents = ArrayList<PendingIntent>(parts.size)
                            for (i in parts.indices) {
                                val intent = Intent(action).apply {
                                    setPackage(context.packageName)
                                    putExtra("part_index", i)
                                }
                                sentIntents.add(
                                    PendingIntent.getBroadcast(
                                        context,
                                        requestCodeBase + i,
                                        intent,
                                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                )
                            }
                            smsManager.sendMultipartTextMessage(emergencyContact, null, parts, sentIntents, null)
                        }
                        Log.i(tag, "SmsNotifier: Dispatched SMS to radio for $maskedNumber (parts=${parts.size}), waiting for sent PendingIntent")
                    } catch (e: Exception) {
                        try {
                            context.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                        if (hasCompleted.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(
                                SmsResult.Failure("Cellular dispatch error: ${e.message ?: "Unknown error"}")
                            )
                        }
                    }
                }
            }

            transmissionResult ?: SmsResult.Failure("SMS confirmation timed out (no carrier ACK)")
        } catch (e: Exception) {
            val reason = "Cellular dispatch failed: ${e.message ?: "Unknown error"}"
            Log.e(tag, "SmsNotifier: $reason", e)
            SmsResult.Failure(reason)
        }
    }

    companion object {
        // Spaces PendingIntent request codes per recipient; a long L5 SMS is at most a few parts.
        private const val REQUEST_CODE_STRIDE = 100

        /**
         * Combines per-recipient results. Success if any recipient was confirmed (RESULT_OK);
         * on partial delivery the masked label reads "k of n contacts (masked list)".
         */
        fun summarizeResults(results: List<SmsResult>): SmsResult {
            val successes = results.filterIsInstance<SmsResult.Success>()
            val failures = results.filterIsInstance<SmsResult.Failure>()
            if (successes.isEmpty()) {
                val reason = failures.map { it.reason }.distinct().joinToString("; ")
                    .ifEmpty { "No recipients" }
                return SmsResult.Failure(
                    if (results.size > 1) "all ${results.size} contacts failed ($reason)" else reason
                )
            }
            val maskedList = successes.joinToString(", ") { it.maskedNumber }
            val label = if (failures.isEmpty()) {
                maskedList
            } else {
                "${successes.size} of ${results.size} contacts ($maskedList)"
            }
            return SmsResult.Success(maskedNumber = label, message = "SMS sent to $label")
        }
        /**
         * Translates Android telephony resultCode into human-readable description.
         */
        fun getSmsResultCodeDescription(resultCode: Int): String {
            return when (resultCode) {
                Activity.RESULT_OK -> "OK"
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic carrier failure"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off / Airplane mode"
                else -> "Error code $resultCode"
            }
        }

        /**
         * Formats message per docs/PHRASES.md Section 3:
         * ALERT: {driver_name} may be unresponsive while driving.
         * Last alert: {timestamp}
         * Alerts in this trip: {count}
         * Location: {gps_lat}, {gps_lon}
         * — Sent by Jaagrit (automated safety alert)
         */
        fun formatSmsMessage(
            driverName: String,
            timestamp: String,
            alertCount: Int,
            location: String
        ): String {
            return "ALERT: $driverName may be unresponsive while driving.\n" +
                    "Last alert: $timestamp\n" +
                    "Alerts in this trip: $alertCount\n" +
                    "Location: $location\n" +
                    "— Sent by Jaagrit (automated safety alert)"
        }

        fun getLastKnownLocationString(context: Context): String {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return "unavailable"
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return "unavailable"

            return try {
                val loc: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                if (loc != null) {
                    "%.5f, %.5f".format(Locale.US, loc.latitude, loc.longitude)
                } else {
                    "unavailable"
                }
            } catch (e: Exception) {
                "unavailable"
            }
        }

        /**
         * Pure logic to evaluate SMS availability from system states.
         * Hierarchy:
         * 1. Airplane mode -> AIRPLANE_MODE ("airplane mode")
         * 2. Permission -> NO_PERMISSION ("no permission")
         * 3. SIM card state -> NO_SIM ("no SIM")
         * 4. Emergency contact -> NO_CONTACT ("emergency contact not set")
         * 5. Ready -> READY ("ready")
         */
        fun evaluateSmsAvailability(
            isAirplaneMode: Boolean,
            hasSmsPermission: Boolean,
            simState: Int,
            emergencyContact: String
        ): SmsAvailability {
            return when {
                isAirplaneMode -> SmsAvailability.AIRPLANE_MODE
                !hasSmsPermission -> SmsAvailability.NO_PERMISSION
                simState != TelephonyManager.SIM_STATE_READY -> SmsAvailability.NO_SIM
                emergencyContact.isBlank() -> SmsAvailability.NO_CONTACT
                else -> SmsAvailability.READY
            }
        }

        /**
         * Derives honest L5 status text for the UI based on real SmsResult and availability.
         * Rule 1: Never show "sent" or "dispatched" unless RESULT_OK is confirmed.
         * Rule 3: If SMS is unavailable, no countdown; show "Emergency SMS unavailable (reason)".
         */
        fun formatL5StatusLabel(
            level: Level,
            l5CountdownSeconds: Int?,
            smsNotificationStatus: String?,
            smsAvailability: SmsAvailability
        ): String? {
            if (level != Level.L5 && l5CountdownSeconds == null && smsNotificationStatus == null) {
                return null
            }

            if (smsAvailability != SmsAvailability.READY) {
                return "Emergency SMS unavailable (${smsAvailability.reason})"
            }

            return when {
                smsNotificationStatus != null -> smsNotificationStatus
                l5CountdownSeconds != null -> "Emergency SMS in ${l5CountdownSeconds}s (tap to cancel)"
                level == Level.L5 -> "Sending SMS..."
                else -> null
            }
        }
    }
}
