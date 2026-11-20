package com.example.telemetry

import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FrameMetricsTracker {
    private val _snapshot = MutableStateFlow(FrameMetricsSnapshot())
    val snapshot: StateFlow<FrameMetricsSnapshot> = _snapshot.asStateFlow()

    private val recentFrameTimesMs = ConcurrentLinkedDeque<Float>()
    private val jankCounter = AtomicInteger(0)
    private val totalFramesCounter = AtomicLong(0L)

    @Volatile
    private var lastDrawMs: Float = 0f

    @Volatile
    private var lastSyncMs: Float = 0f

    @Volatile
    private var lastCommandMs: Float = 0f

    @Volatile
    private var lastTotalPipelineMs: Float = 0f

    @Volatile
    private var targetBudgetMs: Float = 16.67f

    private var lastFrameNanos: Long = 0L
    private var isTracking = false
    private var attachedWindow: Window? = null
    private var metricsThread: HandlerThread? = null
    private var metricsListener: Window.OnFrameMetricsAvailableListener? = null

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isTracking) return
            if (lastFrameNanos != 0L) {
                val dtMs = ((frameTimeNanos - lastFrameNanos) / 1_000_000f).coerceIn(0.5f, 250f)
                recordFrameInterval(dtMs)
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setTargetRefreshRate(refreshRateHz: Float) {
        if (refreshRateHz > 1f) {
            targetBudgetMs = 1000f / refreshRateHz
        }
    }

    fun start(window: Window?) {
        if (isTracking) return
        isTracking = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(choreographerCallback)

        if (window != null) {
            attachFrameMetrics(window)
        }
    }

    fun stop() {
        isTracking = false
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        detachFrameMetrics()
    }

    fun resetSessionCounters() {
        jankCounter.set(0)
        totalFramesCounter.set(0L)
        recentFrameTimesMs.clear()
    }

    /**
     * Allows the OpenGL 3D Benchmark GLSurfaceView renderer to feed direct GL frame intervals
     * while active so the HUD shows exact GL thread frame pacing.
     */
    fun recordExternalGlFrame(frameDurationMs: Float) {
        recordFrameInterval(frameDurationMs.coerceIn(0.5f, 250f))
    }

    private fun recordFrameInterval(dtMs: Float) {
        totalFramesCounter.incrementAndGet()
        if (dtMs > targetBudgetMs * 1.45f) {
            jankCounter.incrementAndGet()
        }
        recentFrameTimesMs.addLast(dtMs)
        while (recentFrameTimesMs.size > 120) {
            recentFrameTimesMs.pollFirst()
        }
        publishSnapshot()
    }

    private fun attachFrameMetrics(window: Window) {
        if (attachedWindow === window) return
        detachFrameMetrics()
        val thread = HandlerThread("CorePulseFrameMetrics").apply { start() }
        val handler = Handler(thread.looper)
        val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            val drawNs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            val syncNs = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
            val cmdNs = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
            val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)

            if (drawNs >= 0) lastDrawMs = drawNs / 1_000_000f
            if (syncNs >= 0) lastSyncMs = syncNs / 1_000_000f
            if (cmdNs >= 0) lastCommandMs = cmdNs / 1_000_000f
            if (totalNs >= 0) lastTotalPipelineMs = totalNs / 1_000_000f
        }
        runCatching {
            window.addOnFrameMetricsAvailableListener(listener, handler)
            attachedWindow = window
            metricsThread = thread
            metricsListener = listener
        }.onFailure {
            thread.quitSafely()
        }
    }

    private fun detachFrameMetrics() {
        val w = attachedWindow
        val l = metricsListener
        if (w != null && l != null) {
            runCatching { w.removeOnFrameMetricsAvailableListener(l) }
        }
        metricsThread?.quitSafely()
        attachedWindow = null
        metricsListener = null
        metricsThread = null
    }

    private fun publishSnapshot() {
        val list = recentFrameTimesMs.toList()
        if (list.isEmpty()) return
        val recentWindow = list.takeLast(30)
        val avgFrameMs = recentWindow.average().toFloat().coerceAtLeast(0.5f)
        val fps = (1000f / avgFrameMs).coerceIn(1f, 240f)

        val sortedWorst = list.sortedDescending()
        val onePercentCount = (list.size * 0.05f).toInt().coerceAtLeast(1)
        val worstAvgMs = sortedWorst.take(onePercentCount).average().toFloat().coerceAtLeast(avgFrameMs)
        val onePercentLow = (1000f / worstAvgMs).coerceIn(1f, fps)

        _snapshot.value = FrameMetricsSnapshot(
            fps = fps,
            onePercentLowFps = onePercentLow,
            frameTimeMs = avgFrameMs,
            drawDurationMs = lastDrawMs,
            syncDurationMs = lastSyncMs,
            commandIssueMs = lastCommandMs,
            jankCount = jankCounter.get(),
            totalFrames = totalFramesCounter.get(),
            sourceLabel = if (lastCommandMs > 0f || lastDrawMs > 0f) {
                "Choreographer + FrameMetrics"
            } else {
                "Choreographer VSync"
            }
        )
    }
}
