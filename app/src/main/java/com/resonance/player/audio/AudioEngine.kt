package com.resonance.player.audio

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

/** The 10 graphic-EQ band centre frequencies (Hz). */
object EqBands {
    val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    const val COUNT = 10
    const val Q = 1.41
    const val MAX_DB = 12f

    fun label(i: Int): String {
        val f = FREQS[i]
        return if (f >= 1000f) "${(f / 1000f).toInt()}k" else f.toInt().toString()
    }
}

/** All sound-shaping settings. Treated as immutable: always replace, never mutate. */
@Suppress("ArrayInDataClass")
data class EqSettings(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val bands: FloatArray = FloatArray(EqBands.COUNT),
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val limiter: Boolean = true,
)

enum class ReplayGainMode { OFF, TRACK, ALBUM }

/**
 * Shared state between the playback service (audio thread) and the UI.
 * Both run in the same app process, so a simple singleton is enough.
 */
object AudioEngine {
    const val SPECTRUM_BANDS = 48
    const val WAVE_SIZE = 256

    @Volatile
    var settings: EqSettings = EqSettings()
        private set

    @Volatile var replayGainMode: ReplayGainMode = ReplayGainMode.OFF
    @Volatile var trackGainDb: Float? = null
    @Volatile var albumGainDb: Float? = null

    /** Number of visualizers on screen; the audio thread only analyses when one is visible. */
    val visualizerUsers = AtomicInteger(0)
    val visualizerActive: Boolean get() = visualizerUsers.get() > 0
    val spectrum = FloatArray(SPECTRUM_BANDS)
    val waveform = FloatArray(WAVE_SIZE)
    @Volatile var lastFrameNanos: Long = 0L

    /** Raw lyrics text embedded in the current track's tags (null if none). */
    val embeddedLyrics = MutableStateFlow<String?>(null)

    fun update(newSettings: EqSettings) {
        settings = newSettings
    }

    fun currentReplayGainDb(): Float = when (replayGainMode) {
        ReplayGainMode.OFF -> 0f
        ReplayGainMode.TRACK -> trackGainDb ?: albumGainDb ?: 0f
        ReplayGainMode.ALBUM -> albumGainDb ?: trackGainDb ?: 0f
    }
}
