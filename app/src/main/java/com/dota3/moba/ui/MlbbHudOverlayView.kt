package com.dota3.moba.ui

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.dota3.moba.core.DotaAssetLoader
import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.*
import com.dota3.moba.model.PrimaryAttribute
import com.dota3.moba.model.StatusType
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
    private val clipPath = Path()
    private var minimapBg: Bitmap? = null

    // Touch state
    private var joystickPointerId = -1
    private var joystickBase = Vec2()
    private var joystickKnob = Vec2()
    private var joystickActive = false

    private var aimPointerId = -1
    private var aimStartPos = Vec2()
    private var aimCurrentPos = Vec2()
    private var aimManualMoved = false
    private var aimingCancelHovered = false

    private var attackPointerId = -1
    private var heldAttackMode = -1 // 0=hero, 1=creep, 2=tower/deny
    private var lastAttackRepeatMs = 0L

    private var minimapPointerId = -1
    private var panPointerId = -1
    private var panLastPos = Vec2()

    private var matchEndNotified = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer3D.cameraZoomDist = (renderer3D.cameraZoomDist / detector.scaleFactor).coerceIn(520f, 980f)
            return true
        }
    })

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
                intArrayOf(0xFF163222.toInt(), 0xFF1D293B.toInt(), 0xFF36181E.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), bg)

        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF196792.toInt(); style = Paint.Style.STROKE; strokeWidth = 250f * scale
        }
        c.drawLine(450f * scale, 450f * scale, 4350f * scale, 4350f * scale, rp)

        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF566376.toInt(); style = Paint.Style.STROKE; strokeWidth = 170f * scale; strokeCap = Paint.Cap.ROUND
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

        synchronized(world) {
            // Hold-to-attack repeat while ATK/CREEP/TWR button is pressed
            if (heldAttackMode >= 0 && attackPointerId != -1) {
                val nowMs = SystemClock.uptimeMillis()
                if (nowMs - lastAttackRepeatMs >= 260L) {
                    lastAttackRepeatMs = nowMs
                    when (heldAttackMode) {
                        0 -> world.playerSmartAttack(preferCreep = false, preferDeny = false, silentNoTarget = true)
                        1 -> world.playerSmartAttack(preferCreep = true, preferDeny = false, silentNoTarget = true)
                        2 -> world.playerSmartAttack(preferCreep = false, preferDeny = true, preferStructure = true, silentNoTarget = true)
                    }
                }
            }

            if (world.matchOver && !matchEndNotified) {
                matchEndNotified = true
                post { listener.onMatchEnded() }
            }

            // 1. Projected 3D HP/MP Bars & Floating Damage/Crit Numbers
            drawProjectedWorldOverlays(canvas, uiScale)

            // 2. Minimap, Camera Lock, Zoom & Quick-Buy Cards
            drawMinimap(canvas)
            drawBoxBtn(canvas, geo.camBtn, if (renderer3D.cameraLocked) "CAM LOCK" else "FREE CAM",
                0xCC111827.toInt(), if (renderer3D.cameraLocked) 0xFF38BDF8.toInt() else 0xFFF59E0B.toInt())
            val zoomTag = when {
                renderer3D.cameraZoomDist < 640f -> "Z+"
                renderer3D.cameraZoomDist > 780f -> "Z-"
                else -> "ZOOM"
            }
            drawBoxBtn(canvas, geo.zoomBtn, zoomTag, 0xCC111827.toInt(), 0xFF94A3B8.toInt())
            drawQuickBuyCards(canvas)

            // 3. Top Match Header
            drawTopHeader(canvas, w, h)

            // 4. Top-Right Menu Buttons
            val ph = world.playerHero
            drawBoxBtn(canvas, geo.shopBtn, "SHOP ${ph.gold}g", 0xDD78350F.toInt(), 0xFFF59E0B.toInt())
            val hasTalent = ph.availableTalentTier() != null
            drawBoxBtn(canvas, geo.talentBtn, if (hasTalent) "TALENT!" else "TALENTS",
                if (hasTalent) 0xDD065F46.toInt() else 0xCC1E293B.toInt(),
                if (hasTalent) 0xFF34D399.toInt() else 0xFF64748B.toInt())
            drawBoxBtn(canvas, geo.statsBtn, "SCORE", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())
            drawBoxBtn(canvas, geo.pauseBtn, "MENU", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())

            // 5. Left Ergonomic Joystick
            drawJoystick(canvas)

            // 6. Bottom-Center Authentic Dota 2 Hero Console (Portrait, Level, XP, Attributes, Stats, HP/MP, 3x2 Items, TP, Neutral)
            drawActiveBuffsStrip(canvas, uiScale)
            drawDotaHeroConsole(canvas, uiScale)

            // 7. Right MLBB Attack Cluster, Skill Fan & Courier/Glyph
            drawAttackCluster(canvas)
            drawAbilityArc(canvas, uiScale)
            drawUtilityButtons(canvas)

            // 8. Aim indicator for active items & Cancel X zone during skill/item drag-aim
            if (renderer3D.aimingItemSlotIdx >= 0) {
                val ringCx = aimStartPos.x.coerceIn(geo.abilityRadius * 1.6f, w - geo.abilityRadius * 1.6f)
                val ringCy = aimStartPos.y.coerceIn(geo.abilityRadius * 1.6f, h - geo.abilityRadius * 1.6f)
                val ringR = geo.abilityRadius * 1.35f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.4f
                paint.color = 0xAA38BDF8.toInt()
                canvas.drawCircle(ringCx, ringCy, ringR, paint)
                linePaint.color = 0xAA38BDF8.toInt()
                linePaint.strokeWidth = 2.8f
                canvas.drawLine(ringCx, ringCy, aimCurrentPos.x, aimCurrentPos.y, linePaint)
                paint.style = Paint.Style.FILL
                paint.color = 0xCC38BDF8.toInt()
                canvas.drawCircle(aimCurrentPos.x, aimCurrentPos.y, ringR * 0.30f, paint)
            }
            if (renderer3D.aimingAbilityIdx in 0..3 || renderer3D.aimingItemSlotIdx >= 0) {
                val bg = if (aimingCancelHovered) 0xEEEF4444.toInt() else 0xCC7F1D1D.toInt()
                drawCircleBtn(canvas, geo.cancelCenter, geo.cancelRadius, "CANCEL X", bg, 0xFFFCA5A5.toInt(), null)
            }

            // 9. Dead/Buyback & Announcements
            drawDeadAndAnnouncements(canvas, w, h)
        }

        postInvalidateOnAnimation()
    }

    private fun drawProjectedWorldOverlays(canvas: Canvas, uiScale: Float) {
        for (bar in renderer3D.projectedBars) {
            if (bar.isHero) {
                val barW = 74f * uiScale
                val hpH = 7.5f * uiScale
                val mpH = 4.2f * uiScale
                val left = bar.sx - barW * 0.5f
                val hpTop = bar.sy - 14f * uiScale

                // Clean Dota-style level box on left of HP bar without overlapping label
                val lvlR = 9.5f * uiScale
                val lx = left - lvlR - 3f
                val ly = hpTop + (hpH + mpH) * 0.5f
                paint.style = Paint.Style.FILL
                paint.color = 0xEE0F172A.toInt()
                canvas.drawCircle(lx, ly, lvlR, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.8f
                paint.color = 0xFFF59E0B.toInt()
                canvas.drawCircle(lx, ly, lvlR, paint)
                textPaint.color = 0xFFFDE047.toInt()
                textPaint.textSize = 9.5f * uiScale
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("${bar.level}", lx, ly + 3.5f * uiScale, textPaint)

                drawBar(canvas, left, hpTop, barW, hpH, bar.hp, bar.maxHp, bar.barColor, bar.notchStep)
                drawBar(canvas, left, hpTop + hpH + 1.5f, barW, mpH, bar.mana, bar.maxMana, 0xFF38BDF8.toInt(), 0f)

                textPaint.color = Color.WHITE
                textPaint.textSize = 11f * uiScale
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.setShadowLayer(3f, 0f, 1.5f, Color.BLACK)
                canvas.drawText(bar.label, bar.sx, hpTop - 4.5f, textPaint)
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
        paint.color = 0xFFD4A037.toInt()
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
            canvas.drawRoundRect(rectF, 9f, 9f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (canAfford) 0xFF34D399.toInt() else 0xFF475569.toInt()
            canvas.drawRoundRect(rectF, 9f, 9f, paint)

            DotaAssetLoader.getItemIcon(item.id)?.let { bmp ->
                val pad = 4f
                val iconH = b.height() - pad * 2f
                canvas.drawBitmap(bmp, null, RectF(b.left + pad, b.top + pad, b.left + pad + iconH * 1.25f, b.bottom - pad), paint)
            }
            val qbTxt = "${item.shortCode} ${effCost}g"
            val iconRight = b.left + 4f + (b.height() - 8f) * 1.25f
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = fitTextSize(qbTxt, (b.right - 10f - iconRight).coerceAtLeast(36f), (b.height() * 0.34f).coerceIn(10f, 18f), 7.5f)
            textPaint.color = if (canAfford) 0xFFFDE047.toInt() else 0xFFCBD5E1.toInt()
            canvas.drawText(qbTxt, b.right - 8f, b.centerY() + textPaint.textSize * 0.35f, textPaint)
        }
    }

    private fun fitTextSize(text: String, maxW: Float, baseSize: Float, minSize: Float = 7f): Float {
        var sz = baseSize
        textPaint.textSize = sz
        val measured = textPaint.measureText(text)
        if (measured > maxW && measured > 1f) {
            sz = (sz * (maxW / measured)).coerceIn(minSize, baseSize)
        }
        return sz
    }

    private fun drawTopHeader(canvas: Canvas, w: Float, h: Float) {
        val th = geo.topHeader
        val topW = th.width()
        val topH = th.height()
        val topL = th.left
        paint.style = Paint.Style.FILL
        paint.color = 0xCC0D1320.toInt()
        rectF.set(th.left, th.top, th.right, th.bottom)
        canvas.drawRoundRect(rectF, 14f, 14f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = 0xFF334155.toInt()
        canvas.drawRoundRect(rectF, 14f, 14f, paint)

        val mins = (world.gameTime / 60f).toInt()
        val secs = (world.gameTime % 60f).toInt()
        val clock = "%02d:%02d".format(mins, secs)
        val clockStr = if (world.isNight) "NIGHT $clock" else "DAY $clock"
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = fitTextSize(clockStr, topW * 0.52f, topH * 0.44f, 9f)
        textPaint.color = if (world.isNight) 0xFF93C5FD.toInt() else 0xFFFDE047.toInt()
        canvas.drawText(clockStr, th.centerX(), th.top + topH * 0.65f, textPaint)

        textPaint.textSize = (topH * 0.54f).coerceIn(11f, 28f)
        textPaint.color = 0xFF2ECC71.toInt()
        canvas.drawText("${world.radiantKills}", topL + topW * 0.14f, th.top + topH * 0.68f, textPaint)
        textPaint.color = 0xFFE74C3C.toInt()
        canvas.drawText("${world.direKills}", topL + topW * 0.86f, th.top + topH * 0.68f, textPaint)

        val ph = world.playerHero
        val statLine = "GOLD ${ph.gold}  •  ${ph.kills}/${ph.deaths}/${ph.assists}  •  CS ${ph.lastHits}/${ph.denies}"
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = fitTextSize(statLine, topW * 1.15f, topH * 0.35f, 8.5f)
        textPaint.color = 0xFFFDE047.toInt()
        canvas.drawText(statLine, th.centerX(), th.bottom + topH * 0.44f, textPaint)
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

    private fun drawActiveBuffsStrip(canvas: Canvas, uiScale: Float) {
        val ph = world.playerHero
        val pills = ArrayList<Triple<String, Int, Int>>(6)
        if (ph.hasAegis) pills.add(Triple("AEGIS", 0xFFD97706.toInt(), 0xFFFEF08A.toInt()))
        if (ph.hasStatus(StatusType.MAGIC_IMMUNE)) pills.add(Triple("BKB", 0xFFB45309.toInt(), 0xFFFDE047.toInt()))
        if (ph.hasStatus(StatusType.BLADE_FURY)) pills.add(Triple("SPIN", 0xFFC2410C.toInt(), 0xFFFED7AA.toInt()))
        if (ph.hasStatus(StatusType.DOUBLE_DAMAGE)) pills.add(Triple("2X DMG", 0xFF0369A1.toInt(), 0xFFBAE6FD.toInt()))
        if (ph.hasStatus(StatusType.HASTE)) pills.add(Triple("HASTE", 0xFF991B1B.toInt(), 0xFFFECACA.toInt()))
        if (ph.hasStatus(StatusType.REGEN)) pills.add(Triple("REGEN", 0xFF166534.toInt(), 0xFFBBF7D0.toInt()))
        if (ph.hasStatus(StatusType.BLUR) || ph.hasStatus(StatusType.INVISIBLE)) pills.add(Triple("INVIS", 0xFF475569.toInt(), 0xFFE2E8F0.toInt()))
        if (ph.hasStatus(StatusType.SATANIC)) pills.add(Triple("SATANIC", 0xFF7F1D1D.toInt(), 0xFFFCA5A5.toInt()))
        if (ph.isStunned()) pills.add(Triple("STUN", 0xFF991B1B.toInt(), 0xFFFFFFFF.toInt()))
        if (ph.isSilenced()) pills.add(Triple("SILENCE", 0xFF6B21A8.toInt(), 0xFFE9D5FF.toInt()))
        if (pills.isEmpty()) return

        val pillH = 16f * uiScale
        val pillW = 54f * uiScale
        var x = geo.heroConsole.left + 8f
        val y = geo.heroConsole.top - pillH - 4f
        for ((lbl, bg, fg) in pills.take(6)) {
            rectF.set(x, y, x + pillW, y + pillH)
            paint.style = Paint.Style.FILL
            paint.color = bg
            canvas.drawRoundRect(rectF, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            paint.color = fg
            canvas.drawRoundRect(rectF, 6f, 6f, paint)
            textPaint.color = fg
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 9.2f * uiScale
            canvas.drawText(lbl, rectF.centerX(), rectF.centerY() + 3.2f * uiScale, textPaint)
            x += pillW + 4f * uiScale
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawDotaHeroConsole(canvas: Canvas, uiScale: Float) {
        val ph = world.playerHero
        val c = geo.heroConsole
        if (c.width() < 160f) return

        // 1. Outer Dota 2 Console Frame
        rectF.set(c.left, c.top, c.right, c.bottom)
        paint.style = Paint.Style.FILL
        paint.color = 0xEA0B1019.toInt()
        canvas.drawRoundRect(rectF, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = 0xFFD4A037.toInt()
        canvas.drawRoundRect(rectF, 12f, 12f, paint)

        // 2. Hero Portrait + Primary Attribute + Level Badge + XP Bar
        val pb = geo.portraitBox
        rectF.set(pb.left, pb.top, pb.right, pb.bottom)
        DotaAssetLoader.getHeroPortrait(ph.def.id)?.let { bmp ->
            canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(rectF, 8f, 8f, Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bmp, null, rectF, paint)
            canvas.restore()
        } ?: run {
            paint.style = Paint.Style.FILL
            paint.color = ph.def.primaryColor
            canvas.drawRoundRect(rectF, 8f, 8f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFFD4A037.toInt()
        canvas.drawRoundRect(rectF, 8f, 8f, paint)

        // Primary attribute badge top-left of portrait
        val attrR = (pb.height() * 0.14f).coerceIn(7f, 13f)
        paint.style = Paint.Style.FILL
        paint.color = ph.def.attribute.colorHex
        canvas.drawCircle(pb.left + attrR + 3f, pb.top + attrR + 3f, attrR, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = Color.WHITE
        canvas.drawCircle(pb.left + attrR + 3f, pb.top + attrR + 3f, attrR, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = attrR * 0.92f
        canvas.drawText(ph.def.attribute.shortLabel.take(1), pb.left + attrR + 3f, pb.top + attrR * 1.32f + 3f, textPaint)

        // Golden Level Circle bottom-right of portrait
        val lvlR = (pb.height() * 0.19f).coerceIn(9f, 18f)
        val lvlCx = pb.right - lvlR - 2f
        val lvlCy = pb.bottom - lvlR - 2f
        paint.style = Paint.Style.FILL
        paint.color = 0xEE111827.toInt()
        canvas.drawCircle(lvlCx, lvlCy, lvlR, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0xFFFACC15.toInt()
        canvas.drawCircle(lvlCx, lvlCy, lvlR, paint)
        textPaint.color = 0xFFFDE047.toInt()
        textPaint.textSize = lvlR * 1.05f
        canvas.drawText("${ph.level}", lvlCx, lvlCy + lvlR * 0.36f, textPaint)

        // Skill points available indicator at top-right of portrait
        if (ph.skillPoints > 0) {
            val spR = (pb.height() * 0.14f).coerceIn(7f, 13f)
            val spCx = pb.right - spR - 2f
            val spCy = pb.top + spR + 2f
            paint.style = Paint.Style.FILL
            paint.color = 0xEE15803D.toInt()
            canvas.drawCircle(spCx, spCy, spR, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f
            paint.color = 0xFF4ADE80.toInt()
            canvas.drawCircle(spCx, spCy, spR, paint)
            textPaint.color = Color.WHITE
            textPaint.textSize = spR * 0.96f
            canvas.drawText("+${ph.skillPoints}", spCx, spCy + spR * 0.34f, textPaint)
        }

        // XP Bar right below portrait
        val xb = geo.xpBarBox
        val xpPct = DotaMath.xpProgress(ph.xp, ph.level)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF1E293B.toInt()
        rectF.set(xb.left, xb.top, xb.right, xb.bottom)
        canvas.drawRoundRect(rectF, 4f, 4f, paint)
        paint.color = 0xFFEAB308.toInt()
        rectF.set(xb.left, xb.top, xb.left + xb.width() * xpPct, xb.bottom)
        canvas.drawRoundRect(rectF, 4f, 4f, paint)
        val (_, nextXp) = DotaMath.xpForNextLevel(ph.level)
        val xpLabel = if (ph.level >= 30) "MAX" else "XP ${ph.xp}/$nextXp"
        textPaint.color = Color.WHITE
        textPaint.textSize = fitTextSize(xpLabel, xb.width() - 4f, (xb.height() * 0.72f).coerceIn(7.5f, 12f), 6.5f)
        canvas.drawText(xpLabel, xb.centerX(), xb.centerY() + textPaint.textSize * 0.34f, textPaint)

        // 3. Attributes (STR/AGI/INT), Combat Stats, Talent Tree & Scepter/Shard
        val ab = geo.attrBox
        val tb = geo.talentHudBtn
        val hasTalent = ph.availableTalentTier() != null
        drawBoxBtn(canvas, tb, if (hasTalent) "★ TALENT" else "TALENTS",
            if (hasTalent) 0xEE065F46.toInt() else 0xCC162032.toInt(),
            if (hasTalent) 0xFF34D399.toInt() else 0xFFD4A037.toInt())

        // Scepter & Shard mini badges beside Talent button
        val badgeLeft = tb.right + 4f
        val badgeW = ((ab.right - badgeLeft - 4f) * 0.5f).coerceAtLeast(16f)
        val badgeBaseSz = (tb.height() * 0.36f).coerceIn(7.5f, 12.5f)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        rectF.set(badgeLeft, tb.top, badgeLeft + badgeW, tb.bottom)
        paint.style = Paint.Style.FILL
        paint.color = if (ph.hasScepter) 0xFF1D4ED8.toInt() else 0xFF151E2E.toInt()
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.4f
        paint.color = if (ph.hasScepter) 0xFF60A5FA.toInt() else 0xFF334155.toInt()
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        textPaint.color = if (ph.hasScepter) Color.WHITE else 0xFF64748B.toInt()
        textPaint.textSize = fitTextSize("AGH", badgeW - 4f, badgeBaseSz, 6.5f)
        canvas.drawText("AGH", rectF.centerX(), rectF.centerY() + textPaint.textSize * 0.35f, textPaint)

        rectF.set(badgeLeft + badgeW + 4f, tb.top, ab.right, tb.bottom)
        paint.style = Paint.Style.FILL
        paint.color = if (ph.hasShard) 0xFF0E7490.toInt() else 0xFF151E2E.toInt()
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = if (ph.hasShard) 0xFF38BDF8.toInt() else 0xFF334155.toInt()
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        textPaint.color = if (ph.hasShard) Color.WHITE else 0xFF64748B.toInt()
        textPaint.textSize = fitTextSize("SHD", badgeW - 4f, badgeBaseSz, 6.5f)
        canvas.drawText("SHD", rectF.centerX(), rectF.centerY() + textPaint.textSize * 0.35f, textPaint)

        // STR / AGI / INT Labeled Attribute Columns (Orbs + Labels + Values)
        val attrTop = tb.bottom + 3f
        val attrAreaH = ab.bottom - attrTop
        val colW = ab.width() / 3f
        val attrs = arrayOf(
            Triple("STR", ph.cachedStr.toInt(), PrimaryAttribute.STRENGTH),
            Triple("AGI", ph.cachedAgi.toInt(), PrimaryAttribute.AGILITY),
            Triple("INT", ph.cachedInt.toInt(), PrimaryAttribute.INTELLIGENCE)
        )
        val dotR = (attrAreaH * 0.072f).coerceIn(3.5f, 6.5f)
        val lblSz = (attrAreaH * 0.145f).coerceIn(7.5f, 11.5f)
        val valSz = (attrAreaH * 0.190f).coerceIn(9.5f, 15.5f)
        for (k in 0..2) {
            val colLeft = ab.left + colW * k
            val colCx = colLeft + colW * 0.5f
            val isPrim = ph.def.attribute == attrs[k].third
            val dotCx = colCx - dotR - 7f
            val row1Y = attrTop + attrAreaH * 0.21f
            paint.style = Paint.Style.FILL
            paint.color = attrs[k].third.colorHex
            canvas.drawCircle(dotCx, row1Y - dotR * 0.4f, dotR, paint)
            if (isPrim) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                paint.color = Color.WHITE
                canvas.drawCircle(dotCx, row1Y - dotR * 0.4f, dotR + 1.3f, paint)
            }
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = fitTextSize(attrs[k].first, colW * 0.55f, lblSz, 6.5f)
            textPaint.color = if (isPrim) 0xFFFDE047.toInt() else 0xFF94A3B8.toInt()
            canvas.drawText(attrs[k].first, dotCx + dotR + 3f, row1Y, textPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = fitTextSize("${attrs[k].second}", colW - 4f, valSz, 8f)
            textPaint.color = if (isPrim) 0xFFFDE047.toInt() else Color.WHITE
            canvas.drawText("${attrs[k].second}", colCx, attrTop + attrAreaH * 0.46f, textPaint)
        }

        // Combat Stats: DMG, ARM, MS, AS
        val statY1 = attrTop + attrAreaH * 0.72f
        val statY2 = attrTop + attrAreaH * 0.96f
        val statSz = (attrAreaH * 0.155f).coerceIn(8.5f, 13.5f)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textAlign = Paint.Align.LEFT
        val halfW = ab.width() * 0.48f
        val dmgTxt = "DMG ${ph.cachedDamage.toInt()}"
        val armTxt = "ARM ${"%.1f".format(ph.armor)}"
        val msTxt = "MS ${ph.cachedMoveSpeed.toInt()}"
        val asTxt = "AS ${ph.cachedAttackSpeed.toInt()}"
        textPaint.color = if (ph.hasStatus(StatusType.DOUBLE_DAMAGE) || ph.enchantTotemBuff) 0xFF38BDF8.toInt() else 0xFFE2E8F0.toInt()
        textPaint.textSize = fitTextSize(dmgTxt, halfW, statSz, 7f)
        canvas.drawText(dmgTxt, ab.left + 2f, statY1, textPaint)
        textPaint.color = 0xFFCBD5E1.toInt()
        textPaint.textSize = fitTextSize(armTxt, halfW, statSz, 7f)
        canvas.drawText(armTxt, ab.left + ab.width() * 0.51f, statY1, textPaint)
        textPaint.textSize = fitTextSize(msTxt, halfW, statSz, 7f)
        canvas.drawText(msTxt, ab.left + 2f, statY2, textPaint)
        textPaint.textSize = fitTextSize(asTxt, halfW, statSz, 7f)
        canvas.drawText(asTxt, ab.left + ab.width() * 0.51f, statY2, textPaint)

        // 4. Center Hero Name + Gold/Stash + Collision-Free HP/MP Bars
        val p = geo.heroPlate
        val rowH = (p.height() - 8f) / 3f
        val extraTag = when {
            ph.def.id == "pudge" && ph.fleshHeapStacks > 0 -> " • Heap +${ph.fleshHeapStacks}"
            ph.def.id == "axe" && ph.coatOfBloodStacks > 0 -> " • Blood +${ph.coatOfBloodStacks}"
            ph.desoStacks > 0 -> " • Deso +${ph.desoStacks}"
            ph.def.id == "invoker" -> " • QWE"
            ph.stash.isNotEmpty() -> " • Stash ${ph.stash.size}"
            else -> ""
        }
        val titleTxt = "${ph.def.name.uppercase()}$extraTag"
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = 0xFFF5B041.toInt()
        textPaint.textSize = fitTextSize(titleTxt, p.width() - 4f, (rowH * 0.52f).coerceIn(9f, 16f), 7.5f)
        canvas.drawText(titleTxt, p.left + 2f, p.top + rowH * 0.72f, textPaint)

        // HP Bar (collision-free HP + regen layout)
        val hpY = p.top + rowH + 2f
        val barH = rowH * 0.88f
        val hpPct = (ph.hp / ph.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF132A1C.toInt()
        rectF.set(p.left, hpY, p.right, hpY + barH)
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        paint.color = 0xFF22C55E.toInt()
        rectF.set(p.left, hpY, p.left + p.width() * hpPct, hpY + barH)
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        val hpMain = "${ph.hp.toInt()} / ${ph.maxHp.toInt()}"
        val hpReg = "+${"%.1f".format(ph.cachedHpRegen)}"
        val barFont = (barH * 0.54f).coerceIn(8.5f, 15f)
        val regFont = (barH * 0.44f).coerceIn(7.5f, 12.5f)
        textPaint.textSize = barFont
        val hpW = textPaint.measureText(hpMain)
        textPaint.textSize = regFont
        val regW = textPaint.measureText(hpReg)
        if (hpW + regW * 2f + 16f <= p.width()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = barFont
            canvas.drawText(hpMain, p.centerX(), hpY + barH * 0.70f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFFBBF7D0.toInt()
            textPaint.textSize = regFont
            canvas.drawText(hpReg, p.right - 5f, hpY + barH * 0.68f, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = fitTextSize(hpMain, p.width() - regW - 12f, barFont, 7.5f)
            canvas.drawText(hpMain, p.left + 5f, hpY + barH * 0.70f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFFBBF7D0.toInt()
            textPaint.textSize = regFont
            canvas.drawText(hpReg, p.right - 5f, hpY + barH * 0.68f, textPaint)
        }

        // Mana Bar (collision-free MP + regen layout)
        val mpY = hpY + rowH + 2f
        val mpPct = (ph.mana / ph.maxMana.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.color = 0xFF0E233A.toInt()
        rectF.set(p.left, mpY, p.right, mpY + barH)
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        paint.color = 0xFF0EA5E9.toInt()
        rectF.set(p.left, mpY, p.left + p.width() * mpPct, mpY + barH)
        canvas.drawRoundRect(rectF, 5f, 5f, paint)
        val mpMain = "${ph.mana.toInt()} / ${ph.maxMana.toInt()}"
        val mpReg = "+${"%.1f".format(ph.cachedManaRegen)}"
        textPaint.textSize = barFont
        val mpW = textPaint.measureText(mpMain)
        textPaint.textSize = regFont
        val mpRegW = textPaint.measureText(mpReg)
        if (mpW + mpRegW * 2f + 16f <= p.width()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = barFont
            canvas.drawText(mpMain, p.centerX(), mpY + barH * 0.70f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFFBAE6FD.toInt()
            textPaint.textSize = regFont
            canvas.drawText(mpReg, p.right - 5f, mpY + barH * 0.68f, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            textPaint.textSize = fitTextSize(mpMain, p.width() - mpRegW - 12f, barFont, 7.5f)
            canvas.drawText(mpMain, p.left + 5f, mpY + barH * 0.70f, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = 0xFFBAE6FD.toInt()
            textPaint.textSize = regFont
            canvas.drawText(mpReg, p.right - 5f, mpY + barH * 0.68f, textPaint)
        }

        // 5. Dota 2 3x2 Inventory Slots
        for (i in 0..5) {
            val b = geo.itemSlots[i]
            rectF.set(b.left, b.top, b.right, b.bottom)
            val slot = ph.inventory.getOrNull(i)
            paint.style = Paint.Style.FILL
            paint.color = 0xDD101624.toInt()
            canvas.drawRoundRect(rectF, 6f, 6f, paint)

            if (slot != null) {
                val item = ItemCatalog.get(slot.itemId)
                val icon = item?.let { DotaAssetLoader.getItemIcon(it.id) }
                if (icon != null) {
                    canvas.save()
                    clipPath.reset()
                    clipPath.addRoundRect(RectF(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f), 5f, 5f, Path.Direction.CW)
                    canvas.clipPath(clipPath)
                    canvas.drawBitmap(icon, null, RectF(b.left + 2f, b.top + 2f, b.right - 2f, b.bottom - 2f), paint)
                    canvas.restore()
                }
                val lowManaItem = item != null && item.isActive && item.manaCost > ph.mana
                if (slot.cooldownRemaining > 0f) {
                    paint.style = Paint.Style.FILL
                    paint.color = 0xAA000000.toInt()
                    canvas.drawRoundRect(rectF, 6f, 6f, paint)
                    textPaint.color = Color.WHITE
                    textPaint.textSize = (b.height() * 0.36f).coerceIn(10f, 18f)
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("${slot.cooldownRemaining.toInt()}", b.centerX(), b.centerY() + textPaint.textSize * 0.35f, textPaint)
                } else if (lowManaItem) {
                    paint.style = Paint.Style.FILL
                    paint.color = 0x880C2D57.toInt()
                    canvas.drawRoundRect(rectF, 6f, 6f, paint)
                }
                if (item != null && item.isActive) {
                    paint.style = Paint.Style.FILL
                    paint.color = if (isAimableItem(item.id)) 0xFF38BDF8.toInt() else 0xFFFACC15.toInt()
                    canvas.drawCircle(b.left + 6f, b.top + 6f, 3.2f, paint)
                }
                if (slot.cooldownRemaining <= 0f && (slot.stacks > 1 || item?.id == "magic_wand")) {
                    textPaint.color = 0xFFFDE047.toInt()
                    textPaint.textSize = (b.height() * 0.25f).coerceIn(8.5f, 14f)
                    textPaint.textAlign = Paint.Align.RIGHT
                    val badge = if (item?.id == "magic_wand") "${slot.charges}" else "x${slot.stacks}"
                    canvas.drawText(badge, b.right - 3f, b.bottom - 3f, textPaint)
                } else if (item?.id == "power_treads") {
                    val (tLbl, tCol) = when (slot.treadMode) {
                        0 -> "STR" to 0xFFE74C3C.toInt()
                        1 -> "AGI" to 0xFF2ECC71.toInt()
                        else -> "INT" to 0xFF38BDF8.toInt()
                    }
                    textPaint.color = tCol
                    textPaint.textSize = (b.height() * 0.23f).coerceIn(8f, 13f)
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(tLbl, b.left + 3f, b.bottom - 3f, textPaint)
                }
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            paint.color = when {
                renderer3D.aimingItemSlotIdx == i -> 0xFF38BDF8.toInt()
                slot != null -> 0xFFD4A037.toInt()
                else -> 0xFF334155.toInt()
            }
            canvas.drawRoundRect(rectF, 6f, 6f, paint)
        }

        // 6. TP Scroll Slot & Neutral Item Slot
        val tpB = geo.tpSlot
        val tpR = minOf(tpB.width(), tpB.height()) * 0.46f
        val tpC = Vec2(tpB.centerX(), tpB.centerY())
        drawCircleBtn(canvas, tpC, tpR, "",
            0xCC1E3A8A.toInt(), if (ph.tpCooldown > 0f) 0xFF475569.toInt() else 0xFF60A5FA.toInt(),
            DotaAssetLoader.getItemIcon("tp_scroll"))
        if (ph.tpCooldown > 0f) {
            paint.style = Paint.Style.FILL
            paint.color = 0xAA000000.toInt()
            canvas.drawCircle(tpC.x, tpC.y, tpR, paint)
        }
        val tpTxt = if (ph.tpCooldown > 0f) "${ph.tpCooldown.toInt()}" else "TP"
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (tpR * 0.42f).coerceIn(9f, 16f)
        textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
        canvas.drawText(tpTxt, tpC.x, tpC.y + textPaint.textSize * 0.36f, textPaint)
        textPaint.clearShadowLayer()

        val neuB = geo.neutralSlot
        val neuR = minOf(neuB.width(), neuB.height()) * 0.46f
        val nid = ph.neutralItemId
        val neuDef = nid?.let { ItemCatalog.getNeutral(it) }
        val neuIcon = nid?.let { DotaAssetLoader.getItemIcon(it) }
        val neuTxt = if (neuDef != null) "T${neuDef.tier}" else "NEU"
        drawCircleBtn(canvas, Vec2(neuB.centerX(), neuB.centerY()), neuR, if (neuIcon != null) "" else neuTxt,
            0xCC181828.toInt(), if (neuDef != null) 0xFFC084FC.toInt() else 0xFF475569.toInt(), neuIcon)
        if (neuDef != null) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (neuR * 0.42f).coerceIn(8f, 13f)
            textPaint.color = 0xFFE9D5FF.toInt()
            canvas.drawText("T${neuDef.tier}", neuB.centerX(), neuB.bottom - 2f, textPaint)
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawAttackCluster(canvas: Canvas) {
        val atkBg = if (heldAttackMode == 0) 0xFFDC2626.toInt() else 0xDDB91C1C.toInt()
        val crpBg = if (heldAttackMode == 1) 0xFFD97706.toInt() else 0xDD92400E.toInt()
        val twrBg = if (heldAttackMode == 2) 0xFF0284C7.toInt() else 0xDD0369A1.toInt()
        drawCircleBtn(canvas, geo.attackCenter, geo.attackRadius, "ATK", atkBg, 0xFFF87171.toInt(), null)
        drawCircleBtn(canvas, geo.minionCenter, geo.subRadius, "CREEP", crpBg, 0xFFFBBF24.toInt(), null)
        drawCircleBtn(canvas, geo.towerDenyCenter, geo.subRadius, "TWR/!", twrBg, 0xFF38BDF8.toInt(), null)
    }

    private fun drawAbilityArc(canvas: Canvas, uiScale: Float) {
        val ph = world.playerHero
        val r = geo.abilityRadius
        for (i in 0..3) {
            val ab = ph.def.abilities[i]
            val c = geo.abilityCenters[i]
            val lvl = ph.abilityLevels[i]
            val cd = ph.abilityCooldowns[i]
            val manaCost = ab.manaCost.getOrElse((lvl - 1).coerceAtLeast(0)) { 0 }
            val lowMana = lvl > 0 && ph.mana < manaCost
            val isPassive = ab.targetType == TargetType.PASSIVE
            val isRotOn = ab.targetType == TargetType.TOGGLE && ph.def.id == "pudge" && ph.rotEnabled
            val icon = DotaAssetLoader.getAbilityCircleIcon(ab.id, 128)
            val border = when {
                renderer3D.aimingAbilityIdx == i -> 0xFF38BDF8.toInt()
                isRotOn -> 0xFF4ADE80.toInt()
                ab.isUltimate -> 0xFFF59E0B.toInt()
                isPassive && lvl > 0 -> 0xFF94A3B8.toInt()
                lvl > 0 -> 0xFF60A5FA.toInt()
                else -> 0xFF475569.toInt()
            }

            if (icon != null) {
                canvas.drawBitmap(icon, null, RectF(c.x - r, c.y - r, c.x + r, c.y + r), paint)
                if (lvl == 0 || cd > 0f || lowMana) {
                    paint.style = Paint.Style.FILL
                    paint.color = if (lowMana && cd <= 0f) 0xAA0C2D57.toInt() else 0xAA090D16.toInt()
                    canvas.drawCircle(c.x, c.y, r, paint)
                }
            } else {
                paint.style = Paint.Style.FILL
                paint.color = 0xCC1E3A8A.toInt()
                canvas.drawCircle(c.x, c.y, r, paint)
            }

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (ab.isUltimate || isRotOn) 4f else 2.8f) * uiScale * 0.65f
            paint.color = border
            canvas.drawCircle(c.x, c.y, r, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            if (lvl == 0) {
                textPaint.color = 0xFFCBD5E1.toInt()
                textPaint.textSize = r * 0.40f
                canvas.drawText(ab.keyLabel, c.x, c.y + r * 0.14f, textPaint)
            } else if (cd > 0f) {
                textPaint.color = Color.WHITE
                textPaint.textSize = r * 0.46f
                canvas.drawText("${"%.1f".format(cd)}", c.x, c.y + r * 0.16f, textPaint)
            } else if (isPassive) {
                textPaint.color = 0xFFE2E8F0.toInt()
                textPaint.textSize = r * 0.25f
                canvas.drawText("PSV", c.x, c.y + r * 0.10f, textPaint)
            } else if (ab.maxCharges > 1) {
                textPaint.color = 0xFFFDE047.toInt()
                textPaint.textSize = r * 0.34f
                canvas.drawText("${ph.abilityCharges[i]}", c.x + r * 0.55f, c.y - r * 0.38f, textPaint)
            }

            if (manaCost > 0 && lvl > 0 && !isPassive) {
                textPaint.color = if (lowMana) 0xFFF87171.toInt() else 0xFF7DD3FC.toInt()
                textPaint.textSize = (r * 0.27f).coerceIn(8f, 14f)
                canvas.drawText("$manaCost", c.x, c.y - r * 0.55f, textPaint)
            }

            for (d in 0 until ab.maxLevel) {
                paint.style = Paint.Style.FILL
                paint.color = if (d < lvl) 0xFFFACC15.toInt() else 0xFF334155.toInt()
                val dx = (d - (ab.maxLevel - 1) * 0.5f) * (r * 0.24f)
                canvas.drawCircle(c.x + dx, c.y + r - 5f, (r * 0.08f).coerceAtLeast(2.8f), paint)
            }

            if (ph.canLevelAbility(i)) {
                drawBoxBtn(canvas, geo.levelUpPills[i], "+ LVL", 0xEE15803D.toInt(), 0xFF4ADE80.toInt())
            }

            if (renderer3D.aimingAbilityIdx == i) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = 0xAA38BDF8.toInt()
                canvas.drawCircle(c.x, c.y, r * 1.45f, paint)
                linePaint.color = 0xAA38BDF8.toInt()
                linePaint.strokeWidth = 3f
                canvas.drawLine(c.x, c.y, aimCurrentPos.x, aimCurrentPos.y, linePaint)
                paint.style = Paint.Style.FILL
                paint.color = 0xCC38BDF8.toInt()
                canvas.drawCircle(aimCurrentPos.x, aimCurrentPos.y, r * 0.42f, paint)
            }
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawUtilityButtons(canvas: Canvas) {
        val ph = world.playerHero
        val stashN = ph.stash.size
        val courActive = world.couriers.any { it.heroId == ph.id && (it.delivering || it.returning) }
        drawCircleBtn(canvas, geo.courierCenter, geo.utilRadius,
            if (stashN > 0) "C($stashN)" else if (courActive) "FLY" else "COUR",
            if (stashN > 0) 0xEEB45309.toInt() else 0xCC713F12.toInt(),
            if (stashN > 0) 0xFFFDE047.toInt() else 0xFFFBBF24.toInt(), null)
        val gCd = if (ph.team == Team.RADIANT) world.radiantGlyphCd else world.direGlyphCd
        drawCircleBtn(canvas, geo.glyphCenter, geo.utilRadius, if (gCd > 0f) "${gCd.toInt()}" else "GLYPH",
            0xCC0C4A6E.toInt(), 0xFF38BDF8.toInt(), null)
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
            canvas.save()
            clipPath.reset()
            clipPath.addCircle(c.x, c.y, r * 0.90f, Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(icon, null, RectF(c.x - r * 0.92f, c.y - r * 0.92f, c.x + r * 0.92f, c.y + r * 0.92f), paint)
            canvas.restore()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f
        paint.color = border
        canvas.drawCircle(c.x, c.y, r, paint)

        if (label.isNotEmpty()) {
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = (r * 0.34f).coerceIn(10f, 22f)
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
        canvas.drawRoundRect(rectF, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.color = border
        canvas.drawRoundRect(rectF, 8f, 8f, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (b.height() * 0.38f).coerceIn(9.5f, 18f)
        canvas.drawText(label, b.centerX(), b.centerY() + textPaint.textSize * 0.35f, textPaint)
        textPaint.typeface = Typeface.DEFAULT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2 && joystickPointerId == -1 && aimPointerId == -1 && minimapPointerId == -1) {
            scaleDetector.onTouchEvent(event)
        }
        val action = event.actionMasked
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)
        val sx = event.getX(idx)
        val sy = event.getY(idx)
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)

        synchronized(world) {
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
                    if (geo.zoomBtn.contains(sx, sy)) {
                        renderer3D.cameraZoomDist = when {
                            renderer3D.cameraZoomDist < 640f -> 690f
                            renderer3D.cameraZoomDist < 780f -> 850f
                            else -> 580f
                        }
                        return true
                    }
                    if (geo.shopBtn.contains(sx, sy)) { listener.onOpenShop(); return true }
                    if (geo.talentBtn.contains(sx, sy) || geo.talentHudBtn.contains(sx, sy) || geo.attrBox.contains(sx, sy)) {
                        listener.onOpenTalents()
                        return true
                    }
                    if (geo.statsBtn.contains(sx, sy)) { listener.onOpenScoreboard(); return true }
                    if (geo.pauseBtn.contains(sx, sy)) { listener.onOpenPauseMenu(); return true }
                    if (geo.portraitBox.contains(sx, sy)) {
                        renderer3D.cameraLocked = true
                        renderer3D.cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
                        return true
                    }

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
                        attackPointerId = pid
                        heldAttackMode = 0
                        lastAttackRepeatMs = SystemClock.uptimeMillis()
                        renderer3D.showAttackRangeRing = true
                        world.playerSmartAttack(preferCreep = false, preferDeny = false)
                        return true
                    }
                    if (hypot(sx - geo.minionCenter.x, sy - geo.minionCenter.y) <= geo.subRadius + 10f) {
                        attackPointerId = pid
                        heldAttackMode = 1
                        lastAttackRepeatMs = SystemClock.uptimeMillis()
                        renderer3D.showAttackRangeRing = true
                        world.playerSmartAttack(preferCreep = true, preferDeny = false)
                        return true
                    }
                    if (hypot(sx - geo.towerDenyCenter.x, sy - geo.towerDenyCenter.y) <= geo.subRadius + 10f) {
                        attackPointerId = pid
                        heldAttackMode = 2
                        lastAttackRepeatMs = SystemClock.uptimeMillis()
                        renderer3D.showAttackRangeRing = true
                        world.playerSmartAttack(preferCreep = false, preferDeny = true, preferStructure = true)
                        return true
                    }
                    for (i in 0..3) {
                        val pill = geo.levelUpPills[i]
                        if (world.playerHero.canLevelAbility(i) &&
                            sx in (pill.left - 8f)..(pill.right + 8f) &&
                            sy in (pill.top - 8f)..(pill.bottom + 8f)
                        ) {
                            world.levelUpAbility(world.playerHero, i)
                            return true
                        }
                    }
                    for (i in 0..3) {
                        val c = geo.abilityCenters[i]
                        if (hypot(sx - c.x, sy - c.y) <= geo.abilityRadius + 10f) {
                            val ph = world.playerHero
                            val ab = ph.def.abilities[i]
                            if (ab.targetType == TargetType.PASSIVE) {
                                world.addFloatingText(ph.pos, "${ab.name} (Passive)", 0xFF94A3B8.toInt(), 0.7f)
                                return true
                            }
                            val lvl = ph.abilityLevels[i]
                            if (lvl <= 0) {
                                world.addFloatingText(ph.pos, "Not Learned", 0xFF94A3B8.toInt(), 0.6f)
                                return true
                            }
                            if (ab.maxCharges > 1 && ph.abilityCharges[i] <= 0) {
                                world.addFloatingText(ph.pos, "No Charges", 0xFFF59E0B.toInt(), 0.6f)
                                return true
                            }
                            if (ab.maxCharges <= 1 && ph.abilityCooldowns[i] > 0f) {
                                world.addFloatingText(ph.pos, "CD ${"%.1f".format(ph.abilityCooldowns[i])}s", 0xFF94A3B8.toInt(), 0.6f)
                                return true
                            }
                            val cost = ab.manaCost.getOrElse((lvl - 1).coerceAtLeast(0)) { 0 }
                            if (ph.mana < cost) {
                                world.addFloatingText(ph.pos, "NO MANA", 0xFF3498DB.toInt(), 0.7f)
                                return true
                            }
                            if (ab.targetType == TargetType.INSTANT || ab.targetType == TargetType.TOGGLE) {
                                world.castAbility(ph, i, defaultAutoAim(ab.castRange))
                            } else {
                                aimPointerId = pid
                                aimStartPos.set(c.x, c.y)
                                aimCurrentPos.set(c.x, c.y)
                                aimManualMoved = false
                                aimingCancelHovered = false
                                renderer3D.aimingAbilityIdx = i
                                renderer3D.aimingItemSlotIdx = -1
                                val auto = defaultAutoAim(ab.castRange)
                                renderer3D.aimWorldPos.set(auto.x, auto.y)
                            }
                            return true
                        }
                    }
                    if (geo.tpSlot.contains(sx, sy)) {
                        val tpAim = world.smartTpTarget(world.playerHero, renderer3D.cameraPos, renderer3D.cameraLocked)
                        world.useTpScroll(world.playerHero, tpAim)
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
                            val ph = world.playerHero
                            val slot = ph.inventory.getOrNull(i) ?: return true
                            val def = ItemCatalog.get(slot.itemId) ?: return true
                            if (!def.isActive) return true
                            if (slot.cooldownRemaining > 0f) {
                                world.addFloatingText(ph.pos, "${def.shortCode} CD ${slot.cooldownRemaining.toInt()}s", 0xFF94A3B8.toInt(), 0.6f)
                                return true
                            }
                            if (ph.mana < def.manaCost) {
                                world.addFloatingText(ph.pos, "NO MANA", 0xFF3498DB.toInt(), 0.6f)
                                return true
                            }
                            if (isAimableItem(slot.itemId)) {
                                aimPointerId = pid
                                aimStartPos.set(sx, sy)
                                aimCurrentPos.set(sx, sy)
                                aimManualMoved = false
                                aimingCancelHovered = false
                                renderer3D.aimingItemSlotIdx = i
                                renderer3D.aimingAbilityIdx = -1
                                val smart = smartItemTarget(slot.itemId)
                                renderer3D.aimWorldPos.set(smart.x, smart.y)
                            } else {
                                world.useItemSlot(ph, i, ph.pos)
                            }
                            return true
                        }
                    }
                    if (geo.heroConsole.contains(sx, sy)) {
                        return true
                    }
                    if (sx < w * 0.42f && sy > h * 0.40f && joystickPointerId == -1) {
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
                        world.playerHero.attackCommandAge = 0f
                        world.playerHero.facingAngle = world.playerHero.pos.angleTo(tappedEnemy.pos)
                        if (tappedEnemy.pos.distTo(world.playerHero.pos) > world.playerHero.cachedAttackRange) {
                            world.playerHero.tapMoveTarget = tappedEnemy.pos.copyVec()
                        }
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
                            if (dist > 11f) {
                                val clamped = dist.coerceAtMost(maxR)
                                joystickKnob.set(joystickBase.x + (dx / dist) * clamped, joystickBase.y + (dy / dist) * clamped)
                                world.playerHero.moveIntent.set(dx / dist, dy / dist)
                            } else {
                                joystickKnob.set(joystickBase.x, joystickBase.y)
                                world.playerHero.moveIntent.set(0f, 0f)
                            }
                        } else if (p == aimPointerId && renderer3D.aimingAbilityIdx in 0..3) {
                            aimingCancelHovered = hypot(mx - geo.cancelCenter.x, my - geo.cancelCenter.y) <= geo.cancelRadius + 32f
                            val dx = mx - aimStartPos.x
                            val dy = my - aimStartPos.y
                            val dist = hypot(dx, dy)
                            val maxR = geo.abilityRadius * 1.45f
                            val deadzone = geo.abilityRadius * 0.45f
                            if (dist > deadzone) {
                                aimManualMoved = true
                                val clamped = dist.coerceAtMost(maxR)
                                aimCurrentPos.set(aimStartPos.x + (dx / dist) * clamped, aimStartPos.y + (dy / dist) * clamped)
                                val ab = world.playerHero.def.abilities[renderer3D.aimingAbilityIdx]
                                val range = ab.castRange.takeIf { it > 0f } ?: 650f
                                val factor = if (ab.targetType == TargetType.DIRECTION_SKILLSHOT) 1.0f
                                else ((dist - deadzone) / (maxR - deadzone).coerceAtLeast(16f)).coerceIn(0.15f, 1.0f)
                                renderer3D.aimWorldPos.set(
                                    world.playerHero.pos.x + (dx / dist) * range * factor,
                                    world.playerHero.pos.y + (dy / dist) * range * factor
                                )
                            } else if (!aimManualMoved) {
                                aimCurrentPos.set(mx, my)
                            }
                        } else if (p == aimPointerId && renderer3D.aimingItemSlotIdx >= 0) {
                            aimingCancelHovered = hypot(mx - geo.cancelCenter.x, my - geo.cancelCenter.y) <= geo.cancelRadius + 32f
                            val dx = mx - aimStartPos.x
                            val dy = my - aimStartPos.y
                            val dist = hypot(dx, dy)
                            val maxR = geo.abilityRadius * 1.35f
                            val deadzone = 20f
                            if (dist > deadzone) {
                                aimManualMoved = true
                                val clamped = dist.coerceAtMost(maxR)
                                aimCurrentPos.set(aimStartPos.x + (dx / dist) * clamped, aimStartPos.y + (dy / dist) * clamped)
                                val slot = world.playerHero.inventory.getOrNull(renderer3D.aimingItemSlotIdx)
                                val maxItemRange = if (slot?.itemId == "boots_of_travel") 2400f else 850f
                                val factor = ((dist - deadzone) / (maxR - deadzone).coerceAtLeast(16f)).coerceIn(0.20f, 1.0f)
                                renderer3D.aimWorldPos.set(
                                    (world.playerHero.pos.x + (dx / dist) * maxItemRange * factor).coerceIn(0f, MapLayout.SIZE),
                                    (world.playerHero.pos.y + (dy / dist) * maxItemRange * factor).coerceIn(0f, MapLayout.SIZE)
                                )
                            } else if (!aimManualMoved) {
                                aimCurrentPos.set(mx, my)
                            }
                        } else if (p == panPointerId && event.pointerCount == 1) {
                            val dx = mx - panLastPos.x
                            val dy = my - panLastPos.y
                            if (hypot(dx, dy) > 4f) {
                                renderer3D.cameraLocked = false
                                renderer3D.cameraPos.x = (renderer3D.cameraPos.x - dx * 1.5f).coerceIn(0f, MapLayout.SIZE)
                                renderer3D.cameraPos.y = (renderer3D.cameraPos.y - dy * 1.5f).coerceIn(0f, MapLayout.SIZE)
                                panLastPos.set(mx, my)
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (pid == minimapPointerId) minimapPointerId = -1
                    if (pid == panPointerId) panPointerId = -1
                    if (pid == attackPointerId) {
                        attackPointerId = -1
                        heldAttackMode = -1
                        renderer3D.showAttackRangeRing = false
                    }
                    if (pid == joystickPointerId) {
                        joystickPointerId = -1
                        joystickActive = false
                        world.playerHero.moveIntent.set(0f, 0f)
                    }
                    if (pid == aimPointerId) {
                        val idxAb = renderer3D.aimingAbilityIdx
                        val idxItem = renderer3D.aimingItemSlotIdx
                        if (idxAb in 0..3 && !aimingCancelHovered) {
                            world.castAbility(world.playerHero, idxAb, renderer3D.aimWorldPos)
                        } else if (idxItem >= 0 && !aimingCancelHovered) {
                            world.useItemSlot(world.playerHero, idxItem, renderer3D.aimWorldPos)
                        }
                        aimPointerId = -1
                        aimManualMoved = false
                        aimingCancelHovered = false
                        renderer3D.aimingAbilityIdx = -1
                        renderer3D.aimingItemSlotIdx = -1
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    minimapPointerId = -1
                    panPointerId = -1
                    attackPointerId = -1
                    heldAttackMode = -1
                    renderer3D.showAttackRangeRing = false
                    joystickPointerId = -1
                    joystickActive = false
                    world.playerHero.moveIntent.set(0f, 0f)
                    aimPointerId = -1
                    aimManualMoved = false
                    aimingCancelHovered = false
                    renderer3D.aimingAbilityIdx = -1
                    renderer3D.aimingItemSlotIdx = -1
                }
            }
        }
        return true
    }

    private fun isAimableItem(itemId: String): Boolean = itemId in setOf(
        "blink_dagger", "obs_ward", "sentry_ward", "euls_scepter", "orchid", "scythe_of_vyse", "boots_of_travel"
    )

    private fun smartItemTarget(itemId: String): Vec2 {
        val ph = world.playerHero
        return when (itemId) {
            "orchid", "scythe_of_vyse", "euls_scepter" -> defaultAutoAim(680f)
            "boots_of_travel" -> world.smartTpTarget(ph, renderer3D.cameraPos, renderer3D.cameraLocked)
            else -> ph.pos + Vec2.fromAngle(ph.facingAngle, 650f)
        }
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
        val hasAnyUpgradedBoots = ph.hasItem("power_treads") || ph.hasItem("phase_boots") ||
            ph.hasItem("arcane_boots") || ph.hasItem("boots_of_travel")
        val plan = when {
            ph.def.attribute == PrimaryAttribute.INTELLIGENCE || ph.def.id == "invoker" ->
                listOf("boots", "magic_wand", "arcane_boots", "blink_dagger", "aghanims_scepter", "bkb", "shivas_guard")
            ph.def.isRanged ->
                listOf("boots", "magic_wand", "power_treads", "maelstrom", "bkb", "daedalus", "butterfly")
            else ->
                listOf("boots", "magic_wand", "power_treads", "vanguard", "bkb", "desolator", "heart")
        }
        return buildList {
            ph.quickBuyItemId?.let { add(it) }
            for (f in plan) {
                if (size >= 2) break
                if (f == "boots" && hasAnyUpgradedBoots) continue
                if (!ph.hasItem(f) && f !in this) add(f)
            }
        }.take(2)
    }
}
