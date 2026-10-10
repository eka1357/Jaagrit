package com.jaagrit.app.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.jaagrit.app.audio.FamilyClipPlayer
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.data.JaagritDatabase
import com.jaagrit.app.data.SettingsStore
import com.jaagrit.app.data.model.AlertSample
import com.jaagrit.app.data.repository.TripRepository
import com.jaagrit.app.engine.Action
import com.jaagrit.app.engine.AlertEventType
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Clock
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.DriverState
import com.jaagrit.app.engine.EngineOutput
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.engine.FatigueEngine
import com.jaagrit.app.engine.Lang
import com.jaagrit.app.engine.Level
import com.jaagrit.app.engine.VoiceEvent
import com.jaagrit.app.platform.AlarmToneGenerator
import com.jaagrit.app.platform.VibeManager
import com.jaagrit.app.sms.SmsAvailability
import com.jaagrit.app.sms.SmsNotifier
import com.jaagrit.app.sms.SmsResult
import com.jaagrit.app.speech.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI state exposed by the decoupled [MonitoringPipeline].
 */
data class MonitoringUiState(
    val alertness: Int = 100,
    val state: DriverState = DriverState.NORMAL,
    val level: Level = Level.L0,
    val driveTimeMs: Long = 0L,
    val alertCount: Int = 0,
    val reasons: List<String> = emptyList(),
    val isRedFlashActive: Boolean = false,
    val faceFound: Boolean = false,
    val inferenceTimeMs: Long = 0L,
    val fps: Float = 0f,
    val faceFrame: FaceFrame = FaceFrame.EMPTY,
    val l5CountdownSeconds: Int? = null,
    val smsNotificationStatus: String? = null,
    val smsAvailability: SmsAvailability = SmsAvailability.READY,
    val falseAlertCount: Int = 0,
    val suggestRecalibration: Boolean = false,
    val demoTimers: Boolean = false,
    val quickCalibration: Boolean = false
) {
    val driveTimeFormatted: String
        get() {
            val totalSeconds = (driveTimeMs / 1000).coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
}

/**
 * Decoupled monitoring session managing camera, vision analyzer, engine, and platform actuators (AGENTS.md Rule 10, D15).
 * Independent of Compose UI; can later run directly inside a Foreground Service.
 */
class MonitoringPipeline(
    private val context: Context,
    val landmarkerWrapper: FaceLandmarkerWrapper = FaceLandmarkerWrapper(context),
    val cameraController: CameraController = CameraController(context, landmarkerWrapper),
    private val speaker: TtsSpeaker = TtsSpeaker(context),
    private val alarmToneGenerator: AlarmToneGenerator = AlarmToneGenerator(context),
    private val vibeManager: VibeManager = VibeManager(context),
    private val baselineStore: BaselineStore = BaselineStore(context),
    private val settingsStore: SettingsStore = SettingsStore(context),
    private val familyClipPlayer: FamilyClipPlayer = FamilyClipPlayer(context) { phrase ->
        speaker.speak(phrase, Lang.HI, urgent = true)
    },
    private val smsNotifier: SmsNotifier = SmsNotifier(context, settingsStore),
    initialConfig: Config = Config.DEFAULT,
    val clock: Clock = Clock { SystemClock.elapsedRealtime() },
    private val tripRepository: TripRepository = TripRepository(
        JaagritDatabase.getDatabase(context).tripDao(),
        JaagritDatabase.getDatabase(context).alertDao()
    )
) {
    private val tag = "JAAGRIT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Confinement to single thread/coroutine (AUDIT-003)
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    private sealed interface PipelineEvent {
        data class Vision(val result: VisionResult) : PipelineEvent
        object Tick : PipelineEvent
        object ImAwake : PipelineEvent
    }

    private val eventChannel = Channel<PipelineEvent>(capacity = Channel.UNLIMITED)

    private var currentConfig: Config = initialConfig
    private var currentBaseline: Baseline = Baseline.DEFAULT
    private var engine: FatigueEngine = FatigueEngine(config = currentConfig, clock = clock)
    private val driveStartTimeMs: Long = clock.nowMs()
    private var totalAlerts = 0
    private var totalCriticalAlerts = 0
    private var currentTripId: Long? = null
    private var l5NotSentLoggedForEpisode = false

    // Alert episode tracking for Room logging
    private var activeEpisodeStartRealtimeMs: Long? = null
    private var activeEpisodeStartWallMs: Long? = null
    private var activeEpisodeMaxLevel: Level = Level.L0
    private var activeEpisodeReason: String = ""

    private val _uiState = MutableStateFlow(
        MonitoringUiState(
            demoTimers = initialConfig.demoTimers,
            quickCalibration = initialConfig.quickCalibration
        )
    )
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var isStarted = false

    /**
     * Start the camera preview and processing pipeline.
     */
    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (isStarted) return
        isStarted = true
        Log.i(tag, "Starting MonitoringPipeline session")

        // 1. Create Room Trip off main thread
        val wallStart = System.currentTimeMillis()
        dbScope.launch {
            val tripId = tripRepository.createTrip(startMs = wallStart)
            currentTripId = tripId
            Log.i(tag, "Room DB: Created active Trip #$tripId at wall $wallStart")
        }

        // 2. Start CameraX preview
        cameraController.startCamera(lifecycleOwner, previewView)

        // 3. Sequential engine loop confined to engineDispatcher (AUDIT-003)
        scope.launch(engineDispatcher) {
            val baseline = baselineStore.getBaseline() ?: Baseline.DEFAULT
            val demoTimersSaved = settingsStore.isDemoTimersEnabled()
            val quickCalibSaved = settingsStore.isQuickCalibrationEnabled()
            currentBaseline = baseline
            currentConfig = currentConfig.copy(
                demoTimers = demoTimersSaved,
                quickCalibration = quickCalibSaved
            )

            Log.d(tag, "Loaded driver baseline: threshold=${baseline.threshold}, isValid=${baseline.isValid}, demoTimers=$demoTimersSaved")
            engine = FatigueEngine(config = currentConfig, clock = clock, baseline = currentBaseline)
            engine.resetDrive(driveStartTimeMs)

            _uiState.update {
                it.copy(
                    demoTimers = demoTimersSaved,
                    quickCalibration = quickCalibSaved
                )
            }

            for (event in eventChannel) {
                when (event) {
                    is PipelineEvent.Vision -> processVisionResult(event.result)
                    is PipelineEvent.Tick -> processTick()
                    is PipelineEvent.ImAwake -> processImAwake()
                }
            }
        }

        // 4. Producers
        scope.launch {
            landmarkerWrapper.visionResult.collect { result ->
                eventChannel.send(PipelineEvent.Vision(result))
            }
        }

        scope.launch {
            while (isActive) {
                delay(100L)
                eventChannel.send(PipelineEvent.Tick)
            }
        }

        // 5. 5-second alertness sampling flushed to Room DB (crash resilient)
        scope.launch {
            while (isActive) {
                delay(5000L)
                val tripId = currentTripId ?: continue
                val currentAlertness = _uiState.value.alertness
                val wallNow = System.currentTimeMillis()
                val sample = AlertSample(
                    tripId = tripId,
                    tsMs = wallNow,
                    alertness = currentAlertness
                )
                dbScope.launch {
                    tripRepository.flushAlertSamples(listOf(sample))
                }
            }
        }
    }

    /**
     * Handle user tapping the "I'M AWAKE" button during an alert.
     */
    fun onImAwake() {
        Log.i(tag, "User tapped 'I'M AWAKE' — silencing alerts and resetting state")
        l5NotSentLoggedForEpisode = false
        // Immediate physical silencing for instant tactile/auditory feedback
        alarmToneGenerator.stopAlarm()
        familyClipPlayer.stop()
        vibeManager.cancel()
        speaker.stop()

        _uiState.update { it.copy(smsNotificationStatus = null) }

        // Record alert event resolution via imAwake button
        endActiveAlertEpisode(responseType = "imAwake")

        // Confined processing via event channel (AUDIT-003)
        eventChannel.trySend(PipelineEvent.ImAwake)
    }

    fun toggleDemoTimers() {
        scope.launch(engineDispatcher) {
            val newDemo = !currentConfig.demoTimers
            currentConfig = currentConfig.copy(demoTimers = newDemo)
            engine = FatigueEngine(config = currentConfig, clock = clock, baseline = currentBaseline)
            settingsStore.setDemoTimers(newDemo)
            _uiState.update { it.copy(demoTimers = newDemo) }
            Log.i(tag, "Toggled DEMO_TIMERS: $newDemo (L4=${currentConfig.l4AfterL3Ms}ms, L5=${currentConfig.l5AfterL4Ms}ms)")
        }
    }

    fun toggleQuickCalibration() {
        scope.launch(engineDispatcher) {
            val newQuick = !currentConfig.quickCalibration
            currentConfig = currentConfig.copy(quickCalibration = newQuick)
            settingsStore.setQuickCalibration(newQuick)
            _uiState.update { it.copy(quickCalibration = newQuick) }
            Log.i(tag, "Toggled QUICK_CALIBRATION: $newQuick")
        }
    }

    /**
     * Stop and release all underlying resources. Closes the active trip in Room.
     */
    fun release() {
        Log.i(tag, "Releasing MonitoringPipeline")
        // If an alert was still active, close it as 'none'
        endActiveAlertEpisode(responseType = "none")

        val tripId = currentTripId
        if (tripId != null) {
            val endWall = System.currentTimeMillis()
            val alerts = totalAlerts
            val criticals = totalCriticalAlerts
            dbScope.launch {
                tripRepository.closeTrip(
                    tripId = tripId,
                    endMs = endWall,
                    alertCount = alerts,
                    criticalCount = criticals
                )
                Log.i(tag, "Room DB: Closed Trip #$tripId (endMs=$endWall, alerts=$alerts, critical=$criticals)")
            }
        }

        isStarted = false
        eventChannel.close()
        scope.cancel()
        try {
            runBlocking {
                scope.coroutineContext[Job]?.join()
            }
        } catch (e: Exception) {
            Log.w(tag, "Exception joining pipeline scope: ${e.message}")
        }
        alarmToneGenerator.release()
        familyClipPlayer.release()
        vibeManager.cancel()
        speaker.shutdown()
        cameraController.release()
    }

    // --- Private Processing Logic (Sequential on engineDispatcher) ---

    private fun processVisionResult(result: VisionResult) {
        val output = engine.onFrame(result.faceFrame)
        handleEngineOutput(output, faceFound = result.faceFound, visionResult = result)
    }

    private fun processTick() {
        val output = engine.onTick()
        handleEngineOutput(output, faceFound = _uiState.value.faceFound, visionResult = null)
    }

    private fun processImAwake() {
        val output = engine.onVoice(VoiceEvent.ImAwake)
        handleEngineOutput(output, faceFound = _uiState.value.faceFound, visionResult = null)
    }

    private fun handleEngineOutput(
        output: EngineOutput,
        faceFound: Boolean,
        visionResult: VisionResult?
    ) {
        val availability = smsNotifier.checkAvailability()

        // Check if ladder resolved response via continuous open eyes (>= 3s)
        if (output.actions.any { it is Action.Log && it.detail.contains("Eyes open >= 3s") }) {
            endActiveAlertEpisode(responseType = "eyesOpen")
        }

        // AUDIT-004: derive alarm, vibration, family clip, and red flash state from ladder state (output.isAlertActive)
        // Alarm and vibration keep running through FACE_LOST and stop only on response (which clears isAlertActive)
        val isAlertActive = output.isAlertActive
        if (!isAlertActive && _uiState.value.isRedFlashActive) {
            alarmToneGenerator.stopAlarm()
            familyClipPlayer.stop()
            vibeManager.cancel()
            l5NotSentLoggedForEpisode = false
            // If alert resolved without explicit awake/eyesOpen/voice, mark as dismissed
            if (activeEpisodeStartRealtimeMs != null) {
                endActiveAlertEpisode(responseType = "dismissed")
            }
        }

        // If SMS is unavailable at L5: no fake countdown. Show "Emergency SMS unavailable (reason)"
        // copy the prepared emergency message to clipboard, and log alert event L5_NOT_SENT.
        val effectiveL5Countdown = if (availability == SmsAvailability.READY) {
            output.l5CountdownSeconds
        } else {
            null
        }

        if (output.level == Level.L5 && availability != SmsAvailability.READY && !l5NotSentLoggedForEpisode) {
            l5NotSentLoggedForEpisode = true
            val tripId = currentTripId
            val wallNow = System.currentTimeMillis()
            dbScope.launch {
                val preparedMsg = smsNotifier.prepareEmergencyMessage(totalAlerts)
                smsNotifier.copyMessageToClipboard(preparedMsg)
                Log.w(tag, "Engine Log: [${AlertEventType.L5_NOT_SENT}] Emergency SMS unavailable (${availability.reason}) - message copied to clipboard")
                if (tripId != null) {
                    tripRepository.recordAlertEvent(
                        tripId = tripId,
                        level = Level.L5,
                        reason = "Emergency SMS unavailable (${availability.reason})",
                        durationMs = 0L,
                        response = "L5_NOT_SENT",
                        tsMs = wallNow
                    )
                }
            }
        }

        val updatedSmsStatus = when {
            !isAlertActive -> null
            availability != SmsAvailability.READY && (output.level == Level.L5 || output.l5CountdownSeconds != null) -> {
                "Emergency SMS unavailable (${availability.reason})"
            }
            else -> _uiState.value.smsNotificationStatus
        }

        executeActions(output.actions)

        _uiState.update { current ->
            current.copy(
                alertness = output.alertness,
                state = output.state,
                level = output.level,
                reasons = output.reasons,
                isRedFlashActive = isAlertActive,
                faceFound = faceFound,
                inferenceTimeMs = visionResult?.inferenceTimeMs ?: current.inferenceTimeMs,
                fps = visionResult?.fps ?: current.fps,
                faceFrame = visionResult?.faceFrame ?: current.faceFrame,
                driveTimeMs = (clock.nowMs() - driveStartTimeMs).coerceAtLeast(0L),
                alertCount = totalAlerts,
                l5CountdownSeconds = effectiveL5Countdown,
                smsNotificationStatus = updatedSmsStatus,
                smsAvailability = availability,
                falseAlertCount = output.falseAlertCount,
                suggestRecalibration = output.suggestRecalibration,
                demoTimers = currentConfig.demoTimers,
                quickCalibration = currentConfig.quickCalibration
            )
        }
    }

    private fun executeActions(actions: List<Action>) {
        if (actions.isEmpty()) return
        for (action in actions) {
            when (action) {
                is Action.ShowRedFlash -> {
                    Log.w(tag, "Action.ShowRedFlash triggered")
                    totalAlerts++ // Counted once per L3 episode (AUDIT-001, AUDIT-004)
                    totalCriticalAlerts++
                    alarmToneGenerator.startAlarm()
                    _uiState.update { it.copy(isRedFlashActive = true, alertCount = totalAlerts) }

                    // Start alert episode tracking for Room DB
                    if (activeEpisodeStartRealtimeMs == null) {
                        activeEpisodeStartRealtimeMs = clock.nowMs()
                        activeEpisodeStartWallMs = System.currentTimeMillis()
                        activeEpisodeMaxLevel = Level.L3
                        activeEpisodeReason = _uiState.value.reasons.firstOrNull() ?: "Critical drowsiness detected"
                    }
                }
                is Action.Vibrate -> {
                    Log.d(tag, "Action.Vibrate triggered: ${action.pattern}")
                    vibeManager.vibrate(action.pattern)
                }
                is Action.Speak -> {
                    Log.d(tag, "Action.Speak triggered (urgent=${action.urgent}): ${action.text}")
                    speaker.speak(action.text, action.lang, action.urgent)
                }
                is Action.PlayFamilyClip -> {
                    Log.i(tag, "Action.PlayFamilyClip triggered: ${action.index}")
                    activeEpisodeMaxLevel = maxOf(activeEpisodeMaxLevel, Level.L4)
                    familyClipPlayer.play(action.index)
                }
                is Action.SendSms -> {
                    Log.w(tag, "Action.SendSms triggered: ${action.reason}")
                    activeEpisodeMaxLevel = maxOf(activeEpisodeMaxLevel, Level.L5)
                    scope.launch {
                        val availability = smsNotifier.checkAvailability()
                        val tripId = currentTripId
                        val wallNow = System.currentTimeMillis()
                        if (availability != SmsAvailability.READY) {
                            val reason = availability.reason
                            val preparedMsg = smsNotifier.prepareEmergencyMessage(totalAlerts)
                            smsNotifier.copyMessageToClipboard(preparedMsg)
                            Log.w(tag, "Engine Log: [${AlertEventType.L5_NOT_SENT}] Emergency SMS unavailable ($reason) - message copied to clipboard")
                            _uiState.update {
                                it.copy(
                                    smsNotificationStatus = "Emergency SMS unavailable ($reason)"
                                )
                            }
                            if (tripId != null) {
                                dbScope.launch {
                                    tripRepository.recordAlertEvent(
                                        tripId = tripId,
                                        level = Level.L5,
                                        reason = "Emergency SMS unavailable ($reason)",
                                        durationMs = 0L,
                                        response = "L5_NOT_SENT",
                                        tsMs = wallNow
                                    )
                                }
                            }
                        } else {
                            _uiState.update { it.copy(smsNotificationStatus = "Sending SMS...") }
                            val result = smsNotifier.sendEmergencyAlert(
                                totalAlerts = totalAlerts,
                                onStatusUpdate = { status ->
                                    _uiState.update { it.copy(smsNotificationStatus = status) }
                                }
                            )
                            val statusText = when (result) {
                                is SmsResult.Success -> "SMS sent to ${result.maskedNumber}"
                                is SmsResult.Failure -> {
                                    Log.w(tag, "Engine Log: [${AlertEventType.L5_NOT_SENT}] SMS dispatch failure: ${result.reason}")
                                    if (tripId != null) {
                                        dbScope.launch {
                                            tripRepository.recordAlertEvent(
                                                tripId = tripId,
                                                level = Level.L5,
                                                reason = "SMS dispatch failure: ${result.reason}",
                                                durationMs = 0L,
                                                response = "L5_NOT_SENT",
                                                tsMs = wallNow
                                            )
                                        }
                                    }
                                    "SMS not sent: ${result.reason}"
                                }
                            }
                            Log.i(tag, "Sms dispatch result: $statusText")
                            _uiState.update { it.copy(smsNotificationStatus = statusText) }
                        }
                    }
                }
                is Action.Log -> {
                    Log.d(tag, "Engine Log: [${action.event}] ${action.detail}")
                }
                is Action.AskQuestion -> {
                    Log.d(tag, "Action.AskQuestion triggered: ${action.q.prompt}")
                }
            }
        }
    }

    private fun endActiveAlertEpisode(responseType: String) {
        val startRealtime = activeEpisodeStartRealtimeMs ?: return
        val startWall = activeEpisodeStartWallMs ?: System.currentTimeMillis()
        val duration = (clock.nowMs() - startRealtime).coerceAtLeast(0L)
        val level = activeEpisodeMaxLevel
        val reason = activeEpisodeReason.ifBlank { "Fatigue alert" }
        val tripId = currentTripId

        activeEpisodeStartRealtimeMs = null
        activeEpisodeStartWallMs = null
        activeEpisodeMaxLevel = Level.L0
        activeEpisodeReason = ""

        if (tripId != null) {
            dbScope.launch {
                tripRepository.recordAlertEvent(
                    tripId = tripId,
                    level = level,
                    reason = reason,
                    durationMs = duration,
                    response = responseType,
                    tsMs = startWall
                )
                Log.i(tag, "Room DB: Logged AlertEvent (level=$level, duration=${duration}ms, response=$responseType)")
            }
        }
    }
}
