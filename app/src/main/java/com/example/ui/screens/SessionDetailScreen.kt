package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AnalyzerSettings
import com.example.data.GameSessionEntity
import com.example.data.TelemetrySampleEntity
import com.example.ui.components.ChartSeries
import com.example.ui.components.MultiMetricSessionChart
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionDetailScreen(
    session: GameSessionEntity?,
    samples: List<TelemetrySampleEntity>,
    settings: AnalyzerSettings,
    onBack: () -> Unit,
    onShareReport: (GameSessionEntity) -> Unit,
    onDeleteSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current
    var showFps by remember { mutableStateOf(true) }
    var showGpu by remember { mutableStateOf(true) }
    var showCpu by remember { mutableStateOf(true) }
    var showRam by remember { mutableStateOf(true) }
    var showTemp by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.gameName ?: "Detalle de Sesión",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("session_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(
                            onClick = { onShareReport(session) },
                            modifier = Modifier.testTag("share_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Exportar CSV"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Eliminar sesión"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cargando reporte...")
            }
        } else {
            val scoreColor = when {
                session.stabilityScore >= 80 -> telemetry.ram
                session.stabilityScore >= 55 -> telemetry.cpu
                else -> telemetry.thermal
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Stability + Diagnosis Header
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Puntuación de Estabilidad",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${session.workloadPreset} · ${session.durationSeconds}s · ${samples.size} muestras",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${session.stabilityScore}/100",
                                        style = MonoMetricLarge,
                                        color = scoreColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = session.bottleneckSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Multi-Metric Chart
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, telemetry.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Línea Temporal GPU · CPU · RAM · FPS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SeriesToggleChip("FPS", showFps, telemetry.frame) { showFps = !showFps }
                                    SeriesToggleChip("GPU %", showGpu, telemetry.gpu) { showGpu = !showGpu }
                                    SeriesToggleChip("CPU %", showCpu, telemetry.cpu) { showCpu = !showCpu }
                                    SeriesToggleChip("RAM %", showRam, telemetry.ram) { showRam = !showRam }
                                    SeriesToggleChip("Temp", showTemp, telemetry.thermal) { showTemp = !showTemp }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val maxFpsScale = (samples.maxOfOrNull { it.fps } ?: 60f).coerceAtLeast(60f)
                                val totalRam = session.totalRamMb.coerceAtLeast(1L).toFloat()

                                val seriesList = buildList {
                                    if (showFps) add(
                                        ChartSeries(
                                            "FPS",
                                            telemetry.frame,
                                            samples.map { (it.fps / maxFpsScale).coerceIn(0f, 1f) }
                                        )
                                    )
                                    if (showGpu) add(
                                        ChartSeries(
                                            "GPU",
                                            telemetry.gpu,
                                            samples.map { (it.gpuPercent / 100f).coerceIn(0f, 1f) }
                                        )
                                    )
                                    if (showCpu) add(
                                        ChartSeries(
                                            "CPU",
                                            telemetry.cpu,
                                            samples.map { (it.cpuPercent / 100f).coerceIn(0f, 1f) }
                                        )
                                    )
                                    if (showRam) add(
                                        ChartSeries(
                                            "RAM",
                                            telemetry.ram,
                                            samples.map { (it.ramUsedMb / totalRam).coerceIn(0f, 1f) }
                                        )
                                    )
                                    if (showTemp) add(
                                        ChartSeries(
                                            "TEMP",
                                            telemetry.thermal,
                                            samples.map { ((it.tempC - 20f) / 35f).coerceIn(0f, 1f) }
                                        )
                                    )
                                }

                                if (samples.size < 2) {
                                    Text(
                                        text = "Se necesitan al menos 2 muestras para trazar la gráfica.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp)
                                    )
                                } else {
                                    MultiMetricSessionChart(
                                        seriesList = seriesList,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Detailed KPI Summary Grid
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, telemetry.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Resumen Técnico Completo",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                KpiRow(
                                    "FPS Promedio / Máximo",
                                    "${String.format("%.1f", session.avgFps)} / ${String.format("%.1f", session.maxFps)} FPS",
                                    telemetry.frame
                                )
                                KpiRow(
                                    "1% Low FPS · Frame Time",
                                    "${String.format("%.1f", session.min1PercentFps)} FPS · ${String.format("%.1f", session.avgFrameTimeMs)} ms",
                                    telemetry.frame
                                )
                                KpiRow(
                                    "Carga GPU (Prom / Pico)",
                                    "${String.format("%.1f", session.avgGpuPercent)}% / ${String.format("%.1f", session.peakGpuPercent)}%",
                                    telemetry.gpu
                                )
                                KpiRow(
                                    "Carga CPU (Prom / Pico)",
                                    "${String.format("%.1f", session.avgCpuPercent)}% / ${String.format("%.1f", session.peakCpuPercent)}%",
                                    telemetry.cpu
                                )
                                KpiRow(
                                    "RAM (Prom / Pico / Total)",
                                    "${session.avgRamUsedMb} / ${session.peakRamUsedMb} / ${session.totalRamMb} MB",
                                    telemetry.ram
                                )
                                KpiRow(
                                    "Temperatura (Inicio / Pico)",
                                    "${formatTemp(session.startTempC, settings.useFahrenheit)} → ${formatTemp(session.peakTempC, settings.useFahrenheit)}",
                                    telemetry.thermal
                                )
                                KpiRow(
                                    "Estado Térmico Máximo",
                                    session.thermalStatusMax,
                                    telemetry.thermal
                                )
                            }
                        }
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("¿Eliminar sesión?") },
                    text = { Text("Se borrarán los datos y muestras de «${session.gameName}».") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteSession(session.id)
                                onBack()
                            }
                        ) {
                            Text("Eliminar", color = telemetry.thermal)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SeriesToggleChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MonoMetricSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}

@Composable
private fun KpiRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MonoMetricMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
