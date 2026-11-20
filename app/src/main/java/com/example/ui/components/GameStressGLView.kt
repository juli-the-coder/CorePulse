package com.example.ui.components

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.telemetry.BenchmarkPreset
import com.example.telemetry.FrameMetricsTracker
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun GameStressGLView(
    preset: BenchmarkPreset,
    isRunning: Boolean,
    frameMetricsTracker: FrameMetricsTracker,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(frameMetricsTracker) {
        CyberStressRenderer(frameMetricsTracker)
    }

    renderer.complexity = preset.shaderComplexity
    renderer.activeStress = isRunning

    val glView = remember(context) {
        GLSurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier
    )
}

private class CyberStressRenderer(
    private val frameMetricsTracker: FrameMetricsTracker
) : GLSurfaceView.Renderer {

    @Volatile
    var complexity: Int = 2

    @Volatile
    var activeStress: Boolean = false

    private var programId: Int = 0
    private var positionHandle: Int = 0
    private var timeHandle: Int = 0
    private var resolutionHandle: Int = 0
    private var complexityHandle: Int = 0
    private var activeHandle: Int = 0
    private var mvpHandle: Int = 0

    private val quadVertices = floatArrayOf(
        -1f, -1f, 0f,
        1f, -1f, 0f,
        -1f, 1f, 0f,
        1f, 1f, 0f
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(quadVertices)
        .apply { position(0) }

    private val mvpMatrix = FloatArray(16)
    private var widthPx = 1
    private var heightPx = 1
    private var startTimeNs = System.nanoTime()
    private var lastFrameNs = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.047f, 0.07f, 1f)
        val vertShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertShader)
            GLES20.glAttachShader(prog, fragShader)
            GLES20.glLinkProgram(prog)
        }
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        timeHandle = GLES20.glGetUniformLocation(programId, "uTime")
        resolutionHandle = GLES20.glGetUniformLocation(programId, "uResolution")
        complexityHandle = GLES20.glGetUniformLocation(programId, "uComplexity")
        activeHandle = GLES20.glGetUniformLocation(programId, "uActive")
        mvpHandle = GLES20.glGetUniformLocation(programId, "uMvp")
        Matrix.setIdentityM(mvpMatrix, 0)
        startTimeNs = System.nanoTime()
        lastFrameNs = startTimeNs
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, widthPx, heightPx)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        if (activeStress && lastFrameNs != 0L) {
            val dtMs = (nowNs - lastFrameNs) / 1_000_000f
            frameMetricsTracker.recordExternalGlFrame(dtMs)
        }
        lastFrameNs = nowNs

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(programId)

        val elapsedSec = (nowNs - startTimeNs) / 1_000_000_000f
        GLES20.glUniform1f(timeHandle, elapsedSec)
        GLES20.glUniform2f(resolutionHandle, widthPx.toFloat(), heightPx.toFloat())
        GLES20.glUniform1i(complexityHandle, if (activeStress) complexity else 1)
        GLES20.glUniform1f(activeHandle, if (activeStress) 1.0f else 0.35f)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        // Draw multi-pass layers in Ultra/Alto presets to create genuine GPU fill-rate & ALU load
        val passes = if (activeStress) when (complexity) {
            1 -> 1
            2 -> 2
            3 -> 4
            else -> 7
        } else 1

        for (p in 0 until passes) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            uniform mat4 uMvp;
            void main() {
                gl_Position = uMvp * aPosition;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            uniform float uTime;
            uniform vec2 uResolution;
            uniform int uComplexity;
            uniform float uActive;

            float sdSphere(vec3 p, float r) {
                return length(p) - r;
            }

            float mapScene(vec3 p, float t, int comp) {
                vec3 q = p;
                float angle = t * 0.65;
                mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
                q.xz = rot * q.xz;
                q.xy = rot * q.xy;
                float core = sdSphere(q, 0.72 + 0.08 * sin(t * 2.4 + q.y * 5.0));
                float wave = sin(q.x * 5.0 + t * 2.0) * sin(q.y * 5.0 - t) * sin(q.z * 5.0) * 0.14;
                float ring = abs(length(vec2(length(q.xz) - 1.15, q.y)) - 0.09) - 0.02;
                float d = min(core + wave, ring);
                if (comp >= 3) {
                    vec3 rep = mod(p + vec3(1.5), 3.0) - vec3(1.5);
                    float sat = sdSphere(rep, 0.22);
                    d = min(d, sat);
                }
                return d;
            }

            void main() {
                vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / min(uResolution.x, uResolution.y);
                float t = uTime * (0.6 + uActive * 0.9);

                vec3 ro = vec3(0.0, 0.25, 2.75);
                vec3 rd = normalize(vec3(uv, -1.35));

                int maxSteps = 16;
                if (uComplexity == 2) maxSteps = 28;
                if (uComplexity == 3) maxSteps = 44;
                if (uComplexity >= 4) maxSteps = 64;

                float dist = 0.0;
                float glow = 0.0;
                vec3 col = vec3(0.035, 0.05, 0.08);

                for (int i = 0; i < 64; i++) {
                    if (i >= maxSteps) break;
                    vec3 p = ro + rd * dist;
                    float d = mapScene(p, t, uComplexity);
                    glow += 0.018 / (0.04 + abs(d));
                    if (d < 0.008 || dist > 6.5) break;
                    dist += d * 0.75;
                }

                // Cyber grid floor projection
                float grid = smoothstep(0.95, 0.99, abs(sin(uv.x * 18.0 + t * 0.5))) +
                             smoothstep(0.95, 0.99, abs(sin(uv.y * 18.0 - t * 0.7)));
                col += vec3(0.0, 0.22, 0.30) * grid * 0.18;

                vec3 cyan = vec3(0.0, 0.90, 1.0);
                vec3 amber = vec3(1.0, 0.70, 0.0);
                vec3 emerald = vec3(0.0, 0.90, 0.46);

                float pulse = 0.5 + 0.5 * sin(t * 2.0 + length(uv) * 4.0);
                vec3 energy = mix(cyan, amber, pulse * 0.45);
                if (uComplexity >= 4) {
                    energy = mix(energy, emerald, 0.25);
                }

                col += energy * glow * (0.28 + 0.45 * uActive);
                col = col / (vec3(1.0) + col * 0.45);
                gl_FragColor = vec4(col, 1.0);
            }
        """
    }
}
