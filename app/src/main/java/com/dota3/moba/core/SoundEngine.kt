package com.dota3.moba.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class SoundCue {
    ATTACK_MELEE,
    ATTACK_RANGED,
    CRIT_HIT,
    GOLD_COIN,
    DENY,
    SPELL_CAST,
    ULTIMATE,
    BKB_AVATAR,
    LEVEL_UP,
    RUNE_PICKUP,
    TOWER_SHOT,
    TOWER_DESTROY,
    HERO_KILL,
    FIRST_BLOOD,
    KILL_SPREE,
    GODLIKE,
    ROSHAN_ROAR,
    BUY_ITEM,
    TP_CHANNEL,
    TACTICAL_PING,
    VICTORY
}

/**
 * Multi-layered procedural PCM synthesizer delivering punchy Dota impact,
 * sub-bass ultimates, crisp crit slashes, coin chimes, and killstreak horns.
 * Safe no-op in headless JVM unit tests.
 */
object SoundEngine {
    @Volatile
    var enabled: Boolean = true
    @Volatile
    var initialized: Boolean = false

    private val executor = Executors.newFixedThreadPool(6) { r ->
        Thread(r, "dota3-sfx").apply { isDaemon = true }
    }
    private val lastPlayed = HashMap<SoundCue, Long>()
    private const val SAMPLE_RATE = 22050

    fun init() {
        initialized = true
    }

    fun play(cue: SoundCue) {
        if (!enabled || !initialized) return
        val now = System.currentTimeMillis()
        synchronized(lastPlayed) {
            val prev = lastPlayed[cue] ?: 0L
            val minGap = when (cue) {
                SoundCue.ATTACK_MELEE, SoundCue.ATTACK_RANGED -> 85L
                SoundCue.CRIT_HIT -> 120L
                SoundCue.TOWER_SHOT -> 180L
                else -> 55L
            }
            if (now - prev < minGap) return
            lastPlayed[cue] = now
        }
        val enqueuedAt = now
        executor.execute {
            if (System.currentTimeMillis() - enqueuedAt > 180L) return@execute
            try {
                playSynthesized(cue)
            } catch (_: Throwable) {
                // Ignore audio errors on restricted devices
            }
        }
    }

    private fun playSynthesized(cue: SoundCue) {
        val pcm: ShortArray = when (cue) {
            SoundCue.ATTACK_MELEE -> synthImpact(195f, 65f, 0.085f, noiseMix = 0.42f, subMix = 0.55f, gain = 0.36f)
            SoundCue.ATTACK_RANGED -> synthImpact(680f, 240f, 0.065f, noiseMix = 0.28f, subMix = 0.15f, gain = 0.25f)
            SoundCue.CRIT_HIT -> synthCritSlash()
            SoundCue.GOLD_COIN -> synthArpeggio(floatArrayOf(1174f, 1568f), 0.075f, 0.34f)
            SoundCue.DENY -> synthTone(380f, 175f, 0.10f, 2, 0.30f)
            SoundCue.SPELL_CAST -> synthImpact(420f, 820f, 0.13f, noiseMix = 0.20f, subMix = 0.25f, gain = 0.30f)
            SoundCue.ULTIMATE -> synthImpact(145f, 44f, 0.38f, noiseMix = 0.35f, subMix = 0.78f, gain = 0.52f)
            SoundCue.BKB_AVATAR -> synthArpeggio(floatArrayOf(220f, 330f, 440f), 0.10f, 0.44f)
            SoundCue.LEVEL_UP -> synthArpeggio(floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f), 0.08f, 0.38f)
            SoundCue.RUNE_PICKUP -> synthArpeggio(floatArrayOf(659f, 987f, 1318f), 0.075f, 0.34f)
            SoundCue.TOWER_SHOT -> synthImpact(280f, 95f, 0.10f, noiseMix = 0.22f, subMix = 0.45f, gain = 0.26f)
            SoundCue.TOWER_DESTROY -> synthImpact(115f, 36f, 0.52f, noiseMix = 0.52f, subMix = 0.85f, gain = 0.54f)
            SoundCue.HERO_KILL -> synthBrassChord(floatArrayOf(220f, 277.18f, 329.63f, 440f), 0.36f, 0.44f)
            SoundCue.FIRST_BLOOD -> synthBrassChord(floatArrayOf(146.83f, 220f, 293.66f, 440f), 0.55f, 0.50f)
            SoundCue.KILL_SPREE -> synthArpeggio(floatArrayOf(392f, 493.88f, 587.33f, 783.99f), 0.10f, 0.46f)
            SoundCue.GODLIKE -> synthArpeggio(floatArrayOf(329.63f, 440f, 554.37f, 659.25f, 880f), 0.11f, 0.52f)
            SoundCue.ROSHAN_ROAR -> synthImpact(92f, 34f, 0.55f, noiseMix = 0.48f, subMix = 0.90f, gain = 0.56f)
            SoundCue.BUY_ITEM -> synthArpeggio(floatArrayOf(784f, 1046f), 0.055f, 0.28f)
            SoundCue.TP_CHANNEL -> synthTone(480f, 840f, 0.22f, 0, 0.26f)
            SoundCue.TACTICAL_PING -> synthArpeggio(floatArrayOf(880f, 1174f), 0.06f, 0.32f)
            SoundCue.VICTORY -> synthBrassChord(floatArrayOf(261.63f, 329.63f, 392f, 523.25f, 659.25f), 0.65f, 0.50f)
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        val ms = (pcm.size * 1000L / SAMPLE_RATE) + 45L
        Thread.sleep(ms)
        track.stop()
        track.release()
    }

