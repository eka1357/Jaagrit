package com.jaagrit.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.jaagrit.app.data.BaselineStore
import com.jaagrit.app.engine.Action
import com.jaagrit.app.engine.Baseline
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
    config: Config = Config.DEFAULT
) {
    private val tag = "JAAGRIT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: FatigueEngine = FatigueEngine(config = config)
    private val driveStartTimeMs = System.currentTimeMillis()
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

        // 2. Load driver baseline and initialize engine
        scope.launch {
            val baseline = baselineStore.getBaseline() ?: Baseline.DEFAULT
            Log.d(tag, "Loaded driver baseline: threshold=${baseline.threshold}, isValid=${baseline.isValid}")
            engine = FatigueEngine(baseline = baseline)
            engine.resetDrive(driveStartTimeMs)

            // 3. Consume vision frames from live-stream callback
            launch {
                landmarkerWrapper.visionResult.collect { result ->
                    processVisionResult(result)
                }
            }

            // 4. Periodic 100ms ticker for timers and engine ticks
            launch {
                while (isActive) {
                    delay(100L)
                    processTick()
                }
            }
        }
    }

    /**
     * Handle user tapping the "I'M AWAKE" button during an alert.
     */
    fun onImAwake() {
        Log.i(tag, "User tapped 'I'M AWAKE' — silencing alerts and resetting state")
        // Silence actuators immediately
        alarmToneGenerator.stopAlarm()
        vibeManager.cancel()
        speaker.stop()

        // Inform engine of the awake response
        val output = engine.onVoice(VoiceEvent.ImAwake)
        executeActions(output.actions)

        _uiState.update { current ->
            current.copy(
                state = output.state,
                level = output.level,
                alertness = output.alertness,
                reasons = output.reasons,
                isRedFlashActive = false
            )
        }
    }

    /**
     * Stop and release all underlying resources.
     */
    fun release() {
        Log.i(tag, "Releasing MonitoringPipeline")
        isStarted = false
        alarmToneGenerator.release()
        vibeManager.cancel()
        speaker.shutdown()
        cameraController.release()
        scope.cancel()
    }

    // --- Private Processing Logic ---

    private fun processVisionResult(result: VisionResult) {
        val output = engine.onFrame(result.faceFrame)
        executeActions(output.actions)

        val isCriticalAlert = output.state == DriverState.CRITICAL || output.level in listOf(Level.L3, Level.L4, Level.L5)
        val shouldRedFlash = if (output.state == DriverState.FACE_LOST || output.state == DriverState.NORMAL) {
            false
        } else {
            isCriticalAlert || _uiState.value.isRedFlashActive
        }

        // If transitioning out of critical, silence alarms
        if (!isCriticalAlert && _uiState.value.isRedFlashActive) {
            alarmToneGenerator.stopAlarm()
            vibeManager.cancel()
        }

        _uiState.update { current ->
            current.copy(
                alertness = output.alertness,
                state = output.state,
                level = output.level,
                reasons = output.reasons,
                isRedFlashActive = shouldRedFlash,
                faceFound = result.faceFound,
                inferenceTimeMs = result.inferenceTimeMs,
                fps = result.fps,
                faceFrame = result.faceFrame,
                driveTimeMs = System.currentTimeMillis() - driveStartTimeMs,
                alertCount = totalAlerts
            )
        }
    }

    private fun processTick() {
        val output = engine.onTick()
        executeActions(output.actions)

        val isCriticalAlert = output.state == DriverState.CRITICAL || output.level in listOf(Level.L3, Level.L4, Level.L5)
        val shouldRedFlash = if (output.state == DriverState.FACE_LOST || output.state == DriverState.NORMAL) {
            false
        } else {
            isCriticalAlert || _uiState.value.isRedFlashActive
        }

        // If transitioning out of critical, silence alarms
        if (!isCriticalAlert && _uiState.value.isRedFlashActive) {
            alarmToneGenerator.stopAlarm()
            vibeManager.cancel()
        }

        _uiState.update { current ->
            current.copy(
                alertness = output.alertness,
                state = output.state,
                level = output.level,
                reasons = output.reasons,
                isRedFlashActive = shouldRedFlash,
                driveTimeMs = System.currentTimeMillis() - driveStartTimeMs,
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
                    totalAlerts++
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
                    totalAlerts++
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
