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
    private val cache = object : LruCache<String, Bitmap>(12) {}

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
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "VyroMusicApp/5.1.0 (Android)")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}

// ==================== INTERACTIVE 3D HOLOGRAM STAGE COMPOSABLE ====================

@Composable
fun DeviceHologram3DStage(
    device: SmartBtDevice,
    isArabic: Boolean,
    accent: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val isCaseDevice = device.category == BtDeviceCategory.AIRPODS ||
        device.category == BtDeviceCategory.SOUNDCORE ||
        device.category == BtDeviceCategory.GALAXY_BUDS ||
        device.category == BtDeviceCategory.SMART_EARBUDS

    var yaw by remember(device.address) { mutableFloatStateOf(-0.38f) }
    var pitch by remember(device.address) { mutableFloatStateOf(0.24f) }
    var autoSpin by remember(device.address) { mutableStateOf(false) }

    var lidTarget by remember(device.address) { mutableFloatStateOf(0f) }
    var leftPullTarget by remember(device.address) { mutableFloatStateOf(0f) }
    var rightPullTarget by remember(device.address) { mutableFloatStateOf(0f) }

    var showRealHoloPhoto by remember(device.address) { mutableStateOf(false) }
    var holoPhoto by remember(device.address) { mutableStateOf<ImageBitmap?>(null) }
    var loadingPhoto by remember(device.address) { mutableStateOf(false) }

    val lidOpen by animateFloatAsState(
        targetValue = lidTarget.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "lidOpen"
    )
    val leftPull by animateFloatAsState(
        targetValue = if (lidTarget > 0.35f) leftPullTarget.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "leftPull"
    )
    val rightPull by animateFloatAsState(
        targetValue = if (lidTarget > 0.35f) rightPullTarget.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "rightPull"
    )

    val lidScreenPos = remember { mutableStateOf(Offset.Zero) }
    val leftBudScreenPos = remember { mutableStateOf(Offset.Zero) }
    val rightBudScreenPos = remember { mutableStateOf(Offset.Zero) }
    var activeDragMode by remember { mutableStateOf(HoloDragMode.ROTATE) }

    LaunchedEffect(showRealHoloPhoto, device.name) {
        if (showRealHoloPhoto && holoPhoto == null) {
            loadingPhoto = true
            holoPhoto = DeviceHoloImageFetcher.fetchModelPhoto(device.name, device.category)?.asImageBitmap()
            loadingPhoto = false
        }
    }

    val inf = rememberInfiniteTransition(label = "holoAnim")
    val scanLine by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan"
    )
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val autoYawOffset by inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "autoYaw"
    )

    val effectiveYaw = if (autoSpin) yaw + autoYawOffset else yaw
    val holoCyan = Color(0xFF00F0FF)
    val holoSecondary = accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF04101D), Color(0xFF061626), Color(0xFF030811))
                )
            )
            .border(1.3.dp, holoCyan.copy(0.45f), RoundedCornerShape(26.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(holoCyan.copy(0.15f))
                    .border(1.dp, holoCyan.copy(0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "HOLO 3D • ${device.capabilities.brandTag}",
                    color = holoCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(0.08f))
                    .clickable { showRealHoloPhoto = !showRealHoloPhoto }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (showRealHoloPhoto) Icons.Rounded.Autorenew else Icons.Rounded.PhotoCamera,
                    null,
                    tint = Color(0xFFFFC857),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (showRealHoloPhoto) {
                        if (isArabic) "مجسم 3D" else "3D Model"
                    } else {
                        if (isArabic) "صورة الموديل" else "Model Photo"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(275.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF02070F))
                .pointerInput(device.address, isCaseDevice, showRealHoloPhoto) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            autoSpin = false
                            if (!showRealHoloPhoto && isCaseDevice) {
                                val dLeft = hypot(startOffset.x - leftBudScreenPos.value.x, startOffset.y - leftBudScreenPos.value.y)
                                val dRight = hypot(startOffset.x - rightBudScreenPos.value.x, startOffset.y - rightBudScreenPos.value.y)
                                val dLid = hypot(startOffset.x - lidScreenPos.value.x, startOffset.y - lidScreenPos.value.y)

                                activeDragMode = when {
                                    lidTarget > 0.4f && dLeft < 68f && dLeft <= dRight -> HoloDragMode.LEFT_BUD
                                    lidTarget > 0.4f && dRight < 68f -> HoloDragMode.RIGHT_BUD
                                    dLid < 72f -> HoloDragMode.LID
                                    else -> HoloDragMode.ROTATE
                                }
                            } else {
                                activeDragMode = HoloDragMode.ROTATE
                            }
                        },
                        onDragEnd = {
                            if (activeDragMode == HoloDragMode.LID) {
                                lidTarget = if (lidTarget >= 0.4f) 1f else 0f
                                if (lidTarget == 0f) {
                                    leftPullTarget = 0f
                                    rightPullTarget = 0f
                                }
                            } else if (activeDragMode == HoloDragMode.LEFT_BUD) {
                                leftPullTarget = if (leftPullTarget >= 0.45f) 1f else 0f
                            } else if (activeDragMode == HoloDragMode.RIGHT_BUD) {
                                rightPullTarget = if (rightPullTarget >= 0.45f) 1f else 0f
                            }
                            activeDragMode = HoloDragMode.ROTATE
                        },
                        onDragCancel = {
                            activeDragMode = HoloDragMode.ROTATE
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            when (activeDragMode) {
                                HoloDragMode.LID -> {
                                    if (abs(dragAmount.x) > abs(dragAmount.y) * 1.8f) {
                                        yaw += dragAmount.x * 0.011f
                                    } else {
                                        lidTarget = (lidTarget - dragAmount.y / 145f).coerceIn(0f, 1f)
                                    }
                                }
                                HoloDragMode.LEFT_BUD -> {
                                    leftPullTarget = (leftPullTarget - dragAmount.y / 130f).coerceIn(0f, 1f)
                                }
                                HoloDragMode.RIGHT_BUD -> {
                                    rightPullTarget = (rightPullTarget - dragAmount.y / 130f).coerceIn(0f, 1f)
                                }
                                HoloDragMode.ROTATE -> {
                                    yaw += dragAmount.x * 0.012f
                                    pitch = (pitch + dragAmount.y * 0.009f).coerceIn(-1.05f, 1.05f)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (showRealHoloPhoto) {
                RealModelHologramProjection(
                    photo = holoPhoto,
                    loading = loadingPhoto,
                    deviceName = device.name,
                    yaw = effectiveYaw,
                    pitch = pitch,
                    scanLine = scanLine,
                    holoColor = holoCyan,
                    isArabic = isArabic
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height * 0.49f
                    val unit = minOf(size.width, size.height) * 0.42f
                    val floatBob = sin(pulse) * 0.035f

                    drawHologramProjectorBase(cx, size.height, unit, holoCyan, holoSecondary, pulse)

                    val quads = mutableListOf<Quad3D>()
                    val edges = mutableListOf<Edge3D>()

                    when (device.category) {
                        BtDeviceCategory.AIRPODS,
                        BtDeviceCategory.SOUNDCORE,
                        BtDeviceCategory.GALAXY_BUDS,
                        BtDeviceCategory.SMART_EARBUDS -> {
                            build3DEarbudsCaseScene(
                                quads = quads,
                                edges = edges,
                                lidOpen = lidOpen,
                                leftPull = leftPull,
                                rightPull = rightPull,
                                floatBob = floatBob,
                                isAirPodsStyle = device.category == BtDeviceCategory.AIRPODS,
                                holoCyan = holoCyan,
                                accent = holoSecondary
                            )

                            val lidAnchor = rotateAroundX(Vec3(0f, 0.42f, -0.18f), 0.28f, 0.24f, lidOpen * 1.75f)
                                .let { Vec3(it.x, it.y + floatBob, it.z) }
                            val leftAnchor = Vec3(-0.27f, 0.14f + leftPull * 0.72f + floatBob, 0f)
                            val rightAnchor = Vec3(0.27f, 0.14f + rightPull * 0.72f + floatBob, 0f)

                            lidScreenPos.value = project3D(lidAnchor, effectiveYaw, pitch, cx, cy, unit).first
                            leftBudScreenPos.value = project3D(leftAnchor, effectiveYaw, pitch, cx, cy, unit).first
                            rightBudScreenPos.value = project3D(rightAnchor, effectiveYaw, pitch, cx, cy, unit).first
                        }

                        BtDeviceCategory.HEADPHONES -> {
                            build3DHeadphonesScene(
                                quads = quads,
                                edges = edges,
                                floatBob = floatBob,
                                pulse = if (isPlaying) sin(pulse * 2f) * 0.04f else 0f,
                                holoCyan = holoCyan,
                                accent = holoSecondary
                            )
                        }

                        BtDeviceCategory.CAR_AUDIO -> {
                            build3DCarScene(
                                quads = quads,
                                edges = edges,
                                floatBob = floatBob,
                                wheelSpin = pulse,
                                isPlaying = isPlaying,
                                holoCyan = holoCyan,
                                accent = holoSecondary
                            )
                        }

                        else -> {
                            build3DSpeakerScene(
                                quads = quads,
                                edges = edges,
                                floatBob = floatBob,
                                bassPulse = if (isPlaying) (sin(pulse * 3f) + 1f) * 0.04f else 0f,
                                holoCyan = holoCyan,
                                accent = holoSecondary
                            )
                        }
                    }

                    val sortedQuads = quads.map { q ->
                        val r0 = project3D(q.p0, effectiveYaw, pitch, cx, cy, unit)
                        val r1 = project3D(q.p1, effectiveYaw, pitch, cx, cy, unit)
                        val r2 = project3D(q.p2, effectiveYaw, pitch, cx, cy, unit)
                        val r3 = project3D(q.p3, effectiveYaw, pitch, cx, cy, unit)
                        val avgZ = (r0.second + r1.second + r2.second + r3.second) / 4f
                        QuadProjected(q, r0.first, r1.first, r2.first, r3.first, avgZ)
                    }.sortedByDescending { it.avgZ }

                    sortedQuads.forEach { pq ->
                        val col = pq.quad.tint ?: holoCyan
                        val path = Path().apply {
                            moveTo(pq.s0.x, pq.s0.y)
                            lineTo(pq.s1.x, pq.s1.y)
                            lineTo(pq.s2.x, pq.s2.y)
                            lineTo(pq.s3.x, pq.s3.y)
                            close()
                        }
                        drawPath(path, col.copy(alpha = pq.quad.fillAlpha), style = Fill)
                        drawPath(path, col.copy(alpha = pq.quad.strokeAlpha * 0.28f), style = Stroke(width = pq.quad.strokeWidth + 3.2f))
                        drawPath(path, col.copy(alpha = pq.quad.strokeAlpha), style = Stroke(width = pq.quad.strokeWidth))
                    }

                    edges.forEach { e ->
                        val pA = project3D(e.a, effectiveYaw, pitch, cx, cy, unit).first
                        val pB = project3D(e.b, effectiveYaw, pitch, cx, cy, unit).first
                        val col = e.tint ?: holoCyan
                        drawLine(col.copy(alpha = e.alpha * 0.28f), pA, pB, strokeWidth = e.width + 3.5f)
                        drawLine(col.copy(alpha = e.alpha), pA, pB, strokeWidth = e.width)
                    }

                    val scanY = size.height * (0.12f + scanLine * 0.72f)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, holoCyan.copy(0.65f), Color.White.copy(0.8f), holoCyan.copy(0.65f), Color.Transparent)
                        ),
                        start = Offset(size.width * 0.1f, scanY),
                        end = Offset(size.width * 0.9f, scanY),
                        strokeWidth = 2.5f
                    )
                }

                val air = device.airPodsBattery
                if (isCaseDevice && (air != null || leftPull > 0.3f || rightPull > 0.3f)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HoloMiniBadge(
                            label = "L",
                            value = when {
                                air != null && air.leftPercent >= 0 -> "${air.leftPercent}%" + if (air.leftCharging) "⚡" else ""
                                leftPull > 0.3f -> if (isArabic) "خارج العلبة" else "Out"
                                else -> if (device.batteryPercent >= 0) "${device.batteryPercent}%" else "--"
                            },
                            active = leftPull > 0.3f || (air?.leftPercent ?: -1) >= 0,
                            color = holoCyan
                        )
                        HoloMiniBadge(
                            label = if (isArabic) "العلبة" else "CASE",
                            value = when {
                                air != null && air.casePercent >= 0 -> "${air.casePercent}%" + if (air.caseCharging) "⚡" else ""
                                lidOpen > 0.4f -> if (isArabic) "مفتوحة" else "Open"
                                else -> if (isArabic) "مغلقة" else "Closed"
                            },
                            active = lidOpen > 0.4f || (air?.casePercent ?: -1) >= 0,
                            color = Color(0xFFFFC857)
                        )
                        HoloMiniBadge(
                            label = "R",
                            value = when {
                                air != null && air.rightPercent >= 0 -> "${air.rightPercent}%" + if (air.rightCharging) "⚡" else ""
                                rightPull > 0.3f -> if (isArabic) "خارج العلبة" else "Out"
                                else -> if (device.batteryPercent >= 0) "${device.batteryPercent}%" else "--"
                            },
                            active = rightPull > 0.3f || (air?.rightPercent ?: -1) >= 0,
                            color = holoCyan
                        )
                    }
                }
            }

            Text(
                text = when {
                    showRealHoloPhoto -> if (isArabic) "اسحب لإمالة الهولوجرام • ${device.name}" else "Drag to tilt hologram"
                    isCaseDevice && lidOpen < 0.35f -> if (isArabic) "اسحب للتدوير 360° • اسحب الغطاء لأعلى للفتح" else "Drag to rotate 360° • Pull lid up to open"
                    isCaseDevice -> if (isArabic) "العلبة مفتوحة! اسحب السماعة L أو R للأعلى لإخراجها" else "Lid open! Pull L or R earbud up"
                    else -> if (isArabic) "اسحب بصباعك لتدوير المجسم الهولوجرام 360°" else "Drag to rotate 3D hologram 360°"
                },
                color = holoCyan.copy(0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isCaseDevice && !showRealHoloPhoto) {
                HoloControlChip(
                    label = if (lidTarget > 0.5f) (if (isArabic) "غلق الغطاء" else "Close Lid") else (if (isArabic) "فتح الغطاء" else "Open Lid"),
                    active = lidTarget > 0.5f,
                    color = Color(0xFFFFC857),
                    modifier = Modifier.weight(1.1f),
                    onClick = {
                        if (lidTarget > 0.5f) {
                            leftPullTarget = 0f
                            rightPullTarget = 0f
                            lidTarget = 0f
                        } else {
                            lidTarget = 1f
                        }
                    }
                )
                HoloControlChip(
                    label = if (leftPullTarget > 0.5f) (if (isArabic) "إرجاع L" else "Dock L") else (if (isArabic) "شد L" else "Pull L"),
                    active = leftPullTarget > 0.5f,
                    color = holoCyan,
                    modifier = Modifier.weight(0.9f),
                    onClick = {
                        if (lidTarget < 0.5f) lidTarget = 1f
                        leftPullTarget = if (leftPullTarget > 0.5f) 0f else 1f
                    }
                )
                HoloControlChip(
                    label = if (rightPullTarget > 0.5f) (if (isArabic) "إرجاع R" else "Dock R") else (if (isArabic) "شد R" else "Pull R"),
                    active = rightPullTarget > 0.5f,
                    color = holoCyan,
                    modifier = Modifier.weight(0.9f),
                    onClick = {
                        if (lidTarget < 0.5f) lidTarget = 1f
                        rightPullTarget = if (rightPullTarget > 0.5f) 0f else 1f
                    }
                )
            }
            HoloControlChip(
                label = if (autoSpin) (if (isArabic) "إيقاف اللف" else "Stop Spin") else (if (isArabic) "لف 360°" else "360° Spin"),
                active = autoSpin,
                color = holoSecondary,
                modifier = Modifier.weight(1f),
                onClick = { autoSpin = !autoSpin }
            )
            if (!isCaseDevice || showRealHoloPhoto) {
                HoloControlChip(
                    label = if (isArabic) "توسيط" else "Reset",
                    active = false,
                    color = Color.White,
                    modifier = Modifier.weight(0.8f),
                    onClick = {
                        autoSpin = false
                        yaw = -0.38f
                        pitch = 0.24f
                    }
                )
            }
        }
    }
}

