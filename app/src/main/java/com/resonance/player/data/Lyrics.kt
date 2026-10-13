package com.resonance.player.data

import java.io.File

data class LyricLine(val timeMs: Long?, val text: String)

object Lyrics {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val infoTag = Regex("""^\[[a-zA-Z]+:.*\]$""")

    /** Parses LRC (synced) or plain text lyrics. Synced output is sorted by time. */
    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        var synced = false
        for (line in raw.lines()) {
            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) {
                val t = line.trim()
                if (t.isNotEmpty() && !infoTag.matches(t)) out += LyricLine(null, t)
                continue
            }
            val text = line.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1000 + ms, text)
                synced = true
            }
        }
        return if (synced) out.filter { it.timeMs != null }.sortedBy { it.timeMs } else out
    }

    /** Looks for "Song name.lrc" (or .txt) next to the audio file. */
    fun readSidecar(song: Song): String? {
        if (song.path.isBlank()) return null
        val base = song.path.substringBeforeLast('.')
        for (ext in listOf(".lrc", ".LRC", ".txt")) {
            try {
                val f = File(base + ext)
                if (f.isFile && f.canRead()) return f.readText()
            } catch (e: Exception) {
                // Not readable (e.g. Android's storage rules); ignore.
            }
        }
        return null
    }
}
