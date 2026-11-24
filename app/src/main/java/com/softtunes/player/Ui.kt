package com.softtunes.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

// ------------------------------------------------------------------ theme
private val LightColors = lightColorScheme(
    primary = Color(0xFFB07A7A), onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DEDA), onPrimaryContainer = Color(0xFF4A2E2E),
    secondary = Color(0xFF7F9C8C), secondaryContainer = Color(0xFFDDE8E0), onSecondaryContainer = Color(0xFF2B3B32),
    background = Color(0xFFFAF5EE), surface = Color(0xFFFAF5EE),
    surfaceVariant = Color(0xFFF0E7DB), onSurface = Color(0xFF3B3330), onSurfaceVariant = Color(0xFF7A6E66),
    outline = Color(0xFFD9CCBD), error = Color(0xFFB3574F),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFDDA7A3), onPrimary = Color(0xFF3B2322),
    primaryContainer = Color(0xFF4A3534), onPrimaryContainer = Color(0xFFF3DEDA),
    secondary = Color(0xFFA9C4B4), secondaryContainer = Color(0xFF34443B), onSecondaryContainer = Color(0xFFDDE8E0),
    background = Color(0xFF241F1E), surface = Color(0xFF241F1E),
    surfaceVariant = Color(0xFF352E2C), onSurface = Color(0xFFEFE6DC), onSurfaceVariant = Color(0xFFB8AB9F),
    outline = Color(0xFF5A4F49), error = Color(0xFFE59A92),
)

@Composable
fun SoftTunesTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        typography = type,
        content = content,
    )
}

private val CoverInk = Color(0x8C5A4A44)

private val spines = listOf(
    Color(0xFFF3D5D0), Color(0xFFDDE8E0), Color(0xFFE6DDF0),
    Color(0xFFF4E6C8), Color(0xFFD6E4EC), Color(0xFFEBD9CF),
)

