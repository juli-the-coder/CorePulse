package com.example.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import java.io.File
import kotlin.math.abs

class LiveTelemetryCollector(
    private val context: Context,
    private val frameMetricsTracker: FrameMetricsTracker
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var prevProcStatTotal: Long = 0L
    private var prevProcStatIdle: Long = 0L
    private var prevWallTimeMs: Long = 0L
    private var prevProcessCpuMs: Long = 0L
    private var prevNetWallTimeMs: Long = 0L
    private var prevRxBytes: Long = TrafficStats.UNSUPPORTED.toLong()
    private var prevTxBytes: Long = TrafficStats.UNSUPPORTED.toLong()

    @Volatile
    var externalBenchmarkGpuLoadHint: Float? = null

    fun collect(specs: DeviceSpecs): LiveTelemetrySnapshot {
        val nowMs = System.currentTimeMillis()
        val cores = readCpuCores(specs.coreCount)
        val (cpuPercent, cpuSource) = readTotalCpuUsage(cores, specs.coreCount)
        val avgMhz = if (cores.isNotEmpty()) cores.map { it.currentMhz }.filter { it > 0 }.average().let {
            if (it.isNaN()) 0 else it.toInt()
        } else 0
        val maxMhz = cores.maxOfOrNull { it.currentMhz } ?: 0

        val frameSnap = frameMetricsTracker.snapshot.value
        val (gpuPercent, gpuClockMhz, gpuSource) = readGpuTelemetry(specs, frameSnap)

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)

        val memMap = readProcMemInfoMap()
        val cachedMb = ((memMap["Cached"] ?: 0L) + (memMap["Buffers"] ?: 0L)) / 1024L
        val swapTotalMb = (memMap["SwapTotal"] ?: 0L) / 1024L
        val swapFreeMb = (memMap["SwapFree"] ?: 0L) / 1024L
        val swapUsedMb = (swapTotalMb - swapFreeMb).coerceAtLeast(0L)

        val dbgMem = Debug.MemoryInfo()
        Debug.getMemoryInfo(dbgMem)
        val appPssMb = dbgMem.totalPss / 1024f
        val graphicsPssMb = (dbgMem.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0) / 1024f
        val nativeHeapMb = (dbgMem.getMemoryStat("summary.native-heap")?.toIntOrNull() ?: 0) / 1024f
        val dalvikHeapMb = (dbgMem.getMemoryStat("summary.java-heap")?.toIntOrNull() ?: 0) / 1024f

        val batteryData = readBatteryAndPower()
        val (thermalLabel, thermalSeverity, thermalHeadroom) = readThermalState()
        val networkSnap = readNetworkTelemetry()

        return LiveTelemetrySnapshot(
            timestampMs = nowMs,
            cpuUsagePercent = cpuPercent,
            cpuSource = cpuSource,
            cpuAvgMhz = avgMhz,
            cpuMaxMhz = maxMhz,
            cores = cores,
            gpuUsagePercent = gpuPercent,
            gpuClockMhz = gpuClockMhz,
            gpuSource = gpuSource,
            ramTotalMb = totalRamMb,
            ramUsedMb = usedRamMb,
            ramAvailableMb = availRamMb,
            ramCachedMb = cachedMb,
            ramSwapUsedMb = swapUsedMb,
            ramSwapTotalMb = swapTotalMb,
            appPssMb = appPssMb,
            graphicsPssMb = graphicsPssMb,
            nativeHeapMb = nativeHeapMb,
            dalvikHeapMb = dalvikHeapMb,
            isLowMemory = memInfo.lowMemory,
            batteryTempC = batteryData.tempC,
            batteryLevelPercent = batteryData.levelPct,
            isCharging = batteryData.isCharging,
            voltageMv = batteryData.voltageMv,
            currentMa = batteryData.currentMa,
            powerWatts = batteryData.watts,
            thermalStatusLabel = thermalLabel,
            thermalSeverity = thermalSeverity,
            thermalHeadroom = thermalHeadroom,
            frameMetrics = frameSnap,
            network = networkSnap
        )
    }

    private fun readCpuCores(coreCount: Int): List<CpuCoreSnapshot> {
        val count = coreCount.coerceAtLeast(1)
        val maxList = (0 until count).map { idx ->
            readSysInt("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq")?.div(1000) ?: 0
        }
        val highestMax = maxList.maxOrNull() ?: 0
        val lowestMax = maxList.filter { it > 0 }.minOrNull() ?: 0

        return (0 until count).map { idx ->
            val curMhz = readSysInt("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")?.div(1000) ?: 0
            val minMhz = readSysInt("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_min_freq")?.div(1000) ?: 0
            val maxMhz = maxList[idx]
            val cluster = when {
                maxMhz == 0 -> "Core $idx"
                highestMax > lowestMax && maxMhz == highestMax -> "Prime"
                highestMax > lowestMax && maxMhz == lowestMax -> "Efic."
                else -> "Perf."
            }
            val usage = if (maxMhz > minMhz && curMhz >= minMhz) {
                ((curMhz - minMhz).toFloat() / (maxMhz - minMhz).toFloat() * 100f).coerceIn(0f, 100f)
            } else 0f

            CpuCoreSnapshot(
                coreIndex = idx,
                currentMhz = curMhz,
                minMhz = minMhz,
                maxMhz = maxMhz,
                clusterLabel = cluster,
                usagePercent = usage
            )
        }
    }

    private fun readTotalCpuUsage(cores: List<CpuCoreSnapshot>, coreCount: Int): Pair<Float, String> {
        // 1. Try /proc/stat aggregate CPU jiffies delta
        val procStat = readProcStatJiffies()
        if (procStat != null) {
            val (total, idle) = procStat
            if (prevProcStatTotal > 0L && total > prevProcStatTotal) {
                val totalDelta = (total - prevProcStatTotal).toFloat()
                val idleDelta = (idle - prevProcStatIdle).coerceAtLeast(0L).toFloat()
                prevProcStatTotal = total
                prevProcStatIdle = idle
                val pct = (((totalDelta - idleDelta) / totalDelta) * 100f).coerceIn(0f, 100f)
                return pct to "/proc/stat"
            }
            prevProcStatTotal = total
            prevProcStatIdle = idle
        }

        // 2. Fallback: Combine cpufreq scaling utilization + Process CPU elapsed time delta
        val nowWall = SystemClock.elapsedRealtime()
        val nowProcCpu = Process.getElapsedCpuTime()
        var procCpuPct = 0f
        if (prevWallTimeMs > 0L && nowWall > prevWallTimeMs) {
            val wallDelta = (nowWall - prevWallTimeMs).toFloat()
            val cpuDelta = (nowProcCpu - prevProcessCpuMs).coerceAtLeast(0L).toFloat()
            procCpuPct = (cpuDelta / (wallDelta * coreCount.coerceAtLeast(1)) * 100f).coerceIn(0f, 100f)
        }
        prevWallTimeMs = nowWall
        prevProcessCpuMs = nowProcCpu

        val freqRatios = cores.filter { it.maxMhz > 0 }.map { it.usagePercent }
        return if (freqRatios.isNotEmpty()) {
            val freqAvg = freqRatios.average().toFloat()
            val combined = maxOf(freqAvg, procCpuPct).coerceIn(0f, 100f)
            combined to "cpufreq + proc"
        } else {
            procCpuPct to "Process CPU"
        }
    }

    private fun readGpuTelemetry(
        specs: DeviceSpecs,
        frameSnap: FrameMetricsSnapshot
    ): Triple<Float, Int, String> {
        // 1. Try Qualcomm Adreno KGSL sysfs
        val adrenoBusy = readSysString("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            ?.replace("%", "")?.trim()?.toFloatOrNull()
        val adrenoClockHz = readSysLong("/sys/class/kgsl/kgsl-3d0/gpuclk")
        val adrenoClockMhz = readSysInt("/sys/class/kgsl/kgsl-3d0/clock_mhz")
            ?: adrenoClockHz?.div(1_000_000L)?.toInt()
            ?: 0
        if (adrenoBusy != null) {
            return Triple(adrenoBusy.coerceIn(0f, 100f), adrenoClockMhz, "Sysfs Adreno KGSL")
        }

        // 2. Try Adreno gpubusy ratio ("busy total")
        val gpuBusyPair = readSysString("/sys/class/kgsl/kgsl-3d0/gpubusy")
            ?.trim()?.split(Regex("\\s+"))
        if (gpuBusyPair != null && gpuBusyPair.size >= 2) {
            val busy = gpuBusyPair[0].toFloatOrNull()
            val total = gpuBusyPair[1].toFloatOrNull()
            if (busy != null && total != null && total > 0f) {
                return Triple((busy / total * 100f).coerceIn(0f, 100f), adrenoClockMhz, "Sysfs KGSL Busy")
            }
        }

        // 3. Try ARM Mali utilization
        val maliUtil = readSysString("/sys/class/misc/mali0/device/utilization")?.trim()?.toFloatOrNull()
            ?: readSysString("/sys/devices/platform/mali.0/utilization")?.trim()?.toFloatOrNull()
        if (maliUtil != null) {
            val maliClock = readSysInt("/sys/class/misc/mali0/device/clock")?.div(1_000_000) ?: 0
            return Triple(maliUtil.coerceIn(0f, 100f), maliClock, "Sysfs Mali")
        }

        // 4. Derive GPU frame budget load from real FrameMetrics / GL Benchmark workload
        val budgetMs = (1000f / specs.refreshRateHz.coerceAtLeast(30f))
        val gpuPipelineMs = (frameSnap.commandIssueMs + frameSnap.syncDurationMs + frameSnap.drawDurationMs * 0.65f)
            .let { if (it > 0.2f) it else frameSnap.frameTimeMs * 0.6f }
        val budgetLoad = (gpuPipelineMs / budgetMs * 100f).coerceIn(0f, 100f)
        val hint = externalBenchmarkGpuLoadHint
        val effectiveLoad = if (hint != null) maxOf(budgetLoad, hint).coerceIn(0f, 100f) else budgetLoad
        val source = if (hint != null) "OpenGL ES 3D Lab" else "FrameMetrics GPU"
        return Triple(effectiveLoad, adrenoClockMhz, source)
    }

    private fun readProcStatJiffies(): Pair<Long, Long>? = runCatching {
        val file = File("/proc/stat")
        if (!file.canRead()) return@runCatching null
        val firstLine = file.bufferedReader().use { it.readLine() } ?: return@runCatching null
        if (!firstLine.startsWith("cpu ")) return@runCatching null
        val parts = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 4) return@runCatching null
        val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
        val total = parts.sum()
        total to idle
    }.getOrNull()

    private fun readProcMemInfoMap(): Map<String, Long> = runCatching {
        val file = File("/proc/meminfo")
        if (!file.canRead()) return@runCatching emptyMap()
        val result = mutableMapOf<String, Long>()
        file.forEachLine { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val valKb = line.substring(idx + 1).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                if (valKb != null) result[key] = valKb
            }
        }
        result
    }.getOrDefault(emptyMap())

    private fun readBatteryAndPower(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempC = tempTenths / 10f
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val levelPct = if (level >= 0) (level * 100 / scale) else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            // Some OEMs report mA instead of uA; normalize values > 15,000 as uA
            val rawAbs = abs(currentUa).toFloat()
            if (rawAbs > 15_000f) rawAbs / 1000f else rawAbs
        } else 0f

        val watts = (voltageMv / 1000f) * (currentMa / 1000f)
        return BatteryReading(tempC, levelPct, isCharging, voltageMv, currentMa, watts)
    }

    private fun readThermalState(): Triple<String, Int, Float?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Triple("Normal", 0, null)
        }
        val status = powerManager.currentThermalStatus
        val (label, sev) = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal" to 0
            PowerManager.THERMAL_STATUS_LIGHT -> "Tibio" to 1
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderado" to 2
            PowerManager.THERMAL_STATUS_SEVERE -> "Severo" to 3
            PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico" to 4
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Límite Térmico" to 5
            else -> "Nominal" to 0
        }
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val h = powerManager.getThermalHeadroom(10)
                if (h.isNaN() || h < 0f) null else h
            }.getOrNull()
        } else null

        return Triple(label, sev, headroom)
    }

    private fun readNetworkTelemetry(): NetworkTelemetrySnapshot {
        val nowMs = SystemClock.elapsedRealtime()
        val rxNow = TrafficStats.getTotalRxBytes()
        val txNow = TrafficStats.getTotalTxBytes()
        var downKbps = 0f
        var upKbps = 0f

        if (rxNow != TrafficStats.UNSUPPORTED.toLong() &&
            txNow != TrafficStats.UNSUPPORTED.toLong() &&
            prevRxBytes != TrafficStats.UNSUPPORTED.toLong() &&
            prevNetWallTimeMs > 0L &&
            nowMs > prevNetWallTimeMs
        ) {
            val dtSec = ((nowMs - prevNetWallTimeMs) / 1000f).coerceAtLeast(0.1f)
            val rxDelta = (rxNow - prevRxBytes).coerceAtLeast(0L)
            val txDelta = (txNow - prevTxBytes).coerceAtLeast(0L)
            downKbps = (rxDelta / 1024f) / dtSec
            upKbps = (txDelta / 1024f) / dtSec
        }
        prevNetWallTimeMs = nowMs
        prevRxBytes = rxNow
        prevTxBytes = txNow

        return runCatching {
            val activeNet = connectivityManager.activeNetwork
            val caps = activeNet?.let { connectivityManager.getNetworkCapabilities(it) }
            val linkProps = activeNet?.let { connectivityManager.getLinkProperties(it) }

            if (caps == null) {
                return@runCatching NetworkTelemetrySnapshot(
                    connectionType = "Sin Red",
                    isConnected = false,
                    downloadKbps = downKbps,
                    uploadKbps = upKbps
                )
            }

            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val typeLabel = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                isVpn -> "VPN"
                else -> "Red Activa"
            }

            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val linkDownMbps = (caps.linkDownstreamBandwidthKbps / 1000).coerceAtLeast(0)
            val linkUpMbps = (caps.linkUpstreamBandwidthKbps / 1000).coerceAtLeast(0)

            var rssiDbm: Int? = null
            var freqMhz: Int? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transportInfo = caps.transportInfo
                if (transportInfo is WifiInfo) {
                    if (transportInfo.rssi != -127) rssiDbm = transportInfo.rssi
                    if (transportInfo.frequency > 0) freqMhz = transportInfo.frequency
                } else if (caps.signalStrength != Int.MIN_VALUE) {
                    rssiDbm = caps.signalStrength
                }
            }

            NetworkTelemetrySnapshot(
                connectionType = typeLabel,
                isConnected = isConnected,
                isMetered = isMetered,
                isVpn = isVpn,
                downloadKbps = downKbps,
                uploadKbps = upKbps,
                linkDownMbps = linkDownMbps,
                linkUpMbps = linkUpMbps,
                wifiRssiDbm = rssiDbm,
                wifiFreqMhz = freqMhz,
                interfaceName = linkProps?.interfaceName ?: "-"
            )
        }.getOrElse {
            NetworkTelemetrySnapshot(
                connectionType = "No disponible",
                isConnected = false,
                downloadKbps = downKbps,
                uploadKbps = upKbps
            )
        }
    }

    private fun readSysInt(path: String): Int? = readSysString(path)?.toIntOrNull()
    private fun readSysLong(path: String): Long? = readSysString(path)?.toLongOrNull()

    private fun readSysString(path: String): String? = runCatching {
        val file = File(path)
        if (!file.canRead()) return@runCatching null
        file.readText().trim()
    }.getOrNull()

    private data class BatteryReading(
        val tempC: Float,
        val levelPct: Int,
        val isCharging: Boolean,
        val voltageMv: Int,
        val currentMa: Float,
        val watts: Float
    )
}
