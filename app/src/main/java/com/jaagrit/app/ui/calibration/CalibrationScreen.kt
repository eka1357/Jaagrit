package com.jaagrit.app.ui.calibration

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.R
import com.jaagrit.app.camera.CameraController
import com.jaagrit.app.camera.FaceLandmarkerWrapper
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.BaselineCalculator
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.ui.components.JaagritBrandHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
 * Single language at a time with clean typography and zero mixed text.
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
    val settingsStore = remember { SettingsStore(context) }
    val isQuickCalib by settingsStore.quickCalibrationFlow.collectAsState(initial = false)
    val activeConfig = remember(isQuickCalib, config) { config.copy(quickCalibration = isQuickCalib) }

    // Keep screen on during calibration
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Vision pipeline components
    val landmarkerWrapper = remember { FaceLandmarkerWrapper(context) }
    val cameraController = remember { CameraController(context, landmarkerWrapper) }
    val visionResult by landmarkerWrapper.visionResult.collectAsState()

    var isCameraReady by remember { mutableStateOf(false) }

    // Calibration state
    var currentStep by remember { mutableStateOf(CalibrationStep.LOOK_NORMAL) }
    var countdownSeconds by remember(activeConfig) { mutableIntStateOf((activeConfig.calibrationOpenMs / 1000L).toInt()) }
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
    var validationResult by remember { mutableStateOf<CalibrationValidationResult?>(null) }
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
                val totalSecs = (activeConfig.calibrationOpenMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
                currentStep = CalibrationStep.CLOSE_EYES
            }
            CalibrationStep.CLOSE_EYES -> {
                closedPhaseStartMs = phaseStartMs
                val totalSecs = (activeConfig.calibrationClosedMs / 1000L).toInt()
                for (s in totalSecs downTo 1) {
                    countdownSeconds = s
                    delay(1000L)
                }
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
                delay(8000L)
                if (currentStep == CalibrationStep.MATH_QUESTION) {
                    mathLatencyMs = Config.DEFAULT_RESPONSE_LATENCY_MS
                    finishCalibration(
                        openFrames, openPhaseStartMs,
                        closedFrames, closedPhaseStartMs,
                        yawnFrames, yawnPhaseStartMs,
                        mathLatencyMs, config
                    ) { result, validation ->
                        computedBaseline = result
                        validationResult = validation
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
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.calib_perm_needed_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.calib_perm_needed_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CalibBlueAccent)
                    ) {
                        Text(
                            text = stringResource(R.string.calib_btn_grant_perm),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.calib_btn_cancel),
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar: Brand + Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        JaagritBrandHeader(
                            wordmarkSize = 20.sp,
                            subSize = 12.sp,
                            wordmarkColor = Color.White,
                            subColor = CalibBlueLight,
                            horizontalAlignment = Alignment.Start
                        )
                        Text(
                            text = stringResource(R.string.calib_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CalibBlueLight
                        )
                    }

                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.calib_btn_cancel),
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
                        modifier = Modifier.padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (visionResult.faceFound) Color(0xCC00C853) else Color(0xCCD50000)
                    ) {
                        Text(
                            text = stringResource(if (visionResult.faceFound) R.string.calib_face_detected else R.string.no_face),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Main Active Step Display (Single language at a time)
                when (currentStep) {
                    CalibrationStep.LOOK_NORMAL -> {
                        StepCard(
                            stepTitle = stringResource(R.string.calib_step_1_title),
                            prompt = stringResource(R.string.calib_step_1_prompt),
                            instruction = stringResource(R.string.calib_step_1_desc),
                            countdown = countdownSeconds,
                            maxSeconds = (activeConfig.calibrationOpenMs / 1000L).toInt()
                        )
                    }
                    CalibrationStep.CLOSE_EYES -> {
                        StepCard(
                            stepTitle = stringResource(R.string.calib_step_2_title),
                            prompt = stringResource(R.string.calib_step_2_prompt),
                            instruction = stringResource(R.string.calib_step_2_desc),
                            countdown = countdownSeconds,
                            maxSeconds = (activeConfig.calibrationClosedMs / 1000L).toInt(),
                            accentColor = Color(0xFFFFB74D)
                        )
                    }
                    CalibrationStep.YAWN -> {
                        StepCardWithSkip(
                            stepTitle = stringResource(R.string.calib_step_3_title),
                            prompt = stringResource(R.string.calib_step_3_prompt),
                            instruction = stringResource(R.string.calib_step_3_desc),
                            countdown = countdownSeconds,
                            maxSeconds = (config.calibrationYawnMs / 1000L).toInt(),
                            onSkip = { currentStep = CalibrationStep.LOOK_AROUND }
                        )
                    }
                    CalibrationStep.LOOK_AROUND -> {
                        StepCardWithSkip(
                            stepTitle = stringResource(R.string.calib_step_4_title),
                            prompt = stringResource(R.string.calib_step_4_prompt),
                            instruction = stringResource(R.string.calib_step_4_desc),
                            countdown = countdownSeconds,
                            maxSeconds = (config.calibrationHeadPoseMs / 1000L).toInt(),
                            onSkip = { currentStep = CalibrationStep.MATH_QUESTION }
                        )
                    }
                    CalibrationStep.MATH_QUESTION -> {
                        MathQuestionCard(
                            question = "50 + 50 = ?",
                            options = listOf(90, 100, 110),
                            onOptionSelected = {
                                mathLatencyMs = SystemClock.uptimeMillis() - mathStartTimeMs
                                finishCalibration(
                                    openFrames, openPhaseStartMs,
                                    closedFrames, closedPhaseStartMs,
                                    yawnFrames, yawnPhaseStartMs,
                                    mathLatencyMs, config
                                ) { result, validation ->
                                    computedBaseline = result
                                    validationResult = validation
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
                                ) { result, validation ->
                                    computedBaseline = result
                                    validationResult = validation
                                    currentStep = CalibrationStep.RESULT
                                }
                            }
                        )
                    }
                    CalibrationStep.RESULT -> {
                        computedBaseline?.let { baseline ->
                            ResultCard(
                                baseline = baseline,
                                validationResult = validationResult,
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
                                    computedBaseline = null
                                    validationResult = null
                                    currentStep = CalibrationStep.LOOK_NORMAL
                                },
                                isSaving = isSaving
                            )
                        }
                    }
                }

                // Bottom privacy note
                Text(
                    text = stringResource(R.string.calib_footer_privacy),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
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
    onResult: (Baseline, CalibrationValidationResult) -> Unit
) {
    Log.i("JAAGRIT", "finishCalibration: openFrames=${openFrames.size}, closedFrames=${closedFrames.size}")
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
    val validation = CalibrationValidator.validate(
        openFrames = openFrames,
        openStartMs = openStartMs,
        closedFrames = closedFrames,
        closedStartMs = closedStartMs,
        baseline = baseline,
        config = config
    )
    val finalBaseline = when (validation) {
        is CalibrationValidationResult.Success -> validation.baseline
        is CalibrationValidationResult.Rejected -> validation.partialBaseline
    }
    Log.i("JAAGRIT", "finishCalibration result: thresh=${finalBaseline.threshold}, gap=${finalBaseline.earGap}, valid=${finalBaseline.isValid}")
    onResult(finalBaseline, validation)
}

@Composable
private fun StepCard(
    stepTitle: String,
    prompt: String,
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
                text = prompt,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Large circular countdown with Latin digits
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
                    text = String.format(Locale.US, "%ds", countdown),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = instruction,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepCardWithSkip(
    stepTitle: String,
    prompt: String,
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
                text = prompt,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
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
                    text = String.format(Locale.US, "%ds", countdown),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = instruction,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            OutlinedButton(
                onClick = onSkip,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.calib_btn_skip),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun MathQuestionCard(
    question: String,
    options: List<Int>,
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
                text = stringResource(R.string.calib_step_5_title),
                color = CalibBlueLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.calib_step_5_prompt),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = stringResource(R.string.calib_step_5_desc),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Text(
                text = question,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
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
                            text = String.format(Locale.US, "%d", opt),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            TextButton(onClick = onSkip) {
                Text(
                    text = stringResource(R.string.calib_btn_skip_test),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    baseline: Baseline,
    validationResult: CalibrationValidationResult?,
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
                    text = stringResource(R.string.calib_result_success_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
                Text(
                    text = stringResource(R.string.calib_result_success_sub),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )

                // Metrics summary table (always Latin digits)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricRow(
                        label = stringResource(R.string.calib_metric_open_ear),
                        value = String.format(Locale.US, "%.3f", baseline.openEar)
                    )
                    MetricRow(
                        label = stringResource(R.string.calib_metric_closed_ear),
                        value = String.format(Locale.US, "%.3f", baseline.closedEar)
                    )
                    MetricRow(
                        label = stringResource(R.string.calib_metric_threshold),
                        value = String.format(Locale.US, "%.3f", baseline.threshold),
                        isHighlight = true
                    )
                    MetricRow(
                        label = stringResource(R.string.calib_metric_gap),
                        value = String.format(Locale.US, "%.3f", baseline.earGap)
                    )
                    MetricRow(
                        label = stringResource(R.string.calib_metric_blink_rate),
                        value = String.format(Locale.US, "%.1f / min", baseline.blinkRate)
                    )
                    if (baseline.responseLatencyMs > 0) {
                        MetricRow(
                            label = stringResource(R.string.calib_metric_reaction),
                            value = "${baseline.responseLatencyMs} ms"
                        )
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
                        text = stringResource(if (isSaving) R.string.calib_btn_saving else R.string.calib_btn_save),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                val failMessage = when (val reason = (validationResult as? CalibrationValidationResult.Rejected)?.reason) {
                    is CalibrationValidationResult.RejectionReason.NoClosedEyeFrames ->
                        stringResource(R.string.calib_fail_no_closed_frames)
                    is CalibrationValidationResult.RejectionReason.InsufficientOpenFrames ->
                        stringResource(R.string.calib_fail_insufficient_open_frames, reason.usableCount)
                    is CalibrationValidationResult.RejectionReason.InsufficientClosedFrames ->
                        stringResource(R.string.calib_fail_insufficient_closed_frames, reason.usableCount)
                    is CalibrationValidationResult.RejectionReason.GapTooSmall ->
                        stringResource(
                            R.string.calib_result_fail_sub,
                            String.format(Locale.US, "%.3f", reason.gap)
                        )
                    null ->
                        stringResource(
                            R.string.calib_result_fail_sub,
                            String.format(Locale.US, "%.3f", baseline.earGap)
                        )
                }

                Text(
                    text = stringResource(R.string.calib_result_fail_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5252)
                )
                Text(
                    text = failMessage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.calib_result_fail_desc),
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
                        text = stringResource(R.string.calib_btn_retry),
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
