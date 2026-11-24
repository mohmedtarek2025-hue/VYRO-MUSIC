package com.mohamedtarek.vyromusic

import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// ==================== LOCK-FREE REAL-TIME DSP CONTROLLER ====================

object VyroDspController {
    @Volatile var band0: Float = 0.28f
    @Volatile var band1: Float = 0.16f
    @Volatile var band2: Float = 0.00f
    @Volatile var band3: Float = 0.14f
    @Volatile var band4: Float = 0.24f

    @Volatile var bassStrength: Int = 35
    @Volatile var virtualizerStrength: Int = 30
    @Volatile var loudnessGain: Int = 0
    @Volatile var spatial8DEnabled: Boolean = false
    @Volatile var version: Int = 1

    fun syncFromPrefs(prefs: SharedPreferences) {
        val gains = VyroSafePrefs.getBandGains(prefs)
        band0 = gains.getOrElse(0) { 0.28f }.coerceIn(-1f, 1f)
        band1 = gains.getOrElse(1) { 0.16f }.coerceIn(-1f, 1f)
        band2 = gains.getOrElse(2) { 0.00f }.coerceIn(-1f, 1f)
        band3 = gains.getOrElse(3) { 0.14f }.coerceIn(-1f, 1f)
        band4 = gains.getOrElse(4) { 0.24f }.coerceIn(-1f, 1f)
        bassStrength = VyroSafePrefs.getInt(prefs, "bass_strength", 35).coerceIn(0, 100)
        virtualizerStrength = VyroSafePrefs.getInt(prefs, "virtualizer_strength", 30).coerceIn(0, 100)
        loudnessGain = VyroSafePrefs.getInt(prefs, "loudness_gain", 0).coerceIn(0, 100)
        spatial8DEnabled = VyroSafePrefs.getBoolean(prefs, "spatial_8d_enabled", false)
        version++
    }
}

// ==================== PURE SOFTWARE BIQUAD FILTER ====================

private class StereoBiquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun setPeaking(sampleRate: Double, freq: Double, q: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05 || freq >= sampleRate * 0.48) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val nb0 = 1.0 + alpha * a
        val nb1 = -2.0 * cosW0
        val nb2 = 1.0 - alpha * a
        val na0 = 1.0 + alpha / a
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha / a

        if (abs(na0) < 1e-9) return
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setLowShelf(sampleRate: Double, freq: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05 || freq >= sampleRate * 0.45) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * 0.7071)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val nb0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val nb2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val na0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val na2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

        if (abs(na0) < 1e-9) return
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun processLeft(x: Double): Double {
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return if (y.isFinite()) y else { reset(); x }
    }

    fun processRight(x: Double): Double {
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return if (y.isFinite()) y else { reset(); x }
    }

    fun reset() {
        z1L = 0.0; z2L = 0.0
        z1R = 0.0; z2R = 0.0
    }
}

// ==================== STUDIO HALL COMB REVERB (FOR TRUE 8D DEPTH) ====================

private class CombReverb(size: Int) {
    private val buf = DoubleArray(size.coerceAtLeast(32))
    private var idx = 0
    private var filterStore = 0.0

    fun process(input: Double, feedback: Double, damp: Double): Double {
        val output = buf[idx]
        filterStore = output * (1.0 - damp) + filterStore * damp
        buf[idx] = input + filterStore * feedback
        idx++
        if (idx >= buf.size) idx = 0
        return output
    }

    fun reset() {
        buf.fill(0.0)
        idx = 0
        filterStore = 0.0
    }
}

// ==================== VYRO STUDIO PRO PCM AUDIO PROCESSOR ====================

@OptIn(UnstableApi::class)
class VyroDspAudioProcessor : BaseAudioProcessor() {

    private val eq60 = StereoBiquad()
    private val eq230 = StereoBiquad()
    private val eq910 = StereoBiquad()
    private val eq3600 = StereoBiquad()
    private val eq14000 = StereoBiquad()
    private val subBassShelf = StereoBiquad()

    private var sampleRate = 44100.0
    private var channelCount = 2
    private var appliedVersion = -1

    // محرك الـ 8D البينورال (HRTF Head-Shadow + ITD Delay + Concert Hall Reverb)
    private var orbitPhase = 0.0
    private val itdSize = 64
    private val itdMask = itdSize - 1
    private val itdBufL = DoubleArray(itdSize)
    private val itdBufR = DoubleArray(itdSize)
    private var itdWriteIdx = 0

