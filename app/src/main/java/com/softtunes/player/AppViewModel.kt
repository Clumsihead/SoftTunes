package com.softtunes.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.Room
import androidx.room.withTransaction
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

@Immutable
data class PlayerUi(
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

@Immutable
data class ConvUi(
    val running: Boolean = false,
    val percent: Float = 0f,
    val status: String = "",
    val resultName: String? = null,
    val error: String? = null,
)

/** Called from Python (converter.py) with download/encode progress. */
interface ProgressListener {
    fun onProgress(percent: Double, status: String)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val db = Room.databaseBuilder(app, AppDb::class.java, "softtunes.db")
        .addMigrations(MIGRATION_1_2).build()
    private val dao = db.dao()
    private val prefs = app.getSharedPreferences("softtunes", 0)

    val allSongs = dao.songs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(
        runCatching { SortOption.valueOf(prefs.getString("sort", null) ?: "") }.getOrDefault(SortOption.NAME_ASC)
    )
    fun setQuery(q: String) { query.value = q }
    fun setSort(o: SortOption) { sort.value = o; prefs.edit().putString("sort", o.name).apply() }

    /** Filtered + sorted list, computed off the main thread so scrolling never waits on it. */
    val songs: StateFlow<List<Song>> = combine(allSongs, query, sort) { list, q, s ->
        val filtered = if (q.isBlank()) list else list.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        sortSongs(filtered, s)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun playlistSongs(id: Long) = dao.playlistSongs(id)

    val scanning = MutableStateFlow(false)
    val player = MutableStateFlow(PlayerUi())
    val position = MutableStateFlow(0L)   // separate so 2x/sec ticks only recompose the seek bar
    val conv = MutableStateFlow(ConvUi())

    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) = refresh()
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(ctx))

        viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) position.value = c.currentPosition
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val md: MediaMetadata = c.mediaMetadata
        position.value = c.currentPosition
        player.value = PlayerUi(
            hasItem = c.mediaItemCount > 0,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    // ---- playback ----
    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare(); c.play()
    }
    fun playShuffled(list: List<Song>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        play(list, (list.indices).random())
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); refresh() }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---- library ----
    private fun folders(): Set<String> = prefs.getStringSet("folders", emptySet()).orEmpty()

    fun rescan() {
        if (scanning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            scanning.value = true
            try {
                val all = scanAll()
                db.withTransaction {   // one atomic swap -> the list updates once, no empty flash
                    dao.clearSongs()
                    dao.insertSongs(all.distinctBy { it.uri })
                }
            } finally { scanning.value = false }
        }
    }

    /** Blocking scan at background priority so it never steals CPU from scrolling. */
    private fun scanAll(): List<Song> {
        val prev = Process.getThreadPriority(0)
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            val known = allSongs.value.associateBy { it.uri }
            val all = Scanner.mediaStore(ctx).toMutableList()
            folders().forEach { all += Scanner.tree(ctx, Uri.parse(it), known) }
            return all
        } finally {
            Process.setThreadPriority(prev)
        }
    }

    fun addFolder(uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putStringSet("folders", folders() + uri.toString()).apply()
        rescan()
    }

    // ---- playlists ----
    fun createPlaylist(name: String) = viewModelScope.launch { dao.insertPlaylist(Playlist(name = name.trim())) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.clearEntries(id); dao.deletePlaylist(id) }
    fun addToPlaylist(id: Long, uri: String) =
        viewModelScope.launch { dao.addEntry(PlaylistEntry(id, uri, dao.nextPosition(id))) }
    fun removeFromPlaylist(id: Long, uri: String) = viewModelScope.launch { dao.removeEntry(id, uri) }

    // ---- YouTube -> MP3 ----
    fun convert(url: String, kbps: Int) {
        if (conv.value.running) return
        viewModelScope.launch(Dispatchers.IO) {
            conv.value = ConvUi(running = true, status = "Starting…")
            try {
                val work = File(ctx.cacheDir, "convert").apply { mkdirs() }
                val cb = object : ProgressListener {
                    override fun onProgress(percent: Double, status: String) {
                        conv.update { it.copy(percent = (percent / 100.0).toFloat().coerceIn(0f, 1f), status = status) }
                    }
                }
                val path = run { if (!Python.isStarted()) Python.start(AndroidPlatform(ctx)); Python.getInstance() }
                    .getModule("converter")
                    .callAttr("convert", url.trim(), work.absolutePath, kbps, cb).toString()
                val name = saveToMusic(File(path))
                conv.value = ConvUi(percent = 1f, status = "Saved to Music/SoftTunes", resultName = name)
                rescan()
            } catch (e: Exception) {
                conv.value = ConvUi(error = e.message ?: "Conversion failed")
            }
        }
    }

    private fun saveToMusic(file: File): String {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SoftTunes")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = ctx.contentResolver.insert(coll, values) ?: error("Could not create the file in Music")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        file.delete()
        return file.name
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
