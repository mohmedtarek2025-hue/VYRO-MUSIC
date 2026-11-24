package com.mohamedtarek.vyromusic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ==================== BLUETOOTH SMART MODELS ====================

enum class BtDeviceCategory {
    AIRPODS,
    SOUNDCORE,
    GALAXY_BUDS,
    SMART_EARBUDS,
    CAR_AUDIO,
    SPEAKER,
    HEADPHONES,
    UNKNOWN
}

enum class SmartTouchAction(val id: String, val labelAr: String, val labelEn: String) {
    NEXT("next", "الأغنية التالية", "Next Track"),
    PREV("prev", "الأغنية السابقة", "Previous Track"),
    TOGGLE_8D("toggle_8d", "تشغيل / إيقاف 8D محيطي", "Toggle 8D Spatial"),
    SEEK_FWD_15("seek_fwd_15", "تقديم 15 ثانية", "Forward 15s"),
    SEEK_BACK_15("seek_back_15", "ترجيع 15 ثانية", "Rewind 15s"),
    TOGGLE_FAV("toggle_fav", "إضافة / إزالة من المفضلة", "Toggle Favorite"),
    BOOST_BASS("boost_bass", "تبديل وضع Bass قوي", "Toggle Power Bass");

    companion object {
        fun fromId(id: String?): SmartTouchAction =
            values().find { it.id == id } ?: NEXT
    }
}

data class AirPodsBatteryInfo(
    val modelName: String,
    val leftPercent: Int,
    val rightPercent: Int,
    val casePercent: Int,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val rssi: Int,
    val updatedAtMs: Long
)

data class DeviceCapabilities(
    val supportsTriBattery: Boolean,
    val supportsStandardBattery: Boolean,
    val supportsSmartTouch: Boolean,
    val supportsAutoEq: Boolean,
    val supportsCarForceMusic: Boolean,
    val supportsAncHint: Boolean,
    val brandTag: String
)

data class BtDeviceProfile(
    val address: String,
    val autoPlayOnConnect: Boolean = true,
    val eqPreset: String = "Keep Current",
    val enable8D: Boolean = false,
    val doubleTapAction: SmartTouchAction = SmartTouchAction.NEXT,
    val tripleTapAction: SmartTouchAction = SmartTouchAction.PREV,
    val forceCarMusic: Boolean = true,
    val autoOpenCarMode: Boolean = false,
    val carTargetVolumePercent: Int = 85
)

data class SmartBtDevice(
    val name: String,
    val address: String,
    val category: BtDeviceCategory,
    val isBonded: Boolean,
    val isConnected: Boolean,
    val batteryPercent: Int,
    val airPodsBattery: AirPodsBatteryInfo?,
    val capabilities: DeviceCapabilities,
    val rssi: Int = 0
)

// ==================== SMART BLUETOOTH & CAR ENGINE ====================

object VyroBluetoothEngine {

    const val ACTION_FORCE_CAR_PLAY = "com.mohamedtarek.vyromusic.FORCE_CAR_PLAY"
    const val ACTION_APPLY_DEVICE_PROFILE = "com.mohamedtarek.vyromusic.APPLY_DEVICE_PROFILE"
    const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
    const val EXTRA_IS_CAR = "extra_is_car"

    private const val APPLE_MANUFACTURER_ID = 0x004C
    private const val GITHUB_CATALOG_URL =
        "https://raw.githubusercontent.com/mohmedtarek2025-hue/VYRO-MUSIC/main/devices_catalog.json"

    @Volatile
    var latestAirPodsBeacon: AirPodsBatteryInfo? = null
        private set

    @Volatile
    private var cachedCatalog: List<Pair<String, String>>? = null

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getAdapter(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter
    }

