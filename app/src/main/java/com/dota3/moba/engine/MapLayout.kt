package com.dota3.moba.engine

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.model.Team
import kotlin.math.abs

data class TowerBlueprint(
    val id: String,
    val team: Team,
    val lane: Int, // 0=top, 1=mid, 2=bot, 3=base
    val tier: Int, // 1..4
    val pos: Vec2
)

data class NeutralCampBlueprint(
    val id: String,
    val team: Team,
    val tier: Int, // 1=small, 2=med, 3=large, 4=ancient
    val pos: Vec2
)

data class TreeNode(
    val id: Int,
    val pos: Vec2,
    val radius: Float = 26f,
    var alive: Boolean = true,
    var respawnTimer: Float = 0f
)

object MapLayout {
    const val SIZE = DotaMath.MAP_SIZE

    val radiantFountain = Vec2(210f, 4590f)
    val direFountain = Vec2(4590f, 210f)

    val radiantAncientPos = Vec2(450f, 4350f)
    val direAncientPos = Vec2(4350f, 450f)

    val roshanPitPos = Vec2(1660f, 1840f)

    val twinGateTopLeft = Vec2(250f, 250f)
    val twinGateBotRight = Vec2(4550f, 4550f)

    val lotusTop = Vec2(690f, 1220f)
    val lotusBot = Vec2(4110f, 3580f)

    val radiantTormentorPos = Vec2(580f, 2950f)
    val direTormentorPos = Vec2(4220f, 1850f)

    val powerRuneTopPos = Vec2(1320f, 1320f)
    val powerRuneBotPos = Vec2(3480f, 3480f)

    val bountyRuneRadiantPos = Vec2(1650f, 3980f)
    val bountyRuneDirePos = Vec2(3150f, 820f)

    val wisdomRadiantPos = Vec2(430f, 1180f)
    val wisdomDirePos = Vec2(4370f, 3620f)

    val wardSpots: List<Vec2> = listOf(
        Vec2(1500f, 1500f),
        Vec2(2150f, 2150f),
        Vec2(2650f, 2650f),
        Vec2(3300f, 3300f),
        Vec2(1420f, 3520f),
        Vec2(3380f, 1280f)
    )

    // Lane waypoints from Radiant to Dire
    val laneWaypoints: Array<List<Vec2>> = arrayOf(
        // 0 = Top
        listOf(
            Vec2(450f, 4050f),
            Vec2(430f, 2650f),
            Vec2(430f, 1650f),
            Vec2(440f, 520f),
            Vec2(1650f, 430f),
            Vec2(2650f, 430f),
            Vec2(4050f, 450f)
        ),
        // 1 = Mid
        listOf(
            Vec2(720f, 4080f),
            Vec2(1220f, 3580f),
            Vec2(1720f, 3080f),
            Vec2(2400f, 2400f),
            Vec2(3080f, 1720f),
            Vec2(3580f, 1220f),
            Vec2(4080f, 720f)
        ),
        // 2 = Bot
        listOf(
            Vec2(750f, 4350f),
            Vec2(2150f, 4370f),
            Vec2(3150f, 4370f),
            Vec2(4360f, 4360f),
            Vec2(4370f, 3150f),
            Vec2(4370f, 2150f),
            Vec2(4350f, 750f)
        )
    )

