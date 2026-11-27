package com.dota3.moba.engine

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.model.BotDifficulty
import com.dota3.moba.model.GameMode
import com.dota3.moba.model.TargetType
import com.dota3.moba.model.Team
import kotlin.random.Random

object BotAI {

    private val coreBuildOrder = listOf(
        "boots", "magic_wand", "power_treads", "vanguard", "maelstrom",
        "bkb", "desolator", "daedalus", "heart", "butterfly", "satanic"
    )
    private val casterBuildOrder = listOf(
        "boots", "magic_wand", "arcane_boots", "blink_dagger", "euls_scepter",
        "aghanims_scepter", "bkb", "shivas_guard", "scythe_of_vyse", "refresher"
    )

    fun updateBot(world: GameWorld, hero: HeroEntity, dt: Float, difficulty: BotDifficulty) {
        if (!hero.alive) {
            // Bots consider buyback late game when Ancient is threatened
            if (world.gameTime > 900f && hero.respawnTimer > 15f && hero.buybackCooldown <= 0f && hero.gold >= hero.buybackCost() + 200) {
                val ownAncient = if (hero.team == Team.RADIANT) world.radiantAncient else world.direAncient
                val enemyNearAncient = world.heroes.any { it.team != hero.team && it.alive && it.pos.distTo(ownAncient.pos) < 1300f }
                if (enemyNearAncient) {
                    world.buyback(hero)
                }
            }
            return
        }
        if (hero.isStunned() || hero.hasStatus(com.dota3.moba.model.StatusType.CYCLONE) || hero.tpChannelTimer > 0f) {
            hero.moveIntent.set(0f, 0f)
            return
        }

        // Sandbox enemy bots stand or lightly skirmish only if near
        if (world.mode == GameMode.SANDBOX && hero.team != world.playerHero.team) {
            hero.moveIntent.set(0f, 0f)
            return
        }

        hero.botStateTimer -= dt
        if (hero.skillPoints > 0) autoLevelAbilities(hero)
        hero.availableTalentTier()?.let { tier ->
            world.chooseTalent(hero, tier, if (Random.nextBoolean()) 0 else 1)
        }

        // Bot shopping & courier
        if (hero.botStateTimer <= 0f) {
            hero.botStateTimer = 0.35f + Random.nextFloat() * 0.25f
            tryBotShop(world, hero, difficulty)
        }

        // Consumables when hurt
        if (hero.hp < hero.maxHp * 0.6f) {
            world.tryUseItemById(hero, "tango")
            if (hero.hp < hero.maxHp * 0.42f) {
                world.tryUseItemById(hero, "salve")
                world.tryUseItemById(hero, "magic_wand")
            }
        }
        if (hero.mana < hero.maxMana * 0.35f) {
            world.tryUseItemById(hero, "arcane_boots")
            world.tryUseItemById(hero, "clarity")
        }

        val fountain = if (hero.team == Team.RADIANT) MapLayout.radiantFountain else MapLayout.direFountain
        val enemyAncient = if (hero.team == Team.RADIANT) world.direAncient else world.radiantAncient
        val ownAncient = if (hero.team == Team.RADIANT) world.radiantAncient else world.direAncient

        // Healing at fountain check
        if (hero.pos.distTo(fountain) < 380f && (hero.hp < hero.maxHp * 0.85f || hero.mana < hero.maxMana * 0.7f)) {
            hero.botRetreating = true
            hero.moveIntent.set(0f, 0f)
            return
        }

        val enemyHeroesNear = world.heroes.filter {
            it.alive && it.team != hero.team && !it.isInvisible() && it.pos.distTo(hero.pos) < 950f
        }
        val allyHeroesNear = world.heroes.count {
            it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 950f
        }

        // Retreat if low HP or heavily outnumbered under enemy tower
        val underEnemyTower = world.towers.any {
            it.alive && it.team != hero.team && it.pos.distTo(hero.pos) < it.attackRange + 60f &&
                world.creeps.none { c -> c.alive && c.team == hero.team && c.pos.distTo(it.pos) < it.attackRange }
        }

        if (hero.hp < hero.maxHp * 0.27f || underEnemyTower) {
            hero.botRetreating = true
        } else if (hero.hp > hero.maxHp * 0.62f) {
            hero.botRetreating = false
        }

        if (hero.botRetreating) {
            // Try Blink/Phase Boots/BKB escape
            if (enemyHeroesNear.isNotEmpty() && hero.hp < hero.maxHp * 0.35f) {
                world.tryUseItemById(hero, "phase_boots")
                world.tryUseItemById(hero, "shadow_blade")
            }
            moveToward(world, hero, fountain)
            hero.attackTargetId = null
            return
        }

        // Check if own base is under attack!
        val baseThreat = world.heroes.firstOrNull {
            it.alive && it.team != hero.team && !it.isInvisible() && it.pos.distTo(ownAncient.pos) < 1350f
        }

        // Decide strategic target point
        val waypoints = MapLayout.laneWaypoints[hero.assignedLane.coerceIn(0, 2)]
        val laneIdx = if (hero.team == Team.RADIANT) {
            // Advance along lane towards Dire, or group mid after 12 min
            if (world.gameTime > 720f && hero.level >= 9) {
                world.findFurthestAllyFront(hero.team, 1) ?: MapLayout.laneWaypoints[1][3]
            } else {
                world.findFurthestAllyFront(hero.team, hero.assignedLane) ?: waypoints[2]
            }
        } else {
            if (world.gameTime > 720f && hero.level >= 9) {
                world.findFurthestAllyFront(hero.team, 1) ?: MapLayout.laneWaypoints[1][3]
            } else {
                world.findFurthestAllyFront(hero.team, hero.assignedLane) ?: waypoints[4]
            }
        }

        // Global Ult check (Zeus Thundergod's Wrath!)
        if (hero.def.id == "zeus" && hero.abilityLevels[3] > 0 && hero.abilityCooldowns[3] <= 0f) {
            val lowGlobal = world.heroes.firstOrNull {
                it.alive && it.team != hero.team && it.hp < 360f + hero.abilityLevels[3] * 140f
            }
            if (lowGlobal != null) {
                world.castAbility(hero, 3, lowGlobal.pos, lowGlobal.id)
            }
        }

        // Nearby combat targets
        val bestHeroTarget = enemyHeroesNear.minByOrNull { it.hp + it.pos.distTo(hero.pos) * 0.35f }
        val nearEnemyCreeps = world.creeps.filter { it.alive && it.team != hero.team && it.pos.distTo(hero.pos) < 760f }
        val nearOwnCreeps = world.creeps.filter { it.alive && it.team == hero.team && it.pos.distTo(hero.pos) < 620f }
        val nearEnemyTowers = world.towers.filter { it.alive && it.team != hero.team && it.pos.distTo(hero.pos) < 780f && world.isTowerVulnerable(it) }
        val nearEnemyRax = world.barracks.filter { it.alive && it.team != hero.team && it.pos.distTo(hero.pos) < 780f && world.isBarracksVulnerable(it) }

        // Last-hit & Deny scoring
        val lastHitCreep = nearEnemyCreeps.firstOrNull { it.hp <= hero.cachedDamage * 1.15f }
        val denyCreep = if (difficulty != BotDifficulty.EASY) {
            nearOwnCreeps.firstOrNull { it.hp < it.maxHp * 0.48f && it.hp <= hero.cachedDamage * 1.1f }
        } else null

        // Use items & abilities in hero fight
        if (bestHeroTarget != null && (allyHeroesNear >= enemyHeroesNear.size || bestHeroTarget.hp < bestHeroTarget.maxHp * 0.5f)) {
            val dist = hero.pos.distTo(bestHeroTarget.pos)
            if (dist < 500f && enemyHeroesNear.size >= 2) {
                world.tryUseItemById(hero, "bkb")
                world.tryUseItemById(hero, "shivas_guard")
            }
            if (dist < 420f) {
                world.tryUseItemById(hero, "orchid", bestHeroTarget.pos, bestHeroTarget.id)
                world.tryUseItemById(hero, "scythe_of_vyse", bestHeroTarget.pos, bestHeroTarget.id)
                world.tryUseItemById(hero, "mask_of_madness")
                if (hero.hp < hero.maxHp * 0.55f) world.tryUseItemById(hero, "satanic")
            }
            tryBotCastCombatSpells(world, hero, bestHeroTarget, enemyHeroesNear, difficulty)
        }

        // Target priority
        val target: CombatUnit? = when {
            baseThreat != null -> baseThreat
            lastHitCreep != null && (bestHeroTarget == null || bestHeroTarget.hp > bestHeroTarget.maxHp * 0.35f) -> lastHitCreep
            denyCreep != null && bestHeroTarget == null -> denyCreep
            bestHeroTarget != null && (bestHeroTarget.pos.distTo(hero.pos) < 620f || bestHeroTarget.hp < bestHeroTarget.maxHp * 0.45f) -> bestHeroTarget
            nearEnemyCreeps.isNotEmpty() -> nearEnemyCreeps.minByOrNull { it.hp }
            nearEnemyTowers.isNotEmpty() -> nearEnemyTowers.first()
            nearEnemyRax.isNotEmpty() -> nearEnemyRax.first()
            enemyAncient.pos.distTo(hero.pos) < 850f && world.isAncientVulnerable(enemyAncient.team) -> enemyAncient
            else -> null
        }

        if (target != null) {
            val d = hero.pos.distTo(target.pos)
            val desiredDist = hero.cachedAttackRange + target.radius * 0.7f
            if (d <= desiredDist + 20f) {
                hero.attackTargetId = target.id
                // Slight orb-walk or hold
                hero.moveIntent.set(0f, 0f)
            } else {
                hero.attackTargetId = target.id
                moveToward(world, hero, target.pos)
            }
        } else {
            hero.attackTargetId = null
            // Check nearby Rune!
            val nearRune = world.runes.firstOrNull { it.alive && it.pos.distTo(hero.pos) < 550f }
            val moveGoal = when {
                baseThreat != null -> ownAncient.pos
                nearRune != null -> nearRune.pos
                else -> laneIdx
            }
            moveToward(world, hero, moveGoal)
        }
    }

