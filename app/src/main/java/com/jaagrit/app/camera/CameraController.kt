package com.jaagrit.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, front camera binding, and feeds ImageAnalysis
 * frames into FaceLandmarkerWrapper.
 * Decoupled from UI per AGENTS.md Rule 10.
 */
class CameraController(
    private val context: Context,
    private val landmarkerWrapper: FaceLandmarkerWrapper
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Keep track of this controller's use cases to avoid global unbindAll (AUDIT-005)
    private var boundPreview: Preview? = null
    private var boundImageAnalysis: ImageAnalysis? = null

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null,
        onCameraReady: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    previewView?.let { pv ->
                        it.surfaceProvider = pv.surfaceProvider
                    }
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        if (cameraExecutor.isShutdown) {
                            cameraExecutor = Executors.newSingleThreadExecutor()
                        }
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            landmarkerWrapper.processImageProxy(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Unbind only this controller's previously bound use cases (AUDIT-005)
                val oldUseCases = listOfNotNull(boundPreview, boundImageAnalysis).toTypedArray()
                if (oldUseCases.isNotEmpty()) {
                    cameraProvider?.unbind(*oldUseCases)
                }

                boundPreview = preview
                boundImageAnalysis = imageAnalysis

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i(TAG, "CameraX front camera bound successfully with KEEP_ONLY_LATEST strategy")
                onCameraReady?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        try {
            // Unbind only this controller's use cases, never process-wide unbindAll (AUDIT-005)
            val useCases = listOfNotNull(boundPreview, boundImageAnalysis).toTypedArray()
            if (useCases.isNotEmpty()) {
                cameraProvider?.unbind(*useCases)
            }
            boundPreview = null
            boundImageAnalysis = null
            Log.i(TAG, "CameraX use cases unbound for controller")
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding CameraX", e)
        }
    }

    fun release() {
        stopCamera()
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        landmarkerWrapper.close()
    }

    companion object {
        private const val TAG = "JAAGRIT"
    }
}
