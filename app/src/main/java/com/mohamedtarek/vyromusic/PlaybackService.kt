package com.mohamedtarek.vyromusic

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val saveHandler = Handler(Looper.getMainLooper())

    private val positionSaver = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                persistPlaybackState()
            }
            saveHandler.postDelayed(this, 2000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistPlaybackState()
                VyroWidgetProvider.updateAllWidgets(this@PlaybackService)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
                prefs.edit().putInt("stat_tracks_played", prefs.getInt("stat_tracks_played", 0) + 1).apply()
                persistPlaybackState()
                VyroWidgetProvider.updateAllWidgets(this@PlaybackService)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                persistPlaybackState()
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        restoreLastSessionIfEmpty()
        saveHandler.postDelayed(positionSaver, 2000L)
    }

    private fun persistPlaybackState() {
        if (!::player.isInitialized) return
        val currentItem = player.currentMediaItem ?: return
        val songId = currentItem.mediaId.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val meta = currentItem.mediaMetadata
        val prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
        val albumId = currentItem.requestMetadata.extras?.getLong("album_id", -1L) ?: prefs.getLong("last_album_id", -1L)
        prefs.edit()
            .putLong("last_song_id", songId)
            .putLong("last_position_ms", pos)
            .putString("last_title", meta.title?.toString() ?: "VYRO MUSIC")
            .putString("last_artist", meta.artist?.toString() ?: "Mohamed Tarek")
            .putLong("last_album_id", albumId)
            .putBoolean("last_is_playing", player.isPlaying)
            .apply()
    }

    private fun restoreLastSessionIfEmpty() {
        if (player.mediaItemCount > 0) return
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) return

        val prefs = getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
        val savedSongId = prefs.getLong("last_song_id", -1L)
        val savedPos = prefs.getLong("last_position_ms", 0L)

        val items = mutableListOf<MediaItem>()
        var targetIndex = 0
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        )
        runCatching {
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
                var idx = 0
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val albumId = c.getLong(albumIdCol)
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    if (id == savedSongId) targetIndex = idx
                    val extras = android.os.Bundle().apply { putLong("album_id", albumId) }
                    items += MediaItem.Builder()
                        .setMediaId(id.toString())
                        .setUri(contentUri)
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .setAlbumTitle(album)
                                .setArtworkUri(artUri)
                                .build()
                        )
                        .build()
                    idx++
                }
            }
            if (items.isNotEmpty()) {
                player.setMediaItems(items, targetIndex, savedPos.coerceAtLeast(0L))
                player.prepare()
                player.playWhenReady = false
                VyroWidgetProvider.updateAllWidgets(this)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            VyroWidgetProvider.ACTION_TOGGLE -> {
                restoreLastSessionIfEmpty()
                if (player.isPlaying) player.pause() else player.play()
            }
            VyroWidgetProvider.ACTION_NEXT -> {
                restoreLastSessionIfEmpty()
                if (player.hasNextMediaItem()) player.seekToNext()
                player.play()
            }
            VyroWidgetProvider.ACTION_PREV -> {
                restoreLastSessionIfEmpty()
                if (player.hasPreviousMediaItem()) player.seekToPrevious() else player.seekTo(0L)
                player.play()
            }
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPlaybackState()
        if (!player.isPlaying) {
            VyroWidgetProvider.updateAllWidgets(this)
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistPlaybackState()
        saveHandler.removeCallbacks(positionSaver)
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
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE, ACTION_NEXT, ACTION_PREV -> {
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    action = intent.action
                }
                runCatching {
                    ContextCompat.startForegroundService(context, serviceIntent)
                }.onFailure {
                    runCatching { context.startService(serviceIntent) }
                }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.mohamedtarek.vyromusic.WIDGET_TOGGLE"
        const val ACTION_NEXT = "com.mohamedtarek.vyromusic.WIDGET_NEXT"
        const val ACTION_PREV = "com.mohamedtarek.vyromusic.WIDGET_PREV"

        fun updateAllWidgets(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, VyroWidgetProvider::class.java))
                if (ids.isEmpty()) return

                val prefs = context.getSharedPreferences("vyro_prefs", Context.MODE_PRIVATE)
                val title = prefs.getString("last_title", "VYRO MUSIC PRO") ?: "VYRO MUSIC PRO"
                val artist = prefs.getString("last_artist", "Crafted by Mohamed Tarek") ?: "Crafted by Mohamed Tarek"
                val albumId = prefs.getLong("last_album_id", -1L)
                val isPlaying = prefs.getBoolean("last_is_playing", false)

                val artworkBitmap = renderWidgetCardBitmap(context, albumId)

                val openAppIntent = PendingIntent.getActivity(
                    context,
                    100,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val togglePending = PendingIntent.getBroadcast(
                    context,
                    101,
                    Intent(context, VyroWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val nextPending = PendingIntent.getBroadcast(
                    context,
                    102,
                    Intent(context, VyroWidgetProvider::class.java).setAction(ACTION_NEXT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val prevPending = PendingIntent.getBroadcast(
                    context,
                    103,
                    Intent(context, VyroWidgetProvider::class.java).setAction(ACTION_PREV),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                for (widgetId in ids) {
                    val views = RemoteViews(context.packageName, R.layout.vyro_widget)
                    views.setTextViewText(R.id.widget_title, title)
                    views.setTextViewText(R.id.widget_artist, artist)
                    views.setImageViewBitmap(R.id.widget_album_art, artworkBitmap)
                    views.setImageViewResource(
                        R.id.widget_play_pause,
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
                    views.setOnClickPendingIntent(R.id.widget_play_pause, togglePending)
                    views.setOnClickPendingIntent(R.id.widget_next, nextPending)
                    views.setOnClickPendingIntent(R.id.widget_prev, prevPending)
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        private fun renderWidgetCardBitmap(context: Context, albumId: Long): Bitmap {
            val size = 380
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            val clipPath = Path().apply { addRoundRect(rect, 44f, 44f, Path.Direction.CW) }
            canvas.clipPath(clipPath)

            var rawArt: Bitmap? = null
            if (albumId > 0) {
                runCatching {
                    val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        rawArt = BitmapFactory.decodeStream(stream)
                    }
                }
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (rawArt != null) {
                val scaled = Bitmap.createScaledBitmap(rawArt!!, size, size, true)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
            } else {
                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(0xFF3B1D7A.toInt(), 0xFF191233.toInt(), 0xFF090A12.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(rect, paint)
                paint.shader = null
                paint.color = 0x559B6CFF
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                canvas.drawCircle(size / 2f, size / 2.4f, 75f, paint)
                paint.style = Paint.Style.FILL
            }

            // تظليل سينمائي أسفل الويدجت لظهور اسم الأغنية والأزرار بوضوح فائق
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, size * 0.25f, 0f, size.toFloat(),
                    intArrayOf(0x10000000, 0xAA080912.toInt(), 0xEE06070E.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, overlayPaint)
            return out
        }
    }
}