    fun isBluetoothEnabled(context: Context): Boolean =
        runCatching { getAdapter(context)?.isEnabled == true }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun extractDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    fun classifyDevice(device: BluetoothDevice, nameOverride: String? = null): BtDeviceCategory {
        val name = (nameOverride ?: runCatching { device.name }.getOrNull() ?: "").lowercase(Locale.ROOT)
        val btClass = runCatching { device.bluetoothClass }.getOrNull()
        val devClass = btClass?.deviceClass ?: -1

        return when {
            name.contains("airpod") || name.contains("pods pro") || name.contains("pods max") -> BtDeviceCategory.AIRPODS
            name.contains("soundcore") || name.contains("anker") || name.contains("rove") || name.contains("liberty") -> BtDeviceCategory.SOUNDCORE
            name.contains("galaxy buds") || name.contains("buds2") || name.contains("buds3") || name.contains("buds fe") || name.contains("buds pro") -> BtDeviceCategory.GALAXY_BUDS
            devClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                isLikelyCarName(name) -> BtDeviceCategory.CAR_AUDIO
            name.contains("wf-1000") || name.contains("wh-1000") || name.contains("freebuds") ||
                name.contains("redmi buds") || name.contains("mi buds") || name.contains("oppo enco") ||
                name.contains("realme buds") || name.contains("jbl") || name.contains("beats") ||
                name.contains("nothing ear") || name.contains("earbuds") || name.contains("tws") ||
                name.contains("buds") || name.contains("pods") -> BtDeviceCategory.SMART_EARBUDS
            devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                name.contains("speaker") || name.contains("boombox") || name.contains("partybox") ||
                name.contains("charge") || name.contains("flip") -> BtDeviceCategory.SPEAKER
            devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE ||
                name.contains("headset") || name.contains("headphone") -> BtDeviceCategory.HEADPHONES
            else -> BtDeviceCategory.UNKNOWN
        }
    }

    private fun isLikelyCarName(lower: String): Boolean {
        val carKeywords = listOf(
            "car", "auto", "bmw", "mercedes", "mb bluetooth", "audi", "toyota", "honda",
            "hyundai", "kia", "nissan", "chevrolet", "ford", "sync", "uconnect", "carplay",
            "vw", "volkswagen", "skoda", "seat", "renault", "peugeot", "mg", "chery",
            "byd", "geely", "suzuki", "mitsubishi", "mazda", "jeep", "fiat", "opel",
            "pioneer", "kenwood", "alpine", "sony xav", "car-kit", "carkit", "bt-car",
            "handsfreelink", "my link", "mylink", "multimedia", "headunit", "obd"
        )
        return carKeywords.any { lower.contains(it) }
    }

