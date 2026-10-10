package com.jaagrit.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

/**
 * Interface to allow testing and abstracting device volume management (AUDIT-014).
 */
interface StreamVolumeManager {
    fun getVolume(streamType: Int): Int
    fun getMaxVolume(streamType: Int): Int
    fun setVolume(streamType: Int, index: Int, flags: Int)
}

class AndroidStreamVolumeManager(private val context: Context) : StreamVolumeManager {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun getVolume(streamType: Int): Int = audioManager?.getStreamVolume(streamType) ?: 0
    override fun getMaxVolume(streamType: Int): Int = audioManager?.getStreamMaxVolume(streamType) ?: 100
    override fun setVolume(streamType: Int, index: Int, flags: Int) {
        audioManager?.setStreamVolume(streamType, index, flags)
    }
}

/**
 * Programmatic alarm tone generator on USAGE_ALARM at maximum volume (D13, UI-2, AUDIT-014).
 * Strictly synthesizes audio in code — no bundled audio asset files.
 * Generates an unmistakable, repeating two-tone emergency alarm siren pattern.
 */
class AlarmToneGenerator(
    private val context: Context? = null,
    internal var volumeManager: StreamVolumeManager? = context?.let { AndroidStreamVolumeManager(it) },
    audioTrackFactory: (() -> AudioTrack?)? = null
) {

    private val audioTrackFactory: () -> AudioTrack? = audioTrackFactory ?: { createSynthesizedAlarmTrack() }
    private val tag = "JAAGRIT"
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var originalAlarmVolume: Int? = null

    /**
     * Start playing the looping alarm tone at max volume on USAGE_ALARM.
     * Sets device STREAM_ALARM to maximum and preserves previous volume (AUDIT-014).
     */
    @Synchronized
    fun startAlarm() {
        if (isPlaying) return
        try {
            volumeManager?.let { vm ->
                try {
                    val currentVol = vm.getVolume(AudioManager.STREAM_ALARM)
                    val maxVol = vm.getMaxVolume(AudioManager.STREAM_ALARM)
                    originalAlarmVolume = currentVol
                    vm.setVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                    Log.d(tag, "Set STREAM_ALARM to max ($maxVol), saved previous volume ($currentVol)")
                } catch (e: Exception) {
                    Log.w(tag, "Failed to set STREAM_ALARM to max: ${e.message}")
                }
            }

            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                audioTrack = audioTrackFactory()
            }
            isPlaying = true
            audioTrack?.let { track ->
                track.setVolume(1.0f)
                track.play()
                Log.d(tag, "Alarm tone started on USAGE_ALARM")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting alarm tone: ${e.message}", e)
        }
    }

    /**
     * Stop and silence the alarm tone and restore previous STREAM_ALARM volume (AUDIT-014).
     */
    @Synchronized
    fun stopAlarm() {
        if (!isPlaying) return
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.reloadStaticData()
                }
            }
            isPlaying = false
            Log.d(tag, "Alarm tone stopped")
        } catch (e: Exception) {
            Log.e(tag, "Error stopping alarm tone: ${e.message}", e)
        } finally {
            restoreAlarmVolume()
        }
    }

    private fun restoreAlarmVolume() {
        originalAlarmVolume?.let { prevVol ->
            try {
                volumeManager?.setVolume(AudioManager.STREAM_ALARM, prevVol, 0)
                Log.d(tag, "Restored STREAM_ALARM to previous volume ($prevVol)")
            } catch (e: Exception) {
                Log.w(tag, "Failed to restore STREAM_ALARM volume: ${e.message}")
            } finally {
                originalAlarmVolume = null
            }
        }
    }

    /**
     * Release underlying audio hardware resources.
     */
    @Synchronized
    fun release() {
        try {
            stopAlarm()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AlarmToneGenerator: ${e.message}", e)
        }
    }

    /**
     * Synthesizes 1.0 s of dual-tone alternating emergency siren pulses (950 Hz & 1400 Hz)
     * and sets up a looping AudioTrack in static mode.
     */
    private fun createSynthesizedAlarmTrack(): AudioTrack {
        val durationMs = 1000
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcmData = ShortArray(numSamples)

        val freq1 = 950.0   // Low siren pulse (Hz)
        val freq2 = 1400.0  // High siren pulse (Hz)

        // 4 pulses in 1 second (150ms tone, 50ms silence, 150ms tone, 50ms silence, ...)
        val samplesPerMs = sampleRate / 1000.0
        val p1End = (150 * samplesPerMs).toInt()
        val s1End = (200 * samplesPerMs).toInt()
        val p2End = (350 * samplesPerMs).toInt()
        val s2End = (400 * samplesPerMs).toInt()
        val p3End = (550 * samplesPerMs).toInt()
        val s3End = (600 * samplesPerMs).toInt()
        val p4End = (750 * samplesPerMs).toInt()

        for (i in 0 until numSamples) {
            val sample = when {
                i < p1End -> (sin(2.0 * Math.PI * i * freq1 / sampleRate) * Short.MAX_VALUE * 0.9).toInt().toShort()
                i < s1End -> 0
                i < p2End -> (sin(2.0 * Math.PI * i * freq2 / sampleRate) * Short.MAX_VALUE * 0.9).toInt().toShort()
                i < s2End -> 0
                i < p3End -> (sin(2.0 * Math.PI * i * freq1 / sampleRate) * Short.MAX_VALUE * 0.9).toInt().toShort()
                i < s3End -> 0
                i < p4End -> (sin(2.0 * Math.PI * i * freq2 / sampleRate) * Short.MAX_VALUE * 0.9).toInt().toShort()
                else -> 0 // 250ms silence at end of pulse cycle
            }
            pcmData[i] = sample
        }

        val bufferSize = pcmData.size * 2 // 2 bytes per short

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcmData, 0, pcmData.size)
        track.setLoopPoints(0, numSamples, -1) // -1 = loop indefinitely until stopped
        return track
    }
}
