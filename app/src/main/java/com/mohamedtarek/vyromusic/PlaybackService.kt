package com.mohamedtarek.vyromusic

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPreferences

    private var boundSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var playSessionStartMs: Long = 0L

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.mohamedtarek.vyromusic.WIDGET_TOGGLE"
        const val ACTION_WIDGET_NEXT = "com.mohamedtarek.vyromusic.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.mohamedtarek.vyromusic.WIDGET_PREV"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                bindHardwareDsp(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val now = System.currentTimeMillis()
                if (isPlaying) {
                    playSessionStartMs = now
                    if (equalizer == null || bassBoost == null || loudnessEnhancer == null) {
                        bindHardwareDsp(player.audioSessionId)
                    }
                } else {
                    flushListeningTime()
                }
                prefs.edit().putBoolean("is_playing_now", isPlaying).apply()
                VyroWidgetProvider.updateAllWidgets(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                flushListeningTime()
                if (player.isPlaying) playSessionStartMs = System.currentTimeMillis()

                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                val meta = mediaItem.mediaMetadata
                val albumId = mediaItem.requestMetadata.extras?.getLong("album_id", -1L) ?: -1L
                val songUriStr = mediaItem.localConfiguration?.uri?.toString()
                    ?: mediaItem.requestMetadata.extras?.getString("song_uri")
                    ?: ""
                val totalPlayed = prefs.getInt("stat_tracks_played", 0) + 1
                prefs.edit()
                    .putLong("last_song_id", id)
                    .putString("last_song_uri", songUriStr)
                    .putString("last_title", meta.title?.toString() ?: "VYRO MUSIC")
                    .putString("last_artist", meta.artist?.toString() ?: "Mohamed Tarek")
                    .putLong("last_album_id", albumId)
                    .putInt("stat_tracks_played", totalPlayed)
                    .apply()
                VyroWidgetProvider.updateAllWidgets(this@PlaybackService)
            }
        })

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            bindHardwareDsp(player.audioSessionId)
        }
        applyPlaybackParams()

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun flushListeningTime() {
        if (playSessionStartMs > 0L) {
            val deltaSec = ((System.currentTimeMillis() - playSessionStartMs) / 1000L).coerceIn(0L, 14400L)
            if (deltaSec > 0L) {
                val prev = prefs.getLong("stat_listen_seconds", 0L)
                prefs.edit().putLong("stat_listen_seconds", prev + deltaSec).apply()
            }
            playSessionStartMs = 0L
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE -> {
                if (player.isPlaying) player.pause() else player.play()
                VyroWidgetProvider.updateAllWidgets(this)
            }
            ACTION_WIDGET_NEXT -> {
                player.seekToNext()
                VyroWidgetProvider.updateAllWidgets(this)
            }
            ACTION_WIDGET_PREV -> {
                player.seekToPrevious()
                VyroWidgetProvider.updateAllWidgets(this)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun bindHardwareDsp(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        if (boundSessionId != sessionId) {
            releaseDsp()
            boundSessionId = sessionId
        }
        if (equalizer == null) {
            runCatching { equalizer = Equalizer(0, sessionId).apply { enabled = true } }
        }
        if (bassBoost == null) {
            runCatching { bassBoost = BassBoost(0, sessionId).apply { enabled = true } }
        }
        if (virtualizer == null) {
            runCatching { virtualizer = Virtualizer(0, sessionId).apply { enabled = true } }
        }
        if (loudnessEnhancer == null) {
            runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true } }
        }
        applyDspLevels()
    }

    private fun applyDspLevels() {
        val antiClipping = prefs.getBoolean("anti_clipping", true)
        val spatial8D = prefs.getBoolean("spatial_8d_enabled", false)
        val gainsJson = prefs.getString("band_gains", null)
        val gains = runCatching {
            if (gainsJson.isNullOrBlank()) null
            else {
                val arr = JSONArray(gainsJson)
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            }
        }.getOrNull() ?: listOf(0.30f, 0.18f, 0.0f, 0.16f, 0.28f)

        val bassStrength = prefs.getInt("bass_strength", 35).coerceIn(0, 100)
        val virtStrength = prefs.getInt("virtualizer_strength", 30).coerceIn(0, 100)
        val loudnessGain = prefs.getInt("loudness_gain", 0).coerceIn(0, 100)

        val safeScale = if (antiClipping) 0.58f else 0.85f
        val maxPeak = gains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val headroomDrop = if (antiClipping && (maxPeak > 0.50f || bassStrength > 55)) {
            ((maxPeak - 0.45f).coerceAtLeast(0f) * 0.25f) + ((bassStrength - 55).coerceAtLeast(0) / 400f)
        } else 0f

        runCatching {
            val eq = equalizer ?: return@runCatching
            eq.enabled = true
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLvl = range[0].toInt()
            val maxLvl = range[1].toInt()
            val centerLvl = (minLvl + maxLvl) / 2
            val halfSpan = (maxLvl - minLvl) / 2f

            val subBass0 = (bassStrength / 100f) * (if (antiClipping) 0.20f else 0.36f)
            val subBass1 = (bassStrength / 100f) * (if (antiClipping) 0.10f else 0.20f)

            for (b in 0 until minOf(numBands, gains.size)) {
                val extra = when (b) {
                    0 -> subBass0
                    1 -> subBass1
                    else -> 0f
                }
                val netGain = (gains[b] + extra - headroomDrop).coerceIn(-0.95f, 0.95f) * safeScale
                val target = (centerLvl + (netGain * halfSpan).toInt()).coerceIn(minLvl, maxLvl).toShort()
                eq.setBandLevel(b.toShort(), target)
            }
        }

        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = bassStrength > 0
                if (bb.strengthSupported) {
                    val maxBb = if (antiClipping) 600 else 920
                    bb.setStrength(((bassStrength * maxBb) / 100).coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            virtualizer?.let { vt ->
                val effectiveVirt = if (spatial8D) maxOf(virtStrength, 85) else virtStrength
                vt.enabled = effectiveVirt > 0
                if (vt.strengthSupported) {
                    vt.setStrength((effectiveVirt * 9).coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            loudnessEnhancer?.let { le ->
                val safeLoudness = if (antiClipping) (loudnessGain * 3) else (loudnessGain * 7)
                le.enabled = safeLoudness > 0
                le.setTargetGain(safeLoudness)
            }
        }
    }

    private fun applyPlaybackParams() {
        val speed = prefs.getFloat("playback_speed", 1f).coerceIn(0.5f, 2.0f)
        val pitch = prefs.getFloat("playback_pitch", 1f).coerceIn(0.5f, 2.0f)
        runCatching {
            player.playbackParameters = PlaybackParameters(speed, pitch)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "band_gains", "bass_strength", "virtualizer_strength", "loudness_gain", "anti_clipping", "spatial_8d_enabled" -> applyDspLevels()
            "playback_speed", "playback_pitch" -> applyPlaybackParams()
            "widget_style", "accent_color_id" -> VyroWidgetProvider.updateAllWidgets(this)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun releaseDsp() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    override fun onDestroy() {
        flushListeningTime()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        releaseDsp()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

class VyroWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateSingleWidget(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PlaybackService.ACTION_WIDGET_TOGGLE,
            PlaybackService.ACTION_WIDGET_NEXT,
            PlaybackService.ACTION_WIDGET_PREV -> {
                val svcIntent = Intent(context, PlaybackService::class.java).apply {
                    action = intent.action
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(svcIntent)
                    } else {
                        context.startService(svcIntent)
                    }
                }
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            runCatching {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, VyroWidgetProvider::class.java))
                ids.forEach { id -> updateSingleWidget(context, mgr, id) }
            }
        }

        private fun updateSingleWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            runCatching {
                val prefs = context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
                val title = prefs.getString("last_title", "VYRO MUSIC") ?: "VYRO MUSIC"
                val artist = prefs.getString("last_artist", "Mohamed Tarek") ?: "Mohamed Tarek"
                val songUriStr = prefs.getString("last_song_uri", "") ?: ""
                val albumId = prefs.getLong("last_album_id", -1L)
                val isPlaying = prefs.getBoolean("is_playing_now", false)
                val savedStyle = prefs.getString("widget_style", "Full Poster") ?: "Full Poster"

                val options = mgr.getAppWidgetOptions(widgetId)
                val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200)
                val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)

                val useSquareLayout = when (savedStyle) {
                    "Full Poster", "Square Glass" -> true
                    "Wide Bar" -> false
                    else -> (minH >= 110 && minW <= 240) || (abs(minW - minH) < 60)
                }

                val isFullBleedPoster = useSquareLayout && (savedStyle == "Full Poster" || savedStyle == "Auto Adaptive")

                val views = RemoteViews(context.packageName, R.layout.vyro_widget)
                views.setViewVisibility(R.id.widget_square_box, if (useSquareLayout) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_wide_box, if (useSquareLayout) View.GONE else View.VISIBLE)
                views.setViewVisibility(
                    R.id.widget_art_square,
                    if (isFullBleedPoster) View.GONE else View.VISIBLE
                )

                val rawCover = loadRobustAlbumArt(context, songUriStr, albumId)
                val domColor = extractSoftDominantColor(rawCover)

                val bgBmp = renderSleekWidgetBackground(
                    rawCover = rawCover,
                    domColor = domColor,
                    isSquare = useSquareLayout,
                    fullBleedPoster = isFullBleedPoster
                )
                val artBmp = renderRoundedCover(rawCover, domColor)

                views.setImageViewBitmap(R.id.widget_bg, bgBmp)

                val playIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

                views.setTextViewText(R.id.widget_title_wide, title)
                views.setTextViewText(R.id.widget_artist_wide, artist)
                views.setImageViewBitmap(R.id.widget_art_wide, artBmp)
                views.setImageViewResource(R.id.widget_play_wide, playIcon)

                views.setTextViewText(R.id.widget_title_square, title)
                views.setTextViewText(R.id.widget_artist_square, artist)
                views.setImageViewBitmap(R.id.widget_art_square, artBmp)
                views.setImageViewResource(R.id.widget_play_square, playIcon)

                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val openPi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
                views.setOnClickPendingIntent(R.id.widget_root, openPi)
                views.setOnClickPendingIntent(R.id.widget_art_wide, openPi)
                views.setOnClickPendingIntent(R.id.widget_art_square, openPi)

                val togglePi = PendingIntent.getBroadcast(
                    context, 1, Intent(context, VyroWidgetProvider::class.java).setAction(PlaybackService.ACTION_WIDGET_TOGGLE), flags
                )
                val nextPi = PendingIntent.getBroadcast(
                    context, 2, Intent(context, VyroWidgetProvider::class.java).setAction(PlaybackService.ACTION_WIDGET_NEXT), flags
                )
                val prevPi = PendingIntent.getBroadcast(
                    context, 3, Intent(context, VyroWidgetProvider::class.java).setAction(PlaybackService.ACTION_WIDGET_PREV), flags
                )

                views.setOnClickPendingIntent(R.id.widget_play_wide, togglePi)
                views.setOnClickPendingIntent(R.id.widget_next_wide, nextPi)
                views.setOnClickPendingIntent(R.id.widget_prev_wide, prevPi)

                views.setOnClickPendingIntent(R.id.widget_play_square, togglePi)
                views.setOnClickPendingIntent(R.id.widget_next_square, nextPi)
                views.setOnClickPendingIntent(R.id.widget_prev_square, prevPi)

                mgr.updateAppWidget(widgetId, views)
            }
        }

        // محرك تحميل الصور الثلاثي لضمان ظهور صورة الأغنية على أندرويد 14/15/16 بنسبة 100%
        fun loadRobustAlbumArt(context: Context, songUriStr: String, albumId: Long): Bitmap? {
            if (songUriStr.isNotBlank()) {
                val uri = Uri.parse(songUriStr)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching {
                        val thumb = context.contentResolver.loadThumbnail(uri, Size(360, 360), null)
                        if (thumb != null) return thumb
                    }
                }
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(context, uri)
                    val bytes = mmr.embeddedPicture
                    mmr.release()
                    if (bytes != null) {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        if (decoded != null) return decoded
                    }
                }
            }
            if (albumId > 0) {
                runCatching {
                    val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    context.contentResolver.openInputStream(artUri)?.use { stream ->
                        val decoded = BitmapFactory.decodeStream(stream)
                        if (decoded != null) return decoded
                    }
                }
            }
            return null
        }

        private fun extractSoftDominantColor(bmp: Bitmap?): Int {
            if (bmp == null) return 0xFF1D2C49.toInt()
            var r = 0L
            var g = 0L
            var b = 0L
            var c = 0L
            val sx = (bmp.width / 8).coerceAtLeast(1)
            val sy = (bmp.height / 8).coerceAtLeast(1)
            for (x in 0 until bmp.width step sx) {
                for (y in 0 until bmp.height step sy) {
                    val px = bmp.getPixel(x, y)
                    r += android.graphics.Color.red(px)
                    g += android.graphics.Color.green(px)
                    b += android.graphics.Color.blue(px)
                    c++
                }
            }
            if (c == 0L) return 0xFF1D2C49.toInt()
            return android.graphics.Color.rgb(
                ((r / c) * 0.72f).toInt().coerceIn(22, 165),
                ((g / c) * 0.72f).toInt().coerceIn(22, 165),
                ((b / c) * 0.78f).toInt().coerceIn(28, 175)
            )
        }

        private fun renderSleekWidgetBackground(
            rawCover: Bitmap?,
            domColor: Int,
            isSquare: Boolean,
            fullBleedPoster: Boolean
        ): Bitmap {
            val w = if (isSquare) 360 else 640
            val h = if (isSquare) 360 else 190
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val corner = 48f
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

            if (fullBleedPoster && rawCover != null) {
                // بوستر سينمائي: صورة الأغنية تملأ المربع بالكامل مع تدرج داكن أنيق في الأسفل للأزرار
                val scaled = centerCropBitmap(rawCover, w, h)
                val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                }
                canvas.drawRoundRect(rect, corner, corner, coverPaint)

                val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, h * 0.25f, 0f, h.toFloat(),
                        intArrayOf(0x18000000, 0xB0080A12.toInt(), 0xEE06070D.toInt()),
                        floatArrayOf(0f, 0.58f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(rect, corner, corner, scrimPaint)
            } else {
                // بطاقة زجاجية فاخرة بألوان هادئة مستوحاة من غلاف الأغنية
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, w.toFloat(), h.toFloat(),
                        intArrayOf(domColor, 0xFF10131C.toInt(), 0xFF0A0C12.toInt()),
                        floatArrayOf(0f, 0.62f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(rect, corner, corner, bgPaint)
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = 0x33FFFFFF
            }
            canvas.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), corner, corner, borderPaint)
            return out
        }

        private fun renderRoundedCover(rawArt: Bitmap?, domColor: Int): Bitmap {
            val size = 180
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            val radius = 32f

            if (rawArt != null) {
                val cropped = centerCropBitmap(rawArt, size, size)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = BitmapShader(cropped, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                }
                canvas.drawRoundRect(rect, radius, radius, paint)
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, size.toFloat(), size.toFloat(),
                        intArrayOf(domColor, 0xFF171D2B.toInt()),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRoundRect(rect, radius, radius, paint)
                val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFFFFF.toInt()
                    textSize = 72f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("♪", size / 2f, size * 0.64f, notePaint)
            }
            return out
        }

        private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
            val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
            val scaledW = (src.width * scale).toInt().coerceAtLeast(targetW)
            val scaledH = (src.height * scale).toInt().coerceAtLeast(targetH)
            val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
            val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
            val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
            return Bitmap.createBitmap(scaled, x, y, targetW, targetH)
        }
    }
}
