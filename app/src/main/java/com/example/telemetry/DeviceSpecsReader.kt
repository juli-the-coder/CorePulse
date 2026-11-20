package com.example.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeviceSpecsReader(private val context: Context) {

    suspend fun readDeviceSpecs(): DeviceSpecs = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val coreMaxList = (0 until coreCount).map { idx ->
            readSysInt("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq")?.div(1000) ?: 0
        }
        val cpuFreqReadable = coreMaxList.any { it > 0 }
        val topologySummary = buildTopologySummary(coreCount, coreMaxList)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)

        val memInfoFile = File("/proc/meminfo")
        val memInfoReadable = memInfoFile.canRead()
        val swapTotalKb = if (memInfoReadable) {
            readProcMemValueKb(memInfoFile, "SwapTotal:") ?: 0L
        } else 0L

        val procStatReadable = File("/proc/stat").canRead()
        val gpuSysfsPath = findReadableGpuSysfsPath()

        val gpuInfo = queryGpuHardwareInfo()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (widthPx, heightPx, refreshHz, supportedRates) = readDisplayInfo(wm)

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mfg = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            listOfNotNull(mfg, model).joinToString(" ").ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

        val cpuModelName = readCpuModelName(socModel, coreCount)

        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .replaceFirstChar { it.uppercase() }

        DeviceSpecs(
            deviceName = deviceName,
            socModel = socModel,
            cpuModelName = cpuModelName,
            board = Build.BOARD,
            abiList = Build.SUPPORTED_ABIS.joinToString(", "),
            coreCount = coreCount,
            coreTopologySummary = topologySummary,
            gpuInfo = gpuInfo,
            totalRamMb = totalRamMb,
            swapTotalMb = swapTotalKb / 1024L,
            displayWidthPx = widthPx,
            displayHeightPx = heightPx,
            refreshRateHz = refreshHz,
            supportedRefreshRates = supportedRates,
            procStatReadable = procStatReadable,
            cpuFreqReadable = cpuFreqReadable,
            memInfoReadable = memInfoReadable,
            gpuSysfsPath = gpuSysfsPath
        )
    }

    private fun readCpuModelName(socFallback: String, coreCount: Int): String {
        val cpuInfoFile = File("/proc/cpuinfo")
        if (cpuInfoFile.canRead()) {
            val lines = runCatching { cpuInfoFile.readLines() }.getOrDefault(emptyList())
            val hardware = lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }
            val modelName = lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }
            val processor = lines.firstOrNull { it.startsWith("Processor", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }
            val resolved = hardware ?: modelName ?: processor
            if (!resolved.isNullOrBlank()) {
                return if (socFallback.isNotBlank() && !resolved.contains(socFallback, ignoreCase = true)) {
                    "$socFallback ($resolved)"
                } else {
                    resolved
                }
            }
        }
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM"
        return "$socFallback · $coreCount núcleos ($primaryAbi)"
    }

    private fun buildTopologySummary(coreCount: Int, maxFreqsMhz: List<Int>): String {
        val valid = maxFreqsMhz.filter { it > 0 }
        if (valid.isEmpty()) return "$coreCount núcleos lógicos"
        val groups = valid.groupingBy { it }.eachCount().toList().sortedByDescending { it.first }
        return groups.joinToString(" + ") { (mhz, count) ->
            val ghz = String.format("%.2f GHz", mhz / 1000f)
            "${count}x @ $ghz"
        }
    }

    private fun readDisplayInfo(wm: WindowManager): Quad<Int, Int, Float, List<Float>> {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull() ?: wm.defaultDisplay
        } else {
            wm.defaultDisplay
        }

        val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            display?.mode?.physicalWidth ?: 1080
        }
        val height = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            display?.mode?.physicalHeight ?: 2400
        }

        val currentRate = display?.mode?.refreshRate ?: 60f
        val supported = display?.supportedModes?.map { it.refreshRate }?.distinct()?.sorted()
            ?: listOf(currentRate)

        return Quad(width, height, currentRate, supported)
    }

    private fun queryGpuHardwareInfo(): GpuHardwareInfo {
        var vendor = "Desconocido"
        var renderer = "GPU Android"
        var glVersion = "OpenGL ES"
        var glslVersion = "-"
        var maxTextureSize = 0
        var maxViewport = 0 to 0
        var extCount = 0

        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (dpy != EGL14.EGL_NO_DISPLAY) {
            val version = IntArray(2)
            if (EGL14.eglInitialize(dpy, version, 0, version, 1)) {
                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfig = IntArray(1)
                if (EGL14.eglChooseConfig(dpy, attribList, 0, configs, 0, 1, numConfig, 0) && numConfig[0] > 0) {
                    val config = configs[0]
                    val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE)
                    val surface = EGL14.eglCreatePbufferSurface(dpy, config, pbufferAttribs, 0)
                    val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                    val eglCtx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

                    if (surface != EGL14.EGL_NO_SURFACE && eglCtx != EGL14.EGL_NO_CONTEXT) {
                        if (EGL14.eglMakeCurrent(dpy, surface, surface, eglCtx)) {
                            vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: vendor
                            renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: renderer
                            glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: glVersion
                            glslVersion = GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION) ?: glslVersion
                            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
                            extCount = ext.split(" ").count { it.isNotBlank() }

                            val texBuf = IntArray(1)
                            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, texBuf, 0)
                            maxTextureSize = texBuf[0]

                            val vpBuf = IntArray(2)
                            GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, vpBuf, 0)
                            maxViewport = vpBuf[0] to vpBuf[1]

                            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        }
                    }
                    if (eglCtx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, eglCtx)
                    if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surface)
                }
                EGL14.eglTerminate(dpy)
            }
        }

        val pm = context.packageManager
        val vulkanLevelFeature = pm.systemAvailableFeatures.firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
        }
        val vulkanVersionFeature = pm.systemAvailableFeatures.firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
        }
        val vulkanSupported = vulkanLevelFeature != null || vulkanVersionFeature != null
        val vulkanLabel = if (vulkanSupported) {
            val ver = vulkanVersionFeature?.version ?: 0
            if (ver > 0) {
                val major = (ver shr 22) and 0x3FF
                val minor = (ver shr 12) and 0x3FF
                val patch = ver and 0xFFF
                "Vulkan $major.$minor.$patch (Nivel ${vulkanLevelFeature?.version ?: 0})"
            } else {
                "Vulkan Nivel ${vulkanLevelFeature?.version ?: 0}"
            }
        } else {
            "No soportado"
        }

        return GpuHardwareInfo(
            vendor = vendor,
            renderer = renderer,
            glVersion = glVersion,
            glslVersion = glslVersion,
            maxTextureSize = maxTextureSize,
            maxViewportDims = maxViewport,
            extensionCount = extCount,
            vulkanSupported = vulkanSupported,
            vulkanVersionLabel = vulkanLabel
        )
    }

    private fun findReadableGpuSysfsPath(): String? {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/devices/platform/mali.0/utilization",
            "/sys/kernel/gpu/gpu_busy"
        )
        return candidates.firstOrNull { File(it).canRead() }
    }

    private fun readSysInt(path: String): Int? = runCatching {
        val file = File(path)
        if (!file.canRead()) return@runCatching null
        file.readText().trim().toIntOrNull()
    }.getOrNull()

    private fun readProcMemValueKb(file: File, prefix: String): Long? = runCatching {
        file.useLines { lines ->
            lines.firstOrNull { it.startsWith(prefix) }
                ?.substringAfter(prefix)
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.toLongOrNull()
        }
    }.getOrNull()

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