@Composable
private fun RealModelHologramProjection(
    photo: ImageBitmap?,
    loading: Boolean,
    deviceName: String,
    yaw: Float,
    pitch: Float,
    scanLine: Float,
    holoColor: Color,
    isArabic: Boolean
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = holoColor, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isArabic) "جاري البحث عن صورة $deviceName..." else "Fetching $deviceName model...",
                    color = holoColor,
                    fontSize = 12.sp
                )
            }
        } else if (photo != null) {
            Box(
                modifier = Modifier
                    .size(205.dp)
                    .graphicsLayer {
                        rotationY = (yaw * 38f).coerceIn(-48f, 48f)
                        rotationX = (-pitch * 30f).coerceIn(-35f, 35f)
                        cameraDistance = 14f * density
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(holoColor.copy(0.1f))
                    .border(1.5.dp, holoColor.copy(0.75f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = photo,
                    contentDescription = deviceName,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.tint(holoColor.copy(alpha = 0.36f), BlendMode.SrcAtop),
                    modifier = Modifier.fillMaxSize()
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(holoColor.copy(0.14f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += 8f
                    }
                    val sy = size.height * scanLine
                    drawLine(holoColor.copy(0.85f), Offset(0f, sy), Offset(size.width, sy), strokeWidth = 3f)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = holoColor, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isArabic) "تعذر جلب صورة خارجية — اضغط «مجسم 3D» للتفاعل مع المجسم الكامل" else "Photo unavailable — switch to 3D Model",
                    color = Color.White.copy(0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HoloMiniBadge(label: String, value: String, active: Boolean, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) color.copy(0.22f) else Color.Black.copy(0.55f))
            .border(1.dp, if (active) color else Color.White.copy(0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontWeight = FontWeight.Black, fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
private fun HoloControlChip(
    label: String,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) color.copy(0.26f) else Color.White.copy(0.07f))
            .border(1.dp, if (active) color else Color.White.copy(0.18f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) color else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==================== 3D PROJECTION & GEOMETRY ENGINE ====================

private data class QuadProjected(
    val quad: Quad3D,
    val s0: Offset,
    val s1: Offset,
    val s2: Offset,
    val s3: Offset,
    val avgZ: Float
)

private fun project3D(
    v: Vec3,
    yaw: Float,
    pitch: Float,
    cx: Float,
    cy: Float,
    unit: Float
): Pair<Offset, Float> {
    val cosY = cos(yaw)
    val sinY = sin(yaw)
    val cosX = cos(pitch)
    val sinX = sin(pitch)

    val x1 = v.x * cosY + v.z * sinY
    val z1 = -v.x * sinY + v.z * cosY

    val y2 = v.y * cosX - z1 * sinX
    val z2 = v.y * sinX + z1 * cosX

    val fov = 3.8f
    val dist = (fov + z2 + 3.1f).coerceAtLeast(0.8f)
    val scale = (fov / dist) * unit
    return Offset(cx + x1 * scale, cy - y2 * scale) to z2
}

private fun rotateAroundX(p: Vec3, pivotY: Float, pivotZ: Float, angleRad: Float): Vec3 {
    val dy = p.y - pivotY
    val dz = p.z - pivotZ
    val c = cos(angleRad)
    val s = sin(angleRad)
    val ny = dy * c - dz * s
    val nz = dy * s + dz * c
    return Vec3(p.x, pivotY + ny, pivotZ + nz)
}

private fun DrawScope.drawHologramProjectorBase(
    cx: Float,
    totalH: Float,
    unit: Float,
    holoCyan: Color,
    accent: Color,
    pulse: Float
) {
    val baseY = totalH * 0.87f
    val rx = unit * 1.15f
    val ry = unit * 0.24f

    val beamPath = Path().apply {
        moveTo(cx - rx * 0.72f, baseY)
        lineTo(cx + rx * 0.72f, baseY)
        lineTo(cx + rx * 1.25f, totalH * 0.14f)
        lineTo(cx - rx * 1.25f, totalH * 0.14f)
        close()
    }
    drawPath(
        path = beamPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, holoCyan.copy(0.06f), holoCyan.copy(0.20f)),
            startY = totalH * 0.14f,
            endY = baseY
        )
    )

    val ringPulse = 1f + sin(pulse) * 0.04f
    drawOval(
        color = holoCyan.copy(0.18f),
        topLeft = Offset(cx - rx * ringPulse, baseY - ry * ringPulse),
        size = Size(rx * 2f * ringPulse, ry * 2f * ringPulse),
        style = Fill
    )
    drawOval(
        color = holoCyan.copy(0.72f),
        topLeft = Offset(cx - rx * ringPulse, baseY - ry * ringPulse),
        size = Size(rx * 2f * ringPulse, ry * 2f * ringPulse),
        style = Stroke(width = 2.4f)
    )
    drawOval(
        color = accent.copy(0.55f),
        topLeft = Offset(cx - rx * 0.62f, baseY - ry * 0.62f),
        size = Size(rx * 1.24f, ry * 1.24f),
        style = Stroke(width = 1.6f)
    )
}

private fun addBox3D(
    quads: MutableList<Quad3D>,
    minX: Float, maxX: Float,
    minY: Float, maxY: Float,
    minZ: Float, maxZ: Float,
    tint: Color? = null,
    fillAlpha: Float = 0.14f,
    strokeAlpha: Float = 0.85f,
    strokeWidth: Float = 1.9f,
    transform: (Vec3) -> Vec3 = { it }
) {
    val v000 = transform(Vec3(minX, minY, minZ))
    val v100 = transform(Vec3(maxX, minY, minZ))
    val v110 = transform(Vec3(maxX, maxY, minZ))
    val v010 = transform(Vec3(minX, maxY, minZ))
    val v001 = transform(Vec3(minX, minY, maxZ))
    val v101 = transform(Vec3(maxX, minY, maxZ))
    val v111 = transform(Vec3(maxX, maxY, maxZ))
    val v011 = transform(Vec3(minX, maxY, maxZ))

    quads.add(Quad3D(v000, v100, v110, v010, fillAlpha, strokeAlpha, tint, strokeWidth))
    quads.add(Quad3D(v001, v101, v111, v011, fillAlpha, strokeAlpha, tint, strokeWidth))
    quads.add(Quad3D(v010, v110, v111, v011, fillAlpha, strokeAlpha, tint, strokeWidth))
    quads.add(Quad3D(v000, v100, v101, v001, fillAlpha, strokeAlpha, tint, strokeWidth))
    quads.add(Quad3D(v000, v010, v011, v001, fillAlpha, strokeAlpha, tint, strokeWidth))
    quads.add(Quad3D(v100, v110, v111, v101, fillAlpha, strokeAlpha, tint, strokeWidth))
}

private fun build3DEarbudsCaseScene(
    quads: MutableList<Quad3D>,
    edges: MutableList<Edge3D>,
    lidOpen: Float,
    leftPull: Float,
    rightPull: Float,
    floatBob: Float,
    isAirPodsStyle: Boolean,
    holoCyan: Color,
    accent: Color
) {
    val bob: (Vec3) -> Vec3 = { Vec3(it.x, it.y + floatBob, it.z) }
    val halfW = if (isAirPodsStyle) 0.62f else 0.56f
    val bottomY = -0.58f
    val splitY = 0.28f
    val topY = 0.62f
    val halfD = 0.25f

    addBox3D(
        quads = quads,
        minX = -halfW, maxX = halfW,
        minY = bottomY, maxY = splitY,
        minZ = -halfD, maxZ = halfD,
        tint = holoCyan,
        fillAlpha = 0.11f,
        strokeAlpha = 0.85f,
        strokeWidth = 2.0f,
        transform = bob
    )

    val ledY = 0.06f
    val ledCol = if (lidOpen > 0.3f) Color(0xFF00FF88) else accent
    addBox3D(
        quads = quads,
        minX = -0.045f, maxX = 0.045f,
        minY = ledY - 0.045f, maxY = ledY + 0.045f,
        minZ = -halfD - 0.015f, maxZ = -halfD,
        tint = ledCol,
        fillAlpha = 0.85f,
        strokeAlpha = 1f,
        strokeWidth = 1.5f,
        transform = bob
    )

    addBox3D(
        quads = quads,
        minX = -0.11f, maxX = 0.11f,
        minY = bottomY - 0.01f, maxY = bottomY + 0.02f,
        minZ = -0.05f, maxZ = 0.05f,
        tint = Color(0xFFFFC857),
        fillAlpha = 0.35f,
        strokeAlpha = 0.9f,
        strokeWidth = 1.5f,
        transform = bob
    )

    buildSingle3DEarbud(
        quads = quads,
        edges = edges,
        centerX = -0.27f,
        baseY = -0.34f + leftPull * 0.88f,
        isLeft = true,
        isPulled = leftPull > 0.25f,
        floatBob = floatBob,
        tint = if (leftPull > 0.25f) Color(0xFF72FFFF) else holoCyan.copy(0.95f)
    )
    buildSingle3DEarbud(
        quads = quads,
        edges = edges,
        centerX = 0.27f,
        baseY = -0.34f + rightPull * 0.88f,
        isLeft = false,
        isPulled = rightPull > 0.25f,
        floatBob = floatBob,
        tint = if (rightPull > 0.25f) Color(0xFF72FFFF) else holoCyan.copy(0.95f)
    )

    val lidAngleRad = lidOpen * 1.78f
    val lidTransform: (Vec3) -> Vec3 = { p ->
        val rotated = rotateAroundX(p, pivotY = splitY, pivotZ = halfD, angleRad = lidAngleRad)
        Vec3(rotated.x, rotated.y + floatBob, rotated.z)
    }

    addBox3D(
        quads = quads,
        minX = -halfW, maxX = halfW,
        minY = splitY, maxY = topY,
        minZ = -halfD, maxZ = halfD,
        tint = if (lidOpen > 0.35f) Color(0xFFFFC857) else holoCyan,
        fillAlpha = 0.15f,
        strokeAlpha = 0.92f,
        strokeWidth = 2.1f,
        transform = lidTransform
    )

    edges.add(
        Edge3D(
            a = bob(Vec3(-halfW * 0.65f, splitY, halfD + 0.01f)),
            b = bob(Vec3(halfW * 0.65f, splitY, halfD + 0.01f)),
            alpha = 0.95f,
            width = 3.2f,
            tint = Color(0xFFFFC857)
        )
    )
}

private fun buildSingle3DEarbud(
    quads: MutableList<Quad3D>,
    edges: MutableList<Edge3D>,
    centerX: Float,
    baseY: Float,
    isLeft: Boolean,
    isPulled: Boolean,
    floatBob: Float,
    tint: Color
) {
    val bob: (Vec3) -> Vec3 = { Vec3(it.x, it.y + floatBob, it.z) }
    val dir = if (isLeft) -1f else 1f
    val fill = if (isPulled) 0.30f else 0.16f

    addBox3D(
        quads = quads,
        minX = centerX - 0.048f, maxX = centerX + 0.048f,
        minY = baseY, maxY = baseY + 0.44f,
        minZ = -0.048f, maxZ = 0.048f,
        tint = tint,
        fillAlpha = fill,
        strokeAlpha = 0.95f,
        strokeWidth = 1.7f,
        transform = bob
    )

    val headMinX = if (isLeft) centerX - 0.09f else centerX - 0.17f
    val headMaxX = if (isLeft) centerX + 0.17f else centerX + 0.09f
    addBox3D(
        quads = quads,
        minX = headMinX, maxX = headMaxX,
        minY = baseY + 0.38f, maxY = baseY + 0.60f,
        minZ = -0.10f, maxZ = 0.10f,
        tint = tint,
        fillAlpha = fill + 0.06f,
        strokeAlpha = 1f,
        strokeWidth = 1.9f,
        transform = bob
    )

    val tipX = if (isLeft) headMaxX else headMinX
    addBox3D(
        quads = quads,
        minX = minOf(tipX, tipX - dir * 0.08f),
        maxX = maxOf(tipX, tipX - dir * 0.08f),
        minY = baseY + 0.43f, maxY = baseY + 0.55f,
        minZ = -0.065f, maxZ = 0.065f,
        tint = Color.White,
        fillAlpha = 0.32f,
        strokeAlpha = 0.9f,
        strokeWidth = 1.5f,
        transform = bob
    )

    if (isPulled) {
        edges.add(
            Edge3D(
                a = bob(Vec3(centerX, baseY, 0f)),
                b = bob(Vec3(centerX, -0.30f, 0f)),
                alpha = 0.65f,
                width = 2.0f,
                tint = Color(0xFF00FFAA)
            )
        )
    }
}

private fun build3DHeadphonesScene(
    quads: MutableList<Quad3D>,
    edges: MutableList<Edge3D>,
    floatBob: Float,
    pulse: Float,
    holoCyan: Color,
    accent: Color
) {
    val bob: (Vec3) -> Vec3 = { Vec3(it.x, it.y + floatBob, it.z) }

    val segments = 12
    val radius = 0.72f
    val bandHalfD = 0.13f
    for (i in 0 until segments) {
        val a0 = PI.toFloat() * (i.toFloat() / segments)
        val a1 = PI.toFloat() * ((i + 1).toFloat() / segments)
        val x0 = -cos(a0) * radius
        val y0 = sin(a0) * radius + 0.08f
        val x1 = -cos(a1) * radius
        val y1 = sin(a1) * radius + 0.08f

        quads.add(
            Quad3D(
                p0 = bob(Vec3(x0, y0, -bandHalfD)),
                p1 = bob(Vec3(x1, y1, -bandHalfD)),
                p2 = bob(Vec3(x1, y1, bandHalfD)),
                p3 = bob(Vec3(x0, y0, bandHalfD)),
                fillAlpha = 0.18f,
                strokeAlpha = 0.92f,
                tint = holoCyan,
                strokeWidth = 2.0f
            )
        )
    }

    listOf(-1f, 1f).forEach { side ->
        val cx = side * 0.68f
        val innerX = cx - side * 0.14f
        val outerX = cx + side * (0.14f + pulse)

        addBox3D(
            quads = quads,
            minX = minOf(innerX, outerX),
            maxX = maxOf(innerX, outerX),
            minY = -0.50f, maxY = 0.12f,
            minZ = -0.26f, maxZ = 0.26f,
            tint = holoCyan,
            fillAlpha = 0.16f,
            strokeAlpha = 0.92f,
            strokeWidth = 2.0f,
            transform = bob
        )

        addBox3D(
            quads = quads,
            minX = minOf(innerX, innerX - side * 0.09f),
            maxX = maxOf(innerX, innerX - side * 0.09f),
            minY = -0.44f, maxY = 0.06f,
            minZ = -0.21f, maxZ = 0.21f,
            tint = accent,
            fillAlpha = 0.25f,
            strokeAlpha = 0.9f,
            strokeWidth = 1.6f,
            transform = bob
        )

        edges.add(
            Edge3D(
                a = bob(Vec3(side * 0.72f, 0.12f, 0f)),
                b = bob(Vec3(cx, 0.04f, 0f)),
                alpha = 0.95f,
                width = 3.2f,
                tint = Color(0xFFFFC857)
            )
        )
    }
}

private fun build3DCarScene(
    quads: MutableList<Quad3D>,
    edges: MutableList<Edge3D>,
    floatBob: Float,
    wheelSpin: Float,
    isPlaying: Boolean,
    holoCyan: Color,
    accent: Color
) {
    val bob: (Vec3) -> Vec3 = { Vec3(it.x, it.y + floatBob, it.z) }

    addBox3D(
        quads = quads,
        minX = -0.96f, maxX = 0.96f,
        minY = -0.34f, maxY = 0.04f,
        minZ = -0.44f, maxZ = 0.44f,
        tint = holoCyan,
        fillAlpha = 0.14f,
        strokeAlpha = 0.9f,
        strokeWidth = 2.0f,
        transform = bob
    )

    addBox3D(
        quads = quads,
        minX = -0.46f, maxX = 0.42f,
        minY = 0.04f, maxY = 0.36f,
        minZ = -0.36f, maxZ = 0.36f,
        tint = accent,
        fillAlpha = 0.18f,
        strokeAlpha = 0.92f,
        strokeWidth = 1.9f,
        transform = bob
    )

    listOf(-0.30f, 0.30f).forEach { zPos ->
        addBox3D(
            quads = quads,
            minX = 0.93f, maxX = 0.98f,
            minY = -0.18f, maxY = -0.06f,
            minZ = zPos - 0.08f, maxZ = zPos + 0.08f,
            tint = Color(0xFFFFF59D),
            fillAlpha = 0.85f,
            strokeAlpha = 1f,
            strokeWidth = 1.5f,
            transform = bob
        )
    }

    val wheelXs = listOf(-0.56f, 0.56f)
    val wheelZs = listOf(-0.46f, 0.46f)
    wheelXs.forEach { wx ->
        wheelZs.forEach { wz ->
            addBox3D(
                quads = quads,
                minX = wx - 0.16f, maxX = wx + 0.16f,
                minY = -0.50f, maxY = -0.18f,
                minZ = wz - 0.06f, maxZ = wz + 0.06f,
                tint = Color(0xFFFFC857),
                fillAlpha = 0.22f,
                strokeAlpha = 0.9f,
                strokeWidth = 1.6f,
                transform = bob
            )
        }
    }

    if (isPlaying) {
        for (i in 0 until 3) {
            val r = 0.25f + i * 0.18f + (sin(wheelSpin + i) * 0.04f)
            edges.add(
                Edge3D(
                    a = bob(Vec3(-r, 0.52f + i * 0.08f, 0f)),
                    b = bob(Vec3(r, 0.52f + i * 0.08f, 0f)),
                    alpha = 0.8f - i * 0.2f,
                    width = 2.4f,
                    tint = Color(0xFF00FF88)
                )
            )
        }
    }
}

private fun build3DSpeakerScene(
    quads: MutableList<Quad3D>,
    edges: MutableList<Edge3D>,
    floatBob: Float,
    bassPulse: Float,
    holoCyan: Color,
    accent: Color
) {
    val bob: (Vec3) -> Vec3 = { Vec3(it.x, it.y + floatBob, it.z) }

    val sides = 8
    val r = 0.36f
    val halfL = 0.80f
    for (i in 0 until sides) {
        val a0 = (2.0 * PI * i / sides).toFloat()
        val a1 = (2.0 * PI * (i + 1) / sides).toFloat()
        val y0 = sin(a0) * r
        val z0 = cos(a0) * r
        val y1 = sin(a1) * r
        val z1 = cos(a1) * r

        quads.add(
            Quad3D(
                p0 = bob(Vec3(-halfL, y0, z0)),
                p1 = bob(Vec3(halfL, y0, z0)),
                p2 = bob(Vec3(halfL, y1, z1)),
                p3 = bob(Vec3(-halfL, y1, z1)),
                fillAlpha = 0.14f,
                strokeAlpha = 0.88f,
                tint = holoCyan,
                strokeWidth = 1.9f
            )
        )
    }

    listOf(-1f, 1f).forEach { side ->
        val x = side * (halfL + 0.03f + bassPulse)
        addBox3D(
            quads = quads,
            minX = x - 0.03f, maxX = x + 0.03f,
            minY = -0.24f, maxY = 0.24f,
            minZ = -0.24f, maxZ = 0.24f,
            tint = accent,
            fillAlpha = 0.35f,
            strokeAlpha = 1f,
            strokeWidth = 2.0f,
            transform = bob
        )
    }

    edges.add(
        Edge3D(
            a = bob(Vec3(-0.3f, r + 0.03f, 0f)),
            b = bob(Vec3(0.3f, r + 0.03f, 0f)),
            alpha = 0.95f,
            width = 3.0f,
            tint = Color(0xFFFFC857)
        )
    )
}
