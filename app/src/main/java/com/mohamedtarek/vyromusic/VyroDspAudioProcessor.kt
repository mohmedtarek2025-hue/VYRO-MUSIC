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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// ==================== LOCK-FREE REAL-TIME DSP CONTROLLER ====================

object VyroDspController {
    @Volatile var band0: Float = 0.20f
    @Volatile var band1: Float = 0.10f
    @Volatile var band2: Float = 0.00f
    @Volatile var band3: Float = 0.12f
    @Volatile var band4: Float = 0.20f

    @Volatile var bassStrength: Int = 35        // Sub-Bass (0..100)
    @Volatile var virtualizerStrength: Int = 30 // 3D Stage (0..100)
    @Volatile var loudnessGain: Int = 0         // Mega Amp / الصوت المضخم (0..100)
    @Volatile var luxuryTone: Int = 0           // Luxury Warmth & Air / الصوت الفخم (0..100)
    @Volatile var vocalClarity: Int = 0         // Vocal Presence / نقاء الصوت البشري (0..100)
    @Volatile var spatial8DEnabled: Boolean = false
    @Volatile var version: Int = 1

    fun syncFromPrefs(prefs: SharedPreferences) {
        val gains = VyroSafePrefs.getBandGains(prefs)
        band0 = gains.getOrElse(0) { 0.20f }.coerceIn(-1f, 1f)
        band1 = gains.getOrElse(1) { 0.10f }.coerceIn(-1f, 1f)
        band2 = gains.getOrElse(2) { 0.00f }.coerceIn(-1f, 1f)
        band3 = gains.getOrElse(3) { 0.12f }.coerceIn(-1f, 1f)
        band4 = gains.getOrElse(4) { 0.20f }.coerceIn(-1f, 1f)
        bassStrength = VyroSafePrefs.getInt(prefs, "bass_strength", 35).coerceIn(0, 100)
        virtualizerStrength = VyroSafePrefs.getInt(prefs, "virtualizer_strength", 30).coerceIn(0, 100)
        loudnessGain = VyroSafePrefs.getInt(prefs, "loudness_gain", 0).coerceIn(0, 100)
        luxuryTone = VyroSafePrefs.getInt(prefs, "luxury_tone", 0).coerceIn(0, 100)
        vocalClarity = VyroSafePrefs.getInt(prefs, "vocal_clarity", 0).coerceIn(0, 100)
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
        if (abs(gainDb) < 0.05 || freq >= sampleRate * 0.46) {
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

    fun setHighShelf(sampleRate: Double, freq: Double, gainDb: Double) {
        if (abs(gainDb) < 0.05 || freq >= sampleRate * 0.46) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * 0.7071)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val nb0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val nb2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val na0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val na2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

        if (abs(na0) < 1e-9) return
        b0 = nb0 / na0
        b1 = nb1 / na0
        b2 = nb2 / na0
        a1 = na1 / na0
        a2 = na2 / na0
    }

    fun setHighPass(sampleRate: Double, freq: Double, q: Double = 0.7071) {
        if (freq <= 5.0 || freq >= sampleRate * 0.45) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        val nb0 = (1.0 + cosW0) * 0.5
        val nb1 = -(1.0 + cosW0)
        val nb2 = (1.0 + cosW0) * 0.5
        val na0 = 1.0 + alpha
        val na1 = -2.0 * cosW0
        val na2 = 1.0 - alpha

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

// ==================== VYRO STUDIO PRO ZERO-DISTORTION PCM PROCESSOR ====================

@OptIn(UnstableApi::class)
class VyroDspAudioProcessor : BaseAudioProcessor() {

    // فلتر تنظيف الترددات تحت السمعية (28Hz) لإزالة الطنين والغروشة
    private val subSonicHp = StereoBiquad()

    // معادل 5 قنوات نقي
    private val eq60 = StereoBiquad()
    private val eq230 = StereoBiquad()
    private val eq910 = StereoBiquad()
    private val eq3600 = StereoBiquad()
    private val eq14000 = StereoBiquad()

    // فلاتر النكهة الاحترافية (Sub-Bass + Luxury Warmth + Luxury Air + Vocal Clarity)
    private val subBassShelf = StereoBiquad()
    private val luxuryWarmth = StereoBiquad()
    private val luxuryAir = StereoBiquad()
    private val vocalPresence = StereoBiquad()

    private var sampleRate = 44100.0
    private var channelCount = 2
    private var appliedVersion = -1

    // محرك الـ 8D البينورال (HRTF Head-Shadow + ITD Delay + Clean Hall Reverb)
    private var orbitPhase = 0.0
    private val itdSize = 64
    private val itdMask = itdSize - 1
    private val itdBufL = DoubleArray(itdSize)
    private val itdBufR = DoubleArray(itdSize)
    private var itdWriteIdx = 0

    private var shadowL = 0.0
    private var shadowR = 0.0
    private var reverbLowCutState = 0.0

    private val combL1 = CombReverb(1117)
    private val combL2 = CombReverb(1357)
    private val combL3 = CombReverb(1619)
    private val combR1 = CombReverb(1141)
    private val combR2 = CombReverb(1381)
    private val combR3 = CombReverb(1643)

    // مُحدّد القمم الديناميكي لمنع التشويه والغروشة 100%
    private var limiterEnv = 0.0
    private var attackCoeff = 0.97
    private var releaseCoeff = 0.9998

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

        attackCoeff = exp(-1.0 / (sampleRate * 0.0007))
        releaseCoeff = exp(-1.0 / (sampleRate * 0.145))
        subSonicHp.setHighPass(sampleRate, 28.0, 0.7071)

        appliedVersion = -1
        refreshFilters()
        return inputAudioFormat
    }

    private fun refreshFilters() {
        val v = VyroDspController.version
        if (appliedVersion == v) return
        appliedVersion = v

        // مدى موسيقي متوازن (±7.5 dB) يعطي فرقاً واضحاً بدون غروشة
        eq60.setPeaking(sampleRate, 62.0, 0.95, VyroDspController.band0 * 7.5)
        eq230.setPeaking(sampleRate, 230.0, 1.0, VyroDspController.band1 * 7.0)
        eq910.setPeaking(sampleRate, 910.0, 1.0, VyroDspController.band2 * 6.5)
        eq3600.setPeaking(sampleRate, 3600.0, 1.0, VyroDspController.band3 * 7.0)
        eq14000.setPeaking(sampleRate, 13500.0, 0.85, VyroDspController.band4 * 7.5)

        // Sub-Bass عميق ونظيف على 68Hz (بحد أقصى +6.5 dB لتجنب تضاعف التشويه مع 60Hz)
        val bassDb = (VyroDspController.bassStrength / 100.0) * 6.5
        subBassShelf.setLowShelf(sampleRate, 68.0, bassDb)

        // الصوت الفخم (Luxury Tone): دفء مخملي على 135Hz + لمعة هوائية فاخرة على 10kHz
        val luxRatio = VyroDspController.luxuryTone / 100.0
        luxuryWarmth.setPeaking(sampleRate, 135.0, 0.75, luxRatio * 3.8)
        luxuryAir.setHighShelf(sampleRate, 9800.0, luxRatio * 4.5)

        // نقاء الصوت البشري (Vocal Clarity): إبراز ناعم على 2.5kHz بدون حدّة مزعجة
        val vocalDb = (VyroDspController.vocalClarity / 100.0) * 5.2
        vocalPresence.setPeaking(sampleRate, 2500.0, 1.15, vocalDb)

        is8DActive = VyroDspController.spatial8DEnabled
        val effectiveVirt = if (is8DActive) maxOf(VyroDspController.virtualizerStrength, 78) else VyroDspController.virtualizerStrength
        stereoWidth = 1.0 + (effectiveVirt / 100.0) * 0.72

        // موازنة Headroom تلقائية ذكية + الصوت المضخم (Mega Amp)
        val maxEqBoost = maxOf(
            VyroDspController.band0,
            VyroDspController.band1,
            VyroDspController.band2,
            VyroDspController.band3,
            VyroDspController.band4,
            0f
        ) * 7.5
        val totalPositiveStack = (maxEqBoost + bassDb * 0.75 + luxRatio * 3.2 + vocalDb * 0.45)
        val autoHeadroomDrop = (totalPositiveStack * 0.52).coerceIn(0.0, 9.5)
        val megaAmpDb = (VyroDspController.loudnessGain / 100.0) * 6.0

        val netDb = (megaAmpDb - autoHeadroomDrop).coerceIn(-8.5, 5.5)
        preampLinear = 10.0.pow(netDb / 20.0)
    }

    private fun safetySoftClip(x: Double): Double {
        val ax = abs(x)
        if (ax <= 0.93) return x
        return sign(x) * (0.93 + 0.065 * tanh((ax - 0.93) * 11.0))
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val startPos = inputBuffer.position()
        val limit = inputBuffer.limit()
        val byteCount = limit - startPos
        if (byteCount <= 0) return

        refreshFilters()

        val out = replaceOutputBuffer(byteCount)
        val orbitStep = (2.0 * PI * 0.135) / sampleRate
        val maxItdSamples = (sampleRate * 0.00060).coerceIn(8.0, 26.0)
        val ceiling = 0.85

        if (channelCount == 2) {
            var i = startPos
            while (i + 3 < limit) {
                var l = inputBuffer.getShort(i).toDouble() / 32768.0
                var r = inputBuffer.getShort(i + 2).toDouble() / 32768.0

                // 1. فلتر التنظيف تحت السمعي 28Hz (يمنع تشويه السماعات)
                l = subSonicHp.processLeft(l)
                r = subSonicHp.processRight(r)

                // 2. معادل 5 قنوات + Sub-Bass + الصوت الفخم + نقاء الصوت البشري
                l = eq14000.processLeft(eq3600.processLeft(eq910.processLeft(eq230.processLeft(eq60.processLeft(l)))))
                r = eq14000.processRight(eq3600.processRight(eq910.processRight(eq230.processRight(eq60.processRight(r)))))

                l = vocalPresence.processLeft(luxuryAir.processLeft(luxuryWarmth.processLeft(subBassShelf.processLeft(l))))
                r = vocalPresence.processRight(luxuryAir.processRight(luxuryWarmth.processRight(subBassShelf.processRight(r))))

                // 3. توسيع المسرح الصوتي 3D Stage (بحدود نظيفة)
                if (stereoWidth > 1.01) {
                    val mid = 0.5 * (l + r)
                    val side = 0.5 * (l - r) * stereoWidth
                    l = mid + side
                    r = mid - side
                }

                // 4. محرك الصوت المحيطي 8D الاحترافي (HRTF + ITD + Clean Reverb)
                if (is8DActive) {
                    orbitPhase += orbitStep
                    if (orbitPhase >= 2.0 * PI) orbitPhase -= 2.0 * PI

                    val pan = sin(orbitPhase)
                    val frontBack = cos(orbitPhase)
                    val distDepth = 0.87 + 0.13 * frontBack

                    itdBufL[itdWriteIdx] = l
                    itdBufR[itdWriteIdx] = r

                    val dL = (((pan + 1.0) * 0.5) * maxItdSamples).toInt().coerceIn(0, 28)
                    val dR = (((1.0 - pan) * 0.5) * maxItdSamples).toInt().coerceIn(0, 28)

                    val rawL = itdBufL[(itdWriteIdx - dL) and itdMask]
                    val rawR = itdBufR[(itdWriteIdx - dR) and itdMask]
                    itdWriteIdx = (itdWriteIdx + 1) and itdMask

                    val rearAtten = if (frontBack < 0.0) 0.13 * (-frontBack) else 0.0
                    val alphaL = (1.0 - ((pan + 1.0) * 0.32) - rearAtten).coerceIn(0.28, 1.0)
                    val alphaR = (1.0 - ((1.0 - pan) * 0.32) - rearAtten).coerceIn(0.28, 1.0)
                    shadowL += alphaL * (rawL - shadowL)
                    shadowR += alphaR * (rawR - shadowR)

                    val angle = (pan + 1.0) * (PI / 4.0)
                    val gainL = (0.16 + 0.94 * cos(angle)) * distDepth
                    val gainR = (0.16 + 0.94 * sin(angle)) * distDepth

                    val directL = shadowL * gainL
                    val directR = shadowR * gainR

                    // تنظيف إشارة الصدى من البيز الثقيل حتى يظل الـ 8D صافياً بلا طنين
                    val monoFeed = 0.42 * (rawL + rawR)
                    reverbLowCutState += 0.035 * (monoFeed - reverbLowCutState)
                    val cleanRevFeed = monoFeed - reverbLowCutState

                    val wetL = (combL1.process(cleanRevFeed, 0.50, 0.40) +
                        combL2.process(cleanRevFeed, 0.48, 0.42) +
                        combL3.process(cleanRevFeed, 0.46, 0.44)) * 0.095
                    val wetR = (combR1.process(cleanRevFeed, 0.50, 0.40) +
                        combR2.process(cleanRevFeed, 0.48, 0.42) +
                        combR3.process(cleanRevFeed, 0.46, 0.44)) * 0.095

                    l = directL * 0.86 + wetL
                    r = directR * 0.86 + wetR
                }

                // 5. تقوية + مُحدّد قمم ديناميكي (Dynamic Envelope Limiter) يمنع الغروشة تماماً
                l *= preampLinear
                r *= preampLinear

                val peak = maxOf(abs(l), abs(r))
                limiterEnv = if (peak > limiterEnv) {
                    attackCoeff * limiterEnv + (1.0 - attackCoeff) * peak
                } else {
                    releaseCoeff * limiterEnv + (1.0 - releaseCoeff) * peak
                }
                val dynGain = if (limiterEnv > ceiling) ceiling / limiterEnv else 1.0

                val outL = (safetySoftClip(l * dynGain) * 32767.0).toInt().coerceIn(-32768, 32767)
                val outR = (safetySoftClip(r * dynGain) * 32767.0).toInt().coerceIn(-32768, 32767)

                out.putShort(outL.toShort())
                out.putShort(outR.toShort())
                i += 4
            }
        } else {
            var i = startPos
            while (i + 1 < limit) {
                var m = inputBuffer.getShort(i).toDouble() / 32768.0
                m = subSonicHp.processLeft(m)
                m = eq14000.processLeft(eq3600.processLeft(eq910.processLeft(eq230.processLeft(eq60.processLeft(m)))))
                m = vocalPresence.processLeft(luxuryAir.processLeft(luxuryWarmth.processLeft(subBassShelf.processLeft(m))))
                if (is8DActive) {
                    orbitPhase += orbitStep
                    if (orbitPhase >= 2.0 * PI) orbitPhase -= 2.0 * PI
                    val depth = 0.88 + 0.12 * cos(orbitPhase)
                    reverbLowCutState += 0.035 * (m - reverbLowCutState)
                    val cleanRev = m - reverbLowCutState
                    val wet = (combL1.process(cleanRev, 0.48, 0.42) + combL2.process(cleanRev, 0.46, 0.44)) * 0.10
                    m = m * depth * 0.88 + wet
                }
                m *= preampLinear
                val peak = abs(m)
                limiterEnv = if (peak > limiterEnv) {
                    attackCoeff * limiterEnv + (1.0 - attackCoeff) * peak
                } else {
                    releaseCoeff * limiterEnv + (1.0 - releaseCoeff) * peak
                }
                val dynGain = if (limiterEnv > ceiling) ceiling / limiterEnv else 1.0
                val outM = (safetySoftClip(m * dynGain) * 32767.0).toInt().coerceIn(-32768, 32767)
                out.putShort(outM.toShort())
                i += 2
            }
        }

        inputBuffer.position(limit)
        out.flip()
    }

    override fun onFlush() {
        subSonicHp.reset()
        eq60.reset(); eq230.reset(); eq910.reset(); eq3600.reset(); eq14000.reset()
        subBassShelf.reset(); luxuryWarmth.reset(); luxuryAir.reset(); vocalPresence.reset()
        itdBufL.fill(0.0); itdBufR.fill(0.0)
        shadowL = 0.0; shadowR = 0.0; reverbLowCutState = 0.0
        limiterEnv = 0.0
        combL1.reset(); combL2.reset(); combL3.reset()
        combR1.reset(); combR2.reset(); combR3.reset()
    }

    override fun onReset() {
        onFlush()
        orbitPhase = 0.0
    }
}
