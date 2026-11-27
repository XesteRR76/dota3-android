package com.dota3.moba.engine

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.SoundCue
import com.dota3.moba.core.SoundEngine
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.HeroCatalog
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.model.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class MatchAnnouncement(
    val title: String,
    val subtitle: String,
    val color: Int,
    var remaining: Float = 3.2f
)

data class MinimapPing(
    val pos: Vec2,
    val color: Int,
    var remaining: Float = 2.8f
)

class GameWorld(
    val playerHeroId: String,
    val playerFacetIndex: Int = 0,
    val playerTeam: Team = Team.RADIANT,
    val playerLane: Int = 1,
    val mode: GameMode = GameMode.ALL_PICK_5V5,
    val difficulty: BotDifficulty = BotDifficulty.NORMAL,
    seed: Int = 1337,
    val draftedAllyHeroIds: List<String> = emptyList(),
    val draftedEnemyHeroIds: List<String> = emptyList()
) {
    private val rng = Random(seed)

    var gameTime: Float = 0f
    var matchOver: Boolean = false
    var winner: Team? = null
    var paused: Boolean = false
    var firstBloodClaimed: Boolean = false

    var radiantKills: Int = 0
    var direKills: Int = 0
    var radiantGlyphCd: Float = 0f
    var direGlyphCd: Float = 0f

    var allyCommand: TacticalCommand = TacticalCommand.AUTO
    var allyCommandTimer: Float = 0f

    @Volatile var pendingCameraShake: Float = 0f
    @Volatile var lastSkillshotAimPos: Vec2? = null
    @Volatile var lastSkillshotHoldTimer: Float = 0f

    val fogOfWar = FogOfWar(128)
    val trees: MutableList<TreeNode> = MapLayout.generateTrees()
    val heroes: MutableList<HeroEntity> = mutableListOf()
    val creeps: MutableList<CreepEntity> = mutableListOf()
    val neutrals: MutableList<NeutralCreepEntity> = mutableListOf()
    val towers: MutableList<TowerEntity> = mutableListOf()
    val barracks: MutableList<BarracksEntity> = mutableListOf()
    val wards: MutableList<WardEntity> = mutableListOf()
    val runes: MutableList<RuneEntity> = mutableListOf()
    val droppedItems: MutableList<DroppedItemEntity> = mutableListOf()
    val couriers: MutableList<CourierEntity> = mutableListOf()
    val projectiles: MutableList<Projectile> = mutableListOf()
    val zones: MutableList<AoEZone> = mutableListOf()
    val particles: MutableList<ParticleEffect> = mutableListOf()
    val floatingTexts: MutableList<FloatingText> = mutableListOf()
    val announcements: MutableList<MatchAnnouncement> = mutableListOf()
    val pings: MutableList<MinimapPing> = mutableListOf()

    val radiantAncient = AncientEntity(Team.RADIANT, MapLayout.radiantAncientPos.copyVec())
    val direAncient = AncientEntity(Team.DIRE, MapLayout.direAncientPos.copyVec())
    val roshan = RoshanEntity(MapLayout.roshanPitPos.copyVec())
    val radiantTormentor = TormentorEntity("rad_tormentor", Team.RADIANT, MapLayout.radiantTormentorPos.copyVec())
    val direTormentor = TormentorEntity("dire_tormentor", Team.DIRE, MapLayout.direTormentorPos.copyVec())

    lateinit var playerHero: HeroEntity
        private set

    private var entityUid = 1
    private var nextCreepWaveTime = 2.0f
    private var creepWaveCount = 0
    private var nextNeutralCheckTime = 5.0f
    private var nextPowerRuneTime = 120f
    private var nextBountyRuneTime = 1.0f
    private var nextWisdomTime = 240f
    private var nextLotusTime = 90f
    private var passiveGoldAccum = 0f
    private var fogUpdateAccum = 0f
    private var towerAuraAccum = 0f
    private var sandboxDummyRespawn = 0f

    val isNight: Boolean
        get() = ((gameTime / 150f).toInt() % 2) == 1

    init {
        setupStructures()
        setupHeroes()
        if (mode == GameMode.TURBO_5V5) {
            heroes.forEach { it.gold = 900; it.recalcStats() }
        } else if (mode == GameMode.SANDBOX) {
            playerHero.gold = 99999
            playerHero.addXp(5040)
            playerHero.hp = playerHero.maxHp
            playerHero.mana = playerHero.maxMana
            playerHero.recalcStats()
        }
        updateFogOfWar()
    }

    private fun uid(prefix: String): String = "${prefix}_${entityUid++}"

    private fun setupStructures() {
        for (bp in MapLayout.towers) {
            if (mode == GameMode.MID_1V1 && bp.lane != 1 && bp.tier < 4) continue
            towers.add(TowerEntity(bp))
        }
        // Barracks for each lane
        val radRax = listOf(
            BarracksEntity("rad_top_rax_m", Team.RADIANT, Vec2(390f, 3810f), 0, true),
            BarracksEntity("rad_top_rax_r", Team.RADIANT, Vec2(500f, 3810f), 0, false),
            BarracksEntity("rad_mid_rax_m", Team.RADIANT, Vec2(640f, 4050f), 1, true),
            BarracksEntity("rad_mid_rax_r", Team.RADIANT, Vec2(750f, 4160f), 1, false),
            BarracksEntity("rad_bot_rax_m", Team.RADIANT, Vec2(990f, 4300f), 2, true),
            BarracksEntity("rad_bot_rax_r", Team.RADIANT, Vec2(990f, 4410f), 2, false)
        )
        val direRax = listOf(
            BarracksEntity("dire_top_rax_m", Team.DIRE, Vec2(3810f, 390f), 0, true),
            BarracksEntity("dire_top_rax_r", Team.DIRE, Vec2(3810f, 500f), 0, false),
            BarracksEntity("dire_mid_rax_m", Team.DIRE, Vec2(4050f, 640f), 1, true),
            BarracksEntity("dire_mid_rax_r", Team.DIRE, Vec2(4160f, 750f), 1, false),
            BarracksEntity("dire_bot_rax_m", Team.DIRE, Vec2(4300f, 990f), 2, true),
            BarracksEntity("dire_bot_rax_r", Team.DIRE, Vec2(4410f, 990f), 2, false)
        )
        barracks.addAll(radRax)
        barracks.addAll(direRax)
    }

    private fun setupHeroes() {
        val pool = HeroCatalog.allHeroes.map { it.id }.toMutableList()
        pool.remove(playerHeroId)
        pool.shuffle(rng)

        val playerDef = HeroCatalog.get(playerHeroId)
        val playerSpawn = spawnPosFor(playerTeam, 0)
        playerHero = HeroEntity(
            id = "hero_player",
            team = playerTeam,
            pos = playerSpawn,
            def = playerDef,
            isPlayer = true,
            facetIndex = playerFacetIndex,
            assignedLane = playerLane
        )
        heroes.add(playerHero)
        couriers.add(CourierEntity(playerHero.id, playerTeam, fountainPos(playerTeam)))

        if (mode == GameMode.MID_1V1 || mode == GameMode.SANDBOX) {
            val enemyId = pool.removeAt(0)
            val enemyTeam = playerTeam.enemy()
            val enemy = HeroEntity(
                id = "hero_enemy_0",
                team = enemyTeam,
                pos = if (mode == GameMode.SANDBOX) Vec2(2400f, 2400f) else spawnPosFor(enemyTeam, 0),
                def = HeroCatalog.get(enemyId),
                isPlayer = false,
                facetIndex = rng.nextInt(2),
                assignedLane = 1
            )
            heroes.add(enemy)
            couriers.add(CourierEntity(enemy.id, enemyTeam, fountainPos(enemyTeam)))
            return
        }

        // Fill remaining 4 allies + 5 enemies
        val lanePlan = listOf(0, 2, 1, 0, 2)
        var allyIdx = 1
        val usedPlayerLanes = mutableListOf(playerLane)
        val allyLanes = listOf(0, 2, 1, 0, 2).toMutableList()
        allyLanes.remove(playerLane)
        val preDraftAllies = draftedAllyHeroIds.filter { it != playerHeroId }.distinct().toMutableList()
        preDraftAllies.forEach { pool.remove(it) }
        val preDraftEnemies = draftedEnemyHeroIds.filter { it != playerHeroId && it !in preDraftAllies }.distinct().toMutableList()
        preDraftEnemies.forEach { pool.remove(it) }

        for (i in 0 until 4) {
            val hid = if (preDraftAllies.isNotEmpty()) preDraftAllies.removeAt(0) else pool.removeAt(0)
            val h = HeroEntity(
                id = "hero_ally_${allyIdx++}",
                team = playerTeam,
                pos = spawnPosFor(playerTeam, i + 1),
                def = HeroCatalog.get(hid),
                isPlayer = false,
                facetIndex = rng.nextInt(2),
                assignedLane = allyLanes[i]
            )
            heroes.add(h)
            couriers.add(CourierEntity(h.id, playerTeam, fountainPos(playerTeam)))
        }

        val enemyTeam = playerTeam.enemy()
        for (i in 0 until 5) {
            val hid = if (preDraftEnemies.isNotEmpty()) preDraftEnemies.removeAt(0) else pool.removeAt(0)
            val h = HeroEntity(
                id = "hero_enemy_$i",
                team = enemyTeam,
                pos = spawnPosFor(enemyTeam, i),
                def = HeroCatalog.get(hid),
                isPlayer = false,
                facetIndex = rng.nextInt(2),
                assignedLane = lanePlan[i]
            )
            heroes.add(h)
            couriers.add(CourierEntity(h.id, enemyTeam, fountainPos(enemyTeam)))
        }
    }

    private fun fountainPos(team: Team): Vec2 =
        if (team == Team.RADIANT) MapLayout.radiantFountain else MapLayout.direFountain

    private fun spawnPosFor(team: Team, slot: Int): Vec2 {
        val base = fountainPos(team)
        val angle = slot * 0.7f
        val offset = if (team == Team.RADIANT) Vec2(95f + cos(angle) * 45f, -95f + sin(angle) * 45f)
        else Vec2(-95f + cos(angle) * 45f, 95f + sin(angle) * 45f)
        return base + offset
    }

    fun step(rawDt: Float) {
        if (paused || matchOver) return
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        gameTime += dt

        // Passive gold trickle (every 0.6s ~ +1-2 gold scaled by mode)
        passiveGoldAccum += dt * mode.goldMult
        if (passiveGoldAccum >= 0.6f) {
            passiveGoldAccum -= 0.6f
            for (h in heroes) {
                h.gold += 2
                h.totalGoldEarned += 2
            }
        }

        // Glyph cooldowns & Tactical Command timers
        if (radiantGlyphCd > 0f) radiantGlyphCd = (radiantGlyphCd - dt).coerceAtLeast(0f)
        if (direGlyphCd > 0f) direGlyphCd = (direGlyphCd - dt).coerceAtLeast(0f)
        if (lastSkillshotHoldTimer > 0f) {
            lastSkillshotHoldTimer = (lastSkillshotHoldTimer - dt).coerceAtLeast(0f)
            if (lastSkillshotHoldTimer <= 0f) lastSkillshotAimPos = null
        }
        if (allyCommand != TacticalCommand.AUTO && allyCommandTimer > 0f) {
            allyCommandTimer = (allyCommandTimer - dt).coerceAtLeast(0f)
            if (allyCommandTimer <= 0f) allyCommand = TacticalCommand.AUTO
        }

        // Tree respawn
        for (t in trees) {
            if (!t.alive) {
                t.respawnTimer -= dt
                if (t.respawnTimer <= 0f) t.alive = true
            }
        }

        // Creep wave spawns
        if (gameTime >= nextCreepWaveTime && mode != GameMode.SANDBOX) {
            spawnCreepWave()
            nextCreepWaveTime += 30f
        }

        // Neutral camps every 60s (supports camp stacking!)
        if (gameTime >= nextNeutralCheckTime && mode != GameMode.SANDBOX) {
            spawnNeutralCampsIfEmpty()
            nextNeutralCheckTime += 60f
        }

        // Runes & Lotus & Wisdom
        if (gameTime >= nextPowerRuneTime) {
            spawnPowerRunes()
            nextPowerRuneTime += 120f
        }
        if (gameTime >= nextBountyRuneTime) {
            spawnBountyRunes()
            nextBountyRuneTime += 180f
        }
        if (gameTime >= nextWisdomTime) {
            spawnWisdomRunes()
            nextWisdomTime += 300f
        }
        if (gameTime >= nextLotusTime) {
            spawnLotus()
            nextLotusTime += 90f
        }

        // Roshan & Tormentor timers
        updateRoshan(dt)
        updateTormentors(dt)

        // Update Heroes (including Bot AI)
        for (h in heroes) {
            if (!h.isPlayer) {
                BotAI.updateBot(this, h, dt, difficulty)
            }
            updateHero(h, dt)
        }

        // Update Creeps & Neutrals
        updateCreeps(dt)
        updateNeutrals(dt)

        // Update Towers & Ancients
        updateTowersAndBase(dt)

        // Update Couriers, Wards, Dropped Items
        updateCouriers(dt)
        updateWardsAndItems(dt)

        // Update Projectiles, AoE Zones, Particles, Floating Text, Announcements
        updateProjectiles(dt)
        updateZones(dt)
        updateVisualEffects(dt)

        // Update Fog of War periodically (~10 Hz)
        fogUpdateAccum += dt
        if (fogUpdateAccum >= 0.10f) {
            fogUpdateAccum = 0f
            updateFogOfWar()
        }

        // Sandbox dummy auto-heal/respawn
        if (mode == GameMode.SANDBOX) {
            val dummy = heroes.firstOrNull { !it.isPlayer }
            if (dummy != null && !dummy.alive) {
                sandboxDummyRespawn += dt
                if (sandboxDummyRespawn > 2.5f) {
                    sandboxDummyRespawn = 0f
                    dummy.alive = true
                    dummy.hp = dummy.maxHp
                    dummy.pos.set(2400f, 2400f)
                }
            }
        }
    }

    private fun spawnCreepWave() {
        creepWaveCount++
        val lanes = if (mode == GameMode.MID_1V1) listOf(1) else listOf(0, 1, 2)
        val hpScale = 1f + (creepWaveCount / 8f) * 0.14f

        for (lane in lanes) {
            val wp = MapLayout.laneWaypoints[lane]
            for (team in listOf(Team.RADIANT, Team.DIRE)) {
                val enemyTeam = team.enemy()
                val allEnemyRaxDead = barracks.filter { it.team == enemyTeam }.all { !it.alive }
                val laneEnemyMeleeDead = barracks.any { it.team == enemyTeam && it.lane == lane && it.isMelee && !it.alive }
                val laneEnemyRangedDead = barracks.any { it.team == enemyTeam && it.lane == lane && !it.isMelee && !it.alive }

                val spawnPt = if (team == Team.RADIANT) wp.first() else wp.last()
                val startWp = if (team == Team.RADIANT) 1 else wp.size - 2

                // 3 Melee (or Super/Mega)
                val meleeType = when {
                    allEnemyRaxDead -> CreepType.MEGA_MELEE
                    laneEnemyMeleeDead -> CreepType.SUPER_MELEE
                    else -> CreepType.MELEE
                }
                for (i in 0 until 3) {
                    val c = CreepEntity(
                        id = uid("crp"),
                        team = team,
                        pos = Vec2(spawnPt.x + (i - 1) * 32f, spawnPt.y + (i - 1) * 18f),
                        type = meleeType,
                        lane = lane,
                        waypointIndex = startWp,
                        attackDamage = when (meleeType) {
                            CreepType.MEGA_MELEE -> 62f
                            CreepType.SUPER_MELEE -> 42f
                            else -> 21f * hpScale
                        },
                        attackRange = 115f,
                        bountyGold = if (meleeType == CreepType.MELEE) 36 else 18,
                        bountyXp = if (meleeType == CreepType.MELEE) 57 else 25
                    )
                    c.maxHp = when (meleeType) {
                        CreepType.MEGA_MELEE -> 1250f
                        CreepType.SUPER_MELEE -> 820f
                        else -> 550f * hpScale
                    }
                    c.hp = c.maxHp
                    c.armor = if (meleeType == CreepType.MELEE) 2f else 5f
                    creeps.add(c)
                }

                // 1 Ranged Creep
                val r = CreepEntity(
                    id = uid("crp"),
                    team = team,
                    pos = Vec2(spawnPt.x + 25f, spawnPt.y - 25f),
                    type = CreepType.RANGED,
                    lane = lane,
                    waypointIndex = startWp,
                    attackDamage = if (laneEnemyRangedDead) 44f else 24f * hpScale,
                    attackRange = 390f,
                    bountyGold = 45,
                    bountyXp = 69
                )
                r.maxHp = if (laneEnemyRangedDead) 520f else 310f * hpScale
                r.hp = r.maxHp
                creeps.add(r)

                // Siege Catapult every 5th wave
                if (creepWaveCount % 5 == 0) {
                    val s = CreepEntity(
                        id = uid("crp"),
                        team = team,
                        pos = Vec2(spawnPt.x - 30f, spawnPt.y + 30f),
                        type = CreepType.SIEGE,
                        lane = lane,
                        waypointIndex = startWp,
                        attackDamage = 48f,
                        attackRange = 460f,
                        bountyGold = 68,
                        bountyXp = 88
                    )
                    s.maxHp = 940f
                    s.hp = s.maxHp
                    s.magicResist = 0.80f
                    creeps.add(s)
                }
            }
        }
    }

    private fun spawnNeutralCampsIfEmpty() {
        for (camp in MapLayout.neutralCamps) {
            // Camp stacking: only checks if a unit is inside the small 160-unit spawn box!
            val occupied = neutrals.any { it.alive && it.pos.distTo(camp.pos) < 160f } ||
                heroes.any { it.alive && it.pos.distTo(camp.pos) < 140f }
            if (occupied) continue

            val count = if (camp.tier <= 2) 3 else 2
            for (i in 0 until count) {
                val n = NeutralCreepEntity(
                    id = uid("neu"),
                    pos = Vec2(camp.pos.x + (i - 1) * 38f, camp.pos.y + ((i % 2) - 0.5f) * 32f),
                    homePos = camp.pos.copyVec(),
                    campId = camp.id,
                    tier = camp.tier,
                    attackDamage = 18f + camp.tier * 16f,
                    bountyGold = 24 + camp.tier * 18,
                    bountyXp = 45 + camp.tier * 35
                )
                n.maxHp = 320f + camp.tier * 280f
                n.hp = n.maxHp
                n.armor = camp.tier * 2f
                neutrals.add(n)
            }
        }
    }

    private fun spawnPowerRunes() {
        val spots = listOf(MapLayout.powerRuneTopPos, MapLayout.powerRuneBotPos)
        val powerTypes = listOf(
            RuneType.DOUBLE_DAMAGE, RuneType.HASTE, RuneType.REGENERATION,
            RuneType.ARCANE, RuneType.INVISIBILITY
        )
        runes.removeAll { !it.alive || it.type in powerTypes }
        val chosenSpot = spots[rng.nextInt(spots.size)]
        val chosenType = powerTypes[rng.nextInt(powerTypes.size)]
        runes.add(RuneEntity(uid("rune"), chosenType, chosenSpot.copyVec()))
    }

    private fun spawnBountyRunes() {
        runes.removeAll { !it.alive || it.type == RuneType.BOUNTY }
        runes.add(RuneEntity(uid("rune"), RuneType.BOUNTY, MapLayout.bountyRuneRadiantPos.copyVec()))
        runes.add(RuneEntity(uid("rune"), RuneType.BOUNTY, MapLayout.bountyRuneDirePos.copyVec()))
    }

    private fun spawnWisdomRunes() {
        runes.add(RuneEntity(uid("rune"), RuneType.WISDOM, MapLayout.wisdomRadiantPos.copyVec()))
        runes.add(RuneEntity(uid("rune"), RuneType.WISDOM, MapLayout.wisdomDirePos.copyVec()))
    }

    private fun spawnLotus() {
        runes.add(RuneEntity(uid("rune"), RuneType.LOTUS, MapLayout.lotusTop.copyVec()))
        runes.add(RuneEntity(uid("rune"), RuneType.LOTUS, MapLayout.lotusBot.copyVec()))
    }

    private fun updateHero(h: HeroEntity, dt: Float) {
        if (!h.alive) {
            if (h.aegisRespawnTimer > 0f) {
                h.aegisRespawnTimer -= dt
                if (h.aegisRespawnTimer <= 0f) {
                    h.alive = true
                    h.hp = h.maxHp
                    h.mana = h.maxMana
                    h.statuses.clear()
                    h.recalcStats()
                    spawnParticles(h.pos, 0xFFFFD700.toInt(), 20, 140f)
                    addFloatingText(h.pos, "AEGIS REBORN!", 0xFFFFD700.toInt(), 1.5f)
                }
                return
            }
            h.respawnTimer -= dt
            if (h.respawnTimer <= 0f) {
                respawnHero(h)
            }
            return
        }

        // Cooldowns & Statuses
        tickUnitStatuses(h, dt)
        if (h.attackAnimTimer > 0f) h.attackAnimTimer = (h.attackAnimTimer - dt).coerceAtLeast(0f)
        if (h.attackComboResetTimer > 0f) {
            h.attackComboResetTimer = (h.attackComboResetTimer - dt).coerceAtLeast(0f)
            if (h.attackComboResetTimer <= 0f) h.attackComboStep = 0
        }
        if (h.castAnimTimer > 0f) h.castAnimTimer = (h.castAnimTimer - dt).coerceAtLeast(0f)
        h.attackCommandAge += dt
        if (abs(h.moveIntent.x) > 0.05f || abs(h.moveIntent.y) > 0.05f || h.tapMoveTarget != null) {
            h.walkCycle += dt * 12f
        }
        if (h.buybackCooldown > 0f) h.buybackCooldown = (h.buybackCooldown - dt).coerceAtLeast(0f)
        if (h.tpCooldown > 0f) h.tpCooldown = (h.tpCooldown - dt).coerceAtLeast(0f)

        for (i in 0..3) {
            if (h.abilityCooldowns[i] > 0f) h.abilityCooldowns[i] = (h.abilityCooldowns[i] - dt).coerceAtLeast(0f)
            val maxCh = h.def.abilities[i].maxCharges
            if (maxCh > 1 && h.abilityCharges[i] < maxCh) {
                h.abilityChargeTimers[i] -= dt
                if (h.abilityChargeTimers[i] <= 0f) {
                    h.abilityCharges[i]++
                    val lvl = h.abilityLevels[i].coerceAtLeast(1)
                    h.abilityChargeTimers[i] = h.def.abilities[i].cooldown[(lvl - 1).coerceIn(0, h.def.abilities[i].cooldown.lastIndex)]
                }
            }
        }
        for (slot in h.inventory) {
            if (slot.cooldownRemaining > 0f) slot.cooldownRemaining = (slot.cooldownRemaining - dt).coerceAtLeast(0f)
        }

        // Regen
        h.recalcStats()
        var hpRegen = h.cachedHpRegen
        var mpRegen = h.cachedManaRegen
        if (h.hasStatus(StatusType.REGEN)) {
            hpRegen += h.maxHp * 0.06f
            mpRegen += h.maxMana * 0.06f
        }
        val fountain = fountainPos(h.team)
        if (h.pos.distTo(fountain) < 380f) {
            hpRegen += h.maxHp * 0.14f
            mpRegen += h.maxMana * 0.16f
        }
        h.hp = (h.hp + hpRegen * dt).coerceAtMost(h.maxHp)
        h.mana = (h.mana + mpRegen * dt).coerceAtMost(h.maxMana)

        // Radiance Burn Aura
        if (h.cachedRadianceDps > 0f) {
            val tickDps = h.cachedRadianceDps * dt
            for (enemy in allEnemyUnits(h.team, h.pos, 420f)) {
                applyDamage(enemy, tickDps, DamageType.MAGICAL, h, showFloat = false)
            }
        }

        // Hero-specific ongoing spells (Blade Fury, Rot, Freezing Field, Omnislash, Dismember, Dissimilate)
        updateHeroOngoingSpells(h, dt)

        // TP Channel
        if (h.tpChannelTimer > 0f) {
            if (h.isStunned() || h.isSilenced()) {
                h.tpChannelTimer = 0f
                h.tpTargetPos = null
                addFloatingText(h.pos, "TP CANCELLED", 0xFFE74C3C.toInt())
            } else {
                h.tpChannelTimer -= dt
                if (h.tpChannelTimer <= 0f) {
                    h.tpTargetPos?.let { dest ->
                        h.pos.set(dest.x, dest.y)
                        h.tapMoveTarget = null
                        spawnParticles(h.pos, 0xFF38BDF8.toInt(), 18, 120f)
                    }
                    h.tpTargetPos = null
                }
                return
            }
        }

        // Clean target lock & stop chase at attack range (prevents ranged heroes walking into melee!)
        if (h.attackTargetId != null) {
            val lockTgt = findUnitById(h.attackTargetId)
            if (lockTgt == null || !lockTgt.alive) {
                h.attackTargetId = null
                h.tapMoveTarget = null
            } else if (h.isPlayer) {
                val distToTgt = h.pos.distTo(lockTgt.pos)
                if (h.tapMoveTarget != null && distToTgt <= h.cachedAttackRange * 0.90f + lockTgt.radius * 0.5f) {
                    h.tapMoveTarget = null
                } else if (h.tapMoveTarget == null && distToTgt > h.cachedAttackRange + lockTgt.radius + 240f) {
                    h.attackTargetId = null
                } else if ((abs(h.moveIntent.x) > 0.1f || abs(h.moveIntent.y) > 0.1f) && h.attackCommandAge > 0.40f) {
                    val toTgt = (lockTgt.pos - h.pos).normalized()
                    val mv = h.moveIntent.normalized()
                    if (toTgt.x * mv.x + toTgt.y * mv.y < -0.45f ||
                        (h.attackCommandAge > 0.85f && distToTgt > h.cachedAttackRange + lockTgt.radius + 40f)
                    ) {
                        h.attackTargetId = null
                    }
                }
            }
        }

        // Movement
        if (!h.isStunned() && !h.isRooted() && h.dissimilateTimer <= 0f && h.omnislashTimer <= 0f && h.dismemberTimer <= 0f) {
            if (h.isPlayer && h.tapMoveTarget != null && abs(h.moveIntent.x) <= 0.05f && abs(h.moveIntent.y) <= 0.05f) {
                val t = h.tapMoveTarget!!
                val dx = t.x - h.pos.x
                val dy = t.y - h.pos.y
                val d = hypot(dx, dy)
                if (d < 16f) {
                    h.tapMoveTarget = null
                } else {
                    moveEntityWithCollision(h, dx / d, dy / d, dt)
                    h.facingAngle = kotlin.math.atan2(dy, dx)
                }
            } else if (abs(h.moveIntent.x) > 0.05f || abs(h.moveIntent.y) > 0.05f) {
                h.tapMoveTarget = null
                val norm = h.moveIntent.normalized()
                moveEntityWithCollision(h, norm.x, norm.y, dt)
                h.facingAngle = kotlin.math.atan2(norm.y, norm.x)
            }
        }

        // Twin Gate Portal Check
        if (h.pos.distTo(MapLayout.twinGateTopLeft) < 52f && h.tpCooldown <= 0f) {
            h.pos.set(MapLayout.twinGateBotRight.x - 85f, MapLayout.twinGateBotRight.y - 85f)
            h.tpCooldown = 4f
            spawnParticles(h.pos, 0xFFA855F7.toInt(), 16, 110f)
            addFloatingText(h.pos, "TWIN GATE!", 0xFFD8B4FE.toInt())
        } else if (h.pos.distTo(MapLayout.twinGateBotRight) < 52f && h.tpCooldown <= 0f) {
            h.pos.set(MapLayout.twinGateTopLeft.x + 85f, MapLayout.twinGateTopLeft.y + 85f)
            h.tpCooldown = 4f
            spawnParticles(h.pos, 0xFFA855F7.toInt(), 16, 110f)
            addFloatingText(h.pos, "TWIN GATE!", 0xFFD8B4FE.toInt())
        }

        // Rune pickup
        for (r in runes) {
            if (r.alive && r.pos.distTo(h.pos) < 56f) {
                consumeRune(h, r)
            }
        }

        // Dropped item pickup (Divine Rapier, Aegis, Cheese!)
        val iterDrop = droppedItems.iterator()
        while (iterDrop.hasNext()) {
            val drop = iterDrop.next()
            if (drop.pos.distTo(h.pos) < 56f) {
                when (drop.itemId) {
                    "aegis" -> {
                        if (!h.hasAegis) {
                            h.hasAegis = true
                            iterDrop.remove()
                            addAnnouncement("${h.def.name} picked up AEGIS OF THE IMMORTAL!", "Automatic resurrection on death!", 0xFFFFD700.toInt())
                            if (h.isPlayer) SoundEngine.play(SoundCue.RUNE_PICKUP)
                        }
                    }
                    "cheese" -> {
                        h.hp = h.maxHp
                        h.mana = h.maxMana
                        iterDrop.remove()
                        addFloatingText(h.pos, "CHEESE +FULL HP/MP!", 0xFFF1C40F.toInt())
                    }
                    "refresher_shard" -> {
                        for (i in 0..3) h.abilityCooldowns[i] = 0f
                        iterDrop.remove()
                        addFloatingText(h.pos, "REFRESHER SHARD!", 0xFF2ECC71.toInt())
                    }
                    "divine_rapier" -> {
                        if (h.inventory.size < 6) {
                            h.inventory.add(ItemSlot("divine_rapier"))
                            h.recalcStats()
                            iterDrop.remove()
                            addAnnouncement("${h.def.name} claimed DIVINE RAPIER!", "+350 Attack Damage!", 0xFFFFD700.toInt())
                        }
                    }
                }
            }
        }

        // Auto-Attack
        if (h.attackCooldown > 0f) h.attackCooldown = (h.attackCooldown - dt).coerceAtLeast(0f)
        if (!h.isStunned() && !h.isDisarmed() && h.omnislashTimer <= 0f && h.dismemberTimer <= 0f && h.freezingFieldTimer <= 0f) {
            performHeroAutoAttack(h)
        }
    }

    private fun updateHeroOngoingSpells(h: HeroEntity, dt: Float) {
        // Juggernaut Blade Fury
        if (h.hasStatus(StatusType.BLADE_FURY)) {
            h.bladeFuryTickAccum += dt
            if (h.bladeFuryTickAccum >= 0.25f) {
                h.bladeFuryTickAccum -= 0.25f
                val lvl = h.abilityLevels[0].coerceAtLeast(1)
                val dps = h.def.abilities[0].baseValues[lvl - 1] * (if (h.hasTalent("spec_20")) 1.2f else 1f)
                val rad = 210f + (if (h.hasShard) 100f else 0f)
                for (enemy in allEnemyUnits(h.team, h.pos, rad)) {
                    applyDamage(enemy, dps * 0.25f, DamageType.MAGICAL, h, showFloat = false)
                }
                spawnParticles(h.pos, 0xFFF39C12.toInt(), 4, rad * 0.5f, ring = true)
            }
        }

        // Juggernaut Omnislash
        if (h.omnislashTimer > 0f) {
            h.omnislashTimer -= dt
            h.omnislashSlashAccum += dt
            if (h.omnislashSlashAccum >= 0.28f) {
                h.omnislashSlashAccum -= 0.28f
                val target = findUnitById(h.omnislashTargetId)
                    ?.takeIf { it.alive && it.team != h.team && it.pos.distTo(h.pos) < 420f }
                    ?: allEnemyUnits(h.team, h.pos, 380f).firstOrNull()
                if (target != null) {
                    h.pos.set(target.pos.x + rng.nextInt(-30, 31), target.pos.y + rng.nextInt(-30, 31))
                    h.omnislashTargetId = target.id
                    val slashDmg = h.cachedDamage * 1.15f + 50f
                    applyDamage(target, slashDmg, DamageType.PHYSICAL, h)
                    spawnParticles(target.pos, 0xFFFFD700.toInt(), 8, 90f)
                } else {
                    h.omnislashTimer = 0f
                }
            }
        }

        // Pudge Rot
        if (h.rotEnabled && h.abilityLevels[1] > 0) {
            if (h.isSilenced()) {
                h.rotEnabled = false
            } else {
                h.rotTickAccum += dt
                if (h.rotTickAccum >= 0.25f) {
                    h.rotTickAccum -= 0.25f
                    val lvl = h.abilityLevels[1]
                    val dps = h.def.abilities[1].baseValues[lvl - 1] + (if (h.hasScepter) 75f else 0f)
                    val rad = 210f + (if (h.hasScepter) 100f else 0f)
                    for (enemy in allEnemyUnits(h.team, h.pos, rad)) {
                        applyDamage(enemy, dps * 0.25f, DamageType.MAGICAL, h, showFloat = false)
                        enemy.addStatus(StatusType.SLOW, 0.5f, 0.24f, h.team, h.id)
                    }
                    // Self damage (reduced by magic resist, cannot deny self unless < 10 hp check)
                    val selfDmg = dps * 0.25f * DotaMath.magicMultiplier(h.magicResist) * 0.65f
                    h.hp = (h.hp - selfDmg).coerceAtLeast(1f)
                    spawnParticles(h.pos, 0xFF27AE60.toInt(), 3, rad * 0.6f, ring = true)
                }
            }
        }

        // Pudge Dismember
        if (h.dismemberTimer > 0f) {
            val target = findUnitById(h.dismemberTargetId)
            if (target == null || !target.alive || h.isStunned() || target.pos.distTo(h.pos) > 320f) {
                h.dismemberTimer = 0f
            } else {
                h.dismemberTimer -= dt
                h.dismemberAccum += dt
                if (h.dismemberAccum >= 0.4f) {
                    h.dismemberAccum -= 0.4f
                    val lvl = h.abilityLevels[3].coerceAtLeast(1)
                    val totalDmg = h.def.abilities[3].baseValues[lvl - 1] + h.cachedStr * 0.8f
                    val chunk = totalDmg * 0.18f
                    val dealt = applyDamage(target, chunk, DamageType.MAGICAL, h)
                    h.hp = (h.hp + dealt).coerceAtMost(h.maxHp)
                    spawnParticles(target.pos, 0xFFC0392B.toInt(), 6, 70f)
                }
            }
        }

        // CM Freezing Field
        if (h.freezingFieldTimer > 0f) {
            if (h.isStunned() || h.isSilenced()) {
                h.freezingFieldTimer = 0f
            } else {
                h.freezingFieldTimer -= dt
                h.freezingFieldAccum += dt
                if (h.freezingFieldAccum >= 0.3f) {
                    h.freezingFieldAccum -= 0.3f
                    val lvl = h.abilityLevels[3].coerceAtLeast(1)
                    val dps = h.def.abilities[3].baseValues[lvl - 1] + (if (h.hasTalent("spec_ff")) 140f else 0f)
                    for (enemy in allEnemyUnits(h.team, h.pos, 480f)) {
                        applyDamage(enemy, dps * 0.3f, DamageType.MAGICAL, h, showFloat = false)
                        enemy.addStatus(StatusType.SLOW, 0.8f, 0.40f, h.team, h.id)
                    }
                    spawnParticles(h.pos, 0xFF7DD3FC.toInt(), 10, 440f, ring = true)
                }
            }
        }

        // Void Spirit Dissimilate
        if (h.dissimilateTimer > 0f) {
            h.dissimilateTimer -= dt
            if (h.dissimilateTimer <= 0f) {
                h.dissimilateTarget?.let { h.pos.set(it.x, it.y) }
                h.dissimilateTarget = null
                val lvl = h.abilityLevels[1].coerceAtLeast(1)
                val dmg = h.def.abilities[1].baseValues[lvl - 1] + (if (h.hasShard) 100f else 0f)
                for (enemy in allEnemyUnits(h.team, h.pos, 260f)) {
                    applyDamage(enemy, dmg, DamageType.MAGICAL, h)
                }
                spawnParticles(h.pos, 0xFF9B59B6.toInt(), 18, 240f, ring = true)
            }
        }
    }

    private fun performHeroAutoAttack(h: HeroEntity) {
        val target = findUnitById(h.attackTargetId)?.takeIf {
            it.alive && it.pos.distTo(h.pos) <= h.cachedAttackRange + it.radius + 25f && !it.isInvulnerable()
        } ?: return

        if (target.team == h.team && target is CreepEntity && target.hp >= target.maxHp * 0.5f) return
        if (target.team == h.team && target !is CreepEntity && target !is TowerEntity) return
        if (target.team == h.team && target is TowerEntity && target.hp >= target.maxHp * 0.1f) return
        if (target.isInvisible() && !fogOfWar.hasTrueSight(target.pos.x, target.pos.y) && target.team != h.team) return

        if (h.attackCooldown > 0f) return
        h.facingAngle = h.pos.angleTo(target.pos)
        h.attackCooldown = h.def.baseAttackTime / (h.cachedAttackSpeed / 100f)
        h.attackAnimDuration = minOf(0.34f, h.attackCooldown * 0.72f).coerceAtLeast(0.20f)
        h.attackAnimTimer = h.attackAnimDuration
        h.attackComboStep = (h.attackComboStep + 1) % 3
        h.attackComboResetTimer = 1.9f

        // Breaking invisibility on attack (Shadow Blade bonus!)
        var bonusBackstab = 0f
        if (h.hasStatus(StatusType.INVISIBLE)) {
            h.statuses.removeAll { it.type == StatusType.INVISIBLE }
            if (h.hasItem("shadow_blade")) bonusBackstab = 175f
        }
        h.statuses.removeAll { it.type == StatusType.SMOKE }

        var dmg = h.cachedDamage + bonusBackstab
        if (target is CreepEntity && h.hasItem("quelling_blade")) dmg += 12f

        // Juggernaut innate
        if (h.def.id == "juggernaut" && target.hp < target.maxHp * 0.4f) {
            dmg *= 1.15f
        }

        // Earthshaker Enchant Totem
        if (h.enchantTotemBuff) {
            val lvl = h.abilityLevels[1].coerceAtLeast(1)
            dmg *= h.def.abilities[1].baseValues[lvl - 1]
            h.enchantTotemBuff = false
            addFloatingText(target.pos, "TOTEM SLAM!", 0xFFF39C12.toInt())
        }

        // Sniper Headshot
        if (h.def.id == "sniper" && h.abilityLevels[1] > 0) {
            val proc = if (h.hasStatus(StatusType.PHASE)) 1.0f else 0.40f
            if (rng.nextFloat() < proc) {
                dmg += h.def.abilities[1].baseValues[h.abilityLevels[1] - 1]
                val kb = Vec2.fromAngle(h.facingAngle, if (h.hasTalent("spec_hs")) 55f else 24f)
                target.pos.x = (target.pos.x + kb.x).coerceIn(80f, MapLayout.SIZE - 80f)
                target.pos.y = (target.pos.y + kb.y).coerceIn(80f, MapLayout.SIZE - 80f)
            }
        }

        // Crit roll (every 3rd combo finisher gets +8% bonus crit intuition too!)
        var isCrit = false
        if (h.cachedCritChance > 0f && target !is TowerEntity && target !is AncientEntity && target !is BarracksEntity) {
            if (rng.nextFloat() < h.cachedCritChance) {
                dmg *= h.cachedCritMult
                isCrit = true
            }
        }
        h.lastHitWasCrit = isCrit

        if (h.def.isRanged) {
            projectiles.add(
                Projectile(
                    id = uid("prj"),
                    team = h.team,
                    sourceId = h.id,
                    pos = h.pos.copyVec(),
                    targetId = target.id,
                    speed = h.def.projectileSpeed,
                    damage = dmg,
                    damageType = DamageType.PHYSICAL,
                    color = if (isCrit) 0xFFE74C3C.toInt() else h.def.accentColor,
                    radius = if (isCrit) 11f else 7f
                )
            )
            if (h.isPlayer) SoundEngine.play(SoundCue.ATTACK_RANGED)
        } else {
            resolvePhysicalAttackHit(h, target, dmg, isCrit)
            if (h.isPlayer && !isCrit) SoundEngine.play(SoundCue.ATTACK_MELEE)
        }
    }

    private fun resolvePhysicalAttackHit(attacker: HeroEntity, target: CombatUnit, rawDmg: Float, isCrit: Boolean) {
        if (!target.alive || target.isInvulnerable()) return

        // High-ground miss check (25% uphill miss for ranged, or evasion!)
        if (attacker.def.isRanged && !MapLayout.isHighGround(attacker.pos.x, attacker.pos.y) && MapLayout.isHighGround(target.pos.x, target.pos.y)) {
            if (rng.nextFloat() < 0.25f) {
                addFloatingText(target.pos, "MISS (High Ground)", 0xFF94A3B8.toInt())
                return
            }
        }
        if (target is HeroEntity && target.cachedEvasion > 0f && rng.nextFloat() < target.cachedEvasion) {
            addFloatingText(target.pos, "EVADE", 0xFFCBD5E1.toInt())
            return
        }

        val dealt = applyDamage(target, rawDmg, DamageType.PHYSICAL, attacker, showFloat = !isCrit)
        if (isCrit) {
            addFloatingText(target.pos, "CRIT ${dealt.toInt()}!", 0xFFFF4D4D.toInt(), life = 1.15f, scale = 1.65f)
            spawnParticles(target.pos, 0xFFEF4444.toInt(), 14, 115f)
            if (attacker.isPlayer) {
                SoundEngine.play(SoundCue.CRIT_HIT)
                pendingCameraShake = (pendingCameraShake + 0.40f).coerceAtMost(1.0f)
            }
        } else if (attacker.isPlayer || (target is HeroEntity && target.isPlayer)) {
            spawnParticles(target.pos, 0xFFFDE047.toInt(), 7, 65f)
        }

        // Lifesteal
        if (attacker.cachedLifesteal > 0f && target !is TowerEntity && target !is AncientEntity) {
            attacker.hp = (attacker.hp + dealt * attacker.cachedLifesteal).coerceAtMost(attacker.maxHp)
        }

        // Desolator Armor Corruption
        if (attacker.hasItem("desolator")) {
            target.armorCorruption = maxOf(target.armorCorruption, 6f)
            target.armorCorruptionTimer = 7f
        }

        // Skadi slow
        if (attacker.hasItem("skadi")) {
            target.addStatus(StatusType.SLOW, 2.5f, 0.30f, attacker.team, attacker.id)
        }

        // Anti-Mage Mana Break
        if (attacker.def.id == "antimage" && attacker.abilityLevels[0] > 0 && target.maxMana > 0f) {
            val burn = attacker.def.abilities[0].baseValues[attacker.abilityLevels[0] - 1]
            val burned = minOf(target.mana, burn)
            target.mana -= burned
            applyDamage(target, burned * 0.6f, DamageType.PHYSICAL, attacker, showFloat = false)
        }

        // Chronos Time Lock
        if (attacker.def.id == "chronos" && attacker.abilityLevels[2] > 0 && target !is TowerEntity && rng.nextFloat() < 0.22f) {
            val bonus = attacker.def.abilities[2].baseValues[attacker.abilityLevels[2] - 1]
            target.addStatus(StatusType.STUN, 0.55f, sourceTeam = attacker.team, sourceId = attacker.id)
            applyDamage(target, bonus, DamageType.PURE, attacker)
        }

        // Battle Fury Cleave
        if (attacker.cachedCleave > 0f && !attacker.def.isRanged) {
            val cleaveDmg = rawDmg * attacker.cachedCleave
            for (other in allEnemyUnits(attacker.team, target.pos, 220f)) {
                if (other.id != target.id) {
                    applyDamage(other, cleaveDmg, DamageType.PHYSICAL, attacker, showFloat = false)
                }
            }
        }

        // Maelstrom Chain Lightning
        if (attacker.hasItem("maelstrom") && rng.nextFloat() < 0.30f) {
            var bounces = 0
            for (other in allEnemyUnits(attacker.team, target.pos, 380f)) {
                if (bounces++ >= 4) break
                applyDamage(other, 135f, DamageType.MAGICAL, attacker)
                spawnParticles(other.pos, 0xFF38BDF8.toInt(), 5, 70f)
            }
        }

        // Axe Counter Helix trigger when attacked!
        if (target is HeroEntity && target.def.id == "axe" && target.abilityLevels[2] > 0) {
            val chance = if (target.hasShard) 0.32f else 0.22f
            if (rng.nextFloat() < chance) {
                val lvl = target.abilityLevels[2]
                val spinDmg = target.def.abilities[2].baseValues[lvl - 1] + (if (target.hasTalent("spec_helix")) 40f else 0f)
                for (enemy in allEnemyUnits(target.team, target.pos, 220f)) {
                    applyDamage(enemy, spinDmg, DamageType.PURE, target)
                }
                spawnParticles(target.pos, 0xFFE74C3C.toInt(), 12, 210f, ring = true)
            }
        }

        // Tormentor 70% damage reflection!
        if (target is TormentorEntity) {
            applyDamage(attacker, dealt * 0.70f, DamageType.PURE, null, showFloat = false)
        }

        // Blink Dagger mute on hero damage
        if (target is HeroEntity) {
            target.inventory.firstOrNull { it.itemId == "blink_dagger" }?.let {
                it.cooldownRemaining = maxOf(it.cooldownRemaining, 3.0f)
            }
        }
    }

    private fun updateCreeps(dt: Float) {
        for (c in creeps) {
            if (!c.alive) continue
            tickUnitStatuses(c, dt)
            if (c.attackCooldown > 0f) c.attackCooldown = (c.attackCooldown - dt).coerceAtLeast(0f)
            if (c.attackAnimTimer > 0f) c.attackAnimTimer = (c.attackAnimTimer - dt).coerceAtLeast(0f)
            if (c.isStunned()) continue

            val wpList = MapLayout.laneWaypoints[c.lane.coerceIn(0, 2)]
            val enemyUnits = allEnemyUnits(c.team, c.pos, 460f)
            val target = enemyUnits.minByOrNull {
                val prio = if (it is CreepEntity) 0f else if (it is TowerEntity) 50f else 100f
                c.pos.distTo(it.pos) + prio
            }

            if (target != null) {
                val dist = c.pos.distTo(target.pos)
                if (dist <= c.attackRange + target.radius + 10f) {
                    c.facingAngle = c.pos.angleTo(target.pos)
                    if (c.attackCooldown <= 0f && !c.isDisarmed()) {
                        c.attackCooldown = 1.0f
                        c.attackAnimDuration = 0.30f
                        c.attackAnimTimer = 0.30f
                        if (c.attackRange > 200f) {
                            projectiles.add(
                                Projectile(
                                    id = uid("prj"),
                                    team = c.team,
                                    sourceId = c.id,
                                    pos = c.pos.copyVec(),
                                    targetId = target.id,
                                    speed = 750f,
                                    damage = c.attackDamage,
                                    damageType = DamageType.PHYSICAL,
                                    color = if (c.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(),
                                    radius = if (c.type == CreepType.SIEGE) 9f else 5f
                                )
                            )
                        } else {
                            applyDamage(target, c.attackDamage, DamageType.PHYSICAL, null, showFloat = false)
                        }
                    }
                } else {
                    val dir = (target.pos - c.pos).normalized()
                    moveEntitySimple(c, dir.x, dir.y, dt)
                    c.walkCycle += dt * 10f
                }
            } else if (!c.isRooted()) {
                val wp = wpList[c.waypointIndex.coerceIn(0, wpList.lastIndex)]
                val dist = c.pos.distTo(wp)
                if (dist < 45f) {
                    if (c.team == Team.RADIANT && c.waypointIndex < wpList.lastIndex) c.waypointIndex++
                    else if (c.team == Team.DIRE && c.waypointIndex > 0) c.waypointIndex--
                } else {
                    val dir = (wp - c.pos).normalized()
                    moveEntitySimple(c, dir.x, dir.y, dt)
                    c.walkCycle += dt * 10f
                }
            }
        }
        creeps.removeAll { !it.alive }
    }

    private fun updateNeutrals(dt: Float) {
        for (n in neutrals) {
            if (!n.alive) continue
            tickUnitStatuses(n, dt)
            if (n.attackCooldown > 0f) n.attackCooldown = (n.attackCooldown - dt).coerceAtLeast(0f)
            if (n.attackAnimTimer > 0f) n.attackAnimTimer = (n.attackAnimTimer - dt).coerceAtLeast(0f)
            if (n.isStunned()) continue

            val distHome = n.pos.distTo(n.homePos)
            val attacker = heroes.filter { it.alive && it.pos.distTo(n.pos) < 340f && !it.isInvisible() }
                .minByOrNull { it.pos.distTo(n.pos) }

            if (attacker != null && distHome < 520f && (n.hp < n.maxHp || attacker.pos.distTo(n.pos) < 220f)) {
                val d = n.pos.distTo(attacker.pos)
                if (d <= 125f) {
                    n.facingAngle = n.pos.angleTo(attacker.pos)
                    if (n.attackCooldown <= 0f) {
                        n.attackCooldown = 1.25f
                        n.attackAnimDuration = 0.34f
                        n.attackAnimTimer = 0.34f
                        applyDamage(attacker, n.attackDamage, DamageType.PHYSICAL, null, showFloat = false)
                    }
                } else if (!n.isRooted()) {
                    val dir = (attacker.pos - n.pos).normalized()
                    moveEntitySimple(n, dir.x, dir.y, dt)
                    n.walkCycle += dt * 9f
                }
            } else if (distHome > 25f && !n.isRooted()) {
                val dir = (n.homePos - n.pos).normalized()
                moveEntitySimple(n, dir.x, dir.y, dt)
                n.walkCycle += dt * 9f
                n.hp = (n.hp + 20f * dt).coerceAtMost(n.maxHp)
            }
        }
        neutrals.removeAll { !it.alive }
    }

    private fun updateRoshan(dt: Float) {
        if (!roshan.alive) {
            roshan.respawnTimer -= dt
            if (roshan.respawnTimer <= 0f) {
                roshan.respawnScaled()
                addAnnouncement("ROSHAN HAS RESPAWNED!", "The Immortal waits in the pit.", 0xFFFF9800.toInt())
            }
            return
        }
        tickUnitStatuses(roshan, dt)
        if (roshan.attackCooldown > 0f) roshan.attackCooldown = (roshan.attackCooldown - dt).coerceAtLeast(0f)
        if (roshan.slamCooldown > 0f) roshan.slamCooldown = (roshan.slamCooldown - dt).coerceAtLeast(0f)
        if (roshan.attackAnimTimer > 0f) roshan.attackAnimTimer = (roshan.attackAnimTimer - dt).coerceAtLeast(0f)

        val target = heroes.filter { it.alive && it.pos.distTo(MapLayout.roshanPitPos) < 260f && !it.isInvisible() }
            .minByOrNull { it.pos.distTo(roshan.pos) }
        if (target != null && !roshan.isStunned()) {
            val d = roshan.pos.distTo(target.pos)
            if (d <= 155f) {
                roshan.facingAngle = roshan.pos.angleTo(target.pos)
                if (roshan.slamCooldown <= 0f) {
                    roshan.slamCooldown = 7f
                    roshan.attackAnimDuration = 0.42f
                    roshan.attackAnimTimer = 0.42f
                    for (h in heroes.filter { it.alive && it.pos.distTo(roshan.pos) < 240f }) {
                        applyDamage(h, 130f + roshan.deathsCount * 40f, DamageType.MAGICAL, null)
                        h.addStatus(StatusType.SLOW, 2.0f, 0.35f)
                    }
                    spawnParticles(roshan.pos, 0xFFFF5722.toInt(), 16, 220f, ring = true)
                } else if (roshan.attackCooldown <= 0f) {
                    roshan.attackCooldown = 0.85f
                    roshan.attackAnimDuration = 0.34f
                    roshan.attackAnimTimer = 0.34f
                    applyDamage(target, roshan.attackDamage, DamageType.PHYSICAL, null)
                    if (rng.nextFloat() < 0.16f) target.addStatus(StatusType.STUN, 1.0f)
                }
            } else {
                val dir = (target.pos - roshan.pos).normalized()
                moveEntitySimple(roshan, dir.x, dir.y, dt)
                roshan.walkCycle += dt * 7f
            }
        } else if (roshan.pos.distTo(MapLayout.roshanPitPos) > 30f) {
            val dir = (MapLayout.roshanPitPos - roshan.pos).normalized()
            moveEntitySimple(roshan, dir.x, dir.y, dt)
            roshan.walkCycle += dt * 7f
            roshan.hp = (roshan.hp + 55f * dt).coerceAtMost(roshan.maxHp)
        }
    }

    private fun updateTormentors(dt: Float) {
        for (tor in listOf(radiantTormentor, direTormentor)) {
            if (!tor.alive) {
                tor.respawnTimer -= dt
                if (tor.respawnTimer <= 0f) {
                    tor.alive = true
                    tor.hp = tor.maxHp
                }
            }
        }
    }

    private fun updateTowersAndBase(dt: Float) {
        towerAuraAccum += dt
        for (t in towers) {
            if (!t.alive) continue
            if (t.fortifyTimer > 0f) t.fortifyTimer = (t.fortifyTimer - dt).coerceAtLeast(0f)
            if (t.attackCooldown > 0f) t.attackCooldown = (t.attackCooldown - dt).coerceAtLeast(0f)

            // Backdoor Protection for T2+ towers
            if (t.blueprint.tier >= 2) {
                val enemyCreepNear = creeps.any { it.alive && it.team != t.team && it.pos.distTo(t.pos) < 820f }
                if (!enemyCreepNear && t.hp < t.maxHp) {
                    t.hp = (t.hp + 90f * dt).coerceAtMost(t.maxHp)
                }
            }

            if (t.attackCooldown <= 0f) {
                val enemies = allEnemyUnits(t.team, t.pos, t.attackRange).filter { !it.isInvisible() || true }
                if (enemies.isNotEmpty()) {
                    // Prioritize creeps over heroes unless enemy hero attacked allied hero nearby
                    val creepTarget = enemies.filterIsInstance<CreepEntity>().minByOrNull { t.pos.distTo(it.pos) }
                    val heroTarget = enemies.filterIsInstance<HeroEntity>().minByOrNull { t.pos.distTo(it.pos) }
                    val mainTarget = creepTarget ?: heroTarget
                    if (mainTarget != null) {
                        t.attackCooldown = 1.05f
                        fireTowerShot(t, mainTarget)
                        // Fortified multishot!
                        if (t.fortifyTimer > 0f) {
                            enemies.filter { it.id != mainTarget.id }.take(2).forEach { extra ->
                                fireTowerShot(t, extra)
                            }
                        }
                    }
                }
            }
        }

        // Fountain laser kills enemy heroes trying to fountain-dive!
        for (team in listOf(Team.RADIANT, Team.DIRE)) {
            val fp = fountainPos(team)
            for (h in heroes) {
                if (h.alive && h.team != team && h.pos.distTo(fp) < 420f) {
                    applyDamage(h, 320f * dt, DamageType.PURE, null, showFloat = false)
                }
            }
        }
    }

    private fun fireTowerShot(t: TowerEntity, target: CombatUnit) {
        projectiles.add(
            Projectile(
                id = uid("prj"),
                team = t.team,
                sourceId = t.id,
                pos = Vec2(t.pos.x, t.pos.y - 25f),
                targetId = target.id,
                speed = 920f,
                damage = t.attackDamage,
                damageType = DamageType.PHYSICAL,
                color = if (t.team == Team.RADIANT) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(),
                radius = 9f,
                kind = "tower"
            )
        )
        if (target is HeroEntity && target.isPlayer) SoundEngine.play(SoundCue.TOWER_SHOT)
    }

    private fun updateCouriers(dt: Float) {
        for (c in couriers) {
            if (!c.alive) {
                c.respawnTimer -= dt
                if (c.respawnTimer <= 0f) {
                    c.alive = true
                    c.hp = 150f
                    c.pos.set(c.homePos.x, c.homePos.y)
                }
                continue
            }
            val owner = heroes.firstOrNull { it.id == c.heroId } ?: continue
            if (c.delivering) {
                if (!owner.alive) {
                    c.delivering = false
                    c.returning = true
                    continue
                }
                val d = c.pos.distTo(owner.pos)
                if (d < 48f) {
                    var deliveredAny = false
                    val iter = c.cargo.iterator()
                    while (iter.hasNext()) {
                        val item = iter.next()
                        if (addOrStackToInventory(owner, item)) {
                            iter.remove()
                            deliveredAny = true
                        }
                    }
                    val stIter = owner.stash.iterator()
                    while (stIter.hasNext()) {
                        val item = stIter.next()
                        if (addOrStackToInventory(owner, item)) {
                            stIter.remove()
                            deliveredAny = true
                        }
                    }
                    // Keep undelivered cargo safely in stash if inventory is full
                    val cargoRest = c.cargo.iterator()
                    while (cargoRest.hasNext() && owner.stash.size < 6) {
                        owner.stash.add(cargoRest.next())
                        cargoRest.remove()
                    }
                    owner.recalcStats()
                    c.delivering = false
                    c.returning = true
                    if (owner.isPlayer && deliveredAny) {
                        addFloatingText(owner.pos, "Courier Delivered!", 0xFF38BDF8.toInt())
                        SoundEngine.play(SoundCue.BUY_ITEM)
                    } else if (owner.isPlayer && (owner.stash.isNotEmpty() || c.cargo.isNotEmpty())) {
                        addFloatingText(owner.pos, "Inventory Full (In Stash)", 0xFFF59E0B.toInt())
                    }
                } else {
                    val dir = (owner.pos - c.pos).normalized()
                    c.pos.x += dir.x * c.speed * dt
                    c.pos.y += dir.y * c.speed * dt
                }
            } else if (c.returning) {
                val d = c.pos.distTo(c.homePos)
                if (d < 35f) {
                    c.returning = false
                } else {
                    val dir = (c.homePos - c.pos).normalized()
                    c.pos.x += dir.x * c.speed * dt
                    c.pos.y += dir.y * c.speed * dt
                }
            }
        }
    }

    private fun updateWardsAndItems(dt: Float) {
        val wIter = wards.iterator()
        while (wIter.hasNext()) {
            val w = wIter.next()
            w.remaining -= dt
            if (w.remaining <= 0f) wIter.remove()
        }
        val dIter = droppedItems.iterator()
        while (dIter.hasNext()) {
            val d = dIter.next()
            d.ttl -= dt
            if (d.ttl <= 0f) dIter.remove()
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iter = projectiles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (!p.alive) {
                iter.remove()
                continue
            }
            if (p.trail.isEmpty() || p.trail.first().distTo(p.pos) >= 16f) {
                p.trail.add(0, p.pos.copyVec())
                if (p.trail.size > 6) p.trail.removeAt(p.trail.lastIndex)
            }
            if (p.isSkillshot) {
                val step = p.velocity * dt
                p.pos.x += step.x
                p.pos.y += step.y
                p.traveled += hypot(step.x, step.y)
                if (p.traveled >= p.maxRange) {
                    p.alive = false
                    iter.remove()
                    continue
                }
                val shooter = heroes.firstOrNull { it.id == p.sourceId }
                // Skillshot hit check
                when (p.kind) {
                    "hook" -> {
                        val hit = (heroes.filter { it.alive && it.id != p.sourceId } + creeps.filter { it.alive })
                            .firstOrNull { it.pos.distTo(p.pos) <= it.radius + p.radius }
                        if (hit != null) {
                            if (hit.team != p.team) {
                                applyDamage(hit, p.damage, DamageType.PURE, shooter)
                                hit.addStatus(StatusType.STUN, 1.1f, sourceTeam = p.team, sourceId = p.sourceId)
                            }
                            // Pull hooked unit straight in front of Pudge!
                            shooter?.let { pud ->
                                val pullPos = pud.pos + Vec2.fromAngle(pud.facingAngle, 55f)
                                hit.pos.set(pullPos.x, pullPos.y)
                                spawnParticles(hit.pos, 0xFFC0392B.toInt(), 12, 95f)
                                addFloatingText(hit.pos, "FRESH MEAT!", 0xFFE74C3C.toInt())
                            }
                            p.alive = false
                        }
                    }
                    "meteor", "blast", "lance" -> {
                        for (enemy in allEnemyUnits(p.team, p.pos, p.radius)) {
                            if (p.hitIds.add(enemy.id)) {
                                applyDamage(enemy, p.damage, p.damageType, shooter)
                                if (p.stunDur > 0f) enemy.addStatus(StatusType.STUN, p.stunDur, sourceTeam = p.team, sourceId = p.sourceId)
                                if (p.slowDur > 0f) enemy.addStatus(StatusType.SLOW, p.slowDur, 0.35f, p.team, p.sourceId)
                                if (p.kind == "blast") {
                                    enemy.addStatus(StatusType.DISARM, 2.2f, sourceTeam = p.team, sourceId = p.sourceId)
                                }
                            }
                        }
                    }
                }
            } else {
                val target = findUnitById(p.targetId)
                if (target == null || !target.alive) {
                    p.alive = false
                    iter.remove()
                    continue
                }
                val dx = target.pos.x - p.pos.x
                val dy = target.pos.y - p.pos.y
                val dist = hypot(dx, dy)
                if (dist <= p.speed * dt + target.radius * 0.6f) {
                    val shooter = heroes.firstOrNull { it.id == p.sourceId }
                    when (p.kind) {
                        "dagger" -> {
                            applyDamage(target, p.damage, DamageType.PHYSICAL, shooter)
                            target.addStatus(StatusType.SLOW, p.slowDur, 0.45f, p.team, p.sourceId)
                        }
                        "assassinate" -> {
                            applyDamage(target, p.damage, DamageType.MAGICAL, shooter)
                            target.addStatus(StatusType.STUN, p.stunDur, sourceTeam = p.team, sourceId = p.sourceId)
                            spawnParticles(target.pos, 0xFFF1C40F.toInt(), 14, 100f)
                        }
                        else -> {
                            if (shooter != null && p.damageType == DamageType.PHYSICAL && p.kind == "basic") {
                                resolvePhysicalAttackHit(shooter, target, p.damage, isCrit = p.radius > 10f)
                            } else {
                                applyDamage(target, p.damage, p.damageType, shooter, showFloat = false)
                            }
                        }
                    }
                    p.alive = false
                    iter.remove()
                } else {
                    p.pos.x += (dx / dist) * p.speed * dt
                    p.pos.y += (dy / dist) * p.speed * dt
                }
            }
        }
    }

    private fun updateZones(dt: Float) {
        val iter = zones.iterator()
        while (iter.hasNext()) {
            val z = iter.next()
            z.remaining -= dt
            val caster = heroes.firstOrNull { it.id == z.sourceId }

            when (z.kind) {
                "sunstrike" -> {
                    if (z.remaining <= 0.1f && !z.triggered) {
                        z.triggered = true
                        val targets = heroes.filter { it.alive && it.team != z.team && it.pos.distTo(z.pos) <= z.radius }
                        val splitDmg = if (targets.isNotEmpty()) z.burstDamage / targets.size else z.burstDamage
                        for (t in targets) applyDamage(t, splitDmg, DamageType.PURE, caster)
                        spawnParticles(z.pos, 0xFFFF9800.toInt(), 20, z.radius, ring = true)
                    }
                }
                "emp" -> {
                    if (z.remaining <= 0.1f && !z.triggered) {
                        z.triggered = true
                        for (enemy in allEnemyUnits(z.team, z.pos, z.radius)) {
                            val burned = minOf(enemy.mana, z.burstDamage * 0.7f)
                            enemy.mana -= burned
                            applyDamage(enemy, z.burstDamage + burned * 0.5f, DamageType.MAGICAL, caster)
                            enemy.addStatus(StatusType.STUN, 1.9f, sourceTeam = z.team, sourceId = z.sourceId)
                        }
                        spawnParticles(z.pos, 0xFFA855F7.toInt(), 24, z.radius, ring = true)
                    }
                }
                "shrapnel", "time_rift" -> {
                    z.tickAccum += dt
                    if (z.tickAccum >= 0.4f) {
                        z.tickAccum -= 0.4f
                        for (enemy in allEnemyUnits(z.team, z.pos, z.radius)) {
                            applyDamage(enemy, z.dps * 0.4f, DamageType.MAGICAL, caster, showFloat = false)
                            enemy.addStatus(StatusType.SLOW, 0.7f, 0.30f, z.team, z.sourceId)
                        }
                    }
                }
                "healing_ward" -> {
                    caster?.let {
                        val dir = (it.pos - z.pos)
                        val d = hypot(dir.x, dir.y)
                        if (d > 85f) {
                            z.pos.x += (dir.x / d) * 290f * dt
                            z.pos.y += (dir.y / d) * 290f * dt
                        }
                    }
                    z.tickAccum += dt
                    if (z.tickAccum >= 0.4f) {
                        z.tickAccum -= 0.4f
                        for (ally in heroes.filter { it.alive && it.team == z.team && it.pos.distTo(z.pos) <= z.radius }) {
                            ally.hp = (ally.hp + ally.maxHp * (z.dps / 100f) * 0.4f).coerceAtMost(ally.maxHp)
                        }
                    }
                }
                "chrono" -> {
                    // Freeze all enemies inside Chronos's Time Dome!
                    for (enemy in allEnemyUnits(z.team, z.pos, z.radius)) {
                        enemy.addStatus(StatusType.CHRONO_FREEZE, 0.25f, sourceTeam = z.team, sourceId = z.sourceId)
                    }
                    if (z.dps > 0f) {
                        z.tickAccum += dt
                        if (z.tickAccum >= 0.5f) {
                            z.tickAccum -= 0.5f
                            for (enemy in allEnemyUnits(z.team, z.pos, z.radius)) {
                                applyDamage(enemy, z.dps * 0.5f, DamageType.PURE, caster, showFloat = false)
                            }
                        }
                    }
                }
                "aether_remnant" -> {
                    val caught = heroes.firstOrNull { it.alive && it.team != z.team && it.pos.distTo(z.pos) <= z.radius }
                    if (caught != null && !z.triggered) {
                        z.triggered = true
                        applyDamage(caught, z.burstDamage, DamageType.MAGICAL, caster)
                        caught.addStatus(StatusType.STUN, 1.5f, sourceTeam = z.team, sourceId = z.sourceId)
                        caught.pos.set(z.pos.x, z.pos.y)
                        z.remaining = 0f
                        spawnParticles(z.pos, 0xFF9B59B6.toInt(), 14, 140f)
                    }
                }
            }
            if (z.remaining <= 0f) iter.remove()
        }
    }

    private fun updateVisualEffects(dt: Float) {
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.life -= dt
            p.pos.x += p.vel.x * dt
            p.pos.y += p.vel.y * dt
            if (!p.ring) {
                p.height = (p.height + p.zVel * dt).coerceAtLeast(2f)
                p.zVel -= 420f * dt
            }
            if (p.life <= 0f) pIter.remove()
        }
        val tIter = floatingTexts.iterator()
        while (tIter.hasNext()) {
            val ft = tIter.next()
            ft.life -= dt
            ft.pos.y -= 28f * dt
            if (ft.life <= 0f) tIter.remove()
        }
        val aIter = announcements.iterator()
        while (aIter.hasNext()) {
            val a = aIter.next()
            a.remaining -= dt
            if (a.remaining <= 0f) aIter.remove()
        }
        val pingIter = pings.iterator()
        while (pingIter.hasNext()) {
            val pg = pingIter.next()
            pg.remaining -= dt
            if (pg.remaining <= 0f) pingIter.remove()
        }
    }

    fun castAbility(hero: HeroEntity, idx: Int, aimPoint: Vec2, targetUnitId: String? = null): Boolean {
        if (!hero.alive || hero.isStunned() || hero.isSilenced()) return false
        if (idx !in 0..3) return false
        val lvl = hero.abilityLevels[idx]
        if (lvl <= 0) return false
        val ab = hero.def.abilities[idx]
        if (ab.targetType == TargetType.PASSIVE) return false

        if (ab.targetType == TargetType.TOGGLE) {
            if (hero.def.id == "pudge") {
                hero.rotEnabled = !hero.rotEnabled
                return true
            }
        }

        if (ab.maxCharges > 1) {
            if (hero.abilityCharges[idx] <= 0) return false
        } else if (hero.abilityCooldowns[idx] > 0f) return false

        val cost = ab.manaCost[(lvl - 1).coerceIn(0, ab.manaCost.lastIndex)]
        if (hero.mana < cost) {
            if (hero.isPlayer) addFloatingText(hero.pos, "NO MANA", 0xFF3498DB.toInt())
            return false
        }

        hero.mana -= cost
        val baseCd = ab.cooldown[(lvl - 1).coerceIn(0, ab.cooldown.lastIndex)] * (1f - hero.cachedCdr)
        if (ab.maxCharges > 1) {
            hero.abilityCharges[idx]--
            if (hero.abilityChargeTimers[idx] <= 0f) hero.abilityChargeTimers[idx] = baseCd
        } else {
            hero.abilityCooldowns[idx] = baseCd
        }

        // Magic Wand stick charge for nearby enemy heroes!
        for (other in heroes) {
            if (other.alive && other.team != hero.team && other.pos.distTo(hero.pos) < 750f) {
                other.inventory.firstOrNull { it.itemId == "magic_wand" }?.let {
                    it.charges = (it.charges + 1).coerceAtMost(20)
                }
            }
        }

        // Earthshaker Aftershock passive!
        if (hero.def.id == "earthshaker" && hero.abilityLevels[2] > 0) {
            val asLvl = hero.abilityLevels[2]
            val asDmg = hero.def.abilities[2].baseValues[asLvl - 1]
            val asDur = hero.def.abilities[2].duration[asLvl - 1]
            for (enemy in allEnemyUnits(hero.team, hero.pos, 240f)) {
                applyDamage(enemy, asDmg, DamageType.MAGICAL, hero)
                enemy.addStatus(StatusType.STUN, asDur, sourceTeam = hero.team, sourceId = hero.id)
            }
            spawnParticles(hero.pos, 0xFF5DADE2.toInt(), 12, 230f, ring = true)
        }

        // Zeus Static Field innate!
        if (hero.def.id == "zeus") {
            for (enemy in allEnemyUnits(hero.team, hero.pos, 520f)) {
                applyDamage(enemy, enemy.hp * 0.06f, DamageType.MAGICAL, hero, showFloat = false)
            }
        }

        val value = ab.baseValues[(lvl - 1).coerceIn(0, ab.baseValues.lastIndex)]
        val dur = ab.duration[(lvl - 1).coerceIn(0, ab.duration.lastIndex)]
        val angle = hero.pos.angleTo(aimPoint)
        hero.facingAngle = angle
        hero.castAnimTimer = 0.32f

        if (hero.isPlayer) {
            SoundEngine.play(if (ab.isUltimate) SoundCue.ULTIMATE else SoundCue.SPELL_CAST)
            if (ab.isUltimate) {
                pendingCameraShake = (pendingCameraShake + 0.65f).coerceAtMost(1.0f)
            }
            if (ab.castRange >= 720f && (ab.targetType == TargetType.DIRECTION_SKILLSHOT || ab.targetType == TargetType.POINT_AOE)) {
                lastSkillshotAimPos = clampPoint(hero.pos, aimPoint, ab.castRange)
                lastSkillshotHoldTimer = 0.60f
            }
        }

        when (ab.id) {
            // JUGGERNAUT
            "jugg_q" -> hero.addStatus(StatusType.BLADE_FURY, dur, sourceTeam = hero.team, sourceId = hero.id)
            "jugg_w" -> zones.add(AoEZone(uid("zn"), hero.team, hero.id, hero.pos.copyVec(), 360f, dur, "healing_ward", dps = value, color = 0xFF2ECC71.toInt()))
            "jugg_r" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    hero.omnislashTargetId = target.id
                    hero.omnislashTimer = dur + (if (hero.hasTalent("ult_1")) 1.0f else 0f) + (if (hero.hasScepter) 0.8f else 0f)
                    hero.omnislashSlashAccum = 0.3f
                    hero.addStatus(StatusType.MAGIC_IMMUNE, hero.omnislashTimer)
                }
            }

            // PUDGE
            "pudge_q" -> {
                val vel = Vec2.fromAngle(angle, 1080f)
                val bonusDmg = if (hero.hasTalent("spec_hook")) 120f else 0f
                projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), velocity = vel, speed = 1080f,
                    damage = value + bonusDmg, damageType = DamageType.PURE, color = 0xFFC0392B.toInt(), radius = 44f, maxRange = ab.castRange,
                    isSkillshot = true, kind = "hook", piercesBkb = true))
            }
            "pudge_e" -> {
                hero.addStatus(StatusType.SHIELD, dur, value)
                spawnParticles(hero.pos, 0xFF2ECC71.toInt(), 10, 90f, ring = true)
            }
            "pudge_r" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, 260f)
                if (target != null) {
                    target.addStatus(StatusType.STUN, dur, sourceTeam = hero.team, sourceId = hero.id)
                    hero.dismemberTargetId = target.id
                    hero.dismemberTimer = dur
                    hero.dismemberAccum = 0.4f
                }
            }

            // INVOKER
            "inv_q" -> {
                val bonus = if (hero.hasTalent("spec_ss")) 180f else 0f
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, clampPoint(hero.pos, aimPoint, ab.castRange), ab.radius, 1.3f, "sunstrike", burstDamage = value + bonus, damageType = DamageType.PURE, color = 0xFFFF9800.toInt()))
                if (hero.hasScepter) {
                    // Cataclysm drops extra Sun Strikes on nearby enemy heroes!
                    heroes.filter { it.alive && it.team != hero.team }.take(2).forEach { eh ->
                        zones.add(AoEZone(uid("zn"), hero.team, hero.id, eh.pos.copyVec(), ab.radius, 1.4f, "sunstrike", burstDamage = value * 0.85f, damageType = DamageType.PURE, color = 0xFFFF5722.toInt()))
                    }
                }
            }
            "inv_w" -> {
                val vel = Vec2.fromAngle(angle, 420f)
                projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), velocity = vel, speed = 420f,
                    damage = value, damageType = DamageType.MAGICAL, color = 0xFFE67E22.toInt(), radius = 145f, maxRange = ab.castRange,
                    isSkillshot = true, kind = "meteor"))
            }
            "inv_e" -> {
                val vel = Vec2.fromAngle(angle, 760f)
                projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), velocity = vel, speed = 760f,
                    damage = value, damageType = DamageType.MAGICAL, color = 0xFFAB47BC.toInt(), radius = 135f, maxRange = ab.castRange,
                    isSkillshot = true, kind = "blast", stunDur = 0.8f))
            }
            "inv_r" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, p, ab.radius, 1.25f, "emp", burstDamage = value, color = 0xFFA855F7.toInt()))
            }

            // PHANTOM ASSASSIN
            "pa_q" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    val dmg = value + hero.cachedDamage * 0.6f
                    projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), targetId = target.id,
                        speed = 1150f, damage = dmg, damageType = DamageType.PHYSICAL, color = 0xFF38BDF8.toInt(), radius = 9f, kind = "dagger", slowDur = dur))
                }
            }
            "pa_w" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    hero.pos.set(target.pos.x - cos(angle) * 55f, target.pos.y - sin(angle) * 55f)
                    hero.attackTargetId = target.id
                    hero.attackCooldown = 0f
                    hero.addStatus(StatusType.HASTE, 2.0f)
                    spawnParticles(hero.pos, 0xFF2980B9.toInt(), 12, 85f)
                }
            }
            "pa_e" -> {
                hero.addStatus(StatusType.BLUR, dur)
                spawnParticles(hero.pos, 0xFF60A5FA.toInt(), 12, 100f, ring = true)
            }

            // AXE
            "axe_q" -> {
                val rad = ab.radius + (if (hero.hasTalent("spec_call")) 60f else 0f)
                hero.addStatus(StatusType.SHIELD, dur, value)
                for (enemy in allEnemyUnits(hero.team, hero.pos, rad)) {
                    enemy.addStatus(StatusType.TAUNT, dur, sourceTeam = hero.team, sourceId = hero.id)
                    if (enemy is HeroEntity) enemy.attackTargetId = hero.id
                }
                spawnParticles(hero.pos, 0xFFE74C3C.toInt(), 18, rad, ring = true)
            }
            "axe_w" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    applyDamage(target, value * 2f, DamageType.PHYSICAL, hero)
                    target.addStatus(StatusType.SLOW, dur, 0.25f, hero.team, hero.id)
                    hero.addStatus(StatusType.PHASE, dur * 0.5f)
                }
            }
            "axe_r" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, 280f)
                if (target != null) {
                    val threshold = value + (if (hero.hasTalent("spec_cull")) 150f else 0f)
                    if (target.hp <= threshold) {
                        applyDamage(target, target.hp + 999f, DamageType.PURE, hero)
                        hero.abilityCooldowns[3] = 0f // Reset Culling Blade!
                        hero.coatOfBloodStacks += 2
                        for (ally in heroes.filter { it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 550f }) {
                            ally.addStatus(StatusType.HASTE, 4f)
                        }
                        addFloatingText(target.pos, "CULLING EXECUTED!", 0xFFE74C3C.toInt(), scale = 1.4f)
                    } else {
                        applyDamage(target, threshold * 0.55f, DamageType.PURE, hero)
                    }
                    spawnParticles(target.pos, 0xFFC0392B.toInt(), 18, 120f)
                }
            }

            // CRYSTAL MAIDEN
            "cm_q" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                val bonus = if (hero.hasTalent("spec_nova")) 120f else 0f
                for (enemy in allEnemyUnits(hero.team, p, ab.radius)) {
                    applyDamage(enemy, value + bonus, DamageType.MAGICAL, hero)
                    enemy.addStatus(StatusType.SLOW, dur, 0.35f, hero.team, hero.id)
                }
                spawnParticles(p, 0xFF38BDF8.toInt(), 20, ab.radius, ring = true)
            }
            "cm_w" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    val fd = dur + (if (hero.hasTalent("spec_fb")) 1.2f else 0f)
                    applyDamage(target, value, DamageType.MAGICAL, hero)
                    target.addStatus(StatusType.ROOT, fd, sourceTeam = hero.team, sourceId = hero.id)
                    target.addStatus(StatusType.DISARM, fd, sourceTeam = hero.team, sourceId = hero.id)
                    spawnParticles(target.pos, 0xFFBAE6FD.toInt(), 14, 90f)
                }
            }
            "cm_e" -> {
                val dash = Vec2.fromAngle(angle, 240f)
                moveEntitySimple(hero, dash.x, dash.y, 1f)
                for (ally in heroes.filter { it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 600f }) {
                    ally.mana = (ally.mana + 65f + value * 12f).coerceAtMost(ally.maxMana)
                }
                spawnParticles(hero.pos, 0xFF38BDF8.toInt(), 14, 150f, ring = true)
            }
            "cm_r" -> {
                hero.freezingFieldTimer = dur
                hero.freezingFieldAccum = 0.3f
                hero.addStatus(StatusType.SHIELD, dur, 20f)
            }

            // SNIPER
            "snp_q" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                val bonus = if (hero.hasTalent("spec_shrap")) 35f else 0f
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, p, ab.radius, dur, "shrapnel", dps = value + bonus, color = 0xFFE67E22.toInt()))
            }
            "snp_e" -> {
                hero.addStatus(StatusType.PHASE, dur)
                hero.addStatus(StatusType.SHIELD, dur, 8f)
                spawnParticles(hero.pos, 0xFFF1C40F.toInt(), 10, 80f, ring = true)
            }
            "snp_r" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    val dmg = value * (if (hero.hasScepter) 1.35f else 1f)
                    projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), targetId = target.id,
                        speed = 1850f, damage = dmg, damageType = DamageType.MAGICAL, color = 0xFFF1C40F.toInt(), radius = 10f,
                        kind = "assassinate", stunDur = if (hero.hasScepter) 1.5f else 0.35f))
                }
            }

            // EARTHSHAKER
            "es_q" -> {
                val dir = Vec2.fromAngle(angle, 1f)
                val end = hero.pos + dir * ab.castRange
                // Hit units along the fissure line!
                for (enemy in allEnemyUnits(hero.team, hero.pos + dir * (ab.castRange * 0.5f), ab.castRange * 0.55f)) {
                    if (DotaMath.distPointToSegment(enemy.pos.x, enemy.pos.y, hero.pos.x, hero.pos.y, end.x, end.y) <= ab.radius) {
                        applyDamage(enemy, value, DamageType.MAGICAL, hero)
                        enemy.addStatus(StatusType.STUN, dur, sourceTeam = hero.team, sourceId = hero.id)
                    }
                }
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, hero.pos.copyVec(), 34f, 4.5f, "fissure", color = 0xFFA04000.toInt(), endPos = end))
            }
            "es_w" -> {
                if (hero.hasScepter) {
                    val p = clampPoint(hero.pos, aimPoint, 600f)
                    hero.pos.set(p.x, p.y)
                }
                hero.enchantTotemBuff = true
                spawnParticles(hero.pos, 0xFFF39C12.toInt(), 14, 110f)
            }
            "es_r" -> {
                val enemies = allEnemyUnits(hero.team, hero.pos, ab.radius)
                val base = value + (if (hero.hasTalent("spec_echo")) 80f else 0f)
                val echoPerUnit = 45f + lvl * 18f
                val totalEcho = base + enemies.size * echoPerUnit
                for (e in enemies) {
                    applyDamage(e, totalEcho, DamageType.MAGICAL, hero)
                    e.addStatus(StatusType.STUN, 1.35f, sourceTeam = hero.team, sourceId = hero.id)
                }
                spawnParticles(hero.pos, 0xFF5DADE2.toInt(), 30, ab.radius, ring = true)
                if (enemies.size >= 3) addFloatingText(hero.pos, "SLAMMIN'! x${enemies.size}", 0xFFF1C40F.toInt(), scale = 1.4f)
            }

            // ANTI-MAGE
            "am_w" -> {
                val dest = clampPoint(hero.pos, aimPoint, ab.castRange)
                spawnParticles(hero.pos, 0xFF9B59B6.toInt(), 12, 80f)
                hero.pos.set(dest.x.coerceIn(90f, MapLayout.SIZE - 90f), dest.y.coerceIn(90f, MapLayout.SIZE - 90f))
                hero.tapMoveTarget = null
                spawnParticles(hero.pos, 0xFF9B59B6.toInt(), 14, 95f, ring = true)
            }
            "am_e" -> {
                hero.addStatus(StatusType.MAGIC_IMMUNE, dur)
                spawnParticles(hero.pos, 0xFF8E44AD.toInt(), 12, 95f, ring = true)
            }
            "am_r" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    val mult = value + (if (hero.hasTalent("spec_mv")) 0.25f else 0f)
                    val missingMana = (target.maxMana - target.mana).coerceAtLeast(120f)
                    val voidDmg = missingMana * mult
                    target.addStatus(StatusType.STUN, dur, sourceTeam = hero.team, sourceId = hero.id)
                    for (e in allEnemyUnits(hero.team, target.pos, ab.radius)) {
                        applyDamage(e, voidDmg, DamageType.MAGICAL, hero)
                    }
                    spawnParticles(target.pos, 0xFF9B59B6.toInt(), 24, ab.radius, ring = true)
                }
            }

            // ZEUS
            "zeus_q" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetUnitId, ab.castRange)
                if (target != null) {
                    applyDamage(target, value, DamageType.MAGICAL, hero)
                    spawnParticles(target.pos, 0xFF38BDF8.toInt(), 6, 60f)
                    var bounces = 0
                    for (other in allEnemyUnits(hero.team, target.pos, ab.radius)) {
                        if (other.id != target.id && bounces++ < 5) {
                            applyDamage(other, value * 0.85f, DamageType.MAGICAL, hero)
                            spawnParticles(other.pos, 0xFF38BDF8.toInt(), 5, 50f)
                        }
                    }
                }
            }
            "zeus_w" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                val target = allEnemyUnits(hero.team, p, ab.radius).minByOrNull { it.pos.distTo(p) }
                if (target != null) {
                    val bonus = if (hero.hasTalent("spec_bolt")) 80f else 0f
                    applyDamage(target, value + bonus, DamageType.MAGICAL, hero)
                    target.addStatus(StatusType.STUN, dur, sourceTeam = hero.team, sourceId = hero.id)
                    target.addStatus(StatusType.TRUE_SIGHT_REVEAL, 4.5f)
                    spawnParticles(target.pos, 0xFFF7DC6F.toInt(), 14, 100f)
                } else {
                    spawnParticles(p, 0xFFF7DC6F.toInt(), 10, 90f, ring = true)
                }
            }
            "zeus_e" -> {
                val leap = Vec2.fromAngle(angle, 300f)
                moveEntitySimple(hero, leap.x, leap.y, 1f)
                for (e in allEnemyUnits(hero.team, hero.pos, ab.radius)) {
                    applyDamage(e, value, DamageType.MAGICAL, hero)
                    e.addStatus(StatusType.SLOW, dur, 0.35f, hero.team, hero.id)
                }
                spawnParticles(hero.pos, 0xFF5DADE2.toInt(), 14, 140f, ring = true)
            }
            "zeus_r" -> {
                val bonus = if (hero.hasTalent("spec_tgw")) 180f else 0f
                for (eh in heroes) {
                    if (eh.alive && eh.team != hero.team) {
                        eh.addStatus(StatusType.TRUE_SIGHT_REVEAL, 5.0f)
                        if (!eh.isInvisible()) {
                            applyDamage(eh, value + bonus, DamageType.MAGICAL, hero)
                        }
                        spawnParticles(eh.pos, 0xFFF7DC6F.toInt(), 18, 130f, ring = true)
                    }
                }
                addAnnouncement("THUNDERGOD'S WRATH!", "Zeus strikes all enemies across the realm!", 0xFF38BDF8.toInt(), 2.0f)
            }

            // VOID SPIRIT
            "vs_q" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, p, ab.radius, 6.0f, "aether_remnant", burstDamage = value, color = 0xFF9B59B6.toInt()))
            }
            "vs_w" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                hero.dissimilateTimer = 0.85f
                hero.dissimilateTarget = p
                spawnParticles(hero.pos, 0xFF7D3C98.toInt(), 16, 160f, ring = true)
            }
            "vs_e" -> {
                val hitHeroes = heroes.count { it.alive && it.team != hero.team && it.pos.distTo(hero.pos) <= ab.radius }
                for (e in allEnemyUnits(hero.team, hero.pos, ab.radius)) {
                    applyDamage(e, value, DamageType.MAGICAL, hero)
                    if (hero.hasScepter) e.addStatus(StatusType.SILENCE, 2.0f, sourceTeam = hero.team, sourceId = hero.id)
                }
                val shield = (value + hitHeroes * 90f) * (if (hero.facetIndex == 0) 1.35f else 1.0f)
                hero.addStatus(StatusType.SHIELD, dur, shield)
                spawnParticles(hero.pos, 0xFFAF7AC5.toInt(), 18, ab.radius, ring = true)
            }
            "vs_r" -> {
                val dest = clampPoint(hero.pos, aimPoint, ab.castRange)
                val steps = 8
                val start = hero.pos.copyVec()
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    val sample = Vec2(DotaMath.lerp(start.x, dest.x, t), DotaMath.lerp(start.y, dest.y, t))
                    for (e in allEnemyUnits(hero.team, sample, 120f)) {
                        applyDamage(e, value * 0.45f, DamageType.MAGICAL, hero)
                        e.addStatus(StatusType.SLOW, 1.5f, 0.35f, hero.team, hero.id)
                    }
                    spawnParticles(sample, 0xFFE8DAEF.toInt(), 4, 55f)
                }
                hero.pos.set(dest.x.coerceIn(90f, MapLayout.SIZE - 90f), dest.y.coerceIn(90f, MapLayout.SIZE - 90f))
            }

            // CHRONOS PRIME (DOTA 3)
            "chr_q" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                for (e in allEnemyUnits(hero.team, p, ab.radius)) {
                    applyDamage(e, value, DamageType.MAGICAL, hero)
                    e.addStatus(StatusType.SLOW, dur, 0.40f, hero.team, hero.id)
                    // Pull toward rift center
                    val dir = (p - e.pos).normalized()
                    e.pos.x += dir.x * 85f
                    e.pos.y += dir.y * 85f
                }
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, p, ab.radius, dur, "time_rift", dps = 35f, color = 0xFF16A085.toInt()))
                spawnParticles(p, 0xFF1ABC9C.toInt(), 20, ab.radius, ring = true)
            }
            "chr_w" -> {
                val vel = Vec2.fromAngle(angle, 1020f)
                projectiles.add(Projectile(uid("prj"), hero.team, hero.id, hero.pos.copyVec(), velocity = vel, speed = 1020f,
                    damage = value, damageType = DamageType.PURE, color = 0xFF1ABC9C.toInt(), radius = 100f, maxRange = ab.castRange,
                    isSkillshot = true, kind = "lance", slowDur = 2.0f, piercesBkb = true))
                if (hero.hasShard) {
                    hero.hp = (hero.hp + hero.maxHp * 0.14f).coerceAtMost(hero.maxHp)
                }
            }
            "chr_e" -> {
                hero.addStatus(StatusType.HASTE, dur)
                spawnParticles(hero.pos, 0xFFFFD700.toInt(), 12, 95f, ring = true)
            }
            "chr_r" -> {
                val p = clampPoint(hero.pos, aimPoint, ab.castRange)
                val rad = ab.radius + (if (hero.hasScepter) 90f else 0f)
                val cdur = dur + (if (hero.hasScepter) 1.0f else 0f) + (if (hero.hasTalent("ult_12")) 1.2f else 0f)
                for (e in allEnemyUnits(hero.team, p, rad)) {
                    applyDamage(e, value, DamageType.PURE, hero)
                }
                zones.add(AoEZone(uid("zn"), hero.team, hero.id, p, rad, cdur, "chrono",
                    dps = if (hero.hasScepter) 90f else 0f, color = 0xFFFFD700.toInt()))
                hero.addStatus(StatusType.HASTE, cdur)
                spawnParticles(p, 0xFFFFD700.toInt(), 28, rad, ring = true)
            }
        }
        return true
    }

    fun tryUseItemById(hero: HeroEntity, itemId: String, aimPoint: Vec2 = hero.pos, targetId: String? = null): Boolean {
        val idx = hero.inventory.indexOfFirst { it.itemId == itemId }
        if (idx < 0) return false
        return useItemSlot(hero, idx, aimPoint, targetId)
    }

    fun useItemSlot(hero: HeroEntity, slotIdx: Int, aimPoint: Vec2 = hero.pos, targetId: String? = null): Boolean {
        if (!hero.alive || slotIdx !in hero.inventory.indices) return false
        val slot = hero.inventory[slotIdx]
        val def = ItemCatalog.get(slot.itemId) ?: return false
        if (!def.isActive || slot.cooldownRemaining > 0f) return false
        if (hero.mana < def.manaCost) return false
        if (hero.isStunned() && def.id != "satanic") return false

        hero.mana -= def.manaCost
        slot.cooldownRemaining = def.cooldown * (1f - hero.cachedCdr)

        when (def.id) {
            "tango" -> {
                // Eat nearby tree if within 160 units!
                trees.firstOrNull { it.alive && it.pos.distTo(hero.pos) < 160f }?.let {
                    it.alive = false
                    it.respawnTimer = 180f
                }
                hero.addStatus(StatusType.REGEN, 10f)
                consumeOneStack(hero, slotIdx)
            }
            "salve" -> {
                hero.hp = (hero.hp + 340f).coerceAtMost(hero.maxHp)
                consumeOneStack(hero, slotIdx)
                spawnParticles(hero.pos, 0xFF2ECC71.toInt(), 10, 70f)
            }
            "clarity" -> {
                hero.mana = (hero.mana + 180f).coerceAtMost(hero.maxMana)
                consumeOneStack(hero, slotIdx)
                spawnParticles(hero.pos, 0xFF3498DB.toInt(), 10, 70f)
            }
            "obs_ward" -> {
                val p = clampPoint(hero.pos, aimPoint, 500f)
                wards.add(WardEntity(uid("wrd"), hero.team, p, isSentry = false))
                consumeOneStack(hero, slotIdx)
                if (hero.isPlayer) addFloatingText(p, "Observer Ward!", 0xFFF1C40F.toInt())
            }
            "sentry_ward" -> {
                val p = clampPoint(hero.pos, aimPoint, 500f)
                wards.add(WardEntity(uid("wrd"), hero.team, p, isSentry = true))
                consumeOneStack(hero, slotIdx)
                if (hero.isPlayer) addFloatingText(p, "Sentry (True Sight)!", 0xFF38BDF8.toInt())
            }
            "dust" -> {
                for (eh in heroes.filter { it.alive && it.team != hero.team && it.pos.distTo(hero.pos) < 700f }) {
                    eh.addStatus(StatusType.TRUE_SIGHT_REVEAL, 10f)
                    eh.addStatus(StatusType.SLOW, 6f, 0.20f)
                }
                consumeOneStack(hero, slotIdx)
                spawnParticles(hero.pos, 0xFFA855F7.toInt(), 20, 450f, ring = true)
            }
            "smoke" -> {
                for (ally in heroes.filter { it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 700f }) {
                    ally.addStatus(StatusType.SMOKE, 25f)
                    ally.addStatus(StatusType.PHASE, 25f)
                }
                consumeOneStack(hero, slotIdx)
                spawnParticles(hero.pos, 0xFF9B59B6.toInt(), 18, 350f, ring = true)
            }
            "quelling_blade" -> {
                trees.firstOrNull { it.alive && it.pos.distTo(hero.pos) < 260f }?.let {
                    it.alive = false
                    it.respawnTimer = 180f
                    spawnParticles(it.pos, 0xFF27AE60.toInt(), 8, 55f)
                }
            }
            "magic_wand" -> {
                val gain = (slot.charges.coerceAtLeast(1)) * 18f
                hero.hp = (hero.hp + gain).coerceAtMost(hero.maxHp)
                hero.mana = (hero.mana + gain).coerceAtMost(hero.maxMana)
                slot.charges = 0
                addFloatingText(hero.pos, "+${gain.toInt()} HP/MP", 0xFF1ABC9C.toInt())
            }
            "power_treads" -> {
                slot.treadMode = (slot.treadMode + 1) % 3
                hero.recalcStats()
                val label = when (slot.treadMode) { 0 -> "STR"; 1 -> "AGI"; else -> "INT" }
                addFloatingText(hero.pos, "Treads: $label", 0xFFF1C40F.toInt())
            }
            "phase_boots" -> hero.addStatus(StatusType.PHASE, 3.0f)
            "arcane_boots" -> {
                for (ally in heroes.filter { it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 600f }) {
                    ally.mana = (ally.mana + 175f).coerceAtMost(ally.maxMana)
                }
                spawnParticles(hero.pos, 0xFF3498DB.toInt(), 14, 300f, ring = true)
            }
            "boots_of_travel" -> {
                val tpDest = findNearestAlliedStructureOrCreep(hero.team, aimPoint)
                hero.tpTargetPos = tpDest
                hero.tpChannelTimer = 2.0f
                if (hero.isPlayer) SoundEngine.play(SoundCue.TP_CHANNEL)
            }
            "blink_dagger" -> {
                val dest = clampPoint(hero.pos, aimPoint, 850f)
                spawnParticles(hero.pos, 0xFF38BDF8.toInt(), 10, 70f)
                hero.pos.set(dest.x.coerceIn(90f, MapLayout.SIZE - 90f), dest.y.coerceIn(90f, MapLayout.SIZE - 90f))
                hero.tapMoveTarget = null
                spawnParticles(hero.pos, 0xFF38BDF8.toInt(), 12, 85f)
            }
            "force_staff" -> {
                val dir = Vec2.fromAngle(hero.facingAngle, 520f)
                hero.pos.x = (hero.pos.x + dir.x).coerceIn(90f, MapLayout.SIZE - 90f)
                hero.pos.y = (hero.pos.y + dir.y).coerceIn(90f, MapLayout.SIZE - 90f)
                spawnParticles(hero.pos, 0xFF2ECC71.toInt(), 12, 85f)
            }
            "euls_scepter" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetId, 560f) ?: hero
                target.addStatus(StatusType.CYCLONE, 2.5f, sourceTeam = hero.team, sourceId = hero.id)
                spawnParticles(target.pos, 0xFF7DD3FC.toInt(), 14, 90f, ring = true)
            }
            "shadow_blade" -> {
                hero.addStatus(StatusType.INVISIBLE, 14f)
                hero.addStatus(StatusType.PHASE, 14f)
            }
            "mask_of_madness" -> {
                hero.addStatus(StatusType.HASTE, 6f)
                hero.addStatus(StatusType.SILENCE, 6f)
            }
            "bkb" -> {
                hero.statuses.removeAll { it.type in listOf(StatusType.SLOW, StatusType.SILENCE, StatusType.ROOT, StatusType.DISARM) }
                hero.addStatus(StatusType.MAGIC_IMMUNE, 6.0f)
                spawnParticles(hero.pos, 0xFFFFD700.toInt(), 20, 120f, ring = true)
                if (hero.isPlayer) SoundEngine.play(SoundCue.BKB_AVATAR)
            }
            "satanic" -> {
                hero.addStatus(StatusType.SATANIC, 5.0f)
                spawnParticles(hero.pos, 0xFFC0392B.toInt(), 16, 110f, ring = true)
            }
            "orchid" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetId, 680f)
                if (target != null) {
                    target.addStatus(StatusType.SILENCE, 4.0f, sourceTeam = hero.team, sourceId = hero.id)
                    applyDamage(target, 140f, DamageType.MAGICAL, hero)
                }
            }
            "scythe_of_vyse" -> {
                val target = findBestEnemyTarget(hero, aimPoint, targetId, 680f)
                if (target != null) {
                    target.addStatus(StatusType.HEX, 2.8f, sourceTeam = hero.team, sourceId = hero.id)
                    addFloatingText(target.pos, "HEXED!", 0xFFA855F7.toInt())
                }
            }
            "shivas_guard" -> {
                for (enemy in allEnemyUnits(hero.team, hero.pos, 650f)) {
                    applyDamage(enemy, 220f, DamageType.MAGICAL, hero)
                    enemy.addStatus(StatusType.SLOW, 4.0f, 0.40f, hero.team, hero.id)
                }
                spawnParticles(hero.pos, 0xFF38BDF8.toInt(), 28, 600f, ring = true)
            }
            "refresher" -> {
                for (i in 0..3) hero.abilityCooldowns[i] = 0f
                for (s in hero.inventory) {
                    if (s.itemId != "refresher") s.cooldownRemaining = 0f
                }
                addFloatingText(hero.pos, "REFRESHED!", 0xFF2ECC71.toInt(), scale = 1.3f)
            }
            "bloodstone" -> {
                hero.hp = (hero.hp + 350f).coerceAtMost(hero.maxHp)
                spawnParticles(hero.pos, 0xFFE74C3C.toInt(), 14, 110f, ring = true)
            }
        }
        return true
    }

    fun smartTpTarget(hero: HeroEntity, camPos: Vec2, camLocked: Boolean): Vec2 {
        val fp = fountainPos(hero.team)
        if (!camLocked) return camPos.copyVec()
        // If already in fountain, TP toward frontline tower in player's lane; otherwise TP home to fountain
        if (hero.pos.distTo(fp) < 620f) {
            val laneTowers = towers.filter { it.alive && it.team == hero.team && it.blueprint.lane == hero.assignedLane }
            val fallbackTowers = towers.filter { it.alive && it.team == hero.team }
            val enemyAnc = if (hero.team == Team.RADIANT) direAncient.pos else radiantAncient.pos
            val frontTower = (laneTowers.ifEmpty { fallbackTowers }).minByOrNull { it.pos.distTo(enemyAnc) }
            return frontTower?.pos?.copyVec() ?: fp.copyVec()
        }
        return fp.copyVec()
    }

    fun useTpScroll(hero: HeroEntity, aimPoint: Vec2 = fountainPos(hero.team)): Boolean {
        if (!hero.alive || hero.tpCooldown > 0f || hero.isStunned() || hero.isSilenced()) return false
        hero.tpCooldown = 45f
        hero.tpTargetPos = findNearestAlliedStructureOrCreep(hero.team, aimPoint)
        hero.tpChannelTimer = 2.5f
        if (hero.isPlayer) SoundEngine.play(SoundCue.TP_CHANNEL)
        return true
    }

    fun activateGlyph(team: Team): Boolean {
        val cd = if (team == Team.RADIANT) radiantGlyphCd else direGlyphCd
        if (cd > 0f) return false
        if (team == Team.RADIANT) radiantGlyphCd = 210f else direGlyphCd = 210f
        for (t in towers) {
            if (t.alive && t.team == team) t.fortifyTimer = 5.5f
        }
        addAnnouncement(
            if (team == Team.RADIANT) "RADIANT FORTIFIED!" else "DIRE FORTIFIED!",
            "Structures invulnerable for 5.5s!",
            0xFF38BDF8.toInt(), 2.2f
        )
        return true
    }

    fun buyback(hero: HeroEntity): Boolean {
        if (hero.alive || hero.buybackCooldown > 0f) return false
        val cost = hero.buybackCost()
        if (hero.gold < cost) return false
        hero.gold -= cost
        hero.buybackCooldown = 240f
        respawnHero(hero)
        addAnnouncement("${hero.def.name} BOUGHT BACK!", "Returned to battle for ${cost}g", 0xFFF1C40F.toInt(), 2.2f)
        return true
    }

    fun canAffordItem(hero: HeroEntity, itemId: String): Boolean {
        val def = ItemCatalog.get(itemId) ?: return false
        return hero.gold >= effectiveItemCost(hero, def)
    }

    fun effectiveItemCost(hero: HeroEntity, def: ItemDef): Int {
        var cost = def.cost
        val allOwned = (hero.inventory + hero.stash).map { it.itemId }.toMutableList()
        fun deduct(compIds: List<String>) {
            for (compId in compIds) {
                val compDef = ItemCatalog.get(compId) ?: continue
                if (allOwned.remove(compId)) {
                    cost -= compDef.cost
                } else if (compDef.components.isNotEmpty()) {
                    deduct(compDef.components)
                }
            }
        }
        deduct(def.components)
        return cost.coerceAtLeast(0)
    }

    fun buyItem(hero: HeroEntity, itemId: String): Boolean {
        val def = ItemCatalog.get(itemId) ?: return false
        val price = effectiveItemCost(hero, def)
        if (hero.gold < price) return false

        // Aghanim's Shard instant permanent consume!
        if (itemId == "aghanims_shard") {
            if (hero.hasShard) return false
            hero.gold -= price
            hero.hasShard = true
            hero.recalcStats()
            if (hero.isPlayer) SoundEngine.play(SoundCue.BUY_ITEM)
            addFloatingText(hero.pos, "SHARD UPGRADED!", 0xFF38BDF8.toInt())
            return true
        }

        val atFountain = hero.pos.distTo(fountainPos(hero.team)) < 480f
        // Snapshot & remove consumed components (supports nested sub-components!), rolling back if no space
        val removedFromInv = mutableListOf<ItemSlot>()
        val removedFromStash = mutableListOf<ItemSlot>()
        fun consumeComponents(compIds: List<String>) {
            for (compId in compIds) {
                val invIdx = hero.inventory.indexOfFirst { it.itemId == compId }
                if (invIdx >= 0) {
                    removedFromInv.add(hero.inventory.removeAt(invIdx))
                } else {
                    val stIdx = hero.stash.indexOfFirst { it.itemId == compId }
                    if (stIdx >= 0) {
                        removedFromStash.add(hero.stash.removeAt(stIdx))
                    } else {
                        val compDef = ItemCatalog.get(compId)
                        if (compDef != null && compDef.components.isNotEmpty()) {
                            consumeComponents(compDef.components)
                        }
                    }
                }
            }
        }
        consumeComponents(def.components)

        val newSlot = ItemSlot(itemId, stacks = 1, charges = if (itemId == "magic_wand") 3 else 0)
        hero.gold -= price

        val placed = if (atFountain || mode == GameMode.TURBO_5V5 || mode == GameMode.SANDBOX) {
            if (addOrStackToInventory(hero, newSlot)) true
            else if (hero.stash.size < 6) { hero.stash.add(newSlot); true }
            else false
        } else {
            if (hero.stash.size < 6) { hero.stash.add(newSlot); true }
            else addOrStackToInventory(hero, newSlot)
        }
        if (!placed) {
            hero.gold += price
            hero.inventory.addAll(removedFromInv)
            hero.stash.addAll(removedFromStash)
            return false
        }

        if (itemId == "aghanims_scepter") hero.hasScepter = true
        hero.recalcStats()
        if (hero.isPlayer) SoundEngine.play(SoundCue.BUY_ITEM)
        return true
    }

    fun sellItem(hero: HeroEntity, slotIdx: Int): Boolean {
        if (slotIdx !in hero.inventory.indices) return false
        val slot = hero.inventory.removeAt(slotIdx)
        val def = ItemCatalog.get(slot.itemId) ?: return false
        hero.gold += (def.cost * 0.5f).toInt()
        hero.hasScepter = hero.hasItem("aghanims_scepter")
        hero.recalcStats()
        return true
    }

    fun dispatchCourier(hero: HeroEntity): Boolean {
        val c = couriers.firstOrNull { it.heroId == hero.id } ?: return false
        if (!c.alive || (hero.stash.isEmpty() && c.cargo.isEmpty())) return false
        c.cargo.addAll(hero.stash)
        hero.stash.clear()
        c.delivering = true
        c.returning = false
        return true
    }

    fun chooseTalent(hero: HeroEntity, tierIdx: Int, side: Int): Boolean {
        if (tierIdx !in hero.talentsChosen.indices) return false
        if (hero.talentsChosen[tierIdx] != -1) return false
        if (hero.level < hero.def.talents[tierIdx].level) return false
        hero.talentsChosen[tierIdx] = side.coerceIn(0, 1)
        hero.recalcStats()
        return true
    }

    fun levelUpAbility(hero: HeroEntity, idx: Int): Boolean {
        if (!hero.canLevelAbility(idx)) return false
        hero.abilityLevels[idx]++
        hero.skillPoints--
        hero.recalcStats()
        if (hero.isPlayer) SoundEngine.play(SoundCue.LEVEL_UP)
        return true
    }

    fun addPing(worldPos: Vec2, color: Int = 0xFFF1C40F.toInt()) {
        pings.add(MinimapPing(worldPos.copyVec(), color))
    }

    fun applyDamage(
        target: CombatUnit,
        amount: Float,
        type: DamageType,
        attacker: HeroEntity?,
        showFloat: Boolean = true
    ): Float {
        if (!target.alive || target.isInvulnerable()) return 0f
        if (target is TowerEntity && target.fortifyTimer > 0f) return 0f
        if (target is TowerEntity && !isTowerVulnerable(target)) return 0f
        if (target is BarracksEntity && !isBarracksVulnerable(target)) return 0f
        if (target is AncientEntity && !isAncientVulnerable(target.team)) return 0f
        if (type == DamageType.MAGICAL && target.isMagicImmune()) return 0f

        var mult = when (type) {
            DamageType.PHYSICAL -> DotaMath.armorMultiplier(target.armor)
            DamageType.MAGICAL -> DotaMath.magicMultiplier(target.magicResist)
            DamageType.PURE -> 1f
        }
        if (attacker != null && type == DamageType.MAGICAL) {
            mult *= (1f + attacker.cachedSpellAmp)
        }
        // Vanguard damage block
        var raw = amount
        if (target is HeroEntity && type == DamageType.PHYSICAL && target.hasItem("vanguard")) {
            raw = (raw - 42f).coerceAtLeast(1f)
        }

        val finalDmg = (raw * mult).coerceAtLeast(1f)
        target.hp -= finalDmg

        if (attacker != null) {
            if (target is HeroEntity) {
                attacker.heroDamageDealt += finalDmg.toInt()
                target.recentAttackers[attacker.id] = gameTime
                if (type == DamageType.MAGICAL && attacker.cachedSpellLifesteal > 0f) {
                    attacker.hp = (attacker.hp + finalDmg * attacker.cachedSpellLifesteal).coerceAtMost(attacker.maxHp)
                }
            } else if (target is TowerEntity || target is BarracksEntity || target is AncientEntity) {
                attacker.towerDamageDealt += finalDmg.toInt()
            }
        }

        if (showFloat && (attacker?.isPlayer == true || (target is HeroEntity && target.isPlayer))) {
            val col = when (type) {
                DamageType.PHYSICAL -> 0xFFF8FAFC.toInt()
                DamageType.MAGICAL -> 0xFF38BDF8.toInt()
                DamageType.PURE -> 0xFFF59E0B.toInt()
            }
            addFloatingText(target.pos, "-${finalDmg.toInt()}", col, 0.75f)
        }

        if (target.hp <= 0f) {
            target.hp = 0f
            handleDeath(target, attacker)
        }
        return finalDmg
    }

    private fun handleDeath(victim: CombatUnit, killer: HeroEntity?) {
        when (victim) {
            is HeroEntity -> {
                // Aegis of the Immortal!
                if (victim.hasAegis) {
                    victim.hasAegis = false
                    victim.alive = false
                    victim.aegisRespawnTimer = 3.0f
                    addAnnouncement("${victim.def.name} has AEGIS!", "Reincarnating in 3 seconds...", 0xFFFFD700.toInt())
                    return
                }
                victim.alive = false
                victim.deaths++
                victim.killStreak = 0
                victim.respawnTimer = (6f + victim.level * 1.8f) / (if (mode == GameMode.TURBO_5V5) 1.6f else 1f)

                // Divine Rapier drops on death!
                val rapierIdx = victim.inventory.indexOfFirst { it.itemId == "divine_rapier" }
                if (rapierIdx >= 0) {
                    victim.inventory.removeAt(rapierIdx)
                    droppedItems.add(DroppedItemEntity(uid("drp"), "divine_rapier", victim.pos.copyVec()))
                    addAnnouncement("DIVINE RAPIER DROPPED!", "Claim it from the battlefield!", 0xFFFFD700.toInt())
                }

                if (killer != null && killer.team != victim.team) {
                    if (killer.team == Team.RADIANT) radiantKills++ else direKills++
                    killer.kills++
                    killer.killStreak++

                    var bounty = 180 + victim.level * 16 + victim.killStreak * 30
                    var playedFirstBlood = false
                    if (!firstBloodClaimed) {
                        firstBloodClaimed = true
                        bounty += 135
                        addAnnouncement("FIRST BLOOD!", "${killer.def.name} drew first blood!", 0xFFE74C3C.toInt())
                        SoundEngine.play(SoundCue.FIRST_BLOOD)
                        playedFirstBlood = true
                    }
                    bounty = (bounty * mode.goldMult).toInt()
                    killer.gold += bounty
                    killer.reliableGold += bounty
                    killer.totalGoldEarned += bounty
                    if (killer.isPlayer && !playedFirstBlood) SoundEngine.play(SoundCue.HERO_KILL)
                    addFloatingText(victim.pos, "+${bounty}g", 0xFFF1C40F.toInt(), scale = 1.3f)

                    // Desolator & PA Scepter & Axe & Pudge stacks
                    if (killer.hasItem("desolator")) killer.desoStacks = (killer.desoStacks + 2).coerceAtMost(25)
                    if (killer.def.id == "pa" && killer.hasScepter) {
                        killer.abilityCooldowns[0] = 0f
                        killer.abilityCooldowns[2] = 0f
                    }
                    if (killer.def.id == "axe") killer.coatOfBloodStacks++

                    // Pudge Flesh Heap nearby enemy deaths
                    for (pudge in heroes.filter { it.alive && it.team != victim.team && it.def.id == "pudge" && it.pos.distTo(victim.pos) < 750f }) {
                        pudge.fleshHeapStacks++
                    }

                    // Assists & XP share
                    val xpReward = ((120 + victim.level * 45) * mode.xpMult).toInt()
                    val assisters = heroes.filter {
                        it.alive && it.team == killer.team && it.id != killer.id &&
                            (it.pos.distTo(victim.pos) < 950f || (victim.recentAttackers[it.id] ?: -99f) > gameTime - 15f)
                    }
                    killer.addXp(xpReward)
                    for (a in assisters) {
                        a.assists++
                        a.gold += (75 * mode.goldMult).toInt()
                        a.addXp((xpReward * 0.6f).toInt())
                    }

                    // Killstreak announcement
                    val ksTitle = when (killer.killStreak) {
                        3 -> "KILLING SPREE!"
                        4 -> "DOMINATING!"
                        5 -> "MEGA KILL!"
                        6 -> "UNSTOPPABLE!"
                        7 -> "WICKED SICK!"
                        8 -> "MONSTER KILL!"
                        9 -> "GODLIKE!"
                        else -> if (killer.killStreak >= 10) "BEYOND GODLIKE!!" else null
                    }
                    if (ksTitle != null) {
                        addAnnouncement(ksTitle, "${killer.def.name} (${killer.kills}/${killer.deaths})", 0xFFF39C12.toInt())
                        if (killer.isPlayer) {
                            SoundEngine.play(if (killer.killStreak >= 6) SoundCue.GODLIKE else SoundCue.KILL_SPREE)
                        }
                    }

                    // 1v1 Mid win check (2 kills wins!)
                    if (mode == GameMode.MID_1V1 && killer.kills >= 2) {
                        endMatch(killer.team, "${killer.def.name} won 1v1 Mid (2 Kills)!")
                    }
                }
                spawnParticles(victim.pos, 0xFFE74C3C.toInt(), 24, 150f)
            }
            is CreepEntity -> {
                victim.alive = false
                if (killer != null) {
                    if (killer.team == victim.team) {
                        // DENY!
                        killer.denies++
                        addFloatingText(victim.pos, "!", 0xFF38BDF8.toInt(), scale = 1.35f)
                        if (killer.isPlayer) SoundEngine.play(SoundCue.DENY)
                    } else {
                        killer.lastHits++
                        val g = (victim.bountyGold * mode.goldMult).toInt()
                        killer.gold += g
                        killer.totalGoldEarned += g
                        addFloatingText(victim.pos, "+${g}g", 0xFFF1C40F.toInt())
                        if (killer.isPlayer) SoundEngine.play(SoundCue.GOLD_COIN)
                    }
                }
                // Share XP to nearby enemy heroes
                val xpGain = (victim.bountyXp * mode.xpMult).toInt()
                val nearEnemyHeroes = heroes.filter { it.alive && it.team != victim.team && it.pos.distTo(victim.pos) < 850f }
                if (nearEnemyHeroes.isNotEmpty()) {
                    val share = (xpGain / nearEnemyHeroes.size).coerceAtLeast(10)
                    nearEnemyHeroes.forEach { it.addXp(share) }
                }
            }
            is NeutralCreepEntity -> {
                victim.alive = false
                if (killer != null) {
                    killer.lastHits++
                    val g = (victim.bountyGold * mode.goldMult).toInt()
                    killer.gold += g
                    killer.totalGoldEarned += g
                    killer.addXp((victim.bountyXp * mode.xpMult).toInt())
                    addFloatingText(victim.pos, "+${g}g", 0xFFF1C40F.toInt())
                    if (killer.isPlayer) SoundEngine.play(SoundCue.GOLD_COIN)

                    // Neutral Item Token Drop!
                    val targetTier = when {
                        gameTime > 1200f -> 4
                        gameTime > 720f -> 3
                        gameTime > 360f -> 2
                        else -> 1
                    }
                    if (rng.nextFloat() < 0.22f) {
                        val tierPool = ItemCatalog.neutralItems.filter { it.tier == targetTier }
                        val drop = tierPool[rng.nextInt(tierPool.size)]
                        val currentTier = killer.neutralItemId?.let { ItemCatalog.getNeutral(it)?.tier } ?: 0
                        if (drop.tier > currentTier) {
                            killer.neutralItemId = drop.id
                            killer.recalcStats()
                            addFloatingText(killer.pos, "NEUTRAL: ${drop.name}!", 0xFFA855F7.toInt(), 1.4f)
                        }
                    }
                }
            }
            is TowerEntity -> {
                victim.alive = false
                val rewardTeam = victim.team.enemy()
                val teamGold = (160 * mode.goldMult).toInt()
                for (h in heroes.filter { it.team == rewardTeam }) {
                    h.gold += teamGold
                    h.totalGoldEarned += teamGold
                }
                if (killer != null) killer.gold += 140
                // Refresh Glyph when T1 falls!
                if (victim.blueprint.tier == 1) {
                    if (victim.team == Team.RADIANT) radiantGlyphCd = 0f else direGlyphCd = 0f
                }
                addAnnouncement("${victim.team.name} TOWER FALLEN!", "Tier ${victim.blueprint.tier} destroyed (+${teamGold}g team)", 0xFFF39C12.toInt())
                spawnParticles(victim.pos, 0xFFFF9800.toInt(), 32, 180f, ring = true)
                SoundEngine.play(SoundCue.TOWER_DESTROY)
                pendingCameraShake = (pendingCameraShake + 0.50f).coerceAtMost(1.0f)
                if (mode == GameMode.MID_1V1) {
                    endMatch(rewardTeam, "Enemy Mid Tower Destroyed!")
                }
            }
            is BarracksEntity -> {
                victim.alive = false
                val rewardTeam = victim.team.enemy()
                for (h in heroes.filter { it.team == rewardTeam }) h.gold += 180
                addAnnouncement("${victim.team.name} BARRACKS DESTROYED!", "Super Creeps unlocked in lane!", 0xFFE67E22.toInt())
                spawnParticles(victim.pos, 0xFFE67E22.toInt(), 28, 160f)
            }
            is AncientEntity -> {
                victim.alive = false
                endMatch(victim.team.enemy(), "The ${victim.team.name} Ancient has fallen!")
            }
            is RoshanEntity -> {
                victim.alive = false
                victim.respawnTimer = 300f
                droppedItems.add(DroppedItemEntity(uid("drp"), "aegis", victim.pos.copyVec()))
                if (victim.deathsCount >= 1) droppedItems.add(DroppedItemEntity(uid("drp"), "cheese", victim.pos + Vec2(40f, 0f)))
                if (victim.deathsCount >= 2) droppedItems.add(DroppedItemEntity(uid("drp"), "refresher_shard", victim.pos - Vec2(40f, 0f)))
                killer?.let { k ->
                    for (h in heroes.filter { it.team == k.team }) {
                        h.gold += 225
                        h.addXp(350)
                    }
                }
                addAnnouncement("ROSHAN HAS FALLEN!", "Aegis of the Immortal dropped!", 0xFFFFD700.toInt())
                SoundEngine.play(SoundCue.ROSHAN_ROAR)
                pendingCameraShake = (pendingCameraShake + 0.65f).coerceAtMost(1.0f)
                if (allyCommand == TacticalCommand.ROSHAN) {
                    allyCommand = TacticalCommand.AUTO
                    allyCommandTimer = 0f
                }
            }
            is TormentorEntity -> {
                victim.alive = false
                victim.respawnTimer = 420f
                killer?.let { k ->
                    val recipient = heroes.filter { it.team == k.team && !it.hasShard }.minByOrNull { it.netWorth } ?: k
                    recipient.hasShard = true
                    recipient.recalcStats()
                    addAnnouncement("TORMENTOR DEFEATED!", "${recipient.def.name} received Aghanim's Shard!", 0xFF38BDF8.toInt())
                }
            }
        }
    }

    private fun endMatch(winTeam: Team, reason: String) {
        if (matchOver) return
        matchOver = true
        winner = winTeam
        addAnnouncement(
            if (winTeam == playerTeam) "RADIANT VICTORY!" else "DEFEAT",
            reason,
            if (winTeam == playerTeam) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt(),
            10f
        )
        SoundEngine.play(SoundCue.VICTORY)
    }

    private fun respawnHero(h: HeroEntity) {
        val sp = fountainPos(h.team)
        h.alive = true
        h.hp = h.maxHp
        h.mana = h.maxMana
        h.statuses.clear()
        h.pos.set(sp.x, sp.y)
        h.botRetreating = false
        h.recalcStats()
    }

    private fun consumeRune(h: HeroEntity, r: RuneEntity) {
        r.alive = false
        if (h.isPlayer) SoundEngine.play(SoundCue.RUNE_PICKUP)
        when (r.type) {
            RuneType.DOUBLE_DAMAGE -> h.addStatus(StatusType.DOUBLE_DAMAGE, 40f)
            RuneType.HASTE -> h.addStatus(StatusType.HASTE, 22f)
            RuneType.REGENERATION -> h.addStatus(StatusType.REGEN, 24f)
            RuneType.ARCANE -> {
                h.mana = (h.mana + h.maxMana * 0.45f).coerceAtMost(h.maxMana)
                for (i in 0..3) h.abilityCooldowns[i] *= 0.5f
            }
            RuneType.INVISIBILITY -> h.addStatus(StatusType.INVISIBLE, 35f)
            RuneType.BOUNTY -> {
                val g = (65 + (gameTime / 60f).toInt() * 12)
                for (ally in heroes.filter { it.team == h.team }) {
                    ally.gold += g
                    ally.totalGoldEarned += g
                }
            }
            RuneType.WISDOM -> {
                val xp = 280 + (gameTime / 60f).toInt() * 45
                h.addXp(xp)
            }
            RuneType.WATER, RuneType.LOTUS -> {
                h.hp = (h.hp + 175f).coerceAtMost(h.maxHp)
                h.mana = (h.mana + 110f).coerceAtMost(h.maxMana)
            }
        }
        addFloatingText(h.pos, r.type.label.uppercase(), r.type.color, 1.3f)
    }

    private fun tickUnitStatuses(u: CombatUnit, dt: Float) {
        if (u.armorCorruptionTimer > 0f) {
            u.armorCorruptionTimer -= dt
            if (u.armorCorruptionTimer <= 0f) u.armorCorruption = 0f
        }
        val iter = u.statuses.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.remaining -= dt
            if (s.remaining <= 0f) iter.remove()
        }
    }

    private fun moveEntityWithCollision(u: CombatUnit, dx: Float, dy: Float, dt: Float) {
        val dist = u.moveSpeed * dt
        var nx = (u.pos.x + dx * dist).coerceIn(75f, MapLayout.SIZE - 75f)
        var ny = (u.pos.y + dy * dist).coerceIn(75f, MapLayout.SIZE - 75f)
        if (!u.hasStatus(StatusType.PHASE)) {
            for (t in trees) {
                if (!t.alive) continue
                val d = hypot(nx - t.pos.x, ny - t.pos.y)
                val minD = u.radius + t.radius * 0.7f
                if (d < minD && d > 0.01f) {
                    val push = (minD - d)
                    nx += ((nx - t.pos.x) / d) * push
                    ny += ((ny - t.pos.y) / d) * push
                }
            }
        }
        u.pos.set(nx, ny)
    }

    private fun moveEntitySimple(u: CombatUnit, dx: Float, dy: Float, dt: Float) {
        u.pos.x = (u.pos.x + dx * u.moveSpeed * dt).coerceIn(75f, MapLayout.SIZE - 75f)
        u.pos.y = (u.pos.y + dy * u.moveSpeed * dt).coerceIn(75f, MapLayout.SIZE - 75f)
        u.facingAngle = kotlin.math.atan2(dy, dx)
    }

    fun updateFogOfWar() {
        fogOfWar.beginFrame()
        val visionMult = if (isNight) 0.65f else 1.0f
        for (h in heroes) {
            if (h.alive && h.team == playerTeam) {
                fogOfWar.revealCircle(h.pos.x, h.pos.y, h.def.dayVision * visionMult, grantsTrueSight = false)
            }
        }
        for (c in creeps) {
            if (c.alive && c.team == playerTeam) fogOfWar.revealCircle(c.pos.x, c.pos.y, 440f)
        }
        for (t in towers) {
            if (t.alive && t.team == playerTeam) fogOfWar.revealCircle(t.pos.x, t.pos.y, 720f, grantsTrueSight = true)
        }
        for (b in barracks) {
            if (b.alive && b.team == playerTeam) fogOfWar.revealCircle(b.pos.x, b.pos.y, 520f)
        }
        val ownAnc = if (playerTeam == Team.RADIANT) radiantAncient else direAncient
        fogOfWar.revealCircle(ownAnc.pos.x, ownAnc.pos.y, 820f, grantsTrueSight = true)
        for (w in wards) {
            if (w.team == playerTeam) {
                fogOfWar.revealCircle(w.pos.x, w.pos.y, if (w.isSentry) 520f else 850f, grantsTrueSight = w.isSentry)
            }
        }
    }

    fun isTowerVulnerable(t: TowerEntity): Boolean {
        if (t.blueprint.tier == 1) return true
        if (t.blueprint.tier == 4) {
            return towers.any { it.team == t.team && it.blueprint.tier == 3 && !it.alive }
        }
        return towers.none { it.team == t.team && it.blueprint.lane == t.blueprint.lane && it.blueprint.tier == t.blueprint.tier - 1 && it.alive }
    }

    fun isBarracksVulnerable(b: BarracksEntity): Boolean {
        return towers.none { it.team == b.team && it.blueprint.lane == b.lane && it.blueprint.tier == 3 && it.alive }
    }

    fun isAncientVulnerable(team: Team): Boolean {
        return towers.none { it.team == team && it.blueprint.tier == 4 && it.alive }
    }

    fun findFurthestAllyFront(team: Team, lane: Int): Vec2? {
        val allyCreeps = creeps.filter { it.alive && it.team == team && it.lane == lane }
        if (allyCreeps.isEmpty()) return null
        val enemyAnc = if (team == Team.RADIANT) direAncient.pos else radiantAncient.pos
        return allyCreeps.minByOrNull { it.pos.distTo(enemyAnc) }?.pos
    }

    fun allEnemyUnits(myTeam: Team, center: Vec2, radius: Float): List<CombatUnit> {
        val list = mutableListOf<CombatUnit>()
        for (h in heroes) if (h.alive && h.team != myTeam && h.pos.distTo(center) <= radius) list.add(h)
        for (c in creeps) if (c.alive && c.team != myTeam && c.pos.distTo(center) <= radius) list.add(c)
        for (n in neutrals) if (n.alive && n.pos.distTo(center) <= radius) list.add(n)
        for (t in towers) if (t.alive && t.team != myTeam && t.pos.distTo(center) <= radius) list.add(t)
        for (b in barracks) if (b.alive && b.team != myTeam && b.pos.distTo(center) <= radius) list.add(b)
        val enemyAnc = if (myTeam == Team.RADIANT) direAncient else radiantAncient
        if (enemyAnc.alive && enemyAnc.pos.distTo(center) <= radius) list.add(enemyAnc)
        if (roshan.alive && roshan.pos.distTo(center) <= radius) list.add(roshan)
        for (tor in listOf(radiantTormentor, direTormentor)) {
            if (tor.alive && tor.pos.distTo(center) <= radius) list.add(tor)
        }
        return list
    }

    fun findUnitById(id: String?): CombatUnit? {
        if (id == null) return null
        heroes.firstOrNull { it.id == id }?.let { return it }
        creeps.firstOrNull { it.id == id }?.let { return it }
        neutrals.firstOrNull { it.id == id }?.let { return it }
        towers.firstOrNull { it.id == id }?.let { return it }
        barracks.firstOrNull { it.id == id }?.let { return it }
        if (radiantAncient.id == id) return radiantAncient
        if (direAncient.id == id) return direAncient
        if (roshan.id == id) return roshan
        if (radiantTormentor.id == id) return radiantTormentor
        if (direTormentor.id == id) return direTormentor
        return null
    }

    private fun findBestEnemyTarget(caster: HeroEntity, aimPoint: Vec2, targetUnitId: String?, range: Float): CombatUnit? {
        findUnitById(targetUnitId)?.takeIf { it.alive && it.team != caster.team && it.pos.distTo(caster.pos) <= range + 80f }?.let { return it }
        findUnitById(caster.attackTargetId)?.takeIf {
            it is HeroEntity && it.alive && it.team != caster.team && it.pos.distTo(caster.pos) <= range + 80f &&
                (!it.isInvisible() || fogOfWar.hasTrueSight(it.pos.x, it.pos.y))
        }?.let { return it }
        val nearAim = allEnemyUnits(caster.team, aimPoint, 240f).filter { it.pos.distTo(caster.pos) <= range + 80f && !it.isInvisible() }
        if (nearAim.isNotEmpty()) {
            return nearAim.filterIsInstance<HeroEntity>().minByOrNull { it.pos.distTo(aimPoint) } ?: nearAim.minByOrNull { it.pos.distTo(aimPoint) }
        }
        return allEnemyUnits(caster.team, caster.pos, range).filter { !it.isInvisible() }.minByOrNull {
            (if (it is HeroEntity) 0f else 400f) + it.pos.distTo(caster.pos)
        }
    }

    private fun findNearestAlliedStructureOrCreep(team: Team, aim: Vec2): Vec2 {
        val candidates = towers.filter { it.alive && it.team == team }.map { it.pos } + fountainPos(team)
        return candidates.minByOrNull { it.distTo(aim) } ?: fountainPos(team)
    }

    private fun addOrStackToInventory(hero: HeroEntity, slot: ItemSlot): Boolean {
        val def = ItemCatalog.get(slot.itemId)
        if (def != null && def.maxStacks > 1) {
            val existing = hero.inventory.firstOrNull { it.itemId == slot.itemId && it.stacks < def.maxStacks }
            if (existing != null) {
                existing.stacks = (existing.stacks + slot.stacks).coerceAtMost(def.maxStacks)
                return true
            }
        }
        if (hero.inventory.size < 6) {
            hero.inventory.add(slot)
            return true
        }
        return false
    }

    private fun consumeOneStack(hero: HeroEntity, slotIdx: Int) {
        if (slotIdx !in hero.inventory.indices) return
        val s = hero.inventory[slotIdx]
        s.stacks--
        if (s.stacks <= 0) {
            hero.inventory.removeAt(slotIdx)
            hero.recalcStats()
        }
    }

    private fun clampPoint(origin: Vec2, dest: Vec2, maxDist: Float): Vec2 {
        val dx = dest.x - origin.x
        val dy = dest.y - origin.y
        val d = hypot(dx, dy)
        return if (d <= maxDist || maxDist <= 0f) dest.copyVec()
        else Vec2(origin.x + (dx / d) * maxDist, origin.y + (dy / d) * maxDist)
    }

    fun spawnParticles(pos: Vec2, color: Int, count: Int, spread: Float, ring: Boolean = false) {
        if (particles.size > 280) return
        for (i in 0 until count) {
            val ang = (i.toFloat() / count) * 6.283f + rng.nextFloat() * 0.4f
            val spd = if (ring) spread * 1.4f else rng.nextFloat() * spread * 1.35f
            val zv = if (ring) 0f else 65f + rng.nextFloat() * 145f
            particles.add(
                ParticleEffect(
                    pos.copyVec(), Vec2.fromAngle(ang, spd), color,
                    if (ring) 7.5f else 5.5f, 0.36f, 0.36f, ring,
                    height = if (ring) 5f else 22f + rng.nextFloat() * 14f, zVel = zv
                )
            )
        }
    }

    fun addFloatingText(pos: Vec2, text: String, color: Int, life: Float = 1.0f, scale: Float = 1.0f) {
        if (floatingTexts.size > 60) return
        floatingTexts.add(FloatingText(Vec2(pos.x + rng.nextInt(-18, 19), pos.y - 32f), text, color, life, scale))
    }

    fun addAnnouncement(title: String, subtitle: String, color: Int, duration: Float = 3.0f) {
        announcements.add(MatchAnnouncement(title, subtitle, color, duration))
        if (announcements.size > 3) announcements.removeAt(0)
    }

    fun issueTacticalCommand(cmd: TacticalCommand) {
        allyCommand = cmd
        allyCommandTimer = if (cmd == TacticalCommand.AUTO) 0f else 32f
        val pingPos = when (cmd) {
            TacticalCommand.PUSH -> findFurthestAllyFront(playerTeam, playerHero.assignedLane)
                ?: if (playerTeam == Team.RADIANT) direAncient.pos else radiantAncient.pos
            TacticalCommand.ROSHAN -> MapLayout.roshanPitPos
            TacticalCommand.DEFEND -> if (playerTeam == Team.RADIANT) radiantAncient.pos else direAncient.pos
            TacticalCommand.RETREAT -> fountainPos(playerTeam)
            TacticalCommand.AUTO -> playerHero.pos
        }
        addPing(pingPos, cmd.colorHex)
        val msg = when (cmd) {
            TacticalCommand.PUSH -> "ALLIES: PUSH!"
            TacticalCommand.ROSHAN -> if (roshan.alive) "ALLIES: TAKE ROSHAN!" else "ROSHAN DEAD (${roshan.respawnTimer.toInt()}s)"
            TacticalCommand.DEFEND -> "ALLIES: DEFEND BASE!"
            TacticalCommand.RETREAT -> "ALLIES: RETREAT!"
            TacticalCommand.AUTO -> "ALLIES: AUTO AI"
        }
        addFloatingText(playerHero.pos, msg, cmd.colorHex, 1.4f, 1.15f)
        SoundEngine.play(SoundCue.TACTICAL_PING)
    }

    fun lockEnemyHero(heroId: String): Boolean {
        val h = playerHero
        if (!h.alive) return false
        val target = heroes.firstOrNull {
            it.id == heroId && it.alive && it.team != playerTeam &&
                (!it.isInvisible() || fogOfWar.hasTrueSight(it.pos.x, it.pos.y))
        } ?: return false
        h.attackTargetId = target.id
        h.attackCommandAge = 0f
        h.facingAngle = h.pos.angleTo(target.pos)
        if (target.pos.distTo(h.pos) > h.cachedAttackRange) {
            h.tapMoveTarget = target.pos.copyVec()
        } else if (!h.isStunned() && !h.isDisarmed() && h.omnislashTimer <= 0f && h.dismemberTimer <= 0f && h.freezingFieldTimer <= 0f) {
            performHeroAutoAttack(h)
        }
        spawnParticles(target.pos, 0xFFEF4444.toInt(), 8, 55f, ring = true)
        return true
    }

    // Smart Attack helpers for MLBB-style buttons (Hero / Creep / Tower-or-Deny)
    fun playerSmartAttack(
        preferCreep: Boolean = false,
        preferDeny: Boolean = false,
        preferStructure: Boolean = false,
        silentNoTarget: Boolean = false
    ) {
        val h = playerHero
        if (!h.alive) return
        val searchR = h.cachedAttackRange + 320f
        if (preferDeny) {
            val denyCreep = creeps.filter { it.alive && it.team == h.team && it.hp < it.maxHp * 0.5f && it.pos.distTo(h.pos) <= searchR }
                .minByOrNull { it.hp }
            if (denyCreep != null) {
                h.attackTargetId = denyCreep.id
                h.attackCommandAge = 0f
                h.facingAngle = h.pos.angleTo(denyCreep.pos)
                if (denyCreep.pos.distTo(h.pos) > h.cachedAttackRange) h.tapMoveTarget = denyCreep.pos.copyVec()
                else if (!h.isStunned() && !h.isDisarmed()) performHeroAutoAttack(h)
                return
            }
            val denyTower = towers.filter { it.alive && it.team == h.team && it.hp < it.maxHp * 0.1f && it.pos.distTo(h.pos) <= searchR }
                .minByOrNull { it.hp }
            if (denyTower != null) {
                h.attackTargetId = denyTower.id
                h.attackCommandAge = 0f
                h.facingAngle = h.pos.angleTo(denyTower.pos)
                if (!h.isStunned() && !h.isDisarmed()) performHeroAutoAttack(h)
                return
            }
        }
        val enemies = allEnemyUnits(h.team, h.pos, searchR).filter { !it.isInvisible() || fogOfWar.hasTrueSight(it.pos.x, it.pos.y) }
        val lockedHero = if (!preferCreep && !preferStructure) {
            enemies.filterIsInstance<HeroEntity>().firstOrNull { it.id == h.attackTargetId }
        } else null
        val target = lockedHero ?: when {
            preferStructure -> {
                enemies.filter { it is TowerEntity || it is BarracksEntity || it is AncientEntity }.minByOrNull { it.pos.distTo(h.pos) }
                    ?: enemies.minByOrNull { it.pos.distTo(h.pos) }
            }
            preferCreep -> {
                enemies.filter { it is CreepEntity || it is NeutralCreepEntity }.minByOrNull { it.hp }
                    ?: enemies.minByOrNull { it.pos.distTo(h.pos) }
            }
            else -> {
                enemies.filterIsInstance<HeroEntity>().minByOrNull { it.hp }
                    ?: enemies.minByOrNull { it.pos.distTo(h.pos) }
            }
        }
        if (target != null) {
            h.attackTargetId = target.id
            h.attackCommandAge = 0f
            h.facingAngle = h.pos.angleTo(target.pos)
            if (target.pos.distTo(h.pos) > h.cachedAttackRange) {
                h.tapMoveTarget = target.pos.copyVec()
            } else if (!h.isStunned() && !h.isDisarmed() && h.omnislashTimer <= 0f && h.dismemberTimer <= 0f && h.freezingFieldTimer <= 0f) {
                // Instant 0ms MLBB hit responsiveness!
                performHeroAutoAttack(h)
            }
        } else if (!silentNoTarget) {
            if (h.attackAnimTimer <= 0f) {
                h.attackAnimDuration = 0.22f
                h.attackAnimTimer = 0.22f
            }
            addFloatingText(h.pos, "No Target", 0xFF94A3B8.toInt(), 0.5f)
        }
    }
}
