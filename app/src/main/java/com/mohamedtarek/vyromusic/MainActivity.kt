package com.mohamedtarek.vyromusic

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.sin

// ==================== DATA MODELS ====================

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val folder: String,
    val dateAdded: Long
)

data class CustomPlaylist(
    val id: String,
    val name: String,
    val songIds: List<Long>
)

data class WidgetPreset(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val descAr: String,
    val gradient: List<Color>,
    val borderColor: Color,
    val isWide: Boolean = false,
    val isCircle: Boolean = false
)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val htmlUrl: String
)

enum class AppScreen {
    MAIN, THEME_STUDIO, WIDGET_STUDIO, UPDATE_CENTER, SETTINGS
}

// ==================== ALBUM ART CACHE ====================

object VyroMediaCache {
    private val maxMem = (Runtime.getRuntime().maxMemory() / 1024).toInt().coerceAtLeast(8192)
    private val artCache = object : LruCache<String, Bitmap>(maxMem / 10) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    suspend fun loadSongArt(context: Context, song: Song): Bitmap? = withContext(Dispatchers.IO) {
        val key = "song_${song.id}"
        artCache.get(key)?.let { return@withContext it }
        val bmp = runCatching {
            VyroWidgetProvider.loadRobustAlbumArt(context, song.uri.toString(), song.albumId)
        }.getOrNull()
        if (bmp != null) artCache.put(key, bmp)
        bmp
    }
}

// ==================== MAIN ACTIVITY ====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
            VyroSafePrefs.sanitizeOnUpgrade(prefs)
        }
        enableEdgeToEdge()
        setContent {
            VyroMusicApp()
        }
    }
}

// ==================== CONSTANTS ====================

val ACCENT_PALETTE = listOf(
    Color(0xFF00C2FF),
    Color(0xFF00E676),
    Color(0xFF3B82F6),
    Color(0xFF8B5CF6),
    Color(0xFFFF3366),
    Color(0xFFFFC857),
    Color(0xFFFF6B35),
    Color(0xFFEC4899)
)

val WIDGET_PRESETS = listOf(
    WidgetPreset("Glassify Frost", "زجاج Glassify", "Glassify Frost", "زجاج ضبابي يتلوّن مع غلاف الأغنية", listOf(Color(0xFF163A5F), Color(0xFF0B1120)), Color(0x88FFFFFF)),
    WidgetPreset("Full Poster", "بوستر كامل", "Full Poster", "غلاف الأغنية يملأ الويدجت بالكامل", listOf(Color(0xFF2B1C3D), Color(0xFF090B12)), Color(0x66FFFFFF)),
    WidgetPreset("Stadium Glass", "زجاج الملعب", "Stadium Glass", "إضاءة ملاعب خضراء وتخطيط احترافي", listOf(Color(0xFF0A331C), Color(0xFF050B0F)), Color(0xFF00F57A)),
    WidgetPreset("Wide Glass", "شريط عريض زجاجي", "Wide Glass", "تصميم أفقي أنيق للشاشة الرئيسية", listOf(Color(0xFF132C4A), Color(0xFF090E1A)), Color(0x66FFFFFF), isWide = true),
    WidgetPreset("AMOLED Pure", "أسود AMOLED نقي", "AMOLED Pure", "أسود فحمي مع إطار نيون سماوي", listOf(Color(0xFF000000), Color(0xFF000000)), Color(0xFF00E5FF)),
    WidgetPreset("Cyber Neon", "سايبر نيون", "Cyber Neon", "توهج أرجواني ووردي بطابع مستقبلي", listOf(Color(0xFF1A0836), Color(0xFF070A1A)), Color(0xFFFF007A)),
    WidgetPreset("Vinyl Deck", "قرص الفينيل", "Vinyl Deck", "غلاف دائري مع حلقات أسطوانة كلاسيك", listOf(Color(0xFF191D29), Color(0xFF090B10)), Color(0xFF00E5FF), isCircle = true),
    WidgetPreset("Blaugrana Pitch", "ملعب البلاوجرانا", "Blaugrana Pitch", "ألوان أزرق وقرمزي مستوحاة من كامب نو", listOf(Color(0xFF002A70), Color(0xFF8A0D32)), Color(0xFFFFC857)),
    WidgetPreset("Golden Luxury", "ذهبي فاخر", "Golden Luxury", "أسود ملكي مع لمعان وإطار ذهبي", listOf(Color(0xFF1D170B), Color(0xFF090806)), Color(0xFFFFD56B)),
    WidgetPreset("Aurora Mesh", "شفق قطبي", "Aurora Mesh", "تدرج شفق متداخل حيوي وسلس", listOf(Color(0xFF0A3242), Color(0xFF2A124C)), Color(0xFF00FFA3)),
    WidgetPreset("Dynamic Pill", "كبسولة ديناميكية", "Dynamic Pill", "شريط كبسولي منحني مع غلاف دائري", listOf(Color(0xFF121826), Color(0xFF1A2236)), Color(0xFF00E5FF), isWide = true, isCircle = true),
    WidgetPreset("Transparent Clear", "شفاف بالكامل", "Transparent Clear", "شفافية فائقة تُظهر خلفية موبايلك", listOf(Color(0x33000000), Color(0x22000000)), Color(0x77FFFFFF)),
    WidgetPreset("Retro Cassette", "كاسيت كلاسيك", "Retro Cassette", "طابع أشرطة الكاسيت بخطوط دافئة", listOf(Color(0xFF1F2433), Color(0xFF12141E)), Color(0xFFFF6B35)),
    WidgetPreset("Minimal Split", "تقسيم عصري", "Minimal Split", "شريط عريض مقسّم بتظليل ناعم", listOf(Color(0xFF16325C), Color(0xFF0B0D14)), Color(0x88FFFFFF), isWide = true)
)

// ==================== ROOT COMPOSABLE ====================

