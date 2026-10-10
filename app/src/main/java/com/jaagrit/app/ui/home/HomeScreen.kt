package com.jaagrit.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jaagrit.app.speech.VoiceReadinessChecker
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jaagrit.app.R
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.sms.SmsAvailability
import com.jaagrit.app.sms.SmsNotifier
import com.jaagrit.app.ui.components.LanguageSwitch
import com.jaagrit.app.ui.theme.HorizonAmber
import com.jaagrit.app.ui.theme.HorizonForest
import com.jaagrit.app.ui.theme.HorizonIvory
import com.jaagrit.app.ui.theme.HorizonLime
import com.jaagrit.app.ui.theme.HorizonMuted
import com.jaagrit.app.ui.theme.MuktaFontFamily
import java.util.Locale

@Composable
fun HomeScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onStartDrive: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baselineStore = remember { BaselineStore(context) }
    val baseline by baselineStore.baselineFlow.collectAsState(initial = null)
    val settingsStore = remember { SettingsStore(context) }
    val smsNotifier = remember { SmsNotifier(context, settingsStore) }
    val emergencyContact by settingsStore.emergencyContactFlow.collectAsState(initial = "")

    var showUncalibratedDialog by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        resumeTick++
    }

    val hasCamera = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    val smsAvailability = remember(resumeTick, emergencyContact) {
        smsNotifier.checkAvailability()
    }
    var isOfflineHindiVoiceAvailable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(resumeTick) {
        VoiceReadinessChecker.checkOfflineHindiAvailability(context) { available ->
            isOfflineHindiVoiceAvailable = available
        }
    }

    // Detect offline Hindi voice presence via TextToSpeech
    var isHindiVoiceReady by remember { mutableStateOf(false) }
    DisposableEffect(context, resumeTick) {
        var ttsInstance: TextToSpeech? = null
        try {
            ttsInstance = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val locale = Locale.forLanguageTag("hi-IN")
                    val langResult = ttsInstance?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    val hasLang = langResult != TextToSpeech.LANG_MISSING_DATA &&
                            langResult != TextToSpeech.LANG_NOT_SUPPORTED
                    val hasVoice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ttsInstance?.voices?.any { voice ->
                            (voice.locale.language == "hi" || voice.locale.toLanguageTag().startsWith("hi")) &&
                                    !voice.isNetworkConnectionRequired
                        } ?: hasLang
                    } else {
                        hasLang
                    }
                    isHindiVoiceReady = hasVoice
                }
            }
        } catch (_: Exception) {
            isHindiVoiceReady = false
        }
        onDispose {
            ttsInstance?.shutdown()
        }
    }

    val isCalibrated = baseline?.isValid == true
    val isSmsUnavailable = smsAvailability != SmsAvailability.READY || emergencyContact.isBlank()

    if (showUncalibratedDialog) {
        AlertDialog(
            onDismissRequest = { showUncalibratedDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_uncalibrated_title),
                    fontWeight = FontWeight.Bold,
                    fontFamily = MuktaFontFamily,
                    color = HorizonForest
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_uncalibrated_body),
                    fontSize = 14.sp,
                    fontFamily = MuktaFontFamily,
                    color = HorizonForest.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUncalibratedDialog = false
                        onNavigateToCalibration()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HorizonForest,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dialog_btn_calibrate_now),
                        fontFamily = MuktaFontFamily
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUncalibratedDialog = false
                        onStartDrive()
                    },
                    border = BorderStroke(1.dp, HorizonMuted),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = HorizonForest
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dialog_btn_start_defaults),
                        fontFamily = MuktaFontFamily
                    )
                }
            },
            containerColor = HorizonIvory
        )
    }

    Scaffold(
        containerColor = HorizonIvory,
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Wordmark on left, LanguageToggle and Settings on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "JAAGRIT",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MuktaFontFamily,
                        color = HorizonForest,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "जागृत",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = MuktaFontFamily,
                        color = HorizonForest.copy(alpha = 0.75f),
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageSwitch(
                        currentLanguage = currentLanguage,
                        onLanguageSelected = onLanguageChange
                    )

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HorizonMuted.copy(alpha = 0.55f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = HorizonForest,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2 & 3. Middle Content: Hero + Readiness Card + Fallback Note
            // Scrolls at font scale > 1.0 or on compact screens, while Start Drive stays pinned
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Hero Section: BrandMark + 2-line headline + short subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    EyeHorizonBrandMark(
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.home_headline),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MuktaFontFamily,
                        color = HorizonForest,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                        letterSpacing = 0.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.home_hero_subtitle),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = MuktaFontFamily,
                        color = HorizonForest.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }

                // Readiness Card with 4 compact 48dp rows
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, HorizonMuted)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: Camera
                        ReadinessRow(
                            iconRes = R.drawable.ic_camera,
                            label = stringResource(R.string.readiness_camera),
                            statusText = stringResource(if (hasCamera) R.string.status_ready else R.string.status_permission_needed),
                            isReady = hasCamera,
                            onClick = {
                                if (!hasCamera) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )

                        HorizontalDivider(
                            color = HorizonMuted.copy(alpha = 0.6f),
                            thickness = 1.dp
                        )

                        // Row 2: Calibration
                        ReadinessRow(
                            iconRes = R.drawable.ic_tune,
                            label = stringResource(R.string.readiness_calibration),
                            statusText = stringResource(if (isCalibrated) R.string.status_calibrated else R.string.status_not_calibrated),
                            isReady = isCalibrated,
                            onClick = onNavigateToCalibration
                        )

                        HorizontalDivider(
                            color = HorizonMuted.copy(alpha = 0.6f),
                            thickness = 1.dp
                        )

                        // Row 3: Emergency SMS (merged contact + SMS state)
                        val (smsText, smsReady) = when {
                            emergencyContact.isBlank() -> Pair(stringResource(R.string.status_contact_not_set), false)
                            smsAvailability == SmsAvailability.READY -> Pair(stringResource(R.string.status_ready), true)
                            smsAvailability == SmsAvailability.NO_SIM -> Pair(stringResource(R.string.status_no_sim), false)
                            smsAvailability == SmsAvailability.NO_PERMISSION -> Pair(stringResource(R.string.status_permission_needed), false)
                            smsAvailability == SmsAvailability.AIRPLANE_MODE -> Pair(stringResource(R.string.status_airplane_mode), false)
                            else -> Pair(stringResource(R.string.status_contact_not_set), false)
                        }

                        ReadinessRow(
                            iconRes = R.drawable.ic_sms,
                            label = stringResource(R.string.readiness_sms),
                            statusText = smsText,
                            isReady = smsReady,
                            onClick = onNavigateToSettings
                        )

                        HorizontalDivider(
                            color = HorizonMuted.copy(alpha = 0.6f),
                            thickness = 1.dp
                        )

                        // Row 4: Hindi voice (AUDIT-020)
                        val voiceReady = isOfflineHindiVoiceAvailable == true || isHindiVoiceReady
                        val voiceStatusText = when {
                            isOfflineHindiVoiceAvailable == true -> stringResource(R.string.status_ready)
                            isOfflineHindiVoiceAvailable == false -> stringResource(R.string.status_voice_missing)
                            isHindiVoiceReady -> stringResource(R.string.status_ready)
                            else -> stringResource(R.string.status_checking)
                        }

                        ReadinessRow(
                            iconRes = R.drawable.ic_volume_up,
                            label = stringResource(R.string.readiness_hindi_voice),
                            statusText = voiceStatusText,
                            isReady = voiceReady,
                            onClick = {
                                try {
                                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }

                if (isOfflineHindiVoiceAvailable == false) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = HorizonAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.notice_offline_voice_missing),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MuktaFontFamily,
                            color = Color(0xFF8D5B00),
                            lineHeight = 15.sp
                        )
                    }
                }

                // One amber line when Emergency SMS is unavailable (only when relevant)
                if (isSmsUnavailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = HorizonAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.notice_sms_unavailable_fallback),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = MuktaFontFamily,
                            color = Color(0xFF8D5B00),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 6. Pinned Bottom Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary "Start Drive" button: forest fill, white text, lime circular arrow, 64dp
                Button(
                    onClick = {
                        if (!isCalibrated) {
                            showUncalibratedDialog = true
                        } else {
                            onStartDrive()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HorizonForest,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(start = 24.dp, end = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.btn_start_drive),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MuktaFontFamily,
                            color = Color.White
                        )

                        Surface(
                            shape = CircleShape,
                            color = HorizonLime,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_forward),
                                    contentDescription = null,
                                    tint = HorizonForest,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Two equal text buttons: History & Recalibrate (48dp each)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToDashboard,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, HorizonForest),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HorizonForest
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_btn_open),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MuktaFontFamily,
                            color = HorizonForest
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HorizonMuted),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HorizonForest
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_history_short),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MuktaFontFamily,
                            color = HorizonForest
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToCalibration,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HorizonMuted),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HorizonForest
                        )
                    ) {
                        Text(
                            text = stringResource(
                                if (isCalibrated) R.string.btn_recalibrate_short else R.string.btn_calibrate_short
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MuktaFontFamily,
                            color = HorizonForest
                        )
                    }
                }

                // Offline lock line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = HorizonForest.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.home_footer_offline_lock),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = MuktaFontFamily,
                        color = HorizonForest.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Single 48dp readiness row inside the Readiness Card.
 * Uses icon + label on left, status icon + text on right.
 * Entire row is a 48dp touch target and clickable if action is needed.
 */
