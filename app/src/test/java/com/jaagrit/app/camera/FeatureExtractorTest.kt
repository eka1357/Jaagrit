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
}