@OptIn(UnstableApi::class)
@Composable
fun VyroMusicApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE).also {
            VyroSafePrefs.sanitizeOnUpgrade(it)
        }
    }
    val scope = rememberCoroutineScope()

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "5.0.4-TITAN"
        }.getOrDefault("5.0.4-TITAN")
    }

    var isArabic by remember { mutableStateOf(VyroSafePrefs.getBoolean(prefs, "lang_ar", true)) }
    var themeMode by remember { mutableStateOf(VyroSafePrefs.getString(prefs, "theme_mode", "dark")) }
    var bgStyle by remember { mutableStateOf(VyroSafePrefs.getString(prefs, "bg_style", "aura")) }
    var listLayout by remember { mutableStateOf(VyroSafePrefs.getString(prefs, "list_layout", "medium")) }
    var accentIndex by remember { mutableIntStateOf(VyroSafePrefs.getInt(prefs, "accent_color_id", 0).coerceIn(0, ACCENT_PALETTE.lastIndex)) }
    var widgetStyle by remember { mutableStateOf(VyroSafePrefs.getString(prefs, "widget_style", "Glassify Frost")) }
    var spatial8D by remember { mutableStateOf(VyroSafePrefs.getBoolean(prefs, "spatial_8d_enabled", false)) }
    var vinylMode by remember { mutableStateOf(VyroSafePrefs.getBoolean(prefs, "vinyl_disc_mode", false)) }
    var showWaves by remember { mutableStateOf(VyroSafePrefs.getBoolean(prefs, "show_waves", true)) }
    var showTour by remember { mutableStateOf(!VyroSafePrefs.getBoolean(prefs, "tour_done_v5", false)) }
    var tourStep by remember { mutableIntStateOf(0) }

    var bandGains by remember { mutableStateOf(VyroSafePrefs.getBandGains(prefs)) }
    var bassStrength by remember { mutableIntStateOf(VyroSafePrefs.getInt(prefs, "bass_strength", 35).coerceIn(0, 100)) }
    var virtStrength by remember { mutableIntStateOf(VyroSafePrefs.getInt(prefs, "virtualizer_strength", 30).coerceIn(0, 100)) }
    var loudnessGain by remember { mutableIntStateOf(VyroSafePrefs.getInt(prefs, "loudness_gain", 0).coerceIn(0, 100)) }
    var playbackSpeed by remember { mutableFloatStateOf(VyroSafePrefs.getFloat(prefs, "playback_speed", 1f).coerceIn(0.5f, 2.0f)) }
    var playbackPitch by remember { mutableFloatStateOf(VyroSafePrefs.getFloat(prefs, "playback_pitch", 1f).coerceIn(0.5f, 2.0f)) }

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var hiddenIds by remember { mutableStateOf(VyroSafePrefs.getLongSet(prefs, "hidden_ids")) }
    var favoriteIds by remember { mutableStateOf(VyroSafePrefs.getLongSet(prefs, "favorite_ids")) }
    var recentIds by remember { mutableStateOf(VyroSafePrefs.getLongList(prefs, "recent_ids")) }
    var playCounts by remember { mutableStateOf(VyroSafePrefs.getPlayCounts(prefs)) }
    var customPlaylists by remember { mutableStateOf(loadPlaylistsSafe(prefs)) }

    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    var previousScreenBeforeUpdate by remember { mutableStateOf(AppScreen.MAIN) }
    var activeTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableIntStateOf(VyroSafePrefs.getInt(prefs, "sort_mode", 0).coerceIn(0, 2)) }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showCarMode by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showHiddenDialog by remember { mutableStateOf(false) }
    var showPlayerPrefsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var sleepMinutesLeft by remember { mutableIntStateOf(0) }
    var latestRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var updateBannerDismissed by remember { mutableStateOf(false) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(1L) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasAudioPerm = res[audioPerm] == true ||
            ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val json = JSONObject().apply {
                    put("favorites", JSONArray(favoriteIds.toList()))
                    put("hidden", JSONArray(hiddenIds.toList()))
                    put("accent_color_id", accentIndex)
                    put("widget_style", widgetStyle)
                    put("theme_mode", themeMode)
                    put("bg_style", bgStyle)
                    put("list_layout", listLayout)
                    put("playlists", serializePlaylists(customPlaylists))
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
                Toast.makeText(context, if (isArabic) "تم تصدير النسخة الاحتياطية بنجاح" else "Backup exported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@runCatching
                val json = JSONObject(text)
                val favs = mutableSetOf<Long>()
                json.optJSONArray("favorites")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i).toLongOrNull()?.let { favs.add(it) }
                }
                favoriteIds = favs
                saveIdSet(prefs, "favorite_ids", favs)

                accentIndex = json.optInt("accent_color_id", accentIndex).coerceIn(0, ACCENT_PALETTE.lastIndex)
                widgetStyle = json.optString("widget_style", widgetStyle)
                themeMode = json.optString("theme_mode", themeMode)
                bgStyle = json.optString("bg_style", bgStyle)
                listLayout = json.optString("list_layout", listLayout)

                prefs.edit()
                    .putInt("accent_color_id", accentIndex)
                    .putString("widget_style", widgetStyle)
                    .putString("theme_mode", themeMode)
                    .putString("bg_style", bgStyle)
                    .putString("list_layout", listLayout)
                    .apply()
                VyroWidgetProvider.updateAllWidgets(context)
                Toast.makeText(context, if (isArabic) "تمت استعادة النسخة الاحتياطية بنجاح" else "Backup restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun checkUpdatesNow() {
        scope.launch {
            isCheckingUpdate = true
            val rel = fetchLatestRelease()
            if (rel != null) {
                latestRelease = rel
                updateAvailable = isNewerVersion(appVersion, rel.tag)
            }
            isCheckingUpdate = false
        }
    }

    LaunchedEffect(Unit) {
        val toAsk = mutableListOf<String>()
        if (!hasAudioPerm) toAsk.add(audioPerm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            toAsk.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (toAsk.isNotEmpty()) permLauncher.launch(toAsk.toTypedArray())
    }

    LaunchedEffect(hasAudioPerm) {
        if (hasAudioPerm) {
            allSongs = scanDeviceSongs(context)
            val lastId = VyroSafePrefs.getLong(prefs, "last_song_id", -1L)
            if (currentSong == null && allSongs.isNotEmpty()) {
                val matched = allSongs.find { it.id == lastId } ?: allSongs.first()
                currentSong = matched
                totalDuration = matched.duration.coerceAtLeast(1L)
            }
        }
    }

    LaunchedEffect(Unit) {
        checkUpdatesNow()
    }

    DisposableEffect(context) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            runCatching {
                val ctrl = future.get()
                controller = ctrl
                isPlaying = ctrl.isPlaying
                shuffleOn = ctrl.shuffleModeEnabled
                repeatMode = ctrl.repeatMode
                ctrl.currentMediaItem?.mediaId?.toLongOrNull()?.let { playingId ->
                    allSongs.find { it.id == playingId }?.let { s ->
                        currentSong = s
                        totalDuration = s.duration.coerceAtLeast(1L)
                    }
                }
                ctrl.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (playing) runCatching { ctrl.volume = 1f }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                        allSongs.find { it.id == id }?.let { s ->
                            currentSong = s
                            totalDuration = s.duration.coerceAtLeast(1L)
                            val updatedRecents = (listOf(s.id) + recentIds.filter { it != s.id }).take(40)
                            recentIds = updatedRecents
                            saveIdList(prefs, "recent_ids", updatedRecents)

                            val updatedCounts = playCounts.toMutableMap()
                            updatedCounts[s.id] = (updatedCounts[s.id] ?: 0) + 1
                            playCounts = updatedCounts
                            savePlayCounts(prefs, updatedCounts)
                        }
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        shuffleOn = shuffleModeEnabled
                    }

                    override fun onRepeatModeChanged(mode: Int) {
                        repeatMode = mode
                    }
                })
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { MediaController.releaseFuture(future) }
        }
    }

    LaunchedEffect(controller, isPlaying) {
        while (true) {
            runCatching {
                controller?.let { ctrl ->
                    currentPosition = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration
                    if (dur > 0) totalDuration = dur
                }
            }
            delay(450L)
        }
    }

    // مؤقت النوم التدريجي (Fade-Out في آخر 10 ثواني)
    LaunchedEffect(sleepMinutesLeft) {
        if (sleepMinutesLeft > 1) {
            delay(60_000L)
            sleepMinutesLeft -= 1
        } else if (sleepMinutesLeft == 1) {
            delay(50_000L)
            for (step in 9 downTo 1) {
                runCatching { controller?.volume = step / 10f }
                delay(1_000L)
            }
            runCatching {
                controller?.pause()
                controller?.volume = 1f
            }
            sleepMinutesLeft = 0
            Toast.makeText(context, if (isArabic) "انتهى مؤقت النوم التدريجي وتم إيقاف التشغيل" else "Fade-out sleep timer ended", Toast.LENGTH_SHORT).show()
        }
    }

    val visibleSongs = remember(allSongs, hiddenIds, searchQuery, sortMode) {
        val base = allSongs.filter { it.id !in hiddenIds }.filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.folder.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            1 -> base.sortedByDescending { it.dateAdded }
            2 -> base.sortedByDescending { it.duration }
            else -> base.sortedBy { it.title.lowercase(Locale.ROOT) }
        }
    }

    fun playFromQueue(queue: List<Song>, startIndex: Int) {
        val ctrl = controller ?: return
        if (queue.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, queue.lastIndex)
        val items = queue.map { song ->
            val extras = Bundle().apply {
                putLong("album_id", song.albumId)
                putString("song_uri", song.uri.toString())
            }
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        runCatching {
            ctrl.volume = 1f
            ctrl.setMediaItems(items, safeIdx, 0L)
            ctrl.prepare()
            ctrl.play()
            currentSong = queue[safeIdx]
            totalDuration = queue[safeIdx].duration.coerceAtLeast(1L)
        }
    }

    fun toggleFavorite(songId: Long) {
        val next = favoriteIds.toMutableSet()
        if (songId in next) next.remove(songId) else next.add(songId)
        favoriteIds = next
        saveIdSet(prefs, "favorite_ids", next)
    }

    fun hideSong(songId: Long) {
        val next = hiddenIds.toMutableSet().apply { add(songId) }
        hiddenIds = next
        saveIdSet(prefs, "hidden_ids", next)
    }

    val isDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val accent = ACCENT_PALETTE[accentIndex]
    val bgBase = if (isDark) Color(0xFF06080D) else Color(0xFFF3F6FA)
    val cardColor = if (isDark) Color(0xFF101520) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val colorScheme = if (isDark) {
        darkColorScheme(primary = accent, background = bgBase, surface = cardColor, onBackground = textPrimary, onSurface = textPrimary)
    } else {
        lightColorScheme(primary = accent, background = bgBase, surface = cardColor, onBackground = textPrimary, onSurface = textPrimary)
    }

    val backgroundBrush = remember(isDark, bgStyle, accent) {
        when {
            !isDark -> Brush.verticalGradient(listOf(Color(0xFFEAF2FA), Color(0xFFF8FAFC)))
            bgStyle == "pure" -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
            bgStyle == "stadium" -> Brush.verticalGradient(listOf(Color(0xFF072B18), Color(0xFF050E0A), Color(0xFF030508)))
            else -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color(0xFF080D16), Color(0xFF04060A)))
        }
    }

    BackHandler(
        enabled = searchVisible || selectedFolder != null || selectedArtist != null || selectedPlaylistTitle != null || activeTab != 0
    ) {
        when {
            searchVisible -> {
                searchVisible = false
                searchQuery = ""
            }
            selectedFolder != null -> selectedFolder = null
            selectedArtist != null -> selectedArtist = null
            selectedPlaylistTitle != null -> selectedPlaylistTitle = null
            activeTab != 0 -> activeTab = 0
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colorScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .systemBarsPadding()
            ) {
                when (currentScreen) {
                    AppScreen.MAIN -> {
                        MainLibraryScreen(
                            isArabic = isArabic,
                            appVersion = appVersion,
                            accent = accent,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            songs = visibleSongs,
                            allSongs = allSongs,
                            listLayout = listLayout,
                            activeTab = activeTab,
                            onTabChange = {
                                activeTab = it
                                selectedFolder = null
                                selectedArtist = null
                                selectedPlaylistTitle = null
                            },
                            searchVisible = searchVisible,
                            onToggleSearch = {
                                searchVisible = !searchVisible
                                if (!searchVisible) searchQuery = ""
                            },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onCycleSort = {
                                sortMode = (sortMode + 1) % 3
                                prefs.edit().putInt("sort_mode", sortMode).apply()
                                val msg = when (sortMode) {
                                    1 -> if (isArabic) "الترتيب: الأحدث" else "Sort: Newest"
                                    2 -> if (isArabic) "الترتيب: الأطول مدة" else "Sort: Duration"
                                    else -> if (isArabic) "الترتيب: أبجدي" else "Sort: A-Z"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            listenSeconds = VyroSafePrefs.getLong(prefs, "stat_listen_seconds", 0L),
                            favoriteIds = favoriteIds,
                            recentIds = recentIds,
                            playCounts = playCounts,
                            customPlaylists = customPlaylists,
                            selectedFolder = selectedFolder,
                            onSelectFolder = { selectedFolder = it },
                            selectedArtist = selectedArtist,
                            onSelectArtist = { selectedArtist = it },
                            selectedPlaylistTitle = selectedPlaylistTitle,
                            selectedPlaylistSongs = selectedPlaylistSongs,
                            onOpenPlaylist = { title, list ->
                                selectedPlaylistTitle = title
                                selectedPlaylistSongs = list
                            },
                            onClosePlaylist = { selectedPlaylistTitle = null },
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onDeleteCustomPlaylist = { plId ->
                                customPlaylists = customPlaylists.filterNot { it.id == plId }
                                savePlaylists(prefs, customPlaylists)
                            },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            progressFraction = (currentPosition.toFloat() / totalDuration.coerceAtLeast(1L)).coerceIn(0f, 1f),
                            currentPosition = currentPosition,
                            hasAudioPerm = hasAudioPerm,
                            onRequestPerm = { permLauncher.launch(arrayOf(audioPerm)) },
                            onPlaySong = { list, idx -> playFromQueue(list, idx) },
                            onTogglePlay = {
                                controller?.let { ctrl ->
                                    if (ctrl.isPlaying) ctrl.pause()
                                    else if (ctrl.mediaItemCount > 0) ctrl.play()
                                    else currentSong?.let { s ->
                                        val idx = visibleSongs.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                                        playFromQueue(visibleSongs, idx)
                                    }
                                }
                            },
                            onNext = { controller?.seekToNext() },
                            onPrev = { controller?.seekToPrevious() },
                            onOpenNowPlaying = { if (currentSong != null) showNowPlaying = true },
                            onToggleFav = { toggleFavorite(it) },
                            onHideSong = { hideSong(it) },
                            onAddToPlaylist = { songForPlaylistDialog = it },
                            onOpenThemeStudio = { currentScreen = AppScreen.THEME_STUDIO },
                            onOpenWidgetStudio = { currentScreen = AppScreen.WIDGET_STUDIO },
                            onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                            updateAvailable = updateAvailable,
                            showUpdateBanner = updateAvailable && !updateBannerDismissed,
                            latestReleaseTag = latestRelease?.tag ?: "",
                            onOpenUpdateCenter = {
                                previousScreenBeforeUpdate = AppScreen.MAIN
                                currentScreen = AppScreen.UPDATE_CENTER
                            },
                            onDismissUpdateBanner = { updateBannerDismissed = true }
                        )
                    }

                    AppScreen.THEME_STUDIO -> {
                        ThemeStudioScreen(
                            isArabic = isArabic,
                            accent = accent,
                            accentIndex = accentIndex,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            sampleSongs = visibleSongs.take(4),
                            listLayout = listLayout,
                            bgStyle = bgStyle,
                            themeMode = themeMode,
                            onSelectLayout = {
                                listLayout = it
                                prefs.edit().putString("list_layout", it).apply()
                            },
                            onSelectBgStyle = {
                                bgStyle = it
                                prefs.edit().putString("bg_style", it).apply()
                            },
                            onSelectThemeMode = {
                                themeMode = it
                                prefs.edit().putString("theme_mode", it).apply()
                            },
                            onSelectAccent = {
                                accentIndex = it
                                prefs.edit().putInt("accent_color_id", it).apply()
                                VyroWidgetProvider.updateAllWidgets(context)
                            },
                            onBack = { currentScreen = AppScreen.MAIN }
                        )
                    }

                    AppScreen.WIDGET_STUDIO -> {
                        GlassifyWidgetStudioScreen(
                            isArabic = isArabic,
                            accent = accent,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            selectedWidgetStyle = widgetStyle,
                            currentSongTitle = currentSong?.title ?: "VYRO MUSIC",
                            currentSongArtist = currentSong?.artist ?: "Mohamed Tarek",
                            onSelectWidgetStyle = { style ->
                                widgetStyle = style
                                prefs.edit().putString("widget_style", style).apply()
                                VyroWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(
                                    context,
                                    if (isArabic) "تم تفعيل ويدجت: $style" else "Widget applied: $style",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onPinWidget = { pinVyroWidget(context, isArabic) },
                            onBack = { currentScreen = AppScreen.MAIN }
                        )
                    }

                    AppScreen.UPDATE_CENTER -> {
                        UpdateCenterScreen(
                            isArabic = isArabic,
                            currentVersion = appVersion,
                            accent = accent,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            release = latestRelease,
                            isChecking = isCheckingUpdate,
                            onRefresh = { checkUpdatesNow() },
                            onBack = { currentScreen = previousScreenBeforeUpdate }
                        )
                    }

                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            isArabic = isArabic,
                            appVersion = appVersion,
                            accent = accent,
                            cardColor = cardColor,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            updateAvailable = updateAvailable,
                            listenSeconds = VyroSafePrefs.getLong(prefs, "stat_listen_seconds", 0L),
                            tracksPlayed = VyroSafePrefs.getInt(prefs, "stat_tracks_played", 0),
                            bgStyle = bgStyle,
                            listLayout = listLayout,
                            widgetStyle = widgetStyle,
                            sleepMinutesLeft = sleepMinutesLeft,
                            hiddenCount = hiddenIds.size,
                            onOpenUpdates = {
                                previousScreenBeforeUpdate = AppScreen.SETTINGS
                                currentScreen = AppScreen.UPDATE_CENTER
                            },
                            onExportBackup = { exportLauncher.launch("vyro_backup_${System.currentTimeMillis()}.json") },
                            onImportBackup = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            onOpenThemeStudio = { currentScreen = AppScreen.THEME_STUDIO },
                            onOpenWidgetStudio = { currentScreen = AppScreen.WIDGET_STUDIO },
                            onToggleLanguage = {
                                isArabic = !isArabic
                                prefs.edit().putBoolean("lang_ar", isArabic).apply()
                            },
                            onOpenSleepTimer = { showSleepDialog = true },
                            onOpenHidden = { showHiddenDialog = true },
                            onOpenPlayerPrefs = { showPlayerPrefsDialog = true },
                            onBatteryOptimization = { openBatteryExemption(context) },
                            onNotificationSettings = { openNotificationSettings(context) },
                            onRestartTour = {
                                tourStep = 0
                                showTour = true
                                currentScreen = AppScreen.MAIN
                            },
                            onResetDefaults = {
                                themeMode = "dark"
                                bgStyle = "aura"
                                listLayout = "medium"
                                accentIndex = 0
                                widgetStyle = "Glassify Frost"
                                spatial8D = false
                                bassStrength = 35
                                virtStrength = 30
                                loudnessGain = 0
                                playbackSpeed = 1f
                                playbackPitch = 1f
                                bandGains = listOf(0.28f, 0.16f, 0f, 0.14f, 0.24f)
                                prefs.edit()
                                    .putString("theme_mode", themeMode)
                                    .putString("bg_style", bgStyle)
                                    .putString("list_layout", listLayout)
                                    .putInt("accent_color_id", 0)
                                    .putString("widget_style", widgetStyle)
                                    .putBoolean("spatial_8d_enabled", false)
                                    .putInt("bass_strength", 35)
                                    .putInt("virtualizer_strength", 30)
                                    .putInt("loudness_gain", 0)
                                    .putFloat("playback_speed", 1f)
                                    .putFloat("playback_pitch", 1f)
                                    .putString("band_gains", JSONArray(bandGains).toString())
                                    .apply()
                                VyroWidgetProvider.updateAllWidgets(context)
                                Toast.makeText(context, if (isArabic) "تمت إعادة الضبط بنجاح" else "Defaults restored", Toast.LENGTH_SHORT).show()
                            },
                            onOpenAbout = { showAboutDialog = true },
                            onBack = { currentScreen = AppScreen.MAIN }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showNowPlaying && currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
                ) {
                    currentSong?.let { song ->
                        NowPlayingOverlay(
                            song = song,
                            isArabic = isArabic,
                            accent = accent,
                            isPlaying = isPlaying,
                            position = currentPosition,
                            duration = totalDuration,
                            shuffleOn = shuffleOn,
                            repeatMode = repeatMode,
                            isFav = song.id in favoriteIds,
                            spatial8D = spatial8D,
                            vinylMode = vinylMode,
                            showWaves = showWaves,
                            onClose = { showNowPlaying = false },
                            onSeek = { controller?.seekTo(it) },
                            onTogglePlay = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            onNext = { controller?.seekToNext() },
                            onPrev = { controller?.seekToPrevious() },
                            onToggleShuffle = {
                                controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
                            },
                            onCycleRepeat = {
                                controller?.let {
                                    it.repeatMode = when (it.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            },
                            onToggleFav = { toggleFavorite(song.id) },
                            onOpenEq = { showEqSheet = true },
                            onToggle8D = {
                                spatial8D = !spatial8D
                                prefs.edit().putBoolean("spatial_8d_enabled", spatial8D).apply()
                                Toast.makeText(
                                    context,
                                    if (spatial8D) (if (isArabic) "تم تفعيل الصوت المحيطي 8D" else "8D Surround ON")
                                    else (if (isArabic) "تم إيقاف 8D" else "8D Surround OFF"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onOpenLyrics = { showLyricsDialog = true },
                            onOpenCarMode = { showCarMode = true },
                            onAddToPlaylist = { songForPlaylistDialog = song }
                        )
                    }
                }

                if (showCarMode && currentSong != null) {
                    CarModeOverlay(
                        song = currentSong!!,
                        accent = accent,
                        isPlaying = isPlaying,
                        onTogglePlay = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                        onNext = { controller?.seekToNext() },
                        onPrev = { controller?.seekToPrevious() },
                        onClose = { showCarMode = false }
                    )
                }

                if (showTour && currentScreen == AppScreen.MAIN) {
                    OnboardingTourOverlay(
                        isArabic = isArabic,
                        step = tourStep,
                        accent = accent,
                        onNext = {
                            if (tourStep < 3) tourStep++
                            else {
                                showTour = false
                                prefs.edit().putBoolean("tour_done_v5", true).apply()
                            }
                        },
                        onSkip = {
                            showTour = false
                            prefs.edit().putBoolean("tour_done_v5", true).apply()
                        }
                    )
                }
            }

            if (showEqSheet) {
                AudioStudioDialog(
                    isArabic = isArabic,
                    accent = accent,
                    spatial8D = spatial8D,
                    bandGains = bandGains,
                    bassStrength = bassStrength,
                    virtStrength = virtStrength,
                    loudnessGain = loudnessGain,
                    speed = playbackSpeed,
                    pitch = playbackPitch,
                    onToggle8D = {
                        spatial8D = it
                        prefs.edit().putBoolean("spatial_8d_enabled", it).apply()
                    },
                    onUpdateBand = { idx, value ->
                        val next = bandGains.toMutableList()
                        next[idx] = value
                        bandGains = next
                        prefs.edit().putString("band_gains", JSONArray(next).toString()).apply()
                    },
                    onUpdateBass = {
                        bassStrength = it
                        prefs.edit().putInt("bass_strength", it).apply()
                    },
                    onUpdateVirt = {
                        virtStrength = it
                        prefs.edit().putInt("virtualizer_strength", it).apply()
                    },
                    onUpdateLoudness = {
                        loudnessGain = it
                        prefs.edit().putInt("loudness_gain", it).apply()
                    },
                    onUpdateSpeed = {
                        playbackSpeed = it
                        prefs.edit().putFloat("playback_speed", it).apply()
                    },
                    onUpdatePitch = {
                        playbackPitch = it
                        prefs.edit().putFloat("playback_pitch", it).apply()
                    },
                    onApplyPreset = { gains, bass, virt ->
                        bandGains = gains
                        bassStrength = bass
                        virtStrength = virt
                        prefs.edit()
                            .putString("band_gains", JSONArray(gains).toString())
                            .putInt("bass_strength", bass)
                            .putInt("virtualizer_strength", virt)
                            .apply()
                    },
                    onDismiss = { showEqSheet = false }
                )
            }

            if (showLyricsDialog && currentSong != null) {
                LyricsDialog(
                    song = currentSong!!,
                    isArabic = isArabic,
                    accent = accent,
                    prefs = prefs,
                    onDismiss = { showLyricsDialog = false }
                )
            }

            if (showSleepDialog) {
                AlertDialog(
                    onDismissRequest = { showSleepDialog = false },
                    title = { Text(if (isArabic) "مؤقت النوم التدريجي" else "Fade-Out Sleep Timer", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 15, 30, 45, 60).forEach { mins ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sleepMinutesLeft == mins) accent.copy(0.2f) else Color.Transparent)
                                        .clickable {
                                            sleepMinutesLeft = mins
                                            showSleepDialog = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (mins == 0) (if (isArabic) "إيقاف المؤقت" else "Turn Off")
                                        else (if (isArabic) "$mins دقيقة (خفض تدريجي)" else "$mins Minutes (Fade-Out)"),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } }
                )
            }

            if (showCreatePlaylistDialog) {
                var plName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text(if (isArabic) "قائمة تشغيل جديدة" else "New Playlist", fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = plName,
                            onValueChange = { plName = it },
                            placeholder = { Text(if (isArabic) "اسم القائمة..." else "Playlist name...") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (plName.isNotBlank()) {
                                val next = customPlaylists + CustomPlaylist(System.currentTimeMillis().toString(), plName.trim(), emptyList())
                                customPlaylists = next
                                savePlaylists(prefs, next)
                                showCreatePlaylistDialog = false
                            }
                        }) { Text(if (isArabic) "إنشاء" else "Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(if (isArabic) "إلغاء" else "Cancel") }
                    }
                )
            }

            songForPlaylistDialog?.let { targetSong ->
                AlertDialog(
                    onDismissRequest = { songForPlaylistDialog = null },
                    title = { Text(if (isArabic) "إضافة إلى قائمة" else "Add to Playlist", fontWeight = FontWeight.Bold) },
                    text = {
                        if (customPlaylists.isEmpty()) {
                            Text(if (isArabic) "لا توجد قوائم مخصصة بعد. أنشئ قائمة جديدة من تبويب القوائم." else "No custom playlists yet.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                customPlaylists.forEach { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                val updated = customPlaylists.map {
                                                    if (it.id == pl.id && targetSong.id !in it.songIds) {
                                                        it.copy(songIds = it.songIds + targetSong.id)
                                                    } else it
                                                }
                                                customPlaylists = updated
                                                savePlaylists(prefs, updated)
                                                songForPlaylistDialog = null
                                                Toast.makeText(context, if (isArabic) "تمت الإضافة إلى ${pl.name}" else "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.QueueMusic, null, tint = accent)
                                        Spacer(Modifier.width(10.dp))
                                        Text(pl.name, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            songForPlaylistDialog = null
                            showCreatePlaylistDialog = true
                        }) { Text(if (isArabic) "+ قائمة جديدة" else "+ New") }
                    }
                )
            }

            if (showHiddenDialog) {
                val hiddenSongs = allSongs.filter { it.id in hiddenIds }
                AlertDialog(
                    onDismissRequest = { showHiddenDialog = false },
                    title = { Text(if (isArabic) "الملفات المخفية (${hiddenSongs.size})" else "Hidden Files", fontWeight = FontWeight.Bold) },
                    text = {
                        if (hiddenSongs.isEmpty()) {
                            Text(if (isArabic) "لا توجد ملفات مخفية." else "No hidden files.")
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(hiddenSongs) { s ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        TextButton(onClick = {
                                            val next = hiddenIds.toMutableSet().apply { remove(s.id) }
                                            hiddenIds = next
                                            saveIdSet(prefs, "hidden_ids", next)
                                        }) { Text(if (isArabic) "إظهار" else "Restore") }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showHiddenDialog = false }) { Text(if (isArabic) "تم" else "Done") } }
                )
            }

            if (showPlayerPrefsDialog) {
                AlertDialog(
                    onDismissRequest = { showPlayerPrefsDialog = false },
                    title = { Text(if (isArabic) "إعدادات التشغيل والمشغل" else "Player Settings", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isArabic) "صوت محيطي 8D" else "8D Surround", modifier = Modifier.weight(1f))
                                Switch(checked = spatial8D, onCheckedChange = {
                                    spatial8D = it
                                    prefs.edit().putBoolean("spatial_8d_enabled", it).apply()
                                })
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isArabic) "شكل القرص الدوّار (Vinyl)" else "Spinning Vinyl Disc", modifier = Modifier.weight(1f))
                                Switch(checked = vinylMode, onCheckedChange = {
                                    vinylMode = it
                                    prefs.edit().putBoolean("vinyl_disc_mode", it).apply()
                                })
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isArabic) "موجات الصوت التفاعلية" else "Audio Wave Visualizer", modifier = Modifier.weight(1f))
                                Switch(checked = showWaves, onCheckedChange = {
                                    showWaves = it
                                    prefs.edit().putBoolean("show_waves", it).apply()
                                })
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showPlayerPrefsDialog = false }) { Text(if (isArabic) "تم" else "Done") } }
                )
            }

            if (showAboutDialog) {
                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    title = { Text("VYRO MUSIC $appVersion", fontWeight = FontWeight.ExtraBold) },
                    text = {
                        Text(
                            if (isArabic)
                                "مشغل موسيقى احترافي متكامل بتطوير محمد طارق.\n\n• محرك صوت نقي + 8D محيطي + مانع غروشة\n• 14 شكل ويدجت Glassify فاخرة\n• استوديو سمات حي وتخصيص كامل\n• مركز تحديثات متكامل مع «ما الجديد» وتثبيت آمن بدون مسح البيانات"
                            else
                                "Professional music player built by Mohamed Tarek.\n\n• Crystal Audio & 8D Surround\n• 14 Glassify Widgets\n• Live Theme Studio\n• Full Update Center with What's New & Safe Seamless Updates"
                        )
                    },
                    confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(if (isArabic) "إغلاق" else "Close") } }
                )
            }
        }
    }
}

