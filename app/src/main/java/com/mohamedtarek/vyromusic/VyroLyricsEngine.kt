package com.mohamedtarek.vyromusic

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

// ==================== SYNCED LYRICS DATA MODELS ====================

data class LyricLine(
    val timeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class LyricsData(
    val synced: List<LyricLine>,
    val plain: String,
    val rawLrc: String,
    val source: String
)

// ==================== FREE ZERO-API-KEY LYRICS ENGINE (LRCLIB) ====================

object VyroLyricsEngine {

    private val lrcRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val arabicRegex = Regex("[\\u0600-\\u06FF]")

    fun isArabicText(text: String): Boolean = arabicRegex.containsMatchIn(text)

    fun cleanTrackQuery(raw: String): String {
        return raw
            .replace(Regex("\\.(mp3|m4a|wav|flac|ogg|aac)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\([^)]*(official|video|audio|lyrics|hd|4k|hq|mv|clip|remix|bass|slowed|reverb|كلمات|فيديو|كليب|حصريا|رسمي)[^)]*\\)", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\[[^]]*\\]"), " ")
            .replace(Regex("\\b(320kbps|128kbps|mp3|feat\\.?|ft\\.?|prod\\.?)\\b", RegexOption.IGNORE_CASE), " ")
            .replace("_", " ")
            .replace("|", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parseLrc(rawLrc: String, totalDurationMs: Long): List<LyricLine> {
        if (rawLrc.isBlank()) return emptyList()
        val rawPairs = mutableListOf<Pair<Long, String>>()
        rawLrc.lines().forEach { line ->
            val matches = lrcRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val text = line.replace(lrcRegex, "").trim()
                if (text.isNotEmpty()) {
                    matches.forEach { m ->
                        val min = m.groupValues[1].toLongOrNull() ?: 0L
                        val sec = m.groupValues[2].toLongOrNull() ?: 0L
                        val fracStr = m.groupValues[3]
                        val ms = when (fracStr.length) {
                            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                            3 -> fracStr.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        val timeMs = (min * 60_000L) + (sec * 1000L) + ms
                        rawPairs.add(timeMs to text)
                    }
                }
            }
        }
        val sorted = rawPairs.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()
        return sorted.mapIndexed { idx, (timeMs, text) ->
            val nextTime = if (idx < sorted.lastIndex) sorted[idx + 1].first else maxOf(totalDurationMs, timeMs + 4500L)
            val endMs = nextTime.coerceAtLeast(timeMs + 900L)
            LyricLine(timeMs = timeMs, endTimeMs = endMs, text = text)
        }
    }

    // توليد تأثير بصري للمدّ الصوتي للجمل الممتدة (مثل ياااااا)
    fun hasVocalElongation(text: String): Boolean {
        return Regex("([اويىآaeiuyo])\\1{2,}", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            text.contains("ـ")
    }

    fun loadCachedLyrics(prefs: SharedPreferences, song: Song): LyricsData? {
        val rawLrc = VyroSafePrefs.getString(prefs, "lyrics_lrc_${song.id}", "")
        val plain = VyroSafePrefs.getString(prefs, "lyrics_${song.id}", "")
        val src = VyroSafePrefs.getString(prefs, "lyrics_src_${song.id}", "Saved")
        if (rawLrc.isNotBlank()) {
            val parsed = parseLrc(rawLrc, song.duration)
            if (parsed.isNotEmpty()) return LyricsData(parsed, plain.ifBlank { parsed.joinToString("\n") { it.text } }, rawLrc, src)
        }
        if (plain.isNotBlank()) {
            val parsed = parseLrc(plain, song.duration)
            return LyricsData(parsed, plain, if (parsed.isNotEmpty()) plain else "", src)
        }
        return null
    }

    fun saveLyrics(prefs: SharedPreferences, songId: Long, rawLrc: String, plain: String, source: String) {
        prefs.edit()
            .putString("lyrics_lrc_$songId", rawLrc)
            .putString("lyrics_$songId", plain)
            .putString("lyrics_src_$songId", source)
            .apply()
    }

    fun getOffsetMs(prefs: SharedPreferences, songId: Long): Long =
        VyroSafePrefs.getLong(prefs, "lyrics_offset_$songId", 0L)

    fun saveOffsetMs(prefs: SharedPreferences, songId: Long, offsetMs: Long) {
        prefs.edit().putLong("lyrics_offset_$songId", offsetMs).apply()
    }

    suspend fun fetchOnlineLyrics(song: Song, prefs: SharedPreferences, forceRefresh: Boolean = false): LyricsData? =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                loadCachedLyrics(prefs, song)?.let { return@withContext it }
            }

            val cleanTitle = cleanTrackQuery(song.title)
            val rawArtist = song.artist.takeUnless {
                it.equals("<unknown>", ignoreCase = true) || it.equals("unknown", ignoreCase = true)
            }.orEmpty()
            val cleanArtist = cleanTrackQuery(rawArtist)
            val durSec = (song.duration / 1000L).coerceAtLeast(10L)

            // 1. محاولة المطابقة الدقيقة عبر LRCLIB /api/get (مجاني 100% بدون API Key)
            if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                val directUrl = "https://lrclib.net/api/get?track_name=${enc(cleanTitle)}&artist_name=${enc(cleanArtist)}&duration=$durSec"
                httpGetJson(directUrl)?.let { body ->
                    parseSingleLrcLibObj(JSONObject(body), song)?.let { res ->
                        saveLyrics(prefs, song.id, res.rawLrc, res.plain, res.source)
                        return@withContext res
                    }
                }
            }

            // 2. محاولة البحث الذكي باسم الأغنية + الفنان (أو اسم الملف المقسّم بـ -)
            val queries = buildList {
                if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) add("$cleanTitle $cleanArtist")
                if (cleanTitle.isNotBlank()) add(cleanTitle)
                if (cleanTitle.contains("-")) {
                    val parts = cleanTitle.split("-").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        add("${parts[1]} ${parts[0]}")
                        add(parts[1])
                        add(parts[0])
                    }
                }
            }.distinct()

            for (q in queries) {
                val searchUrl = "https://lrclib.net/api/search?q=${enc(q)}"
                val body = httpGetJson(searchUrl) ?: continue
                val best = runCatching {
                    val arr = JSONArray(body)
                    var bestSynced: JSONObject? = null
                    var bestSyncedDiff = Long.MAX_VALUE
                    var bestPlain: JSONObject? = null

                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val synced = o.optString("syncedLyrics", "").trim()
                        val plain = o.optString("plainLyrics", "").trim()
                        val d = o.optLong("duration", 0L)
                        val diff = if (d > 0L) abs(d - durSec) else 999L

                        if (synced.isNotBlank() && synced != "null") {
                            if (diff < bestSyncedDiff) {
                                bestSyncedDiff = diff
                                bestSynced = o
                            }
                        } else if (plain.isNotBlank() && plain != "null" && bestPlain == null) {
                            bestPlain = o
                        }
                    }
                    bestSynced ?: bestPlain
                }.getOrNull()

                if (best != null) {
                    parseSingleLrcLibObj(best, song)?.let { res ->
                        saveLyrics(prefs, song.id, res.rawLrc, res.plain, res.source)
                        return@withContext res
                    }
                }
            }
            null
        }

    private fun parseSingleLrcLibObj(obj: JSONObject, song: Song): LyricsData? {
        val syncedRaw = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val plainRaw = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        if (syncedRaw.isBlank() && plainRaw.isBlank()) return null
        val parsed = parseLrc(syncedRaw, song.duration)
        val finalPlain = plainRaw.ifBlank { parsed.joinToString("\n") { it.text } }
        return LyricsData(
            synced = parsed,
            plain = finalPlain,
            rawLrc = syncedRaw,
            source = if (parsed.isNotEmpty()) "LRCLIB Synced" else "LRCLIB Lyrics"
        )
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun httpGetJson(urlStr: String): String? {
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 10000
                setRequestProperty("User-Agent", "VYRO-MUSIC/5.2.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val res = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            res
        }.getOrNull()
    }
}

