package com.jaagrit.app.camera

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure feature extractor transforming MediaPipe FaceLandmarker output into a FaceFrame.
 * Extracts EAR (left/right/avg), MAR, and head pose (pitch/yaw/roll).
 *
 * Requirements: CAM-3, DECISIONS D3 (positive pitch = head down).
 */
object FeatureExtractor {

    data class Point3D(val x: Float, val y: Float, val z: Float = 0f)

    // Mouth landmark indices
    // Inner lip vertical pairs (upper, lower)
    val MAR_VERTICAL_PAIRS = listOf(
        Pair(13, 14),   // Center upper/lower inner lip
        Pair(81, 178),  // Left inner lip
        Pair(311, 402)  // Right inner lip
    )
    // Mouth corners (horizontal width)
    val MAR_CORNERS = Pair(78, 308)

    // Landmark indices for geometric head pose
    const val LANDMARK_FOREHEAD = 10
    const val LANDMARK_CHIN = 152
    const val LANDMARK_NOSE_TIP = 1
    const val LANDMARK_LEFT_TRAGUS = 234
    const val LANDMARK_RIGHT_TRAGUS = 454
    const val LANDMARK_LEFT_EYE_OUTER = 263
    const val LANDMARK_RIGHT_EYE_OUTER = 33

    /**
     * Extracts a FaceFrame from FaceLandmarkerResult.
     */
    fun extract(result: FaceLandmarkerResult?, timestampMs: Long): FaceFrame {
        if (result == null || result.faceLandmarks().isEmpty()) {
            return FaceFrame(
                tsMs = timestampMs,
                faceFound = false,
                earL = 0f,
                earR = 0f,
                mar = 0f,
                pitchDeg = 0f,
                yawDeg = 0f,
                rollDeg = 0f
            )
        }

        val landmarksList: List<NormalizedLandmark> = result.faceLandmarks()[0]
        val points = landmarksList.map { Point3D(it.x(), it.y(), it.z()) }

        // 1. Calculate Eye Aspect Ratio (EAR)
        val earR = calculateEar(points, Config.LANDMARKS_EYE_RIGHT)
        val earL = calculateEar(points, Config.LANDMARKS_EYE_LEFT)

        // 2. Calculate Mouth Aspect Ratio (MAR)
        val mar = calculateMar(points)

        // 3. Calculate Head Pose (pitch, yaw, roll)
        // Prefer facial transformation matrix if available, fallback to geometric estimate
        val matrixListOpt = result.facialTransformationMatrixes()
        val (pitch, yaw, roll) = if (matrixListOpt.isPresent && matrixListOpt.get().isNotEmpty()) {
            calculatePoseFromMatrix(matrixListOpt.get()[0])
        } else {
            calculatePoseFromLandmarks(points)
        }

        return FaceFrame(
            tsMs = timestampMs,
            faceFound = true,
            earL = earL,
            earR = earR,
            mar = mar,
            pitchDeg = pitch,
            yawDeg = yaw,
            rollDeg = roll
        )
    }

    /**
     * Calculates EAR for a single eye given 6 landmark points.
     * Formula: (dist(p2, p6) + dist(p3, p5)) / (2.0 * dist(p1, p4))
     */
    fun calculateEar(points: List<Point3D>, indices: IntArray): Float {
        if (indices.size < 6) return 0f
        for (idx in indices) {
            if (idx < 0 || idx >= points.size) return 0f
        }
        val p1 = points[indices[0]] // lateral canthus
        val p2 = points[indices[1]] // top 1
        val p3 = points[indices[2]] // top 2
        val p4 = points[indices[3]] // medial canthus
        val p5 = points[indices[4]] // bottom 2
        val p6 = points[indices[5]] // bottom 1

        val v1 = dist2D(p2, p6)
        val v2 = dist2D(p3, p5)
        val h = dist2D(p1, p4)

        return if (h > 1e-6f) (v1 + v2) / (2.0f * h) else 0f
    }

