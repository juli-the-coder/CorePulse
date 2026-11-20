package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.AnalyzerSettings
import com.example.telemetry.CpuCoreSnapshot
import com.example.telemetry.DeviceSpecs
import com.example.telemetry.InstalledGameInfo
import com.example.telemetry.LiveTelemetrySnapshot
import com.example.telemetry.SelectedGameTarget
import com.example.ui.components.LiveSparklineChart
import com.example.ui.components.TelemetryRingGauge
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.viewmodel.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    specs: DeviceSpecs,
    snapshot: LiveTelemetrySnapshot,
    history: List<LiveTelemetrySnapshot>,
    recordingState: RecordingState,
    settings: AnalyzerSettings,
    installedApps: List<InstalledGameInfo>,
    selectedGame: SelectedGameTarget,
    onSelectGameTarget: (InstalledGameInfo?) -> Unit,
    onSelectBuiltInBenchmark: () -> Unit,
    onToggleRecording: () -> Unit,
    onLaunchSelectedGame: () -> Unit,
    onOpenBenchmarkLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current
    var showGameSelectorSheet by remember { mutableStateOf(false) }

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
            item {
                HardwareHeaderStrip(
                    specs = specs,
                    snapshot = snapshot,
                    useFahrenheit = settings.useFahrenheit
                )
            }

            item {
                GameSelectorAndRecorderCard(
                    selectedGame = selectedGame,
                    recordingState = recordingState,
                    onOpenSelector = { showGameSelectorSheet = true },
                    onToggleRecording = onToggleRecording,
                    onLaunchOrBenchmark = {
                        if (selectedGame.isBuiltInBenchmark) {
                            onOpenBenchmarkLab()
                        } else {
                            onLaunchSelectedGame()
                        }
                    }
                )
            }

            // Hero GPU Card
            item {
                PillarTelemetryCard(
                    title = "GPU · PROCESADOR GRÁFICO",
                    subtitle = "${specs.gpuInfo.renderer} · ${specs.gpuInfo.vendor}",
                    sourceTag = snapshot.gpuSource,
                    usagePercent = snapshot.gpuUsagePercent,
                    gaugeCenterText = "${snapshot.gpuUsagePercent.toInt()}%",
                    gaugeSubText = if (snapshot.gpuClockMhz > 0) "${snapshot.gpuClockMhz} MHz" else "Carga",
                    accentColor = telemetry.gpu,
                    sparklineValues = history.map { it.gpuUsagePercent },
                    primaryMetricLabel = "FPS / Frame",
                    primaryMetricValue = "${snapshot.frameMetrics.fps.toInt()} FPS · ${String.format("%.1f", snapshot.frameMetrics.frameTimeMs)} ms",
                    secondaryMetricLabel = "VRAM / Gráficos App",
                    secondaryMetricValue = "${String.format("%.1f", snapshot.graphicsPssMb)} MB",
                    icon = Icons.Outlined.Layers
                )
            }

            // Hero CPU Card
            item {
                PillarTelemetryCard(
                    title = "CPU · PROCESADOR (${specs.coreCount} NÚCLEOS)",
                    subtitle = specs.cpuModelName.ifBlank { specs.socModel },
                    sourceTag = snapshot.cpuSource,
                    usagePercent = snapshot.cpuUsagePercent,
                    gaugeCenterText = "${snapshot.cpuUsagePercent.toInt()}%",
                    gaugeSubText = if (snapshot.cpuAvgMhz > 0) "${snapshot.cpuAvgMhz} MHz" else "Uso",
                    accentColor = telemetry.cpu,
                    sparklineValues = history.map { it.cpuUsagePercent },
                    primaryMetricLabel = "Frecuencia Pico",
                    primaryMetricValue = if (snapshot.cpuMaxMhz > 0) "${snapshot.cpuMaxMhz} MHz" else "Auto",
                    secondaryMetricLabel = "Topología",
                    secondaryMetricValue = specs.coreTopologySummary.ifBlank { specs.abiList },
                    icon = Icons.Outlined.DeveloperBoard
                )
            }

            // Hero RAM Card
            item {
                val ramPct = snapshot.ramUsagePercent
                PillarTelemetryCard(
                    title = "RAM · MEMORIA DEL SISTEMA",
                    subtitle = "${snapshot.ramUsedMb} MB usados de ${snapshot.ramTotalMb} MB",
                    sourceTag = if (specs.memInfoReadable) "ActivityManager + /proc/meminfo" else "ActivityManager",
                    usagePercent = ramPct,
                    gaugeCenterText = "${ramPct.toInt()}%",
                    gaugeSubText = "${String.format("%.1f", snapshot.ramUsedMb / 1024f)} GB",
                    accentColor = telemetry.ram,
                    sparklineValues = history.map { it.ramUsagePercent },
                    primaryMetricLabel = "Disponible",
                    primaryMetricValue = "${snapshot.ramAvailableMb} MB",
                    secondaryMetricLabel = "Caché / Swap",
                    secondaryMetricValue = "${snapshot.ramCachedMb} MB / ${snapshot.ramSwapUsedMb} MB",
                    icon = Icons.Outlined.SdStorage
                )
            }

            // Network Telemetry Card
            item {
                NetworkTelemetryCard(
                    snapshot = snapshot,
                    history = history
                )
            }

            // Frame Pacing & Pipeline Breakdown
            item {
                FramePacingCard(
                    snapshot = snapshot,
                    targetFps = settings.targetFps,
                    fpsHistory = history.map { it.frameMetrics.fps }
                )
            }

            // Per-Core Frequency Matrix
            if (snapshot.cores.isNotEmpty()) {
                item {
                    PerCoreMatrixCard(cores = snapshot.cores)
                }
            }

            // Memory PSS + Thermal & Power Telemetry
            item {
                ThermalAndMemoryCard(
                    snapshot = snapshot,
                    useFahrenheit = settings.useFahrenheit
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showGameSelectorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGameSelectorSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            GameSelectorSheetContent(
                installedApps = installedApps,
                selectedPackage = selectedGame.packageName,
                selectedIsBenchmark = selectedGame.isBuiltInBenchmark,
                onSelectGeneral = {
                    onSelectGameTarget(null)
                    showGameSelectorSheet = false
                },
                onSelectBenchmark = {
                    onSelectBuiltInBenchmark()
                    showGameSelectorSheet = false
                },
                onSelectApp = { app ->
                    onSelectGameTarget(app)
                    showGameSelectorSheet = false
                }
            )
        }
    }
}

