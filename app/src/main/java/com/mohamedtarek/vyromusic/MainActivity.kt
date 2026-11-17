package com.mohamedtarek.vyromusic

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    val dominant: Color = Color(0xFF8E54E9),
    val secondary: Color = Color(0xFF4776E6),
    val deepTint: Color = Color(0xFF0A0818)
)

data class LrcLine(val timeMs: Long, val text: String)

data class VisualOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val c1: Color,
    val c2: Color,
    val previewKind: String
)

private val LAYOUT_VISUAL_OPTIONS = listOf(
    VisualOption("Royal Glass", "Royal Glass", "بطاقات زجاجية فاخرة", Color(0xFF8E54E9), Color(0xFF4776E6), "LAYOUT_GLASS"),
    VisualOption("AMOLED Pure", "AMOLED Pure", "سواد نقي بدون إطارات", Color(0xFF00E5FF), Color(0xFF050508), "LAYOUT_AMOLED"),
    VisualOption("Bento Grid", "Bento Grid 2x2", "شبكة بوسترات مزدوجة", Color(0xFFFFC857), Color(0xFF1F1638), "LAYOUT_GRID2"),
    VisualOption("Poster Wall 3x3", "Poster Wall 3x3", "جدار ألبومات ثلاثي", Color(0xFFFF2A6D), Color(0xFF161026), "LAYOUT_GRID3"),
    VisualOption("Stadium Cards", "Stadium Cards", "بطاقات مباريات عريضة", Color(0xFF00F57A), Color(0xFF072614), "LAYOUT_STADIUM"),
    VisualOption("Neon Capsule", "Neon Capsule", "كبسولات دائرية مضيئة", Color(0xFF00F0FF), Color(0xFF280B45), "LAYOUT_CAPSULE"),
    VisualOption("Compact Pro", "Compact Studio", "قائمة مدمجة سريعة", Color(0xFFA87BFF), Color(0xFF121424), "LAYOUT_COMPACT")
)

private val THEME_VISUAL_OPTIONS = listOf(
    VisualOption("Dynamic Cover Aura", "غلاف الأغنية الذكي", "يتلون تلقائياً بصورة الأغنية", Color(0xFFFF6B9D), Color(0xFF6A4CFF), "THEME_DYNAMIC"),
    VisualOption("Stadium Champions", "استاد الأبطال", "ملعب كرة قدم وكشافات", Color(0xFF00F57A), Color(0xFF052412), "THEME_STADIUM"),
    VisualOption("Camp Blaugrana", "مدرجات كامب نو", "أزرق كتالوني وقرمزي", Color(0xFFED1B4E), Color(0xFF004D98), "THEME_STADIUM"),
    VisualOption("Ocean Bubbles", "البحر والفقاقيع", "أمواج بحرية وفقاقيع صاعدة", Color(0xFF00E5FF), Color(0xFF03254C), "THEME_OCEAN"),
    VisualOption("Cyber Neon City", "أضواء النيون", "شبكة ليزر وإضاءة سايبر", Color(0xFFFF00E5), Color(0xFF00F0FF), "THEME_NEON"),
    VisualOption("Golden Pharaoh VIP", "الذهب الملكي", "جزيئات ذهب فاخرة", Color(0xFFFFC857), Color(0xFF1E1606), "THEME_PARTICLES"),
    VisualOption("Red Devil Arena", "مدرجات الشياطين", "أحمر ناري وتخطيط ملعب", Color(0xFFFF2A3C), Color(0xFF240408), "THEME_STADIUM"),
    VisualOption("Matrix Cyber Rain", "مطر الماتريكس", "شيفرات رقمية متساقطة", Color(0xFF00FF66), Color(0xFF031A09), "THEME_MATRIX"),
    VisualOption("Deep Space Nebula", "مجرة الفضاء", "نجوم وسدم كونية متحركة", Color(0xFF9E7BFF), Color(0xFF09061A), "THEME_PARTICLES"),
    VisualOption("Pitch Black AMOLED", "سواد AMOLED", "خلفية سوداء صافية للبطارية", Color(0xFF8A94B8), Color(0xFF000000), "THEME_AMOLED")
)

private val DISC_VISUAL_OPTIONS = listOf(
    VisualOption("Dynamic Aura", "Dynamic Aura Card", "غلاف متوهج بلون الأغنية", Color(0xFFFF6B9D), Color(0xFF6A4CFF), "DISC_AURA"),
    VisualOption("Vinyl Spin", "Vinyl Spin", "أسطوانة فينيل دوارة", Color(0xFFA87BFF), Color(0xFF141624), "DISC_VINYL"),
    VisualOption("Stadium Crest", "Stadium Crest", "درع الاستاد الرياضي", Color(0xFF00F57A), Color(0xFF082914), "DISC_CREST"),
    VisualOption("Square Studio", "Square Poster", "بوستر استوديو مربع", Color(0xFFFFC857), Color(0xFF1A1C2C), "DISC_SQUARE"),
    VisualOption("Neon Ring", "Neon Laser Ring", "حلقة ليزر نيون دائرية", Color(0xFF00E5FF), Color(0xFF101830), "DISC_RING")
)

private val WAVE_VISUAL_OPTIONS = listOf(
    VisualOption("Smooth Wave", "موجة ليزر ناعمة", "موجة صوتية انسيابية", Color(0xFF00E5FF), Color(0xFF6A4CFF), "WAVE_SMOOTH"),
    VisualOption("Mirror Twin", "موجة مزدوجة", "موجتان متعاكستان", Color(0xFFFF00E5), Color(0xFF00E5FF), "WAVE_MIRROR"),
    VisualOption("Spectrum Bars", "أعمدة طيفية", "أعمدة استوديو راقصة", Color(0xFF00F57A), Color(0xFF0B2E1B), "WAVE_BARS"),
    VisualOption("Pulse Dots", "نقاط نبضية", "كرات ضوئية متفاعلة", Color(0xFFFFC857), Color(0xFF281E0B), "WAVE_DOTS"),
    VisualOption("Hidden", "إخفاء الموجات", "واجهة هادئة بدون موجات", Color(0xFF6C738A), Color(0xFF121420), "WAVE_NONE")
)

private val WIDGET_VISUAL_OPTIONS = listOf(
    VisualOption("Auto Adaptive", "تلقائي ذكي", "يتحول لمربع أو طويل حسب سحبك", Color(0xFF00E5FF), Color(0xFF6A4CFF), "WIDGET_AUTO"),
    VisualOption("Square Card 2x2", "مربع بوستر 2x2", "شكل مربع بصورة أغنية كبيرة", Color(0xFFFF6B9D), Color(0xFF261230), "WIDGET_SQUARE"),
    VisualOption("Wide Pill 4x1", "شريط عريض 4x1", "شريط أفقي أنيق ومسطح", Color(0xFF00F57A), Color(0xFF0B2918), "WIDGET_WIDE"),
    VisualOption("Neon Capsule", "كبسولة دائرية", "حواف دائرية بالكامل مع نيون", Color(0xFFFFC857), Color(0xFF291F09), "WIDGET_CAPSULE")
)