    fun capabilitiesFor(category: BtDeviceCategory, hasAirPodsBeacon: Boolean): DeviceCapabilities {
        return when (category) {
            BtDeviceCategory.AIRPODS -> DeviceCapabilities(
                supportsTriBattery = true,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = true,
                brandTag = if (hasAirPodsBeacon) "AirPods BLE" else "AirPods Series"
            )
            BtDeviceCategory.SOUNDCORE -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = true,
                brandTag = "Anker Soundcore"
            )
            BtDeviceCategory.GALAXY_BUDS -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = true,
                brandTag = "Samsung Galaxy Buds"
            )
            BtDeviceCategory.CAR_AUDIO -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = false,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = true,
                supportsAncHint = false,
                brandTag = "Car Head Unit"
            )
            BtDeviceCategory.SMART_EARBUDS -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = false,
                brandTag = "Smart Earbuds"
            )
            BtDeviceCategory.SPEAKER -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = false,
                brandTag = "Bluetooth Speaker"
            )
            BtDeviceCategory.HEADPHONES -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = false,
                brandTag = "Headphones"
            )
            BtDeviceCategory.UNKNOWN -> DeviceCapabilities(
                supportsTriBattery = false,
                supportsStandardBattery = true,
                supportsSmartTouch = true,
                supportsAutoEq = true,
                supportsCarForceMusic = false,
                supportsAncHint = false,
                brandTag = "Bluetooth Audio"
            )
        }
    }

    fun readClassicBatteryLevel(device: BluetoothDevice): Int {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level in 0..100) level else -1
        }.getOrDefault(-1)
    }

    fun isDeviceConnectedReflect(device: BluetoothDevice): Boolean {
        return runCatching {
            val method = device.javaClass.getMethod("isConnected")
            (method.invoke(device) as? Boolean) == true
        }.getOrDefault(false)
    }

    // اكتشاف فوري لأي سماعة متصلة حالياً (حتى لو اتصلت قبل فتح التطبيق)
    fun findConnectedAudioDevice(context: Context): SmartBtDevice? {
        if (!hasConnectPermission(context) || !isBluetoothEnabled(context)) return null
        return getBondedSmartDevices(context).firstOrNull { it.isConnected }
    }

    fun syncCurrentlyConnectedDevice(context: Context, prefs: SharedPreferences): SmartBtDevice? {
        val connected = findConnectedAudioDevice(context) ?: return null
        setActiveDevice(prefs, connected.address, connected.name, connected.category)
        return connected
    }

    fun parseAirPodsBleResult(result: ScanResult): AirPodsBatteryInfo? {
        val data = result.scanRecord?.getManufacturerSpecificData(APPLE_MANUFACTURER_ID) ?: return null
        if (data.size < 15) return null
        val type = data[0].toInt() and 0xFF
        if (type != 0x07) return null

        val m1 = data[3].toInt() and 0xFF
        val m2 = data[4].toInt() and 0xFF
        val modelCode = (m1 shl 8) or m2
        val modelName = when (modelCode) {
            0x0220 -> "AirPods (1st Gen)"
            0x0F20 -> "AirPods (2nd Gen)"
            0x1320 -> "AirPods (3rd Gen)"
            0x0E20 -> "AirPods Pro"
            0x1420 -> "AirPods Pro 2"
            0x2420 -> "AirPods Pro 2 (USB-C)"
            0x1920 -> "AirPods 4"
            0x1B20 -> "AirPods 4 (ANC)"
            0x0A20 -> "AirPods Max"
            else -> "AirPods / Smart Pods"
        }

        val status = data[5].toInt() and 0xFF
        val isFlipped = (status and 0x02) == 0

        val b6 = data[6].toInt() and 0xFF
        val highNibble = (b6 shr 4) and 0x0F
        val lowNibble = b6 and 0x0F

        val leftRaw = if (isFlipped) lowNibble else highNibble
        val rightRaw = if (isFlipped) highNibble else lowNibble

        val b7 = data[7].toInt() and 0xFF
        val caseRaw = b7 and 0x0F
        val chargeFlags = (b7 shr 4) and 0x0F

        val leftCharging = if (isFlipped) (chargeFlags and 0x01) != 0 else (chargeFlags and 0x02) != 0
        val rightCharging = if (isFlipped) (chargeFlags and 0x02) != 0 else (chargeFlags and 0x01) != 0
        val caseCharging = (chargeFlags and 0x04) != 0

        val leftPct = if (leftRaw in 0..10) leftRaw * 10 else -1
        val rightPct = if (rightRaw in 0..10) rightRaw * 10 else -1
        val casePct = if (caseRaw in 0..10) caseRaw * 10 else -1

        if (leftPct < 0 && rightPct < 0 && casePct < 0) return null

        val info = AirPodsBatteryInfo(
            modelName = modelName,
            leftPercent = leftPct,
            rightPercent = rightPct,
            casePercent = casePct,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = caseCharging,
            rssi = result.rssi,
            updatedAtMs = System.currentTimeMillis()
        )
        latestAirPodsBeacon = info
        return info
    }

    @SuppressLint("MissingPermission")
    fun startAirPodsBleScanner(
        context: Context,
        onAirPodsUpdate: (AirPodsBatteryInfo) -> Unit
    ): ScanCallback? {
        if (!hasScanPermission(context) || !isBluetoothEnabled(context)) return null
        val scanner: BluetoothLeScanner = getAdapter(context)?.bluetoothLeScanner ?: return null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                parseAirPodsBleResult(result)?.let(onAirPodsUpdate)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { r -> parseAirPodsBleResult(r)?.let(onAirPodsUpdate) }
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        return runCatching {
            scanner.startScan(null, settings, callback)
            callback
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    fun stopAirPodsBleScanner(context: Context, callback: ScanCallback?) {
        if (callback == null || !hasScanPermission(context)) return
        runCatching {
            getAdapter(context)?.bluetoothLeScanner?.stopScan(callback)
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedSmartDevices(context: Context): List<SmartBtDevice> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = getAdapter(context) ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return emptyList()
        val freshBeacon = latestAirPodsBeacon?.takeIf { System.currentTimeMillis() - it.updatedAtMs < 90_000L }

        return bonded.mapNotNull { dev ->
            val devName = runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cat = classifyDevice(dev, devName)
            val connected = isDeviceConnectedReflect(dev)
            val classicBat = readClassicBatteryLevel(dev)
            val beacon = if (cat == BtDeviceCategory.AIRPODS) freshBeacon else null
            val effectiveBat = when {
                classicBat in 0..100 -> classicBat
                beacon != null -> listOf(beacon.leftPercent, beacon.rightPercent).filter { it >= 0 }.minOrNull() ?: beacon.casePercent
                else -> -1
            }
            SmartBtDevice(
                name = devName,
                address = dev.address,
                category = cat,
                isBonded = true,
                isConnected = connected,
                batteryPercent = effectiveBat,
                airPodsBattery = beacon,
                capabilities = capabilitiesFor(cat, beacon != null)
            )
        }.sortedWith(
            compareByDescending<SmartBtDevice> { it.isConnected }
                .thenByDescending { it.category == BtDeviceCategory.CAR_AUDIO || it.category == BtDeviceCategory.AIRPODS }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    @SuppressLint("MissingPermission")
    fun connectAudioDevice(
        context: Context,
        address: String,
        onResult: (Boolean) -> Unit
    ) {
        if (!hasConnectPermission(context)) {
            onResult(false)
            return
        }
        val adapter = getAdapter(context)
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (adapter == null || device == null) {
            onResult(false)
            return
        }

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            val startedBond = runCatching { device.createBond() }.getOrDefault(false)
            onResult(startedBond)
            return
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                var ok = false
                if (profile == BluetoothProfile.A2DP && proxy is BluetoothA2dp) {
                    ok = runCatching {
                        val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        (connectMethod.invoke(proxy, device) as? Boolean) == true
                    }.getOrDefault(false)
                }
                runCatching { adapter.closeProfileProxy(profile, proxy) }
                onResult(ok)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }

        val bound = runCatching {
            adapter.getProfileProxy(context.applicationContext, listener, BluetoothProfile.A2DP)
        }.getOrDefault(false)
        if (!bound) onResult(false)
    }

    @Suppress("DEPRECATION")
    fun forceCarAudioMusicMode(context: Context, targetVolumePercent: Int = 85) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (am.isBluetoothScoOn) {
                runCatching { am.stopBluetoothSco() }
                runCatching { am.isBluetoothScoOn = false }
            }
            if (am.mode != AudioManager.MODE_NORMAL) {
                runCatching { am.mode = AudioManager.MODE_NORMAL }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { am.clearCommunicationDevice() }
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .build()
            am.requestAudioFocus(req)

            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val desiredVol = ((maxVol * targetVolumePercent.coerceIn(30, 100)) / 100).coerceIn(1, maxVol)
            if (curVol < desiredVol) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, desiredVol, 0)
            }
        }
    }

    // ==================== ZERO-API DEVICE PHOTO CACHE & GITHUB CATALOG ====================

    private fun safeFileName(address: String): String =
        "bt_img_" + address.replace(Regex("[^A-Za-z0-9]"), "_") + ".png"

    fun getCustomPhotoFile(context: Context, address: String): File {
        val dir = File(context.filesDir, "bt_photos").apply { mkdirs() }
        return File(dir, safeFileName(address))
    }

    fun saveCustomPhoto(context: Context, address: String, uri: Uri): Bitmap? {
        return runCatching {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val maxDim = 640
            val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else bmp
            FileOutputStream(getCustomPhotoFile(context, address)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
            scaled
        }.getOrNull()
    }

    fun loadSavedDevicePhoto(context: Context, address: String): Bitmap? {
        val f = getCustomPhotoFile(context, address)
        if (!f.exists() || f.length() < 128L) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun fetchGithubCatalogImageUrl(deviceName: String): String? {
        val lowerName = deviceName.lowercase(Locale.ROOT)
        val entries = cachedCatalog ?: runCatching {
            val conn = (URL(GITHUB_CATALOG_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 8000
                setRequestProperty("User-Agent", "VYRO-MUSIC/5.2.0")
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@runCatching emptyList<Pair<String, String>>()
            }
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val kw = o.optString("keyword", "").trim().lowercase(Locale.ROOT)
                    val url = o.optString("image", "").trim()
                    if (kw.isNotBlank() && url.isNotBlank()) add(kw to url)
                }
            }
        }.getOrDefault(emptyList()).also { cachedCatalog = it }

        return entries.firstOrNull { (kw, _) -> lowerName.contains(kw) }?.second
    }

    // ==================== PER-DEVICE PROFILES STORAGE ====================

    fun loadProfile(prefs: SharedPreferences, address: String, category: BtDeviceCategory = BtDeviceCategory.UNKNOWN): BtDeviceProfile {
        val raw = VyroSafePrefs.getString(prefs, "bt_profile_$address", "")
        if (raw.isBlank()) {
            return BtDeviceProfile(
                address = address,
                autoPlayOnConnect = true,
                eqPreset = if (category == BtDeviceCategory.CAR_AUDIO) "Car Punch" else "Keep Current",
                enable8D = false,
                doubleTapAction = SmartTouchAction.NEXT,
                tripleTapAction = SmartTouchAction.PREV,
                forceCarMusic = category == BtDeviceCategory.CAR_AUDIO,
                autoOpenCarMode = false,
                carTargetVolumePercent = 85
            )
        }
        return runCatching {
            val o = JSONObject(raw)
            BtDeviceProfile(
                address = address,
                autoPlayOnConnect = o.optBoolean("autoPlay", true),
                eqPreset = o.optString("eqPreset", "Keep Current"),
                enable8D = o.optBoolean("enable8D", false),
                doubleTapAction = SmartTouchAction.fromId(o.optString("doubleTap", "next")),
                tripleTapAction = SmartTouchAction.fromId(o.optString("tripleTap", "prev")),
                forceCarMusic = o.optBoolean("forceCarMusic", category == BtDeviceCategory.CAR_AUDIO),
                autoOpenCarMode = o.optBoolean("autoCarMode", false),
                carTargetVolumePercent = o.optInt("carVol", 85).coerceIn(30, 100)
            )
        }.getOrDefault(BtDeviceProfile(address = address))
    }

    fun saveProfile(prefs: SharedPreferences, profile: BtDeviceProfile) {
        val o = JSONObject().apply {
            put("autoPlay", profile.autoPlayOnConnect)
            put("eqPreset", profile.eqPreset)
            put("enable8D", profile.enable8D)
            put("doubleTap", profile.doubleTapAction.id)
            put("tripleTap", profile.tripleTapAction.id)
            put("forceCarMusic", profile.forceCarMusic)
            put("autoCarMode", profile.autoOpenCarMode)
            put("carVol", profile.carTargetVolumePercent)
        }
        prefs.edit().putString("bt_profile_${profile.address}", o.toString()).apply()
    }

    fun getActiveDeviceAddress(prefs: SharedPreferences): String =
        VyroSafePrefs.getString(prefs, "active_bt_address", "")

    fun setActiveDevice(prefs: SharedPreferences, address: String, name: String, category: BtDeviceCategory) {
        prefs.edit()
            .putString("active_bt_address", address)
            .putString("active_bt_name", name)
            .putString("active_bt_cat", category.name)
            .apply()
    }

    fun applyProfileAudioSettings(prefs: SharedPreferences, profile: BtDeviceProfile) {
        val editor = prefs.edit()
        when (profile.eqPreset) {
            "Phonk Bass" -> {
                editor.putString("band_gains", "[0.75,0.45,0.05,0.28,0.50]")
                editor.putInt("bass_strength", 78)
                editor.putInt("virtualizer_strength", 50)
            }
            "Crystal Clear" -> {
                editor.putString("band_gains", "[0.28,0.16,0.0,0.14,0.24]")
                editor.putInt("bass_strength", 35)
                editor.putInt("virtualizer_strength", 30)
            }
            "Stadium 8D" -> {
                editor.putString("band_gains", "[0.50,0.25,0.20,0.40,0.60]")
                editor.putInt("bass_strength", 55)
                editor.putInt("virtualizer_strength", 92)
            }
            "Car Punch" -> {
                editor.putString("band_gains", "[0.65,0.35,0.15,0.30,0.42]")
                editor.putInt("bass_strength", 72)
                editor.putInt("virtualizer_strength", 40)
            }
            "Flat" -> {
                editor.putString("band_gains", "[0.0,0.0,0.0,0.0,0.0]")
                editor.putInt("bass_strength", 0)
                editor.putInt("virtualizer_strength", 0)
            }
        }
        if (profile.eqPreset != "Keep Current") {
            editor.putBoolean("spatial_8d_enabled", profile.enable8D)
        }
        editor.apply()
        VyroDspController.syncFromPrefs(prefs)
    }
}
