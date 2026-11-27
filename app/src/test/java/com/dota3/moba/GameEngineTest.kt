package com.dota3.moba

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.HeroCatalog
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.engine.GameWorld
import com.dota3.moba.model.*
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
            for (comp in item.components) {
                assertNotNull("Missing component $comp for ${item.id}", ItemCatalog.get(comp))
            }
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
        val resolutions = listOf(1280f to 720f, 1600f to 720f, 1920f to 1080f, 2400f to 1080f, 2560f to 1600f)
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
}
