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

data class HudCustomization(
    val joystickScale: Float = 1.0f,
    val skillScale: Float = 1.0f,
    val consoleScale: Float = 1.0f,
    val minimapScale: Float = 1.0f,
    val joystickOffsetX: Float = 0f, // normalized [-0.06..0.06] of screen width
    val joystickOffsetY: Float = 0f, // normalized [-0.08..0.04] of screen height
    val skillOffsetX: Float = 0f,    // normalized [-0.06..0.04] of screen width
    val skillOffsetY: Float = 0f     // normalized [-0.08..0.04] of screen height
) {
    fun clamped(): HudCustomization = HudCustomization(
        joystickScale = joystickScale.coerceIn(0.75f, 1.28f),
        skillScale = skillScale.coerceIn(0.75f, 1.20f),
        consoleScale = consoleScale.coerceIn(0.80f, 1.18f),
        minimapScale = minimapScale.coerceIn(0.80f, 1.28f),
        joystickOffsetX = joystickOffsetX.coerceIn(-0.05f, 0.06f),
        joystickOffsetY = joystickOffsetY.coerceIn(-0.08f, 0.04f),
        skillOffsetX = skillOffsetX.coerceIn(-0.06f, 0.04f),
        skillOffsetY = skillOffsetY.coerceIn(-0.08f, 0.04f)
    )
}

/**
 * Pure Kotlin Dota 2 Bottom Console + MLBB Ergonomic Right-Thumb Skill Arc +
 * Enemy Target-Lock Portraits + Tactical Bot Command Grid layout calculator.
 */
class HudGeometry {
    val minimap = BoxBounds()
    val camBtn = BoxBounds()
    val zoomBtn = BoxBounds()
    val tacticalBtns = Array(4) { BoxBounds() } // PUSH, ROSH, DEF, BACK
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

    // MLBB Right-Thumb Attack & Skill Fan + Target-Lock Portraits
    val attackCenter = Vec2()
    var attackRadius = 56f
    val minionCenter = Vec2()
    val towerDenyCenter = Vec2()
    var subRadius = 30f

    val targetLockCenters = Array(3) { Vec2() }
    var targetLockRadius = 18f

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

