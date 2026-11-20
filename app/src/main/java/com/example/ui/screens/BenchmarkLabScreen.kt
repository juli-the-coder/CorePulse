package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AnalyzerSettings
import com.example.telemetry.BenchmarkPreset
import com.example.telemetry.FrameMetricsTracker
import com.example.telemetry.LiveTelemetrySnapshot
import com.example.ui.components.GameStressGLView
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.viewmodel.BenchmarkLabState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BenchmarkLabScreen(
    benchmarkState: BenchmarkLabState,
    snapshot: LiveTelemetrySnapshot,
    settings: AnalyzerSettings,
    frameMetricsTracker: FrameMetricsTracker,
    onSelectPreset: (BenchmarkPreset) -> Unit,
    onToggleCpuStress: (Boolean) -> Unit,
    onToggleRamStress: (Boolean) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onStartBenchmark: () -> Unit,
    onStopBenchmark: () -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Silicon Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_silicon_banner),
                        contentDescription = "Laboratorio de estrés 3D GPU CPU RAM",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        telemetry.bannerOverlay.copy(alpha = 0.92f),
                                        telemetry.bannerOverlay.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Column {
                            Text(
                                text = "Laboratorio 3D · Estrés de Juego",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Renderizado OpenGL ES en tiempo real + carga de cómputo CPU y memoria RAM para detectar cuellos de botella.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Live 3D OpenGL Viewport with HUD Overlay
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (benchmarkState.isRunning) telemetry.gpu else telemetry.cardBorder
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        GameStressGLView(
                            preset = benchmarkState.preset,
                            isRunning = benchmarkState.isRunning,
                            frameMetricsTracker = frameMetricsTracker,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-left Live HUD
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = telemetry.bannerOverlay.copy(alpha = 0.82f),
                            border = BorderStroke(1.dp, telemetry.gpu.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HudStat("FPS", "${snapshot.frameMetrics.fps.toInt()}", telemetry.gpu)
                                HudStat("1% LOW", "${snapshot.frameMetrics.onePercentLowFps.toInt()}", telemetry.frame)
                                HudStat("FRAME", "${String.format("%.1f", snapshot.frameMetrics.frameTimeMs)}ms", Color.White)
                            }
                        }

                        // Top-right Timer / Status
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = telemetry.bannerOverlay.copy(alpha = 0.82f)
                        ) {
                            val timerText = if (benchmarkState.durationLimitSeconds > 0) {
                                "${benchmarkState.elapsedSeconds}s / ${benchmarkState.durationLimitSeconds}s"
                            } else {
                                "${benchmarkState.elapsedSeconds}s"
                            }
                            Text(
                                text = if (benchmarkState.isRunning) "ACTIVO · $timerText" else "ESPERA",
                                style = MonoMetricSmall,
                                color = if (benchmarkState.isRunning) telemetry.ram else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Bottom Telemetry Strip
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = telemetry.bannerOverlay.copy(alpha = 0.84f),
                            border = BorderStroke(1.dp, telemetry.cardBorder.copy(alpha = 0.7f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                HudStat("GPU", "${snapshot.gpuUsagePercent.toInt()}%", telemetry.gpu)
                                HudStat("CPU", "${snapshot.cpuUsagePercent.toInt()}%", telemetry.cpu)
                                HudStat("RAM", "${snapshot.ramUsedMb} MB", telemetry.ram)
                                HudStat("TEMP", formatTemp(snapshot.batteryTempC, settings.useFahrenheit), telemetry.thermal)
                            }
                        }
                    }
                }
            }

            // Controls Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Perfil de Carga Gráfica",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BenchmarkPreset.entries.forEach { preset ->
                                val selected = benchmarkState.preset == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectPreset(preset) },
                                    modifier = Modifier.testTag("preset_chip_${preset.name.lowercase()}"),
                                    label = {
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = telemetry.gpu.copy(alpha = 0.2f),
                                        selectedLabelColor = telemetry.gpu
                                    )
                                )
                            }
                        }

                        Text(
                            text = benchmarkState.preset.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Duración de Prueba",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15 to "15 s", 30 to "30 s", 60 to "60 s", 0 to "Libre").forEach { (sec, label) ->
                                FilterChip(
                                    selected = benchmarkState.durationLimitSeconds == sec,
                                    onClick = { onSelectDuration(sec) },
                                    enabled = !benchmarkState.isRunning,
                                    modifier = Modifier.testTag("duration_chip_$sec"),
                                    label = { Text(label, style = MonoMetricSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Carga Multi-Hilo CPU",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Simula hilos de físicas e IA (${benchmarkState.preset.cpuWorkerThreads} hilos)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = benchmarkState.cpuStressEnabled,
                                onCheckedChange = onToggleCpuStress,
                                modifier = Modifier.testTag("cpu_stress_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Presión de Memoria RAM",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Reserva temporal de búferes (${benchmarkState.preset.ramBufferMb} MB)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = benchmarkState.ramStressEnabled,
                                onCheckedChange = onToggleRamStress,
                                modifier = Modifier.testTag("ram_stress_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (benchmarkState.isRunning) onStopBenchmark() else onStartBenchmark()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("benchmark_toggle_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (benchmarkState.isRunning) telemetry.thermal else telemetry.gpu,
                                contentColor = if (benchmarkState.isRunning) Color.White else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (benchmarkState.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (benchmarkState.isRunning) {
                                    "Detener y Guardar Resultados"
                                } else {
                                    "Iniciar Benchmark 3D"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Diagnosis Result Card
            if (benchmarkState.latestDiagnosis != null) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, telemetry.ram.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = "Diagnóstico",
                                    tint = telemetry.ram
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Diagnóstico de Cuello de Botella",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = telemetry.ram
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = benchmarkState.latestDiagnosis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (benchmarkState.lastSavedSessionId != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { onOpenSessionDetail(benchmarkState.lastSavedSessionId) },
                                    modifier = Modifier.testTag("open_benchmark_report_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Analytics,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ver Gráfica y Reporte Completo")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HudStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MonoMetricMedium,
            color = color
        )
    }
}