// ==================== FULL-SCREEN UPDATE CENTER + WHAT'S NEW ====================

@Composable
fun UpdateCenterScreen(
    isArabic: Boolean,
    currentVersion: String,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    release: ReleaseInfo?,
    isChecking: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadProgress by remember { mutableFloatStateOf(-1f) }
    var showWhatsNewPanel by remember { mutableStateOf(false) }

    val hasNew = release != null && isNewerVersion(currentVersion, release.tag)
    val whatsNewText = remember(release, isArabic) {
        val remoteNotes = release?.notes?.trim().orEmpty()
        if (remoteNotes.isNotBlank()) remoteNotes
        else if (isArabic) {
            "• إعادة تصميم صفحة التحديثات الكاملة وإرجاع زر «ما الجديد» لعرض تفاصيل الإصدار.\n• إصلاح العطل الفني عند التثبيت فوق النسخة القديمة بدون مسح بيانات التطبيق.\n• إصلاح تداخل الأزرار في لوحة المشغل وتنظيم أزرار المعادل و8D والكلمات.\n• ربط زر الرجوع في النظام للعودة لصفحة الأغاني مباشرة.\n• دعم 14 شكل ويدجت Glassify مع محرك صوت نقي و8D."
        } else {
            "• Restored full-screen Update Center with dedicated What's New button.\n• Fixed upgrade crash when installing over older versions without wiping data.\n• Fixed Now Playing controls overlap and system Back navigation.\n• 14 Glassify Widgets & Crystal 8D Audio."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isArabic) "مركز التحديثات" else "Update Center",
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    color = textPrimary
                )
                Text(
                    text = if (isArabic) "تحديثات VYRO MUSIC الرسمية عبر GitHub" else "Official VYRO MUSIC GitHub Updates",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
            IconButton(
                onClick = { if (!isChecking) onRefresh() }
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = accent)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Main Hero Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cardColor)
                .border(
                    width = 1.5.dp,
                    color = if (hasNew) Color(0xFF00E676).copy(0.75f) else accent.copy(0.35f),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (hasNew) Color(0xFF00E676).copy(0.18f) else accent.copy(0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (isChecking) {
                    CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                } else {
                    Icon(
                        imageVector = if (hasNew) Icons.Rounded.SystemUpdate else Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = if (hasNew) Color(0xFF00E676) else accent,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = when {
                    isChecking -> if (isArabic) "جاري فحص أحدث إصدار..." else "Checking for updates..."
                    hasNew -> if (isArabic) "تحديث جديد متوفر الآن!" else "New Update Available!"
                    else -> if (isArabic) "تطبيقك محدّث لآخر إصدار" else "Your App is Up to Date"
                },
                fontWeight = FontWeight.Black,
                fontSize = 19.sp,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = if (isArabic) "الإصدار المثبّت حالياً: $currentVersion" else "Installed Version: $currentVersion",
                fontSize = 13.sp,
                color = textSecondary
            )

            if (release != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(0.14f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isArabic) "أحدث إصدار على السيرفر: ${release.name}" else "Latest Release: ${release.name}",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // شريط تقدم التحميل المباشر
            if (downloadProgress >= 0f) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                    color = accent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isArabic) "جاري تنزيل التحديث... ${(downloadProgress * 100).toInt()}%" else "Downloading update... ${(downloadProgress * 100).toInt()}%",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
            }

            // زر التحميل والتثبيت المباشر أو الفحص
            if (hasNew && release != null && release.apkUrl.isNotBlank()) {
                Button(
                    enabled = downloadProgress < 0f,
                    onClick = {
                        scope.launch {
                            downloadProgress = 0.05f
                            downloadAndInstallApk(context, release.apkUrl, release.htmlUrl) { p ->
                                downloadProgress = p
                            }
                            downloadProgress = -1f
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "تنزيل وتثبيت التحديث الآن" else "Download & Install Now",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    enabled = !isChecking,
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "فحص التحديثات الآن" else "Check for Updates",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // زر «ما الجديد» المستقل لعرض تفاصيل الإصدار والمميزات
            OutlinedButton(
                onClick = { showWhatsNewPanel = !showWhatsNewPanel },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.2.dp, accent.copy(0.65f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.NewReleases, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (showWhatsNewPanel) {
                        if (isArabic) "إخفاء «ما الجديد»" else "Hide What's New"
                    } else {
                        if (isArabic) "ما الجديد في هذا الإصدار" else "What's New"
                    },
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // بطاقة تفاصيل «ما الجديد» (تظهر عند الضغط على زر ما الجديد أو تلقائياً لو فيه تحديث جديد)
        AnimatedVisibility(visible = showWhatsNewPanel || hasNew) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardColor)
                    .border(1.dp, accent.copy(0.3f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFFFC857), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "ما الجديد (سجل التغييرات)" else "What's New (Changelog)",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = textPrimary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = whatsNewText,
                    color = textPrimary.copy(0.9f),
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // خيار فتح صفحة الإصدارات على GitHub
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(cardColor)
                .clickable {
                    val targetUrl = release?.htmlUrl ?: "https://github.com/mohmedtarek2025-hue/VYRO-MUSIC/releases"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))) }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.OpenInNew, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (isArabic) "عرض جميع الإصدارات على GitHub" else "View all releases on GitHub",
                color = textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Rounded.ChevronLeft, null, tint = textSecondary)
        }
    }
}

// ==================== MAIN LIBRARY SCREEN ====================

@Composable
fun MainLibraryScreen(
    isArabic: Boolean,
    appVersion: String,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    songs: List<Song>,
    allSongs: List<Song>,
    listLayout: String,
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    searchVisible: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCycleSort: () -> Unit,
    listenSeconds: Long,
    favoriteIds: Set<Long>,
    recentIds: List<Long>,
    playCounts: Map<Long, Int>,
    customPlaylists: List<CustomPlaylist>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    selectedArtist: String?,
    onSelectArtist: (String?) -> Unit,
    selectedPlaylistTitle: String?,
    selectedPlaylistSongs: List<Song>,
    onOpenPlaylist: (String, List<Song>) -> Unit,
    onClosePlaylist: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeleteCustomPlaylist: (String) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    progressFraction: Float,
    currentPosition: Long,
    hasAudioPerm: Boolean,
    onRequestPerm: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onToggleFav: (Long) -> Unit,
    onHideSong: (Long) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onOpenThemeStudio: () -> Unit,
    onOpenWidgetStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    updateAvailable: Boolean,
    showUpdateBanner: Boolean,
    latestReleaseTag: String,
    onOpenUpdateCenter: () -> Unit,
    onDismissUpdateBanner: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(0.55f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VYRO",
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        color = textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .clickable { onOpenUpdateCenter() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = appVersion.substringBefore("-"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderCompactIcon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search, textPrimary, onToggleSearch)
                    HeaderCompactIcon(Icons.Rounded.Sort, textPrimary, onCycleSort)
                    HeaderCompactIcon(Icons.Rounded.Dashboard, Color(0xFFFFC857), onOpenWidgetStudio)
                    HeaderCompactIcon(Icons.Rounded.Palette, accent, onOpenThemeStudio)
                    Box {
                        HeaderCompactIcon(Icons.Rounded.Settings, textPrimary, onOpenSettings)
                        if (updateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-3).dp, y = 3.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isArabic) "${songs.size} مقطع • ${formatHoursMinutes(listenSeconds)}" else "${songs.size} tracks • ${formatHoursMinutes(listenSeconds)}",
                fontSize = 12.sp,
                color = textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 48.dp)
            )
        }

        // بطاقة الإشعار الذكية عند توفر تحديث جديد
        AnimatedVisibility(visible = showUpdateBanner) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF00E676).copy(0.14f))
                    .border(1.dp, Color(0xFF00E676).copy(0.6f), RoundedCornerShape(16.dp))
                    .clickable { onOpenUpdateCenter() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color(0xFF00E676), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isArabic) "تحديث جديد متوفر ($latestReleaseTag)" else "Update Available ($latestReleaseTag)",
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isArabic) "اضغط لعرض «ما الجديد» والتثبيت المباشر" else "Tap to view What's New & install",
                        color = textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDismissUpdateBanner, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(if (isArabic) "ابحث عن أغنية أو فنان أو مجلد..." else "Search songs, artists, folders...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = accent) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) { Icon(Icons.Rounded.Clear, null) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        val tabs = if (isArabic) listOf("المكتبة", "القوائم", "المجلدات", "الفنانون") else listOf("Library", "Playlists", "Folders", "Artists")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                val selected = activeTab == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent else cardColor.copy(alpha = 0.85f))
                        .clickable { onTabChange(idx) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (selected) Color(0xFF04101A) else textSecondary
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!hasAudioPerm) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (isArabic) "اسمح للتطبيق بالوصول إلى ملفات الصوت لتشغيل مكتبتك" else "Grant audio permission to load your music",
                        textAlign = TextAlign.Center,
                        color = textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPerm, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text(if (isArabic) "منح الإذن" else "Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                when (activeTab) {
                    0 -> SongsListArea(
                        songs = songs,
                        listLayout = listLayout,
                        currentSongId = currentSong?.id,
                        accent = accent,
                        cardColor = cardColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        isArabic = isArabic,
                        favoriteIds = favoriteIds,
                        onPlay = onPlaySong,
                        onToggleFav = onToggleFav,
                        onHide = onHideSong,
                        onAddToPlaylist = onAddToPlaylist
                    )

                    1 -> {
                        if (selectedPlaylistTitle != null) {
                            Column(Modifier.fillMaxSize()) {
                                SubHeaderBar(selectedPlaylistTitle, textPrimary, onClosePlaylist)
                                SongsListArea(
                                    songs = selectedPlaylistSongs,
                                    listLayout = listLayout,
                                    currentSongId = currentSong?.id,
                                    accent = accent,
                                    cardColor = cardColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isArabic = isArabic,
                                    favoriteIds = favoriteIds,
                                    onPlay = onPlaySong,
                                    onToggleFav = onToggleFav,
                                    onHide = onHideSong,
                                    onAddToPlaylist = onAddToPlaylist
                                )
                            }
                        } else {
                            val favList = songs.filter { it.id in favoriteIds }
                            val recentList = recentIds.mapNotNull { id -> songs.find { it.id == id } }
                            val topList = songs.filter { (playCounts[it.id] ?: 0) > 0 }.sortedByDescending { playCounts[it.id] ?: 0 }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (isArabic) "قوائمك الذكية" else "Smart Playlists",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            if (isArabic) "+ قائمة جديدة" else "+ New Playlist",
                                            color = accent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { onCreatePlaylistClick() }.padding(6.dp)
                                        )
                                    }
                                }
                                item {
                                    SmartPlaylistCard(
                                        title = if (isArabic) "الأغاني المفضلة" else "Favorites",
                                        subtitle = if (isArabic) "${favList.size} أغنية" else "${favList.size} songs",
                                        icon = Icons.Rounded.Favorite,
                                        iconTint = Color(0xFFFF4081),
                                        cardColor = cardColor,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = { onOpenPlaylist(if (isArabic) "الأغاني المفضلة" else "Favorites", favList) }
                                    )
                                }
                                item {
                                    SmartPlaylistCard(
                                        title = if (isArabic) "تم تشغيلها مؤخراً" else "Recently Played",
                                        subtitle = if (isArabic) "${recentList.size} أغنية" else "${recentList.size} songs",
                                        icon = Icons.Rounded.History,
                                        iconTint = accent,
                                        cardColor = cardColor,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = { onOpenPlaylist(if (isArabic) "تم تشغيلها مؤخراً" else "Recently Played", recentList) }
                                    )
                                }
                                item {
                                    SmartPlaylistCard(
                                        title = if (isArabic) "الأكثر استماعاً" else "Most Played",
                                        subtitle = if (isArabic) "${topList.size} أغنية" else "${topList.size} songs",
                                        icon = Icons.Rounded.Star,
                                        iconTint = Color(0xFFFFC857),
                                        cardColor = cardColor,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = { onOpenPlaylist(if (isArabic) "الأكثر استماعاً" else "Most Played", topList) }
                                    )
                                }
                                items(customPlaylists) { pl ->
                                    val plSongs = pl.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(cardColor)
                                            .clickable { onOpenPlaylist(pl.name, plSongs) }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.QueueMusic, null, tint = accent)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pl.name, fontWeight = FontWeight.Bold, color = textPrimary)
                                            Text(if (isArabic) "${plSongs.size} أغنية" else "${plSongs.size} songs", fontSize = 12.sp, color = textSecondary)
                                        }
                                        IconButton(onClick = { onDeleteCustomPlaylist(pl.id) }) {
                                            Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF5252))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        val grouped = remember(songs) { songs.groupBy { it.folder }.toList().sortedByDescending { it.second.size } }
                        if (selectedFolder != null) {
                            val folderSongs = songs.filter { it.folder == selectedFolder }
                            Column(Modifier.fillMaxSize()) {
                                SubHeaderBar(selectedFolder, textPrimary) { onSelectFolder(null) }
                                SongsListArea(
                                    songs = folderSongs,
                                    listLayout = listLayout,
                                    currentSongId = currentSong?.id,
                                    accent = accent,
                                    cardColor = cardColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isArabic = isArabic,
                                    favoriteIds = favoriteIds,
                                    onPlay = onPlaySong,
                                    onToggleFav = onToggleFav,
                                    onHide = onHideSong,
                                    onAddToPlaylist = onAddToPlaylist
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(grouped) { (folder, list) ->
                                    SmartPlaylistCard(
                                        title = folder,
                                        subtitle = if (isArabic) "${list.size} ملف صوتي" else "${list.size} audio files",
                                        icon = Icons.Rounded.Folder,
                                        iconTint = accent,
                                        cardColor = cardColor,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        onClick = { onSelectFolder(folder) }
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        val grouped = remember(songs) { songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase(Locale.ROOT) } }
                        if (selectedArtist != null) {
                            val artistSongs = songs.filter { it.artist == selectedArtist }
                            Column(Modifier.fillMaxSize()) {
                                SubHeaderBar(selectedArtist, textPrimary) { onSelectArtist(null) }
                                SongsListArea(
                                    songs = artistSongs,
                                    listLayout = listLayout,
                                    currentSongId = currentSong?.id,
                                    accent = accent,
                                    cardColor = cardColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    isArabic = isArabic,
                                    favoriteIds = favoriteIds,
                                    onPlay = onPlaySong,
                                    onToggleFav = onToggleFav,
                                    onHide = onHideSong,
                                    onAddToPlaylist = onAddToPlaylist
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(grouped) { (artist, list) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(cardColor)
                                            .clickable { onSelectArtist(artist) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SongCoverImage(list.first(), accent, Modifier.size(48.dp).clip(CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(artist, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (isArabic) "${list.size} أغنية" else "${list.size} songs", fontSize = 12.sp, color = textSecondary)
                                        }
                                        Icon(Icons.Rounded.ChevronLeft, null, tint = textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        currentSong?.let { song ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(cardColor.copy(alpha = 0.96f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .clickable { onOpenNowPlaying() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongCoverImage(song, accent, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(song.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${song.artist} • ${formatMs(currentPosition)}", fontSize = 12.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, null, tint = textPrimary)
                            }
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                                    .clickable { onTogglePlay() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF04101A), modifier = Modifier.size(25.dp))
                            }
                            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.SkipNext, null, tint = textPrimary)
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(textSecondary.copy(0.2f))) {
                        Box(modifier = Modifier.fillMaxWidth(progressFraction).fillMaxHeight().background(accent))
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderCompactIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun SongsListArea(
    songs: List<Song>,
    listLayout: String,
    currentSongId: Long?,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isArabic: Boolean,
    favoriteIds: Set<Long>,
    onPlay: (List<Song>, Int) -> Unit,
    onToggleFav: (Long) -> Unit,
    onHide: (Long) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (isArabic) "لا توجد مقاطع صوتية هنا" else "No audio tracks found", color = textSecondary)
        }
        return
    }

    if (listLayout == "grid" || listLayout == "grid3") {
        val cols = if (listLayout == "grid3") 3 else 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(songs.indices.toList()) { idx ->
                val song = songs[idx]
                val active = song.id == currentSongId
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) accent.copy(0.18f) else cardColor)
                        .border(1.dp, if (active) accent else Color.White.copy(0.06f), RoundedCornerShape(16.dp))
                        .clickable { onPlay(songs, idx) }
                        .padding(8.dp)
                ) {
                    SongCoverImage(song, accent, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(song.title, fontWeight = FontWeight.Bold, fontSize = if (cols == 3) 11.sp else 13.sp, color = if (active) accent else textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, fontSize = if (cols == 3) 9.sp else 11.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        val compact = listLayout == "compact"
        val circleArt = listLayout == "circle"
        val minimal = listLayout == "minimal"
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact || minimal) 6.dp else 10.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
                val active = song.id == currentSongId
                var menuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (compact || minimal) 14.dp else 18.dp))
                        .background(if (active) accent.copy(0.18f) else cardColor.copy(0.92f))
                        .border(1.dp, if (active) accent.copy(0.8f) else Color.White.copy(0.06f), RoundedCornerShape(if (compact || minimal) 14.dp else 18.dp))
                        .clickable { onPlay(songs, idx) }
                        .padding(horizontal = 12.dp, vertical = if (compact || minimal) 8.dp else 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!minimal) {
                        SongCoverImage(
                            song = song,
                            accent = accent,
                            modifier = Modifier
                                .size(if (compact) 44.dp else 54.dp)
                                .clip(if (circleArt) CircleShape else RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact || minimal) 14.sp else 15.sp,
                            color = if (active) accent else textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${song.artist} • ${formatMs(song.duration)}",
                            fontSize = 12.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, null, tint = textSecondary)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (song.id in favoriteIds) (if (isArabic) "إزالة من المفضلة" else "Unfavorite") else (if (isArabic) "إضافة للمفضلة" else "Favorite")) },
                                leadingIcon = { Icon(Icons.Rounded.Favorite, null) },
                                onClick = { menuOpen = false; onToggleFav(song.id) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArabic) "إضافة لقائمة تشغيل" else "Add to Playlist") },
                                leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                                onClick = { menuOpen = false; onAddToPlaylist(song) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isArabic) "إخفاء الملف" else "Hide Track") },
                                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                                onClick = { menuOpen = false; onHide(song.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 14 WIDGETS STUDIO SCREEN ====================

@Composable
fun GlassifyWidgetStudioScreen(
    isArabic: Boolean,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    selectedWidgetStyle: String,
    currentSongTitle: String,
    currentSongArtist: String,
    onSelectWidgetStyle: (String) -> Unit,
    onPinWidget: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isArabic) "استوديو ويدجت Glassify" else "Glassify Widget Studio",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isArabic) "14 شكل ويدجت فاخر للشاشة الرئيسية" else "14 Premium Home Screen Widgets",
                    fontSize = 12.sp,
                    color = textSecondary
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(WIDGET_PRESETS) { preset ->
                val isSelected = selectedWidgetStyle == preset.id
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent else Color.White.copy(0.08f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelectWidgetStyle(preset.id) }
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(RoundedCornerShape(if (preset.id == "Dynamic Pill") 34.dp else 16.dp))
                            .background(Brush.linearGradient(preset.gradient))
                            .border(1.5.dp, preset.borderColor, RoundedCornerShape(if (preset.id == "Dynamic Pill") 34.dp else 16.dp))
                            .padding(10.dp)
                    ) {
                        if (preset.isWide) {
                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(if (preset.isCircle) CircleShape else RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(currentSongTitle, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(currentSongArtist, color = Color.White.copy(0.7f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(if (preset.isCircle) CircleShape else RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(0.22f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Text(currentSongTitle, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = accent,
                                modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isArabic) preset.titleAr else preset.titleEn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isSelected) accent else textPrimary,
                        maxLines = 1
                    )
                    Text(
                        preset.descAr,
                        fontSize = 10.sp,
                        color = textSecondary,
                        maxLines = 2,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        Button(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC857)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Rounded.Dashboard, null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                if (isArabic) "إضافة الويدجت المختار للشاشة الرئيسية" else "Add Selected Widget to Home Screen",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp
            )
        }
    }
}

// ==================== LIVE THEME STUDIO ====================

@Composable
fun ThemeStudioScreen(
    isArabic: Boolean,
    accent: Color,
    accentIndex: Int,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    sampleSongs: List<Song>,
    listLayout: String,
    bgStyle: String,
    themeMode: String,
    onSelectLayout: (String) -> Unit,
    onSelectBgStyle: (String) -> Unit,
    onSelectThemeMode: (String) -> Unit,
    onSelectAccent: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = textPrimary) }
            Text(if (isArabic) "استوديو السمات الحي" else "Live Theme Studio", fontWeight = FontWeight.Black, fontSize = 20.sp, color = textPrimary)
        }
        Spacer(Modifier.height(14.dp))

        val layouts = listOf(
            "medium" to (if (isArabic) "العرض المتوسط" else "Medium View"),
            "compact" to (if (isArabic) "العرض المدمج" else "Compact View"),
            "circle" to (if (isArabic) "أغلفة دائرية" else "Circle Covers"),
            "grid" to (if (isArabic) "شبكة البوسترات 2x2" else "Poster Grid 2x2"),
            "grid3" to (if (isArabic) "شبكة مدمجة 3x3" else "Compact Grid 3x3"),
            "minimal" to (if (isArabic) "قائمة نصية سريعة" else "Minimal List")
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(layouts) { (id, label) ->
                val selected = listLayout == id
                Column(
                    modifier = Modifier
                        .width(205.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(cardColor)
                        .border(if (selected) 2.dp else 1.dp, if (selected) accent else Color.White.copy(0.08f), RoundedCornerShape(22.dp))
                        .clickable { onSelectLayout(id) }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.Bold, color = accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (id == "grid" || id == "grid3") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sampleSongs.take(if (id == "grid3") 3 else 2).forEach { s ->
                                Column(Modifier.weight(1f)) {
                                    SongCoverImage(s, accent, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)))
                                    Text(s.title, fontSize = 8.sp, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        sampleSongs.take(3).forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (id != "minimal") {
                                    SongCoverImage(
                                        s,
                                        accent,
                                        Modifier
                                            .size(if (id == "compact") 28.dp else 34.dp)
                                            .clip(if (id == "circle") CircleShape else RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.artist, fontSize = 9.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accent else Color.White.copy(0.08f))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (selected) (if (isArabic) "✓ محدد الآن" else "✓ Active") else (if (isArabic) "تحديد هذا الشكل" else "Select"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) Color.Black else textPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(if (isArabic) "خلفية التطبيق" else "App Atmosphere", fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                "pure" to (if (isArabic) "سادة AMOLED" else "AMOLED"),
                "aura" to (if (isArabic) "هالة الغلاف 🎨" else "Cover Aura"),
                "stadium" to (if (isArabic) "استاد الأبطال 🏟️" else "Stadium")
            ).forEach { (id, label) ->
                val sel = bgStyle == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) accent.copy(0.2f) else cardColor)
                        .border(1.dp, if (sel) accent else Color.White.copy(0.08f), RoundedCornerShape(16.dp))
                        .clickable { onSelectBgStyle(id) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) accent else textPrimary)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(if (isArabic) "وضع الإضاءة" else "Appearance", fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardColor).padding(4.dp)
        ) {
            listOf(
                "dark" to (if (isArabic) "ليلي" else "Dark"),
                "light" to (if (isArabic) "نهاري" else "Light"),
                "system" to (if (isArabic) "النظام" else "System")
            ).forEach { (id, label) ->
                val sel = themeMode == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) accent else Color.Transparent)
                        .clickable { onSelectThemeMode(id) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontWeight = FontWeight.Bold, color = if (sel) Color.Black else textSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(if (isArabic) "لون التمييز" else "Accent Color", fontWeight = FontWeight.Bold, color = textPrimary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ACCENT_PALETTE.forEachIndexed { idx, col ->
                val sel = accentIndex == idx
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { onSelectAccent(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black)
                }
            }
        }
    }
}

// ==================== NOW PLAYING OVERLAY ====================

@Composable
fun NowPlayingOverlay(
    song: Song,
    isArabic: Boolean,
    accent: Color,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    shuffleOn: Boolean,
    repeatMode: Int,
    isFav: Boolean,
    spatial8D: Boolean,
    vinylMode: Boolean,
    showWaves: Boolean,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFav: () -> Unit,
    onOpenEq: () -> Unit,
    onToggle8D: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenCarMode: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }

    val inf = rememberInfiniteTransition(label = "np")
    val spin by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "vinyl"
    )
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060912))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.26f), Color(0xFF0A0D14), Color(0xFF040508))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val wArt = maxWidth * 0.74f
        val hArt = maxHeight * 0.34f
        val artSize = (if (wArt < hArt) wArt else hArt).coerceIn(170.dp, 300.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = "VYRO STUDIO PLAYER",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                IconButton(onClick = onOpenCarMode, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.weight(0.6f))

            Box(
                modifier = Modifier
                    .size(artSize)
                    .rotate(if (vinylMode && isPlaying) spin else 0f),
                contentAlignment = Alignment.Center
            ) {
                SongCoverImage(
                    song = song,
                    accent = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(if (vinylMode) CircleShape else RoundedCornerShape(26.dp))
                        .border(2.dp, Color.White.copy(0.14f), if (vinylMode) CircleShape else RoundedCornerShape(26.dp))
                )
                if (vinylMode) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF0A0D14)).border(2.dp, accent, CircleShape))
                }
            }

            Spacer(Modifier.height(14.dp))

            if (showWaves) {
                Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    val bars = 36
                    val step = size.width / bars
                    for (i in 0 until bars) {
                        val h = if (isPlaying) {
                            ((sin(wavePhase + i * 0.45f) + 1f) * 0.42f + 0.14f) * size.height
                        } else size.height * 0.14f
                        val x = i * step + step / 2f
                        drawLine(
                            color = accent.copy(alpha = 0.85f),
                            start = Offset(x, size.height / 2f - h / 2f),
                            end = Offset(x, size.height / 2f + h / 2f),
                            strokeWidth = 4f
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        color = Color.White.copy(0.68f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onToggleFav, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (isFav) Color(0xFFFF4081) else Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                        onValueChange = { onSeek(it.toLong()) },
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(0.22f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatMs(position), color = Color.White.copy(0.78f), fontSize = 12.sp, maxLines = 1)
                        Text("VYRO CRYSTAL AUDIO", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
                        Text(formatMs(duration), color = Color.White.copy(0.78f), fontSize = 12.sp, maxLines = 1)
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Shuffle, null, tint = if (shuffleOn) accent else Color.White.copy(0.55f), modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = onCycleRepeat, modifier = Modifier.size(44.dp)) {
                            val ic = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                            Icon(ic, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(0.55f), modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerActionTile(
                    label = if (isArabic) "معادل الصوت" else "Equalizer",
                    icon = Icons.Rounded.Equalizer,
                    active = false,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenEq
                )
                PlayerActionTile(
                    label = if (isArabic) "8D محيطي" else "8D Spatial",
                    icon = Icons.Rounded.Headphones,
                    active = spatial8D,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = onToggle8D
                )
                PlayerActionTile(
                    label = if (isArabic) "الكلمات" else "Lyrics",
                    icon = Icons.Rounded.Article,
                    active = false,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLyrics
                )
            }
        }
    }
}

@Composable
fun PlayerActionTile(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) accent.copy(0.24f) else Color.White.copy(0.07f))
            .border(1.dp, if (active) accent else Color.White.copy(0.18f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (active) accent else Color.White, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (active) accent else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== SETTINGS SCREEN ====================

@Composable
fun SettingsScreen(
    isArabic: Boolean,
    appVersion: String,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    updateAvailable: Boolean,
    listenSeconds: Long,
    tracksPlayed: Int,
    bgStyle: String,
    listLayout: String,
    widgetStyle: String,
    sleepMinutesLeft: Int,
    hiddenCount: Int,
    onOpenUpdates: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    onOpenWidgetStudio: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenPlayerPrefs: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onNotificationSettings: () -> Unit,
    onRestartTour: () -> Unit,
    onResetDefaults: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = textPrimary) }
            Text(if (isArabic) "الإعدادات" else "Settings", fontWeight = FontWeight.Black, fontSize = 22.sp, color = textPrimary)
        }
        Spacer(Modifier.height(12.dp))

        SettingsCardGroup(cardColor) {
            SettingsRow(
                icon = Icons.Rounded.SystemUpdate,
                tint = if (updateAvailable) Color(0xFF00E676) else accent,
                title = if (isArabic) "تحديثات التطبيق" else "App Updates",
                subtitle = if (isArabic) "صفحة التحديثات وزر «ما الجديد»" else "Update Center & What's New",
                trailing = if (updateAvailable) (if (isArabic) "تحديث جديد!" else "Update!") else (if (isArabic) "محدث" else "Latest"),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenUpdates
            )
            SettingsRow(
                icon = Icons.Rounded.AccessTime,
                tint = textPrimary,
                title = if (isArabic) "وقت التشغيل" else "Listening Time",
                subtitle = if (isArabic) "$tracksPlayed مقطع تم تشغيله" else "$tracksPlayed tracks played",
                trailing = formatHoursMinutes(listenSeconds),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Rounded.CloudUpload,
                tint = textPrimary,
                title = if (isArabic) "تصدير نسخة احتياطية (JSON)" else "Export Backup (JSON)",
                subtitle = null,
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onExportBackup
            )
            SettingsRow(
                icon = Icons.Rounded.CloudDownload,
                tint = textPrimary,
                title = if (isArabic) "استعادة نسخة احتياطية" else "Restore Backup",
                subtitle = null,
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onImportBackup
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup(cardColor) {
            SettingsRow(
                icon = Icons.Rounded.Palette,
                tint = accent,
                title = if (isArabic) "استوديو السمات والواجهة" else "Theme Studio",
                subtitle = null,
                trailing = "$bgStyle / $listLayout",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenThemeStudio
            )
            SettingsRow(
                icon = Icons.Rounded.Dashboard,
                tint = Color(0xFFFFC857),
                title = if (isArabic) "استوديو ويدجت Glassify" else "Glassify Widgets",
                subtitle = if (isArabic) "14 شكل ويدجت للشاشة الرئيسية" else "14 Home Screen Widgets",
                trailing = widgetStyle,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenWidgetStudio
            )
            SettingsRow(
                icon = Icons.Rounded.Language,
                tint = textPrimary,
                title = if (isArabic) "اللغة" else "Language",
                subtitle = null,
                trailing = if (isArabic) "العربية" else "English",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onToggleLanguage
            )
            SettingsRow(
                icon = Icons.Rounded.Bedtime,
                tint = textPrimary,
                title = if (isArabic) "مؤقت النوم التدريجي" else "Fade-Out Sleep Timer",
                subtitle = null,
                trailing = if (sleepMinutesLeft > 0) "${sleepMinutesLeft}m" else (if (isArabic) "إيقاف" else "Off"),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenSleepTimer
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup(cardColor) {
            SettingsRow(
                icon = Icons.Rounded.VisibilityOff,
                tint = textPrimary,
                title = if (isArabic) "الملفات المخفية" else "Hidden Files",
                subtitle = null,
                trailing = if (isArabic) "$hiddenCount ملف" else "$hiddenCount files",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenHidden
            )
            SettingsRow(
                icon = Icons.Rounded.Headphones,
                tint = textPrimary,
                title = if (isArabic) "إعدادات التشغيل والمشغل" else "Playback & Player",
                subtitle = if (isArabic) "8D • شكل القرص والموجات" else "8D • Disc & Waves",
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenPlayerPrefs
            )
            SettingsRow(
                icon = Icons.Rounded.BatteryStd,
                tint = textPrimary,
                title = if (isArabic) "منع توقف الموسيقى" else "Keep Playing in Background",
                subtitle = if (isArabic) "استثناء من توفير البطارية" else "Battery optimization exemption",
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onBatteryOptimization
            )
            SettingsRow(
                icon = Icons.Rounded.Notifications,
                tint = textPrimary,
                title = if (isArabic) "إعدادات الإشعارات" else "Notification Settings",
                subtitle = null,
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onNotificationSettings
            )
        }

        Spacer(Modifier.height(14.dp))

        SettingsCardGroup(cardColor) {
            SettingsRow(
                icon = Icons.Rounded.HelpOutline,
                tint = accent,
                title = if (isArabic) "إعادة تشغيل الجولة التعليمية" else "Restart Guided Tour",
                subtitle = if (isArabic) "شرح جميع أزرار ومميزات التطبيق" else "Walkthrough of app controls",
                trailing = "",
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onRestartTour
            )
            SettingsRow(
                icon = Icons.Rounded.Refresh,
                tint = Color(0xFFFF5252),
                title = if (isArabic) "إعادة ضبط الإعدادات الافتراضية" else "Reset Default Settings",
                subtitle = null,
                trailing = "",
                textPrimary = Color(0xFFFF5252),
                textSecondary = textSecondary,
                onClick = onResetDefaults
            )
            SettingsRow(
                icon = Icons.Rounded.Info,
                tint = textPrimary,
                title = if (isArabic) "حول التطبيق" else "About App",
                subtitle = null,
                trailing = appVersion,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenAbout
            )
        }
    }
}

// ==================== DIALOGS & AUXILIARY UI ====================

@Composable
fun AudioStudioDialog(
    isArabic: Boolean,
    accent: Color,
    spatial8D: Boolean,
    bandGains: List<Float>,
    bassStrength: Int,
    virtStrength: Int,
    loudnessGain: Int,
    speed: Float,
    pitch: Float,
    onToggle8D: (Boolean) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onUpdateBass: (Int) -> Unit,
    onUpdateVirt: (Int) -> Unit,
    onUpdateLoudness: (Int) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdatePitch: (Float) -> Unit,
    onApplyPreset: (List<Float>, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0E131F))
                .border(1.dp, accent.copy(0.4f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Equalizer, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(if (isArabic) "استوديو الصوت الـ 8D والمعادل" else "8D Audio & EQ Studio", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(accent.copy(0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isArabic) "تفعيل الصوت المحيطي 8D" else "Enable 8D Surround", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = spatial8D, onCheckedChange = onToggle8D)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "Phonk Bass" to Triple(listOf(0.75f, 0.45f, 0.05f, 0.28f, 0.50f), 78, 50),
                    "Crystal Clear" to Triple(listOf(0.28f, 0.16f, 0.0f, 0.14f, 0.24f), 35, 30),
                    "Stadium 8D" to Triple(listOf(0.50f, 0.25f, 0.20f, 0.40f, 0.60f), 55, 92),
                    "Flat" to Triple(listOf(0f, 0f, 0f, 0f, 0f), 0, 0)
                ).forEach { (name, cfg) ->
                    AssistChip(
                        onClick = { onApplyPreset(cfg.first, cfg.second, cfg.third) },
                        label = { Text(name, color = Color.White, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            labels.forEachIndexed { idx, hz ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hz, color = Color.White.copy(0.75f), fontSize = 12.sp, modifier = Modifier.width(50.dp))
                    Slider(
                        value = bandGains.getOrElse(idx) { 0f },
                        onValueChange = { onUpdateBand(idx, it) },
                        valueRange = -1f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
            }

            Text("SUB-BASS POWER ($bassStrength%)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Slider(value = bassStrength.toFloat(), onValueChange = { onUpdateBass(it.toInt()) }, valueRange = 0f..100f)

            Text("3D STAGE ($virtStrength%)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Slider(value = virtStrength.toFloat(), onValueChange = { onUpdateVirt(it.toInt()) }, valueRange = 0f..100f)

            Text("EXTRA GAIN ($loudnessGain%)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Slider(value = loudnessGain.toFloat(), onValueChange = { onUpdateLoudness(it.toInt()) }, valueRange = 0f..100f)

            Text(
                String.format(Locale.US, "SPEED %.2fx • PITCH %.2fx", speed, pitch),
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(value = speed, onValueChange = onUpdateSpeed, valueRange = 0.5f..2.0f)
            Slider(value = pitch, onValueChange = onUpdatePitch, valueRange = 0.5f..2.0f)
        }
    }
}

@Composable
fun LyricsDialog(song: Song, isArabic: Boolean, accent: Color, prefs: SharedPreferences, onDismiss: () -> Unit) {
    val key = "lyrics_${song.id}"
    var text by remember { mutableStateOf(VyroSafePrefs.getString(prefs, key, "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "كلمات الأغنية" else "Lyrics", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                placeholder = { Text(if (isArabic) "الصق كلمات الأغنية هنا ليتم حفظها..." else "Paste song lyrics here to save...") }
            )
        },
        confirmButton = {
            Button(onClick = {
                prefs.edit().putString(key, text).apply()
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text(if (isArabic) "حفظ" else "Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}

@Composable
fun CarModeOverlay(
    song: Song,
    accent: Color,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DRIVE & GYM MODE", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(song.artist, color = accent, fontSize = 18.sp)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(80.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(58.dp))
                }
                Box(
                    Modifier.size(110.dp).clip(CircleShape).background(accent).clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(62.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(80.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(58.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun OnboardingTourOverlay(
    isArabic: Boolean,
    step: Int,
    accent: Color,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    BackHandler(enabled = true) { onSkip() }

    val steps = if (isArabic) listOf(
        "أهلاً بك في VYRO MUSIC TITAN 🎧" to "مشغلك الاحترافي السريع بدون أي إعلانات وبصوت نقي ومانع غروشة تلقائياً.",
        "استوديو ويدجت Glassify 🎛️" to "اضغط أيقونة المربعات الذهبية فوق للاختيار من بين 14 شكل ويدجت فاخر للشاشة الرئيسية.",
        "استوديو السمات الحي 🎨" to "اضغط أيقونة الألوان لتغيير شكل عرض أغانيك ولون التطبيق وخلفية AMOLED فوراً.",
        "مركز التحديثات وما الجديد 🚀" to "من الإعدادات افتح مركز التحديثات لعرض «ما الجديد» وتثبيت أي تحديث مباشر فوق القديم."
    ) else listOf(
        "Welcome to VYRO MUSIC TITAN 🎧" to "Fast, ad-free music player with Crystal Audio.",
        "Glassify Widget Studio 🎛️" to "Tap the gold grid icon to pick from 14 premium home screen widgets.",
        "Live Theme Studio 🎨" to "Tap the palette icon to customize layouts, atmosphere, and accent colors.",
        "Update Center & What's New 🚀" to "Open Update Center from Settings to inspect What's New and install updates."
    )
    val (title, body) = steps[step.coerceIn(0, steps.lastIndex)]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101624))
                .border(1.5.dp, accent, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Text("${step + 1} / ${steps.size}", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            Text(body, color = Color.White.copy(0.82f), fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSkip) { Text(if (isArabic) "تخطي" else "Skip", color = Color.White.copy(0.65f)) }
                Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text(
                        if (step < steps.lastIndex) (if (isArabic) "التالي" else "Next") else (if (isArabic) "ابدأ الآن" else "Start"),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==================== REUSABLE UI HELPERS ====================

@Composable
fun SongCoverImage(song: Song, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song.id) {
        bmp = VyroMediaCache.loadSongArt(context, song)?.asImageBitmap()
    }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(accent.copy(0.35f), Color(0xFF131A29)))),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp!!, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(0.75f))
        }
    }
}

@Composable
fun SmartPlaylistCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(iconTint.copy(0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
            Text(subtitle, fontSize = 12.sp, color = textSecondary)
        }
        Icon(Icons.Rounded.ChevronLeft, null, tint = textSecondary)
    }
}

@Composable
fun SubHeaderBar(title: String, textPrimary: Color, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = textPrimary) }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingsCardGroup(cardColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardColor)
            .padding(vertical = 6.dp),
        content = content
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    trailing: String,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textPrimary)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = textSecondary)
            }
        }
        if (trailing.isNotBlank()) {
            Text(trailing, fontSize = 12.sp, color = textSecondary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Rounded.ChevronLeft, null, tint = textSecondary.copy(0.7f), modifier = Modifier.size(18.dp))
    }
}

// ==================== SYSTEM & UTILITY FUNCTIONS ====================

fun pinVyroWidget(context: Context, isArabic: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val mgr = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, VyroWidgetProvider::class.java)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(provider, null, null)
            return
        }
    }
    Toast.makeText(
        context,
        if (isArabic) "اضغط مطولاً على الشاشة الرئيسية ← التطبيقات المصغرة ← VYRO MUSIC" else "Long-press Home Screen → Widgets → VYRO MUSIC",
        Toast.LENGTH_LONG
    ).show()
}

suspend fun scanDeviceSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val list = mutableListOf<Song>()
    val cols = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 8000"
    runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            cols,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val path = cursor.getString(dataIdx) ?: ""
                val folder = File(path).parentFile?.name ?: "Music"
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                list.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleIdx) ?: "Unknown",
                        artist = cursor.getString(artistIdx) ?: "<unknown>",
                        album = cursor.getString(albumIdx) ?: "Unknown",
                        albumId = cursor.getLong(albumIdIdx),
                        duration = cursor.getLong(durIdx),
                        uri = uri,
                        folder = folder,
                        dateAdded = cursor.getLong(dateIdx)
                    )
                )
            }
        }
    }
    list
}

suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("https://api.github.com/repos/mohmedtarek2025-hue/VYRO-MUSIC/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VyroMusicApp/5.0.4")
        }
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return@withContext null
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val obj = JSONObject(text)
        val tag = obj.optString("tag_name", "")
        val name = obj.optString("name", tag)
        val notes = obj.optString("body", "")
        val htmlUrl = obj.optString("html_url", "https://github.com/mohmedtarek2025-hue/VYRO-MUSIC/releases")
        var apkUrl = ""
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val dl = a.optString("browser_download_url", "")
                if (dl.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = dl
                    break
                }
            }
        }
        ReleaseInfo(tag, name, notes, apkUrl, htmlUrl)
    }.getOrNull()
}

