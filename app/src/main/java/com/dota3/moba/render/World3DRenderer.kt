package com.dota3.moba.render

import android.graphics.Color as AndroidColor
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.engine.*
import com.dota3.moba.model.StatusType
import com.dota3.moba.model.TargetType
import com.dota3.moba.model.Team
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

/**
 * LibGDX 3D OpenGL Engine for Dota 3:
 * - Loads generated smooth .obj 3D models with painted UV textures
 * - DirectionalShadowLight real shadow mapping + ambient/rim/point lighting
 * - Volumetric Fog of War shroud + 3D terrain + animated river water
 * - Hero 3-hit combo animations + 3D weapon ribbon trails + 3D projectile trails + 3D impact sparks
 * - Classic 60-deg Dota 2 strategic camera with look-ahead, skillshot shift & shake
 */
@Suppress("DEPRECATION")
class World3DRenderer(
    private val world: GameWorld,
    private val onFrameStep: (Float) -> Unit
) : ApplicationAdapter() {

    val cameraController = DotaCameraController(world.playerHero.pos.x, world.playerHero.pos.y)

    var cameraPos: Vec2
        get() = cameraController.cameraPos
        set(value) { cameraController.cameraPos.set(value.x, value.y) }

    var cameraLocked: Boolean
        get() = cameraController.cameraLocked
        set(value) { cameraController.cameraLocked = value }

    var cameraZoomDist: Float
        get() = cameraController.baseZoomDist
        set(value) { cameraController.baseZoomDist = cameraController.clampZoom(value) }

    @Volatile var aimingAbilityIdx: Int = -1
    @Volatile var aimingItemSlotIdx: Int = -1
    @Volatile var aimWorldPos: Vec2 = Vec2()
    @Volatile var showAttackRangeRing: Boolean = false
    @Volatile var viewportWidth: Int = 1280
    @Volatile var viewportHeight: Int = 720

    @Volatile var projectedBars: List<ProjectedBar> = emptyList()
    @Volatile var projectedTexts: List<ProjectedText> = emptyList()

    private lateinit var camera: PerspectiveCamera
    private lateinit var modelBatch: ModelBatch
    private lateinit var shadowBatch: ModelBatch
    private lateinit var environment: Environment
    private lateinit var shadowLight: DirectionalShadowLight
    private lateinit var rimLight: DirectionalLight
    private lateinit var spellFlashLight: PointLight

    // Thread-safe camera snapshot for UI screenToWorld raypicking
    private val camLock = Any()
    private val snapInvProjView = Matrix4()
    private val snapCamPos = Vector3()
    private var snapGroundY = 0f
    private var snapWidth = 1280f
    private var snapHeight = 720f

    // Shared textures & models
    private val loadedTextures = ArrayList<Texture>()
    private val loadedModels = ArrayList<Model>()
    private val pools = HashMap<String, ModelPool>()

    private lateinit var terrainModel: Model
    private lateinit var terrainInstance: ModelInstance
    private lateinit var terrainTex: Texture
    private lateinit var waterModel: Model
    private lateinit var waterInstance: ModelInstance
    private lateinit var waterTex: Texture
    private lateinit var waterTexAttr: TextureAttribute

    private lateinit var fogModel: Model
    private lateinit var fogInstance: ModelInstance
    private lateinit var fogPixmap: Pixmap
    private lateinit var fogTexture: Texture
    private var fogAccum = 0f

    private val shadowQueue = ArrayList<ModelInstance>(320)
    private val opaqueQueue = ArrayList<ModelInstance>(360)
    private val blendQueue = ArrayList<ModelInstance>(240)
    private val scratchVec = Vector3()
    private val shadowCenter = Vector3()
    private val zeroVec = Vector3(0f, 0f, 0f)

    private class ModelPool(val model: Model, val isBlended: Boolean = false) {
        private val items = ArrayList<ModelInstance>()
        private var cursor = 0

        fun reset() {
            cursor = 0
        }

        fun acquire(
            r: Float, g: Float, b: Float,
            alpha: Float = 1f,
            emissive: Float = 0f
        ): ModelInstance {
            val inst = if (cursor < items.size) {
                items[cursor]
            } else {
                ModelInstance(model).also { items.add(it) }
            }
            cursor++
            for (mat in inst.materials) {
                val diff = mat.get(ColorAttribute.Diffuse) as? ColorAttribute
                if (diff != null) {
                    diff.color.set(r, g, b, alpha)
                } else {
                    mat.set(ColorAttribute.createDiffuse(r, g, b, alpha))
                }
                if (emissive > 0.001f) {
                    val em = mat.get(ColorAttribute.Emissive) as? ColorAttribute
                    if (em != null) em.color.set(r * emissive, g * emissive, b * emissive, 1f)
                    else mat.set(ColorAttribute.createEmissive(r * emissive, g * emissive, b * emissive, 1f))
                } else {
                    val em = mat.get(ColorAttribute.Emissive) as? ColorAttribute
                    if (em != null) em.color.set(0f, 0f, 0f, 1f)
                    else mat.set(ColorAttribute.createEmissive(0f, 0f, 0f, 1f))
                }
                if (isBlended) {
                    val bl = mat.get(BlendingAttribute.Type) as? BlendingAttribute
                    if (bl != null) bl.opacity = alpha
                    else mat.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha))
                }
            }
            return inst
        }
    }

    override fun create() {
        if (loadedModels.isNotEmpty() || loadedTextures.isNotEmpty()) {
            dispose()
        }
        ObjLoader.logWarning = false

        viewportWidth = Gdx.graphics.width.coerceAtLeast(1)
        viewportHeight = Gdx.graphics.height.coerceAtLeast(1)

        camera = PerspectiveCamera(41f, viewportWidth.toFloat(), viewportHeight.toFloat()).apply {
            near = 110f
            far = 3200f
        }

        modelBatch = ModelBatch()
        shadowBatch = ModelBatch(DepthShaderProvider())

        environment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.42f, 0.43f, 0.46f, 1f))
            set(ColorAttribute(ColorAttribute.Fog, 0.05f, 0.07f, 0.11f, 1f))
        }

        shadowLight = DirectionalShadowLight(1024, 1024, 1750f, 1750f, 50f, 1850f).apply {
            set(0.95f, 0.90f, 0.82f, -0.45f, -0.82f, 0.35f)
        }
        rimLight = DirectionalLight().set(0.28f, 0.36f, 0.54f, 0.42f, -0.40f, -0.80f)
        environment.add(shadowLight)
        environment.add(rimLight)
        environment.shadowMap = shadowLight

        // Strategic Dota 2 Point Lights (Fountains, Roshan Magma Pit, Spell Flash)
        val radFtnLight = PointLight().set(0.18f, 0.95f, 0.65f, MapLayout.radiantFountain.x, 95f, MapLayout.radiantFountain.y, 48000f)
        val direFtnLight = PointLight().set(0.98f, 0.25f, 0.16f, MapLayout.direFountain.x, 95f, MapLayout.direFountain.y, 48000f)
        val roshLight = PointLight().set(1.0f, 0.45f, 0.12f, MapLayout.roshanPitPos.x, 90f, MapLayout.roshanPitPos.y, 35000f)
        spellFlashLight = PointLight().set(1f, 0.8f, 0.3f, 2400f, 110f, 2400f, 0f)
        environment.add(radFtnLight, direFtnLight, roshLight, spellFlashLight)

        loadObjModelsAndTextures()
        createTerrainModel()
        createWaterModel()
        createFogOfWarModel()
        createHelperVfxModels()

        cameraController.cameraPos.set(world.playerHero.pos.x, world.playerHero.pos.y)
        updateFogPixmap()
    }

    private fun loadTex(path: String, repeat: Boolean = false): Texture {
        val tex = Texture(Gdx.files.internal(path), true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            if (repeat) setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        }
        loadedTextures.add(tex)
        return tex
    }

    private fun loadObjModelsAndTextures() {
        val texHero = loadTex("dota/models/tex_hero_armor.png")
        val texRad = loadTex("dota/models/tex_radiant_stone.png")
        val texDire = loadTex("dota/models/tex_dire_obsidian.png")
        val texTreeRad = loadTex("dota/models/tex_tree_radiant.png")
        val texTreeDire = loadTex("dota/models/tex_tree_dire.png")
        val texRoshan = loadTex("dota/models/tex_roshan.png")
        val texWeapon = loadTex("dota/models/tex_weapons.png")

        val loader = ObjLoader()
        val params = ObjLoader.ObjLoaderParameters(true)

        fun registerObj(name: String, tex: Texture, shininess: Float = 16f, doubleSided: Boolean = false, blended: Boolean = false) {
            val model = loader.loadModel(Gdx.files.internal("dota/models/$name.obj"), params)
            loadedModels.add(model)
            for (mat in model.materials) {
                mat.set(TextureAttribute.createDiffuse(tex))
                mat.set(ColorAttribute.createSpecular(0.32f, 0.32f, 0.34f, 1f))
                mat.set(FloatAttribute.createShininess(shininess))
                if (doubleSided) mat.set(IntAttribute.createCullFace(GL20.GL_NONE))
                if (blended) {
                    mat.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.85f))
                    mat.set(DepthTestAttribute(GL20.GL_LEQUAL, false))
                }
            }
            pools[name] = ModelPool(model, blended)
            if (!blended && name.startsWith("hero_")) {
                // Separate translucent pool for invisible heroes
                val invModel = loader.loadModel(Gdx.files.internal("dota/models/$name.obj"), params)
                loadedModels.add(invModel)
                for (mat in invModel.materials) {
                    mat.set(TextureAttribute.createDiffuse(tex))
                    if (doubleSided) mat.set(IntAttribute.createCullFace(GL20.GL_NONE))
                    mat.set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.42f))
                    mat.set(DepthTestAttribute(GL20.GL_LEQUAL, false))
                }
                pools["${name}_inv"] = ModelPool(invModel, isBlended = true)
            }
        }

        // Heroes & Limbs
        registerObj("hero_torso_agile", texHero, 22f)
        registerObj("hero_torso_brute", texHero, 18f)
        registerObj("hero_torso_mage", texHero, 14f)
        registerObj("hero_limb", texHero, 16f)
        registerObj("hero_cape", texHero, 8f, doubleSided = true)
        registerObj("hero_head_samurai", texHero, 24f)
        registerObj("hero_head_horned", texHero, 20f)
        registerObj("hero_head_hood", texHero, 12f)
        registerObj("hero_head_mage", texHero, 22f)
        registerObj("hero_head_butcher", texHero, 12f)

        // Weapons & Ribbon Slash
        for (w in listOf(
            "weapon_katana", "weapon_axe", "weapon_glaive", "weapon_cleaver",
            "weapon_totem", "weapon_staff", "weapon_rifle", "weapon_bolt", "weapon_halberd"
        )) {
            registerObj(w, texWeapon, 28f)
        }
        registerObj("weapon_slash_ribbon", texWeapon, 8f, doubleSided = true, blended = true)

        // Creeps & Roshan
        registerObj("creep_melee", texHero, 14f)
        registerObj("creep_ranged", texHero, 12f)
        registerObj("creep_siege", texWeapon, 10f)
        registerObj("creep_neutral", texRoshan, 10f)
        registerObj("roshan", texRoshan, 16f, doubleSided = true)
        registerObj("ward_sentinel", texRad, 22f)

        // Structures & Trees
        registerObj("tower_radiant", texRad, 16f)
        registerObj("tower_dire", texDire, 16f)
        registerObj("barracks_radiant", texRad, 14f)
        registerObj("barracks_dire", texDire, 14f)
        registerObj("ancient_radiant", texRad, 24f)
        registerObj("ancient_dire", texDire, 24f)
        registerObj("tree_radiant", texTreeRad, 6f)
        registerObj("tree_dire", texTreeDire, 6f)

        // Projectiles
        for (p in listOf("proj_arrow", "proj_orb", "proj_hook", "proj_dagger", "proj_spear")) {
            registerObj(p, texWeapon, 28f)
        }
    }

    private fun buildTerrainTexture(): Texture {
        val texSize = 512
        val mapPix = Pixmap(texSize, texSize, Pixmap.Format.RGBA8888)
        val segs = ArrayList<Pair<Vec2, Vec2>>(18)
        for (lane in MapLayout.laneWaypoints) {
            for (i in 0 until lane.size - 1) segs.add(lane[i] to lane[i + 1])
        }
        val runeSpots = listOf(
            MapLayout.powerRuneTopPos, MapLayout.powerRuneBotPos,
            MapLayout.bountyRuneRadiantPos, MapLayout.bountyRuneDirePos
        )

        for (pz in 0 until texSize) {
            val wz = ((pz + 0.5f) / texSize) * MapLayout.SIZE
            for (px in 0 until texSize) {
                val wx = ((px + 0.5f) / texSize) * MapLayout.SIZE
                val isRad = wx < wz
                val nA = sin(wx * 0.009f) * cos(wz * 0.010f)
                val nB = sin((wx - wz) * 0.022f) * 0.5f
                val detail = (((px * 73856093) xor (pz * 19349663)) and 255) / 255f - 0.5f

                var r: Float
                var g: Float
                var b: Float
                if (isRad) {
                    r = 0.14f + nA * 0.030f + nB * 0.015f + detail * 0.035f
                    g = 0.34f + nA * 0.055f + nB * 0.020f + detail * 0.045f
                    b = 0.16f + nA * 0.020f + detail * 0.025f
                } else {
                    r = 0.29f + nA * 0.050f + nB * 0.022f + detail * 0.045f
                    g = 0.17f + nA * 0.028f + detail * 0.030f
                    b = 0.19f + nA * 0.028f + detail * 0.030f
                }

                // High ground cliff & sunlit/volcanic plateau
                val hg = MapLayout.highGroundFactor(wx, wz)
                if (hg > 0.01f) {
                    val cliffBand = if (hg in 0.06f..0.88f) 1f else 0f
                    if (isRad) {
                        r = DotaMath.lerp(r, 0.23f - cliffBand * 0.07f, hg)
                        g = DotaMath.lerp(g, 0.42f - cliffBand * 0.10f, hg)
                        b = DotaMath.lerp(b, 0.26f - cliffBand * 0.06f, hg)
                    } else {
                        r = DotaMath.lerp(r, 0.39f - cliffBand * 0.08f, hg)
                        g = DotaMath.lerp(g, 0.21f - cliffBand * 0.04f, hg)
                        b = DotaMath.lerp(b, 0.23f - cliffBand * 0.04f, hg)
                    }
                }

                // Cobblestone lanes
                var minLaneDist = 9999f
                for ((a, bPt) in segs) {
                    val d = DotaMath.distPointToSegment(wx, wz, a.x, a.y, bPt.x, bPt.y)
                    if (d < minLaneDist) minLaneDist = d
                }
                val roadMask = 1f - DotaMath.smoothstep(74f, 132f, minLaneDist)
                if (roadMask > 0.01f) {
                    val cobble = (sin(wx * 0.11f) * cos(wz * 0.11f)) * 0.045f + detail * 0.04f
                    val borderMask = DotaMath.smoothstep(68f, 114f, minLaneDist)
                    val rr = (if (isRad) 0.44f else 0.40f) - borderMask * 0.09f + cobble
                    val rg = (if (isRad) 0.41f else 0.33f) - borderMask * 0.07f + cobble
                    val rb = (if (isRad) 0.34f else 0.34f) - borderMask * 0.06f + cobble
                    r = DotaMath.lerp(r, rr, roadMask)
                    g = DotaMath.lerp(g, rg, roadMask)
                    b = DotaMath.lerp(b, rb, roadMask)
                }

                // Base courtyards
                val radBaseDist = hypot(wx - MapLayout.radiantAncientPos.x, wz - MapLayout.radiantAncientPos.y)
                if (radBaseDist < 490f) {
                    val m = 1f - DotaMath.smoothstep(290f, 490f, radBaseDist)
                    val ringGlow = if (radBaseDist in 205f..246f) 0.18f else 0f
                    r = DotaMath.lerp(r, 0.36f, m)
                    g = DotaMath.lerp(g, 0.45f + ringGlow, m)
                    b = DotaMath.lerp(b, 0.40f + ringGlow, m)
                }
                val direBaseDist = hypot(wx - MapLayout.direAncientPos.x, wz - MapLayout.direAncientPos.y)
                if (direBaseDist < 490f) {
                    val m = 1f - DotaMath.smoothstep(290f, 490f, direBaseDist)
                    val ringGlow = if (direBaseDist in 205f..246f) 0.24f else 0f
                    r = DotaMath.lerp(r, 0.31f + ringGlow, m)
                    g = DotaMath.lerp(g, 0.16f, m)
                    b = DotaMath.lerp(b, 0.18f, m)
                }

                // Roshan Pit & Rune Pads & Neutral Camps
                val roshDist = hypot(wx - MapLayout.roshanPitPos.x, wz - MapLayout.roshanPitPos.y)
                if (roshDist < 245f) {
                    val m = 1f - DotaMath.smoothstep(140f, 245f, roshDist)
                    val pitRing = if (roshDist in 150f..178f) 0.18f else 0f
                    r = DotaMath.lerp(r, 0.28f + pitRing, m)
                    g = DotaMath.lerp(g, 0.19f + pitRing * 0.35f, m)
                    b = DotaMath.lerp(b, 0.16f, m)
                }
                for (rs in runeSpots) {
                    val rd = hypot(wx - rs.x, wz - rs.y)
                    if (rd < 90f && rd > 50f) {
                        val ringM = 1f - abs(rd - 70f) / 22f
                        r = DotaMath.lerp(r, 0.24f, ringM * 0.65f)
                        g = DotaMath.lerp(g, 0.48f, ringM * 0.65f)
                        b = DotaMath.lerp(b, 0.62f, ringM * 0.65f)
                    }
                }
                for (camp in MapLayout.neutralCamps) {
                    val cd = hypot(wx - camp.pos.x, wz - camp.pos.y)
                    if (cd < 135f) {
                        val m = (1f - DotaMath.smoothstep(65f, 135f, cd)) * 0.65f
                        r = DotaMath.lerp(r, 0.28f, m)
                        g = DotaMath.lerp(g, 0.24f, m)
                        b = DotaMath.lerp(b, 0.18f, m)
                    }
                }

                // Riverbed sand & dark depth
                val rDist = MapLayout.riverDistance(wx, wz)
                if (rDist < 210f) {
                    val bankMask = 1f - DotaMath.smoothstep(130f, 205f, rDist)
                    val deepMask = 1f - DotaMath.smoothstep(60f, 145f, rDist)
                    r = DotaMath.lerp(r, 0.36f, bankMask * 0.72f)
                    g = DotaMath.lerp(g, 0.33f, bankMask * 0.72f)
                    b = DotaMath.lerp(b, 0.25f, bankMask * 0.72f)
                    r = DotaMath.lerp(r, 0.09f, deepMask)
                    g = DotaMath.lerp(g, 0.22f, deepMask)
                    b = DotaMath.lerp(b, 0.29f, deepMask)
                }

                mapPix.setColor(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
                mapPix.drawPixel(px, pz)
            }
        }
        val mapTex = Texture(mapPix, true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
        mapPix.dispose()
        loadedTextures.add(mapTex)
        return mapTex
    }

    private fun createTerrainModel() {
        terrainTex = buildTerrainTexture()

        val grid = 128
        val count = grid + 1
        val step = MapLayout.SIZE / grid
        val heights = FloatArray(count * count)
        for (gz in 0..grid) {
            for (gx in 0..grid) {
                heights[gz * count + gx] = terrainHeight(gx * step, gz * step)
            }
        }
        val verts = FloatArray(count * count * 8) // x,y,z, nx,ny,nz, u,v
        for (gz in 0..grid) {
            for (gx in 0..grid) {
                val wx = gx * step
                val wz = gz * step
                val h = heights[gz * count + gx]
                val hL = heights[gz * count + (gx - 1).coerceAtLeast(0)]
                val hR = heights[gz * count + (gx + 1).coerceAtMost(grid)]
                val hD = heights[(gz - 1).coerceAtLeast(0) * count + gx]
                val hU = heights[(gz + 1).coerceAtMost(grid) * count + gx]
                var nx = hL - hR
                var ny = 2f * step
                var nz = hD - hU
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
                nx /= len; ny /= len; nz /= len
                val base = (gz * count + gx) * 8
                verts[base + 0] = wx
                verts[base + 1] = h
                verts[base + 2] = wz
                verts[base + 3] = nx
                verts[base + 4] = ny
                verts[base + 5] = nz
                verts[base + 6] = wx / MapLayout.SIZE
                verts[base + 7] = wz / MapLayout.SIZE
            }
        }
        val indices = ShortArray(grid * grid * 6)
        var ptr = 0
        for (gz in 0 until grid) {
            for (gx in 0 until grid) {
                val a = (gz * count + gx).toShort()
                val b = ((gz + 1) * count + gx).toShort()
                val c = ((gz + 1) * count + gx + 1).toShort()
                val d = (gz * count + gx + 1).toShort()
                indices[ptr++] = a; indices[ptr++] = b; indices[ptr++] = c
                indices[ptr++] = a; indices[ptr++] = c; indices[ptr++] = d
            }
        }

        val mesh = Mesh(
            true, count * count, indices.size,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
        )
        mesh.setVertices(verts)
        mesh.setIndices(indices)

        val mb = ModelBuilder()
        mb.begin()
        val mat = Material(
            TextureAttribute.createDiffuse(terrainTex),
            ColorAttribute.createSpecular(0.08f, 0.08f, 0.10f, 1f),
            FloatAttribute.createShininess(6f)
        )
        mb.part("terrain", mesh, GL20.GL_TRIANGLES, mat)
        terrainModel = mb.end()
        loadedModels.add(terrainModel)
        terrainInstance = ModelInstance(terrainModel)
    }

    private fun buildWaterTexture(): Texture {
        val ws = 128
        val wpix = Pixmap(ws, ws, Pixmap.Format.RGBA8888)
        for (y in 0 until ws) {
            for (x in 0 until ws) {
                val u = x.toFloat() / ws * Math.PI.toFloat() * 2f
                val v = y.toFloat() / ws * Math.PI.toFloat() * 2f
                val wave = (sin(u * 3f + sin(v * 2f)) + cos(v * 4f - u * 2f)) * 0.5f
                val r = (0.08f + max(0f, wave) * 0.14f).coerceIn(0f, 1f)
                val g = (0.36f + max(0f, wave) * 0.24f).coerceIn(0f, 1f)
                val b = (0.56f + max(0f, wave) * 0.28f).coerceIn(0f, 1f)
                wpix.setColor(r, g, b, 0.78f)
                wpix.drawPixel(x, y)
            }
        }
        val tex = Texture(wpix, false).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        }
        wpix.dispose()
        loadedTextures.add(tex)
        return tex
    }

    private fun createWaterModel() {
        waterTex = buildWaterTexture()
        waterTexAttr = TextureAttribute.createDiffuse(waterTex).apply {
            scaleU = 38f
            scaleV = 38f
        }
        val waterMat = Material(
            waterTexAttr,
            ColorAttribute.createSpecular(0.70f, 0.88f, 1.0f, 1f),
            FloatAttribute.createShininess(32f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.78f),
            DepthTestAttribute(GL20.GL_LEQUAL, false)
        )
        val mb = ModelBuilder()
        waterModel = mb.createRect(
            0f, 0f, MapLayout.SIZE,
            MapLayout.SIZE, 0f, MapLayout.SIZE,
            MapLayout.SIZE, 0f, 0f,
            0f, 0f, 0f,
            0f, 1f, 0f,
            waterMat,
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
        )
        loadedModels.add(waterModel)
        waterInstance = ModelInstance(waterModel)
    }

    private fun createFogOfWarModel() {
        val fSize = world.fogOfWar.size()
        // Pixmap.Blending.None is critical: SourceOver accumulates alpha and makes cleared fog stay opaque black forever.
        fogPixmap = Pixmap(fSize, fSize, Pixmap.Format.RGBA8888).apply {
            blending = Pixmap.Blending.None
        }
        fogTexture = Texture(fogPixmap, false).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
        }
        loadedTextures.add(fogTexture)

        val grid = 128
        val count = grid + 1
        val step = MapLayout.SIZE / grid
        val verts = FloatArray(count * count * 8)
        for (gz in 0..grid) {
            for (gx in 0..grid) {
                val wx = gx * step
                val wz = gz * step
                val h = terrainHeight(wx, wz) + 3.2f
                val base = (gz * count + gx) * 8
                verts[base + 0] = wx
                verts[base + 1] = h
                verts[base + 2] = wz
                verts[base + 3] = 0f; verts[base + 4] = 1f; verts[base + 5] = 0f
                verts[base + 6] = wx / MapLayout.SIZE
                verts[base + 7] = wz / MapLayout.SIZE
            }
        }
        val indices = ShortArray(grid * grid * 6)
        var ptr = 0
        for (gz in 0 until grid) {
            for (gx in 0 until grid) {
                val a = (gz * count + gx).toShort()
                val b = ((gz + 1) * count + gx).toShort()
                val c = ((gz + 1) * count + gx + 1).toShort()
                val d = (gz * count + gx + 1).toShort()
                indices[ptr++] = a; indices[ptr++] = b; indices[ptr++] = c
                indices[ptr++] = a; indices[ptr++] = c; indices[ptr++] = d
            }
        }
        val mesh = Mesh(
            true, count * count, indices.size,
            VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)
        )
        mesh.setVertices(verts)
        mesh.setIndices(indices)

        val fogMat = Material(
            TextureAttribute.createDiffuse(fogTexture),
            ColorAttribute.createDiffuse(1f, 1f, 1f, 1f),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1f),
            DepthTestAttribute(GL20.GL_LEQUAL, false),
            IntAttribute.createCullFace(GL20.GL_NONE)
        )
        val mb = ModelBuilder()
        mb.begin()
        mb.part("fog_shroud", mesh, GL20.GL_TRIANGLES, fogMat)
        fogModel = mb.end()
        loadedModels.add(fogModel)
        fogInstance = ModelInstance(fogModel)
    }

    private fun createHelperVfxModels() {
        val mb = ModelBuilder()
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        fun registerMatModel(key: String, blended: Boolean, build: (ModelBuilder, Material) -> Model) {
            val mat = Material(ColorAttribute.createDiffuse(Color.WHITE)).apply {
                if (blended) {
                    set(BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.65f))
                    set(DepthTestAttribute(GL20.GL_LEQUAL, false))
                    set(IntAttribute.createCullFace(GL20.GL_NONE))
                }
            }
            val model = build(mb, mat)
            loadedModels.add(model)
            pools[key] = ModelPool(model, blended)
        }

        // Ground ring (unit radius 1.0, thin torus-like flat disc ring built via custom mesh)
        run {
            val segs = 32
            val verts = FloatArray((segs + 1) * 2 * 6)
            val idx = ShortArray(segs * 6)
            val innerR = 0.88f
            val outerR = 1.0f
            for (i in 0..segs) {
                val a = (i.toFloat() / segs) * Math.PI.toFloat() * 2f
                val ca = cos(a); val sa = sin(a)
                val b0 = (i * 2) * 6
                verts[b0 + 0] = ca * innerR; verts[b0 + 1] = 0f; verts[b0 + 2] = sa * innerR
                verts[b0 + 3] = 0f; verts[b0 + 4] = 1f; verts[b0 + 5] = 0f
                val b1 = (i * 2 + 1) * 6
                verts[b1 + 0] = ca * outerR; verts[b1 + 1] = 0f; verts[b1 + 2] = sa * outerR
                verts[b1 + 3] = 0f; verts[b1 + 4] = 1f; verts[b1 + 5] = 0f
            }
            for (i in 0 until segs) {
                val i0 = (i * 2).toShort()
                val o0 = (i * 2 + 1).toShort()
                val i1 = ((i + 1) * 2).toShort()
                val o1 = ((i + 1) * 2 + 1).toShort()
                idx[i * 6 + 0] = i0; idx[i * 6 + 1] = i1; idx[i * 6 + 2] = o1
                idx[i * 6 + 3] = i0; idx[i * 6 + 4] = o1; idx[i * 6 + 5] = o0
            }
            val rMesh = Mesh(true, (segs + 1) * 2, idx.size, VertexAttribute.Position(), VertexAttribute.Normal())
            rMesh.setVertices(verts); rMesh.setIndices(idx)
            val mat = Material(
                ColorAttribute.createDiffuse(Color.WHITE),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.7f),
                DepthTestAttribute(GL20.GL_LEQUAL, false),
                IntAttribute.createCullFace(GL20.GL_NONE)
            )
            mb.begin()
            mb.part("vfx_ring", rMesh, GL20.GL_TRIANGLES, mat)
            val rm = mb.end()
            loadedModels.add(rm)
            pools["vfx_ring"] = ModelPool(rm, isBlended = true)
        }

        registerMatModel("vfx_sphere_blend", blended = true) { b, m ->
            b.createSphere(1f, 1f, 1f, 10, 8, m, attrs)
        }
        registerMatModel("vfx_beam", blended = true) { b, m ->
            b.createCylinder(1f, 1f, 1f, 12, m, attrs)
        }
        registerMatModel("vfx_spark", blended = true) { b, m ->
            b.createBox(1f, 1f, 1f, m, attrs)
        }
        registerMatModel("vfx_rune_crystal", blended = false) { b, m ->
            b.createCone(1f, 1.4f, 1f, 6, m, attrs)
        }
    }

    private fun updateFogPixmap() {
        val n = world.fogOfWar.size()
        fogPixmap.blending = Pixmap.Blending.None
        for (gy in 0 until n) {
            for (gx in 0 until n) {
                val a = when (world.fogOfWar.getCellState(gx, gy)) {
                    2 -> 0.0f
                    1 -> 0.34f
                    else -> 0.60f
                }
                fogPixmap.setColor(0.04f, 0.06f, 0.11f, a)
                fogPixmap.drawPixel(gx, gy)
            }
        }
        fogTexture.draw(fogPixmap, 0, 0)
    }

    override fun resize(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        camera.viewportWidth = viewportWidth.toFloat()
        camera.viewportHeight = viewportHeight.toFloat()
        camera.update()
    }

    override fun resume() {
        super.resume()
        if (::terrainTex.isInitialized && !Gdx.gl.glIsTexture(terrainTex.textureObjectHandle)) {
            val oldTerrain = terrainTex
            terrainTex = buildTerrainTexture()
            terrainInstance.materials.firstOrNull()?.set(TextureAttribute.createDiffuse(terrainTex))
            loadedTextures.remove(oldTerrain)
            runCatching { oldTerrain.dispose() }

            val oldWater = waterTex
            waterTex = buildWaterTexture()
            waterTexAttr = TextureAttribute.createDiffuse(waterTex).apply {
                scaleU = 38f
                scaleV = 38f
            }
            waterInstance.materials.firstOrNull()?.set(waterTexAttr)
            loadedTextures.remove(oldWater)
            runCatching { oldWater.dispose() }

            val oldFog = fogTexture
            fogTexture = Texture(fogPixmap, false).apply {
                setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
                setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge)
            }
            loadedTextures.add(fogTexture)
            fogInstance.materials.firstOrNull()?.set(TextureAttribute.createDiffuse(fogTexture))
            loadedTextures.remove(oldFog)
            runCatching { oldFog.dispose() }
            updateFogPixmap()
        }
    }

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceIn(0.001f, 0.05f)

        val curGameTime: Float
        val curIsNight: Boolean
        val bars = ArrayList<ProjectedBar>(48)
        val texts = ArrayList<ProjectedText>(32)

        synchronized(world) {
            onFrameStep(dt)

            // Consume pending camera shake from crits / ultimates / tower destruction
            if (world.pendingCameraShake > 0f) {
                cameraController.addShake(world.pendingCameraShake)
                world.pendingCameraShake = 0f
            }

            val aimRange = if (aimingAbilityIdx in 0..3) {
                world.playerHero.def.abilities[aimingAbilityIdx].castRange
            } else if (aimingItemSlotIdx >= 0) 850f else 0f

            cameraController.update(
                dt = dt,
                playerHero = world.playerHero,
                aimActive = (aimingAbilityIdx in 0..3 || aimingItemSlotIdx >= 0),
                aimWorldPos = aimWorldPos,
                aimRange = aimRange,
                lastSkillshotPos = world.lastSkillshotAimPos,
                lastSkillshotHoldTimer = world.lastSkillshotHoldTimer
            )

            curGameTime = world.gameTime
            curIsNight = world.isNight

            fogAccum += dt
            if (fogAccum >= 0.10f) {
                fogAccum = 0f
                updateFogPixmap()
            }

            // Update camera from CameraPose
            val groundY = terrainHeight(cameraController.cameraPos.x, cameraController.cameraPos.y)
            val pose = cameraController.computePose(groundY)
            camera.position.set(pose.eyeX, pose.eyeY, pose.eyeZ)
            camera.lookAt(pose.lookX, pose.lookY, pose.lookZ)
            camera.up.set(0f, 1f, 0f)
            camera.update()

            synchronized(camLock) {
                snapInvProjView.set(camera.invProjectionView)
                snapCamPos.set(camera.position)
                snapGroundY = groundY
                snapWidth = viewportWidth.toFloat()
                snapHeight = viewportHeight.toFloat()
            }

            // Animate river water UVs
            waterTexAttr.offsetU = (curGameTime * 0.12f) % 1f
            waterTexAttr.offsetV = (-curGameTime * 0.08f) % 1f
            waterInstance.transform.setToTranslation(0f, -10.2f + sin(curGameTime * 1.9f) * 1.2f, 0f)

            // Day / Night Dota 2 lighting mood
            val ambAttr = environment.get(ColorAttribute.AmbientLight) as ColorAttribute
            val fogAttr = environment.get(ColorAttribute.Fog) as ColorAttribute
            if (curIsNight) {
                ambAttr.color.set(0.28f, 0.34f, 0.50f, 1f)
                fogAttr.color.set(0.03f, 0.05f, 0.10f, 1f)
                shadowLight.set(0.60f, 0.72f, 0.96f, 0.35f, -0.84f, -0.42f)
                rimLight.set(0.24f, 0.32f, 0.54f, -0.45f, -0.35f, 0.80f)
            } else {
                ambAttr.color.set(0.46f, 0.45f, 0.44f, 1f)
                fogAttr.color.set(0.06f, 0.08f, 0.12f, 1f)
                shadowLight.set(1.0f, 0.92f, 0.82f, -0.46f, -0.82f, 0.34f)
                rimLight.set(0.32f, 0.42f, 0.62f, 0.45f, -0.38f, -0.80f)
            }

            // Dynamic spell flash point light
            val activeZone = world.zones.lastOrNull()
            if (activeZone != null) {
                val (zr, zg, zb) = rgbFloats(activeZone.color)
                spellFlashLight.set(zr, zg, zb, activeZone.pos.x, terrainHeight(activeZone.pos.x, activeZone.pos.y) + 85f, activeZone.pos.y, 42000f)
            } else {
                spellFlashLight.intensity = 0f
            }

            shadowCenter.set(pose.lookX, groundY, pose.lookZ)
            prepareFrameInstances(curGameTime, bars, texts)
        }

        projectedBars = bars
        projectedTexts = texts

        // PASS 1: DirectionalShadowLight shadow map
        shadowLight.begin(shadowCenter, zeroVec)
        shadowBatch.begin(shadowLight.camera)
        for (i in 0 until shadowQueue.size) shadowBatch.render(shadowQueue[i])
        shadowBatch.end()
        shadowLight.end()

        // PASS 2: Full Lit 3D Scene + Real Shadows + Water + FOW Shroud + VFX
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0.04f, 0.06f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        modelBatch.begin(camera)
        modelBatch.render(terrainInstance, environment)
        for (i in 0 until opaqueQueue.size) modelBatch.render(opaqueQueue[i], environment)
        modelBatch.flush()
        modelBatch.render(waterInstance, environment)
        modelBatch.flush()
        modelBatch.render(fogInstance) // Unlit volumetric dark-fantasy Fog of War shroud
        modelBatch.flush()
        for (i in 0 until blendQueue.size) modelBatch.render(blendQueue[i])
        modelBatch.end()
    }

    private fun prepareFrameInstances(
        curTime: Float,
        bars: MutableList<ProjectedBar>,
        texts: MutableList<ProjectedText>
    ) {
        shadowQueue.clear()
        opaqueQueue.clear()
        blendQueue.clear()
        for (pool in pools.values) pool.reset()

        val camX = cameraController.cameraPos.x
        val camZ = cameraController.cameraPos.y
        val cullX = 1480f * (cameraController.baseZoomDist / 900f).coerceAtLeast(1f)
        val cullZ = 1280f * (cameraController.baseZoomDist / 900f).coerceAtLeast(1f)

        // 1. Fountains
        drawFountain3D(MapLayout.radiantFountain, isRadiant = true, curTime, camX, camZ, cullX, cullZ)
        drawFountain3D(MapLayout.direFountain, isRadiant = false, curTime, camX, camZ, cullX, cullZ)

        // 2. Trees (with Fog-of-War darkening tint!)
        for (t in world.trees) {
            if (!t.alive) continue
            if (abs(t.pos.x - camX) > cullX || abs(t.pos.y - camZ) > cullZ) continue
            val isDire = t.pos.x > t.pos.y
            val key = if (isDire) "tree_dire" else "tree_radiant"
            val h = terrainHeight(t.pos.x, t.pos.y)
            val vScale = 0.88f + (t.id % 6) * 0.065f
            val rot = (t.id * 53f) % 360f
            val fogTone = fogTint(t.pos.x, t.pos.y)
            val inst = pools[key]!!.acquire(fogTone, fogTone, fogTone)
            inst.transform.setToTranslation(t.pos.x, h - 1.5f, t.pos.y)
            inst.transform.rotate(Vector3.Y, rot)
            inst.transform.scale(vScale, vScale, vScale)
            shadowQueue.add(inst)
            opaqueQueue.add(inst)
        }

        // Twin Gates
        drawGate3D(MapLayout.twinGateTopLeft, curTime, camX, camZ, cullX, cullZ)
        drawGate3D(MapLayout.twinGateBotRight, curTime, camX, camZ, cullX, cullZ)

        // 3. Towers, Barracks & Ancients
        for (t in world.towers) {
            if (!t.alive) continue
            if (abs(t.pos.x - camX) > cullX + 120f || abs(t.pos.y - camZ) > cullZ + 120f) continue
            drawTower3D(t, curTime, bars)
        }
        for (b in world.barracks) {
            if (!b.alive) continue
            if (abs(b.pos.x - camX) > cullX + 120f || abs(b.pos.y - camZ) > cullZ + 120f) continue
            drawBarracks3D(b, bars)
        }
        for (anc in listOf(world.radiantAncient, world.direAncient)) {
            if (!anc.alive) continue
            if (abs(anc.pos.x - camX) > cullX + 220f || abs(anc.pos.y - camZ) > cullZ + 220f) continue
            drawAncient3D(anc, curTime, bars)
        }

        // 4. Wards, Runes, Dropped Items
        for (w in world.wards) {
            val vis = w.team == world.playerTeam || world.fogOfWar.hasTrueSight(w.pos.x, w.pos.y)
            if (!vis) continue
            val h = terrainHeight(w.pos.x, w.pos.y)
            val (r, g, b) = if (w.isSentry) Triple(0.28f, 0.80f, 1.0f) else Triple(1.0f, 0.82f, 0.14f)
            val inst = pools["ward_sentinel"]!!.acquire(r, g, b, emissive = 0.35f)
            inst.transform.setToTranslation(w.pos.x, h, w.pos.y).rotate(Vector3.Y, curTime * 75f)
            shadowQueue.add(inst)
            opaqueQueue.add(inst)
        }

        for (r in world.runes) {
            if (!r.alive) continue
            val h = terrainHeight(r.pos.x, r.pos.y)
            val (cr, cg, cb) = rgbFloats(r.type.color)
            val bob = sin(curTime * 3.8f) * 6f
            val inst = pools["vfx_rune_crystal"]!!.acquire(cr, cg, cb, emissive = 0.55f)
            inst.transform.setToTranslation(r.pos.x, h + 26f + bob, r.pos.y)
                .rotate(Vector3.Y, curTime * 90f)
                .scale(24f, 26f, 24f)
            opaqueQueue.add(inst)
            addRing(r.pos.x, h + 4f, r.pos.y, 44f, cr, cg, cb, 0.55f)
        }

        for (d in world.droppedItems) {
            val h = terrainHeight(d.pos.x, d.pos.y)
            val bob = sin(curTime * 4.2f) * 5f
            val inst = pools["vfx_rune_crystal"]!!.acquire(1f, 0.84f, 0.08f, emissive = 0.65f)
            inst.transform.setToTranslation(d.pos.x, h + 24f + bob, d.pos.y)
                .rotate(Vector3.Y, curTime * 115f)
                .scale(26f, 28f, 26f)
            opaqueQueue.add(inst)
            addRing(d.pos.x, h + 4f, d.pos.y, 46f, 1f, 0.84f, 0.08f, 0.65f)
        }

        // 5. Creeps, Neutrals, Roshan, Tormentors, Couriers
        val lockId = world.playerHero.attackTargetId
        for (c in world.creeps) {
            if (!c.alive) continue
            if (abs(c.pos.x - camX) > cullX || abs(c.pos.y - camZ) > cullZ) continue
            if (c.team != world.playerTeam && !world.fogOfWar.isVisible(c.pos.x, c.pos.y)) continue
            drawCreep3D(c, c.id == lockId, bars)
        }

        for (n in world.neutrals) {
            if (!n.alive || !world.fogOfWar.isVisible(n.pos.x, n.pos.y)) continue
            drawNeutral3D(n, n.id == lockId, bars)
        }

        if (world.roshan.alive && world.fogOfWar.isVisible(world.roshan.pos.x, world.roshan.pos.y)) {
            drawRoshan3D(world.roshan, world.roshan.id == lockId, curTime, bars)
        }

        for (tor in listOf(world.radiantTormentor, world.direTormentor)) {
            if (!tor.alive || !world.fogOfWar.isVisible(tor.pos.x, tor.pos.y)) continue
            val h = terrainHeight(tor.pos.x, tor.pos.y)
            val bob = sin(curTime * 2.8f) * 6f
            val inst = pools["vfx_rune_crystal"]!!.acquire(0.16f, 0.85f, 0.96f, emissive = 0.50f)
            inst.transform.setToTranslation(tor.pos.x, h + 44f + bob, tor.pos.y)
                .rotate(Vector3.Y, curTime * 55f)
                .scale(48f, 56f, 48f)
            shadowQueue.add(inst)
            opaqueQueue.add(inst)
            addRing(tor.pos.x, h + 5f, tor.pos.y, 58f, 0.25f, 0.88f, 1f, 0.6f)
            projectToScreen(tor.pos.x, h + 102f, tor.pos.y)?.let { (sx, sy) ->
                bars.add(ProjectedBar(sx, sy, "TORMENTOR", tor.hp, tor.maxHp, 0f, 0f, 0xFF38BDF8.toInt(), false))
            }
        }

        for (c in world.couriers) {
            if (!c.alive || (!c.delivering && !c.returning)) continue
            val h = terrainHeight(c.pos.x, c.pos.y)
            val bob = sin(curTime * 10f) * 5f
            val (cr, cg, cb) = if (c.team == Team.RADIANT) Triple(0.28f, 0.88f, 0.56f) else Triple(0.92f, 0.30f, 0.26f)
            val inst = pools["vfx_rune_crystal"]!!.acquire(cr, cg, cb, emissive = 0.4f)
            inst.transform.setToTranslation(c.pos.x, h + 32f + bob, c.pos.y)
                .rotate(Vector3.X, 90f)
                .scale(22f, 24f, 16f)
            shadowQueue.add(inst)
            opaqueQueue.add(inst)
        }

        // 6. Articulated .OBJ Heroes + 3-Hit Combo Swings + 3D Weapon Ribbon Trails
        for (hero in world.heroes) {
            if (!hero.alive) continue
            val isAlly = hero.team == world.playerTeam
            if (!isAlly) {
                if (!world.fogOfWar.isVisible(hero.pos.x, hero.pos.y)) continue
                if (hero.isInvisible() && !world.fogOfWar.hasTrueSight(hero.pos.x, hero.pos.y)) continue
            }
            drawHero3D(hero, isAlly, hero.id == lockId, curTime)
            val h = terrainHeight(hero.pos.x, hero.pos.y)
            projectToScreen(hero.pos.x, h + 122f, hero.pos.y)?.let { (sx, sy) ->
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

        // 7. 3D AoE Spell Zones
        drawZones3D(curTime)

        // 8. 3D Projectiles (.obj arrows, daggers, hooks, spears, orbs) + Trails
        drawProjectiles3D(curTime)

        // 9. 3D Impact Spark Bursts & Shockwave Rings
        drawParticles3D()

        // 10. Aim Indicators
        drawAimIndicators3D()

        // 11. Projected Floating Damage / Crit Numbers
        for (ft in world.floatingTexts) {
            val h = terrainHeight(ft.pos.x, ft.pos.y)
            projectToScreen(ft.pos.x, h + 84f + (1.2f - ft.life) * 52f, ft.pos.y)?.let { (sx, sy) ->
                texts.add(ProjectedText(sx, sy, ft.text, ft.color, ft.scale))
            }
        }
    }

    private fun drawFountain3D(pos: Vec2, isRadiant: Boolean, curTime: Float, camX: Float, camZ: Float, cx: Float, cz: Float) {
        if (abs(pos.x - camX) > cx + 200f || abs(pos.y - camZ) > cz + 200f) return
        val h = terrainHeight(pos.x, pos.y)
        val (r, g, b) = if (isRadiant) Triple(0.18f, 0.92f, 0.65f) else Triple(0.95f, 0.24f, 0.18f)
        addRing(pos.x, h + 4f, pos.y, 170f, r, g, b, 0.45f)
        val crystal = pools["vfx_rune_crystal"]!!.acquire(r, g, b, emissive = 0.6f)
        crystal.transform.setToTranslation(pos.x, h + 44f + sin(curTime * 2.5f) * 5f, pos.y)
            .rotate(Vector3.Y, curTime * 42f)
            .scale(34f, 44f, 34f)
        opaqueQueue.add(crystal)
    }

    private fun drawGate3D(pos: Vec2, curTime: Float, camX: Float, camZ: Float, cx: Float, cz: Float) {
        if (abs(pos.x - camX) > cx + 100f || abs(pos.y - camZ) > cz + 100f) return
        val h = terrainHeight(pos.x, pos.y)
        addRing(pos.x, h + 4f, pos.y, 56f, 0.68f, 0.34f, 0.98f, 0.65f)
        val orb = pools["proj_orb"]!!.acquire(0.68f, 0.34f, 0.98f, emissive = 0.55f)
        orb.transform.setToTranslation(pos.x, h + 38f + sin(curTime * 3.5f) * 4f, pos.y).scale(16f, 22f, 16f)
        opaqueQueue.add(orb)
    }

    private fun drawTower3D(t: TowerEntity, curTime: Float, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(t.pos.x, t.pos.y)
        val isRad = t.team == Team.RADIANT
        val key = if (isRad) "tower_radiant" else "tower_dire"
        val fogTone = fogTint(t.pos.x, t.pos.y)
        val shotGlow = if (t.attackCooldown > 0.72f) 0.45f else 0.12f
        val inst = pools[key]!!.acquire(fogTone, fogTone, fogTone, emissive = shotGlow)
        inst.transform.setToTranslation(t.pos.x, h, t.pos.y)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

        if (t.pos.distTo(world.playerHero.pos) < t.attackRange + 260f) {
            val (r, g, b) = if (isRad) Triple(0.18f, 0.84f, 0.48f) else Triple(0.92f, 0.22f, 0.18f)
            addRing(t.pos.x, h + 3.5f, t.pos.y, t.attackRange, r, g, b, 0.32f)
        }
        if (t.fortifyTimer > 0f) {
            val dome = pools["vfx_sphere_blend"]!!.acquire(0.28f, 0.82f, 1.0f, alpha = 0.36f, emissive = 0.4f)
            dome.transform.setToTranslation(t.pos.x, h + 65f, t.pos.y).scale(136f, 155f, 136f)
            blendQueue.add(dome)
        }

        projectToScreen(t.pos.x, h + 170f, t.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "T${t.blueprint.tier}", t.hp, t.maxHp, 0f, 0f,
                if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false, notchStep = 500f))
        }
    }

    private fun drawBarracks3D(b: BarracksEntity, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(b.pos.x, b.pos.y)
        val isRad = b.team == Team.RADIANT
        val key = if (isRad) "barracks_radiant" else "barracks_dire"
        val fogTone = fogTint(b.pos.x, b.pos.y)
        val inst = pools[key]!!.acquire(fogTone, fogTone, fogTone)
        inst.transform.setToTranslation(b.pos.x, h, b.pos.y)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

        projectToScreen(b.pos.x, h + 98f, b.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", b.hp, b.maxHp, 0f, 0f,
                if (isRad) 0xFF27AE60.toInt() else 0xFFC0392B.toInt(), false))
        }
    }

    private fun drawAncient3D(anc: AncientEntity, curTime: Float, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(anc.pos.x, anc.pos.y)
        val isRad = anc.team == Team.RADIANT
        val key = if (isRad) "ancient_radiant" else "ancient_dire"
        val fogTone = fogTint(anc.pos.x, anc.pos.y)
        val pulse = 0.16f + 0.08f * sin(curTime * 2.4f)
        val inst = pools[key]!!.acquire(fogTone, fogTone, fogTone, emissive = pulse)
        inst.transform.setToTranslation(anc.pos.x, h, anc.pos.y).rotate(Vector3.Y, curTime * 6f)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

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
        val (r, g, b) = if (isRad) Triple(0.24f, 0.85f, 0.48f) else Triple(0.92f, 0.28f, 0.24f)
        val angleDeg = 90f - Math.toDegrees(c.facingAngle.toDouble()).toFloat()

        if (isLocked) addRing(c.pos.x, h + 3f, c.pos.y, 26f * sc, 0.98f, 0.22f, 0.22f, 0.80f)

        val key = when (c.type) {
            CreepType.SIEGE -> "creep_siege"
            CreepType.RANGED -> "creep_ranged"
            else -> "creep_melee"
        }
        val bob = abs(sin(c.walkCycle)) * 2.2f * sc
        val atkPitch = if (c.attackAnimTimer > 0f && c.attackAnimDuration > 0f) {
            val p = 1f - (c.attackAnimTimer / c.attackAnimDuration).coerceIn(0f, 1f)
            sin(p * PI.toFloat()) * 16f
        } else 0f

        val inst = pools[key]!!.acquire(r, g, b)
        inst.transform.setToTranslation(c.pos.x, h + bob, c.pos.y)
            .rotate(Vector3.Y, angleDeg)
            .rotate(Vector3.X, atkPitch)
            .scale(sc, sc, sc)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

        projectToScreen(c.pos.x, h + 58f * sc, c.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", c.hp, c.maxHp, 0f, 0f,
                if (isRad) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(), false))
        }
    }

    private fun drawNeutral3D(n: NeutralCreepEntity, isLocked: Boolean, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(n.pos.x, n.pos.y)
        val sc = 0.88f + n.tier * 0.16f
        val angleDeg = 90f - Math.toDegrees(n.facingAngle.toDouble()).toFloat()
        if (isLocked) addRing(n.pos.x, h + 3f, n.pos.y, 30f * sc, 0.98f, 0.22f, 0.22f, 0.80f)

        val (br, bg, bb) = when (n.tier) {
            4 -> Triple(0.85f, 0.68f, 0.44f)
            3 -> Triple(0.75f, 0.52f, 0.72f)
            else -> Triple(0.88f, 0.72f, 0.52f)
        }
        val inst = pools["creep_neutral"]!!.acquire(br, bg, bb)
        inst.transform.setToTranslation(n.pos.x, h + abs(sin(n.walkCycle)) * 2.5f, n.pos.y)
            .rotate(Vector3.Y, angleDeg)
            .scale(sc, sc, sc)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

        projectToScreen(n.pos.x, h + 64f * sc, n.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "", n.hp, n.maxHp, 0f, 0f, 0xFFF39C12.toInt(), false))
        }
    }

    private fun drawRoshan3D(rsh: RoshanEntity, isLocked: Boolean, curTime: Float, bars: MutableList<ProjectedBar>) {
        val h = terrainHeight(rsh.pos.x, rsh.pos.y)
        val angleDeg = 90f - Math.toDegrees(rsh.facingAngle.toDouble()).toFloat()
        if (isLocked) addRing(rsh.pos.x, h + 4f, rsh.pos.y, 58f, 0.98f, 0.22f, 0.22f, 0.82f)

        val breath = sin(curTime * 3.5f) * 2.2f
        val atkPitch = if (rsh.attackAnimTimer > 0f && rsh.attackAnimDuration > 0f) {
            val p = 1f - (rsh.attackAnimTimer / rsh.attackAnimDuration).coerceIn(0f, 1f)
            sin(p * PI.toFloat()) * 18f
        } else 0f
        val inst = pools["roshan"]!!.acquire(1f, 0.95f, 0.95f, emissive = 0.14f)
        inst.transform.setToTranslation(rsh.pos.x, h + breath.coerceAtLeast(0f), rsh.pos.y)
            .rotate(Vector3.Y, angleDeg)
            .rotate(Vector3.X, atkPitch)
        shadowQueue.add(inst)
        opaqueQueue.add(inst)

        projectToScreen(rsh.pos.x, h + 136f, rsh.pos.y)?.let { (sx, sy) ->
            bars.add(ProjectedBar(sx, sy, "ROSHAN", rsh.hp, rsh.maxHp, 0f, 0f, 0xFFFF9800.toInt(), false, notchStep = 1000f))
        }
    }

    private fun drawHero3D(hero: HeroEntity, isAlly: Boolean, isTargetLocked: Boolean, curTime: Float) {
        val h = terrainHeight(hero.pos.x, hero.pos.y)
        val (pr, pg, pb) = rgbFloats(hero.def.primaryColor)
        val (ar, ag, ab) = rgbFloats(hero.def.accentColor)
        val angleDeg = 90f - Math.toDegrees(hero.facingAngle.toDouble()).toFloat()
        val bkbScale = if (hero.hasStatus(StatusType.MAGIC_IMMUNE)) 1.24f else 1.0f
        val sc = 1.10f * bkbScale

        val swinging = hero.attackAnimTimer > 0f && hero.attackAnimDuration > 0f
        val atkProg = if (swinging) 1f - (hero.attackAnimTimer / hero.attackAnimDuration).coerceIn(0f, 1f) else 0f
        val combo = hero.attackComboStep // 1 -> Hit 1, 2 -> Hit 2, 0 -> Hit 3 finisher
        val casting = hero.castAnimTimer > 0f

        // Dynamic 3-hit combo lunge & torso twist
        val lungeMag = when {
            !swinging -> 0f
            hero.def.isRanged -> -sin(atkProg * PI.toFloat()) * 5f // crisp recoil kick
            combo == 0 -> sin(atkProg * PI.toFloat()) * 22f // Hit 3 heavy finisher lunge
            else -> sin(atkProg * PI.toFloat()) * 14f
        }
        val hx = hero.pos.x + cos(hero.facingAngle) * lungeMag
        val hz = hero.pos.y + sin(hero.facingAngle) * lungeMag

        val torsoTwist = when {
            !swinging -> 0f
            combo == 1 -> -34f + atkProg * 68f
            combo == 2 -> 34f - atkProg * 68f
            else -> -12f + atkProg * 24f
        }
        val torsoLean = when {
            !swinging -> if (casting) -12f else 0f
            combo == 0 && !hero.def.isRanged -> -14f + sin(atkProg * PI.toFloat()) * 32f
            else -> sin(atkProg * PI.toFloat()) * 10f
        }

        val moving = abs(hero.moveIntent.x) > 0.05f || abs(hero.moveIntent.y) > 0.05f || hero.tapMoveTarget != null
        val walkSwing = if (moving) sin(hero.walkCycle) * 32f else 0f
        val idleBob = if (!moving) sin(curTime * 3.5f + hero.pos.x * 0.01f) * 1.4f else abs(sin(hero.walkCycle)) * 2.2f

        val cosA = cos(hero.facingAngle)
        val sinA = sin(hero.facingAngle)
        val rx = -sinA
        val rz = cosA
        val invis = hero.isInvisible()
        val invSuffix = if (invis) "_inv" else ""
        val alpha = if (invis) 0.42f else 1.0f

        // Ground selection rings & attack target indicator
        if (hero.isPlayer) {
            addRing(hero.pos.x, h + 4.8f, hero.pos.y, 44f, 0.18f, 0.94f, 0.48f, 0.78f)
            if (showAttackRangeRing || hero.attackTargetId != null) {
                addRing(hero.pos.x, h + 4.2f, hero.pos.y, hero.cachedAttackRange, 0.25f, 0.85f, 0.98f, 0.24f)
            }
        } else if (isTargetLocked) {
            val pulse = 1.0f + sin(curTime * 9f) * 0.12f
            addRing(hero.pos.x, h + 5.2f, hero.pos.y, 48f * pulse, 0.98f, 0.20f, 0.20f, 0.88f)
        } else {
            addRing(hero.pos.x, h + 4.2f, hero.pos.y, 38f,
                if (isAlly) 0.2f else 0.92f, if (isAlly) 0.62f else 0.20f, if (isAlly) 0.95f else 0.20f, 0.55f)
        }

        fun submitPart(key: String, inst: ModelInstance) {
            if (invis) blendQueue.add(inst)
            else {
                shadowQueue.add(inst)
                opaqueQueue.add(inst)
            }
        }

        // Articulated Legs (origin at hip y=0 down to y=-1.0)
        val hipY = h + (26f + idleBob * 0.4f) * sc
        val legSpread = 9.0f * sc
        for (side in intArrayOf(-1, 1)) {
            val leg = pools["hero_limb$invSuffix"]!!.acquire(0.24f, 0.26f, 0.34f, alpha)
            leg.transform.setToTranslation(hx + rx * legSpread * side, hipY, hz + rz * legSpread * side)
                .rotate(Vector3.Y, angleDeg)
                .rotate(Vector3.X, walkSwing * side)
                .scale(10f * sc, 26f * sc, 10f * sc)
            submitPart("hero_limb$invSuffix", leg)
        }

        // Torso (.obj hero silhouette)
        val torsoKey = when (hero.def.id) {
            "axe", "pudge", "earthshaker" -> "hero_torso_brute$invSuffix"
            "cm", "invoker", "zeus", "void_spirit", "chronos" -> "hero_torso_mage$invSuffix"
            else -> "hero_torso_agile$invSuffix"
        }
        val bodyY = h + (45f + idleBob) * sc
        val torso = pools[torsoKey]!!.acquire(pr, pg, pb, alpha)
        torso.transform.setToTranslation(hx, bodyY, hz)
            .rotate(Vector3.Y, angleDeg + torsoTwist)
            .rotate(Vector3.X, torsoLean)
            .scale(sc, sc, sc)
        submitPart(torsoKey, torso)

        // Flowing Cape (+X rotation pitches bottom -Y backward toward -Z)
        val capeFlutter = sin(curTime * 6f + hero.walkCycle) * 7f + (if (moving) 16f else 4f)
        val cape = pools["hero_cape$invSuffix"]!!.acquire(pr * 0.72f, pg * 0.72f, pb * 0.72f, alpha)
        cape.transform.setToTranslation(hx - cosA * 10f * sc, bodyY + 13f * sc, hz - sinA * 10f * sc)
            .rotate(Vector3.Y, angleDeg + torsoTwist * 0.6f)
            .rotate(Vector3.X, capeFlutter)
            .scale(sc, sc, sc)
        submitPart("hero_cape$invSuffix", cape)

        // Head (.obj distinct helmet/hood/crown)
        val headKey = when (hero.def.id) {
            "juggernaut" -> "hero_head_samurai$invSuffix"
            "axe", "earthshaker" -> "hero_head_horned$invSuffix"
            "pa", "sniper" -> "hero_head_hood$invSuffix"
            "pudge" -> "hero_head_butcher$invSuffix"
            else -> "hero_head_mage$invSuffix"
        }
        val headY = h + (71f + idleBob) * sc
        val head = pools[headKey]!!.acquire(
            ar * 0.6f + 0.4f, ag * 0.6f + 0.4f, ab * 0.6f + 0.4f, alpha
        )
        head.transform.setToTranslation(hx, headY, hz)
            .rotate(Vector3.Y, angleDeg + torsoTwist * 0.45f)
            .rotate(Vector3.X, torsoLean * 0.5f)
            .scale(sc, sc, sc)
        submitPart(headKey, head)

        // Left & Right Arms (swing opposite to legs) + 3-Hit Combo Weapon Swing!
        val shoulderY = bodyY + 11f * sc
        val shoulderSpread = when (hero.def.id) {
            "axe", "pudge", "earthshaker" -> 19f * sc
            else -> 14.5f * sc
        }
        val leftArmPitch = if (casting) -65f else walkSwing
        val leftArm = pools["hero_limb$invSuffix"]!!.acquire(pr * 0.85f, pg * 0.85f, pb * 0.85f, alpha)
        leftArm.transform.setToTranslation(hx - rx * shoulderSpread, shoulderY, hz - rz * shoulderSpread)
            .rotate(Vector3.Y, angleDeg + torsoTwist)
            .rotate(Vector3.X, leftArmPitch)
            .scale(8.5f * sc, 23f * sc, 8.5f * sc)
        submitPart("hero_limb$invSuffix", leftArm)

        // Compute 3-hit combo right arm + weapon Euler swing!
        val armYaw: Float
        val armPitch: Float
        val armRoll: Float
        when {
            casting -> {
                armYaw = 0f; armPitch = -78f; armRoll = 0f
            }
            !swinging -> {
                armYaw = 16f; armPitch = -42f - walkSwing * 0.7f; armRoll = 0f
            }
            hero.def.isRanged -> {
                armYaw = if (combo == 2) -14f else 14f
                armPitch = -75f - sin(atkProg * PI.toFloat()) * 18f
                armRoll = 0f
            }
            combo == 1 -> {
                // Hit 1: Forehand horizontal sweep (right -> left)
                armYaw = -72f + atkProg * 144f
                armPitch = -82f
                armRoll = -80f
            }
            combo == 2 -> {
                // Hit 2: Backhand diagonal upward cut (left -> right)
                armYaw = 74f - atkProg * 148f
                armPitch = -74f
                armRoll = 55f
            }
            else -> {
                // Hit 3: Heavy overhead finisher slam!
                armYaw = 6f
                armPitch = -155f + atkProg * 130f
                armRoll = -10f
            }
        }

        val rightShoulderX = hx + rx * shoulderSpread
        val rightShoulderZ = hz + rz * shoulderSpread
        val rightArm = pools["hero_limb$invSuffix"]!!.acquire(pr * 0.9f, pg * 0.9f, pb * 0.9f, alpha)
        rightArm.transform.setToTranslation(rightShoulderX, shoulderY, rightShoulderZ)
            .rotate(Vector3.Y, angleDeg + torsoTwist + armYaw)
            .rotate(Vector3.X, armPitch)
            .rotate(Vector3.Z, armRoll)
            .scale(8.5f * sc, 23f * sc, 8.5f * sc)
        submitPart("hero_limb$invSuffix", rightArm)

        // Hero-specific .obj Weapon anchored at right hand!
        val weaponKey = when (hero.def.id) {
            "juggernaut" -> "weapon_katana"
            "axe" -> "weapon_axe"
            "pa", "antimage" -> "weapon_glaive"
            "pudge" -> "weapon_cleaver"
            "earthshaker" -> "weapon_totem"
            "cm", "invoker" -> "weapon_staff"
            "sniper" -> "weapon_rifle"
            "zeus" -> "weapon_bolt"
            else -> "weapon_halberd"
        }
        val wEmissive = if (hero.enchantTotemBuff || hero.lastHitWasCrit) 0.45f else 0.12f
        val weapon = pools[weaponKey]!!.acquire(ar, ag, ab, alpha, emissive = wEmissive)
        weapon.transform.setToTranslation(rightShoulderX, shoulderY, rightShoulderZ)
            .rotate(Vector3.Y, angleDeg + torsoTwist + armYaw)
            .rotate(Vector3.X, armPitch)
            .rotate(Vector3.Z, armRoll)
            .translate(0f, -21f * sc, 2f * sc)
            .rotate(Vector3.X, 90f) // Extend forward from fist!
            .scale(sc, sc, sc)
        if (!invis) {
            shadowQueue.add(weapon)
            opaqueQueue.add(weapon)
        }

        // Off-hand dual weapon for PA / Anti-Mage / Pudge Hook
        if (hero.def.id == "pa" || hero.def.id == "antimage" || hero.def.id == "pudge") {
            val offKey = if (hero.def.id == "pudge") "proj_hook" else "weapon_glaive"
            val off = pools[offKey]!!.acquire(ar, ag, ab, alpha, emissive = 0.14f)
            off.transform.setToTranslation(hx - rx * shoulderSpread, shoulderY, hz - rz * shoulderSpread)
                .rotate(Vector3.Y, angleDeg + torsoTwist)
                .rotate(Vector3.X, leftArmPitch)
                .translate(0f, -20f * sc, 2f * sc)
                .rotate(Vector3.X, 90f)
                .scale(sc, sc, sc)
            if (!invis) opaqueQueue.add(off)
        }

        // 3D Weapon Ribbon Trail during melee combo swings!
        if (swinging && !hero.def.isRanged && atkProg in 0.05f..0.95f) {
            val isFinisher = combo == 0
            val ribAlpha = sin(atkProg * PI.toFloat()) * (if (hero.lastHitWasCrit || isFinisher) 0.92f else 0.78f)
            val ribR = if (hero.lastHitWasCrit) 1.0f else ar * 0.55f + 0.45f
            val ribG = if (hero.lastHitWasCrit) 0.24f else ag * 0.55f + 0.40f
            val ribB = if (hero.lastHitWasCrit) 0.16f else ab * 0.35f + 0.22f

            val ribScale = (if (isFinisher) 78f else 66f) * sc
            val ribbon = pools["weapon_slash_ribbon"]!!.acquire(ribR, ribG, ribB, alpha = ribAlpha, emissive = 0.75f)
            when (combo) {
                1 -> ribbon.transform.setToTranslation(hx, h + 44f * sc, hz)
                    .rotate(Vector3.Y, angleDeg + (-42f + atkProg * 84f))
                    .rotate(Vector3.Z, 6f)
                    .scale(ribScale, ribScale * 0.35f, ribScale)
                2 -> ribbon.transform.setToTranslation(hx, h + 44f * sc, hz)
                    .rotate(Vector3.Y, angleDeg + (42f - atkProg * 84f))
                    .rotate(Vector3.Z, -32f)
                    .scale(ribScale, ribScale * 0.35f, ribScale)
                else -> ribbon.transform.setToTranslation(hx, h + 48f * sc, hz)
                    .rotate(Vector3.Y, angleDeg)
                    .rotate(Vector3.Z, 90f)
                    .rotate(Vector3.Y, 52f - atkProg * 104f)
                    .scale(ribScale, ribScale * 0.40f, ribScale)
            }
            blendQueue.add(ribbon)
        }

        // Hero-specific active auras & buffs
        if (hero.def.id == "invoker") {
            val cols = arrayOf(Triple(0.22f, 0.74f, 0.98f), Triple(0.68f, 0.34f, 0.98f), Triple(1.0f, 0.60f, 0.08f))
            for (i in 0..2) {
                val a = curTime * 3.4f + i * 2.094f
                val orb = pools["proj_orb"]!!.acquire(cols[i].first, cols[i].second, cols[i].third, emissive = 0.65f)
                orb.transform.setToTranslation(hx + cos(a) * 38f, h + 76f, hz + sin(a) * 38f).scale(6.5f, 6.5f, 6.5f)
                opaqueQueue.add(orb)
            }
        }
        if (hero.hasStatus(StatusType.BLADE_FURY)) {
            for (k in 0..1) {
                val bf = pools["weapon_slash_ribbon"]!!.acquire(1.0f, 0.72f, 0.16f, alpha = 0.75f, emissive = 0.75f)
                bf.transform.setToTranslation(hx, h + 38f * sc + k * 6f, hz)
                    .rotate(Vector3.Y, curTime * 720f + k * 180f)
                    .scale(190f, 32f, 190f)
                blendQueue.add(bf)
            }
        }
        if (hero.rotEnabled) {
            addRing(hx, h + 10f, hz, 210f, 0.20f, 0.88f, 0.32f, 0.45f)
        }
        if (hero.hasStatus(StatusType.MAGIC_IMMUNE)) {
            val bkbDome = pools["vfx_sphere_blend"]!!.acquire(1.0f, 0.84f, 0.14f, alpha = 0.26f, emissive = 0.45f)
            bkbDome.transform.setToTranslation(hx, h + 46f, hz).scale(100f, 110f, 100f)
            blendQueue.add(bkbDome)
        }
        if (hero.isPlayer && hero.attackTargetId != null) {
            val target = world.findUnitById(hero.attackTargetId)
            if (target != null && target.alive) {
                val steps = 5
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps
                    val lx = DotaMath.lerp(hero.pos.x, target.pos.x, t)
                    val lz = DotaMath.lerp(hero.pos.y, target.pos.y, t)
                    val dot = pools["vfx_sphere_blend"]!!.acquire(1.0f, 0.30f, 0.22f, alpha = 0.65f, emissive = 0.5f)
                    dot.transform.setToTranslation(lx, terrainHeight(lx, lz) + 6f, lz).scale(8f, 3f, 8f)
                    blendQueue.add(dot)
                }
            }
        }
    }

    private fun drawZones3D(curTime: Float) {
        for (z in world.zones) {
            val h = terrainHeight(z.pos.x, z.pos.y)
            val (r, g, b) = rgbFloats(z.color)
            if (z.kind == "fissure" && z.endPos != null) {
                val steps = 11
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val rx = DotaMath.lerp(z.pos.x, z.endPos.x, t)
                    val rz = DotaMath.lerp(z.pos.y, z.endPos.y, t)
                    val stone = pools["vfx_rune_crystal"]!!.acquire(0.54f, 0.36f, 0.20f)
                    stone.transform.setToTranslation(rx, terrainHeight(rx, rz) + 22f, rz)
                        .rotate(Vector3.Y, i * 27f)
                        .scale(42f, 44f, 42f)
                    shadowQueue.add(stone)
                    opaqueQueue.add(stone)
                }
                continue
            }
            when (z.kind) {
                "chrono" -> {
                    val dome = pools["vfx_sphere_blend"]!!.acquire(1.0f, 0.84f, 0.14f, alpha = 0.30f, emissive = 0.55f)
                    dome.transform.setToTranslation(z.pos.x, h + 12f, z.pos.y).scale(z.radius * 2f, z.radius * 1.4f, z.radius * 2f)
                    blendQueue.add(dome)
                    addRing(z.pos.x, h + 6f, z.pos.y, z.radius, 1.0f, 0.90f, 0.25f, 0.80f)
                }
                "sunstrike" -> {
                    val beam = pools["vfx_beam"]!!.acquire(1.0f, 0.52f, 0.10f, alpha = 0.45f, emissive = 0.9f)
                    beam.transform.setToTranslation(z.pos.x, h + 160f, z.pos.y).scale(z.radius * 0.72f, 320f, z.radius * 0.72f)
                    blendQueue.add(beam)
                    addRing(z.pos.x, h + 5f, z.pos.y, z.radius, 1.0f, 0.45f, 0.08f, 0.82f)
                }
                "healing_ward" -> {
                    val crystal = pools["vfx_rune_crystal"]!!.acquire(0.22f, 0.94f, 0.55f, emissive = 0.65f)
                    crystal.transform.setToTranslation(z.pos.x, h + 38f + sin(curTime * 4f) * 6f, z.pos.y)
                        .rotate(Vector3.Y, curTime * 90f).scale(24f, 30f, 24f)
                    opaqueQueue.add(crystal)
                    addRing(z.pos.x, h + 4f, z.pos.y, z.radius, 0.22f, 0.94f, 0.55f, 0.38f)
                }
                else -> {
                    addRing(z.pos.x, h + 5f, z.pos.y, z.radius, r, g, b, 0.52f)
                }
            }
        }
    }

    private fun drawProjectiles3D(curTime: Float) {
        for (p in world.projectiles) {
            val h = terrainHeight(p.pos.x, p.pos.y)
            val (r, g, b) = rgbFloats(p.color)
            val dirAngle = if (p.isSkillshot) {
                atan2(p.velocity.y, p.velocity.x)
            } else {
                val tgt = world.findUnitById(p.targetId)
                if (tgt != null) p.pos.angleTo(tgt.pos) else 0f
            }
            val angleDeg = 90f - Math.toDegrees(dirAngle.toDouble()).toFloat()

            if (p.kind == "hook") {
                val shooter = world.heroes.firstOrNull { it.id == p.sourceId }
                if (shooter != null) {
                    val steps = 10
                    for (s in 1 until steps) {
                        val t = s.toFloat() / steps
                        val lx = DotaMath.lerp(shooter.pos.x, p.pos.x, t)
                        val lz = DotaMath.lerp(shooter.pos.y, p.pos.y, t)
                        val link = pools["vfx_spark"]!!.acquire(0.68f, 0.68f, 0.72f, alpha = 0.95f)
                        link.transform.setToTranslation(lx, terrainHeight(lx, lz) + 32f, lz)
                            .rotate(Vector3.Y, angleDeg)
                            .scale(8f, 8f, 14f)
                        blendQueue.add(link)
                    }
                    val hook = pools["proj_hook"]!!.acquire(0.88f, 0.22f, 0.18f, emissive = 0.25f)
                    hook.transform.setToTranslation(p.pos.x, h + 32f, p.pos.y).rotate(Vector3.Y, angleDeg).scale(1.35f, 1.35f, 1.35f)
                    opaqueQueue.add(hook)
                }
                continue
            }

            // Pick authentic 3D projectile .obj model
            when (p.kind) {
                "dagger" -> {
                    val d = pools["proj_dagger"]!!.acquire(r, g, b, emissive = 0.45f)
                    d.transform.setToTranslation(p.pos.x, h + 34f, p.pos.y)
                        .rotate(Vector3.Y, curTime * 950f)
                        .scale(1.4f, 1.4f, 1.4f)
                    opaqueQueue.add(d)
                }
                "lance", "blast" -> {
                    val sp = pools["proj_spear"]!!.acquire(r, g, b, emissive = 0.60f)
                    sp.transform.setToTranslation(p.pos.x, h + 34f, p.pos.y)
                        .rotate(Vector3.Y, angleDeg)
                        .scale(1.5f, 1.5f, 1.5f)
                    opaqueQueue.add(sp)
                }
                "tower", "assassinate" -> {
                    val arr = pools["proj_arrow"]!!.acquire(r, g, b, emissive = 0.55f)
                    arr.transform.setToTranslation(p.pos.x, h + 38f, p.pos.y)
                        .rotate(Vector3.Y, angleDeg)
                        .scale(1.5f, 1.5f, 1.6f)
                    opaqueQueue.add(arr)
                }
                "meteor" -> {
                    val met = pools["proj_orb"]!!.acquire(1.0f, 0.44f, 0.10f, emissive = 0.70f)
                    met.transform.setToTranslation(p.pos.x, h + 38f, p.pos.y)
                        .rotate(Vector3.X, curTime * 320f)
                        .scale(38f, 38f, 38f)
                    opaqueQueue.add(met)
                }
                else -> {
                    val shooter = world.heroes.firstOrNull { it.id == p.sourceId }
                    if (shooter?.def?.id == "sniper") {
                        val arr = pools["proj_arrow"]!!.acquire(1.0f, 0.86f, 0.25f, emissive = 0.6f)
                        arr.transform.setToTranslation(p.pos.x, h + 36f, p.pos.y)
                            .rotate(Vector3.Y, angleDeg)
                            .scale(1.1f, 1.1f, 1.4f)
                        opaqueQueue.add(arr)
                    } else {
                        val orb = pools["proj_orb"]!!.acquire(r, g, b, emissive = 0.6f)
                        val sc = p.radius * 1.4f
                        orb.transform.setToTranslation(p.pos.x, h + 35f, p.pos.y).scale(sc, sc, sc)
                        opaqueQueue.add(orb)
                    }
                }
            }

            // Glowing 3D Projectile Trail!
            val baseTrailRadius = when (p.kind) {
                "meteor" -> 42f
                "blast", "lance" -> 18f
                "hook" -> 12f
                else -> (p.radius * 1.35f).coerceAtMost(24f)
            }
            for (ti in p.trail.indices) {
                val tp = p.trail[ti]
                val fade = 1f - (ti + 1).toFloat() / (p.trail.size + 1)
                val ts = baseTrailRadius * fade
                val tr = pools["vfx_sphere_blend"]!!.acquire(r, g, b, alpha = fade * 0.68f, emissive = 0.75f)
                tr.transform.setToTranslation(tp.x, terrainHeight(tp.x, tp.y) + 34f, tp.y).scale(ts, ts, ts)
                blendQueue.add(tr)
            }
        }
    }

    private fun drawParticles3D() {
        for (pt in world.particles) {
            val h = terrainHeight(pt.pos.x, pt.pos.y)
            val (r, g, b) = rgbFloats(pt.color)
            val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            if (pt.ring) {
                val spreadScale = pt.size * 2.2f * (1.25f - alpha * 0.4f)
                addRing(pt.pos.x, h + 6f, pt.pos.y, spreadScale, r, g, b, alpha * 0.78f)
            } else {
                val sz = pt.size * (0.9f + alpha * 1.1f)
                val spark = pools["vfx_spark"]!!.acquire(r, g, b, alpha = alpha, emissive = 0.85f)
                val dirDeg = 90f - Math.toDegrees(atan2(pt.vel.y, pt.vel.x).toDouble()).toFloat()
                spark.transform.setToTranslation(pt.pos.x, h + pt.height, pt.pos.y)
                    .rotate(Vector3.Y, dirDeg)
                    .scale(sz, sz, sz * 2.4f)
                blendQueue.add(spark)
            }
        }
    }

    private fun drawAimIndicators3D() {
        val ph = world.playerHero
        val h = terrainHeight(ph.pos.x, ph.pos.y)
        if (aimingAbilityIdx in 0..3) {
            val ab = ph.def.abilities.getOrNull(aimingAbilityIdx) ?: return
            val range = ab.castRange.takeIf { it > 0f } ?: 650f
            if (ab.castRange > 0f) {
                addRing(ph.pos.x, h + 4f, ph.pos.y, range, 0.22f, 0.74f, 0.98f, 0.55f)
            }
            if (ab.targetType == TargetType.DIRECTION_SKILLSHOT) {
                val steps = 10
                val ang = ph.pos.angleTo(aimWorldPos)
                val endPt = ph.pos + Vec2.fromAngle(ang, range)
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    val lx = DotaMath.lerp(ph.pos.x, endPt.x, t)
                    val lz = DotaMath.lerp(ph.pos.y, endPt.y, t)
                    val dot = pools["vfx_sphere_blend"]!!.acquire(0.25f, 0.88f, 1.0f, alpha = 0.65f, emissive = 0.75f)
                    dot.transform.setToTranslation(lx, terrainHeight(lx, lz) + 8f, lz).scale(22f, 5f, 22f)
                    blendQueue.add(dot)
                }
            } else {
                val rad = ab.radius.takeIf { it > 0f } ?: 95f
                val ah = terrainHeight(aimWorldPos.x, aimWorldPos.y)
                addRing(aimWorldPos.x, ah + 5f, aimWorldPos.y, rad, 0.98f, 0.78f, 0.16f, 0.72f)
                val disc = pools["vfx_sphere_blend"]!!.acquire(0.22f, 0.74f, 0.98f, alpha = 0.25f, emissive = 0.5f)
                disc.transform.setToTranslation(aimWorldPos.x, ah + 5f, aimWorldPos.y).scale(rad * 1.9f, 4f, rad * 1.9f)
                blendQueue.add(disc)
            }
        } else if (aimingItemSlotIdx in 0..5 && ph.itemAtGridSlot(aimingItemSlotIdx) != null) {
            val ah = terrainHeight(aimWorldPos.x, aimWorldPos.y)
            addRing(ph.pos.x, h + 4f, ph.pos.y, 850f, 0.98f, 0.76f, 0.20f, 0.52f)
            addRing(aimWorldPos.x, ah + 5f, aimWorldPos.y, 95f, 1.0f, 0.82f, 0.18f, 0.78f)
        }
    }

    private fun addRing(x: Float, y: Float, z: Float, radius: Float, r: Float, g: Float, b: Float, alpha: Float) {
        val ring = pools["vfx_ring"]!!.acquire(r, g, b, alpha = alpha, emissive = 0.65f)
        ring.transform.setToTranslation(x, y, z).scale(radius, 1f, radius)
        blendQueue.add(ring)
    }

    private fun fogTint(wx: Float, wz: Float): Float {
        val gx = (wx / world.fogOfWar.cellWorldSize()).toInt().coerceIn(0, world.fogOfWar.size() - 1)
        val gy = (wz / world.fogOfWar.cellWorldSize()).toInt().coerceIn(0, world.fogOfWar.size() - 1)
        return when (world.fogOfWar.getCellState(gx, gy)) {
            2 -> 1.0f
            1 -> 0.72f
            else -> 0.48f
        }
    }

    private fun projectToScreen(wx: Float, wy: Float, wz: Float): Pair<Float, Float>? {
        scratchVec.set(wx, wy, wz)
        scratchVec.prj(camera.combined)
        if (scratchVec.z < -1f || scratchVec.z > 1f) return null
        if (scratchVec.x < -1.15f || scratchVec.x > 1.15f || scratchVec.y < -1.15f || scratchVec.y > 1.15f) return null
        val sx = (scratchVec.x * 0.5f + 0.5f) * viewportWidth
        val sy = (1f - (scratchVec.y * 0.5f + 0.5f)) * viewportHeight
        return sx to sy
    }

    /**
     * Thread-safe screen touch -> ground world coordinate raypicker callable from Android UI thread.
     */
    fun screenToWorld(sx: Float, sy: Float): Vec2 {
        val ray = Ray()
        val planeY: Float
        synchronized(camLock) {
            val w = snapWidth.coerceAtLeast(1f)
            val h = snapHeight.coerceAtLeast(1f)
            val ndcX = (2f * sx / w) - 1f
            val ndcY = 1f - (2f * sy / h)
            ray.origin.set(ndcX, ndcY, -1f).prj(snapInvProjView)
            ray.direction.set(ndcX, ndcY, 1f).prj(snapInvProjView).sub(ray.origin).nor()
            planeY = snapGroundY
        }
        val groundPlane = Plane(Vector3.Y, -planeY)
        val hit = Vector3()
        return if (Intersector.intersectRayPlane(ray, groundPlane, hit)) {
            Vec2(hit.x.coerceIn(0f, MapLayout.SIZE), hit.z.coerceIn(0f, MapLayout.SIZE))
        } else {
            cameraController.cameraPos.copyVec()
        }
    }

    private fun terrainHeight(wx: Float, wz: Float): Float {
        val rDist = MapLayout.riverDistance(wx, wz)
        val riverDip = -26f * (1f - DotaMath.smoothstep(95f, 200f, rDist))
        val hg = MapLayout.highGroundFactor(wx, wz)
        val cliffRise = 38f * hg
        val roll = if (rDist > 220f && hg < 0.1f) sin(wx * 0.0038f) * cos(wz * 0.0038f) * 3.5f else 0f
        return riverDip + cliffRise + roll
    }

    private fun rgbFloats(argb: Int): Triple<Float, Float, Float> =
        Triple(AndroidColor.red(argb) / 255f, AndroidColor.green(argb) / 255f, AndroidColor.blue(argb) / 255f)

    override fun dispose() {
        runCatching { modelBatch.dispose() }
        runCatching { shadowBatch.dispose() }
        runCatching { shadowLight.dispose() }
        runCatching { fogPixmap.dispose() }
        loadedModels.forEach { runCatching { it.dispose() } }
        loadedTextures.forEach { runCatching { it.dispose() } }
        loadedModels.clear()
        loadedTextures.clear()
        pools.clear()
    }
}
