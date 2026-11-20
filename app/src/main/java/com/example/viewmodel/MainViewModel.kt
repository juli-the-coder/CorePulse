package com.example.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AnalyzerSettings
import com.example.data.CorePulseDatabase
import com.example.data.GameSessionEntity
import com.example.data.SettingsDataStore
import com.example.data.TelemetryRepository
import com.example.data.TelemetrySampleEntity
import com.example.telemetry.BenchmarkPreset
import com.example.telemetry.DeviceSpecs
import com.example.telemetry.DeviceSpecsReader
import com.example.telemetry.FrameMetricsTracker
import com.example.telemetry.GameDiscoveryRepository
import com.example.telemetry.InstalledGameInfo
import com.example.telemetry.LiveTelemetryCollector
import com.example.telemetry.LiveTelemetrySnapshot
import com.example.telemetry.SelectedGameTarget
import com.example.telemetry.TelemetryNotificationHelper
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

data class RecordingState(
    val isRecording: Boolean = false,
    val gameName: String = "Sesión General",
    val packageName: String = "device.monitor",
    val isBuiltInBenchmark: Boolean = false,
    val workloadPreset: String = "Monitor en Vivo",
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Int = 0,
    val sampleCount: Int = 0
)

data class BenchmarkLabState(
    val isRunning: Boolean = false,
    val preset: BenchmarkPreset = BenchmarkPreset.MEDIO,
    val cpuStressEnabled: Boolean = true,
    val ramStressEnabled: Boolean = false,
    val durationLimitSeconds: Int = 30,
    val elapsedSeconds: Int = 0,
    val latestDiagnosis: String? = null,
    val lastSavedSessionId: Long? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CorePulseDatabase.getInstance(application)
    private val repository = TelemetryRepository(database.sessionDao())
    private val settingsStore = SettingsDataStore(application)
    private val specsReader = DeviceSpecsReader(application)
    val frameMetricsTracker = FrameMetricsTracker()
    private val collector = LiveTelemetryCollector(application, frameMetricsTracker)
    private val gameDiscovery = GameDiscoveryRepository(application)
    private val notificationHelper = TelemetryNotificationHelper(application)

    val settings: StateFlow<AnalyzerSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnalyzerSettings())

    val savedSessions: StateFlow<List<GameSessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _deviceSpecs = MutableStateFlow(DeviceSpecs())
    val deviceSpecs: StateFlow<DeviceSpecs> = _deviceSpecs.asStateFlow()

    private val _liveSnapshot = MutableStateFlow(LiveTelemetrySnapshot())
    val liveSnapshot: StateFlow<LiveTelemetrySnapshot> = _liveSnapshot.asStateFlow()

    private val _liveHistory = MutableStateFlow<List<LiveTelemetrySnapshot>>(emptyList())
    val liveHistory: StateFlow<List<LiveTelemetrySnapshot>> = _liveHistory.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _benchmarkState = MutableStateFlow(BenchmarkLabState())
    val benchmarkState: StateFlow<BenchmarkLabState> = _benchmarkState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledGameInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledGameInfo>> = _installedApps.asStateFlow()

    private val _isScanningGames = MutableStateFlow(false)
    val isScanningGames: StateFlow<Boolean> = _isScanningGames.asStateFlow()

    private val _selectedGame = MutableStateFlow(SelectedGameTarget())
    val selectedGame: StateFlow<SelectedGameTarget> = _selectedGame.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val recordedBuffer = mutableListOf<LiveTelemetrySnapshot>()
    private var stressWorkerJobs = mutableListOf<Job>()
    private var ramStressBuffers = mutableListOf<ByteArray>()
    private var benchmarkTimerJob: Job? = null

    init {
        loadDeviceSpecs()
        refreshInstalledGames()
        startTelemetryLoop()
    }

    private fun loadDeviceSpecs() {
        viewModelScope.launch {
            val specs = specsReader.readDeviceSpecs()
            _deviceSpecs.value = specs
            frameMetricsTracker.setTargetRefreshRate(specs.refreshRateHz)
        }
    }

    fun refreshInstalledGames() {
        viewModelScope.launch {
            _isScanningGames.value = true
            _installedApps.value = gameDiscovery.getLaunchableAppsAndGames()
            _isScanningGames.value = false
        }
    }

    private fun startTelemetryLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val snap = collector.collect(_deviceSpecs.value)
                _liveSnapshot.value = snap

                val currentHist = _liveHistory.value
                _liveHistory.value = (currentHist + snap).takeLast(45)

                val rec = _recordingState.value
                if (rec.isRecording) {
                    synchronized(recordedBuffer) {
                        recordedBuffer.add(snap)
                    }
                    val elapsed = ((System.currentTimeMillis() - rec.startTimeMillis) / 1000L)
                        .toInt().coerceAtLeast(0)
                    _recordingState.value = rec.copy(
                        elapsedSeconds = elapsed,
                        sampleCount = recordedBuffer.size
                    )
                    if (settings.value.enableLiveNotification) {
                        notificationHelper.showOrUpdate(rec.gameName, snap, elapsed)
                    }
                }

                val interval = settings.value.samplingIntervalMs.coerceIn(350L, 5000L)
                delay(interval)
            }
        }
    }

    fun startRecording(
        gameName: String = "Sesión en Vivo",
        packageName: String = "device.monitor",
        isBuiltInBenchmark: Boolean = false,
        workloadPreset: String = "Monitor"
    ) {
        if (_recordingState.value.isRecording) return
        frameMetricsTracker.resetSessionCounters()
        synchronized(recordedBuffer) {
            recordedBuffer.clear()
        }
        val now = System.currentTimeMillis()
        _recordingState.value = RecordingState(
            isRecording = true,
            gameName = gameName,
            packageName = packageName,
            isBuiltInBenchmark = isBuiltInBenchmark,
            workloadPreset = workloadPreset,
            startTimeMillis = now,
            elapsedSeconds = 0,
            sampleCount = 0
        )
    }

    fun stopAndSaveRecording(onSaved: ((Long) -> Unit)? = null) {
        val rec = _recordingState.value
        if (!rec.isRecording) return
        _recordingState.value = RecordingState(isRecording = false)
        notificationHelper.cancel()

        val snapshotCopy: List<LiveTelemetrySnapshot>
        synchronized(recordedBuffer) {
            snapshotCopy = recordedBuffer.toList()
            recordedBuffer.clear()
        }

        if (snapshotCopy.isEmpty()) {
            _uiMessage.value = "No se capturaron suficientes muestras."
            return
        }

        viewModelScope.launch {
            val savedId = repository.saveRecordedSession(
                gameName = rec.gameName,
                packageName = rec.packageName,
                isBuiltInBenchmark = rec.isBuiltInBenchmark,
                workloadPreset = rec.workloadPreset,
                startTimeMillis = rec.startTimeMillis,
                targetFps = settings.value.targetFps,
                samples = snapshotCopy
            )
            if (savedId > 0L) {
                _uiMessage.value = "Sesión «${rec.gameName}» guardada correctamente."
                onSaved?.invoke(savedId)
            }
        }
    }

    fun selectGameTarget(game: InstalledGameInfo?) {
        _selectedGame.value = if (game == null) {
            SelectedGameTarget()
        } else {
            SelectedGameTarget(
                name = game.appName,
                packageName = game.packageName,
                isExternalGame = true,
                isBuiltInBenchmark = false,
                icon = game.icon
            )
        }
    }

    fun selectBuiltInBenchmarkTarget() {
        _selectedGame.value = SelectedGameTarget(
            name = "CorePulse 3D Lab",
            packageName = getApplication<Application>().packageName,
            isExternalGame = false,
            isBuiltInBenchmark = true,
            icon = null
        )
    }

    fun startRecordingForSelectedGame() {
        val target = _selectedGame.value
        startRecording(
            gameName = target.name,
            packageName = target.packageName,
            isBuiltInBenchmark = target.isBuiltInBenchmark,
            workloadPreset = if (target.isExternalGame) "Juego Seleccionado" else "Monitor en Vivo"
        )
    }

    fun launchSelectedGameAndMonitor() {
        val target = _selectedGame.value
        if (!target.isExternalGame) {
            startRecordingForSelectedGame()
            return
        }
        val intent = gameDiscovery.buildLaunchIntent(target.packageName)
        if (intent == null) {
            _uiMessage.value = "No se pudo abrir ${target.name}."
            return
        }
        if (!_recordingState.value.isRecording) {
            startRecording(
                gameName = target.name,
                packageName = target.packageName,
                isBuiltInBenchmark = false,
                workloadPreset = "Juego Externo"
            )
        }
        runCatching {
            getApplication<Application>().startActivity(intent)
        }.onFailure {
            _uiMessage.value = "Error al iniciar ${target.name}."
        }
    }

    fun launchGameAndMonitor(game: InstalledGameInfo) {
        selectGameTarget(game)
        val intent = gameDiscovery.buildLaunchIntent(game.packageName)
        if (intent == null) {
            _uiMessage.value = "No se pudo abrir ${game.appName}."
            return
        }
        if (!_recordingState.value.isRecording) {
            startRecording(
                gameName = game.appName,
                packageName = game.packageName,
                isBuiltInBenchmark = false,
                workloadPreset = "Juego Externo"
            )
        }
        runCatching {
            getApplication<Application>().startActivity(intent)
        }.onFailure {
            _uiMessage.value = "Error al iniciar ${game.appName}."
        }
    }

    // --- OpenGL 3D Benchmark Lab ---

    fun selectBenchmarkPreset(preset: BenchmarkPreset) {
        val wasRunning = _benchmarkState.value.isRunning
        _benchmarkState.value = _benchmarkState.value.copy(preset = preset)
        if (wasRunning) {
            restartStressWorkers()
        }
    }

    fun toggleBenchmarkCpuStress(enabled: Boolean) {
        _benchmarkState.value = _benchmarkState.value.copy(cpuStressEnabled = enabled)
        if (_benchmarkState.value.isRunning) {
            restartStressWorkers()
        }
    }

    fun toggleBenchmarkRamStress(enabled: Boolean) {
        _benchmarkState.value = _benchmarkState.value.copy(ramStressEnabled = enabled)
        if (_benchmarkState.value.isRunning) {
            restartStressWorkers()
        }
    }

    fun setBenchmarkDuration(seconds: Int) {
        _benchmarkState.value = _benchmarkState.value.copy(durationLimitSeconds = seconds)
    }

    fun startBenchmark() {
        val state = _benchmarkState.value
        if (state.isRunning) return

        _benchmarkState.value = state.copy(
            isRunning = true,
            elapsedSeconds = 0,
            latestDiagnosis = null,
            lastSavedSessionId = null
        )
        collector.externalBenchmarkGpuLoadHint = when (state.preset) {
            BenchmarkPreset.LIGERO -> 32f
            BenchmarkPreset.MEDIO -> 58f
            BenchmarkPreset.ALTO -> 82f
            BenchmarkPreset.ULTRA -> 96f
        }

        startRecording(
            gameName = "CorePulse 3D Lab (${state.preset.title})",
            packageName = getApplication<Application>().packageName,
            isBuiltInBenchmark = true,
            workloadPreset = state.preset.title
        )
        restartStressWorkers()

        benchmarkTimerJob?.cancel()
        benchmarkTimerJob = viewModelScope.launch {
            while (isActive && _benchmarkState.value.isRunning) {
                delay(1000L)
                val cur = _benchmarkState.value
                val nextSec = cur.elapsedSeconds + 1
                _benchmarkState.value = cur.copy(elapsedSeconds = nextSec)
                if (cur.durationLimitSeconds > 0 && nextSec >= cur.durationLimitSeconds) {
                    stopBenchmark(autoSave = true)
                    break
                }
            }
        }
    }

    fun stopBenchmark(autoSave: Boolean = true) {
        benchmarkTimerJob?.cancel()
        benchmarkTimerJob = null
        stopStressWorkers()
        collector.externalBenchmarkGpuLoadHint = null

        val snap = _liveSnapshot.value
        val diagnosis = TelemetryRepository.diagnoseBottleneck(
            avgFps = snap.frameMetrics.fps,
            min1PctFps = snap.frameMetrics.onePercentLowFps,
            targetFps = settings.value.targetFps,
            avgCpu = snap.cpuUsagePercent,
            avgGpu = snap.gpuUsagePercent,
            ramPeakRatio = snap.ramUsagePercent / 100f,
            peakTemp = snap.batteryTempC,
            thermalSeverity = snap.thermalSeverity
        )

        _benchmarkState.value = _benchmarkState.value.copy(
            isRunning = false,
            latestDiagnosis = diagnosis
        )

        if (autoSave) {
            stopAndSaveRecording { savedId ->
                _benchmarkState.value = _benchmarkState.value.copy(lastSavedSessionId = savedId)
            }
        } else if (_recordingState.value.isRecording) {
            _recordingState.value = RecordingState(isRecording = false)
            notificationHelper.cancel()
        }
    }

    private fun restartStressWorkers() {
        stopStressWorkers()
        val state = _benchmarkState.value
        val preset = state.preset

        collector.externalBenchmarkGpuLoadHint = when (preset) {
            BenchmarkPreset.LIGERO -> 32f
            BenchmarkPreset.MEDIO -> 58f
            BenchmarkPreset.ALTO -> 82f
            BenchmarkPreset.ULTRA -> 96f
        }

        if (state.cpuStressEnabled && preset.cpuWorkerThreads > 0) {
            repeat(preset.cpuWorkerThreads) { workerIdx ->
                val job = viewModelScope.launch(Dispatchers.Default) {
                    var acc = (workerIdx + 1) * 0.137
                    while (isActive) {
                        for (i in 0 until 25_000) {
                            acc = sin(acc + i * 0.001) * cos(acc - i * 0.0007) + 1.0001
                        }
                        if (acc > 1e9) acc = 0.5
                        delay(if (preset == BenchmarkPreset.ULTRA) 4L else 12L)
                    }
                }
                stressWorkerJobs.add(job)
            }
        }

        if (state.ramStressEnabled) {
            runCatching {
                val chunkCount = (preset.ramBufferMb / 4).coerceAtLeast(1)
                repeat(chunkCount) {
                    val block = ByteArray(4 * 1024 * 1024) { idx -> (idx and 0x7F).toByte() }
                    ramStressBuffers.add(block)
                }
            }
        }
    }

    private fun stopStressWorkers() {
        stressWorkerJobs.forEach { it.cancel() }
        stressWorkerJobs.clear()
        ramStressBuffers.clear()
    }

    // --- Session Detail & Export ---

    fun observeSession(sessionId: Long): Flow<GameSessionEntity?> =
        repository.observeSession(sessionId)

    fun observeSessionSamples(sessionId: Long): Flow<List<TelemetrySampleEntity>> =
        repository.observeSamples(sessionId)

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            _uiMessage.value = "Sesión eliminada."
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAllSessions()
            _uiMessage.value = "Historial limpiado."
        }
    }

    fun shareSessionReport(session: GameSessionEntity) {
        viewModelScope.launch {
            val csvText = repository.buildCsvExport(session)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte CorePulse: ${session.gameName}")
                putExtra(Intent.EXTRA_TEXT, csvText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Exportar Telemetría").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                getApplication<Application>().startActivity(chooser)
            }
        }
    }

    // --- Settings ---

    fun updateSamplingInterval(ms: Int) {
        viewModelScope.launch { settingsStore.setSamplingIntervalMs(ms) }
    }

    fun updateTargetFps(fps: Int) {
        viewModelScope.launch { settingsStore.setTargetFps(fps) }
    }

    fun updateUseFahrenheit(useF: Boolean) {
        viewModelScope.launch { settingsStore.setUseFahrenheit(useF) }
    }

    fun updateLiveNotification(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setEnableLiveNotification(enabled) }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStressWorkers()
        benchmarkTimerJob?.cancel()
        frameMetricsTracker.stop()
        notificationHelper.cancel()
    }
}
