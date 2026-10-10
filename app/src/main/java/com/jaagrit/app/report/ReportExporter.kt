package com.jaagrit.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import com.jaagrit.app.data.model.AlertEvent
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.model.Trip
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Config
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result data holder for generated trip reports.
 */
data class ReportExportResult(
    val pdfFile: File,
    val jsonFile: File,
    val destinationPath: String,
    val honestLimitsNote: String
)

/**
 * Generates one-page PDF (via Android PdfDocument) and JSON trip reports (OFF-2).
 * Saves to Download/Jaagrit/ so Office Kit file transfer can easily access it.
 */
class ReportExporter(private val context: Context) {

    private val tag = "JAAGRIT"

    companion object {
        const val HONEST_LIMITS_NOTE =
            "Honest Limits: Jaagrit estimates driver alertness heuristically using on-device computer vision. " +
                    "Accuracy may be reduced in low light, when wearing dark sunglasses, or at extreme head angles (>30°). " +
                    "This system does not claim medical diagnosis or guarantee accident prevention."

        /**
         * Formats a concise, human-readable text summary of an alert event for clipboard copying (OFF-4).
         */
        fun formatAlertSummaryForClipboard(
            tripId: Long,
            event: AlertEvent?,
            safetyScore: Int,
            driverName: String
        ): String {
            if (event == null) {
                return "Jaagrit Safety Summary: Trip #$tripId by $driverName. Safety Score: $safetyScore/100. No fatigue alerts recorded."
            }
            val timeFmt = SimpleDateFormat("HH:mm:ss, dd MMM yyyy", Locale.US)
            val timeStr = timeFmt.format(Date(event.tsMs))
            val durationSec = String.format(Locale.US, "%.1fs", event.durationMs / 1000.0)

            return buildString {
                appendLine("--- JAAGRIT ALERT SUMMARY ---")
                appendLine("Driver: $driverName | Trip #$tripId")
                appendLine("Time: $timeStr")
                appendLine("Level: ${event.level.name}")
                appendLine("Reason: ${event.reason}")
                appendLine("Duration: $durationSec")
                appendLine("Response: ${event.response}")
                appendLine("Trip Safety Score: $safetyScore/100")
                appendLine("Note: 100% on-device heuristic estimate (offline)")
            }
        }

        /**
         * Builds standard JSON telemetry report.
         * Pure Kotlin string formatting — executes cleanly on both Android runtime and JVM tests.
         */
        fun buildJsonReport(
            trip: Trip,
            samples: List<AlertSample>,
            events: List<AlertEvent>,
            baseline: Baseline?,
            driverName: String,
            emergencyContact: String,
            safetyScore: Int
        ): String {
            fun esc(s: String): String =
                s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"reportVersion\": \"1.0\",")
            sb.appendLine("  \"generatedAtEpochMs\": ${System.currentTimeMillis()},")
            sb.appendLine("  \"app\": \"Jaagrit\",")
            sb.appendLine("  \"honestLimitsDisclaimer\": \"${esc(HONEST_LIMITS_NOTE)}\",")

            // Driver details
            sb.appendLine("  \"driver\": {")
            sb.appendLine("    \"name\": \"${esc(driverName)}\",")
            sb.appendLine("    \"emergencyContact\": \"${esc(emergencyContact.ifEmpty { "Not configured" })}\",")
            sb.appendLine("    \"isCalibrated\": ${baseline?.isValid ?: false}")
            if (baseline != null) {
                sb.appendLine("    ,\"openEarMedian\": ${baseline.openEar},")
                sb.appendLine("    \"closedEarMedian\": ${baseline.closedEar},")
                sb.appendLine("    \"closureThreshold\": ${baseline.threshold},")
                sb.appendLine("    \"baselineBlinkRate\": ${baseline.blinkRate},")
                sb.appendLine("    \"mathReactionLatencyMs\": ${baseline.responseLatencyMs},")
                sb.appendLine("    \"calibratedAtEpochMs\": ${baseline.calibratedAtMs}")
            }
            sb.appendLine("  },")

            // Trip Summary
            sb.appendLine("  \"trip\": {")
            sb.appendLine("    \"tripId\": ${trip.id},")
            sb.appendLine("    \"startEpochMs\": ${trip.startMs},")
            val endEpochVal = if (trip.endMs != null) "${trip.endMs}" else "null"
            sb.appendLine("    \"endEpochMs\": $endEpochVal,")
            sb.appendLine("    \"durationSeconds\": ${trip.durationMs / 1000L},")
            sb.appendLine("    \"avgAlertness\": ${trip.avgAlertness ?: 100.0},")
            sb.appendLine("    \"safetyScore\": $safetyScore,")
            sb.appendLine("    \"alertCount\": ${trip.alertCount},")
            sb.appendLine("    \"criticalCount\": ${trip.criticalCount}")
            sb.appendLine("  },")

            // Telemetry Samples
            sb.appendLine("  \"samples\": [")
            samples.forEachIndexed { idx, s ->
                val comma = if (idx < samples.size - 1) "," else ""
                sb.appendLine("    {\"epochMs\": ${s.tsMs}, \"offsetSeconds\": ${(s.tsMs - trip.startMs) / 1000L}, \"alertness\": ${s.alertness}}$comma")
            }
            sb.appendLine("  ],")

            // Alert Events
            sb.appendLine("  \"events\": [")
            events.forEachIndexed { idx, e ->
                val comma = if (idx < events.size - 1) "," else ""
                sb.appendLine("    {\"eventId\": ${e.id}, \"epochMs\": ${e.tsMs}, \"offsetSeconds\": ${(e.tsMs - trip.startMs) / 1000L}, \"level\": \"${e.level.name}\", \"reason\": \"${esc(e.reason)}\", \"durationMs\": ${e.durationMs}, \"response\": \"${esc(e.response)}\"}$comma")
            }
            sb.appendLine("  ]")
            sb.append("}")

            return sb.toString()
        }
    }

    /**
     * Exports both a one-page PDF and JSON report to Download/Jaagrit/
     */
    fun exportReport(
        trip: Trip,
        samples: List<AlertSample>,
        events: List<AlertEvent>,
        baseline: Baseline?,
        driverName: String,
        emergencyContact: String
    ): ReportExportResult {
        val outputDir = getJaagritDownloadDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = "jaagrit_trip_${trip.id}_$timestamp"

        val safetyScore = Config.calculateTripSafetyScore(
            avgAlertness = trip.avgAlertness,
            alertCount = trip.alertCount,
            criticalCount = trip.criticalCount
        )

        // 1. Export JSON
        val jsonFile = File(outputDir, "$baseName.json")
        val jsonContent = buildJsonReport(
            trip = trip,
            samples = samples,
            events = events,
            baseline = baseline,
            driverName = driverName,
            emergencyContact = emergencyContact,
            safetyScore = safetyScore
        )
        jsonFile.writeText(jsonContent, Charsets.UTF_8)
        Log.i(tag, "Exported JSON report to ${jsonFile.absolutePath}")

        // 2. Export one-page PDF
        val pdfFile = File(outputDir, "$baseName.pdf")
        generatePdfReport(
            targetFile = pdfFile,
            trip = trip,
            samples = samples,
            events = events,
            baseline = baseline,
            driverName = driverName,
            emergencyContact = emergencyContact,
            safetyScore = safetyScore
        )
        Log.i(tag, "Exported PDF report to ${pdfFile.absolutePath}")

        return ReportExportResult(
            pdfFile = pdfFile,
            jsonFile = jsonFile,
            destinationPath = pdfFile.parent ?: outputDir.absolutePath,
            honestLimitsNote = HONEST_LIMITS_NOTE
        )
    }

    /**
     * Resolves the target directory under Download/Jaagrit/
     * Falls back to app external files directory if public directory is inaccessible.
     */
    fun getJaagritDownloadDirectory(): File {
        return try {
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val jaagritDir = File(publicDownloads, "Jaagrit")
            if (!jaagritDir.exists()) {
                val created = jaagritDir.mkdirs()
                if (!created && !jaagritDir.exists()) {
                    fallbackDownloadDirectory()
                } else {
                    jaagritDir
                }
            } else {
                jaagritDir
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not access public Download directory; falling back to app files dir", e)
            fallbackDownloadDirectory()
        }
    }

    private fun fallbackDownloadDirectory(): File {
        val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val jaagritDir = File(appDownloads ?: context.filesDir, "Jaagrit")
        if (!jaagritDir.exists()) {
            jaagritDir.mkdirs()
        }
        return jaagritDir
    }

    /**
     * Renders a clean, high-density one-page A4 PDF using Android PdfDocument.
     * Dimensions: 595 x 842 points (standard A4).
     */
    private fun generatePdfReport(
        targetFile: File,
        trip: Trip,
        samples: List<AlertSample>,
        events: List<AlertEvent>,
        baseline: Baseline?,
        driverName: String,
        emergencyContact: String,
        safetyScore: Int
    ) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
        val tripStartStr = dateFmt.format(Date(trip.startMs))
        val tripEndStr = if (trip.endMs != null) dateFmt.format(Date(trip.endMs)) else "In Progress"

        val durationMinutes = (trip.durationMs / 60000L).coerceAtLeast(0L)
        val durationHours = durationMinutes / 60
        val durationRemMins = durationMinutes % 60
        val durationStr = if (durationHours > 0) "${durationHours}h ${durationRemMins}m" else "${durationMinutes} min"

        // 1. Top Header Brand Bar
        paint.color = Color.parseColor("#1B4D3E") // Horizon Forest Green
        canvas.drawRect(0f, 0f, 595f, 70f, paint)

        // Brand title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("JAAGRIT (जागृत) — TRIP SAFETY REPORT", 32f, 38f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("OFFLINE DRIVER FATIGUE TELEMETRY • OFFICE KIT MIRROR READY", 32f, 55f, paint)

        // Trip ID badge on right
        paint.color = Color.parseColor("#D4E7C5") // Horizon Lime accent
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TRIP #${trip.id}", 500f, 42f, paint)

        // 2. Driver & Trip Summary Cards (y: 84 to 200)
        // Left Card: Driver Profile & Calibration
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F7F6")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8E0DC")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        canvas.drawRoundRect(RectF(32f, 84f, 290f, 206f), 8f, 8f, cardPaint)
        canvas.drawRoundRect(RectF(32f, 84f, 290f, 206f), 8f, 8f, borderPaint)

        paint.color = Color.parseColor("#1B4D3E")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DRIVER PROFILE", 44f, 104f, paint)

        paint.color = Color.parseColor("#2C3E35")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Driver: $driverName", 44f, 122f, paint)
        canvas.drawText("Emergency Contact: ${emergencyContact.ifEmpty { "Not Set" }}", 44f, 137f, paint)

        val thresholdStr = if (baseline?.isValid == true) String.format(Locale.US, "%.3f", baseline.threshold) else "0.224 (Default)"
        val blinkRateStr = if (baseline?.isValid == true) String.format(Locale.US, "%.1f/min", baseline.blinkRate) else "16.0/min (Default)"
        val latencyStr = if (baseline?.isValid == true) "${baseline.responseLatencyMs}ms" else "1500ms"

        canvas.drawText("Eye Threshold: EAR < $thresholdStr", 44f, 152f, paint)
        canvas.drawText("Blink Baseline: $blinkRateStr", 44f, 167f, paint)
        canvas.drawText("Math Reaction Baseline: $latencyStr", 44f, 182f, paint)
        canvas.drawText("Calibration: ${if (baseline?.isValid == true) "Calibrated" else "Default Heuristics"}", 44f, 197f, paint)

        // Right Card: Trip Overview & Safety Score
        canvas.drawRoundRect(RectF(305f, 84f, 563f, 206f), 8f, 8f, cardPaint)
        canvas.drawRoundRect(RectF(305f, 84f, 563f, 206f), 8f, 8f, borderPaint)

        paint.color = Color.parseColor("#1B4D3E")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TRIP SAFETY SCORE", 317f, 104f, paint)

        // Big Score Circle / Box
        val scoreColor = when {
            safetyScore >= 80 -> Color.parseColor("#2E7D32") // Green
            safetyScore >= 60 -> Color.parseColor("#EF6C00") // Amber
            else -> Color.parseColor("#C62828") // Red
        }
        val scoreBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scoreColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(475f, 96f, 551f, 152f), 8f, 8f, scoreBoxPaint)

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$safetyScore", 513f, 132f, paint)

        paint.textSize = 9f
        canvas.drawText("OUT OF 100", 513f, 145f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Details next to score box
        paint.color = Color.parseColor("#2C3E35")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Start: $tripStartStr", 317f, 122f, paint)
        canvas.drawText("End: $tripEndStr", 317f, 137f, paint)
        canvas.drawText("Duration: $durationStr", 317f, 152f, paint)

        val avgScore = trip.avgAlertness?.toInt() ?: 100
        canvas.drawText("Avg Alertness: $avgScore%", 317f, 167f, paint)
        canvas.drawText("Total Alerts: ${trip.alertCount} (Critical: ${trip.criticalCount})", 317f, 182f, paint)

        val ratingText = when {
            safetyScore >= 90 -> "EXCELLENT • HIGH VIGILANCE"
            safetyScore >= 75 -> "GOOD • NORMAL ALERTNESS"
            safetyScore >= 60 -> "MODERATE FATIGUE RISK"
            else -> "HIGH RISK • CRITICAL INTERVENTIONS"
        }
        paint.color = scoreColor
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(ratingText, 317f, 197f, paint)

        // 3. Alertness Over Time Graph (y: 220 to 450)
        paint.color = Color.parseColor("#1B4D3E")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ALERTNESS TELEMETRY CURVE (0–100%)", 32f, 230f, paint)

        val graphLeft = 60f
        val graphRight = 555f
        val graphTop = 246f
        val graphBottom = 420f
        val graphWidth = graphRight - graphLeft
        val graphHeight = graphBottom - graphTop

        // Graph Background & Grid
        val graphBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FAFBFB")
            style = Paint.Style.FILL
        }
        canvas.drawRect(graphLeft, graphTop, graphRight, graphBottom, graphBgPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E4EAE6")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // Draw horizontal grid lines at 100, 71, 51, 31, 0
        val yGridLevels = listOf(
            100 to "100",
            71 to "71 (Alert)",
            51 to "51 (Caution)",
            31 to "31 (Fatigued)",
            0 to "0"
        )

        val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = Color.parseColor("#718079")
            textAlign = Paint.Align.RIGHT
        }

        for ((level, label) in yGridLevels) {
            val yPos = graphBottom - (level / 100f) * graphHeight
            canvas.drawLine(graphLeft, yPos, graphRight, yPos, gridPaint)
            canvas.drawText(label, graphLeft - 4f, yPos + 3f, yLabelPaint)
        }

        // Colored threshold indicator bands on right margin
        val zoneAlertPaint = Paint().apply { color = Color.parseColor("#102E7D32"); style = Paint.Style.FILL } // light green
        val zoneCautionPaint = Paint().apply { color = Color.parseColor("#10EF6C00"); style = Paint.Style.FILL } // light amber
        val zoneCriticalPaint = Paint().apply { color = Color.parseColor("#10C62828"); style = Paint.Style.FILL } // light red

        val y71 = graphBottom - (71f / 100f) * graphHeight
        val y51 = graphBottom - (51f / 100f) * graphHeight
        val y31 = graphBottom - (31f / 100f) * graphHeight

        canvas.drawRect(graphLeft, graphTop, graphRight, y71, zoneAlertPaint)
        canvas.drawRect(graphLeft, y71, graphRight, y51, zoneCautionPaint)
        canvas.drawRect(graphLeft, y31, graphRight, graphBottom, zoneCriticalPaint)

        // Draw Alertness Line
        if (samples.isNotEmpty()) {
            val minTs = trip.startMs
            val maxTs = (trip.endMs ?: samples.maxOf { it.tsMs }).coerceAtLeast(minTs + 1000L)
            val timeRange = (maxTs - minTs).toFloat().coerceAtLeast(1000f)

            val linePath = Path()
            val points = samples.sortedBy { it.tsMs }

            points.forEachIndexed { index, sample ->
                val x = graphLeft + ((sample.tsMs - minTs) / timeRange) * graphWidth
                val clampedAlertness = sample.alertness.coerceIn(0, 100)
                val y = graphBottom - (clampedAlertness / 100f) * graphHeight

                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B4D3E")
                strokeWidth = 2.2f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(linePath, strokePaint)

            // Draw point highlights
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1B4D3E")
                style = Paint.Style.FILL
            }
            points.forEach { sample ->
                val x = graphLeft + ((sample.tsMs - minTs) / timeRange) * graphWidth
                val clampedAlertness = sample.alertness.coerceIn(0, 100)
                val y = graphBottom - (clampedAlertness / 100f) * graphHeight
                canvas.drawCircle(x, y, 2.5f, dotPaint)
            }

            // Draw Event markers on the graph
            val eventMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C62828")
                strokeWidth = 1.2f
                style = Paint.Style.STROKE
            }
            val eventDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C62828")
                style = Paint.Style.FILL
            }

            events.forEach { event ->
                val x = graphLeft + ((event.tsMs - minTs) / timeRange).coerceIn(0f, 1f) * graphWidth
                canvas.drawLine(x, graphTop, x, graphBottom, eventMarkerPaint)
                canvas.drawCircle(x, graphTop + 4f, 3.5f, eventDotPaint)
            }
        } else {
            // Empty state placeholder line at 100
            val emptyLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A0B2A6")
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(graphLeft, graphTop + 10f, graphRight, graphTop + 10f, emptyLinePaint)
            paint.color = Color.parseColor("#718079")
            paint.textSize = 10f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Baseline telemetry recorded. Constant high alertness.", (graphLeft + graphRight) / 2f, (graphTop + graphBottom) / 2f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        // Graph Outer Border
        canvas.drawRect(graphLeft, graphTop, graphRight, graphBottom, borderPaint)

        // X-axis Time Labels
        val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = Color.parseColor("#718079")
        }
        canvas.drawText("0m (Start)", graphLeft, graphBottom + 12f, xLabelPaint)
        canvas.drawText("$durationStr (End)", graphRight - 45f, graphBottom + 12f, xLabelPaint)

        // 4. Alert Intervention Timeline Table (y: 444 to 710)
        paint.color = Color.parseColor("#1B4D3E")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FATIGUE INTERVENTION TIMELINE (${events.size} EVENTS)", 32f, 450f, paint)

        val tableLeft = 32f
        val tableRight = 563f
        var currentY = 462f

        // Table Header
        paint.color = Color.parseColor("#1B4D3E")
        canvas.drawRect(tableLeft, currentY, tableRight, currentY + 18f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TIME", tableLeft + 8f, currentY + 12f, paint)
        canvas.drawText("LEVEL", tableLeft + 70f, currentY + 12f, paint)
        canvas.drawText("TRIGGER REASON", tableLeft + 130f, currentY + 12f, paint)
        canvas.drawText("DURATION", tableLeft + 360f, currentY + 12f, paint)
        canvas.drawText("RESOLUTION / RESPONSE", tableLeft + 430f, currentY + 12f, paint)

        currentY += 18f

        val timeOnlyFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val rowBgPaintEven = Paint().apply { color = Color.parseColor("#FFFFFF"); style = Paint.Style.FILL }
        val rowBgPaintOdd = Paint().apply { color = Color.parseColor("#F5F7F6"); style = Paint.Style.FILL }

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 8.5f

        if (events.isEmpty()) {
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + 24f, rowBgPaintEven)
            canvas.drawRect(tableLeft, currentY, tableRight, currentY + 24f, borderPaint)
            paint.color = Color.parseColor("#2E7D32")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("✔ Safe Driving Record: Zero fatigue interventions or microsleep alarms triggered.", tableLeft + 12f, currentY + 16f, paint)
            currentY += 24f
        } else {
            val displayEvents = events.take(8) // Limit to top 8 events on single page
            displayEvents.forEachIndexed { idx, ev ->
                val isOdd = idx % 2 != 0
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 18f, if (isOdd) rowBgPaintOdd else rowBgPaintEven)
                canvas.drawRect(tableLeft, currentY, tableRight, currentY + 18f, borderPaint)

                paint.color = Color.parseColor("#2C3E35")
                canvas.drawText(timeOnlyFmt.format(Date(ev.tsMs)), tableLeft + 8f, currentY + 12f, paint)

                val levelColor = when (ev.level.name) {
                    "L1", "L2" -> Color.parseColor("#E65100")
                    else -> Color.parseColor("#C62828")
                }
                paint.color = levelColor
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(ev.level.name, tableLeft + 70f, currentY + 12f, paint)

                paint.color = Color.parseColor("#2C3E35")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                val truncatedReason = if (ev.reason.length > 42) ev.reason.substring(0, 39) + "..." else ev.reason
                canvas.drawText(truncatedReason, tableLeft + 130f, currentY + 12f, paint)

                val durText = String.format(Locale.US, "%.1fs", ev.durationMs / 1000.0)
                canvas.drawText(durText, tableLeft + 360f, currentY + 12f, paint)
                canvas.drawText(ev.response, tableLeft + 430f, currentY + 12f, paint)

                currentY += 18f
            }

            if (events.size > 8) {
                paint.color = Color.parseColor("#718079")
                paint.textSize = 8f
                canvas.drawText("+ ${events.size - 8} additional alert events logged in accompanying JSON telemetry file", tableLeft + 8f, currentY + 12f, paint)
            }
        }

        // 5. Honest Limits & Offline Privacy Disclaimer Box (y: 720 to 820)
        val disclaimerTop = 724f
        val disclaimerBottom = 812f
        val disclaimerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F4F2")
            style = Paint.Style.FILL
        }
        val disclaimerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C2D1C9")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        canvas.drawRoundRect(RectF(tableLeft, disclaimerTop, tableRight, disclaimerBottom), 6f, 6f, disclaimerPaint)
        canvas.drawRoundRect(RectF(tableLeft, disclaimerTop, tableRight, disclaimerBottom), 6f, 6f, disclaimerBorder)

        paint.color = Color.parseColor("#1B4D3E")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("HONEST LIMITS & SYSTEM BOUNDARIES (NON-MEDICAL DISCLAIMER)", tableLeft + 12f, disclaimerTop + 16f, paint)

        paint.color = Color.parseColor("#384841")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val line1 = "Jaagrit estimates driver alertness heuristically using on-device computer vision. Detection accuracy is reduced"
        val line2 = "under low lighting, when wearing dark sunglasses, or at extreme head tilt (>30°). This product does not provide medical diagnosis"
        val line3 = "or replace attentive driving practices. No video frames or audio recordings are retained on storage."
        val line4 = "100% On-Device • Zero Network Transmission • iQOO 15 Offline Edge Architecture"

        canvas.drawText(line1, tableLeft + 12f, disclaimerTop + 30f, paint)
        canvas.drawText(line2, tableLeft + 12f, disclaimerTop + 42f, paint)
        canvas.drawText(line3, tableLeft + 12f, disclaimerTop + 54f, paint)

        paint.color = Color.parseColor("#1B4D3E")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(line4, tableLeft + 12f, disclaimerTop + 72f, paint)

        document.finishPage(page)

        FileOutputStream(targetFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }
}
