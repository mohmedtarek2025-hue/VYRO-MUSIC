package com.mohamedtarek.vyromusic

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Night = Color(0xFF090A10)
private val Panel = Color(0xFF151722)
private val Purple = Color(0xFF9B6CFF)
private val Muted = Color(0xFF9A9CAD)

data class Song(val id: Long, val title: String, val artist: String, val album: String, val uri: Uri, val duration: Long)

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setContent {
                    controller?.let { VyroApp(it, ::loadSongs) }
                }
            } catch (error: Exception) {
                setContent { MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
                        Text("Unable to connect to the playback service. Please restart VYRO MUSIC.", color = Color.White)
                    }
                } }
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun loadSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION)
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list += Song(id, c.getString(titleCol) ?: "Unknown title",
                    c.getString(artistCol) ?: "Unknown artist", c.getString(albumCol) ?: "Unknown album",
                    Uri.withAppendedPath(collection, id.toString()), c.getLong(durationCol))
            }
        }
        return list
    }
    override fun onDestroy() {
        // Releasing the future also releases the controller it created.
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VyroApp(player: Player, loadSongs: () -> List<Song>) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var permission by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Songs") }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    val appScope = rememberCoroutineScope()
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerLabel by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionName = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) songs = loadSongs()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED
        if (permission) {
            songs = loadSongs()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            launcher.launch(permissionName)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { selected = player.currentMediaItemIndex }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    val filtered = songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Night, surface = Panel, primary = Purple, secondary = Purple,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = Night, bottomBar = {
            Column(Modifier.background(Night)) {
                if (selected >= 0 && selected < songs.size) MiniPlayer(
                    song = songs[selected], playing = playing, onToggle = { if (player.isPlaying) player.pause() else player.play() },
                    onOpen = { tab = "Now Playing" }
                )
                NavigationBar(containerColor = Panel) {
                    listOf("Songs", "Albums", "Playlists", "Settings").forEach { item ->
                        NavigationBarItem(selected = tab == item, onClick = { tab = item },
                            icon = { Icon(when(item) { "Songs" -> Icons.Default.MusicNote; "Albums" -> Icons.Default.Album; "Playlists" -> Icons.Default.QueueMusic; else -> Icons.Default.Settings }, null) },
                            label = { Text(item) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Purple, selectedTextColor = Purple, indicatorColor = Purple.copy(alpha=.15f), unselectedTextColor = Muted))
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VYRO", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("M U S I C", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    }
                    IconButton(onClick = { songs = loadSongs() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Muted) }
                    Icon(Icons.Default.MoreVert, null, tint = Muted)
                }
                Spacer(Modifier.height(22.dp))
                Text(when(tab) { "Now Playing" -> "Now Playing"; "Albums" -> "Your albums"; "Playlists" -> "Your playlists"; "Settings" -> "Experience"; else -> "Your library" },
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                when(tab) {
                    "Settings" -> SettingsContent()
                    "Now Playing" -> {
                        if (selected in songs.indices) NowPlaying(songs[selected], playing, position, duration,
                            onToggle = { if (player.isPlaying) player.pause() else player.play() },
                            onSeek = { player.seekTo(it) },
                            onPrevious = { player.seekToPrevious() }, onNext = { player.seekToNext() },
                            shuffle = shuffle, repeat = repeat,
                            onShuffle = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                            onRepeat = { repeat = !repeat; player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF },
                            sleepTimerLabel = sleepTimerLabel,
                            onSleepTimer = { minutes ->
                                sleepTimerJob?.cancel()
                                if (minutes == 0) {
                                    sleepTimerLabel = null
                                } else {
                                    sleepTimerLabel = "$minutes min"
                                    sleepTimerJob = appScope.launch {
                                        delay(minutes * 60_000L)
                                        player.pause()
                                        sleepTimerLabel = null
                                    }
                                }
                            })
                        else EmptyState("Choose a song from your library")
                    }
                    "Albums" -> {
                        val albums = songs.map { it.album }.distinct()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            itemsIndexed(albums) { _, album ->
                                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { query = album; tab = "Songs" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AlbumArt(); Spacer(Modifier.width(12.dp)); Text(album, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    "Playlists" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureTile("Liked songs", "A quick place for your favorites", Icons.Default.Favorite)
                        FeatureTile("Recently played", "Your listening history", Icons.Default.History)
                        FeatureTile("Create playlist", "Playlist management is ready for the next build", Icons.Default.Add)
                    }
                    else -> {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs, artists, albums…", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            singleLine = true, shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, unfocusedBorderColor = Panel, focusedContainerColor = Panel, unfocusedContainerColor = Panel, cursorColor = Purple))
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${filtered.size} TRACKS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("OFFLINE LIBRARY", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!permission) EmptyState("Allow audio access to scan your music")
                        else if (filtered.isEmpty()) EmptyState("No music found on this device")
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                            itemsIndexed(filtered) { _, song ->
                                val realIndex = songs.indexOf(song)
                                SongRow(song, selected == realIndex && playing) {
                                    selected = realIndex
                                    player.setMediaItems(songs.map { MediaItem.fromUri(it.uri) }, realIndex, 0L)
                                    player.prepare(); player.play()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SongRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if(active) Purple.copy(alpha=.13f) else Panel, RoundedCornerShape(15.dp))
        .clickable(onClick = onClick).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if(active) Purple else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.duration), color = Muted, fontSize = 11.sp)
        Spacer(Modifier.width(5.dp)); Icon(Icons.Default.MoreHoriz, null, tint = Muted)
    }
}
@Composable private fun AlbumArt() {
    Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Color(0xFF38265E), Color(0xFF171A29))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.GraphicEq, null, tint = Purple, modifier = Modifier.size(25.dp))
    }
}
@Composable private fun MiniPlayer(song: Song, playing: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).background(Panel, RoundedCornerShape(15.dp)).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(song.artist, color = Muted, fontSize = 11.sp) }
        IconButton(onClick = onToggle) { Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Purple) }
    }
}
@Composable private fun NowPlaying(song: Song, playing: Boolean, position: Long, duration: Long, onToggle: () -> Unit, onSeek: (Long) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, shuffle: Boolean, repeat: Boolean, onShuffle: () -> Unit, onRepeat: () -> Unit, sleepTimerLabel: String?, onSleepTimer: (Int) -> Unit) {
    var showSleepDialog by remember { mutableStateOf(false) }
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                        TextButton(onClick = { onSleepTimer(minutes); showSleepDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop playback in $minutes minutes")
                        }
                    }
                    TextButton(onClick = { onSleepTimer(0); showSleepDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel timer")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(25.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(Color(0xFF38265E), Color(0xFF141725))), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.GraphicEq, null, tint = Purple, modifier = Modifier.size(120.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(song.artist, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        Slider(value = if(duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f, onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..(duration.coerceAtLeast(1).toFloat()), colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(position), color = Muted, fontSize = 11.sp); Text(formatTime(duration), color = Muted, fontSize = 11.sp) }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, null, tint = if(shuffle) Purple else Muted) }
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(35.dp)) }
            IconButton(onClick = onToggle, modifier = Modifier.size(70.dp).background(Purple, CircleShape)) { Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(35.dp)) }
            IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, null, tint = if(repeat) Purple else Muted) }
        }
        Spacer(Modifier.height(18.dp))
        AssistChip(
            onClick = { showSleepDialog = true },
            label = { Text(sleepTimerLabel?.let { "SLEEP TIMER · $it" } ?: "SLEEP TIMER") },
            leadingIcon = { Icon(Icons.Default.Bedtime, null) }
        )
        Text("Sleep timer pauses playback when the selected time ends", color = Muted, fontSize = 10.sp)
    }
}
@Composable private fun SettingsContent() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf("Audio quality" to "Use original file quality", "Equalizer" to "System audio effects integration", "Sleep timer" to "Stop playback after a set time", "Crossfade" to "Smooth transitions between tracks", "Smart playlists" to "Recently played and favorites", "Backup & Restore" to "Export your library preferences", "Gesture controls" to "Quick player interactions").forEach { (title, sub) ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(15.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = Purple); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(sub, color = Muted, fontSize = 11.sp) }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("VYRO MUSIC  •  VERSION 1.1.2", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text("Developed by Mohamed Tarek", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun FeatureTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
        Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) }
    }
}
@Composable private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = Purple, modifier = Modifier.size(45.dp))
            Spacer(Modifier.height(12.dp)); Text(message, color = Muted)
        }
    }
}
private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}