    private fun tryBotCastCombatSpells(
        world: GameWorld,
        hero: HeroEntity,
        target: HeroEntity,
        allEnemies: List<HeroEntity>,
        difficulty: BotDifficulty
    ) {
        if (hero.isSilenced()) return
        val dist = hero.pos.distTo(target.pos)
        // Ultimate first if favorable
        if (hero.abilityLevels[3] > 0 && hero.abilityCooldowns[3] <= 0f && Random.nextFloat() < 0.45f * difficulty.reactionMult) {
            val abR = hero.def.abilities[3]
            if (abR.targetType != TargetType.PASSIVE) {
                val shouldUlt = when (hero.def.id) {
                    "axe" -> target.hp <= abR.baseValues[hero.abilityLevels[3] - 1] + 50f && dist < 240f
                    "earthshaker" -> allEnemies.count { it.pos.distTo(hero.pos) < 420f } >= 2
                    "chronos" -> allEnemies.count { it.pos.distTo(target.pos) < 320f } >= 1 && dist < 520f
                    else -> dist <= (abR.castRange.takeIf { it > 0f } ?: abR.radius.takeIf { it > 0f } ?: 380f)
                }
                if (shouldUlt) {
                    world.castAbility(hero, 3, target.pos, target.id)
                }
            }
        }
        // Basic Q/W/E spells
        for (idx in 0..2) {
            val ab = hero.def.abilities[idx]
            if (ab.targetType == TargetType.PASSIVE) continue
            if (hero.abilityLevels[idx] <= 0 || hero.abilityCooldowns[idx] > 0f) continue
            if (ab.targetType == TargetType.TOGGLE) {
                if (hero.def.id == "pudge") {
                    val wantRot = dist < 210f && hero.hp > hero.maxHp * 0.3f
                    if (wantRot != hero.rotEnabled) world.castAbility(hero, idx, target.pos, target.id)
                }
                continue
            }
            val range = ab.castRange.takeIf { it > 0f } ?: ab.radius.takeIf { it > 0f } ?: 350f
            if (dist <= range * 0.95f && Random.nextFloat() < 0.35f * difficulty.reactionMult) {
                world.castAbility(hero, idx, target.pos, target.id)
                break
            }
        }
    }

