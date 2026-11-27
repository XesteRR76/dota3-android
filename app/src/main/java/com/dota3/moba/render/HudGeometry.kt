package com.dota3.moba.render

import com.dota3.moba.core.Vec2
import kotlin.math.abs
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
    fun overlaps(other: BoxBounds): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

/**
 * Pure Kotlin Dota 2 Bottom Console + MLBB Ergonomic Right-Thumb Skill Arc layout calculator.
 */
class HudGeometry {
    val minimap = BoxBounds()
    val camBtn = BoxBounds()
    val zoomBtn = BoxBounds()
    val quickBuy = Array(2) { BoxBounds() }

    val shopBtn = BoxBounds()
    val talentBtn = BoxBounds()
    val statsBtn = BoxBounds()
    val pauseBtn = BoxBounds()

    // Authentic Dota 2 Bottom Hero Console
    val heroConsole = BoxBounds()
    val portraitBox = BoxBounds()
    val xpBarBox = BoxBounds()
    val attrBox = BoxBounds()
    val talentHudBtn = BoxBounds()
    val heroPlate = BoxBounds()
    val itemSlots = Array(6) { BoxBounds() }
    val tpSlot = BoxBounds()
    val neutralSlot = BoxBounds()
    val buybackBtn = BoxBounds()

    // MLBB Right-Thumb Attack & Skill Fan
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
        val pad = (w * 0.012f).coerceIn(12f, 28f)
        val mmSize = (h * 0.235f).coerceIn(112f, 224f)
        minimap.set(pad, pad, pad + mmSize, pad + mmSize)
        val camH = (h * 0.042f).coerceIn(24f, 44f)
        camBtn.set(pad, minimap.bottom + 6f, pad + mmSize * 0.62f, minimap.bottom + 6f + camH)
        zoomBtn.set(camBtn.right + 5f, minimap.bottom + 6f, pad + mmSize, minimap.bottom + 6f + camH)

        // 2 MLBB Quick-Buy Item Cards cleanly beside Minimap
        val qbLeft = minimap.right + 10f
        val qbW = (w * 0.115f).coerceIn(112f, 230f)
        val qbH = (h * 0.056f).coerceIn(32f, 58f)
        quickBuy[0].set(qbLeft, pad, qbLeft + qbW, pad + qbH)
        quickBuy[1].set(qbLeft, pad + qbH + 6f, qbLeft + qbW, pad + qbH * 2f + 6f)

        // Top Right Menu Buttons
        val btnW = (w * 0.072f).coerceIn(66f, 136f)
        val btnH = (h * 0.060f).coerceIn(34f, 58f)
        pauseBtn.set(w - pad - btnW * 0.85f, pad, w - pad, pad + btnH)
        statsBtn.set(pauseBtn.left - 8f - btnW, pad, pauseBtn.left - 8f, pad + btnH)
        talentBtn.set(statsBtn.left - 8f - btnW * 1.1f, pad, statsBtn.left - 8f, pad + btnH)
        shopBtn.set(talentBtn.left - 8f - btnW * 1.35f, pad, talentBtn.left - 8f, pad + btnH)

        // MLBB Ergonomic Right-Thumb Fan (reachable arc from Q at 188° to R at 266°)
        attackRadius = (h * 0.078f).coerceIn(45f, 88f)
        attackCenter.set(w - pad - attackRadius * 1.15f, h - pad - attackRadius * 1.15f)

        subRadius = attackRadius * 0.50f
        val subDist = attackRadius * 1.62f
        val minionRad = Math.toRadians(173.0).toFloat()
        val towerRad = Math.toRadians(274.0).toFloat()
        minionCenter.set(attackCenter.x + cos(minionRad) * subDist, attackCenter.y + sin(minionRad) * subDist)
        towerDenyCenter.set(attackCenter.x + cos(towerRad) * subDist, attackCenter.y + sin(towerRad) * subDist)

        abilityRadius = attackRadius * 0.58f
        val arcR = attackRadius * 3.04f
        val anglesDeg = floatArrayOf(188f, 214f, 240f, 266f)
        val pillW = abilityRadius * 1.18f
        val pillH = (h * 0.030f).coerceIn(19f, 32f)
        for (i in 0..3) {
            val rad = Math.toRadians(anglesDeg[i].toDouble()).toFloat()
            val cx = attackCenter.x + cos(rad) * arcR
            val cy = attackCenter.y + sin(rad) * arcR
            abilityCenters[i].set(cx, cy)

            val edgeOffset = abs(cos(rad)) * (pillW * 0.5f) + abs(sin(rad)) * (pillH * 0.5f)
            val pDist = abilityRadius + edgeOffset + 6f
            val px = (cx + cos(rad) * pDist).coerceIn(pillW * 0.5f + 4f, w - pillW * 0.5f - 4f)
            val py = (cy + sin(rad) * pDist).coerceIn(pillH * 0.5f + 4f, h - pillH * 0.5f - 4f)
            levelUpPills[i].set(px - pillW * 0.5f, py - pillH * 0.5f, px + pillW * 0.5f, py + pillH * 0.5f)
        }