    val towers: List<TowerBlueprint> = listOf(
        // Radiant
        TowerBlueprint("rad_top_1", Team.RADIANT, 0, 1, Vec2(430f, 1650f)),
        TowerBlueprint("rad_top_2", Team.RADIANT, 0, 2, Vec2(430f, 2650f)),
        TowerBlueprint("rad_top_3", Team.RADIANT, 0, 3, Vec2(450f, 3680f)),

        TowerBlueprint("rad_mid_1", Team.RADIANT, 1, 1, Vec2(1720f, 3080f)),
        TowerBlueprint("rad_mid_2", Team.RADIANT, 1, 2, Vec2(1220f, 3580f)),
        TowerBlueprint("rad_mid_3", Team.RADIANT, 1, 3, Vec2(760f, 4040f)),

        TowerBlueprint("rad_bot_1", Team.RADIANT, 2, 1, Vec2(3150f, 4370f)),
        TowerBlueprint("rad_bot_2", Team.RADIANT, 2, 2, Vec2(2150f, 4370f)),
        TowerBlueprint("rad_bot_3", Team.RADIANT, 2, 3, Vec2(1120f, 4350f)),

        TowerBlueprint("rad_t4_a", Team.RADIANT, 3, 4, Vec2(530f, 4170f)),
        TowerBlueprint("rad_t4_b", Team.RADIANT, 3, 4, Vec2(630f, 4270f)),

        // Dire
        TowerBlueprint("dire_top_1", Team.DIRE, 0, 1, Vec2(1650f, 430f)),
        TowerBlueprint("dire_top_2", Team.DIRE, 0, 2, Vec2(2650f, 430f)),
        TowerBlueprint("dire_top_3", Team.DIRE, 0, 3, Vec2(3680f, 450f)),

        TowerBlueprint("dire_mid_1", Team.DIRE, 1, 1, Vec2(3080f, 1720f)),
        TowerBlueprint("dire_mid_2", Team.DIRE, 1, 2, Vec2(3580f, 1220f)),
        TowerBlueprint("dire_mid_3", Team.DIRE, 1, 3, Vec2(4040f, 760f)),

        TowerBlueprint("dire_bot_1", Team.DIRE, 2, 1, Vec2(4370f, 3150f)),
        TowerBlueprint("dire_bot_2", Team.DIRE, 2, 2, Vec2(4370f, 2150f)),
        TowerBlueprint("dire_bot_3", Team.DIRE, 2, 3, Vec2(4350f, 1120f)),

        TowerBlueprint("dire_t4_a", Team.DIRE, 3, 4, Vec2(4270f, 630f)),
        TowerBlueprint("dire_t4_b", Team.DIRE, 3, 4, Vec2(4170f, 530f))
    )

    val neutralCamps: List<NeutralCampBlueprint> = listOf(
        // Radiant Jungle
        NeutralCampBlueprint("rad_small", Team.RADIANT, 1, Vec2(2520f, 3920f)),
        NeutralCampBlueprint("rad_med_1", Team.RADIANT, 2, Vec2(1980f, 3680f)),
        NeutralCampBlueprint("rad_med_2", Team.RADIANT, 2, Vec2(1150f, 3050f)),
        NeutralCampBlueprint("rad_large", Team.RADIANT, 3, Vec2(2760f, 3420f)),
        NeutralCampBlueprint("rad_ancient", Team.RADIANT, 4, Vec2(1180f, 2320f)),

        // Dire Jungle
        NeutralCampBlueprint("dire_small", Team.DIRE, 1, Vec2(2280f, 880f)),
        NeutralCampBlueprint("dire_med_1", Team.DIRE, 2, Vec2(2820f, 1120f)),
        NeutralCampBlueprint("dire_med_2", Team.DIRE, 2, Vec2(3650f, 1750f)),
        NeutralCampBlueprint("dire_large", Team.DIRE, 3, Vec2(2040f, 1380f)),
        NeutralCampBlueprint("dire_ancient", Team.DIRE, 4, Vec2(3620f, 2480f))
    )

    fun riverDistance(x: Float, y: Float): Float {
        val wave = kotlin.math.sin((x + y) * 0.0022f) * 70f
        val diagDist = abs((x - y) - wave) / 1.414f
        val endMargin = maxOf(0f, maxOf(480f - x, maxOf(x - 4320f, maxOf(480f - y, y - 4320f))))
        return diagDist + endMargin
    }

    fun isInRiver(x: Float, y: Float): Boolean = riverDistance(x, y) < 145f

    fun isHighGround(x: Float, y: Float): Boolean {
        // Radiant & Dire base plateaus
        if (x < 1050f && y > 3750f) return true
        if (x > 3750f && y < 1050f) return true
        // Jungle elevated ridges overlooking river
        if (!isInRiver(x, y)) {
            if (x in 1300f..1700f && y in 3300f..3700f) return true
            if (x in 3100f..3500f && y in 1100f..1500f) return true
        }
        return false
    }