    // فلتر ظل الرأس (HRTF Head-Shadow Low-Pass) للأذن البعيدة
    private var shadowL = 0.0
    private var shadowR = 0.0

    // قاعة صوتية ستيريو ثلاثية الأبعاد (تعمل على كل المخارج حتى سبيكر الموبايل)
    private val combL1 = CombReverb(1117)
    private val combL2 = CombReverb(1357)
    private val combL3 = CombReverb(1619)
    private val combR1 = CombReverb(1141)
    private val combR2 = CombReverb(1381)
    private val combR3 = CombReverb(1643)

    private var stereoWidth = 1.0
    private var preampLinear = 1.0
    private var is8DActive = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount != 1 && inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000).toDouble()
        channelCount = inputAudioFormat.channelCount
        appliedVersion = -1
        refreshFilters()
        return inputAudioFormat
    }

    private fun refreshFilters() {
        val v = VyroDspController.version
        if (appliedVersion == v) return
        appliedVersion = v

        eq60.setPeaking(sampleRate, 60.0, 1.0, VyroDspController.band0 * 10.5)
        eq230.setPeaking(sampleRate, 230.0, 1.1, VyroDspController.band1 * 9.5)
        eq910.setPeaking(sampleRate, 910.0, 1.1, VyroDspController.band2 * 9.0)
        eq3600.setPeaking(sampleRate, 3600.0, 1.1, VyroDspController.band3 * 9.5)
        eq14000.setPeaking(sampleRate, 14000.0, 0.9, VyroDspController.band4 * 10.5)

        val bassDb = (VyroDspController.bassStrength / 100.0) * 10.0
        subBassShelf.setLowShelf(sampleRate, 85.0, bassDb)

        is8DActive = VyroDspController.spatial8DEnabled
        val effectiveVirt = if (is8DActive) maxOf(VyroDspController.virtualizerStrength, 82) else VyroDspController.virtualizerStrength
        stereoWidth = 1.0 + (effectiveVirt / 100.0) * 0.90

        val peakBoost = maxOf(
            VyroDspController.band0,
            VyroDspController.band1,
            VyroDspController.band2,
            VyroDspController.band3,
            VyroDspController.band4,
            0f
        ) * 4.0 + (bassDb * 0.35)
        val extraGainDb = (VyroDspController.loudnessGain / 100.0) * 7.5
        val netDb = (extraGainDb - (peakBoost * 0.40)).coerceIn(-6.0, 7.5)
        preampLinear = 10.0.pow(netDb / 20.0)
    }

    private fun softLimit(x: Double): Double {
        val ax = abs(x)
        if (ax <= 0.80) return x
        return sign(x) * (0.80 + 0.20 * tanh((ax - 0.80) * 4.0))
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val startPos = inputBuffer.position()
        val limit = inputBuffer.limit()
        val byteCount = limit - startPos
        if (byteCount <= 0) return

        refreshFilters()

        val out = replaceOutputBuffer(byteCount)
        val orbitStep = (2.0 * PI * 0.135) / sampleRate // دورة 360 درجة احترافية كل ~7.4 ثواني
        val maxItdSamples = (sampleRate * 0.00062).coerceIn(8.0, 28.0) // ~0.62ms تأخير حقيقي بين الأذنين

        if (channelCount == 2) {
            var i = startPos
            while (i + 3 < limit) {
                var l = inputBuffer.getShort(i).toDouble() / 32768.0
                var r = inputBuffer.getShort(i + 2).toDouble() / 32768.0

                // 1. معادل 5 قنوات + Sub-Bass
                l = subBassShelf.processLeft(eq14000.processLeft(eq3600.processLeft(eq910.processLeft(eq230.processLeft(eq60.processLeft(l))))))
                r = subBassShelf.processRight(eq14000.processRight(eq3600.processRight(eq910.processRight(eq230.processRight(eq60.processRight(r))))))

                // 2. توسيع المسرح الصوتي (3D Stage)
                if (stereoWidth > 1.01) {
                    val mid = 0.5 * (l + r)
                    val side = 0.5 * (l - r) * stereoWidth
                    l = mid + side
                    r = mid - side
                }

                // 3. محرك الـ 8D الاستوديو الاحترافي (مفروض على كل مخارج الصوت)
                if (is8DActive) {
                    orbitPhase += orbitStep
                    if (orbitPhase >= 2.0 * PI) orbitPhase -= 2.0 * PI

                    val pan = sin(orbitPhase)              // -1 شمال .. +1 يمين
                    val frontBack = cos(orbitPhase)        // +1 أمام .. -1 خلف الرأس
                    val distDepth = 0.86 + 0.14 * frontBack

                    itdBufL[itdWriteIdx] = l
                    itdBufR[itdWriteIdx] = r

                    val dL = (((pan + 1.0) * 0.5) * maxItdSamples).toInt().coerceIn(0, 30)
                    val dR = (((1.0 - pan) * 0.5) * maxItdSamples).toInt().coerceIn(0, 30)

                    val rawL = itdBufL[(itdWriteIdx - dL) and itdMask]
                    val rawR = itdBufR[(itdWriteIdx - dR) and itdMask]
                    itdWriteIdx = (itdWriteIdx + 1) and itdMask

                    // فلتر ظل الرأس HRTF: الأذن البعيدة تفقد الترددات العالية تدريجياً فيشعر المخ بالاتجاه ثلاثي الأبعاد
                    val rearAtten = if (frontBack < 0.0) 0.14 * (-frontBack) else 0.0
                    val alphaL = (1.0 - ((pan + 1.0) * 0.34) - rearAtten).coerceIn(0.24, 1.0)
                    val alphaR = (1.0 - ((1.0 - pan) * 0.34) - rearAtten).coerceIn(0.24, 1.0)
                    shadowL += alphaL * (rawL - shadowL)
                    shadowR += alphaR * (rawR - shadowR)

                    val angle = (pan + 1.0) * (PI / 4.0)
                    val gainL = (0.14 + 0.98 * cos(angle)) * distDepth
                    val gainR = (0.14 + 0.98 * sin(angle)) * distDepth

                    val directL = shadowL * gainL
                    val directR = shadowR * gainR

                    // صدى قاعة الاستوديو (Studio Hall Reverb) يعطي عمقاً حقيقياً على السماعات وحتى سبيكر الموبايل
                    val reverbFeed = 0.45 * (rawL + rawR)
                    val wetL = (combL1.process(reverbFeed, 0.56, 0.36) +
                        combL2.process(reverbFeed, 0.54, 0.38) +
                        combL3.process(reverbFeed, 0.52, 0.40)) * 0.105
                    val wetR = (combR1.process(reverbFeed, 0.56, 0.36) +
                        combR2.process(reverbFeed, 0.54, 0.38) +
                        combR3.process(reverbFeed, 0.52, 0.40)) * 0.105

                    l = directL * 0.84 + wetL
                    r = directR * 0.84 + wetR
                }

                val outL = (softLimit(l * preampLinear) * 32767.0).toInt().coerceIn(-32768, 32767)
                val outR = (softLimit(r * preampLinear) * 32767.0).toInt().coerceIn(-32768, 32767)

                out.putShort(outL.toShort())
                out.putShort(outR.toShort())
                i += 4
            }
        } else {
            var i = startPos
            while (i + 1 < limit) {
                var m = inputBuffer.getShort(i).toDouble() / 32768.0
                m = subBassShelf.processLeft(eq14000.processLeft(eq3600.processLeft(eq910.processLeft(eq230.processLeft(eq60.processLeft(m))))))
                if (is8DActive) {
                    orbitPhase += orbitStep
                    if (orbitPhase >= 2.0 * PI) orbitPhase -= 2.0 * PI
                    val depth = 0.88 + 0.12 * cos(orbitPhase)
                    val wet = (combL1.process(m, 0.54, 0.38) + combL2.process(m, 0.52, 0.40)) * 0.12
                    m = m * depth * 0.86 + wet
                }
                val outM = (softLimit(m * preampLinear) * 32767.0).toInt().coerceIn(-32768, 32767)
                out.putShort(outM.toShort())
                i += 2
            }
        }

        inputBuffer.position(limit)
        out.flip()
    }

    override fun onFlush() {
        eq60.reset(); eq230.reset(); eq910.reset(); eq3600.reset(); eq14000.reset(); subBassShelf.reset()
        itdBufL.fill(0.0); itdBufR.fill(0.0)
        shadowL = 0.0; shadowR = 0.0
        combL1.reset(); combL2.reset(); combL3.reset()
        combR1.reset(); combR2.reset(); combR3.reset()
    }

    override fun onReset() {
        onFlush()
        orbitPhase = 0.0
    }
}
