package com.dota3.moba.render

import android.graphics.*
import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.*
import com.dota3.moba.model.*
import kotlin.math.cos
import kotlin.math.sin

class HudLayout {
    val minimapRect = RectF()
    val shopBtnRect = RectF()
    val talentBtnRect = RectF()
    val statsBtnRect = RectF()
    val pauseBtnRect = RectF()
    val camBtnRect = RectF()

    val quickBuyRect = RectF()
    val courierBtnRect = RectF()
    val tpBtnRect = RectF()
    val glyphBtnRect = RectF()
    val buybackBtnRect = RectF()

    val attackBtnRect = RectF()
    val lastHitBtnRect = RectF()
    val denyBtnRect = RectF()

    val abilityRects = Array(4) { RectF() }
    val levelUpRects = Array(4) { RectF() }
    val itemRects = Array(6) { RectF() }
}

class GameRenderer {
    val hud = HudLayout()
    val cameraPos = Vec2(800f, 4000f)
    var cameraLocked = true
    var zoom = 1.0f

    // Drag-to-aim preview state
    var aimingAbilityIdx: Int = -1
    var aimWorldPos: Vec2 = Vec2()
    var joystickOrigin: Vec2? = null
    var joystickThumb: Vec2? = null

    private var terrainBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private fun ensureTerrainBitmap() {
        if (terrainBitmap != null) return
        val size = 960
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        val scale = size / MapLayout.SIZE

        // Base Radiant vs Dire split gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, size.toFloat(), size.toFloat(), 0f,
                intArrayOf(0xFF172B1E.toInt(), 0xFF1A2421.toInt(), 0xFF28191B.toInt(), 0xFF2B1316.toInt()),
                floatArrayOf(0f, 0.45f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Radiant & Dire base plateaus (High Ground)
        val radBaseP = Paint().apply { color = 0xFF233D2A.toInt() }
        c.drawRect(0f, size * (3750f / MapLayout.SIZE), size * (1050f / MapLayout.SIZE), size.toFloat(), radBaseP)
        val direBaseP = Paint().apply { color = 0xFF3E1C20.toInt() }
        c.drawRect(size * (3750f / MapLayout.SIZE), 0f, size.toFloat(), size * (1050f / MapLayout.SIZE), direBaseP)

        // River diagonal
        val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF123E59.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 280f * scale
            strokeCap = Paint.Cap.ROUND
        }
        val riverPath = Path()
        riverPath.moveTo(450f * scale, 450f * scale)
        riverPath.quadTo(2280f * scale, 2520f * scale, 4350f * scale, 4350f * scale)
        c.drawPath(riverPath, riverPaint)

        // River inner glow
        riverPaint.color = 0xFF1C5F87.toInt()
        riverPaint.strokeWidth = 150f * scale
        c.drawPath(riverPath, riverPaint)

        // 3 Lanes stone road
        val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2F2E2A.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 190f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (lane in MapLayout.laneWaypoints) {
            val lp = Path()
            lp.moveTo(lane.first().x * scale, lane.first().y * scale)
            for (i in 1 until lane.size) lp.lineTo(lane[i].x * scale, lane[i].y * scale)
            c.drawPath(lp, lanePaint)
        }

        // Roshan pit dark ring
        val pitP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1214.toInt() }
        c.drawCircle(MapLayout.roshanPitPos.x * scale, MapLayout.roshanPitPos.y * scale, 185f * scale, pitP)
        pitP.style = Paint.Style.STROKE
        pitP.strokeWidth = 4f
        pitP.color = 0xFF8B2635.toInt()
        c.drawCircle(MapLayout.roshanPitPos.x * scale, MapLayout.roshanPitPos.y * scale, 185f * scale, pitP)

        // Neutral Camp rings
        pitP.color = 0x44A1887F
        pitP.strokeWidth = 2f
        for (camp in MapLayout.neutralCamps) {
            c.drawCircle(camp.pos.x * scale, camp.pos.y * scale, 110f * scale, pitP)
        }

        // Ward eye spots
        pitP.color = 0x88F1C40F.toInt()
        for (ws in MapLayout.wardSpots) {
            c.drawCircle(ws.x * scale, ws.y * scale, 28f * scale, pitP)
        }