fun isNewerVersion(current: String, remoteTag: String): Boolean {
    val cNums = Regex("\\d+").findAll(current).map { it.value.toInt() }.toList()
    val rNums = Regex("\\d+").findAll(remoteTag).map { it.value.toInt() }.toList()
    val max = maxOf(cNums.size, rNums.size)
    for (i in 0 until max) {
        val cv = cNums.getOrElse(i) { 0 }
        val rv = rNums.getOrElse(i) { 0 }
        if (rv > cv) return true
        if (rv < cv) return false
    }
    return false
}

suspend fun downloadAndInstallApk(
    context: Context,
    apkUrl: String,
    fallbackUrl: String,
    onProgress: (Float) -> Unit
) = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "vyro_update.apk")
    val downloaded = runCatching {
        val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
        }
        val total = conn.contentLength.coerceAtLeast(1)
        conn.inputStream.use { input ->
            FileOutputStream(file).use { out ->
                val buf = ByteArray(8192)
                var read: Int
                var sum = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    sum += read
                    withContext(Dispatchers.Main) { onProgress((sum.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
        conn.disconnect()
        true
    }.getOrDefault(false)

    withContext(Dispatchers.Main) {
        if (downloaded && file.exists()) {
            val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }.getOrNull()
                ?: runCatching { FileProvider.getUriForFile(context, "${context.packageName}.provider", file) }.getOrNull()
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                return@withContext
            }
        }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))) }
    }
}

