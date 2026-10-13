package com.resonance.player.ui

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import com.resonance.player.audio.AudioEngine
import com.resonance.player.audio.EqSettings
import com.resonance.player.audio.ReplayGainMode
import com.resonance.player.data.EqPreset
import com.resonance.player.data.LyricLine
import com.resonance.player.data.Lyrics
import com.resonance.player.data.MusicRepository
import com.resonance.player.data.Prefs
import com.resonance.player.data.Song
import com.resonance.player.data.UiSettings
import com.resonance.player.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class QueueEntry(val index: Int, val mediaId: String, val title: String, val artist: String)

data class PlayerUiState(
    val currentSong: Song? = null,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val queueIndex: Int = -1,
) {
    val hasMedia: Boolean get() = queue.isNotEmpty()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private val prefs = Prefs(app)
    private var controller: MediaController? = null
    private var songsById: Map<String, Song> = emptyMap()

    // Library
    val songs = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(false)
    val albums = songs.map { MusicRepository.albums(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val artists = songs.map { MusicRepository.artists(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val folders = songs.map { MusicRepository.folders(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Playback
    val player = MutableStateFlow(PlayerUiState())
    val position = MutableStateFlow(0L)
    val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val artColor = MutableStateFlow<Int?>(null)
    val sleepTimerEndMs = MutableStateFlow<Long?>(null)
    private var sleepJob: Job? = null

    // Sound + appearance
    val eq = MutableStateFlow(prefs.loadEq())
    val replayGain = MutableStateFlow(prefs.replayGainMode)
    val customPresets = MutableStateFlow(prefs.loadCustomPresets())
    val ui = MutableStateFlow(prefs.loadUi())

    init {
        AudioEngine.update(eq.value)
        AudioEngine.replayGainMode = replayGain.value
        connect()
        startPositionTicker()
        watchCurrentSong()
    }

    // ---------- Library ----------

    fun loadLibrary() {
        if (loading.value) return
        viewModelScope.launch {
            loading.value = true
            val list = repo.loadSongs()
            songsById = list.associateBy { it.id.toString() }
            songs.value = list
            loading.value = false
            refreshState()
        }
    }

    fun rescan() {
        ArtLoader.clearMissing()
        loadLibrary()
    }

    // ---------- Player connection ----------

    private fun connect() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                val c = MediaController.Builder(app, token).buildAsync().await()
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = refreshState()
                })
                refreshState()
            } catch (e: Exception) {
                // Service unavailable; controls stay disabled.
            }
        }
    }

    private fun refreshState() {
        val c = controller ?: return
        val count = c.mediaItemCount
        val queue = ArrayList<QueueEntry>(count)
        for (i in 0 until count) {
            val item = c.getMediaItemAt(i)
            queue += entryFor(i, item)
        }
        val current = c.currentMediaItem
        val song = current?.mediaId?.let { songsById[it] }
        val dur = c.duration
        player.value = PlayerUiState(
            currentSong = song,
            currentTitle = song?.title ?: current?.mediaMetadata?.title?.toString() ?: "",
            currentArtist = song?.artist ?: current?.mediaMetadata?.artist?.toString() ?: "",
            isPlaying = c.isPlaying,
            durationMs = if (dur == C.TIME_UNSET || dur < 0) (song?.durationMs ?: 0L) else dur,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            queueIndex = if (count == 0) -1 else c.currentMediaItemIndex,
        )
        position.value = c.currentPosition
    }

    private fun entryFor(index: Int, item: MediaItem): QueueEntry {
        val s = songsById[item.mediaId]
        return QueueEntry(
            index = index,
            mediaId = item.mediaId,
            title = s?.title ?: item.mediaMetadata.title?.toString() ?: "Unknown",
            artist = s?.artist ?: item.mediaMetadata.artist?.toString() ?: "",
        )
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (true) {
                controller?.let { if (it.isPlaying) position.value = it.currentPosition }
                delay(250)
            }
        }
    }

    private fun watchCurrentSong() {
        val songFlow = player.map { it.currentSong }.distinctUntilChanged()
        // Lyrics: embedded tags first, then a .lrc file next to the song.
        viewModelScope.launch {
            combine(AudioEngine.embeddedLyrics, songFlow) { embedded, song ->
                val raw = embedded ?: song?.let { Lyrics.readSidecar(it) }
                raw?.let { Lyrics.parse(it) } ?: emptyList()
            }.flowOn(Dispatchers.IO).collect { lyrics.value = it }
        }
        // Accent colour from the album art.
        viewModelScope.launch {
            songFlow.collect { song ->
                if (song == null) { artColor.value = null; return@collect }
                val bmp = ArtLoader.load(getApplication(), song)
                artColor.value = if (bmp == null) null else withContext(Dispatchers.Default) {
                    val p = Palette.from(bmp).maximumColorCount(16).generate()
                    (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch)?.rgb
                }
            }
        }
    }

    // ---------- Transport ----------

    fun play(list: List<Song>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        play(list, Random.nextInt(list.size), shuffle = true)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(song), 0); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { play(listOf(song), 0); return }
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position.value = ms
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(queueIndex: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(queueIndex)
        c.play()
    }

    fun removeFromQueue(queueIndex: Int) { controller?.removeMediaItem(queueIndex) }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) { sleepTimerEndMs.value = null; return }
        val ms = minutes * 60_000L
        sleepTimerEndMs.value = System.currentTimeMillis() + ms
        sleepJob = viewModelScope.launch {
            delay(ms)
            controller?.pause()
            sleepTimerEndMs.value = null
        }
    }

    // ---------- Sound ----------

    private fun setEq(s: EqSettings) {
        eq.value = s
        AudioEngine.update(s)
        prefs.saveEq(s)
    }

    fun setEqEnabled(on: Boolean) = setEq(eq.value.copy(enabled = on))
    fun setPreamp(db: Float) = setEq(eq.value.copy(preampDb = db))
    fun setBass(db: Float) = setEq(eq.value.copy(bassDb = db))
    fun setTreble(db: Float) = setEq(eq.value.copy(trebleDb = db))
    fun setLimiter(on: Boolean) = setEq(eq.value.copy(limiter = on))

    fun setBand(index: Int, db: Float) {
        val bands = eq.value.bands.copyOf()
        bands[index] = db
        setEq(eq.value.copy(bands = bands))
    }

    fun applyPreset(p: EqPreset) {
        setEq(eq.value.copy(enabled = true, bands = p.bands.copyOf(), bassDb = p.bass, trebleDb = p.treble))
    }

    fun resetEq() = setEq(EqSettings(enabled = eq.value.enabled, limiter = eq.value.limiter))

    fun savePreset(name: String) {
        val clean = name.trim().ifBlank { return }
        val s = eq.value
        val preset = EqPreset(clean, s.bands.copyOf(), s.bassDb, s.trebleDb, custom = true)
        val list = customPresets.value.filter { it.name != clean } + preset
        customPresets.value = list
        prefs.saveCustomPresets(list)
    }

    fun deletePreset(p: EqPreset) {
        val list = customPresets.value.filter { it.name != p.name }
        customPresets.value = list
        prefs.saveCustomPresets(list)
    }

    fun setReplayGain(mode: ReplayGainMode) {
        replayGain.value = mode
        AudioEngine.replayGainMode = mode
        prefs.replayGainMode = mode
    }

    // ---------- Appearance ----------

    fun updateUi(transform: (UiSettings) -> UiSettings) {
        val u = transform(ui.value)
        ui.value = u
        prefs.saveUi(u)
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}