    private fun synthTone(fStart: Float, fEnd: Float, durSec: Float, waveType: Int, gain: Float): ShortArray {
        val count = (SAMPLE_RATE * durSec).toInt().coerceAtLeast(128)
        val pcm = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = fStart + (fEnd - fStart) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val raw = when (waveType) {
                0 -> sin(phase)
                1 -> if (sin(phase) >= 0.0) 0.7 else -0.7
                else -> (2.0 * (phase / (2.0 * PI) % 1.0) - 1.0) * 0.7
            }
            val env = exp(-4.0 * t) * (1.0 - t * 0.2)
            pcm[i] = toSample(raw * env * gain)
        }
        return pcm
    }

    private fun synthImpact(
        fStart: Float,
        fEnd: Float,
        durSec: Float,
        noiseMix: Float,
        subMix: Float,
        gain: Float
    ): ShortArray {
        val count = (SAMPLE_RATE * durSec).toInt().coerceAtLeast(128)
        val pcm = ShortArray(count)
        var phase = 0.0
        var subPhase = 0.0
        var rng = 0x517cc1b7
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = fStart + (fEnd - fStart) * (t * 0.8f)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            subPhase += 2.0 * PI * (freq * 0.5) / SAMPLE_RATE
            rng = rng * 1664525 + 1013904223
            val noise = ((rng ushr 16) and 0xFFFF) / 32768.0 - 1.0
            val noiseEnv = exp(-18.0 * t)
            val body = sin(phase) * (1.0 - subMix * 0.4) + sin(subPhase) * subMix
            val mixed = tanh((body + noise * noiseMix * noiseEnv) * 1.4)
            val env = exp(-4.8 * t) * (1.0 - t * 0.15)
            pcm[i] = toSample(mixed * env * gain)
        }
        return pcm
    }

    private fun synthCritSlash(): ShortArray {
        val count = (SAMPLE_RATE * 0.19f).toInt()
        val pcm = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        var pSub = 0.0
        var rng = 0x1234567
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val f = 920f - 540f * t
            p1 += 2.0 * PI * f / SAMPLE_RATE
            p2 += 2.0 * PI * (f * 1.52) / SAMPLE_RATE
            pSub += 2.0 * PI * (120.0 - 65.0 * t) / SAMPLE_RATE
            rng = rng * 1664525 + 1013904223
            val noise = (((rng ushr 16) and 0xFFFF) / 32768.0 - 1.0) * exp(-16.0 * t)
            val metallic = (sin(p1) * 0.55 + sin(p2) * 0.45) * exp(-7.5 * t)
            val punch = sin(pSub) * exp(-9.0 * t) * 0.8
            pcm[i] = toSample(tanh((metallic + punch + noise * 0.55) * 1.35) * 0.46)
        }
        return pcm
    }

    private fun synthArpeggio(notes: FloatArray, noteDur: Float, gain: Float): ShortArray {
        val perNote = (SAMPLE_RATE * noteDur).toInt().coerceAtLeast(128)
        val tail = (SAMPLE_RATE * 0.10f).toInt()
        val pcm = ShortArray(perNote * notes.size + tail)
        for (ni in notes.indices) {
            val f = notes[ni]
            val start = ni * perNote
            val len = perNote + tail
            var phase = 0.0
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= pcm.size) break
                val t = i.toFloat() / len
                phase += 2.0 * PI * f / SAMPLE_RATE
                val wave = sin(phase) * 0.72 + sin(phase * 2.0) * 0.28
                val env = (1.0 - exp(-28.0 * t)) * exp(-4.8 * t)
                val cur = pcm[idx] / Short.MAX_VALUE.toDouble()
                pcm[idx] = toSample(cur + wave * env * gain)
            }
        }
        return pcm
    }

    private fun synthBrassChord(freqs: FloatArray, durSec: Float, gain: Float): ShortArray {
        val count = (SAMPLE_RATE * durSec).toInt().coerceAtLeast(256)
        val pcm = ShortArray(count)
        val phases = DoubleArray(freqs.size)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            var sum = 0.0
            for (fi in freqs.indices) {
                phases[fi] += 2.0 * PI * freqs[fi] / SAMPLE_RATE
                val saw = 2.0 * (phases[fi] / (2.0 * PI) % 1.0) - 1.0
                sum += (sin(phases[fi]) * 0.65 + saw * 0.35) / freqs.size
            }
            val attack = (t / 0.08).coerceIn(0.0, 1.0)
            val env = attack * exp(-3.2 * t)
            pcm[i] = toSample(tanh(sum * 1.8) * env * gain)
        }
        return pcm
    }

    private fun toSample(v: Double): Short =
        (v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}
