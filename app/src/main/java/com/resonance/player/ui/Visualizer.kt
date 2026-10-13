package com.resonance.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.resonance.player.audio.AudioEngine
import com.resonance.player.data.VisStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Live visualizer driven by Resonance's own audio tap (no microphone permission). */
@Composable
fun Visualizer(style: VisStyle, color: Color, modifier: Modifier = Modifier, accent: Color = color) {
    val bands = remember { FloatArray(AudioEngine.SPECTRUM_BANDS) }
    val wave = remember { FloatArray(AudioEngine.WAVE_SIZE) }
    var tick by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        AudioEngine.visualizerUsers.incrementAndGet()
        onDispose { AudioEngine.visualizerUsers.decrementAndGet() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame ->
                val fresh = System.nanoTime() - AudioEngine.lastFrameNanos < 150_000_000L
                val src = AudioEngine.spectrum
                for (i in bands.indices) {
                    val target = if (fresh) src[i] else 0f
                    bands[i] += (target - bands[i]) * 0.3f
                }
                val w = AudioEngine.waveform
                for (i in wave.indices) {
                    val target = if (fresh) w[i] else 0f
                    wave[i] += (target - wave[i]) * 0.5f
                }
                tick = frame
            }
        }
    }

    Canvas(modifier) {
        if (tick < 0L) return@Canvas // reading tick redraws every frame
        when (style) {
            VisStyle.BARS -> {
                val n = bands.size
                val slot = size.width / n
                val gap = slot * 0.22f
                for (i in 0 until n) {
                    val v = bands[i]
                    val h = (v * size.height).coerceAtLeast(slot * 0.4f)
                    drawRoundRect(
                        color = lerpColor(color, accent, i.toFloat() / n).copy(alpha = 0.45f + 0.55f * v),
                        topLeft = Offset(i * slot + gap / 2, size.height - h),
                        size = Size(slot - gap, h),
                        cornerRadius = CornerRadius((slot - gap) / 2, (slot - gap) / 2),
                    )
                }
            }
            VisStyle.WAVE -> {
                val mid = size.height / 2
                val path = Path()
                val step = size.width / (wave.size - 1)
                for (i in wave.indices) {
                    val y = mid - (wave[i] * 2.5f).coerceIn(-1f, 1f) * mid * 0.9f
                    if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
                }
                drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 14f, cap = StrokeCap.Round))
                drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            VisStyle.RADIAL -> {
                val center = Offset(size.width / 2, size.height / 2)
                val minDim = min(size.width, size.height)
                val base = minDim * 0.22f
                val n = bands.size
                val total = n * 2
                var avg = 0f
                for (v in bands) avg += v
                avg /= n
                for (k in 0 until total) {
                    val i = if (k < n) k else total - 1 - k // mirrored, so both halves match
                    val v = bands[i]
                    val angle = (k.toFloat() / total) * 2f * PI.toFloat() - PI.toFloat() / 2f
                    val len = minDim * 0.04f + v * minDim * 0.24f
                    val start = Offset(center.x + cos(angle) * base, center.y + sin(angle) * base)
                    val end = Offset(center.x + cos(angle) * (base + len), center.y + sin(angle) * (base + len))
                    drawLine(
                        lerpColor(color, accent, v).copy(alpha = 0.5f + 0.5f * v),
                        start, end, strokeWidth = minDim * 0.012f, cap = StrokeCap.Round,
                    )
                }
                drawCircle(color.copy(alpha = 0.18f + 0.3f * avg), radius = base * (0.85f + avg * 0.25f), center = center)
                drawCircle(color, radius = base * 0.92f, center = center, style = Stroke(width = minDim * 0.008f))
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
