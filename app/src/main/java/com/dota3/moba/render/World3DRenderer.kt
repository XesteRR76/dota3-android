package com.dota3.moba.render

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.engine.*
import com.dota3.moba.model.StatusType
import com.dota3.moba.model.TargetType
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
    var cameraZoomDist: Float = 690f

    @Volatile var aimingAbilityIdx: Int = -1
    @Volatile var aimingItemSlotIdx: Int = -1
    @Volatile var aimWorldPos: Vec2 = Vec2()
    @Volatile var showAttackRangeRing: Boolean = false
    @Volatile var viewportWidth: Int = 1280
    @Volatile var viewportHeight: Int = 720

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
    private var uInvScaleLoc = 0
    private var uTintLoc = 0
    private var uSunDirLoc = 0
    private var uAmbientLoc = 0
    private var uCamPosLoc = 0
    private var uUseFogLoc = 0
    private var uFogTexLoc = 0
    private var uUseGroundTexLoc = 0
    private var uGroundTexLoc = 0
    private var uUseMapTexLoc = 0
    private var uMapTexLoc = 0
    private var uWaterModeLoc = 0
    private var uMapSizeLoc = 0
    private var uAlphaLoc = 0
    private var uTimeLoc = 0

    private var aPosLoc = 0
    private var aNormLoc = 0
    private var aColLoc = 0

    private var fogTexId = 0
    private var groundTexId = 0
    private var mapTexId = 0
    private val fogGridSize = world.fogOfWar.size()
    private val fogPixels = ByteBuffer.allocateDirect(fogGridSize * fogGridSize)

    private lateinit var terrainMesh: Mesh3D
    private lateinit var waterMesh: Mesh3D
    private lateinit var boxMesh: Mesh3D
    private lateinit var cylMesh: Mesh3D
    private lateinit var limbMesh: Mesh3D
    private lateinit var coneMesh: Mesh3D
    private lateinit var sphereMesh: Mesh3D
    private lateinit var ringMesh: Mesh3D
    private lateinit var torusMesh: Mesh3D
    private lateinit var crystalMesh: Mesh3D
    private lateinit var bladeMesh: Mesh3D
    private lateinit var crescentMesh: Mesh3D
    private lateinit var domeMesh: Mesh3D

    private var lastFrameNs = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.06f, 0.11f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        programId = compileProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(programId, "uMVP")
        uModelLoc = GLES20.glGetUniformLocation(programId, "uModel")
        uInvScaleLoc = GLES20.glGetUniformLocation(programId, "uInvScale")
        uTintLoc = GLES20.glGetUniformLocation(programId, "uTint")
        uSunDirLoc = GLES20.glGetUniformLocation(programId, "uSunDir")
        uAmbientLoc = GLES20.glGetUniformLocation(programId, "uAmbient")
        uCamPosLoc = GLES20.glGetUniformLocation(programId, "uCamPos")
        uUseFogLoc = GLES20.glGetUniformLocation(programId, "uUseFog")
        uFogTexLoc = GLES20.glGetUniformLocation(programId, "uFogTex")
        uUseGroundTexLoc = GLES20.glGetUniformLocation(programId, "uUseGroundTex")
        uGroundTexLoc = GLES20.glGetUniformLocation(programId, "uGroundTex")
        uUseMapTexLoc = GLES20.glGetUniformLocation(programId, "uUseMapTex")
        uMapTexLoc = GLES20.glGetUniformLocation(programId, "uMapTex")
        uWaterModeLoc = GLES20.glGetUniformLocation(programId, "uWaterMode")
        uMapSizeLoc = GLES20.glGetUniformLocation(programId, "uMapSize")
        uAlphaLoc = GLES20.glGetUniformLocation(programId, "uAlpha")
        uTimeLoc = GLES20.glGetUniformLocation(programId, "uTime")

        aPosLoc = GLES20.glGetAttribLocation(programId, "aPos")
        aNormLoc = GLES20.glGetAttribLocation(programId, "aNorm")
        aColLoc = GLES20.glGetAttribLocation(programId, "aCol")

        val texIds = IntArray(3)
        GLES20.glGenTextures(3, texIds, 0)
        fogTexId = texIds[0]
        groundTexId = texIds[1]
        mapTexId = texIds[2]

        // Fog of War bilinear POT texture (128x128)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, fogGridSize, fogGridSize, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, fogPixels)

        createGroundDetailTexture(groundTexId)
        createMapAlbedoTexture(mapTexId)

        terrainMesh = Mesh3D.createTerrainMesh(
            gridSize = 128,
            mapSize = MapLayout.SIZE,
            heightFn = { wx, wz -> terrainHeight(wx, wz) },
            colorFn = { wx, wz, h -> terrainSlopeShade(wx, wz, h) }
        )
        waterMesh = Mesh3D.createBox(MapLayout.SIZE, 1.5f, MapLayout.SIZE)
        boxMesh = Mesh3D.createBox(1f, 1f, 1f)
        cylMesh = Mesh3D.createCylinder(12, 0.5f, 0.5f, 1f)
        limbMesh = Mesh3D.createCylinder(10, 0.42f, 0.54f, 1f)
        coneMesh = Mesh3D.createCylinder(12, 0.04f, 0.5f, 1f)
        sphereMesh = Mesh3D.createSphere(9, 14, 0.5f)
        ringMesh = Mesh3D.createRing(32, 0.86f, 1.0f)
        torusMesh = Mesh3D.createTorus(24, 8, 0.92f, 0.08f)
        crystalMesh = Mesh3D.createCrystal(6, 0.5f, 1.0f)
        bladeMesh = Mesh3D.createCurvedBlade()
        crescentMesh = Mesh3D.createCrescentArc(14, 0.58f, 1.0f, 148f)
        domeMesh = Mesh3D.createDome(7, 16, 0.5f)

        cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
        lastFrameNs = System.nanoTime()
    }

    private fun createGroundDetailTexture(texId: Int) {
        val size = 256
        val buf = ByteBuffer.allocateDirect(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n1 = sin(x * 0.16f + y * 0.11f) * cos(y * 0.14f - x * 0.09f)
                val n2 = sin(x * 0.43f + 0.7f) * sin(y * 0.39f + 0.3f)
                val n3 = cos((x + y) * 0.75f)
                val hash = (((x * 73856093) xor (y * 19349663)) and 255) / 255f
                val v = (0.80f + n1 * 0.10f + n2 * 0.06f + n3 * 0.03f + (hash - 0.5f) * 0.11f).coerceIn(0.45f, 1.0f)
                buf.put((v * 255f).toInt().toByte())
            }
        }
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, size, size, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buf)
    }

    private fun createMapAlbedoTexture(texId: Int) {
        val size = 512
        val buf = ByteBuffer.allocateDirect(size * size * 4)
        val segs = ArrayList<Pair<Vec2, Vec2>>(18)
        for (lane in MapLayout.laneWaypoints) {
            for (i in 0 until lane.size - 1) segs.add(lane[i] to lane[i + 1])
        }
        val runeSpots = listOf(
            MapLayout.powerRuneTopPos, MapLayout.powerRuneBotPos,
            MapLayout.bountyRuneRadiantPos, MapLayout.bountyRuneDirePos
        )

        for (pz in 0 until size) {
            val wz = ((pz + 0.5f) / size) * MapLayout.SIZE
            for (px in 0 until size) {
                val wx = ((px + 0.5f) / size) * MapLayout.SIZE
                val isRad = wx < wz
                val nA = sin(wx * 0.008f) * cos(wz * 0.009f)
                val nB = sin((wx - wz) * 0.017f) * 0.5f

                var r: Float
                var g: Float
                var b: Float
                if (isRad) {
                    r = 0.13f + nA * 0.025f + nB * 0.015f
                    g = 0.37f + nA * 0.055f + nB * 0.020f
                    b = 0.16f + nA * 0.020f
                } else {
                    r = 0.25f + nA * 0.045f + nB * 0.018f
                    g = 0.13f + nA * 0.022f
                    b = 0.14f + nA * 0.020f
                }

                // High ground sunlit/volcanic plateau + terraced cliff edge shading
                val hg = MapLayout.highGroundFactor(wx, wz)
                if (hg > 0.01f) {
                    val cliffBand = if (hg in 0.06f..0.88f) 1f else 0f
                    if (isRad) {
                        r = DotaMath.lerp(r, 0.21f - cliffBand * 0.05f, hg)
                        g = DotaMath.lerp(g, 0.43f - cliffBand * 0.08f, hg)
                        b = DotaMath.lerp(b, 0.24f - cliffBand * 0.04f, hg)
                    } else {
                        r = DotaMath.lerp(r, 0.33f - cliffBand * 0.06f, hg)
                        g = DotaMath.lerp(g, 0.16f - cliffBand * 0.03f, hg)
                        b = DotaMath.lerp(b, 0.17f - cliffBand * 0.03f, hg)
                    }
                }

                // Crisp stone-paved Dota lanes with tile seams & worn curb border
                var minLaneDist = 9999f
                for ((a, bPt) in segs) {
                    val d = DotaMath.distPointToSegment(wx, wz, a.x, a.y, bPt.x, bPt.y)
                    if (d < minLaneDist) minLaneDist = d
                }
                val roadMask = 1f - DotaMath.smoothstep(76f, 130f, minLaneDist)
                if (roadMask > 0.01f) {
                    val cobble = (sin(wx * 0.095f) * cos(wz * 0.095f)) * 0.035f
                    val borderMask = DotaMath.smoothstep(68f, 112f, minLaneDist)
                    val rr = (if (isRad) 0.43f else 0.32f) - borderMask * 0.09f + cobble
                    val rg = (if (isRad) 0.40f else 0.27f) - borderMask * 0.07f + cobble
                    val rb = (if (isRad) 0.32f else 0.28f) - borderMask * 0.06f + cobble
                    r = DotaMath.lerp(r, rr, roadMask)
                    g = DotaMath.lerp(g, rg, roadMask)
                    b = DotaMath.lerp(b, rb, roadMask)
                }

                // Radiant & Dire Base Courtyards & Fountain Sanctuaries
                val radBaseDist = hypot(wx - MapLayout.radiantAncientPos.x, wz - MapLayout.radiantAncientPos.y)
                if (radBaseDist < 480f) {
                    val m = 1f - DotaMath.smoothstep(290f, 480f, radBaseDist)
                    val ringGlow = if (radBaseDist in 208f..246f) 0.16f else 0f
                    r = DotaMath.lerp(r, 0.36f, m)
                    g = DotaMath.lerp(g, 0.45f + ringGlow, m)
                    b = DotaMath.lerp(b, 0.39f + ringGlow, m)
                }
                val direBaseDist = hypot(wx - MapLayout.direAncientPos.x, wz - MapLayout.direAncientPos.y)
                if (direBaseDist < 480f) {
                    val m = 1f - DotaMath.smoothstep(290f, 480f, direBaseDist)
                    val ringGlow = if (direBaseDist in 208f..246f) 0.22f else 0f
                    r = DotaMath.lerp(r, 0.29f + ringGlow, m)
                    g = DotaMath.lerp(g, 0.16f, m)
                    b = DotaMath.lerp(b, 0.18f, m)
                }

                // Roshan Pit Arena, Rune Circles & Neutral Camp Clearings
                val roshDist = hypot(wx - MapLayout.roshanPitPos.x, wz - MapLayout.roshanPitPos.y)
                if (roshDist < 245f) {
                    val m = 1f - DotaMath.smoothstep(140f, 245f, roshDist)
                    val pitRing = if (roshDist in 150f..178f) 0.16f else 0f
                    r = DotaMath.lerp(r, 0.26f + pitRing, m)
                    g = DotaMath.lerp(g, 0.19f + pitRing * 0.35f, m)
                    b = DotaMath.lerp(b, 0.17f, m)
                }
                for (rs in runeSpots) {
                    val rd = hypot(wx - rs.x, wz - rs.y)
                    if (rd < 90f && rd > 52f) {
                        val ringM = 1f - abs(rd - 70f) / 20f
                        r = DotaMath.lerp(r, 0.24f, ringM * 0.6f)
                        g = DotaMath.lerp(g, 0.44f, ringM * 0.6f)
                        b = DotaMath.lerp(b, 0.56f, ringM * 0.6f)
                    }
                }
                for (camp in MapLayout.neutralCamps) {
                    val cd = hypot(wx - camp.pos.x, wz - camp.pos.y)
                    if (cd < 135f) {
                        val m = (1f - DotaMath.smoothstep(65f, 135f, cd)) * 0.65f
                        r = DotaMath.lerp(r, 0.28f, m)
                        g = DotaMath.lerp(g, 0.25f, m)
                        b = DotaMath.lerp(b, 0.19f, m)
                    }
                }

                // Riverbed sand, pebbles & stone ford mid-crossing
                val rDist = MapLayout.riverDistance(wx, wz)
                if (rDist < 210f) {
                    val bankMask = 1f - DotaMath.smoothstep(130f, 205f, rDist)
                    val deepMask = 1f - DotaMath.smoothstep(60f, 145f, rDist)
                    r = DotaMath.lerp(r, 0.36f, bankMask * 0.72f)
                    g = DotaMath.lerp(g, 0.33f, bankMask * 0.72f)
                    b = DotaMath.lerp(b, 0.25f, bankMask * 0.72f)
                    r = DotaMath.lerp(r, 0.10f, deepMask)
                    g = DotaMath.lerp(g, 0.24f, deepMask)
                    b = DotaMath.lerp(b, 0.31f, deepMask)
                }

                buf.put((r.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put((g.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put((b.coerceIn(0f, 1f) * 255f).toInt().toByte())
                buf.put(255.toByte())
            }
        }
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 42f, aspect, 80f, 5200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        val curGameTime: Float
        val curIsNight: Boolean
        synchronized(world) {
            onFrameStep(dt)
            if (cameraLocked) {
                cameraPos.x = DotaMath.lerp(cameraPos.x, world.playerHero.pos.x, 0.22f)
                cameraPos.y = DotaMath.lerp(cameraPos.y, world.playerHero.pos.y, 0.22f)
            }
            curGameTime = world.gameTime
            curIsNight = world.isNight
            copyFogPixels()
        }

        val targetY = terrainHeight(cameraPos.x, cameraPos.y)
        val camY = targetY + cameraZoomDist
        val camZOffset = cameraZoomDist * 0.72f
        val eyeX = cameraPos.x
        val eyeY = camY
        val eyeZ = cameraPos.y + camZOffset

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            cameraPos.x, targetY + 26f, cameraPos.y,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVpMatrix, 0, vpMatrix, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programId)

        uploadFogTexture()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glUniform1i(uFogTexLoc, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, groundTexId)
        GLES20.glUniform1i(uGroundTexLoc, 1)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mapTexId)
        GLES20.glUniform1i(uMapTexLoc, 2)

        GLES20.glUniform1f(uMapSizeLoc, MapLayout.SIZE)
        GLES20.glUniform1f(uTimeLoc, curGameTime)
        GLES20.glUniform3f(uCamPosLoc, eyeX, eyeY, eyeZ)

        if (curIsNight) {
            GLES20.glUniform3f(uSunDirLoc, -0.35f, 0.85f, -0.38f)
            GLES20.glUniform3f(uAmbientLoc, 0.34f, 0.40f, 0.58f)
        } else {
            GLES20.glUniform3f(uSunDirLoc, 0.48f, 0.82f, -0.32f)
            GLES20.glUniform3f(uAmbientLoc, 0.52f, 0.52f, 0.50f)
        }

        // 1. High-Definition 3D Terrain with Map Albedo + Detail Stone/Grass + Fog of War
        GLES20.glUniform1i(uUseFogLoc, 1)
        GLES20.glUniform1i(uUseGroundTexLoc, 1)
        GLES20.glUniform1i(uUseMapTexLoc, 1)
        GLES20.glUniform1i(uWaterModeLoc, 0)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(terrainMesh, 1f, 1f, 1f)

        // 2. Animated Shimmering 3D River Water
        GLES20.glUniform1i(uUseGroundTexLoc, 0)
        GLES20.glUniform1i(uUseMapTexLoc, 0)
        GLES20.glUniform1i(uWaterModeLoc, 1)
        val waveOffset = sin(curGameTime * 2.0f) * 1.4f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, MapLayout.SIZE * 0.5f, -10.5f + waveOffset, MapLayout.SIZE * 0.5f)
        GLES20.glUniform1f(uAlphaLoc, 0.78f)
        drawMesh(waterMesh, 0.12f, 0.52f, 0.74f)

        GLES20.glUniform1i(uWaterModeLoc, 0)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        val bars = ArrayList<ProjectedBar>(48)
        val texts = ArrayList<ProjectedText>(32)

        synchronized(world) {
            drawDynamicWorld(bars, texts)
        }

        projectedBars = bars
        projectedTexts = texts
    }

    private fun drawDynamicWorld(bars: MutableList<ProjectedBar>, texts: MutableList<ProjectedText>) {

        // 3. Fountains (Glowing Healing Sanctuary Rings)
        drawFountain3D(MapLayout.radiantFountain, isRadiant = true)
        drawFountain3D(MapLayout.direFountain, isRadiant = false)

        // 4. Organic Multi-Crown 3D Trees with Ground Shadows
        for (t in world.trees) {
            if (!t.alive) continue
            if (abs(t.pos.x - cameraPos.x) > 1350f || abs(t.pos.y - cameraPos.y) > 1100f) continue
            val h = terrainHeight(t.pos.x, t.pos.y)
            val isDire = t.pos.x > t.pos.y
            val vScale = 0.86f + (t.id % 6) * 0.07f
            val rot = (t.id * 53f) % 360f
            val lean = ((t.id % 5) - 2) * 3.5f

            // Ground shadow
            GLES20.glUniform1f(uAlphaLoc, 0.34f)
            drawPart(cylMesh, t.pos.x + 7f, h + 1.2f, t.pos.y + 8f, 48f * vScale, 1.8f, 48f * vScale, 0f, 0f, 0f, 0f)
            GLES20.glUniform1f(uAlphaLoc, 1.0f)

            // Tapered trunk + root base
            drawPartTilt(limbMesh, t.pos.x, h + 24f * vScale, t.pos.y, 13f * vScale, 48f * vScale, 13f * vScale,
                lean, rot, 0f, if (isDire) 0.24f else 0.38f, if (isDire) 0.16f else 0.24f, if (isDire) 0.16f else 0.13f)

            if (isDire) {
                // Dire gnarled spiky pine/blight trees
                drawPart(coneMesh, t.pos.x, h + 52f * vScale, t.pos.y, 50f * vScale, 42f * vScale, 50f * vScale, rot, 0.28f, 0.11f, 0.16f)
                drawPart(coneMesh, t.pos.x + 5f, h + 72f * vScale, t.pos.y - 4f, 38f * vScale, 36f * vScale, 38f * vScale, rot + 30f, 0.42f, 0.14f, 0.18f)
                drawPart(coneMesh, t.pos.x - 3f, h + 90f * vScale, t.pos.y + 3f, 24f * vScale, 28f * vScale, 24f * vScale, rot + 60f, 0.56f, 0.19f, 0.20f)
            } else {
                // Radiant lush multi-cluster broad canopies
                drawPart(sphereMesh, t.pos.x, h + 56f * vScale, t.pos.y, 52f * vScale, 36f * vScale, 52f * vScale, rot, 0.10f, 0.42f, 0.16f)
                drawPart(sphereMesh, t.pos.x - 12f * vScale, h + 64f * vScale, t.pos.y + 10f * vScale, 36f * vScale, 30f * vScale, 36f * vScale, rot, 0.14f, 0.52f, 0.20f)
                drawPart(sphereMesh, t.pos.x + 11f * vScale, h + 68f * vScale, t.pos.y - 8f * vScale, 34f * vScale, 28f * vScale, 34f * vScale, rot, 0.18f, 0.58f, 0.22f)
                drawPart(sphereMesh, t.pos.x, h + 79f * vScale, t.pos.y, 32f * vScale, 26f * vScale, 32f * vScale, rot, 0.22f, 0.65f, 0.24f)
            }
        }

        drawGate3D(MapLayout.twinGateTopLeft)
        drawGate3D(MapLayout.twinGateBotRight)

        // 5. Detailed Dota Watchtowers, Barracks & Ancients
        for (t in world.towers) {
            if (!t.alive) continue
            if (abs(t.pos.x - cameraPos.x) > 1400f || abs(t.pos.y - cameraPos.y) > 1200f) continue
            drawTower3D(t, bars)
        }

        for (b in world.barracks) {
            if (!b.alive) continue
            if (abs(b.pos.x - cameraPos.x) > 1400f || abs(b.pos.y - cameraPos.y) > 1200f) continue
            drawBarracks3D(b, bars)
        }

        for (anc in listOf(world.radiantAncient, world.direAncient)) {
            if (!anc.alive) continue
            drawAncient3D(anc, bars)
        }

        // 6. Wards, Runes, Dropped Items (clear through Fog of War when revealed)
        GLES20.glUniform1i(uUseFogLoc, 0)
        for (w in world.wards) {
            val vis = w.team == world.playerTeam || world.fogOfWar.hasTrueSight(w.pos.x, w.pos.y)
            if (!vis) continue
            val h = terrainHeight(w.pos.x, w.pos.y)
            val (r, g, b) = if (w.isSentry) Triple(0.22f, 0.74f, 0.98f) else Triple(0.95f, 0.77f, 0.06f)
            drawPart(limbMesh, w.pos.x, h + 15f, w.pos.y, 12f, 30f, 12f, 0f, 0.32f, 0.34f, 0.38f)
            drawPart(crystalMesh, w.pos.x, h + 36f, w.pos.y, 18f, 24f, 18f, world.gameTime * 80f, r, g, b)
            drawPart(torusMesh, w.pos.x, h + 34f, w.pos.y, 26f, 26f, 26f, world.gameTime * 90f, r, g, b)
        }

        for (r in world.runes) {
            if (!r.alive) continue
            val h = terrainHeight(r.pos.x, r.pos.y)
            val (cr, cg, cb) = rgbFloats(r.type.color)
            val bob = sin(world.gameTime * 3.8f) * 6f
            drawPart(crystalMesh, r.pos.x, h + 28f + bob, r.pos.y, 24f, 38f, 24f, world.gameTime * 85f, cr, cg, cb)
            drawPart(torusMesh, r.pos.x, h + 5f, r.pos.y, 48f, 36f, 48f, -world.gameTime * 60f, cr, cg, cb)
        }

        for (d in world.droppedItems) {
            val h = terrainHeight(d.pos.x, d.pos.y)
            val bob = sin(world.gameTime * 4f) * 4f
            drawPart(crystalMesh, d.pos.x, h + 24f + bob, d.pos.y, 24f, 34f, 24f, world.gameTime * 110f, 1f, 0.84f, 0f)
            drawPart(torusMesh, d.pos.x, h + 4f, d.pos.y, 46f, 40f, 46f, 0f, 1f, 0.84f, 0f)
        }

        // 7. Creeps, Neutrals, Roshan, Tormentors, Couriers
        val currentLockTargetId = world.playerHero.attackTargetId
        for (c in world.creeps) {
            if (!c.alive) continue
            if (abs(c.pos.x - cameraPos.x) > 1150f || abs(c.pos.y - cameraPos.y) > 950f) continue
            if (c.team != world.playerTeam && !world.fogOfWar.isVisible(c.pos.x, c.pos.y)) continue
            drawCreep3D(c, c.id == currentLockTargetId, bars)
        }

        for (n in world.neutrals) {
            if (!n.alive || !world.fogOfWar.isVisible(n.pos.x, n.pos.y)) continue
            drawNeutral3D(n, n.id == currentLockTargetId, bars)
        }

        if (world.roshan.alive && world.fogOfWar.isVisible(world.roshan.pos.x, world.roshan.pos.y)) {
            drawRoshan3D(world.roshan, world.roshan.id == currentLockTargetId, bars)
        }

        for (tor in listOf(world.radiantTormentor, world.direTormentor)) {
            if (!tor.alive || !world.fogOfWar.isVisible(tor.pos.x, tor.pos.y)) continue
            val h = terrainHeight(tor.pos.x, tor.pos.y)
            val bob = sin(world.gameTime * 2.8f) * 6f
            drawPart(crystalMesh, tor.pos.x, h + 48f + bob, tor.pos.y, 44f, 74f, 44f, world.gameTime * 45f, 0.08f, 0.82f, 0.92f)
            drawPartTilt(torusMesh, tor.pos.x, h + 48f + bob, tor.pos.y, 74f, 62f, 74f, 34f, world.gameTime * 80f, 0f, 0.25f, 0.88f, 1.0f)
            drawPartTilt(torusMesh, tor.pos.x, h + 48f + bob, tor.pos.y, 64f, 52f, 64f, -34f, -world.gameTime * 65f, 20f, 1.0f, 0.82f, 0.22f)
            projectToScreen(tor.pos.x, h + 102f, tor.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "TORMENTOR", tor.hp, tor.maxHp, 0f, 0f, 0xFF38BDF8.toInt(), false))
            }
        }

        for (c in world.couriers) {
            if (!c.alive || (!c.delivering && !c.returning)) continue
            val h = terrainHeight(c.pos.x, c.pos.y)
            val bob = sin(world.gameTime * 9f) * 5f
            val isRad = c.team == Team.RADIANT
            drawPart(boxMesh, c.pos.x, h + 28f + bob, c.pos.y, 18f, 14f, 24f, 0f, 0.72f, 0.50f, 0.28f)
            drawPart(boxMesh, c.pos.x, h + 37f + bob, c.pos.y, 14f, 10f, 14f, 0f, 1.0f, 0.84f, 0.24f)
            drawPartTilt(boxMesh, c.pos.x - 16f, h + 34f + bob, c.pos.y, 18f, 3f, 10f, 0f, 0f, sin(world.gameTime * 18f) * 28f,
                if (isRad) 0.3f else 0.9f, if (isRad) 0.9f else 0.3f, 0.4f)
            drawPartTilt(boxMesh, c.pos.x + 16f, h + 34f + bob, c.pos.y, 18f, 3f, 10f, 0f, 0f, -sin(world.gameTime * 18f) * 28f,
                if (isRad) 0.3f else 0.9f, if (isRad) 0.9f else 0.3f, 0.4f)
        }

        // 8. Articulated 3D Heroes + Weapons + Animations + Slash Crescents
        for (hero in world.heroes) {
            if (!hero.alive) continue
            val isAlly = hero.team == world.playerTeam
            if (!isAlly) {
                if (!world.fogOfWar.isVisible(hero.pos.x, hero.pos.y)) continue
                if (hero.isInvisible() && !world.fogOfWar.hasTrueSight(hero.pos.x, hero.pos.y)) continue
            }
            drawHero3D(hero, isAlly, hero.id == currentLockTargetId)
            val h = terrainHeight(hero.pos.x, hero.pos.y)
            projectToScreen(hero.pos.x, h + 124f, hero.pos.y)?.let { (sx, sy) ->
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
                    val steps = 10
                    for (s in 1 until steps) {
                        val t = s.toFloat() / steps
                        val lx = DotaMath.lerp(shooter.pos.x, p.pos.x, t)
                        val lz = DotaMath.lerp(shooter.pos.y, p.pos.y, t)
                        val lh = terrainHeight(lx, lz)
                        drawPart(torusMesh, lx, lh + 32f, lz, 11f, 11f, 11f, s * 35f, 0.72f, 0.72f, 0.76f)
                    }
                    val hookAngle = -Math.toDegrees(atan2(p.velocity.y, p.velocity.x).toDouble()).toFloat() + 90f
                    drawPart(bladeMesh, p.pos.x, h + 32f, p.pos.y, 44f, 44f, 34f, hookAngle, 0.85f, 0.18f, 0.16f)
                }
                continue
            }
            drawPart(sphereMesh, p.pos.x, h + 34f, p.pos.y, p.radius * 2.4f, p.radius * 2.4f, p.radius * 2.4f, 0f, r, g, b)
        }

        for (pt in world.particles) {
            val h = terrainHeight(pt.pos.x, pt.pos.y)
            val (r, g, b) = rgbFloats(pt.color)
            val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            GLES20.glUniform1f(uAlphaLoc, alpha)
            drawPart(sphereMesh, pt.pos.x, h + 26f, pt.pos.y, pt.size * 2.4f, pt.size * 2.4f, pt.size * 2.4f, 0f, r, g, b)
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        // 11. MLBB Aim Reticle & Directional Skillshot Line on 3D Ground
        drawAimIndicators3D()

        for (ft in world.floatingTexts) {
            val h = terrainHeight(ft.pos.x, ft.pos.y)
            projectToScreen(ft.pos.x, h + 84f + (1.2f - ft.life) * 52f, ft.pos.y)?.let { (sx, sy) ->
                texts.add(ProjectedText(sx, sy, ft.text, ft.color, ft.scale))
            }
        }
    }

    private fun drawFountain3D(pos: Vec2, isRadiant: Boolean) {
        if (abs(pos.x - cameraPos.x) > 1400f || abs(pos.y - cameraPos.y) > 1200f) return
        val h = terrainHeight(pos.x, pos.y)
        val (r, g, b) = if (isRadiant) Triple(0.18f, 0.90f, 0.65f) else Triple(0.95f, 0.24f, 0.18f)
        drawPart(torusMesh, pos.x, h + 4f, pos.y, 170f, 80f, 170f, world.gameTime * 20f, r, g, b)
        drawPart(cylMesh, pos.x, h + 10f, pos.y, 90f, 16f, 90f, 0f, 0.22f, 0.24f, 0.30f)
        drawPart(crystalMesh, pos.x, h + 44f + sin(world.gameTime * 2.5f) * 5f, pos.y, 34f, 52f, 34f, world.gameTime * 40f, r, g, b)
    }

    private fun drawTower3D(t: TowerEntity, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(t.pos.x, t.pos.y)
        val isRad = t.team == Team.RADIANT
        val (r, g, b) = if (isRad) Triple(0.18f, 0.84f, 0.48f) else Triple(0.92f, 0.22f, 0.18f)

        if (t.pos.distTo(world.playerHero.pos) < t.attackRange + 260f) {
            GLES20.glUniform1f(uAlphaLoc, 0.34f)
            drawPart(ringMesh, t.pos.x, h + 3f, t.pos.y, t.attackRange, 1f, t.attackRange, 0f, r, g, b)
            GLES20.glUniform1f(uAlphaLoc, 1.0f)
        }

        // Stepped stone foundation + 4 corner buttresses (marble for Radiant, volcanic spikes for Dire)
        val baseCol = if (isRad) Triple(0.36f, 0.40f, 0.39f) else Triple(0.22f, 0.16f, 0.19f)
        drawPart(cylMesh, t.pos.x, h + 12f, t.pos.y, 90f, 24f, 90f, 0f, baseCol.first, baseCol.second, baseCol.third)
        drawPart(limbMesh, t.pos.x, h + 58f, t.pos.y, 52f, 70f, 52f, 0f, baseCol.first * 1.15f, baseCol.second * 1.15f, baseCol.third * 1.15f)
        for (k in 0..3) {
            val a = k * 90f + 45f
            val rad = Math.toRadians(a.toDouble()).toFloat()
            val bx = t.pos.x + cos(rad) * 32f
            val bz = t.pos.y + sin(rad) * 32f
            drawPart(boxMesh, bx, h + 38f, bz, 14f, 48f, 14f, a, baseCol.first * 0.9f, baseCol.second * 0.9f, baseCol.third * 0.9f)
            if (!isRad) {
                drawPartTilt(coneMesh, bx, h + 68f, bz, 10f, 20f, 10f, 0f, a, -16f, 0.65f, 0.14f, 0.12f)
            }
        }
        // Crown & Glowing Crystal Core (pulses brighter on shot!)
        val shotPulse = if (t.attackCooldown > 0.72f) 1.28f else 1.0f
        drawPart(torusMesh, t.pos.x, h + 94f, t.pos.y, 40f * shotPulse, 36f, 40f * shotPulse, world.gameTime * 55f, r, g, b)
        val bob = sin(world.gameTime * 3.0f + t.pos.x) * 6f
        drawPart(crystalMesh, t.pos.x, h + 126f + bob, t.pos.y, 40f * shotPulse, 56f * shotPulse, 40f * shotPulse, world.gameTime * 65f, r, g, b)

        if (t.fortifyTimer > 0f) {
            GLES20.glUniform1f(uAlphaLoc, 0.38f)
            drawPart(domeMesh, t.pos.x, h + 2f, t.pos.y, 130f, 160f, 130f, 0f, 0.25f, 0.80f, 1.0f)
            GLES20.glUniform1f(uAlphaLoc, 1.0f)
        }

        projectToScreen(t.pos.x, h + 170f, t.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "T${t.blueprint.tier}", t.hp, t.maxHp, 0f, 0f,
                if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false, notchStep = 500f))
        }
    }

    private fun drawBarracks3D(b: BarracksEntity, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(b.pos.x, b.pos.y)
        val isRad = b.team == Team.RADIANT
        val (r, g, cb) = if (isRad) Triple(0.16f, 0.78f, 0.45f) else Triple(0.88f, 0.22f, 0.18f)
        drawPart(boxMesh, b.pos.x, h + 24f, b.pos.y, 72f, 48f, 66f, 0f, 0.25f, 0.27f, 0.31f)
        drawPart(coneMesh, b.pos.x, h + 60f, b.pos.y, 82f, 28f, 76f, 45f, r * 0.65f, g * 0.65f, cb * 0.65f)
        if (b.isMelee) {
            drawPart(boxMesh, b.pos.x, h + 74f, b.pos.y, 14f, 26f, 14f, 0f, r, g, cb)
        } else {
            drawPart(crystalMesh, b.pos.x, h + 80f, b.pos.y, 24f, 34f, 24f, world.gameTime * 55f, r, g, cb)
        }
        projectToScreen(b.pos.x, h + 98f, b.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", b.hp, b.maxHp, 0f, 0f,
                if (isRad) 0xFF27AE60.toInt() else 0xFFC0392B.toInt(), false))
        }
    }

    private fun drawAncient3D(anc: AncientEntity, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(anc.pos.x, anc.pos.y)
        val isRad = anc.team == Team.RADIANT
        val (r, g, b) = if (isRad) Triple(0.14f, 0.90f, 0.80f) else Triple(0.98f, 0.24f, 0.12f)
        drawPart(cylMesh, anc.pos.x, h + 16f, anc.pos.y, 152f, 32f, 152f, 0f, 0.22f, 0.25f, 0.30f)
        drawPart(cylMesh, anc.pos.x, h + 38f, anc.pos.y, 108f, 24f, 108f, 0f, 0.28f, 0.31f, 0.36f)
        val bob = sin(world.gameTime * 2.2f) * 8f
        drawPart(crystalMesh, anc.pos.x, h + 108f + bob, anc.pos.y, 74f, 128f, 74f, world.gameTime * 25f, r, g, b)
        drawPartTilt(torusMesh, anc.pos.x, h + 106f + bob, anc.pos.y, 82f, 68f, 82f, 28f, world.gameTime * 48f, 0f, 1f, 0.84f, 0.16f)
        drawPartTilt(torusMesh, anc.pos.x, h + 106f + bob, anc.pos.y, 70f, 58f, 70f, -28f, -world.gameTime * 38f, 22f, r, g, b)
        for (k in 0..3) {
            val a = k * 90f + 45f
            val rad = Math.toRadians(a.toDouble()).toFloat()
            drawPart(crystalMesh, anc.pos.x + cos(rad) * 58f, h + 68f, anc.pos.y + sin(rad) * 58f, 22f, 52f, 22f, a, r * 0.8f, g * 0.8f, b * 0.8f)
        }
        projectToScreen(anc.pos.x, h + 188f, anc.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "${anc.team.name} ANCIENT", anc.hp, anc.maxHp, 0f, 0f,
                if (isRad) 0xFF00E5FF.toInt() else 0xFFFF3D00.toInt(), false, notchStep = 500f))
        }
    }

    private fun drawCreep3D(c: CreepEntity, isLocked: Boolean, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(c.pos.x, c.pos.y)
        val isRad = c.team == Team.RADIANT
        val sc = when (c.type) {
            CreepType.MEGA_MELEE -> 1.38f
            CreepType.SUPER_MELEE -> 1.18f
            CreepType.SIEGE -> 1.25f
            else -> 1.0f
        }
        val (r, g, b) = if (isRad) Triple(0.16f, 0.78f, 0.44f) else Triple(0.88f, 0.26f, 0.22f)
        val cAngleDeg = -Math.toDegrees(c.facingAngle.toDouble()).toFloat() + 90f
        val cosA = cos(c.facingAngle); val sinA = sin(c.facingAngle)
        val rx = -sinA; val rz = cosA

        if (isLocked) {
            val pulse = 1.0f + sin(world.gameTime * 9f) * 0.12f
            drawPart(torusMesh, c.pos.x, h + 3f, c.pos.y, 26f * sc * pulse, 20f, 26f * sc * pulse, 0f, 0.98f, 0.22f, 0.22f)
        }
        // Ground shadow
        GLES20.glUniform1f(uAlphaLoc, 0.35f)
        drawPart(cylMesh, c.pos.x, h + 1.2f, c.pos.y, 28f * sc, 1.5f, 28f * sc, 0f, 0f, 0f, 0f)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        if (c.type == CreepType.SIEGE) {
            // Siege Catapult with animated rolling wheels & launch arm
            val wheelBob = abs(sin(c.walkCycle)) * 2f
            drawPart(boxMesh, c.pos.x, h + (15f + wheelBob) * sc, c.pos.y, 26f * sc, 14f * sc, 34f * sc, cAngleDeg, 0.44f, 0.29f, 0.17f)
            drawPartTilt(cylMesh, c.pos.x - rx * 15f * sc, h + 10f * sc, c.pos.y - rz * 15f * sc, 16f * sc, 6f * sc, 16f * sc, 0f, cAngleDeg, 90f, 0.25f, 0.20f, 0.16f)
            drawPartTilt(cylMesh, c.pos.x + rx * 15f * sc, h + 10f * sc, c.pos.y + rz * 15f * sc, 16f * sc, 6f * sc, 16f * sc, 0f, cAngleDeg, 90f, 0.25f, 0.20f, 0.16f)
            val armTilt = if (c.attackAnimTimer > 0f) -45f else 25f
            drawPartTilt(boxMesh, c.pos.x, h + 28f * sc, c.pos.y, 5f * sc, 26f * sc, 5f * sc, armTilt, cAngleDeg, 0f, r, g, b)
            drawPart(sphereMesh, c.pos.x + cosA * 10f * sc, h + 38f * sc, c.pos.y + sinA * 10f * sc, 12f * sc, 12f * sc, 12f * sc, 0f, r, g, b)
        } else if (c.type == CreepType.RANGED) {
            // Ranged Mage Creep with robe glide & glowing crystal staff
            val glide = abs(sin(c.walkCycle)) * 2.8f * sc
            val staffThrust = if (c.attackAnimTimer > 0f) 18f else 0f
            drawPart(coneMesh, c.pos.x, h + 20f * sc + glide, c.pos.y, 24f * sc, 38f * sc, 24f * sc, cAngleDeg, r * 0.85f, g * 0.85f, b * 0.85f)
            drawPart(sphereMesh, c.pos.x, h + 42f * sc + glide, c.pos.y, 14f * sc, 14f * sc, 14f * sc, cAngleDeg, 0.85f, 0.78f, 0.70f)
            drawPart(coneMesh, c.pos.x, h + 50f * sc + glide, c.pos.y, 18f * sc, 16f * sc, 18f * sc, cAngleDeg, r, g, b)
            // Angled Crystal Staff
            val staffX = c.pos.x + rx * 12f * sc + cosA * 10f * sc
            val staffZ = c.pos.y + rz * 12f * sc + sinA * 10f * sc
            drawPartTilt(cylMesh, staffX, h + 26f * sc + glide, staffZ, 4f * sc, 42f * sc, 4f * sc, 16f + staffThrust, cAngleDeg, 0f, 0.45f, 0.30f, 0.18f)
            drawPart(crystalMesh, staffX + cosA * 7f * sc, h + 49f * sc + glide, staffZ + sinA * 7f * sc, 12f * sc, 15f * sc, 12f * sc, world.gameTime * 90f, r, g, b)
        } else {
            // Melee Creep with walking legs, shield & swinging sword
            val stride = sin(c.walkCycle) * 7f * sc
            val atkSwing = if (c.attackAnimTimer > 0f && c.attackAnimDuration > 0f) {
                val p = 1f - (c.attackAnimTimer / c.attackAnimDuration).coerceIn(0f, 1f)
                sin(p * PI.toFloat()) * 42f
            } else 0f
            drawPart(cylMesh, c.pos.x - rx * 6f * sc + cosA * stride, h + 9f * sc, c.pos.y - rz * 6f * sc + sinA * stride, 7f * sc, 18f * sc, 7f * sc, cAngleDeg, 0.22f, 0.24f, 0.28f)
            drawPart(cylMesh, c.pos.x + rx * 6f * sc - cosA * stride, h + 9f * sc, c.pos.y + rz * 6f * sc - sinA * stride, 7f * sc, 18f * sc, 7f * sc, cAngleDeg, 0.22f, 0.24f, 0.28f)
            drawPart(limbMesh, c.pos.x, h + 25f * sc, c.pos.y, 20f * sc, 20f * sc, 16f * sc, cAngleDeg, r, g, b)
            drawPart(sphereMesh, c.pos.x, h + 40f * sc, c.pos.y, 13f * sc, 13f * sc, 13f * sc, cAngleDeg, 0.86f, 0.76f, 0.66f)
            drawPart(coneMesh, c.pos.x, h + 47f * sc, c.pos.y, 15f * sc, 12f * sc, 15f * sc, cAngleDeg, r * 0.8f, g * 0.8f, b * 0.8f)
            // Shield on left arm
            drawPart(boxMesh, c.pos.x - rx * 13f * sc + cosA * 4f * sc, h + 26f * sc, c.pos.y - rz * 13f * sc + sinA * 4f * sc,
                4f * sc, 16f * sc, 14f * sc, cAngleDeg, 0.75f, 0.78f, 0.84f)
            // Sword on right arm
            drawPart(bladeMesh, c.pos.x + rx * 13f * sc, h + 27f * sc, c.pos.y + rz * 13f * sc,
                22f * sc, 22f * sc, 24f * sc, cAngleDeg - atkSwing, 0.82f, 0.86f, 0.92f)
        }

        projectToScreen(c.pos.x, h + 56f * sc, c.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", c.hp, c.maxHp, 0f, 0f,
                if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false))
        }
    }

    private fun drawNeutral3D(n: NeutralCreepEntity, isLocked: Boolean, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(n.pos.x, n.pos.y)
        val sc = 0.88f + n.tier * 0.16f
        val angleDeg = -Math.toDegrees(n.facingAngle.toDouble()).toFloat() + 90f
        val cosA = cos(n.facingAngle); val sinA = sin(n.facingAngle)
        val rx = -sinA; val rz = cosA
        val stride = sin(n.walkCycle) * 7f * sc

        if (isLocked) {
            drawPart(torusMesh, n.pos.x, h + 3f, n.pos.y, 30f * sc, 22f, 30f * sc, 0f, 0.98f, 0.22f, 0.22f)
        }
        GLES20.glUniform1f(uAlphaLoc, 0.35f)
        drawPart(cylMesh, n.pos.x, h + 1.2f, n.pos.y, 32f * sc, 1.5f, 32f * sc, 0f, 0f, 0f, 0f)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        val (br, bg, bb) = when (n.tier) {
            4 -> Triple(0.55f, 0.38f, 0.22f)
            3 -> Triple(0.46f, 0.28f, 0.42f)
            else -> Triple(0.58f, 0.42f, 0.26f)
        }
        drawPart(cylMesh, n.pos.x - rx * 8f * sc + cosA * stride, h + 10f * sc, n.pos.y - rz * 8f * sc + sinA * stride, 9f * sc, 20f * sc, 9f * sc, angleDeg, br * 0.7f, bg * 0.7f, bb * 0.7f)
        drawPart(cylMesh, n.pos.x + rx * 8f * sc - cosA * stride, h + 10f * sc, n.pos.y + rz * 8f * sc - sinA * stride, 9f * sc, 20f * sc, 9f * sc, angleDeg, br * 0.7f, bg * 0.7f, bb * 0.7f)
        drawPart(limbMesh, n.pos.x, h + 28f * sc, n.pos.y, 28f * sc, 26f * sc, 22f * sc, angleDeg, br, bg, bb)
        drawPart(sphereMesh, n.pos.x, h + 46f * sc, n.pos.y, 20f * sc, 18f * sc, 20f * sc, angleDeg, br * 1.15f, bg * 1.15f, bb * 1.15f)
        drawPart(coneMesh, n.pos.x - rx * 8f * sc, h + 56f * sc, n.pos.y - rz * 8f * sc, 6f * sc, 14f * sc, 6f * sc, angleDeg, 0.92f, 0.85f, 0.68f)
        drawPart(coneMesh, n.pos.x + rx * 8f * sc, h + 56f * sc, n.pos.y + rz * 8f * sc, 6f * sc, 14f * sc, 6f * sc, angleDeg, 0.92f, 0.85f, 0.68f)
        projectToScreen(n.pos.x, h + 64f * sc, n.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", n.hp, n.maxHp, 0f, 0f, 0xFFF39C12.toInt(), false))
        }
    }

    private fun drawRoshan3D(rsh: RoshanEntity, isLocked: Boolean, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(rsh.pos.x, rsh.pos.y)
        val angleDeg = -Math.toDegrees(rsh.facingAngle.toDouble()).toFloat() + 90f
        val cosA = cos(rsh.facingAngle); val sinA = sin(rsh.facingAngle)
        val rx = -sinA; val rz = cosA
        val breath = sin(world.gameTime * 3.5f) * 2.5f

        if (isLocked) {
            drawPart(torusMesh, rsh.pos.x, h + 4f, rsh.pos.y, 56f, 32f, 56f, 0f, 0.98f, 0.22f, 0.22f)
        }
        GLES20.glUniform1f(uAlphaLoc, 0.45f)
        drawPart(cylMesh, rsh.pos.x, h + 1.5f, rsh.pos.y, 82f, 2f, 82f, 0f, 0f, 0f, 0f)
        GLES20.glUniform1f(uAlphaLoc, 1.0f)

        // Heavy legs, demon chest, fiery core, clawed arms, horned head & wings
        drawPart(limbMesh, rsh.pos.x - rx * 18f, h + 18f, rsh.pos.y - rz * 18f, 18f, 36f, 18f, angleDeg, 0.34f, 0.12f, 0.12f)
        drawPart(limbMesh, rsh.pos.x + rx * 18f, h + 18f, rsh.pos.y + rz * 18f, 18f, 36f, 18f, angleDeg, 0.34f, 0.12f, 0.12f)
        drawPart(limbMesh, rsh.pos.x, h + 52f + breath, rsh.pos.y, 64f, 48f, 46f, angleDeg, 0.48f, 0.14f, 0.14f)
        drawPart(crystalMesh, rsh.pos.x + cosA * 22f, h + 54f + breath, rsh.pos.y + sinA * 22f, 18f, 24f, 14f, angleDeg, 1.0f, 0.45f, 0.10f)
        drawPart(sphereMesh, rsh.pos.x, h + 88f + breath, rsh.pos.y, 38f, 34f, 36f, angleDeg, 0.58f, 0.18f, 0.16f)
        drawPartTilt(coneMesh, rsh.pos.x - rx * 16f, h + 106f + breath, rsh.pos.y - rz * 16f, 10f, 28f, 10f, 0f, angleDeg, 22f, 0.95f, 0.85f, 0.65f)
        drawPartTilt(coneMesh, rsh.pos.x + rx * 16f, h + 106f + breath, rsh.pos.y + rz * 16f, 10f, 28f, 10f, 0f, angleDeg, -22f, 0.95f, 0.85f, 0.65f)
        // Spread wings
        drawPartTilt(boxMesh, rsh.pos.x - rx * 38f - cosA * 14f, h + 74f + breath, rsh.pos.y - rz * 38f - sinA * 14f,
            42f, 36f, 5f, 0f, angleDeg, 28f, 0.28f, 0.09f, 0.11f)
        drawPartTilt(boxMesh, rsh.pos.x + rx * 38f - cosA * 14f, h + 74f + breath, rsh.pos.y + rz * 38f - sinA * 14f,
            42f, 36f, 5f, 0f, angleDeg, -28f, 0.28f, 0.09f, 0.11f)

        projectToScreen(rsh.pos.x, h + 130f, rsh.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "ROSHAN", rsh.hp, rsh.maxHp, 0f, 0f, 0xFFFF9800.toInt(), false, notchStep = 1000f))
        }
    }

    private fun drawHero3D(hero: HeroEntity, isAlly: Boolean, isTargetLocked: Boolean) {
        val h = terrainHeight(hero.pos.x, hero.pos.y)
        val (pr, pg, pb) = rgbFloats(hero.def.primaryColor)
        val (ar, ag, ab) = rgbFloats(hero.def.accentColor)
        val angleDeg = -Math.toDegrees(hero.facingAngle.toDouble()).toFloat() + 90f
        val bkbScale = if (hero.hasStatus(StatusType.MAGIC_IMMUNE)) 1.25f else 1.0f
        val sc = 1.12f * bkbScale

        val atkProg = if (hero.attackAnimTimer > 0f && hero.attackAnimDuration > 0f) {
            1f - (hero.attackAnimTimer / hero.attackAnimDuration).coerceIn(0f, 1f)
        } else 0f
        val swinging = hero.attackAnimTimer > 0f
        val casting = hero.castAnimTimer > 0f
        val lungeOffset = if (swinging && !hero.def.isRanged) sin(atkProg * PI.toFloat()) * 16f else 0f
        val hx = hero.pos.x + cos(hero.facingAngle) * lungeOffset
        val hz = hero.pos.y + sin(hero.facingAngle) * lungeOffset

        val moving = abs(hero.moveIntent.x) > 0.05f || abs(hero.moveIntent.y) > 0.05f || hero.tapMoveTarget != null
        val walkSwing = if (moving) sin(hero.walkCycle) * 11f else 0f
        val idleBob = if (!moving) sin(world.gameTime * 3.5f + hero.pos.x * 0.01f) * 1.5f else abs(sin(hero.walkCycle)) * 2.2f

        val cosA = cos(hero.facingAngle)
        val sinA = sin(hero.facingAngle)
        val rx = -sinA
        val rz = cosA

        val alpha = if (hero.isInvisible()) 0.42f else 1.0f

        // Ground Shadow
        GLES20.glUniform1f(uAlphaLoc, alpha * 0.42f)
        drawPart(cylMesh, hx, h + 1.5f, hz, 58f * sc, 2f, 58f * sc, 0f, 0f, 0f, 0f)
        GLES20.glUniform1f(uAlphaLoc, alpha)

        // Selection Ring + Facing Indicator + Attack Range Ring
        if (hero.isPlayer) {
            drawPart(torusMesh, hero.pos.x, h + 3.5f, hero.pos.y, 44f, 24f, 44f, 0f, 0.18f, 0.92f, 0.46f)
            val arrowDist = 48f
            drawPart(crystalMesh, hero.pos.x + cosA * arrowDist, h + 5f, hero.pos.y + sinA * arrowDist,
                16f, 6f, 24f, angleDeg, 0.35f, 1.0f, 0.55f)
            if (showAttackRangeRing || hero.attackTargetId != null) {
                GLES20.glUniform1f(uAlphaLoc, 0.24f)
                drawPart(ringMesh, hero.pos.x, h + 3f, hero.pos.y, hero.cachedAttackRange, 1f, hero.cachedAttackRange, 0f, 0.25f, 0.85f, 0.95f)
                GLES20.glUniform1f(uAlphaLoc, alpha)
            }
        } else if (isTargetLocked) {
            val pulse = 1.0f + sin(world.gameTime * 9f) * 0.12f
            drawPart(torusMesh, hero.pos.x, h + 4f, hero.pos.y, 46f * pulse, 28f, 46f * pulse, 0f, 0.98f, 0.20f, 0.20f)
        } else {
            drawPart(ringMesh, hero.pos.x, h + 2.5f, hero.pos.y, 38f, 1f, 38f, 0f,
                if (isAlly) 0.2f else 0.92f, if (isAlly) 0.62f else 0.20f, if (isAlly) 0.95f else 0.20f)
        }

        val isBulky = hero.def.id == "pudge" || hero.def.id == "axe" || hero.def.id == "earthshaker"
        val torsoW = if (isBulky) 35f else 26f
        val torsoH = if (isBulky) 30f else 28f

        // Articulated Legs + Boots
        val legX = 9.5f * sc
        drawPart(limbMesh, hx - rx * legX + cosA * walkSwing * 0.65f, h + 15f * sc, hz - rz * legX + sinA * walkSwing * 0.65f,
            11f * sc, 28f * sc, 11f * sc, angleDeg, 0.16f, 0.18f, 0.24f)
        drawPart(limbMesh, hx + rx * legX - cosA * walkSwing * 0.65f, h + 15f * sc, hz + rz * legX - sinA * walkSwing * 0.65f,
            11f * sc, 28f * sc, 11f * sc, angleDeg, 0.16f, 0.18f, 0.24f)

        // Waist Sash / Armored Skirt
        drawPart(limbMesh, hx, h + (29f + idleBob * 0.3f) * sc, hz, (torsoW + 3f) * sc, 12f * sc, 24f * sc, angleDeg, pr * 0.72f, pg * 0.72f, pb * 0.72f)

        // Sculpted Torso + Chest Emblem + Layered Shoulder Pauldrons
        val bodyY = h + (45f + idleBob) * sc
        drawPart(limbMesh, hx, bodyY, hz, torsoW * sc, torsoH * sc, 22f * sc, angleDeg, pr, pg, pb)
        drawPart(boxMesh, hx + cosA * 10f * sc, bodyY + 2f * sc, hz + sinA * 10f * sc,
            16f * sc, 16f * sc, 7f * sc, angleDeg, ar, ag, ab)
        // Left & Right Pauldrons
        val pSpread = (torsoW * 0.56f) * sc
        drawPart(sphereMesh, hx - rx * pSpread, bodyY + 11f * sc, hz - rz * pSpread, 15f * sc, 13f * sc, 17f * sc, angleDeg, ar, ag, ab)
        drawPart(sphereMesh, hx + rx * pSpread, bodyY + 11f * sc, hz + rz * pSpread, 15f * sc, 13f * sc, 17f * sc, angleDeg, ar, ag, ab)

        // Flowing Tapered Cape (angled backward)
        val capeFlutter = sin(world.gameTime * 6f + hero.walkCycle) * 6f
        drawPartTilt(boxMesh, hx - cosA * 14f * sc, bodyY - 5f * sc, hz - sinA * 14f * sc,
            24f * sc, 36f * sc, 4f * sc, 18f + capeFlutter, angleDeg, 0f, pr * 0.58f, pg * 0.58f, pb * 0.58f)

        // Head & Distinct Hero Headgear
        val headY = h + (69f + idleBob) * sc
        val skinCol = when (hero.def.id) {
            "axe" -> Triple(0.84f, 0.24f, 0.18f)
            "pudge" -> Triple(0.66f, 0.56f, 0.44f)
            "earthshaker" -> Triple(0.42f, 0.30f, 0.22f)
            "void_spirit" -> Triple(0.76f, 0.68f, 0.84f)
            "chronos" -> Triple(0.20f, 0.72f, 0.68f)
            else -> Triple(0.88f, 0.76f, 0.66f)
        }
        drawPart(sphereMesh, hx, headY, hz, 19f * sc, 20f * sc, 19f * sc, angleDeg, skinCol.first, skinCol.second, skinCol.third)

        when (hero.def.id) {
            "juggernaut" -> {
                // Iconic White Samurai Mask + Red Sash Headband
                drawPart(boxMesh, hx + cosA * 6f * sc, headY, hz + sinA * 6f * sc, 16f * sc, 17f * sc, 11f * sc, angleDeg, 0.96f, 0.93f, 0.88f)
                drawPart(boxMesh, hx, headY + 9f * sc, hz, 21f * sc, 5f * sc, 21f * sc, angleDeg, 0.88f, 0.18f, 0.15f)
            }
            "axe" -> {
                // Steel Helm + Curved Horns
                drawPartTilt(coneMesh, hx - rx * 11f * sc, headY + 12f * sc, hz - rz * 11f * sc, 7f * sc, 18f * sc, 7f * sc, 0f, angleDeg, 20f, 0.94f, 0.88f, 0.76f)
                drawPartTilt(coneMesh, hx + rx * 11f * sc, headY + 12f * sc, hz + rz * 11f * sc, 7f * sc, 18f * sc, 7f * sc, 0f, angleDeg, -20f, 0.94f, 0.88f, 0.76f)
            }
            "cm" -> {
                // Frost Braid Crown + Flowing Blonde Hair
                drawPart(coneMesh, hx, headY + 11f * sc, hz, 22f * sc, 14f * sc, 22f * sc, angleDeg, 0.75f, 0.92f, 1.0f)
                drawPart(boxMesh, hx - cosA * 8f * sc, headY - 4f * sc, hz - sinA * 8f * sc, 14f * sc, 18f * sc, 8f * sc, angleDeg, 0.96f, 0.88f, 0.55f)
            }
            "void_spirit" -> {
                // Celestial Conical Hat
                drawPart(coneMesh, hx, headY + 12f * sc, hz, 34f * sc, 16f * sc, 34f * sc, angleDeg, 0.45f, 0.22f, 0.60f)
            }
            "zeus" -> {
                // Golden Olympian Halo + White Crest
                drawPart(torusMesh, hx, headY + 12f * sc, hz, 26f * sc, 26f * sc, 26f * sc, world.gameTime * 60f, 1.0f, 0.84f, 0.22f)
                drawPart(boxMesh, hx, headY + 6f * sc, hz, 16f * sc, 10f * sc, 18f * sc, angleDeg, 0.95f, 0.96f, 0.98f)
            }
            "invoker" -> {
                // Golden High Collar + Arcane Blonde Crest
                drawPart(coneMesh, hx, headY + 12f * sc, hz, 18f * sc, 16f * sc, 18f * sc, angleDeg, 0.96f, 0.82f, 0.35f)
                drawPart(torusMesh, hx, headY - 6f * sc, hz, 26f * sc, 20f * sc, 26f * sc, 0f, 0.95f, 0.72f, 0.14f)
            }
            "sniper" -> {
                // Ghillie Hood + Brass Scope Goggles
                drawPart(coneMesh, hx, headY + 9f * sc, hz, 26f * sc, 16f * sc, 26f * sc, angleDeg, 0.42f, 0.38f, 0.24f)
                drawPart(boxMesh, hx + cosA * 8f * sc, headY + 2f * sc, hz + sinA * 8f * sc, 15f * sc, 6f * sc, 6f * sc, angleDeg, 0.98f, 0.78f, 0.15f)
            }
            "earthshaker" -> {
                // Curved Bull Horns
                drawPartTilt(coneMesh, hx - rx * 13f * sc, headY + 9f * sc, hz - rz * 13f * sc, 8f * sc, 20f * sc, 8f * sc, 0f, angleDeg, 45f, 0.88f, 0.82f, 0.70f)
                drawPartTilt(coneMesh, hx + rx * 13f * sc, headY + 9f * sc, hz + rz * 13f * sc, 8f * sc, 20f * sc, 8f * sc, 0f, angleDeg, -45f, 0.88f, 0.82f, 0.70f)
            }
            "antimage" -> {
                // Purple Monk Mohawk Crest
                drawPart(boxMesh, hx, headY + 12f * sc, hz, 6f * sc, 14f * sc, 22f * sc, angleDeg, 0.62f, 0.30f, 0.78f)
            }
            "pa" -> {
                // Assassin Hood & Glowing Visor
                drawPart(coneMesh, hx, headY + 10f * sc, hz, 24f * sc, 16f * sc, 24f * sc, angleDeg, 0.14f, 0.42f, 0.56f)
            }
            "chronos" -> {
                // Cosmic Helmet + Golden Astrolabe Halo Behind Back
                drawPart(crystalMesh, hx, headY + 10f * sc, hz, 18f * sc, 22f * sc, 18f * sc, angleDeg, 1.0f, 0.84f, 0.18f)
                drawPartTilt(torusMesh, hx - cosA * 10f * sc, bodyY + 10f * sc, hz - sinA * 10f * sc,
                    48f * sc, 48f * sc, 48f * sc, 90f, angleDeg, world.gameTime * 85f, 1.0f, 0.84f, 0.16f)
            }
        }

        // Left & Right Arms + Hero-Specific Weapons + Curved Slash Arcs
        val castLift = if (casting) -32f else 0f
        val swingAngle = if (swinging) hero.facingAngle + (-1.05f + atkProg * 2.1f) else hero.facingAngle + 0.35f
        val wRotDeg = -Math.toDegrees(swingAngle.toDouble()).toFloat() + 90f
        val handCos = cos(swingAngle)
        val handSin = sin(swingAngle)
        // Anchor weapon near right shoulder/hand, sweeping smoothly across front arc during melee swing
        val handX = hx + rx * (pSpread * 0.55f) + handCos * (20f * sc)
        val handZ = hz + rz * (pSpread * 0.55f) + handSin * (20f * sc)
        val swingPitch = if (swinging) -25f + atkProg * 75f else 18f

        // Left arm swinging opposite to legs
        drawPartTilt(limbMesh, hx - rx * (pSpread + 2f * sc), bodyY - 2f * sc, hz - rz * (pSpread + 2f * sc),
            9f * sc, 24f * sc, 9f * sc, walkSwing * 2.2f + castLift, angleDeg, 0f, skinCol.first * 0.9f, skinCol.second * 0.9f, skinCol.third * 0.9f)
        // Right weapon arm reaching toward hand
        drawPartTilt(limbMesh, hx + rx * (pSpread + 2f * sc), bodyY - 2f * sc, hz + rz * (pSpread + 2f * sc),
            9f * sc, 24f * sc, 9f * sc, -walkSwing * 2.2f + castLift - (if (swinging) 30f else 12f), wRotDeg, 0f, skinCol.first * 0.9f, skinCol.second * 0.9f, skinCol.third * 0.9f)

        when (hero.def.id) {
            "juggernaut" -> {
                // Curved Samurai Katana + Golden Guard
                drawPart(boxMesh, handX, bodyY - 1f * sc, handZ, 12f * sc, 4f * sc, 4f * sc, wRotDeg, 0.95f, 0.78f, 0.22f)
                drawPart(bladeMesh, handX, bodyY - 1f * sc, handZ, 44f * sc, 48f * sc, 60f * sc, wRotDeg, 0.98f, 0.90f, 0.62f)
            }
            "pa", "antimage" -> {
                // Dual Curved Crescent Warglaives
                drawPart(bladeMesh, handX, bodyY, handZ, 42f * sc, 44f * sc, 50f * sc, wRotDeg, ar, ag, ab)
                drawPart(bladeMesh, hx - rx * 18f * sc + cosA * 8f, bodyY, hz - rz * 18f * sc + sinA * 8f,
                    42f * sc, 44f * sc, 50f * sc, angleDeg + 18f, ar, ag, ab)
            }
            "sniper" -> {
                // Long Scoped Sniper Rifle + Wooden Stock + Brass Scope
                drawPart(boxMesh, hx + cosA * 18f * sc + rx * 6f * sc, bodyY + 2f * sc, hz + sinA * 18f * sc + rz * 6f * sc,
                    7f * sc, 9f * sc, 24f * sc, angleDeg, 0.45f, 0.28f, 0.14f)
                drawPart(boxMesh, hx + cosA * 34f * sc + rx * 6f * sc, bodyY + 4f * sc, hz + sinA * 34f * sc + rz * 6f * sc,
                    5f * sc, 6f * sc, 46f * sc, angleDeg, 0.20f, 0.22f, 0.25f)
                drawPartTilt(cylMesh, hx + cosA * 24f * sc + rx * 6f * sc, bodyY + 10f * sc, hz + sinA * 24f * sc + rz * 6f * sc,
                    6f * sc, 20f * sc, 6f * sc, 90f, angleDeg, 0f, 0.95f, 0.78f, 0.16f)
                if (swinging) {
                    drawPart(crystalMesh, hx + cosA * 59f * sc + rx * 6f * sc, bodyY + 4f * sc, hz + sinA * 59f * sc + rz * 6f * sc,
                        18f * sc, 18f * sc, 24f * sc, angleDeg, 1.0f, 0.92f, 0.30f)
                }
            }
            "axe" -> {
                // Giant Twin-Bit Red Mist Battleaxe
                drawPartTilt(cylMesh, handX + handCos * 16f, bodyY + 2f * sc, handZ + handSin * 16f, 5f * sc, 54f * sc, 5f * sc, 80f, wRotDeg, 0f, 0.45f, 0.28f, 0.16f)
                drawPartTilt(boxMesh, handX + handCos * 34f, bodyY + 4f * sc, handZ + handSin * 34f, 38f * sc, 6f * sc, 20f * sc, 0f, wRotDeg, swingPitch * 0.3f, 0.88f, 0.20f, 0.16f)
                drawPart(boxMesh, handX + handCos * 34f, bodyY + 4f * sc, handZ + handSin * 34f, 44f * sc, 3f * sc, 12f * sc, wRotDeg, 0.95f, 0.88f, 0.85f)
            }
            "earthshaker" -> {
                // Massive Enchanted Stone Totem (tilted forward and slammed on swing!)
                val tGlow = if (hero.enchantTotemBuff) 1.0f else 0.0f
                val totemPitch = if (swinging) 32f + atkProg * 58f else 28f
                drawPartTilt(limbMesh, handX + handCos * 14f, bodyY + 6f * sc, handZ + handSin * 14f,
                    16f * sc, 56f * sc, 16f * sc, totemPitch, wRotDeg, 0f, 0.45f + tGlow * 0.55f, 0.34f + tGlow * 0.50f, 0.22f)
                if (hero.enchantTotemBuff) {
                    drawPart(torusMesh, handX + handCos * 20f, bodyY + 14f * sc, handZ + handSin * 20f,
                        28f * sc, 24f * sc, 28f * sc, world.gameTime * 140f, 1.0f, 0.82f, 0.15f)
                }
            }
            "cm", "invoker" -> {
                // Ornate Angled Crystal / Arcane Mage Staff
                val stX = handX + handCos * 8f; val stZ = handZ + handSin * 8f
                drawPartTilt(cylMesh, stX, bodyY + 6f * sc, stZ, 5f * sc, 60f * sc, 5f * sc, 24f, wRotDeg, 0f, 0.50f, 0.35f, 0.22f)
                drawPart(crystalMesh, stX + handCos * 12f * sc, bodyY + 34f * sc, stZ + handSin * 12f * sc,
                    17f * sc, 26f * sc, 17f * sc, world.gameTime * 90f, ar, ag, ab)
            }
            "pudge" -> {
                // Stitched Belly Patch + Meat Cleaver + Curved Butcher Hook
                drawPart(boxMesh, hx + cosA * 11f * sc, bodyY - 2f * sc, hz + sinA * 11f * sc, 18f * sc, 14f * sc, 4f * sc, angleDeg, 0.72f, 0.52f, 0.44f)
                drawPart(boxMesh, handX + handCos * 12f, bodyY, handZ + handSin * 12f, 6f * sc, 18f * sc, 36f * sc, wRotDeg, 0.82f, 0.84f, 0.88f)
                drawPart(bladeMesh, hx - rx * 18f * sc + cosA * 6f, bodyY, hz - rz * 18f * sc + sinA * 6f, 40f * sc, 40f * sc, 40f * sc, angleDeg + 35f, 0.82f, 0.24f, 0.20f)
            }
            "zeus" -> {
                // Crackling Angled Golden Thunderbolt
                drawPartTilt(crystalMesh, handX + handCos * 12f, bodyY + 8f * sc, handZ + handSin * 12f,
                    15f * sc, 48f * sc, 15f * sc, 38f, wRotDeg, 0f, 1.0f, 0.90f, 0.22f)
            }
            else -> {
                // Void Spirit / Chronos Astral Halberd (tilted forward for thrust/slash)
                drawPartTilt(cylMesh, handX + handCos * 10f, bodyY + 4f * sc, handZ + handSin * 10f, 5f * sc, 56f * sc, 5f * sc, 42f, wRotDeg, 0f, 0.38f, 0.38f, 0.44f)
                drawPart(bladeMesh, handX + handCos * 20f, bodyY + 14f * sc, handZ + handSin * 20f, 40f * sc, 40f * sc, 46f * sc, wRotDeg, ar, ag, ab)
            }
        }

        // Smooth 3D Crescent Melee Slash Wave!
        if (swinging && !hero.def.isRanged) {
            GLES20.glUniform1f(uAlphaLoc, (1f - atkProg) * 0.88f)
            val slashRot = -Math.toDegrees((hero.facingAngle + (-0.6f + atkProg * 1.2f)).toDouble()).toFloat() + 90f
            drawPart(crescentMesh, hx, h + 42f * sc, hz, 62f * sc, 6f * sc, 62f * sc, slashRot, ar * 0.5f + 0.5f, ag * 0.5f + 0.45f, ab * 0.3f + 0.2f)
            GLES20.glUniform1f(uAlphaLoc, alpha)
        }

        // Hero-specific Active Auras & Orbs
        if (hero.def.id == "invoker") {
            val cols = arrayOf(Triple(0.22f, 0.74f, 0.98f), Triple(0.68f, 0.34f, 0.98f), Triple(1.0f, 0.60f, 0.08f))
            for (i in 0..2) {
                val a = world.gameTime * 3.4f + i * 2.094f
                drawPart(sphereMesh, hx + cos(a) * 40f, h + 72f, hz + sin(a) * 40f, 14f, 14f, 14f, 0f, cols[i].first, cols[i].second, cols[i].third)
            }
        }
        if (hero.hasStatus(StatusType.BLADE_FURY)) {
            GLES20.glUniform1f(uAlphaLoc, 0.75f)
            drawPart(crescentMesh, hx, h + 38f * sc, hz, 185f, 14f, 185f, world.gameTime * 720f, 1.0f, 0.78f, 0.15f)
            drawPart(crescentMesh, hx, h + 44f * sc, hz, 185f, 14f, 185f, world.gameTime * 720f + 180f, 1.0f, 0.55f, 0.12f)
            GLES20.glUniform1f(uAlphaLoc, alpha)
        }
        if (hero.rotEnabled) {
            GLES20.glUniform1f(uAlphaLoc, 0.42f)
            drawPart(ringMesh, hx, h + 12f, hz, 210f, 1f, 210f, world.gameTime * 180f, 0.20f, 0.88f, 0.32f)
            GLES20.glUniform1f(uAlphaLoc, alpha)
        }
        if (hero.isStunned()) {
            val sa = world.gameTime * 300f
            drawPart(torusMesh, hx, headY + 24f * sc, hz, 34f * sc, 24f * sc, 34f * sc, sa, 1.0f, 0.90f, 0.22f)
        }

        // Visible Attack Target Line from Player to Locked Target
        if (hero.isPlayer && hero.attackTargetId != null) {
            val target = world.findUnitById(hero.attackTargetId)
            if (target != null && target.alive) {
                val steps = 6
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps
                    val lx = DotaMath.lerp(hero.pos.x, target.pos.x, t)
                    val lz = DotaMath.lerp(hero.pos.y, target.pos.y, t)
                    val lh = terrainHeight(lx, lz)
                    drawPart(sphereMesh, lx, lh + 5f, lz, 7f, 3f, 7f, 0f, 1.0f, 0.32f, 0.22f)
                }
            }
        }

        if (hero.hasStatus(StatusType.MAGIC_IMMUNE)) {
            GLES20.glUniform1f(uAlphaLoc, 0.28f)
            drawPart(domeMesh, hx, h + 2f, hz, 96f, 108f, 96f, world.gameTime * 100f, 1.0f, 0.84f, 0.12f)
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
    }

    private fun drawAimIndicators3D() {
        val ph = world.playerHero
        val h = terrainHeight(ph.pos.x, ph.pos.y)
        if (aimingAbilityIdx in 0..3) {
            val ab = ph.def.abilities.getOrNull(aimingAbilityIdx) ?: return
            GLES20.glUniform1f(uAlphaLoc, 0.48f)
            val range = ab.castRange.takeIf { it > 0f } ?: 650f
            if (ab.castRange > 0f) {
                drawPart(ringMesh, ph.pos.x, h + 4f, ph.pos.y, range, 1f, range, 0f, 0.22f, 0.74f, 0.98f)
            }
            if (ab.targetType == TargetType.DIRECTION_SKILLSHOT) {
                val steps = 10
                val ang = ph.pos.angleTo(aimWorldPos)
                val endPt = ph.pos + Vec2.fromAngle(ang, range)
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    val lx = DotaMath.lerp(ph.pos.x, endPt.x, t)
                    val lz = DotaMath.lerp(ph.pos.y, endPt.y, t)
                    val lh = terrainHeight(lx, lz)
                    drawPart(sphereMesh, lx, lh + 7f, lz, 18f, 4f, 18f, 0f, 0.25f, 0.88f, 1.0f)
                }
            } else {
                val rad = ab.radius.takeIf { it > 0f } ?: 95f
                val ah = terrainHeight(aimWorldPos.x, aimWorldPos.y)
                drawPart(ringMesh, aimWorldPos.x, ah + 5f, aimWorldPos.y, rad, 1f, rad, 0f, 0.98f, 0.78f, 0.16f)
                drawPart(sphereMesh, aimWorldPos.x, ah + 5f, aimWorldPos.y, rad * 1.6f, 3f, rad * 1.6f, 0f, 0.22f, 0.74f, 0.98f)
            }
            GLES20.glUniform1f(uAlphaLoc, 1.0f)
        } else if (aimingItemSlotIdx in ph.inventory.indices) {
            val ah = terrainHeight(aimWorldPos.x, aimWorldPos.y)
            GLES20.glUniform1f(uAlphaLoc, 0.48f)
            drawPart(ringMesh, ph.pos.x, h + 4f, ph.pos.y, 850f, 1f, 850f, 0f, 0.98f, 0.76f, 0.20f)
            drawPart(ringMesh, aimWorldPos.x, ah + 5f, aimWorldPos.y, 95f, 1f, 95f, 0f, 1.0f, 0.82f, 0.18f)
            GLES20.glUniform1f(uAlphaLoc, 1.0f)
        }
    }

    private fun drawZones3D() {
        for (z in world.zones) {
            val h = terrainHeight(z.pos.x, z.pos.y)
            val (r, g, b) = rgbFloats(z.color)
            if (z.kind == "fissure" && z.endPos != null) {
                val steps = 12
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val rx = DotaMath.lerp(z.pos.x, z.endPos.x, t)
                    val rz = DotaMath.lerp(z.pos.y, z.endPos.y, t)
                    val rh = terrainHeight(rx, rz)
                    drawPart(crystalMesh, rx, rh + 24f, rz, 44f, 52f, 44f, i * 25f, 0.48f, 0.30f, 0.18f)
                }
                continue
            }
            GLES20.glUniform1f(uAlphaLoc, 0.36f)
            when (z.kind) {
                "chrono" -> {
                    drawPart(domeMesh, z.pos.x, h + 2f, z.pos.y, z.radius * 2f, z.radius * 1.35f, z.radius * 2f, world.gameTime * 35f, 1.0f, 0.84f, 0.14f)
                    drawPart(ringMesh, z.pos.x, h + 10f, z.pos.y, z.radius, 1f, z.radius, -world.gameTime * 55f, 1.0f, 0.90f, 0.25f)
                }
                "sunstrike" -> {
                    drawPart(cylMesh, z.pos.x, h + 150f, z.pos.y, z.radius * 0.7f, 300f, z.radius * 0.7f, 0f, 1.0f, 0.55f, 0.1f)
                    drawPart(ringMesh, z.pos.x, h + 5f, z.pos.y, z.radius, 1f, z.radius, 0f, 1.0f, 0.45f, 0.08f)
                }
                "healing_ward" -> {
                    GLES20.glUniform1f(uAlphaLoc, 0.92f)
                    drawPart(crystalMesh, z.pos.x, h + 38f + sin(world.gameTime * 4f) * 6f, z.pos.y, 24f, 34f, 24f, world.gameTime * 90f, 0.22f, 0.94f, 0.55f)
                    GLES20.glUniform1f(uAlphaLoc, 0.28f)
                    drawPart(ringMesh, z.pos.x, h + 4f, z.pos.y, z.radius, 1f, z.radius, 0f, 0.22f, 0.94f, 0.55f)
                }
                else -> {
                    drawPart(ringMesh, z.pos.x, h + 5f, z.pos.y, z.radius, 1f, z.radius, 0f, r, g, b)
                }
            }
        }
        GLES20.glUniform1f(uAlphaLoc, 1.0f)
    }

    private fun drawGate3D(pos: Vec2) {
        val h = terrainHeight(pos.x, pos.y)
        drawPart(torusMesh, pos.x, h + 4f, pos.y, 54f, 24f, 54f, world.gameTime * 60f, 0.68f, 0.34f, 0.98f)
        drawPart(limbMesh, pos.x - 36f, h + 38f, pos.y, 14f, 76f, 14f, 0f, 0.45f, 0.24f, 0.70f)
        drawPart(limbMesh, pos.x + 36f, h + 38f, pos.y, 14f, 76f, 14f, 0f, 0.45f, 0.24f, 0.70f)
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
        val ax = abs(sx).coerceAtLeast(0.001f)
        val ay = abs(sy).coerceAtLeast(0.001f)
        val az = abs(sz).coerceAtLeast(0.001f)
        val maxS = maxOf(ax, maxOf(ay, az))
        drawMesh(mesh, r, g, b, maxS / ax, maxS / ay, maxS / az)
    }

    private fun drawPartTilt(
        mesh: Mesh3D,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotXDeg: Float, rotYDeg: Float, rotZDeg: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotYDeg, 0f, 1f, 0f)
        if (rotXDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotXDeg, 1f, 0f, 0f)
        if (rotZDeg != 0f) Matrix.rotateM(modelMatrix, 0, rotZDeg, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        val ax = abs(sx).coerceAtLeast(0.001f)
        val ay = abs(sy).coerceAtLeast(0.001f)
        val az = abs(sz).coerceAtLeast(0.001f)
        val maxS = maxOf(ax, maxOf(ay, az))
        drawMesh(mesh, r, g, b, maxS / ax, maxS / ay, maxS / az)
    }

    private fun drawMesh(
        mesh: Mesh3D,
        r: Float, g: Float, b: Float,
        relInvSx: Float = 1f, relInvSy: Float = 1f, relInvSz: Float = 1f
    ) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uInvScaleLoc, relInvSx, relInvSy, relInvSz)
        GLES20.glUniform3f(uTintLoc, r, g, b)
        mesh.draw(aPosLoc, aNormLoc, aColLoc)
    }

    private fun copyFogPixels() {
        val n = fogGridSize
        fogPixels.position(0)
        for (gy in 0 until n) {
            for (gx in 0 until n) {
                val b = when (world.fogOfWar.getCellState(gx, gy)) {
                    2 -> 255.toByte()
                    1 -> 132.toByte()
                    else -> 54.toByte()
                }
                fogPixels.put(b)
            }
        }
        fogPixels.position(0)
    }

    private fun uploadFogTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTexId)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, fogGridSize, fogGridSize, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, fogPixels)
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
        val planeY = terrainHeight(cameraPos.x, cameraPos.y)
        val t = ((planeY - ny) / dy).coerceIn(0f, 1f)
        val gx = (nx + (fx - nx) * t).coerceIn(0f, MapLayout.SIZE)
        val gz = (nz + (fz - nz) * t).coerceIn(0f, MapLayout.SIZE)
        return Vec2(gx, gz)
    }

    private fun terrainHeight(wx: Float, wz: Float): Float {
        val rDist = MapLayout.riverDistance(wx, wz)
        val riverDip = -26f * (1f - DotaMath.smoothstep(95f, 200f, rDist))
        val hg = MapLayout.highGroundFactor(wx, wz)
        val cliffRise = 38f * hg
        val roll = if (rDist > 220f && hg < 0.1f) sin(wx * 0.0038f) * cos(wz * 0.0038f) * 3.5f else 0f
        return riverDip + cliffRise + roll
    }

    private fun terrainSlopeShade(wx: Float, wz: Float, h: Float): Triple<Float, Float, Float> {
        val hg = MapLayout.highGroundFactor(wx, wz)
        val cliffEdge = if (hg in 0.08f..0.90f) 0.82f else 1.0f
        return Triple(cliffEdge, cliffEdge, cliffEdge)
    }

    private fun rgbFloats(argb: Int): Triple<Float, Float, Float> =
        Triple(Color.red(argb) / 255f, Color.green(argb) / 255f, Color.blue(argb) / 255f)

    private fun compileProgram(vsSrc: String, fsSrc: String): Int {
        fun compileShader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val status = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("World3DRenderer", "Shader compile error: ${GLES20.glGetShaderInfoLog(s)}")
            }
            return s
        }
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("World3DRenderer", "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
        }
        return prog
    }

    companion object {
        private const val VERTEX_SHADER = """
            precision highp float;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform vec3 uInvScale;
            attribute vec3 aPos;
            attribute vec3 aNorm;
            attribute vec3 aCol;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vNorm = normalize(mat3(uModel) * (aNorm * uInvScale * uInvScale));
                vCol = aCol;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec3 uTint;
            uniform vec3 uSunDir;
            uniform vec3 uAmbient;
            uniform vec3 uCamPos;
            uniform int uUseFog;
            uniform int uUseGroundTex;
            uniform int uUseMapTex;
            uniform int uWaterMode;
            uniform sampler2D uFogTex;
            uniform sampler2D uGroundTex;
            uniform sampler2D uMapTex;
            uniform float uMapSize;
            uniform float uAlpha;
            uniform float uTime;
            varying vec3 vWorldPos;
            varying vec3 vNorm;
            varying vec3 vCol;
            void main() {
                vec3 N = normalize(vNorm);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                float diff = max(dot(N, L), 0.0);
                vec3 hemi = mix(uAmbient * 0.72, uAmbient * 1.12, N.y * 0.5 + 0.5);
                vec3 baseColor = vCol * uTint;

                if (uUseMapTex == 1) {
                    vec2 muv = clamp(vWorldPos.xz / uMapSize, 0.0, 1.0);
                    baseColor *= texture2D(uMapTex, muv).rgb;
                }

                vec3 lit = (hemi + diff * 0.74) * baseColor;

                if (uUseGroundTex == 1) {
                    vec2 guv = vWorldPos.xz * 0.0052;
                    float texVal = texture2D(uGroundTex, guv).r;
                    lit *= (0.78 + texVal * 0.36);
                }

                if (uWaterMode == 1) {
                    float diag = (vWorldPos.x + vWorldPos.z) * 0.032 - uTime * 2.2;
                    float across = (vWorldPos.x - vWorldPos.z) * 0.048 + uTime * 1.3;
                    float w1 = sin(diag + sin(across) * 0.8);
                    float w2 = cos(across * 1.15 - uTime * 1.8);
                    float wave = (w1 + w2) * 0.5;
                    lit += vec3(0.05, 0.15, 0.22) * wave;
                    float glint = pow(max(wave, 0.001), 3.0) * 0.35;
                    lit += vec3(0.68, 0.90, 1.0) * glint;
                } else if (uUseMapTex == 0) {
                    float rim = pow(max(1.0 - max(dot(N, V), 0.0), 0.001), 2.6);
                    lit += baseColor * rim * 0.40;
                }

                if (uUseFog == 1) {
                    vec2 uv = clamp(vWorldPos.xz / uMapSize, 0.0, 1.0);
                    float fogVal = texture2D(uFogTex, uv).r;
                    lit = mix(vec3(0.05, 0.08, 0.14), lit, fogVal);
                }
                gl_FragColor = vec4(lit, uAlpha);
            }
        """
    }
}