// ==================== DYNAMIC SYNCED LYRICS FULL-SCREEN OVERLAY ====================

@Composable
fun DynamicSyncedLyricsOverlay(
    song: Song,
    isArabic: Boolean,
    accent: Color,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    prefs: SharedPreferences,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true) { onClose() }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var lyricsData by remember(song.id) { mutableStateOf<LyricsData?>(null) }
    var isLoading by remember(song.id) { mutableStateOf(true) }
    var loadingStage by remember(song.id) { mutableIntStateOf(0) }
    var offsetMs by remember(song.id) { mutableLongStateOf(VyroLyricsEngine.getOffsetMs(prefs, song.id)) }
    var showEditDialog by remember(song.id) { mutableStateOf(false) }

    fun loadLyrics(force: Boolean) {
        scope.launch {
            isLoading = true
            loadingStage = 0
            if (force) loadingStage = 1
            val fetched = VyroLyricsEngine.fetchOnlineLyrics(song, prefs, forceRefresh = force)
            lyricsData = fetched
            isLoading = false
        }
    }

    LaunchedEffect(song.id) {
        loadLyrics(force = false)
    }

    // انتقال رسائل شاشة التحميل لإحساس احترافي أثناء التنزيل
    LaunchedEffect(isLoading) {
        if (isLoading) {
            loadingStage = 0
            kotlinx.coroutines.delay(900L)
            if (isLoading) loadingStage = 1
            kotlinx.coroutines.delay(1100L)
            if (isLoading) loadingStage = 2
        }
    }

    val effectivePos = (positionMs + offsetMs).coerceAtLeast(0L)
    val synced = lyricsData?.synced.orEmpty()
    val activeIndex = remember(synced, effectivePos) {
        if (synced.isEmpty()) -1
        else synced.indexOfLast { effectivePos >= it.timeMs }
    }

    // تمرير تلقائي سلس لوضع السطر النشط في منتصف الشاشة
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && synced.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0))
            }
        }
    }

    val inf = rememberInfiniteTransition(label = "lyrAnim")
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "lyrWave"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050810))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.28f), Color(0xFF070B14), Color(0xFF03050A))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // الشريط العلوي
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = lyricsData?.source?.let { "${song.artist} • $it" } ?: song.artist,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { loadLyrics(force = true) }) {
                    Icon(Icons.Rounded.Refresh, null, tint = Color.White)
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White)
                }
            }

            // شريط معايرة التوقيت بالملي ثانية (لو فيه كلمات متزامنة)
            AnimatedVisibility(visible = !isLoading && synced.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.06f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isArabic) "تزامن الكلمات: ${String.format(Locale.US, "%+.1fs", offsetMs / 1000f)}"
                        else "Sync Offset: ${String.format(Locale.US, "%+.1fs", offsetMs / 1000f)}",
                        color = Color.White.copy(0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SyncOffsetChip("-0.5s", accent) {
                            offsetMs -= 500L
                            VyroLyricsEngine.saveOffsetMs(prefs, song.id, offsetMs)
                        }
                        SyncOffsetChip("0", Color.White.copy(0.75f)) {
                            offsetMs = 0L
                            VyroLyricsEngine.saveOffsetMs(prefs, song.id, 0L)
                        }
                        SyncOffsetChip("+0.5s", accent) {
                            offsetMs += 500L
                            VyroLyricsEngine.saveOffsetMs(prefs, song.id, offsetMs)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // منطقة عرض الكلمات أو شاشة التحميل
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        // شاشة تحميل احترافية أثناء تنزيل الكلمات المتزامنة
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(0.16f))
                                    .border(2.dp, accent.copy(0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(56.dp)) {
                                    val bars = 7
                                    val step = size.width / bars
                                    for (i in 0 until bars) {
                                        val h = ((sin(wavePhase + i * 0.7f) + 1f) * 0.4f + 0.18f) * size.height
                                        val x = i * step + step / 2f
                                        drawLine(
                                            color = accent,
                                            start = Offset(x, size.height / 2f - h / 2f),
                                            end = Offset(x, size.height / 2f + h / 2f),
                                            strokeWidth = 6f
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = when (loadingStage) {
                                    0 -> if (isArabic) "جاري البحث عن كلمات الأغنية..." else "Searching synced lyrics..."
                                    1 -> if (isArabic) "جاري تنزيل الكلمات المتزامنة..." else "Downloading time-synced lyrics..."
                                    else -> if (isArabic) "جاري ضبط التزامن بالملي ثانية..." else "Calibrating millisecond sync..."
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = song.title,
                                color = accent,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .width(210.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = accent,
                                trackColor = Color.White.copy(0.14f)
                            )
                        }
                    }

                    synced.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(synced) { idx, line ->
                                val isCurrent = idx == activeIndex
                                val isPast = idx < activeIndex
                                val lineDur = (line.endTimeMs - line.timeMs).coerceAtLeast(800L)
                                val lineProgress = if (isCurrent) {
                                    ((effectivePos - line.timeMs).toFloat() / lineDur.toFloat()).coerceIn(0f, 1f)
                                } else if (isPast) 1f else 0f

                                val isSustainedHold = lineDur >= 3400L || VyroLyricsEngine.hasVocalElongation(line.text)
                                val stretchBreath = if (isCurrent && isPlaying && isSustainedHold) {
                                    1.0f + (sin(wavePhase * 1.4f) + 1f) * 0.032f
                                } else 1.0f

                                val isRtlLine = VyroLyricsEngine.isArabicText(line.text)

                                DynamicKaraokeLineCard(
                                    line = line,
                                    isCurrent = isCurrent,
                                    isPast = isPast,
                                    progress = lineProgress,
                                    stretchScaleX = stretchBreath,
                                    isRtlLine = isRtlLine,
                                    accent = accent,
                                    onClick = { onSeek((line.timeMs - offsetMs).coerceAtLeast(0L)) }
                                )
                            }
                        }
                    }

                    !lyricsData?.plain.isNullOrBlank() -> {
                        val plainLines = lyricsData!!.plain.lines().filter { it.isNotBlank() }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = if (isArabic) "كلمات نصية (غير مزامنة زمنياً — يمكنك تعديلها بصيغة LRC من أيقونة القلم)"
                                    else "Plain lyrics (tap Edit to paste synced LRC)",
                                    color = accent,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                            }
                            itemsIndexed(plainLines) { _, line ->
                                Text(
                                    text = line,
                                    color = Color.White.copy(0.9f),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 29.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = accent.copy(0.7f), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (isArabic) "لم يتم العثور على كلمات تلقائية لهذا المقطع" else "No automatic lyrics found for this track",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isArabic) "تأكد من الاتصال بالإنترنت أو أن اسم الأغنية صحيح، أو أضفها يدوياً" else "Check internet or track title, or add lyrics manually",
                                color = Color.White.copy(0.65f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { loadLyrics(force = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                ) {
                                    Text(if (isArabic) "إعادة البحث" else "Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { showEditDialog = true }) {
                                    Text(if (isArabic) "إضافة يدوياً" else "Add Manually", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // شريط التحكم المصغر أسفل شاشة الكلمات
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    LinearProgressIndicator(
                        progress = { (positionMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                        color = accent,
                        trackColor = Color.White.copy(0.18f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { onTogglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        var rawInput by remember {
            mutableStateOf(
                lyricsData?.rawLrc?.takeIf { it.isNotBlank() }
                    ?: lyricsData?.plain.orEmpty()
            )
        }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(if (isArabic) "تعديل كلمات الأغنية (يدعم LRC)" else "Edit Lyrics (LRC Supported)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                    placeholder = { Text(if (isArabic) "الصق الكلمات أو توقيت [00:15.20] ياااااا..." else "Paste lyrics or [00:15.20] synced LRC...") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = VyroLyricsEngine.parseLrc(rawInput, song.duration)
                        val lrcToSave = if (parsed.isNotEmpty()) rawInput else ""
                        VyroLyricsEngine.saveLyrics(prefs, song.id, lrcToSave, rawInput, if (parsed.isNotEmpty()) "Custom Synced" else "Custom")
                        lyricsData = LyricsData(parsed, rawInput, lrcToSave, if (parsed.isNotEmpty()) "Custom Synced" else "Custom")
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text(if (isArabic) "حفظ" else "Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun DynamicKaraokeLineCard(
    line: LyricLine,
    isCurrent: Boolean,
    isPast: Boolean,
    progress: Float,
    stretchScaleX: Float,
    isRtlLine: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val words = remember(line.text) { line.text.split(" ").filter { it.isNotBlank() } }
    val activeWordCount = if (isCurrent && words.isNotEmpty()) {
        ((progress * words.size) + 0.35f).toInt().coerceIn(1, words.size)
    } else if (isPast) words.size else 0

    val annotated = remember(line.text, isCurrent, isPast, activeWordCount, accent) {
        buildAnnotatedString {
            if (!isCurrent) {
                withStyle(
                    SpanStyle(
                        color = if (isPast) Color.White.copy(0.42f) else Color.White.copy(0.32f),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(line.text)
                }
            } else {
                words.forEachIndexed { i, w ->
                    val isSung = i < activeWordCount
                    val isCurrentWord = i == activeWordCount - 1
                    val isElongatedWord = VyroLyricsEngine.hasVocalElongation(w)
                    withStyle(
                        SpanStyle(
                            color = when {
                                isCurrentWord && isElongatedWord -> Color(0xFFFFD56B)
                                isCurrentWord -> accent
                                isSung -> Color.White
                                else -> Color.White.copy(0.38f)
                            },
                            fontWeight = if (isSung) FontWeight.Black else FontWeight.Bold,
                            fontSize = if (isCurrentWord && isElongatedWord) 25.sp else 23.sp
                        )
                    ) {
                        append(w)
                    }
                    if (i < words.lastIndex) append(" ")
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isRtlLine) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (isCurrent) accent.copy(0.12f) else Color.Transparent)
                .border(
                    width = if (isCurrent) 1.dp else 0.dp,
                    color = if (isCurrent) accent.copy(0.35f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = if (isCurrent) 12.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = annotated,
                fontSize = if (isCurrent) 23.sp else 17.sp,
                lineHeight = if (isCurrent) 34.sp else 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = if (isCurrent) stretchScaleX else 1f
                        scaleY = if (isCurrent) 1.03f else 1f
                    }
            )
            if (isCurrent) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncOffsetChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(0.16f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== RINGTONE / AUDIO CUTTER (RESTORED FEATURE) ====================

object VyroAudioCutter {
    suspend fun cutAudioSegment(
        context: Context,
        song: Song,
        startMs: Long,
        endMs: Long
    ): String? = withContext(Dispatchers.IO) {
        val safeStartUs = (startMs.coerceAtLeast(0L)) * 1000L
        val safeEndUs = (endMs.coerceAtLeast(startMs + 1000L)) * 1000L
        val cleanName = song.title.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF\\s_-]"), "").trim().ifBlank { "VYRO_Cut" }
        val fileNameBase = "${cleanName}_cut_${System.currentTimeMillis() % 10000}"

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, song.uri, null)
            var audioTrackIdx = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIdx < 0 || audioFormat == null) return@withContext null

            val mime = audioFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val isMp3 = mime.contains("mpeg", ignoreCase = true)
            val ext = if (isMp3) "mp3" else "m4a"
            val outMime = if (isMp3) "audio/mpeg" else "audio/mp4"
            val finalFileName = "$fileNameBase.$ext"

            val tempFile = File(context.cacheDir, finalFileName)
            extractor.selectTrack(audioTrackIdx)
            extractor.seekTo(safeStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buf = ByteBuffer.allocate(256 * 1024)

            if (isMp3) {
                FileOutputStream(tempFile).use { fos ->
                    val arr = ByteArray(256 * 1024)
                    while (true) {
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) break
                        val ts = extractor.sampleTime
                        if (ts > safeEndUs) break
                        if (ts >= safeStartUs) {
                            buf.position(0)
                            buf.get(arr, 0, size)
                            fos.write(arr, 0, size)
                        }
                        extractor.advance()
                    }
                }
            } else {
                val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                try {
                    val muxTrack = muxer.addTrack(audioFormat)
                    muxer.start()
                    val info = MediaCodec.BufferInfo()
                    var firstTs = -1L
                    while (true) {
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) break
                        val ts = extractor.sampleTime
                        if (ts > safeEndUs) break
                        if (ts >= safeStartUs) {
                            if (firstTs < 0L) firstTs = ts
                            info.offset = 0
                            info.size = size
                            info.presentationTimeUs = (ts - firstTs).coerceAtLeast(0L)
                            info.flags = extractor.sampleFlags
                            muxer.writeSampleData(muxTrack, buf, info)
                        }
                        extractor.advance()
                    }
                    muxer.stop()
                } finally {
                    runCatching { muxer.release() }
                }
            }

            if (!tempFile.exists() || tempFile.length() < 1024L) return@withContext null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, outMime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/VYRO_Cuts")
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { it.copyTo(out) }
                    }
                    tempFile.delete()
                    return@withContext "Music/VYRO_Cuts/$finalFileName"
                }
            }

            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "VYRO_Cuts").apply { mkdirs() }
            val dest = File(fallbackDir, finalFileName)
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
            dest.name
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
}

@Composable
fun RingtoneCutterDialog(
    song: Song,
    isArabic: Boolean,
    accent: Color,
    onPreviewRange: (Long) -> Unit,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val totalMs = song.duration.coerceAtLeast(5000L)
    var range by remember(song.id) { mutableStateOf(0f..minOf(totalMs, 30_000L).toFloat()) }
    var isCutting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCutting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ContentCut, null, tint = accent)
                Spacer(Modifier.width(8.dp))
                Text(if (isArabic) "قص نغمة / مقطع صوتي" else "Ringtone & Audio Cutter", fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RangeSlider(
                        value = range,
                        onValueChange = { next ->
                            if (next.endInclusive - next.start >= 1500f) range = next
                        },
                        valueRange = 0f..totalMs.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = (if (isArabic) "البداية: " else "Start: ") + formatMs(range.start.toLong()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    Text(
                        text = (if (isArabic) "المدة: " else "Length: ") + formatMs((range.endInclusive - range.start).toLong()),
                        fontSize = 12.sp
                    )
                    Text(
                        text = (if (isArabic) "النهاية: " else "End: ") + formatMs(range.endInclusive.toLong()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
                OutlinedButton(
                    onClick = { onPreviewRange(range.start.toLong()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isArabic) "سماع من نقطة البداية" else "Preview from Start", color = accent)
                }
                if (isCutting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isCutting,
                onClick = {
                    scope.launch {
                        isCutting = true
                        val savedPath = VyroAudioCutter.cutAudioSegment(
                            context = context,
                            song = song,
                            startMs = range.start.toLong(),
                            endMs = range.endInclusive.toLong()
                        )
                        isCutting = false
                        if (savedPath != null) {
                            Toast.makeText(
                                context,
                                if (isArabic) "تم الحفظ بنجاح في $savedPath" else "Saved to $savedPath",
                                Toast.LENGTH_LONG
                            ).show()
                            onSaved()
                            onDismiss()
                        } else {
                            Toast.makeText(
                                context,
                                if (isArabic) "تعذر قص هذا النوع من الملفات" else "Could not trim this audio format",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(if (isArabic) "قص وحفظ الآن" else "Trim & Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isCutting, onClick = onDismiss) {
                Text(if (isArabic) "إغلاق" else "Close")
            }
        }
    )
}
