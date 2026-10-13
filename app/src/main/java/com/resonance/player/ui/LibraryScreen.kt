package com.resonance.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.data.Song
import com.resonance.player.data.SongCollection
import com.resonance.player.data.formatDuration

@Composable
fun LibraryScreen(vm: MainViewModel, onOpen: (SongCollection) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val state by vm.player.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val rowFor: @Composable (Song, List<Song>, Int) -> Unit = { song, list, index ->
        SongRow(
            song = song,
            isCurrent = state.currentSong?.id == song.id,
            onClick = { vm.play(list, index) },
            onPlayNext = { vm.playNext(song) },
            onAddToQueue = { vm.addToQueue(song) },
            onOpen = onOpen,
            albumSongs = { albums.firstOrNull { it.id == song.albumId }?.songs ?: listOf(song) },
            artistSongs = { artists.firstOrNull { it.name == song.artist }?.songs ?: listOf(song) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )

        when {
            loading && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            songs.isEmpty() -> EmptyMessage(
                "No music found",
                "Copy music files onto your phone (for example into the Music folder), then tap Rescan in Settings.",
            )
            query.isNotBlank() -> {
                val q = query.trim()
                val results = songs.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                }
                if (results.isEmpty()) EmptyMessage("No matches", "Nothing in your library matches \"$q\".")
                else LazyColumn(Modifier.fillMaxSize()) {
                    items(results.size, key = { results[it].id }) { i -> rowFor(results[i], results, i) }
                }
            }
            else -> {
                TabRow(selectedTabIndex = page) {
                    listOf("Songs", "Albums", "Artists", "Folders").forEachIndexed { i, title ->
                        Tab(selected = page == i, onClick = { page = i }, text = { Text(title) })
                    }
                }
                when (page) {
                    0 -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilledTonalButton(onClick = { vm.shuffleAll(songs) }) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Shuffle all")
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${songs.size} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(songs.size, key = { songs[it].id }) { i -> rowFor(songs[i], songs, i) }
                    }
                    1 -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(albums, key = { it.id }) { album ->
                            Column(Modifier.clickable { onOpen(SongCollection(album.title, album.artist, album.songs)) }) {
                                AlbumArt(album.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    album.artist, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    2 -> LazyColumn(Modifier.fillMaxSize()) {
                        items(artists, key = { it.name }) { artist ->
                            ListEntry(
                                icon = { CircleIcon { Icon(Icons.Rounded.Person, contentDescription = null) } },
                                title = artist.name,
                                subtitle = "${artist.songs.size} songs · ${artist.albumCount} albums",
                                onClick = { onOpen(SongCollection(artist.name, "Artist", artist.songs)) },
                            )
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(folders, key = { it.path }) { folder ->
                            ListEntry(
                                icon = { CircleIcon { Icon(Icons.Rounded.Folder, contentDescription = null) } },
                                title = folder.name,
                                subtitle = "${folder.songs.size} songs · ${folder.path}",
                                onClick = { onOpen(SongCollection(folder.name, folder.path, folder.songs)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListEntry(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A page for one album, artist or folder. */
@Composable
fun CollectionScreen(vm: MainViewModel, collection: SongCollection, onBack: () -> Unit) {
    val state by vm.player.collectAsStateWithLifecycle()
    val list = collection.songs
    val total = remember(list) { list.sumOf { it.durationMs } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(list.firstOrNull(), Modifier.size(120.dp), corner = 16.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            collection.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            collection.subtitle, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${list.size} songs · ${formatDuration(total)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(onClick = { vm.play(list, 0) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = { vm.shuffleAll(list) }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        items(list.size, key = { list[it].id }) { i ->
            val song = list[i]
            SongRow(
                song = song,
                isCurrent = state.currentSong?.id == song.id,
                onClick = { vm.play(list, i) },
                onPlayNext = { vm.playNext(song) },
                onAddToQueue = { vm.addToQueue(song) },
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
