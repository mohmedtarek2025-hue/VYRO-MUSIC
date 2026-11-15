package com.mohamedtarek.vyromusic

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val uri: Uri,
    val duration: Long,
    val dateAdded: Long,
    val sizeBytes: Long,
    val filePath: String?
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

data class CoverPalette(
    val dominant: Color = Color(0xFF9B6CFF),
    val secondary: Color = Color(0xFF28194D),
    val isLightCover: Boolean = false
)

data class Strings(
    val isRtl: Boolean,
    val songs: String,
    val albums: String,
    val playlists: String,
    val eqStudio: String,
    val settings: String,
    val searchHint: String,
    val tracksCount: String,
    val nowPlaying: String,
    val likedSongs: String,
    val recentlyPlayed: String,
    val newPlaylist: String,
    val playNow: String,
    val playNext: String,
    val favToggle: String,
    val addToPlaylist: String,
    val setRingtone: String,
    val viewLyrics: String,
    val shareSong: String,
    val fileInfo: String,
    val deleteDevice: String,
    val themeTitle: String,
    val pickBgImage: String,
    val clearBgImage: String,
    val bgDimLevel: String,
    val langTitle: String,
    val langSystem: String,
    val autoScanToggle: String,
    val autoPlayNewToggle: String,
    val sleepTimer: String,
    val crossfade: String,
    val backupRestore: String,
    val vipEditionTitle: String
)

