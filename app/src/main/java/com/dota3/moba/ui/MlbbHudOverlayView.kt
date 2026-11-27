package com.dota3.moba.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.*
import com.dota3.moba.model.TargetType
import com.dota3.moba.model.Team
import com.dota3.moba.render.World3DRenderer
import kotlin.math.*

interface GameHudListener {
    fun onOpenShop()
    fun onOpenTalents()
    fun onOpenScoreboard()
    fun onOpenPauseMenu()
    fun onMatchEnded()
}

class MlbbHudOverlayView(
    context: Context,
    private val world: GameWorld,
    private val renderer3D: World3DRenderer,
    private val listener: GameHudListener
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private var minimapBg: Bitmap? = null

    // Hitboxes
    private val minimapRect = RectF()
    private val camBtnRect = RectF()
    private val shopBtnRect = RectF()
    private val talentBtnRect = RectF()
    private val statsBtnRect = RectF()
    private val pauseBtnRect = RectF()
    private val quickBuyRects = Array(2) { RectF() }
    private val buybackRect = RectF()

    // MLBB Right-Thumb Arc
    private val attackCenter = Vec2()
    private var attackRadius = 56f
    private val minionCenter = Vec2()
    private val towerDenyCenter = Vec2()
    private var subRadius = 32f

    private val abilityCenters = Array(4) { Vec2() }
    private var abilityRadius = 40f
    private val levelUpRects = Array(4) { RectF() }

    private val tpCenter = Vec2()
    private val courierCenter = Vec2()
    private val glyphCenter = Vec2()
    private var utilRadius = 26f

    private val itemRects = Array(6) { RectF() }
    private val cancelZoneCenter = Vec2()
    private var cancelZoneRadius = 44f

    // Touch state
    private var joystickPointerId = -1
    private var joystickBase = Vec2()
    private var joystickKnob = Vec2()
    private var joystickActive = false

    private var aimPointerId = -1
    private var aimStartPos = Vec2()
    private var aimCurrentPos = Vec2()
    private var aimingCancelHovered = false

    private var minimapPointerId = -1
    private var panPointerId = -1
    private var panLastPos = Vec2()

    private var matchEndNotified = false

    init {
        isClickable = true
        isFocusable = true
    }

    private fun ensureMinimapBg() {
        if (minimapBg != null) return
        val s = 256
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val scale = s / MapLayout.SIZE

        val bg = Paint().apply {
            shader = LinearGradient(
                0f, s.toFloat(), s.toFloat(), 0f,
                intArrayOf(0xFF172B1E.toInt(), 0xFF1E293B.toInt(), 0xFF31171B.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), bg)

        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF165B82.toInt(); style = Paint.Style.STROKE; strokeWidth = 260f * scale
        }
        c.drawLine(450f * scale, 450f * scale, 4350f * scale, 4350f * scale, rp)

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF475569.toInt(); style = Paint.Style.STROKE; strokeWidth = 170f * scale; strokeCap = Paint.Cap.ROUND
        }
        for (lane in MapLayout.laneWaypoints) {
            val path = Path()
            path.moveTo(lane.first().x * scale, lane.first().y * scale)
            for (i in 1 until lane.size) path.lineTo(lane[i].x * scale, lane[i].y * scale)
            c.drawPath(path, lp)
        }
        minimapBg = bmp
    }

    private fun layoutHud(w: Float, h: Float) {
        val pad = w * 0.014f
        val mmSize = (h * 0.25f).coerceIn(120f, 200f)
        minimapRect.set(pad, pad, pad + mmSize, pad + mmSize)
        camBtnRect.set(pad, minimapRect.bottom + 6f, pad + mmSize * 0.62f, minimapRect.bottom + 36f)

        // 2 MLBB Quick-Buy Cards right below minimap
        val qbW = mmSize * 0.92f
        val qbH = (h * 0.068f).coerceIn(36f, 52f)
        quickBuyRects[0].set(pad, camBtnRect.bottom + 8f, pad + qbW, camBtnRect.bottom + 8f + qbH)
        quickBuyRects[1].set(pad, quickBuyRects[0].bottom + 6f, pad + qbW, quickBuyRects[0].bottom + 6f + qbH)

        // Top Right Menu Buttons
        val btnW = (w * 0.075f).coerceIn(72f, 118f)
        val btnH = (h * 0.064f).coerceIn(36f, 52f)
        pauseBtnRect.set(w - pad - btnW * 0.85f, pad, w - pad, pad + btnH)
        statsBtnRect.set(pauseBtnRect.left - 8f - btnW, pad, pauseBtnRect.left - 8f, pad + btnH)
        talentBtnRect.set(statsBtnRect.left - 8f - btnW * 1.1f, pad, statsBtnRect.left - 8f, pad + btnH)
        shopBtnRect.set(talentBtnRect.left - 8f - btnW * 1.3f, pad, talentBtnRect.left - 8f, pad + btnH)

        // Cancel X zone when dragging ability
        cancelZoneRadius = (h * 0.058f).coerceIn(34f, 52f)
        cancelZoneCenter.set(w - pad - cancelZoneRadius * 1.1f, shopBtnRect.bottom + cancelZoneRadius * 1.4f)

        // MLBB Right-Thumb Ergonomic Arc!
        attackRadius = (h * 0.088f).coerceIn(50f, 74f)
        attackCenter.set(w - pad - attackRadius * 1.15f, h - pad - attackRadius * 1.10f)

        subRadius = attackRadius * 0.56f
        minionCenter.set(attackCenter.x - attackRadius * 1.52f, attackCenter.y + attackRadius * 0.34f)
        towerDenyCenter.set(attackCenter.x + attackRadius * 0.12f, attackCenter.y - attackRadius * 1.56f)

        // 4 Ability Buttons in an ergonomic quarter-circle arc around Attack
        abilityRadius = (h * 0.068f).coerceIn(38f, 56f)
        val arcR = attackRadius * 2.55f
        val anglesDeg = floatArrayOf(196f, 166f, 136f, 106f) // Q -> W -> E -> R (Ult top-inner)
        for (i in 0..3) {
            val rad = Math.toRadians(anglesDeg[i].toDouble()).toFloat()
            val cx = attackCenter.x + cos(rad) * arcR
            val cy = attackCenter.y + sin(rad) * arcR
            abilityCenters[i].set(cx, cy)
            levelUpRects[i].set(cx - abilityRadius * 0.52f, cy - abilityRadius - 26f, cx + abilityRadius * 0.52f, cy - abilityRadius - 2f)
        }

        // Utility buttons (Recall TP, Courier, Glyph) neatly in bottom-center-right row
        utilRadius = (h * 0.044f).coerceIn(24f, 38f)
        val utilY = h - pad - utilRadius * 1.1f
        tpCenter.set(minionCenter.x - subRadius - utilRadius * 1.6f, utilY)
        courierCenter.set(tpCenter.x - utilRadius * 2.35f, utilY)
        glyphCenter.set(courierCenter.x - utilRadius * 2.35f, utilY)

        // 6 Active Item Slots neatly along bottom center-left / center
        val itemSize = (h * 0.072f).coerceIn(40f, 58f)
        val itemY = h - pad - itemSize
        val totalItemsW = itemSize * 6 + 6f * 5
        val itemStartX = (w * 0.5f - totalItemsW * 0.65f).coerceAtLeast(minimapRect.right + 18f)
        for (i in 0..5) {
            val left = itemStartX + i * (itemSize + 6f)
            itemRects[i].set(left, itemY, left + itemSize, itemY + itemSize)
        }

        // Buyback center
        buybackRect.set(w * 0.40f, h * 0.62f, w * 0.60f, h * 0.72f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        ensureMinimapBg()
        layoutHud(w, h)

        if (world.matchOver && !matchEndNotified) {
            matchEndNotified = true
            post { listener.onMatchEnded() }
        }

        // 1. Projected 3D HP/MP Bars & Hero Badges
        drawProjectedWorldOverlays(canvas)

        // 2. Minimap & Camera Lock & Quick-Buy Item Cards
        drawMinimap(canvas)
        drawRoundRectBtn(canvas, camBtnRect, if (renderer3D.cameraLocked) "CAM LOCKED" else "FREE CAM",
            0xCC111827.toInt(), if (renderer3D.cameraLocked) 0xFF38BDF8.toInt() else 0xFFF59E0B.toInt())
        drawQuickBuyCards(canvas)

        // 3. Top Match Header (Radiant vs Dire Score, Clock, Gold/KDA)
        drawTopHeader(canvas, w, h)

        // 4. Right Menu Buttons
        val ph = world.playerHero
        drawRoundRectBtn(canvas, shopBtnRect, "SHOP ${ph.gold}g", 0xDD78350F.toInt(), 0xFFF59E0B.toInt())
        val hasTalent = ph.availableTalentTier() != null
        drawRoundRectBtn(canvas, talentBtnRect, if (hasTalent) "TALENT!" else "TALENTS",
            if (hasTalent) 0xDD065F46.toInt() else 0xCC1E293B.toInt(),
            if (hasTalent) 0xFF34D399.toInt() else 0xFF64748B.toInt())
        drawRoundRectBtn(canvas, statsBtnRect, "SCORE", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())
        drawRoundRectBtn(canvas, pauseBtnRect, "MENU", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())

        // 5. MLBB Joystick (Left Bottom)
        drawJoystick(canvas, h)

        // 6. MLBB Right-Thumb Attack Cluster & Ability Arc (with real Dota 2 PNG icons!)
        drawAttackCluster(canvas)
        drawAbilityArc(canvas)
        drawUtilityButtons(canvas)
        drawInventoryBar(canvas)

        // 7. Cancel Cast X Zone when dragging a skillshot
        if (renderer3D.aimingAbilityIdx in 0..3) {
            drawCancelCastZone(canvas)
        }

        // 8. Dead / Buyback overlay & Killstreak Announcements
        drawDeadAndAnnouncements(canvas, w, h)

        // Refresh smoothly ~60fps aligned with 3D render
        postInvalidateOnAnimation()
    }

    private fun drawProjectedWorldOverlays(canvas: Canvas) {
        for (bar in renderer3D.projectedBars) {
            if (bar.isHero) {
                val barW = 78f
                val left = bar.sx - barW * 0.5f
                val hpTop = bar.sy - 14f

                // Mini Hero Circular Portrait beside HP bar!
                bar.heroId?.let { hid ->
                    DotaAssetLoader.getHeroCirclePortrait(hid, 44)?.let { bmp ->
                        val pr = 14f
                        val cx = left - pr - 3f
                        val cy = hpTop + 5f
                        canvas.drawBitmap(bmp, null, RectF(cx - pr, cy - pr, cx + pr, cy + pr), paint)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        paint.color = bar.barColor
                        canvas.drawCircle(cx, cy, pr, paint)
                    }
                }

                // HP + 250-HP Notches
                drawBar(canvas, left, hpTop, barW, 8.5f, bar.hp, bar.maxHp, bar.barColor, bar.notchStep)
                drawBar(canvas, left, hpTop + 9.5f, barW, 4.5f, bar.mana, bar.maxMana, 0xFF38BDF8.toInt(), 0f)

                textPaint.color = Color.WHITE
                textPaint.textSize = 13f
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
                canvas.drawText("Lv${bar.level} ${bar.label}", bar.sx, hpTop - 4f, textPaint)
                textPaint.clearShadowLayer()
            } else {
                val barW = if (bar.notchStep > 0f) 84f else 44f
                val barH = if (bar.notchStep > 0f) 8f else 5f
                drawBar(canvas, bar.sx - barW * 0.5f, bar.sy - 6f, barW, barH, bar.hp, bar.maxHp, bar.barColor, bar.notchStep)
                if (bar.label.isNotEmpty()) {
                    textPaint.color = 0xFFFDE047.toInt()
                    textPaint.textSize = 12f
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(bar.label, bar.sx, bar.sy - 10f, textPaint)
                }
            }
        }

        for (ft in renderer3D.projectedTexts) {
            textPaint.color = ft.color
            textPaint.textSize = 17f * ft.scale
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
            canvas.drawText(ft.text, ft.sx, ft.sy, textPaint)
            textPaint.clearShadowLayer()
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawBar(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, valNow: Float, valMax: Float, col: Int, notch: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0xDD0B0F17.toInt()
        canvas.drawRect(left - 1f, top - 1f, left + w + 1f, top + h + 1f, paint)
        val pct = (valNow / valMax.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.color = col
        canvas.drawRect(left, top, left + w * pct, top + h, paint)
        if (notch > 0f && valMax > notch) {
            linePaint.color = 0xAA000000.toInt()
            linePaint.strokeWidth = 1.5f
            var cur = notch
            while (cur < valMax) {
                val nx = left + w * (cur / valMax)
                canvas.drawLine(nx, top, nx, top + h, linePaint)
                cur += notch
            }
        }
    }

    private fun drawMinimap(canvas: Canvas) {
        val mm = minimapRect
        minimapBg?.let { canvas.drawBitmap(it, null, mm, null) }
        val sx = mm.width() / MapLayout.SIZE
        val sy = mm.height() / MapLayout.SIZE

        for (t in world.towers) {
            if (!t.alive) continue
            paint.style = Paint.Style.FILL
            paint.color = if (t.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
            canvas.drawRect(mm.left + t.pos.x * sx - 2.5f, mm.top + t.pos.y * sy - 2.5f,
                mm.left + t.pos.x * sx + 2.5f, mm.top + t.pos.y * sy + 2.5f, paint)
        }
        if (world.roshan.alive) {
            paint.color = 0xFFFF9800.toInt()
            canvas.drawCircle(mm.left + world.roshan.pos.x * sx, mm.top + world.roshan.pos.y * sy, 4f, paint)
        }
        for (r in world.runes) {
            if (!r.alive) continue
            paint.color = r.type.color
            canvas.drawCircle(mm.left + r.pos.x * sx, mm.top + r.pos.y * sy, 3f, paint)
        }
        for (h in world.heroes) {
            if (!h.alive) continue
            if (h.team != world.playerTeam && (!world.fogOfWar.isVisible(h.pos.x, h.pos.y) || h.isInvisible())) continue
            val hx = mm.left + h.pos.x * sx
            val hy = mm.top + h.pos.y * sy
            val hr = if (h.isPlayer) 8f else 6.5f
            val bmp = DotaAssetLoader.getHeroCirclePortrait(h.def.id, 40)
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, RectF(hx - hr, hy - hr, hx + hr, hy + hr), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (h.isPlayer) 2.2f else 1.5f
                paint.color = if (h.isPlayer) Color.WHITE else if (h.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
                canvas.drawCircle(hx, hy, hr, paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = if (h.isPlayer) Color.WHITE else if (h.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
                canvas.drawCircle(hx, hy, 4.5f, paint)
            }
        }
        // Camera Frustum box on minimap
        val cx = mm.left + renderer3D.cameraPos.x * sx
        val cy = mm.top + renderer3D.cameraPos.y * sy
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawRect((cx - 18f).coerceAtLeast(mm.left), (cy - 12f).coerceAtLeast(mm.top),
            (cx + 18f).coerceAtMost(mm.right), (cy + 12f).coerceAtMost(mm.bottom), paint)

        paint.strokeWidth = 2.5f
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawRect(mm, paint)
    }

    private fun drawQuickBuyCards(canvas: Canvas) {
        val ph = world.playerHero
        val suggestions = buildList {
            ph.quickBuyItemId?.let { add(it) }
            val fallback = listOf("boots", "magic_wand", "power_treads", "bkb", "daedalus")
            for (f in fallback) if (size < 2 && !ph.hasItem(f) && f !in this) add(f)
        }.take(2)

        for (i in suggestions.indices) {
            val item = ItemCatalog.get(suggestions[i]) ?: continue
            val r = quickBuyRects[i]
            val effCost = world.effectiveItemCost(ph, item)
            val canAfford = ph.gold >= effCost

            paint.style = Paint.Style.FILL
            paint.color = if (canAfford) 0xDD064E3B.toInt() else 0xCC111827.toInt()
            canvas.drawRoundRect(r, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (canAfford) 0xFF34D399.toInt() else 0xFF475569.toInt()
            canvas.drawRoundRect(r, 10f, 10f, paint)

            DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                val pad = 4f
                val iconH = r.height() - pad * 2f
                canvas.drawBitmap(bmp, null, RectF(r.left + pad, r.top + pad, r.left + pad + iconH * 1.25f, r.bottom - pad), paint)
            }
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = (r.height() * 0.34f).coerceIn(11f, 15f)
            textPaint.color = if (canAfford) 0xFFFDE047.toInt() else 0xFFCBD5E1.toInt()
            canvas.drawText("${item.shortCode} ${effCost}g", r.right - 8f, r.centerY() + 5f, textPaint)
        }
    }

    private fun drawTopHeader(canvas: Canvas, w: Float, h: Float) {
        val topW = w * 0.30f
        val topH = h * 0.075f
        val topL = w * 0.5f - topW * 0.5f
        paint.style = Paint.Style.FILL
        paint.color = 0xCC0D1320.toInt()
        rectF.set(topL, 6f, topL + topW, 6f + topH)
        canvas.drawRoundRect(rectF, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = 0xFF334155.toInt()
        canvas.drawRoundRect(rectF, 14f, 14f, paint)

        val mins = (world.gameTime / 60f).toInt()
        val secs = (world.gameTime % 60f).toInt()
        val clock = "%02d:%02d".format(mins, secs)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = topH * 0.44f
        textPaint.color = if (world.isNight) 0xFF93C5FD.toInt() else 0xFFFDE047.toInt()
        canvas.drawText(if (world.isNight) "NIGHT $clock" else "DAY $clock", w * 0.5f, 6f + topH * 0.65f, textPaint)

        textPaint.textSize = topH * 0.54f
        textPaint.color = 0xFF2ECC71.toInt()
        canvas.drawText("${world.radiantKills}", topL + topW * 0.14f, 6f + topH * 0.68f, textPaint)
        textPaint.color = 0xFFE74C3C.toInt()
        canvas.drawText("${world.direKills}", topL + topW * 0.86f, 6f + topH * 0.68f, textPaint)

        val ph = world.playerHero
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = topH * 0.34f
        textPaint.color = 0xFFFDE047.toInt()
        canvas.drawText("GOLD ${ph.gold}  •  ${ph.kills}/${ph.deaths}/${ph.assists}  •  CS ${ph.lastHits}/${ph.denies}",
            w * 0.5f, 6f + topH + topH * 0.44f, textPaint)
    }

    private fun drawJoystick(canvas: Canvas, h: Float) {
        val restCenter = if (joystickActive) joystickBase else Vec2(minimapRect.left + 92f, h - 98f)
        val rOuter = 68f
        paint.style = Paint.Style.FILL
        paint.color = if (joystickActive) 0x4438BDF8 else 0x22FFFFFF
        canvas.drawCircle(restCenter.x, restCenter.y, rOuter, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = if (joystickActive) 0xAA38BDF8.toInt() else 0x55CBD5E1
        canvas.drawCircle(restCenter.x, restCenter.y, rOuter, paint)

        val knob = if (joystickActive) joystickKnob else restCenter
        paint.style = Paint.Style.FILL
        paint.color = if (joystickActive) 0xDD38BDF8.toInt() else 0x8894A3B8.toInt()
        canvas.drawCircle(knob.x, knob.y, 28f, paint)
    }

    private fun drawAttackCluster(canvas: Canvas) {
        drawCircleBtn(canvas, attackCenter, attackRadius, "ATK", 0xDDB91C1C.toInt(), 0xFFF87171.toInt(), null)
        drawCircleBtn(canvas, minionCenter, subRadius, "CREEP", 0xDD92400E.toInt(), 0xFFFBBF24.toInt(), null)
        drawCircleBtn(canvas, towerDenyCenter, subRadius, "TWR/!", 0xDD0369A1.toInt(), 0xFF38BDF8.toInt(), null)
    }

    private fun drawAbilityArc(canvas: Canvas) {
        val ph = world.playerHero
        for (i in 0..3) {
            val ab = ph.def.abilities[i]
            val c = abilityCenters[i]
            val lvl = ph.abilityLevels[i]
            val cd = ph.abilityCooldowns[i]
            val icon = DotaAssetLoader.getAbilityCircleIcon(ab.id, 112)
            val border = when {
                renderer3D.aimingAbilityIdx == i -> 0xFF38BDF8.toInt()
                ab.isUltimate -> 0xFFF59E0B.toInt()
                lvl > 0 -> 0xFF60A5FA.toInt()
                else -> 0xFF475569.toInt()
            }

            // Draw real Dota 2 ability icon inside circle!
            if (icon != null) {
                canvas.drawBitmap(icon, null, RectF(c.x - abilityRadius, c.y - abilityRadius, c.x + abilityRadius, c.y + abilityRadius), paint)
                if (lvl == 0 || cd > 0f || ph.mana < ab.manaCost.getOrElse((lvl - 1).coerceAtLeast(0)) { 0 }) {
                    paint.style = Paint.Style.FILL
                    paint.color = 0xAA090D16.toInt()
                    canvas.drawCircle(c.x, c.y, abilityRadius, paint)
                }
            } else {
                paint.style = Paint.Style.FILL
                paint.color = 0xCC1E3A8A.toInt()
                canvas.drawCircle(c.x, c.y, abilityRadius, paint)
            }

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (ab.isUltimate) 4f else 3f
            paint.color = border
            canvas.drawCircle(c.x, c.y, abilityRadius, paint)

            // Cooldown or Key text
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            if (lvl == 0) {
                textPaint.color = 0xFF94A3B8.toInt()
                textPaint.textSize = 14f
                canvas.drawText(ab.keyLabel, c.x, c.y + 5f, textPaint)
            } else if (cd > 0f) {
                textPaint.color = Color.WHITE
                textPaint.textSize = 18f
                canvas.drawText("${"%.1f".format(cd)}", c.x, c.y + 6f, textPaint)
            } else if (ab.maxCharges > 1) {
                textPaint.color = 0xFFFDE047.toInt()
                textPaint.textSize = 14f
                canvas.drawText("${ph.abilityCharges[i]}", c.x + abilityRadius * 0.55f, c.y - abilityRadius * 0.4f, textPaint)
            }

            // Level dots at bottom of ability circle
            for (d in 0 until ab.maxLevel) {
                paint.style = Paint.Style.FILL
                paint.color = if (d < lvl) 0xFFFACC15.toInt() else 0xFF334155.toInt()
                val dx = (d - (ab.maxLevel - 1) * 0.5f) * 10f
                canvas.drawCircle(c.x + dx, c.y + abilityRadius - 6f, 3.2f, paint)
            }

            // MLBB-style "+ UPGRADE" pill right above ability
            if (ph.canLevelAbility(i)) {
                drawRoundRectBtn(canvas, levelUpRects[i], "+ LVL", 0xEE15803D.toInt(), 0xFF4ADE80.toInt())
            }

            // Aim joystick wheel when dragging this skill!
            if (renderer3D.aimingAbilityIdx == i) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = 0xAA38BDF8.toInt()
                canvas.drawCircle(c.x, c.y, abilityRadius * 1.45f, paint)
                paint.style = Paint.Style.FILL
                paint.color = 0xCC38BDF8.toInt()
                canvas.drawCircle(aimCurrentPos.x, aimCurrentPos.y, 18f, paint)
            }
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawUtilityButtons(canvas: Canvas) {
        val ph = world.playerHero
        val tpTxt = if (ph.tpCooldown > 0f) "${ph.tpCooldown.toInt()}" else "TP"
        drawCircleBtn(canvas, tpCenter, utilRadius, tpTxt, 0xCC1E3A8A.toInt(), 0xFF60A5FA.toInt(), DotaAssetLoader.getItemIcon("tp_scroll"))
        val stashN = ph.stash.size
        drawCircleBtn(canvas, courierCenter, utilRadius, if (stashN > 0) "C($stashN)" else "COUR", 0xCC713F12.toInt(), 0xFFFBBF24.toInt(), null)
        val gCd = if (ph.team == Team.RADIANT) world.radiantGlyphCd else world.direGlyphCd
        drawCircleBtn(canvas, glyphCenter, utilRadius, if (gCd > 0f) "${gCd.toInt()}" else "GLYPH", 0xCC0C4A6E.toInt(), 0xFF38BDF8.toInt(), null)
    }

    private fun drawInventoryBar(canvas: Canvas) {
        val ph = world.playerHero
        for (i in 0..5) {
            val r = itemRects[i]
            val slot = ph.inventory.getOrNull(i)
            paint.style = Paint.Style.FILL
            paint.color = 0xCC0E131D.toInt()
            canvas.drawRoundRect(r, 8f, 8f, paint)

            if (slot != null) {
                val item = ItemCatalog.get(slot.itemId)
                val icon = item?.let { DotaAssetLoader.getItemIcon(it.id) }
                if (icon != null) {
                    canvas.drawBitmap(icon, null, RectF(r.left + 3f, r.top + 3f, r.right - 3f, r.bottom - 3f), paint)
                }
                if (slot.cooldownRemaining > 0f) {
                    paint.color = 0xAA000000.toInt()
                    canvas.drawRoundRect(r, 8f, 8f, paint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 14f
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("${slot.cooldownRemaining.toInt()}", r.centerX(), r.centerY() + 5f, textPaint)
                } else if (slot.stacks > 1 || item?.id == "magic_wand") {
                    textPaint.color = 0xFFFDE047.toInt()
                    textPaint.textSize = 12f
                    textPaint.textAlign = Paint.Align.RIGHT
                    val badge = if (item?.id == "magic_wand") "${slot.charges}" else "x${slot.stacks}"
                    canvas.drawText(badge, r.right - 4f, r.bottom - 4f, textPaint)
                }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (slot != null) 0xFFF59E0B.toInt() else 0xFF334155.toInt()
            canvas.drawRoundRect(r, 8f, 8f, paint)
        }
    }

    private fun drawCancelCastZone(canvas: Canvas) {
        val bg = if (aimingCancelHovered) 0xEEEF4444.toInt() else 0xCC7F1D1D.toInt()
        drawCircleBtn(canvas, cancelZoneCenter, cancelZoneRadius, "CANCEL X", bg, 0xFFFCA5A5.toInt(), null)
    }

    private fun drawDeadAndAnnouncements(canvas: Canvas, w: Float, h: Float) {
        val ph = world.playerHero
        if (!ph.alive) {
            textPaint.color = 0xFFEF4444.toInt()
            textPaint.textSize = h * 0.052f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val msg = if (ph.aegisRespawnTimer > 0f) "AEGIS REINCARNATING: ${"%.1f".format(ph.aegisRespawnTimer)}s"
            else "DEAD — RESPAWN IN ${ph.respawnTimer.toInt()}s"
            canvas.drawText(msg, w * 0.5f, h * 0.56f, textPaint)
            textPaint.typeface = Typeface.DEFAULT
            if (ph.aegisRespawnTimer <= 0f && ph.buybackCooldown <= 0f) {
                val cost = ph.buybackCost()
                drawRoundRectBtn(canvas, buybackRect, "BUYBACK (${cost}g)",
                    if (ph.gold >= cost) 0xEEB45309.toInt() else 0xAA1E293B.toInt(), 0xFFFACC15.toInt())
            }
        }

        world.announcements.lastOrNull()?.let { ann ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCC090D16.toInt()
            rectF.set(w * 0.25f, h * 0.16f, w * 0.75f, h * 0.27f)
            canvas.drawRoundRect(rectF, 14f, 14f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.color = ann.color
            textPaint.textSize = h * 0.040f
            canvas.drawText(ann.title, w * 0.5f, h * 0.21f, textPaint)
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.WHITE
            textPaint.textSize = h * 0.025f
            canvas.drawText(ann.subtitle, w * 0.5f, h * 0.25f, textPaint)
        }
    }

    private fun drawCircleBtn(canvas: Canvas, c: Vec2, r: Float, label: String, bg: Int, border: Int, icon: Bitmap?) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawCircle(c.x, c.y, r, paint)
        if (icon != null) {
            canvas.drawBitmap(icon, null, RectF(c.x - r * 0.72f, c.y - r * 0.72f, c.x + r * 0.72f, c.y + r * 0.72f), paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = border
        canvas.drawCircle(c.x, c.y, r, paint)

        if (label.isNotEmpty()) {
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = (r * 0.34f).coerceIn(11f, 20f)
            textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
            canvas.drawText(label, c.x, c.y + textPaint.textSize * 0.36f, textPaint)
            textPaint.clearShadowLayer()
            textPaint.typeface = Typeface.DEFAULT
        }
    }

    private fun drawRoundRectBtn(canvas: Canvas, r: RectF, label: String, bg: Int, border: Int) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawRoundRect(r, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = border
        canvas.drawRoundRect(r, 10f, 10f, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (r.height() * 0.33f).coerceIn(11f, 16f)
        canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.35f, textPaint)
        textPaint.typeface = Typeface.DEFAULT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        val sx = event.getX(idx)
        val sy = event.getY(idx)
        val w = width.toFloat().coerceAtLeast(1f)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (minimapRect.contains(sx, sy)) {
                    minimapPointerId = pid
                    panFromMinimap(sx, sy)
                    return true
                }
                if (camBtnRect.contains(sx, sy)) {
                    renderer3D.cameraLocked = !renderer3D.cameraLocked
                    if (renderer3D.cameraLocked) renderer3D.cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
                    return true
                }
                if (shopBtnRect.contains(sx, sy)) { listener.onOpenShop(); return true }
                if (talentBtnRect.contains(sx, sy)) { listener.onOpenTalents(); return true }
                if (statsBtnRect.contains(sx, sy)) { listener.onOpenScoreboard(); return true }
                if (pauseBtnRect.contains(sx, sy)) { listener.onOpenPauseMenu(); return true }

                for (qi in 0..1) {
                    if (quickBuyRects[qi].contains(sx, sy)) {
                        val suggestions = buildQuickBuySuggestions()
                        suggestions.getOrNull(qi)?.let { id ->
                            if (world.buyItem(world.playerHero, id)) world.dispatchCourier(world.playerHero)
                        }
                        return true
                    }
                }
                if (!world.playerHero.alive && buybackRect.contains(sx, sy)) {
                    world.buyback(world.playerHero)
                    return true
                }
                // Attack Cluster
                if (hypot(sx - attackCenter.x, sy - attackCenter.y) <= attackRadius + 10f) {
                    world.playerSmartAttack(preferCreep = false, preferDeny = false)
                    return true
                }
                if (hypot(sx - minionCenter.x, sy - minionCenter.y) <= subRadius + 10f) {
                    world.playerSmartAttack(preferCreep = true, preferDeny = false)
                    return true
                }
                if (hypot(sx - towerDenyCenter.x, sy - towerDenyCenter.y) <= subRadius + 10f) {
                    world.playerSmartAttack(preferCreep = false, preferDeny = true, preferStructure = true)
                    return true
                }
                // Level up badges
                for (i in 0..3) {
                    if (world.playerHero.canLevelAbility(i) && levelUpRects[i].contains(sx, sy)) {
                        world.levelUpAbility(world.playerHero, i)
                        return true
                    }
                }
                // Ability Arc (MLBB Drag-to-Aim + Cancel X)
                for (i in 0..3) {
                    val c = abilityCenters[i]
                    if (hypot(sx - c.x, sy - c.y) <= abilityRadius + 10f) {
                        val ab = world.playerHero.def.abilities[i]
                        if (ab.targetType == TargetType.INSTANT || ab.targetType == TargetType.TOGGLE || ab.targetType == TargetType.PASSIVE) {
                            world.castAbility(world.playerHero, i, defaultAutoAim(ab.castRange))
                        } else {
                            aimPointerId = pid
                            aimStartPos.set(c.x, c.y)
                            aimCurrentPos.set(c.x, c.y)
                            aimingCancelHovered = false
                            renderer3D.aimingAbilityIdx = i
                            val auto = defaultAutoAim(ab.castRange)
                            renderer3D.aimWorldPos.set(auto.x, auto.y)
                        }
                        return true
                    }
                }
                // Utility buttons
                if (hypot(sx - tpCenter.x, sy - tpCenter.y) <= utilRadius + 8f) {
                    world.useTpScroll(world.playerHero, renderer3D.cameraPos)
                    return true
                }
                if (hypot(sx - courierCenter.x, sy - courierCenter.y) <= utilRadius + 8f) {
                    world.dispatchCourier(world.playerHero)
                    return true
                }
                if (hypot(sx - glyphCenter.x, sy - glyphCenter.y) <= utilRadius + 8f) {
                    world.activateGlyph(world.playerTeam)
                    return true
                }
                // Inventory items
                for (i in 0..5) {
                    if (itemRects[i].contains(sx, sy)) {
                        val aim = world.playerHero.pos + Vec2.fromAngle(world.playerHero.facingAngle, 450f)
                        world.useItemSlot(world.playerHero, i, aim)
                        return true
                    }
                }
                // Left side -> MLBB Floating/Clamped Thumbstick!
                if (sx < w * 0.44f && joystickPointerId == -1) {
                    joystickPointerId = pid
                    joystickActive = true
                    joystickBase.set(sx, sy)
                    joystickKnob.set(sx, sy)
                    return true
                }
                // Right/Upper viewport drag -> Pan camera OR tap enemy to target lock!
                val worldPt = renderer3D.screenToWorld(sx, sy)
                val tappedEnemy = world.allEnemyUnits(world.playerTeam, worldPt, 85f).minByOrNull { it.pos.distTo(worldPt) }
                if (tappedEnemy != null) {
                    world.playerHero.attackTargetId = tappedEnemy.id
                    world.spawnParticles(tappedEnemy.pos, 0xFFEF4444.toInt(), 6, 40f)
                } else if (panPointerId == -1) {
                    panPointerId = pid
                    panLastPos.set(sx, sy)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    val mx = event.getX(i)
                    val my = event.getY(i)
                    if (p == minimapPointerId) {
                        panFromMinimap(mx, my)
                    } else if (p == joystickPointerId) {
                        val dx = mx - joystickBase.x
                        val dy = my - joystickBase.y
                        val dist = hypot(dx, dy)
                        val maxR = 72f
                        if (dist > 8f) {
                            val clamped = dist.coerceAtMost(maxR)
                            joystickKnob.set(joystickBase.x + (dx / dist) * clamped, joystickBase.y + (dy / dist) * clamped)
                            world.playerHero.moveIntent.set(dx / dist, dy / dist)
                        } else {
                            joystickKnob.set(joystickBase.x, joystickBase.y)
                            world.playerHero.moveIntent.set(0f, 0f)
                        }
                    } else if (p == aimPointerId && renderer3D.aimingAbilityIdx in 0..3) {
                        aimingCancelHovered = hypot(mx - cancelZoneCenter.x, my - cancelZoneCenter.y) <= cancelZoneRadius + 24f
                        val dx = mx - aimStartPos.x
                        val dy = my - aimStartPos.y
                        val dist = hypot(dx, dy)
                        val maxR = abilityRadius * 1.45f
                        if (dist > 14f) {
                            val clamped = dist.coerceAtMost(maxR)
                            aimCurrentPos.set(aimStartPos.x + (dx / dist) * clamped, aimStartPos.y + (dy / dist) * clamped)
                            val ab = world.playerHero.def.abilities[renderer3D.aimingAbilityIdx]
                            val range = ab.castRange.takeIf { it > 0f } ?: 650f
                            val factor = (dist / 120f).coerceIn(0.20f, 1.0f)
                            renderer3D.aimWorldPos.set(
                                world.playerHero.pos.x + (dx / dist) * range * factor,
                                world.playerHero.pos.y + (dy / dist) * range * factor
                            )
                        }
                    } else if (p == panPointerId) {
                        val dx = mx - panLastPos.x
                        val dy = my - panLastPos.y
                        if (hypot(dx, dy) > 4f) {
                            renderer3D.cameraLocked = false
                            renderer3D.cameraPos.x = (renderer3D.cameraPos.x - dx * 1.6f).coerceIn(0f, MapLayout.SIZE)
                            renderer3D.cameraPos.y = (renderer3D.cameraPos.y - dy * 1.6f).coerceIn(0f, MapLayout.SIZE)
                            panLastPos.set(mx, my)
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pid == minimapPointerId) minimapPointerId = -1
                if (pid == panPointerId) panPointerId = -1
                if (pid == joystickPointerId) {
                    joystickPointerId = -1
                    joystickActive = false
                    world.playerHero.moveIntent.set(0f, 0f)
                }
                if (pid == aimPointerId) {
                    val idxAb = renderer3D.aimingAbilityIdx
                    if (idxAb in 0..3 && !aimingCancelHovered) {
                        world.castAbility(world.playerHero, idxAb, renderer3D.aimWorldPos)
                    }
                    aimPointerId = -1
                    aimingCancelHovered = false
                    renderer3D.aimingAbilityIdx = -1
                }
            }
        }
        return true
    }

    private fun panFromMinimap(sx: Float, sy: Float) {
        val nx = ((sx - minimapRect.left) / minimapRect.width()).coerceIn(0f, 1f)
        val ny = ((sy - minimapRect.top) / minimapRect.height()).coerceIn(0f, 1f)
        renderer3D.cameraLocked = false
        renderer3D.cameraPos.set(nx * MapLayout.SIZE, ny * MapLayout.SIZE)
    }

    private fun defaultAutoAim(range: Float): Vec2 {
        val h = world.playerHero
        val searchR = range.takeIf { it > 0f } ?: 650f
        val target = world.allEnemyUnits(h.team, h.pos, searchR)
            .filter { !it.isInvisible() }
            .minByOrNull { (if (it is HeroEntity) 0f else 500f) + it.pos.distTo(h.pos) }
        return target?.pos?.copyVec() ?: (h.pos + Vec2.fromAngle(h.facingAngle, searchR * 0.6f))
    }

    private fun buildQuickBuySuggestions(): List<String> {
        val ph = world.playerHero
        return buildList {
            ph.quickBuyItemId?.let { add(it) }
            val fallback = listOf("boots", "magic_wand", "power_treads", "bkb", "daedalus")
            for (f in fallback) if (size < 2 && !ph.hasItem(f) && f !in this) add(f)
        }.take(2)
    }
}
