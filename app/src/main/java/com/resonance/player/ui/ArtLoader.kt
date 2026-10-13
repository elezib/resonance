package com.resonance.player.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resonance.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads album artwork from the phone's media index, with a small memory cache. */
object ArtLoader {
    private val cache = LruCache<Long, Bitmap>(60)
    private val missing = HashSet<Long>()

    suspend fun load(context: Context, song: Song, size: Int = 600): Bitmap? = withContext(Dispatchers.IO) {
        val key = song.albumId
        cache.get(key)?.let { return@withContext it }
        if (synchronized(missing) { key in missing }) return@withContext null
        val bmp: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
            } else {
                val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), key)
                context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing += key }
        bmp
    }

    fun clearMissing() = synchronized(missing) { missing.clear() }
}

@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, song?.albumId) {
        value = song?.let { ArtLoader.load(context, it) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

