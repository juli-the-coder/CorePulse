package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AnalyzerSettings
import com.example.telemetry.DeviceSpecs
import com.example.telemetry.LiveTelemetrySnapshot
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricSmall

@Composable
fun HardwareSettingsScreen(
    specs: DeviceSpecs,
    snapshot: LiveTelemetrySnapshot,
    settings: AnalyzerSettings,
    onUpdateSamplingMs: (Int) -> Unit,
    onUpdateTargetFps: (Int) -> Unit,
    onUpdateUseFahrenheit: (Boolean) -> Unit,
    onUpdateLiveNotification: (Boolean) -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry = LocalTelemetryColors.current
    val context = LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateLiveNotification(granted)
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
            // GPU Specs
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Layers, contentDescription = null, tint = telemetry.gpu)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESPECIFICACIONES GPU",
                                style = MaterialTheme.typography.labelLarge,
                                color = telemetry.gpu
                            )
                        }
                        SpecLine("Renderizador", specs.gpuInfo.renderer)
                        SpecLine("Fabricante", specs.gpuInfo.vendor)
                        SpecLine("OpenGL ES", specs.gpuInfo.glVersion)
                        SpecLine("GLSL", specs.gpuInfo.glslVersion)
                        SpecLine("Vulkan", specs.gpuInfo.vulkanVersionLabel)
                        SpecLine("Textura Máxima", "${specs.gpuInfo.maxTextureSize} px")
                        SpecLine("Extensiones GL", "${specs.gpuInfo.extensionCount}")
                    }
                }
            }

            // CPU & RAM & Display Specs
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DeveloperBoard, contentDescription = null, tint = telemetry.cpu)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SOC · CPU · RAM · PANTALLA",
                                style = MaterialTheme.typography.labelLarge,
                                color = telemetry.cpu
                            )
                        }
                        SpecLine("Dispositivo", specs.deviceName)
                        SpecLine("CPU / Procesador", specs.cpuModelName.ifBlank { specs.socModel })
                        SpecLine("SoC / Board", "${specs.socModel} (${specs.board})")
                        SpecLine("ABIs Soportadas", specs.abiList)
                        SpecLine("Topología CPU", specs.coreTopologySummary)
                        SpecLine("RAM Total / Swap", "${specs.totalRamMb} MB / ${specs.swapTotalMb} MB")
                        SpecLine(
                            "Pantalla",
                            "${specs.displayWidthPx}×${specs.displayHeightPx} (${specs.supportedRefreshRates.joinToString("/") { "${it.toInt()}Hz" }})"
                        )
                    }
                }
            }

            // Network & Connectivity Details
            item {
                val net = snapshot.network
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RED Y CONECTIVIDAD",
                            style = MaterialTheme.typography.labelLarge,
                            color = telemetry.frame
                        )
                        SpecLine("Tipo de Red", net.connectionType)
                        SpecLine("Estado", if (net.isConnected) "Conectado y validado" else "Sin conexión")
                        SpecLine("Interfaz", net.interfaceName)
                        SpecLine("Ancho de banda enlace", if (net.linkDownMbps > 0) "${net.linkDownMbps} / ${net.linkUpMbps} Mbps" else "N/D")
                        SpecLine("Señal / Frecuencia", "${net.wifiRssiDbm?.let { "$it dBm" } ?: "N/D"} · ${net.wifiFreqMhz?.let { "$it MHz" } ?: "-"}")
                    }
                }
            }

            // Kernel / API Telemetry Diagnostics
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sensors, contentDescription = null, tint = telemetry.ram)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "FUENTES DE TELEMETRÍA DEL SISTEMA",
                                style = MaterialTheme.typography.labelLarge,
                                color = telemetry.ram
                            )
                        }
                        SensorStatusRow("Frecuencias CPU (sysfs cpufreq)", specs.cpuFreqReadable)
                        SensorStatusRow("Carga Kernel (/proc/stat)", specs.procStatReadable)
                        SensorStatusRow("Memoria Detallada (/proc/meminfo)", specs.memInfoReadable)
                        SensorStatusRow("Sysfs GPU Directo (${specs.gpuSysfsPath ?: "FrameMetrics"})", specs.gpuSysfsPath != null)
                        SensorStatusRow("FrameMetrics + Choreographer", true)
                    }
                }
            }

            // Analyzer Preferences
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, telemetry.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, tint = telemetry.frame)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AJUSTES DEL ANALIZADOR",
                                style = MaterialTheme.typography.labelLarge,
                                color = telemetry.frame
                            )
                        }

                        Text("Frecuencia de Muestreo", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500 to "500 ms", 1000 to "1 s", 2000 to "2 s").forEach { (ms, label) ->
                                FilterChip(
                                    selected = settings.samplingIntervalMs.toInt() == ms,
                                    onClick = { onUpdateSamplingMs(ms) },
                                    modifier = Modifier.testTag("sampling_chip_$ms"),
                                    label = { Text(label, style = MonoMetricSmall) }
                                )
                            }
                        }

                        Text("Objetivo de FPS", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60, 90, 120).forEach { fps ->
                                FilterChip(
                                    selected = settings.targetFps == fps,
                                    onClick = { onUpdateTargetFps(fps) },
                                    modifier = Modifier.testTag("target_fps_chip_$fps"),
                                    label = { Text("$fps FPS", style = MonoMetricSmall) }
                                )
                            }
                        }

                        Text("Tema Visual", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                AppThemeMode.DARK_PRO to "Oscuro Pro",
                                AppThemeMode.SYSTEM to "Sistema",
                                AppThemeMode.LIGHT to "Claro"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onUpdateThemeMode(mode) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}"),
                                    label = { Text(label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Temperatura en Fahrenheit (°F)", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Desactivado usa Celsius (°C)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.useFahrenheit,
                                onCheckedChange = onUpdateUseFahrenheit,
                                modifier = Modifier.testTag("fahrenheit_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notificación en Vivo al Jugar", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Muestra CPU, RAM y °C mientras grabas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.enableLiveNotification,
                                onCheckedChange = { enable ->
                                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onUpdateLiveNotification(true)
                                        } else {
                                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        onUpdateLiveNotification(enable)
                                    }
                                },
                                modifier = Modifier.testTag("live_notif_switch")
                            )
                        }

                        OutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("clear_history_button")
                        ) {
                            Text("Borrar Historial de Sesiones")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SpecLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MonoMetricSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun SensorStatusRow(label: String, available: Boolean) {
    val telemetry = LocalTelemetryColors.current
    val badgeColor = if (available) telemetry.ram else telemetry.cpu
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = if (available) "Activo" else "Fallback API",
                style = MonoMetricSmall,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
