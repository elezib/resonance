package com.resonance.player.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Normalised biquad filter coefficients (a0 = 1). Formulas: RBJ Audio EQ Cookbook. */
class BiquadCoeffs(
    val b0: Double, val b1: Double, val b2: Double,
    val a1: Double, val a2: Double,
) {
    /** Gain of this filter at [freq] Hz, in dB. Used to draw the EQ curve. */
    fun magnitudeDb(freq: Double, fs: Double): Double {
        val w = 2.0 * PI * freq / fs
        val c1 = cos(w); val s1 = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1.0 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val mag = sqrt(nr * nr + ni * ni) / sqrt(dr * dr + di * di)
        return 20.0 * log10(mag.coerceAtLeast(1e-9))
    }

    companion object {
        private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        fun peaking(f: Double, fs: Double, q: Double, gainDb: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / fs
            val alpha = sin(w0) / (2.0 * q)
            val c = cos(w0)
            return norm(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }

        fun lowShelf(f: Double, fs: Double, gainDb: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / fs
            val c = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val sa = 2.0 * sqrt(a) * alpha
            return norm(
                a * ((a + 1) - (a - 1) * c + sa),
                2 * a * ((a - 1) - (a + 1) * c),
                a * ((a + 1) - (a - 1) * c - sa),
                (a + 1) + (a - 1) * c + sa,
                -2 * ((a - 1) + (a + 1) * c),
                (a + 1) + (a - 1) * c - sa,
            )
        }

        fun highShelf(f: Double, fs: Double, gainDb: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / fs
            val c = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val sa = 2.0 * sqrt(a) * alpha
            return norm(
                a * ((a + 1) + (a - 1) * c + sa),
                -2 * a * ((a - 1) + (a + 1) * c),
                a * ((a + 1) + (a - 1) * c - sa),
                (a + 1) - (a - 1) * c + sa,
                2 * ((a - 1) - (a + 1) * c),
                (a + 1) - (a - 1) * c - sa,
            )
        }
    }
}

/** Per-channel filter memory (transposed direct form II). */
class BiquadState {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(c: BiquadCoeffs, x: Double): Double {
        val y = c.b0 * x + z1
        z1 = c.b1 * x - c.a1 * y + z2
        z2 = c.b2 * x - c.a2 * y
        return y
    }
}

object EqChain {
    const val BASS_FREQ = 105.0
    const val TREBLE_FREQ = 9000.0

    /** Builds the active filter list for [s] at sample rate [fs]. Flat bands are skipped. */
    fun build(s: EqSettings, fs: Double): List<BiquadCoeffs> {
        val list = ArrayList<BiquadCoeffs>(12)
        if (!s.enabled) return list
        val nyquistSafe = fs * 0.45
        if (abs(s.bassDb) > 0.05f) list += BiquadCoeffs.lowShelf(BASS_FREQ, fs, s.bassDb.toDouble())
        for (i in 0 until EqBands.COUNT) {
            val g = s.bands[i]
            val f = EqBands.FREQS[i].toDouble()
            if (abs(g) > 0.05f && f < nyquistSafe) list += BiquadCoeffs.peaking(f, fs, EqBands.Q, g.toDouble())
        }
        if (abs(s.trebleDb) > 0.05f && TREBLE_FREQ < nyquistSafe) {
            list += BiquadCoeffs.highShelf(TREBLE_FREQ, fs, s.trebleDb.toDouble())
        }
        return list
    }

    /** Overall response in dB at [freq] (for drawing). */
    fun responseDb(s: EqSettings, freq: Double, filters: List<BiquadCoeffs>): Double {
        if (!s.enabled) return 0.0
        var db = s.preampDb.toDouble()
        for (f in filters) db += f.magnitudeDb(freq, 48000.0)
        return db
    }
}
