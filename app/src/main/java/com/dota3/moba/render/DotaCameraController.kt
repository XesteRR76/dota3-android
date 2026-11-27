package com.dota3.moba.render

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.engine.HeroEntity
import com.dota3.moba.engine.MapLayout
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

data class CameraPose(
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val lookX: Float,
    val lookY: Float,
    val lookZ: Float,
    val pitchDeg: Float
)

/**
 * Pure Kotlin Dota 2 strategic camera controller:
 * - ~60° high strategic angle
 * - Vertical world-Z offset so the hero centers cleanly above the bottom Dota console
 * - Smooth movement look-ahead
 * - Smooth camera shift + gentle zoom-out toward long-range skillshots (Hook / Sun Strike / etc.)
 * - Tactile camera shake on crits, ultimates, and Roshan slams
 */
class DotaCameraController(
    initialX: Float = 800f,
    initialY: Float = 4000f
) {
    val cameraPos = Vec2(initialX, initialY)
    var cameraLocked: Boolean = true
    var baseZoomDist: Float = 900f

    val lookAhead = Vec2(0f, 0f)
    val skillshotShift = Vec2(0f, 0f)
    private var dynamicZoomBonus: Float = 0f

    private var shakeIntensity: Float = 0f
    private var shakePhase: Float = 0f

    companion object {
        const val MIN_ZOOM = 620f
        const val MAX_ZOOM = 1280f
        const val DEFAULT_ZOOM = 900f
        // Classic Dota 2 ~60° strategic pitch angle (sin 60° = 0.8660254, cos 60° = 0.5)
        const val PITCH_SIN = 0.8660254f
        const val PITCH_COS = 0.5000000f
    }

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    fun addShake(amount: Float) {
        if (amount <= 0f) return
        shakeIntensity = (shakeIntensity + amount).coerceAtMost(1.0f)
    }

    fun update(
        dt: Float,
        playerHero: HeroEntity,
        aimActive: Boolean,
        aimWorldPos: Vec2,
        aimRange: Float,
        lastSkillshotPos: Vec2?,
        lastSkillshotHoldTimer: Float
    ) {
        val safeDt = dt.coerceIn(0.001f, 0.05f)
        val followAlpha = 1f - exp(-10f * safeDt)

        if (cameraLocked) {
            cameraPos.x = DotaMath.lerp(cameraPos.x, playerHero.pos.x, followAlpha)
            cameraPos.y = DotaMath.lerp(cameraPos.y, playerHero.pos.y, followAlpha)
        }

        // Smooth movement look-ahead (only when locked onto moving hero)
        val moveTargetX: Float
        val moveTargetY: Float
        if (cameraLocked && playerHero.alive) {
            if (abs(playerHero.moveIntent.x) > 0.08f || abs(playerHero.moveIntent.y) > 0.08f) {
                val n = playerHero.moveIntent.normalized()
                moveTargetX = n.x * 84f
                moveTargetY = n.y * 84f
            } else if (playerHero.tapMoveTarget != null) {
                val d = playerHero.tapMoveTarget!! - playerHero.pos
                val len = hypot(d.x, d.y)
                if (len > 25f) {
                    moveTargetX = (d.x / len) * 84f
                    moveTargetY = (d.y / len) * 84f
                } else {
                    moveTargetX = 0f; moveTargetY = 0f
                }
            } else {
                moveTargetX = 0f; moveTargetY = 0f
            }
        } else {
            moveTargetX = 0f; moveTargetY = 0f
        }
        val lookAlpha = 1f - exp(-4.8f * safeDt)
        lookAhead.x = DotaMath.lerp(lookAhead.x, moveTargetX, lookAlpha)
        lookAhead.y = DotaMath.lerp(lookAhead.y, moveTargetY, lookAlpha)

        // Smooth camera shift toward long-range skillshots (aiming OR recent cast hold)
        var targetShiftX = 0f
        var targetShiftY = 0f
        var targetZoomBonus = 0f
        val activeSkillTarget = when {
            aimActive && cameraLocked -> aimWorldPos
            lastSkillshotPos != null && lastSkillshotHoldTimer > 0f && cameraLocked -> lastSkillshotPos
            else -> null
        }
        if (activeSkillTarget != null) {
            val dx = activeSkillTarget.x - playerHero.pos.x
            val dy = activeSkillTarget.y - playerHero.pos.y
            val dist = hypot(dx, dy)
            if (dist > 200f) {
                val shiftLen = ((dist - 160f) * 0.42f).coerceIn(0f, 360f)
                targetShiftX = (dx / dist) * shiftLen
                targetShiftY = (dy / dist) * shiftLen
            }
            if (aimRange >= 720f || dist >= 650f) {
                targetZoomBonus = 85f
            }
        }
        val shiftAlpha = 1f - exp(-7.2f * safeDt)
        skillshotShift.x = DotaMath.lerp(skillshotShift.x, targetShiftX, shiftAlpha)
        skillshotShift.y = DotaMath.lerp(skillshotShift.y, targetShiftY, shiftAlpha)
        dynamicZoomBonus = DotaMath.lerp(dynamicZoomBonus, targetZoomBonus, shiftAlpha)

        // Subtle tactile camera shake decay
        if (shakeIntensity > 0.002f) {
            shakePhase += safeDt * 58f
            shakeIntensity *= exp(-10.5f * safeDt)
            if (shakeIntensity < 0.004f) shakeIntensity = 0f
        } else {
            shakeIntensity = 0f
        }
    }

    fun computePose(groundHeight: Float): CameraPose {
        val effZoom = clampZoom(baseZoomDist + dynamicZoomBonus)
        // Shift lookAt slightly south (+Z) when cameraLocked so hero sits centered above the bottom Dota console
        val consoleBiasZ = if (cameraLocked) 62f * (effZoom / DEFAULT_ZOOM) else 0f

        val shakeX = sin(shakePhase * 1.13f) * 9.5f * shakeIntensity
        val shakeZ = cos(shakePhase * 1.47f) * 9.5f * shakeIntensity
        val shakeY = sin(shakePhase * 2.1f) * 4.5f * shakeIntensity

        val lookX = (cameraPos.x + lookAhead.x + skillshotShift.x).coerceIn(0f, MapLayout.SIZE) + shakeX
        val lookZ = (cameraPos.y + lookAhead.y + skillshotShift.y + consoleBiasZ).coerceIn(0f, MapLayout.SIZE + 120f) + shakeZ
        val lookY = groundHeight + 24f + shakeY

        val camHeight = effZoom * PITCH_SIN
        val camOffsetZ = effZoom * PITCH_COS
        val eyeX = lookX
        val eyeY = lookY + camHeight
        val eyeZ = lookZ + camOffsetZ

        val pitchDeg = Math.toDegrees(atan2(camHeight.toDouble(), camOffsetZ.toDouble())).toFloat()
        return CameraPose(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, pitchDeg)
    }
}