@Composable
private fun ReadinessRow(
    iconRes: Int,
    label: String,
    statusText: String,
    isReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Row icon + label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = HorizonForest,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MuktaFontFamily,
                color = HorizonForest
            )
        }

        // Right: Status icon + status text
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(if (isReady) R.drawable.ic_check else R.drawable.ic_warning),
                contentDescription = null,
                tint = if (isReady) HorizonForest else HorizonAmber,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MuktaFontFamily,
                color = if (isReady) HorizonForest else Color(0xFF8D5B00)
            )
        }
    }
}

/**
 * Bespoke Canvas-drawn eye-and-horizon BrandMark (~120dp).
 * Harmonious geometric composition of the eye contour (vigilance)
 * meeting the highway perspective and rising horizon in Horizon tokens.
 */
@Composable
fun EyeHorizonBrandMark(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Soft circular base dish
        drawCircle(
            color = HorizonMuted,
            radius = w * 0.46f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // 2. Horizon line (road meeting the sky)
        val horizonY = h * 0.52f
        drawLine(
            color = HorizonForest,
            start = Offset(w * 0.16f, horizonY),
            end = Offset(w * 0.84f, horizonY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Road perspective vanishing into the horizon
        val roadPath = Path().apply {
            moveTo(w * 0.38f, h * 0.84f)
            lineTo(w * 0.48f, horizonY)
            lineTo(w * 0.52f, horizonY)
            lineTo(w * 0.62f, h * 0.84f)
            close()
        }
        drawPath(
            path = roadPath,
            color = HorizonForest.copy(alpha = 0.10f)
        )

        // Lime centerline on the highway
        drawLine(
            color = HorizonLime,
            start = Offset(w * 0.5f, h * 0.82f),
            end = Offset(w * 0.5f, horizonY + 5.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Eye Contour: Upper and lower eyelid arcs
        val leftCornerX = w * 0.20f
        val rightCornerX = w * 0.80f
        val midY = h * 0.48f

        val upperLid = Path().apply {
            moveTo(leftCornerX, midY)
            quadraticTo(
                w * 0.50f, h * 0.20f,
                rightCornerX, midY
            )
        }
        drawPath(
            path = upperLid,
            color = HorizonForest,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        val lowerLid = Path().apply {
            moveTo(leftCornerX, midY)
            quadraticTo(
                w * 0.50f, h * 0.76f,
                rightCornerX, midY
            )
        }
        drawPath(
            path = lowerLid,
            color = HorizonForest,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 4. Iris (Forest vigilance)
        val irisRadius = w * 0.165f
        drawCircle(
            color = HorizonForest,
            radius = irisRadius,
            center = Offset(w * 0.5f, midY)
        )

        // 5. Center Pupil (Vibrant Lime Alert Core)
        val pupilRadius = w * 0.082f
        drawCircle(
            color = HorizonLime,
            radius = pupilRadius,
            center = Offset(w * 0.5f, midY)
        )

        // 6. Specular catchlight
        drawCircle(
            color = Color.White,
            radius = w * 0.024f,
            center = Offset(w * 0.53f, midY - w * 0.032f)
        )
    }
}
