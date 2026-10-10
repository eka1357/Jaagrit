package com.jaagrit.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaagrit.app.data.JaagritDatabase
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.data.repository.TripRepository
import com.jaagrit.app.engine.Level
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { JaagritDatabase.getDatabase(context) }
    val repository = remember { TripRepository(db.tripDao(), db.alertDao()) }
    val trips by repository.getAllTrips().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trip History",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "यात्रा इतिहास • On-device Room Database",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (trips.isEmpty()) {
            EmptyHistoryView(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                // Summary Metrics Card
                item {
                    HistorySummaryHeader(trips = trips)
                }

                item {
                    Text(
                        text = "Driving Sessions (${trips.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(trips, key = { it.id }) { trip ->
                    TripCardItem(
                        trip = trip,
                        repository = repository,
                        onDeleteTrip = {
                            coroutineScope.launch {
                                repository.getTripById(trip.id)?.let {
                                    db.tripDao().deleteTrip(it)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryHeader(trips: List<Trip>) {
    val completedTrips = trips.filter { it.endMs != null }
    val avgScore = if (completedTrips.isNotEmpty()) {
        completedTrips.mapNotNull { it.avgAlertness }.takeIf { it.isNotEmpty() }?.average() ?: 100.0
    } else 100.0

    val totalAlerts = trips.sumOf { it.alertCount }
    val totalCriticals = trips.sumOf { it.criticalCount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Drive Telemetry Overview",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStat(
                    title = "Total Drives",
                    value = "${trips.size}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                SummaryStat(
                    title = "Avg Alertness",
                    value = "${avgScore.toInt()}%",
                    color = if (avgScore >= 70) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                )
                SummaryStat(
                    title = "Total Alerts",
                    value = "$totalAlerts ($totalCriticals crit)",
                    color = if (totalCriticals > 0) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun TripCardItem(
    trip: Trip,
    repository: TripRepository,
    onDeleteTrip: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val events by repository.getEventsForTrip(trip.id).collectAsState(initial = emptyList())

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val startTimeFormatted = remember(trip.startMs) { dateFormatter.format(Date(trip.startMs)) }

    val durationFormatted = remember(trip.durationMs, trip.isActive) {
        if (trip.isActive) {
            "Active Drive 🟢"
        } else {
            val totalSeconds = trip.durationMs / 1000
            val hours = totalSeconds / 3600
            val mins = (totalSeconds % 3600) / 60
            val secs = totalSeconds % 60
            if (hours > 0) "%dh %02dm %02ds".format(hours, mins, secs)
            else "%dm %02ds".format(mins, secs)
        }
    }

    val score = trip.avgAlertness?.toInt() ?: 100
    val scoreColor = when {
        score >= 71 -> Color(0xFF2E7D32)
        score >= 51 -> Color(0xFFF57F17)
        score >= 31 -> Color(0xFFE65100)
        else -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: ID, Date, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "#${trip.id}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = startTimeFormatted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDeleteTrip,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(text = "✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Stats row: Duration, Alertness score badge, Alert counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Duration", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = durationFormatted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Avg Alertness", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scoreColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (trip.avgAlertness != null) "$score%" else "N/A",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Alerts", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = "${trip.alertCount} (${trip.criticalCount} crit)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (trip.alertCount > 0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Expand Hint Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (events.isNotEmpty()) "⚠️ ${events.size} logged events" else "✅ 0 logged events",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = if (expanded) "Hide details ▲" else "Tap for events ▼",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Expanded Event List
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (events.isEmpty()) {
                        Text(
                            text = "No alert events recorded during this session.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        events.forEach { event ->
                            AlertEventItem(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertEventItem(event: AlertEvent) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val eventTime = remember(event.tsMs) { timeFormatter.format(Date(event.tsMs)) }

    val levelColor = when (event.level) {
        Level.L5 -> Color(0xFFB71C1C)
        Level.L4 -> Color(0xFFD32F2F)
        Level.L3 -> Color(0xFFE65100)
        Level.L2 -> Color(0xFFF57F17)
        Level.L1 -> Color(0xFF1976D2)
        Level.L0 -> Color(0xFF2E7D32)
    }

    val responseBgColor = when (event.response) {
        "imAwake" -> Color(0xFFE8F5E9)
        "eyesOpen" -> Color(0xFFE3F2FD)
        "voice" -> Color(0xFFE0F7FA)
        "dismissed" -> Color(0xFFFFF3E0)
        "L5_NOT_SENT" -> Color(0xFFFCE4EC)
        else -> Color(0xFFFFEBEE)
    }

    val responseTextColor = when (event.response) {
        "imAwake" -> Color(0xFF1B5E20)
        "eyesOpen" -> Color(0xFF0D47A1)
        "voice" -> Color(0xFF006064)
        "dismissed" -> Color(0xFFE65100)
        "L5_NOT_SENT" -> Color(0xFF880E4F)
        else -> Color(0xFFB71C1C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = levelColor
                    ) {
                        Text(
                            text = event.level.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }

                    Text(
                        text = eventTime,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.reason,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (event.durationMs > 0L) {
                    Text(
                        text = "Duration: ${"%.1f".format(event.durationMs / 1000.0)}s",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = responseBgColor
            ) {
                Text(
                    text = event.response,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = responseTextColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "📜", fontSize = 36.sp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "No Driving Sessions Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a drive to automatically record 5-second alertness telemetry and fatigue interventions offline.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
