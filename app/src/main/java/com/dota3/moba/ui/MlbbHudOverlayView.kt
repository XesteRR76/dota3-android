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
import com.dota3.moba.render.BoxBounds
import com.dota3.moba.render.HudGeometry
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

    private val geo = HudGeometry()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private var minimapBg: Bitmap? = null

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val uiScale = (h / 540f).coerceIn(0.8f, 2.5f)
        ensureMinimapBg()
        geo.layout(w, h)

        if (world.matchOver && !matchEndNotified) {
            matchEndNotified = true
            post { listener.onMatchEnded() }
        }

        // 1. Projected 3D HP/MP Bars, Hero Portraits & Floating Damage/Crit Text
        drawProjectedWorldOverlays(canvas, uiScale)

        // 2. Minimap, Camera Lock & MLBB Quick-Buy Cards
        drawMinimap(canvas)
        drawBoxBtn(canvas, geo.camBtn, if (renderer3D.cameraLocked) "CAM LOCKED" else "FREE CAM",
            0xCC111827.toInt(), if (renderer3D.cameraLocked) 0xFF38BDF8.toInt() else 0xFFF59E0B.toInt())
        drawQuickBuyCards(canvas)

        // 3. Top Match Header
        drawTopHeader(canvas, w, h)

        // 4. Right Menu Buttons
        val ph = world.playerHero
        drawBoxBtn(canvas, geo.shopBtn, "SHOP ${ph.gold}g", 0xDD78350F.toInt(), 0xFFF59E0B.toInt())
        val hasTalent = ph.availableTalentTier() != null
        drawBoxBtn(canvas, geo.talentBtn, if (hasTalent) "TALENT!" else "TALENTS",
            if (hasTalent) 0xDD065F46.toInt() else 0xCC1E293B.toInt(),
            if (hasTalent) 0xFF34D399.toInt() else 0xFF64748B.toInt())
        drawBoxBtn(canvas, geo.statsBtn, "SCORE", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())
        drawBoxBtn(canvas, geo.pauseBtn, "MENU", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())

        // 5. Left MLBB Joystick
        drawJoystick(canvas)

        // 6. Bottom-Center Hero HP/MP/Stats Plate & 6 Active Item Slots
        drawHeroStatusPlate(canvas)
        drawInventoryBar(canvas, uiScale)

        // 7. Right MLBB Attack Cluster, Ability Arc & Utilities
        drawAttackCluster(canvas)
        drawAbilityArc(canvas, uiScale)
        drawUtilityButtons(canvas)

        // 8. Cancel X zone during drag-aim
        if (renderer3D.aimingAbilityIdx in 0..3) {
            val bg = if (aimingCancelHovered) 0xEEEF4444.toInt() else 0xCC7F1D1D.toInt()
            drawCircleBtn(canvas, geo.cancelCenter, geo.cancelRadius, "CANCEL X", bg, 0xFFFCA5A5.toInt(), null)
        }

        // 9. Dead/Buyback & Announcements
        drawDeadAndAnnouncements(canvas, w, h)

        postInvalidateOnAnimation()
    }

    private fun drawProjectedWorldOverlays(canvas: Canvas, uiScale: Float) {
        for (bar in renderer3D.projectedBars) {
            if (bar.isHero) {
                val barW = 72f * uiScale
                val hpH = 7.5f * uiScale
                val mpH = 4.2f * uiScale
                val left = bar.sx - barW * 0.5f
                val hpTop = bar.sy - 14f * uiScale

                bar.heroId?.let { hid ->
                    DotaAssetLoader.getHeroCirclePortrait(hid, 48)?.let { bmp ->
                        val pr = 12f * uiScale
                        val cx = left - pr - 3f
                        val cy = hpTop + hpH * 0.6f
                        canvas.drawBitmap(bmp, null, RectF(cx - pr, cy - pr, cx + pr, cy + pr), paint)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f * uiScale * 0.6f
                        paint.color = bar.barColor
                        canvas.drawCircle(cx, cy, pr, paint)
                    }
                }

                drawBar(canvas, left, hpTop, barW, hpH, bar.hp, bar.maxHp, bar.barColor, bar.notchStep)
                drawBar(canvas, left, hpTop + hpH + 1.5f, barW, mpH, bar.mana, bar.maxMana, 0xFF38BDF8.toInt(), 0f)

                textPaint.color = Color.WHITE
                textPaint.textSize = 11.5f * uiScale
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.setShadowLayer(3f, 0f, 1.5f, Color.BLACK)
                canvas.drawText("Lv${bar.level} ${bar.label}", bar.sx, hpTop - 4f, textPaint)
                textPaint.clearShadowLayer()
                textPaint.typeface = Typeface.DEFAULT
            } else {
                val barW = (if (bar.notchStep > 0f) 76f else 42f) * uiScale
                val barH = (if (bar.notchStep > 0f) 7f else 4.5f) * uiScale
                drawBar(canvas, bar.sx - barW * 0.5f, bar.sy - 6f, barW, barH, bar.hp, bar.maxHp, bar.barColor, bar.notchStep)
                if (bar.label.isNotEmpty()) {
                    textPaint.color = 0xFFFDE047.toInt()
                    textPaint.textSize = 11f * uiScale
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(bar.label, bar.sx, bar.sy - 10f, textPaint)
                }
            }
        }

        for (ft in renderer3D.projectedTexts) {
            textPaint.color = ft.color
            textPaint.textSize = 16.5f * uiScale * ft.scale
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
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
            linePaint.strokeWidth = 1.6f
            var cur = notch
            while (cur < valMax) {
                val nx = left + w * (cur / valMax)
                canvas.drawLine(nx, top, nx, top + h, linePaint)
                cur += notch
            }
        }
    }

    private fun drawMinimap(canvas: Canvas) {
        val mm = geo.minimap
        rectF.set(mm.left, mm.top, mm.right, mm.bottom)
        minimapBg?.let { canvas.drawBitmap(it, null, rectF, null) }
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
        val cx = mm.left + renderer3D.cameraPos.x * sx
        val cy = mm.top + renderer3D.cameraPos.y * sy
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawRect((cx - 18f).coerceAtLeast(mm.left), (cy - 12f).coerceAtLeast(mm.top),
            (cx + 18f).coerceAtMost(mm.right), (cy + 12f).coerceAtMost(mm.bottom), paint)

        paint.strokeWidth = 2.5f
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawRect(rectF, paint)
    }

    private fun drawQuickBuyCards(canvas: Canvas) {
        val ph = world.playerHero
        val suggestions = buildQuickBuySuggestions()
        for (i in suggestions.indices) {
            val item = ItemCatalog.get(suggestions[i]) ?: continue
            val b = geo.quickBuy[i]
            val effCost = world.effectiveItemCost(ph, item)
            val canAfford = ph.gold >= effCost

            rectF.set(b.left, b.top, b.right, b.bottom)
            paint.style = Paint.Style.FILL
            paint.color = if (canAfford) 0xDD064E3B.toInt() else 0xCC111827.toInt()
            canvas.drawRoundRect(rectF, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (canAfford) 0xFF34D399.toInt() else 0xFF475569.toInt()
            canvas.drawRoundRect(rectF, 10f, 10f, paint)

            DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                val pad = 4f
                val iconH = b.height() - pad * 2f
                canvas.drawBitmap(bmp, null, RectF(b.left + pad, b.top + pad, b.left + pad + iconH * 1.25f, b.bottom - pad), paint)
            }
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = (b.height() * 0.34f).coerceIn(11f, 18f)
            textPaint.color = if (canAfford) 0xFFFDE047.toInt() else 0xFFCBD5E1.toInt()
            canvas.drawText("${item.shortCode} ${effCost}g", b.right - 8f, b.centerY() + 5f, textPaint)
        }
    }

    private fun drawTopHeader(canvas: Canvas, w: Float, h: Float) {
        val topW = w * 0.28f
        val topH = h * 0.072f
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
        textPaint.textSize = topH * 0.35f
        textPaint.color = 0xFFFDE047.toInt()
        canvas.drawText("GOLD ${ph.gold}  •  ${ph.kills}/${ph.deaths}/${ph.assists}  •  CS ${ph.lastHits}/${ph.denies}",
            w * 0.5f, 6f + topH + topH * 0.44f, textPaint)
    }

    private fun drawJoystick(canvas: Canvas) {
        val center = if (joystickActive) joystickBase else geo.joystickRestCenter
        val rOuter = geo.joystickRadius
        paint.style = Paint.Style.FILL
        paint.color = if (joystickActive) 0x4438BDF8 else 0x22FFFFFF
        canvas.drawCircle(center.x, center.y, rOuter, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = if (joystickActive) 0xAA38BDF8.toInt() else 0x55CBD5E1
        canvas.drawCircle(center.x, center.y, rOuter, paint)

        val knob = if (joystickActive) joystickKnob else center
        paint.style = Paint.Style.FILL
        paint.color = if (joystickActive) 0xDD38BDF8.toInt() else 0x8894A3B8.toInt()
        canvas.drawCircle(knob.x, knob.y, rOuter * 0.42f, paint)
    }

    private fun drawHeroStatusPlate(canvas: Canvas) {
        val ph = world.playerHero
        val p = geo.heroPlate
        if (p.width() < 120f) return
        rectF.set(p.left, p.top, p.right, p.bottom)
        paint.style = Paint.Style.FILL
        paint.color = 0xCC0D1320.toInt()
        canvas.drawRoundRect(rectF, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = 0xFF334155.toInt()
        canvas.drawRoundRect(rectF, 10f, 10f, paint)

        val padX = 8f
        val innerW = p.width() - padX * 2f
        val rowH = (p.height() - 10f) / 3f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (rowH * 0.68f).coerceIn(11f, 20f)
        textPaint.color = 0xFFF5B041.toInt()
        val tags = buildString {
            append("Lv${ph.level}  DMG:${ph.cachedDamage.toInt()}  ARM:${ph.armor.toInt()}  MS:${ph.cachedMoveSpeed.toInt()}")
            if (ph.hasScepter) append(" [AGH]")
            if (ph.hasShard) append(" [SHD]")
            if (ph.hasAegis) append(" [AEGIS]")
        }
        canvas.drawText(tags, p.left + padX, p.top + rowH * 0.78f, textPaint)

        // HP Bar
        val hpY = p.top + rowH + 3f
        val hpPct = (ph.hp / ph.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF14291E.toInt()
        canvas.drawRect(p.left + padX, hpY, p.left + padX + innerW, hpY + rowH * 0.85f, paint)
        paint.color = 0xFF22C55E.toInt()
        canvas.drawRect(p.left + padX, hpY, p.left + padX + innerW * hpPct, hpY + rowH * 0.85f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = (rowH * 0.62f).coerceIn(10f, 18f)
        canvas.drawText("${ph.hp.toInt()} / ${ph.maxHp.toInt()}  (+${"%.1f".format(ph.cachedHpRegen)})",
            p.centerX(), hpY + rowH * 0.66f, textPaint)

        // Mana Bar
        val mpY = hpY + rowH + 2f
        val mpPct = (ph.mana / ph.maxMana.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.color = 0xFF112238.toInt()
        canvas.drawRect(p.left + padX, mpY, p.left + padX + innerW, mpY + rowH * 0.85f, paint)
        paint.color = 0xFF38BDF8.toInt()
        canvas.drawRect(p.left + padX, mpY, p.left + padX + innerW * mpPct, mpY + rowH * 0.85f, paint)
        textPaint.color = Color.WHITE
        canvas.drawText("${ph.mana.toInt()} / ${ph.maxMana.toInt()}  (+${"%.1f".format(ph.cachedManaRegen)})",
            p.centerX(), mpY + rowH * 0.66f, textPaint)
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawAttackCluster(canvas: Canvas) {
        drawCircleBtn(canvas, geo.attackCenter, geo.attackRadius, "ATK", 0xDDB91C1C.toInt(), 0xFFF87171.toInt(), null)
        drawCircleBtn(canvas, geo.minionCenter, geo.subRadius, "CREEP", 0xDD92400E.toInt(), 0xFFFBBF24.toInt(), null)
        drawCircleBtn(canvas, geo.towerDenyCenter, geo.subRadius, "TWR/!", 0xDD0369A1.toInt(), 0xFF38BDF8.toInt(), null)
    }

    private fun drawAbilityArc(canvas: Canvas, uiScale: Float) {
        val ph = world.playerHero
        val r = geo.abilityRadius
        for (i in 0..3) {
            val ab = ph.def.abilities[i]
            val c = geo.abilityCenters[i]
            val lvl = ph.abilityLevels[i]
            val cd = ph.abilityCooldowns[i]
            val icon = DotaAssetLoader.getAbilityCircleIcon(ab.id, 128)
            val border = when {
                renderer3D.aimingAbilityIdx == i -> 0xFF38BDF8.toInt()
                ab.isUltimate -> 0xFFF59E0B.toInt()
                lvl > 0 -> 0xFF60A5FA.toInt()
                else -> 0xFF475569.toInt()
            }

            if (icon != null) {
                canvas.drawBitmap(icon, null, RectF(c.x - r, c.y - r, c.x + r, c.y + r), paint)
                if (lvl == 0 || cd > 0f || ph.mana < ab.manaCost.getOrElse((lvl - 1).coerceAtLeast(0)) { 0 }) {
                    paint.style = Paint.Style.FILL
                    paint.color = 0xAA090D16.toInt()
                    canvas.drawCircle(c.x, c.y, r, paint)
                }
            } else {
                paint.style = Paint.Style.FILL
                paint.color = 0xCC1E3A8A.toInt()
                canvas.drawCircle(c.x, c.y, r, paint)
            }

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (ab.isUltimate) 4f else 3f) * uiScale * 0.65f
            paint.color = border
            canvas.drawCircle(c.x, c.y, r, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            if (lvl == 0) {
                textPaint.color = 0xFFCBD5E1.toInt()
                textPaint.textSize = r * 0.42f
                canvas.drawText(ab.keyLabel, c.x, c.y + r * 0.14f, textPaint)
            } else if (cd > 0f) {
                textPaint.color = Color.WHITE
                textPaint.textSize = r * 0.48f
                canvas.drawText("${"%.1f".format(cd)}", c.x, c.y + r * 0.16f, textPaint)
            } else if (ab.maxCharges > 1) {
                textPaint.color = 0xFFFDE047.toInt()
                textPaint.textSize = r * 0.34f
                canvas.drawText("${ph.abilityCharges[i]}", c.x + r * 0.55f, c.y - r * 0.4f, textPaint)
            }

            for (d in 0 until ab.maxLevel) {
                paint.style = Paint.Style.FILL
                paint.color = if (d < lvl) 0xFFFACC15.toInt() else 0xFF334155.toInt()
                val dx = (d - (ab.maxLevel - 1) * 0.5f) * (r * 0.24f)
                canvas.drawCircle(c.x + dx, c.y + r - 6f, (r * 0.08f).coerceAtLeast(3f), paint)
            }

            if (ph.canLevelAbility(i)) {
                drawBoxBtn(canvas, geo.levelUpPills[i], "+ LVL", 0xEE15803D.toInt(), 0xFF4ADE80.toInt())
            }

            if (renderer3D.aimingAbilityIdx == i) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = 0xAA38BDF8.toInt()
                canvas.drawCircle(c.x, c.y, r * 1.45f, paint)
                paint.style = Paint.Style.FILL
                paint.color = 0xCC38BDF8.toInt()
                canvas.drawCircle(aimCurrentPos.x, aimCurrentPos.y, r * 0.42f, paint)
            }
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawUtilityButtons(canvas: Canvas) {
        val ph = world.playerHero
        val tpTxt = if (ph.tpCooldown > 0f) "${ph.tpCooldown.toInt()}" else "TP"
        drawCircleBtn(canvas, geo.tpCenter, geo.utilRadius, tpTxt, 0xCC1E3A8A.toInt(), 0xFF60A5FA.toInt(), DotaAssetLoader.getItemIcon("tp_scroll"))
        val stashN = ph.stash.size
        drawCircleBtn(canvas, geo.courierCenter, geo.utilRadius, if (stashN > 0) "C($stashN)" else "COUR", 0xCC713F12.toInt(), 0xFFFBBF24.toInt(), null)
        val gCd = if (ph.team == Team.RADIANT) world.radiantGlyphCd else world.direGlyphCd
        drawCircleBtn(canvas, geo.glyphCenter, geo.utilRadius, if (gCd > 0f) "${gCd.toInt()}" else "GLYPH", 0xCC0C4A6E.toInt(), 0xFF38BDF8.toInt(), null)
    }

    private fun drawInventoryBar(canvas: Canvas, uiScale: Float) {
        val ph = world.playerHero
        for (i in 0..5) {
            val b = geo.itemSlots[i]
            rectF.set(b.left, b.top, b.right, b.bottom)
            val slot = ph.inventory.getOrNull(i)
            paint.style = Paint.Style.FILL
            paint.color = 0xCC0E131D.toInt()
            canvas.drawRoundRect(rectF, 8f, 8f, paint)

            if (slot != null) {
                val item = ItemCatalog.get(slot.itemId)
                val icon = item?.let { DotaAssetLoader.getItemIcon(it.id) }
                if (icon != null) {
                    canvas.drawBitmap(icon, null, RectF(b.left + 3f, b.top + 3f, b.right - 3f, b.bottom - 3f), paint)
                }
                if (slot.cooldownRemaining > 0f) {
                    paint.color = 0xAA000000.toInt()
                    canvas.drawRoundRect(rectF, 8f, 8f, paint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = 13f * uiScale
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("${slot.cooldownRemaining.toInt()}", b.centerX(), b.centerY() + 5f, textPaint)
                } else if (slot.stacks > 1 || item?.id == "magic_wand") {
                    textPaint.color = 0xFFFDE047.toInt()
                    textPaint.textSize = 11f * uiScale
                    textPaint.textAlign = Paint.Align.RIGHT
                    val badge = if (item?.id == "magic_wand") "${slot.charges}" else "x${slot.stacks}"
                    canvas.drawText(badge, b.right - 4f, b.bottom - 4f, textPaint)
                }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (slot != null) 0xFFF59E0B.toInt() else 0xFF334155.toInt()
            canvas.drawRoundRect(rectF, 8f, 8f, paint)
        }
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
                drawBoxBtn(canvas, geo.buybackBtn, "BUYBACK (${cost}g)",
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
            textPaint.textSize = (r * 0.34f).coerceIn(11f, 24f)
            textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
            canvas.drawText(label, c.x, c.y + textPaint.textSize * 0.36f, textPaint)
            textPaint.clearShadowLayer()
            textPaint.typeface = Typeface.DEFAULT
        }
    }

    private fun drawBoxBtn(canvas: Canvas, b: BoxBounds, label: String, bg: Int, border: Int) {
        rectF.set(b.left, b.top, b.right, b.bottom)
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawRoundRect(rectF, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = border
        canvas.drawRoundRect(rectF, 10f, 10f, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (b.height() * 0.36f).coerceIn(11f, 20f)
        canvas.drawText(label, b.centerX(), b.centerY() + textPaint.textSize * 0.35f, textPaint)
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
                if (geo.minimap.contains(sx, sy)) {
                    minimapPointerId = pid
                    panFromMinimap(sx, sy)
                    return true
                }
                if (geo.camBtn.contains(sx, sy)) {
                    renderer3D.cameraLocked = !renderer3D.cameraLocked
                    if (renderer3D.cameraLocked) renderer3D.cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
                    return true
                }
                if (geo.shopBtn.contains(sx, sy)) { listener.onOpenShop(); return true }
                if (geo.talentBtn.contains(sx, sy)) { listener.onOpenTalents(); return true }
                if (geo.statsBtn.contains(sx, sy)) { listener.onOpenScoreboard(); return true }
                if (geo.pauseBtn.contains(sx, sy)) { listener.onOpenPauseMenu(); return true }

                for (qi in 0..1) {
                    if (geo.quickBuy[qi].contains(sx, sy)) {
                        buildQuickBuySuggestions().getOrNull(qi)?.let { id ->
                            if (world.buyItem(world.playerHero, id)) world.dispatchCourier(world.playerHero)
                        }
                        return true
                    }
                }
                if (!world.playerHero.alive && geo.buybackBtn.contains(sx, sy)) {
                    world.buyback(world.playerHero)
                    return true
                }
                if (hypot(sx - geo.attackCenter.x, sy - geo.attackCenter.y) <= geo.attackRadius + 10f) {
                    world.playerSmartAttack(preferCreep = false, preferDeny = false)
                    return true
                }
                if (hypot(sx - geo.minionCenter.x, sy - geo.minionCenter.y) <= geo.subRadius + 10f) {
                    world.playerSmartAttack(preferCreep = true, preferDeny = false)
                    return true
                }
                if (hypot(sx - geo.towerDenyCenter.x, sy - geo.towerDenyCenter.y) <= geo.subRadius + 10f) {
                    world.playerSmartAttack(preferCreep = false, preferDeny = true, preferStructure = true)
                    return true
                }
                for (i in 0..3) {
                    if (world.playerHero.canLevelAbility(i) && geo.levelUpPills[i].contains(sx, sy)) {
                        world.levelUpAbility(world.playerHero, i)
                        return true
                    }
                }
                for (i in 0..3) {
                    val c = geo.abilityCenters[i]
                    if (hypot(sx - c.x, sy - c.y) <= geo.abilityRadius + 10f) {
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
                if (hypot(sx - geo.tpCenter.x, sy - geo.tpCenter.y) <= geo.utilRadius + 8f) {
                    world.useTpScroll(world.playerHero, renderer3D.cameraPos)
                    return true
                }
                if (hypot(sx - geo.courierCenter.x, sy - geo.courierCenter.y) <= geo.utilRadius + 8f) {
                    world.dispatchCourier(world.playerHero)
                    return true
                }
                if (hypot(sx - geo.glyphCenter.x, sy - geo.glyphCenter.y) <= geo.utilRadius + 8f) {
                    world.activateGlyph(world.playerTeam)
                    return true
                }
                for (i in 0..5) {
                    if (geo.itemSlots[i].contains(sx, sy)) {
                        val aim = world.playerHero.pos + Vec2.fromAngle(world.playerHero.facingAngle, 450f)
                        world.useItemSlot(world.playerHero, i, aim)
                        return true
                    }
                }
                if (sx < w * 0.44f && joystickPointerId == -1) {
                    joystickPointerId = pid
                    joystickActive = true
                    joystickBase.set(sx, sy)
                    joystickKnob.set(sx, sy)
                    return true
                }
                val worldPt = renderer3D.screenToWorld(sx, sy)
                val tappedEnemy = world.allEnemyUnits(world.playerTeam, worldPt, 95f).minByOrNull { it.pos.distTo(worldPt) }
                if (tappedEnemy != null) {
                    world.playerHero.attackTargetId = tappedEnemy.id
                    world.playerHero.facingAngle = world.playerHero.pos.angleTo(tappedEnemy.pos)
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
                        val maxR = geo.joystickRadius
                        if (dist > 8f) {
                            val clamped = dist.coerceAtMost(maxR)
                            joystickKnob.set(joystickBase.x + (dx / dist) * clamped, joystickBase.y + (dy / dist) * clamped)
                            world.playerHero.moveIntent.set(dx / dist, dy / dist)
                        } else {
                            joystickKnob.set(joystickBase.x, joystickBase.y)
                            world.playerHero.moveIntent.set(0f, 0f)
                        }
                    } else if (p == aimPointerId && renderer3D.aimingAbilityIdx in 0..3) {
                        aimingCancelHovered = hypot(mx - geo.cancelCenter.x, my - geo.cancelCenter.y) <= geo.cancelRadius + 26f
                        val dx = mx - aimStartPos.x
                        val dy = my - aimStartPos.y
                        val dist = hypot(dx, dy)
                        val maxR = geo.abilityRadius * 1.45f
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
        val mm = geo.minimap
        val nx = ((sx - mm.left) / mm.width()).coerceIn(0f, 1f)
        val ny = ((sy - mm.top) / mm.height()).coerceIn(0f, 1f)
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
