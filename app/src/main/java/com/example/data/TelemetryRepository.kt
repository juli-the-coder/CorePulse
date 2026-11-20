package com.example.data

import com.example.telemetry.LiveTelemetrySnapshot
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

class TelemetryRepository(private val sessionDao: SessionDao) {

    val allSessions: Flow<List<GameSessionEntity>> = sessionDao.observeAllSessions()

    fun observeSession(sessionId: Long): Flow<GameSessionEntity?> =
        sessionDao.observeSession(sessionId)

    fun observeSamples(sessionId: Long): Flow<List<TelemetrySampleEntity>> =
        sessionDao.observeSamplesForSession(sessionId)

    suspend fun saveRecordedSession(
        gameName: String,
        packageName: String,
        isBuiltInBenchmark: Boolean,
        workloadPreset: String,
        startTimeMillis: Long,
        targetFps: Int,
        samples: List<LiveTelemetrySnapshot>
    ): Long {
        if (samples.isEmpty()) return -1L

        val durationSec = ((samples.last().timestampMs - startTimeMillis) / 1000L)
            .toInt()
            .coerceAtLeast(1)

        val validFps = samples.map { it.frameMetrics.fps }.filter { it > 0f }
        val avgFps = if (validFps.isNotEmpty()) validFps.average().toFloat() else 60f
        val min1PctFps = samples.map { it.frameMetrics.onePercentLowFps }
            .filter { it > 0f }
            .minOrNull() ?: avgFps
        val maxFps = validFps.maxOrNull() ?: avgFps
        val avgFrameTime = samples.map { it.frameMetrics.frameTimeMs }
            .filter { it > 0f }
            .let { if (it.isNotEmpty()) it.average().toFloat() else 16.6f }

        val avgCpu = samples.map { it.cpuUsagePercent }.average().toFloat()
        val peakCpu = samples.maxOf { it.cpuUsagePercent }
        val avgGpu = samples.map { it.gpuUsagePercent }.average().toFloat()
        val peakGpu = samples.maxOf { it.gpuUsagePercent }

        val avgRam = samples.map { it.ramUsedMb }.average().toLong()
        val peakRam = samples.maxOf { it.ramUsedMb }
        val totalRam = samples.last().ramTotalMb.coerceAtLeast(1L)

        val startTemp = samples.first().batteryTempC
        val peakTemp = samples.maxOf { it.batteryTempC }
        val maxThermalSample = samples.maxByOrNull { it.thermalSeverity }
        val thermalMaxLabel = maxThermalSample?.thermalStatusLabel ?: "Nominal"
        val maxThermalSeverity = maxThermalSample?.thermalSeverity ?: 0

        val ramPeakRatio = peakRam.toFloat() / totalRam.toFloat()
        val bottleneck = diagnoseBottleneck(
            avgFps = avgFps,
            min1PctFps = min1PctFps,
            targetFps = targetFps,
            avgCpu = avgCpu,
            avgGpu = avgGpu,
            ramPeakRatio = ramPeakRatio,
            peakTemp = peakTemp,
            thermalSeverity = maxThermalSeverity
        )

        val stabilityScore = computeStabilityScore(
            avgFps = avgFps,
            min1PctFps = min1PctFps,
            targetFps = targetFps,
            thermalSeverity = maxThermalSeverity,
            ramPeakRatio = ramPeakRatio
        )

        val entity = GameSessionEntity(
            gameName = gameName,
            packageName = packageName,
            isBuiltInBenchmark = isBuiltInBenchmark,
            workloadPreset = workloadPreset,
            startTimeMillis = startTimeMillis,
            durationSeconds = durationSec,
            avgFps = avgFps,
            min1PercentFps = min1PctFps,
            maxFps = maxFps,
            avgFrameTimeMs = avgFrameTime,
            avgCpuPercent = avgCpu,
            peakCpuPercent = peakCpu,
            avgGpuPercent = avgGpu,
            peakGpuPercent = peakGpu,
            avgRamUsedMb = avgRam,
            peakRamUsedMb = peakRam,
            totalRamMb = totalRam,
            startTempC = startTemp,
            peakTempC = peakTemp,
            thermalStatusMax = thermalMaxLabel,
            bottleneckSummary = bottleneck,
            stabilityScore = stabilityScore
        )

        val sessionId = sessionDao.insertSession(entity)
        val sampleEntities = samples.map { snap ->
            val elapsed = ((snap.timestampMs - startTimeMillis) / 1000L).toInt().coerceAtLeast(0)
            TelemetrySampleEntity(
                sessionId = sessionId,
                elapsedSeconds = elapsed,
                fps = snap.frameMetrics.fps,
                frameTimeMs = snap.frameMetrics.frameTimeMs,
                cpuPercent = snap.cpuUsagePercent,
                cpuAvgMhz = snap.cpuAvgMhz,
                gpuPercent = snap.gpuUsagePercent,
                gpuClockMhz = snap.gpuClockMhz,
                ramUsedMb = snap.ramUsedMb,
                tempC = snap.batteryTempC,
                powerWatts = snap.powerWatts
            )
        }
        sessionDao.insertSamples(sampleEntities)
        return sessionId
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSessionById(sessionId)
    }

