package com.resonance.player.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.data.ThemeMode
import com.resonance.player.data.VisStyle

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        SectionTitle("Theme")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = ui.themeMode == mode,
                    onClick = { vm.updateUi { it.copy(themeMode = mode) } },
                    label = { Text(mode.label) },
                )
            }
        }
        ToggleRow(
            "Colours from album art",
            "Tint the app with the colours of what's playing.",
            ui.artColors,
        ) { on -> vm.updateUi { it.copy(artColors = on) } }
        if (Build.VERSION.SDK_INT >= 31) {
            ToggleRow(
                "Match wallpaper (Material You)",
                "Use your phone's wallpaper colours as the base theme.",
                ui.materialYou,
            ) { on -> vm.updateUi { it.copy(materialYou = on) } }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionTitle("Visualizer")
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisStyle.entries.forEach { style ->
                FilterChip(
                    selected = ui.visStyle == style,
                    onClick = { vm.updateUi { it.copy(visStyle = style) } },
                    label = { Text(style.label) },
                )
            }
        }
        ToggleRow(
            "Visualizer under artwork",
            "Show a small live visualizer below the cover on the Now Playing screen.",
            ui.visUnderArt,
        ) { on -> vm.updateUi { it.copy(visUnderArt = on) } }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionTitle("Library")
        Text(
            if (loading) "Scanning…" else "${songs.size} songs found on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        FilledTonalButton(onClick = vm::rescan, enabled = !loading, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Rescan library")
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        SectionTitle("About")
        Text(
            "Resonance 1.0\nFree and open. No ads, no accounts, no tracking — the app doesn't even have " +
                "permission to use the internet. It only plays music stored on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
