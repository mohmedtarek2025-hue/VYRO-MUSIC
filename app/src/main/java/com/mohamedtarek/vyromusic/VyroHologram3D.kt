package com.mohamedtarek.vyromusic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ==================== 3D MATH PRIMITIVES ====================

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class Quad3D(
    val p0: Vec3,
    val p1: Vec3,
    val p2: Vec3,
    val p3: Vec3,
    val fillAlpha: Float = 0.14f,
    val strokeAlpha: Float = 0.85f,
    val tint: Color? = null,
    val strokeWidth: Float = 2.0f
)

private data class Edge3D(
    val a: Vec3,
    val b: Vec3,
    val alpha: Float = 0.9f,
    val width: Float = 2.2f,
    val tint: Color? = null
)

private enum class HoloDragMode {
    ROTATE, LID, LEFT_BUD, RIGHT_BUD
}

// ==================== EXACT MODEL HOLO-IMAGE FETCHER ====================

object DeviceHoloImageFetcher {
    private val cache = LruCache<String, Bitmap>(12)

    suspend fun fetchModelPhoto(deviceName: String, category: BtDeviceCategory): Bitmap? =
        withContext(Dispatchers.IO) {
            val cleanQuery = buildSearchQuery(deviceName, category)
            cache.get(cleanQuery)?.let { return@withContext it }

            val imgUrl = searchWikimediaImage(cleanQuery)
                ?: searchWikimediaImage(fallbackQueryFor(category))
                ?: return@withContext null

            val bmp = downloadBitmap(imgUrl)
            if (bmp != null) cache.put(cleanQuery, bmp)
            bmp
        }

    private fun buildSearchQuery(name: String, category: BtDeviceCategory): String {
        val cleaned = name
            .replace(Regex("[^a-zA-Z0-9\\s\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val lower = cleaned.lowercase(Locale.ROOT)
        return when {
            lower.contains("airpods pro") -> "Apple AirPods Pro"
            lower.contains("airpods max") -> "Apple AirPods Max"
            lower.contains("airpods") -> "Apple AirPods"
            lower.contains("galaxy buds") -> "Samsung Galaxy Buds"
            lower.contains("soundcore") -> "Anker Soundcore"
            cleaned.length >= 4 && category != BtDeviceCategory.CAR_AUDIO -> cleaned
            else -> fallbackQueryFor(category)
        }
    }

    private fun fallbackQueryFor(category: BtDeviceCategory): String {
        return when (category) {
            BtDeviceCategory.AIRPODS -> "Apple AirPods Pro"
            BtDeviceCategory.SOUNDCORE -> "wireless earbuds charging case"
            BtDeviceCategory.GALAXY_BUDS -> "Samsung Galaxy Buds"
            BtDeviceCategory.SMART_EARBUDS -> "wireless earbuds"
            BtDeviceCategory.HEADPHONES -> "wireless over ear headphones"
            BtDeviceCategory.SPEAKER -> "JBL Bluetooth speaker"
            BtDeviceCategory.CAR_AUDIO -> "modern car dashboard audio"
            BtDeviceCategory.UNKNOWN -> "bluetooth headphones"
        }
    }

    private fun searchWikimediaImage(query: String): String? {
        return runCatching {
            val encoded = URLEncoder.encode("filetype:bitmap $query", "UTF-8")
            val apiUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=$encoded&gsrlimit=6&prop=imageinfo&iiprop=url|size&iiurlwidth=640&format=json"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                setRequestProperty("User-Agent", "VyroMusicApp/5.1.0 (Android)")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val pages = JSONObject(json).optJSONObject("query")?.optJSONObject("pages") ?: return null
            val keys = pages.keys()
            while (keys.hasNext()) {
                val p = pages.optJSONObject(keys.next()) ?: continue
                val info = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
                val low = thumb.lowercase(Locale.ROOT)
                if (thumb.isNotBlank() && (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp"))) {
                    return thumb
                }
            }
            null
        }.getOrNull()
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
