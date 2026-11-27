package com.dota3.moba.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun distTo(other: Vec2): Float = hypot(other.x - x, other.y - y)
    fun distSqTo(other: Vec2): Float {
        val dx = other.x - x
        val dy = other.y - y
        return dx * dx + dy * dy
    }
    fun angleTo(other: Vec2): Float = atan2(other.y - y, other.x - x)
    fun copyVec(): Vec2 = Vec2(x, y)
    fun set(nx: Float, ny: Float) {
        x = nx
        y = ny
    }
    operator fun plus(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float): Vec2 = Vec2(x * s, y * s)

    fun normalized(): Vec2 {
        val len = sqrt(x * x + y * y)
        return if (len > 1e-5f) Vec2(x / len, y / len) else Vec2(1f, 0f)
    }

    companion object {
        fun fromAngle(rad: Float, length: Float = 1f): Vec2 =
            Vec2(cos(rad) * length, sin(rad) * length)
    }
}

object DotaMath {
    const val MAP_SIZE = 4800f

    // Authentic Dota XP thresholds per level (1..30)
    val XP_FOR_LEVEL: IntArray = intArrayOf(
        0,      // Lv 1
        240,    // Lv 2
        600,    // Lv 3
        1080,   // Lv 4
        1680,   // Lv 5
        2300,   // Lv 6
        2940,   // Lv 7
        3600,   // Lv 8
        4300,   // Lv 9
        5040,   // Lv 10
        5840,   // Lv 11
        6700,   // Lv 12
        7620,   // Lv 13
        8600,   // Lv 14
        9640,   // Lv 15
        10740,  // Lv 16
        11900,  // Lv 17
        13120,  // Lv 18
        14400,  // Lv 19
        15800,  // Lv 20
        17300,  // Lv 21
        18950,  // Lv 22
        20750,  // Lv 23
        22700,  // Lv 24
        24800,  // Lv 25
        28000,  // Lv 26
        31500,  // Lv 27
        35500,  // Lv 28
        40000,  // Lv 29
        45000   // Lv 30
    )

    fun levelForXp(xp: Int): Int {
        var lvl = 1
        for (i in 1 until XP_FOR_LEVEL.size) {
            if (xp >= XP_FOR_LEVEL[i]) lvl = i + 1 else break
        }
        return lvl.coerceIn(1, 30)
    }

    /**
     * Authentic Dota 2/3 armor physical damage multiplier:
     * mult = 1 - (0.06 * armor) / (1 + 0.06 * |armor|)
     */
    fun armorMultiplier(armor: Float): Float {
        val factor = 0.06f * armor
        return (1f - factor / (1f + abs(factor))).coerceIn(0.05f, 2.0f)
    }

    /**
     * Magic damage multiplier given magic resistance in [0..0.95]
     */
    fun magicMultiplier(magicResist: Float): Float {
        return (1f - magicResist.coerceIn(-0.5f, 0.95f))
    }

    fun clamp(v: Float, minV: Float, maxV: Float): Float =
        if (v < minV) minV else if (v > maxV) maxV else v

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun distPointToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
        val cx = ax + abx * t
        val cy = ay + aby * t
        return hypot(px - cx, py - cy)
    }
}
