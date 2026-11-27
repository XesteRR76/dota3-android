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
    val topHeader = BoxBounds()

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
        val pad = (w * 0.011f).coerceIn(10f, 26f)
        val mmSize = (h * 0.232f).coerceIn(108f, 240f)
        minimap.set(pad, pad, pad + mmSize, pad + mmSize)
        val camH = (h * 0.044f).coerceIn(24f, 48f)
        camBtn.set(pad, minimap.bottom + 5f, pad + mmSize * 0.61f, minimap.bottom + 5f + camH)
        zoomBtn.set(camBtn.right + 5f, minimap.bottom + 5f, pad + mmSize, minimap.bottom + 5f + camH)

        // 2 MLBB Quick-Buy Item Cards cleanly beside Minimap
        val qbLeft = minimap.right + 8f
        val qbW = (w * 0.112f).coerceIn(104f, 224f)
        val qbH = (h * 0.056f).coerceIn(30f, 60f)
        quickBuy[0].set(qbLeft, pad, qbLeft + qbW, pad + qbH)
        quickBuy[1].set(qbLeft, pad + qbH + 5f, qbLeft + qbW, pad + qbH * 2f + 5f)

        // Top Right Menu Buttons
        val btnW = (w * 0.068f).coerceIn(62f, 134f)
        val btnH = (h * 0.058f).coerceIn(32f, 60f)
        pauseBtn.set(w - pad - btnW * 0.85f, pad, w - pad, pad + btnH)
        statsBtn.set(pauseBtn.left - 6f - btnW, pad, pauseBtn.left - 6f, pad + btnH)
        talentBtn.set(statsBtn.left - 6f - btnW * 1.12f, pad, statsBtn.left - 6f, pad + btnH)
        shopBtn.set(talentBtn.left - 6f - btnW * 1.32f, pad, talentBtn.left - 6f, pad + btnH)

        // Top Center Score & Clock Header (guaranteed clear of Quick-Buy & Shop)
        val centerClearHalf = minOf(w * 0.5f - (quickBuy[0].right + 8f), (shopBtn.left - 8f) - w * 0.5f).coerceAtLeast(60f)
        val topW = minOf(w * 0.27f, centerClearHalf * 2f).coerceAtLeast(120f)
        val topH = (h * 0.070f).coerceIn(36f, 76f)
        topHeader.set(w * 0.5f - topW * 0.5f, 6f, w * 0.5f + topW * 0.5f, 6f + topH)

        // MLBB Ergonomic Right-Thumb Fan (tight reachable arc Q=186°..R=264°)
        attackRadius = (h * 0.080f).coerceIn(44f, 94f)
        attackCenter.set(w - pad - attackRadius * 1.14f, h - pad - attackRadius * 1.14f)

        subRadius = attackRadius * 0.48f
        val subDist = attackRadius * 1.56f
        val minionRad = Math.toRadians(162.0).toFloat()
        val towerRad = Math.toRadians(282.0).toFloat()
        minionCenter.set(attackCenter.x + cos(minionRad) * subDist, attackCenter.y + sin(minionRad) * subDist)
        towerDenyCenter.set(attackCenter.x + cos(towerRad) * subDist, attackCenter.y + sin(towerRad) * subDist)

        abilityRadius = attackRadius * 0.56f
        val arcR = attackRadius * 2.68f
        val anglesDeg = floatArrayOf(186f, 212f, 238f, 264f)
        val pillW = abilityRadius * 1.30f
        val pillH = (h * 0.040f).coerceIn(24f, 44f)
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
        cancelRadius = (h * 0.052f).coerceIn(28f, 56f)
        val cancelY = (levelUpPills[3].top - cancelRadius - 12f).coerceAtLeast(shopBtn.bottom + cancelRadius + 10f)
        cancelCenter.set(w - pad - cancelRadius * 1.15f, cancelY)

        // Left Joystick anchor zone
        joystickRadius = (h * 0.095f).coerceIn(48f, 108f)
        joystickRestCenter.set(pad + joystickRadius * 1.24f, h - pad - joystickRadius * 1.22f)

        // Dota 2 Bottom Console height & adjacent Courier / Glyph column
        val consoleH = (h * 0.185f).coerceIn(112f, 216f)
        val consoleBottom = h - pad * 0.65f
        val consoleTop = consoleBottom - consoleH

        utilRadius = (consoleH * 0.20f).coerceIn(20f, 38f)
        val qOuterLeft = minOf(abilityCenters[0].x - abilityRadius, levelUpPills[0].left)
        val utilColX = qOuterLeft - utilRadius - (w * 0.010f).coerceAtLeast(10f)
        courierCenter.set(utilColX, consoleTop + utilRadius + 2f)
        glyphCenter.set(utilColX, consoleBottom - utilRadius - 2f)

        // Bottom-center Authentic Dota 2 Hero Console
        val barLeft = joystickRestCenter.x + joystickRadius + (w * 0.012f).coerceAtLeast(12f)
        val barRight = utilColX - utilRadius - (w * 0.010f).coerceAtLeast(10f)
        val availW = (barRight - barLeft).coerceAtLeast(240f)
        val consoleW = availW.coerceAtMost(h * 1.24f)
        val cLeft = barLeft + (availW - consoleW) * 0.5f
        val cRight = cLeft + consoleW
        heroConsole.set(cLeft, consoleTop, cRight, consoleBottom)

        val innerPad = 6f
        val innerH = consoleH - innerPad * 2f
        val xpH = (innerH * 0.135f).coerceIn(11f, 20f)
        val gap = (consoleW * 0.009f).coerceIn(4f, 9f)

        // 1) Portrait + Level + XP Bar
        val portW = minOf(consoleW * 0.145f, innerH * 0.94f).coerceAtLeast(36f)
        portraitBox.set(cLeft + innerPad, consoleTop + innerPad, cLeft + innerPad + portW, consoleBottom - innerPad - xpH - 3f)
        xpBarBox.set(portraitBox.left, portraitBox.bottom + 3f, portraitBox.right, consoleBottom - innerPad)

        // 2) Attributes (STR/AGI/INT), Combat Stats & Talent Tree Emblem
        val attrW = consoleW * 0.235f
        val attrLeft = portraitBox.right + gap
        attrBox.set(attrLeft, consoleTop + innerPad, attrLeft + attrW, consoleBottom - innerPad)
        talentHudBtn.set(attrLeft, consoleTop + innerPad, attrLeft + attrW * 0.56f, consoleTop + innerPad + (innerH * 0.28f).coerceIn(22f, 40f))

        // 3) TP & Neutral Item Column (right edge of Dota Console)
        val slotColW = minOf(consoleW * 0.130f, maxOf(innerH * 0.43f, utilRadius * 1.98f))
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
        val itemGridW = minOf(consoleW * 0.275f, slotBoxH * 1.32f * 3f + itemGapX * 2f)
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
        val plateRight = (itemLeft - gap).coerceAtLeast(plateLeft + 16f)
        heroPlate.set(plateLeft, consoleTop + innerPad, plateRight, consoleBottom - innerPad)

        buybackBtn.set(w * 0.40f, h * 0.62f, w * 0.60f, h * 0.72f)
    }
}
