package com.mohamedtarek.vyromusic

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val APP_VERSION_NAME = "5.0.0-TITAN"
private const val GITHUB_RELEASES_API = "https://api.github.com/repos/mohmedtarek2025-hue/VYRO-MUSIC/releases/latest"
private const val MANDATORY_UPDATE_DAYS = 10L

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
    val folderName: String
        get() = filePath?.let { File(it).parentFile?.name } ?: "Music"
}

data class CoverPalette(
    val dominant: Color = Color(0xFF00C2FF),
    val secondary: Color = Color(0xFF6B5BFF),
    val deepTint: Color = Color(0xFF070C18)
)

data class LrcLine(val timeMs: Long, val text: String)

data class AccentSwatch(val id: String, val label: String, val color: Color)

data class UpdateInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String
)

data class GlassPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val c1: Color,
    val c2: Color,
    val badge: String
)

private val ACCENT_SWATCHES = listOf(
    AccentSwatch("DYNAMIC", "تلقائي بالغلاف", Color(0xFF7B61FF)),
    AccentSwatch("CYAN", "سماوي TITAN", Color(0xFF00C2FF)),
    AccentSwatch("BLUE", "أزرق ملكي", Color(0xFF2196F3)),
    AccentSwatch("EMERALD", "أخضر الملاعب", Color(0xFF00E676)),
    AccentSwatch("PURPLE", "بنفسجي نيون", Color(0xFFC55FFC)),
    AccentSwatch("CRIMSON", "أحمر كتالوني", Color(0xFFED1B4E)),
    AccentSwatch("GOLD", "ذهبي فاخر", Color(0xFFFFC857))
)

private val LIVE_BACKDROPS = listOf(
    "NONE" to "سادة نقي",
    "DYNAMIC_AURA" to "🎨 هالة الغلاف",
    "STADIUM" to "🏟️ استاد الأبطال",
    "BLAUGRANA" to "🔵🔴 كامب نو",
    "OCEAN" to "🌊 البحر والفقاقيع",
    "NEON" to "⚡ أضواء النيون",
    "MATRIX" to "🟢 مطر الماتريكس"
)

private val GLASS_WALLPAPER_PRESETS = listOf(
    GlassPreset("CHAMPIONS_PITCH", "استاد الأبطال ليلاً", "تخطيط ملعب كرة قدم مع كشافات زجاجية", Color(0xFF00E676), Color(0xFF041C0E), "ملاعب"),
    GlassPreset("BLAUGRANA_NIGHT", "ليالي كامب نو", "تدرج بلوجرانا زجاجي وأضواء مدرجات", Color(0xFFED1B4E), Color(0xFF0A1B47), "كرة قدم"),
    GlassPreset("GLASSIFY_FROST", "زجاج Glassify الداكن", "طبقات زجاجية فاخرة وانعكاسات ضوئية", Color(0xFF00C2FF), Color(0xFF0B1020), "زجاجي"),
    GlassPreset("PHONK_CARBON", "كربون الفونك والنيون", "أضواء سايبر حادة لشاشات AMOLED", Color(0xFFFF3D71), Color(0xFF0D0818), "نيون"),
    GlassPreset("OCEAN_GLASS", "أعماق المحيط الزجاجي", "أمواج شفافة وفقاقيع مضيئة هادئة", Color(0xFF00E5FF), Color(0xFF041B30), "بحري")
)

private val TUTORIAL_STEPS = listOf(
    Triple("مرحباً بك في VYRO MUSIC 5.0", "مشغل موسيقى مصمم بهوية أصلية وصوت نقي فائق. اضغط التالي لنأخذك في جولة سريعة على أهم الأزرار.", Icons.Default.GraphicEq),
    Triple("استوديو السمات بالمعاينة الحية", "من أيقونة الألوان في الأعلى يمكنك تغيير حجم العرض (صغير/وسط/كبير)، الوضع الليلي أو النهاري، وتجربة الألوان مباشرة على أغانيك الحقيقية.", Icons.Default.Palette),
    Triple("استوديو Glassify للموبايل والويدجت", "رتّب شاشة هاتفك الرئيسية وشاشة القفل بخلفيات زجاجية وملاعب كرة قدم بضغطة زر، وأضف ويدجت زجاجي متفاعل مع أغلفة أغانيك.", Icons.Default.Widgets),
    Triple("استوديو الصوت 8D والمشغل", "فعّل الصوت المحيطي 8D، قص أي أغنية كنغمة رنين، اعرض كلمات LRC المتزامنة، وكرر مقاطعك المفضلة بـ A-B Loop.", Icons.Default.Headphones),
    Triple("مركز التحديثات الذكي", "سيتحقق التطبيق تلقائياً من أي إصدار جديد ويحمّله لك من داخل التطبيق مع قائمة كاملة بالمميزات الجديدة.", Icons.Default.SystemUpdate)
)

private object CoverArtCache {
    private val cache = LruCache<Long, Bitmap>(64)
    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bmp: Bitmap) {
        cache.put(id, bmp)
    }
}

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingExternalAudioUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (intent?.action == Intent.ACTION_VIEW) {
            pendingExternalAudioUri = intent.data
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setContent {
                    controller?.let { VyroTitanApp(it, ::loadSongs, pendingExternalAudioUri) }
                }
            } catch (_: Exception) {
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Text("VYRO MUSIC Initializing...", color = Color.White)
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            controller?.let { p ->
                p.setMediaItem(MediaItem.fromUri(intent.data!!))
                p.prepare()
                p.play()
            }
        }
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
                    title = c.getString(titleCol) ?: "Unknown Track",
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