        // Ergonomic Cancel X right above the skill arc (easy right-thumb reach!)
        cancelRadius = (h * 0.052f).coerceIn(30f, 52f)
        val cancelY = (levelUpPills[3].top - cancelRadius - 14f).coerceAtLeast(shopBtn.bottom + cancelRadius + 14f)
        cancelCenter.set(w - pad - cancelRadius * 1.20f, cancelY)

        // Left Joystick anchor zone
        joystickRadius = (h * 0.095f).coerceIn(52f, 102f)
        joystickRestCenter.set(pad + joystickRadius * 1.28f, h - pad - joystickRadius * 1.24f)

        // Dota 2 Bottom Console height & adjacent Courier / Glyph column
        val consoleH = (h * 0.165f).coerceIn(114f, 178f)
        val consoleBottom = h - pad * 0.70f
        val consoleTop = consoleBottom - consoleH

        utilRadius = (consoleH * 0.21f).coerceIn(22f, 36f)
        val qOuterLeft = minOf(abilityCenters[0].x - abilityRadius, levelUpPills[0].left)
        val utilColX = qOuterLeft - utilRadius - (w * 0.011f).coerceAtLeast(12f)
        courierCenter.set(utilColX, consoleTop + utilRadius + 2f)
        glyphCenter.set(utilColX, consoleBottom - utilRadius - 2f)

        // Bottom-center Authentic Dota 2 Hero Console
        val barLeft = joystickRestCenter.x + joystickRadius + (w * 0.015f).coerceAtLeast(16f)
        val barRight = utilColX - utilRadius - (w * 0.011f).coerceAtLeast(12f)
        val availW = (barRight - barLeft).coerceAtLeast(380f)
        val consoleW = availW.coerceAtMost(h * 1.04f)
        val cLeft = barLeft + (availW - consoleW) * 0.5f
        val cRight = cLeft + consoleW
        heroConsole.set(cLeft, consoleTop, cRight, consoleBottom)

        val innerPad = 6f
        val innerH = consoleH - innerPad * 2f
        val xpH = (innerH * 0.13f).coerceIn(10f, 18f)
        val gap = (consoleW * 0.010f).coerceIn(5f, 10f)

        // 1) Portrait + Level + XP Bar
        val portW = (consoleW * 0.152f).coerceIn(72f, innerH * 0.96f)
        portraitBox.set(cLeft + innerPad, consoleTop + innerPad, cLeft + innerPad + portW, consoleBottom - innerPad - xpH - 3f)
        xpBarBox.set(portraitBox.left, portraitBox.bottom + 3f, portraitBox.right, consoleBottom - innerPad)

        // 2) Attributes (STR/AGI/INT), Combat Stats & Talent Tree Emblem
        val attrW = consoleW * 0.235f
        val attrLeft = portraitBox.right + gap
        attrBox.set(attrLeft, consoleTop + innerPad, attrLeft + attrW, consoleBottom - innerPad)
        talentHudBtn.set(attrLeft, consoleTop + innerPad, attrLeft + attrW * 0.58f, consoleTop + innerPad + (innerH * 0.30f).coerceIn(22f, 38f))

        // 3) TP & Neutral Item Column (right edge of Dota Console)
        val slotColW = maxOf(innerH * 0.44f, utilRadius * 2.02f)
        val tpRight = cRight - innerPad
        val tpLeft = tpRight - slotColW
        val slotGapY = 5f
        val slotBoxH = (innerH - slotGapY) * 0.5f
        tpSlot.set(tpLeft, consoleTop + innerPad, tpRight, consoleTop + innerPad + slotBoxH)
        neutralSlot.set(tpLeft, tpSlot.bottom + slotGapY, tpRight, consoleBottom - innerPad)
        tpCenter.set(tpSlot.centerX(), tpSlot.centerY())

        // 4) Dota 2 3x2 Inventory Grid
        val itemGapX = 4f
        val itemGapY = 4f
        val itemGridW = (consoleW * 0.270f).coerceIn(118f, slotBoxH * 1.32f * 3f + itemGapX * 2f)
        val itemRight = tpLeft - gap
        val itemLeft = itemRight - itemGridW
        val itemSlotW = (itemGridW - itemGapX * 2f) / 3f
        val itemSlotH = (innerH - itemGapY) * 0.5f
        for (i in 0..5) {
            val col = i % 3
            val row = i / 3
            val l = itemLeft + col * (itemSlotW + itemGapX)
            val t = consoleTop + innerPad + row * (itemSlotH + itemGapY)
            itemSlots[i].set(l, t, l + itemSlotW, t + itemSlotH)
        }

        // 5) Center HP & Mana Bars Plate
        val plateLeft = attrBox.right + gap
        val plateRight = (itemLeft - gap).coerceAtLeast(plateLeft + 90f)
        heroPlate.set(plateLeft, consoleTop + innerPad, plateRight, consoleBottom - innerPad)

        buybackBtn.set(w * 0.40f, h * 0.62f, w * 0.60f, h * 0.72f)
    }
}
