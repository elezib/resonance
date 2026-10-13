package com.resonance.player.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Fft {
    /** In-place iterative radix-2 FFT. Array length must be a power of two. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            val half = len / 2
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Turns blocks of mono samples into smoothed log-spaced bands for the visualizer. */
class SpectrumAnalyzer {
    companion object { const val N = 1024 }

    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private val window = DoubleArray(N) { 0.5 - 0.5 * cos(2.0 * PI * it / (N - 1)) }

    fun analyze(samples: FloatArray, sampleRate: Int) {
        for (i in 0 until N) {
            re[i] = samples[i] * window[i]
            im[i] = 0.0
        }
        Fft.transform(re, im)

        val bands = AudioEngine.spectrum
        val count = bands.size
        val minF = 40.0
        val maxF = min(16000.0, sampleRate / 2.0)
        val binHz = sampleRate.toDouble() / N
        for (b in 0 until count) {
            val f0 = minF * (maxF / minF).pow(b.toDouble() / count)
            val f1 = minF * (maxF / minF).pow((b + 1).toDouble() / count)
            val bin0 = max(1, (f0 / binHz).toInt())
            val bin1 = min(N / 2 - 1, max(bin0, (f1 / binHz).toInt()))
            var peak = 0.0
            for (k in bin0..bin1) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                if (m > peak) peak = m
            }
            val mag = peak / (N / 4.0)
            val db = 20.0 * log10(mag + 1e-9)
            val v = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
            val prev = bands[b]
            bands[b] = if (v > prev) prev * 0.35f + v * 0.65f else prev * 0.8f + v * 0.2f
        }

        val wave = AudioEngine.waveform
        val offset = N - wave.size
        for (i in wave.indices) wave[i] = samples[offset + i]
        AudioEngine.lastFrameNanos = System.nanoTime()
    }
}