@Composable
fun VyroTitanApp(player: Player, loadSongs: () -> List<Song>, externalUri: Uri?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()

    // الشاشة النشطة: MAIN | NOW_PLAYING | SETTINGS | THEME_STUDIO | GLASSIFY_STUDIO | PLAYBACK_SETTINGS | HIDDEN_FILES | UPDATES
    var currentScreen by remember { mutableStateOf("MAIN") }
    var activeTab by remember { mutableStateOf("Songs") }

    // الجولة التعليمية العائمة عند أول تشغيل (زي الألعاب)
    var tutorialStep by remember { mutableIntStateOf(if (prefs.getBoolean("tutorial_v5_done", false)) -1 else 0) }

    // حالة التحديثات الذكية (فحص تلقائي + مهلة 10 أيام + تحميل داخلي + ما الجديد)
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatusMsg by remember { mutableStateOf<String?>(null) }
    var showUpdatePopup by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var isUpdateMandatory by remember { mutableStateOf(false) }
    var updateDaysLeft by remember { mutableIntStateOf(MANDATORY_UPDATE_DAYS.toInt()) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    // إعدادات المظهر
    var langSetting by remember { mutableStateOf(prefs.getString("lang_setting", "AR") ?: "AR") }
    var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "DARK") ?: "DARK") }
    var viewSize by remember { mutableStateOf(prefs.getString("view_size", "MEDIUM") ?: "MEDIUM") }
    var accentColorId by remember { mutableStateOf(prefs.getString("accent_color_id", "CYAN") ?: "CYAN") }
    var liveBackdrop by remember { mutableStateOf(prefs.getString("live_backdrop", "DYNAMIC_AURA") ?: "DYNAMIC_AURA") }
    var discStyle by remember { mutableStateOf(prefs.getString("disc_style", "Dynamic Card") ?: "Dynamic Card") }
    var waveStyle by remember { mutableStateOf(prefs.getString("wave_style", "Smooth Wave") ?: "Smooth Wave") }
    var widgetStyle by remember { mutableStateOf(prefs.getString("widget_style", "Glassify Frost") ?: "Glassify Frost") }
    var customBgUri by remember { mutableStateOf(prefs.getString("custom_bg_uri", null)) }
    var bgDimAlpha by remember { mutableFloatStateOf(prefs.getFloat("bg_dim_alpha", 0.45f)) }

    val isRtl = remember(langSetting) {
        val code = if (langSetting == "SYSTEM") Locale.getDefault().language.uppercase() else langSetting.uppercase()
        code.startsWith("AR")
    }
    val sysDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> sysDark
    }

    // بيانات المكتبة والتشغيل
    var allSongs by remember { mutableStateOf(emptyList<Song>()) }
    var permission by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableLongStateOf(prefs.getLong("last_song_id", -1L)) }
    var position by remember { mutableLongStateOf(prefs.getLong("last_position_ms", 0L)) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var listenSeconds by remember { mutableLongStateOf(prefs.getLong("stat_listen_seconds", 0L)) }

    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(prefs.getString("sort_by", "TITLE") ?: "TITLE") }
    var showSortMenu by remember { mutableStateOf(false) }

    var likedIds by remember { mutableStateOf(loadIdSet(prefs.getString("liked_ids", "[]"))) }
    var hiddenIds by remember { mutableStateOf(loadIdSet(prefs.getString("hidden_ids", "[]"))) }
    var recentIds by remember { mutableStateOf(loadIdList(prefs.getString("recent_ids", "[]"))) }
    var playCounts by remember { mutableStateOf(loadPlayCounts(prefs.getString("play_counts", "{}"))) }
    var playlists by remember { mutableStateOf(loadPlaylists(prefs.getString("playlists", "{}"))) }
    var customLyrics by remember { mutableStateOf(loadLyricsMap(prefs.getString("lyrics_map", "{}"))) }

    var activeCollectionTitle by remember { mutableStateOf<String?>(null) }
    var activeCollectionSongs by remember { mutableStateOf<List<Song>?>(null) }

    // الصوت والـ DSP (بدون أي خيار لمانع الغروشة لأنه مدمج تلقائياً وإجبارياً في الخلفية)
    var spatial8DEnabled by remember { mutableStateOf(prefs.getBoolean("spatial_8d_enabled", false)) }
    var crossfadeSec by remember { mutableIntStateOf(prefs.getInt("crossfade_sec", 0)) }
    var shakeSkipEnabled by remember { mutableStateOf(prefs.getBoolean("shake_skip_enabled", false)) }
    var eqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Pure Hi-Fi") ?: "Pure Hi-Fi") }
    var bandGains by remember {
        mutableStateOf(loadFloatList(prefs.getString("band_gains", null)) ?: listOf(0.28f, 0.16f, 0.0f, 0.14f, 0.24f))
    }
    var bassStrength by remember { mutableIntStateOf(prefs.getInt("bass_strength", 35)) }
    var virtualizerStrength by remember { mutableIntStateOf(prefs.getInt("virtualizer_strength", 30)) }
    var loudnessGain by remember { mutableIntStateOf(prefs.getInt("loudness_gain", 0)) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("playback_speed", 1f)) }
    var playbackPitch by remember { mutableFloatStateOf(prefs.getFloat("playback_pitch", 1f)) }

    var isDriveMode by remember { mutableStateOf(false) }
    var loopPointA by remember { mutableLongStateOf(-1L) }
    var loopPointB by remember { mutableLongStateOf(-1L) }

    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var songForMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForLyrics by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForRingtone by remember { mutableStateOf<Song?>(null) }
    var songForDeleteConfirm by remember { mutableStateOf<Song?>(null) }

    var coverPalette by remember { mutableStateOf(CoverPalette()) }

    val visibleSongs = remember(allSongs, hiddenIds) {
        allSongs.filterNot { hiddenIds.contains(it.id) }
    }

    fun saveState() {
        prefs.edit()
            .putString("lang_setting", langSetting)
            .putString("theme_mode", themeMode)
            .putString("view_size", viewSize)
            .putString("accent_color_id", accentColorId)
            .putString("live_backdrop", liveBackdrop)
            .putString("disc_style", discStyle)
            .putString("wave_style", waveStyle)
            .putString("widget_style", widgetStyle)
            .putString("custom_bg_uri", customBgUri)
            .putFloat("bg_dim_alpha", bgDimAlpha)
            .putString("sort_by", sortBy)
            .putString("liked_ids", JSONArray(likedIds.toList()).toString())
            .putString("hidden_ids", JSONArray(hiddenIds.toList()).toString())
            .putString("recent_ids", JSONArray(recentIds).toString())
            .putString("play_counts", encodePlayCounts(playCounts))
            .putString("playlists", encodePlaylists(playlists))
            .putString("lyrics_map", encodeLyricsMap(customLyrics))
            .putBoolean("spatial_8d_enabled", spatial8DEnabled)
            .putInt("crossfade_sec", crossfadeSec)
            .putBoolean("shake_skip_enabled", shakeSkipEnabled)
            .putString("eq_preset", eqPreset)
            .putString("band_gains", JSONArray(bandGains).toString())
            .putInt("bass_strength", bassStrength)
            .putInt("virtualizer_strength", virtualizerStrength)
            .putInt("loudness_gain", loudnessGain)
            .putFloat("playback_speed", playbackSpeed)
            .putFloat("playback_pitch", playbackPitch)
            .apply()
        VyroWidgetProvider.updateAllWidgets(context)
    }

    fun resetAllDefaults() {
        themeMode = "DARK"
        viewSize = "MEDIUM"
        accentColorId = "CYAN"
        liveBackdrop = "DYNAMIC_AURA"
        discStyle = "Dynamic Card"
        waveStyle = "Smooth Wave"
        widgetStyle = "Glassify Frost"
        customBgUri = null
        bgDimAlpha = 0.45f
        spatial8DEnabled = false
        crossfadeSec = 0
        shakeSkipEnabled = false
        eqPreset = "Pure Hi-Fi"
        bandGains = listOf(0.28f, 0.16f, 0.0f, 0.14f, 0.24f)
        bassStrength = 35
        virtualizerStrength = 30
        loudnessGain = 0
        playbackSpeed = 1f
        playbackPitch = 1f
        saveState()
        Toast.makeText(context, "تمت إعادة ضبط الإعدادات الافتراضية ✓", Toast.LENGTH_SHORT).show()
    }

    fun triggerApkInstall(file: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "يرجى السماح لتطبيق VYRO بتثبيت التحديثات ثم اضغط تثبيت", Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "تعذر فتح المثبت تلقائياً: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun startInAppUpdateDownload(info: UpdateInfo) {
        val urlStr = info.apkDownloadUrl
        if (urlStr.isNullOrBlank()) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))) }
            return
        }
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        downloadProgress = 0f
        downloadedApkFile = null

        appScope.launch(Dispatchers.IO) {
            try {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(updatesDir, "VYRO_MUSIC_${info.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
                if (outFile.exists()) outFile.delete()

                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "VYRO-MUSIC-Updater")
                }
                conn.connect()
                val totalLen = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { inp ->
                    FileOutputStream(outFile).use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var readSoFar = 0L
                        while (true) {
                            val n = inp.read(buffer)
                            if (n <= 0) break
                            out.write(buffer, 0, n)
                            readSoFar += n
                            val pct = (readSoFar.toFloat() / totalLen.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) { downloadProgress = pct }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    downloadProgress = 1f
                    downloadedApkFile = outFile
                    triggerApkInstall(outFile)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloadingUpdate = false
                    Toast.makeText(context, "فشل التحميل المباشر، تحقق من الاتصال بالإنترنت", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun checkForUpdates(manual: Boolean) {
        if (updateChecking) return
        updateChecking = true
        if (manual) updateStatusMsg = "جاري فحص التحديثات..."
        appScope.launch(Dispatchers.IO) {
            val result = fetchLatestGithubRelease()
            withContext(Dispatchers.Main) {
                updateChecking = false
                if (result != null && isNewerVersion(result.tagName, APP_VERSION_NAME)) {
                    availableUpdate = result
                    val seenTag = prefs.getString("update_seen_tag", null)
                    var firstSeenMs = prefs.getLong("update_first_seen_ms", 0L)
                    val nowMs = System.currentTimeMillis()
                    if (seenTag != result.tagName || firstSeenMs <= 0L) {
                        firstSeenMs = nowMs
                        prefs.edit()
                            .putString("update_seen_tag", result.tagName)
                            .putLong("update_first_seen_ms", firstSeenMs)
                            .apply()
                    }
                    val elapsedDays = ((nowMs - firstSeenMs) / (1000L * 60L * 60L * 24L)).coerceAtLeast(0L)
                    val left = (MANDATORY_UPDATE_DAYS - elapsedDays).toInt().coerceAtLeast(0)
                    updateDaysLeft = left
                    isUpdateMandatory = elapsedDays >= MANDATORY_UPDATE_DAYS
                    updateStatusMsg = "يتوفر تحديث جديد: ${result.tagName}"
                    if (!manual || isUpdateMandatory) {
                        showUpdatePopup = true
                    }
                } else {
                    availableUpdate = null
                    isUpdateMandatory = false
                    if (manual) {
                        updateStatusMsg = "أنت تستخدم أحدث إصدار بالفعل ($APP_VERSION_NAME) ✓"
                        Toast.makeText(context, "تطبيقك محدث لآخر إصدار ✓", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdates(manual = false)
    }

    fun requestPinHomeWidget() {
        runCatching {
            val mgr = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, VyroWidgetProvider::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(context, "اضغط مطولاً على الشاشة الرئيسية واختر ويدجت VYRO Glass Widget", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyGlassWallpaper(preset: GlassPreset, targetFlag: Int) {
        appScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val bmp = generateHighResGlassWallpaper(preset.id)
                val wm = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.setBitmap(bmp, null, true, targetFlag)
                } else {
                    wm.setBitmap(bmp)
                }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (ok) "تم تطبيق التصميم الزجاجي بنجاح ✓" else "تعذر تطبيق الخلفية، جرب مرة أخرى",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                query = spoken
                searchExpanded = true
                activeTab = "Songs"
            }
        }
    }

    val deleteResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            allSongs = loadSongs()
            Toast.makeText(context, "تم حذف الملف", Toast.LENGTH_SHORT).show()
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customBgUri = uri.toString()
            saveState()
            Toast.makeText(context, "تم تعيين الخلفية ✓", Toast.LENGTH_SHORT).show()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", APP_VERSION_NAME)
                    put("liked_ids", JSONArray(likedIds.toList()))
                    put("hidden_ids", JSONArray(hiddenIds.toList()))
                    put("recent_ids", JSONArray(recentIds))
                    put("playlists", JSONObject(encodePlaylists(playlists)))
                    put("lyrics_map", JSONObject(encodeLyricsMap(customLyrics)))
                    put("stat_listen_seconds", listenSeconds)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                Toast.makeText(context, "تم حفظ النسخة الاحتياطية ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "{}"
                val root = JSONObject(text)
                if (root.has("liked_ids")) likedIds = loadIdSet(root.getJSONArray("liked_ids").toString())
                if (root.has("hidden_ids")) hiddenIds = loadIdSet(root.getJSONArray("hidden_ids").toString())
                if (root.has("recent_ids")) recentIds = loadIdList(root.getJSONArray("recent_ids").toString())
                if (root.has("playlists")) playlists = loadPlaylists(root.getJSONObject("playlists").toString())
                if (root.has("lyrics_map")) customLyrics = loadLyricsMap(root.getJSONObject("lyrics_map").toString())
                if (root.has("stat_listen_seconds")) {
                    listenSeconds = root.getLong("stat_listen_seconds")
                    prefs.edit().putLong("stat_listen_seconds", listenSeconds).apply()
                }
                saveState()
                Toast.makeText(context, "تمت الاستعادة بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun buildMediaItem(s: Song): MediaItem {
        val extras = Bundle().apply {
            putLong("album_id", s.albumId)
            putString("song_uri", s.uri.toString())
        }
        return MediaItem.Builder()
            .setMediaId(s.id.toString())
            .setUri(s.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.albumId))
                    .build()
            )
            .build()
    }

    fun playQueue(queue: List<Song>, startIndex: Int, startPosMs: Long = 0L, autoPlay: Boolean = true) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        val chosen = queue[startIndex]
        loopPointA = -1L
        loopPointB = -1L
        if (currentSongId == chosen.id && player.mediaItemCount > 0 && startPosMs == 0L) {
            if (autoPlay) {
                if (player.isPlaying) player.pause() else player.play()
            }
            return
        }
        currentSongId = chosen.id
        recentIds = (listOf(chosen.id) + recentIds.filter { it != chosen.id }).take(60)
        playCounts = playCounts + (chosen.id to ((playCounts[chosen.id] ?: 0) + 1))
        prefs.edit()
            .putLong("last_song_id", chosen.id)
            .putString("last_song_uri", chosen.uri.toString())
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
            allSongs = loaded
            val activeId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (activeId != null) {
                currentSongId = activeId
                position = player.currentPosition.coerceAtLeast(0L)
            } else if (currentSongId != -1L) {
                val idx = loaded.indexOfFirst { it.id == currentSongId }
                if (idx >= 0) playQueue(loaded, idx, prefs.getLong("last_position_ms", 0L), autoPlay = false)
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        if (externalUri != null) {
            player.setMediaItem(MediaItem.fromUri(externalUri))
            player.prepare()
            player.play()
        }
        permission = ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED
        if (permission) {
            val loaded = loadSongs()
            allSongs = loaded
            val liveId = player.currentMediaItem?.mediaId?.toLongOrNull()
            if (liveId != null) {
                currentSongId = liveId
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
                playing = player.isPlaying
            } else if (currentSongId != -1L) {
                val idx = loaded.indexOfFirst { it.id == currentSongId }
                if (idx >= 0) playQueue(loaded, idx, prefs.getLong("last_position_ms", 0L), autoPlay = false)
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

    DisposableEffect(shakeSkipEnabled, player) {
        if (!shakeSkipEnabled) return@DisposableEffect onDispose { }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                val gForce = sqrt(
                    (v[0] / SensorManager.GRAVITY_EARTH) * (v[0] / SensorManager.GRAVITY_EARTH) +
                        (v[1] / SensorManager.GRAVITY_EARTH) * (v[1] / SensorManager.GRAVITY_EARTH) +
                        (v[2] / SensorManager.GRAVITY_EARTH) * (v[2] / SensorManager.GRAVITY_EARTH)
                )
                if (gForce > 2.65f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 1200L && player.isPlaying) {
                        lastShakeTime = now
                        player.seekToNext()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    DisposableEffect(permission) {
        if (!permission) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                allSongs = loadSongs()
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val currentSong = allSongs.firstOrNull { it.id == currentSongId }

    LaunchedEffect(currentSong?.id) {
        val song = currentSong ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val bmp = CoverArtCache.get(song.id)
                ?: VyroWidgetProvider.loadRobustAlbumArt(context, song.uri.toString(), song.albumId)?.also {
                    CoverArtCache.put(song.id, it)
                }
            val extracted = extractCoverPaletteFromBitmap(bmp)
            withContext(Dispatchers.Main) { coverPalette = extracted }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                listenSeconds = prefs.getLong("stat_listen_seconds", listenSeconds)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull()
                if (id != null) {
                    currentSongId = id
                    loopPointA = -1L
                    loopPointB = -1L
                    recentIds = (listOf(id) + recentIds.filter { it != id }).take(60)
                    playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
                    saveState()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, crossfadeSec, loopPointA, loopPointB, spatial8DEnabled) {
        var tick = 0
        var angle8D = 0f
        while (true) {
            val realDur = player.duration
            if (player.isPlaying || player.mediaItemCount > 0) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = if (realDur > 0) realDur else (currentSong?.duration ?: 0L)

                if (loopPointA >= 0L && loopPointB > loopPointA + 1000L && position >= loopPointB) {
                    player.seekTo(loopPointA)
                }

                if (player.isPlaying) {
                    tick++
                    if (tick % 4 == 0) {
                        listenSeconds++
                        if (tick % 20 == 0) {
                            prefs.edit()
                                .putLong("last_song_id", currentSongId)
                                .putLong("last_position_ms", position)
                                .putLong("stat_listen_seconds", listenSeconds)
                                .apply()
                        }
                    }
                }
            }
            if (sleepTimerRemainingSec <= 0) {
                if (spatial8DEnabled && player.isPlaying) {
                    angle8D += 0.16f
                    player.volume = (0.86f + 0.14f * sin(angle8D)).coerceIn(0.72f, 1f)
                } else if (crossfadeSec > 0 && realDur > crossfadeSec * 2500L && player.isPlaying) {
                    val fadeWindow = crossfadeSec * 1000L
                    val remaining = realDur - position
                    player.volume = if (remaining in 1..fadeWindow) {
                        (remaining.toFloat() / fadeWindow.toFloat()).coerceIn(0.25f, 1f)
                    } else 1f
                } else {
                    player.volume = 1f
                }
            }
            delay(250)
        }
    }

    val primaryAccent by animateColorAsState(
        targetValue = if (accentColorId == "DYNAMIC") coverPalette.dominant
        else ACCENT_SWATCHES.firstOrNull { it.id == accentColorId }?.color ?: Color(0xFF00C2FF),
        tween(350),
        label = "accent"
    )

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFF3F6FB)
    val cardColor = if (isDark) Color(0xFF12151E) else Color(0xFFFFFFFF)
    val pillBgColor = if (isDark) Color(0xFF1A1E2A) else Color(0xFFE6EBF4)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF10131A)
    val textSecondary = if (isDark) Color(0xFF8C93A6) else Color(0xFF606878)

    val sortedSongs = remember(visibleSongs, query, sortBy, likedIds) {
        val base = if (sortBy == "FAV") visibleSongs.filter { likedIds.contains(it.id) } else visibleSongs
        val filtered = base.filter {
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
            player.volume = 1f
        } else {
            sleepTimerRemainingSec = minutes * 60
            sleepTimerJob = appScope.launch {
                while (sleepTimerRemainingSec > 0) {
                    delay(1000L)
                    sleepTimerRemainingSec--
                    if (sleepTimerRemainingSec in 1..15) {
                        player.volume = (sleepTimerRemainingSec / 15f).coerceIn(0.08f, 1f)
                    }
                }
                player.pause()
                player.volume = 1f
            }
        }
    }

    // الحوارات
    if (showChangelogDialog && availableUpdate != null) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            containerColor = cardColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, null, tint = primaryAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("ما الجديد في ${availableUpdate!!.tagName}", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        availableUpdate!!.changelog.ifBlank { "تحسينات شاملة في الأداء والواجهة وإصلاح المشكلات." },
                        color = textPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showChangelogDialog = false }) { Text("فهمت", color = primaryAccent, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = cardColor,
            title = { Text("إعادة ضبط الإعدادات", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من أنك تريد إعادة ضبط الإعدادات الافتراضية؟", color = textSecondary) },
            confirmButton = {
                Button(
                    onClick = { resetAllDefaults(); showResetConfirmDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                ) { Text("تأكيد", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("إلغاء", color = textSecondary) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = cardColor,
            title = { Text("لغة التطبيق", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("AR" to "العربية", "EN" to "English", "SYSTEM" to "لغة النظام").forEach { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                langSetting = code; saveState(); showLanguageDialog = false
                            }.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, color = textPrimary, fontWeight = if (langSetting == code) FontWeight.Bold else FontWeight.Normal)
                            if (langSetting == code) Icon(Icons.Default.Check, null, tint = primaryAccent)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("إغلاق", color = primaryAccent) } }
        )
    }

    if (showStatsDialog) {
        val topSong = playCounts.maxByOrNull { it.value }?.key?.let { id -> allSongs.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            containerColor = cardColor,
            title = { Text("إحصائيات الاستماع", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoItem("إجمالي وقت التشغيل", formatListenHours(listenSeconds), textPrimary, textSecondary)
                    InfoItem("عدد مرات التشغيل", "${prefs.getInt("stat_tracks_played", 0)} مرة", textPrimary, textSecondary)
                    InfoItem("إجمالي الأغاني", "${visibleSongs.size} أغنية", textPrimary, textSecondary)
                    InfoItem("الأغاني المفضلة", "${likedIds.size} أغنية", textPrimary, textSecondary)
                    if (topSong != null) {
                        InfoItem("أكثر أغنية استماعاً", "${topSong.title} (${playCounts[topSong.id]} مرة)", textPrimary, textSecondary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatsDialog = false }) { Text("حسناً", color = primaryAccent) } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = cardColor,
            title = { Text("VYRO MUSIC $APP_VERSION_NAME", color = primaryAccent, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تطوير وتصميم: محمد طارق (Mohamed Tarek)", color = textPrimary, fontWeight = FontWeight.Bold)
                    Text("مشغل موسيقى مستقل بتصميم VYRO الأصلي، صوت Hi-Fi نقي تلقائي، 8D محيطي، استوديو سمات حي بأغانيك، واستوديو Glassify للموبايل.", color = textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("تم", color = primaryAccent) } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = cardColor,
            title = { Text("مؤقت النوم التدريجي", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(onClick = { startSleepTimer(mins); showSleepDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("$mins دقيقة (خفض تدريجي ناعم)", color = textPrimary)
                        }
                    }
                    if (sleepTimerRemainingSec > 0) {
                        TextButton(onClick = { startSleepTimer(0); showSleepDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("إيقاف المؤقت", color = Color(0xFFFF5263))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("إغلاق", color = primaryAccent) } }
        )
    }

    songForMenu?.let { target ->
        val isLiked = likedIds.contains(target.id)
        AlertDialog(
            onDismissRequest = { songForMenu = null },
            containerColor = cardColor,
            title = {
                Column {
                    Text(target.title, color = textPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(target.artist, color = primaryAccent, fontSize = 12.sp, maxLines = 1)
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MenuActionRow(Icons.Default.PlayArrow, "تشغيل الآن", primaryAccent) {
                        val idx = visibleSongs.indexOfFirst { it.id == target.id }
                        if (idx >= 0) playQueue(visibleSongs, idx, 0L, true)
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.QueuePlayNext, "تشغيل كالتالي في الطابور", textPrimary) {
                        val nextIdx = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
                        player.addMediaItem(nextIdx, buildMediaItem(target))
                        Toast.makeText(context, "تمت الإضافة للتالي", Toast.LENGTH_SHORT).show()
                        songForMenu = null
                    }
                    MenuActionRow(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isLiked) "إزالة من المفضلة" else "إضافة إلى المفضلة", textPrimary) {
                        likedIds = if (isLiked) likedIds - target.id else likedIds + target.id
                        saveState()
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.PlaylistAdd, "إضافة إلى قائمة تشغيل", textPrimary) {
                        songForAddToPlaylist = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.RingVolume, "قص وتعيين كنغمة رنين", Color(0xFFFFC857)) {
                        songForRingtone = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Lyrics, "كلمات الأغنية (LRC)", textPrimary) {
                        songForLyrics = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.VisibilityOff, "إخفاء هذا الملف من المكتبة", textPrimary) {
                        hiddenIds = hiddenIds + target.id
                        saveState()
                        Toast.makeText(context, "تم نقل الملف إلى الملفات المخفية", Toast.LENGTH_SHORT).show()
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Share, "مشاركة الملف الصوتي", textPrimary) {
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
                    MenuActionRow(Icons.Default.Info, "معلومات الملف", textPrimary) {
                        songForInfo = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.DeleteForever, "حذف نهائي من الجهاز", Color(0xFFFF5263)) {
                        songForDeleteConfirm = target
                        songForMenu = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForMenu = null }) { Text("إغلاق", color = primaryAccent) } }
        )
    }

    songForRingtone?.let { target ->
        val safeTotal = (target.duration / 1000L).coerceAtLeast(6L).toFloat()
        var startSec by remember { mutableFloatStateOf(0f) }
        var endSec by remember { mutableFloatStateOf(minOf(30f, safeTotal)) }
        var cutting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!cutting) songForRingtone = null },
            containerColor = cardColor,
            title = { Text("قص وتعيين كنغمة رنين", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(target.title, color = primaryAccent, maxLines = 1)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            Text("${formatTime((startSec * 1000).toLong())}  →  ${formatTime((endSec * 1000).toLong())}", color = textPrimary, fontSize = 12.sp)
                            Slider(value = startSec, onValueChange = { startSec = it.coerceIn(0f, (endSec - 2f).coerceAtLeast(0f)) }, valueRange = 0f..safeTotal)
                            Slider(value = endSec, onValueChange = { endSec = it.coerceIn((startSec + 2f).coerceAtMost(safeTotal), safeTotal) }, valueRange = 0f..safeTotal)
                        }
                    }
                    Button(
                        onClick = {
                            if (!Settings.System.canWrite(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } else {
                                cutting = true
                                appScope.launch {
                                    withContext(Dispatchers.IO) { cutAndSetRingtone(context, target, (startSec * 1000).toLong(), (endSec * 1000).toLong()) }
                                    cutting = false
                                    Toast.makeText(context, "تم تعيين النغمة بنجاح ✓", Toast.LENGTH_SHORT).show()
                                    songForRingtone = null
                                }
                            }
                        },
                        enabled = !cutting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Text(if (cutting) "جاري القص..." else "قص وتعيين النغمة", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForRingtone = null }) { Text("إغلاق", color = textSecondary) } }
        )
    }

    songForInfo?.let { target ->
        val mb = "%.2f MB".format(target.sizeBytes / (1024f * 1024f))
        AlertDialog(
            onDismissRequest = { songForInfo = null },
            containerColor = cardColor,
            title = { Text("معلومات الملف", color = primaryAccent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoItem("الأغنية", target.title, textPrimary, textSecondary)
                    InfoItem("الفنان", target.artist, textPrimary, textSecondary)
                    InfoItem("الألبوم", target.album, textPrimary, textSecondary)
                    InfoItem("المجلد", target.folderName, textPrimary, textSecondary)
                    InfoItem("المدة والحجم", "${formatTime(target.duration)} • $mb", textPrimary, textSecondary)
                    InfoItem("مرات الاستماع", "${playCounts[target.id] ?: 0}", textPrimary, textSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { songForInfo = null }) { Text("حسناً", color = primaryAccent) } }
        )
    }

    songForDeleteConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { songForDeleteConfirm = null },
            containerColor = cardColor,
            title = { Text("حذف من الجهاز", color = Color(0xFFFF5263), fontWeight = FontWeight.Bold) },
            text = { Text(target.title, color = textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(target.uri))
                            deleteResultLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        } else {
                            context.contentResolver.delete(target.uri, null, null)
                            allSongs = loadSongs()
                        }
                    }
                    songForDeleteConfirm = null
                }) { Text("تأكيد الحذف", color = Color(0xFFFF5263), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { songForDeleteConfirm = null }) { Text("إلغاء", color = textSecondary) } }
        )
    }

    songForAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForAddToPlaylist = null },
            containerColor = cardColor,
            title = { Text("إضافة إلى قائمة تشغيل", color = textPrimary) },
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
                                Toast.makeText(context, "تمت الإضافة إلى $pName", Toast.LENGTH_SHORT).show()
                                songForAddToPlaylist = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(pName, color = textPrimary) }
                    }
                    OutlinedButton(onClick = { songForAddToPlaylist = null; showCreatePlaylistDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("+ إنشاء قائمة جديدة", color = primaryAccent)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForAddToPlaylist = null }) { Text("إغلاق") } }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = cardColor,
            title = { Text("قائمة تشغيل جديدة", color = textPrimary) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("تراكات الجيم / فونك") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        playlists = playlists + (trimmed to (playlists[trimmed] ?: emptyList()))
                        saveState()
                        showCreatePlaylistDialog = false
                    }
                }) { Text("إنشاء", color = primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("إلغاء") } }
        )
    }

    songForLyrics?.let { targetSong ->
        val rawLyrics = remember(targetSong.id, customLyrics) {
            customLyrics[targetSong.id] ?: readRawSidecarLyrics(targetSong.filePath) ?: ""
        }
        val parsedLrc = remember(rawLyrics) { parseLrcLines(rawLyrics) }
        var editingMode by remember { mutableStateOf(rawLyrics.isBlank()) }
        var draftText by remember { mutableStateOf(rawLyrics) }

        AlertDialog(
            onDismissRequest = { songForLyrics = null },
            containerColor = cardColor,
            title = { Text("كلمات الأغنية • ${targetSong.title}", color = textPrimary, fontSize = 15.sp, maxLines = 1) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingMode) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("[00:12.00] الصق الكلمات هنا...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else if (parsedLrc.isNotEmpty()) {
                        val activeIdx = parsedLrc.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
                        parsedLrc.forEachIndexed { idx, line ->
                            val isCurrent = idx == activeIdx
                            Text(
                                text = line.text,
                                color = if (isCurrent) primaryAccent else textSecondary,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = if (isCurrent) 16.sp else 13.sp,
                                modifier = Modifier.fillMaxWidth().clickable { player.seekTo(line.timeMs) }.padding(vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(draftText.ifBlank { "لا توجد كلمات محفوظة بعد." }, color = textPrimary, lineHeight = 22.sp)
                    }
                }
            },
            confirmButton = {
                if (editingMode) {
                    TextButton(onClick = {
                        customLyrics = customLyrics + (targetSong.id to draftText.trim())
                        saveState()
                        editingMode = false
                    }) { Text("حفظ", color = primaryAccent) }
                } else {
                    TextButton(onClick = { editingMode = true }) { Text("تعديل", color = primaryAccent) }
                }
            },
            dismissButton = { TextButton(onClick = { songForLyrics = null }) { Text("إغلاق") } }
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(background = bgColor, surface = cardColor, primary = primaryAccent, onBackground = textPrimary, onSurface = textPrimary)
    } else {
        lightColorScheme(background = bgColor, surface = cardColor, primary = primaryAccent, onBackground = textPrimary, onSurface = textPrimary)
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colorScheme) {
            Box(Modifier.fillMaxSize().background(bgColor)) {
                // الخلفية الحية الاختيارية
                if (liveBackdrop != "NONE" && currentScreen != "SETTINGS") {
                    AnimatedThemeBackdrop(liveBackdrop, primaryAccent, coverPalette.secondary, playing)
                }

                if (!customBgUri.isNullOrBlank() && currentScreen != "SETTINGS") {
                    AsyncImage(model = Uri.parse(customBgUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDimAlpha.coerceIn(0.15f, 0.85f))))
                }

                // قفل التطبيق الإجباري في حال مرور 10 أيام بدون تحديث
                if (isUpdateMandatory && availableUpdate != null) {
                    TitanUpdateDownloadScreen(
                        info = availableUpdate!!,
                        isMandatory = true,
                        daysLeft = 0,
                        isDownloading = isDownloadingUpdate,
                        progress = downloadProgress,
                        downloadedFile = downloadedApkFile,
                        accent = primaryAccent,
                        bgColor = bgColor,
                        cardColor = cardColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onBack = null,
                        onShowChangelog = { showChangelogDialog = true },
                        onDownload = { startInAppUpdateDownload(availableUpdate!!) },
                        onInstall = { downloadedApkFile?.let { triggerApkInstall(it) } }
                    )
                } else {
                    when {
                        isDriveMode && currentSong != null -> {
                            DriveGymModeScreen(
                                song = currentSong,
                                playing = playing,
                                position = position,
                                duration = duration.coerceAtLeast(currentSong.duration),
                                accent = primaryAccent,
                                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                                onNext = { player.seekToNext() },
                                onPrev = { player.seekToPrevious() },
                                onExit = { isDriveMode = false }
                            )
                        }

                        currentScreen == "UPDATES" -> {
                            TitanUpdatesCenterScreen(
                                availableUpdate = availableUpdate,
                                checking = updateChecking,
                                statusMsg = updateStatusMsg,
                                daysLeft = updateDaysLeft,
                                isDownloading = isDownloadingUpdate,
                                progress = downloadProgress,
                                downloadedFile = downloadedApkFile,
                                accent = primaryAccent,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onBack = { currentScreen = "SETTINGS" },
                                onCheckNow = { checkForUpdates(manual = true) },
                                onShowChangelog = { showChangelogDialog = true },
                                onDownload = { availableUpdate?.let { startInAppUpdateDownload(it) } },
                                onInstall = { downloadedApkFile?.let { triggerApkInstall(it) } }
                            )
                        }

                        currentScreen == "THEME_STUDIO" -> {
                            TitanLiveThemeStudioScreen(
                                sampleSongs = visibleSongs.take(6),
                                viewSize = viewSize,
                                themeMode = themeMode,
                                accentColorId = accentColorId,
                                liveBackdrop = liveBackdrop,
                                accent = primaryAccent,
                                isDark = isDark,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                pillBgColor = pillBgColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                hasCustomWallpaper = !customBgUri.isNullOrBlank(),
                                onBack = { currentScreen = "MAIN" },
                                onSelectSize = { viewSize = it; saveState() },
                                onSelectMode = { themeMode = it; saveState() },
                                onSelectAccent = { accentColorId = it; saveState() },
                                onSelectBackdrop = {
                                    liveBackdrop = if (liveBackdrop == it) "NONE" else it
                                    saveState()
                                },
                                onPickWallpaper = { bgImagePicker.launch(arrayOf("image/*")) },
                                onClearWallpaper = { customBgUri = null; saveState() },
                                onApplyAndClose = {
                                    saveState()
                                    currentScreen = "MAIN"
                                }
                            )
                        }

                        currentScreen == "GLASSIFY_STUDIO" -> {
                            TitanGlassifyStudioScreen(
                                currentSong = currentSong,
                                widgetStyle = widgetStyle,
                                accent = primaryAccent,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onBack = { currentScreen = "MAIN" },
                                onSelectWidgetStyle = { widgetStyle = it; saveState() },
                                onPinWidget = { requestPinHomeWidget() },
                                onApplyWallpaper = { preset, flag -> applyGlassWallpaper(preset, flag) }
                            )
                        }

                        currentScreen == "HIDDEN_FILES" -> {
                            val hiddenList = allSongs.filter { hiddenIds.contains(it.id) }
                            TitanHiddenFilesScreen(
                                hiddenSongs = hiddenList,
                                accent = primaryAccent,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onBack = { currentScreen = "SETTINGS" },
                                onUnhide = { id -> hiddenIds = hiddenIds - id; saveState() },
                                onUnhideAll = { hiddenIds = emptySet(); saveState() }
                            )
                        }

                        currentScreen == "PLAYBACK_SETTINGS" -> {
                            TitanPlaybackSettingsScreen(
                                spatial8D = spatial8DEnabled,
                                shakeSkip = shakeSkipEnabled,
                                crossfadeSec = crossfadeSec,
                                discStyle = discStyle,
                                waveStyle = waveStyle,
                                accent = primaryAccent,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onBack = { currentScreen = "SETTINGS" },
                                onToggle8D = { spatial8DEnabled = !spatial8DEnabled; saveState() },
                                onToggleShake = { shakeSkipEnabled = !shakeSkipEnabled; saveState() },
                                onChangeCrossfade = { crossfadeSec = it; saveState() },
                                onSelectDisc = { discStyle = it; saveState() },
                                onSelectWave = { waveStyle = it; saveState() }
                            )
                        }

                        currentScreen == "SETTINGS" -> {
                            TitanSettingsScreen(
                                listenSeconds = listenSeconds,
                                viewSize = viewSize,
                                liveBackdrop = liveBackdrop,
                                langSetting = langSetting,
                                sleepRemainingSec = sleepTimerRemainingSec,
                                hiddenCount = hiddenIds.size,
                                widgetStyle = widgetStyle,
                                hasUpdate = availableUpdate != null,
                                updateTag = availableUpdate?.tagName,
                                accent = primaryAccent,
                                bgColor = bgColor,
                                cardColor = cardColor,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                onBack = { currentScreen = "MAIN" },
                                onOpenStats = { showStatsDialog = true },
                                onOpenUpdates = { currentScreen = "UPDATES" },
                                onBackup = { backupLauncher.launch("vyro_titan_backup.json") },
                                onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                                onOpenThemeStudio = { currentScreen = "THEME_STUDIO" },
                                onOpenGlassify = { currentScreen = "GLASSIFY_STUDIO" },
                                onOpenLanguage = { showLanguageDialog = true },
                                onOpenSleep = { showSleepDialog = true },
                                onOpenHiddenFiles = { currentScreen = "HIDDEN_FILES" },
                                onOpenPlaybackSettings = { currentScreen = "PLAYBACK_SETTINGS" },
                                onPreventStop = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }.onFailure {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                            )
                                        }
                                    }
                                },
                                onOpenNotifications = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                        )
                                    }
                                },
                                onReplayTutorial = {
                                    currentScreen = "MAIN"
                                    tutorialStep = 0
                                },
                                onRequestReset = { showResetConfirmDialog = true },
                                onOpenAbout = { showAboutDialog = true }
                            )
                        }

                        currentScreen == "NOW_PLAYING" && currentSong != null -> {
                            TitanNowPlayingScreen(
                                song = currentSong,
                                playing = playing,
                                position = position,
                                duration = duration.coerceAtLeast(currentSong.duration),
                                isLiked = likedIds.contains(currentSong.id),
                                shuffle = shuffle,
                                repeatMode = repeatMode,
                                sleepRemainingSec = sleepTimerRemainingSec,
                                discStyle = discStyle,
                                waveStyle = waveStyle,
                                spatial8D = spatial8DEnabled,
                                loopA = loopPointA,
                                loopB = loopPointB,
                                accent = coverPalette.dominant,
                                palette = coverPalette,
                                onBack = { currentScreen = "MAIN" },
                                onTogglePlay = {
                                    if (player.mediaItemCount == 0) {
                                        val idx = visibleSongs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                        playQueue(visibleSongs, idx, position, true)
                                    } else if (player.isPlaying) player.pause() else player.play()
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
                                onToggleShuffle = { shuffle = !shuffle; player.shuffleModeEnabled = shuffle },
                                onCycleRepeat = {
                                    repeatMode = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                    player.repeatMode = repeatMode
                                },
                                onToggle8D = { spatial8DEnabled = !spatial8DEnabled; saveState() },
                                onToggleABLoop = {
                                    when {
                                        loopPointA < 0L -> {
                                            loopPointA = position
                                            Toast.makeText(context, "نقطة A: ${formatTime(position)}", Toast.LENGTH_SHORT).show()
                                        }
                                        loopPointB < 0L && position > loopPointA + 1000L -> {
                                            loopPointB = position
                                            Toast.makeText(context, "تم تفعيل تكرار A-B", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> { loopPointA = -1L; loopPointB = -1L }
                                    }
                                },
                                onOpenDriveMode = { isDriveMode = true },
                                onOpenSleep = { showSleepDialog = true },
                                onOpenLyrics = { songForLyrics = currentSong },
                                onOpenEq = { activeTab = "EQ"; currentScreen = "MAIN" },
                                onOpenMenu = { songForMenu = currentSong }
                            )
                        }

                        else -> {
                            // الواجهة الرئيسية الأصلية لـ VYRO MUSIC 5.0 TITAN
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (currentSong != null) {
                                        TitanFloatingMiniDock(
                                            song = currentSong,
                                            playing = playing,
                                            position = position,
                                            duration = duration,
                                            accent = primaryAccent,
                                            isDark = isDark,
                                            onTogglePlay = {
                                                if (player.mediaItemCount == 0) {
                                                    val idx = visibleSongs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                                    playQueue(visibleSongs, idx, position, true)
                                                } else if (player.isPlaying) player.pause() else player.play()
                                            },
                                            onPrev = { player.seekToPrevious() },
                                            onNext = { player.seekToNext() },
                                            onOpenFullPlayer = { currentScreen = "NOW_PLAYING" }
                                        )
                                    }
                                }
                            ) { padding ->
                                Column(Modifier.fillMaxSize().padding(padding)) {
                                    // 1. شريط VYRO العلوي بهوية مميزة مستقلة
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(Brush.linearGradient(listOf(primaryAccent, coverPalette.secondary))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.GraphicEq, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("VYRO", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                                Spacer(Modifier.width(5.dp))
                                                Box(
                                                    Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(primaryAccent.copy(alpha = 0.16f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("TITAN 5.0", color = primaryAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                            Text("${visibleSongs.size} مقطع • ${formatListenHours(listenSeconds)}", color = textSecondary, fontSize = 10.5.sp)
                                        }

                                        IconButton(onClick = { searchExpanded = !searchExpanded }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Search, "Search", tint = if (searchExpanded) primaryAccent else textPrimary)
                                        }
                                        Box {
                                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Sort, "Sort", tint = textPrimary)
                                            }
                                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                                listOf("TITLE" to "الاسم (أ-ي)", "ARTIST" to "الفنان", "NEWEST" to "الأحدث", "DURATION" to "الأطول", "FAV" to "المفضلة").forEach { (k, l) ->
                                                    DropdownMenuItem(
                                                        text = { Text(l, color = if (sortBy == k) primaryAccent else textPrimary) },
                                                        onClick = { sortBy = k; saveState(); showSortMenu = false }
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = { currentScreen = "GLASSIFY_STUDIO" }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Widgets, "Glassify", tint = Color(0xFFFFC857))
                                        }
                                        IconButton(onClick = { currentScreen = "THEME_STUDIO" }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Palette, "Theme", tint = primaryAccent)
                                        }
                                        Box {
                                            IconButton(onClick = { currentScreen = "SETTINGS" }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Settings, "Settings", tint = textPrimary)
                                            }
                                            if (availableUpdate != null) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(9.dp)
                                                        .background(Color(0xFFFF3D57), CircleShape)
                                                )
                                            }
                                        }
                                    }

                                    // 2. صندوق إشعار التحديث الذكي (يظهر تلقائياً لو فيه إصدار أحدث على GitHub)
                                    AnimatedVisibility(visible = availableUpdate != null && showUpdatePopup) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(primaryAccent.copy(alpha = 0.16f))
                                                .border(1.dp, primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                                .clickable { currentScreen = "UPDATES" }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.SystemUpdate, null, tint = primaryAccent)
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("تحديث جديد متاح (${availableUpdate?.tagName})", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("متبقي $updateDaysLeft أيام • اضغط لعرض ما الجديد والتنزيل", color = textSecondary, fontSize = 11.sp)
                                            }
                                            IconButton(onClick = { showUpdatePopup = false }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // 3. شريط البحث
                                    AnimatedVisibility(visible = searchExpanded || query.isNotEmpty()) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(cardColor)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = query,
                                                onValueChange = { query = it },
                                                modifier = Modifier.weight(1f),
                                                placeholder = { Text("ابحث عن أغنية أو فنان...", color = textSecondary, fontSize = 13.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            IconButton(onClick = {
                                                runCatching {
                                                    voiceSearchLauncher.launch(
                                                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        }
                                                    )
                                                }
                                            }) { Icon(Icons.Default.Mic, null, tint = primaryAccent) }
                                            IconButton(onClick = { query = ""; searchExpanded = false }) {
                                                Icon(Icons.Default.Close, null, tint = textSecondary)
                                            }
                                        }
                                    }

                                    // 4. شريط التنقل الزجاجي المميز لـ VYRO
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "Songs" to "المكتبة",
                                            "Playlists" to "القوائم",
                                            "Folders" to "المجلدات",
                                            "Artists" to "الفنانون",
                                            "Albums" to "الألبومات",
                                            "EQ" to "الصوت 8D"
                                        ).forEach { (id, label) ->
                                            val selected = activeTab == id
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(if (selected) primaryAccent else pillBgColor)
                                                    .clickable {
                                                        activeTab = id
                                                        activeCollectionTitle = null
                                                        activeCollectionSongs = null
                                                    }
                                                    .padding(horizontal = 15.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    label,
                                                    color = if (selected) Color.Black else textSecondary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // 5. محتوى المكتبة
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        if (!permission) {
                                            EmptyState("يرجى منح إذن الوصول للملفات الصوتية لعرض مكتبتك", primaryAccent, textPrimary)
                                        } else if (activeCollectionSongs != null && activeCollectionTitle != null) {
                                            val list = activeCollectionSongs!!
                                            Column(Modifier.fillMaxSize()) {
                                                Row(
                                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(onClick = { activeCollectionSongs = null; activeCollectionTitle = null }) {
                                                        Icon(Icons.Default.ArrowForward, null, tint = textPrimary)
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text(activeCollectionTitle!!, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
                                                        Text("${list.size} أغنية", color = textSecondary, fontSize = 12.sp)
                                                    }
                                                    Button(
                                                        onClick = { if (list.isNotEmpty()) playQueue(list, 0, 0L, true) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                                        shape = RoundedCornerShape(50)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("تشغيل", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                SongListOrGrid(
                                                    songs = list,
                                                    viewSize = viewSize,
                                                    currentSongId = currentSongId,
                                                    isPlaying = playing,
                                                    likedIds = likedIds,
                                                    accent = primaryAccent,
                                                    textPrimary = textPrimary,
                                                    textSecondary = textSecondary,
                                                    cardColor = cardColor,
                                                    onPlayIndex = { idx -> playQueue(list, idx, 0L, true) },
                                                    onMore = { songForMenu = it }
                                                )
                                            }
                                        } else {
                                            when (activeTab) {
                                                "Songs" -> {
                                                    if (sortedSongs.isEmpty()) {
                                                        EmptyState("لا توجد أغاني مطابقة", primaryAccent, textPrimary)
                                                    } else {
                                                        SongListOrGrid(
                                                            songs = sortedSongs,
                                                            viewSize = viewSize,
                                                            currentSongId = currentSongId,
                                                            isPlaying = playing,
                                                            likedIds = likedIds,
                                                            accent = primaryAccent,
                                                            textPrimary = textPrimary,
                                                            textSecondary = textSecondary,
                                                            cardColor = cardColor,
                                                            onPlayIndex = { idx -> playQueue(sortedSongs, idx, 0L, true) },
                                                            onMore = { songForMenu = it }
                                                        )
                                                    }
                                                }

                                                "Playlists" -> {
                                                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        item {
                                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Text("قوائمك الذكية", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                                TextButton(onClick = { showCreatePlaylistDialog = true }) {
                                                                    Text("+ قائمة جديدة", color = primaryAccent, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                        item {
                                                            CollectionTile("الأغاني المفضلة", "${likedIds.size} أغنية", Icons.Default.Favorite, Color(0xFFFF5263), cardColor, textPrimary, textSecondary) {
                                                                activeCollectionTitle = "الأغاني المفضلة"
                                                                activeCollectionSongs = visibleSongs.filter { likedIds.contains(it.id) }
                                                            }
                                                        }
                                                        item {
                                                            CollectionTile("تم تشغيلها مؤخراً", "${recentIds.size} أغنية", Icons.Default.History, primaryAccent, cardColor, textPrimary, textSecondary) {
                                                                activeCollectionTitle = "تم تشغيلها مؤخراً"
                                                                activeCollectionSongs = recentIds.mapNotNull { id -> visibleSongs.firstOrNull { it.id == id } }
                                                            }
                                                        }
                                                        item {
                                                            CollectionTile("الأكثر استماعاً", "${playCounts.size} أغنية", Icons.Default.Star, Color(0xFFFFC857), cardColor, textPrimary, textSecondary) {
                                                                activeCollectionTitle = "الأكثر استماعاً"
                                                                activeCollectionSongs = visibleSongs.filter { (playCounts[it.id] ?: 0) > 0 }.sortedByDescending { playCounts[it.id] ?: 0 }
                                                            }
                                                        }
                                                        itemsIndexed(playlists.entries.toList()) { _, entry ->
                                                            Row(
                                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).clickable {
                                                                    activeCollectionTitle = entry.key
                                                                    activeCollectionSongs = entry.value.mapNotNull { id -> visibleSongs.firstOrNull { it.id == id } }
                                                                }.padding(14.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.QueueMusic, null, tint = primaryAccent)
                                                                Spacer(Modifier.width(12.dp))
                                                                Column(Modifier.weight(1f)) {
                                                                    Text(entry.key, color = textPrimary, fontWeight = FontWeight.Bold)
                                                                    Text("${entry.value.size} أغنية", color = textSecondary, fontSize = 12.sp)
                                                                }
                                                                IconButton(onClick = { playlists = playlists - entry.key; saveState() }) {
                                                                    Icon(Icons.Default.Delete, null, tint = textSecondary)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                "Folders" -> {
                                                    val folders = remember(visibleSongs) { visibleSongs.groupBy { it.folderName }.entries.sortedBy { it.key.lowercase() } }
                                                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        itemsIndexed(folders) { _, entry ->
                                                            CollectionTile(entry.key, "${entry.value.size} ملف صوتي", Icons.Default.Folder, primaryAccent, cardColor, textPrimary, textSecondary) {
                                                                activeCollectionTitle = entry.key
                                                                activeCollectionSongs = entry.value
                                                            }
                                                        }
                                                    }
                                                }

                                                "Artists" -> {
                                                    val artists = remember(visibleSongs) { visibleSongs.groupBy { it.artist }.entries.sortedBy { it.key.lowercase() } }
                                                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        itemsIndexed(artists) { _, entry ->
                                                            Row(
                                                                Modifier.fillMaxWidth().clickable {
                                                                    activeCollectionTitle = entry.key
                                                                    activeCollectionSongs = entry.value
                                                                }.padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                SongCoverArt(song = entry.value.firstOrNull(), sizeDp = 54, isCircle = true, accent = primaryAccent)
                                                                Spacer(Modifier.width(14.dp))
                                                                Column(Modifier.weight(1f)) {
                                                                    Text(entry.key, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    Text("${entry.value.size} أغنية", color = textSecondary, fontSize = 12.sp)
                                                                }
                                                                Icon(Icons.Default.ChevronLeft, null, tint = textSecondary)
                                                            }
                                                        }
                                                    }
                                                }

                                                "Albums" -> {
                                                    val albums = remember(visibleSongs) { visibleSongs.groupBy { it.album }.entries.sortedBy { it.key.lowercase() } }
                                                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        itemsIndexed(albums) { _, entry ->
                                                            Row(
                                                                Modifier.fillMaxWidth().clickable {
                                                                    activeCollectionTitle = entry.key
                                                                    activeCollectionSongs = entry.value
                                                                }.padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                SongCoverArt(song = entry.value.firstOrNull(), sizeDp = 56, isCircle = false, accent = primaryAccent)
                                                                Spacer(Modifier.width(14.dp))
                                                                Column(Modifier.weight(1f)) {
                                                                    Text(entry.key, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    Text("${entry.value.size} أغنية", color = textSecondary, fontSize = 12.sp)
                                                                }
                                                                Icon(Icons.Default.ChevronLeft, null, tint = textSecondary)
                                                            }
                                                        }
                                                    }
                                                }

                                                "EQ" -> {
                                                    TitanDspStudioTab(
                                                        eqPreset = eqPreset,
                                                        bandGains = bandGains,
                                                        bassStrength = bassStrength,
                                                        virtualizerStrength = virtualizerStrength,
                                                        loudnessGain = loudnessGain,
                                                        playbackSpeed = playbackSpeed,
                                                        playbackPitch = playbackPitch,
                                                        spatial8D = spatial8DEnabled,
                                                        accent = primaryAccent,
                                                        cardColor = cardColor,
                                                        textPrimary = textPrimary,
                                                        textSecondary = textSecondary,
                                                        onSelectPreset = { preset ->
                                                            eqPreset = preset
                                                            when (preset) {
                                                                "Pure Hi-Fi" -> { bandGains = listOf(0.28f, 0.16f, 0.0f, 0.14f, 0.24f); bassStrength = 35; loudnessGain = 0 }
                                                                "Clean Phonk" -> { bandGains = listOf(0.60f, 0.35f, -0.08f, 0.32f, 0.50f); bassStrength = 52; loudnessGain = 10 }
                                                                "Gym Beast" -> { bandGains = listOf(0.70f, 0.45f, 0.05f, 0.38f, 0.55f); bassStrength = 60; loudnessGain = 20 }
                                                                "Vocal Studio" -> { bandGains = listOf(-0.10f, 0.20f, 0.60f, 0.45f, 0.22f); bassStrength = 20; loudnessGain = 0 }
                                                                else -> { bandGains = listOf(0f, 0f, 0f, 0f, 0f); bassStrength = 0; loudnessGain = 0 }
                                                            }
                                                            saveState()
                                                        },
                                                        onBandChange = { idx, v -> eqPreset = "Custom"; bandGains = bandGains.toMutableList().also { it[idx] = v }; saveState() },
                                                        onBassChange = { bassStrength = it; saveState() },
                                                        onVirtChange = { virtualizerStrength = it; saveState() },
                                                        onLoudnessChange = { loudnessGain = it; saveState() },
                                                        onSpeedChange = { playbackSpeed = it; saveState() },
                                                        onPitchChange = { playbackPitch = it; saveState() },
                                                        onToggle8D = { spatial8DEnabled = !spatial8DEnabled; saveState() },
                                                        onOpenSystemEq = {
                                                            runCatching {
                                                                context.startActivity(
                                                                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                                                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                                                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // الجولة التعليمية العائمة التفاعلية (مثل الألعاب عند أول تشغيل)
                    if (tutorialStep in TUTORIAL_STEPS.indices && currentScreen == "MAIN") {
                        val (tTitle, tDesc, tIcon) = TUTORIAL_STEPS[tutorialStep]
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.78f))
                                .clickable {
                                    if (tutorialStep < TUTORIAL_STEPS.lastIndex) tutorialStep++
                                    else {
                                        tutorialStep = -1
                                        prefs.edit().putBoolean("tutorial_v5_done", true).apply()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth(0.88f)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFF121624))
                                    .border(1.5.dp, primaryAccent, RoundedCornerShape(28.dp))
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(58.dp).clip(CircleShape).background(primaryAccent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tIcon, null, tint = primaryAccent, modifier = Modifier.size(30.dp))
                                }
                                Text("${tutorialStep + 1} / ${TUTORIAL_STEPS.size}", color = primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(tTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                Text(tDesc, color = Color(0xFFB5BCD2), fontSize = 13.5.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        tutorialStep = -1
                                        prefs.edit().putBoolean("tutorial_v5_done", true).apply()
                                    }) {
                                        Text("تخطي الجولة", color = Color(0xFF8C93A6))
                                    }
                                    Button(
                                        onClick = {
                                            if (tutorialStep < TUTORIAL_STEPS.lastIndex) tutorialStep++
                                            else {
                                                tutorialStep = -1
                                                prefs.edit().putBoolean("tutorial_v5_done", true).apply()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            if (tutorialStep == TUTORIAL_STEPS.lastIndex) "ابدأ الآن" else "التالي",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black
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

// ==========================================================
// 1. مكون عرض الصور المُصلَح بالكامل (يتحدث فوراً مع كل أغنية!)
// ==========================================================
@Composable
private fun SongCoverArt(
    song: Song?,
    sizeDp: Int,
    isCircle: Boolean = false,
    accent: Color,
    isActivePlaying: Boolean = false,
    cornerDp: Int = 12
) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = song?.id) {
        if (song == null) {
            value = null
            return@produceState
        }
        val cached = CoverArtCache.get(song.id)
        if (cached != null) {
            value = cached.asImageBitmap()
        } else {
            value = null
            val loaded = withContext(Dispatchers.IO) {
                VyroWidgetProvider.loadRobustAlbumArt(context, song.uri.toString(), song.albumId)?.also {
                    CoverArtCache.put(song.id, it)
                }
            }
            value = loaded?.asImageBitmap()
        }
    }

    val shape = if (isCircle) CircleShape else RoundedCornerShape(cornerDp.dp)
    Box(
        Modifier.size(sizeDp.dp).clip(shape).background(Color(0xFF191D29)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp!!, contentDescription = song?.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MusicNote, null, tint = accent.copy(alpha = 0.85f), modifier = Modifier.size((sizeDp * 0.45f).dp))
        }
        if (isActivePlaying) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.52f).dp))
            }
        }
    }
}

@Composable
private fun SongCoverArtFull(
    song: Song,
    accent: Color,
    isActivePlaying: Boolean,
    cornerDp: Int
) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = song.id) {
        val cached = CoverArtCache.get(song.id)
        if (cached != null) {
            value = cached.asImageBitmap()
        } else {
            value = null
            val loaded = withContext(Dispatchers.IO) {
                VyroWidgetProvider.loadRobustAlbumArt(context, song.uri.toString(), song.albumId)?.also {
                    CoverArtCache.put(song.id, it)
                }
            }
            value = loaded?.asImageBitmap()
        }
    }
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(cornerDp.dp)).background(Color(0xFF191D29)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bitmap = bmp!!, contentDescription = song.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(56.dp))
        }
        if (isActivePlaying) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

// ==========================================================
// 2. قائمة وشبكة الأغاني (صغير / وسط / كبير 2x2)
// ==========================================================
@Composable
private fun SongListOrGrid(
    songs: List<Song>,
    viewSize: String,
    currentSongId: Long,
    isPlaying: Boolean,
    likedIds: Set<Long>,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    onPlayIndex: (Int) -> Unit,
    onMore: (Song) -> Unit
) {
    if (viewSize == "LARGE") {
        val rows = remember(songs) { songs.chunked(2) }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(rows) { rIdx, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { cIdx, song ->
                        val realIdx = rIdx * 2 + cIdx
                        val active = song.id == currentSongId
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(cardColor.copy(alpha = 0.72f))
                                .border(if (active) 1.5.dp else 0.dp, accent, RoundedCornerShape(18.dp))
                                .clickable { onPlayIndex(realIdx) }
                                .padding(8.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                SongCoverArtFull(song = song, accent = accent, isActivePlaying = active && isPlaying, cornerDp = 14)
                                IconButton(
                                    onClick = { onMore(song) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                ) { Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(song.title, color = if (active) accent else textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        val isSmall = viewSize == "SMALL"
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(if (isSmall) 5.dp else 8.dp)) {
            itemsIndexed(songs) { idx, song ->
                val active = song.id == currentSongId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) accent.copy(alpha = 0.14f) else cardColor.copy(alpha = 0.52f))
                        .border(if (active) 1.dp else 0.dp, accent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        .clickable { onPlayIndex(idx) }
                        .padding(horizontal = 10.dp, vertical = if (isSmall) 6.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongCoverArt(
                        song = song,
                        sizeDp = if (isSmall) 42 else 52,
                        accent = accent,
                        isActivePlaying = active && isPlaying,
                        cornerDp = if (isSmall) 10 else 13
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = if (active) accent else textPrimary, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (isSmall) 13.5.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text("${song.artist} • ${formatTime(song.duration)}", color = textSecondary, fontSize = if (isSmall) 11.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (likedIds.contains(song.id)) {
                        Icon(Icons.Default.Favorite, null, tint = accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { onMore(song) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = textSecondary, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

// ==========================================================
// 3. منصة المشغل السفلي الزجاجية الخاصة بهوية VYRO
// ==========================================================
@Composable
private fun TitanFloatingMiniDock(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    accent: Color,
    isDark: Boolean,
    onTogglePlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val dockBg = if (isDark) Color(0xEE141824) else Color(0xF5FFFFFF)
    val dockText = if (isDark) Color.White else Color(0xFF10131A)
    val dockSub = if (isDark) Color(0xFF9EA5B8) else Color(0xFF606878)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(dockBg)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenFullPlayer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongCoverArt(song = song, sizeDp = 46, accent = accent, cornerDp = 12)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = dockText, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} • ${formatTime(position)}", color = dockSub, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = dockText, modifier = Modifier.size(21.dp))
                    }
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(38.dp).background(accent, CircleShape)) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = dockText, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accent))
            }
        }
    }
}

// ==========================================================
// 4. استوديو السمات بالمعاينة الحقيقية (يعرض أغانيك وأغلفتها الفعلية + تفاعل مباشر)
// ==========================================================
@Composable
private fun TitanLiveThemeStudioScreen(
    sampleSongs: List<Song>,
    viewSize: String,
    themeMode: String,
    accentColorId: String,
    liveBackdrop: String,
    accent: Color,
    isDark: Boolean,
    bgColor: Color,
    cardColor: Color,
    pillBgColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    hasCustomWallpaper: Boolean,
    onBack: () -> Unit,
    onSelectSize: (String) -> Unit,
    onSelectMode: (String) -> Unit,
    onSelectAccent: (String) -> Unit,
    onSelectBackdrop: (String) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onApplyAndClose: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(bgColor).padding(vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
                Text("استوديو السمات الحي", color = textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("SMALL" to "صغير", "MEDIUM" to "وسط", "LARGE" to "كبير").forEach { (id, label) ->
                    val active = viewSize == id
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) accent else pillBgColor)
                            .clickable { onSelectSize(id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = if (active) Color.Black else textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 3 بطاقات معاينة حقيقية تفاعلية (تضغط على أي واحدة تتطبق حجمها وتوريك أغانيك الحقيقية)
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("MEDIUM" to "العرض المتوسط", "LARGE" to "شبكة البوسترات 2x2", "SMALL" to "العرض المدمج").forEach { (cardSize, cardLabel) ->
                val isSelectedCard = viewSize == cardSize
                val frameBg = if (isDark) Color(0xFF0A0D16) else Color(0xFFF6F8FC)
                val frameCard = if (isDark) Color(0xFF151926) else Color(0xFFFFFFFF)
                val frameText = if (isDark) Color.White else Color(0xFF10131A)
                val frameSub = if (isDark) Color(0xFF8C93A6) else Color(0xFF606878)

                Column(
                    Modifier
                        .width(225.dp)
                        .fillMaxHeight(0.94f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(frameBg)
                        .border(if (isSelectedCard) 2.5.dp else 1.dp, if (isSelectedCard) accent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                        .clickable { onSelectSize(cardSize) }
                        .padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cardLabel, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        if (isSelectedCard) {
                            Box(Modifier.size(20.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (cardSize == "LARGE") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (r in 0..1) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (c in 0..1) {
                                            val song = sampleSongs.getOrNull(r * 2 + c)
                                            Column(
                                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(frameCard).padding(5.dp)
                                            ) {
                                                SongCoverArt(song = song, sizeDp = 82, accent = accent, cornerDp = 10)
                                                Spacer(Modifier.height(4.dp))
                                                Text(song?.title ?: "VYRO Track ${r * 2 + c + 1}", color = frameText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val count = if (cardSize == "SMALL") 5 else 4
                            val thumb = if (cardSize == "SMALL") 30 else 38
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in 0 until count) {
                                    val song = sampleSongs.getOrNull(i)
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (i == 0) accent.copy(alpha = 0.16f) else frameCard).padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SongCoverArt(song = song, sizeDp = thumb, accent = accent, cornerDp = 8)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(song?.title ?: "VYRO Track ${i + 1}", color = if (i == 0) accent else frameText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song?.artist ?: "Mohamed Tarek", color = frameSub, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelectedCard) accent else frameCard).padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isSelectedCard) "✓ مُحدد الآن" else "تحديد هذا الشكل", color = if (isSelectedCard) Color.Black else frameText, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // الخلفيات الحية
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LIVE_BACKDROPS.forEach { (id, label) ->
                val active = liveBackdrop == id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) accent.copy(alpha = 0.20f) else pillBgColor)
                        .border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(50))
                        .clickable { onSelectBackdrop(id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, color = if (active) accent else textSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ليلي / نهاري / النظام
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(50)).background(pillBgColor).padding(4.dp)) {
            listOf("DARK" to Pair("ليلي", Icons.Default.DarkMode), "LIGHT" to Pair("نهاري", Icons.Default.LightMode), "SYSTEM" to Pair("النظام", Icons.Default.Contrast)).forEach { (id, pair) ->
                val active = themeMode == id
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (active) accent else Color.Transparent).clickable { onSelectMode(id) }.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(pair.second, null, tint = if (active) Color.Black else textSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(pair.first, color = if (active) Color.Black else textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // الألوان + خلفية المعرض + زر التطبيق
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(accent).clickable(onClick = onApplyAndClose),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, "Apply", tint = Color.Black) }

            Box(
                Modifier.size(46.dp).clip(CircleShape).background(pillBgColor).border(if (hasCustomWallpaper) 2.dp else 0.dp, accent, CircleShape).clickable {
                    if (hasCustomWallpaper) onClearWallpaper() else onPickWallpaper()
                },
                contentAlignment = Alignment.Center
            ) { Icon(if (hasCustomWallpaper) Icons.Default.Close else Icons.Default.AddPhotoAlternate, null, tint = accent) }

            ACCENT_SWATCHES.forEach { sw ->
                val selected = accentColorId == sw.id
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(sw.color).border(if (selected) 3.dp else 0.dp, Color.White, CircleShape).clickable { onSelectAccent(sw.id) },
                    contentAlignment = Alignment.Center
                ) {
                    if (sw.id == "DYNAMIC") Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    else if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ==========================================================
// 5. استوديو Glassify & Stadium (تجميل الشاشة الرئيسية + شاشة القفل + الويدجت الزجاجي)
// ==========================================================
@Composable
private fun TitanGlassifyStudioScreen(
    currentSong: Song?,
    widgetStyle: String,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onSelectWidgetStyle: (String) -> Unit,
    onPinWidget: () -> Unit,
    onApplyWallpaper: (GlassPreset, Int) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(GLASS_WALLPAPER_PRESETS.first()) }

    Column(
        Modifier.fillMaxSize().background(bgColor).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
            Column {
                Text("استوديو Glassify للموبايل", color = textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("تنسيق الشاشة الرئيسية وشاشة القفل والويدجت الزجاجي", color = textSecondary, fontSize = 11.5.sp)
            }
        }

        // 1. قسم خلفيات الزجاج وملاعب كرة القدم للشاشة الرئيسية والقفل
        Text("1. خلفيات زجاجية وملاعب (الرئيسية والقفل)", color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GLASS_WALLPAPER_PRESETS.forEach { preset ->
                val active = selectedPreset.id == preset.id
                Column(
                    Modifier
                        .width(155.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor)
                        .border(if (active) 2.dp else 1.dp, if (active) accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .clickable { selectedPreset = preset }
                        .padding(8.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(preset.c1.copy(alpha = 0.65f), preset.c2))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (preset.id.contains("PITCH") || preset.id.contains("BLAUGRANA")) Icons.Default.SportsSoccer else Icons.Default.AutoAwesome,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                        Box(
                            Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(preset.badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(preset.title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                    Text(preset.subtitle, color = textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onApplyWallpaper(selectedPreset, WallpaperManager.FLAG_SYSTEM) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Home, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("الرئيسية", color = textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onApplyWallpaper(selectedPreset, WallpaperManager.FLAG_LOCK) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = cardColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Lock, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("القفل", color = textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onApplyWallpaper(selectedPreset, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("تطبيق للكل", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 2. قسم الويدجت الزجاجي (Glassify Widget)
        Text("2. ويدجت VYRO الزجاجي للشاشة الرئيسية", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        listOf(
            Triple("Glassify Frost", "زجاج Glassify الشفاف (مربع)", "انعكاس زجاجي أنيق يتلون بغلاف الأغنية"),
            Triple("Full Poster", "بوستر سينمائي كامل (مربع)", "صورة الأغنية تملأ الويدجت بالكامل"),
            Triple("Stadium Glass", "زجاج استاد كرة القدم", "خطوط ملعب وكشافات خضراء شفافة"),
            Triple("Wide Glass", "شريط زجاجي عريض (4x1)", "مناسب لأعلى أو أسفل الشاشة الرئيسية")
        ).forEach { (id, title, sub) ->
            val selected = widgetStyle == id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .border(if (selected) 2.dp else 0.dp, accent, RoundedCornerShape(16.dp))
                    .clickable { onSelectWidgetStyle(id) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongCoverArt(song = currentSong, sizeDp = 42, accent = accent, cornerDp = 10)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Text(sub, color = textSecondary, fontSize = 11.sp)
                }
                if (selected) Icon(Icons.Default.Check, null, tint = accent)
            }
        }

        Button(
            onClick = onPinWidget,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC857))
        ) {
            Icon(Icons.Default.Widgets, null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("إضافة الويدجت الزجاجي للشاشة الرئيسية", color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

// ==========================================================
// 6. مركز التحديثات الداخلي + شاشة التحديث الإجباري (بعد 10 أيام)
// ==========================================================
@Composable
private fun TitanUpdatesCenterScreen(
    availableUpdate: UpdateInfo?,
    checking: Boolean,
    statusMsg: String?,
    daysLeft: Int,
    isDownloading: Boolean,
    progress: Float,
    downloadedFile: File?,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    onShowChangelog: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    if (availableUpdate != null) {
        TitanUpdateDownloadScreen(
            info = availableUpdate,
            isMandatory = false,
            daysLeft = daysLeft,
            isDownloading = isDownloading,
            progress = progress,
            downloadedFile = downloadedFile,
            accent = accent,
            bgColor = bgColor,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onBack = onBack,
            onShowChangelog = onShowChangelog,
            onDownload = onDownload,
            onInstall = onInstall
        )
    } else {
        Column(
            Modifier.fillMaxSize().background(bgColor).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
                Text("تحديثات VYRO MUSIC", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(88.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SystemUpdate, null, tint = accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("الإصدار الحالي: $APP_VERSION_NAME", color = textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(statusMsg ?: "اضغط للتحقق من وجود إصدار أحدث على الخادم", color = textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onCheckNow,
                enabled = !checking,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth(0.75f).height(48.dp)
            ) {
                Text(if (checking) "جاري الفحص..." else "التحقق من التحديثات الآن", color = Color.Black, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun TitanUpdateDownloadScreen(
    info: UpdateInfo,
    isMandatory: Boolean,
    daysLeft: Int,
    isDownloading: Boolean,
    progress: Float,
    downloadedFile: File?,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: (() -> Unit)?,
    onShowChangelog: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(bgColor).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!isMandatory && onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
            }
            Text(
                if (isMandatory) "تحديث إجباري مطلوب" else "تحديث جديد متوفر",
                color = if (isMandatory) Color(0xFFFF5263) else textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cardColor).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Download, null, tint = accent, modifier = Modifier.size(36.dp))
            }
            Text(info.title.ifBlank { "VYRO MUSIC ${info.tagName}" }, color = textPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text(
                if (isMandatory) "انتهت مهلة الـ 10 أيام. يجب تحديث التطبيق الآن للاستمرار في استخدام VYRO MUSIC."
                else "الإصدار ${info.tagName} جاهز للتحميل الداخلي المباشر (متبقي $daysLeft أيام قبل التحديث الإجباري).",
                color = textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            OutlinedButton(onClick = onShowChangelog, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NewReleases, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ما الجديد في هذا التحديث؟", color = accent, fontWeight = FontWeight.Bold)
            }

            if (isDownloading) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = accent
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("جاري التحميل داخل التطبيق... ${(progress * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Button(
            onClick = { if (downloadedFile != null) onInstall() else onDownload() },
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Icon(Icons.Default.Download, null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isDownloading -> "جاري التنزيل..."
                    downloadedFile != null -> "تثبيت التحديث الآن"
                    else -> "تنزيل التحديث (${info.tagName})"
                },
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp
            )
        }
    }
}

// ==========================================================
// 7. شاشة الإعدادات المرتبة في بطاقات مستقلة
// ==========================================================
@Composable
private fun TitanSettingsScreen(
    listenSeconds: Long,
    viewSize: String,
    liveBackdrop: String,
    langSetting: String,
    sleepRemainingSec: Int,
    hiddenCount: Int,
    widgetStyle: String,
    hasUpdate: Boolean,
    updateTag: String?,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenUpdates: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    onOpenGlassify: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenHiddenFiles: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onPreventStop: () -> Unit,
    onOpenNotifications: () -> Unit,
    onReplayTutorial: () -> Unit,
    onRequestReset: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val sizeAr = when (viewSize) { "SMALL" -> "صغير"; "LARGE" -> "كبير"; else -> "وسط" }
    val themeSummary = "${LIVE_BACKDROPS.firstOrNull { it.first == liveBackdrop }?.second ?: "سادة"} / $sizeAr"
    val langSummary = when (langSetting) { "EN" -> "English"; "SYSTEM" -> "النظام"; else -> "العربية" }
    val sleepSummary = if (sleepRemainingSec > 0) "${sleepRemainingSec / 60} دقيقة" else "إيقاف"

    Column(
        Modifier.fillMaxSize().background(bgColor).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
            Text("الإعدادات", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        // بطاقة 1: التحديثات والإحصائيات والنسخ الاحتياطي
        SettingsGroupCard(cardColor) {
            SettingsRowItem(
                icon = Icons.Default.SystemUpdate,
                title = "تحديثات التطبيق",
                subtitle = if (hasUpdate) "يتوفر إصدار جديد جاهز للتنزيل!" else "فحص التحديثات وما الجديد",
                trailingValue = if (hasUpdate) updateTag else "محدث",
                iconTint = if (hasUpdate) Color(0xFFFF5263) else accent,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onClick = onOpenUpdates
            )
            SettingsDivider()
            SettingsRowItem(Icons.Default.Schedule, "وقت التشغيل", null, formatListenHours(listenSeconds), null, textPrimary, textSecondary, onOpenStats)
            SettingsDivider()
            SettingsRowItem(Icons.Default.CloudUpload, "تصدير نسخة احتياطية (JSON)", null, null, null, textPrimary, textSecondary, onBackup)
            SettingsDivider()
            SettingsRowItem(Icons.Default.CloudDownload, "استعادة نسخة احتياطية", null, null, null, textPrimary, textSecondary, onRestore)
        }

        // بطاقة 2: المظهر و Glassify واللغة والنوم
        SettingsGroupCard(cardColor) {
            SettingsRowItem(Icons.Default.Palette, "استوديو السمات والواجهة", null, themeSummary, accent, textPrimary, textSecondary, onOpenThemeStudio)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Widgets, "تجميل الموبايل وويدجت Glassify", "خلفيات زجاجية وملاعب للرئيسية والقفل", widgetStyle, Color(0xFFFFC857), textPrimary, textSecondary, onOpenGlassify)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Language, "اللغة", null, langSummary, null, textPrimary, textSecondary, onOpenLanguage)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Bedtime, "مؤقت النوم", null, sleepSummary, null, textPrimary, textSecondary, onOpenSleep)
        }

        // بطاقة 3: المكتبة والتشغيل
        SettingsGroupCard(cardColor) {
            SettingsRowItem(Icons.Default.VisibilityOff, "الملفات المخفية", null, "$hiddenCount ملف", null, textPrimary, textSecondary, onOpenHiddenFiles)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Headphones, "إعدادات التشغيل والمشغل", "8D • Crossfade • شكل القرص والموجات", null, null, textPrimary, textSecondary, onOpenPlaybackSettings)
            SettingsDivider()
            SettingsRowItem(Icons.Default.BatteryChargingFull, "منع توقف الموسيقى", "استثناء من توفير البطارية", null, null, textPrimary, textSecondary, onPreventStop)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Notifications, "إعدادات الإشعارات", null, null, null, textPrimary, textSecondary, onOpenNotifications)
        }

        // بطاقة 4: الجولة التعليمية وإعادة الضبط وحول
        SettingsGroupCard(cardColor) {
            SettingsRowItem(Icons.Default.HelpOutline, "إعادة تشغيل الجولة التعليمية", "شرح جميع أزرار ومميزات التطبيق", null, accent, textPrimary, textSecondary, onReplayTutorial)
            SettingsDivider()
            SettingsRowItem(Icons.Default.RestartAlt, "إعادة ضبط الإعدادات الافتراضية", null, null, Color(0xFFFF5263), Color(0xFFFF5263), textSecondary, onRequestReset)
            SettingsDivider()
            SettingsRowItem(Icons.Default.Info, "حول التطبيق", null, APP_VERSION_NAME, null, textPrimary, textSecondary, onOpenAbout)
        }
    }
}

@Composable
private fun SettingsGroupCard(cardColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cardColor).padding(vertical = 4.dp), content = content)
}

@Composable
private fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(0.6.dp).background(Color.White.copy(alpha = 0.06f)))
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingValue: String? = null,
    iconTint: Color? = null,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint ?: textPrimary.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = textSecondary, fontSize = 11.5.sp)
        }
        if (!trailingValue.isNullOrBlank()) {
            Text(trailingValue, color = textSecondary, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Default.ChevronLeft, null, tint = textSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TitanPlaybackSettingsScreen(
    spatial8D: Boolean,
    shakeSkip: Boolean,
    crossfadeSec: Int,
    discStyle: String,
    waveStyle: String,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onToggle8D: () -> Unit,
    onToggleShake: () -> Unit,
    onChangeCrossfade: (Int) -> Unit,
    onSelectDisc: (String) -> Unit,
    onSelectWave: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(bgColor).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
            Text("إعدادات التشغيل والمشغل", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        SettingsGroupCard(cardColor) {
            ToggleSettingsRow("صوت محيطي 8D", "توزيع صوتي يدور حول الرأس 360°", spatial8D, textPrimary, textSecondary, onToggle8D)
            SettingsDivider()
            ToggleSettingsRow("هز الهاتف لتغيير الأغنية", "Shake-to-Skip أثناء التشغيل", shakeSkip, textPrimary, textSecondary, onToggleShake)
        }

        SettingsGroupCard(cardColor) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الدمج السلس بين الأغاني (Crossfade): ${if (crossfadeSec == 0) "إيقاف" else "$crossfadeSec ثوانٍ"}", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(value = crossfadeSec.toFloat(), onValueChange = { onChangeCrossfade(it.toInt()) }, valueRange = 0f..10f, steps = 9)
                }
            }
        }

        SettingsGroupCard(cardColor) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("شكل قرص الأغنية داخل المشغل", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dynamic Card" to "غلاف أنيق", "Vinyl Spin" to "أسطوانة دوارة", "Stadium Crest" to "درع الاستاد", "Neon Ring" to "حلقة نيون").forEach { (id, label) ->
                        FilterChip(selected = discStyle == id, onClick = { onSelectDisc(id) }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
                Text("شكل الموجات الصوتية", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Smooth Wave" to "موجة ناعمة", "Spectrum Bars" to "أعمدة طيفية", "Mirror Twin" to "موجة مزدوجة", "Hidden" to "إخفاء").forEach { (id, label) ->
                        FilterChip(selected = waveStyle == id, onClick = { onSelectWave(id) }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingsRow(title: String, subtitle: String, checked: Boolean, textPrimary: Color, textSecondary: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = textSecondary, fontSize = 11.5.sp)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TitanHiddenFilesScreen(
    hiddenSongs: List<Song>,
    accent: Color,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onBack: () -> Unit,
    onUnhide: (Long) -> Unit,
    onUnhideAll: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(bgColor).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null, tint = textPrimary) }
                Text("الملفات المخفية (${hiddenSongs.size})", color = textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            if (hiddenSongs.isNotEmpty()) {
                TextButton(onClick = onUnhideAll) { Text("إظهار الكل", color = accent, fontWeight = FontWeight.Bold) }
            }
        }
        if (hiddenSongs.isEmpty()) {
            EmptyState("لا توجد ملفات مخفية حالياً", accent, textPrimary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(hiddenSongs) { _, song ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongCoverArt(song = song, sizeDp = 46, accent = accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = textPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(song.artist, color = textSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(onClick = { onUnhide(song.id) }) { Text("إظهار", color = accent, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

// ==========================================================
// 8. شاشة المشغل الكاملة (تحديث فوري للصورة والألوان)
// ==========================================================
@Composable
private fun TitanNowPlayingScreen(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    isLiked: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    sleepRemainingSec: Int,
    discStyle: String,
    waveStyle: String,
    spatial8D: Boolean,
    loopA: Long,
    loopB: Long,
    accent: Color,
    palette: CoverPalette,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggle8D: () -> Unit,
    onToggleABLoop: () -> Unit,
    onOpenDriveMode: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "np")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.dominant.copy(alpha = 0.52f), palette.deepTint, Color(0xFF05060B))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Text("VYRO STUDIO PLAYER", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Row {
                IconButton(onClick = onOpenDriveMode) { Icon(Icons.Default.DirectionsCar, null, tint = Color.White) }
                IconButton(onClick = onOpenMenu) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .size(274.dp)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > 80f) onPrevious()
                            else if (totalDrag < -80f) onNext()
                            totalDrag = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            when (discStyle) {
                "Vinyl Spin" -> {
                    Box(
                        Modifier.size(258.dp).rotate(if (playing) spinAngle else 0f).clip(CircleShape).background(Color(0xFF0C0E14)).border(8.dp, Color(0xFF1F2230), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        SongCoverArt(song = song, sizeDp = 186, isCircle = true, accent = accent)
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.Black).border(3.dp, accent, CircleShape))
                    }
                }
                "Stadium Crest" -> {
                    Box(Modifier.size(258.dp).clip(RoundedCornerShape(36.dp)).border(3.dp, accent, RoundedCornerShape(36.dp)).padding(6.dp)) {
                        SongCoverArtFull(song = song, accent = accent, isActivePlaying = false, cornerDp = 30)
                    }
                }
                "Neon Ring" -> {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(color = accent, radius = size.minDimension / 2.05f, style = Stroke(width = 5f)) }
                    SongCoverArt(song = song, sizeDp = 238, isCircle = true, accent = accent)
                }
                else -> {
                    SongCoverArtFull(song = song, accent = accent, isActivePlaying = false, cornerDp = 26)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (waveStyle != "Hidden") {
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val midY = size.height / 2f
                when (waveStyle) {
                    "Spectrum Bars" -> {
                        val bars = 34
                        val space = size.width / bars
                        for (i in 0 until bars) {
                            val f = if (playing) abs(sin(pulsePhase + i * 0.42f)) else 0.18f
                            val bh = (size.height * (0.18f + 0.78f * f)).coerceAtLeast(4f)
                            drawRoundRect(color = accent, topLeft = Offset(i * space + 2f, (size.height - bh) / 2f), size = Size((space - 4f).coerceAtLeast(3f), bh), cornerRadius = CornerRadius(6f, 6f))
                        }
                    }
                    else -> {
                        val path = Path()
                        path.moveTo(0f, midY)
                        val steps = 60
                        val dx = size.width / steps
                        for (i in 0..steps) {
                            val amp = if (playing) (size.height * 0.40f * sin(pulsePhase + i * 0.25f)) else 2f
                            path.lineTo(i * dx, midY + amp)
                        }
                        drawPath(path, color = accent, style = Stroke(width = 4f))
                        if (waveStyle == "Mirror Twin") {
                            val path2 = Path()
                            path2.moveTo(0f, midY)
                            for (i in 0..steps) {
                                val amp = if (playing) (size.height * 0.40f * sin(pulsePhase + i * 0.25f)) else 2f
                                path2.lineTo(i * dx, midY - amp)
                            }
                            drawPath(path2, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = 2.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color(0xFFB8C0D6), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleLike) {
                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isLiked) Color(0xFFFF5263) else Color.White, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color(0xFFB5BCD0), fontSize = 12.sp)
                    Text(
                        when {
                            loopA >= 0L && loopB >= 0L -> "A-B LOOP"
                            spatial8D -> "8D SPATIAL"
                            else -> "VYRO CRYSTAL AUDIO"
                        },
                        color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Text(formatTime(duration), color = Color(0xFFB5BCD0), fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleShuffle) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) accent else Color.White.copy(alpha = 0.55f)) }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(72.dp).background(Color.White, CircleShape)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                IconButton(onClick = onCycleRepeat) {
                    Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.55f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onOpenEq, label = { Text("معادل الصوت", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.GraphicEq, null, tint = accent, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = onToggle8D, label = { Text(if (spatial8D) "8D مفعل" else "8D محيطي", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Headphones, null, tint = accent, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = onOpenLyrics, label = { Text("الكلمات", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Lyrics, null, tint = accent, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = onToggleABLoop, label = { Text(if (loopA >= 0L) "A-B مفعل" else "تكرار A-B", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Repeat, null, tint = accent, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = onOpenSleep, label = { Text(if (sleepRemainingSec > 0) "${sleepRemainingSec / 60}m" else "مؤقت النوم", fontSize = 11.sp) }, leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = accent, modifier = Modifier.size(16.dp)) })
        }
    }
}

// ==========================================================
// 9. استوديو الصوت 8D والمعادل (بدون خيار مانع الغروشة لأنه مدمج صامتاً)
// ==========================================================
@Composable
private fun TitanDspStudioTab(
    eqPreset: String,
    bandGains: List<Float>,
    bassStrength: Int,
    virtualizerStrength: Int,
    loudnessGain: Int,
    playbackSpeed: Float,
    playbackPitch: Float,
    spatial8D: Boolean,
    accent: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onToggle8D: () -> Unit,
    onOpenSystemEq: () -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (spatial8D) Color(0xFFFFC857).copy(alpha = 0.16f) else cardColor)
                .border(1.dp, if (spatial8D) Color(0xFFFFC857) else Color.Transparent, RoundedCornerShape(18.dp))
                .clickable(onClick = onToggle8D)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Headphones, null, tint = if (spatial8D) Color(0xFFFFC857) else accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("الصوت المحيطي 8D Surround", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (spatial8D) "مفعل • دوران صوتي ثلاثي الأبعاد 360°" else "اضغط للتفعيل (أفضل تجربة مع السماعة)", color = textSecondary, fontSize = 11.5.sp)
            }
            Switch(checked = spatial8D, onCheckedChange = { onToggle8D() })
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Pure Hi-Fi", "Clean Phonk", "Gym Beast", "Vocal Studio", "Flat").forEach { preset ->
                FilterChip(selected = eqPreset == preset, onClick = { onSelectPreset(preset) }, label = { Text(preset, fontSize = 12.sp) })
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardColor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bandGains.forEachIndexed { idx, gain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabels.getOrElse(idx) { "B" }, color = textPrimary, fontSize = 11.sp, modifier = Modifier.width(46.dp))
                        Slider(value = gain, onValueChange = { onBandChange(idx, it) }, valueRange = -1f..1f, modifier = Modifier.weight(1f))
                        Text("${(gain * 12).toInt()}dB", color = accent, fontSize = 11.sp, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardColor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SUB-BASS POWER ($bassStrength%)", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = bassStrength.toFloat(), onValueChange = { onBassChange(it.toInt()) }, valueRange = 0f..100f)
                Text("3D STAGE ($virtualizerStrength%)", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = virtualizerStrength.toFloat(), onValueChange = { onVirtChange(it.toInt()) }, valueRange = 0f..100f)
                Text("EXTRA GAIN (+${(loudnessGain * 0.08f).toInt()}dB)", color = textSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = loudnessGain.toFloat(), onValueChange = { onLoudnessChange(it.toInt()) }, valueRange = 0f..100f)
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardColor).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SPEED ${"%.2f".format(playbackSpeed)}x • PITCH ${"%.2f".format(playbackPitch)}x", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Reset 1.0x", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSpeedChange(1f); onPitchChange(1f) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSpeedChange(0.85f); onPitchChange(0.88f) }) { Text("Slowed + Phonk", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSpeedChange(1.16f); onPitchChange(1.12f) }) { Text("Nightcore", fontSize = 11.sp) }
                }
                Slider(value = playbackSpeed, onValueChange = onSpeedChange, valueRange = 0.6f..1.6f)
            }
        }

        OutlinedButton(onClick = onOpenSystemEq, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Speaker, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("فتح Dolby Atmos للهاتف", color = textPrimary)
        }
    }
}

// ==========================================================
// 10. الخلفيات الحية + مولد خلفيات Glassify + دوال التحديثات والشبكة
// ==========================================================
@Composable
private fun AnimatedThemeBackdrop(backdropId: String, accent: Color, secondaryAccent: Color, playing: Boolean) {
    val infinite = rememberInfiniteTransition(label = "bg")
    val phase by infinite.animateFloat(0f, 6.28318f, infiniteRepeatable(tween(if (playing) 5500 else 11000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val rise by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(if (playing) 4500 else 9000, easing = LinearEasing), RepeatMode.Restart), label = "r")

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (backdropId) {
            "DYNAMIC_AURA" -> {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent), Offset(w * 0.25f + sin(phase) * 40f, h * 0.22f), w * 0.75f), w * 0.75f, Offset(w * 0.25f + sin(phase) * 40f, h * 0.22f))
                drawCircle(Brush.radialGradient(listOf(secondaryAccent.copy(alpha = 0.20f), Color.Transparent), Offset(w * 0.75f, h * 0.72f + cos(phase) * 40f), w * 0.75f), w * 0.75f, Offset(w * 0.75f, h * 0.72f + cos(phase) * 40f))
            }
            "STADIUM", "BLAUGRANA" -> {
                val stripes = 10
                val sh = h / stripes
                for (i in 0 until stripes) {
                    val c = if (backdropId == "BLAUGRANA") (if (i % 2 == 0) Color(0xFF0A1636) else Color(0xFF280919))
                    else (if (i % 2 == 0) Color(0xFF072814) else Color(0xFF051D0E))
                    drawRect(c.copy(alpha = 0.55f), Offset(0f, i * sh), Size(w, sh))
                }
                val lc = Color.White.copy(alpha = 0.14f)
                drawRect(lc, Offset(w * 0.06f, h * 0.08f), Size(w * 0.88f, h * 0.84f), style = Stroke(3f))
                drawLine(lc, Offset(w * 0.06f, h * 0.5f), Offset(w * 0.94f, h * 0.5f), 3f)
                drawCircle(lc, w * 0.18f, Offset(w * 0.5f, h * 0.5f), style = Stroke(3f))
            }
            "OCEAN" -> {
                for (layer in 0..2) {
                    val path = Path().apply {
                        val baseY = h * (0.4f + layer * 0.2f)
                        moveTo(0f, h); lineTo(0f, baseY)
                        for (i in 0..36) lineTo(i * (w / 36f), baseY + sin(phase + i * 0.3f + layer) * 20f)
                        lineTo(w, h); close()
                    }
                    drawPath(path, Color(0xFF0099CC).copy(alpha = 0.10f + layer * 0.04f))
                }
                for (b in 0..20) {
                    val x = (((b * 37) % 100) / 100f) * w + sin(phase + b) * 14f
                    val y = (1f - ((rise + ((b * 19) % 100) / 100f) % 1f)) * h
                    drawCircle(Color(0xFF80F4FF).copy(alpha = 0.25f), 6f + (b % 5) * 4f, Offset(x, y), style = Stroke(2f))
                }
            }
            "NEON" -> {
                for (i in 0..10) {
                    val y = (((i / 10f) + rise * 0.25f) % 1f) * h
                    drawLine(accent.copy(alpha = 0.14f), Offset(0f, y), Offset(w, y), 1.5f)
                }
            }
            "MATRIX" -> {
                val cols = 16
                val cw = w / cols
                for (c in 0 until cols) {
                    val y = ((rise + ((c * 29) % 100) / 100f) % 1f) * h
                    drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF00FF66).copy(alpha = 0.32f))), Offset(c * cw + cw / 2f, (y - 130f).coerceAtLeast(0f)), Offset(c * cw + cw / 2f, y), 4f)
                }
            }
        }
    }
}

private fun generateHighResGlassWallpaper(presetId: String): Bitmap {
    val w = 1080
    val h = 2340
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    when (presetId) {
        "CHAMPIONS_PITCH" -> {
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF04140B.toInt(), 0xFF082E18.toInt(), 0xFF030D07.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f; color = 0x33FFFFFF }
            canvas.drawRect(70f, 180f, w - 70f, h - 180f, line)
            canvas.drawLine(70f, h / 2f, w - 70f, h / 2f, line)
            canvas.drawCircle(w / 2f, h / 2f, 210f, line)
            val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(w * 0.2f, 220f, 700f, intArrayOf(0x5500F57A, 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(w * 0.2f, 220f, 700f, glow)
        }
        "BLAUGRANA_NIGHT" -> {
            paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF00246B.toInt(), 0xFF0C0E24.toInt(), 0xFF7A0C2E.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val orb1 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(250f, 550f, 520f, intArrayOf(0x660052CC, 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)
            }
            val orb2 = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(w - 220f, h - 620f, 560f, intArrayOf(0x66ED1B4E, 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(250f, 550f, 520f, orb1)
            canvas.drawCircle(w - 220f, h - 620f, 560f, orb2)
        }
        "PHONK_CARBON" -> {
            paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF06050D.toInt(), 0xFF1A071E.toInt(), 0xFF06050D.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val laser = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f; color = 0x33FF007A }
            for (y in 200 until h step 140) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), laser)
        }
        "OCEAN_GLASS" -> {
            paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF021024.toInt(), 0xFF052659.toInt(), 0xFF020B18.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val bubble = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f; color = 0x4480F4FF }
            for (i in 1..18) canvas.drawCircle(((i * 197) % (w - 120) + 60).toFloat(), ((i * 311) % (h - 200) + 100).toFloat(), (25 + (i % 5) * 18).toFloat(), bubble)
        }
        else -> {
            // GLASSIFY_FROST
            paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0xFF060914.toInt(), 0xFF101C36.toInt(), 0xFF06070E.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(w * 0.5f, h * 0.35f, 650f, intArrayOf(0x5500C2FF, 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(w * 0.5f, h * 0.35f, 650f, glow)
        }
    }

    // طبقة بطاقات زجاجية شفافة أنيقة (Glassify Layers)
    val glassPanel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x16FFFFFF }
    val glassRim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f; color = 0x33FFFFFF }
    val r1 = android.graphics.RectF(90f, 420f, w - 90f, 920f)
    canvas.drawRoundRect(r1, 64f, 64f, glassPanel)
    canvas.drawRoundRect(r1, 64f, 64f, glassRim)

    return bmp
}

private fun fetchLatestGithubRelease(): UpdateInfo? {
    return runCatching {
        val conn = (URL(GITHUB_RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 9000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VYRO-MUSIC-Updater")
        }
        if (conn.responseCode != 200) return null
        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = JSONObject(jsonStr)
        val tag = obj.optString("tag_name", "").trim()
        if (tag.isBlank()) return null
        val name = obj.optString("name", tag)
        val body = obj.optString("body", "")
        val htmlUrl = obj.optString("html_url", "https://github.com/mohmedtarek2025-hue/VYRO-MUSIC/releases")
        var apkUrl: String? = null
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val dl = asset.optString("browser_download_url", "")
                if (dl.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = dl
                    break
                }
            }
        }
        UpdateInfo(tag, name, body, apkUrl, htmlUrl)
    }.getOrNull()
}

private fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
    fun parseNums(v: String): List<Int> {
        val clean = v.removePrefix("v").removePrefix("V").substringBefore("-")
        return clean.split(".").map { it.toIntOrNull() ?: 0 }
    }
    val r = parseNums(remoteTag)
    val c = parseNums(currentVersion)
    val len = maxOf(r.size, c.size)
    for (i in 0 until len) {
        val rv = r.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (rv != cv) return rv > cv
    }
    return false
}

@Composable
private fun DriveGymModeScreen(
    song: Song, playing: Boolean, position: Long, duration: Long, accent: Color,
    onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onExit: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DRIVE & GYM MODE", color = accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
            OutlinedButton(onClick = onExit) { Text("خروج", color = Color.White) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SongCoverArt(song = song, sizeDp = 165, accent = accent, cornerDp = 24)
            Spacer(Modifier.height(18.dp))
            Text(song.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(song.artist, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(76.dp).background(Color(0xFF181B26), CircleShape)) {
                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(98.dp).background(accent, CircleShape)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(52.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(76.dp).background(Color(0xFF181B26), CircleShape)) {
                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
        }
    }
}

@Composable
private fun CollectionTile(title: String, subtitle: String, icon: ImageVector, iconColor: Color, cardColor: Color, textPrimary: Color, textSecondary: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
            Text(subtitle, color = textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronLeft, null, tint = textSecondary)
    }
}

@Composable
private fun MenuActionRow(icon: ImageVector, title: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = tint, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoItem(label: String, value: String, textPrimary: Color, textSecondary: Color) {
    Column {
        Text(label, color = textSecondary, fontSize = 11.sp)
        Text(value, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(message: String, accent: Color, textPrimary: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = textPrimary, textAlign = TextAlign.Center, fontSize = 14.sp)
        }
    }
}

private fun extractCoverPaletteFromBitmap(bmp: Bitmap?): CoverPalette {
    if (bmp == null) return CoverPalette()
    var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0L
    val sx = (bmp.width / 8).coerceAtLeast(1)
    val sy = (bmp.height / 8).coerceAtLeast(1)
    for (x in 0 until bmp.width step sx) {
        for (y in 0 until bmp.height step sy) {
            val px = bmp.getPixel(x, y)
            rSum += android.graphics.Color.red(px)
            gSum += android.graphics.Color.green(px)
            bSum += android.graphics.Color.blue(px)
            count++
        }
    }
    if (count == 0L) return CoverPalette()
    val r = (rSum / count).toInt().coerceIn(45, 240)
    val g = (gSum / count).toInt().coerceIn(45, 240)
    val b = (bSum / count).toInt().coerceIn(55, 245)
    return CoverPalette(
        dominant = Color((r * 1.25f).toInt().coerceIn(80, 255), (g * 1.15f).toInt().coerceIn(70, 250), (b * 1.28f).toInt().coerceIn(90, 255)),
        secondary = Color((b * 1.1f).toInt().coerceIn(65, 240), (r * 0.9f).toInt().coerceIn(55, 225), (g * 1.2f).toInt().coerceIn(75, 245)),
        deepTint = Color((r / 7).coerceIn(6, 26), (g / 7).coerceIn(6, 26), (b / 6).coerceIn(8, 32))
    )
}

private fun formatListenHours(totalSec: Long): String {
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    return "${hours}h ${mins}m"
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun cutAndSetRingtone(context: Context, song: Song, startMs: Long, endMs: Long): Boolean {
    return runCatching {
        val outFile = File(context.cacheDir, "vyro_ringtone_${song.id}.m4a")
        if (outFile.exists()) outFile.delete()
        val extractor = MediaExtractor()
        context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd -> extractor.setDataSource(pfd.fileDescriptor) }
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                audioTrackIndex = i; format = f; break
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
            muxer.stop(); muxer.release(); extractor.release()

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VYRO_Ring_${song.title}.m4a")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.IS_RINGTONE, true)
            }
            val newUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (newUri != null) {
                context.contentResolver.openOutputStream(newUri)?.use { out -> outFile.inputStream().use { inp -> inp.copyTo(out) } }
                RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, newUri)
                return true
            }
        }
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
        true
    }.getOrElse {
        runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri) }
        false
    }
}

private fun readRawSidecarLyrics(filePath: String?): String? {
    if (filePath.isNullOrBlank()) return null
    return runCatching {
        val audioFile = File(filePath)
        val base = audioFile.nameWithoutExtension
        val parent = audioFile.parentFile ?: return null
        val lrc = File(parent, "$base.lrc")
        val txt = File(parent, "$base.txt")
        when { lrc.exists() -> lrc.readText(); txt.exists() -> txt.readText(); else -> null }
    }.getOrNull()
}

private fun parseLrcLines(raw: String): List<LrcLine> {
    if (raw.isBlank()) return emptyList()
    val regex = Regex("\\[(\\d+):(\\d+)(?:\\.(\\d+))?](.*)")
    val out = mutableListOf<LrcLine>()
    raw.lines().forEach { line ->
        val m = regex.find(line.trim())
        if (m != null) {
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val txt = m.groupValues[4].trim()
            if (txt.isNotEmpty()) out += LrcLine((min * 60 + sec) * 1000L, txt)
        }
    }
    return out.sortedBy { it.timeMs }
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

private fun loadPlayCounts(json: String?): Map<Long, Int> = runCatching {
    val obj = JSONObject(json ?: "{}")
    val map = mutableMapOf<Long, Int>()
    obj.keys().forEach { key -> key.toLongOrNull()?.let { id -> map[id] = obj.getInt(key) } }
    map
}.getOrDefault(emptyMap())

private fun encodePlayCounts(map: Map<Long, Int>): String {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj.toString()
}

private fun loadLyricsMap(json: String?): Map<Long, String> = runCatching {
    val obj = JSONObject(json ?: "{}")
    val map = mutableMapOf<Long, String>()
    obj.keys().forEach { key -> key.toLongOrNull()?.let { id -> map[id] = obj.getString(key) } }
    map
}.getOrDefault(emptyMap())

private fun encodeLyricsMap(map: Map<Long, String>): String {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k.toString(), v) }
    return obj.toString()
}
