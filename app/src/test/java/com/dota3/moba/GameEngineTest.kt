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
        val resolutions = listOf(1280f to 720f, 1920f to 1080f, 2400f to 1080f, 2560f to 1600f)
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

            // Every button must be strictly inside screen bounds!
            for ((idx, pair) in circles.withIndex()) {
                val (c, r) = pair
                assertTrue("Circle $idx left out of bounds at ${w}x${h}", c.x - r >= 0f)
                assertTrue("Circle $idx right out of bounds at ${w}x${h}", c.x + r <= w)
                assertTrue("Circle $idx top out of bounds at ${w}x${h}", c.y - r >= 0f)
                assertTrue("Circle $idx bottom out of bounds at ${w}x${h}", c.y + r <= h)
            }

            // No two buttons may overlap!
            for (i in circles.indices) {
                for (j in i + 1 until circles.size) {
                    val d = circles[i].first.distTo(circles[j].first)
                    val minD = circles[i].second + circles[j].second
                    assertTrue("Circles $i and $j overlap at ${w}x${h}: d=$d < $minD", d >= minD - 1f)
                }
            }

            // Item slots & Hero Plate fit inside screen and stay left of glyph button
            assertTrue("Item bar overlaps glyph at ${w}x${h}", geo.itemSlots[5].right <= geo.glyphCenter.x - geo.utilRadius)
            assertTrue("Hero plate fits at ${w}x${h}", geo.heroPlate.top >= 0f && geo.heroPlate.bottom <= geo.itemSlots[0].top)
        }
    }
}
