package com.jaagrit.app.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

/**
 * Programmatic alarm tone generator on USAGE_ALARM at maximum volume (D13, UI-2).
 * Strictly synthesizes audio in code — no bundled audio asset files.
 * Generates an unmistakable, repeating two-tone emergency alarm siren pattern.
 */
class AlarmToneGenerator {

    private val tag = "JAAGRIT"
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    init {
        try {
            audioTrack = createSynthesizedAlarmTrack()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AlarmToneGenerator: ${e.message}", e)
        }
    }

    /**
     * Start playing the looping alarm tone at max volume on USAGE_ALARM.
     */
    @Synchronized
    fun startAlarm() {
        if (isPlaying) return
        try {
            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                audioTrack = createSynthesizedAlarmTrack()
            }
            audioTrack?.let { track ->
                track.setVolume(1.0f)
                track.play()
                isPlaying = true
                Log.d(tag, "Alarm tone started on USAGE_ALARM")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting alarm tone: ${e.message}", e)
        }
    }

    /**
     * Stop and silence the alarm tone.
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