private val MINI_VISUAL_OPTIONS = listOf(
    VisualOption("Floating Pill", "كبسولة عائمة", "مرفوعة بحواف دائرية", Color(0xFF8E54E9), Color(0xFF4776E6), "MINI_PILL"),
    VisualOption("Neon Dock", "منصة نيون مضيئة", "إطار متوهج بلون الأغنية", Color(0xFF00E5FF), Color(0xFF142238), "MINI_DOCK"),
    VisualOption("Full Bar", "شريط كامل العرض", "ملتصق بعرض الشاشة", Color(0xFFFFC857), Color(0xFF221C10), "MINI_BAR")
)

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setContent {
                    controller?.let { VyroRoyalApp(it, ::loadSongs) }
                }
            } catch (e: Exception) {
                setContent {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Text("VYRO Audio Engine Initializing...", color = Color.White)
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
fun VyroRoyalApp(player: Player, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()

    var langSetting by remember { mutableStateOf(prefs.getString("lang_setting", "AR") ?: "AR") }
    val isRtl = remember(langSetting) {
        val code = if (langSetting == "SYSTEM") Locale.getDefault().language.uppercase() else langSetting.uppercase()
        code.startsWith("AR")
    }

    // الإعدادات الافتراضية الجديدة: تصميم Royal Glass أنيق + تفاعل ديناميكي تلقائي مع صورة الأغنية
    var uiLayout by remember { mutableStateOf(prefs.getString("ui_layout", "Royal Glass") ?: "Royal Glass") }
    var themeName by remember { mutableStateOf(prefs.getString("theme_name", "Dynamic Cover Aura") ?: "Dynamic Cover Aura") }
    var dynamicPlayerColor by remember { mutableStateOf(prefs.getBoolean("dynamic_player_color", true)) }
    var discStyle by remember { mutableStateOf(prefs.getString("disc_style", "Dynamic Aura") ?: "Dynamic Aura") }
    var waveStyle by remember { mutableStateOf(prefs.getString("wave_style", "Smooth Wave") ?: "Smooth Wave") }
    var miniPlayerStyle by remember { mutableStateOf(prefs.getString("mini_player_style", "Floating Pill") ?: "Floating Pill") }
    var headerStyle by remember { mutableStateOf(prefs.getString("header_style", "Royal Clean") ?: "Royal Clean") }
    var widgetStyle by remember { mutableStateOf(prefs.getString("widget_style", "Auto Adaptive") ?: "Auto Adaptive") }

    var cornerRadiusDp by remember { mutableIntStateOf(prefs.getInt("corner_radius_dp", 20)) }
    var cardOpacity by remember { mutableFloatStateOf(prefs.getFloat("card_opacity", 0.22f)) }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }
    var borderWidthDp by remember { mutableFloatStateOf(prefs.getFloat("border_width_dp", 0.8f)) }
    var bgAnimIntensity by remember { mutableFloatStateOf(prefs.getFloat("bg_anim_intensity", 0.75f)) }

    var customBgUri by remember { mutableStateOf(prefs.getString("custom_bg_uri", null)) }
    var bgDimAlpha by remember { mutableFloatStateOf(prefs.getFloat("bg_dim_alpha", 0.40f)) }

    var pendingResetSection by remember { mutableStateOf<String?>(null) }

    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var permission by remember { mutableStateOf(false) }
    var currentSongId by remember { mutableLongStateOf(prefs.getLong("last_song_id", -1L)) }
    var position by remember { mutableLongStateOf(prefs.getLong("last_position_ms", 0L)) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }

    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(prefs.getString("sort_by", "TITLE") ?: "TITLE") }
    var tab by remember { mutableStateOf("Songs") }

    var likedIds by remember { mutableStateOf(loadIdSet(prefs.getString("liked_ids", "[]"))) }
    var recentIds by remember { mutableStateOf(loadIdList(prefs.getString("recent_ids", "[]"))) }
    var playCounts by remember { mutableStateOf(loadPlayCounts(prefs.getString("play_counts", "{}"))) }
    var playlists by remember { mutableStateOf(loadPlaylists(prefs.getString("playlists", "{}"))) }
    var customLyrics by remember { mutableStateOf(loadLyricsMap(prefs.getString("lyrics_map", "{}"))) }

    var crossfadeSec by remember { mutableIntStateOf(prefs.getInt("crossfade_sec", 0)) }
    var shakeSkipEnabled by remember { mutableStateOf(prefs.getBoolean("shake_skip_enabled", true)) }
    var isDriveMode by remember { mutableStateOf(false) }

    var loopPointA by remember { mutableLongStateOf(-1L) }
    var loopPointB by remember { mutableLongStateOf(-1L) }

    // إعدادات الصوت النقية الخالية من الغروشة + ميزة الصوت المحيطي 8D الجديدة
    var antiClipping by remember { mutableStateOf(prefs.getBoolean("anti_clipping", true)) }
    var spatial8DEnabled by remember { mutableStateOf(prefs.getBoolean("spatial_8d_enabled", false)) }
    var eqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Pure Hi-Fi") ?: "Pure Hi-Fi") }
    var bandGains by remember {
        mutableStateOf(loadFloatList(prefs.getString("band_gains", null)) ?: listOf(0.35f, 0.20f, 0.0f, 0.18f, 0.32f))
    }
    var bassStrength by remember { mutableIntStateOf(prefs.getInt("bass_strength", 40)) }
    var virtualizerStrength by remember { mutableIntStateOf(prefs.getInt("virtualizer_strength", 35)) }
    var loudnessGain by remember { mutableIntStateOf(prefs.getInt("loudness_gain", 0)) }
    var playbackSpeed by remember { mutableFloatStateOf(prefs.getFloat("playback_speed", 1f)) }
    var playbackPitch by remember { mutableFloatStateOf(prefs.getFloat("playback_pitch", 1f)) }

    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }
    var sleepTimerRemainingSec by remember { mutableIntStateOf(0) }

    var activePlaylistView by remember { mutableStateOf<String?>(null) }
    var showUiStudioSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForMenu by remember { mutableStateOf<Song?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForLyrics by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songForRingtone by remember { mutableStateOf<Song?>(null) }
    var songForDeleteConfirm by remember { mutableStateOf<Song?>(null) }
    var showSleepDialog by remember { mutableStateOf(false) }

    var coverPalette by remember { mutableStateOf(CoverPalette()) }

    fun saveState() {
        prefs.edit()
            .putString("lang_setting", langSetting)
            .putString("ui_layout", uiLayout)
            .putString("theme_name", themeName)
            .putBoolean("dynamic_player_color", dynamicPlayerColor)
            .putString("disc_style", discStyle)
            .putString("wave_style", waveStyle)
            .putString("mini_player_style", miniPlayerStyle)
            .putString("header_style", headerStyle)
            .putString("widget_style", widgetStyle)
            .putInt("corner_radius_dp", cornerRadiusDp)
            .putFloat("card_opacity", cardOpacity)
            .putFloat("font_scale", fontScale)
            .putFloat("border_width_dp", borderWidthDp)
            .putFloat("bg_anim_intensity", bgAnimIntensity)
            .putString("custom_bg_uri", customBgUri)
            .putFloat("bg_dim_alpha", bgDimAlpha)
            .putString("sort_by", sortBy)
            .putString("liked_ids", JSONArray(likedIds.toList()).toString())
            .putString("recent_ids", JSONArray(recentIds).toString())
            .putString("play_counts", encodePlayCounts(playCounts))
            .putString("playlists", encodePlaylists(playlists))
            .putString("lyrics_map", encodeLyricsMap(customLyrics))
            .putInt("crossfade_sec", crossfadeSec)
            .putBoolean("shake_skip_enabled", shakeSkipEnabled)
            .putBoolean("anti_clipping", antiClipping)
            .putBoolean("spatial_8d_enabled", spatial8DEnabled)
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

    fun executeSectionReset(section: String) {
        when (section) {
            "LAYOUT" -> {
                uiLayout = "Royal Glass"
                cardOpacity = 0.22f
            }
            "THEME" -> {
                themeName = "Dynamic Cover Aura"
                dynamicPlayerColor = true
                bgAnimIntensity = 0.75f
            }
            "DISC" -> discStyle = "Dynamic Aura"
            "WAVE" -> waveStyle = "Smooth Wave"
            "MINI" -> miniPlayerStyle = "Floating Pill"
            "HEADER" -> headerStyle = "Royal Clean"
            "WIDGET" -> widgetStyle = "Auto Adaptive"
            "DIMENSIONS" -> {
                cornerRadiusDp = 20
                cardOpacity = 0.22f
                fontScale = 1.0f
                borderWidthDp = 0.8f
                bgAnimIntensity = 0.75f
            }
            "WALLPAPER" -> {
                customBgUri = null
                bgDimAlpha = 0.40f
            }
            "DSP" -> {
                antiClipping = true
                spatial8DEnabled = false
                eqPreset = "Pure Hi-Fi"
                bandGains = listOf(0.35f, 0.20f, 0.0f, 0.18f, 0.32f)
                bassStrength = 40
                virtualizerStrength = 35
                loudnessGain = 0
                playbackSpeed = 1f
                playbackPitch = 1f
            }
            "ALL" -> {
                uiLayout = "Royal Glass"
                themeName = "Dynamic Cover Aura"
                dynamicPlayerColor = true
                discStyle = "Dynamic Aura"
                waveStyle = "Smooth Wave"
                miniPlayerStyle = "Floating Pill"
                headerStyle = "Royal Clean"
                widgetStyle = "Auto Adaptive"
                cornerRadiusDp = 20
                cardOpacity = 0.22f
                fontScale = 1.0f
                borderWidthDp = 0.8f
                bgAnimIntensity = 0.75f
                customBgUri = null
                bgDimAlpha = 0.40f
                antiClipping = true
                spatial8DEnabled = false
                eqPreset = "Pure Hi-Fi"
                bandGains = listOf(0.35f, 0.20f, 0.0f, 0.18f, 0.32f)
                bassStrength = 40
                virtualizerStrength = 35
                loudnessGain = 0
                playbackSpeed = 1f
                playbackPitch = 1f
            }
        }
        saveState()
        Toast.makeText(context, "تمت إعادة الضبط للإعدادات الافتراضية ✓", Toast.LENGTH_SHORT).show()
    }

    fun requestPinHomeWidget() {
        runCatching {
            val mgr = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, VyroWidgetProvider::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(context, "اضغط مطولاً على الشاشة الرئيسية واختر ويدجت VYRO MUSIC", Toast.LENGTH_LONG).show()
            }
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                query = spoken
                searchExpanded = true
                tab = "Songs"
            }
        }
    }

    val deleteResultLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            songs = loadSongs()
            Toast.makeText(context, "تم حذف الملف", Toast.LENGTH_SHORT).show()
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customBgUri = uri.toString()
            bgDimAlpha = 0.35f
            saveState()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", "3.2.0-ROYAL")
                    put("liked_ids", JSONArray(likedIds.toList()))
                    put("recent_ids", JSONArray(recentIds))
                    put("playlists", JSONObject(encodePlaylists(playlists)))
                    put("lyrics_map", JSONObject(encodeLyricsMap(customLyrics)))
                    put("ui_layout", uiLayout)
                    put("theme_name", themeName)
                    put("widget_style", widgetStyle)
                    put("eq_preset", eqPreset)
                    put("band_gains", JSONArray(bandGains))
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
                if (root.has("recent_ids")) recentIds = loadIdList(root.getJSONArray("recent_ids").toString())
                if (root.has("playlists")) playlists = loadPlaylists(root.getJSONObject("playlists").toString())
                if (root.has("lyrics_map")) customLyrics = loadLyricsMap(root.getJSONObject("lyrics_map").toString())
                if (root.has("ui_layout")) uiLayout = root.getString("ui_layout")
                if (root.has("theme_name")) themeName = root.getString("theme_name")
                if (root.has("widget_style")) widgetStyle = root.getString("widget_style")
                saveState()
                Toast.makeText(context, "تمت الاستعادة بنجاح ✓", Toast.LENGTH_SHORT).show()
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
                val gX = v[0] / SensorManager.GRAVITY_EARTH
                val gY = v[1] / SensorManager.GRAVITY_EARTH
                val gZ = v[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
                if (gForce > 2.65f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 1200L && player.isPlaying) {
                        lastShakeTime = now
                        player.seekToNext()
                        Toast.makeText(context, "⏭ تم تغيير الأغنية بالهز!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    DisposableEffect(permission) {
        if (!permission) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                songs = loadSongs()
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val currentSong = songs.firstOrNull { it.id == currentSongId }

    LaunchedEffect(currentSong?.albumId) {
        if (currentSong == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val extracted = extractCoverPalette(context, currentSong.albumArtUri)
            withContext(Dispatchers.Main) { coverPalette = extracted }
        }
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

    // حلقة تحديث الوقت + Crossfade + ميزة 8D Spatial Audio المحيطية الجديدة
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
                    if (tick % 6 == 0) {
                        prefs.edit()
                            .putLong("last_song_id", currentSongId)
                            .putLong("last_position_ms", position)
                            .apply()
                    }
                }
            }
            if (sleepTimerRemainingSec <= 0) {
                if (spatial8DEnabled && player.isPlaying) {
                    angle8D += 0.16f
                    val waveVol = 0.86f + 0.14f * sin(angle8D)
                    player.volume = waveVol.coerceIn(0.72f, 1f)
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

    // اللون الأساسي يتفاعل تلقائياً مع صورة الأغنية عند تفعيل Dynamic Cover Aura أو dynamicPlayerColor
    val primaryAccent by animateColorAsState(
        targetValue = when {
            themeName == "Dynamic Cover Aura" || (dynamicPlayerColor && tab == "Now Playing") -> coverPalette.dominant
            themeName == "Stadium Champions" -> Color(0xFF00F57A)
            themeName == "Camp Blaugrana" -> Color(0xFFED1B4E)
            themeName == "Ocean Bubbles" -> Color(0xFF00E5FF)
            themeName == "Cyber Neon City" -> Color(0xFFFF00E5)
            themeName == "Red Devil Arena" -> Color(0xFFFF2A3C)
            themeName == "Golden Pharaoh VIP" -> Color(0xFFFFC857)
            themeName == "Matrix Cyber Rain" -> Color(0xFF00FF66)
            else -> Color(0xFF9E7BFF)
        },
        tween(500),
        label = "accent"
    )

    val bgNight by animateColorAsState(
        targetValue = when {
            uiLayout == "AMOLED Pure" || themeName == "Pitch Black AMOLED" -> Color(0xFF000000)
            (themeName == "Dynamic Cover Aura" || dynamicPlayerColor) && currentSong != null -> coverPalette.deepTint
            themeName == "Stadium Champions" -> Color(0xFF04140A)
            themeName == "Camp Blaugrana" -> Color(0xFF07091E)
            themeName == "Ocean Bubbles" -> Color(0xFF031424)
            themeName == "Cyber Neon City" -> Color(0xFF0B0418)
            else -> Color(0xFF060812)
        },
        tween(600),
        label = "bg"
    )

    val effectiveCardAlpha = when {
        !customBgUri.isNullOrBlank() -> cardOpacity.coerceIn(0.10f, 0.42f)
        uiLayout == "AMOLED Pure" -> cardOpacity.coerceIn(0.0f, 0.15f)
        else -> cardOpacity.coerceIn(0.14f, 0.65f)
    }
    val surfacePanel = Color(0xFF15192B).copy(alpha = effectiveCardAlpha)
    val mutedText = Color(0xFFA6B0C9)

    val sortedSongs = remember(songs, query, sortBy, likedIds) {
        val base = if (sortBy == "FAV") songs.filter { likedIds.contains(it.id) } else songs
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

    // نافذة تأكيد إعادة ضبط الإعدادات الافتراضية
    pendingResetSection?.let { sec ->
        AlertDialog(
            onDismissRequest = { pendingResetSection = null },
            containerColor = Color(0xFF0E1222),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, null, tint = primaryAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("إعادة ضبط الإعدادات", color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            },
            text = {
                Text(
                    "هل أنت متأكد من أنك تريد إعادة ضبط الإعدادات الافتراضية؟",
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        executeSectionReset(sec)
                        pendingResetSection = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                ) {
                    Text("تأكيد", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetSection = null }) {
                    Text("إلغاء", color = mutedText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // استوديو المعاينة المصورة السريع من الهيدر
    if (showUiStudioSheet) {
        AlertDialog(
            onDismissRequest = { showUiStudioSheet = false },
            containerColor = Color(0xFF0A0D18),
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("معرض تصميم الواجهة والسمات", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    ResetChipButton(accent = Color(0xFFFF5C5C), label = "↺ ضبط الكل") {
                        pendingResetSection = "ALL"
                    }
                }
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SectionHeaderWithReset("1. شكل تخطيط الواجهة (اسحب واختار)", primaryAccent) {
                        pendingResetSection = "LAYOUT"
                    }
                    VisualPreviewCarousel(
                        options = LAYOUT_VISUAL_OPTIONS,
                        selectedId = uiLayout,
                        defaultId = "Royal Glass",
                        accent = primaryAccent,
                        onSelectOrToggle = { chosen ->
                            uiLayout = chosen
                            cardOpacity = if (chosen == "AMOLED Pure") 0.0f else 0.22f
                            saveState()
                        }
                    )

                    SectionHeaderWithReset("2. السمات والخلفيات الحية", primaryAccent) {
                        pendingResetSection = "THEME"
                    }
                    VisualPreviewCarousel(
                        options = THEME_VISUAL_OPTIONS,
                        selectedId = themeName,
                        defaultId = "Dynamic Cover Aura",
                        accent = primaryAccent,
                        onSelectOrToggle = { chosen ->
                            themeName = chosen
                            saveState()
                        }
                    )

                    SectionHeaderWithReset("3. شكل مشغل الأغنية (Disc Style)", primaryAccent) {
                        pendingResetSection = "DISC"
                    }
                    VisualPreviewCarousel(
                        options = DISC_VISUAL_OPTIONS,
                        selectedId = discStyle,
                        defaultId = "Dynamic Aura",
                        accent = primaryAccent,
                        onSelectOrToggle = { chosen ->
                            discStyle = chosen
                            saveState()
                        }
                    )

                    SectionHeaderWithReset("4. شكل ويدجت الشاشة الرئيسية", primaryAccent) {
                        pendingResetSection = "WIDGET"
                    }
                    VisualPreviewCarousel(
                        options = WIDGET_VISUAL_OPTIONS,
                        selectedId = widgetStyle,
                        defaultId = "Auto Adaptive",
                        accent = primaryAccent,
                        onSelectOrToggle = { chosen ->
                            widgetStyle = chosen
                            saveState()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUiStudioSheet = false }) {
                    Text("تم", color = primaryAccent, fontWeight = FontWeight.Black)
                }
            }
        )
    }

    songForMenu?.let { target ->
        val isLiked = likedIds.contains(target.id)
        AlertDialog(
            onDismissRequest = { songForMenu = null },
            containerColor = Color(0xFF0E101C),
            title = {
                Column {
                    Text(target.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(target.artist, color = primaryAccent, fontSize = 12.sp, maxLines = 1)
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MenuActionRow(Icons.Default.PlayArrow, "تشغيل الآن", primaryAccent) {
                        val idx = songs.indexOfFirst { it.id == target.id }
                        if (idx >= 0) playQueue(songs, idx, 0L, true)
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.QueuePlayNext, "تشغيل كالتالي في الطابور", Color.White) {
                        val nextIdx = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
                        player.addMediaItem(nextIdx, buildMediaItem(target))
                        Toast.makeText(context, "تمت الإضافة للطابور", Toast.LENGTH_SHORT).show()
                        songForMenu = null
                    }
                    MenuActionRow(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "إضافة / إزالة من المفضلة", Color.White) {
                        likedIds = if (isLiked) likedIds - target.id else likedIds + target.id
                        saveState()
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.PlaylistAdd, "إضافة إلى قائمة تشغيل...", Color.White) {
                        songForAddToPlaylist = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.RingVolume, "قص وتعيين كنغمة رنين", Color(0xFFFFC857)) {
                        songForRingtone = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Lyrics, "كلمات الأغنية المتزامنة (LRC)", Color.White) {
                        songForLyrics = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.Share, "مشاركة الملف الصوتي", Color.White) {
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
                    MenuActionRow(Icons.Default.Info, "معلومات الملف التفصيلية", Color.White) {
                        songForInfo = target
                        songForMenu = null
                    }
                    MenuActionRow(Icons.Default.DeleteForever, "حذف من الجهاز", Color(0xFFFF5C5C)) {
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
            containerColor = Color(0xFF0E101C),
            title = { Text("قص وتعيين كنغمة رنين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(target.title, color = primaryAccent, maxLines = 1)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            Text("${formatTime((startSec * 1000).toLong())}  →  ${formatTime((endSec * 1000).toLong())}", color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = startSec,
                                onValueChange = { startSec = it.coerceIn(0f, (endSec - 2f).coerceAtLeast(0f)) },
                                valueRange = 0f..safeTotal
                            )
                            Slider(
                                value = endSec,
                                onValueChange = { endSec = it.coerceIn((startSec + 2f).coerceAtMost(safeTotal), safeTotal) },
                                valueRange = 0f..safeTotal
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (!Settings.System.canWrite(context)) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                            } else {
                                cutting = true
                                appScope.launch {
                                    withContext(Dispatchers.IO) {
                                        cutAndSetRingtone(context, target, (startSec * 1000).toLong(), (endSec * 1000).toLong())
                                    }
                                    cutting = false
                                    Toast.makeText(context, "تم تعيين النغمة بنجاح!", Toast.LENGTH_SHORT).show()
                                    songForRingtone = null
                                }
                            }
                        },
                        enabled = !cutting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Text(if (cutting) "..." else "قص وتعيين المقطع كنغمة", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForRingtone = null }) { Text("إغلاق", color = mutedText) } }
        )
    }

    songForInfo?.let { target ->
        val mb = "%.2f MB".format(target.sizeBytes / (1024f * 1024f))
        val plays = playCounts[target.id] ?: 0
        AlertDialog(
            onDismissRequest = { songForInfo = null },
            containerColor = Color(0xFF0E101C),
            title = { Text("معلومات الملف الصوتي", color = primaryAccent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoItem("الأغنية", target.title)
                    InfoItem("الفنان", target.artist)
                    InfoItem("الألبوم", target.album)
                    InfoItem("المدة والحجم", "${formatTime(target.duration)} • $mb")
                    InfoItem("مرات الاستماع", "$plays مرة")
                    InfoItem("المسار", target.filePath ?: target.uri.toString())
                }
            },
            confirmButton = { TextButton(onClick = { songForInfo = null }) { Text("حسناً", color = primaryAccent) } }
        )
    }

    songForDeleteConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { songForDeleteConfirm = null },
            containerColor = Color(0xFF0E101C),
            title = { Text("حذف من الجهاز", color = Color(0xFFFF5C5C), fontWeight = FontWeight.Bold) },
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
                }) { Text("تأكيد الحذف", color = Color(0xFFFF5C5C), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { songForDeleteConfirm = null }) { Text("إلغاء", color = Color.White) } }
        )
    }

    songForAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForAddToPlaylist = null },
            containerColor = Color(0xFF0E101C),
            title = { Text("إضافة إلى قائمة تشغيل", color = Color.White) },
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
                        ) { Text(pName, color = Color.White) }
                    }
                    OutlinedButton(
                        onClick = {
                            songForAddToPlaylist = null
                            showCreatePlaylistDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ إنشاء قائمة جديدة", color = primaryAccent) }
                }
            },
            confirmButton = { TextButton(onClick = { songForAddToPlaylist = null }) { Text("إغلاق") } }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = Color(0xFF0E101C),
            title = { Text("قائمة تشغيل جديدة", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("مثال: تراكات الجيم / فونك") },
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
            containerColor = Color(0xFF0C0E18),
            title = { Text("كلمات الأغنية • ${targetSong.title}", color = Color.White, fontSize = 15.sp, maxLines = 1) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingMode) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("[00:12.00] الصق كلمات الأغنية هنا...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else if (parsedLrc.isNotEmpty()) {
                        val activeIdx = parsedLrc.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
                        parsedLrc.forEachIndexed { idx, line ->
                            val isCurrent = idx == activeIdx
                            Text(
                                text = line.text,
                                color = if (isCurrent) primaryAccent else Color.White.copy(alpha = 0.55f),
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                fontSize = if (isCurrent) 16.sp else 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { player.seekTo(line.timeMs) }
                                    .padding(vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(draftText.ifBlank { "لا توجد كلمات مضافة بعد. اضغط تعديل للصق الكلمات." }, color = Color.White, lineHeight = 22.sp)
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

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = Color(0xFF0E101C),
            title = { Text("مؤقت النوم التدريجي", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { startSleepTimer(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins دقيقة (خفض تدريجي للصوت)", color = Color.White) }
                    }
                    if (sleepTimerRemainingSec > 0) {
                        TextButton(
                            onClick = { startSleepTimer(0); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إلغاء المؤقت", color = Color(0xFFFF5C5C)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("إغلاق") } }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
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
                // رسم الخلفية الحية أو هالة صورة الأغنية الديناميكية
                if (bgAnimIntensity > 0.02f && uiLayout != "AMOLED Pure") {
                    AnimatedThemeBackdrop(
                        themeName = themeName,
                        accent = primaryAccent,
                        secondaryAccent = coverPalette.secondary,
                        intensity = bgAnimIntensity,
                        playing = playing
                    )
                }

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
                            .background(Color.Black.copy(alpha = bgDimAlpha.coerceIn(0.05f, 0.88f)))
                    )
                }

                if (isDriveMode && currentSong != null) {
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
                } else {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            Column(
                                Modifier.background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, bgNight.copy(alpha = 0.88f), bgNight.copy(alpha = 0.98f))
                                    )
                                )
                            ) {
                                if (currentSong != null && tab != "Now Playing") {
                                    SleekMiniPlayerPill(
                                        song = currentSong,
                                        playing = playing,
                                        position = position,
                                        duration = duration,
                                        accent = if (dynamicPlayerColor) coverPalette.dominant else primaryAccent,
                                        miniStyle = miniPlayerStyle,
                                        cornerDp = cornerRadiusDp,
                                        onToggle = {
                                            if (player.mediaItemCount == 0) {
                                                val idx = songs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                                playQueue(songs, idx, position, true)
                                            } else if (player.isPlaying) player.pause() else player.play()
                                        },
                                        onNext = { player.seekToNext() },
                                        onPrevious = { player.seekToPrevious() },
                                        onOpen = { tab = "Now Playing" }
                                    )
                                }
                                NavigationBar(
                                    containerColor = Color(0xFF070913).copy(alpha = 0.94f),
                                    tonalElevation = 0.dp
                                ) {
                                    listOf(
                                        "Songs" to "المكتبة",
                                        "Now Playing" to "المشغل",
                                        "EQ Studio" to "الصوت 8D",
                                        "Playlists" to "القوائم",
                                        "Settings" to "التخصيص"
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
                                                        "EQ Studio" -> Icons.Default.GraphicEq
                                                        "Playlists" -> Icons.Default.QueueMusic
                                                        else -> Icons.Default.Palette
                                                    },
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = primaryAccent,
                                                selectedTextColor = primaryAccent,
                                                indicatorColor = primaryAccent.copy(alpha = 0.16f),
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
                                .padding(horizontal = 14.dp)
                        ) {
                            // هيدر ملكي نظيف ومرتب بدون زحمة
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.linearGradient(listOf(primaryAccent, coverPalette.secondary))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.GraphicEq, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "VYRO",
                                            color = Color.White,
                                            fontSize = if (headerStyle == "Centered VIP") 22.sp else 19.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.8.sp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(primaryAccent.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ROYAL 3.2", color = primaryAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Text(
                                        "${songs.size} مقطع صوتي • استوديو محمد طارق",
                                        color = mutedText,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        tab = "Songs"
                                        searchExpanded = !searchExpanded
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (searchExpanded || query.isNotEmpty()) primaryAccent.copy(alpha = 0.24f)
                                            else Color.White.copy(alpha = 0.07f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(Icons.Default.Search, "Search", tint = if (searchExpanded) primaryAccent else Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { showUiStudioSheet = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(primaryAccent.copy(alpha = 0.20f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Palette, "Visual Studio", tint = primaryAccent, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { if (currentSong != null) isDriveMode = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.07f), CircleShape)
                                ) {
                                    Icon(Icons.Default.DirectionsCar, "Drive Mode", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                            when (tab) {
                                "Now Playing" -> {
                                    if (currentSong != null) {
                                        UltraNowPlayingScreen(
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
                                            dynamicPlayerColor = dynamicPlayerColor,
                                            spatial8DEnabled = spatial8DEnabled,
                                            cornerDp = cornerRadiusDp,
                                            loopA = loopPointA,
                                            loopB = loopPointB,
                                            accent = if (dynamicPlayerColor) coverPalette.dominant else primaryAccent,
                                            palette = coverPalette,
                                            onTogglePlay = {
                                                if (player.mediaItemCount == 0) {
                                                    val idx = songs.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
                                                    playQueue(songs, idx, position, true)
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
                                            onToggleDynamicColor = {
                                                dynamicPlayerColor = !dynamicPlayerColor
                                                saveState()
                                            },
                                            onToggle8D = {
                                                spatial8DEnabled = !spatial8DEnabled
                                                saveState()
                                            },
                                            onToggleABLoop = {
                                                when {
                                                    loopPointA < 0L -> {
                                                        loopPointA = position
                                                        Toast.makeText(context, "بداية التكرار A: ${formatTime(position)}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    loopPointB < 0L && position > loopPointA + 1000L -> {
                                                        loopPointB = position
                                                        Toast.makeText(context, "تم تفعيل تكرار A-B!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    else -> {
                                                        loopPointA = -1L
                                                        loopPointB = -1L
                                                    }
                                                }
                                            },
                                            onOpenSleep = { showSleepDialog = true },
                                            onOpenLyrics = { songForLyrics = currentSong },
                                            onOpenUiStudio = { showUiStudioSheet = true },
                                            onOpenMenu = { songForMenu = currentSong }
                                        )
                                    } else {
                                        EmptyState("اختر مقطعا صوتيا لبدء الاستماع", primaryAccent)
                                    }
                                }

                                "EQ Studio" -> {
                                    UltraDspStudioScreen(
                                        eqPreset = eqPreset,
                                        bandGains = bandGains,
                                        bassStrength = bassStrength,
                                        virtualizerStrength = virtualizerStrength,
                                        loudnessGain = loudnessGain,
                                        playbackSpeed = playbackSpeed,
                                        playbackPitch = playbackPitch,
                                        antiClipping = antiClipping,
                                        spatial8D = spatial8DEnabled,
                                        accent = primaryAccent,
                                        cornerDp = cornerRadiusDp,
                                        onSelectPreset = { preset ->
                                            if (eqPreset == preset && preset != "Pure Hi-Fi") {
                                                // الضغط مرة ثانية يلغي التحديد ويرجع للوضع النقي الافتراضي
                                                eqPreset = "Pure Hi-Fi"
                                                bandGains = listOf(0.35f, 0.20f, 0.0f, 0.18f, 0.32f)
                                                bassStrength = 40
                                                loudnessGain = 0
                                            } else {
                                                eqPreset = preset
                                                when (preset) {
                                                    "Pure Hi-Fi" -> {
                                                        bandGains = listOf(0.35f, 0.20f, 0.0f, 0.18f, 0.32f)
                                                        bassStrength = 40
                                                        loudnessGain = 0
                                                    }
                                                    "Clean Phonk" -> {
                                                        bandGains = listOf(0.65f, 0.38f, -0.08f, 0.35f, 0.55f)
                                                        bassStrength = 55
                                                        loudnessGain = 15
                                                    }
                                                    "Gym Beast" -> {
                                                        bandGains = listOf(0.75f, 0.50f, 0.05f, 0.40f, 0.60f)
                                                        bassStrength = 65
                                                        loudnessGain = 25
                                                    }
                                                    "Vocal Studio" -> {
                                                        bandGains = listOf(-0.10f, 0.20f, 0.65f, 0.50f, 0.25f)
                                                        bassStrength = 20
                                                        loudnessGain = 5
                                                    }
                                                    "Night Chill" -> {
                                                        bandGains = listOf(0.40f, 0.25f, -0.15f, -0.10f, 0.15f)
                                                        bassStrength = 35
                                                        loudnessGain = 0
                                                    }
                                                    else -> {
                                                        bandGains = listOf(0f, 0f, 0f, 0f, 0f)
                                                        bassStrength = 0
                                                        loudnessGain = 0
                                                    }
                                                }
                                            }
                                            saveState()
                                        },
                                        onBandChange = { idx, value ->
                                            eqPreset = "Custom"
                                            bandGains = bandGains.toMutableList().also { it[idx] = value }
                                            saveState()
                                        },
                                        onBassChange = { bassStrength = it; saveState() },
                                        onVirtChange = { virtualizerStrength = it; saveState() },
                                        onLoudnessChange = { loudnessGain = it; saveState() },
                                        onSpeedChange = { playbackSpeed = it; saveState() },
                                        onPitchChange = { playbackPitch = it; saveState() },
                                        onToggleAntiClipping = { antiClipping = !antiClipping; saveState() },
                                        onToggle8D = { spatial8DEnabled = !spatial8DEnabled; saveState() },
                                        onRequestResetDsp = { pendingResetSection = "DSP" },
                                        onOpenSystemEq = {
                                            runCatching {
                                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                                }
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                }

                                "Playlists" -> {
                                    SmartPlaylistsScreen(
                                        songs = songs,
                                        likedIds = likedIds,
                                        recentIds = recentIds,
                                        playCounts = playCounts,
                                        playlists = playlists,
                                        activeView = activePlaylistView,
                                        currentSongId = currentSongId,
                                        accent = primaryAccent,
                                        panel = surfacePanel,
                                        cornerDp = cornerRadiusDp,
                                        borderWidthDp = borderWidthDp,
                                        fontScale = fontScale,
                                        onChangeView = { activePlaylistView = it },
                                        onCreateNew = { showCreatePlaylistDialog = true },
                                        onDeletePlaylist = { playlists = playlists - it; saveState() },
                                        onPlayList = { list, idx -> playQueue(list, idx, 0L, true) },
                                        onMoreSong = { songForMenu = it },
                                        onSelectAlbum = { alb ->
                                            query = alb
                                            searchExpanded = true
                                            tab = "Songs"
                                        }
                                    )
                                }

                                "Settings" -> {
                                    UltraSettingsScreen(
                                        uiLayout = uiLayout,
                                        themeName = themeName,
                                        dynamicPlayerColor = dynamicPlayerColor,
                                        discStyle = discStyle,
                                        waveStyle = waveStyle,
                                        miniPlayerStyle = miniPlayerStyle,
                                        headerStyle = headerStyle,
                                        widgetStyle = widgetStyle,
                                        langSetting = langSetting,
                                        customBgUri = customBgUri,
                                        bgDimAlpha = bgDimAlpha,
                                        cornerRadiusDp = cornerRadiusDp,
                                        cardOpacity = cardOpacity,
                                        borderWidthDp = borderWidthDp,
                                        fontScale = fontScale,
                                        bgAnimIntensity = bgAnimIntensity,
                                        shakeSkipEnabled = shakeSkipEnabled,
                                        crossfadeSec = crossfadeSec,
                                        sleepRemainingSec = sleepTimerRemainingSec,
                                        tracksPlayedStat = prefs.getInt("stat_tracks_played", 0),
                                        accent = primaryAccent,
                                        onSelectLayout = {
                                            uiLayout = it
                                            cardOpacity = if (it == "AMOLED Pure") 0f else 0.22f
                                            saveState()
                                        },
                                        onSelectTheme = { themeName = it; saveState() },
                                        onToggleDynamicColor = { dynamicPlayerColor = !dynamicPlayerColor; saveState() },
                                        onSelectDisc = { discStyle = it; saveState() },
                                        onSelectWave = { waveStyle = it; saveState() },
                                        onSelectMini = { miniPlayerStyle = it; saveState() },
                                        onSelectHeader = { headerStyle = it; saveState() },
                                        onSelectWidget = { widgetStyle = it; saveState() },
                                        onSelectLang = { langSetting = it; saveState() },
                                        onPickBg = { bgImagePicker.launch(arrayOf("image/*")) },
                                        onClearBg = { customBgUri = null; saveState() },
                                        onChangeDim = { bgDimAlpha = it; saveState() },
                                        onChangeCorner = { cornerRadiusDp = it; saveState() },
                                        onChangeOpacity = { cardOpacity = it; saveState() },
                                        onChangeBorder = { borderWidthDp = it; saveState() },
                                        onChangeFont = { fontScale = it; saveState() },
                                        onChangeAnimIntensity = { bgAnimIntensity = it; saveState() },
                                        onToggleShake = { shakeSkipEnabled = !shakeSkipEnabled; saveState() },
                                        onChangeCrossfade = { crossfadeSec = it; saveState() },
                                        onOpenSleep = { showSleepDialog = true },
                                        onPinWidget = { requestPinHomeWidget() },
                                        onRequestResetSection = { pendingResetSection = it },
                                        onBackup = { backupLauncher.launch("vyro_royal_backup.json") },
                                        onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                                    )
                                }

                                else -> {
                                    AnimatedVisibility(visible = searchExpanded || query.isNotEmpty()) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                                .clip(RoundedCornerShape(cornerRadiusDp.dp))
                                                .background(Color(0xFF101322).copy(alpha = 0.94f))
                                                .border(1.dp, primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(cornerRadiusDp.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Search, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                OutlinedTextField(
                                                    value = query,
                                                    onValueChange = { query = it },
                                                    modifier = Modifier.weight(1f),
                                                    placeholder = { Text("ابحث عن أغنية أو فنان أو ألبوم...", color = mutedText, fontSize = 12.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                IconButton(onClick = {
                                                    runCatching {
                                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        }
                                                        voiceSearchLauncher.launch(intent)
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Mic, "Voice Search", tint = primaryAccent)
                                                }
                                                IconButton(onClick = {
                                                    query = ""
                                                    searchExpanded = false
                                                }) {
                                                    Icon(Icons.Default.Close, "Close", tint = mutedText)
                                                }
                                            }
                                            Row(
                                                Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf(
                                                    "TITLE" to "أبجدي A-Z",
                                                    "ARTIST" to "الفنان",
                                                    "DURATION" to "الأطول",
                                                    "NEWEST" to "الأحدث",
                                                    "FAV" to "❤ المفضلة"
                                                ).forEach { (key, label) ->
                                                    FilterChip(
                                                        selected = sortBy == key,
                                                        onClick = {
                                                            sortBy = if (sortBy == key && key != "TITLE") "TITLE" else key
                                                            saveState()
                                                        },
                                                        label = { Text(label, fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // شريط أنيق ومختصر للوصول السريع للمفضلة واستوديو الصور والـ 8D
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        QuickActionPill(
                                            label = "🎨 معرض الواجهات والسمات",
                                            active = false,
                                            accent = Color(0xFFFFC857),
                                            onClick = { showUiStudioSheet = true }
                                        )
                                        QuickActionPill(
                                            label = if (spatial8DEnabled) "🎧 صوت 8D: مفعل" else "🎧 تفعيل صوت 8D",
                                            active = spatial8DEnabled,
                                            accent = primaryAccent,
                                            onClick = {
                                                spatial8DEnabled = !spatial8DEnabled
                                                saveState()
                                            }
                                        )
                                        QuickActionPill(
                                            label = "❤ المفضلة (${likedIds.size})",
                                            active = sortBy == "FAV",
                                            accent = Color(0xFFFF4D79),
                                            onClick = {
                                                sortBy = if (sortBy == "FAV") "TITLE" else "FAV"
                                                saveState()
                                            }
                                        )
                                        QuickActionPill(
                                            label = "🔀 تشغيل عشوائي",
                                            active = shuffle,
                                            accent = primaryAccent,
                                            onClick = {
                                                if (sortedSongs.isNotEmpty()) {
                                                    shuffle = true
                                                    player.shuffleModeEnabled = true
                                                    playQueue(sortedSongs.shuffled(), 0, 0L, true)
                                                }
                                            }
                                        )
                                    }

                                    if (!permission) {
                                        EmptyState("يرجى منح إذن الوصول للملفات الصوتية لعرض مكتبتك", primaryAccent)
                                    } else if (sortedSongs.isEmpty()) {
                                        EmptyState("لا توجد مقاطع صوتية مطابقة", primaryAccent)
                                    } else if (uiLayout == "Bento Grid" || uiLayout == "Poster Wall 3x3") {
                                        val cols = if (uiLayout == "Poster Wall 3x3") 3 else 2
                                        val chunks = remember(sortedSongs, cols) { sortedSongs.chunked(cols) }
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            itemsIndexed(chunks) { rowIndex, rowItems ->
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    rowItems.forEachIndexed { colIndex, song ->
                                                        val realIdx = rowIndex * cols + colIndex
                                                        Box(Modifier.weight(1f)) {
                                                            BentoSongCard(
                                                                song = song,
                                                                active = currentSongId == song.id,
                                                                isLiked = likedIds.contains(song.id),
                                                                compactPoster = cols == 3,
                                                                accent = primaryAccent,
                                                                cornerDp = cornerRadiusDp,
                                                                borderWidthDp = borderWidthDp,
                                                                fontScale = fontScale,
                                                                onClick = { playQueue(sortedSongs, realIdx, 0L, true) },
                                                                onMore = { songForMenu = song }
                                                            )
                                                        }
                                                    }
                                                    repeat(cols - rowItems.size) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(
                                                when (uiLayout) {
                                                    "AMOLED Pure" -> 2.dp
                                                    "Stadium Cards" -> 10.dp
                                                    else -> 7.dp
                                                }
                                            ),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            itemsIndexed(sortedSongs) { idx, song ->
                                                SongRow(
                                                    song = song,
                                                    active = currentSongId == song.id,
                                                    isLiked = likedIds.contains(song.id),
                                                    uiLayout = uiLayout,
                                                    accent = primaryAccent,
                                                    panel = surfacePanel,
                                                    cornerDp = cornerRadiusDp,
                                                    borderWidthDp = borderWidthDp,
                                                    fontScale = fontScale,
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
}

// معرض البطاقات المصورة (تقلب في الصور وتشوف شكل الواجهة وتحت كل صورة زر "تحديد" أو "إلغاء التحديد")
@Composable
private fun VisualPreviewCarousel(
    options: List<VisualOption>,
    selectedId: String,
    defaultId: String,
    accent: Color,
    onSelectOrToggle: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { opt ->
            val isSelected = selectedId == opt.id
            Column(
                Modifier
                    .width(154.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121628).copy(alpha = 0.92f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        val next = if (isSelected && opt.id != defaultId) defaultId else opt.id
                        onSelectOrToggle(next)
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // صورة المعاينة الحية لشكل الخيار
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(opt.c1.copy(alpha = 0.35f), opt.c2))),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width
                        val h = size.height
                        when (opt.previewKind) {
                            "LAYOUT_GLASS", "LAYOUT_COMPACT", "LAYOUT_AMOLED" -> {
                                for (r in 0..2) {
                                    val y = r * (h / 3f) + 4f
                                    drawRoundRect(
                                        color = if (opt.previewKind == "LAYOUT_AMOLED") Color.White.copy(alpha = 0.10f) else opt.c1.copy(alpha = 0.45f),
                                        topLeft = Offset(0f, y),
                                        size = Size(w, h / 3.8f),
                                        cornerRadius = CornerRadius(10f, 10f)
                                    )
                                }
                            }
                            "LAYOUT_GRID2", "LAYOUT_GRID3" -> {
                                val cols = if (opt.previewKind == "LAYOUT_GRID3") 3 else 2
                                val cellW = (w - (cols - 1) * 8f) / cols
                                for (c in 0 until cols) {
                                    drawRoundRect(
                                        color = opt.c1.copy(alpha = 0.55f),
                                        topLeft = Offset(c * (cellW + 8f), 4f),
                                        size = Size(cellW, h - 8f),
                                        cornerRadius = CornerRadius(12f, 12f)
                                    )
                                }
                            }
                            "LAYOUT_CAPSULE", "WIDGET_CAPSULE" -> {
                                drawRoundRect(
                                    color = opt.c1.copy(alpha = 0.55f),
                                    topLeft = Offset(4f, h * 0.25f),
                                    size = Size(w - 8f, h * 0.5f),
                                    cornerRadius = CornerRadius(50f, 50f)
                                )
                            }
                            "THEME_STADIUM", "LAYOUT_STADIUM", "DISC_CREST" -> {
                                drawRect(
                                    color = Color.White.copy(alpha = 0.35f),
                                    topLeft = Offset(6f, 6f),
                                    size = Size(w - 12f, h - 12f),
                                    style = Stroke(width = 2.5f)
                                )
                                drawCircle(
                                    color = opt.c1,
                                    radius = h * 0.26f,
                                    center = Offset(w / 2f, h / 2f),
                                    style = Stroke(width = 3f)
                                )
                            }
                            "THEME_OCEAN" -> {
                                for (b in 0..4) {
                                    drawCircle(
                                        color = opt.c1.copy(alpha = 0.65f),
                                        radius = 6f + b * 2f,
                                        center = Offset((w / 5f) * b + 12f, h * (0.7f - b * 0.1f)),
                                        style = Stroke(width = 2f)
                                    )
                                }
                            }
                            "WIDGET_SQUARE", "DISC_SQUARE" -> {
                                val side = minOf(w, h) * 0.78f
                                drawRoundRect(
                                    color = opt.c1.copy(alpha = 0.65f),
                                    topLeft = Offset((w - side) / 2f, (h - side) / 2f),
                                    size = Size(side, side),
                                    cornerRadius = CornerRadius(16f, 16f)
                                )
                            }
                            "WIDGET_WIDE", "MINI_BAR", "MINI_PILL", "MINI_DOCK" -> {
                                drawRoundRect(
                                    color = opt.c1.copy(alpha = 0.65f),
                                    topLeft = Offset(4f, h * 0.28f),
                                    size = Size(w - 8f, h * 0.44f),
                                    cornerRadius = CornerRadius(18f, 18f)
                                )
                            }
                            "WAVE_BARS" -> {
                                val bars = 10
                                val bw = w / bars
                                for (i in 0 until bars) {
                                    val bh = h * (0.3f + (i % 3) * 0.2f)
                                    drawRoundRect(
                                        color = opt.c1,
                                        topLeft = Offset(i * bw + 3f, (h - bh) / 2f),
                                        size = Size(bw - 6f, bh),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                }
                            }
                            else -> {
                                drawCircle(
                                    color = opt.c1.copy(alpha = 0.75f),
                                    radius = h * 0.34f,
                                    center = Offset(w / 2f, h / 2f)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = h * 0.10f,
                                    center = Offset(w / 2f, h / 2f)
                                )
                            }
                        }
                    }
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(20.dp)
                                .background(accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(13.dp))
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(opt.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(opt.subtitle, color = Color(0xFF9EA6C0), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))

                // زر "تحديد" أو "إلغاء التحديد" تحت كل صورة معاينة مباشرة
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent else Color.White.copy(alpha = 0.08f))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSelected) "✓ مُحدد (إلغاء)" else "تحديد",
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) accent.copy(alpha = 0.24f) else Color(0xFF141828).copy(alpha = 0.85f))
            .border(1.dp, if (active) accent else accent.copy(alpha = 0.30f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (active) accent else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedThemeBackdrop(
    themeName: String,
    accent: Color,
    secondaryAccent: Color,
    intensity: Float,
    playing: Boolean
) {
    val infinite = rememberInfiniteTransition(label = "backdrop")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(if (playing) 5500 else 11000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val riseProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (playing) 4500 else 9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rise"
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val alphaMul = intensity.coerceIn(0.1f, 1f)

        when (themeName) {
            "Dynamic Cover Aura" -> {
                val swayX = sin(phase) * w * 0.14f
                val swayY = cos(phase) * h * 0.08f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.34f * alphaMul), Color.Transparent),
                        center = Offset(w * 0.30f + swayX, h * 0.24f + swayY),
                        radius = w * 0.75f
                    ),
                    center = Offset(w * 0.30f + swayX, h * 0.24f + swayY),
                    radius = w * 0.75f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(secondaryAccent.copy(alpha = 0.28f * alphaMul), Color.Transparent),
                        center = Offset(w * 0.75f - swayX, h * 0.68f - swayY),
                        radius = w * 0.75f
                    ),
                    center = Offset(w * 0.75f - swayX, h * 0.68f - swayY),
                    radius = w * 0.75f
                )
            }

            "Stadium Champions", "Camp Blaugrana", "Red Devil Arena" -> {
                val stripes = 10
                val stripeH = h / stripes
                for (i in 0 until stripes) {
                    val c = when (themeName) {
                        "Camp Blaugrana" -> if (i % 2 == 0) Color(0xFF0A1636) else Color(0xFF280919)
                        "Red Devil Arena" -> if (i % 2 == 0) Color(0xFF22060A) else Color(0xFF120305)
                        else -> if (i % 2 == 0) Color(0xFF072814) else Color(0xFF051D0E)
                    }
                    drawRect(
                        color = c.copy(alpha = 0.55f * alphaMul),
                        topLeft = Offset(0f, i * stripeH),
                        size = Size(w, stripeH)
                    )
                }

                val lineColor = Color.White.copy(alpha = 0.14f * alphaMul)
                val stroke = Stroke(width = 3f)
                drawRect(
                    color = lineColor,
                    topLeft = Offset(w * 0.06f, h * 0.08f),
                    size = Size(w * 0.88f, h * 0.84f),
                    style = stroke
                )
                drawLine(
                    color = lineColor,
                    start = Offset(w * 0.06f, h * 0.5f),
                    end = Offset(w * 0.94f, h * 0.5f),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = lineColor,
                    radius = w * 0.18f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = stroke
                )

                val sway = sin(phase) * (w * 0.12f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.28f * alphaMul), Color.Transparent),
                        center = Offset(w * 0.15f + sway, h * 0.08f),
                        radius = w * 0.65f
                    ),
                    center = Offset(w * 0.15f + sway, h * 0.08f),
                    radius = w * 0.65f
                )
            }

            "Ocean Bubbles" -> {
                for (layer in 0..2) {
                    val wavePath = Path()
                    val baseY = h * (0.35f + layer * 0.22f)
                    wavePath.moveTo(0f, h)
                    wavePath.lineTo(0f, baseY)
                    val steps = 40
                    val dx = w / steps
                    for (i in 0..steps) {
                        val x = i * dx
                        val y = baseY + sin(phase + i * 0.3f + layer) * 22f
                        wavePath.lineTo(x, y)
                    }
                    wavePath.lineTo(w, h)
                    wavePath.close()
                    drawPath(
                        path = wavePath,
                        color = Color(0xFF0099CC).copy(alpha = (0.10f + layer * 0.05f) * alphaMul)
                    )
                }

                val bubbleCount = 24
                for (b in 0 until bubbleCount) {
                    val seedX = ((b * 37) % 100) / 100f
                    val speedOffset = ((b * 19) % 100) / 100f
                    val yFrac = 1f - ((riseProgress + speedOffset) % 1f)
                    val xPos = (seedX * w) + sin(phase + b) * 18f
                    val yPos = yFrac * h
                    val radius = (6f + (b % 5) * 5f)
                    drawCircle(
                        color = Color(0xFF80F4FF).copy(alpha = 0.26f * alphaMul),
                        radius = radius,
                        center = Offset(xPos, yPos),
                        style = Stroke(width = 2f)
                    )
                }
            }

            "Cyber Neon City" -> {
                val lines = 12
                for (i in 0 until lines) {
                    val progress = ((i.toFloat() / lines) + riseProgress * 0.25f) % 1f
                    val y = h * progress
                    drawLine(
                        color = accent.copy(alpha = 0.14f * alphaMul),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5f
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.26f * alphaMul), Color.Transparent),
                        center = Offset(w * 0.2f, h * (0.3f + 0.1f * sin(phase))),
                        radius = w * 0.6f
                    ),
                    center = Offset(w * 0.2f, h * (0.3f + 0.1f * sin(phase))),
                    radius = w * 0.6f
                )
            }

            "Matrix Cyber Rain" -> {
                val cols = 18
                val colW = w / cols
                for (c in 0 until cols) {
                    val offset = ((c * 29) % 100) / 100f
                    val headY = ((riseProgress + offset) % 1f) * h
                    drawLine(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF00FF66).copy(alpha = 0.35f * alphaMul))
                        ),
                        start = Offset(c * colW + colW / 2f, (headY - 140f).coerceAtLeast(0f)),
                        end = Offset(c * colW + colW / 2f, headY),
                        strokeWidth = 4f
                    )
                }
            }

            else -> {
                val particles = 22
                for (p in 0 until particles) {
                    val px = (((p * 43) % 100) / 100f) * w + sin(phase + p) * 14f
                    val py = ((((p * 61) % 100) / 100f + riseProgress * 0.3f) % 1f) * h
                    drawCircle(
                        color = accent.copy(alpha = 0.28f * alphaMul),
                        radius = (3f + (p % 4) * 2.5f),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderWithReset(
    title: String,
    accent: Color,
    onResetClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        ResetChipButton(accent = accent, label = "↺ افتراضي", onClick = onResetClick)
    }
}

@Composable
private fun ResetChipButton(
    accent: Color,
    label: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SleekMiniPlayerPill(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    accent: Color,
    miniStyle: String,
    cornerDp: Int,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit
) {
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val horizontalPad = if (miniStyle == "Full Bar") 0.dp else 12.dp
    val shapeRadius = if (miniStyle == "Full Bar") 0.dp else cornerDp.dp

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = 4.dp)
            .clip(RoundedCornerShape(shapeRadius))
            .background(Color(0xFF0B0E1B).copy(alpha = 0.94f))
            .border(
                width = if (miniStyle == "Neon Dock") 1.6.dp else 1.dp,
                color = accent.copy(alpha = if (miniStyle == "Neon Dock") 0.80f else 0.40f),
                shape = RoundedCornerShape(shapeRadius)
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArtThumb(song, accent, sizeDp = 42, cornerDp = (cornerDp - 4).coerceAtLeast(8))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} • ${formatTime(position)}", color = Color(0xFFB5B9D0), fontSize = 11.sp, maxLines = 1)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White)
                    }
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(34.dp).background(accent, CircleShape)
                    ) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.SkipNext, "Next", tint = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun UltraNowPlayingScreen(
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
    dynamicPlayerColor: Boolean,
    spatial8DEnabled: Boolean,
    cornerDp: Int,
    loopA: Long,
    loopB: Long,
    accent: Color,
    palette: CoverPalette,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleDynamicColor: () -> Unit,
    onToggle8D: () -> Unit,
    onToggleABLoop: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenUiStudio: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ultra_anim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl_spin"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = if (dynamicPlayerColor) 0.28f else 0.10f),
                        palette.secondary.copy(alpha = if (dynamicPlayerColor) 0.16f else 0.05f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (dynamicPlayerColor) accent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
                        .border(1.dp, if (dynamicPlayerColor) accent else Color.Transparent, RoundedCornerShape(50))
                        .clickable(onClick = onToggleDynamicColor)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (dynamicPlayerColor) "🎨 لون الغلاف التلقائي: مفعل" else "🎨 تفعيل لون الغلاف",
                        color = if (dynamicPlayerColor) accent else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onOpenUiStudio)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("تغيير الشكل", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .size(240.dp)
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
            // هالة ضوئية خلف الغلاف بلون صورة الأغنية
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.42f), palette.secondary.copy(alpha = 0.18f), Color.Transparent)
                    ),
                    radius = size.minDimension / 1.9f
                )
            }

            when (discStyle) {
                "Dynamic Aura", "Square Studio" -> {
                    Box(
                        Modifier
                            .size(216.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF121422))
                            .border(2.5.dp, Brush.linearGradient(listOf(accent, palette.secondary)), RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(64.dp))
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                "Stadium Crest" -> {
                    Box(
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color(0xFF071F10))
                            .border(3.dp, accent, RoundedCornerShape(36.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SportsSoccer, null, tint = accent, modifier = Modifier.size(68.dp))
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize().padding(6.dp).clip(RoundedCornerShape(30.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                "Vinyl Spin" -> {
                    Box(
                        Modifier
                            .size(222.dp)
                            .rotate(if (playing) spinAngle else 0f)
                            .clip(CircleShape)
                            .background(Color(0xFF0B0C12))
                            .border(6.dp, Color(0xFF1E2030), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier.size(165.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(3.dp, accent, CircleShape)
                        )
                    }
                }
                else -> {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(color = accent, radius = r * 0.88f, style = Stroke(width = 4f))
                    }
                    Box(Modifier.size(195.dp).clip(CircleShape).background(Color(0xFF121422)), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (waveStyle != "Hidden") {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 12.dp)
            ) {
                when (waveStyle) {
                    "Smooth Wave", "Mirror Twin" -> {
                        val path = Path()
                        val midY = size.height / 2f
                        path.moveTo(0f, midY)
                        val steps = 60
                        val dx = size.width / steps
                        for (i in 0..steps) {
                            val x = i * dx
                            val amp = if (playing) (size.height * 0.42f * sin(pulsePhase + i * 0.25f)) else 2f
                            path.lineTo(x, midY + amp)
                        }
                        drawPath(path, color = accent, style = Stroke(width = 4f))
                        if (waveStyle == "Mirror Twin") {
                            val path2 = Path()
                            path2.moveTo(0f, midY)
                            for (i in 0..steps) {
                                val x = i * dx
                                val amp = if (playing) (size.height * 0.42f * sin(pulsePhase + i * 0.25f)) else 2f
                                path2.lineTo(x, midY - amp)
                            }
                            drawPath(path2, color = palette.secondary.copy(alpha = 0.75f), style = Stroke(width = 3f))
                        }
                    }
                    "Pulse Dots" -> {
                        val dots = 28
                        val space = size.width / dots
                        for (i in 0 until dots) {
                            val f = if (playing) abs(sin(pulsePhase + i * 0.35f)) else 0.25f
                            drawCircle(
                                color = if (i % 2 == 0) accent else palette.secondary,
                                radius = (4f + 6f * f),
                                center = Offset(i * space + space / 2f, size.height / 2f)
                            )
                        }
                    }
                    else -> {
                        val bars = 36
                        val space = size.width / bars
                        for (i in 0 until bars) {
                            val animFactor = if (playing) abs(sin(pulsePhase + i * 0.42f)) else 0.20f
                            val barH = (size.height * (0.18f + 0.78f * animFactor)).coerceAtLeast(5f)
                            drawRoundRect(
                                color = if (i % 2 == 0) accent else palette.secondary,
                                topLeft = Offset(i * space + 2f, (size.height - barH) / 2f),
                                size = Size((space - 4f).coerceAtLeast(3f), barH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} • ${song.album}", color = Color(0xFFB5B9D0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleLike) {
                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (isLiked) accent else Color.White)
            }
        }

        Spacer(Modifier.height(8.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = Color(0xFFA5A9C0), fontSize = 11.sp)
                    val centerTag = when {
                        loopA >= 0L && loopB >= 0L -> "A-B LOOP (${formatTime(loopA)}-${formatTime(loopB)})"
                        spatial8DEnabled -> "🎧 8D SPATIAL SURROUND ACTIVE"
                        else -> "PURE HI-FI • ANTI-CLIPPING"
                    }
                    Text(centerTag, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(formatTime(duration), color = Color(0xFFA5A9C0), fontSize = 11.sp)
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
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) accent else Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(68.dp)
                        .background(Brush.linearGradient(listOf(accent, palette.secondary)), CircleShape)
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) accent else Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onToggle8D,
                label = { Text(if (spatial8DEnabled) "8D محيطي: مفعل" else "تفعيل 8D محيطي", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Headphones, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
            AssistChip(
                onClick = onToggleABLoop,
                label = { Text(if (loopA >= 0L) "تكرار A-B مفعل" else "تكرار مقطع A-B", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Repeat, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
            AssistChip(
                onClick = onOpenLyrics,
                label = { Text("الكلمات LRC", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Lyrics, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
            AssistChip(
                onClick = onOpenSleep,
                label = {
                    Text(
                        if (sleepRemainingSec > 0) "${sleepRemainingSec / 60}:${"%02d".format(sleepRemainingSec % 60)}"
                        else "مؤقت النوم",
                        fontSize = 11.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.Bedtime, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
        }
    }
}

@Composable
private fun UltraDspStudioScreen(
    eqPreset: String,
    bandGains: List<Float>,
    bassStrength: Int,
    virtualizerStrength: Int,
    loudnessGain: Int,
    playbackSpeed: Float,
    playbackPitch: Float,
    antiClipping: Boolean,
    spatial8D: Boolean,
    accent: Color,
    cornerDp: Int,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onToggleAntiClipping: () -> Unit,
    onToggle8D: () -> Unit,
    onRequestResetDsp: () -> Unit,
    onOpenSystemEq: () -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    val cardBg = Color(0xFF0E1120).copy(alpha = 0.85f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeaderWithReset("استوديو الصوت النقي والـ 8D المحيطي", accent, onRequestResetDsp)

        // بطاقة منع الغروشة + الصوت المحيطي 8D
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(cornerDp.dp))
                    .background(if (antiClipping) accent.copy(alpha = 0.18f) else cardBg)
                    .border(1.dp, if (antiClipping) accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(cornerDp.dp))
                    .clickable(onClick = onToggleAntiClipping)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Verified, null, tint = if (antiClipping) accent else Color.White)
                Spacer(Modifier.height(6.dp))
                Text("مانع الغروشة الذكي", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (antiClipping) "مفعل • صوت نقي 100%" else "متوقف", color = Color(0xFFA6B0C9), fontSize = 10.sp)
            }

            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(cornerDp.dp))
                    .background(if (spatial8D) Color(0xFFFFC857).copy(alpha = 0.18f) else cardBg)
                    .border(1.dp, if (spatial8D) Color(0xFFFFC857) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(cornerDp.dp))
                    .clickable(onClick = onToggle8D)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Headphones, null, tint = if (spatial8D) Color(0xFFFFC857) else Color.White)
                Spacer(Modifier.height(6.dp))
                Text("صوت محيطي 8D", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (spatial8D) "مفعل • دوران 360°" else "اضغط للتفعيل", color = Color(0xFFA6B0C9), fontSize = 10.sp)
            }
        }

        // أوضاع الصوت الذكية (الضغط مرة ثانية يلغي التحديد ويرجع للوضع الافتراضي)
        Text("أوضاع الاستماع الذكية (اضغط للتحديد أو الإلغاء)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Pure Hi-Fi" to "✨ Pure Hi-Fi",
                "Clean Phonk" to "🔥 Clean Phonk",
                "Gym Beast" to "💪 Gym Beast",
                "Vocal Studio" to "🎤 Vocal Studio",
                "Night Chill" to "🌙 Night Chill",
                "Flat" to "Flat 0dB"
            ).forEach { (id, label) ->
                FilterChip(
                    selected = eqPreset == id,
                    onClick = { onSelectPreset(id) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(cornerDp.dp))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(cornerDp.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandGains.forEachIndexed { idx, gain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(freqLabels.getOrElse(idx) { "Band" }, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(48.dp))
                        Slider(
                            value = gain,
                            onValueChange = { onBandChange(idx, it) },
                            valueRange = -1f..1f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                        )
                        Text("${(gain * 12).toInt()}dB", color = accent, fontSize = 11.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(cornerDp.dp))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(cornerDp.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("CLEAN SUB-BASS ($bassStrength%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = bassStrength.toFloat(),
                    onValueChange = { onBassChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Text("PRE-AMP GAIN (+${(loudnessGain * 0.08f).toInt()} dB • 0% أنقى صوت)", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = loudnessGain.toFloat(),
                    onValueChange = { onLoudnessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC857), activeTrackColor = Color(0xFFFFC857))
                )

                Text("3D SURROUND STAGE ($virtualizerStrength%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = virtualizerStrength.toFloat(),
                    onValueChange = { onVirtChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(cornerDp.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SPEED (${"%.2f".format(playbackSpeed)}x) • PITCH (${"%.2f".format(playbackPitch)}x)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Reset 1.0x", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onSpeedChange(1f); onPitchChange(1f) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSpeedChange(0.85f); onPitchChange(0.88f) }) {
                        Text("Slowed + Phonk", color = accent, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { onSpeedChange(1.16f); onPitchChange(1.12f) }) {
                        Text("Nightcore", color = Color.White, fontSize = 11.sp)
                    }
                }
                Slider(value = playbackSpeed, onValueChange = onSpeedChange, valueRange = 0.6f..1.6f)
            }
        }

        OutlinedButton(onClick = onOpenSystemEq, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Speaker, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("فتح إعدادات Dolby Atmos للهاتف", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmartPlaylistsScreen(
    songs: List<Song>,
    likedIds: Set<Long>,
    recentIds: List<Long>,
    playCounts: Map<Long, Int>,
    playlists: Map<String, List<Long>>,
    activeView: String?,
    currentSongId: Long,
    accent: Color,
    panel: Color,
    cornerDp: Int,
    borderWidthDp: Float,
    fontScale: Float,
    onChangeView: (String?) -> Unit,
    onCreateNew: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayList: (List<Song>, Int) -> Unit,
    onMoreSong: (Song) -> Unit,
    onSelectAlbum: (String) -> Unit
) {
    if (activeView == null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("القوائم والألبومات", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onCreateNew) { Text("+ قائمة جديدة", color = accent, fontWeight = FontWeight.Bold) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FeatureTile("الأغاني المفضلة", "${likedIds.size} مقطع", Icons.Default.Favorite, accent, cornerDp) {
                    onChangeView("__LIKED__")
                }
            }
            item {
                FeatureTile("الأكثر استماعاً", "${playCounts.size} مقطع", Icons.Default.Star, Color(0xFFFFC857), cornerDp) {
                    onChangeView("__MOST_PLAYED__")
                }
            }
            item {
                FeatureTile("تم تشغيلها مؤخراً", "${recentIds.size} مقطع", Icons.Default.History, accent, cornerDp) {
                    onChangeView("__RECENT__")
                }
            }
            item {
                FeatureTile("الألبومات", "${songs.map { it.album }.distinct().size} ألبوم", Icons.Default.Album, accent, cornerDp) {
                    onChangeView("__ALBUMS__")
                }
            }
            itemsIndexed(playlists.entries.toList()) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E101C).copy(alpha = 0.78f), RoundedCornerShape(cornerDp.dp))
                        .clickable { onChangeView(entry.key) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${entry.value.size} مقطع", color = Color(0xFFA5A9C0), fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDeletePlaylist(entry.key) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFA5A9C0))
                    }
                }
            }
        }
    } else if (activeView == "__ALBUMS__") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChangeView(null) }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text("الألبومات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        val albums = songs.groupBy { it.album }.entries.toList()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(albums) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E101C).copy(alpha = 0.78f), RoundedCornerShape(cornerDp.dp))
                        .clickable { onSelectAlbum(entry.key) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtThumb(entry.value.firstOrNull(), accent, 46, cornerDp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${entry.value.size} مقطع", color = Color(0xFFA5A9C0), fontSize = 11.sp)
                    }
                }
            }
        }
    } else {
        val listSongs = when (activeView) {
            "__LIKED__" -> songs.filter { likedIds.contains(it.id) }
            "__MOST_PLAYED__" -> songs.filter { (playCounts[it.id] ?: 0) > 0 }.sortedByDescending { playCounts[it.id] ?: 0 }
            "__RECENT__" -> recentIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }
            else -> (playlists[activeView] ?: emptyList()).mapNotNull { id -> songs.firstOrNull { it.id == id } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChangeView(null) }) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text(activeView.removePrefix("__"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(listSongs) { idx, song ->
                SongRow(
                    song = song,
                    active = currentSongId == song.id,
                    isLiked = likedIds.contains(song.id),
                    uiLayout = "Royal Glass",
                    accent = accent,
                    panel = panel,
                    cornerDp = cornerDp,
                    borderWidthDp = borderWidthDp,
                    fontScale = fontScale,
                    onClick = { onPlayList(listSongs, idx) },
                    onMore = { onMoreSong(song) }
                )
            }
        }
    }
}

// شاشة الإعدادات والتخصيص الجديدة: كل قسم مفصول في بطاقة مستقلة تماماً مع صور المعاينة وزر "تحديد" وزر "افتراضي"
@Composable
private fun UltraSettingsScreen(
    uiLayout: String,
    themeName: String,
    dynamicPlayerColor: Boolean,
    discStyle: String,
    waveStyle: String,
    miniPlayerStyle: String,
    headerStyle: String,
    widgetStyle: String,
    langSetting: String,
    customBgUri: String?,
    bgDimAlpha: Float,
    cornerRadiusDp: Int,
    cardOpacity: Float,
    borderWidthDp: Float,
    fontScale: Float,
    bgAnimIntensity: Float,
    shakeSkipEnabled: Boolean,
    crossfadeSec: Int,
    sleepRemainingSec: Int,
    tracksPlayedStat: Int,
    accent: Color,
    onSelectLayout: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onToggleDynamicColor: () -> Unit,
    onSelectDisc: (String) -> Unit,
    onSelectWave: (String) -> Unit,
    onSelectMini: (String) -> Unit,
    onSelectHeader: (String) -> Unit,
    onSelectWidget: (String) -> Unit,
    onSelectLang: (String) -> Unit,
    onPickBg: () -> Unit,
    onClearBg: () -> Unit,
    onChangeDim: (Float) -> Unit,
    onChangeCorner: (Int) -> Unit,
    onChangeOpacity: (Float) -> Unit,
    onChangeBorder: (Float) -> Unit,
    onChangeFont: (Float) -> Unit,
    onChangeAnimIntensity: (Float) -> Unit,
    onToggleShake: () -> Unit,
    onChangeCrossfade: (Int) -> Unit,
    onOpenSleep: () -> Unit,
    onPinWidget: () -> Unit,
    onRequestResetSection: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val cardBg = Color(0xFF0E1222).copy(alpha = 0.86f)
    val cardShape = RoundedCornerShape(cornerRadiusDp.dp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. بطاقة مستقلة: شكل تخطيط الواجهة (UI Layouts بالصور)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("1. شكل تخطيط الواجهة (UI Layouts)", accent) {
                onRequestResetSection("LAYOUT")
            }
            Text("اسحب بين الصور واضغط (تحديد) لتغيير شكل القائمة، أو اضغط مرة ثانية لإلغاء التحديد:", color = Color(0xFFA6B0C9), fontSize = 11.sp)
            VisualPreviewCarousel(
                options = LAYOUT_VISUAL_OPTIONS,
                selectedId = uiLayout,
                defaultId = "Royal Glass",
                accent = accent,
                onSelectOrToggle = onSelectLayout
            )
        }

        // 2. بطاقة مستقلة: السمات والخلفيات الحية (Themes بالصور)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("2. السمات الحية (استاد • بحر • نيون • غلاف)", accent) {
                onRequestResetSection("THEME")
            }
            VisualPreviewCarousel(
                options = THEME_VISUAL_OPTIONS,
                selectedId = themeName,
                defaultId = "Dynamic Cover Aura",
                accent = accent,
                onSelectOrToggle = onSelectTheme
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(onClick = onToggleDynamicColor)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("تلوين المشغل تلقائياً بلون صورة الأغنية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("يتفاعل المشغل المصغر والكبير مع ألوان غلاف كل أغنية", color = Color(0xFFA6B0C9), fontSize = 10.sp)
                }
                Switch(checked = dynamicPlayerColor, onCheckedChange = { onToggleDynamicColor() })
            }
        }

        // 3. بطاقة مستقلة: تخصيص ويدجت الشاشة الرئيسية (مربع 2x2 أو شريط طويل 4x1)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("3. شكل وحجم ويدجت الشاشة الرئيسية", Color(0xFFFFC857)) {
                onRequestResetSection("WIDGET")
            }
            Text("اختر شكل الويدجت (مربع 2x2 أو طويل عريض أو كبسولة) ويتفاعل لونه تلقائياً مع صورة الأغنية:", color = Color(0xFFA6B0C9), fontSize = 11.sp)
            VisualPreviewCarousel(
                options = WIDGET_VISUAL_OPTIONS,
                selectedId = widgetStyle,
                defaultId = "Auto Adaptive",
                accent = Color(0xFFFFC857),
                onSelectOrToggle = onSelectWidget
            )
            Button(
                onClick = onPinWidget,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC857))
            ) {
                Icon(Icons.Default.Widgets, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("تثبيت الويدجت على الشاشة الرئيسية الآن", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }

        // 4. بطاقة مستقلة: شكل قرص المشغل (Now Playing Disc)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("4. شكل قرص المشغل (Now Playing Disc)", accent) {
                onRequestResetSection("DISC")
            }
            VisualPreviewCarousel(
                options = DISC_VISUAL_OPTIONS,
                selectedId = discStyle,
                defaultId = "Dynamic Aura",
                accent = accent,
                onSelectOrToggle = onSelectDisc
            )
        }

        // 5. بطاقة مستقلة: شكل الموجات الصوتية (Visualizer)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("5. شكل الموجات الصوتية (Visualizer)", accent) {
                onRequestResetSection("WAVE")
            }
            VisualPreviewCarousel(
                options = WAVE_VISUAL_OPTIONS,
                selectedId = waveStyle,
                defaultId = "Smooth Wave",
                accent = accent,
                onSelectOrToggle = onSelectWave
            )
        }

        // 6. بطاقة مستقلة: شكل المشغل المصغر السفلي (Mini-Player)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeaderWithReset("6. شكل المشغل المصغر السفلي (Mini-Player)", accent) {
                onRequestResetSection("MINI")
            }
            VisualPreviewCarousel(
                options = MINI_VISUAL_OPTIONS,
                selectedId = miniPlayerStyle,
                defaultId = "Floating Pill",
                accent = accent,
                onSelectOrToggle = onSelectMini
            )
        }

        // 7. بطاقة مستقلة: شكل الهيدر العلوي (Header Style)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeaderWithReset("7. شكل الهيدر العلوي", accent) {
                onRequestResetSection("HEADER")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Royal Clean" to "هيدر ملكي مدمج", "Centered VIP" to "هيدر عريض VIP").forEach { (id, label) ->
                    FilterChip(
                        selected = headerStyle == id,
                        onClick = { onSelectHeader(if (headerStyle == id) "Royal Clean" else id) },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // 8. بطاقة مستقلة: خلفية الهاتف الشخصية (Custom Wallpaper)
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeaderWithReset("8. خلفية الهاتف الشخصية (من الاستوديو)", accent) {
                onRequestResetSection("WALLPAPER")
            }
            Button(
                onClick = onPickBg,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f))
            ) {
                Icon(Icons.Default.Wallpaper, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text("اختيار صورة خلفية من الهاتف", color = Color.White, fontSize = 12.sp)
            }
            if (!customBgUri.isNullOrBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        Text("درجة تعتيم الصورة: ${(bgDimAlpha * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                        Slider(value = bgDimAlpha, onValueChange = onChangeDim, valueRange = 0.05f..0.85f)
                    }
                }
                OutlinedButton(onClick = onClearBg, modifier = Modifier.fillMaxWidth()) {
                    Text("إلغاء خلفية الهاتف", color = Color(0xFFFF5C5C), fontSize = 12.sp)
                }
            }
        }

        // 9. بطاقة مستقلة: الأبعاد والشفافية الدقيقة
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeaderWithReset("9. الأبعاد والشفافية الدقيقة", accent) {
                onRequestResetSection("DIMENSIONS")
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Text("قوة مؤثرات الخلفية (${(bgAnimIntensity * 100).toInt()}%)", color = Color(0xFFA6B0C9), fontSize = 11.sp)
                    Slider(value = bgAnimIntensity, onValueChange = onChangeAnimIntensity, valueRange = 0f..1f)

                    Text("استدارة الحواف (${cornerRadiusDp}dp)", color = Color(0xFFA6B0C9), fontSize = 11.sp)
                    Slider(value = cornerRadiusDp.toFloat(), onValueChange = { onChangeCorner(it.toInt()) }, valueRange = 4f..32f)

                    Text("شفافية البطاقات (${(cardOpacity * 100).toInt()}%)", color = Color(0xFFA6B0C9), fontSize = 11.sp)
                    Slider(value = cardOpacity, onValueChange = onChangeOpacity, valueRange = 0f..0.70f)

                    Text("سمك الإطارات (${"%.1f".format(borderWidthDp)}dp)", color = Color(0xFFA6B0C9), fontSize = 11.sp)
                    Slider(value = borderWidthDp, onValueChange = onChangeBorder, valueRange = 0f..2.5f)

                    Text("حجم الخط (${"%.2f".format(fontScale)}x)", color = Color(0xFFA6B0C9), fontSize = 11.sp)
                    Slider(value = fontScale, onValueChange = onChangeFont, valueRange = 0.85f..1.25f)
                }
            }
        }

        // 10. بطاقة مستقلة: الأدوات والنسخ الاحتياطي واللغة
        Column(Modifier.fillMaxWidth().background(cardBg, cardShape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("10. اللغة والأدوات الذكية", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AR" to "العربية", "EN" to "English", "FR" to "Français", "ES" to "Español", "DE" to "Deutsch", "TR" to "Türkçe").forEach { (code, name) ->
                    FilterChip(selected = langSetting == code, onClick = { onSelectLang(code) }, label = { Text(name, fontSize = 11.sp) })
                }
            }

            FeatureTile(
                title = "هز الهاتف لتغيير الأغنية (Shake-to-Skip)",
                subtitle = if (shakeSkipEnabled) "مفعل • هز الموبايل ينقل للأغنية التالية" else "متوقف",
                icon = Icons.Default.Vibration,
                accent = accent,
                cornerDp = cornerRadiusDp,
                onClick = onToggleShake
            )

            FeatureTile(
                title = "مؤقت النوم التدريجي (Fade-Out)",
                subtitle = if (sleepRemainingSec > 0) "متبقي ${sleepRemainingSec / 60} دقيقة" else "اضغط لضبط المؤقت",
                icon = Icons.Default.Bedtime,
                accent = accent,
                cornerDp = cornerRadiusDp,
                onClick = onOpenSleep
            )

            Text("الدمج السلس بين الأغاني (Crossfade: ${if (crossfadeSec == 0) "متوقف" else "$crossfadeSec ثوانٍ"})", color = Color.White, fontSize = 12.sp)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(value = crossfadeSec.toFloat(), onValueChange = { onChangeCrossfade(it.toInt()) }, valueRange = 0f..10f, steps = 9)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudUpload, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("حفظ نسخة JSON", color = Color.White, fontSize = 11.sp)
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudDownload, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استعادة JSON", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        OutlinedButton(
            onClick = { onRequestResetSection("ALL") },
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape
        ) {
            Icon(Icons.Default.RestartAlt, null, tint = Color(0xFFFF5C5C))
            Spacer(Modifier.width(8.dp))
            Text("إعادة ضبط جميع إعدادات التطبيق للوضع الافتراضي", color = Color(0xFFFF5C5C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Text(
            "VYRO MUSIC 3.2.0 ROYAL • BY MOHAMED TAREK • PLAYED: $tracksPlayedStat",
            color = Color(0xFF7E8299),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
private fun DriveGymModeScreen(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    accent: Color,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DRIVE & GYM MODE", color = accent, fontWeight = FontWeight.Black, fontSize = 16.sp)
            OutlinedButton(onClick = onExit) { Text("خروج", color = Color.White) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArtThumb(song, accent, sizeDp = 150, cornerDp = 24)
            Spacer(Modifier.height(16.dp))
            Text(song.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(song.artist, color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, modifier = Modifier.size(76.dp).background(Color(0xFF161826), CircleShape)) {
                    Icon(Icons.Default.SkipPrevious, "Prev", tint = Color.White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(98.dp).background(accent, CircleShape)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(52.dp))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(76.dp).background(Color(0xFF161826), CircleShape)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    active: Boolean,
    isLiked: Boolean,
    uiLayout: String,
    accent: Color,
    panel: Color,
    cornerDp: Int,
    borderWidthDp: Float,
    fontScale: Float,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val isCompact = uiLayout == "Compact Pro"
    val isAmoled = uiLayout == "AMOLED Pure"
    val isCapsule = uiLayout == "Neon Capsule"
    val isStadium = uiLayout == "Stadium Cards"
    val effectiveRadius = if (isCapsule) 50.dp else cornerDp.dp
    val rowBg = if (active) accent.copy(alpha = 0.18f) else panel

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(effectiveRadius))
                .background(rowBg)
                .border(
                    width = if (active) (borderWidthDp + 0.6f).dp else if (!isAmoled) borderWidthDp.dp else 0.dp,
                    color = when {
                        active -> accent
                        isCapsule || isStadium -> accent.copy(alpha = 0.28f)
                        uiLayout == "Royal Glass" -> Color.White.copy(alpha = 0.09f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(effectiveRadius)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = if (isCompact) 5.dp else if (isStadium) 11.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(
                song = song,
                accent = accent,
                sizeDp = if (isCompact) 40 else if (isStadium) 54 else 48,
                cornerDp = if (isCapsule) 50 else (cornerDp - 4).coerceAtLeast(8)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = if (active) accent else Color.White,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = (13.5f * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${song.artist} • ${formatTime(song.duration)}",
                    color = Color(0xFFA2A7C0),
                    fontSize = (11f * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isLiked) {
                Icon(Icons.Default.Favorite, null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "More", tint = Color(0xFFB5B9D0), modifier = Modifier.size(18.dp))
            }
        }
        if (isAmoled && !active) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(0.5.dp)
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }
    }
}

@Composable
private fun BentoSongCard(
    song: Song,
    active: Boolean,
    isLiked: Boolean,
    compactPoster: Boolean,
    accent: Color,
    cornerDp: Int,
    borderWidthDp: Float,
    fontScale: Float,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(Color(0xFF0E101C).copy(alpha = 0.72f))
            .border(
                width = if (active) 1.5.dp else borderWidthDp.dp,
                color = if (active) accent else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(cornerDp.dp)
            )
            .clickable(onClick = onClick)
            .padding(if (compactPoster) 6.dp else 8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compactPoster) 94.dp else 128.dp)
                .clip(RoundedCornerShape((cornerDp - 4).coerceAtLeast(8).dp))
                .background(Color(0xFF17192B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(36.dp))
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            color = if (active) accent else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = ((if (compactPoster) 11f else 12.5f) * fontScale).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(song.artist, color = Color(0xFF989CB3), fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (isLiked) Icon(Icons.Default.Favorite, null, tint = accent, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun AlbumArtThumb(song: Song?, accent: Color, sizeDp: Int = 46, cornerDp: Int = 12) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(Color(0xFF151726)),
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
            .padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF989CB3), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeatureTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    cornerDp: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF14182B).copy(alpha = 0.80f), RoundedCornerShape(cornerDp.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color(0xFF989CB3), fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF989CB3))
    }
}

@Composable
private fun EmptyState(message: String, accent: Color) {
    Box(Modifier.fillMaxWidth().padding(top = 55.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = accent, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center, fontSize = 13.sp)
        }
    }
}

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
        val stepX = (bmp.width / 10).coerceAtLeast(1)
        val stepY = (bmp.height / 10).coerceAtLeast(1)

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
        val r = (rSum / count).toInt().coerceIn(45, 240)
        val g = (gSum / count).toInt().coerceIn(45, 240)
        val b = (bSum / count).toInt().coerceIn(55, 245)

        CoverPalette(
            dominant = Color((r * 1.28f).toInt().coerceIn(85, 255), (g * 1.18f).toInt().coerceIn(75, 250), (b * 1.32f).toInt().coerceIn(95, 255)),
            secondary = Color((b * 1.15f).toInt().coerceIn(70, 245), (r * 0.95f).toInt().coerceIn(60, 230), (g * 1.25f).toInt().coerceIn(80, 250)),
            deepTint = Color((r / 7).coerceIn(4, 28), (g / 7).coerceIn(4, 28), (b / 6).coerceIn(6, 34))
        )
    }.getOrDefault(CoverPalette())
}

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
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VYRO_Ring_${song.title}.m4a")
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
        runCatching { RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri) }
        false
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun readRawSidecarLyrics(filePath: String?): String? {
    if (filePath.isNullOrBlank()) return null
    return runCatching {
        val audioFile = File(filePath)
        val base = audioFile.nameWithoutExtension
        val parent = audioFile.parentFile ?: return null
        val lrc = File(parent, "$base.lrc")
        val txt = File(parent, "$base.txt")
        when {
            lrc.exists() -> lrc.readText()
            txt.exists() -> txt.readText()
            else -> null
        }
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
    obj.keys().forEach { key ->
        key.toLongOrNull()?.let { id -> map[id] = obj.getInt(key) }
    }
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
