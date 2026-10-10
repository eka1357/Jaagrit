package com.jaagrit.app.ui.monitoring

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.camera.MonitoringPipeline
import com.jaagrit.app.camera.MonitoringUiState
import com.jaagrit.app.engine.DriverState

@Composable
fun MonitoringScreen(
    onEndDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (!hasCameraPermission) {
            PreDrivePermissionsRationale(
                onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions)
                },
                onBack = onEndDrive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            ActiveMonitoringContent(
                onEndDrive = onEndDrive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ActiveMonitoringContent(
    onEndDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Pipeline decoupled from UI (AGENTS.md Rule 10, D15)
    val pipeline = remember { MonitoringPipeline(context) }
    val uiState by pipeline.uiState.collectAsState()

    var showDebugPanel by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            pipeline.release()
        }
    }

    // State-tailored theme colors
    val themeColor = when (uiState.state) {
        DriverState.NORMAL -> Color(0xFF2E7D32)     // Rich Green
        DriverState.CAUTION -> Color(0xFFF57F17)    // Amber
        DriverState.FATIGUED -> Color(0xFFE65100)   // Orange
        DriverState.CRITICAL -> Color(0xFFD32F2F)   // Red
        DriverState.FACE_LOST -> Color(0xFF757575)  // Grey
        DriverState.CALIBRATING -> Color(0xFF1976D2)// Blue
    }

    val stateWord = when (uiState.state) {
        DriverState.NORMAL -> "अलर्ट • Focused"
        DriverState.CAUTION -> "थकान बढ़ रही है • Caution"
        DriverState.FATIGUED -> "जवाब दो • Fatigued"
        DriverState.CRITICAL -> "जागो! • DROWSY"
        DriverState.FACE_LOST -> "चेहरा नहीं दिख रहा • Face Lost"
        DriverState.CALIBRATING -> "कैलिब्रेशन • Calibrating"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 1. Top Header with Privacy Badge and Long-press for Telemetry
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
                // App Title
                Text(
                    text = "JAAGRIT",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Privacy Badge (Rule 5 & PRODUCT.md)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔒",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ON-DEVICE • OFFLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Text(
                text = if (showDebugPanel) "Telemetry Active (long-press title to close)" else "Driving Alertness Monitor",
                fontSize = 12.sp,
                color = if (showDebugPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 2. Middle Section: Camera preview + Giant Alertness Number + Metrics
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            // Small Camera Preview (CAM-1)
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 2.5.dp,
                        color = if (uiState.faceFound) Color(0xFF2E7D32) else Color(0xFF757575),
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
                        .padding(bottom = 8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (uiState.faceFound) "FACE VISIBLE" else "NO FACE",
                        color = if (uiState.faceFound) Color(0xFF81C784) else Color(0xFFE57373),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Giant Alertness Number (UI-1, PRODUCT.md)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${uiState.alertness}",
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
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

            // State Word Banner
            Surface(
                shape = RoundedCornerShape(50),
                color = themeColor.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, themeColor.copy(alpha = 0.6f)),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = stateWord,
                    color = themeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                )
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
                            text = "Drive Time",
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
                            text = "Alerts",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.alertCount}",
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
                        Text(
                            text = "⚠️ Recalibration Suggested",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFE65100)
                        )
                        Text(
                            text = "${uiState.falseAlertCount} alerts dismissed in 10 minutes. Calibrating again can adapt to changing light or posture.",
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "DEBUG TELEMETRY & FLAGS (LIVE)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val frame = uiState.faceFrame
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "EAR L: ${"%.3f".format(frame.earL)} | R: ${"%.3f".format(frame.earR)}", fontSize = 12.sp)
                            Text(text = "AVG: ${"%.3f".format(frame.earAvg)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Mouth (MAR): ${"%.3f".format(frame.mar)}", fontSize = 12.sp)
                            Text(text = "Pitch: ${"%+.1f°".format(frame.pitchDeg)} (down+)", fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Inference: ${uiState.inferenceTimeMs} ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(text = "FPS: ${"%.1f".format(uiState.fps)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }

                        // Flags & M6 State (D9, LAD-6, LAD-7)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEMO_TIMERS: ${if (uiState.demoTimers) "ON (5s/8s)" else "OFF (10s/20s)"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (uiState.demoTimers) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant
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
                    }
                }
            }
        }

        // 3. End Drive Button
        Button(
            onClick = onEndDrive,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = "End Drive",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError
            )
        }
    }

        // Full-screen flashing red state overlay for Critical Alert (UI-2, LAD-1, AUDIT-006)
        if (uiState.isRedFlashActive) {
            RedAlertFullScreen(
                uiState = uiState,
                onImAwake = { pipeline.onImAwake() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Full-screen flashing red state for Level 3 Critical Alert (UI-2, LAD-1).
 * Features high-urgency flashing red background, clear Devanagari/English warnings,
 * and a giant prominent "I'M AWAKE" button that resets the alert.
 */
@Composable
private fun RedAlertFullScreen(
    uiState: MonitoringUiState,
    onImAwake: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Flashing red animation
    val infiniteTransition = rememberInfiniteTransition(label = "RedAlertFlash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FlashAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFD32F2F).copy(alpha = flashAlpha))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Warning Icon and Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "जागो! WAKE UP!",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "गाड़ी धीरे करो और आँखें खोलो!",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Visible L5 Countdown or In-App SMS Status (LAD-4, D6)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                if (uiState.l5CountdownSeconds != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD54F)),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "EMERGENCY SMS IN",
                                color = Color(0xFFFFD54F),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "${uiState.l5CountdownSeconds}s",
                                color = Color(0xFFFFD54F),
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Tap I'M AWAKE to cancel SMS",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (uiState.smsNotificationStatus != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = uiState.smsNotificationStatus,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Primary Emergency Action: Giant "I'M AWAKE" Button (UI-2)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 30.dp)
            ) {
                Button(
                    onClick = onImAwake,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFFB71C1C)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
                ) {
                    Text(
                        text = "I'M AWAKE",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Tap button to stop alarm and cancel SMS",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PreDrivePermissionsRationale(
    onRequestPermissions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(
                text = "Pre-Drive Permissions Needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Jaagrit requires camera access to monitor driver alertness, and SMS + GPS to notify emergency contacts if you become unresponsive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Pre-Drive Checklist & Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Camera frames processed in-memory only (never stored)\n" +
                            "• Zero network/internet usage (100% offline)\n" +
                            "• Emergency SMS uses cellular radio directly\n" +
                            "• ⚠️ Pre-drive check: SMS needs mobile signal — airplane mode blocks it (D6)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Grant Pre-Drive Permissions", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = "Back to Home", fontSize = 16.sp)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
