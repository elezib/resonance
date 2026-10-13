package com.resonance.player.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val folder: String get() = path.substringBeforeLast('/', "")

    fun toMediaItem(): MediaItem {
        val u = uri
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(u)
            // The URI also travels in requestMetadata so the playback service can resolve it.
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
    }
}

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>)

data class Artist(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
}

data class Folder(val path: String, val songs: List<Song>) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path }
}

/** A list of songs the UI can open as its own page (album, artist, folder). */
data class SongCollection(val title: String, val subtitle: String, val songs: List<Song>)

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