fun openBatteryExemption(context: Context) {
    runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
        } else {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

fun openNotificationSettings(context: Context) {
    runCatching {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(i)
    }
}

fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatHoursMinutes(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}

fun saveIdSet(prefs: SharedPreferences, key: String, set: Set<Long>) {
    prefs.edit().putString(key, JSONArray(set.toList()).toString()).apply()
}

fun saveIdList(prefs: SharedPreferences, key: String, list: List<Long>) {
    prefs.edit().putString(key, JSONArray(list).toString()).apply()
}

fun savePlayCounts(prefs: SharedPreferences, map: Map<Long, Int>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    prefs.edit().putString("play_counts_json", obj.toString()).apply()
}

fun loadPlaylistsSafe(prefs: SharedPreferences): List<CustomPlaylist> {
    val raw = VyroSafePrefs.getString(prefs, "custom_playlists_json", "")
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { i.toString() }
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                val ids = o.optJSONArray("songIds")
                val idList = buildList {
                    if (ids != null) {
                        for (j in 0 until ids.length()) {
                            ids.optString(j).toLongOrNull()?.let { add(it) }
                        }
                    }
                }
                add(CustomPlaylist(id, name, idList))
            }
        }
    }.getOrDefault(emptyList())
}

fun serializePlaylists(list: List<CustomPlaylist>): JSONArray {
    val arr = JSONArray()
    list.forEach { pl ->
        arr.put(JSONObject().apply {
            put("id", pl.id)
            put("name", pl.name)
            put("songIds", JSONArray(pl.songIds))
        })
    }
    return arr
}

fun savePlaylists(prefs: SharedPreferences, list: List<CustomPlaylist>) {
    prefs.edit().putString("custom_playlists_json", serializePlaylists(list).toString()).apply()
}
