package com.dota3.moba

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.HeroCatalog
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.engine.MapLayout
import com.dota3.moba.model.*
import com.dota3.moba.render.DotaCameraController
import com.dota3.moba.render.HudCustomization
import com.dota3.moba.render.HudGeometry
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {

    @Test
    fun catalogIntegrity() {
        assertEquals(12, HeroCatalog.allHeroes.size)
        for (h in HeroCatalog.allHeroes) {
            assertEquals("Hero ${h.id} must have 4 abilities", 4, h.abilities.size)
            assertEquals("Hero ${h.id} must have 2 facets", 2, h.facets.size)
            assertEquals("Hero ${h.id} must have 4 talent tiers", 4, h.talents.size)
        }
        assertTrue(ItemCatalog.allItems.size >= 40)
        val ids = ItemCatalog.allItems.map { it.id }.toSet()
        assertEquals(ItemCatalog.allItems.size, ids.size)
        for (item in ItemCatalog.allItems) {
            var compCostSum = 0
            for (comp in item.components) {
                val cDef = ItemCatalog.get(comp)
                assertNotNull("Missing component $comp for ${item.id}", cDef)
                compCostSum += cDef!!.cost
            }
            assertTrue("Item ${item.id} cost (${item.cost}) < components sum ($compCostSum)", item.cost >= compCostSum)
        }
        assertEquals(12, ItemCatalog.neutralItems.size)
    }

    @Test
    fun dotaMathFormulas() {
        assertEquals(1.0f, DotaMath.armorMultiplier(0f), 0.001f)
        assertTrue(DotaMath.armorMultiplier(10f) < 0.7f)
        assertTrue(DotaMath.armorMultiplier(-10f) > 1.3f)
        assertEquals(1, DotaMath.levelForXp(0))
        assertEquals(6, DotaMath.levelForXp(2300))
        assertEquals(30, DotaMath.levelForXp(50000))
    }

    @Test
    fun worldSimulationAndCreepsAndBots() {
        val world = GameWorld("juggernaut", mode = GameMode.ALL_PICK_5V5)
        assertEquals(10, world.heroes.size)
        assertEquals(22, world.towers.size)
        assertEquals(12, world.barracks.size)

        // Simulate 15 seconds (300 ticks @ 0.05s) so creeps spawn and bots move/fight
        repeat(300) {
            world.step(0.05f)
        }
        assertTrue("Creeps should have spawned", world.creeps.isNotEmpty())
        for (h in world.heroes) {
            assertFalse(h.pos.x.isNaN())
            assertFalse(h.pos.y.isNaN())
        }
    }

    @Test
    fun itemRecipeCombineAndCourierAndRapier() {
        val world = GameWorld("pa", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.gold = 2000
        assertTrue(world.buyItem(ph, "boots"))
        assertEquals(1500, ph.gold)
        // Power Treads costs 1400, minus 500 for Boots = 900g!
        assertTrue(world.buyItem(ph, "power_treads"))
        assertEquals(600, ph.gold)
        assertTrue(ph.hasItem("power_treads"))
        assertFalse(ph.hasItem("boots"))

        // Leave fountain, buy item into stash, deliver via courier
        ph.pos.set(2400f, 2400f)
        ph.gold = 5000
        assertTrue(world.buyItem(ph, "aghanims_shard"))
        assertTrue(ph.hasShard)

        assertTrue(world.buyItem(ph, "blades_of_attack"))
        assertEquals(1, ph.stash.size)
        assertTrue(world.dispatchCourier(ph))
    }

    @Test
    fun allTwelveHeroesCastAbilitiesCleanly() {
        for (def in HeroCatalog.allHeroes) {
            val world = GameWorld(def.id, mode = GameMode.SANDBOX)
            val ph = world.playerHero
            ph.addXp(25000)
            for (i in 0..3) {
                ph.abilityLevels[i] = def.abilities[i].maxLevel
            }
            ph.recalcStats()
            ph.mana = ph.maxMana
            val enemy = world.heroes.first { it.team != ph.team }
            enemy.pos.set(ph.pos.x + 150f, ph.pos.y)
            for (i in 0..3) {
                world.castAbility(ph, i, enemy.pos, enemy.id)
            }
            world.step(0.05f)
            assertFalse(ph.pos.x.isNaN())
        }
    }

    @Test
    fun aegisResurrectionAndGlyphAndFog() {
        val world = GameWorld("axe", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.hasAegis = true
        val enemy = world.heroes.first { it.team != ph.team }

        // Deal lethal damage
        world.applyDamage(ph, 99999f, DamageType.PURE, enemy)
        assertFalse(ph.alive)
        assertTrue(ph.aegisRespawnTimer > 0f)

        // Advance 3.2 seconds -> Aegis revives hero!
        repeat(65) { world.step(0.05f) }
        assertTrue(ph.alive)
        assertFalse(ph.hasAegis)
        assertEquals(ph.maxHp, ph.hp, 1f)

        // Glyph fortifies towers
        assertTrue(world.activateGlyph(ph.team))
        assertTrue(world.towers.first { it.team == ph.team }.fortifyTimer > 0f)

        // Fog of War sees player
        assertTrue(world.fogOfWar.isVisible(ph.pos.x, ph.pos.y))
    }

    @Test
    fun divineRapierDropAndTalentsAndTwinGate() {
        val world = GameWorld("chronos", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.addXp(25000) // Level 25+
        assertNotNull(ph.availableTalentTier())
        assertTrue(world.chooseTalent(ph, 0, 0))
        assertTrue(world.chooseTalent(ph, 3, 1))

        // Buy Divine Rapier, move to river center, and die -> drops on map!
        ph.gold = 10000
        assertTrue(world.buyItem(ph, "divine_rapier"))
        assertTrue(ph.hasItem("divine_rapier"))
        ph.pos.set(2400f, 2400f)
        val enemy = world.heroes.first { it.team != ph.team }
        world.applyDamage(ph, 99999f, DamageType.PURE, enemy)
        assertFalse(ph.alive)
        assertTrue(world.droppedItems.any { it.itemId == "divine_rapier" })

        // Enemy walks over dropped rapier -> claims it!
        enemy.pos.set(2400f, 2400f)
        world.step(0.05f)
        assertTrue(enemy.hasItem("divine_rapier"))

        // Twin Gate teleportation
        enemy.tpCooldown = 0f
        enemy.pos.set(com.dota3.moba.engine.MapLayout.twinGateTopLeft.x, com.dota3.moba.engine.MapLayout.twinGateTopLeft.y)
        world.step(0.05f)
        assertTrue(enemy.pos.x > 4000f && enemy.pos.y > 4000f)
    }

    @Test
    fun mlbbSmartTargetPriorityHeroCreepTowerDeny() {
        val world = GameWorld("sniper", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        val enemyTower = world.towers.first { it.team != ph.team && it.blueprint.tier == 1 }
        ph.pos.set(enemyTower.pos.x - 200f, enemyTower.pos.y)
        world.playerSmartAttack(preferCreep = false, preferDeny = true, preferStructure = true)
        assertEquals(enemyTower.id, ph.attackTargetId)
    }

    @Test
    fun playerAttackAnimAndDamageNumbersWork() {
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        val enemy = world.heroes.first { it.team != ph.team }
        enemy.pos.set(ph.pos.x + 80f, ph.pos.y)

        world.playerSmartAttack()
        assertEquals(enemy.id, ph.attackTargetId)
        world.step(0.05f)

        assertTrue("Attack animation timer must activate", ph.attackAnimTimer > 0f)
        assertTrue("Player hit must spawn floating damage number", world.floatingTexts.isNotEmpty())
    }

    @Test
    fun mlbbHudGeometryHasZeroOverlapAndFitsAllPhones() {
        val resolutions = listOf(
            800f to 600f,
            854f to 480f,
            1280f to 720f,
            1600f to 720f,
            1920f to 1080f,
            2340f to 1080f,
            2400f to 1080f,
            2560f to 1600f,
            2208f to 1768f
        )
        val geo = com.dota3.moba.render.HudGeometry()
        for ((w, h) in resolutions) {
            geo.layout(w, h)
            val circles = mutableListOf<Pair<Vec2, Float>>()
            for (i in 0..3) circles.add(geo.abilityCenters[i] to geo.abilityRadius)
            circles.add(geo.attackCenter to geo.attackRadius)
            circles.add(geo.minionCenter to geo.subRadius)
            circles.add(geo.towerDenyCenter to geo.subRadius)
            circles.add(geo.tpCenter to geo.utilRadius)
            circles.add(geo.courierCenter to geo.utilRadius)
            circles.add(geo.glyphCenter to geo.utilRadius)
            circles.add(geo.cancelCenter to geo.cancelRadius)
            for (i in 0..2) circles.add(geo.targetLockCenters[i] to geo.targetLockRadius)

            // Every button must be strictly inside screen bounds!
            for ((idx, pair) in circles.withIndex()) {
                val (c, r) = pair
                assertTrue("Circle $idx left out of bounds at ${w}x${h}", c.x - r >= 0f)
                assertTrue("Circle $idx right out of bounds at ${w}x${h}", c.x + r <= w)
                assertTrue("Circle $idx top out of bounds at ${w}x${h}", c.y - r >= 0f)
                assertTrue("Circle $idx bottom out of bounds at ${w}x${h}", c.y + r <= h)
            }

            // No two circular buttons may overlap!
            for (i in circles.indices) {
                for (j in i + 1 until circles.size) {
                    val d = circles[i].first.distTo(circles[j].first)
                    val minD = circles[i].second + circles[j].second
                    assertTrue("Circles $i and $j overlap at ${w}x${h}: d=$d < $minD", d >= minD - 1f)
                }
            }

            // Tactical 2x2 buttons stay inside screen and clear of joystick / console / quickBuy
            for ((ti, tb) in geo.tacticalBtns.withIndex()) {
                assertTrue("Tactical $ti out of bounds at ${w}x${h}", tb.left >= 0f && tb.right <= w && tb.top >= geo.camBtn.bottom && tb.bottom <= h)
                assertFalse("Tactical $ti overlaps heroConsole at ${w}x${h}", tb.overlaps(geo.heroConsole))
                assertTrue("Tactical $ti overlaps joystick at ${w}x${h}", tb.bottom <= geo.joystickRestCenter.y - geo.joystickRadius)
            }

            // Top header stays clear of Quick-Buy and Shop menu
            assertFalse("topHeader overlaps quickBuy0 at ${w}x${h}", geo.topHeader.overlaps(geo.quickBuy[0]))
            assertFalse("topHeader overlaps shopBtn at ${w}x${h}", geo.topHeader.overlaps(geo.shopBtn))

            // Dota 2 Bottom Console sits cleanly between Joystick and Courier/Glyph column
            assertTrue("Console left overlaps joystick at ${w}x${h}", geo.heroConsole.left >= geo.joystickRestCenter.x + geo.joystickRadius)
            assertTrue("Console right overlaps courier/glyph at ${w}x${h}", geo.heroConsole.right <= geo.courierCenter.x - geo.utilRadius)
            assertTrue("Console overlaps minimap/cam at ${w}x${h}", geo.heroConsole.top > geo.camBtn.bottom)
            assertTrue("Item bar overlaps courier at ${w}x${h}", geo.itemSlots[5].right <= geo.courierCenter.x - geo.utilRadius)

            // All Dota console boxes fit inside heroConsole and do not overlap one another
            val consoleBoxes = listOf(
                geo.portraitBox, geo.attrBox, geo.heroPlate,
                geo.itemSlots[0], geo.itemSlots[1], geo.itemSlots[2],
                geo.itemSlots[3], geo.itemSlots[4], geo.itemSlots[5],
                geo.tpSlot, geo.neutralSlot
            )
            for ((bIdx, b) in consoleBoxes.withIndex()) {
                assertTrue("Box $bIdx left outside console at ${w}x${h}", b.left >= geo.heroConsole.left)
                assertTrue("Box $bIdx right outside console at ${w}x${h}", b.right <= geo.heroConsole.right)
                assertTrue("Box $bIdx top outside console at ${w}x${h}", b.top >= geo.heroConsole.top)
                assertTrue("Box $bIdx bottom outside console at ${w}x${h}", b.bottom <= geo.heroConsole.bottom)
            }
            for (i in consoleBoxes.indices) {
                for (j in i + 1 until consoleBoxes.size) {
                    assertFalse("Console boxes $i and $j overlap at ${w}x${h}", consoleBoxes[i].overlaps(consoleBoxes[j]))
                }
            }

            // +LVL pills stay inside screen and never overlap skill circles or console
            for (i in 0..3) {
                val pill = geo.levelUpPills[i]
                assertTrue("Pill $i out of bounds at ${w}x${h}", pill.left >= 0f && pill.right <= w && pill.top >= 0f && pill.bottom <= h)
                assertFalse("Pill $i overlaps heroConsole at ${w}x${h}", pill.overlaps(geo.heroConsole))
                for (j in 0..3) {
                    val c = geo.abilityCenters[j]
                    val nearX = c.x.coerceIn(pill.left, pill.right)
                    val nearY = c.y.coerceIn(pill.top, pill.bottom)
                    val d = kotlin.math.hypot(c.x - nearX, c.y - nearY)
                    assertTrue("Pill $i overlaps ability $j at ${w}x${h}: d=$d < ${geo.abilityRadius}", d >= geo.abilityRadius - 1f)
                }
            }
        }
    }

    @Test
    fun rangedHeroStopsAtAttackRangeWhenChasing() {
        val world = GameWorld("sniper", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        val enemy = world.heroes.first { it.team != ph.team }
        ph.pos.set(1600f, 2400f)
        enemy.pos.set(2600f, 2400f)

        world.playerSmartAttack()
        assertEquals(enemy.id, ph.attackTargetId)
        assertNotNull("Out-of-range enemy should start chase move", ph.tapMoveTarget)

        repeat(80) { world.step(0.05f) }
        val dist = ph.pos.distTo(enemy.pos)
        assertTrue("Sniper must stop near attack range (~750), got dist=$dist", dist > 550f && dist <= ph.cachedAttackRange + 40f)
        assertNull("tapMoveTarget should clear once in attack range", ph.tapMoveTarget)
    }

    @Test
    fun fleeingWithJoystickClearsStaleAttackTarget() {
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        val enemy = world.heroes.first { it.team != ph.team }
        ph.pos.set(2000f, 2400f)
        enemy.pos.set(2120f, 2400f)

        world.playerSmartAttack()
        assertEquals(enemy.id, ph.attackTargetId)

        // Hold joystick directly away (-X) for 0.55s
        ph.moveIntent.set(-1f, 0f)
        repeat(12) { world.step(0.05f) }
        assertNull("Fleeing away should clear attackTargetId so hero doesn't spin back", ph.attackTargetId)
    }

    @Test
    fun xpProgressAndMapTerrainHelpersWork() {
        assertEquals(0f, DotaMath.xpProgress(0, 1), 0.001f)
        assertEquals(0.5f, DotaMath.xpProgress(120, 1), 0.001f)
        assertEquals(1.0f, DotaMath.xpProgress(50000, 30), 0.001f)
        assertTrue(com.dota3.moba.engine.MapLayout.isInRiver(2400f, 2400f))
        assertTrue(com.dota3.moba.engine.MapLayout.highGroundFactor(300f, 4500f) > 0.9f)
        assertEquals(0f, com.dota3.moba.engine.MapLayout.highGroundFactor(2400f, 2400f), 0.01f)
    }

    @Test
    fun creepsAnimateWalkAndAttack() {
        val world = GameWorld("juggernaut", mode = GameMode.MID_1V1)
        repeat(120) { world.step(0.05f) }
        assertTrue(world.creeps.isNotEmpty())
        assertTrue("Creeps should animate walkCycle", world.creeps.any { it.walkCycle > 0f })
    }

    @Test
    fun courierPreservesStashWhenInventoryIsFull() {
        val world = GameWorld("pa", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.pos.set(2400f, 2400f)
        ph.gold = 20000
        while (ph.inventory.size < 6) {
            ph.inventory.add(com.dota3.moba.engine.ItemSlot("blades_of_attack"))
        }
        assertTrue(world.buyItem(ph, "boots"))
        assertEquals(1, ph.stash.size)
        assertTrue(world.dispatchCourier(ph))
        // Move courier to player and step until delivered/returned
        val courier = world.couriers.first { it.heroId == ph.id }
        courier.pos.set(ph.pos.x, ph.pos.y)
        world.step(0.05f)
        // Boots must NOT be deleted when inventory is full — kept safely in stash!
        assertEquals(6, ph.inventory.size)
        assertEquals(1, ph.stash.size)
        assertEquals("boots", ph.stash.first().itemId)
    }

    @Test
    fun buyItemPreservesComponentsWhenInventoryAndStashFull() {
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        ph.inventory.clear()
        ph.stash.clear()
        repeat(6) { ph.inventory.add(com.dota3.moba.engine.ItemSlot("blades_of_attack")) }
        repeat(6) { ph.stash.add(com.dota3.moba.engine.ItemSlot("chainmail")) }
        val goldBefore = ph.gold
        assertFalse(world.buyItem(ph, "vanguard"))
        assertEquals(goldBefore, ph.gold)
        assertEquals(6, ph.inventory.size)
        assertEquals(6, ph.stash.size)
    }

    @Test
    fun smartTpTargetUsesFountainFromLaneAndTowerFromFountain() {
        val world = GameWorld("juggernaut", playerTeam = Team.RADIANT, playerLane = 1, mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        // In fountain with locked camera -> TP out to lane tower
        val fromFountain = world.smartTpTarget(ph, ph.pos, camLocked = true)
        assertTrue("From fountain should TP out toward lane tower", fromFountain.distTo(com.dota3.moba.engine.MapLayout.radiantFountain) > 600f)

        // In lane with locked camera -> TP home to fountain
        ph.pos.set(2400f, 2400f)
        val fromLaneLocked = world.smartTpTarget(ph, ph.pos, camLocked = true)
        assertTrue("From lane locked should TP to fountain", fromLaneLocked.distTo(com.dota3.moba.engine.MapLayout.radiantFountain) < 50f)

        // Free cam -> TP toward cameraPos
        val customCam = Vec2(430f, 1650f)
        val freeCamTp = world.smartTpTarget(ph, customCam, camLocked = false)
        assertEquals(customCam.x, freeCamTp.x, 1f)
        assertEquals(customCam.y, freeCamTp.y, 1f)
    }

    @Test
    fun craftTreePieceByPieceQuickBuyAndNestedConsume() {
        // Queue for Daedalus includes nested Blades of Attack -> Crystalys -> Mithril Hammer -> Daedalus
        val fullQueue = ItemCatalog.getQuickBuyQueue("daedalus", emptyList())
        assertEquals(listOf("blades_of_attack", "crystalys", "mithril_hammer", "daedalus"), fullQueue)

        // Duplicate components (Desolator = 2x Mithril Hammer)
        val desoQueueOneOwned = ItemCatalog.getQuickBuyQueue("desolator", listOf("mithril_hammer"))
        assertEquals(listOf("mithril_hammer", "desolator"), desoQueueOneOwned)

        // Piece-by-piece purchase of BKB (Ogre Axe 1000 + Mithril Hammer 1600 + 1450 = 4050)
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        ph.inventory.clear()
        ph.gold = 4050
        assertTrue(world.buyItem(ph, "ogre_axe"))
        assertTrue(world.buyItem(ph, "mithril_hammer"))
        assertEquals(1450, ph.gold)
        assertTrue(world.buyItem(ph, "bkb"))
        assertEquals(0, ph.gold)
        assertTrue(ph.hasItem("bkb"))
        assertFalse(ph.hasItem("ogre_axe"))
        assertFalse(ph.hasItem("mithril_hammer"))

        // Nested direct craft: owning Blades of Attack discounts Daedalus by 450g
        ph.inventory.clear()
        ph.gold = 5150
        assertTrue(world.buyItem(ph, "blades_of_attack"))
        assertEquals(4700, ph.gold)
        assertTrue(world.buyItem(ph, "daedalus"))
        assertEquals(0, ph.gold)
        assertTrue(ph.hasItem("daedalus"))
        assertFalse(ph.hasItem("blades_of_attack"))
    }

    @Test
    fun threeHitComboAndZeroMsAttack() {
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        val enemy = world.heroes.first { it.team != ph.team }
        repeat(3) { enemy.inventory.add(com.dota3.moba.engine.ItemSlot("heart")) }
        enemy.recalcStats()
        enemy.hp = enemy.maxHp
        enemy.pos.set(ph.pos.x + 80f, ph.pos.y)

        // Instant 0ms responsiveness right inside playerSmartAttack without waiting a frame
        world.playerSmartAttack()
        assertTrue("0ms hit must trigger attackAnimTimer immediately", ph.attackAnimTimer > 0f)
        assertTrue("0ms hit must deal damage immediately", enemy.hp < enemy.maxHp)

        val stepsSeen = mutableSetOf(ph.attackComboStep)
        repeat(80) {
            world.step(0.05f)
            stepsSeen.add(ph.attackComboStep)
        }
        assertEquals("3-hit combo must cycle across steps 0, 1, 2", setOf(0, 1, 2), stepsSeen)

        // Idle resets combo back to 0
        ph.attackTargetId = null
        repeat(45) { world.step(0.05f) }
        assertEquals(0, ph.attackComboStep)
    }

    @Test
    fun tacticalCommandsDirectAlliedBots() {
        val world = GameWorld("juggernaut", playerTeam = Team.RADIANT, mode = GameMode.ALL_PICK_5V5)
        val ally = world.heroes.first { it.team == Team.RADIANT && !it.isPlayer }
        ally.pos.set(2400f, 2400f)
        ally.hp = ally.maxHp
        ally.mana = ally.maxMana

        // ROSHAN command rallies allied bot toward Roshan pit
        world.issueTacticalCommand(TacticalCommand.ROSHAN)
        assertEquals(TacticalCommand.ROSHAN, world.allyCommand)
        val dBefore = ally.pos.distTo(MapLayout.roshanPitPos)
        repeat(20) { world.step(0.05f) }
        assertTrue("Ally bot must move toward Roshan pit", ally.pos.distTo(MapLayout.roshanPitPos) < dBefore)

        // RETREAT command sends allied bot toward fountain
        val dFountainBefore = ally.pos.distTo(MapLayout.radiantFountain)
        world.issueTacticalCommand(TacticalCommand.RETREAT)
        repeat(20) { world.step(0.05f) }
        assertTrue("RETREAT must move ally bot toward Radiant fountain", ally.pos.distTo(MapLayout.radiantFountain) < dFountainBefore)

        // Killing Roshan resets ROSHAN order back to AUTO
        world.issueTacticalCommand(TacticalCommand.ROSHAN)
        world.applyDamage(world.roshan, 99999f, DamageType.PURE, world.playerHero)
        assertEquals(TacticalCommand.AUTO, world.allyCommand)
    }

    @Test
    fun enemyHeroTargetLockPrioritizesLockedHero() {
        val world = GameWorld("juggernaut", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.pos.set(2400f, 2400f)
        val enemies = world.heroes.filter { it.team != ph.team }.take(2)
        enemies[0].pos.set(2470f, 2400f)
        enemies[0].hp = 200f // Low HP hero
        enemies[1].pos.set(2490f, 2400f)
        enemies[1].hp = 800f // Higher HP hero

        assertTrue(world.lockEnemyHero(enemies[1].id))
        assertEquals(enemies[1].id, ph.attackTargetId)

        // Pressing ATK preserves explicitly locked enemy hero
        world.playerSmartAttack()
        assertEquals(enemies[1].id, ph.attackTargetId)
    }

    @Test
    fun dotaCameraPitchLookAheadSkillshotAndShake() {
        val world = GameWorld("pudge", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        val cam = DotaCameraController(ph.pos.x, ph.pos.y)

        // 1. Classic Dota ~60° angle + vertical console center offset
        val restPose = cam.computePose(0f)
        assertTrue("Camera pitch must be ~58-62 deg, was ${restPose.pitchDeg}", restPose.pitchDeg in 58f..62f)
        assertTrue("Camera lookZ must sit south of hero so hero centers above bottom console", restPose.lookZ > ph.pos.y)

        // 2. Movement look-ahead shifts toward moveIntent
        ph.moveIntent.set(1f, 0f)
        repeat(15) {
            cam.update(0.03f, ph, aimActive = false, Vec2(), 0f, null, 0f)
        }
        assertTrue("Moving +X must build +X lookAhead", cam.lookAhead.x > 25f)

        // 3. Long-range skillshot aim shifts camera toward target & adds dynamic zoom-out
        val hookAim = Vec2(ph.pos.x + 850f, ph.pos.y)
        repeat(20) {
            cam.update(0.03f, ph, aimActive = true, hookAim, 860f, null, 0f)
        }
        assertTrue("Skillshot aim must shift camera toward target", cam.skillshotShift.x > 150f)

        // 4. Camera shake adds transient jitter and decays cleanly
        cam.addShake(0.8f)
        cam.update(0.016f, ph, aimActive = false, Vec2(), 0f, null, 0f)
        val shakenPose = cam.computePose(0f)
        assertFalse(shakenPose.eyeX.isNaN())
        repeat(40) { cam.update(0.03f, ph, aimActive = false, Vec2(), 0f, null, 0f) }
        val settledPose = cam.computePose(0f)
        assertTrue(kotlin.math.abs(settledPose.eyeX - settledPose.lookX) < 0.1f)
    }

    @Test
    fun hudCustomizationBoundsStaySafe() {
        val geo = HudGeometry()
        val presets = listOf(
            HudCustomization(0.75f, 0.75f, 0.80f, 0.80f, -0.05f, -0.08f, -0.06f, -0.08f),
            HudCustomization(1.28f, 1.20f, 1.18f, 1.28f, 0.06f, 0.04f, 0.04f, 0.04f),
            HudCustomization(1.28f, 1.20f, 1.18f, 1.28f, 0.06f, -0.08f, -0.06f, -0.08f)
        )
        for ((w, h) in listOf(854f to 480f, 800f to 600f, 1280f to 720f, 2400f to 1080f, 2208f to 1768f)) {
            for (preset in presets) {
                geo.layout(w, h, preset)
                val circles = mutableListOf<Pair<Vec2, Float>>()
                for (i in 0..3) circles.add(geo.abilityCenters[i] to geo.abilityRadius)
                circles.add(geo.attackCenter to geo.attackRadius)
                circles.add(geo.minionCenter to geo.subRadius)
                circles.add(geo.towerDenyCenter to geo.subRadius)
                circles.add(geo.courierCenter to geo.utilRadius)
                circles.add(geo.glyphCenter to geo.utilRadius)
                circles.add(geo.cancelCenter to geo.cancelRadius)
                for (i in 0..2) circles.add(geo.targetLockCenters[i] to geo.targetLockRadius)

                for ((idx, pair) in circles.withIndex()) {
                    val (c, r) = pair
                    assertTrue("Custom circle $idx OOB at ${w}x${h}", c.x - r >= 0f && c.x + r <= w && c.y - r >= 0f && c.y + r <= h)
                }
                for (i in circles.indices) {
                    for (j in i + 1 until circles.size) {
                        val d = circles[i].first.distTo(circles[j].first)
                        val minD = circles[i].second + circles[j].second
                        assertTrue("Custom circles $i & $j overlap at ${w}x${h}: $d < $minD", d >= minD - 1f)
                    }
                }
                for (i in 0..3) {
                    val pill = geo.levelUpPills[i]
                    assertFalse("Custom pill $i overlaps console at ${w}x${h}", pill.overlaps(geo.heroConsole))
                    val nearX = geo.cancelCenter.x.coerceIn(pill.left, pill.right)
                    val nearY = geo.cancelCenter.y.coerceIn(pill.top, pill.bottom)
                    assertTrue("Cancel overlaps pill $i at ${w}x${h}", kotlin.math.hypot(geo.cancelCenter.x - nearX, geo.cancelCenter.y - nearY) >= geo.cancelRadius - 1f)
                }
                assertTrue("Joystick left OOB at ${w}x${h}", geo.joystickRestCenter.x - geo.joystickRadius >= 0f)
                assertTrue("Joystick bottom OOB at ${w}x${h}", geo.joystickRestCenter.y + geo.joystickRadius <= h)
                assertTrue("Joystick overlaps tactical at ${w}x${h}", geo.joystickRestCenter.y - geo.joystickRadius >= geo.tacticalBtns[3].bottom)
                assertTrue("Console overlaps joystick at ${w}x${h}", geo.heroConsole.left >= geo.joystickRestCenter.x + geo.joystickRadius)
                assertTrue("Console overlaps courier at ${w}x${h}", geo.heroConsole.right <= geo.courierCenter.x - geo.utilRadius)
                assertTrue("Items overlap courier at ${w}x${h}", geo.itemSlots[5].right <= geo.courierCenter.x - geo.utilRadius)
            }
        }
    }

    @Test
    fun courierCargoCountsTowardRecipeCraftAndQuickBuy() {
        val world = GameWorld("juggernaut", mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        ph.pos.set(2400f, 2400f) // out in mid lane
        ph.inventory.clear()
        ph.gold = 4050
        ph.quickBuyItemId = "bkb"

        // Buy Ogre Axe -> moves into courier cargo in flight
        assertTrue(world.buyItem(ph, "ogre_axe"))
        assertTrue(world.dispatchCourier(ph))
        assertTrue("Ogre Axe should be tracked across inventory/stash/courier", "ogre_axe" in world.allOwnedItemIds(ph))

        // Effective cost of BKB must deduct Ogre Axe while courier is flying!
        val bkbDef = ItemCatalog.get("bkb")!!
        assertEquals(3050, world.effectiveItemCost(ph, bkbDef))

        // Buy Mithril Hammer -> also enters stash/courier
        assertTrue(world.buyItem(ph, "mithril_hammer"))
        assertTrue(world.dispatchCourier(ph))
        assertEquals(1450, world.effectiveItemCost(ph, bkbDef))

        // Complete BKB while components are on courier -> clears quickBuy pin & delivers BKB
        assertTrue(world.buyItem(ph, "bkb"))
        assertEquals(0, ph.gold)
        assertNull("Completing pinned item must clear quickBuyItemId", ph.quickBuyItemId)
        assertTrue("Completed BKB should be owned on courier", "bkb" in world.allOwnedItemIds(ph))
        assertFalse("Ogre Axe must be consumed from courier", "ogre_axe" in world.allOwnedItemIds(ph))

        // If all components are already in inventory in lane, combine happens directly in inventory!
        ph.inventory.clear()
        ph.gold = 4050
        ph.inventory.add(com.dota3.moba.engine.ItemSlot("ogre_axe"))
        ph.inventory.add(com.dota3.moba.engine.ItemSlot("mithril_hammer"))
        assertTrue(world.buyItem(ph, "bkb"))
        assertTrue("Completed item from inventory components goes straight to inventory", ph.hasItem("bkb"))
    }

    @Test
    fun draftedRosterWorksIn1v1AndSandbox() {
        val w1v1 = GameWorld(
            playerHeroId = "pudge",
            playerTeam = Team.RADIANT,
            mode = GameMode.MID_1V1,
            draftedEnemyHeroIds = listOf("invoker")
        )
        assertEquals(2, w1v1.heroes.size)
        assertEquals("invoker", w1v1.heroes.first { it.team == Team.DIRE }.def.id)

        val wSb = GameWorld(
            playerHeroId = "sniper",
            playerTeam = Team.RADIANT,
            mode = GameMode.SANDBOX,
            draftedEnemyHeroIds = listOf("axe")
        )
        assertEquals("axe", wSb.heroes.first { it.team == Team.DIRE }.def.id)
    }

    @Test
    fun tacticalRoshanIgnoresDistractingLaneCreepsAndDeadRoshan() {
        val world = GameWorld("juggernaut", playerTeam = Team.RADIANT, mode = GameMode.ALL_PICK_5V5)
        val ally = world.heroes.first { it.team == Team.RADIANT && !it.isPlayer }
        // Place ally near mid wave with enemy creep right beside them, far from Roshan pit
        ally.pos.set(2400f, 2400f)
        ally.hp = ally.maxHp
        ally.mana = ally.maxMana
        world.step(2.1f) // spawn first creep wave

        world.issueTacticalCommand(TacticalCommand.ROSHAN)
        assertEquals(TacticalCommand.ROSHAN, world.allyCommand)
        val distBefore = ally.pos.distTo(MapLayout.roshanPitPos)
        repeat(30) { world.step(0.05f) }
        assertTrue("Ally ordered ROSHAN must march toward Roshan instead of stopping in lane", ally.pos.distTo(MapLayout.roshanPitPos) < distBefore - 150f)

        // When Roshan is dead, issuing ROSHAN must not hijack bots into empty pit
        world.applyDamage(world.roshan, 99999f, DamageType.PURE, world.playerHero)
        assertFalse(world.roshan.alive)
        world.issueTacticalCommand(TacticalCommand.ROSHAN)
        assertEquals(TacticalCommand.AUTO, world.allyCommand)
    }

    @Test
    fun draftedRosterPopulatesWorldWithoutDuplicates() {
        val allies = listOf("cm", "axe", "zeus", "invoker")
        val enemies = listOf("pudge", "pa", "sniper", "earthshaker", "chronos")
        val world = GameWorld(
            playerHeroId = "juggernaut",
            playerTeam = Team.RADIANT,
            mode = GameMode.ALL_PICK_5V5,
            draftedAllyHeroIds = allies,
            draftedEnemyHeroIds = enemies
        )
        assertEquals(10, world.heroes.size)
        assertEquals(10, world.heroes.map { it.def.id }.toSet().size)
        val radIds = world.heroes.filter { it.team == Team.RADIANT }.map { it.def.id }.toSet()
        val direIds = world.heroes.filter { it.team == Team.DIRE }.map { it.def.id }.toSet()
        assertEquals((allies + "juggernaut").toSet(), radIds)
        assertEquals(enemies.toSet(), direIds)
    }

    @Test
    fun inventorySwapAndSellByGridSlot() {
        val world = GameWorld("juggernaut", mode = GameMode.SANDBOX)
        val ph = world.playerHero
        ph.inventory.clear()
        ph.gold = 5000
        assertTrue(world.buyItem(ph, "boots"))
        assertTrue(world.buyItem(ph, "bkb"))
        assertEquals("boots", ph.itemAtGridSlot(0)?.itemId)
        assertEquals("bkb", ph.itemAtGridSlot(1)?.itemId)

        // Move Boots from slot 0 into empty slot 4
        assertTrue(world.swapInventoryGridSlots(ph, 0, 4))
        assertNull(ph.itemAtGridSlot(0))
        assertEquals("boots", ph.itemAtGridSlot(4)?.itemId)

        // Swap BKB (slot 1) with Boots (slot 4)
        assertTrue(world.swapInventoryGridSlots(ph, 1, 4))
        assertEquals("boots", ph.itemAtGridSlot(1)?.itemId)
        assertEquals("bkb", ph.itemAtGridSlot(4)?.itemId)

        // Use BKB at grid slot 4
        assertTrue(world.useItemInGridSlot(ph, 4))
        assertTrue(ph.hasStatus(StatusType.MAGIC_IMMUNE))

        // Sell Boots at grid slot 1 -> refunds 50% (250g) and keeps BKB in slot 4
        val goldBefore = ph.gold
        assertTrue(world.sellItemAtGridSlot(ph, 1))
        assertEquals(goldBefore + 250, ph.gold)
        assertNull(ph.itemAtGridSlot(1))
        assertEquals("bkb", ph.itemAtGridSlot(4)?.itemId)
    }

    @Test
    fun fogOfWarClearsDireSideWhenHeroMovesThere() {
        val world = GameWorld("juggernaut", playerTeam = Team.RADIANT, mode = GameMode.ALL_PICK_5V5)
        val ph = world.playerHero
        val direJungleSpot = Vec2(3600f, 1200f)
        val cellSize = world.fogOfWar.cellWorldSize()
        val gx = (direJungleSpot.x / cellSize).toInt()
        val gy = (direJungleSpot.y / cellSize).toInt()
        assertEquals(0, world.fogOfWar.getCellState(gx, gy))

        // Move player into Dire side and update fog
        ph.pos.set(direJungleSpot.x, direJungleSpot.y)
        world.updateFogOfWar()
        assertEquals(2, world.fogOfWar.getCellState(gx, gy))
        assertTrue(world.fogOfWar.isVisible(MapLayout.roshanPitPos.x, MapLayout.roshanPitPos.y).not())

        // Also check Roshan pit clears cleanly when ally moves there
        val ally = world.heroes.first { it.team == Team.RADIANT && !it.isPlayer }
        ally.pos.set(MapLayout.roshanPitPos.x, MapLayout.roshanPitPos.y)
        world.updateFogOfWar()
        assertTrue(world.fogOfWar.isVisible(MapLayout.roshanPitPos.x, MapLayout.roshanPitPos.y))
    }
}