    fun layout(w: Float, h: Float, rawCustom: HudCustomization = HudCustomization()) {
        val custom = rawCustom.clamped()
        val pad = (w * 0.011f).coerceIn(10f, 26f)
        val mmSize = ((h * 0.232f).coerceIn(108f, 240f) * custom.minimapScale).coerceAtMost(h * 0.30f)
        minimap.set(pad, pad, pad + mmSize, pad + mmSize)
        val camH = (h * 0.044f).coerceIn(24f, 46f)
        camBtn.set(pad, minimap.bottom + 5f, pad + mmSize * 0.61f, minimap.bottom + 5f + camH)
        zoomBtn.set(camBtn.right + 5f, minimap.bottom + 5f, pad + mmSize, minimap.bottom + 5f + camH)

        // 2x2 Tactical Bot Command Grid right under Minimap/Cam controls
        val tacTop = camBtn.bottom + 6f
        val tacGap = 4f
        val tacW = (mmSize - tacGap) * 0.5f
        val tacH = (h * 0.042f).coerceIn(22f, 40f)
        for (i in 0..3) {
            val col = i % 2
            val row = i / 2
            val l = pad + col * (tacW + tacGap)
            val t = tacTop + row * (tacH + tacGap)
            tacticalBtns[i].set(l, t, l + tacW, t + tacH)
        }

        // 2 MLBB Quick-Buy Item Cards cleanly beside Minimap
        val qbLeft = minimap.right + 8f
        val qbW = (w * 0.112f).coerceIn(100f, 224f)
        val qbH = (h * 0.056f).coerceIn(30f, 58f)
        quickBuy[0].set(qbLeft, pad, qbLeft + qbW, pad + qbH)
        quickBuy[1].set(qbLeft, pad + qbH + 5f, qbLeft + qbW, pad + qbH * 2f + 5f)

        // Top Right Menu Buttons
        val btnW = (w * 0.068f).coerceIn(60f, 134f)
        val btnH = (h * 0.058f).coerceIn(32f, 58f)
        pauseBtn.set(w - pad - btnW * 0.85f, pad, w - pad, pad + btnH)
        statsBtn.set(pauseBtn.left - 6f - btnW, pad, pauseBtn.left - 6f, pad + btnH)
        talentBtn.set(statsBtn.left - 6f - btnW * 1.12f, pad, statsBtn.left - 6f, pad + btnH)
        shopBtn.set(talentBtn.left - 6f - btnW * 1.32f, pad, talentBtn.left - 6f, pad + btnH)

        // Top Center Score & Clock Header (guaranteed clear of Quick-Buy & Shop)
        val centerClearHalf = minOf(w * 0.5f - (quickBuy[0].right + 8f), (shopBtn.left - 8f) - w * 0.5f).coerceAtLeast(60f)
        val topW = minOf(w * 0.27f, centerClearHalf * 2f).coerceAtLeast(116f)
        val topH = (h * 0.070f).coerceIn(36f, 74f)
        topHeader.set(w * 0.5f - topW * 0.5f, 6f, w * 0.5f + topW * 0.5f, 6f + topH)

        // Left Joystick anchor zone & Console sizing (computed before right-thumb fan so bar span stays guaranteed)
        val baseJoyR = (h * 0.095f).coerceIn(46f, 106f) * custom.joystickScale
        joystickRadius = baseJoyR.coerceAtMost(minOf(h * 0.145f, w * 0.080f))
        val minJoyY = tacticalBtns[3].bottom + joystickRadius + 10f
        val rawJoyX = pad + joystickRadius * 1.24f + custom.joystickOffsetX * w
        val rawJoyY = h - pad - joystickRadius * 1.22f + custom.joystickOffsetY * h
        joystickRestCenter.set(
            rawJoyX.coerceIn(pad + joystickRadius + 4f, w * 0.25f),
            rawJoyY.coerceIn(minJoyY.coerceAtMost(h - pad - joystickRadius), h - pad - joystickRadius)
        )

        val consoleH = ((h * 0.185f).coerceIn(110f, 212f) * custom.consoleScale).coerceAtMost(h * 0.25f)
        val consoleBottom = h - pad * 0.65f
        val consoleTop = consoleBottom - consoleH
        utilRadius = (consoleH * 0.20f).coerceIn(20f, 38f)

        // MLBB Ergonomic Right-Thumb Fan (tight reachable arc Q=186°..R=264°)
        val baseAttackR = (h * 0.080f).coerceIn(44f, 92f) * custom.skillScale
        attackRadius = baseAttackR.coerceAtMost(minOf(h * 0.115f, w * 0.065f))
        abilityRadius = attackRadius * 0.56f
        val arcR = attackRadius * 2.68f
        val anglesDeg = floatArrayOf(186f, 212f, 238f, 264f)
        val pillW = abilityRadius * 1.30f
        val pillH = (h * 0.040f).coerceIn(24f, 44f)

        val qRad = Math.toRadians(anglesDeg[0].toDouble()).toFloat()
        val qEdgeOffset = abs(cos(qRad)) * (pillW * 0.5f) + abs(sin(qRad)) * (pillH * 0.5f)
        val qPDist = abilityRadius + qEdgeOffset + 6f
        val qOuterOffsetFromAtk = minOf(
            cos(qRad) * arcR - abilityRadius,
            cos(qRad) * (arcR + qPDist) - pillW * 0.5f
        )
        val barLeft = joystickRestCenter.x + joystickRadius + (w * 0.012f).coerceAtLeast(12f)
        val utilColPad = (w * 0.010f).coerceAtLeast(10f)
        val minCenterConsoleW = 220f

        val rawAtkX = w - pad - attackRadius * 1.14f + custom.skillOffsetX * w
        val rawAtkY = h - pad - attackRadius * 1.14f + custom.skillOffsetY * h
        val minArcMargin = attackRadius * 2.68f + attackRadius * 0.56f + 8f
        val maxAtkX = w - pad - attackRadius * 1.05f
        val minAtkX = minOf(
            maxAtkX,
            maxOf(
                w * 0.55f + minArcMargin * 0.35f,
                barLeft + minCenterConsoleW - qOuterOffsetFromAtk + utilRadius * 2f + utilColPad * 2f
            )
        )
        attackCenter.set(
            rawAtkX.coerceIn(minAtkX, maxAtkX),
            rawAtkY.coerceIn(shopBtn.bottom + minArcMargin * 0.65f, h - pad - attackRadius * 1.05f)
        )

        subRadius = attackRadius * 0.48f
        val subDist = attackRadius * 1.56f
        val minionRad = Math.toRadians(162.0).toFloat()
        val towerRad = Math.toRadians(282.0).toFloat()
        minionCenter.set(attackCenter.x + cos(minionRad) * subDist, attackCenter.y + sin(minionRad) * subDist)
        towerDenyCenter.set(attackCenter.x + cos(towerRad) * subDist, attackCenter.y + sin(towerRad) * subDist)

        // 3 MLBB Enemy Hero Target-Lock Portrait Icons nestled between CREEP (162°) and TWR/! (282°)
        targetLockRadius = attackRadius * 0.33f
        val lockDist = attackRadius * 1.72f
        val lockAnglesDeg = floatArrayOf(191f, 217f, 243f)
        for (i in 0..2) {
            val lRad = Math.toRadians(lockAnglesDeg[i].toDouble()).toFloat()
            targetLockCenters[i].set(attackCenter.x + cos(lRad) * lockDist, attackCenter.y + sin(lRad) * lockDist)
        }

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

        // Ergonomic Cancel X right above the skill arc
        cancelRadius = (h * 0.050f).coerceIn(26f, 54f)
        val cancelY = (levelUpPills[3].top - cancelRadius - 10f).coerceAtLeast(shopBtn.bottom + cancelRadius + 8f)
        cancelCenter.set(w - pad - cancelRadius * 1.15f, cancelY)

        // Dota 2 Adjacent Courier / Glyph column & Bottom Hero Console
        val qOuterLeft = minOf(abilityCenters[0].x - abilityRadius, levelUpPills[0].left)
        val utilColX = qOuterLeft - utilRadius - utilColPad
        courierCenter.set(utilColX, consoleTop + utilRadius + 2f)
        glyphCenter.set(utilColX, consoleBottom - utilRadius - 2f)

        val barRight = utilColX - utilRadius - utilColPad
        val availW = (barRight - barLeft).coerceAtLeast(160f)
        val consoleW = availW.coerceAtMost(h * 1.24f * custom.consoleScale)
        val cLeft = barLeft + (availW - consoleW) * 0.5f
        val cRight = cLeft + consoleW
        heroConsole.set(cLeft, consoleTop, cRight, consoleBottom)

        val innerPad = 6f
        val innerH = consoleH - innerPad * 2f
        val xpH = (innerH * 0.135f).coerceIn(11f, 20f)
        val gap = (consoleW * 0.009f).coerceIn(4f, 9f)

        // 1) Portrait + Level + XP Bar
        val portW = minOf(consoleW * 0.145f, innerH * 0.94f).coerceAtLeast(minOf(36f, consoleW * 0.145f))
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
