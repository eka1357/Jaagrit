package com.jaagrit.app.ui.monitoring

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.R
import com.jaagrit.app.camera.MonitoringPipeline
import com.jaagrit.app.engine.DriverState
import com.jaagrit.app.sms.SmsNotifier
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.speech.SpeechReadiness
import com.jaagrit.app.speech.SpeechReadinessChecker
import com.jaagrit.app.speech.DriverIntent
import com.jaagrit.app.speech.RecognizerInput
import com.jaagrit.app.speech.SpeechInput
import com.jaagrit.app.ui.components.JaagritBrandHeader
import java.util.Locale

@Composable
fun MonitoringScreen(
    onEndDrive: () -> Unit,
    onNavigateToCalibration: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baselineStore = remember { BaselineStore(context) }

    // Ensure driver never silently runs on uncalibrated or outdated baseline (AUDIT-022)
    LaunchedEffect(Unit) {
        if (!baselineStore.hasSavedBaselineWithCurrentSchema()) {
            onNavigateToCalibration()
        }
    }

    // Keep screen awake and force max brightness while monitoring (UI-4)
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val params = window?.attributes
        params?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window?.attributes = params

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val restoreParams = window?.attributes
            restoreParams?.screenBrightness = originalBrightness
            window?.attributes = restoreParams
        }
    }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var preDriveDismissed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        cameraGranted = results[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        audioGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        smsGranted = results[Manifest.permission.SEND_SMS] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted && audioGranted && smsGranted && locationGranted) {
            preDriveDismissed = true
        }
    }

    // Lock monitoring screen to portrait (AUDIT-015)
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val showPreDrive = (!cameraGranted || !audioGranted || !smsGranted || !locationGranted) && !preDriveDismissed

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (showPreDrive) {
            PreDrivePermissionsRationale(
                cameraGranted = cameraGranted,
                audioGranted = audioGranted,
                smsGranted = smsGranted,
                locationGranted = locationGranted,
                onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions)
                },
                onContinueAnyway = {
                    if (cameraGranted) {
                        preDriveDismissed = true
                    }
                },
                onBack = onEndDrive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            ActiveMonitoringContent(
                onEndDrive = onEndDrive,
                onNavigateToCalibration = onNavigateToCalibration,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveMonitoringContent(
    onEndDrive: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pipeline decoupled from UI (AGENTS.md Rule 10, D15)
    val pipeline = remember { MonitoringPipeline(context) }
    val uiState by pipeline.uiState.collectAsState()

    // Speech input lifecycle bound to ActiveMonitoringContent
    val speechInput = remember { RecognizerInput(context) }
    DisposableEffect(speechInput) {
        onDispose {
            speechInput.destroy()
        }
    }

    val settingsStore = remember { SettingsStore(context) }
    var speechReadiness by remember { mutableStateOf<SpeechReadiness?>(null) }
    var showVoiceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val appLanguage = settingsStore.getAppLanguage()
        SpeechReadinessChecker.checkReadiness(context, appLanguage) { readiness ->
            speechReadiness = readiness
        }
    }

    // Dismiss bottom sheet immediately when alert occurs (mic isolation & safety path)
    LaunchedEffect(uiState.isAlert) {
        if (uiState.isAlert) {
            showVoiceSheet = false
        }
    }

    var showDebugPanel by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    pipeline.onAppBackgrounded()
                }
                Lifecycle.Event.ON_START -> {
                    pipeline.onAppForegrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pipeline.release()
        }
    }

    val isAlert = uiState.isRedFlashActive

    // Flashing opaque red animation for active alerts (no bleed-through)
    val infiniteTransition = rememberInfiniteTransition(label = "RedAlertFlash")
    val flashPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RedPulse"
    )

    val backgroundColor = if (isAlert) {
        if (flashPulse > 0.5f) Color(0xFFB71C1C) else Color(0xFFD32F2F)
    } else {
        MaterialTheme.colorScheme.background
    }

    // State-tailored theme colors for normal mode
    val themeColor = when (uiState.state) {
        DriverState.NORMAL -> Color(0xFF2E7D32)     // Rich Green
        DriverState.CAUTION -> Color(0xFFF57F17)    // Amber
        DriverState.FATIGUED -> Color(0xFFE65100)   // Orange
        DriverState.CRITICAL -> Color(0xFFD32F2F)   // Red
        DriverState.FACE_LOST -> Color(0xFF757575)  // Grey
        DriverState.CALIBRATING -> Color(0xFF1976D2)// Blue
    }

    // State word from string resources (Rule 8: single word/phrase + icon per language)
    val stateRes = when (uiState.state) {
        DriverState.NORMAL -> R.string.state_normal
        DriverState.CAUTION -> R.string.state_caution
        DriverState.FATIGUED -> R.string.state_fatigued
        DriverState.CRITICAL -> R.string.state_critical
        DriverState.FACE_LOST -> R.string.state_face_lost
        DriverState.CALIBRATING -> R.string.state_calibrating
    }

    val stateIconRes = when (uiState.state) {
        DriverState.NORMAL -> R.drawable.ic_check
        DriverState.CAUTION -> R.drawable.ic_warning
        DriverState.FATIGUED -> R.drawable.ic_warning
        DriverState.CRITICAL -> R.drawable.ic_warning
        DriverState.FACE_LOST -> R.drawable.ic_camera
        DriverState.CALIBRATING -> R.drawable.ic_history
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Header
        if (isAlert) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.critical_banner_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                showDebugPanel = !showDebugPanel
                            }
                        )
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    JaagritBrandHeader(
                        wordmarkSize = 22.sp,
                        subSize = 12.sp,
                        horizontalAlignment = Alignment.Start
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lock),
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = stringResource(R.string.badge_offline),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(if (showDebugPanel) R.string.telemetry_active_hint else R.string.monitoring_title),
                    fontSize = 12.sp,
                    color = if (showDebugPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 2. Middle Section: Camera preview + Alertness + State + Stats
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            // Camera Preview (doesn't overlap text)
            Box(
                modifier = Modifier
                    .size(width = 140.dp, height = 175.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 2.5.dp,
                        color = if (isAlert) Color.White else (if (uiState.faceFound) Color(0xFF2E7D32) else Color(0xFF757575)),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            pipeline.start(
                                lifecycleOwner = lifecycleOwner,
                                previewView = this
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Face status pill on preview
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(if (uiState.faceFound) R.string.face_visible else R.string.no_face),
                        color = if (uiState.faceFound) Color(0xFF81C784) else Color(0xFFE57373),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isAlert) {
                // Critical Alert Mode State: Clean single headline & instruction (Rule 8)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.critical_headline),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.critical_instruction),
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Score in Alert Mode: White text (always Latin digits)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%d", uiState.alertness),
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 68.sp
                    )
                    Text(
                        text = "/100",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                    )
                }

                // L5 Status Line
                val l5StatusText = SmsNotifier.formatL5StatusLabel(
                    level = uiState.level,
                    l5CountdownSeconds = uiState.l5CountdownSeconds,
                    smsNotificationStatus = uiState.smsNotificationStatus,
                    smsAvailability = uiState.smsAvailability
                )
                if (l5StatusText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFD54F)),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = l5StatusText,
                            color = Color(0xFFFFD54F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                // Giant Alertness Number (Normal Mode, always Latin digits)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%d", uiState.alertness),
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor,
                        lineHeight = 76.sp
                    )
                    Text(
                        text = "/100",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                }

                // State Word Banner with Icon (Rule 8: one word/phrase plus an icon)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = themeColor.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, themeColor.copy(alpha = 0.6f)),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(stateIconRes),
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(stateRes),
                            color = themeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                }

                // Reasons summary (if degraded)
                if (uiState.reasons.isNotEmpty() && uiState.state != DriverState.NORMAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.reasons.firstOrNull() ?: "",
                        fontSize = 13.sp,
                        color = themeColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minimal Driver View Stats: Drive Time & Alert Count
                Card(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.stat_drive_time),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.driveTimeFormatted,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(30.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.stat_alerts),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%d", uiState.alertCount),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.alertCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Recalibration Suggestion (LAD-6: > 3 alerts dismissed in 10 min)
                if (uiState.suggestRecalibration) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFA000))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_warning),
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.recalib_suggested_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFFE65100)
                                )
                            }
                            Text(
                                text = stringResource(R.string.recalib_suggested_body, uiState.falseAlertCount),
                                fontSize = 12.sp,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }
                }

                // Debug Telemetry Panel (shown on long-pressing title)
                AnimatedVisibility(visible = showDebugPanel) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Engine & Camera Telemetry",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "FPS: ${String.format(Locale.US, "%.1f", uiState.fps)}", fontSize = 12.sp)
                                Text(text = "Inference: ${uiState.inferenceTimeMs}ms", fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "EAR L/R: ${String.format(Locale.US, "%.3f", uiState.faceFrame.earL)} / ${String.format(Locale.US, "%.3f", uiState.faceFrame.earR)}", fontSize = 12.sp)
                                Text(text = "MAR: ${String.format(Locale.US, "%.3f", uiState.faceFrame.mar)}", fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Pitch: ${String.format(Locale.US, "%.1f", uiState.faceFrame.pitchDeg)}°", fontSize = 12.sp)
                                Text(text = "State: ${uiState.state.name} (${uiState.level.name})", fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DEMO_TIMERS: ${if (uiState.demoTimers) "ON (3s/5s)" else "OFF (10s/20s)"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.demoTimers) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { pipeline.toggleDemoTimers() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(text = if (uiState.demoTimers) "Disable" else "Enable", fontSize = 11.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "QUICK_CALIB: ${if (uiState.quickCalibration) "ON (5s/2s)" else "OFF (10s/3s)"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.quickCalibration) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { pipeline.toggleQuickCalibration() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(text = if (uiState.quickCalibration) "Disable" else "Enable", fontSize = 11.sp)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "False Alerts (10m): ${uiState.falseAlertCount}", fontSize = 12.sp)
                                Text(text = "L5 Countdown: ${uiState.l5CountdownSeconds?.let { "${it}s" } ?: "N/A"}", fontSize = 12.sp)
                            }
                            if (uiState.smsNotificationStatus != null) {
                                Text(
                                    text = "SMS: ${uiState.smsNotificationStatus}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Voice: ${uiState.lastRecognizedText ?: "None"}", fontSize = 12.sp)
                                Text(text = "Intent: ${uiState.lastMatchedIntent?.name ?: "None"}", fontSize = 12.sp)
                            }
                            if (uiState.recognitionLatencyMs != null) {
                                Text(
                                    text = "Voice Latency: ${uiState.recognitionLatencyMs}ms",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Bottom Action Section
        if (isAlert) {
            // Alert mode: "I'M AWAKE" button at bottom, End Drive and Voice/Button controls are HIDDEN!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { pipeline.onImAwake() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB71C1C)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_im_awake),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.im_awake_hint),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            // Normal mode: Reserved voice answer banner + bottom bar (48dp mic button + End Drive button)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reserved 44dp row for 4-second voice answer banner so Drive Time & Alert stats are NEVER covered or shifted
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.voiceAnswerText != null,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        uiState.voiceAnswerText?.let { answerText ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = answerText,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom bar: 48dp mic button (opens bottom sheet) + End Drive button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small 48dp mic icon button (M8: hidden during alerts, opens sheet in normal mode)
                    Surface(
                        onClick = { showVoiceSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = if (uiState.isListening) Color(0xFFC62828) else MaterialTheme.colorScheme.primaryContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.isListening) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = stringResource(R.string.btn_open_voice),
                                tint = if (uiState.isListening) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // End Drive button
                    Button(
                        onClick = onEndDrive,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_end_drive),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }

    // Bottom sheet for Voice Commands & Fallback Quick Buttons
    if (showVoiceSheet && !isAlert) {
        ModalBottomSheet(
            onDismissRequest = { showVoiceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            VoiceCommandsBottomSheetContent(
                uiState = uiState,
                speechReadiness = speechReadiness,
                onPushToTalk = {
                    if (uiState.isListening) {
                        pipeline.cancelPushToTalk(speechInput)
                    } else {
                        pipeline.startPushToTalk(speechInput, onNavigateToCalibration)
                    }
                },
                onExecuteIntent = { intent ->
                    pipeline.executeIntent(intent, onNavigateToCalibration)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun VoiceCommandsBottomSheetContent(
    uiState: com.jaagrit.app.camera.MonitoringUiState,
    speechReadiness: SpeechReadiness?,
    onPushToTalk: () -> Unit,
    onExecuteIntent: (DriverIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSpeechAvailable = speechReadiness == SpeechReadiness.READY

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sheet Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.voice_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Voice answer banner inside bottom sheet (if active)
        AnimatedVisibility(visible = uiState.voiceAnswerText != null) {
            uiState.voiceAnswerText?.let { answerText ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = answerText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Push-to-Talk "Ask Jaagrit" Button (or disabled with one-line reason)
        Button(
            onClick = onPushToTalk,
            enabled = isSpeechAvailable,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isListening) Color(0xFFC62828) else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (uiState.isListening) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.outline
            )
        ) {
            if (isSpeechAvailable) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(if (uiState.isListening) R.string.listening_hint else R.string.btn_ask_jaagrit),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = stringResource(R.string.btn_ask_jaagrit),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = when (speechReadiness) {
                                SpeechReadiness.LANG_PACK_MISSING -> stringResource(R.string.btn_speech_disabled_lang_pack)
                                SpeechReadiness.UNAVAILABLE -> stringResource(R.string.btn_speech_disabled_unavailable)
                                else -> stringResource(R.string.btn_speech_disabled_unavailable)
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 4 Fallback Intent Buttons (each >= 48dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onExecuteIntent(DriverIntent.DRIVE_TIME) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cmd_drive_time),
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }

            OutlinedButton(
                onClick = { onExecuteIntent(DriverIntent.ALERTNESS) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cmd_alertness),
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }

            OutlinedButton(
                onClick = { onExecuteIntent(DriverIntent.ALERT_COUNT) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cmd_alert_count),
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }

            OutlinedButton(
                onClick = { onExecuteIntent(DriverIntent.LAST_ALERT) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cmd_last_alert),
                    fontSize = 11.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PreDrivePermissionsRationale(
    cameraGranted: Boolean,
    audioGranted: Boolean,
    smsGranted: Boolean,
    locationGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onContinueAnyway: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.predrive_perm_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.predrive_perm_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Line 1: Camera
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.perm_camera_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = stringResource(if (cameraGranted) R.string.perm_status_granted else R.string.perm_status_required),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (cameraGranted) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    Text(
                        text = stringResource(R.string.perm_camera_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Line 2: SMS
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sms),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.perm_sms_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = stringResource(if (smsGranted) R.string.perm_status_granted else R.string.perm_status_not_granted),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (smsGranted) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                    Text(
                        text = stringResource(R.string.perm_sms_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Line 3: Location
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_location),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.perm_location_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = stringResource(if (locationGranted) R.string.perm_status_granted else R.string.perm_status_not_granted),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (locationGranted) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                    Text(
                        text = stringResource(R.string.perm_location_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Line 4: Microphone (M8)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.perm_audio_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = stringResource(if (audioGranted) R.string.perm_status_granted else R.string.perm_status_not_granted),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (audioGranted) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                    Text(
                        text = stringResource(R.string.perm_audio_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_grant_permissions),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (cameraGranted) {
                Button(
                    onClick = onContinueAnyway,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_start_anyway),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_back_home),
                    fontSize = 16.sp
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