@Composable
private fun HardwareHeaderStrip(
    specs: DeviceSpecs,
    snapshot: LiveTelemetrySnapshot,
    useFahrenheit: Boolean
) {
    val telemetry = LocalTelemetryColors.current
    val tempDisplay = formatTemp(snapshot.batteryTempC, useFahrenheit)
    val thermalColor = when {
        snapshot.thermalSeverity >= 3 || snapshot.batteryTempC >= 43f -> telemetry.thermal
        snapshot.thermalSeverity >= 2 || snapshot.batteryTempC >= 38f -> telemetry.cpu
        else -> telemetry.ram
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = specs.deviceName.ifBlank { "Dispositivo Android" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${specs.displayWidthPx}×${specs.displayHeightPx} @ ${specs.refreshRateHz.toInt()} Hz · ${specs.abiList.substringBefore(',')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = thermalColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, thermalColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Thermostat,
                            contentDescription = "Temperatura",
                            tint = thermalColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tempDisplay,
                            style = MonoMetricSmall,
                            color = thermalColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Identity Grid: GPU Name, CPU Name, RAM, Network
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HardwareIdentityRow(
                    tag = "GPU",
                    value = "${specs.gpuInfo.renderer} (${specs.gpuInfo.vendor})",
                    color = telemetry.gpu
                )
                HardwareIdentityRow(
                    tag = "CPU",
                    value = specs.cpuModelName.ifBlank { specs.socModel },
                    color = telemetry.cpu
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        label = "RAM: ${String.format("%.1f", specs.totalRamMb / 1024f)} GB (${specs.totalRamMb} MB)",
                        color = telemetry.ram,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        label = "Red: ${snapshot.network.connectionType}" +
                            (snapshot.network.wifiRssiDbm?.let { " ($it dBm)" } ?: ""),
                        color = telemetry.frame,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(
                    label = "Térmico: ${snapshot.thermalStatusLabel}",
                    color = thermalColor,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(
                    label = if (snapshot.powerWatts > 0.05f) {
                        "${String.format("%.2f", snapshot.powerWatts)} W · ${snapshot.batteryLevelPercent}%"
                    } else {
                        "Bat: ${snapshot.batteryLevelPercent}% · ${snapshot.voltageMv} mV"
                    },
                    color = telemetry.gpu,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HardwareIdentityRow(tag: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.16f)
            ) {
                Text(
                    text = tag,
                    style = MonoMetricSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MonoMetricSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GameSelectorAndRecorderCard(
    selectedGame: SelectedGameTarget,
    recordingState: RecordingState,
    onOpenSelector: () -> Unit,
    onToggleRecording: () -> Unit,
    onLaunchOrBenchmark: () -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val borderColor by animateColorAsState(
        targetValue = if (recordingState.isRecording) telemetry.thermal else telemetry.cardBorder,
        label = "rec_border"
    )
    val selectedIconBitmap = remember(selectedGame.packageName) {
        runCatching { selectedGame.icon?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECTOR DE JUEGO Y SESIÓN",
                    style = MaterialTheme.typography.labelMedium,
                    color = telemetry.gpu
                )
                if (recordingState.isRecording) {
                    val mins = recordingState.elapsedSeconds / 60
                    val secs = recordingState.elapsedSeconds % 60
                    Text(
                        text = String.format("REC %02d:%02d · %d m", mins, secs, recordingState.sampleCount),
                        style = MonoMetricSmall,
                        color = telemetry.thermal,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Game Selector Picker Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, telemetry.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !recordingState.isRecording, onClick = onOpenSelector)
                    .testTag("open_game_selector_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedIconBitmap != null) {
                        Image(
                            bitmap = selectedIconBitmap,
                            contentDescription = selectedGame.name,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(telemetry.gpu.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedGame.isBuiltInBenchmark) Icons.Outlined.ViewInAr else Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = telemetry.gpu,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedGame.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedGame.isExternalGame) {
                                selectedGame.packageName
                            } else if (selectedGame.isBuiltInBenchmark) {
                                "Prueba gráfica OpenGL ES integrada"
                            } else {
                                "Toca para elegir un juego instalado"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Elegir juego",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("record_toggle_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recordingState.isRecording) telemetry.thermal else MaterialTheme.colorScheme.primary,
                        contentColor = if (recordingState.isRecording) Color.White else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (recordingState.isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (recordingState.isRecording) "Guardar Sesión" else "Grabar",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (selectedGame.isExternalGame || selectedGame.isBuiltInBenchmark) {
                    OutlinedButton(
                        onClick = onLaunchOrBenchmark,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("launch_selected_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedGame.isBuiltInBenchmark) "Abrir Lab 3D" else "Lanzar y Medir",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSelectorSheetContent(
    installedApps: List<InstalledGameInfo>,
    selectedPackage: String,
    selectedIsBenchmark: Boolean,
    onSelectGeneral: () -> Unit,
    onSelectBenchmark: () -> Unit,
    onSelectApp: (InstalledGameInfo) -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    var query by remember { mutableStateOf("") }
    var onlyTaggedGames by remember { mutableStateOf(false) }

    val filtered = remember(installedApps, query, onlyTaggedGames) {
        val base = if (onlyTaggedGames) installedApps.filter { it.isTaggedGame } else installedApps
        if (query.isBlank()) base
        else base.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Seleccionar Juego o Perfil",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar juego o aplicación...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_search_game_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onlyTaggedGames,
                onClick = { onlyTaggedGames = false },
                label = { Text("Todas (${installedApps.size})") }
            )
            FilterChip(
                selected = onlyTaggedGames,
                onClick = { onlyTaggedGames = true },
                label = { Text("Solo Juegos (${installedApps.count { it.isTaggedGame }})") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SelectorOptionRow(
                    title = "Monitor General del Dispositivo",
                    subtitle = "Telemetría libre sin juego específico",
                    isSelected = selectedPackage == "device.monitor" && !selectedIsBenchmark,
                    icon = Icons.Filled.Speed,
                    accent = telemetry.ram,
                    onClick = onSelectGeneral
                )
            }
            item {
                SelectorOptionRow(
                    title = "CorePulse 3D Lab (Integrado)",
                    subtitle = "Banco de pruebas OpenGL ES + CPU + RAM",
                    isSelected = selectedIsBenchmark,
                    icon = Icons.Outlined.ViewInAr,
                    accent = telemetry.gpu,
                    onClick = onSelectBenchmark
                )
            }
            items(filtered, key = { it.packageName }) { app ->
                val bmp = remember(app.packageName) {
                    runCatching { app.icon?.toBitmap(72, 72)?.asImageBitmap() }.getOrNull()
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedPackage == app.packageName) {
                        telemetry.gpu.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectApp(app) }
                        .testTag("selector_app_${app.packageName}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = telemetry.gpu,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (app.isTaggedGame) "Juego · ${app.packageName}" else app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selectedPackage == app.packageName) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = telemetry.gpu
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SelectorOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Seleccionado", tint = accent)
            }
        }
    }
}

@Composable
private fun NetworkTelemetryCard(
    snapshot: LiveTelemetrySnapshot,
    history: List<LiveTelemetrySnapshot>
) {
    val telemetry = LocalTelemetryColors.current
    val net = snapshot.network
    val netAccent = telemetry.frame
    val trafficSeries = history.map { it.network.downloadKbps + it.network.uploadKbps }
    val maxTraffic = (trafficSeries.maxOrNull() ?: 100f).coerceAtLeast(128f)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (net.connectionType == "Wi-Fi") Icons.Filled.Wifi else Icons.Outlined.Public,
                        contentDescription = "Red",
                        tint = netAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RED · ${net.connectionType.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = netAccent
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (net.isConnected) telemetry.ram else telemetry.thermal).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = buildString {
                            append(if (net.isConnected) "En línea" else "Sin Internet")
                            if (net.isVpn) append(" · VPN")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (net.isConnected) telemetry.ram else telemetry.thermal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bajada",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRate(net.downloadKbps),
                        style = MonoMetricMedium,
                        color = telemetry.gpu
                    )
                }
                Column {
                    Text(
                        text = "Subida",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRate(net.uploadKbps),
                        style = MonoMetricMedium,
                        color = telemetry.cpu
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Enlace Máx.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (net.linkDownMbps > 0) "${net.linkDownMbps}/${net.linkUpMbps} Mbps" else "N/D",
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LiveSparklineChart(
                values = trafficSeries,
                accentColor = netAccent,
                maxValue = maxTraffic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipelinePill("Interfaz", net.interfaceName, Modifier.weight(1f))
                PipelinePill(
                    "Señal",
                    net.wifiRssiDbm?.let { "$it dBm" } ?: "N/D",
                    Modifier.weight(1f)
                )
                PipelinePill(
                    "Banda",
                    net.wifiFreqMhz?.let { if (it >= 4900) "5/6 GHz" else "2.4 GHz" }
                        ?: if (net.isMetered) "Medida" else "Libre",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatRate(kbps: Float): String {
    return if (kbps >= 1024f) {
        String.format("%.2f MB/s", kbps / 1024f)
    } else {
        String.format("%.1f KB/s", kbps)
    }
}

@Composable
private fun PillarTelemetryCard(
    title: String,
    subtitle: String,
    sourceTag: String,
    usagePercent: Float,
    gaugeCenterText: String,
    gaugeSubText: String,
    accentColor: Color,
    sparklineValues: List<Float>,
    primaryMetricLabel: String,
    primaryMetricValue: String,
    secondaryMetricLabel: String,
    secondaryMetricValue: String,
    icon: ImageVector
) {
    val telemetry = LocalTelemetryColors.current

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = sourceTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryRingGauge(
                    valuePercent = usagePercent,
                    accentColor = accentColor,
                    centerValueText = gaugeCenterText,
                    centerSubText = gaugeSubText,
                    gaugeSize = 86.dp,
                    strokeWidth = 8.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    LiveSparklineChart(
                        values = sparklineValues,
                        accentColor = accentColor,
                        maxValue = 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = primaryMetricValue,
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = secondaryMetricLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = secondaryMetricValue,
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FramePacingCard(
    snapshot: LiveTelemetrySnapshot,
    targetFps: Int,
    fpsHistory: List<Float>
) {
    val telemetry = LocalTelemetryColors.current
    val fm = snapshot.frameMetrics
    val fpsColor = when {
        fm.fps >= targetFps * 0.9f -> telemetry.ram
        fm.fps >= targetFps * 0.65f -> telemetry.cpu
        else -> telemetry.thermal
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Frame pacing",
                        tint = telemetry.frame,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FRAME PACING & LATENCIA GPU",
                        style = MaterialTheme.typography.labelLarge,
                        color = telemetry.frame
                    )
                }
                Text(
                    text = "Meta: $targetFps FPS",
                    style = MonoMetricSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "${fm.fps.toInt()}",
                        style = MonoMetricLarge,
                        color = fpsColor
                    )
                    Text(
                        text = "FPS EN VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${fm.onePercentLowFps.toInt()} FPS",
                        style = MonoMetricMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1% LOW",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${String.format("%.1f", fm.frameTimeMs)} ms",
                        style = MonoMetricMedium,
                        color = telemetry.gpu
                    )
                    Text(
                        text = "FRAME TIME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${fm.jankCount}",
                        style = MonoMetricMedium,
                        color = if (fm.jankCount > 0) telemetry.thermal else telemetry.ram
                    )
                    Text(
                        text = "JANK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LiveSparklineChart(
                values = fpsHistory,
                accentColor = telemetry.frame,
                maxValue = targetFps.toFloat().coerceAtLeast(60f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipelinePill("Draw", "${String.format("%.2f", fm.drawDurationMs)} ms", Modifier.weight(1f))
                PipelinePill("Sync", "${String.format("%.2f", fm.syncDurationMs)} ms", Modifier.weight(1f))
                PipelinePill("GPU Cmd", "${String.format("%.2f", fm.commandIssueMs)} ms", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PipelinePill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PerCoreMatrixCard(cores: List<CpuCoreSnapshot>) {
    val telemetry = LocalTelemetryColors.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Frecuencias por núcleo",
                    tint = telemetry.cpu,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FRECUENCIA POR NÚCLEO CPU",
                    style = MaterialTheme.typography.labelLarge,
                    color = telemetry.cpu
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rows = cores.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { core ->
                            CoreCell(core = core, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreCell(core: CpuCoreSnapshot, modifier: Modifier = Modifier) {
    val telemetry = LocalTelemetryColors.current
    val barColor = when {
        core.usagePercent >= 85f -> telemetry.thermal
        core.usagePercent >= 50f -> telemetry.cpu
        else -> telemetry.ram
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU${core.coreIndex} · ${core.clusterLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (core.currentMhz > 0) "${core.currentMhz} MHz" else "N/D",
                    style = MonoMetricSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (core.usagePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = telemetry.gridLine.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ThermalAndMemoryCard(
    snapshot: LiveTelemetrySnapshot,
    useFahrenheit: Boolean
) {
    val telemetry = LocalTelemetryColors.current
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = "Memoria y energía",
                    tint = telemetry.ram,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MEMORIA DETALLADA & ENERGÍA",
                    style = MaterialTheme.typography.labelLarge,
                    color = telemetry.ram
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipelinePill("PSS App", "${String.format("%.1f", snapshot.appPssMb)} MB", Modifier.weight(1f))
                PipelinePill("Heap Nativo", "${String.format("%.1f", snapshot.nativeHeapMb)} MB", Modifier.weight(1f))
                PipelinePill("ART Heap", "${String.format("%.1f", snapshot.dalvikHeapMb)} MB", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipelinePill("Temp. Bat", formatTemp(snapshot.batteryTempC, useFahrenheit), Modifier.weight(1f))
                PipelinePill(
                    "Corriente",
                    if (snapshot.currentMa > 0f) "${snapshot.currentMa.toInt()} mA" else "N/D",
                    Modifier.weight(1f)
                )
                PipelinePill(
                    "Margen Térm.",
                    snapshot.thermalHeadroom?.let { "${String.format("%.0f", it * 100)}%" } ?: snapshot.thermalStatusLabel,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

fun formatTemp(celsius: Float, useFahrenheit: Boolean): String {
    return if (useFahrenheit) {
        val f = celsius * 9f / 5f + 32f
        String.format("%.1f °F", f)
    } else {
        String.format("%.1f °C", celsius)
    }
}