/** Soft "book-spine" tile standing in for album art. */
@Composable
fun Cover(seed: String, size: Dp) {
    val color = remember(seed) { spines[(seed.hashCode() and 0x7fffffff) % spines.size] }
    val letter = remember(seed) { seed.firstOrNull()?.uppercase() ?: "♪" }
    Box(
        Modifier.size(size).background(color, RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            fontFamily = FontFamily.Serif, fontSize = (size.value * 0.42f).sp,
            color = CoverInk,
        )
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val sec = s % 60
    return "${s / 60}:${if (sec < 10) "0" else ""}$sec"
}

private val lossless = setOf("FLAC", "WAV", "ALAC", "AIF", "AIFF")

// ------------------------------------------------------------------ app shell
@Composable
fun SoftTunesApp(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    val ui by vm.player.collectAsState()

    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.rescan()
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val scan: () -> Unit = {
        if (ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) vm.rescan()
        else permLauncher.launch(perm)
    }
    LaunchedEffect(Unit) { scan() }
    BackHandler(showPlayer) { showPlayer = false }

    if (showPlayer) {
        NowPlaying(vm, ui) { showPlayer = false }
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (ui.hasItem) MiniPlayer(vm, ui) { showPlayer = true }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    listOf(
                        Triple("Library", Icons.Filled.LibraryMusic, 0),
                        Triple("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay, 1),
                        Triple("Convert", Icons.Filled.Download, 2),
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, label) }, label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> LibraryScreen(vm, scan)
                1 -> PlaylistsScreen(vm)
                else -> ConvertScreen(vm)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------ library
@Composable
fun LibraryScreen(vm: AppViewModel, onScan: () -> Unit) {
    val total by vm.allSongs.collectAsState()
    val shown by vm.songs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val sort by vm.sort.collectAsState()
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Song?>(null) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::addFolder) }
    val onPlay = remember(shown) { { i: Int -> vm.play(shown, i) } }
    val onMore = remember { { s: Song -> target = s } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "header") {
            Column {
                ScreenHeader("Your Library", "${total.size} songs on this device")
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onScan) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Scan device")
                    }
                    OutlinedButton(onClick = { folder.launch(null) }) {
                        Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add folder")
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it; vm.setQuery(it) }, singleLine = true,
                    placeholder = { Text("Search title, artist, album") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
                Box(Modifier.padding(horizontal = 20.dp)) {
                    AssistChip(
                        onClick = { sortMenu = true },
                        label = { Text("Sort: ${sort.label}") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOption.entries.forEach { o ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (o == sort) "✓  " else "     ") + o.label,
                                        fontWeight = if (o == sort) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                onClick = { vm.setSort(o); sortMenu = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                if (shown.isEmpty() && !scanning) {
                    Text(
                        "No music found.\nTap “Scan device” or pick a folder.",
                        Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(shown, key = { _, s -> s.uri }, contentType = { _, _ -> "song" }) { i, s ->
            SongRow(s, i, onPlay, onMore, Icons.Filled.MoreVert, "More")
        }
    }

    target?.let { song ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) Text("Create a playlist first in the Playlists tab.")
                else Column {
                    playlists.forEach { pl ->
                        TextButton(onClick = { vm.addToPlaylist(pl.id, song.uri); target = null }) { Text(pl.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { target = null }) { Text("Cancel") } },
        )
    }
}

/** Kept deliberately light: no per-row menus or state, so 400+ rows scroll cheaply. */
@Composable
private fun SongRow(
    song: Song,
    index: Int,
    onPlay: (Int) -> Unit,
    onAction: (Song) -> Unit,
    actionIcon: ImageVector,
    actionLabel: String,
) {
    val duration = remember(song.durationMs) { fmt(song.durationMs) }
    val sub = remember(song.artist, song.album) { "${song.artist} · ${song.album}" }
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(index) }.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.title, 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (song.format.isNotBlank()) {
            val bg = if (song.format in lossless) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
            Text(
                song.format,
                Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(duration, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onAction(song) }) { Icon(actionIcon, actionLabel) }
    }
}

// ------------------------------------------------------------------ playlists
@Composable
fun PlaylistsScreen(vm: AppViewModel) {
    val playlists by vm.playlists.collectAsState()
    var open by remember { mutableStateOf<Playlist?>(null) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    open?.let { pl ->
        BackHandler { open = null }
        PlaylistDetail(vm, pl) { open = null }
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Playlists", "${playlists.size} collections")
        Button(onClick = { creating = true }, Modifier.padding(horizontal = 20.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New playlist")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(playlists, key = { it.id }) { pl ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = pl }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(pl.name, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(pl.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.deletePlaylist(pl.id) }) { Icon(Icons.Filled.Delete, "Delete playlist") }
                }
            }
        }
    }
    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.createPlaylist(name); name = ""; creating = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlaylistDetail(vm: AppViewModel, pl: Playlist, onBack: () -> Unit) {
    val songs by remember(pl.id) { vm.playlistSongs(pl.id) }.collectAsState(emptyList())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(pl.name, style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = songs.isNotEmpty(), onClick = { vm.play(songs, 0) }) {
                Icon(Icons.Filled.PlayArrow, null); Text("Play")
            }
            FilledTonalButton(enabled = songs.isNotEmpty(), onClick = { vm.playShuffled(songs) }) {
                Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Shuffle")
            }
        }
        if (songs.isEmpty()) {
            Text("Empty. Add songs from the Library using the ⋮ menu.", Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            itemsIndexed(songs, key = { _, s -> s.uri }) { i, s ->
                SongRow(s, i, { idx -> vm.play(songs, idx) }, { vm.removeFromPlaylist(pl.id, it.uri) }, Icons.Filled.Delete, "Remove")
            }
        }
    }
}

// ------------------------------------------------------------------ now playing
@Composable
private fun MiniPlayer(vm: AppViewModel, ui: PlayerUi, onOpen: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(ui.title, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(ui.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = vm::toggle) {
                Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
        }
    }
}

@Composable
private fun NowPlaying(vm: AppViewModel, ui: PlayerUi, onClose: () -> Unit) {
    val pos by vm.position.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (ui.durationMs > 0) pos.toFloat() / ui.durationMs else 0f
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
        }
        Spacer(Modifier.weight(1f))
        Cover(ui.title, 280.dp)
        Spacer(Modifier.height(32.dp))
        Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, textAlign = TextAlign.Center)
        Text(ui.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Slider(
            value = drag ?: frac,
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { vm.seekTo((it * ui.durationMs).toLong()) }; drag = null },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), style = MaterialTheme.typography.bodySmall)
            Text(fmt(ui.durationMs), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = vm::toggleShuffle) {
                Icon(Icons.Filled.Shuffle, "Shuffle",
                    tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::prev) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp)) }
            FilledIconButton(onClick = vm::toggle, Modifier.size(72.dp), shape = CircleShape) {
                Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(40.dp))
            }
            IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp)) }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                    tint = if (ui.repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ convert
@Composable
fun ConvertScreen(vm: AppViewModel) {
    val st by vm.conv.collectAsState()
    var url by remember { mutableStateOf("") }
    var kbps by remember { mutableIntStateOf(192) }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Convert", "Paste a YouTube link and save it as MP3")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                placeholder = { Text("https://youtube.com/watch?v=…") },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { url = it } }) {
                        Icon(Icons.Filled.ContentPaste, "Paste")
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quality", color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(128, 192, 256, 320).forEach {
                    FilterChip(selected = kbps == it, onClick = { kbps = it }, label = { Text("$it") })
                }
            }
            Button(
                enabled = url.isNotBlank() && !st.running,
                onClick = { vm.convert(url, kbps) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (st.running) "Converting…" else "Convert to MP3") }

            if (st.running || st.resultName != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { st.percent }, Modifier.fillMaxWidth())
                    Text(st.status, style = MaterialTheme.typography.bodySmall)
                }
            }
            st.resultName?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Saved: $it\nFind it in your Library under Music/SoftTunes.", Modifier.padding(14.dp).fillMaxWidth())
                }
            }
            st.error?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(it, Modifier.padding(14.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Text(
                "Only convert audio you own or have permission to download. Downloading from YouTube can break its Terms of Service.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
