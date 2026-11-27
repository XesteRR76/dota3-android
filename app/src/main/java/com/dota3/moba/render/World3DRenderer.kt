package com.dota3.moba.render

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.engine.*
import com.dota3.moba.model.StatusType
import com.dota3.moba.model.Team
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class ProjectedBar(
    val sx: Float,
    val sy: Float,
    val label: String,
    val hp: Float,
    val maxHp: Float,
    val mana: Float,
    val maxMana: Float,
    val barColor: Int,
    val isHero: Boolean,
    val heroId: String? = null,
    val level: Int = 1,
    val notchStep: Float = 0f
)

data class ProjectedText(
    val sx: Float,
    val sy: Float,
    val text: String,
    val color: Int,
    val scale: Float
)

class World3DRenderer(
    private val world: GameWorld,
    private val onFrameStep: (Float) -> Unit
) : GLSurfaceView.Renderer {

    val cameraPos = Vec2(800f, 4000f)
    var cameraLocked: Boolean = true
    var cameraZoomDist: Float = 860f

    // Drag-to-aim preview in 3D world
    @Volatile var aimingAbilityIdx: Int = -1
    @Volatile var aimWorldPos: Vec2 = Vec2()

    @Volatile var viewportWidth: Int = 1280
    @Volatile var viewportHeight: Int = 720

    // Snapshot for 2D HUD overlay thread
    @Volatile var projectedBars: List<ProjectedBar> = emptyList()
    @Volatile var projectedTexts: List<ProjectedText> = emptyList()

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val invVpMatrix = FloatArray(16)

    private var programId = 0
    private var uMvpLoc = 0
    private var uModelLoc = 0
    private var uTintLoc = 0
    private var uSunDirLoc = 0
    private var uAmbientLoc = 0
    private var uUseFogLoc = 0
    private var uFogTexLoc = 0
    private var uMapSizeLoc = 0
    private var uAlphaLoc = 0

    private var aPosLoc = 0
    private var aNormLoc = 0
    private var aColLoc = 0

    private var fogTexId = 0
    private val fogPixels = ByteBuffer.allocateDirect(96 * 96)

    private lateinit var terrainMesh: Mesh3D
    private lateinit var waterMesh: Mesh3D
    private lateinit var boxMesh: Mesh3D
    private lateinit var cylMesh: Mesh3D
    private lateinit var coneMesh: Mesh3D
    private lateinit var sphereMesh: Mesh3D
    private lateinit var ringMesh: Mesh3D

    private var lastFrameNs = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.05f, 0.08f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        programId = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(programId, "uMVP")
        uModelLoc = GLES20.glGetUniformLocation(programId, "uModel")
        uTintLoc = GLES20.glGetUniformLocation(programId, "uTint")
        uSunDirLoc = GLES20.glGetUniformLocation(programId, "uSunDir")
        uAmbientLoc = GLES20.glGetUniformLocation(programId, "uAmbient")
        uUseFogLoc = GLES20.glGetUniformLocation(programId, "uUseFog")
        uFogTexLoc = GLES20.glGetUniformLocation(programId, "uFogTex")
        uMapSizeLoc = GLES20.glGetUniformLocation(programId, "uMapSize")
        uAlphaLoc = GLES20.glGetUniformLocation(programId, "uAlpha")

        aPosLoc = GLES20.glGetAttribLocation(programId, "aPos")
        aNormLoc = GLES20.glGetAttribLocation(programId, "aNorm")
        aColLoc = GLES20.glGetAttribLocation(programId, "aCol")

        // Create 96x96 Fog of War texture
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        fogTexId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 96, 96, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, fogPixels)

        // Build 3D Terrain & Primitives
        terrainMesh = Mesh3D.createTerrainMesh(
            gridSize = 96,
            mapSize = MapLayout.SIZE,
            heightFn = { wx, wz -> terrainHeight(wx, wz) },
            colorFn = { wx, wz, h -> terrainColor(wx, wz, h) }
        )
        waterMesh = Mesh3D.createBox(MapLayout.SIZE, 2f, MapLayout.SIZE)
        boxMesh = Mesh3D.createBox(1f, 1f, 1f)
        cylMesh = Mesh3D.createCylinder(12, 0.5f, 0.5f, 1f)
        coneMesh = Mesh3D.createCylinder(12, 0.04f, 0.5f, 1f)
        sphereMesh = Mesh3D.createSphere(8, 12, 0.5f)
        ringMesh = Mesh3D.createRing(32, 0.86f, 1.0f)

        cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 44f, aspect, 80f, 5200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        onFrameStep(dt)

        if (cameraLocked) {
            cameraPos.x = DotaMath.lerp(cameraPos.x, world.playerHero.pos.x, 0.16f)
            cameraPos.y = DotaMath.lerp(cameraPos.y, world.playerHero.pos.y, 0.16f)
        }

        val targetY = terrainHeight(cameraPos.x, cameraPos.y)
        val camY = targetY + cameraZoomDist
        val camZOffset = cameraZoomDist * 0.72f
        Matrix.setLookAtM(
            viewMatrix, 0,
            cameraPos.x, camY, cameraPos.y + camZOffset,
            cameraPos.x, targetY + 25f, cameraPos.y,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVpMatrix, 0, vpMatrix, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programId)

        // Upload updated Fog of War texture
        uploadFogTexture()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glUniform1i(uFogTexLoc, 0)
        GLES20.glUniform1f(uMapSizeLoc, MapLayout.SIZE)

        // Day/Night Sun Lighting
        if (world.isNight) {
            GLES20.glUniform3f(uSunDirLoc, -0.3f, 0.85f, -0.4f)
            GLES20.glUniform3f(uAmbientLoc, 0.28f, 0.34f, 0.52f)
        } else {
            GLES20.glUniform3f(uSunDirLoc, 0.45f, 0.82f, -0.35f)
            GLES20.glUniform3f(uAmbientLoc, 0.46f, 0.46f, 0.44f)
        }

        // 1. Draw 3D Terrain with Fog of War
        GLES20.glUniform1i(uUseFogLoc, 1)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(terrainMesh, 1f, 1f, 1f)

        // 2. Draw 3D River Water Plane
        val waveOffset = sin(world.gameTime * 2.1f) * 1.8f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, MapLayout.SIZE * 0.5f, -12f + waveOffset, MapLayout.SIZE * 0.5f)
        GLES20.glUniform1f(uAlphaLoc, 0.72f)
        drawMesh(waterMesh, 0.10f, 0.44f, 0.64f)

        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        val bars = ArrayList<ProjectedBar>(40)
        val texts = ArrayList<ProjectedText>(30)

        // 3. Draw Trees (3D Trunks + Layered Pine Cones)
        for (t in world.trees) {
            if (!t.alive) continue
            if (abs(t.pos.x - cameraPos.x) > 1300f || abs(t.pos.y - cameraPos.y) > 1100f) continue
            val h = terrainHeight(t.pos.x, t.pos.y)
            val isDire = t.pos.x > t.pos.y
            // Trunk
            drawPart(cylMesh, t.pos.x, h + 22f, t.pos.y, 12f, 44f, 12f, 0f, 0.35f, 0.22f, 0.12f)
            // Lower canopy
            if (isDire) {
                drawPart(coneMesh, t.pos.x, h + 52f, t.pos.y, 52f, 46f, 52f, 0f, 0.32f, 0.14f, 0.20f)
                drawPart(coneMesh, t.pos.x, h + 74f, t.pos.y, 38f, 38f, 38f, 0f, 0.45f, 0.18f, 0.22f)
            } else {
                drawPart(coneMesh, t.pos.x, h + 52f, t.pos.y, 52f, 46f, 52f, 0f, 0.10f, 0.42f, 0.16f)
                drawPart(coneMesh, t.pos.x, h + 74f, t.pos.y, 38f, 38f, 38f, 0f, 0.16f, 0.58f, 0.22f)
            }
        }

        // 4. Twin Gates & Lotus Pools
        drawGate3D(MapLayout.twinGateTopLeft)
        drawGate3D(MapLayout.twinGateBotRight)

        // 5. Structures (Towers, Barracks, Ancients)
        for (t in world.towers) {
            if (!t.alive) continue
            if (abs(t.pos.x - cameraPos.x) > 1400f || abs(t.pos.y - cameraPos.y) > 1200f) continue
            val h = terrainHeight(t.pos.x, t.pos.y)
            val isRad = t.team == Team.RADIANT
            val r = if (isRad) 0.18f else 0.88f
            val g = if (isRad) 0.82f else 0.22f
            val b = if (isRad) 0.44f else 0.20f

            // Range ring when player is near
            if (t.pos.distTo(world.playerHero.pos) < t.attackRange + 260f) {
                GLES20.glUniform1f(uAlphaLoc, 0.35f)
                drawPart(ringMesh, t.pos.x, h + 3f, t.pos.y, t.attackRange * 2f, 1f, t.attackRange * 2f, 0f, r, g, b)
                GLES20.glUniform1f(uAlphaLoc, 1.0f)
            }

            // Stone Pedestal & Pillar & Floating Crystal
            drawPart(cylMesh, t.pos.x, h + 18f, t.pos.y, 76f, 36f, 76f, 0f, 0.26f, 0.29f, 0.34f)
            drawPart(cylMesh, t.pos.x, h + 66f, t.pos.y, 44f, 68f, 44f, 0f, 0.36f, 0.38f, 0.44f)
            val bob = sin(world.gameTime * 3.0f + t.pos.x) * 6f
            drawPart(sphereMesh, t.pos.x, h + 122f + bob, t.pos.y, 32f, 38f, 32f, world.gameTime * 60f, r, g, b)

            if (t.fortifyTimer > 0f) {
                GLES20.glUniform1f(uAlphaLoc, 0.4f)
                drawPart(sphereMesh, t.pos.x, h + 70f, t.pos.y, 115f, 150f, 115f, 0f, 0.2f, 0.75f, 1.0f)
                GLES20.glUniform1f(uAlphaLoc, 1.0f)
            }

            projectToScreen(t.pos.x, h + 158f, t.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "T${t.blueprint.tier}", t.hp, t.maxHp, 0f, 0f,
                    if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false, notchStep = 500f))
            }
        }

        for (b in world.barracks) {
            if (!b.alive) continue
            if (abs(b.pos.x - cameraPos.x) > 1400f || abs(b.pos.y - cameraPos.y) > 1200f) continue
            val h = terrainHeight(b.pos.x, b.pos.y)
            val isRad = b.team == Team.RADIANT
            drawPart(boxMesh, b.pos.x, h + 28f, b.pos.y, 64f, 56f, 64f, 0f, 0.24f, 0.26f, 0.30f)
            drawPart(boxMesh, b.pos.x, h + 58f, b.pos.y, 42f, 16f, 42f, 0f,
                if (isRad) 0.16f else 0.85f, if (isRad) 0.75f else 0.20f, if (isRad) 0.42f else 0.18f)
            projectToScreen(b.pos.x, h + 85f, b.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "", b.hp, b.maxHp, 0f, 0f,
                    if (isRad) 0xFF27AE60.toInt() else 0xFFC0392B.toInt(), false))
            }
        }

        for (anc in listOf(world.radiantAncient, world.direAncient)) {
            if (!anc.alive) continue
            val h = terrainHeight(anc.pos.x, anc.pos.y)
            val isRad = anc.team == Team.RADIANT
            val r = if (isRad) 0.12f else 0.95f
            val g = if (isRad) 0.88f else 0.22f
            val b = if (isRad) 0.78f else 0.10f
            drawPart(cylMesh, anc.pos.x, h + 26f, anc.pos.y, 128f, 52f, 128f, 0f, 0.20f, 0.24f, 0.30f)
            drawPart(coneMesh, anc.pos.x, h + 95f, anc.pos.y, 76f, 110f, 76f, world.gameTime * 25f, r, g, b)
            drawPart(ringMesh, anc.pos.x, h + 90f, anc.pos.y, 130f, 1f, 130f, world.gameTime * 45f, 1f, 0.84f, 0.1f)
            projectToScreen(anc.pos.x, h + 175f, anc.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "${anc.team.name} ANCIENT", anc.hp, anc.maxHp, 0f, 0f,
                    if (isRad) 0xFF00E5FF.toInt() else 0xFFFF3D00.toInt(), false, notchStep = 500f))
            }
        }

        // 6. Wards, Runes, Dropped Items
        GLES20.glUniform1i(uUseFogLoc, 0)
        for (w in world.wards) {
            val vis = w.team == world.playerTeam || world.fogOfWar.hasTrueSight(w.pos.x, w.pos.y)
            if (!vis) continue
            val h = terrainHeight(w.pos.x, w.pos.y)
            val (r, g, b) = if (w.isSentry) Triple(0.22f, 0.74f, 0.98f) else Triple(0.95f, 0.77f, 0.06f)
            drawPart(cylMesh, w.pos.x, h + 14f, w.pos.y, 10f, 28f, 10f, 0f, 0.3f, 0.3f, 0.35f)
            drawPart(sphereMesh, w.pos.x, h + 32f, w.pos.y, 18f, 18f, 18f, 0f, r, g, b)
        }

        for (r in world.runes) {
            if (!r.alive) continue
            val h = terrainHeight(r.pos.x, r.pos.y)
            val (cr, cg, cb) = rgbFloats(r.type.color)
            val bob = sin(world.gameTime * 4f) * 6f
            drawPart(sphereMesh, r.pos.x, h + 24f + bob, r.pos.y, 26f, 34f, 26f, world.gameTime * 80f, cr, cg, cb)
            drawPart(ringMesh, r.pos.x, h + 4f, r.pos.y, 52f, 1f, 52f, 0f, cr, cg, cb)
        }

        for (d in world.droppedItems) {
            val h = terrainHeight(d.pos.x, d.pos.y)
            drawPart(sphereMesh, d.pos.x, h + 20f, d.pos.y, 28f, 28f, 28f, world.gameTime * 90f, 1f, 0.84f, 0f)
        }

        // 7. Creeps, Neutrals, Roshan, Tormentors, Couriers
        for (c in world.creeps) {
            if (!c.alive) continue
            if (abs(c.pos.x - cameraPos.x) > 1100f || abs(c.pos.y - cameraPos.y) > 950f) continue
            if (c.team != world.playerTeam && !world.fogOfWar.isVisible(c.pos.x, c.pos.y)) continue
            val h = terrainHeight(c.pos.x, c.pos.y)
            val isRad = c.team == Team.RADIANT
            val sc = if (c.type == CreepType.MEGA_MELEE) 1.35f else if (c.type == CreepType.SUPER_MELEE) 1.15f else 1.0f
            val (r, g, b) = if (isRad) Triple(0.18f, 0.80f, 0.44f) else Triple(0.90f, 0.29f, 0.23f)
            drawPart(cylMesh, c.pos.x, h + 14f * sc, c.pos.y, 20f * sc, 28f * sc, 20f * sc, 0f, r, g, b)
            drawPart(sphereMesh, c.pos.x, h + 32f * sc, c.pos.y, 16f * sc, 16f * sc, 16f * sc, 0f, 0.9f, 0.8f, 0.7f)
            projectToScreen(c.pos.x, h + 48f * sc, c.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "", c.hp, c.maxHp, 0f, 0f,
                    if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false))
            }
        }

        for (n in world.neutrals) {
            if (!n.alive || !world.fogOfWar.isVisible(n.pos.x, n.pos.y)) continue
            val h = terrainHeight(n.pos.x, n.pos.y)
            val sc = 0.85f + n.tier * 0.15f
            drawPart(boxMesh, n.pos.x, h + 18f * sc, n.pos.y, 26f * sc, 34f * sc, 26f * sc, 0f, 0.55f, 0.42f, 0.30f)
            projectToScreen(n.pos.x, h + 45f * sc, n.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "", n.hp, n.maxHp, 0f, 0f, 0xFFF39C12.toInt(), false))
            }
        }

        if (world.roshan.alive && world.fogOfWar.isVisible(world.roshan.pos.x, world.roshan.pos.y)) {
            val rsh = world.roshan
            val h = terrainHeight(rsh.pos.x, rsh.pos.y)
            drawPart(boxMesh, rsh.pos.x, h + 36f, rsh.pos.y, 58f, 72f, 52f, 0f, 0.48f, 0.12f, 0.12f)
            drawPart(sphereMesh, rsh.pos.x, h + 84f, rsh.pos.y, 38f, 34f, 38f, 0f, 0.62f, 0.18f, 0.16f)
            projectToScreen(rsh.pos.x, h + 115f, rsh.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "ROSHAN", rsh.hp, rsh.maxHp, 0f, 0f, 0xFFFF9800.toInt(), false, notchStep = 1000f))
            }
        }

        for (tor in listOf(world.radiantTormentor, world.direTormentor)) {
            if (!tor.alive || !world.fogOfWar.isVisible(tor.pos.x, tor.pos.y)) continue
            val h = terrainHeight(tor.pos.x, tor.pos.y)
            drawPart(coneMesh, tor.pos.x, h + 42f, tor.pos.y, 48f, 76f, 48f, world.gameTime * 40f, 0.06f, 0.78f, 0.85f)
            projectToScreen(tor.pos.x, h + 95f, tor.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "TORMENTOR", tor.hp, tor.maxHp, 0f, 0f, 0xFF38BDF8.toInt(), false))
            }
        }

        for (c in world.couriers) {
            if (!c.alive || (!c.delivering && !c.returning)) continue
            val h = terrainHeight(c.pos.x, c.pos.y)
            drawPart(boxMesh, c.pos.x, h + 28f, c.pos.y, 20f, 16f, 26f, 0f, 1f, 0.82f, 0.25f)
        }

        // 8. 3D Articulated Heroes
        val currentLockTargetId = world.playerHero.attackTargetId
        for (hero in world.heroes) {
            if (!hero.alive) continue
            val isAlly = hero.team == world.playerTeam
            if (!isAlly) {
                if (!world.fogOfWar.isVisible(hero.pos.x, hero.pos.y)) continue
                if (hero.isInvisible() && !world.fogOfWar.hasTrueSight(hero.pos.x, hero.pos.y)) continue
            }
            drawHero3D(hero, isAlly, hero.id == currentLockTargetId)
            val h = terrainHeight(hero.pos.x, hero.pos.y)
            projectToScreen(hero.pos.x, h + 108f, hero.pos.y)?.let { (sx, sy) ->
                val barCol = if (hero.isPlayer) 0xFF2ECC71.toInt() else if (isAlly) 0xFF3498DB.toInt() else 0xFFE74C3C.toInt()
                val statusTag = when {
                    hero.hasStatus(StatusType.CHRONO_FREEZE) -> " [FROZEN]"
                    hero.hasStatus(StatusType.STUN) -> " [STUN]"
                    hero.hasStatus(StatusType.HEX) -> " [HEX]"
                    hero.hasStatus(StatusType.SILENCE) -> " [SILENCE]"
                    hero.hasAegis -> " [AEGIS]"
                    else -> ""
                }
                bars.add(
                    ProjectedBar(
                        sx, sy, "${hero.def.name}$statusTag", hero.hp, hero.maxHp, hero.mana, hero.maxMana,
                        barCol, true, hero.def.id, hero.level, 250f
                    )
                )
            }
        }

        // 9. 3D AoE Zones & Spell VFX
        drawZones3D()

        // 10. 3D Projectiles & Pudge Hook Chain
        for (p in world.projectiles) {
            val h = terrainHeight(p.pos.x, p.pos.y)
            val (r, g, b) = rgbFloats(p.color)
            if (p.kind == "hook") {
                val shooter = world.heroes.firstOrNull { it.id == p.sourceId }
                if (shooter != null) {
                    val steps = 7
                    for (s in 1 until steps) {
                        val t = s.toFloat() / steps
                        val lx = DotaMath.lerp(shooter.pos.x, p.pos.x, t)
                        val lz = DotaMath.lerp(shooter.pos.y, p.pos.y, t)
                        drawPart(sphereMesh, lx, h + 32f, lz, 9f, 9f, 9f, 0f, 0.78f, 0.16f, 0.14f)
                    }
                }
            }
            drawPart(sphereMesh, p.pos.x, h + 34f, p.pos.y, p.radius * 2.2f, p.radius * 2.2f, p.radius * 2.2f, 0f, r, g, b)
        }

        for (pt in world.particles) {
            val h = terrainHeight(pt.pos.x, pt.pos.y)
            val (r, g, b) = rgbFloats(pt.color)
            val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            GLES20.glUniform1f(uAlphaLoc, alpha)
            drawPart(sphereMesh, pt.pos.x, h + 24f, pt.pos.y, pt.size * 2.2f, pt.size * 2.2f, pt.size * 2.2f, 0f, r, g, b)
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        // 11. MLBB Aim Reticle on 3D Ground!
        if (aimingAbilityIdx in 0..3) {
            val ab = world.playerHero.def.abilities.getOrNull(aimingAbilityIdx)
            if (ab != null) {
                val ph = world.playerHero
                val h = terrainHeight(ph.pos.x, ph.pos.y)
                GLES20.glUniform1f(uAlphaLoc, 0.45f)
                if (ab.castRange > 0f) {
                    drawPart(ringMesh, ph.pos.x, h + 4f, ph.pos.y, ab.castRange * 2f, 1f, ab.castRange * 2f, 0f, 0.22f, 0.74f, 0.98f)
                }
                val rad = ab.radius.takeIf { it > 0f } ?: 95f
                val ah = terrainHeight(aimWorldPos.x, aimWorldPos.y)
                drawPart(ringMesh, aimWorldPos.x, ah + 5f, aimWorldPos.y, rad * 2f, 1f, rad * 2f, 0f, 0.98f, 0.75f, 0.14f)
                drawPart(sphereMesh, aimWorldPos.x, ah + 6f, aimWorldPos.y, rad * 1.8f, 4f, rad * 1.8f, 0f, 0.22f, 0.74f, 0.98f)
                GLES20.glUniform1f(uAlphaLoc, 1.0f)
            }
        }

        // Project Floating Combat Texts
        for (ft in world.floatingTexts) {
            val h = terrainHeight(ft.pos.x, ft.pos.y)
            projectToScreen(ft.pos.x, h + 65f + (1.1f - ft.life) * 45f, ft.pos.y)?.let { (sx, sy) ->
                texts.add(ProjectedText(sx, sy, ft.text, ft.color, ft.scale))
            }
        }

        projectedBars = bars
        projectedTexts = texts
    }

    private fun drawHero3D(hero: HeroEntity, isAlly: Boolean, isTargetLocked: Boolean) {
        val h = terrainHeight(hero.pos.x, hero.pos.y)
        val (pr, pg, pb) = rgbFloats(hero.def.primaryColor)
        val (ar, ag, ab) = rgbFloats(hero.def.accentColor)
        val angleDeg = -Math.toDegrees(hero.facingAngle.toDouble()).toFloat() + 90f
        val sc = if (hero.hasStatus(StatusType.MAGIC_IMMUNE)) 1.28f else 1.0f
        val moving = abs(hero.moveIntent.x) > 0.05f || abs(hero.moveIntent.y) > 0.05f
        val walkSwing = if (moving) sin(world.gameTime * 11f) * 14f else 0f

        val alpha = if (hero.isInvisible()) 0.42f else 1.0f
        GLES20.glUniform1f(uAlphaLoc, alpha)

        // Ground selection / Target Lock ring
        if (hero.isPlayer) {
            drawPart(ringMesh, hero.pos.x, h + 3f, hero.pos.y, 76f, 1f, 76f, 0f, 0.18f, 0.85f, 0.45f)
        } else if (isTargetLocked) {
            val pulse = 1.0f + sin(world.gameTime * 8f) * 0.12f
            drawPart(ringMesh, hero.pos.x, h + 4f, hero.pos.y, 82f * pulse, 1f, 82f * pulse, 0f, 0.96f, 0.22f, 0.22f)
        } else {
            drawPart(ringMesh, hero.pos.x, h + 2.5f, hero.pos.y, 64f, 1f, 64f, 0f,
                if (isAlly) 0.2f else 0.9f, if (isAlly) 0.6f else 0.2f, if (isAlly) 0.95f else 0.2f)
        }

        // Legs (animated)
        drawPart(cylMesh, hero.pos.x - 8f * sc, h + 13f * sc, hero.pos.y + walkSwing * 0.4f, 11f * sc, 26f * sc, 11f * sc, angleDeg, 0.18f, 0.20f, 0.24f)
        drawPart(cylMesh, hero.pos.x + 8f * sc, h + 13f * sc, hero.pos.y - walkSwing * 0.4f, 11f * sc, 26f * sc, 11f * sc, angleDeg, 0.18f, 0.20f, 0.24f)

        // Torso & Pauldrons
        val torsoW = if (hero.def.id == "pudge" || hero.def.id == "axe") 34f else 26f
        drawPart(boxMesh, hero.pos.x, h + 40f * sc, hero.pos.y, torsoW * sc, 34f * sc, 20f * sc, angleDeg, pr, pg, pb)
        drawPart(boxMesh, hero.pos.x, h + 52f * sc, hero.pos.y, (torsoW + 12f) * sc, 11f * sc, 22f * sc, angleDeg, ar, ag, ab)

        // Head / Helm
        drawPart(sphereMesh, hero.pos.x, h + 68f * sc, hero.pos.y, 20f * sc, 22f * sc, 20f * sc, angleDeg, 0.88f, 0.78f, 0.68f)

        // Weapon pointing forward in facing direction
        val fx = cos(hero.facingAngle) * 26f * sc
        val fz = sin(hero.facingAngle) * 26f * sc
        val weaponLen = if (hero.def.id == "sniper") 44f else 36f
        drawPart(boxMesh, hero.pos.x + fx, h + 46f * sc, hero.pos.y + fz, 7f * sc, weaponLen * sc, 7f * sc, angleDeg, ar, ag, ab)

        // Signature 3D Hero Accessories
        if (hero.def.id == "invoker") {
            val cols = arrayOf(Triple(0.22f, 0.74f, 0.98f), Triple(0.68f, 0.34f, 0.98f), Triple(1.0f, 0.60f, 0.08f))
            for (i in 0..2) {
                val a = world.gameTime * 3.4f + i * 2.094f
                drawPart(sphereMesh, hero.pos.x + cos(a) * 38f, h + 62f, hero.pos.y + sin(a) * 38f, 13f, 13f, 13f, 0f, cols[i].first, cols[i].second, cols[i].third)
            }
        } else if (hero.def.id == "chronos") {
            drawPart(ringMesh, hero.pos.x, h + 62f, hero.pos.y, 68f, 1f, 68f, world.gameTime * 90f, 1.0f, 0.84f, 0.12f)
        } else if (hero.def.id == "cm" || hero.def.id == "void_spirit") {
            drawPart(coneMesh, hero.pos.x, h + 82f * sc, hero.pos.y, 26f * sc, 18f * sc, 26f * sc, angleDeg, pr, pg, pb)
        }

        // BKB / Blade Fury 3D Shell
        if (hero.hasStatus(StatusType.MAGIC_IMMUNE) || hero.hasStatus(StatusType.BLADE_FURY)) {
            GLES20.glUniform1f(uAlphaLoc, 0.35f)
            drawPart(sphereMesh, hero.pos.x, h + 46f, hero.pos.y, 88f, 96f, 88f, world.gameTime * 120f, 1.0f, 0.84f, 0.1f)
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
    }

    private fun drawZones3D() {
        for (z in world.zones) {
            val h = terrainHeight(z.pos.x, z.pos.y)
            val (r, g, b) = rgbFloats(z.color)
            if (z.kind == "fissure" && z.endPos != null) {
                val steps = 10
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val rx = DotaMath.lerp(z.pos.x, z.endPos.x, t)
                    val rz = DotaMath.lerp(z.pos.y, z.endPos.y, t)
                    val rh = terrainHeight(rx, rz)
                    drawPart(boxMesh, rx, rh + 24f, rz, 44f, 48f, 44f, i * 25f, 0.42f, 0.28f, 0.20f)
                }
                continue
            }
            GLES20.glUniform1f(uAlphaLoc, 0.36f)
            if (z.kind == "chrono") {
                drawPart(sphereMesh, z.pos.x, h + 20f, z.pos.y, z.radius * 2f, z.radius * 1.4f, z.radius * 2f, world.gameTime * 40f, 1.0f, 0.84f, 0.1f)
            } else if (z.kind == "sunstrike") {
                drawPart(cylMesh, z.pos.x, h + 150f, z.pos.y, z.radius * 0.7f, 300f, z.radius * 0.7f, 0f, 1.0f, 0.55f, 0.1f)
            } else {
                drawPart(ringMesh, z.pos.x, h + 5f, z.pos.y, z.radius * 2f, 1f, z.radius * 2f, 0f, r, g, b)
            }
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
    }

    private fun drawGate3D(pos: Vec2) {
        val h = terrainHeight(pos.x, pos.y)
        drawPart(ringMesh, pos.x, h + 4f, pos.y, 92f, 1f, 92f, world.gameTime * 60f, 0.68f, 0.34f, 0.98f)
        drawPart(cylMesh, pos.x - 32f, h + 38f, pos.y, 14f, 76f, 14f, 0f, 0.45f, 0.24f, 0.70f)
        drawPart(cylMesh, pos.x + 32f, h + 38f, pos.y, 14f, 76f, 14f, 0f, 0.45f, 0.24f, 0.70f)
    }

    private fun drawPart(
        mesh: Mesh3D,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotYDeg: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotYDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        drawMesh(mesh, r, g, b)
    }

    private fun drawMesh(mesh: Mesh3D, r: Float, g: Float, b: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uTintLoc, r, g, b)
        mesh.draw(aPosLoc, aNormLoc, aColLoc)
    }

    private fun uploadFogTexture() {
        val n = world.fogOfWar.size()
        fogPixels.position(0)
        for (gy in 0 until n) {
            for (gx in 0 until n) {
                val b = when (world.fogOfWar.getCellState(gx, gy)) {
                    2 -> 255.toByte()
                    1 -> 125.toByte()
                    else -> 42.toByte()
                }
                fogPixels.put(b)
            }
        }
        fogPixels.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 96, 96, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, fogPixels)
    }

    private fun projectToScreen(wx: Float, wy: Float, wz: Float): Pair<Float, Float>? {
        val worldPt = floatArrayOf(wx, wy, wz, 1f)
        val clipPt = FloatArray(4)
        Matrix.multiplyMV(clipPt, 0, vpMatrix, 0, worldPt, 0)
        if (clipPt[3] <= 0.1f) return null
        val ndcX = clipPt[0] / clipPt[3]
        val ndcY = clipPt[1] / clipPt[3]
        if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) return null
        val sx = (ndcX * 0.5f + 0.5f) * viewportWidth
        val sy = (1f - (ndcY * 0.5f + 0.5f)) * viewportHeight
        return sx to sy
    }

    fun screenToWorld(sx: Float, sy: Float): Vec2 {
        val w = viewportWidth.toFloat().coerceAtLeast(1f)
        val h = viewportHeight.toFloat().coerceAtLeast(1f)
        val ndcX = (sx / w) * 2f - 1f
        val ndcY = 1f - (sy / h) * 2f

        val nearNdc = floatArrayOf(ndcX, ndcY, -1f, 1f)
        val farNdc = floatArrayOf(ndcX, ndcY, 1f, 1f)
        val nearW = FloatArray(4)
        val farW = FloatArray(4)
        Matrix.multiplyMV(nearW, 0, invVpMatrix, 0, nearNdc, 0)
        Matrix.multiplyMV(farW, 0, invVpMatrix, 0, farNdc, 0)
        if (abs(nearW[3]) < 1e-5f || abs(farW[3]) < 1e-5f) return cameraPos.copyVec()

        val nx = nearW[0] / nearW[3]; val ny = nearW[1] / nearW[3]; val nz = nearW[2] / nearW[3]
        val fx = farW[0] / farW[3]; val fy = farW[1] / farW[3]; val fz = farW[2] / farW[3]
        val dy = fy - ny
        if (abs(dy) < 1e-4f) return cameraPos.copyVec()
        val t = (-ny / dy).coerceIn(0f, 1f)
        val gx = (nx + (fx - nx) * t).coerceIn(0f, MapLayout.SIZE)
        val gz = (nz + (fz - nz) * t).coerceIn(0f, MapLayout.SIZE)
        return Vec2(gx, gz)
    }

    private fun terrainHeight(wx: Float, wz: Float): Float {
        if (MapLayout.isInRiver(wx, wz)) return -26f
        if (MapLayout.isHighGround(wx, wz)) return 38f
        return 0f
    }

    private fun terrainColor(wx: Float, wz: Float, h: Float): Triple<Float, Float, Float> {
        if (h < -10f) return Triple(0.14f, 0.24f, 0.30f) // River bed
        // Check if on lane stone road
        for (lane in MapLayout.laneWaypoints) {
            for (i in 0 until lane.size - 1) {
                if (DotaMath.distPointToSegment(wx, wz, lane[i].x, lane[i].y, lane[i + 1].x, lane[i + 1].y) < 110f) {
                    return if (wx < wz) Triple(0.28f, 0.30f, 0.26f) else Triple(0.26f, 0.21f, 0.22f)
                }
            }
        }
        val isRadiantSide = wx < wz
        return if (isRadiantSide) {
            if (h > 20f) Triple(0.18f, 0.36f, 0.22f) else Triple(0.13f, 0.28f, 0.17f)
        } else {
            if (h > 20f) Triple(0.32f, 0.16f, 0.17f) else Triple(0.22f, 0.12f, 0.14f)
        }
    }

    private fun rgbFloats(argb: Int): Triple<Float, Float, Float> {
        val r = Color.red(argb) / 255f
        val g = Color.green(argb) / 255f
        val b = Color.blue(argb) / 255f
        return Triple(r, g, b)
    }

    private fun compileProgram(vsSrc: String, fsSrc: String): Int {
        fun compileShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec3 aCol;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vNorm = normalize(mat3(uModel) * aNorm);
                vCol = aCol;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec3 uTint;
            uniform vec3 uSunDir;
            uniform vec3 uAmbient;
            uniform int uUseFog;
            uniform sampler2D uFogTex;
            uniform float uMapSize;
            uniform float uAlpha;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            void main() {
                float diff = max(dot(normalize(vNorm), normalize(uSunDir)), 0.0);
                vec3 lit = (uAmbient + diff * 0.65) * vCol * uTint;
                if (uUseFog == 1) {
                    vec2 uv = clamp(vWorldPos.xz / uMapSize, 0.0, 1.0);
                    float fogVal = texture2D(uFogTex, uv).r;
                    lit *= fogVal;
                }
                gl_FragColor = vec4(lit, uAlpha);
            }
        """
    }
}
