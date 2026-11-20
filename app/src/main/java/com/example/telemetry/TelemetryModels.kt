package com.example.telemetry

import android.graphics.drawable.Drawable

data class CpuCoreSnapshot(
    val coreIndex: Int,
    val currentMhz: Int,
    val minMhz: Int,
    val maxMhz: Int,
    val clusterLabel: String,
    val usagePercent: Float
)

data class GpuHardwareInfo(
    val vendor: String = "Detectando...",
    val renderer: String = "Detectando...",
    val glVersion: String = "OpenGL ES",
    val glslVersion: String = "-",
    val maxTextureSize: Int = 0,
    val maxViewportDims: Pair<Int, Int> = 0 to 0,
    val extensionCount: Int = 0,
    val vulkanSupported: Boolean = false,
    val vulkanVersionLabel: String = "No detectado"
)

data class DeviceSpecs(
    val deviceName: String = "",
    val socModel: String = "",
    val cpuModelName: String = "",
    val board: String = "",
    val abiList: String = "",
    val coreCount: Int = 0,
    val coreTopologySummary: String = "",
    val gpuInfo: GpuHardwareInfo = GpuHardwareInfo(),
    val totalRamMb: Long = 0L,
    val swapTotalMb: Long = 0L,
    val displayWidthPx: Int = 0,
    val displayHeightPx: Int = 0,
    val refreshRateHz: Float = 60f,
    val supportedRefreshRates: List<Float> = listOf(60f),
    val procStatReadable: Boolean = false,
    val cpuFreqReadable: Boolean = false,
    val memInfoReadable: Boolean = false,
    val gpuSysfsPath: String? = null
)

data class FrameMetricsSnapshot(
    val fps: Float = 0f,
    val onePercentLowFps: Float = 0f,
    val frameTimeMs: Float = 0f,
    val drawDurationMs: Float = 0f,
    val syncDurationMs: Float = 0f,
    val commandIssueMs: Float = 0f,
    val jankCount: Int = 0,
    val totalFrames: Long = 0L,
    val sourceLabel: String = "Choreographer + FrameMetrics"
)

data class NetworkTelemetrySnapshot(
    val connectionType: String = "Detectando...",
    val isConnected: Boolean = false,
    val isMetered: Boolean = false,
    val isVpn: Boolean = false,
    val downloadKbps: Float = 0f,
    val uploadKbps: Float = 0f,
    val linkDownMbps: Int = 0,
    val linkUpMbps: Int = 0,
    val wifiRssiDbm: Int? = null,
    val wifiFreqMhz: Int? = null,
    val interfaceName: String = "-"
)

data class LiveTelemetrySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val cpuUsagePercent: Float = 0f,
    val cpuSource: String = "Inicializando",
    val cpuAvgMhz: Int = 0,
    val cpuMaxMhz: Int = 0,
    val cores: List<CpuCoreSnapshot> = emptyList(),
    val gpuUsagePercent: Float = 0f,
    val gpuClockMhz: Int = 0,
    val gpuSource: String = "FrameMetrics GPU",
    val ramTotalMb: Long = 0L,
    val ramUsedMb: Long = 0L,
    val ramAvailableMb: Long = 0L,
    val ramCachedMb: Long = 0L,
    val ramSwapUsedMb: Long = 0L,
    val ramSwapTotalMb: Long = 0L,
    val appPssMb: Float = 0f,
    val graphicsPssMb: Float = 0f,
    val nativeHeapMb: Float = 0f,
    val dalvikHeapMb: Float = 0f,
    val isLowMemory: Boolean = false,
    val batteryTempC: Float = 0f,
    val batteryLevelPercent: Int = 0,
    val isCharging: Boolean = false,
    val voltageMv: Int = 0,
    val currentMa: Float = 0f,
    val powerWatts: Float = 0f,
    val thermalStatusLabel: String = "Nominal",
    val thermalSeverity: Int = 0,
    val thermalHeadroom: Float? = null,
    val frameMetrics: FrameMetricsSnapshot = FrameMetricsSnapshot(),
    val network: NetworkTelemetrySnapshot = NetworkTelemetrySnapshot()
) {
    val ramUsagePercent: Float
        get() = if (ramTotalMb > 0L) (ramUsedMb.toFloat() / ramTotalMb.toFloat() * 100f).coerceIn(0f, 100f) else 0f
}

enum class BenchmarkPreset(
    val title: String,
    val subtitle: String,
    val shaderComplexity: Int,
    val cpuWorkerThreads: Int,
    val ramBufferMb: Int
) {
    LIGERO("Ligero", "60 FPS · Shader base", 1, 0, 8),
    MEDIO("Medio", "Juego estándar · Iluminación", 2, 1, 24),
    ALTO("Alto", "Raymarching · Carga GPU", 3, 2, 48),
    ULTRA("Ultra", "Estrés GPU + CPU + RAM", 4, 4, 96)
}

data class InstalledGameInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isTaggedGame: Boolean,
    val icon: Drawable?
)

data class SelectedGameTarget(
    val name: String = "Monitor General",
    val packageName: String = "device.monitor",
    val isExternalGame: Boolean = false,
    val isBuiltInBenchmark: Boolean = false,
    val icon: Drawable? = null
)
