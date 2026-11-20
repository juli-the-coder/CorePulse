package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TelemetryColors(
    val gpu: Color,
    val cpu: Color,
    val ram: Color,
    val thermal: Color,
    val frame: Color,
    val gridLine: Color,
    val cardBorder: Color,
    val bannerOverlay: Color
)

val LocalTelemetryColors = staticCompositionLocalOf {
    TelemetryColors(
        gpu = GpuCyan,
        cpu = CpuAmber,
        ram = RamEmerald,
        thermal = ThermalCoral,
        frame = FrameViolet,
        gridLine = OutlineDark,
        cardBorder = OutlineDark,
        bannerOverlay = ObsidianBg
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = GpuCyan,
    onPrimary = ObsidianBg,
    primaryContainer = SurfaceElevatedDark,
    onPrimaryContainer = GpuCyan,
    secondary = CpuAmber,
    onSecondary = ObsidianBg,
    secondaryContainer = SurfaceElevatedDark,
    onSecondaryContainer = CpuAmber,
    tertiary = RamEmerald,
    onTertiary = ObsidianBg,
    tertiaryContainer = SurfaceElevatedDark,
    onTertiaryContainer = RamEmerald,
    error = ThermalCoral,
    onError = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark.copy(alpha = 0.6f)
)

private val LightColorScheme = lightColorScheme(
    primary = GpuCyanLight,
    onPrimary = Color.White,
    primaryContainer = SurfaceContainerLight,
    onPrimaryContainer = GpuCyanLight,
    secondary = CpuAmberLight,
    onSecondary = Color.White,
    secondaryContainer = SurfaceContainerLight,
    onSecondaryContainer = CpuAmberLight,
    tertiary = RamEmeraldLight,
    onTertiary = Color.White,
    tertiaryContainer = SurfaceContainerLight,
    onTertiaryContainer = RamEmeraldLight,
    error = ThermalCoralLight,
    onError = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceElevatedLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight.copy(alpha = 0.7f)
)

val CorePulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

enum class AppThemeMode {
    DARK_PRO,
    SYSTEM,
    LIGHT
}

@Composable
fun CorePulseTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_PRO,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK_PRO -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val telemetryColors = if (useDark) {
        TelemetryColors(
            gpu = GpuCyan,
            cpu = CpuAmber,
            ram = RamEmerald,
            thermal = ThermalCoral,
            frame = FrameViolet,
            gridLine = OutlineDark,
            cardBorder = OutlineDark,
            bannerOverlay = ObsidianBg
        )
    } else {
        TelemetryColors(
            gpu = GpuCyanLight,
            cpu = CpuAmberLight,
            ram = RamEmeraldLight,
            thermal = ThermalCoralLight,
            frame = FrameVioletLight,
            gridLine = OutlineLight,
            cardBorder = OutlineLight,
            bannerOverlay = TextPrimaryLight
        )
    }

    CompositionLocalProvider(LocalTelemetryColors provides telemetryColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = CorePulseShapes,
            content = content
        )
    }
}
