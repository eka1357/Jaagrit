package com.jaagrit.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates MediaPipe FaceLandmarker in live-stream mode.
 * Decoupled from Android UI per AGENTS.md Rule 10.
 */
class FaceLandmarkerWrapper(
    private val context: Context,
    private val onResult: ((VisionResult, FaceLandmarkerResult?) -> Unit)? = null
) {
    private var faceLandmarker: FaceLandmarker? = null

    private val _visionResult = MutableStateFlow(VisionResult())
    val visionResult: StateFlow<VisionResult> = _visionResult.asStateFlow()

    private var lastFrameTimestampMs: Long = 0L
    private val frameStartTimes = ConcurrentHashMap<Long, Long>()

    // Sliding window for FPS calculation (last 15 frames)
    private val frameTimestamps = ArrayDeque<Long>()
    private val fpsWindowSize = 15

    @Volatile
    private var isClosed = false

    init {
        initializeLandmarker()
    }

    private fun initializeLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener { result: FaceLandmarkerResult, _: MPImage ->
                    processDetectionResult(result, result.timestampMs())
                }
                .setErrorListener { error ->
                    Log.e(TAG, "FaceLandmarker live-stream error: ${error.message}", error)
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.i(TAG, "FaceLandmarker initialized successfully in LIVE_STREAM mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FaceLandmarker", e)
        }
    }

    /**
     * Ingests a CameraX ImageProxy frame, transforms it, and submits to FaceLandmarker.
     * Always closes the ImageProxy.
     */
    fun processImageProxy(imageProxy: ImageProxy) {
        if (isClosed || faceLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            val timestampMs = getNextMonotonicTimestamp()
            frameStartTimes[timestampMs] = SystemClock.elapsedRealtime()

            faceLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during frame processing", e)
        } finally {
            imageProxy.close()
        }
    }

    private var lastLoggedMs: Long = 0L

    private fun processDetectionResult(result: FaceLandmarkerResult, timestampMs: Long) {
        val now = SystemClock.elapsedRealtime()
        val startTime = frameStartTimes.remove(timestampMs) ?: now
        val inferenceTimeMs = (now - startTime).coerceAtLeast(0L)

        // Calculate sliding FPS
        val currentFps: Float
        synchronized(frameTimestamps) {
            frameTimestamps.addLast(now)
            while (frameTimestamps.size > fpsWindowSize) {
                frameTimestamps.removeFirst()
            }
            currentFps = if (frameTimestamps.size > 1) {
                val durationSec = (frameTimestamps.last() - frameTimestamps.first()) / 1000f
                if (durationSec > 0f) (frameTimestamps.size - 1) / durationSec else 0f
            } else {
                0f
            }
        }

        val hasFace = result.faceLandmarks().isNotEmpty()
        val faceFrame = FeatureExtractor.extract(result, now)

        val visionOutput = VisionResult(
            faceFound = hasFace,
            inferenceTimeMs = inferenceTimeMs,
            fps = currentFps,
            timestampMs = now,
            faceFrame = faceFrame
        )

        if (now - lastLoggedMs >= 1000L) {
            lastLoggedMs = now
            Log.i(
                TAG,
                "FaceLandmarker: found=$hasFace, EAR=${"%.3f".format(faceFrame.earAvg)}, MAR=${"%.3f".format(faceFrame.mar)}, Pitch=${"%.1f°".format(faceFrame.pitchDeg)}, inf=${inferenceTimeMs}ms, fps=${"%.1f".format(currentFps)}"
            )
        }

        _visionResult.value = visionOutput
        onResult?.invoke(visionOutput, result)
    }

    @Synchronized
    private fun getNextMonotonicTimestamp(): Long {
        var now = SystemClock.elapsedRealtime()
        if (now <= lastFrameTimestampMs) {
            now = lastFrameTimestampMs + 1
        }
        lastFrameTimestampMs = now
        return now
    }

    fun close() {
        isClosed = true
        try {
            faceLandmarker?.close()
            faceLandmarker = null
            frameStartTimes.clear()
            Log.i(TAG, "FaceLandmarker released")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing FaceLandmarker", e)
        }
    }

    companion object {
        private const val TAG = "JAAGRIT"
    }
}
