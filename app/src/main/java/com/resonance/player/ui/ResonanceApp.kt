package com.resonance.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.data.SongCollection

@Composable
fun ResonanceApp(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf<SongCollection?>(null) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    val state by vm.player.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()

    BackHandler(enabled = collection != null && tab == 0) { collection = null }
    BackHandler(enabled = nowPlaying) { nowPlaying = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (state.hasMedia) {
                        MiniPlayer(
                            state = state,
                            position = position,
                            onOpen = { nowPlaying = true },
                            onToggle = vm::togglePlay,
                            onNext = vm::next,
                        )
                    }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { if (tab == 0) collection = null; tab = 0 },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Rounded.Equalizer, contentDescription = null) },
                            label = { Text("Sound") },
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val opened = collection
                when {
                    tab == 0 && opened != null -> CollectionScreen(vm, opened, onBack = { collection = null })
                    tab == 0 -> LibraryScreen(vm, onOpen = { collection = it })
                    tab == 1 -> EqualizerScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlaying && state.hasMedia,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, onClose = { nowPlaying = false })
        }
    }
}
