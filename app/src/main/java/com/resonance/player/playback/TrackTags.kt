package com.resonance.player.playback

import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.resonance.player.audio.AudioEngine

/** Reads ReplayGain values and embedded lyrics from the playing file's tags. */
@UnstableApi
object TrackTags {

    fun clear() {
        AudioEngine.trackGainDb = null
        AudioEngine.albumGainDb = null
        AudioEngine.embeddedLyrics.value = null
    }

    fun apply(tracks: Tracks) {
        var trackGain: Float? = null
        var albumGain: Float? = null
        var lyrics: String? = null

        fun handle(key: String, value: String) {
            when (key.uppercase()) {
                "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseGain(value) ?: trackGain
                "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGain(value) ?: albumGain
                "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" ->
                    if (lyrics == null && value.isNotBlank()) lyrics = value
            }
        }

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (t in 0 until group.length) {
                val metadata = group.getTrackFormat(t).metadata ?: continue
                for (e in 0 until metadata.length()) {
                    when (val entry = metadata.get(e)) {
                        is VorbisComment -> handle(entry.key, entry.value)
                        is TextInformationFrame ->
                            if (entry.id == "TXXX") handle(entry.description ?: "", entry.values.firstOrNull() ?: "")
                        is BinaryFrame ->
                            if (entry.id == "USLT" && lyrics == null) lyrics = parseUslt(entry.data)
                        else -> Unit
                    }
                }
            }
        }
        AudioEngine.trackGainDb = trackGain
        AudioEngine.albumGainDb = albumGain
        AudioEngine.embeddedLyrics.value = lyrics
    }

    private fun parseGain(value: String): Float? =
        value.trim().split(" ").firstOrNull()?.replace("+", "")?.toFloatOrNull()

    /** ID3 USLT frame: [encoding][language x3][descriptor\0][lyrics]. */
    private fun parseUslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        val charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val wide = enc == 1 || enc == 2
        var i = 4
        if (wide) {
            while (i + 1 < d.size && !(d[i] == 0.toByte() && d[i + 1] == 0.toByte())) i += 2
            i += 2
        } else {
            while (i < d.size && d[i] != 0.toByte()) i++
            i += 1
        }
        if (i >= d.size) return null
        return String(d, i, d.size - i, charset).trim('\u0000', ' ', '\n', '\r').ifBlank { null }
    }
}
