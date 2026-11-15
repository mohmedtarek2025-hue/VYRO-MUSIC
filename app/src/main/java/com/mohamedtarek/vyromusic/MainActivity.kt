package com.mohamedtarek.vyromusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Night = Color(0xFF06070C)
private val Panel = Color(0xFF141622)
private val Purple = Color(0xFF9B6CFF)
private val Muted = Color(0xFF9A9CAD)

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val duration: Long,
    val dateAdded: Long,
    val filePath: String?
)

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
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
                            Text("Unable to connect to playback service. Please restart VYRO MUSIC.", color = Color.White)
                        }
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = if (dataCol >= 0) c.getString(dataCol) else null
                list += Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown title",
                    artist = c.getString(artistCol) ?: "Unknown artist",
                    album = c.getString(albumCol) ?: "Unknown album",
                    uri = Uri.withAppendedPath(collection, id.toString()),
                    duration = c.getLong(durationCol),
                    dateAdded = c.getLong(dateCol),
                    filePath = path
                )
            }
        }
        return list
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VyroApp(player: Player, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()

    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var permission by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableLongStateOf(-1L) }
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(prefs.getString("sort_by", "TITLE") ?: "TITLE") }
    var tab by remember { mutableStateOf("Songs") }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }

    var likedIds by remember { mutableStateOf(loadIdSet(prefs.getString("liked_ids", "[]"))) }
    var recentIds by remember { mutableStateOf(loadIdList(prefs.getString("recent_ids", "[]"))) }
    var playlists by remember { mutableStateOf(loadPlaylists(prefs.getString("playlists", "{}"))) }
    var customLyrics by remember { mutableStateOf(loadLyricsMap(prefs.getString("lyrics_map", "{}"))) }

    var crossfadeSec by remember { mutableIntStateOf(prefs.getInt("crossfade_sec", 0)) }
    var gesturesEnabled by remember { mutableStateOf(prefs.getBoolean("gestures_enabled", true)) }
    var eqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Normal") ?: "Normal") }
    var bassStrength by remember { mutableIntStateOf(prefs.getInt("bass_strength", 0)) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("playback_speed", 1f)) }

    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }

    var activePlaylistView by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForLyrics by remember { mutableStateOf<Song?>(null) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    fun saveState() {
        prefs.edit()
            .putString("sort_by", sortBy)
            .putString("liked_ids", JSONArray(likedIds.toList()).toString())
            .putString("recent_ids", JSONArray(recentIds).toString())
            .putString("playlists", encodePlaylists(playlists))
            .putString("lyrics_map", encodeLyricsMap(customLyrics))
            .putInt("crossfade_sec", crossfadeSec)
            .putBoolean("gestures_enabled", gesturesEnabled)
            .putString("eq_preset", eqPreset)
            .putInt("bass_strength", bassStrength)
            .putFloat("playback_speed", playbackSpeed)
            .apply()
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", "1.1.2")
                    put("liked_ids", JSONArray(likedIds.toList()))
                    put("recent_ids", JSONArray(recentIds))
                    put("playlists", JSONObject(encodePlaylists(playlists)))
                    put("lyrics_map", JSONObject(encodeLyricsMap(customLyrics)))
                    put("crossfade_sec", crossfadeSec)
                    put("sort_by", sortBy)
                    put("eq_preset", eqPreset)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                Toast.makeText(context, "VYRO Backup saved successfully!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "{}"
                val root = JSONObject(text)
                if (root.has("liked_ids")) likedIds = loadIdSet(root.getJSONArray("liked_ids").toString())
                if (root.has("recent_ids")) recentIds = loadIdList(root.getJSONArray("recent_ids").toString())
                if (root.has("playlists")) playlists = loadPlaylists(root.getJSONObject("playlists").toString())
                if (root.has("lyrics_map")) customLyrics = loadLyricsMap(root.getJSONObject("lyrics_map").toString())
                if (root.has("crossfade_sec")) crossfadeSec = root.getInt("crossfade_sec")
                if (root.has("sort_by")) sortBy = root.getString("sort_by")
                if (root.has("eq_preset")) eqPreset = root.getString("eq_preset")
                saveState()
                Toast.makeText(context, "VYRO Backup restored successfully!", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionName = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) songs = loadSongs()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED
        if (permission) {
            songs = loadSongs()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            launcher.launch(permissionName)
        }
    }

    LaunchedEffect(eqPreset, bassStrength, playbackSpeed) {
        runCatching { player.playbackParameters = PlaybackParameters(playbackSpeed) }
        runCatching {
            val eq = Equalizer(0, 0)
            eq.enabled = true
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLvl = range[0]
            val maxLvl = range[1]
            val levels = when (eqPreset) {
                "Bass Boost" -> listOf(0.8f, 0.5f, 0.0f, 0.1f, 0.3f)
                "Phonk / EDM" -> listOf(0.9f, 0.4f, -0.2f, 0.5f, 0.8f)
                "Rock" -> listOf(0.6f, 0.3f, -0.1f, 0.4f, 0.7f)
                "Pop" -> listOf(-0.1f, 0.3f, 0.6f, 0.4f, -0.1f)
                "Vocal" -> listOf(-0.3f, 0.2f, 0.8f, 0.5f, 0.1f)
                else -> listOf(0f, 0f, 0f, 0f, 0f)
            }
            for (b in 0 until minOf(bands, levels.size)) {
                val target = ((levels[b] * (maxLvl - minLvl) / 2f)).toInt().toShort()
                eq.setBandLevel(b.toShort(), target)
            }
            val bass = BassBoost(0, 0)
            bass.enabled = bassStrength > 0
            if (bass.strengthSupported) bass.setStrength((bassStrength * 10).coerceIn(0, 1000).toShort())
        }
        saveState()
    }

    fun playQueue(queue: List<Song>, startIndex: Int) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        val chosen = queue[startIndex]
        currentSongId = chosen.id
        recentIds = (listOf(chosen.id) + recentIds.filter { it != chosen.id }).take(40)
        saveState()
        val mediaItems = queue.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
                .build()
        }
        player.volume = 1f
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull()
                if (id != null) {
                    currentSongId = id
                    recentIds = (listOf(id) + recentIds.filter { it != id }).take(40)
                    saveState()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, crossfadeSec) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            if (crossfadeSec > 0 && duration > crossfadeSec * 2000L && player.isPlaying) {
                val fadeWindow = crossfadeSec * 1000L
                val remaining = duration - position
                player.volume = when {
                    remaining in 1..fadeWindow -> (remaining.toFloat() / fadeWindow.toFloat()).coerceIn(0.15f, 1f)
                    position in 0..fadeWindow -> (position.toFloat() / fadeWindow.toFloat()).coerceIn(0.25f, 1f)
                    else -> 1f
                }
            } else {
                player.volume = 1f
            }
            delay(400)
        }
    }

    val currentSong = songs.firstOrNull { it.id == currentSongId }

    val sortedSongs = remember(songs, query, sortBy) {
        val filtered = songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        when (sortBy) {
            "ARTIST" -> filtered.sortedBy { it.artist.lowercase() }
            "DURATION" -> filtered.sortedByDescending { it.duration }
            "NEWEST" -> filtered.sortedByDescending { it.dateAdded }
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            sleepTimerRemainingSec = 0
        } else {
            sleepTimerRemainingSec = minutes * 60
            sleepTimerJob = appScope.launch {
                while (sleepTimerRemainingSec > 0) {
                    delay(1000L)
                    sleepTimerRemainingSec--
                }
                player.pause()
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { startSleepTimer(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop playback in $mins minutes") }
                    }
                    if (sleepTimerRemainingSec > 0) {
                        TextButton(
                            onClick = { startSleepTimer(0); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel active timer", color = Color(0xFFFF6B6B)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }

    if (showEqualizerDialog) {
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = { Text("VYRO Audio Engine & Equalizer") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Sound Presets", color = Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Normal", "Bass Boost", "Phonk / EDM", "Rock", "Pop", "Vocal").forEach { preset ->
                            FilterChip(
                                selected = eqPreset == preset,
                                onClick = { eqPreset = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                    Text("Bass Boost (${bassStrength}%)", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = bassStrength.toFloat(),
                        onValueChange = { bassStrength = it.toInt() },
                        valueRange = 0f..100f
                    )
                    Text("Playback Speed (${"%.2f".format(playbackSpeed)}x)", color = Color.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f, 1.0f, 1.15f, 1.25f).forEach { spd ->
                            FilterChip(
                                selected = playbackSpeed == spd,
                                onClick = { playbackSpeed = spd },
                                label = { Text("${spd}x") }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(context, "Using built-in VYRO Equalizer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open System Equalizer / Dolby")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEqualizerDialog = false }) { Text("Done") } }
        )
    }

    if (showCrossfadeDialog) {
        AlertDialog(
            onDismissRequest = { showCrossfadeDialog = false },
            title = { Text("Crossfade Transition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (crossfadeSec == 0) "Disabled (Instant track switch)" else "$crossfadeSec seconds smooth fade",
                        color = Purple,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = crossfadeSec.toFloat(),
                        onValueChange = { crossfadeSec = it.toInt(); saveState() },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showCrossfadeDialog = false }) { Text("Save") } }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name (e.g. Gym Phonk)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        playlists = playlists + (trimmed to (playlists[trimmed] ?: emptyList()))
                        saveState()
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } }
        )
    }

    songForMenu?.let { targetSong ->
        val isLiked = likedIds.contains(targetSong.id)
        AlertDialog(
            onDismissRequest = { songForMenu = null },
            title = { Text(targetSong.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            likedIds = if (isLiked) likedIds - targetSong.id else likedIds + targetSong.id
                            saveState()
                            songForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs") }
                    TextButton(
                        onClick = {
                            songForAddToPlaylist = targetSong
                            songForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add to Playlist...") }
                    TextButton(
                        onClick = {
                            songForLyrics = targetSong
                            songForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("View / Edit Lyrics") }
                }
            },
            confirmButton = { TextButton(onClick = { songForMenu = null }) { Text("Close") } }
        )
    }

    songForAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForAddToPlaylist = null },
            title = { Text("Add to Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (playlists.isEmpty()) {
                        Text("No custom playlists yet. Create one first!", color = Muted)
                    } else {
                        playlists.keys.forEach { pName ->
                            TextButton(
                                onClick = {
                                    val currentList = playlists[pName] ?: emptyList()
                                    if (!currentList.contains(targetSong.id)) {
                                        playlists = playlists + (pName to (currentList + targetSong.id))
                                        saveState()
                                    }
                                    Toast.makeText(context, "Added to $pName", Toast.LENGTH_SHORT).show()
                                    songForAddToPlaylist = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(pName) }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            songForAddToPlaylist = null
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ New Playlist") }
                }
            },
            confirmButton = { TextButton(onClick = { songForAddToPlaylist = null }) { Text("Close") } }
        )
    }

    songForLyrics?.let { targetSong ->
        val initialLyrics = remember(targetSong.id) {
            customLyrics[targetSong.id] ?: readSidecarLyrics(targetSong.filePath) ?: ""
        }
        var editingMode by remember { mutableStateOf(initialLyrics.isBlank()) }
        var draftText by remember { mutableStateOf(initialLyrics) }
        AlertDialog(
            onDismissRequest = { songForLyrics = null },
            title = { Text("Lyrics • ${targetSong.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingMode) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("Paste or write song lyrics here for offline access...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                        )
                    } else {
                        Text(
                            text = draftText.ifBlank { "No lyrics saved for this song yet." },
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (editingMode) {
                    TextButton(onClick = {
                        customLyrics = customLyrics + (targetSong.id to draftText.trim())
                        saveState()
                        editingMode = false
                    }) { Text("Save Lyrics") }
                } else {
                    TextButton(onClick = { editingMode = true }) { Text("Edit Lyrics") }
                }
            },
            dismissButton = { TextButton(onClick = { songForLyrics = null }) { Text("Close") } }
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Night,
            surface = Panel,
            primary = Purple,
            secondary = Purple,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                Column(Modifier.background(Night)) {
                    if (currentSong != null && tab != "Now Playing") {
                        MiniPlayer(
                            song = currentSong,
                            playing = playing,
                            onToggle = { if (player.isPlaying) player.pause() else player.play() },
                            onNext = { player.seekToNext() },
                            onOpen = { tab = "Now Playing" }
                        )
                    }
                    NavigationBar(containerColor = Panel) {
                        listOf("Songs", "Albums", "Playlists", "Settings").forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = {
                                    tab = item
                                    activePlaylistView = null
                                },
                                icon = {
                                    Icon(
                                        when (item) {
                                            "Songs" -> Icons.Default.MusicNote
                                            "Albums" -> Icons.Default.Album
                                            "Playlists" -> Icons.Default.QueueMusic
                                            else -> Icons.Default.Settings
                                        },
                                        contentDescription = item
                                    )
                                },
                                label = { Text(item) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Purple,
                                    selectedTextColor = Purple,
                                    indicatorColor = Purple.copy(alpha = .15f),
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VYRO", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Text("M U S I C", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    }
                    IconButton(onClick = { showEqualizerDialog = true }) {
                        Icon(Icons.Default.GraphicEq, "Equalizer", tint = Purple)
                    }
                    IconButton(onClick = {
                        songs = loadSongs()
                        Toast.makeText(context, "Library refreshed (${songs.size} tracks)", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Muted)
                    }
                }

                Spacer(Modifier.height(14.dp))

                when (tab) {
                    "Settings" -> {
                        Text("Experience & Audio Engine", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        SettingsContent(
                            eqPreset = eqPreset,
                            crossfadeSec = crossfadeSec,
                            sleepRemainingSec = sleepTimerRemainingSec,
                            gesturesEnabled = gesturesEnabled,
                            onOpenEqualizer = { showEqualizerDialog = true },
                            onOpenCrossfade = { showCrossfadeDialog = true },
                            onOpenSleepTimer = { showSleepDialog = true },
                            onToggleGestures = { gesturesEnabled = !gesturesEnabled; saveState() },
                            onBackup = { backupLauncher.launch("vyro_music_backup.json") },
                            onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                        )
                    }

                    "Now Playing" -> {
                        if (currentSong != null) {
                            NowPlayingScreen(
                                song = currentSong,
                                playing = playing,
                                position = position,
                                duration = duration,
                                isLiked = likedIds.contains(currentSong.id),
                                shuffle = shuffle,
                                repeatMode = repeatMode,
                                sleepRemainingSec = sleepTimerRemainingSec,
                                eqPreset = eqPreset,
                                gesturesEnabled = gesturesEnabled,
                                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                                onSeek = { player.seekTo(it) },
                                onPrevious = { player.seekToPrevious() },
                                onNext = { player.seekToNext() },
                                onToggleLike = {
                                    likedIds = if (likedIds.contains(currentSong.id)) likedIds - currentSong.id else likedIds + currentSong.id
                                    saveState()
                                },
                                onToggleShuffle = {
                                    shuffle = !shuffle
                                    player.shuffleModeEnabled = shuffle
                                },
                                onCycleRepeat = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    player.repeatMode = repeatMode
                                },
                                onOpenSleep = { showSleepDialog = true },
                                onOpenLyrics = { songForLyrics = currentSong },
                                onOpenEq = { showEqualizerDialog = true }
                            )
                        } else {
                            EmptyState("Choose a song from your library")
                        }
                    }

                    "Albums" -> {
                        Text("Your Albums", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        val albums = songs.groupBy { it.album }
                        if (albums.isEmpty()) {
                            EmptyState("No albums found")
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                itemsIndexed(albums.entries.toList()) { _, entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Panel, RoundedCornerShape(16.dp))
                                            .clickable {
                                                query = entry.key
                                                tab = "Songs"
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AlbumArt()
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.key, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text("${entry.value.size} tracks", color = Muted, fontSize = 12.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                                    }
                                }
                            }
                        }
                    }

                    "Playlists" -> {
                        val currentPlaylistName = activePlaylistView
                        if (currentPlaylistName == null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Your Playlists", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { showCreatePlaylistDialog = true }) {
                                    Icon(Icons.Default.Add, null, tint = Purple)
                                    Spacer(Modifier.width(4.dp))
                                    Text("New", color = Purple, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                item {
                                    FeatureTile(
                                        title = "Liked Songs",
                                        subtitle = "${likedIds.size} favorite tracks",
                                        icon = Icons.Default.Favorite,
                                        onClick = { activePlaylistView = "__LIKED__" }
                                    )
                                }
                                item {
                                    FeatureTile(
                                        title = "Recently Played",
                                        subtitle = "${recentIds.size} tracks in history",
                                        icon = Icons.Default.History,
                                        onClick = { activePlaylistView = "__RECENT__" }
                                    )
                                }
                                itemsIndexed(playlists.entries.toList()) { _, entry ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(Panel, RoundedCornerShape(16.dp))
                                            .clickable { activePlaylistView = entry.key }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.QueueMusic, null, tint = Purple, modifier = Modifier.size(25.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("${entry.value.size} tracks", color = Muted, fontSize = 12.sp)
                                        }
                                        IconButton(onClick = {
                                            playlists = playlists - entry.key
                                            saveState()
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Muted)
                                        }
                                    }
                                }
                            }
                        } else {
                            val title = when (currentPlaylistName) {
                                "__LIKED__" -> "Liked Songs"
                                "__RECENT__" -> "Recently Played"
                                else -> currentPlaylistName
                            }
                            val listSongs = when (currentPlaylistName) {
                                "__LIKED__" -> songs.filter { likedIds.contains(it.id) }
                                "__RECENT__" -> recentIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }
                                else -> (playlists[currentPlaylistName] ?: emptyList()).mapNotNull { id -> songs.firstOrNull { it.id == id } }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { activePlaylistView = null }) {
                                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                                }
                                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (listSongs.isEmpty()) {
                                EmptyState("No tracks in $title yet")
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    itemsIndexed(listSongs) { idx, song ->
                                        SongRow(
                                            song = song,
                                            active = currentSongId == song.id && playing,
                                            isLiked = likedIds.contains(song.id),
                                            onClick = { playQueue(listSongs, idx) },
                                            onMore = { songForMenu = song }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs, artists, albums…", color = Muted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, "Clear", tint = Muted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Purple,
                                unfocusedBorderColor = Panel,
                                focusedContainerColor = Panel,
                                unfocusedContainerColor = Panel,
                                cursorColor = Purple
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("TITLE" to "A-Z", "ARTIST" to "Artist", "DURATION" to "Duration", "NEWEST" to "Newest").forEach { (key, label) ->
                                FilterChip(
                                    selected = sortBy == key,
                                    onClick = { sortBy = key; saveState() },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("${sortedSongs.size} TRACKS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!permission) {
                            EmptyState("Allow audio access to scan your music")
                        } else if (sortedSongs.isEmpty()) {
                            EmptyState("No music found")
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                contentPadding = PaddingValues(bottom = 14.dp)
                            ) {
                                itemsIndexed(sortedSongs) { idx, song ->
                                    SongRow(
                                        song = song,
                                        active = currentSongId == song.id && playing,
                                        isLiked = likedIds.contains(song.id),
                                        onClick = { playQueue(sortedSongs, idx) },
                                        onMore = { songForMenu = song }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, active: Boolean, isLiked: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Purple.copy(alpha = .15f) else Panel, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (active) Purple else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isLiked) {
            Icon(Icons.Default.Favorite, null, tint = Purple, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(formatTime(song.duration), color = Muted, fontSize = 11.sp)
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreHoriz, "Options", tint = Muted)
        }
    }
}

@Composable
private fun AlbumArt() {
    Box(
        Modifier
            .size(48.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF38265E), Color(0xFF171A29))), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.GraphicEq, null, tint = Purple, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, onToggle: () -> Unit, onNext: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(Panel, RoundedCornerShape(15.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Purple)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, null, tint = Color.White)
        }
    }
}

@Composable
private fun NowPlayingScreen(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    isLiked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    sleepRemainingSec: Int,
    eqPreset: String,
    gesturesEnabled: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEq: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .background(Brush.linearGradient(listOf(Color(0xFF38265E), Color(0xFF141725))), RoundedCornerShape(28.dp))
                .pointerInput(gesturesEnabled) {
                    if (gesturesEnabled) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (totalDrag > 90f) onPrevious()
                                else if (totalDrag < -90f) onNext()
                                totalDrag = 0f
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, null, tint = Purple, modifier = Modifier.size(110.dp))
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} • ${song.album}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isLiked) Purple else Muted
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Slider(
            value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
            colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Muted, fontSize = 11.sp)
            Text(formatTime(duration), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Purple else Muted)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(68.dp)
                    .background(Purple, CircleShape)
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    null,
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) Purple else Muted
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onOpenSleep,
                label = {
                    Text(
                        if (sleepRemainingSec > 0) "SLEEP • ${sleepRemainingSec / 60}:${"%02d".format(sleepRemainingSec % 60)}"
                        else "SLEEP TIMER"
                    )
                },
                leadingIcon = { Icon(Icons.Default.Bedtime, null, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onOpenLyrics,
                label = { Text("LYRICS") },
                leadingIcon = { Icon(Icons.Default.Subject, null, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onOpenEq,
                label = { Text("EQ • $eqPreset") },
                leadingIcon = { Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun SettingsContent(
    eqPreset: String,
    crossfadeSec: Int,
    sleepRemainingSec: Int,
    gesturesEnabled: Boolean,
    onOpenEqualizer: () -> Unit,
    onOpenCrossfade: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleGestures: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SettingsRow("Equalizer & Bass Boost", "Active preset: $eqPreset", onOpenEqualizer)
        SettingsRow(
            "Crossfade Transition",
            if (crossfadeSec == 0) "Off (Instant switch)" else "$crossfadeSec seconds smooth fade",
            onOpenCrossfade
        )
        SettingsRow(
            "Sleep Timer",
            if (sleepRemainingSec > 0) "Active (${sleepRemainingSec / 60}m left)" else "Stop playback after a set time",
            onOpenSleepTimer
        )
        SettingsRow(
            "Swipe Gesture Controls",
            if (gesturesEnabled) "Enabled (Swipe artwork for next/previous)" else "Disabled",
            onToggleGestures
        )
        SettingsRow("Backup Library Data", "Export playlists, favorites & lyrics to JSON", onBackup)
        SettingsRow("Restore Library Backup", "Import previously saved VYRO JSON backup", onRestore)
        Spacer(Modifier.height(10.dp))
        Text("VYRO MUSIC  •  VERSION 1.1.2", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text("Developed by Mohamed Tarek", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.GraphicEq, null, tint = Purple)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun FeatureTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(25.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = Purple, modifier = Modifier.size(45.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = Muted)
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun readSidecarLyrics(filePath: String?): String? {
    if (filePath.isNullOrBlank()) return null
    return runCatching {
        val audioFile = File(filePath)
        val base = audioFile.nameWithoutExtension
        val parent = audioFile.parentFile ?: return null
        val lrc = File(parent, "$base.lrc")
        val txt = File(parent, "$base.txt")
        val target = when {
            lrc.exists() -> lrc
            txt.exists() -> txt
            else -> null
        } ?: return null
        target.readText().replace(Regex("\\[\\d+:\\d+(\\.\\d+)?]"), "").trim()
    }.getOrNull()
}

private fun loadIdSet(json: String?): Set<Long> = runCatching {
    val arr = JSONArray(json ?: "[]")
    (0 until arr.length()).map { arr.getLong(it) }.toSet()
}.getOrDefault(emptySet())

private fun loadIdList(json: String?): List<Long> = runCatching {
    val arr = JSONArray(json ?: "[]")
    (0 until arr.length()).map { arr.getLong(it) }
}.getOrDefault(emptyList())

private fun loadPlaylists(json: String?): Map<String, List<Long>> = runCatching {
    val obj = JSONObject(json ?: "{}")
    val map = mutableMapOf<String, List<Long>>()
    obj.keys().forEach { key ->
        val arr = obj.getJSONArray(key)
        map[key] = (0 until arr.length()).map { arr.getLong(it) }
    }
    map
}.getOrDefault(emptyMap())

private fun encodePlaylists(map: Map<String, List<Long>>): String {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k, JSONArray(v)) }
    return obj.toString()
}

private fun loadLyricsMap(json: String?): Map<Long, String> = runCatching {
    val obj = JSONObject(json ?: "{}")
    val map = mutableMapOf<Long, String>()
    obj.keys().forEach { key ->
        key.toLongOrNull()?.let { id -> map[id] = obj.getString(key) }
    }
    map
}.getOrDefault(emptyMap())

private fun encodeLyricsMap(map: Map<Long, String>): String {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj.toString()
}
