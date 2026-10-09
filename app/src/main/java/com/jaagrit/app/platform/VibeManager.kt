package com.jaagrit.app.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.jaagrit.app.engine.VibePattern

/**
 * Haptic feedback manager executing [VibePattern] vibrations (UI-2, LAD-1).
 * minSdk is 31+ so uses VibratorManager / VibrationEffect.
 */
class VibeManager(private val context: Context) {

    private val tag = "JAAGRIT"
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Trigger vibration pattern.
     */
    fun vibrate(pattern: VibePattern) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            when (pattern) {
                VibePattern.SOFT -> {
                    val effect = VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                }
                VibePattern.WARNING -> {
                    val timings = longArrayOf(0, 150, 100, 150)
                    val effect = VibrationEffect.createWaveform(timings, -1)
                    vib.vibrate(effect)
                }
                VibePattern.URGENT -> {
                    // Looping aggressive pulses (400ms on, 200ms off) until cancelled
                    val timings = longArrayOf(0, 400, 200, 400, 200)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 1) // repeat index 1
                    vib.vibrate(effect)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Vibration failed: ${e.message}", e)
        }
    }

    /**
     * Cancel any active vibration.
     */
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(tag, "Error cancelling vibration: ${e.message}", e)
        }
    }
}
