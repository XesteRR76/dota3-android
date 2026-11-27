package com.dota3.moba.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundCue {
    ATTACK_MELEE,
    ATTACK_RANGED,
    GOLD_COIN,
    DENY,
    SPELL_CAST,
    ULTIMATE,
    BKB_AVATAR,
    LEVEL_UP,
    RUNE_PICKUP,
    TOWER_SHOT,
    HERO_KILL,
    ROSHAN_ROAR,
    BUY_ITEM,
    TP_CHANNEL,
    VICTORY
}

/**
 * Lightweight procedural PCM synthesizer so Dota 3 has crisp tactile SFX
 * without external audio assets. Safe no-op in headless/JVM tests.
 */
object SoundEngine {
    @Volatile
    var enabled: Boolean = true
    @Volatile
    var initialized: Boolean = false

    private val executor = Executors.newSingleThreadExecutor()
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
                SoundCue.ATTACK_MELEE, SoundCue.ATTACK_RANGED -> 90L
                SoundCue.TOWER_SHOT -> 200L
                else -> 60L
            }
            if (now - prev < minGap) return
            lastPlayed[cue] = now
        }
        executor.execute {
            try {
                playSynthesized(cue)
            } catch (_: Throwable) {
                // Ignore audio errors on restricted devices
            }
        }
    }

    private fun playSynthesized(cue: SoundCue) {
        val (fStart, fEnd, durSec, waveType, gain) = when (cue) {
            SoundCue.ATTACK_MELEE -> ToneSpec(240f, 95f, 0.065f, 1, 0.25f)
            SoundCue.ATTACK_RANGED -> ToneSpec(620f, 340f, 0.055f, 0, 0.20f)
            SoundCue.GOLD_COIN -> ToneSpec(1180f, 1660f, 0.11f, 0, 0.30f)
            SoundCue.DENY -> ToneSpec(340f, 180f, 0.09f, 2, 0.28f)
            SoundCue.SPELL_CAST -> ToneSpec(420f, 780f, 0.12f, 0, 0.28f)
            SoundCue.ULTIMATE -> ToneSpec(160f, 55f, 0.32f, 1, 0.45f)
            SoundCue.BKB_AVATAR -> ToneSpec(220f, 440f, 0.26f, 2, 0.40f)
            SoundCue.LEVEL_UP -> ToneSpec(523f, 1046f, 0.24f, 0, 0.36f)
            SoundCue.RUNE_PICKUP -> ToneSpec(660f, 1320f, 0.18f, 0, 0.32f)
            SoundCue.TOWER_SHOT -> ToneSpec(320f, 120f, 0.09f, 1, 0.22f)
            SoundCue.HERO_KILL -> ToneSpec(440f, 880f, 0.28f, 2, 0.40f)
            SoundCue.ROSHAN_ROAR -> ToneSpec(110f, 48f, 0.42f, 1, 0.48f)
            SoundCue.BUY_ITEM -> ToneSpec(780f, 980f, 0.08f, 0, 0.26f)
            SoundCue.TP_CHANNEL -> ToneSpec(510f, 760f, 0.20f, 0, 0.25f)
            SoundCue.VICTORY -> ToneSpec(392f, 784f, 0.50f, 0, 0.45f)
        }

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
            val env = exp(-4.2 * t) * (1.0 - t * 0.2)
            pcm[i] = (raw * env * gain * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
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
        Thread.sleep((durSec * 1000).toLong() + 40L)
        track.stop()
        track.release()
    }

    private data class ToneSpec(
        val fStart: Float,
        val fEnd: Float,
        val durSec: Float,
        val waveType: Int,
        val gain: Float
    )
}
