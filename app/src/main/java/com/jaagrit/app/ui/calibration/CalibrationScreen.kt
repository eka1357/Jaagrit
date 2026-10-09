package com.jaagrit.app.ui.calibration

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.camera.CameraController
import com.jaagrit.app.camera.FaceLandmarkerWrapper
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.BaselineCalculator
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Calibration step phases */
enum class CalibrationStep {
    LOOK_NORMAL,
    CLOSE_EYES,
    YAWN,
    LOOK_AROUND,
    MATH_QUESTION,
    RESULT
}

private val CalibBlueDark = Color(0xFF0B192C)
private val CalibBlueCard = Color(0xFF1E3E62)
private val CalibBlueAccent = Color(0xFF008DDA)
private val CalibBlueLight = Color(0xFF41C9E2)

/**
 * Milestone M3: Calibration flow screens with dedicated Blue UI (CAL-1, CAL-4, D4).
 * Records open/closed eye medians, calculates personalized threshold, validates gap,
 * and persists the Baseline in DataStore.
 */
@Composable
fun CalibrationScreen(
    onCalibrationFinished: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    config: Config = Config.DEFAULT
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val baselineStore = remember { BaselineStore(context) }

    // Keep screen on during calibration
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Vision pipeline components
    val landmarkerWrapper = remember { FaceLandmarkerWrapper(context) }
    val cameraController = remember { CameraController(context, landmarkerWrapper) }
    val visionResult by landmarkerWrapper.visionResult.collectAsState()

    var isCameraReady by remember { mutableStateOf(false) }

    // Calibration state
    var currentStep by remember { mutableStateOf(CalibrationStep.LOOK_NORMAL) }
    var countdownSeconds by remember { mutableIntStateOf((config.calibrationOpenMs / 1000L).toInt()) }
    var phaseStartMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Sample storage
    val openFrames = remember { mutableStateListOf<FaceFrame>() }
    var openPhaseStartMs by remember { mutableLongStateOf(0L) }

    val closedFrames = remember { mutableStateListOf<FaceFrame>() }
    var closedPhaseStartMs by remember { mutableLongStateOf(0L) }

    val yawnFrames = remember { mutableStateListOf<FaceFrame>() }
    var yawnPhaseStartMs by remember { mutableLongStateOf(0L) }

    var mathLatencyMs by remember { mutableLongStateOf(0L) }
    var mathStartTimeMs by remember { mutableLongStateOf(0L) }

    var computedBaseline by remember { mutableStateOf<Baseline?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Collect frames continuously during calibration
    LaunchedEffect(visionResult) {
        val frame = visionResult.faceFrame
        if (!frame.faceFound) return@LaunchedEffect

        when (currentStep) {
            CalibrationStep.LOOK_NORMAL -> openFrames.add(frame)
            CalibrationStep.CLOSE_EYES -> closedFrames.add(frame)
            CalibrationStep.YAWN -> yawnFrames.add(frame)
            else -> Unit
        }
    }

    // Timer coroutine for the active step
    LaunchedEffect(currentStep) {
        phaseStartMs = SystemClock.uptimeMillis()

        when (currentStep) {
            CalibrationStep.LOOK_NORMAL -> {
                openPhaseStartMs = phaseStartMs
                val totalSecs = (config.calibrationOpenMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
                currentStep = CalibrationStep.CLOSE_EYES
            }
            CalibrationStep.CLOSE_EYES -> {
                closedPhaseStartMs = phaseStartMs
                val totalSecs = (config.calibrationClosedMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
                // Transition to optional yawn
                currentStep = CalibrationStep.YAWN
            }
            CalibrationStep.YAWN -> {
                yawnPhaseStartMs = phaseStartMs
                val totalSecs = (config.calibrationYawnMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
                currentStep = CalibrationStep.LOOK_AROUND
            }
            CalibrationStep.LOOK_AROUND -> {
                val totalSecs = (config.calibrationHeadPoseMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
                currentStep = CalibrationStep.MATH_QUESTION
            }
            CalibrationStep.MATH_QUESTION -> {
                mathStartTimeMs = SystemClock.uptimeMillis()
                // Auto advance after 8 seconds if no option clicked
                delay(8000L)
                if (currentStep == CalibrationStep.MATH_QUESTION) {
                    mathLatencyMs = Config.DEFAULT_RESPONSE_LATENCY_MS
                    finishCalibration(
                        openFrames, openPhaseStartMs,
                        closedFrames, closedPhaseStartMs,
                        yawnFrames, yawnPhaseStartMs,
                        mathLatencyMs, config
                    ) { result ->
                        computedBaseline = result
                        currentStep = CalibrationStep.RESULT
                    }
                }
            }
            CalibrationStep.RESULT -> {
                // Done - result shown
            }
        }
    }

    // Camera preview lifecycle
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraController.release()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CalibBlueDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "जागृत कैलिब्रेशन",
                        fontSize = 15.sp,
                        color = CalibBlueLight,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "DRIVER CALIBRATION",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                TextButton(onClick = onCancel) {
                    Text(
                        text = "Cancel",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }

            // Camera preview with face tracking pill
            Box(
                modifier = Modifier
                    .size(width = 170.dp, height = 130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, CalibBlueLight.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.BottomCenter
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            cameraController.startCamera(
                                lifecycleOwner = lifecycleOwner,
                                previewView = this,
                                onCameraReady = { isCameraReady = true }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Face detected status pill
                Surface(
                    modifier = Modifier
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (visionResult.faceFound) Color(0xCC00C853) else Color(0xCCD50000)
                ) {
                    Text(
                        text = if (visionResult.faceFound) "FACE DETECTED" else "NO FACE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Main Active Step Display
            when (currentStep) {
                CalibrationStep.LOOK_NORMAL -> {
                    StepCard(
                        stepTitle = "STEP 1 OF 5",
                        hindiPrompt = "सामान्य रूप से सामने देखें",
                        englishPrompt = "Look normally at the road ahead",
                        instruction = "Keep your eyes naturally open in normal driving posture. Blinks are natural and counted.",
                        countdown = countdownSeconds,
                        maxSeconds = (config.calibrationOpenMs / 1000L).toInt()
                    )
                }
                CalibrationStep.CLOSE_EYES -> {
                    StepCard(
                        stepTitle = "STEP 2 OF 5",
                        hindiPrompt = "अपनी आँखें बंद करें",
                        englishPrompt = "Close your eyes completely",
                        instruction = "Keep your eyes gently closed until the timer ends to record your closed-eye baseline.",
                        countdown = countdownSeconds,
                        maxSeconds = (config.calibrationClosedMs / 1000L).toInt(),
                        accentColor = Color(0xFFFFB74D)
                    )
                }
                CalibrationStep.YAWN -> {
                    StepCardWithSkip(
                        stepTitle = "STEP 3 OF 5 (OPTIONAL)",
                        hindiPrompt = "एक बार जम्हाई लें (मुँह खोलें)",
                        englishPrompt = "Yawn once (open mouth wide)",
                        instruction = "Helps calibrate your natural yawning mouth opening.",
                        countdown = countdownSeconds,
                        maxSeconds = (config.calibrationYawnMs / 1000L).toInt(),
                        onSkip = { currentStep = CalibrationStep.LOOK_AROUND }
                    )
                }
                CalibrationStep.LOOK_AROUND -> {
                    StepCardWithSkip(
                        stepTitle = "STEP 4 OF 5 (OPTIONAL)",
                        hindiPrompt = "बाएँ, दाएँ और नीचे देखें",
                        englishPrompt = "Check your mirrors & dashboard",
                        instruction = "Look left, right, and down to establish your natural head movement range.",
                        countdown = countdownSeconds,
                        maxSeconds = (config.calibrationHeadPoseMs / 1000L).toInt(),
                        onSkip = { currentStep = CalibrationStep.MATH_QUESTION }
                    )
                }
                CalibrationStep.MATH_QUESTION -> {
                    MathQuestionCard(
                        question = "50 + 50 = ?",
                        options = listOf(90, 100, 110),
                        correctAnswer = 100,
                        onOptionSelected = { selected ->
                            mathLatencyMs = SystemClock.uptimeMillis() - mathStartTimeMs
                            finishCalibration(
                                openFrames, openPhaseStartMs,
                                closedFrames, closedPhaseStartMs,
                                yawnFrames, yawnPhaseStartMs,
                                mathLatencyMs, config
                            ) { result ->
                                computedBaseline = result
                                currentStep = CalibrationStep.RESULT
                            }
                        },
                        onSkip = {
                            mathLatencyMs = Config.DEFAULT_RESPONSE_LATENCY_MS
                            finishCalibration(
                                openFrames, openPhaseStartMs,
                                closedFrames, closedPhaseStartMs,
                                yawnFrames, yawnPhaseStartMs,
                                mathLatencyMs, config
                            ) { result ->
                                computedBaseline = result
                                currentStep = CalibrationStep.RESULT
                            }
                        }
                    )
                }
                CalibrationStep.RESULT -> {
                    computedBaseline?.let { baseline ->
                        ResultCard(
                            baseline = baseline,
                            onSaveAndProceed = {
                                scope.launch {
                                    isSaving = true
                                    baselineStore.saveBaseline(baseline)
                                    isSaving = false
                                    onCalibrationFinished()
                                }
                            },
                            onRetry = {
                                openFrames.clear()
                                closedFrames.clear()
                                yawnFrames.clear()
                                mathLatencyMs = 0L
                                currentStep = CalibrationStep.LOOK_NORMAL
                            },
                            isSaving = isSaving
                        )
                    }
                }
            }

            // Bottom note
            Text(
                text = "Medians are used across all metrics • No frames or audio are stored",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun finishCalibration(
    openFrames: List<FaceFrame>,
    openStartMs: Long,
    closedFrames: List<FaceFrame>,
    closedStartMs: Long,
    yawnFrames: List<FaceFrame>,
    yawnStartMs: Long,
    latencyMs: Long,
    config: Config,
    onResult: (Baseline) -> Unit
) {
    Log.i("JAAGRIT", "finishCalibration: openFrames=${openFrames.size}, closedFrames=${closedFrames.size}, openStart=$openStartMs, closedStart=$closedStartMs")
    val baseline = BaselineCalculator.computeFromFrames(
        openEyeFrames = openFrames,
        openPhaseStartMs = openStartMs,
        closedEyeFrames = closedFrames,
        closedPhaseStartMs = closedStartMs,
        yawnFrames = yawnFrames,
        yawnPhaseStartMs = yawnStartMs,
        responseLatencyMs = latencyMs,
        calibratedAtMs = System.currentTimeMillis(),
        config = config
    )
    Log.i("JAAGRIT", "finishCalibration result: open=${baseline.openEar}, closed=${baseline.closedEar}, thresh=${baseline.threshold}, gap=${baseline.earGap}, valid=${baseline.isValid}")
    onResult(baseline)
}

@Composable
private fun StepCard(
    stepTitle: String,
    hindiPrompt: String,
    englishPrompt: String,
    instruction: String,
    countdown: Int,
    maxSeconds: Int,
    accentColor: Color = CalibBlueLight
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CalibBlueCard)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stepTitle,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = hindiPrompt,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = englishPrompt,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Large circular countdown
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                CircularProgressIndicator(
                    progress = { countdown.toFloat() / maxSeconds.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = accentColor,
                    strokeWidth = 6.dp,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = "${countdown}s",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = instruction,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepCardWithSkip(
    stepTitle: String,
    hindiPrompt: String,
    englishPrompt: String,
    instruction: String,
    countdown: Int,
    maxSeconds: Int,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CalibBlueCard)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stepTitle,
                color = CalibBlueLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = hindiPrompt,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = englishPrompt,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(75.dp)
            ) {
                CircularProgressIndicator(
                    progress = { countdown.toFloat() / maxSeconds.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = CalibBlueLight,
                    strokeWidth = 5.dp,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = "${countdown}s",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = instruction,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            OutlinedButton(
                onClick = onSkip,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Skip Step", color = Color.White)
            }
        }
    }
}

@Composable
private fun MathQuestionCard(
    question: String,
    options: List<Int>,
    correctAnswer: Int,
    onOptionSelected: (Int) -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CalibBlueCard)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "STEP 5 OF 5 — REACTION TEST",
                color = CalibBlueLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "तुरंत सही उत्तर चुनें",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Quick check: Tap the answer to measure baseline reaction latency",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Text(
                text = question,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CalibBlueLight
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                options.forEach { opt ->
                    Button(
                        onClick = { onOptionSelected(opt) },
                        colors = ButtonDefaults.buttonColors(containerColor = CalibBlueAccent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(80.dp)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "$opt",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            TextButton(onClick = onSkip) {
                Text(text = "Skip Reaction Test", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ResultCard(
    baseline: Baseline,
    onSaveAndProceed: () -> Unit,
    onRetry: () -> Unit,
    isSaving: Boolean
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CalibBlueCard)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (baseline.isValid) {
                Text(
                    text = "कैलिब्रेशन सफल!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
                Text(
                    text = "Calibration Complete",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                // Metrics summary table
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(label = "Open Eye EAR (median)", value = "%.3f".format(baseline.openEar))
                    MetricRow(label = "Closed Eye EAR (median)", value = "%.3f".format(baseline.closedEar))
                    MetricRow(
                        label = "Personal Threshold",
                        value = "%.3f".format(baseline.threshold),
                        isHighlight = true
                    )
                    MetricRow(label = "Separation Gap", value = "%.3f".format(baseline.earGap))
                    MetricRow(label = "Baseline Blink Rate", value = "%.1f / min".format(baseline.blinkRate))
                    if (baseline.responseLatencyMs > 0) {
                        MetricRow(label = "Reaction Latency", value = "${baseline.responseLatencyMs} ms")
                    }
                }

                Button(
                    onClick = onSaveAndProceed,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                ) {
                    Text(
                        text = if (isSaving) "Saving..." else "Save & Start Drive",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                // Warning if gap < 0.05
                Text(
                    text = "कैलिब्रेशन ठीक से नहीं हुआ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5252)
                )
                Text(
                    text = "Eye Separation Gap Too Small (${"%.3f".format(baseline.earGap)} < 0.05)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Open EAR (${"%.3f".format(baseline.openEar)}) and closed EAR (${"%.3f".format(baseline.closedEar)}) are too close. Ensure proper lighting, face angle, or check if sunglasses are on.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000))
                ) {
                    Text(
                        text = "Retry Calibration",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) CalibBlueLight else Color.White
        )
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
