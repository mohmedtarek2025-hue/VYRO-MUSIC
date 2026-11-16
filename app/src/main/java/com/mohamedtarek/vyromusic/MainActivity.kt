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
    val dominant: Color = Color(0xFFA87BFF),
    val secondary: Color = Color(0xFF141122),
    val isLightCover: Boolean = false
)

data class LrcLine(val timeMs: Long, val text: String)

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
    val mostPlayed: String,
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
    val uiStudioTitle: String,
    val layoutSectionTitle: String,
    val themeTitle: String,
    val pickBgImage: String,
    val clearBgImage: String,
    val bgDimLevel: String,
    val langTitle: String,
    val langSystem: String,
    val shakeToSkip: String,
    val sleepTimer: String,
    val crossfade: String,
    val backupRestore: String
)

private fun getAppStrings(langSetting: String): Strings {
    val code = if (langSetting == "SYSTEM") Locale.getDefault().language.uppercase() else langSetting.uppercase()
    return when {
        code.startsWith("AR") -> Strings(
            true, "الأغاني", "الألبومات", "القوائم", "الصوت DSP", "الإعدادات",
            "ابحث عن أغنية أو مطرب...", "مقطع", "المشغل", "الأغاني المفضلة", "تم تشغيلها مؤخراً", "الأكثر استماعاً",
            "+ قائمة جديدة", "تشغيل الآن", "تشغيل كالتالي في الطابور", "إضافة / إزالة من المفضلة",
            "إضافة إلى قائمة تشغيل...", "قص وتعيين كنغمة رنين", "كلمات الأغنية المتزامنة (LRC)",
            "مشاركة الملف الصوتي", "معلومات الملف التفصيلية", "حذف من الجهاز",
            "استوديو تخصيص الواجهة 360°", "شكل وتخطيط الواجهة (UI Layout)", "الثيم والألوان (Theme Engine)",
            "اختيار خلفية من استوديو الهاتف", "إلغاء خلفية الهاتف", "درجة تعتيم الخلفية (لوضوح الصورة)",
            "لغة التطبيق (10 لغات)", "لغة الجهاز", "هز الهاتف لتغيير الأغنية (Shake-to-Skip)",
            "مؤقت النوم التدريجي (Fade-Out)", "الدمج السلس (Crossfade)", "النسخ الاحتياطي والاستعادة"
        )
        code.startsWith("FR") -> Strings(
            false, "Titres", "Albums", "Listes", "Audio DSP", "Options",
            "Rechercher titres, artistes...", "TITRES", "Lecture", "Titres likés", "Récents", "Plus écoutés",
            "+ Nouvelle", "Lire", "Lire ensuite", "Favoris", "Ajouter à la liste...", "Couper en sonnerie",
            "Paroles synchronisées", "Partager", "Infos fichier", "Supprimer",
            "Studio d'Interface 360°", "Style de Disposition (Layout)", "Thème & Couleurs",
            "Image de fond (Galerie)", "Réinitialiser le fond", "Opacité du fond",
            "Langue (10 Langues)", "Système", "Secouer pour passer", "Minuteur (Fade-Out)", "Fondu (Crossfade)", "Sauvegarde JSON"
        )
        code.startsWith("ES") -> Strings(
            false, "Canciones", "Álbumes", "Listas", "Audio DSP", "Ajustes",
            "Buscar canciones, artistas...", "PISTAS", "Sonando", "Favoritas", "Recientes", "Más escuchadas",
            "+ Nueva lista", "Reproducir", "Siguiente en cola", "Favorito", "Añadir a lista...", "Cortar como tono",
            "Letras sincronizadas", "Compartir", "Info del archivo", "Eliminar",
            "Estudio de Interfaz 360°", "Diseño de Pantalla (Layout)", "Tema y Colores",
            "Fondo desde galería", "Quitar fondo", "Oscurecimiento de fondo",
            "Idioma (10 Idiomas)", "Sistema", "Agitar para saltar", "Temporizador", "Transición Crossfade", "Copia de seguridad"
        )
        code.startsWith("DE") -> Strings(
            false, "Titel", "Alben", "Listen", "DSP Studio", "Optionen",
            "Songs, Künstler suchen...", "TITEL", "Player", "Favoriten", "Verlauf", "Meistgespielt",
            "+ Neue Liste", "Abspielen", "Als Nächstes", "Favorit", "Zur Liste...", "Als Klingelton",
            "Songtext (LRC)", "Teilen", "Datei-Info", "Löschen",
            "360° UI-Design Studio", "Oberflächen-Layout", "Farbthema",
            "Hintergrundbild wählen", "Hintergrund zurücksetzen", "Hintergrund-Abdunkelung",
            "Sprache (10 Sprachen)", "System", "Schütteln zum Überspringen", "Schlaftimer", "Crossfade", "Backup & Restore"
        )
        code.startsWith("IT") -> Strings(
            false, "Brani", "Album", "Playlist", "DSP Audio", "Impostazioni",
            "Cerca brani, artisti...", "BRANI", "Player", "Preferiti", "Recenti", "Più ascoltati",
            "+ Nuova", "Riproduci", "Riproduci successivo", "Preferito", "Aggiungi a playlist...", "Taglia come suoneria",
            "Testi sincronizzati", "Condividi", "Info file", "Elimina",
            "Studio Interfaccia 360°", "Stile Layout UI", "Tema e Colori",
            "Sfondo dalla galleria", "Rimuovi sfondo", "Oscuramento sfondo",
            "Lingua (10 Lingue)", "Sistema", "Scuoti per cambiare brano", "Sleep Timer", "Crossfade", "Backup JSON"
        )
        code.startsWith("TR") -> Strings(
            false, "Şarkılar", "Albümler", "Listeler", "DSP Ses", "Ayarlar",
            "Şarkı veya sanatçı ara...", "PARÇA", "Çalıyor", "Beğenilenler", "Son Çalınanlar", "En Çok Dinlenenler",
            "+ Yeni Liste", "Şimdi Çal", "Sıradaki Çal", "Favori", "Listeye Ekle...", "Zil Sesi Yap",
            "Senkronize Sözler", "Paylaş", "Dosya Bilgisi", "Sil",
            "360° Arayüz Stüdyosu", "Arayüz Düzeni (Layout)", "Tema ve Renkler",
            "Galeriden Arka Plan Seç", "Arka Planı Sıfırla", "Arka Plan Karartma",
            "Dil (10 Dil)", "Sistem", "Geçmek İçin Salla", "Uyku Zamanlayıcı", "Crossfade Geçiş", "Yedekleme (JSON)"
        )
        code.startsWith("RU") -> Strings(
            false, "Треки", "Альбомы", "Плейлисты", "DSP Звук", "Настройки",
            "Поиск треков, артистов...", "ТРЕКОВ", "Плеер", "Любимые", "Недавние", "Популярные",
            "+ Новый", "Играть", "Следующий", "В избранное", "В плейлист...", "На рингтон",
            "Синхронный текст", "Поделиться", "О файле", "Удалить",
            "Студия Интерфейса 360°", "Макет интерфейса (Layout)", "Тема и Цвета",
            "Фон из галереи", "Сбросить фон", "Затемнение фона",
            "Язык (10 языков)", "Системный", "Встряхнуть для переключения", "Таймер сна", "Кроссфейд", "Резервная копия"
        )
        code.startsWith("HI") -> Strings(
            false, "गाने", "एल्बम", "प्लेलिस्ट", "DSP ऑडियो", "सेटिंग्स",
            "गाने या कलाकार खोजें...", "गाने", "प्लेयर", "पसंदीदा", "हाल ही में", "सबसे ज़्यादा सुने गए",
            "+ नई प्लेलिस्ट", "अभी चलाएं", "अगला चलाएं", "पसंदीदा", "प्लेलिस्ट में जोड़ें...", "रिंगटोन बनाएं",
            "सिंक किए गए बोल", "शेयर करें", "फ़ाइल जानकारी", "हटाएं",
            "360° UI कस्टमाइज़ स्टूडियो", "UI लेआउट स्टाइल", "थीम और रंग",
            "गैलरी से वॉलपेपर चुनें", "वॉलपेपर हटाएं", "वॉलपेपर ब्राइटनेस/डिम",
            "भाषा (10 भाषाएं)", "सिस्टम", "गाना बदलने के लिए फ़ोन हिलाएं", "स्लीप टाइमर", "क्रॉसफ़ेड", "बैकअप और रीस्टोर"
        )
        else -> Strings(
            false, "Songs", "Albums", "Playlists", "DSP Studio", "Settings",
            "Search tracks, artists, albums...", "TRACKS", "Playing", "Liked Songs", "Recently Played", "Most Played",
            "+ New List", "Play Now", "Play Next in Queue", "Toggle Favorite", "Add to Playlist...", "Trim & Set as Ringtone",
            "Synced Lyrics Studio (LRC)", "Share Audio", "File Info", "Delete from Device",
            "360° UI Layout & Theme Studio", "UI Layout Structure (360°)", "Color Theme & Aura",
            "Pick Custom Wallpaper from Phone", "Clear Custom Wallpaper", "Wallpaper Dim Level (Clarity)",
            "App Language (10 Languages)", "System Default", "Shake Phone to Skip Track",
            "Smart Fade-Out Sleep Timer", "Smooth Crossfade", "Backup & Restore (JSON)"
        )
    }
}

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
                    controller?.let { VyroUltraApp(it, ::loadSongs) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VyroUltraApp(player: Player, loadSongs: () -> List<Song>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()

    var langSetting by remember { mutableStateOf(prefs.getString("lang_setting", "SYSTEM") ?: "SYSTEM") }
    val strings = remember(langSetting) { getAppStrings(langSetting) }

    var uiLayout by remember { mutableStateOf(prefs.getString("ui_layout", "AMOLED Pure") ?: "AMOLED Pure") }
    var themeName by remember { mutableStateOf(prefs.getString("theme_name", "Pitch Black Neon") ?: "Pitch Black Neon") }
    var discStyle by remember { mutableStateOf(prefs.getString("disc_style", "Vinyl Spin") ?: "Vinyl Spin") }
    var waveStyle by remember { mutableStateOf(prefs.getString("wave_style", "Smooth Wave") ?: "Smooth Wave") }
    var cornerRadiusDp by remember { mutableIntStateOf(prefs.getInt("corner_radius_dp", 18)) }
    var cardOpacity by remember { mutableFloatStateOf(prefs.getFloat("card_opacity", 0.0f)) }
    var fontScale by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }

    var customBgUri by remember { mutableStateOf(prefs.getString("custom_bg_uri", null)) }
    var bgDimAlpha by remember { mutableFloatStateOf(prefs.getFloat("bg_dim_alpha", 0.35f)) }

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
    var autoScanEnabled by remember { mutableStateOf(prefs.getBoolean("auto_scan_enabled", true)) }
    var isDriveMode by remember { mutableStateOf(false) }

    var loopPointA by remember { mutableLongStateOf(-1L) }
    var loopPointB by remember { mutableLongStateOf(-1L) }

    var eqPreset by remember { mutableStateOf(prefs.getString("eq_preset", "Phonk Master") ?: "Phonk Master") }
    var bandGains by remember {
        mutableStateOf(loadFloatList(prefs.getString("band_gains", null)) ?: listOf(0.80f, 0.45f, -0.10f, 0.50f, 0.85f))
    }
    var bassStrength by remember { mutableIntStateOf(prefs.getInt("bass_strength", 75)) }
    var virtualizerStrength by remember { mutableIntStateOf(prefs.getInt("virtualizer_strength", 55)) }
    var loudnessGain by remember { mutableIntStateOf(prefs.getInt("loudness_gain", 40)) }
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
            .putString("disc_style", discStyle)
            .putString("wave_style", waveStyle)
            .putInt("corner_radius_dp", cornerRadiusDp)
            .putFloat("card_opacity", cardOpacity)
            .putFloat("font_scale", fontScale)
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
            .putBoolean("auto_scan_enabled", autoScanEnabled)
            .putString("eq_preset", eqPreset)
            .putString("band_gains", JSONArray(bandGains).toString())
            .putInt("bass_strength", bassStrength)
            .putInt("virtualizer_strength", virtualizerStrength)
            .putInt("loudness_gain", loudnessGain)
            .putFloat("playback_speed", playbackSpeed)
            .putFloat("playback_pitch", playbackPitch)
            .apply()
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                query = spoken
                searchExpanded = true
                tab = "Songs"
            }
        }
    }

    val deleteResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            songs = loadSongs()
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customBgUri = uri.toString()
            bgDimAlpha = 0.30f
            if (cardOpacity > 0.40f) cardOpacity = 0.22f
            saveState()
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("version", "3.0.0-ULTRA")
                    put("liked_ids", JSONArray(likedIds.toList()))
                    put("recent_ids", JSONArray(recentIds))
                    put("playlists", JSONObject(encodePlaylists(playlists)))
                    put("lyrics_map", JSONObject(encodeLyricsMap(customLyrics)))
                    put("ui_layout", uiLayout)
                    put("theme_name", themeName)
                    put("eq_preset", eqPreset)
                    put("band_gains", JSONArray(bandGains))
                    put("bass_strength", bassStrength)
                    put("loudness_gain", loudnessGain)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
                Toast.makeText(context, "Backup Saved!", Toast.LENGTH_SHORT).show()
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
                if (root.has("bass_strength")) bassStrength = root.getInt("bass_strength")
                if (root.has("loudness_gain")) loudnessGain = root.getInt("loudness_gain")
                saveState()
                Toast.makeText(context, "Restored!", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "⏭ Shake Skip!", Toast.LENGTH_SHORT).show()
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

    DisposableEffect(permission, autoScanEnabled) {
        if (!permission || !autoScanEnabled) return@DisposableEffect onDispose { }
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

    LaunchedEffect(player, crossfadeSec, loopPointA, loopPointB) {
        var tick = 0
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
                if (crossfadeSec > 0 && realDur > crossfadeSec * 2500L && player.isPlaying) {
                    val fadeWindow = crossfadeSec * 1000L
                    val remaining = realDur - position
                    player.volume = if (remaining in 1..fadeWindow) {
                        (remaining.toFloat() / fadeWindow.toFloat()).coerceIn(0.25f, 1f)
                    } else 1f
                } else {
                    player.volume = 1f
                }
            }
            delay(300)
        }
    }

    val primaryAccent by animateColorAsState(
        targetValue = when (themeName) {
            "Dynamic Cover Aura" -> coverPalette.dominant
            "Cyber Gold VIP" -> Color(0xFFFFC857)
            "Blood Phonk Crimson" -> Color(0xFFFF2A54)
            "Matrix Emerald" -> Color(0xFF00F59B)
            "Oceanic Ice" -> Color(0xFF00D2FF)
            else -> Color(0xFFA87BFF)
        },
        label = "accent"
    )

    val bgNight = when {
        uiLayout == "AMOLED Pure" -> Color(0xFF000000)
        themeName == "Dynamic Cover Aura" -> Color(0xFF05040A)
        else -> Color(0xFF030408)
    }

    val effectiveCardAlpha = when {
        !customBgUri.isNullOrBlank() -> cardOpacity.coerceIn(0.10f, 0.42f)
        uiLayout == "AMOLED Pure" -> cardOpacity.coerceIn(0.0f, 0.22f)
        uiLayout == "Cyber Glass" -> cardOpacity.coerceAtLeast(0.22f)
        else -> cardOpacity.coerceAtLeast(0.16f)
    }
    val surfacePanel = Color(0xFF141626).copy(alpha = effectiveCardAlpha)
    val mutedText = Color(0xFFA5A9C0)

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

    if (showUiStudioSheet) {
        AlertDialog(
            onDismissRequest = { showUiStudioSheet = false },
            containerColor = Color(0xFF0C0E18),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = primaryAccent)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.uiStudioTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(strings.layoutSectionTitle, color = primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "AMOLED Pure" to "AMOLED مسطح",
                            "Cyber Glass" to "زجاجي عصري",
                            "Bento Grid" to "شبكي 2x2",
                            "Compact Pro" to "مدمج استوديو"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = uiLayout == id,
                                onClick = {
                                    uiLayout = id
                                    cardOpacity = when (id) {
                                        "AMOLED Pure" -> 0.0f
                                        "Cyber Glass" -> 0.28f
                                        else -> 0.20f
                                    }
                                    saveState()
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text(strings.themeTitle, color = primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Pitch Black Neon",
                            "Dynamic Cover Aura",
                            "Cyber Gold VIP",
                            "Blood Phonk Crimson",
                            "Matrix Emerald",
                            "Oceanic Ice"
                        ).forEach { th ->
                            FilterChip(
                                selected = themeName == th,
                                onClick = { themeName = th; saveState() },
                                label = { Text(th, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("شكل مشغل الأغنية (Now Playing Disc)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Vinyl Spin" to "أسطوانة دوارة",
                            "Square Studio" to "بوستر مربع",
                            "Neon Ring" to "حلقة نيون"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = discStyle == id,
                                onClick = { discStyle = id; saveState() },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("شكل الموجات الصوتية (Visualizer)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Smooth Wave" to "موجة ليزر",
                            "Spectrum Bars" to "أعمدة طيفية",
                            "Pulse Dots" to "نقاط نبضية",
                            "Hidden" to "إخفاء"
                        ).forEach { (id, label) ->
                            FilterChip(
                                selected = waveStyle == id,
                                onClick = { waveStyle = id; saveState() },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column {
                            Text("Corner Radius (${cornerRadiusDp}dp)", color = mutedText, fontSize = 11.sp)
                            Slider(
                                value = cornerRadiusDp.toFloat(),
                                onValueChange = { cornerRadiusDp = it.toInt(); saveState() },
                                valueRange = 4f..30f
                            )
                            Text("Song Card Opacity (${(cardOpacity * 100).toInt()}% • 0% = Pure AMOLED)", color = mutedText, fontSize = 11.sp)
                            Slider(
                                value = cardOpacity,
                                onValueChange = { cardOpacity = it; saveState() },
                                valueRange = 0f..0.75f
                            )
                            Text("Font Scale (${"%.1f".format(fontScale)}x)", color = mutedText, fontSize = 11.sp)
                            Slider(
                                value = fontScale,
                                onValueChange = { fontScale = it; saveState() },
                                valueRange = 0.85f..1.25f
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUiStudioSheet = false }) {
                    Text("تم", color = primaryAccent, fontWeight = FontWeight.Bold)
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
                    MenuActionRow(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, strings.favToggle, Color.White) {
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
                    MenuActionRow(Icons.Default.DeleteForever, strings.deleteDevice, Color(0xFFFF5C5C)) {
                        songForDeleteConfirm = target
                        songForMenu = null
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songForMenu = null }) { Text("OK", color = primaryAccent) } }
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
            title = { Text(strings.setRingtone, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
            title = { Text(strings.fileInfo, color = primaryAccent, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoItem("Title", target.title)
                    InfoItem("Artist", target.artist)
                    InfoItem("Album", target.album)
                    InfoItem("Duration / Size", "${formatTime(target.duration)} • $mb")
                    InfoItem("Play Count", "$plays times")
                    InfoItem("Path", target.filePath ?: target.uri.toString())
                }
            },
            confirmButton = { TextButton(onClick = { songForInfo = null }) { Text("OK", color = primaryAccent) } }
        )
    }

    songForDeleteConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { songForDeleteConfirm = null },
            containerColor = Color(0xFF0E101C),
            title = { Text(strings.deleteDevice, color = Color(0xFFFF5C5C), fontWeight = FontWeight.Bold) },
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
                }) { Text("Delete", color = Color(0xFFFF5C5C), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { songForDeleteConfirm = null }) { Text("Cancel", color = Color.White) } }
        )
    }

    songForAddToPlaylist?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { songForAddToPlaylist = null },
            containerColor = Color(0xFF0E101C),
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
            containerColor = Color(0xFF0E101C),
            title = { Text(strings.newPlaylist, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Gym Phonk / Favorites") },
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
        val rawLyrics = remember(targetSong.id, customLyrics) {
            customLyrics[targetSong.id] ?: readRawSidecarLyrics(targetSong.filePath) ?: ""
        }
        val parsedLrc = remember(rawLyrics) { parseLrcLines(rawLyrics) }
        var editingMode by remember { mutableStateOf(rawLyrics.isBlank()) }
        var draftText by remember { mutableStateOf(rawLyrics) }

        AlertDialog(
            onDismissRequest = { songForLyrics = null },
            containerColor = Color(0xFF0C0E18),
            title = { Text("${strings.viewLyrics} • ${targetSong.title}", color = Color.White, fontSize = 15.sp, maxLines = 1) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingMode) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("[00:12.00] Paste synced LRC or normal lyrics...") },
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
                        Text(draftText.ifBlank { "No lyrics saved yet. Tap Edit to paste." }, color = Color.White, lineHeight = 22.sp)
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
            containerColor = Color(0xFF0E101C),
            title = { Text(strings.sleepTimer, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                        TextButton(
                            onClick = { startSleepTimer(mins); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins min (Smooth Fade-Out)", color = Color.White) }
                    }
                    if (sleepTimerRemainingSec > 0) {
                        TextButton(
                            onClick = { startSleepTimer(0); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel Timer", color = Color(0xFFFF5C5C)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Close") } }
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
                                        listOf(Color.Transparent, bgNight.copy(alpha = 0.85f), bgNight.copy(alpha = 0.96f))
                                    )
                                )
                            ) {
                                if (currentSong != null && tab != "Now Playing") {
                                    SleekMiniPlayerPill(
                                        song = currentSong,
                                        playing = playing,
                                        position = position,
                                        duration = duration,
                                        accent = primaryAccent,
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
                                    containerColor = Color.Black.copy(alpha = if (customBgUri != null) 0.55f else 0.92f),
                                    tonalElevation = 0.dp
                                ) {
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
                                                        "EQ Studio" -> Icons.Default.GraphicEq
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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brush.linearGradient(listOf(primaryAccent, Color(0xFF4A25AA)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.GraphicEq, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "VYRO",
                                            color = Color.White,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "ULTRA",
                                            color = primaryAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    Text(
                                        "BY MOHAMED TAREK • ${songs.size} ${strings.tracksCount}",
                                        color = mutedText,
                                        fontSize = 9.sp,
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
                                            if (searchExpanded || query.isNotEmpty()) primaryAccent.copy(alpha = 0.22f)
                                            else Color.White.copy(alpha = 0.06f),
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
                                        .background(primaryAccent.copy(alpha = 0.18f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Palette, "UI Studio", tint = primaryAccent, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (currentSong != null) isDriveMode = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.06f), CircleShape)
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
                                            cornerDp = cornerRadiusDp,
                                            loopA = loopPointA,
                                            loopB = loopPointB,
                                            accent = primaryAccent,
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
                                            onToggleABLoop = {
                                                when {
                                                    loopPointA < 0L -> {
                                                        loopPointA = position
                                                        Toast.makeText(context, "Point A: ${formatTime(position)}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    loopPointB < 0L && position > loopPointA + 1000L -> {
                                                        loopPointB = position
                                                        Toast.makeText(context, "A-B Loop Active!", Toast.LENGTH_SHORT).show()
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
                                        accent = primaryAccent,
                                        cornerDp = cornerRadiusDp,
                                        onSelectPreset = { preset ->
                                            eqPreset = preset
                                            bandGains = when (preset) {
                                                "Bass Extreme" -> listOf(1.0f, 0.80f, 0.05f, 0.25f, 0.50f)
                                                "Phonk Master" -> listOf(0.95f, 0.55f, -0.15f, 0.60f, 0.90f)
                                                "VIP Studio" -> listOf(0.55f, 0.35f, 0.15f, 0.45f, 0.70f)
                                                "Vocal Crystal" -> listOf(-0.15f, 0.30f, 0.90f, 0.65f, 0.25f)
                                                else -> listOf(0f, 0f, 0f, 0f, 0f)
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
                                        strings = strings,
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
                                        strings = strings,
                                        uiLayout = uiLayout,
                                        themeName = themeName,
                                        langSetting = langSetting,
                                        customBgUri = customBgUri,
                                        bgDimAlpha = bgDimAlpha,
                                        shakeSkipEnabled = shakeSkipEnabled,
                                        crossfadeSec = crossfadeSec,
                                        sleepRemainingSec = sleepTimerRemainingSec,
                                        tracksPlayedStat = prefs.getInt("stat_tracks_played", 0),
                                        accent = primaryAccent,
                                        cornerDp = cornerRadiusDp,
                                        onOpenUiStudio = { showUiStudioSheet = true },
                                        onSelectLayout = {
                                            uiLayout = it
                                            cardOpacity = if (it == "AMOLED Pure") 0f else 0.25f
                                            saveState()
                                        },
                                        onSelectTheme = { themeName = it; saveState() },
                                        onSelectLang = { langSetting = it; saveState() },
                                        onPickBg = { bgImagePicker.launch(arrayOf("image/*")) },
                                        onClearBg = { customBgUri = null; saveState() },
                                        onChangeDim = { bgDimAlpha = it; saveState() },
                                        onToggleShake = { shakeSkipEnabled = !shakeSkipEnabled; saveState() },
                                        onChangeCrossfade = { crossfadeSec = it; saveState() },
                                        onOpenSleep = { showSleepDialog = true },
                                        onBackup = { backupLauncher.launch("vyro_ultra_backup.json") },
                                        onRestore = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                                    )
                                }

                                else -> {
                                    AnimatedVisibility(visible = searchExpanded || query.isNotEmpty()) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clip(RoundedCornerShape(cornerRadiusDp.dp))
                                                .background(Color(0xFF10121E).copy(alpha = 0.90f))
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
                                                    placeholder = { Text(strings.searchHint, color = mutedText, fontSize = 12.sp) },
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
                                                    "TITLE" to "A-Z",
                                                    "ARTIST" to "Artist",
                                                    "DURATION" to "Duration",
                                                    "NEWEST" to "Newest",
                                                    "FAV" to "❤ Favorites"
                                                ).forEach { (key, label) ->
                                                    FilterChip(
                                                        selected = sortBy == key,
                                                        onClick = { sortBy = key; saveState() },
                                                        label = { Text(label, fontSize = 11.sp) }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("AMOLED Pure", "Cyber Glass", "Bento Grid", "Compact Pro").forEach { layoutMode ->
                                            val active = uiLayout == layoutMode
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(if (active) primaryAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                                    .border(1.dp, if (active) primaryAccent else Color.Transparent, RoundedCornerShape(50))
                                                    .clickable {
                                                        uiLayout = layoutMode
                                                        cardOpacity = if (layoutMode == "AMOLED Pure") 0f else 0.25f
                                                        saveState()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    layoutMode,
                                                    color = if (active) primaryAccent else mutedText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(0xFFFFC857).copy(alpha = 0.16f))
                                                .border(1.dp, Color(0xFFFFC857).copy(alpha = 0.5f), RoundedCornerShape(50))
                                                .clickable { showUiStudioSheet = true }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("🎨 الثيمات والتخصيص", color = Color(0xFFFFC857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (!permission) {
                                        EmptyState("يرجى منح إذن الوصول للملفات الصوتية لعرض مكتبتك", primaryAccent)
                                    } else if (sortedSongs.isEmpty()) {
                                        EmptyState("لا توجد مقاطع صوتية مطابقة", primaryAccent)
                                    } else if (uiLayout == "Bento Grid") {
                                        val pairs = remember(sortedSongs) { sortedSongs.chunked(2) }
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(bottom = 16.dp)
                                        ) {
                                            itemsIndexed(pairs) { rowIndex, pair ->
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    pair.forEachIndexed { colIndex, song ->
                                                        val realIdx = rowIndex * 2 + colIndex
                                                        Box(Modifier.weight(1f)) {
                                                            BentoSongCard(
                                                                song = song,
                                                                active = currentSongId == song.id,
                                                                isLiked = likedIds.contains(song.id),
                                                                accent = primaryAccent,
                                                                cornerDp = cornerRadiusDp,
                                                                fontScale = fontScale,
                                                                onClick = { playQueue(sortedSongs, realIdx, 0L, true) },
                                                                onMore = { songForMenu = song }
                                                            )
                                                        }
                                                    }
                                                    if (pair.size == 1) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(if (uiLayout == "AMOLED Pure") 2.dp else 7.dp),
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

@Composable
private fun SleekMiniPlayerPill(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    accent: Color,
    cornerDp: Int,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit
) {
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(Color(0xFF0A0C16).copy(alpha = 0.88f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(cornerDp.dp))
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
                    .height(2.dp)
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
            .padding(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onOpenUiStudio)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(discStyle.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onOpenMenu, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .size(235.dp)
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
                "Square Studio" -> {
                    Box(
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(cornerDp.dp))
                            .background(Color(0xFF121422))
                            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(cornerDp.dp)),
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
                "Vinyl Spin" -> {
                    Box(
                        Modifier
                            .size(225.dp)
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
                        drawCircle(
                            brush = Brush.radialGradient(listOf(palette.dominant.copy(alpha = 0.42f), Color.Transparent)),
                            radius = r
                        )
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

        Spacer(Modifier.height(14.dp))

        if (waveStyle != "Hidden") {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 12.dp)
            ) {
                when (waveStyle) {
                    "Smooth Wave" -> {
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
                    }
                    "Pulse Dots" -> {
                        val dots = 28
                        val space = size.width / dots
                        for (i in 0 until dots) {
                            val f = if (playing) abs(sin(pulsePhase + i * 0.35f)) else 0.25f
                            drawCircle(
                                color = if (i % 2 == 0) accent else palette.dominant,
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
                                color = if (i % 2 == 0) accent else palette.dominant,
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
                Text("${song.artist} • ${song.album}", color = Color(0xFFA5A9C0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    val abStatus = when {
                        loopA >= 0L && loopB >= 0L -> "A-B LOOP (${formatTime(loopA)}-${formatTime(loopB)})"
                        loopA >= 0L -> "A SET (${formatTime(loopA)}) • TAP FOR B"
                        else -> "HI-RES 24-BIT DSP"
                    }
                    Text(abStatus, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                        .background(Brush.linearGradient(listOf(accent, palette.dominant)), CircleShape)
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
                onClick = onToggleABLoop,
                label = { Text(if (loopA >= 0L) "A-B Active" else "A-B Loop", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Repeat, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
            AssistChip(
                onClick = onOpenLyrics,
                label = { Text("Lyrics LRC", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Lyrics, null, tint = accent, modifier = Modifier.size(15.dp)) }
            )
            AssistChip(
                onClick = onOpenSleep,
                label = {
                    Text(
                        if (sleepRemainingSec > 0) "${sleepRemainingSec / 60}:${"%02d".format(sleepRemainingSec % 60)}"
                        else "Sleep Timer",
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
    accent: Color,
    cornerDp: Int,
    onSelectPreset: (String) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onBassChange: (Int) -> Unit,
    onVirtChange: (Int) -> Unit,
    onLoudnessChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onOpenSystemEq: () -> Unit
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    val cardBg = Color(0xFF0E101C).copy(alpha = 0.72f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Phonk Master", "Bass Extreme", "VIP Studio", "Vocal Crystal", "Flat").forEach { preset ->
                FilterChip(
                    selected = eqPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
                        Text("${(gain * 15).toInt()}dB", color = accent, fontSize = 11.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
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
                Text("SUB-BASS OVERDRIVE ($bassStrength%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = bassStrength.toFloat(),
                    onValueChange = { onBassChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                )

                Text("3D PRE-AMP LOUDNESS BOOST (+${(loudnessGain * 0.1f).toInt()} dB)", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(
                    value = loudnessGain.toFloat(),
                    onValueChange = { onLoudnessChange(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFFC857), activeTrackColor = Color(0xFFFFC857))
                )

                Text("SPATIAL 3D VIRTUALIZER ($virtualizerStrength%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    OutlinedButton(onClick = { onSpeedChange(0.84f); onPitchChange(0.86f) }) {
                        Text("Slowed + Phonk", color = accent, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { onSpeedChange(1.18f); onPitchChange(1.14f) }) {
                        Text("Nightcore", color = Color.White, fontSize = 11.sp)
                    }
                }
                Slider(value = playbackSpeed, onValueChange = onSpeedChange, valueRange = 0.6f..1.6f)
            }
        }

        OutlinedButton(onClick = onOpenSystemEq, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Speaker, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("Dolby Atmos / System Audio Panel", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SmartPlaylistsScreen(
    strings: Strings,
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
            Text(strings.playlists, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black)
            TextButton(onClick = onCreateNew) { Text(strings.newPlaylist, color = accent, fontWeight = FontWeight.Bold) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FeatureTile(strings.likedSongs, "${likedIds.size} ${strings.tracksCount}", Icons.Default.Favorite, accent, cornerDp) {
                    onChangeView("__LIKED__")
                }
            }
            item {
                FeatureTile(strings.mostPlayed, "${playCounts.size} ${strings.tracksCount}", Icons.Default.Star, Color(0xFFFFC857), cornerDp) {
                    onChangeView("__MOST_PLAYED__")
                }
            }
            item {
                FeatureTile(strings.recentlyPlayed, "${recentIds.size} ${strings.tracksCount}", Icons.Default.History, accent, cornerDp) {
                    onChangeView("__RECENT__")
                }
            }
            item {
                FeatureTile(strings.albums, "${songs.map { it.album }.distinct().size} ${strings.albums}", Icons.Default.Album, accent, cornerDp) {
                    onChangeView("__ALBUMS__")
                }
            }
            itemsIndexed(playlists.entries.toList()) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E101C).copy(alpha = 0.72f), RoundedCornerShape(cornerDp.dp))
                        .clickable { onChangeView(entry.key) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.QueueMusic, null, tint = accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${entry.value.size} ${strings.tracksCount}", color = Color(0xFFA5A9C0), fontSize = 11.sp)
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
            Text(strings.albums, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        val albums = songs.groupBy { it.album }.entries.toList()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(albums) { _, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E101C).copy(alpha = 0.72f), RoundedCornerShape(cornerDp.dp))
                        .clickable { onSelectAlbum(entry.key) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtThumb(entry.value.firstOrNull(), accent, 46, cornerDp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.key, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${entry.value.size} ${strings.tracksCount}", color = Color(0xFFA5A9C0), fontSize = 11.sp)
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
                    uiLayout = "AMOLED Pure",
                    accent = accent,
                    panel = panel,
                    cornerDp = cornerDp,
                    fontScale = fontScale,
                    onClick = { onPlayList(listSongs, idx) },
                    onMore = { onMoreSong(song) }
                )
            }
        }
    }
}

@Composable
private fun UltraSettingsScreen(
    strings: Strings,
    uiLayout: String,
    themeName: String,
    langSetting: String,
    customBgUri: String?,
    bgDimAlpha: Float,
    shakeSkipEnabled: Boolean,
    crossfadeSec: Int,
    sleepRemainingSec: Int,
    tracksPlayedStat: Int,
    accent: Color,
    cornerDp: Int,
    onOpenUiStudio: () -> Unit,
    onSelectLayout: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onSelectLang: (String) -> Unit,
    onPickBg: () -> Unit,
    onClearBg: () -> Unit,
    onChangeDim: (Float) -> Unit,
    onToggleShake: () -> Unit,
    onChangeCrossfade: (Int) -> Unit,
    onOpenSleep: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val cardBg = Color(0xFF0E101C).copy(alpha = 0.72f)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onOpenUiStudio,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cornerDp.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent)
        ) {
            Icon(Icons.Default.Palette, null, tint = Color.Black)
            Spacer(Modifier.width(8.dp))
            Text(strings.uiStudioTitle, color = Color.Black, fontWeight = FontWeight.Black)
        }

        Column(
            Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(cornerDp.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.layoutSectionTitle, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("AMOLED Pure", "Cyber Glass", "Bento Grid", "Compact Pro").forEach { ly ->
                    FilterChip(selected = uiLayout == ly, onClick = { onSelectLayout(ly) }, label = { Text(ly, fontSize = 11.sp) })
                }
            }

            Text(strings.themeTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Pitch Black Neon", "Dynamic Cover Aura", "Cyber Gold VIP", "Blood Phonk Crimson", "Matrix Emerald", "Oceanic Ice").forEach { th ->
                    FilterChip(selected = themeName == th, onClick = { onSelectTheme(th) }, label = { Text(th, fontSize = 11.sp) })
                }
            }

            Button(
                onClick = onPickBg,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.Wallpaper, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(strings.pickBgImage, color = Color.White, fontSize = 12.sp)
            }
            if (!customBgUri.isNullOrBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        Text("${strings.bgDimLevel}: ${(bgDimAlpha * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                        Slider(value = bgDimAlpha, onValueChange = onChangeDim, valueRange = 0.05f..0.85f)
                    }
                }
                OutlinedButton(onClick = onClearBg, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.clearBgImage, color = Color(0xFFFF5C5C), fontSize = 12.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(cornerDp.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(strings.langTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "SYSTEM" to strings.langSystem,
                    "AR" to "العربية",
                    "EN" to "English",
                    "FR" to "Français",
                    "ES" to "Español",
                    "DE" to "Deutsch",
                    "IT" to "Italiano",
                    "TR" to "Türkçe",
                    "RU" to "Русский",
                    "HI" to "हिन्दी"
                ).forEach { (code, name) ->
                    FilterChip(selected = langSetting == code, onClick = { onSelectLang(code) }, label = { Text(name, fontSize = 11.sp) })
                }
            }
        }

        FeatureTile(
            title = strings.shakeToSkip,
            subtitle = if (shakeSkipEnabled) "ON • هز الموبايل يقلب الأغنية" else "OFF",
            icon = Icons.Default.Vibration,
            accent = accent,
            cornerDp = cornerDp,
            onClick = onToggleShake
        )

        FeatureTile(
            title = strings.sleepTimer,
            subtitle = if (sleepRemainingSec > 0) "${sleepRemainingSec / 60}m active" else "Fade-Out Timer",
            icon = Icons.Default.Bedtime,
            accent = accent,
            cornerDp = cornerDp,
            onClick = onOpenSleep
        )

        Column(Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(cornerDp.dp)).padding(12.dp)) {
            Text("${strings.crossfade}: ${if (crossfadeSec == 0) "Off" else "$crossfadeSec sec"}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(value = crossfadeSec.toFloat(), onValueChange = { onChangeCrossfade(it.toInt()) }, valueRange = 0f..10f, steps = 9)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CloudUpload, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export JSON", color = Color.White, fontSize = 11.sp)
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CloudDownload, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import JSON", color = Color.White, fontSize = 11.sp)
            }
        }

        Text(
            "VYRO MUSIC 3.0.0-ULTRA • DEVELOPED BY MOHAMED TAREK • PLAYED: $tracksPlayedStat",
            color = Color(0xFF7E8299),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
            OutlinedButton(onClick = onExit) { Text("Exit", color = Color.White) }
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
    fontScale: Float,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val isCompact = uiLayout == "Compact Pro"
    val isAmoled = uiLayout == "AMOLED Pure"
    val rowBg = if (active) accent.copy(alpha = 0.14f) else panel

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerDp.dp))
                .background(rowBg)
                .border(
                    width = if (active) 1.dp else if (uiLayout == "Cyber Glass") 0.6.dp else 0.dp,
                    color = if (active) accent else if (uiLayout == "Cyber Glass") Color.White.copy(alpha = 0.10f) else Color.Transparent,
                    shape = RoundedCornerShape(cornerDp.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = if (isCompact) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(song, accent, sizeDp = if (isCompact) 38 else 46, cornerDp = (cornerDp - 4).coerceAtLeast(6))
            Spacer(Modifier.width(10.dp))
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
                    color = Color(0xFF989CB3),
                    fontSize = (11f * fontScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isLiked) {
                Icon(Icons.Default.Favorite, null, tint = accent, modifier = Modifier.size(14.dp))
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
    accent: Color,
    cornerDp: Int,
    fontScale: Float,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerDp.dp))
            .background(Color(0xFF0E101C).copy(alpha = 0.65f))
            .border(1.dp, if (active) accent else Color.White.copy(alpha = 0.08f), RoundedCornerShape(cornerDp.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(125.dp)
                .clip(RoundedCornerShape((cornerDp - 4).coerceAtLeast(8).dp))
                .background(Color(0xFF17192B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(40.dp))
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            color = if (active) accent else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (12.5f * fontScale).sp,
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
            .background(Color(0xFF0E101C).copy(alpha = 0.72f), RoundedCornerShape(cornerDp.dp))
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
        val r = (rSum / count).toInt().coerceIn(40, 245)
        val g = (gSum / count).toInt().coerceIn(40, 245)
        val b = (bSum / count).toInt().coerceIn(40, 245)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

        CoverPalette(
            dominant = Color((r * 1.25f).toInt().coerceIn(80, 255), (g * 1.15f).toInt().coerceIn(70, 250), (b * 1.30f).toInt().coerceIn(95, 255)),
            secondary = Color(r / 4, g / 4, b / 4),
            isLightCover = luminance > 0.55
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