private fun getAppStrings(langSetting: String): Strings {
    val resolved = if (langSetting == "SYSTEM") {
        Locale.getDefault().language.uppercase()
    } else langSetting.uppercase()

    return when {
        resolved.startsWith("AR") -> Strings(
            isRtl = true,
            songs = "الأغاني",
            albums = "الألبومات",
            playlists = "القوائم",
            eqStudio = "استوديو الصوت",
            settings = "الإعدادات",
            searchHint = "ابحث عن أغنية، مطرب، أو ألبوم...",
            tracksCount = "مقطع صوتي",
            nowPlaying = "يعمل الآن",
            likedSongs = "الأغاني المفضلة",
            recentlyPlayed = "تم تشغيلها مؤخراً",
            newPlaylist = "+ قائمة جديدة",
            playNow = "تشغيل الآن",
            playNext = "إضافة لطابور الانتظار (تشغيل التالي)",
            favToggle = "إضافة / إزالة من المفضلة",
            addToPlaylist = "إضافة إلى قائمة تشغيل...",
            setRingtone = "قص وتعيين كنغمة رنين للهاتف",
            viewLyrics = "عرض / تعديل كلمات الأغنية",
            shareSong = "مشاركة الملف الصوتي",
            fileInfo = "معلومات الملف التفصيلية",
            deleteDevice = "حذف الأغنية من الجهاز",
            themeTitle = "محرك الثيمات الفاخرة (Theme Engine)",
            pickBgImage = "إضافة خلفية مخصصة من صور الهاتف",
            clearBgImage = "إلغاء الخلفية الشخصية واستعادة الثيم",
            bgDimLevel = "درجة تعتيم الخلفية ووضوح النصوص",
            langTitle = "لغة التطبيق (Language)",
            langSystem = "حسب لغة الجهاز (افتراضي)",
            autoScanToggle = "مراقبة واكتشاف الأغاني المحملة تلقائياً",
            autoPlayNewToggle = "تشغيل الأغنية تلقائياً فور تحميلها",
            sleepTimer = "مؤقت النوم الذكي",
            crossfade = "الدمج السلس بين الأغاني (Crossfade)",
            backupRestore = "النسخ الاحتياطي والاستعادة (JSON)",
            vipEditionTitle = "نسخة المؤسس والمطور • VIP PRO"
        )
        resolved.startsWith("FR") -> Strings(
            isRtl = false,
            songs = "Titres",
            albums = "Albums",
            playlists = "Playlists",
            eqStudio = "EQ Studio",
            settings = "Options",
            searchHint = "Rechercher titres, artistes, albums…",
            tracksCount = "TITRES",
            nowPlaying = "EN LECTURE",
            likedSongs = "Titres likés",
            recentlyPlayed = "Écoutés récemment",
            newPlaylist = "+ Nouvelle",
            playNow = "Lire maintenant",
            playNext = "Lire ensuite (File d'attente)",
            favToggle = "Ajouter / Retirer des favoris",
            addToPlaylist = "Ajouter à une playlist...",
            setRingtone = "Couper et définir comme sonnerie",
            viewLyrics = "Paroles (Voir / Modifier)",
            shareSong = "Partager le fichier audio",
            fileInfo = "Détails du fichier",
            deleteDevice = "Supprimer de l'appareil",
            themeTitle = "Moteur de Thèmes VIP",
            pickBgImage = "Choisir une image de fond de la galerie",
            clearBgImage = "Réinitialiser l'image de fond",
            bgDimLevel = "Opacité du fond",
            langTitle = "Langue (Language)",
            langSystem = "Langue de l'appareil (Défaut)",
            autoScanToggle = "Détection auto des nouveaux titres",
            autoPlayNewToggle = "Lecture auto après téléchargement",
            sleepTimer = "Minuteur de mise en veille",
            crossfade = "Fondu enchaîné (Crossfade)",
            backupRestore = "Sauvegarde & Restauration (JSON)",
            vipEditionTitle = "FOUNDER VIP EDITION"
        )
        resolved.startsWith("ES") -> Strings(
            isRtl = false,
            songs = "Canciones",
            albums = "Álbumes",
            playlists = "Listas",
            eqStudio = "EQ Studio",
            settings = "Ajustes",
            searchHint = "Buscar canciones, artistas, álbumes…",
            tracksCount = "PISTAS",
            nowPlaying = "REPRODUCIENDO",
            likedSongs = "Canciones favoritas",
            recentlyPlayed = "Reproducidas recientemente",
            newPlaylist = "+ Nueva lista",
            playNow = "Reproducir ahora",
            playNext = "Reproducir siguiente en cola",
            favToggle = "Añadir / Quitar de favoritos",
            addToPlaylist = "Añadir a lista de reproducción...",
            setRingtone = "Cortar y usar como tono de llamada",
            viewLyrics = "Letras (Ver / Editar)",
            shareSong = "Compartir archivo de audio",
            fileInfo = "Información del archivo",
            deleteDevice = "Eliminar del dispositivo",
            themeTitle = "Motor de Temas VIP",
            pickBgImage = "Elegir fondo desde la galería",
            clearBgImage = "Restaurar fondo original",
            bgDimLevel = "Oscurecimiento de fondo",
            langTitle = "Idioma (Language)",
            langSystem = "Idioma del dispositivo (Por defecto)",
            autoScanToggle = "Detectar nuevas canciones automáticamente",
            autoPlayNewToggle = "Reproducir automáticamente al descargar",
            sleepTimer = "Temporizador de apagado",
            crossfade = "Transición suave (Crossfade)",
            backupRestore = "Copia de seguridad y restauración",
            vipEditionTitle = "FOUNDER VIP EDITION"
        )
        else -> Strings(
            isRtl = false,
            songs = "Songs",
            albums = "Albums",
            playlists = "Playlists",
            eqStudio = "EQ Studio",
            settings = "Settings",
            searchHint = "Search songs, artists, albums…",
            tracksCount = "TRACKS",
            nowPlaying = "NOW PLAYING",
            likedSongs = "Liked Songs",
            recentlyPlayed = "Recently Played",
            newPlaylist = "+ New Playlist",
            playNow = "Play Now",
            playNext = "Play Next in Queue",
            favToggle = "Add / Remove from Favorites",
            addToPlaylist = "Add to Playlist...",
            setRingtone = "Trim & Set as Phone Ringtone",
            viewLyrics = "View / Edit Offline Lyrics",
            shareSong = "Share Audio File",
            fileInfo = "Detailed File Information",
            deleteDevice = "Delete from Device",
            themeTitle = "VIP Theme & Background Engine",
            pickBgImage = "Set Custom Background from Gallery",
            clearBgImage = "Reset Custom Background Image",
            bgDimLevel = "Background Dim & Contrast Level",
            langTitle = "App Language",
            langSystem = "Device Language (Default)",
            autoScanToggle = "Smart Auto-Detect Downloaded Music",
            autoPlayNewToggle = "Auto-Play Newly Downloaded Tracks",
            sleepTimer = "Smart Sleep Timer",
            crossfade = "Smooth Crossfade Transition",
            backupRestore = "Backup & Restore Library (JSON)",
            vipEditionTitle = "FOUNDER & DEVELOPER VIP EDITION"
        )
    }
}

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
                    controller?.let { VyroMasterApp(it, ::loadSongs) }
                }
            } catch (error: Exception) {
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF06070C)), contentAlignment = Alignment.Center) {
                            Text("Unable to connect to VYRO Audio Engine. Please restart.", color = Color.White)
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
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
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
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val path = if (dataCol >= 0) c.getString(dataCol) else null
                list += Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown Title",
                    artist = c.getString(artistCol) ?: "Unknown Artist",
                    album = c.getString(albumCol) ?: "Unknown Album",
                    albumId = c.getLong(albumIdCol),
                    uri = Uri.withAppendedPath(collection, id.toString()),
                    duration = c.getLong(durationCol),
                    dateAdded = c.getLong(dateCol),
                    sizeBytes = c.getLong(sizeCol),
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
fun VyroMasterApp(player: Player, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()

    var langSetting by remember { mutableStateOf(prefs.getString("lang_setting", "SYSTEM") ?: "SYSTEM") }
    val strings = remember(langSetting) { getAppStrings(langSetting) }

    var themeName by remember { mutableStateOf(prefs.getString("theme_name", "Midnight Echo") ?: "Midnight Echo") }
    var customBgUri by remember { mutableStateOf(prefs.getString("custom_bg_uri", null)) }
    var bgDimAlpha by remember { mutableFloatStateOf(prefs.getFloat("bg_dim_alpha", 0.78f)) }

    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var permission by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableLongStateOf(prefs.getLong("last_song_id", -1L)) }
    var position by remember { mutableLongStateOf(prefs.getLong("last_position_ms", 0L)) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }

    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(prefs.getString("sort_by", "TITLE") ?: "TITLE") }
    var tab by remember { mutableStateOf("Songs") }

    var likedIds by remember { mutableStateOf(loadIdSet(prefs.getString("liked_ids", "[]"))) }
    var recentIds by remember { mutableStateOf(loadIdList(prefs.getString("recent_ids", "[]"))) }
    var playlists by remember { mutableStateOf(loadPlaylists(prefs.getString("playlists", "{}"))) }
    var customLyrics by remember { mutableStateOf(loadLyricsMap(prefs.getString("lyrics_map", "{}"))) }

    var crossfadeSec by remember { mutableIntStateOf(prefs.getInt("crossfade_sec", 0)) }
    var gesturesEnabled by remember { mutableStateOf(prefs.getBoolean("gestures_enabled", true)) }
    var autoScanEnabled by remember { mutableStateOf(prefs.getBoolean("auto_scan_enabled", true)) }
    var autoPlayDownloaded by remember { mutableStateOf(prefs.getBoolean("auto_play_downloaded", false)) }

    var eqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Phonk Master") ?: "Phonk Master") }
    var bandGains by remember {
        mutableStateOf(
            loadFloatList(prefs.getString("band_gains", null)) ?: listOf(0.75f, 0.35f, -0.10f, 0.45f, 0.80f)
        )
    }
    var bassStrength by remember { mutableIntStateOf(prefs.getInt("bass_strength", 65)) }
    var virtualizerStrength by remember { mutableIntStateOf(prefs.getInt("virtualizer_strength", 50)) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("playback_speed", 1f)) }
    var playbackPitch by remember { mutableFloatStateOf(prefs.getFloat("playback_pitch", 1f)) }

    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }

    var activePlaylistView by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForLyrics by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForRingtone by remember { mutableStateOf<Song?>(null) }
    var songForDeleteConfirm by remember { mutableStateOf<Song?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }

    var coverPalette by remember { mutableStateOf(CoverPalette()) }

    fun saveState() {
        prefs.edit()
            .putString("lang_setting", langSetting)
            .putString("theme_name", themeName)
            .putString("custom_bg_uri", customBgUri)
            .putFloat("bg_dim_alpha", bgDimAlpha)
            .putString("sort_by", sortBy)
            .putString("liked_ids", JSONArray(likedIds.toList()).toString())
            .putString("recent_ids", JSONArray(recentIds).toString())
            .putString("playlists", encodePlaylists(playlists))
            .putString("lyrics_map", encodeLyricsMap(customLyrics))
            .putInt("crossfade_sec", crossfadeSec)
            .putBoolean("gestures_enabled", gesturesEnabled)
            .putBoolean("auto_scan_enabled", autoScanEnabled)
            .putBoolean("auto_play_downloaded", autoPlayDownloaded)
            .putString("eq_preset", eqPreset)
            .putString("band_gains", JSONArray(bandGains).toString())
            .putInt("bass_strength", bassStrength)
            .putInt("virtualizer_strength", virtualizerStrength)
            .putFloat("playback_speed", playbackSpeed)
            .putFloat("playback_pitch", playbackPitch)
            .apply()
    }

    val deleteResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            songs = loadSongs()
            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customBgUri = uri.toString()
            saveState()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", "2.0.0-PRO")
                    put("liked_ids", JSONArray(likedIds.toList()))
                    put("recent_ids", JSONArray(recentIds))
                    put("playlists", JSONObject(encodePlaylists(playlists)))
                    put("lyrics_map", JSONObject(encodeLyricsMap(customLyrics)))
                    put("crossfade_sec", crossfadeSec)
                    put("theme_name", themeName)
                    put("eq_preset", eqPreset)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                Toast.makeText(context, "VYRO PRO Backup Saved!", Toast.LENGTH_SHORT).show()
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
                if (root.has("theme_name")) themeName = root.getString("theme_name")
                if (root.has("eq_preset")) eqPreset = root.getString("eq_preset")
                saveState()
                Toast.makeText(context, "VYRO PRO Backup Restored!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buildMediaItem(s: Song): MediaItem {
        val extras = Bundle().apply { putLong("album_id", s.albumId) }
        return MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(s.albumArtUri)
                    .build()
            )
            .build()
    }

    fun playQueue(queue: List<Song>, startIndex: Int, startPosMs: Long = 0L, autoPlay: Boolean = true) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        val chosen = queue[startIndex]
        if (currentSongId == chosen.id && player.mediaItemCount > 0 && startPosMs == 0L) {
            if (autoPlay) {
                if (player.isPlaying) player.pause() else player.play()
            }
            return
        }
        currentSongId = chosen.id
        recentIds = (listOf(chosen.id) + recentIds.filter { it != chosen.id }).take(50)
        prefs.edit()
            .putLong("last_song_id", chosen.id)
            .putLong("last_position_ms", startPosMs)
            .putString("last_title", chosen.title)
            .putString("last_artist", chosen.artist)
            .putLong("last_album_id", chosen.albumId)
            .apply()
        saveState()

        val mediaItems = queue.map { buildMediaItem(it) }
        player.volume = 1f
        player.setMediaItems(mediaItems, startIndex, startPosMs)
        player.prepare()
        if (autoPlay) player.play() else player.pause()
        VyroWidgetProvider.updateAllWidgets(context)
    }

    val permissionName = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) {
            val loaded = loadSongs()
            songs = loaded
            val activeId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (activeId != null) {
                currentSongId = activeId
                position = player.currentPosition.coerceAtLeast(0L)
            } else if (currentSongId != -1L) {
                val idx = loaded.indexOfFirst { it.id == currentSongId }
                if (idx >= 0) {
                    playQueue(loaded, idx, prefs.getLong("last_position_ms", 0L), autoPlay = false)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        permission = ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED
        if (permission) {
            val loaded = loadSongs()
            songs = loaded
            val liveId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (liveId != null) {
                currentSongId = liveId
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                playing = player.isPlaying
            } else if (currentSongId != -1L) {
                val idx = loaded.indexOfFirst { it.id == currentSongId }
                if (idx >= 0) {
                    playQueue(loaded, idx, prefs.getLong("last_position_ms", 0L), autoPlay = false)
                }
            } else if (loaded.isNotEmpty()) {
                currentSongId = loaded.first().id
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            launcher.launch(permissionName)
        }
    }

    // مراقب تلقائي لمكتبة الموسيقى لاكتشاف أي أغنية يتم تحميلها فوراً
    DisposableEffect(permission, autoScanEnabled, autoPlayDownloaded) {
        if (!permission || !autoScanEnabled) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val oldIds = songs.map { it.id }.toSet()
                val updated = loadSongs()
                songs = updated
                val newlyAdded = updated.firstOrNull { it.id !in oldIds }
                if (newlyAdded != null && autoPlayDownloaded) {
                    val idx = updated.indexOf(newlyAdded)
                    if (idx >= 0) playQueue(updated, idx, 0L, autoPlay = true)
                }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val currentSong = songs.firstOrNull { it.id == currentSongId }

    // استخراج ألوان غلاف الأغنية الشغالة تلقائياً وتحديد ما إذا كانت فاتحة أو غامقة
    LaunchedEffect(currentSong?.albumId) {
        if (currentSong == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val extracted = extractCoverPalette(context, currentSong.albumArtUri)
            withContext(Dispatchers.Main) {
                coverPalette = extracted
            }
        }
    }

    LaunchedEffect(bandGains, bassStrength, virtualizerStrength, playbackSpeed, playbackPitch) {
        runCatching {
            player.playbackParameters = PlaybackParameters(playbackSpeed.coerceIn(0.5f, 2.0f), playbackPitch.coerceIn(0.5f, 2.0f))
        }
        runCatching {
            val eq = Equalizer(0, 0)
            eq.enabled = true
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLvl = range[0]
            val maxLvl = range[1]
            for (b in 0 until minOf(bands, bandGains.size)) {
                val target = (bandGains[b].coerceIn(-1f, 1f) * (maxLvl - minLvl) / 2f).toInt().toShort()
                eq.setBandLevel(b.toShort(), target)
            }
            val bass = BassBoost(0, 0)
            bass.enabled = bassStrength > 0
            if (bass.strengthSupported) bass.setStrength((bassStrength * 10).coerceIn(0, 1000).toShort())

            val virt = Virtualizer(0, 0)
            virt.enabled = virtualizerStrength > 0
            if (virt.strengthSupported) virt.setStrength((virtualizerStrength * 10).coerceIn(0, 1000).toShort())
        }
        saveState()
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
                    recentIds = (listOf(id) + recentIds.filter { it != id }).take(50)
                    saveState()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, crossfadeSec) {
        var tick = 0
        while (true) {
            if (player.isPlaying || player.mediaItemCount > 0) {
                position = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration
                duration = if (dur > 0) dur else (currentSong?.duration ?: 0L)
                if (player.isPlaying) {
                    tick++
                    if (tick % 6 == 0) {
                        prefs.edit()
                            .putLong("last_song_id", currentSongId)
                            .putLong("last_position_ms", position)
                            .apply()
                    }
                }
            }
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
            delay(350)
        }
    }

    val primaryAccent by animateColorAsState(
        targetValue = when (themeName) {
            "Dynamic Album Art" -> coverPalette.dominant
            "VIP Cyber Gold" -> Color(0xFFFFC857)
            "Crimson Phonk" -> Color(0xFFFF3864)
            "Emerald Matrix" -> Color(0xFF00F59B)
            else -> Color(0xFF9B6CFF)
        },
        label = "accent"
    )

    val bgNight = when (themeName) {
        "AMOLED Pure" -> Color(0xFF000000)
        "VIP Cyber Gold" -> Color(0xFF090806)
        "Dynamic Album Art" -> if (coverPalette.isLightCover) Color(0xFF12101B) else Color(0xFF07080F)
        else -> Color(0xFF06070D)
    }

    val surfacePanel = when (themeName) {
        "AMOLED Pure" -> Color(0xFF0E0F14)
        "VIP Cyber Gold" -> Color(0xFF191610)
        "Dynamic Album Art" -> coverPalette.secondary.copy(alpha = 0.55f)
        else -> Color(0xFF131524)
    }

    val mutedText = Color(0xFFA0A3B8)

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

    // النوافذ المنبثقة الاحترافية (قائمة الثلاث نقاط، قاطع النغمات، المعلومات، الكلمات)
    songForMenu?.let { target ->
        val isLiked = likedIds.contains(target.id)
        AlertDialog(
            onDismissRequest = { songForMenu = null },
            containerColor = Color(0xFF151728),
            title = {
                Column {
                    Text(target.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(target.artist, color = primaryAccent, fontSize = 12.sp, maxLines = 1)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    MenuActionRow(Icons.Default.PlayArrow, strings.playNow, primaryAccent) {
                        val idx = songs.indexOfFirst { it.id == target.id }
                        if (idx >= 0) playQueue(songs, idx, 0L, true)
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.QueuePlayNext, strings.playNext, Color.White) {
                        val nextIdx = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
                        player.addMediaItem(nextIdx, buildMediaItem(target))
                        Toast.makeText(context, strings.playNext, Toast.LENGTH_SHORT).show()
                        songForMenu = null
                    }
                    MenuActionRow(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        strings.favToggle,
                        Color.White
                    ) {
                        likedIds = if (isLiked) likedIds - target.id else likedIds + target.id
                        saveState()
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.PlaylistAdd, strings.addToPlaylist, Color.White) {
                        songForAddToPlaylist = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.RingVolume, strings.setRingtone, Color(0xFFFFC857)) {
                        songForRingtone = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Lyrics, strings.viewLyrics, Color.White) {
                        songForLyrics = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Share, strings.shareSong, Color.White) {
                        runCatching {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, target.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, target.title))
                        }
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Info, strings.fileInfo, Color.White) {
                        songForInfo = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.DeleteForever, strings.deleteDevice, Color(0xFFFF6B6B)) {
                        songForDeleteConfirm = target
                        songForMenu = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songForMenu = null }) { Text("OK", color = primaryAccent) }
            }
        )
    }

    // نافذة قص وتعيين نغمة الرنين (Ringtone Cutter & Setter)
    songForRingtone?.let { target ->
        val totalSec = (target.duration / 1000L).coerceAtLeast(10L).toFloat()
        var startSec by remember { mutableFloatStateOf(0f) }
        var endSec by remember { mutableFloatStateOf(minOf(30f, totalSec)) }
        var cutting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!cutting) songForRingtone = null },
            containerColor = Color(0xFF151728),
            title = { Text(strings.setRingtone, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(target.title, color = primaryAccent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            Text("Start: ${formatTime((startSec * 1000).toLong())}   •   End: ${formatTime((endSec * 1000).toLong())}", color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = startSec,
                                onValueChange = { startSec = it.coerceAtMost(endSec - 3f).coerceAtLeast(0f) },
                                valueRange = 0f..totalSec
                            )
                            Slider(
                                value = endSec,
                                onValueChange = { endSec = it.coerceAtLeast(startSec + 3f).coerceAtMost(totalSec) },
                                valueRange = 0f..totalSec
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (!Settings.System.canWrite(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                                Toast.makeText(context, "يرجى تفعيل إذن تعديل إعدادات النظام لتعيين النغمة", Toast.LENGTH_LONG).show()
                            } else {
                                cutting = true
                                appScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        cutAndSetRingtone(context, target, (startSec * 1000).toLong(), (endSec * 1000).toLong())
                                    }
                                    cutting = false
                                    Toast.makeText(
                                        context,
                                        if (ok) "تم تعيين المقطع كنغمة رنين للهاتف بنجاح!" else "تم تعيين الأغنية كنغمة رنين!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    songForRingtone = null
                                }
                            }
                        },
                        enabled = !cutting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Text(if (cutting) "..." else "قص وتعيين المقطع المحدد كنغمة", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            if (!Settings.System.canWrite(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            } else {
                                runCatching {
                                    RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, target.uri)
                                    Toast.makeText(context, "تم تعيين الأغنية كاملة كنغمة رنين!", Toast.LENGTH_SHORT).show()
                                }
                                songForRingtone = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تعيين الأغنية بالكامل بدون قص", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songForRingtone = null }) { Text("إغلاق", color = mutedText) }
            }
        )
    }

    // نافذة معلومات الملف التفصيلية
    songForInfo?.let { target ->
        val mb = "%.2f MB".format(target.sizeBytes / (1024f * 1024f))
        val ext = target.filePath?.substringAfterLast('.', "AUDIO")?.uppercase() ?: "AUDIO"
        AlertDialog(
            onDismissRequest = { songForInfo = null },
            containerColor = Color(0xFF151728),
            title = { Text(strings.fileInfo, color = primaryAccent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoItem("Title", target.title)
                    InfoItem("Artist", target.artist)
                    InfoItem("Album", target.album)
                    InfoItem("Duration", formatTime(target.duration))
                    InfoItem("Size", mb)
                    InfoItem("Format", "$ext • Hi-Res 24-Bit Engine")
                    InfoItem("Path", target.filePath ?: target.uri.toString())
                }
            },
            confirmButton = {
                TextButton(onClick = { songForInfo = null }) { Text("OK", color = primaryAccent) }
            }
        )
    }

    // نافذة تأكيد حذف الأغنية من الجهاز
    songForDeleteConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { songForDeleteConfirm = null },
            containerColor = Color(0xFF151728),
            title = { Text(strings.deleteDevice, color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) },
            text = { Text(target.title, color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(target.uri))
                            deleteResultLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            context.contentResolver.delete(target.uri, null, null)
                            songs = loadSongs()
                        }
                    }
                    songForDeleteConfirm = null
                }) { Text("Delete", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { songForDeleteConfirm = null }) { Text("Cancel", color = Color.White) }
            }
        )
    }

    songForAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForAddToPlaylist = null },
            containerColor = Color(0xFF151728),
            title = { Text(strings.addToPlaylist, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        ) { Text(pName, color = Color.White) }
                    }
                    OutlinedButton(
                        onClick = {
                            songForAddToPlaylist = null
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.newPlaylist, color = primaryAccent) }
                }
            },
            confirmButton = { TextButton(onClick = { songForAddToPlaylist = null }) { Text("Close") } }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = Color(0xFF151728),
            title = { Text(strings.newPlaylist, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Gym Phonk / VIP Mix") },
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
                }) { Text("Create", color = primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") } }
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
            containerColor = Color(0xFF151728),
            title = { Text("${strings.viewLyrics} • ${targetSong.title}", color = Color.White, maxLines = 1) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editingMode) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("Paste song lyrics or synced LRC lines here...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                        )
                    } else {
                        Text(
                            text = draftText.ifBlank { "No offline lyrics saved for this track yet." },
                            color = Color.White,
                            lineHeight = 24.sp,
                            fontSize = 15.sp
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
                    }) { Text("Save", color = primaryAccent) }
                } else {
                    TextButton(onClick = { editingMode = true }) { Text("Edit", color = primaryAccent) }
                }
            },
            dismissButton = { TextButton(onClick = { songForLyrics = null }) { Text("Close") } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = Color(0xFF151728),
            title = { Text(strings.sleepTimer, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { startSleepTimer(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins min", color = Color.White) }
                    }
                    if (sleepTimerRemainingSec > 0) {
                        TextButton(
                            onClick = { startSleepTimer(0); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel Timer", color = Color(0xFFFF6B6B)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
        )
    }

    if (showCrossfadeDialog) {
        AlertDialog(
            onDismissRequest = { showCrossfadeDialog = false },
            containerColor = Color(0xFF151728),
            title = { Text(strings.crossfade, color = Color.White) },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (crossfadeSec == 0) "Off (Gapless Instant)" else "$crossfadeSec sec Smooth Mix",
                            color = primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = crossfadeSec.toFloat(),
                            onValueChange = { crossfadeSec = it.toInt(); saveState() },
                            valueRange = 0f..12f,
                            steps = 11
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCrossfadeDialog = false }) { Text("Done", color = primaryAccent) } }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (strings.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = bgNight,
                surface = surfacePanel,
                primary = primaryAccent,
                secondary = primaryAccent,
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            Box(Modifier.fillMaxSize().background(bgNight)) {
                // إذا اختار المستخدم خلفية شخصية من الموبايل تظهر بكامل الشاشة مع طبقة تعتيم فاخرة
                if (!customBgUri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(customBgUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(bgNight.copy(alpha = bgDimAlpha.coerceIn(0.35f, 0.95f)))
                    )
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        Column(Modifier.background(bgNight.copy(alpha = 0.92f))) {
                            if (currentSong != null && tab != "Now Playing") {
                                DynamicNowBarCard(
                                    song = currentSong,
                                    playing = playing,
                                    position = position,
                                    duration = duration,
                                    palette = coverPalette,
                                    accent = primaryAccent,
                                    onToggle = {
                                        if (player.mediaItemCount == 0) {
                                            val idx = songs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                            playQueue(songs, idx, position, true)
                                        } else if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                    },
                                    onNext = { player.seekToNext() },
                                    onPrevious = { player.seekToPrevious() },
                                    onOpen = { tab = "Now Playing" }
                                )
                            }
                            NavigationBar(containerColor = surfacePanel.copy(alpha = 0.96f)) {
                                listOf(
                                    "Songs" to strings.songs,
                                    "Now Playing" to strings.nowPlaying,
                                    "EQ Studio" to strings.eqStudio,
                                    "Playlists" to strings.playlists,
                                    "Settings" to strings.settings
                                ).forEach { (key, label) ->
                                    NavigationBarItem(
                                        selected = tab == key,
                                        onClick = {
                                            tab = key
                                            activePlaylistView = null
                                        },
                                        icon = {
                                            Icon(
                                                when (key) {
                                                    "Songs" -> Icons.Default.LibraryMusic
                                                    "Now Playing" -> Icons.Default.Album
                                                    "EQ Studio" -> Icons.Default.Equalizer
                                                    "Playlists" -> Icons.Default.QueueMusic
                                                    else -> Icons.Default.Tune
                                                },
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = primaryAccent,
                                            selectedTextColor = primaryAccent,
                                            indicatorColor = primaryAccent.copy(alpha = 0.18f),
                                            unselectedTextColor = mutedText,
                                            unselectedIconColor = mutedText
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
                            .padding(horizontal = 16.dp)
                    ) {
                        // الهيدر الملكي الرسمي لتطبيق VYRO MUSIC PRO
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "VYRO",
                                        color = Color.White,
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 3.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "MUSIC",
                                        color = primaryAccent,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .background(
                                                Brush.horizontalGradient(listOf(Color(0xFFFFC857), primaryAccent)),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("PRO VIP", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                Text(
                                    "CRAFTED & DEVELOPED BY MOHAMED TAREK",
                                    color = mutedText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            IconButton(
                                onClick = { tab = "EQ Studio" },
                                modifier = Modifier.semantics { contentDescription = strings.eqStudio }
                            ) {
                                Icon(Icons.Default.GraphicEq, strings.eqStudio, tint = primaryAccent)
                            }
                            IconButton(
                                onClick = {
                                    songs = loadSongs()
                                    Toast.makeText(context, "Refreshed (${songs.size})", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.semantics { contentDescription = "تحديث المكتبة" }
                            ) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                            }
                        }

                        when (tab) {
                            "Now Playing" -> {
                                if (currentSong != null) {
                                    MidnightEchoNowPlaying(
                                        song = currentSong,
                                        playing = playing,
                                        position = position,
                                        duration = duration.coerceAtLeast(currentSong.duration),
                                        isLiked = likedIds.contains(currentSong.id),
                                        shuffle = shuffle,
                                        repeatMode = repeatMode,
                                        sleepRemainingSec = sleepTimerRemainingSec,
                                        eqPreset = eqPreset,
                                        accent = primaryAccent,
                                        palette = coverPalette,
                                        gesturesEnabled = gesturesEnabled,
                                        onTogglePlay = {
                                            if (player.mediaItemCount == 0) {
                                                val idx = songs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                                playQueue(songs, idx, position, true)
                                            } else if (player.isPlaying) {
                                                player.pause()
                                            } else {
                                                player.play()
                                            }
                                        },
                                        onSeek = {
                                            position = it
                                            player.seekTo(it)
                                            prefs.edit().putLong("last_position_ms", it).apply()
                                        },
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
                                        onOpenEq = { tab = "EQ Studio" },
                                        onOpenMenu = { songForMenu = currentSong }
                                    )
                                } else {
                                    EmptyState("اختر أغنية من مكتبتك لبدء التجربة السينمائية", primaryAccent)
                                }
                            }

                            "EQ Studio" -> {
                                BehanceEqStudioScreen(
                                    eqPreset = eqPreset,
                                    bandGains = bandGains,
                                    bassStrength = bassStrength,
                                    virtualizerStrength = virtualizerStrength,
                                    playbackSpeed = playbackSpeed,
                                    playbackPitch = playbackPitch,
                                    accent = primaryAccent,
                                    panelColor = surfacePanel,
                                    onSelectPreset = { preset ->
                                        eqPreset = preset
                                        bandGains = when (preset) {
                                            "Bass Extreme" -> listOf(0.95f, 0.70f, 0.05f, 0.20f, 0.45f)
                                            "Phonk Master" -> listOf(0.90f, 0.45f, -0.15f, 0.55f, 0.85f)
                                            "VIP Studio" -> listOf(0.50f, 0.30f, 0.15f, 0.40f, 0.65f)
                                            "Rock" -> listOf(0.65f, 0.35f, -0.10f, 0.45f, 0.75f)
                                            "Vocal Crystal" -> listOf(-0.20f, 0.25f, 0.85f, 0.60f, 0.20f)
                                            else -> listOf(0f, 0f, 0f, 0f, 0f)
                                        }
                                    },
                                    onBandChange = { idx, value ->
                                        eqPreset = "Custom"
                                        bandGains = bandGains.toMutableList().also { it[idx] = value }
                                    },
                                    onBassChange = { bassStrength = it },
                                    onVirtChange = { virtualizerStrength = it },
                                    onSpeedChange = { playbackSpeed = it },
                                    onPitchChange = { playbackPitch = it },
                                    onOpenSystemEq = {
                                        runCatching {
                                            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                            }
                                            context.startActivity(intent)
                                        }.onFailure {
                                            Toast.makeText(context, "VYRO Hardware DSP Active", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            "Playlists" -> {
                                val currentPlaylistName = activePlaylistView
                                if (currentPlaylistName == null) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(strings.playlists, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                        Row {
                                            TextButton(onClick = { activePlaylistView = "__ALBUMS__" }) {
                                                Text(strings.albums, color = Color.White)
                                            }
                                            TextButton(onClick = { showCreatePlaylistDialog = true }) {
                                                Text(strings.newPlaylist, color = primaryAccent, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        item {
                                            FeatureTile(
                                                title = strings.likedSongs,
                                                subtitle = "${likedIds.size} ${strings.tracksCount}",
                                                icon = Icons.Default.Favorite,
                                                accent = primaryAccent,
                                                panel = surfacePanel,
                                                onClick = { activePlaylistView = "__LIKED__" }
                                            )
                                        }
                                        item {
                                            FeatureTile(
                                                title = strings.recentlyPlayed,
                                                subtitle = "${recentIds.size} ${strings.tracksCount}",
                                                icon = Icons.Default.History,
                                                accent = primaryAccent,
                                                panel = surfacePanel,
                                                onClick = { activePlaylistView = "__RECENT__" }
                                            )
                                        }
                                        item {
                                            FeatureTile(
                                                title = strings.albums,
                                                subtitle = "${songs.map { it.album }.distinct().size} ${strings.albums}",
                                                icon = Icons.Default.Album,
                                                accent = primaryAccent,
                                                panel = surfacePanel,
                                                onClick = { activePlaylistView = "__ALBUMS__" }
                                            )
                                        }
                                        itemsIndexed(playlists.entries.toList()) { _, entry ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(surfacePanel, RoundedCornerShape(16.dp))
                                                    .clickable { activePlaylistView = entry.key }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.QueueMusic, null, tint = primaryAccent)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold)
                                                    Text("${entry.value.size} ${strings.tracksCount}", color = mutedText, fontSize = 12.sp)
                                                }
                                                IconButton(onClick = {
                                                    playlists = playlists - entry.key
                                                    saveState()
                                                }) {
                                                    Icon(Icons.Default.Delete, "Delete", tint = mutedText)
                                                }
                                            }
                                        }
                                    }
                                } else if (currentPlaylistName == "__ALBUMS__") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { activePlaylistView = null }) {
                                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                                        }
                                        Text(strings.albums, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                    val albums = songs.groupBy { it.album }.entries.toList()
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(albums) { _, entry ->
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(surfacePanel, RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        query = entry.key
                                                        tab = "Songs"
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AlbumArtThumb(entry.value.firstOrNull(), primaryAccent)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    Text("${entry.value.size} ${strings.tracksCount}", color = mutedText, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val title = when (currentPlaylistName) {
                                        "__LIKED__" -> strings.likedSongs
                                        "__RECENT__" -> strings.recentlyPlayed
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
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        itemsIndexed(listSongs) { idx, song ->
                                            SongRow(
                                                song = song,
                                                active = currentSongId == song.id,
                                                playing = playing,
                                                isLiked = likedIds.contains(song.id),
                                                accent = primaryAccent,
                                                panel = surfacePanel,
                                                onClick = { playQueue(listSongs, idx, 0L, true) },
                                                onMore = { songForMenu = song }
                                            )
                                        }
                                    }
                                }
                            }

                            "Settings" -> {
                                VipSettingsScreen(
                                    strings = strings,
                                    themeName = themeName,
                                    langSetting = langSetting,
                                    customBgUri = customBgUri,
                                    bgDimAlpha = bgDimAlpha,
                                    autoScanEnabled = autoScanEnabled,
                                    autoPlayDownloaded = autoPlayDownloaded,
                                    crossfadeSec = crossfadeSec,
                                    sleepRemainingSec = sleepTimerRemainingSec,
                                    tracksPlayedStat = prefs.getInt("stat_tracks_played", 0),
                                    accent = primaryAccent,
                                    panel = surfacePanel,
                                    onSelectTheme = { themeName = it; saveState() },
                                    onSelectLang = { langSetting = it; saveState() },
                                    onPickBg = { bgImagePicker.launch(arrayOf("image/*")) },
                                    onClearBg = { customBgUri = null; saveState() },
                                    onChangeDim = { bgDimAlpha = it; saveState() },
                                    onToggleAutoScan = { autoScanEnabled = !autoScanEnabled; saveState() },
                                    onToggleAutoPlayNew = { autoPlayDownloaded = !autoPlayDownloaded; saveState() },
                                    onOpenCrossfade = { showCrossfadeDialog = true },
                                    onOpenSleep = { showSleepDialog = true },
                                    onBackup = { backupLauncher.launch("vyro_music_pro_backup.json") },
                                    onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                                )
                            }

                            else -> {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(strings.searchHint, color = mutedText, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = primaryAccent) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { query = "" }) {
                                                Icon(Icons.Default.Close, "Clear", tint = mutedText)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryAccent,
                                        unfocusedBorderColor = surfacePanel,
                                        focusedContainerColor = surfacePanel.copy(alpha = 0.85f),
                                        unfocusedContainerColor = surfacePanel.copy(alpha = 0.85f),
                                        cursorColor = primaryAccent
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        "TITLE" to "A-Z",
                                        "ARTIST" to "Artist",
                                        "DURATION" to "Duration",
                                        "NEWEST" to "Newest"
                                    ).forEach { (key, label) ->
                                        FilterChip(
                                            selected = sortBy == key,
                                            onClick = { sortBy = key; saveState() },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${sortedSongs.size} ${strings.tracksCount}",
                                        color = primaryAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                if (!permission) {
                                    EmptyState("يرجى منح إذن الوصول للملفات الصوتية لعرض مكتبتك", primaryAccent)
                                } else if (sortedSongs.isEmpty()) {
                                    EmptyState("لا توجد ملفات صوتية مطابقة", primaryAccent)
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                        contentPadding = PaddingValues(bottom = 16.dp)
                                    ) {
                                        itemsIndexed(sortedSongs) { idx, song ->
                                            SongRow(
                                                song = song,
                                                active = currentSongId == song.id,
                                                playing = playing,
                                                isLiked = likedIds.contains(song.id),
                                                accent = primaryAccent,
                                                panel = surfacePanel,
                                                onClick = { playQueue(sortedSongs, idx, 0L, true) },
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
    }
}

// شريط الـ Now Bar الديناميكي المتفاعل مع ألوان غلاف الأغنية (فاتح أو غامق)
@Composable
private fun DynamicNowBarCard(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    palette: CoverPalette,
    accent: Color,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit
) {
    val barBgStart = palette.dominant.copy(alpha = if (palette.isLightCover) 0.42f else 0.32f)
    val barBgEnd = Color(0xFF0E101C)
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(barBgStart, barBgEnd)))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArtThumb(song, accent, sizeDp = 48)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (playing) Color(0xFF00F59B) else accent, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${song.artist} • ${formatTime(position)} / ${formatTime(duration.coerceAtLeast(song.duration))}",
                    color = Color(0xFFD0C8E8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White)
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .size(38.dp)
                            .background(accent, CircleShape)
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause",
                            tint = Color.Black
                        )
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

// شاشة التشغيل الفاخرة بتصميم Behance "Midnight Echo" مع حلقة النيون والموجات الصوتية الحية
@Composable
private fun MidnightEchoNowPlaying(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    isLiked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    sleepRemainingSec: Int,
    eqPreset: String,
    accent: Color,
    palette: CoverPalette,
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
    onOpenEq: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "echo")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // شارة الجودة الفائقة وتفاعل الغلاف
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .background(palette.dominant.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    if (palette.isLightCover) "MIDNIGHT ECHO • LIGHT AURA" else "MIDNIGHT ECHO • DARK AURA",
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Default.MoreVert, "خيارات الأغنية", tint = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))

        // حلقة النيون المضيئة حول غلاف الأغنية (Behance Midnight Echo Circle)
        Box(
            Modifier
                .size(255.dp)
                .pointerInput(gesturesEnabled) {
                    if (gesturesEnabled) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                if (totalDrag > 85f) onPrevious()
                                else if (totalDrag < -85f) onNext()
                                totalDrag = 0f
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            palette.dominant.copy(alpha = if (playing) 0.48f else 0.22f),
                            accent.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    ),
                    radius = radius
                )
                drawCircle(
                    color = accent.copy(alpha = 0.85f),
                    radius = radius * 0.88f,
                    style = Stroke(width = 5f)
                )
            }
            Box(
                Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF17192B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(72.dp))
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // موجات صوتية متحركة حية (Live Waveform Visualizer)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 12.dp)
        ) {
            val bars = 38
            val space = size.width / bars
            for (i in 0 until bars) {
                val animFactor = if (playing) abs(sin(pulsePhase + i * 0.42f)) else 0.22f
                val barH = (size.height * (0.20f + 0.78f * animFactor)).coerceAtLeast(6f)
                drawRoundRect(
                    color = if (i % 2 == 0) accent else palette.dominant,
                    topLeft = Offset(i * space + 3f, (size.height - barH) / 2f),
                    size = Size((space - 6f).coerceAtLeast(3f), barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${song.artist} • ${song.album}",
                    color = Color(0xFFB5B8CE),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "المفضلة",
                    tint = if (isLiked) accent else Color.White
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // شريط التقدم بوضع LTR دائماً لضمان عدم انعكاس الوقت
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color(0xFFB5B8CE), fontSize = 12.sp)
                    Text("24-BIT / HI-RES DSP", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(duration), color = Color(0xFFB5B8CE), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(Icons.Default.Shuffle, "تبديل عشوائي", tint = if (shuffle) accent else Color.White.copy(alpha = 0.6f))
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, "السابق", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .background(Brush.linearGradient(listOf(accent, palette.dominant)), CircleShape)
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "إيقاف مؤقت" else "تشغيل",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "التالي", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "التكرار",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

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
                leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = accent, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onOpenLyrics,
                label = { Text("LYRICS STUDIO") },
                leadingIcon = { Icon(Icons.Default.Lyrics, null, tint = accent, modifier = Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onOpenEq,
                label = { Text("EQ • $eqPreset") },
                leadingIcon = { Icon(Icons.Default.Equalizer, null, tint = accent, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

// شاشة استوديو الصوت الاحترافي (Behance EQ Studio)
@Composable
private fun BehanceEqStudioScreen(
    eqPreset: String,
    bandGains: List<Float>,
    bassStrength: Int,
    virtualizerStrength: Int,
    playbackSpeed: Float,
    playbackPitch: Float,
    accent: Color,
    panelColor: Color,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onOpenSystemEq: () -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("EQ STUDIO • MASTERING DSP", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Phonk Master", "Bass Extreme", "VIP Studio", "Rock", "Vocal Crystal", "Flat").forEach { preset ->
                FilterChip(
                    selected = eqPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // 5-Band Equalizer Controls
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(panelColor, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bandGains.forEachIndexed { idx, gain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabels.getOrElse(idx) { "Band" }, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(52.dp))
                        Slider(
                            value = gain,
                            onValueChange = { onBandChange(idx, it) },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )
                        Text("${(gain * 15).toInt()} dB", color = accent, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                    }
                }
            }

            // Bass Boost & 3D Spatial Virtualizer
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(panelColor, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("BASS BOOST EXTREME (${bassStrength}%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Slider(
                    value = bassStrength.toFloat(),
                    onValueChange = { onBassChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Text("3D SPATIAL VIRTUALIZER (${virtualizerStrength}%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Slider(
                    value = virtualizerStrength.toFloat(),
                    onValueChange = { onVirtChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }

            // Speed, Pitch & Slowed + Reverb Mode
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(panelColor, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SPEED (${"%.2f".format(playbackSpeed)}x) & PITCH (${"%.2f".format(playbackPitch)}x)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "Reset 1.0x",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSpeedChange(1f); onPitchChange(1f) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSpeedChange(0.85f); onPitchChange(0.88f) }) {
                        Text("Slowed + Phonk", color = accent, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { onSpeedChange(1.18f); onPitchChange(1.12f) }) {
                        Text("Nightcore / Speed", color = Color.White, fontSize = 11.sp)
                    }
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }
        }

        OutlinedButton(onClick = onOpenSystemEq, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Speaker, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("فتح معادل صوت النظام الأصلي (Dolby Atmos / System EQ)", color = Color.White)
        }
    }
}

// شاشة الإعدادات والثيمات والخلفية الشخصية واللغات
@Composable
private fun VipSettingsScreen(
    strings: Strings,
    themeName: String,
    langSetting: String,
    customBgUri: String?,
    bgDimAlpha: Float,
    autoScanEnabled: Boolean,
    autoPlayDownloaded: Boolean,
    crossfadeSec: Int,
    sleepRemainingSec: Int,
    tracksPlayedStat: Int,
    accent: Color,
    panel: Color,
    onSelectTheme: (String) -> Unit,
    onSelectLang: (String) -> Unit,
    onPickBg: () -> Unit,
    onClearBg: () -> Unit,
    onChangeDim: (Float) -> Unit,
    onToggleAutoScan: () -> Unit,
    onToggleAutoPlayNew: () -> Unit,
    onOpenCrossfade: () -> Unit,
    onOpenSleep: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // بطاقة نسخة المؤسس الفاخرة (VIP Founder Card)
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF2B1954), Color(0xFF121324))),
                    RoundedCornerShape(22.dp)
                )
                .border(1.dp, Color(0xFFFFC857), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VYRO MUSIC ULTRA PRO • LIFETIME VIP", color = Color(0xFFFFC857), fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("CRAFTED & DEVELOPED BY MOHAMED TAREK", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("${strings.vipEditionTitle} • Tracks Played: $tracksPlayedStat", color = Color(0xFFD0C8E8), fontSize = 12.sp)
            }
        }

        // اختيار لغة التطبيق (مع خيار حسب لغة الجهاز افتراضياً)
        Column(
            Modifier
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.langTitle, color = Color.White, fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "SYSTEM" to strings.langSystem,
                    "AR" to "العربية",
                    "EN" to "English",
                    "FR" to "Français",
                    "ES" to "Español"
                ).forEach { (code, name) ->
                    FilterChip(
                        selected = langSetting == code,
                        onClick = { onSelectLang(code) },
                        label = { Text(name) }
                    )
                }
            }
        }

        // محرك الثيمات وإضافة خلفية من صور الموبايل
        Column(
            Modifier
                .fillMaxWidth()
                .background(panel, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.themeTitle, color = Color.White, fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Midnight Echo",
                    "Dynamic Album Art",
                    "VIP Cyber Gold",
                    "Crimson Phonk",
                    "Emerald Matrix",
                    "AMOLED Pure"
                ).forEach { th ->
                    FilterChip(
                        selected = themeName == th,
                        onClick = { onSelectTheme(th) },
                        label = { Text(th) }
                    )
                }
            }
            Button(
                onClick = onPickBg,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(Icons.Default.Wallpaper, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(strings.pickBgImage, color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (!customBgUri.isNullOrBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        Text(strings.bgDimLevel, color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = bgDimAlpha,
                            onValueChange = onChangeDim,
                            valueRange = 0.35f..0.95f,
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )
                    }
                }
                OutlinedButton(onClick = onClearBg, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.clearBgImage, color = Color(0xFFFF6B6B))
                }
            }
        }

        // إعدادات المراقب الذكي والتشغيل
        SettingsActionTile(
            title = strings.autoScanToggle,
            subtitle = if (autoScanEnabled) "ON • Active" else "OFF",
            icon = Icons.Default.Radar,
            accent = accent,
            panel = panel,
            onClick = onToggleAutoScan
        )
        SettingsActionTile(
            title = strings.autoPlayNewToggle,
            subtitle = if (autoPlayDownloaded) "ON" else "OFF (Recommended)",
            icon = Icons.Default.DownloadDone,
            accent = accent,
            panel = panel,
            onClick = onToggleAutoPlayNew
        )
        SettingsActionTile(
            title = strings.crossfade,
            subtitle = if (crossfadeSec == 0) "Off" else "$crossfadeSec sec",
            icon = Icons.Default.SwapHoriz,
            accent = accent,
            panel = panel,
            onClick = onOpenCrossfade
        )
        SettingsActionTile(
            title = strings.sleepTimer,
            subtitle = if (sleepRemainingSec > 0) "${sleepRemainingSec / 60}m active" else "Tap to set",
            icon = Icons.Default.Bedtime,
            accent = accent,
            panel = panel,
            onClick = onOpenSleep
        )
        SettingsActionTile(
            title = "${strings.backupRestore} (Export)",
            subtitle = "Save playlists, favorites & settings to JSON",
            icon = Icons.Default.CloudUpload,
            accent = accent,
            panel = panel,
            onClick = onBackup
        )
        SettingsActionTile(
            title = "${strings.backupRestore} (Import)",
            subtitle = "Restore from VYRO JSON file",
            icon = Icons.Default.CloudDownload,
            accent = accent,
            panel = panel,
            onClick = onRestore
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    active: Boolean,
    playing: Boolean,
    isLiked: Boolean,
    accent: Color,
    panel: Color,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) accent.copy(alpha = 0.18f) else panel.copy(alpha = 0.88f), RoundedCornerShape(16.dp))
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) accent else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(song, accent, sizeDp = 50)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (active) accent else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist} • ${song.album}",
                color = Color(0xFFA0A3B8),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isLiked) {
            Icon(Icons.Default.Favorite, "مفضلة", tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(formatTime(song.duration), color = Color(0xFFA0A3B8), fontSize = 11.sp)
        IconButton(onClick = onMore, modifier = Modifier.semantics { contentDescription = "خيارات ${song.title}" }) {
            Icon(Icons.Default.MoreVert, "More", tint = Color.White)
        }
    }
}

@Composable
private fun AlbumArtThumb(song: Song?, accent: Color, sizeDp: Int = 48) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF33205A), Color(0xFF151728)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size((sizeDp / 2).dp))
        if (song != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MenuActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFFA0A3B8), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    panel: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color(0xFFA0A3B8), fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFA0A3B8))
    }
}

@Composable
private fun FeatureTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    panel: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(panel, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFA0A3B8), fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFA0A3B8))
    }
}

@Composable
private fun EmptyState(message: String, accent: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

// دالة استخراج ألوان غلاف الأغنية وتحديد السطوع (فاتح أو غامق)
private fun extractCoverPalette(context: Context, artUri: Uri): CoverPalette {
    return runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = context.contentResolver.openInputStream(artUri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return CoverPalette()

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val stepX = (bmp.width / 12).coerceAtLeast(1)
        val stepY = (bmp.height / 12).coerceAtLeast(1)

        for (x in 0 until bmp.width step stepX) {
            for (y in 0 until bmp.height step stepY) {
                val px = bmp.getPixel(x, y)
                rSum += android.graphics.Color.red(px)
                gSum += android.graphics.Color.green(px)
                bSum += android.graphics.Color.blue(px)
                count++
            }
        }
        if (count == 0L) return CoverPalette()
        val r = (rSum / count).toInt().coerceIn(30, 245)
        val g = (gSum / count).toInt().coerceIn(30, 245)
        val b = (bSum / count).toInt().coerceIn(30, 245)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

        val boostedR = (r * 1.25f).toInt().coerceIn(70, 255)
        val boostedG = (g * 1.15f).toInt().coerceIn(60, 245)
        val boostedB = (b * 1.30f).toInt().coerceIn(90, 255)

        CoverPalette(
            dominant = Color(boostedR, boostedG, boostedB),
            secondary = Color(r / 3, g / 3, b / 3),
            isLightCover = luminance > 0.55
        )
    }.getOrDefault(CoverPalette())
}

// دالة قص وتعيين نغمة الرنين باستخدام MediaExtractor و MediaMuxer
private fun cutAndSetRingtone(context: Context, song: Song, startMs: Long, endMs: Long): Boolean {
    return runCatching {
        val outFile = File(context.cacheDir, "vyro_ringtone_${song.id}.m4a")
        if (outFile.exists()) outFile.delete()

        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
            extractor.setDataSource(pfd.fileDescriptor)
        }
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = f
                break
            }
        }
        if (audioTrackIndex >= 0 && format != null) {
            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()
            val buf = ByteBuffer.allocate(256 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buf, 0)
                if (info.size < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endMs * 1000L) break
                info.presentationTimeUs = (sampleTimeUs - startMs * 1000L).coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buf, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VYRO_Ringtone_${song.title}.m4a")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }
            val newUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (newUri != null) {
                context.contentResolver.openOutputStream(newUri)?.use { out ->
                    outFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, newUri)
                return true
            }
        }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
        true
    }.getOrElse {
        runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
        }
        false
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

private fun loadFloatList(json: String?): List<Float>? = runCatching {
    if (json.isNullOrBlank()) return null
    val arr = JSONArray(json)
    (0 until arr.length()).map { arr.getDouble(it).toFloat() }
}.getOrNull()

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