    suspend fun clearAllSessions() {
        sessionDao.clearAllSessions()
    }

    suspend fun buildCsvExport(session: GameSessionEntity): String {
        val samples = sessionDao.getSamplesForSessionOnce(session.id)
        val sb = StringBuilder()
        sb.appendLine("CorePulse Reporte de Telemetria - ${session.gameName}")
        sb.appendLine("Paquete,${session.packageName}")
        sb.appendLine("Perfil,${session.workloadPreset}")
        sb.appendLine("Duracion (s),${session.durationSeconds}")
        sb.appendLine("FPS Promedio,${String.format("%.1f", session.avgFps)}")
        sb.appendLine("1% Low FPS,${String.format("%.1f", session.min1PercentFps)}")
        sb.appendLine("CPU Prom/Pico (%),${String.format("%.1f", session.avgCpuPercent)} / ${String.format("%.1f", session.peakCpuPercent)}")
        sb.appendLine("GPU Prom/Pico (%),${String.format("%.1f", session.avgGpuPercent)} / ${String.format("%.1f", session.peakGpuPercent)}")
        sb.appendLine("RAM Pico (MB),${session.peakRamUsedMb} / ${session.totalRamMb}")
        sb.appendLine("Temp Pico (C),${String.format("%.1f", session.peakTempC)}")
        sb.appendLine("Diagnostico,${session.bottleneckSummary}")
        sb.appendLine("Estabilidad,${session.stabilityScore}/100")
        sb.appendLine()
        sb.appendLine("Segundos,FPS,FrameTime_ms,CPU_pct,CPU_MHz,GPU_pct,GPU_MHz,RAM_MB,Temp_C,Watts")
        for (s in samples) {
            sb.appendLine(
                "${s.elapsedSeconds},${String.format("%.1f", s.fps)},${String.format("%.2f", s.frameTimeMs)}," +
                    "${String.format("%.1f", s.cpuPercent)},${s.cpuAvgMhz},${String.format("%.1f", s.gpuPercent)}," +
                    "${s.gpuClockMhz},${s.ramUsedMb},${String.format("%.1f", s.tempC)},${String.format("%.2f", s.powerWatts)}"
            )
        }
        return sb.toString()
    }

    companion object {
        fun diagnoseBottleneck(
            avgFps: Float,
            min1PctFps: Float,
            targetFps: Int,
            avgCpu: Float,
            avgGpu: Float,
            ramPeakRatio: Float,
            peakTemp: Float,
            thermalSeverity: Int
        ): String {
            return when {
                thermalSeverity >= 3 || peakTemp >= 44f ->
                    "Throttling Térmico: reducción de frecuencias por temperatura elevada."
                ramPeakRatio >= 0.88f ->
                    "Presión de RAM: memoria al límite, riesgo de recargas o tirones."
                avgGpu >= 82f && avgFps < targetFps * 0.88f ->
                    "Limitado por GPU: carga gráfica alta; baja sombras o resolución."
                avgCpu >= 82f && avgGpu < 70f ->
                    "Limitado por CPU: procesador saturado por lógica, físicas o IA."
                min1PctFps < avgFps * 0.65f ->
                    "Micro-stuttering: caídas puntuales en 1% Low pese a buen promedio."
                else ->
                    "Rendimiento Equilibrado: GPU, CPU y RAM operan sin cuello de botella."
            }
        }

        fun computeStabilityScore(
            avgFps: Float,
            min1PctFps: Float,
            targetFps: Int,
            thermalSeverity: Int,
            ramPeakRatio: Float
        ): Int {
            val targetRatio = (avgFps / targetFps.coerceAtLeast(30).toFloat()).coerceIn(0f, 1f)
            val pacingConsistency = if (avgFps > 0f) (min1PctFps / avgFps).coerceIn(0f, 1f) else 1f
            val thermalPenalty = thermalSeverity * 8f
            val ramPenalty = if (ramPeakRatio > 0.85f) (ramPeakRatio - 0.85f) * 80f else 0f
            val raw = (targetRatio * 55f) + (pacingConsistency * 45f) - thermalPenalty - ramPenalty
            return raw.roundToInt().coerceIn(5, 100)
        }
    }
}
