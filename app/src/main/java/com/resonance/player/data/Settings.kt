package com.resonance.player.data

import android.content.Context
import com.resonance.player.audio.EqBands
import com.resonance.player.audio.EqSettings
import com.resonance.player.audio.ReplayGainMode

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("Black") }

enum class VisStyle(val label: String) { BARS("Bars"), WAVE("Wave"), RADIAL("Radial") }

data class UiSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val materialYou: Boolean = false,
    val artColors: Boolean = true,
    val visStyle: VisStyle = VisStyle.BARS,
    val visUnderArt: Boolean = true,
)

@Suppress("ArrayInDataClass")
data class EqPreset(
    val name: String,
    val bands: FloatArray,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val custom: Boolean = false,
) {
    fun matches(s: EqSettings): Boolean =
        bands.indices.all { kotlin.math.abs(bands[it] - s.bands[it]) < 0.05f } &&
            kotlin.math.abs(bass - s.bassDb) < 0.05f &&
            kotlin.math.abs(treble - s.trebleDb) < 0.05f
}

object EqPresets {
    private fun f(vararg v: Int) = FloatArray(v.size) { v[it].toFloat() }

    val builtIn = listOf(
        EqPreset("Flat", f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        EqPreset("Bass Boost", f(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
        EqPreset("Bass Cut", f(-6, -5, -4, -2, 0, 0, 0, 0, 0, 0)),
        EqPreset("Treble Boost", f(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
        EqPreset("Vocal", f(-2, -2, -1, 1, 3, 4, 4, 2, 0, -1)),
        EqPreset("Rock", f(5, 4, 3, 1, -1, -1, 1, 3, 4, 5)),
        EqPreset("Pop", f(-1, 1, 3, 4, 3, 1, 0, -1, -1, -1)),
        EqPreset("Hip-Hop", f(5, 4, 1, 3, -1, -1, 1, -1, 2, 3)),
        EqPreset("Electronic", f(5, 4, 1, 0, -2, 2, 1, 1, 4, 5)),
        EqPreset("Jazz", f(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
        EqPreset("Classical", f(4, 3, 2, 1, -1, -1, 0, 2, 3, 4)),
        EqPreset("Acoustic", f(4, 3, 2, 1, 2, 2, 3, 3, 2, 1)),
        EqPreset("Late Night", f(3, 2, 1, 0, -1, -1, 0, 1, 2, 2)),
        EqPreset("Spoken Word", f(-3, -2, 0, 2, 4, 4, 3, 1, 0, -2)),
    )
}

/** Small on-device settings store. Nothing ever leaves the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("resonance", Context.MODE_PRIVATE)

    fun loadEq(): EqSettings = EqSettings(
        enabled = sp.getBoolean("eq_enabled", true),
        preampDb = sp.getFloat("eq_preamp", 0f),
        bands = parseBands(sp.getString("eq_bands", null)),
        bassDb = sp.getFloat("eq_bass", 0f),
        trebleDb = sp.getFloat("eq_treble", 0f),
        limiter = sp.getBoolean("eq_limiter", true),
    )

    fun saveEq(s: EqSettings) {
        sp.edit()
            .putBoolean("eq_enabled", s.enabled)
            .putFloat("eq_preamp", s.preampDb)
            .putString("eq_bands", s.bands.joinToString(","))
            .putFloat("eq_bass", s.bassDb)
            .putFloat("eq_treble", s.trebleDb)
            .putBoolean("eq_limiter", s.limiter)
            .apply()
    }

    var replayGainMode: ReplayGainMode
        get() = runCatching { ReplayGainMode.valueOf(sp.getString("rg_mode", "OFF") ?: "OFF") }
            .getOrDefault(ReplayGainMode.OFF)
        set(value) { sp.edit().putString("rg_mode", value.name).apply() }

    fun loadCustomPresets(): List<EqPreset> =
        (sp.getString("custom_presets", "") ?: "").split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) null
            else EqPreset(
                name = parts[0],
                bands = parseBands(parts[1]),
                bass = parts[2].toFloatOrNull() ?: 0f,
                treble = parts[3].toFloatOrNull() ?: 0f,
                custom = true,
            )
        }

    fun saveCustomPresets(list: List<EqPreset>) {
        val text = list.joinToString("\n") { p ->
            val safeName = p.name.replace("|", " ").replace("\n", " ")
            "$safeName|${p.bands.joinToString(",")}|${p.bass}|${p.treble}"
        }
        sp.edit().putString("custom_presets", text).apply()
    }

    fun loadUi(): UiSettings = UiSettings(
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK") ?: "DARK") }
            .getOrDefault(ThemeMode.DARK),
        materialYou = sp.getBoolean("material_you", false),
        artColors = sp.getBoolean("art_colors", true),
        visStyle = runCatching { VisStyle.valueOf(sp.getString("vis_style", "BARS") ?: "BARS") }
            .getOrDefault(VisStyle.BARS),
        visUnderArt = sp.getBoolean("vis_under_art", true),
    )

    fun saveUi(u: UiSettings) {
        sp.edit()
            .putString("theme", u.themeMode.name)
            .putBoolean("material_you", u.materialYou)
            .putBoolean("art_colors", u.artColors)
            .putString("vis_style", u.visStyle.name)
            .putBoolean("vis_under_art", u.visUnderArt)
            .apply()
    }

    private fun parseBands(s: String?): FloatArray {
        val arr = FloatArray(EqBands.COUNT)
        s?.split(",")?.forEachIndexed { i, v -> if (i < arr.size) arr[i] = v.toFloatOrNull() ?: 0f }
        return arr
    }
}
