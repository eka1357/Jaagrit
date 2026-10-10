package com.jaagrit.app.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaagrit.app.R
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.JaagritDatabase
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.data.repository.TripRepository
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import com.jaagrit.app.report.ReportExportResult
import com.jaagrit.app.report.ReportExporter
import com.jaagrit.app.ui.theme.HorizonAmber
import com.jaagrit.app.ui.theme.HorizonForest
import com.jaagrit.app.ui.theme.HorizonLime
import com.jaagrit.app.ui.theme.HorizonMuted
import com.jaagrit.app.ui.theme.MuktaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard & Trip Report screen (Milestone M10, OFF-1 to OFF-4, UI-3, D7).
 * Mirror-ready for laptop display via Office Kit with large text, responsive landscape layout,
 * Canvas-drawn alertness-over-time curve, alert timeline, trip safety score, driver profile,
 * PDF/JSON export, clipboard copy, and large reachable Start/Stop controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    tripId: Long? = null,
    onBack: () -> Unit,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val db = remember { JaagritDatabase.getDatabase(context) }
    val repository = remember { TripRepository(db.tripDao(), db.alertDao()) }
    val baselineStore = remember { BaselineStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val reportExporter = remember { ReportExporter(context) }

    val trips by repository.getAllTrips().collectAsState(initial = emptyList())
    val baseline by baselineStore.baselineFlow.collectAsState(initial = null)
    val driverName by settingsStore.driverNameFlow.collectAsState(initial = "Driver")
    val emergencyContact by settingsStore.emergencyContactFlow.collectAsState(initial = "")

    // Selected trip: either explicitly passed, or active trip, or most recent trip
    val selectedTrip = remember(trips, tripId) {
        if (tripId != null) {
            trips.firstOrNull { it.id == tripId }
        } else {
            trips.firstOrNull { it.isActive } ?: trips.firstOrNull()
        }
    }

    val samplesFlow = remember(selectedTrip?.id) {
        selectedTrip?.let { repository.getSamplesForTrip(it.id) }
    }
    val samples by (samplesFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<AlertSample>()) })

    val eventsFlow = remember(selectedTrip?.id) {
        selectedTrip?.let { repository.getEventsForTrip(it.id) }
    }
    val events by (eventsFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<AlertEvent>()) })

    var exportResult by remember { mutableStateOf<ReportExportResult?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val safetyScore = remember(selectedTrip) {
        if (selectedTrip == null) 100
        else Config.calculateTripSafetyScore(
            avgAlertness = selectedTrip.avgAlertness,
            alertCount = selectedTrip.alertCount,
            criticalCount = selectedTrip.criticalCount
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLandscape) 22.sp else 19.sp,
                                fontFamily = MuktaFontFamily,
                                color = HorizonForest
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = HorizonForest.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "OFFICE KIT READY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HorizonForest,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (selectedTrip != null) {
                                "Trip #${selectedTrip.id} • ${formatDate(selectedTrip.startMs)}"
                            } else {
                                stringResource(R.string.dashboard_subtitle)
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                            tint = HorizonForest,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (selectedTrip == null) {
            EmptyDashboardView(
                onStartDrive = onStartDrive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            if (isLandscape) {
                // Landscape: 2-column widescreen layout tailored for laptop screen mirroring
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Driver profile, Trip Safety Score, Big Controls, Export Actions
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TripSafetyScoreCard(
                            trip = selectedTrip,
                            safetyScore = safetyScore,
                            isLandscape = true
                        )

                        DriverProfileCard(
                            driverName = driverName,
                            emergencyContact = emergencyContact,
                            baseline = baseline
                        )

                        // Large Start / Stop Controls (OFF-5, D7)
                        LargeDriveControlButtons(
                            isActiveDrive = selectedTrip.isActive,
                            onStartDrive = onStartDrive,
                            onStopDrive = onStopDrive
                        )

                        // Action Buttons: Copy Last Alert & Export PDF/JSON
                        ActionControlsCard(
                            trip = selectedTrip,
                            events = events,
                            safetyScore = safetyScore,
                            driverName = driverName,
                            isExporting = isExporting,
                            exportResult = exportResult,
                            onCopyAlert = {
                                copyLastAlertToClipboard(
                                    context = context,
                                    tripId = selectedTrip.id,
                                    events = events,
                                    safetyScore = safetyScore,
                                    driverName = driverName
                                )
                            },
                            onExportReport = {
                                coroutineScope.launch {
                                    isExporting = true
                                    val result = withContext(Dispatchers.IO) {
                                        reportExporter.exportReport(
                                            trip = selectedTrip,
                                            samples = samples,
                                            events = events,
                                            baseline = baseline,
                                            driverName = driverName,
                                            emergencyContact = emergencyContact
                                        )
                                    }
                                    exportResult = result
                                    isExporting = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.dashboard_export_success, result.pdfFile.name),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onSharePdf = { result ->
                                sharePdfReport(context, result.pdfFile)
                            },
                            onOpenPdf = { result ->
                                openPdfReport(context, result.pdfFile)
                            }
                        )

                        HonestLimitsCard()
                    }

                    // Right Pane: Canvas Alertness Line Graph + Alert Timeline
                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Canvas Line Graph
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.54f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_graph_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = MuktaFontFamily,
                                        color = HorizonForest
                                    )
                                    Text(
                                        text = "0–100 Scale • 5s Samples",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                AlertnessLineGraph(
                                    trip = selectedTrip,
                                    samples = samples,
                                    events = events,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }

                        // Alert Intervention Timeline
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.46f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AlertTimelineSection(
                                events = events,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            } else {
                // Portrait Layout
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
                ) {
                    // 1. Safety Score Card
                    item {
                        TripSafetyScoreCard(
                            trip = selectedTrip,
                            safetyScore = safetyScore,
                            isLandscape = false
                        )
                    }

                    // 2. Alertness-Over-Time Canvas Line Graph
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.dashboard_graph_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        fontFamily = MuktaFontFamily,
                                        color = HorizonForest
                                    )
                                    Text(
                                        text = "${samples.size} samples",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                AlertnessLineGraph(
                                    trip = selectedTrip,
                                    samples = samples,
                                    events = events,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                )
                            }
                        }
                    }

                    // 3. Driver Profile
                    item {
                        DriverProfileCard(
                            driverName = driverName,
                            emergencyContact = emergencyContact,
                            baseline = baseline
                        )
                    }

                    // 4. Large Start / Stop Controls
                    item {
                        LargeDriveControlButtons(
                            isActiveDrive = selectedTrip.isActive,
                            onStartDrive = onStartDrive,
                            onStopDrive = onStopDrive
                        )
                    }

                    // 5. Actions Card (Copy Last Alert & Export PDF/JSON)
                    item {
                        ActionControlsCard(
                            trip = selectedTrip,
                            events = events,
                            safetyScore = safetyScore,
                            driverName = driverName,
                            isExporting = isExporting,
                            exportResult = exportResult,
                            onCopyAlert = {
                                copyLastAlertToClipboard(
                                    context = context,
                                    tripId = selectedTrip.id,
                                    events = events,
                                    safetyScore = safetyScore,
                                    driverName = driverName
                                )
                            },
                            onExportReport = {
                                coroutineScope.launch {
                                    isExporting = true
                                    val result = withContext(Dispatchers.IO) {
                                        reportExporter.exportReport(
                                            trip = selectedTrip,
                                            samples = samples,
                                            events = events,
                                            baseline = baseline,
                                            driverName = driverName,
                                            emergencyContact = emergencyContact
                                        )
                                    }
                                    exportResult = result
                                    isExporting = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.dashboard_export_success, result.pdfFile.name),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onSharePdf = { result ->
                                sharePdfReport(context, result.pdfFile)
                            },
                            onOpenPdf = { result ->
                                openPdfReport(context, result.pdfFile)
                            }
                        )
                    }

                    // 6. Alert Timeline List
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AlertTimelineSection(
                                events = events,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }

                    // 7. Honest Limits Card
                    item {
                        HonestLimitsCard()
                    }
                }
            }
        }
    }
}

