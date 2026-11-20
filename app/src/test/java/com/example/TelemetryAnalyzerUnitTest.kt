package com.example

import com.example.data.TelemetryRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryAnalyzerUnitTest {

    @Test
    fun diagnoseBottleneck_detectsThermalThrottling() {
        val diagnosis = TelemetryRepository.diagnoseBottleneck(
            avgFps = 45f,
            min1PctFps = 32f,
            targetFps = 60,
            avgCpu = 70f,
            avgGpu = 75f,
            ramPeakRatio = 0.60f,
            peakTemp = 45.2f,
            thermalSeverity = 3
        )
        assertTrue(diagnosis.contains("Térmico"))
    }

    @Test
    fun diagnoseBottleneck_detectsGpuBound() {
        val diagnosis = TelemetryRepository.diagnoseBottleneck(
            avgFps = 40f,
            min1PctFps = 34f,
            targetFps = 60,
            avgCpu = 45f,
            avgGpu = 94f,
            ramPeakRatio = 0.55f,
            peakTemp = 36f,
            thermalSeverity = 0
        )
        assertTrue(diagnosis.contains("GPU"))
    }

    @Test
    fun stabilityScore_isHighWhenBalancedAtTargetFps() {
        val score = TelemetryRepository.computeStabilityScore(
            avgFps = 60f,
            min1PctFps = 57f,
            targetFps = 60,
            thermalSeverity = 0,
            ramPeakRatio = 0.55f
        )
        assertTrue("Expected score >= 90, was $score", score >= 90)
    }
}
