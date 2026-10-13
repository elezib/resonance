package com.resonance.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Resonance's own DSP stage, inserted into the player's audio output:
 * preamp + ReplayGain -> bass shelf -> 10 peaking bands -> treble shelf -> soft limiter.
 * It also taps the signal for the visualizer, so no microphone permission is needed.
 */
@UnstableApi
class EqAudioProcessor : BaseAudioProcessor() {

    private var coeffs: Array<BiquadCoeffs> = emptyArray()
    private var states: Array<Array<BiquadState>> = emptyArray()
    private var builtFor: EqSettings? = null
    private var builtRate = 0
    private var builtChannels = 0

    private val analyzer = SpectrumAnalyzer()
    private val visBuffer = FloatArray(SpectrumAnalyzer.N)
    private var visPos = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        builtFor = null
        states = emptyArray()
        visPos = 0
    }

    override fun onReset() {
        builtFor = null
        coeffs = emptyArray()
        states = emptyArray()
        visPos = 0
    }

    private fun ensureFilters(rate: Int, channels: Int) {
        val s = AudioEngine.settings
        if (s === builtFor && rate == builtRate && channels == builtChannels) return
        val newCoeffs = EqChain.build(s, rate.toDouble()).toTypedArray()
        // Keep filter memory when only gains change, to avoid clicks while dragging sliders.
        if (states.size != newCoeffs.size || channels != builtChannels) {
            states = Array(newCoeffs.size) { Array(channels) { BiquadState() } }
        }
        coeffs = newCoeffs
        builtFor = s
        builtRate = rate
        builtChannels = channels
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val format = inputAudioFormat
        val channels = format.channelCount
        val rate = format.sampleRate
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2

        ensureFilters(rate, channels)
        val s = builtFor ?: AudioEngine.settings
        val gainDb = (if (s.enabled) s.preampDb else 0f) + AudioEngine.currentReplayGainDb()
        val vis = AudioEngine.visualizerActive
        val c = coeffs
        val st = states

        // Nothing to do: pass the audio through untouched (bit-perfect).
        if (c.isEmpty() && abs(gainDb) < 0.01f && !s.limiter && !vis) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        val gain = 10.0.pow(gainDb / 20.0)
        val limiter = s.limiter
        val frames = remaining / (bytesPerSample * channels)
        val out = replaceOutputBuffer(frames * bytesPerSample * channels)

        for (frame in 0 until frames) {
            var mono = 0.0
            for (ch in 0 until channels) {
                var x = if (isFloat) inputBuffer.getFloat().toDouble()
                else inputBuffer.getShort() / 32768.0
                x *= gain
                for (k in c.indices) x = st[k][ch].process(c[k], x)
                if (limiter) x = softClip(x)
                if (x > 1.0) x = 1.0 else if (x < -1.0) x = -1.0
                if (isFloat) out.putFloat(x.toFloat())
                else out.putShort((x * 32767.0).roundToInt().toShort())
                mono += x
            }
            if (vis) {
                visBuffer[visPos++] = (mono / channels).toFloat()
                if (visPos == visBuffer.size) {
                    analyzer.analyze(visBuffer, rate)
                    visPos = 0
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun softClip(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1.0 - KNEE) * tanh((a - KNEE) / (1.0 - KNEE))
        return if (x < 0) -y else y
    }

    private companion object {
        const val KNEE = 0.85
    }
}
