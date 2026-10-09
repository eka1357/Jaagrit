package com.jaagrit.app.camera

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.engine.Action
import com.jaagrit.app.engine.Baseline
import com.jaagrit.app.engine.Clock
import com.jaagrit.app.engine.Config
import com.jaagrit.app.engine.DriverState
import com.jaagrit.app.engine.EngineOutput
import com.jaagrit.app.engine.FaceFrame
import com.jaagrit.app.engine.FatigueEngine
import com.jaagrit.app.engine.Level
import com.jaagrit.app.engine.VoiceEvent
import com.jaagrit.app.platform.AlarmToneGenerator
import com.jaagrit.app.platform.VibeManager
import com.jaagrit.app.speech.TtsSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    val faceFrame: FaceFrame = FaceFrame.EMPTY
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
    private val alarmToneGenerator: AlarmToneGenerator = AlarmToneGenerator(),
    private val vibeManager: VibeManager = VibeManager(context),
    private val baselineStore: BaselineStore = BaselineStore(context),
    val config: Config = Config.DEFAULT,
    val clock: Clock = Clock { SystemClock.elapsedRealtime() }
) {
    private val tag = "JAAGRIT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Confinement to single thread/coroutine (AUDIT-003)
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    private sealed interface PipelineEvent {
        data class Vision(val result: VisionResult) : PipelineEvent
        object Tick : PipelineEvent
        object ImAwake : PipelineEvent
    }

    private val eventChannel = Channel<PipelineEvent>(capacity = Channel.UNLIMITED)

    private var engine: FatigueEngine = FatigueEngine(config = config, clock = clock)
    private val driveStartTimeMs: Long = clock.nowMs()
    private var totalAlerts = 0

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var isStarted = false

    /**
     * Start the camera preview and processing pipeline.
     */
    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (isStarted) return
        isStarted = true
        Log.i(tag, "Starting MonitoringPipeline session")

        // 1. Start CameraX preview
        cameraController.startCamera(lifecycleOwner, previewView)

        // 2. Sequential engine loop confined to engineDispatcher (AUDIT-003)
        scope.launch(engineDispatcher) {
            val baseline = baselineStore.getBaseline() ?: Baseline.DEFAULT
            Log.d(tag, "Loaded driver baseline: threshold=${baseline.threshold}, isValid=${baseline.isValid}")
            engine = FatigueEngine(config = config, clock = clock, baseline = baseline)
            engine.resetDrive(driveStartTimeMs)

            for (event in eventChannel) {
                when (event) {
                    is PipelineEvent.Vision -> processVisionResult(event.result)
                    is PipelineEvent.Tick -> processTick()
                    is PipelineEvent.ImAwake -> processImAwake()
                }
            }
        }

        // 3. Producers
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
    }

    /**
     * Handle user tapping the "I'M AWAKE" button during an alert.
     */
    fun onImAwake() {
        Log.i(tag, "User tapped 'I'M AWAKE' — silencing alerts and resetting state")
        // Immediate physical silencing for instant tactile/auditory feedback
        alarmToneGenerator.stopAlarm()
        vibeManager.cancel()
        speaker.stop()

        // Confined processing via event channel (AUDIT-003)
        eventChannel.trySend(PipelineEvent.ImAwake)
    }

    /**
     * Stop and release all underlying resources.
     */
    fun release() {
        Log.i(tag, "Releasing MonitoringPipeline")
        isStarted = false
        scope.cancel()
        eventChannel.close()
        alarmToneGenerator.release()
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
        executeActions(output.actions)

        // AUDIT-004: derive alarm, vibration, and red flash state from ladder state (output.isAlertActive)
        // Alarm and vibration keep running through FACE_LOST and stop only on response (which clears isAlertActive)
        val isAlertActive = output.isAlertActive
        if (!isAlertActive && _uiState.value.isRedFlashActive) {
            alarmToneGenerator.stopAlarm()
            vibeManager.cancel()
        }

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
                alertCount = totalAlerts
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
                    alarmToneGenerator.startAlarm()
                    _uiState.update { it.copy(isRedFlashActive = true, alertCount = totalAlerts) }
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
                    Log.d(tag, "Action.PlayFamilyClip triggered: ${action.index}")
                    // PlayFamilyClip must NOT increment totalAlerts (counted once per L3 episode)
                }
                is Action.SendSms -> {
                    Log.w(tag, "Action.SendSms triggered: ${action.reason}")
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
}
