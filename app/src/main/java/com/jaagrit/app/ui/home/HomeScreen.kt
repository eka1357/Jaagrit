package com.jaagrit.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
import com.jaagrit.app.ui.components.JaagritBrandHeader
import com.jaagrit.app.ui.components.LanguageSwitch
import java.util.Locale

@Composable
fun HomeScreen(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onStartDrive: () -> Unit,
    onNavigateToCalibration: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
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

    val hasCamera = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val hasLocation = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val smsAvailability = remember(resumeTick, emergencyContact) {
        smsNotifier.checkAvailability()
    }
    val voiceStatus = remember(resumeTick) {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            VoiceStatus.PERMISSION_NEEDED
        } else {
            val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } else {
                SpeechRecognizer.isRecognitionAvailable(context)
            }
            if (onDeviceAvailable) {
                VoiceStatus.READY
            } else {
                VoiceStatus.OFFLINE_PACK_MISSING
            }
        }
    }

    if (showUncalibratedDialog) {
        AlertDialog(
            onDismissRequest = { showUncalibratedDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_uncalibrated_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_uncalibrated_body),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUncalibratedDialog = false
                        onNavigateToCalibration()
                    }
                ) {
                    Text(stringResource(R.string.dialog_btn_calibrate_now))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showUncalibratedDialog = false
                        onStartDrive()
                    }
                ) {
                    Text(stringResource(R.string.dialog_btn_start_defaults))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section: History, Brand, Language Toggle, Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History Icon
                    IconButton(onClick = onNavigateToHistory) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = stringResource(R.string.history_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                    }

                    // Brand: JAAGRIT with जागृत as a small separate text below
                    JaagritBrandHeader(
                        wordmarkSize = 36.sp,
                        subSize = 16.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Language switch toggle
                        LanguageSwitch(
                            currentLanguage = currentLanguage,
                            onLanguageSelected = onLanguageChange
                        )

                        // Settings Icon
                        IconButton(onClick = onNavigateToSettings) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.home_subtitle),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Calibration Status Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (baseline?.isValid == true) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (baseline?.isValid == true) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(8.dp)
                        ) {}

                        val chipText = if (baseline?.isValid == true) {
                            stringResource(
                                R.string.profile_calibrated,
                                String.format(Locale.US, "%.3f", baseline!!.threshold)
                            )
                        } else {
                            stringResource(R.string.profile_default)
                        }

                        Text(
                            text = chipText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (baseline?.isValid == true) Color(0xFF1B5E20) else Color(0xFFBF360C)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Drive Readiness Checklist Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.checklist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Line 1: Camera
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
                                text = stringResource(R.string.checklist_camera),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(if (hasCamera) R.string.status_ready else R.string.status_permission_needed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasCamera) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 2: Emergency SMS
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
                                text = stringResource(R.string.checklist_sms),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val smsStatusText = when (smsAvailability) {
                            SmsAvailability.READY -> stringResource(R.string.status_ready)
                            SmsAvailability.NO_SIM -> stringResource(R.string.status_no_sim)
                            SmsAvailability.NO_PERMISSION -> stringResource(R.string.status_permission_needed)
                            SmsAvailability.AIRPLANE_MODE -> stringResource(R.string.status_airplane_mode)
                            SmsAvailability.NO_CONTACT -> stringResource(R.string.status_contact_not_set)
                        }

                        Text(
                            text = smsStatusText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (smsAvailability == SmsAvailability.READY) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 3: Location (GPS)
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
                                text = stringResource(R.string.checklist_location),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(if (hasLocation) R.string.status_ready else R.string.status_permission_needed),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasLocation) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // Line 4: Voice Commands (M8)
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
                                text = stringResource(R.string.checklist_voice),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val (voiceStatusText, voiceColor) = when (voiceStatus) {
                            VoiceStatus.READY -> Pair(stringResource(R.string.status_ready), Color(0xFF2E7D32))
                            VoiceStatus.PERMISSION_NEEDED -> Pair(stringResource(R.string.status_permission_needed), Color(0xFFE65100))
                            VoiceStatus.OFFLINE_PACK_MISSING -> Pair(stringResource(R.string.status_offline_pack_missing), Color(0xFFE65100))
                        }

                        Text(
                            text = voiceStatusText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = voiceColor
                        )
                    }

                    // Line 5: Emergency Contact
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
                                painter = painterResource(R.drawable.ic_lock),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.checklist_contact),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (emergencyContact.isNotBlank()) SettingsStore.maskPhoneNumber(emergencyContact) else stringResource(R.string.status_contact_not_set),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (emergencyContact.isNotBlank()) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = stringResource(R.string.notice_sms_cellular),
                            fontSize = 11.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = stringResource(R.string.notice_offline_guarantee),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Start Drive Button (Checks calibration first)
                Button(
                    onClick = {
                        if (baseline?.isValid == true) {
                            onStartDrive()
                        } else {
                            showUncalibratedDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_start_drive),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Calibrate / Recalibrate Button
                OutlinedButton(
                    onClick = onNavigateToCalibration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(if (baseline?.isValid == true) R.string.btn_recalibrate else R.string.btn_calibrate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Trip History Button
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.btn_trip_history),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private enum class VoiceStatus {
    READY,
    PERMISSION_NEEDED,
    OFFLINE_PACK_MISSING
}
