package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall

@Composable
fun TelemetryRingGauge(
    valuePercent: Float,
    accentColor: Color,
    centerValueText: String,
    centerSubText: String,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = 88.dp,
    strokeWidth: Dp = 8.dp
) {
    val clamped = valuePercent.coerceIn(0f, 100f)
    val animatedPct by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 350),
        label = "ring_gauge_anim"
    )
    val trackColor = LocalTelemetryColors.current.gridLine.copy(alpha = 0.45f)

    Box(
        modifier = modifier.size(gaugeSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.6f),
                        accentColor,
                        accentColor
                    )
                ),
                startAngle = 135f,
                sweepAngle = 270f * (animatedPct / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValueText,
                style = MonoMetricMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = centerSubText,
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LiveSparklineChart(
    values: List<Float>,
    accentColor: Color,
    maxValue: Float = 100f,
    modifier: Modifier = Modifier
) {
    val gridColor = LocalTelemetryColors.current.gridLine.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        // Horizontal reference lines
        for (i in 1..3) {
            val y = size.height * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (values.size < 2) return@Canvas
        val safeMax = maxValue.coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { idx, rawVal ->
            val x = idx * stepX
            val norm = (rawVal / safeMax).coerceIn(0f, 1f)
            val y = size.height - (norm * size.height * 0.9f) - (size.height * 0.05f)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((values.size - 1) * stepX, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.30f),
                    accentColor.copy(alpha = 0.02f)
                )
            )
        )
        drawPath(
            path = linePath,
            color = accentColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Live endpoint dot
        val lastNorm = (values.last() / safeMax).coerceIn(0f, 1f)
        val lastY = size.height - (lastNorm * size.height * 0.9f) - (size.height * 0.05f)
        drawCircle(
            color = accentColor,
            radius = 3.5.dp.toPx(),
            center = Offset((values.size - 1) * stepX, lastY)
        )
    }
}

data class ChartSeries(
    val label: String,
    val color: Color,
    val normalizedValues: List<Float> // 0f..1f
)

@Composable
fun MultiMetricSessionChart(
    seriesList: List<ChartSeries>,
    modifier: Modifier = Modifier
) {
    val gridColor = LocalTelemetryColors.current.gridLine.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val rows = 4
        for (i in 0..rows) {
            val y = size.height * (i / rows.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        seriesList.forEach { series ->
            val pts = series.normalizedValues
            if (pts.size >= 2) {
                val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                val path = Path()
                pts.forEachIndexed { idx, norm ->
                    val x = idx * stepX
                    val y = size.height - (norm.coerceIn(0f, 1f) * size.height * 0.92f) - size.height * 0.04f
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = series.color,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