/**
 * Trip Safety Score Overview Card with prominent grade badge.
 */
@Composable
private fun TripSafetyScoreCard(
    trip: Trip,
    safetyScore: Int,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        safetyScore >= 80 -> Color(0xFF2E7D32) // Forest / Green
        safetyScore >= 60 -> HorizonAmber      // Amber
        else -> Color(0xFFC62828)              // Red
    }

    val ratingText = when {
        safetyScore >= 90 -> stringResource(R.string.dashboard_rating_excellent)
        safetyScore >= 75 -> stringResource(R.string.dashboard_rating_good)
        safetyScore >= 60 -> stringResource(R.string.dashboard_rating_moderate)
        else -> stringResource(R.string.dashboard_rating_high)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = HorizonForest.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.5.dp, scoreColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isLandscape) 14.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_card_safety_score),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HorizonForest
                    )
                    Text(
                        text = ratingText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                }

                // Big Score Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scoreColor,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$safetyScore",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 32.sp
                        )
                        Text(
                            text = "/100",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = HorizonMuted)

            // Metrics row: Duration, Avg Alertness, Total Alerts, Critical
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    title = stringResource(R.string.history_duration),
                    value = formatDuration(trip.durationMs)
                )
                MetricColumn(
                    title = stringResource(R.string.history_avg_alertness),
                    value = "${trip.avgAlertness?.toInt() ?: 100}%"
                )
                MetricColumn(
                    title = stringResource(R.string.stat_alerts),
                    value = "${trip.alertCount}"
                )
                MetricColumn(
                    title = stringResource(R.string.history_crit_label).uppercase(),
                    value = "${trip.criticalCount}",
                    color = if (trip.criticalCount > 0) Color(0xFFC62828) else HorizonForest
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    title: String,
    value: String,
    color: Color = HorizonForest
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Driver Profile card displaying baseline thresholds and emergency contact.
 */
@Composable
private fun DriverProfileCard(
    driverName: String,
    emergencyContact: String,
    baseline: Baseline?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_driver_profile),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = MuktaFontFamily,
                    color = HorizonForest
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (baseline?.isValid == true) HorizonLime.copy(alpha = 0.3f) else HorizonMuted
                ) {
                    Text(
                        text = if (baseline?.isValid == true) "CALIBRATED" else "DEFAULT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = HorizonForest,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dashboard_driver_name, driverName),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HorizonForest
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_emergency_contact,
                        emergencyContact.ifEmpty { "None" }
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            val thresholdStr = if (baseline?.isValid == true) {
                String.format(Locale.US, "%.3f", baseline.threshold)
            } else "0.224"

            val blinkStr = if (baseline?.isValid == true) {
                String.format(Locale.US, "%.1f", baseline.blinkRate)
            } else "16.0"

            val latencyStr = if (baseline?.isValid == true) {
                "${baseline.responseLatencyMs}"
            } else "1500"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.dashboard_calib_threshold, thresholdStr),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.dashboard_calib_blink, blinkStr),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.dashboard_calib_latency, latencyStr),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Large, reachable Start/Stop Drive buttons designed for remote control via Office Kit (OFF-5, D7).
 */
