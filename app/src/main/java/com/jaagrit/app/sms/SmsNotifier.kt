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
     * Dispatch emergency SMS to configured emergency contact with last known location.
     *
     * Intermediate status: Calls [onStatusUpdate] with "Sending SMS..." before transmission.
     * Success status: Returns [SmsResult.Success] ONLY after the sent PendingIntent reports RESULT_OK.
     */
    suspend fun sendEmergencyAlert(
        totalAlerts: Int,
        onStatusUpdate: ((String) -> Unit)? = null
    ): SmsResult {
        val emergencyContact = settingsStore.getEmergencyContact().trim()
        val maskedNumber = SettingsStore.maskPhoneNumber(emergencyContact)

        if (emergencyContact.isEmpty()) {
            val reason = "Emergency contact not configured in Settings"
            Log.w(tag, "SmsNotifier: $reason")
            return SmsResult.Failure(reason)
        }

        // 1. Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val reason = "SEND_SMS permission not granted"
            Log.w(tag, "SmsNotifier: $reason")
            return SmsResult.Failure(reason)
        }

        // 2. Airplane mode check (D6: Show 'not sent' clearly without crashing)
        val isAirplaneMode = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (e: Exception) {
            false
        }
        if (isAirplaneMode) {
            val reason = "Airplane mode active (no mobile signal)"
            Log.w(tag, "SmsNotifier: $reason")
            return SmsResult.Failure(reason)
        }

        // 3. Location extraction
        val locationStr = getLastKnownLocationString(context)

        // 4. Build message from PHRASES.md template
        val driverName = settingsStore.getDriverName()
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val messageBody = formatSmsMessage(
            driverName = driverName,
            timestamp = timeStr,
            alertCount = totalAlerts,
            location = locationStr
        )

        // Pre-transmission intermediate status: show "Sending SMS..." before confirmation
        val sendingStatus = "Sending SMS to $maskedNumber..."
        Log.i(tag, "SmsNotifier: $sendingStatus")
        onStatusUpdate?.invoke(sendingStatus)

        // 5. Send via SmsManager and await sent PendingIntent RESULT_OK
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()

            val parts = smsManager.divideMessage(messageBody)
            val action = "${context.packageName}.SMS_SENT_${System.currentTimeMillis()}"

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
                                0,
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
                                        i,
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
    }
}
