package com.resonance.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.resonance.player.data.LyricLine
import com.resonance.player.data.VisStyle
import com.resonance.player.data.formatDuration

private enum class NpView { ART, VISUALIZER, LYRICS, QUEUE }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.player.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val sleepEnd by vm.sleepTimerEndMs.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(NpView.ART) }
    val colors = MaterialTheme.colorScheme

    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.32f), colors.background)))
        ) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top bar
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (sleepEnd != null) "Sleep timer on" else "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    SleepTimerButton(active = sleepEnd != null, onPick = vm::setSleepTimer)
                }

                // Main area
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Crossfade(targetState = view, label = "np-view") { v ->
                        when (v) {
                            NpView.ART -> Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AlbumArt(state.currentSong, Modifier.fillMaxWidth().aspectRatio(1f), corner = 24.dp)
                                if (ui.visUnderArt) {
                                    Spacer(Modifier.height(16.dp))
                                    // Radial needs a square area, so the strip under the art uses bars.
                                    val stripStyle = if (ui.visStyle == VisStyle.RADIAL) VisStyle.BARS else ui.visStyle
                                    Visualizer(
                                        style = stripStyle,
                                        color = colors.primary,
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        accent = colors.tertiary,
                                    )
                                }
                            }
                            NpView.VISUALIZER -> Visualizer(ui.visStyle, colors.primary, Modifier.fillMaxSize(), colors.tertiary)
                            NpView.LYRICS -> LyricsView(lyrics, position, onSeek = vm::seekTo)
                            NpView.QUEUE -> QueueView(state, onJump = vm::jumpTo, onRemove = vm::removeFromQueue)
                        }
                    }
                }

                // Title + artist
                Text(
                    state.currentTitle.ifBlank { "Nothing playing" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Text(
                    listOfNotNull(state.currentArtist.ifBlank { null }, state.currentSong?.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Seek bar
                var dragFraction by remember { mutableStateOf<Float?>(null) }
                val dur = state.durationMs.coerceAtLeast(1L)
                val shown = dragFraction ?: (position.toFloat() / dur).coerceIn(0f, 1f)
                Slider(
                    value = shown,
                    onValueChange = { dragFraction = it },
                    onValueChangeFinished = {
                        dragFraction?.let { vm.seekTo((it * dur).toLong()) }
                        dragFraction = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDuration((shown * dur).toLong()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }

                // Transport controls
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = vm::toggleShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle, contentDescription = "Shuffle",
                            tint = if (state.shuffle) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = vm::togglePlay,
                        modifier = Modifier.size(76.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                }

                // View switcher
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ViewToggle(view == NpView.ART, { view = NpView.ART }) { Icon(Icons.Rounded.Image, "Artwork") }
                    ViewToggle(view == NpView.VISUALIZER, { view = NpView.VISUALIZER }) { Icon(Icons.Rounded.GraphicEq, "Visualizer") }
                    ViewToggle(view == NpView.LYRICS, { view = NpView.LYRICS }) { Icon(Icons.Rounded.Subtitles, "Lyrics") }
                    ViewToggle(view == NpView.QUEUE, { view = NpView.QUEUE }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue")
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewToggle(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { icon() }
}

@Composable
private fun SleepTimerButton(active: Boolean, onPick: (Int?) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Rounded.Timer, contentDescription = "Sleep timer",
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (active) DropdownMenuItem(text = { Text("Turn off timer") }, onClick = { menuOpen = false; onPick(null) })
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("Stop after $m min") }, onClick = { menuOpen = false; onPick(m) })
            }
        }
    }
}

@Composable
private fun LyricsView(lines: List<LyricLine>, position: Long, onSeek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics for this song.\n\nResonance shows lyrics saved inside the music file, or a .lrc file with the same name next to it.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val synced = lines.first().timeMs != null
    if (!synced) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            lines.forEach { Text(it.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp)) }
        }
        return
    }
    val current = lines.indexOfLast { (it.timeMs ?: 0L) <= position }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(current) { listState.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(lines.size) { i ->
            val line = lines[i]
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { line.timeMs?.let(onSeek) }
                    .padding(vertical = 8.dp),
            )
        }
        item { Spacer(Modifier.height(200.dp)) }
    }
}

@Composable
private fun QueueView(state: PlayerUiState, onJump: (Int) -> Unit, onRemove: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.queueIndex > 0) listState.scrollToItem(state.queueIndex) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.queue.size) { i ->
            val e = state.queue[i]
            val current = i == state.queueIndex
            Row(
                Modifier.fillMaxWidth().clickable { onJump(i) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}", modifier = Modifier.width(36.dp),
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        e.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        e.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!current) IconButton(onClick = { onRemove(i) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                }
            }
        }
    }
}