@Composable
private fun LargeDriveControlButtons(
    isActiveDrive: Boolean,
    onStartDrive: () -> Unit,
    onStopDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isActiveDrive) {
        Button(
            onClick = onStopDrive,
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFC62828),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_btn_stop_drive),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MuktaFontFamily
                )
            }
        }
    } else {
        Button(
            onClick = onStartDrive,
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HorizonForest,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_btn_start_drive),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MuktaFontFamily
                )
            }
        }
    }
}

/**
 * Action controls card: "Copy Last Alert" button, Export PDF / JSON, Share PDF.
 */
@Composable
private fun ActionControlsCard(
    trip: Trip,
    events: List<AlertEvent>,
    safetyScore: Int,
    driverName: String,
    isExporting: Boolean,
    exportResult: ReportExportResult?,
    onCopyAlert: () -> Unit,
    onExportReport: () -> Unit,
    onSharePdf: (ReportExportResult) -> Unit,
    onOpenPdf: (ReportExportResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Copy Last Alert button (OFF-4)
            OutlinedButton(
                onClick = onCopyAlert,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, HorizonForest),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HorizonForest)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_btn_copy_last_alert),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MuktaFontFamily
                    )
                }
            }

            // Row 2: Export PDF & JSON Report Button (OFF-2)
            Button(
                onClick = onExportReport,
                enabled = !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HorizonForest,
                    contentColor = Color.White
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.dashboard_exporting), fontSize = 14.sp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${stringResource(R.string.dashboard_btn_export_pdf)} & JSON",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MuktaFontFamily
                        )
                    }
                }
            }

            // Export Result Banner & Share / Open buttons
            AnimatedVisibility(visible = exportResult != null) {
                exportResult?.let { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = HorizonLime.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, HorizonLime)
                        ) {
                            Text(
                                text = "Saved: ${result.pdfFile.name} (${result.destinationPath})",
                                fontSize = 11.sp,
                                color = HorizonForest,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenPdf(result) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HorizonForest)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_btn_open_pdf),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HorizonForest
                                )
                            }

                            Button(
                                onClick = { onSharePdf(result) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = HorizonForest)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_btn_share_pdf),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canvas Alertness Line Graph plotting alertness samples over time (0–100).
 */
