package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.AnalyzerSettings
import com.example.data.GameSessionEntity
import com.example.telemetry.InstalledGameInfo
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GamesScreen(
    installedApps: List<InstalledGameInfo>,
    isScanning: Boolean,
    sessions: List<GameSessionEntity>,
    settings: AnalyzerSettings,
    onRefreshGames: () -> Unit,
    onLaunchAndMonitor: (InstalledGameInfo) -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onGoToBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current
    var showOnlyTaggedGames by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, showOnlyTaggedGames, searchQuery) {
        val taggedGames = installedApps.filter { it.isTaggedGame }
        val base = if (showOnlyTaggedGames && taggedGames.isNotEmpty()) taggedGames else installedApps
        if (searchQuery.isBlank()) base
        else base.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Juegos & Apps Instaladas",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lanza un juego mientras CorePulse registra CPU, RAM y temperatura",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshGames,
                        modifier = Modifier.testTag("refresh_games_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Escanear juegos",
                            tint = telemetry.gpu
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showOnlyTaggedGames,
                        onClick = { showOnlyTaggedGames = true },
                        modifier = Modifier.testTag("filter_games_only"),
                        label = {
                            val gameCount = installedApps.count { it.isTaggedGame }
                            Text("Juegos ($gameCount)")
                        }
                    )
                    FilterChip(
                        selected = !showOnlyTaggedGames,
                        onClick = { showOnlyTaggedGames = false },
                        modifier = Modifier.testTag("filter_all_apps"),
                        label = { Text("Todas (${installedApps.size})") }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar juego o paquete...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Buscar"
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_games_input")
                )
            }

            if (filteredApps.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, telemetry.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = telemetry.gpu,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isScanning) "Buscando juegos instalados..." else "No se detectaron juegos con esa categoría",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Puedes cambiar a «Todas» para seleccionar cualquier app o usar el Lab 3D integrado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredApps.take(20), key = { it.packageName }) { app ->
                            InstalledGameCard(
                                game = app,
                                onLaunchAndMonitor = { onLaunchAndMonitor(app) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Historial de Sesiones y Benchmarks (${sessions.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, telemetry.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assessment,
                                contentDescription = null,
                                tint = telemetry.cpu,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Sin sesiones registradas aún",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Graba una sesión desde Monitor o ejecuta un benchmark en Lab 3D para analizar FPS, GPU, CPU y RAM.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onGoToBenchmark,
                                modifier = Modifier.testTag("empty_go_benchmark_button")
                            ) {
                                Text("Ir al Lab 3D")
                            }
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        useFahrenheit = settings.useFahrenheit,
                        onClick = { onOpenSessionDetail(session.id) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun InstalledGameCard(
    game: InstalledGameInfo,
    onLaunchAndMonitor: () -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val iconBitmap = remember(game.packageName) {
        runCatching { game.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

    Card(
        modifier = Modifier.width(172.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, telemetry.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = game.appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = game.appName,
                        tint = telemetry.gpu,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.appName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (game.isTaggedGame) "Juego" else "App",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (game.isTaggedGame) telemetry.ram else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onLaunchAndMonitor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_game_${game.packageName}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = telemetry.gpu.copy(alpha = 0.16f),
                    contentColor = telemetry.gpu
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lanzar y Medir", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: GameSessionEntity,
    useFahrenheit: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val telemetry = LocalTelemetryColors.current
    val scoreColor = when {
        session.stabilityScore >= 80 -> telemetry.ram
        session.stabilityScore >= 55 -> telemetry.cpu
        else -> telemetry.thermal
    }
    val dateStr = remember(session.startTimeMillis) {
        SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(session.startTimeMillis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("session_card_${session.id}"),
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
                        text = session.gameName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$dateStr · ${session.durationSeconds}s · ${session.workloadPreset}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scoreColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "${session.stabilityScore}/100",
                            style = MonoMetricSmall,
                            color = scoreColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_session_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar sesión",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMini("FPS", "${session.avgFps.toInt()} (${session.min1PercentFps.toInt()})", telemetry.frame)
                MetricMini("GPU", "${session.avgGpuPercent.toInt()}%", telemetry.gpu)
                MetricMini("CPU", "${session.avgCpuPercent.toInt()}%", telemetry.cpu)
                MetricMini("RAM", "${session.peakRamUsedMb} MB", telemetry.ram)
                MetricMini("TEMP", formatTemp(session.peakTempC, useFahrenheit), telemetry.thermal)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = session.bottleneckSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricMini(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
