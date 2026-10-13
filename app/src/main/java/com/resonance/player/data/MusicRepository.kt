package com.resonance.player.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phone's music library from Android's media index. Local files only. */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val result = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sort
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(projection[0])
                val iTitle = c.getColumnIndexOrThrow(projection[1])
                val iArtist = c.getColumnIndexOrThrow(projection[2])
                val iAlbum = c.getColumnIndexOrThrow(projection[3])
                val iAlbumId = c.getColumnIndexOrThrow(projection[4])
                val iDur = c.getColumnIndexOrThrow(projection[5])
                val iData = c.getColumnIndexOrThrow(projection[6])
                val iTrack = c.getColumnIndexOrThrow(projection[7])
                val iYear = c.getColumnIndexOrThrow(projection[8])
                val iAdded = c.getColumnIndexOrThrow(projection[9])
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: ""
                    val rawTitle = c.getString(iTitle)
                    result += Song(
                        id = c.getLong(iId),
                        title = rawTitle?.takeIf { it.isNotBlank() }
                            ?: path.substringAfterLast('/').substringBeforeLast('.'),
                        artist = clean(c.getString(iArtist), "Unknown artist"),
                        album = clean(c.getString(iAlbum), "Unknown album"),
                        albumId = c.getLong(iAlbumId),
                        durationMs = c.getLong(iDur),
                        path = path,
                        track = c.getInt(iTrack) % 1000,
                        year = c.getInt(iYear),
                        dateAdded = c.getLong(iAdded),
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted yet; return empty.
        }
        result
    }

    private fun clean(value: String?, fallback: String): String =
        if (value.isNullOrBlank() || value == "<unknown>") fallback else value

    companion object {
        fun albums(songs: List<Song>): List<Album> =
            songs.groupBy { it.albumId }.map { (id, list) ->
                val sorted = list.sortedWith(compareBy<Song> { it.track }.thenBy { it.title.lowercase() })
                val artists = list.map { it.artist }.distinct()
                Album(id, list.first().album, if (artists.size == 1) artists[0] else "Various artists", sorted)
            }.sortedBy { it.title.lowercase() }

        fun artists(songs: List<Song>): List<Artist> =
            songs.groupBy { it.artist }.map { (name, list) ->
                Artist(name, list.sortedWith(compareBy<Song> { it.album.lowercase() }.thenBy { it.track }))
            }.sortedBy { it.name.lowercase() }

        fun folders(songs: List<Song>): List<Folder> =
            songs.groupBy { it.folder }.map { (path, list) ->
                Folder(path, list.sortedBy { it.path.lowercase() })
            }.sortedBy { it.name.lowercase() }
    }
}
