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
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
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

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        // منع انخفاض الصوت العشوائي عند التنقل بين الشاشات
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
                if (isPlaying && (equalizer == null || bassBoost == null || loudnessEnhancer == null)) {
                    bindHardwareDsp(player.audioSessionId)
                }
                prefs.edit().putBoolean("is_playing_now", isPlaying).apply()
                VyroWidgetProvider.updateAllWidgets(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                val meta = mediaItem.mediaMetadata
                val albumId = mediaItem.requestMetadata.extras?.getLong("album_id", -1L) ?: -1L
                val totalPlayed = prefs.getInt("stat_tracks_played", 0) + 1
                prefs.edit()
                    .putLong("last_song_id", id)
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

    private fun bindHardwareDsp(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        if (boundSessionId != sessionId) {
            releaseDsp()
            boundSessionId = sessionId
        }
        if (equalizer == null) {
            runCatching { equalizer = Equalizer(100, sessionId).apply { enabled = true } }
        }
        if (bassBoost == null) {
            runCatching { bassBoost = BassBoost(100, sessionId).apply { enabled = true } }
        }
        if (virtualizer == null) {
            runCatching { virtualizer = Virtualizer(100, sessionId).apply { enabled = true } }
        }
        if (loudnessEnhancer == null) {
            runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true } }
        }
        applyDspLevels()
    }

    private fun applyDspLevels() {
        val gainsJson = prefs.getString("band_gains", null)
        val gains = runCatching {
            if (gainsJson.isNullOrBlank()) null
            else {
                val arr = JSONArray(gainsJson)
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            }
        }.getOrNull() ?: listOf(0.80f, 0.45f, -0.10f, 0.50f, 0.85f)

        val bassStrength = prefs.getInt("bass_strength", 75).coerceIn(0, 100)
        val virtStrength = prefs.getInt("virtualizer_strength", 55).coerceIn(0, 100)
        val loudnessGain = prefs.getInt("loudness_gain", 40).coerceIn(0, 100)

        runCatching {
            val eq = equalizer ?: return@runCatching
            eq.enabled = true
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLvl = range[0].toInt()
            val maxLvl = range[1].toInt()
            val centerLvl = (minLvl + maxLvl) / 2
            val halfSpan = (maxLvl - minLvl) / 2f

            // ضخ Sub-Bass إضافي على الترددات المنخفضة (60Hz & 230Hz) ليعطي بيز حقيقي وقوي
            val subBass0 = (bassStrength / 100f) * 0.50f
            val subBass1 = (bassStrength / 100f) * 0.30f

            for (b in 0 until minOf(numBands, gains.size)) {
                val extra = when (b) {
                    0 -> subBass0
                    1 -> subBass1
                    else -> 0f
                }
                val combined = (gains[b] + extra).coerceIn(-1f, 1f)
                val target = (centerLvl + (combined * halfSpan).toInt()).coerceIn(minLvl, maxLvl).toShort()
                eq.setBandLevel(b.toShort(), target)
            }
        }

        runCatching {
            bassBoost?.let { bb ->
                bb.enabled = bassStrength > 0
                if (bb.strengthSupported) {
                    bb.setStrength((bassStrength * 10).coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            virtualizer?.let { vt ->
                vt.enabled = virtStrength > 0
                if (vt.strengthSupported) {
                    vt.setStrength((virtStrength * 10).coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            loudnessEnhancer?.let { le ->
                le.enabled = loudnessGain > 0
                le.setTargetGain(loudnessGain * 10)
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
            "band_gains", "bass_strength", "virtualizer_strength", "loudness_gain" -> applyDspLevels()
            "playback_speed", "playback_pitch" -> applyPlaybackParams()
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
                val title = prefs.getString("last_title", "VYRO MUSIC ULTRA") ?: "VYRO MUSIC ULTRA"
                val artist = prefs.getString("last_artist", "Tap to launch studio") ?: "Tap to launch studio"
                val albumId = prefs.getLong("last_album_id", -1L)

                val views = RemoteViews(context.packageName, android.R.layout.activity_list_item)
                views.setTextViewText(android.R.id.text1, "$title • $artist")

                val bmp = renderWidgetBadge(context, albumId)
                views.setImageViewBitmap(android.R.id.icon, bmp)

                val openIntent = Intent(context, MainActivity::class.java)
                val pi = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(android.R.id.text1, pi)
                views.setOnClickPendingIntent(android.R.id.icon, pi)

                mgr.updateAppWidget(widgetId, views)
            }
        }

        private fun renderWidgetBadge(context: Context, albumId: Long): Bitmap {
            val size = 128
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFFA87BFF.toInt(), 0xFF090A14.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f, paint)

            if (albumId > 0) {
                runCatching {
                    val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val art = BitmapFactory.decodeStream(stream)
                        if (art != null) {
                            val scaled = Bitmap.createScaledBitmap(art, size, size, true)
                            canvas.drawBitmap(scaled, 0f, 0f, null)
                        }
                    }
                }
            }
            return out
        }
    }
}
