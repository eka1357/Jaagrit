package com.jaagrit.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.jaagrit.app.engine.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Device thermal monitoring for Jaagrit (CAM-4, DECISIONS D10, D11).
 *
 * Rules:
 * - D10: Polled every 10 s using BatteryManager battery temperature + PowerManager OnThermalStatusChangedListener.
 * - Strictly zero access to /sys/class/thermal/.
 * - D11: Above 42°C (THERMAL_REDUCE_C) or moderate+ thermal status, enables every-other-frame analysis.
 */
class ThermalMonitor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val pollIntervalMs: Long = 10_000L
) {
    private val tag = "JAAGRIT"

    data class ThermalInfo(
        val temperatureCelsius: Float = 0f,
        val thermalStatus: Int = 0,
        val isThrottling: Boolean = false,
        val isWarning: Boolean = false
    ) {
        val thermalStatusLabel: String
            get() = when (thermalStatus) {
                0 -> "NONE"
                1 -> "LIGHT"
                2 -> "MODERATE"
                3 -> "SEVERE"
                4 -> "CRITICAL"
                5 -> "EMERGENCY"
                6 -> "SHUTDOWN"
                else -> "STATUS_$thermalStatus"
            }
    }

    private val _thermalInfo = MutableStateFlow(ThermalInfo())
    val thermalInfo: StateFlow<ThermalInfo> = _thermalInfo.asStateFlow()

    private val powerManager: PowerManager? = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val thermalListener: PowerManager.OnThermalStatusChangedListener?
    private var pollJob: Job? = null
    private val frameSkipCounter = AtomicInteger(0)
    private val shouldThrottleFlag = AtomicBoolean(false)

    init {
        thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            PowerManager.OnThermalStatusChangedListener { status ->
                Log.i(tag, "PowerManager thermal status changed: $status")
                updateState(powerManagerStatus = status)
            }.also { listener ->
                try {
                    powerManager.addThermalStatusListener(context.mainExecutor, listener)
                } catch (e: Exception) {
                    Log.w(tag, "Failed to register thermal status listener: ${e.message}")
                }
            }
        } else {
            null
        }

        startPolling()
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                readBatteryTemperature()
                delay(pollIntervalMs)
            }
        }
    }

    /**
     * Reads battery temperature via standard BatteryManager broadcast.
     * BatteryManager.EXTRA_TEMPERATURE reports tenths of a degree Celsius (e.g. 350 = 35.0°C).
     */
    fun readBatteryTemperature(): Float {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 0f

        updateState(tempCelsius = tempCelsius)
        return tempCelsius
    }

    private fun updateState(
        tempCelsius: Float = _thermalInfo.value.temperatureCelsius,
        powerManagerStatus: Int? = null
    ) {
        val currentPmStatus = powerManagerStatus ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: 0
        } else {
            0
        }

        val isTempHigh = tempCelsius >= Config.THERMAL_REDUCE_C
        val isPmThrottling = currentPmStatus >= 2 // MODERATE or higher
        val shouldThrottle = isTempHigh || isPmThrottling
        val isWarning = tempCelsius >= Config.THERMAL_WARN_C || currentPmStatus >= 3 // SEVERE

        shouldThrottleFlag.set(shouldThrottle)

        _thermalInfo.value = ThermalInfo(
            temperatureCelsius = tempCelsius,
            thermalStatus = currentPmStatus,
            isThrottling = shouldThrottle,
            isWarning = isWarning
        )

        if (shouldThrottle) {
            Log.w(tag, "Thermal throttling active: temp=${tempCelsius}°C, pmStatus=$currentPmStatus (D11 every-other-frame analysis)")
        }
    }

    /**
     * Decides whether the incoming camera frame should be skipped to halve analysis rate (D11).
     * When throttling is active, skips every second frame.
     */
    fun shouldSkipFrame(): Boolean {
        if (!shouldThrottleFlag.get()) return false
        val count = frameSkipCounter.incrementAndGet()
        return (count % 2 == 1)
    }

    fun release() {
        pollJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener)
            } catch (e: Exception) {
                Log.w(tag, "Failed to remove thermal status listener: ${e.message}")
            }
        }
    }
}
