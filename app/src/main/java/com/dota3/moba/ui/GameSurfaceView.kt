package com.dota3.moba.ui

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.dota3.moba.core.Vec2
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.model.TargetType
import com.dota3.moba.render.GameRenderer
import kotlin.math.hypot

interface GameHudListener {
    fun onOpenShop()
    fun onOpenTalents()
    fun onOpenScoreboard()
    fun onOpenPauseMenu()
    fun onMatchEnded()
}

class GameSurfaceView(
    context: Context,
    val world: GameWorld,
    private val listener: GameHudListener
) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val renderer = GameRenderer()
    @Volatile
    private var running = false
    private var renderThread: Thread? = null
    private var matchEndNotified = false

    private var joystickPointerId = -1
    private var aimPointerId = -1
    private var aimStartScreen = Vec2()
    private var minimapPointerId = -1

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread(this, "Dota3GameLoop").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.layoutHud(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            renderThread?.join(1500)
        } catch (_: InterruptedException) {
        }
    }

    override fun run() {
        var lastNs = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNs = now

            world.step(dt)
            if (world.matchOver && !matchEndNotified) {
                matchEndNotified = true
                post { listener.onMatchEnded() }
            }

            val canvas = holder.lockCanvas()
            if (canvas != null) {
                try {
                    renderer.render(canvas, world, width.toFloat(), height.toFloat())
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            // Cap ~60 FPS cleanly
            val elapsedMs = (System.nanoTime() - now) / 1_000_000L
            if (elapsedMs < 15L) {
                try {
                    Thread.sleep(15L - elapsedMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        val sx = event.getX(idx)
        val sy = event.getY(idx)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val hud = renderer.hud

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // 1. Minimap pan
                if (hud.minimapRect.contains(sx, sy)) {
                    minimapPointerId = pid
                    panCameraFromMinimap(sx, sy)
                    return true
                }
                // 2. Top menu buttons
                if (hud.shopBtnRect.contains(sx, sy)) { listener.onOpenShop(); return true }
                if (hud.talentBtnRect.contains(sx, sy)) { listener.onOpenTalents(); return true }
                if (hud.statsBtnRect.contains(sx, sy)) { listener.onOpenScoreboard(); return true }
                if (hud.pauseBtnRect.contains(sx, sy)) { listener.onOpenPauseMenu(); return true }
                if (hud.camBtnRect.contains(sx, sy)) {
                    renderer.cameraLocked = !renderer.cameraLocked
                    if (renderer.cameraLocked) renderer.cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
                    return true
                }
                // 3. Utility & Buyback
                if (!world.playerHero.alive && hud.buybackBtnRect.contains(sx, sy)) {
                    world.buyback(world.playerHero)
                    return true
                }
                if (hud.tpBtnRect.contains(sx, sy)) {
                    world.useTpScroll(world.playerHero, renderer.cameraPos)
                    return true
                }
                if (hud.courierBtnRect.contains(sx, sy)) {
                    world.dispatchCourier(world.playerHero)
                    return true
                }
                if (hud.glyphBtnRect.contains(sx, sy)) {
                    world.activateGlyph(world.playerTeam)
                    return true
                }
                if (world.playerHero.quickBuyItemId != null && hud.quickBuyRect.contains(sx, sy)) {
                    world.playerHero.quickBuyItemId?.let { qid ->
                        if (world.buyItem(world.playerHero, qid)) {
                            world.dispatchCourier(world.playerHero)
                        }
                    }
                    return true
                }
                // 4. Attack / LastHit / Deny
                if (hud.attackBtnRect.contains(sx, sy)) { world.playerSmartAttack(preferCreep = false, preferDeny = false); return true }
                if (hud.lastHitBtnRect.contains(sx, sy)) { world.playerSmartAttack(preferCreep = true, preferDeny = false); return true }
                if (hud.denyBtnRect.contains(sx, sy)) { world.playerSmartAttack(preferCreep = true, preferDeny = true); return true }

                // 5. Level-up badges
                for (i in 0..3) {
                    if (world.playerHero.canLevelAbility(i) && hud.levelUpRects[i].contains(sx, sy)) {
                        world.levelUpAbility(world.playerHero, i)
                        return true
                    }
                }

                // 6. Abilities (Q, W, E, R)
                for (i in 0..3) {
                    if (hud.abilityRects[i].contains(sx, sy)) {
                        val ab = world.playerHero.def.abilities[i]
                        if (ab.targetType == TargetType.INSTANT || ab.targetType == TargetType.TOGGLE || ab.targetType == TargetType.PASSIVE) {
                            val aim = defaultAutoAim(ab.castRange)
                            world.castAbility(world.playerHero, i, aim)
                        } else {
                            aimPointerId = pid
                            aimStartScreen.set(sx, sy)
                            renderer.aimingAbilityIdx = i
                            renderer.aimWorldPos.set(defaultAutoAim(ab.castRange).x, defaultAutoAim(ab.castRange).y)
                        }
                        return true
                    }
                }

                // 7. 6 Inventory Item Slots
                for (i in 0..5) {
                    if (hud.itemRects[i].contains(sx, sy)) {
                        val aim = world.playerHero.pos + Vec2.fromAngle(world.playerHero.facingAngle, 450f)
                        world.useItemSlot(world.playerHero, i, aim)
                        return true
                    }
                }

                // 8. Left half of screen -> Virtual Joystick
                if (sx < w * 0.45f && joystickPointerId == -1) {
                    joystickPointerId = pid
                    renderer.joystickOrigin = Vec2(sx, sy)
                    renderer.joystickThumb = Vec2(sx, sy)
                    return true
                }

                // 9. World tap: attack tapped enemy or tap-move
                val worldPt = renderer.screenToWorld(sx, sy, w, h)
                val tappedEnemy = world.allEnemyUnits(world.playerTeam, worldPt, 75f)
                    .minByOrNull { it.pos.distTo(worldPt) }
                if (tappedEnemy != null) {
                    world.playerHero.attackTargetId = tappedEnemy.id
                    world.playerHero.tapMoveTarget = tappedEnemy.pos.copyVec()
                } else {
                    world.playerHero.attackTargetId = null
                    world.playerHero.tapMoveTarget = worldPt
                    world.spawnParticles(worldPt, 0xFF2ECC71.toInt(), 5, 35f)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    val mx = event.getX(i)
                    val my = event.getY(i)
                    if (p == minimapPointerId) {
                        panCameraFromMinimap(mx, my)
                    } else if (p == joystickPointerId) {
                        val origin = renderer.joystickOrigin ?: continue
                        val dx = mx - origin.x
                        val dy = my - origin.y
                        val dist = hypot(dx, dy)
                        val maxR = 72f
                        if (dist > 6f) {
                            val clamped = dist.coerceAtMost(maxR)
                            renderer.joystickThumb = Vec2(origin.x + (dx / dist) * clamped, origin.y + (dy / dist) * clamped)
                            world.playerHero.moveIntent.set(dx / dist, dy / dist)
                        } else {
                            renderer.joystickThumb = origin.copyVec()
                            world.playerHero.moveIntent.set(0f, 0f)
                        }
                    } else if (p == aimPointerId && renderer.aimingAbilityIdx in 0..3) {
                        val ab = world.playerHero.def.abilities[renderer.aimingAbilityIdx]
                        val dx = mx - aimStartScreen.x
                        val dy = my - aimStartScreen.y
                        val dragDist = hypot(dx, dy)
                        if (dragDist > 16f) {
                            val range = ab.castRange.takeIf { it > 0f } ?: 650f
                            val factor = (dragDist / 130f).coerceIn(0.15f, 1.0f)
                            renderer.aimWorldPos.set(
                                world.playerHero.pos.x + (dx / dragDist) * range * factor,
                                world.playerHero.pos.y + (dy / dragDist) * range * factor
                            )
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pid == minimapPointerId) minimapPointerId = -1
                if (pid == joystickPointerId) {
                    joystickPointerId = -1
                    renderer.joystickOrigin = null
                    renderer.joystickThumb = null
                    world.playerHero.moveIntent.set(0f, 0f)
                }
                if (pid == aimPointerId) {
                    if (renderer.aimingAbilityIdx in 0..3) {
                        world.castAbility(world.playerHero, renderer.aimingAbilityIdx, renderer.aimWorldPos)
                    }
                    aimPointerId = -1
                    renderer.aimingAbilityIdx = -1
                }
            }
        }
        return true
    }

    private fun panCameraFromMinimap(sx: Float, sy: Float) {
        val mm = renderer.hud.minimapRect
        val nx = ((sx - mm.left) / mm.width()).coerceIn(0f, 1f)
        val ny = ((sy - mm.top) / mm.height()).coerceIn(0f, 1f)
        renderer.cameraLocked = false
        renderer.cameraPos.set(nx * com.dota3.moba.engine.MapLayout.SIZE, ny * com.dota3.moba.engine.MapLayout.SIZE)
    }

    private fun defaultAutoAim(range: Float): Vec2 {
        val h = world.playerHero
        val searchR = range.takeIf { it > 0f } ?: 650f
        val target = world.allEnemyUnits(h.team, h.pos, searchR)
            .filter { !it.isInvisible() }
            .minByOrNull { (if (it is com.dota3.moba.engine.HeroEntity) 0f else 500f) + it.pos.distTo(h.pos) }
        return target?.pos?.copyVec() ?: (h.pos + Vec2.fromAngle(h.facingAngle, searchR * 0.6f))
    }
}
