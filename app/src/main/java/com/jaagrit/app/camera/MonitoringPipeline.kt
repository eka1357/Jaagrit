package com.jaagrit.app.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.jaagrit.app.audio.FamilyClipPlayer
import com.jaagrit.app.audio.PlanBAudioPlayer
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
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.jaagrit.app.R
import com.jaagrit.app.speech.DriverIntent
import com.jaagrit.app.speech.IntentParser
import com.jaagrit.app.speech.Phrases
import com.jaagrit.app.speech.SpeechInput
import com.jaagrit.app.speech.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale
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
    val quickCalibration: Boolean = false,
    val voiceAnswerText: String? = null,
    val isListening: Boolean = false,
    val lastRecognizedText: String? = null,
    val lastMatchedIntent: com.jaagrit.app.speech.DriverIntent? = null,
    val recognitionLatencyMs: Long? = null
) {
    val isAlert: Boolean
        get() = level >= Level.L3 || isRedFlashActive
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
    val planBAudioPlayer: PlanBAudioPlayer = PlanBAudioPlayer(context),
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
    private var lastFrameRealtimeMs: Long = 0L
    private var isBackgrounded: Boolean = false
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundPreviewView: PreviewView? = null

    /**
     * Stop camera, alarm, and TTS cleanly when app is backgrounded (AUDIT-015).
     */
    fun onAppBackgrounded() {
        Log.i(tag, "MonitoringPipeline: App backgrounded, stopping camera, alarm, TTS cleanly (AUDIT-015)")
        isBackgrounded = true
        cameraController.stopCamera()
        alarmToneGenerator.stopAlarm()
        familyClipPlayer.stop()
        planBAudioPlayer.stop()
        vibeManager.cancel()
        speaker.stop()
    }

    /**
     * Resume camera when app returns to foreground (AUDIT-015).
     */
    fun onAppForegrounded() {
        Log.i(tag, "MonitoringPipeline: App foregrounded, resuming camera (AUDIT-015)")
        isBackgrounded = false
        lastFrameRealtimeMs = clock.nowMs()
        val owner = boundLifecycleOwner
        val pv = boundPreviewView
        if (owner != null && pv != null && isStarted) {
            cameraController.startCamera(owner, pv)
        }
    }

    /**
     * Start the camera preview and processing pipeline.
     */
    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        boundLifecycleOwner = lifecycleOwner
        boundPreviewView = previewView
        if (isStarted) return
        isStarted = true
        lastFrameRealtimeMs = clock.nowMs()
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
        planBAudioPlayer.stop()
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
        planBAudioPlayer.release()
        vibeManager.cancel()
        answerDismissJob?.cancel()
        speaker.shutdown()
        cameraController.release()
        boundLifecycleOwner = null
        boundPreviewView = null
    }

    // --- Private Processing Logic (Sequential on engineDispatcher) ---

    private fun processVisionResult(result: VisionResult) {
        lastFrameRealtimeMs = clock.nowMs()
        val output = engine.onFrame(result.faceFrame)
        handleEngineOutput(output, faceFound = result.faceFound, visionResult = result)
    }

    private fun processTick() {
        if (isBackgrounded) return

        val now = clock.nowMs()
        // Stale-frame detection (AUDIT-015): no frame for more than 1s treated as FACE_LOST and never escalates
        val isStale = lastFrameRealtimeMs > 0L && (now - lastFrameRealtimeMs > 1000L)
        val output = if (isStale) {
            val staleFrame = FaceFrame(
                tsMs = now,
                faceFound = false,
                earL = 0f,
                earR = 0f,
                mar = 0f,
                pitchDeg = 0f,
                yawDeg = 0f,
                rollDeg = 0f
            )
            engine.onFrame(staleFrame)
        } else {
            engine.onTick()
        }
        handleEngineOutput(output, faceFound = if (isStale) false else _uiState.value.faceFound, visionResult = null)
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
            planBAudioPlayer.stop()
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
                    if (action.urgent) {
                        val phraseIndex = Phrases.L3_ALERTS.indexOf(action.text).let { if (it >= 0) it + 1 else 1 }
                        val played = planBAudioPlayer.play(phraseIndex)
                        if (!played) {
                            speaker.speak(action.text, action.lang, action.urgent)
                        }
                    } else {
                        speaker.speak(action.text, action.lang, action.urgent)
                    }
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

    // --- M8 Voice Commands & Button Fallback (VOI-2, VOI-3) ---

    private var answerDismissJob: Job? = null

    private fun getLocalizedString(@StringRes id: Int, vararg formatArgs: Any): String {
        val lang = runBlocking { settingsStore.getAppLanguage() }
        val locale = if (lang == "hi") Locale.forLanguageTag("hi-IN") else Locale.US
        val conf = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = context.createConfigurationContext(conf)
        return localizedContext.getString(id, *formatArgs)
    }

    /**
     * Executes a [DriverIntent] triggered by voice recognition or on-screen fallback button.
     * Guaranteed to use real data from Room [TripRepository] and engine state.
     */
    fun executeIntent(intent: DriverIntent, onNavigateToCalibration: (() -> Unit)? = null) {
        if (_uiState.value.isAlert || _uiState.value.level >= Level.L3) {
            Log.w(tag, "Intent execution blocked during active alert (D17)")
            return
        }

        dbScope.launch {
            when (intent) {
                DriverIntent.DRIVE_TIME -> {
                    val driveTimeMs = currentTripId?.let { tripRepository.getDriveTimeSoFarMs(it) }
                        ?: (clock.nowMs() - driveStartTimeMs)
                    val totalMinutes = (driveTimeMs / 60000L).coerceAtLeast(0L)
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    val answer = getLocalizedString(R.string.voice_ans_drive_time, hours, minutes)
                    deliverAnswer(answer)
                }
                DriverIntent.ALERTNESS -> {
                    val score = _uiState.value.alertness
                    val stateRes = when (_uiState.value.state) {
                        DriverState.NORMAL -> R.string.state_normal
                        DriverState.CAUTION -> R.string.state_caution
                        DriverState.FATIGUED -> R.string.state_fatigued
                        DriverState.CRITICAL -> R.string.state_critical
                        DriverState.FACE_LOST -> R.string.state_face_lost
                        DriverState.CALIBRATING -> R.string.state_calibrating
                    }
                    val stateWord = getLocalizedString(stateRes)
                    val answer = getLocalizedString(R.string.voice_ans_alertness, score, stateWord)
                    deliverAnswer(answer)
                }
                DriverIntent.ALERT_COUNT -> {
                    val countToday = tripRepository.getAlertCountToday()
                    val answer = if (countToday == 0) {
                        getLocalizedString(R.string.voice_ans_alert_count_zero)
                    } else {
                        getLocalizedString(R.string.voice_ans_alert_count, countToday)
                    }
                    deliverAnswer(answer)
                }
                DriverIntent.LAST_ALERT -> {
                    val lastAlertWallMs = tripRepository.getLastAlertTime(currentTripId)
                    val answer = if (lastAlertWallMs == null || lastAlertWallMs <= 0L) {
                        getLocalizedString(R.string.voice_ans_alert_count_zero)
                    } else {
                        val diffMinutes = ((System.currentTimeMillis() - lastAlertWallMs) / 60000L).coerceAtLeast(0L)
                        if (diffMinutes < 1) {
                            getLocalizedString(R.string.voice_ans_last_alert_now)
                        } else {
                            getLocalizedString(R.string.voice_ans_last_alert, diffMinutes)
                        }
                    }
                    deliverAnswer(answer)
                }
                DriverIntent.RECALIBRATE -> {
                    val answer = getLocalizedString(R.string.voice_ans_recalibrate)
                    deliverAnswer(answer)
                    delay(1200L)
                    withContext(Dispatchers.Main) {
                        onNavigateToCalibration?.invoke()
                    }
                }
                DriverIntent.DISMISS -> {
                    Log.i(tag, "Driver requested DISMISS - logged (companion arrives in M9)")
                }
                DriverIntent.UNKNOWN -> {
                    val answer = getLocalizedString(R.string.voice_ans_say_again)
                    deliverAnswer(answer)
                }
            }
        }
    }

    private fun deliverAnswer(answer: String) {
        answerDismissJob?.cancel()
        _uiState.update { it.copy(voiceAnswerText = answer) }

        val lang = runBlocking { settingsStore.getAppLanguage() }
        val speechLang = if (lang == "hi") Lang.HI else Lang.EN
        speaker.speak(answer, speechLang, urgent = false)

        answerDismissJob = scope.launch {
            delay(4000L)
            _uiState.update { if (it.voiceAnswerText == answer) it.copy(voiceAnswerText = null) else it }
        }
    }

    /**
     * Push-to-talk handler for the "Ask Jaagrit" button.
     * Enforces mic isolation during L3+ alerts (D17).
     * Reports user-visible failure reasons when speech recognition cannot start or fails.
     */
    fun startPushToTalk(speechInput: SpeechInput, onNavigateToCalibration: (() -> Unit)? = null) {
        if (_uiState.value.isAlert || _uiState.value.level >= Level.L3) {
            Log.w(tag, "Microphone access blocked during active L3+ alert (D17)")
            val reason = getLocalizedString(R.string.voice_err_mic_blocked_alert)
            deliverAnswer(reason)
            return
        }

        val isRecAvailable = speechInput.isAvailable()
        val isOnDevice = speechInput.isOnDeviceAvailable()
        val permState = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        Log.i(tag, "SpeechRecognizer check: isRecognitionAvailable=$isRecAvailable, isOnDeviceRecognitionAvailable=$isOnDevice, permissionGranted=$permState")

        if (!permState) {
            Log.w(tag, "Microphone access blocked: RECORD_AUDIO permission not granted")
            val reason = getLocalizedString(R.string.voice_err_mic_permission)
            deliverAnswer(reason)
            return
        }

        if (!isRecAvailable) {
            Log.w(tag, "SpeechRecognizer unavailable on device")
            val reason = getLocalizedString(R.string.voice_err_no_recognizer)
            deliverAnswer(reason)
            return
        }

        speaker.stop()
        _uiState.update { it.copy(isListening = true) }
        val startTimeMs = clock.nowMs()

        val lang = runBlocking { settingsStore.getAppLanguage() }
        val langCode = if (lang == "hi") "hi-IN" else "en-IN"

        speechInput.startListening(
            languageCode = langCode,
            onResult = { spokenText ->
                val latency = clock.nowMs() - startTimeMs
                val intent = IntentParser.parse(spokenText)
                Log.i(tag, "Speech recognition output: '$spokenText' -> $intent (${latency}ms)")
                _uiState.update {
                    it.copy(
                        isListening = false,
                        lastRecognizedText = spokenText,
                        lastMatchedIntent = intent,
                        recognitionLatencyMs = latency
                    )
                }
                executeIntent(intent, onNavigateToCalibration)
            },
            onError = { errorReason ->
                Log.w(tag, "Speech recognition failed: $errorReason")
                _uiState.update {
                    it.copy(
                        isListening = false,
                        lastRecognizedText = "[Error: $errorReason]"
                    )
                }
                deliverFailureReason(errorReason)
            }
        )
    }

    private fun deliverFailureReason(errorReason: String) {
        val answer = when {
            errorReason == "PERM_MISSING" -> getLocalizedString(R.string.voice_err_mic_permission)
            errorReason == "NO_ON_DEVICE_RECOGNIZER" -> getLocalizedString(R.string.voice_err_no_recognizer)
            errorReason == "LANG_PACK_MISSING" -> getLocalizedString(R.string.voice_err_lang_pack_missing)
            errorReason == "BUSY" -> getLocalizedString(R.string.voice_err_busy)
            errorReason == "NO_SPEECH" -> getLocalizedString(R.string.voice_err_no_speech)
            errorReason.startsWith("CODE:") -> {
                val parts = errorReason.split(":")
                val code = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val name = parts.getOrNull(2) ?: "UNKNOWN"
                getLocalizedString(R.string.voice_err_code, name, code)
            }
            else -> errorReason
        }
        deliverAnswer(answer)
    }

    fun cancelPushToTalk(speechInput: SpeechInput) {
        speechInput.cancel()
        _uiState.update { it.copy(isListening = false) }
    }
}
