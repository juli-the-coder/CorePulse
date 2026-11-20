package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameName: String,
    val packageName: String,
    val isBuiltInBenchmark: Boolean,
    val workloadPreset: String,
    val startTimeMillis: Long,
    val durationSeconds: Int,
    val avgFps: Float,
    val min1PercentFps: Float,
    val maxFps: Float,
    val avgFrameTimeMs: Float,
    val avgCpuPercent: Float,
    val peakCpuPercent: Float,
    val avgGpuPercent: Float,
    val peakGpuPercent: Float,
    val avgRamUsedMb: Long,
    val peakRamUsedMb: Long,
    val totalRamMb: Long,
    val startTempC: Float,
    val peakTempC: Float,
    val thermalStatusMax: String,
    val bottleneckSummary: String,
    val stabilityScore: Int
)

@Entity(
    tableName = "telemetry_samples",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val elapsedSeconds: Int,
    val fps: Float,
    val frameTimeMs: Float,
    val cpuPercent: Float,
    val cpuAvgMhz: Int,
    val gpuPercent: Float,
    val gpuClockMhz: Int,
    val ramUsedMb: Long,
    val tempC: Float,
    val powerWatts: Float
)