    private fun tryBotShop(world: GameWorld, hero: HeroEntity, difficulty: BotDifficulty) {
        if (difficulty != BotDifficulty.EASY && difficulty.goldBonusPerMin > 0) {
            hero.gold += (difficulty.goldBonusPerMin / 160).coerceAtLeast(0)
        }
        val plan = if (hero.def.attribute == com.dota3.moba.model.PrimaryAttribute.INTELLIGENCE || hero.def.id == "invoker") {
            casterBuildOrder
        } else coreBuildOrder

        if (hero.botItemPlanIndex < plan.size) {
            val nextId = plan[hero.botItemPlanIndex]
            val item = ItemCatalog.get(nextId)
            if (item != null) {
                if (hero.hasItem(nextId)) {
                    hero.botItemPlanIndex++
                } else if (world.canAffordItem(hero, nextId)) {
                    if (world.buyItem(hero, nextId)) {
                        hero.botItemPlanIndex++
                        world.dispatchCourier(hero)
                    }
                }
            }
        }
    }

    private fun autoLevelAbilities(hero: HeroEntity) {
        var guard = 0
        while (hero.skillPoints > 0 && guard++ < 10) {
            var leveled = false
            // Prioritize R -> Q -> W -> E
            val order = intArrayOf(3, 0, 1, 2)
            for (idx in order) {
                if (hero.canLevelAbility(idx)) {
                    hero.abilityLevels[idx]++
                    hero.skillPoints--
                    leveled = true
                    break
                }
            }
            if (!leveled) break
        }
        hero.recalcStats()
    }

    private fun moveToward(world: GameWorld, hero: HeroEntity, dest: Vec2) {
        val dx = dest.x - hero.pos.x
        val dy = dest.y - hero.pos.y
        val len = kotlin.math.hypot(dx, dy)
        if (len > 16f) {
            hero.moveIntent.set(dx / len, dy / len)
        } else {
            hero.moveIntent.set(0f, 0f)
        }
    }
}