    fun highGroundFactor(x: Float, y: Float): Float {
        // Smooth transition onto Radiant base (x < 1050, y > 3750)
        val radX = DotaMath.smoothstep(1150f, 940f, x)
        val radY = DotaMath.smoothstep(3650f, 3860f, y)
        val radBase = radX * radY

        // Smooth transition onto Dire base (x > 3750, y < 1050)
        val direX = DotaMath.smoothstep(3650f, 3860f, x)
        val direY = DotaMath.smoothstep(1150f, 940f, y)
        val direBase = direX * direY

        // Jungle elevated ridges overlooking river
        val radRidge = DotaMath.smoothstep(1230f, 1340f, x) * DotaMath.smoothstep(1770f, 1660f, x) *
            DotaMath.smoothstep(3230f, 3340f, y) * DotaMath.smoothstep(3770f, 3660f, y)
        val direRidge = DotaMath.smoothstep(3030f, 3140f, x) * DotaMath.smoothstep(3570f, 3460f, x) *
            DotaMath.smoothstep(1030f, 1140f, y) * DotaMath.smoothstep(1570f, 1460f, y)

        return maxOf(maxOf(radBase, direBase), maxOf(radRidge, direRidge)).coerceIn(0f, 1f)
    }

    fun generateTrees(): MutableList<TreeNode> {
        val trees = mutableListOf<TreeNode>()
        var nextId = 1

        fun addCluster(cx: Float, cy: Float, cols: Int, rows: Int, spacing: Float = 66f) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val jitterX = (((nextId * 37) % 23) - 11).toFloat()
                    val jitterY = (((nextId * 53) % 23) - 11).toFloat()
                    val tx = cx + (c - cols / 2f) * spacing + ((r % 2) * 20f) + jitterX
                    val ty = cy + (r - rows / 2f) * spacing + jitterY
                    if (isValidTreeSpot(tx, ty)) {
                        trees.add(TreeNode(nextId++, Vec2(tx, ty)))
                    }
                }
            }
        }

        // Strategic Dota jungle tree clusters & lane side juke woods
        addCluster(900f, 2000f, 3, 4)
        addCluster(920f, 2750f, 3, 3)
        addCluster(1550f, 2550f, 3, 3)
        addCluster(2150f, 3150f, 3, 3)
        addCluster(2450f, 3750f, 4, 2)
        addCluster(3050f, 3880f, 3, 2)
        addCluster(1450f, 3880f, 3, 2)

        addCluster(3900f, 2800f, 3, 4)
        addCluster(3880f, 2050f, 3, 3)
        addCluster(3250f, 2250f, 3, 3)
        addCluster(2650f, 1650f, 3, 3)
        addCluster(2350f, 1050f, 4, 2)
        addCluster(1750f, 920f, 3, 2)
        addCluster(3350f, 920f, 3, 2)

        // Outer border woods for juking along sidelanes
        addCluster(200f, 1400f, 2, 5)
        addCluster(200f, 2400f, 2, 5)
        addCluster(4600f, 2400f, 2, 5)
        addCluster(4600f, 3400f, 2, 5)
        addCluster(1400f, 200f, 5, 2)
        addCluster(2600f, 200f, 5, 2)
        addCluster(2200f, 4600f, 5, 2)
        addCluster(3400f, 4600f, 5, 2)

        return trees
    }

    private fun isValidTreeSpot(x: Float, y: Float): Boolean {
        if (x < 90f || x > SIZE - 90f || y < 90f || y > SIZE - 90f) return false
        if (isInRiver(x, y)) return false
        val p = Vec2(x, y)
        if (p.distTo(roshanPitPos) < 250f) return false
        if (p.distTo(radiantAncientPos) < 620f) return false
        if (p.distTo(direAncientPos) < 620f) return false
        for (t in towers) {
            if (p.distTo(t.pos) < 160f) return false
        }
        for (camp in neutralCamps) {
            if (p.distTo(camp.pos) < 155f) return false
        }
        for (lane in laneWaypoints) {
            for (i in 0 until lane.size - 1) {
                if (DotaMath.distPointToSegment(x, y, lane[i].x, lane[i].y, lane[i + 1].x, lane[i + 1].y) < 130f) {
                    return false
                }
            }
        }
        return true
    }
}
