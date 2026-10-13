package com.resonance.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.audio.EqBands
import com.resonance.player.audio.EqChain
import com.resonance.player.audio.EqSettings
import com.resonance.player.audio.ReplayGainMode
import com.resonance.player.data.EqPreset
import com.resonance.player.data.EqPresets
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun EqualizerScreen(vm: MainViewModel) {
    val eq by vm.eq.collectAsStateWithLifecycle()
    val custom by vm.customPresets.collectAsStateWithLifecycle()
    val rg by vm.replayGain.collectAsStateWithLifecycle()
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sound", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (eq.enabled) "On" else "Off", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(checked = eq.enabled, onCheckedChange = vm::setEqEnabled)
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)),
        ) {
            EqCurve(eq, Modifier.fillMaxWidth().height(150.dp).padding(12.dp))
        }

        SectionTitle("Presets")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (EqPresets.builtIn + custom).forEach { p ->
                PresetChip(p, selected = eq.enabled && p.matches(eq), onClick = { vm.applyPreset(p) })
            }
            AssistChip(
                onClick = { saving = true },
                label = { Text("Save current") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }

        val selectedCustom = custom.firstOrNull { eq.enabled && it.matches(eq) }
        if (selectedCustom != null) {
            TextButton(onClick = { vm.deletePreset(selectedCustom) }, modifier = Modifier.padding(start = 12.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete preset \"${selectedCustom.name}\"")
            }
        }

        SectionTitle("10-band equalizer")
        for (i in 0 until EqBands.COUNT) {
            DbSlider(
                label = EqBands.label(i) + "Hz",
                value = eq.bands[i],
                enabled = eq.enabled,
                onChange = { vm.setBand(i, it) },
            )
        }
        TextButton(onClick = vm::resetEq, modifier = Modifier.padding(start = 12.dp)) { Text("Reset all to 0 dB") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle("Tone")
        DbSlider("Bass", eq.bassDb, eq.enabled, vm::setBass)
        DbSlider("Treble", eq.trebleDb, eq.enabled, vm::setTreble)
        DbSlider("Preamp", eq.preampDb, eq.enabled, vm::setPreamp)
        Text(
            "Lower the preamp if boosted sound distorts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionTitle("Volume levelling (ReplayGain)")
        Text(
            "Evens out loudness between songs using ReplayGain tags in your files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ReplayGainMode.OFF to "Off", ReplayGainMode.TRACK to "Per song", ReplayGainMode.ALBUM to "Per album")
                .forEach { (mode, label) ->
                    FilterChip(selected = rg == mode, onClick = { vm.setReplayGain(mode) }, label = { Text(label) })
                }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Prevent clipping", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Gently limits peaks so boosted sound stays clean.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = eq.limiter, onCheckedChange = vm::setLimiter)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save preset") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.savePreset(name); saving = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PresetChip(p: EqPreset, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(p.name) })
}

@Composable
private fun DbSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = { onChange(kotlin.math.round(it * 2f) / 2f) }, // 0.5 dB steps
            valueRange = -EqBands.MAX_DB..EqBands.MAX_DB,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            (if (value > 0) "+" else "") + "%.1f".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
    }
}

/** Draws the combined frequency response of the current settings. */
@Composable
private fun EqCurve(eq: EqSettings, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val filters = remember(eq) { EqChain.build(eq, 48000.0) }
    Canvas(modifier) {
        val range = 15.0
        fun yFor(db: Double) = (size.height / 2 - (db / range * size.height / 2)).toFloat()
        listOf(-12.0, -6.0, 0.0, 6.0, 12.0).forEach { db ->
            drawLine(grid, Offset(0f, yFor(db)), Offset(size.width, yFor(db)), strokeWidth = if (db == 0.0) 2f else 1f)
        }
        val minF = log10(20.0)
        val maxF = log10(20000.0)
        val steps = 120
        val path = Path()
        for (i in 0..steps) {
            val f = 10.0.pow(minF + (maxF - minF) * i / steps)
            val db = EqChain.responseDb(eq, f, filters).coerceIn(-range, range)
            val x = size.width * i / steps
            val y = yFor(db)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = if (eq.enabled) primary else grid
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 12f))
        drawPath(path, color, style = Stroke(width = 4f))
    }
}