        terrainBitmap = bmp
    }

    fun layoutHud(w: Float, h: Float) {
        val pad = w * 0.012f
        val mmSize = (h * 0.27f).coerceIn(130f, 230f)
        hud.minimapRect.set(pad, pad, pad + mmSize, pad + mmSize)

        // Top right buttons
        val btnW = h * 0.085f
        val btnH = h * 0.068f
        hud.pauseBtnRect.set(w - pad - btnW, pad, w - pad, pad + btnH)
        hud.statsBtnRect.set(hud.pauseBtnRect.left - pad * 0.6f - btnW * 1.15f, pad, hud.pauseBtnRect.left - pad * 0.6f, pad + btnH)
        hud.talentBtnRect.set(hud.statsBtnRect.left - pad * 0.6f - btnW * 1.3f, pad, hud.statsBtnRect.left - pad * 0.6f, pad + btnH)
        hud.shopBtnRect.set(hud.talentBtnRect.left - pad * 0.6f - btnW * 1.45f, pad, hud.talentBtnRect.left - pad * 0.6f, pad + btnH)

        // Camera snap button just below minimap
        hud.camBtnRect.set(pad, hud.minimapRect.bottom + pad * 0.6f, pad + mmSize * 0.48f, hud.minimapRect.bottom + pad * 0.6f + btnH * 0.85f)

        // Quick buy + utility buttons bottom-left above joystick margin
        val utilSize = (h * 0.088f).coerceIn(48f, 76f)
        hud.tpBtnRect.set(pad, h - pad - utilSize, pad + utilSize, h - pad)
        hud.courierBtnRect.set(hud.tpBtnRect.right + pad * 0.5f, h - pad - utilSize, hud.tpBtnRect.right + pad * 0.5f + utilSize, h - pad)
        hud.glyphBtnRect.set(hud.courierBtnRect.right + pad * 0.5f, h - pad - utilSize, hud.courierBtnRect.right + pad * 0.5f + utilSize, h - pad)
        hud.quickBuyRect.set(pad, hud.tpBtnRect.top - pad * 0.6f - utilSize * 0.9f, pad + utilSize * 2.5f, hud.tpBtnRect.top - pad * 0.6f)

        // Buyback center-bottom when dead
        hud.buybackBtnRect.set(w * 0.42f, h * 0.72f, w * 0.58f, h * 0.82f)

        // Right side: Attack button & sub-buttons
        val atkSize = (h * 0.175f).coerceIn(92f, 150f)
        val atkCx = w - pad - atkSize * 0.6f
        val atkCy = h - pad - atkSize * 0.6f
        hud.attackBtnRect.set(atkCx - atkSize * 0.5f, atkCy - atkSize * 0.5f, atkCx + atkSize * 0.5f, atkCy + atkSize * 0.5f)

        val subSize = atkSize * 0.56f
        hud.lastHitBtnRect.set(hud.attackBtnRect.left - subSize - pad * 0.5f, hud.attackBtnRect.bottom - subSize, hud.attackBtnRect.left - pad * 0.5f, hud.attackBtnRect.bottom)
        hud.denyBtnRect.set(hud.attackBtnRect.left - subSize * 0.5f, hud.attackBtnRect.top - subSize - pad * 0.5f, hud.attackBtnRect.left + subSize * 0.5f, hud.attackBtnRect.top - pad * 0.5f)

        // 4 Ability buttons arc / row around attack
        val abSize = (h * 0.125f).coerceIn(66f, 108f)
        val abBaseY = h - pad - abSize
        val abStartX = hud.lastHitBtnRect.left - pad * 0.8f - abSize * 4 - pad * 1.8f
        for (i in 0..3) {
            val left = abStartX + i * (abSize + pad * 0.55f)
            hud.abilityRects[i].set(left, abBaseY, left + abSize, abBaseY + abSize)
            hud.levelUpRects[i].set(left + abSize * 0.15f, abBaseY - abSize * 0.42f, left + abSize * 0.85f, abBaseY - 4f)
        }

        // 6 Item slots cleanly arranged right above ability bar
        val itemSize = (h * 0.088f).coerceIn(48f, 76f)
        val itemY = abBaseY - abSize * 0.45f - itemSize - pad * 0.5f
        val itemStartX = abStartX
        for (i in 0..5) {
            val left = itemStartX + i * (itemSize + pad * 0.45f)
            hud.itemRects[i].set(left, itemY, left + itemSize, itemY + itemSize)
        }
    }

    fun screenToWorld(sx: Float, sy: Float, screenW: Float, screenH: Float): Vec2 {
        val wx = cameraPos.x + (sx - screenW * 0.5f) / zoom
        val wy = cameraPos.y + (sy - screenH * 0.5f) / zoom
        return Vec2(wx.coerceIn(0f, MapLayout.SIZE), wy.coerceIn(0f, MapLayout.SIZE))
    }

    fun render(canvas: Canvas, world: GameWorld, w: Float, h: Float) {
        ensureTerrainBitmap()
        layoutHud(w, h)

        if (cameraLocked) {
            cameraPos.x = DotaMath.lerp(cameraPos.x, world.playerHero.pos.x, 0.18f)
            cameraPos.y = DotaMath.lerp(cameraPos.y, world.playerHero.pos.y, 0.18f)
        }
        zoom = (h / 820f).coerceIn(0.65f, 1.35f)

        canvas.drawColor(0xFF0A0C10.toInt())
        canvas.save()
        canvas.translate(w * 0.5f, h * 0.5f)
        canvas.scale(zoom, zoom)
        canvas.translate(-cameraPos.x, -cameraPos.y)

        // 1. Terrain Bitmap
        terrainBitmap?.let { bmp ->
            rectF.set(0f, 0f, MapLayout.SIZE, MapLayout.SIZE)
            canvas.drawBitmap(bmp, null, rectF, null)
        }

        // 2. Special Map Features (Twin Gates, Lotus, Fountain Rings)
        drawMapFeatures(canvas, world)

        // 3. AoE Zones & Fissures
        drawZones(canvas, world)

        // 4. Trees (within viewport)
        val halfW = (w * 0.55f) / zoom
        val halfH = (h * 0.55f) / zoom
        drawTrees(canvas, world, halfW, halfH)

        // 5. Wards, Runes, Dropped Items
        drawWardsAndRunes(canvas, world)

        // 6. Structures (Towers, Barracks, Ancients)
        drawStructures(canvas, world)

        // 7. Units (Neutrals, Roshan, Tormentors, Creeps, Couriers, Heroes)
        drawUnits(canvas, world, halfW, halfH)

        // 8. Projectiles, Particles, Floating Text
        drawProjectilesAndFx(canvas, world)

        // 9. Aiming Reticle Preview
        if (aimingAbilityIdx in 0..3) {
            drawAimPreview(canvas, world)
        }

        // 10. Fog of War (world-space cells inside viewport)
        drawFogOfWar(canvas, world, halfW, halfH)

        canvas.restore()

        // 11. Nighttime ambient tint
        if (world.isNight) {
            canvas.drawColor(0x280B142B)
        }

        // 12. On-Screen HUD & Minimap
        drawHud(canvas, world, w, h)
    }

    private fun drawMapFeatures(canvas: Canvas, world: GameWorld) {
        // Fountains
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = 0x662ECC71
        canvas.drawCircle(MapLayout.radiantFountain.x, MapLayout.radiantFountain.y, 350f, paint)
        paint.color = 0x66E74C3C
        canvas.drawCircle(MapLayout.direFountain.x, MapLayout.direFountain.y, 350f, paint)

        // Twin Gates
        paint.style = Paint.Style.FILL
        paint.color = 0x889B59B6.toInt()
        canvas.drawCircle(MapLayout.twinGateTopLeft.x, MapLayout.twinGateTopLeft.y, 44f, paint)
        canvas.drawCircle(MapLayout.twinGateBotRight.x, MapLayout.twinGateBotRight.y, 44f, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("GATE", MapLayout.twinGateTopLeft.x, MapLayout.twinGateTopLeft.y + 6f, textPaint)
        canvas.drawText("GATE", MapLayout.twinGateBotRight.x, MapLayout.twinGateBotRight.y + 6f, textPaint)
    }

    private fun drawZones(canvas: Canvas, world: GameWorld) {
        for (z in world.zones) {
            if (z.kind == "fissure" && z.endPos != null) {
                linePaint.color = 0xFF6D4C41.toInt()
                linePaint.strokeWidth = 36f
                linePaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(z.pos.x, z.pos.y, z.endPos.x, z.endPos.y, linePaint)
                linePaint.color = 0xFFD7CCC8.toInt()
                linePaint.strokeWidth = 8f
                canvas.drawLine(z.pos.x, z.pos.y, z.endPos.x, z.endPos.y, linePaint)
                continue
            }
            paint.style = Paint.Style.FILL
            paint.color = (z.color and 0x00FFFFFF) or 0x44000000
            canvas.drawCircle(z.pos.x, z.pos.y, z.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = z.color
            canvas.drawCircle(z.pos.x, z.pos.y, z.radius, paint)
        }
    }

    private fun drawTrees(canvas: Canvas, world: GameWorld, halfW: Float, halfH: Float) {
        for (t in world.trees) {
            if (!t.alive) continue
            if (kotlin.math.abs(t.pos.x - cameraPos.x) > halfW + 50f || kotlin.math.abs(t.pos.y - cameraPos.y) > halfH + 50f) continue
            val isDireSide = t.pos.x > t.pos.y
            paint.style = Paint.Style.FILL
            paint.color = 0x55000000
            canvas.drawCircle(t.pos.x + 6f, t.pos.y + 8f, t.radius, paint)
            paint.color = if (isDireSide) 0xFF3E2723.toInt() else 0xFF1B5E20.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, t.radius, paint)
            paint.color = if (isDireSide) 0xFF6D4C41.toInt() else 0xFF2E7D32.toInt()
            canvas.drawCircle(t.pos.x - 4f, t.pos.y - 4f, t.radius * 0.65f, paint)
        }
    }

    private fun drawWardsAndRunes(canvas: Canvas, world: GameWorld) {
        for (w in world.wards) {
            val visible = w.team == world.playerTeam || world.fogOfWar.hasTrueSight(w.pos.x, w.pos.y)
            if (!visible) continue
            paint.style = Paint.Style.FILL
            paint.color = if (w.isSentry) 0xFF38BDF8.toInt() else 0xFFF1C40F.toInt()
            canvas.drawCircle(w.pos.x, w.pos.y, 14f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0x33FFFFFF
            canvas.drawCircle(w.pos.x, w.pos.y, if (w.isSentry) 520f else 850f, paint)
        }

        for (r in world.runes) {
            if (!r.alive) continue
            paint.style = Paint.Style.FILL
            paint.color = r.type.color
            canvas.drawCircle(r.pos.x, r.pos.y, 16f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Color.WHITE
            canvas.drawCircle(r.pos.x, r.pos.y, 22f, paint)
            textPaint.color = r.type.color
            textPaint.textSize = 16f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(r.type.label, r.pos.x, r.pos.y - 26f, textPaint)
        }

        for (d in world.droppedItems) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFD700.toInt()
            canvas.drawCircle(d.pos.x, d.pos.y, 18f, paint)
            textPaint.color = 0xFFFFD700.toInt()
            textPaint.textSize = 18f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(d.itemId.uppercase(), d.pos.x, d.pos.y - 24f, textPaint)
        }
    }

    private fun drawStructures(canvas: Canvas, world: GameWorld) {
        for (t in world.towers) {
            if (!t.alive) continue
            val col = if (t.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
            // Tower range ring if player is nearby
            if (t.pos.distTo(world.playerHero.pos) < t.attackRange + 260f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = (col and 0x00FFFFFF) or 0x44000000
                canvas.drawCircle(t.pos.x, t.pos.y, t.attackRange, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = 0xFF374151.toInt()
            canvas.drawCircle(t.pos.x, t.pos.y, t.radius, paint)
            paint.color = col
            canvas.drawCircle(t.pos.x, t.pos.y, t.radius * 0.56f, paint)
            if (t.fortifyTimer > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = 0xFF38BDF8.toInt()
                canvas.drawCircle(t.pos.x, t.pos.y, t.radius + 8f, paint)
            }
            drawHealthBar(canvas, t.pos.x, t.pos.y - t.radius - 14f, 76f, 9f, t.hp, t.maxHp, col, 500f)
        }

        for (b in world.barracks) {
            if (!b.alive) continue
            val col = if (b.team == Team.RADIANT) 0xFF27AE60.toInt() else 0xFFC0392B.toInt()
            paint.style = Paint.Style.FILL
            paint.color = 0xFF2C3E50.toInt()
            canvas.drawRect(b.pos.x - 28f, b.pos.y - 28f, b.pos.x + 28f, b.pos.y + 28f, paint)
            paint.color = col
            canvas.drawRect(b.pos.x - 16f, b.pos.y - 16f, b.pos.x + 16f, b.pos.y + 16f, paint)
            drawHealthBar(canvas, b.pos.x, b.pos.y - 40f, 60f, 7f, b.hp, b.maxHp, col)
        }

        for (anc in listOf(world.radiantAncient, world.direAncient)) {
            if (!anc.alive) continue
            val col = if (anc.team == Team.RADIANT) 0xFF00E5FF.toInt() else 0xFFFF3D00.toInt()
            paint.style = Paint.Style.FILL
            paint.color = 0xFF1F2937.toInt()
            canvas.drawCircle(anc.pos.x, anc.pos.y, anc.radius, paint)
            paint.color = col
            canvas.drawCircle(anc.pos.x, anc.pos.y, anc.radius * 0.6f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = 0xFFFFD700.toInt()
            canvas.drawCircle(anc.pos.x, anc.pos.y, anc.radius * 0.85f, paint)
            drawHealthBar(canvas, anc.pos.x, anc.pos.y - anc.radius - 18f, 110f, 12f, anc.hp, anc.maxHp, col, 500f)
        }
    }

    private fun drawUnits(canvas: Canvas, world: GameWorld, halfW: Float, halfH: Float) {
        // Creeps
        for (c in world.creeps) {
            if (!c.alive) continue
            if (kotlin.math.abs(c.pos.x - cameraPos.x) > halfW + 40f || kotlin.math.abs(c.pos.y - cameraPos.y) > halfH + 40f) continue
            if (c.team != world.playerTeam && !world.fogOfWar.isVisible(c.pos.x, c.pos.y)) continue
            val col = if (c.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
            paint.style = Paint.Style.FILL
            paint.color = col
            canvas.drawCircle(c.pos.x, c.pos.y, c.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (c.type == CreepType.MEGA_MELEE) 0xFFFFD700.toInt() else Color.BLACK
            canvas.drawCircle(c.pos.x, c.pos.y, c.radius, paint)
            drawHealthBar(canvas, c.pos.x, c.pos.y - c.radius - 9f, 34f, 4f, c.hp, c.maxHp, col)
        }

        // Neutrals
        for (n in world.neutrals) {
            if (!n.alive || !world.fogOfWar.isVisible(n.pos.x, n.pos.y)) continue
            paint.style = Paint.Style.FILL
            paint.color = 0xFFA1887F.toInt()
            canvas.drawCircle(n.pos.x, n.pos.y, n.radius, paint)
            drawHealthBar(canvas, n.pos.x, n.pos.y - n.radius - 9f, 36f, 5f, n.hp, n.maxHp, 0xFFF39C12.toInt())
        }

        // Roshan & Tormentors
        if (world.roshan.alive && world.fogOfWar.isVisible(world.roshan.pos.x, world.roshan.pos.y)) {
            val r = world.roshan
            paint.style = Paint.Style.FILL
            paint.color = 0xFF7F1D1D.toInt()
            canvas.drawCircle(r.pos.x, r.pos.y, r.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = 0xFFFF9800.toInt()
            canvas.drawCircle(r.pos.x, r.pos.y, r.radius, paint)
            drawHealthBar(canvas, r.pos.x, r.pos.y - r.radius - 16f, 96f, 10f, r.hp, r.maxHp, 0xFFFF9800.toInt(), 1000f)
        }

        for (tor in listOf(world.radiantTormentor, world.direTormentor)) {
            if (!tor.alive || !world.fogOfWar.isVisible(tor.pos.x, tor.pos.y)) continue
            paint.style = Paint.Style.FILL
            paint.color = 0xFF06B6D4.toInt()
            canvas.drawCircle(tor.pos.x, tor.pos.y, tor.radius, paint)
            drawHealthBar(canvas, tor.pos.x, tor.pos.y - tor.radius - 14f, 70f, 8f, tor.hp, tor.maxHp, 0xFF38BDF8.toInt())
        }

        // Couriers
        for (c in world.couriers) {
            if (!c.alive || (!c.delivering && !c.returning)) continue
            if (c.team != world.playerTeam && !world.fogOfWar.isVisible(c.pos.x, c.pos.y)) continue
            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFCA28.toInt()
            canvas.drawCircle(c.pos.x, c.pos.y, 13f, paint)
        }

        // Heroes
        for (h in world.heroes) {
            if (!h.alive) continue
            val isAlly = h.team == world.playerTeam
            if (!isAlly) {
                if (!world.fogOfWar.isVisible(h.pos.x, h.pos.y)) continue
                if (h.isInvisible() && !world.fogOfWar.hasTrueSight(h.pos.x, h.pos.y)) continue
            }
            val alpha = if (h.isInvisible()) 115 else 255

            // Blade Fury / BKB aura
            if (h.hasStatus(StatusType.MAGIC_IMMUNE) || h.hasStatus(StatusType.BLADE_FURY)) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = 0xFFFFD700.toInt()
                canvas.drawCircle(h.pos.x, h.pos.y, h.radius + 10f, paint)
            }

            // Hero shadow
            paint.style = Paint.Style.FILL
            paint.color = 0x66000000
            canvas.drawCircle(h.pos.x + 4f, h.pos.y + 7f, h.radius, paint)

            // Hero body + team ring + facing weapon line
            paint.color = (h.def.primaryColor and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(h.pos.x, h.pos.y, h.radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (h.isPlayer) 4.5f else 3f
            paint.color = if (isAlly) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
            canvas.drawCircle(h.pos.x, h.pos.y, h.radius, paint)

            // Facing weapon pointer
            val wx = h.pos.x + cos(h.facingAngle) * (h.radius + 14f)
            val wy = h.pos.y + sin(h.facingAngle) * (h.radius + 14f)
            linePaint.color = h.def.accentColor
            linePaint.strokeWidth = 6f
            linePaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(h.pos.x, h.pos.y, wx, wy, linePaint)

            // Signature Hero Details (Invoker Q/W/E orbs, Chronos rings, Jugg spin)
            if (h.def.id == "invoker") {
                val orbColors = intArrayOf(0xFF38BDF8.toInt(), 0xFFA855F7.toInt(), 0xFFFF9800.toInt())
                for (oi in 0..2) {
                    val ang = world.gameTime * 3.2f + oi * 2.094f
                    paint.style = Paint.Style.FILL
                    paint.color = orbColors[oi]
                    canvas.drawCircle(h.pos.x + cos(ang) * 38f, h.pos.y + sin(ang) * 38f, 6.5f, paint)
                }
            } else if (h.def.id == "chronos") {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                paint.color = 0xAAFFD700.toInt()
                canvas.drawCircle(h.pos.x, h.pos.y, h.radius + 14f, paint)
            }
            if (h.hasStatus(StatusType.BLADE_FURY)) {
                val spinAng = world.gameTime * 14f
                linePaint.color = 0xFFF39C12.toInt()
                linePaint.strokeWidth = 4f
                canvas.drawLine(
                    h.pos.x - cos(spinAng) * 55f, h.pos.y - sin(spinAng) * 55f,
                    h.pos.x + cos(spinAng) * 55f, h.pos.y + sin(spinAng) * 55f, linePaint
                )
            }

            // Dota-style HP + Mana + Level badge
            val barCol = if (h.isPlayer) 0xFF2ECC71.toInt() else if (isAlly) 0xFF3498DB.toInt() else 0xFFE74C3C.toInt()
            drawHealthBar(canvas, h.pos.x, h.pos.y - h.radius - 20f, 68f, 8f, h.hp, h.maxHp, barCol, 250f)
            drawHealthBar(canvas, h.pos.x, h.pos.y - h.radius - 10f, 68f, 4.5f, h.mana, h.maxMana, 0xFF38BDF8.toInt())

            textPaint.color = Color.WHITE
            textPaint.textSize = 15f
            textPaint.textAlign = Paint.Align.CENTER
            val statusTag = when {
                h.hasStatus(StatusType.CHRONO_FREEZE) -> " [FROZEN]"
                h.hasStatus(StatusType.STUN) -> " [STUN]"
                h.hasStatus(StatusType.HEX) -> " [HEX]"
                h.hasStatus(StatusType.SILENCE) -> " [SILENCED]"
                h.hasAegis -> " [AEGIS]"
                else -> ""
            }
            canvas.drawText("Lv${h.level} ${h.def.name}$statusTag", h.pos.x, h.pos.y - h.radius - 25f, textPaint)
        }
    }

    private fun drawHealthBar(
        canvas: Canvas, cx: Float, top: Float, width: Float, height: Float,
        hp: Float, maxHp: Float, color: Int, notchStep: Float = 0f
    ) {
        val left = cx - width * 0.5f
        paint.style = Paint.Style.FILL
        paint.color = 0xCC0F172A.toInt()
        canvas.drawRect(left - 1f, top - 1f, left + width + 1f, top + height + 1f, paint)
        val pct = (hp / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
        paint.color = color
        canvas.drawRect(left, top, left + width * pct, top + height, paint)
        if (notchStep > 0f && maxHp > notchStep) {
            linePaint.color = 0xAA000000.toInt()
            linePaint.strokeWidth = 1.5f
            var cur = notchStep
            while (cur < maxHp) {
                val nx = left + width * (cur / maxHp)
                canvas.drawLine(nx, top, nx, top + height, linePaint)
                cur += notchStep
            }
        }
    }

    private fun drawProjectilesAndFx(canvas: Canvas, world: GameWorld) {
        for (p in world.projectiles) {
            if (p.kind == "hook") {
                val shooter = world.heroes.firstOrNull { it.id == p.sourceId }
                if (shooter != null) {
                    linePaint.color = 0xFFC0392B.toInt()
                    linePaint.strokeWidth = 5f
                    canvas.drawLine(shooter.pos.x, shooter.pos.y, p.pos.x, p.pos.y, linePaint)
                }
            }
            paint.style = Paint.Style.FILL
            paint.color = p.color
            canvas.drawCircle(p.pos.x, p.pos.y, p.radius.coerceAtMost(18f), paint)
        }
        for (pt in world.particles) {
            val alpha = ((pt.life / pt.maxLife).coerceIn(0f, 1f) * 255).toInt()
            paint.style = Paint.Style.FILL
            paint.color = (pt.color and 0x00FFFFFF) or (alpha shl 24)
            canvas.drawCircle(pt.pos.x, pt.pos.y, pt.size, paint)
        }
        for (ft in world.floatingTexts) {
            textPaint.color = ft.color
            textPaint.textSize = 20f * ft.scale
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(ft.text, ft.pos.x, ft.pos.y, textPaint)
        }
    }

    private fun drawAimPreview(canvas: Canvas, world: GameWorld) {
        val ab = world.playerHero.def.abilities.getOrNull(aimingAbilityIdx) ?: return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xAA38BDF8.toInt()
        if (ab.castRange > 0f) {
            canvas.drawCircle(world.playerHero.pos.x, world.playerHero.pos.y, ab.castRange, paint)
        }
        paint.style = Paint.Style.FILL
        paint.color = 0x4438BDF8
        val rad = ab.radius.takeIf { it > 0f } ?: 90f
        canvas.drawCircle(aimWorldPos.x, aimWorldPos.y, rad, paint)
    }

    private fun drawFogOfWar(canvas: Canvas, world: GameWorld, halfW: Float, halfH: Float) {
        val cs = world.fogOfWar.cellWorldSize()
        val n = world.fogOfWar.size()
        val minGx = ((cameraPos.x - halfW) / cs).toInt().coerceIn(0, n - 1)
        val maxGx = ((cameraPos.x + halfW) / cs).toInt().coerceIn(0, n - 1)
        val minGy = ((cameraPos.y - halfH) / cs).toInt().coerceIn(0, n - 1)
        val maxGy = ((cameraPos.y + halfH) / cs).toInt().coerceIn(0, n - 1)

        paint.style = Paint.Style.FILL
        for (gy in minGy..maxGy) {
            for (gx in minGx..maxGx) {
                when (world.fogOfWar.getCellState(gx, gy)) {
                    0 -> {
                        paint.color = 0xC805070B.toInt()
                        canvas.drawRect(gx * cs, gy * cs, (gx + 1) * cs + 1f, (gy + 1) * cs + 1f, paint)
                    }
                    1 -> {
                        paint.color = 0x7205070B
                        canvas.drawRect(gx * cs, gy * cs, (gx + 1) * cs + 1f, (gy + 1) * cs + 1f, paint)
                    }
                }
            }
        }
    }

    private fun drawHud(canvas: Canvas, world: GameWorld, w: Float, h: Float) {
        // 1. Minimap
        drawMinimap(canvas, world, w, h)

        // 2. Top Score & Clock Bar
        val topW = w * 0.34f
        val topH = h * 0.08f
        val topLeft = w * 0.5f - topW * 0.5f
        paint.style = Paint.Style.FILL
        paint.color = 0xCC111622.toInt()
        rectF.set(topLeft, 8f, topLeft + topW, 8f + topH)
        canvas.drawRoundRect(rectF, 12f, 12f, paint)

        val mins = (world.gameTime / 60f).toInt()
        val secs = (world.gameTime % 60f).toInt()
        val clock = "%02d:%02d".format(mins, secs)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = topH * 0.45f
        textPaint.color = if (world.isNight) 0xFF93C5FD.toInt() else 0xFFFDE047.toInt()
        canvas.drawText(if (world.isNight) "NIGHT $clock" else "DAY $clock", w * 0.5f, 8f + topH * 0.65f, textPaint)

        textPaint.color = 0xFF2ECC71.toInt()
        textPaint.textSize = topH * 0.52f
        canvas.drawText("${world.radiantKills}", topLeft + topW * 0.16f, 8f + topH * 0.68f, textPaint)
        textPaint.color = 0xFFE74C3C.toInt()
        canvas.drawText("${world.direKills}", topLeft + topW * 0.84f, 8f + topH * 0.68f, textPaint)

        // Player K/D/A & Gold pill below top clock
        val ph = world.playerHero
        textPaint.color = 0xFFF1C40F.toInt()
        textPaint.textSize = topH * 0.36f
        canvas.drawText(
            "GOLD: ${ph.gold}   K/D/A: ${ph.kills}/${ph.deaths}/${ph.assists}   LH/DN: ${ph.lastHits}/${ph.denies}",
            w * 0.5f, 8f + topH + topH * 0.48f, textPaint
        )

        // 3. Top-right Menu Buttons
        drawButton(canvas, hud.shopBtnRect, "SHOP (${ph.gold}g)", 0xCC78350F.toInt(), 0xFFF59E0B.toInt())
        val hasTalent = ph.availableTalentTier() != null
        drawButton(canvas, hud.talentBtnRect, if (hasTalent) "TALENT!" else "TALENTS",
            if (hasTalent) 0xCC065F46.toInt() else 0xCC1E293B.toInt(),
            if (hasTalent) 0xFF34D399.toInt() else 0xFF64748B.toInt())
        drawButton(canvas, hud.statsBtnRect, "SCORE", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())
        drawButton(canvas, hud.pauseBtnRect, "MENU", 0xCC1E293B.toInt(), 0xFF94A3B8.toInt())
        drawButton(canvas, hud.camBtnRect, if (cameraLocked) "CAM: LOCK" else "CAM: FREE", 0xCC1E293B.toInt(), 0xFF38BDF8.toInt())

        // 4. Utility & Quick Buy Bottom-Left
        val tpLabel = if (ph.tpCooldown > 0f) "TP ${ph.tpCooldown.toInt()}" else "TP"
        drawButton(canvas, hud.tpBtnRect, tpLabel, 0xCC1E3A8A.toInt(), 0xFF60A5FA.toInt())
        val stashCount = ph.stash.size
        drawButton(canvas, hud.courierBtnRect, if (stashCount > 0) "COUR($stashCount)" else "COUR", 0xCC713F12.toInt(), 0xFFFBBF24.toInt())
        val glyphCd = if (ph.team == Team.RADIANT) world.radiantGlyphCd else world.direGlyphCd
        drawButton(canvas, hud.glyphBtnRect, if (glyphCd > 0f) "G:${glyphCd.toInt()}" else "GLYPH", 0xCC0C4A6E.toInt(), 0xFF38BDF8.toInt())

        ph.quickBuyItemId?.let { qid ->
            ItemCatalog.get(qid)?.let { def ->
                val eff = world.effectiveItemCost(ph, def)
                val afford = ph.gold >= eff
                drawButton(canvas, hud.quickBuyRect, "BUY ${def.shortCode} (${eff}g)",
                    if (afford) 0xCC065F46.toInt() else 0xCC1E293B.toInt(),
                    if (afford) 0xFF34D399.toInt() else 0xFFF59E0B.toInt())
            }
        }

        // 5. Right Attack + Last Hit + Deny
        drawRoundButton(canvas, hud.attackBtnRect, "ATK", 0xCCB91C1C.toInt(), 0xFFF87171.toInt())
        drawRoundButton(canvas, hud.lastHitBtnRect, "LH", 0xCC854D0E.toInt(), 0xFFFACC15.toInt())
        drawRoundButton(canvas, hud.denyBtnRect, "!", 0xCC0369A1.toInt(), 0xFF38BDF8.toInt())

        // 6. 4 Abilities (Q, W, E, R) + Level up badges
        for (i in 0..3) {
            val ab = ph.def.abilities[i]
            val lvl = ph.abilityLevels[i]
            val cd = ph.abilityCooldowns[i]
            val r = hud.abilityRects[i]
            val bg = when {
                lvl == 0 -> 0xCC0F172A.toInt()
                ab.targetType == TargetType.PASSIVE -> 0xCC1E293B.toInt()
                cd > 0f -> 0xCC334155.toInt()
                else -> 0xCC1D4ED8.toInt()
            }
            val border = if (ab.isUltimate) 0xFFF59E0B.toInt() else 0xFF60A5FA.toInt()
            val label = when {
                lvl == 0 -> "${ab.keyLabel}\nLOCK"
                cd > 0f -> "${ab.keyLabel}\n${"%.1f".format(cd)}"
                ab.maxCharges > 1 -> "${ab.keyLabel} (${ph.abilityCharges[i]})\n${ab.name.take(6)}"
                else -> "${ab.keyLabel}\n${ab.name.take(7)}"
            }
            drawButton(canvas, r, label, bg, border)

            // Level dots
            for (d in 0 until ab.maxLevel) {
                paint.style = Paint.Style.FILL
                paint.color = if (d < lvl) 0xFFFACC15.toInt() else 0xFF475569.toInt()
                canvas.drawCircle(r.left + 10f + d * 11f, r.bottom - 8f, 3.5f, paint)
            }

            if (ph.canLevelAbility(i)) {
                drawButton(canvas, hud.levelUpRects[i], "+ LVL", 0xEE15803D.toInt(), 0xFF4ADE80.toInt())
            }
        }

        // 7. 6 Inventory Slots
        for (i in 0..5) {
            val r = hud.itemRects[i]
            val slot = ph.inventory.getOrNull(i)
            if (slot != null) {
                val item = ItemCatalog.get(slot.itemId)
                val cd = slot.cooldownRemaining
                val txt = if (cd > 0f) "${item?.shortCode ?: "?"} ${cd.toInt()}"
                else if (slot.stacks > 1) "${item?.shortCode} x${slot.stacks}"
                else if (item?.id == "magic_wand") "WND(${slot.charges})"
                else item?.shortCode ?: "?"
                drawButton(canvas, r, txt, 0xCC1E293B.toInt(), item?.color ?: 0xFF94A3B8.toInt())
            } else {
                drawButton(canvas, r, "", 0x660F172A, 0xFF334155.toInt())
            }
        }

        // 8. Virtual Joystick Visual
        val jOrigin = joystickOrigin
        val jThumb = joystickThumb
        if (jOrigin != null && jThumb != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0x66FFFFFF
            canvas.drawCircle(jOrigin.x, jOrigin.y, 70f, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xAA38BDF8.toInt()
            canvas.drawCircle(jThumb.x, jThumb.y, 28f, paint)
        }

        // 9. Dead & Buyback Banner
        if (!ph.alive) {
            textPaint.color = 0xFFEF4444.toInt()
            textPaint.textSize = h * 0.055f
            textPaint.textAlign = Paint.Align.CENTER
            val msg = if (ph.aegisRespawnTimer > 0f) "AEGIS REINCARNATING: ${"%.1f".format(ph.aegisRespawnTimer)}s"
            else "DEAD - RESPAWN IN ${ph.respawnTimer.toInt()}s"
            canvas.drawText(msg, w * 0.5f, h * 0.67f, textPaint)

            if (ph.aegisRespawnTimer <= 0f && ph.buybackCooldown <= 0f) {
                val bbCost = ph.buybackCost()
                val canBb = ph.gold >= bbCost
                drawButton(canvas, hud.buybackBtnRect, "BUYBACK (${bbCost}g)",
                    if (canBb) 0xEEB45309.toInt() else 0xAA1E293B.toInt(), 0xFFFACC15.toInt())
            }
        }

        // 10. Announcements Banner
        world.announcements.lastOrNull()?.let { ann ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCC090D16.toInt()
            rectF.set(w * 0.25f, h * 0.16f, w * 0.75f, h * 0.27f)
            canvas.drawRoundRect(rectF, 14f, 14f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = ann.color
            textPaint.textSize = h * 0.042f
            canvas.drawText(ann.title, w * 0.5f, h * 0.21f, textPaint)
            textPaint.color = Color.WHITE
            textPaint.textSize = h * 0.026f
            canvas.drawText(ann.subtitle, w * 0.5f, h * 0.25f, textPaint)
        }

        // 11. Bottom-Center Dota Hero Status Plate (HP / MP / XP / Stats / Scepter / Aegis)
        val plateLeft = hud.glyphBtnRect.right + w * 0.015f
        val plateRight = hud.abilityRects[0].left - w * 0.015f
        if (plateRight - plateLeft > 120f) {
            val plateTop = h - h * 0.125f
            val plateBot = h - 8f
            paint.style = Paint.Style.FILL
            paint.color = 0xCC0D1320.toInt()
            rectF.set(plateLeft, plateTop, plateRight, plateBot)
            canvas.drawRoundRect(rectF, 10f, 10f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = 0xFF334155.toInt()
            canvas.drawRoundRect(rectF, 10f, 10f, paint)

            val padX = 10f
            val innerW = (plateRight - plateLeft) - padX * 2f
            val rowH = (plateBot - plateTop - 12f) / 3f

            // Top line: Lv + Stats + Scepter/Shard/Aegis tags
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = (rowH * 0.62f).coerceIn(11f, 16f)
            textPaint.color = 0xFFF5B041.toInt()
            val tags = buildString {
                append("Lv${ph.level}  DMG:${ph.cachedDamage.toInt()}  ARM:${ph.armor.toInt()}  MS:${ph.cachedMoveSpeed.toInt()}")
                if (ph.hasScepter) append("  [AGH]")
                if (ph.hasShard) append("  [SHD]")
                if (ph.hasAegis) append("  [AEGIS]")
            }
            canvas.drawText(tags, plateLeft + padX, plateTop + rowH * 0.72f, textPaint)

            // HP Bar
            val hpY = plateTop + rowH + 3f
            val hpPct = (ph.hp / ph.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
            paint.style = Paint.Style.FILL
            paint.color = 0xFF14291E.toInt()
            canvas.drawRect(plateLeft + padX, hpY, plateLeft + padX + innerW, hpY + rowH * 0.8f, paint)
            paint.color = 0xFF22C55E.toInt()
            canvas.drawRect(plateLeft + padX, hpY, plateLeft + padX + innerW * hpPct, hpY + rowH * 0.8f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.WHITE
            textPaint.textSize = (rowH * 0.56f).coerceIn(10f, 15f)
            canvas.drawText("${ph.hp.toInt()} / ${ph.maxHp.toInt()}  (+${"%.1f".format(ph.cachedHpRegen)})",
                plateLeft + padX + innerW * 0.5f, hpY + rowH * 0.62f, textPaint)

            // Mana Bar
            val mpY = hpY + rowH + 2f
            val mpPct = (ph.mana / ph.maxMana.coerceAtLeast(1f)).coerceIn(0f, 1f)
            paint.color = 0xFF112238.toInt()
            canvas.drawRect(plateLeft + padX, mpY, plateLeft + padX + innerW, mpY + rowH * 0.8f, paint)
            paint.color = 0xFF38BDF8.toInt()
            canvas.drawRect(plateLeft + padX, mpY, plateLeft + padX + innerW * mpPct, mpY + rowH * 0.8f, paint)
            textPaint.color = Color.WHITE
            canvas.drawText("${ph.mana.toInt()} / ${ph.maxMana.toInt()}  (+${"%.1f".format(ph.cachedManaRegen)})",
                plateLeft + padX + innerW * 0.5f, mpY + rowH * 0.62f, textPaint)
        }
    }

    private fun drawMinimap(canvas: Canvas, world: GameWorld, w: Float, h: Float) {
        val mm = hud.minimapRect
        paint.style = Paint.Style.FILL
        paint.color = 0xEE0B0F17.toInt()
        canvas.drawRect(mm, paint)
        terrainBitmap?.let { canvas.drawBitmap(it, null, mm, null) }

        val sx = mm.width() / MapLayout.SIZE
        val sy = mm.height() / MapLayout.SIZE

        // Towers
        for (t in world.towers) {
            if (!t.alive) continue
            paint.color = if (t.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt()
            canvas.drawRect(mm.left + t.pos.x * sx - 2.5f, mm.top + t.pos.y * sy - 2.5f,
                mm.left + t.pos.x * sx + 2.5f, mm.top + t.pos.y * sy + 2.5f, paint)
        }

        // Runes & Roshan
        if (world.roshan.alive) {
            paint.color = 0xFFFF9800.toInt()
            canvas.drawCircle(mm.left + world.roshan.pos.x * sx, mm.top + world.roshan.pos.y * sy, 3.5f, paint)
        }
        for (r in world.runes) {
            if (!r.alive) continue
            paint.color = r.type.color
            canvas.drawCircle(mm.left + r.pos.x * sx, mm.top + r.pos.y * sy, 3f, paint)
        }

        // Heroes
        for (h in world.heroes) {
            if (!h.alive) continue
            if (h.team != world.playerTeam && (!world.fogOfWar.isVisible(h.pos.x, h.pos.y) || h.isInvisible())) continue
            paint.color = if (h.isPlayer) 0xFFFFFFFF.toInt() else if (h.team == Team.RADIANT) 0xFF4ADE80.toInt() else 0xFFF87171.toInt()
            canvas.drawCircle(mm.left + h.pos.x * sx, mm.top + h.pos.y * sy, if (h.isPlayer) 5f else 4f, paint)
        }

        // Pings
        for (pg in world.pings) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = pg.color
            canvas.drawCircle(mm.left + pg.pos.x * sx, mm.top + pg.pos.y * sy, 8f, paint)
        }

        // Camera Viewport Rectangle on Minimap
        val halfVw = (w * 0.5f / zoom) * sx
        val halfVh = (h * 0.5f / zoom) * sy
        val camCx = mm.left + cameraPos.x * sx
        val camCy = mm.top + cameraPos.y * sy
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawRect(
            (camCx - halfVw).coerceIn(mm.left, mm.right),
            (camCy - halfVh).coerceIn(mm.top, mm.bottom),
            (camCx + halfVw).coerceIn(mm.left, mm.right),
            (camCy + halfVh).coerceIn(mm.top, mm.bottom),
            paint
        )

        // Minimap border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawRect(mm, paint)
    }

    private fun drawButton(canvas: Canvas, r: RectF, label: String, bg: Int, border: Int) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawRoundRect(r, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.color = border
        canvas.drawRoundRect(r, 10f, 10f, paint)

        if (label.isNotEmpty()) {
            val lines = label.split("\n")
            textPaint.color = Color.WHITE
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = (r.height() * (if (lines.size > 1) 0.24f else 0.31f)).coerceIn(13f, 22f)
            if (lines.size == 1) {
                canvas.drawText(lines[0], r.centerX(), r.centerY() + textPaint.textSize * 0.35f, textPaint)
            } else {
                canvas.drawText(lines[0], r.centerX(), r.centerY() - 3f, textPaint)
                canvas.drawText(lines[1], r.centerX(), r.centerY() + textPaint.textSize + 2f, textPaint)
            }
        }
    }

    private fun drawRoundButton(canvas: Canvas, r: RectF, label: String, bg: Int, border: Int) {
        paint.style = Paint.Style.FILL
        paint.color = bg
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = border
        canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = (r.height() * 0.30f).coerceIn(15f, 26f)
        canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.35f, textPaint)
    }
}