@Composable
fun AlertnessLineGraph(
    trip: Trip,
    samples: List<AlertSample>,
    events: List<AlertEvent>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val padLeft = 44.dp.toPx()
        val padRight = 16.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 26.dp.toPx()

        val graphW = w - padLeft - padRight
        val graphH = h - padTop - padBottom

        // 1. Threshold Zone Colored Backgrounds
        val y100 = padTop
        val y71 = padTop + (1f - 0.71f) * graphH
        val y51 = padTop + (1f - 0.51f) * graphH
        val y31 = padTop + (1f - 0.31f) * graphH
        val y0 = padTop + graphH

        // Alert zone (71-100)
        drawRect(
            color = Color(0x152E7D32),
            topLeft = Offset(padLeft, y100),
            size = androidx.compose.ui.geometry.Size(graphW, y71 - y100)
        )
        // Caution zone (51-70)
        drawRect(
            color = Color(0x15F57F17),
            topLeft = Offset(padLeft, y71),
            size = androidx.compose.ui.geometry.Size(graphW, y51 - y71)
        )
        // Fatigued zone (31-50)
        drawRect(
            color = Color(0x18E65100),
            topLeft = Offset(padLeft, y51),
            size = androidx.compose.ui.geometry.Size(graphW, y31 - y51)
        )
        // Critical zone (0-30)
        drawRect(
            color = Color(0x1CD32F2F),
            topLeft = Offset(padLeft, y31),
            size = androidx.compose.ui.geometry.Size(graphW, y0 - y31)
        )

        // 2. Horizontal Grid Lines at 100, 71, 51, 31, 0
        val gridColor = Color(0xFFD2DCD6)
        val gridLevels = listOf(100f, 71f, 51f, 31f, 0f)

        for (lvl in gridLevels) {
            val yPos = padTop + (1f - (lvl / 100f)) * graphH
            drawLine(
                color = gridColor,
                start = Offset(padLeft, yPos),
                end = Offset(padLeft + graphW, yPos),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 3. Line Graph of Telemetry Samples
        if (samples.isNotEmpty()) {
            val sortedSamples = samples.sortedBy { it.tsMs }
            val minTs = trip.startMs
            val maxTs = (trip.endMs ?: sortedSamples.maxOf { it.tsMs }).coerceAtLeast(minTs + 1000L)
            val timeRange = (maxTs - minTs).toFloat().coerceAtLeast(1000f)

            val curvePath = Path()
            val fillPath = Path()

            sortedSamples.forEachIndexed { i, sample ->
                val x = padLeft + ((sample.tsMs - minTs) / timeRange) * graphW
                val y = padTop + (1f - (sample.alertness.coerceIn(0, 100) / 100f)) * graphH

                if (i == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, y0)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (i == sortedSamples.lastIndex) {
                    fillPath.lineTo(x, y0)
                    fillPath.close()
                }
            }

            // Fill gradient under curve
            drawPath(
                path = fillPath,
                color = HorizonForest.copy(alpha = 0.12f)
            )

            // Curve stroke
            drawPath(
                path = curvePath,
                color = HorizonForest,
                style = Stroke(
                    width = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Point dots
            for (sample in sortedSamples) {
                val x = padLeft + ((sample.tsMs - minTs) / timeRange) * graphW
                val y = padTop + (1f - (sample.alertness.coerceIn(0, 100) / 100f)) * graphH

                drawCircle(
                    color = HorizonForest,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // Event markers on the graph
            for (event in events) {
                val x = padLeft + ((event.tsMs - minTs) / timeRange).coerceIn(0f, 1f) * graphW
                drawLine(
                    color = Color(0xFFC62828),
                    start = Offset(x, padTop),
                    end = Offset(x, y0),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawCircle(
                    color = Color(0xFFC62828),
                    radius = 4.dp.toPx(),
                    center = Offset(x, padTop + 4.dp.toPx())
                )
            }
        } else {
            // Flat line at 100 if no samples
            drawLine(
                color = HorizonForest.copy(alpha = 0.5f),
                start = Offset(padLeft, y100),
                end = Offset(padLeft + graphW, y100),
                strokeWidth = 2.dp.toPx()
            )
        }

        // 4. Outer Graph Frame
        drawRect(
            color = gridColor,
            topLeft = Offset(padLeft, padTop),
            size = androidx.compose.ui.geometry.Size(graphW, graphH),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Alert Intervention Timeline section displaying logged alerts.
 */
@Composable
private fun AlertTimelineSection(
    events: List<AlertEvent>,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_timeline_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = MuktaFontFamily,
                color = HorizonForest
            )
            Text(
                text = "${events.size} logged",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (events.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = HorizonLime.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, HorizonLime),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_timeline_empty),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HorizonForest
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    TimelineEventRow(event = event, timeFmt = timeFmt)
                }
            }
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: AlertEvent,
    timeFmt: SimpleDateFormat
) {
    val levelColor = when (event.level.name) {
        "L1", "L2" -> HorizonAmber
        else -> Color(0xFFC62828)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = levelColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = event.level.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = levelColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = timeFmt.format(Date(event.tsMs)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = event.reason,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fs", event.durationMs / 1000.0),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = event.response,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HorizonForest
                )
            }
        }
    }
}

/**
 * Honest Limits & Disclaimer card (Non-medical heuristic notice).
 */
@Composable
private fun HonestLimitsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = HorizonForest.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, HorizonForest.copy(alpha = 0.2f))
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
                    tint = HorizonForest,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.dashboard_honest_limits_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HorizonForest
                )
            }
            Text(
                text = stringResource(R.string.dashboard_honest_limits_text),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Empty state when no trip is recorded yet.
 */
@Composable
private fun EmptyDashboardView(
    onStartDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = HorizonForest,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No Driving Sessions Yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MuktaFontFamily,
            color = HorizonForest
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Start a drive to record alertness telemetry, generate safety scores, and export PDF reports.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onStartDrive,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HorizonForest)
        ) {
            Text(text = stringResource(R.string.btn_start_drive), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Helpers ---

private fun formatDate(epochMs: Long): String {
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(epochMs))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val mins = (totalSeconds % 3600L) / 60L
    val secs = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, mins)
    } else {
        String.format(Locale.US, "%dm %02ds", mins, secs)
    }
}

private fun copyLastAlertToClipboard(
    context: Context,
    tripId: Long,
    events: List<AlertEvent>,
    safetyScore: Int,
    driverName: String
) {
    val lastEvent = events.maxByOrNull { it.tsMs }
    val summary = ReportExporter.formatAlertSummaryForClipboard(
        tripId = tripId,
        event = lastEvent,
        safetyScore = safetyScore,
        driverName = driverName
    )
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Jaagrit Alert Summary", summary)
    clipboard.setPrimaryClip(clip)

    val msg = if (lastEvent != null) {
        context.getString(R.string.dashboard_alert_copied)
    } else {
        context.getString(R.string.dashboard_no_alert_to_copy)
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

private fun sharePdfReport(context: Context, pdfFile: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Jaagrit Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openPdfReport(context: Context, pdfFile: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(viewIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Open PDF failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
