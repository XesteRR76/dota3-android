package com.dota3.moba.render

import com.dota3.moba.core.Vec2
import kotlin.math.cos
import kotlin.math.sin

data class BoxBounds(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    fun set(l: Float, t: Float, r: Float, b: Float) {
        left = l; top = t; right = r; bottom = b
    }
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) * 0.5f
    fun centerY(): Float = (top + bottom) * 0.5f
}

/**
 * Pure Kotlin MLBB-style HUD geometry layout calculator (unit-testable across resolutions).
 */
class HudGeometry {
    val minimap = BoxBounds()
    val camBtn = BoxBounds()
    val quickBuy = Array(2) { BoxBounds() }

    val shopBtn = BoxBounds()
    val talentBtn = BoxBounds()
    val statsBtn = BoxBounds()
    val pauseBtn = BoxBounds()

    val heroPlate = BoxBounds()
    val itemSlots = Array(6) { BoxBounds() }
    val buybackBtn = BoxBounds()

    val attackCenter = Vec2()
    var attackRadius = 56f
    val minionCenter = Vec2()
    val towerDenyCenter = Vec2()
    var subRadius = 30f

    val abilityCenters = Array(4) { Vec2() }
    var abilityRadius = 36f
    val levelUpPills = Array(4) { BoxBounds() }

    val tpCenter = Vec2()
    val courierCenter = Vec2()
    val glyphCenter = Vec2()
    var utilRadius = 26f

    val cancelCenter = Vec2()
    var cancelRadius = 42f

    var joystickRestCenter = Vec2()
    var joystickRadius = 64f

    fun layout(w: Float, h: Float) {
        val pad = w * 0.014f
        val mmSize = (h * 0.25f).coerceIn(115f, 220f)
        minimap.set(pad, pad, pad + mmSize, pad + mmSize)
        camBtn.set(pad, minimap.bottom + 6f, pad + mmSize, minimap.bottom + 6f + (h * 0.042f).coerceIn(24f, 44f))

        // 2 MLBB Quick-Buy Item Cards cleanly beside Minimap (leaves bottom-left 100% free for Joystick!)
        val qbLeft = minimap.right + 12f
        val qbW = (w * 0.115f).coerceIn(116f, 230f)
        val qbH = (h * 0.058f).coerceIn(34f, 60f)
        quickBuy[0].set(qbLeft, pad, qbLeft + qbW, pad + qbH)
        quickBuy[1].set(qbLeft, pad + qbH + 6f, qbLeft + qbW, pad + qbH * 2f + 6f)

        // Top Right Menu Buttons
        val btnW = (w * 0.072f).coerceIn(66f, 136f)
        val btnH = (h * 0.062f).coerceIn(34f, 60f)
        pauseBtn.set(w - pad - btnW * 0.85f, pad, w - pad, pad + btnH)
        statsBtn.set(pauseBtn.left - 8f - btnW, pad, pauseBtn.left - 8f, pad + btnH)
        talentBtn.set(statsBtn.left - 8f - btnW * 1.1f, pad, statsBtn.left - 8f, pad + btnH)
        shopBtn.set(talentBtn.left - 8f - btnW * 1.35f, pad, talentBtn.left - 8f, pad + btnH)

        // MLBB Right-Thumb Ergonomic Arc (angles in 190..268 go UP-LEFT in +Y-down screen space!)
        attackRadius = (h * 0.080f).coerceIn(46f, 90f)
        attackCenter.set(w - pad - attackRadius * 1.15f, h - pad - attackRadius * 1.15f)

        subRadius = attackRadius * 0.54f
        minionCenter.set(attackCenter.x - attackRadius * 1.68f, attackCenter.y + attackRadius * 0.16f)
        towerDenyCenter.set(attackCenter.x + attackRadius * 0.08f, attackCenter.y - attackRadius * 1.68f)

        abilityRadius = attackRadius * 0.62f
        val arcR = attackRadius * 3.25f
        val anglesDeg = floatArrayOf(190f, 216f, 242f, 268f)
        val pillH = (h * 0.030f).coerceIn(18f, 32f)
        for (i in 0..3) {
            val rad = Math.toRadians(anglesDeg[i].toDouble()).toFloat()
            val cx = attackCenter.x + cos(rad) * arcR
            val cy = attackCenter.y + sin(rad) * arcR
            abilityCenters[i].set(cx, cy)
            levelUpPills[i].set(cx - abilityRadius * 0.65f, cy - abilityRadius - pillH - 3f, cx + abilityRadius * 0.65f, cy - abilityRadius - 3f)
        }

        // Cancel X zone neatly in upper-right below menu buttons
        cancelRadius = (h * 0.054f).coerceIn(32f, 54f)
        cancelCenter.set(w - pad - cancelRadius * 1.1f, shopBtn.bottom + cancelRadius * 1.4f)

        // Utility circles (TP, Courier, Glyph) to the left of Q without overlapping Q or CREEP
        utilRadius = attackRadius * 0.44f
        val utilStartX = abilityCenters[0].x - abilityRadius - utilRadius - 14f
        val utilY = h - pad - utilRadius
        tpCenter.set(utilStartX, utilY)
        courierCenter.set(utilStartX - utilRadius * 2.30f, utilY)
        glyphCenter.set(utilStartX - utilRadius * 4.60f, utilY)

        // Left Joystick anchor zone
        joystickRadius = (h * 0.095f).coerceIn(52f, 102f)
        joystickRestCenter.set(pad + joystickRadius * 1.35f, h - pad - joystickRadius * 1.35f)

        // Bottom-center 6 Active Item Slots + Hero HP/MP Status Plate
        val barLeft = (joystickRestCenter.x + joystickRadius + 22f)
        val barRight = (glyphCenter.x - utilRadius - 16f).coerceAtLeast(barLeft + 180f)
        val availW = barRight - barLeft
        val itemSize = ((availW - 25f) / 6f).coerceIn(34f, (h * 0.072f).coerceIn(38f, 70f))
        val itemsTotalW = itemSize * 6f + 5f * 5f
        val itemStartX = barLeft + (availW - itemsTotalW) * 0.5f
        val itemY = h - pad - itemSize
        for (i in 0..5) {
            val l = itemStartX + i * (itemSize + 5f)
            itemSlots[i].set(l, itemY, l + itemSize, itemY + itemSize)
        }

        val plateH = (h * 0.078f).coerceIn(42f, 78f)
        heroPlate.set(itemStartX, itemY - plateH - 6f, itemStartX + itemsTotalW, itemY - 6f)

        buybackBtn.set(w * 0.40f, h * 0.62f, w * 0.60f, h * 0.72f)
    }
}