    /**
     * Calculates MAR from lip landmarks.
     * Formula: sum(vertical_distances) / (3.0 * horizontal_distance)
     */
    fun calculateMar(points: List<Point3D>): Float {
        if (points.size <= 402) return 0f
        val h = dist2D(points[MAR_CORNERS.first], points[MAR_CORNERS.second])
        if (h < 1e-6f) return 0f

        var vSum = 0f
        for ((upper, lower) in MAR_VERTICAL_PAIRS) {
            vSum += dist2D(points[upper], points[lower])
        }

        return vSum / (MAR_VERTICAL_PAIRS.size * h)
    }

    /**
     * Extracts Pitch, Yaw, Roll from 4x4 facial transformation matrix.
     * MediaPipe 4x4 matrix is column-major.
     * Convention: Positive pitch = head down (nodding forward).
     */
    fun calculatePoseFromMatrix(m: FloatArray): Triple<Float, Float, Float> {
        if (m.size < 16) return Triple(0f, 0f, 0f)

        // Rotation matrix in column-major:
        // Col 0: m[0], m[1], m[2]
        // Col 1: m[4], m[5], m[6]
        // Col 2: m[8], m[9], m[10]

        // Pitch: rotation around X axis (nodding down).
        // In MediaPipe coordinate frame, nodding down yields positive pitch:
        val pitchRad = atan2(-m[6], m[10])
        val pitchDeg = (pitchRad * 180.0 / Math.PI).toFloat()

        // Yaw: rotation around Y axis (turning left/right)
        val yawRad = atan2(m[2], sqrt(m[0] * m[0] + m[1] * m[1]))
        val yawDeg = (yawRad * 180.0 / Math.PI).toFloat()

        // Roll: rotation around Z axis (tilting side to side)
        val rollRad = atan2(m[1], m[0])
        val rollDeg = (rollRad * 180.0 / Math.PI).toFloat()

        return Triple(pitchDeg, yawDeg, rollDeg)
    }

    /**
     * Geometric fallback: estimates Pitch, Yaw, Roll directly from 3D landmark positions.
     * Convention: Positive pitch = head down (nodding forward).
     */
    fun calculatePoseFromLandmarks(points: List<Point3D>): Triple<Float, Float, Float> {
        if (points.size <= LANDMARK_RIGHT_TRAGUS) return Triple(0f, 0f, 0f)

        val forehead = points[LANDMARK_FOREHEAD]
        val chin = points[LANDMARK_CHIN]
        val leftTragus = points[LANDMARK_LEFT_TRAGUS]
        val rightTragus = points[LANDMARK_RIGHT_TRAGUS]
        val leftEye = points[LANDMARK_LEFT_EYE_OUTER]
        val rightEye = points[LANDMARK_RIGHT_EYE_OUTER]

        // Pitch: Forehead vs Chin depth difference.
        // When nodding down, forehead moves closer (z decreases) and chin moves back (z increases).
        // dy is positive (chin is below forehead).
        val dy = chin.y - forehead.y
        val dz = chin.z - forehead.z
        val pitchRad = atan2(dz, if (dy > 1e-4f) dy else 1e-4f)
        val pitchDeg = (pitchRad * 180.0 / Math.PI).toFloat() * Config.HEAD_PITCH_GEOMETRIC_SCALE // Scaled to degree space

        // Yaw: Left vs Right tragus depth difference.
        val dxTragus = rightTragus.x - leftTragus.x
        val dzTragus = leftTragus.z - rightTragus.z
        val yawRad = atan2(dzTragus, if (dxTragus > 1e-4f) dxTragus else 1e-4f)
        val yawDeg = (yawRad * 180.0 / Math.PI).toFloat()

        // Roll: Eye line angle in screen plane.
        val dxEye = leftEye.x - rightEye.x
        val dyEye = leftEye.y - rightEye.y
        val rollRad = atan2(dyEye, if (dxEye > 1e-4f) dxEye else 1e-4f)
        val rollDeg = (rollRad * 180.0 / Math.PI).toFloat()

        return Triple(pitchDeg, yawDeg, rollDeg)
    }

    fun dist2D(p1: Point3D, p2: Point3D): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
