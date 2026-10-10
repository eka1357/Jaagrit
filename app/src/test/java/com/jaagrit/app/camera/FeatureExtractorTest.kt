package com.jaagrit.app.camera

import com.jaagrit.app.engine.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {

    @Test
    fun verifySyntheticOpenVsClosedEyeEar() {
        val numLandmarks = 468
        val openLandmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.5f, 0f) }
        val closedLandmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.5f, 0f) }

        // Setup Right Eye [33, 160, 158, 133, 153, 144]
        // Horizontal corners: p1(33) at (0.30, 0.50), p4(133) at (0.40, 0.50) -> width = 0.10
        openLandmarks[33] = FeatureExtractor.Point3D(0.30f, 0.50f, 0f)
        openLandmarks[133] = FeatureExtractor.Point3D(0.40f, 0.50f, 0f)

        closedLandmarks[33] = FeatureExtractor.Point3D(0.30f, 0.50f, 0f)
        closedLandmarks[133] = FeatureExtractor.Point3D(0.40f, 0.50f, 0f)

        // Open eye vertical points: height ~ 0.03
        openLandmarks[160] = FeatureExtractor.Point3D(0.33f, 0.485f, 0f)
        openLandmarks[144] = FeatureExtractor.Point3D(0.33f, 0.515f, 0f)
        openLandmarks[158] = FeatureExtractor.Point3D(0.37f, 0.485f, 0f)
        openLandmarks[153] = FeatureExtractor.Point3D(0.37f, 0.515f, 0f)

        // Closed eye vertical points: height ~ 0.002
        closedLandmarks[160] = FeatureExtractor.Point3D(0.33f, 0.499f, 0f)
        closedLandmarks[144] = FeatureExtractor.Point3D(0.33f, 0.501f, 0f)
        closedLandmarks[158] = FeatureExtractor.Point3D(0.37f, 0.499f, 0f)
        closedLandmarks[153] = FeatureExtractor.Point3D(0.37f, 0.501f, 0f)

        val earOpen = FeatureExtractor.calculateEar(openLandmarks, Config.LANDMARKS_EYE_RIGHT)
        val earClosed = FeatureExtractor.calculateEar(closedLandmarks, Config.LANDMARKS_EYE_RIGHT)

        // Open EAR should be ~0.30
        assertEquals(0.30f, earOpen, 0.01f)
        // Closed EAR should be ~0.02
        assertEquals(0.02f, earClosed, 0.005f)

        // Verify clear separation
        assertTrue("Open EAR must be significantly larger than closed EAR", earOpen > earClosed)
        assertTrue("Open/closed gap must exceed minimum gap threshold", (earOpen - earClosed) > Config.CALIBRATION_MIN_GAP)
    }

    @Test
    fun verifyPixelSpaceEarCalculation() {
        val numLandmarks = 468
        val landmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.5f, 0f) }

        // Simulated portrait frame: 480 width x 640 height (3:4 aspect ratio)
        val imageWidth = 480f
        val imageHeight = 640f

        // Horizontal eye width: 48 pixels in a 480-wide frame -> dx = 0.10
        landmarks[33] = FeatureExtractor.Point3D(100f / imageWidth, 200f / imageHeight, 0f)
        landmarks[133] = FeatureExtractor.Point3D(148f / imageWidth, 200f / imageHeight, 0f)

        // Vertical eye height: 16 pixels in a 640-high frame -> dy = 0.025
        landmarks[160] = FeatureExtractor.Point3D(116f / imageWidth, 192f / imageHeight, 0f)
        landmarks[144] = FeatureExtractor.Point3D(116f / imageWidth, 208f / imageHeight, 0f)
        landmarks[158] = FeatureExtractor.Point3D(132f / imageWidth, 192f / imageHeight, 0f)
        landmarks[153] = FeatureExtractor.Point3D(132f / imageWidth, 208f / imageHeight, 0f)

        // Without pixel-space scaling (1x1 normalized), EAR is distorted by aspect ratio (0.25)
        val earNormalized = FeatureExtractor.calculateEar(landmarks, Config.LANDMARKS_EYE_RIGHT, 1f, 1f)
        assertEquals(0.25f, earNormalized, 0.005f)

        // With pixel-space scaling (480x640), EAR reflects true isotropic geometry: 16 / 48 = 0.333
        val earPixelSpace = FeatureExtractor.calculateEar(landmarks, Config.LANDMARKS_EYE_RIGHT, imageWidth, imageHeight)
        assertEquals(16f / 48f, earPixelSpace, 0.005f)
        assertTrue("Isotropic pixel-space EAR must be independent of camera aspect ratio", earPixelSpace > earNormalized)
    }

    @Test
    fun verifySyntheticNormalVsYawningMouthMar() {
        val numLandmarks = 468
        val normalLandmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.7f, 0f) }
        val yawnLandmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.7f, 0f) }

        // Mouth corners: 78 and 308. Horizontal width = 0.20
        normalLandmarks[78] = FeatureExtractor.Point3D(0.40f, 0.70f, 0f)
        normalLandmarks[308] = FeatureExtractor.Point3D(0.60f, 0.70f, 0f)

        yawnLandmarks[78] = FeatureExtractor.Point3D(0.40f, 0.70f, 0f)
        yawnLandmarks[308] = FeatureExtractor.Point3D(0.60f, 0.70f, 0f)

        // Normal closed mouth: height ~ 0.01
        normalLandmarks[13] = FeatureExtractor.Point3D(0.50f, 0.695f, 0f)
        normalLandmarks[14] = FeatureExtractor.Point3D(0.50f, 0.705f, 0f)
        normalLandmarks[81] = FeatureExtractor.Point3D(0.45f, 0.696f, 0f)
        normalLandmarks[178] = FeatureExtractor.Point3D(0.45f, 0.704f, 0f)
        normalLandmarks[311] = FeatureExtractor.Point3D(0.55f, 0.696f, 0f)
        normalLandmarks[402] = FeatureExtractor.Point3D(0.55f, 0.704f, 0f)

        // Yawning wide open mouth: height ~ 0.12 - 0.14
        yawnLandmarks[13] = FeatureExtractor.Point3D(0.50f, 0.63f, 0f)
        yawnLandmarks[14] = FeatureExtractor.Point3D(0.50f, 0.77f, 0f)
        yawnLandmarks[81] = FeatureExtractor.Point3D(0.45f, 0.64f, 0f)
        yawnLandmarks[178] = FeatureExtractor.Point3D(0.45f, 0.76f, 0f)
        yawnLandmarks[311] = FeatureExtractor.Point3D(0.55f, 0.64f, 0f)
        yawnLandmarks[402] = FeatureExtractor.Point3D(0.55f, 0.76f, 0f)

        val marNormal = FeatureExtractor.calculateMar(normalLandmarks)
        val marYawn = FeatureExtractor.calculateMar(yawnLandmarks)

        assertTrue("Normal mouth MAR should be below 0.15", marNormal < 0.15f)
        assertTrue("Yawn mouth MAR should be above 0.50", marYawn > 0.50f)
        assertTrue("Yawn MAR must be substantially higher than normal", marYawn > marNormal * 3.0f)
    }

    @Test
    fun verifyHeadPitchSignConvention() {
        val numLandmarks = 468
        val landmarks = MutableList(numLandmarks) { FeatureExtractor.Point3D(0.5f, 0.5f, 0f) }

        // Forehead (10) vs Chin (152)
        landmarks[10] = FeatureExtractor.Point3D(0.50f, 0.30f, -0.05f) // Forehead tilts closer to camera (negative z)
        landmarks[152] = FeatureExtractor.Point3D(0.50f, 0.70f, 0.05f)  // Chin tilts away from camera (positive z)
        landmarks[234] = FeatureExtractor.Point3D(0.30f, 0.50f, 0f)
        landmarks[454] = FeatureExtractor.Point3D(0.70f, 0.50f, 0f)
        landmarks[33] = FeatureExtractor.Point3D(0.40f, 0.45f, 0f)
        landmarks[263] = FeatureExtractor.Point3D(0.60f, 0.45f, 0f)

        val (pitch, _, _) = FeatureExtractor.calculatePoseFromLandmarks(landmarks)

        // Positive pitch = head down (nodding forward / droop) per D3 and requirements
        assertTrue("Head nodding down must produce positive pitch (pitchDeg > 0)", pitch > 0f)
    }

    @Test
    fun verifyMatrixPoseCalculationWithKnownRotation() {
        // Identity matrix produces 0 pitch, yaw, roll
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        val (pitchZero, yawZero, rollZero) = FeatureExtractor.calculatePoseFromMatrix(identity)
        assertEquals(0f, pitchZero, 0.001f)
        assertEquals(0f, yawZero, 0.001f)
        assertEquals(0f, rollZero, 0.001f)

        // 20 degree rotation around X axis (nodding down in standard column-major)
        val angleDeg = 20.0
        val angleRad = Math.toRadians(angleDeg)
        val c = kotlin.math.cos(angleRad).toFloat()
        val s = kotlin.math.sin(angleRad).toFloat()

        // Col 0: [1, 0, 0, 0]
        // Col 1: [0, cos, sin, 0]
        // Col 2: [0, -sin, cos, 0]
        // Col 3: [0, 0, 0, 1]
        val rotX20 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f
        )
        val (pitch, yaw, roll) = FeatureExtractor.calculatePoseFromMatrix(rotX20)
        assertEquals(0f, yaw, 0.01f)
        assertEquals(0f, roll, 0.01f)
        // Check pitch magnitude matches 20 degrees
        assertEquals(20f, kotlin.math.abs(pitch), 0.5f)
    }
}
