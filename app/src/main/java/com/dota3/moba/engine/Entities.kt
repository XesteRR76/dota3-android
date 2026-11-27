package com.dota3.moba.engine

import com.dota3.moba.core.DotaMath
import com.dota3.moba.core.Vec2
import com.dota3.moba.data.ItemCatalog
import com.dota3.moba.model.*

data class ActiveStatus(
    val type: StatusType,
    var remaining: Float,
    var magnitude: Float = 0f,
    var sourceTeam: Team = Team.NEUTRAL,
    var sourceId: String = ""
)

data class ItemSlot(
    val itemId: String,
    var stacks: Int = 1,
    var charges: Int = 0,
    var cooldownRemaining: Float = 0f,
    var treadMode: Int = 0 // 0=STR, 1=AGI, 2=INT
)

enum class CreepType { MELEE, RANGED, SIEGE, SUPER_MELEE, MEGA_MELEE }

abstract class CombatUnit(
    val id: String,
    val team: Team,
    val pos: Vec2,
    var radius: Float
) {
    var hp: Float = 100f
    var maxHp: Float = 100f
    var mana: Float = 0f
    var maxMana: Float = 0f
    var armor: Float = 0f
    var magicResist: Float = 0.25f
    var moveSpeed: Float = 300f
    var facingAngle: Float = 0f
    var attackCooldown: Float = 0f
    var alive: Boolean = true
    var armorCorruption: Float = 0f
    var armorCorruptionTimer: Float = 0f
    var recentDamageTaken: Float = 0f
    var recentDamageTimer: Float = 0f
    var attackAnimTimer: Float = 0f
    var attackAnimDuration: Float = 0.30f
    var attackComboStep: Int = 0
    var attackComboResetTimer: Float = 0f
    var lastHitWasCrit: Boolean = false
    var walkCycle: Float = 0f
    val statuses: MutableList<ActiveStatus> = mutableListOf()

    fun addStatus(type: StatusType, duration: Float, magnitude: Float = 0f, sourceTeam: Team = Team.NEUTRAL, sourceId: String = "") {
        if (isMagicImmune() && type in listOf(StatusType.STUN, StatusType.ROOT, StatusType.SILENCE, StatusType.DISARM, StatusType.SLOW, StatusType.HEX, StatusType.CYCLONE)) {
            return
        }
        val existing = statuses.firstOrNull { it.type == type }
        if (existing != null) {
            existing.remaining = maxOf(existing.remaining, duration)
            existing.magnitude = maxOf(existing.magnitude, magnitude)
            existing.sourceId = sourceId
            existing.sourceTeam = sourceTeam
        } else {
            statuses.add(ActiveStatus(type, duration, magnitude, sourceTeam, sourceId))
        }
    }

    fun hasStatus(type: StatusType): Boolean = statuses.any { it.type == type && it.remaining > 0f }
    fun isStunned(): Boolean = hasStatus(StatusType.STUN) || hasStatus(StatusType.HEX) || hasStatus(StatusType.CYCLONE) || hasStatus(StatusType.CHRONO_FREEZE)
    fun isRooted(): Boolean = hasStatus(StatusType.ROOT)
    fun isSilenced(): Boolean = hasStatus(StatusType.SILENCE) || hasStatus(StatusType.HEX)
    fun isDisarmed(): Boolean = hasStatus(StatusType.DISARM) || hasStatus(StatusType.HEX)
    fun isMagicImmune(): Boolean = hasStatus(StatusType.MAGIC_IMMUNE) || hasStatus(StatusType.BLADE_FURY)
    fun isInvulnerable(): Boolean = hasStatus(StatusType.CYCLONE)
    fun isInvisible(): Boolean = (hasStatus(StatusType.INVISIBLE) || hasStatus(StatusType.BLUR) || hasStatus(StatusType.SMOKE)) && !hasStatus(StatusType.TRUE_SIGHT_REVEAL)
}

class HeroEntity(
    id: String,
    team: Team,
    pos: Vec2,
    val def: HeroDef,
    val isPlayer: Boolean,
    var facetIndex: Int = 0,
    var assignedLane: Int = 1 // 0=top, 1=mid, 2=bot
) : CombatUnit(id, team, pos, 28f) {

    var level: Int = 1
    var xp: Int = 0
    var gold: Int = 600
    var reliableGold: Int = 0
    var netWorth: Int = 600
    var totalGoldEarned: Int = 600

    var kills: Int = 0
    var deaths: Int = 0
    var assists: Int = 0
    var lastHits: Int = 0
    var denies: Int = 0
    var killStreak: Int = 0
    var heroDamageDealt: Int = 0
    var towerDamageDealt: Int = 0

    var respawnTimer: Float = 0f
    var buybackCooldown: Float = 0f
    var hasAegis: Boolean = false
    var aegisRespawnTimer: Float = 0f

    // Abilities: Q=0, W=1, E=2, R=3
    val abilityLevels = IntArray(4) { if (it == 0) 1 else 0 }
    val abilityCooldowns = FloatArray(4) { 0f }
    val abilityCharges = IntArray(4) { def.abilities[it].maxCharges }
    val abilityChargeTimers = FloatArray(4) { 0f }
    var skillPoints: Int = 0
    var rotEnabled: Boolean = false

    // Talents & Aghanim
    val talentsChosen = IntArray(4) { -1 } // -1=none, 0=left, 1=right
    var hasScepter: Boolean = false
    var hasShard: Boolean = false

    // Items: 6 inventory + stash + neutral + TP (fast Dota 3 pacing: no backpack/secret shop travel)
    val inventory: MutableList<ItemSlot> = mutableListOf()
    val stash: MutableList<ItemSlot> = mutableListOf()
    var neutralItemId: String? = null
    var tpCooldown: Float = 0f
    var tpChannelTimer: Float = 0f
    var tpTargetPos: Vec2? = null

    // Hero-specific counters
    var fleshHeapStacks: Int = 0
    var coatOfBloodStacks: Int = 0
    var desoStacks: Int = 0
    var enchantTotemBuff: Boolean = false
    var dissimilateTimer: Float = 0f
    var dissimilateTarget: Vec2? = null
    var omnislashTimer: Float = 0f
    var omnislashSlashAccum: Float = 0f
    var omnislashTargetId: String? = null
    var bladeFuryTickAccum: Float = 0f
    var rotTickAccum: Float = 0f
    var freezingFieldTimer: Float = 0f
    var freezingFieldAccum: Float = 0f
    var dismemberTimer: Float = 0f
    var dismemberTargetId: String? = null
    var dismemberAccum: Float = 0f
    var castAnimTimer: Float = 0f

    // Input / AI targetting
    var moveIntent = Vec2(0f, 0f)
    var tapMoveTarget: Vec2? = null
    var attackTargetId: String? = null
    var attackCommandAge: Float = 99f
    var quickBuyItemId: String? = null
    var botStateTimer: Float = 0f
    var botItemPlanIndex: Int = 0
    var botRetreating: Boolean = false

    // Computed stats cache
    var cachedStr: Float = def.baseStr
    var cachedAgi: Float = def.baseAgi
    var cachedInt: Float = def.baseInt
    var cachedDamage: Float = 50f
    var cachedAttackSpeed: Float = 100f
    var cachedAttackRange: Float = def.attackRange
    var cachedMoveSpeed: Float = def.baseMoveSpeed
    var cachedHpRegen: Float = 2f
    var cachedManaRegen: Float = 1f
    var cachedSpellAmp: Float = 0f
    var cachedCdr: Float = 0f
    var cachedLifesteal: Float = 0f
    var cachedSpellLifesteal: Float = 0f
    var cachedEvasion: Float = 0f
    var cachedCritChance: Float = 0f
    var cachedCritMult: Float = 1f
    var cachedCleave: Float = 0f
    var cachedRadianceDps: Float = 0f

    // Damage history for assist credit (attackerId -> timestamp)
    val recentAttackers = HashMap<String, Float>()

    init {
        // Every hero starts with a free Town Portal Scroll & 1 Tango!
        inventory.add(ItemSlot("tango", stacks = 2))
        recalcStats()
        hp = maxHp
        mana = maxMana
    }

    fun hasTalent(key: String): Boolean {
        for (i in def.talents.indices) {
            val choice = talentsChosen[i]
            if (choice == 0 && def.talents[i].leftKey == key) return true
            if (choice == 1 && def.talents[i].rightKey == key) return true
        }
        return false
    }

    fun hasItem(itemId: String): Boolean = inventory.any { it.itemId == itemId }

    fun recalcStats() {
        val lvlDelta = (level - 1).coerceAtLeast(0)
        var str = def.baseStr + def.strGain * lvlDelta
        var agi = def.baseAgi + def.agiGain * lvlDelta
        var intel = def.baseInt + def.intGain * lvlDelta

        // Pudge Flesh Heap
        if (def.id == "pudge") {
            val mult = if (hasTalent("heap_2x")) 1.8f else 1.0f
            str += fleshHeapStacks * 1.5f * mult
        }
        if (hasTalent("agi_15")) agi += 15f

        var itemDmg = 0
        var itemArmor = 0f
        var itemMr = 0.25f
        var itemHp = 0f
        var itemMana = 0f
        var itemHpReg = 0f
        var itemManaReg = 0f
        var itemMs = 0f
        var itemAs = 0f
        var itemLs = 0f
        var itemSpellLs = 0f
        var itemEva = 0f
        var itemSpellAmp = 0f
        var itemCdr = 0f
        var bestCritChance = 0f
        var bestCritMult = 1f
        var cleave = 0f
        var radDps = 0f

        for (slot in inventory) {
            val item = ItemCatalog.get(slot.itemId) ?: continue
            str += item.bonusStr
            agi += item.bonusAgi
            intel += item.bonusInt
            if (item.id == "power_treads") {
                when (slot.treadMode) {
                    0 -> str += 6f
                    1 -> agi += 6f
                    else -> intel += 6f
                }
            }
            itemDmg += item.bonusDamage
            itemArmor += item.bonusArmor
            itemMr = 1f - (1f - itemMr) * (1f - item.bonusMagicResist)
            itemHp += item.bonusHp
            itemMana += item.bonusMana
            itemHpReg += item.bonusHpRegen
            itemManaReg += item.bonusManaRegen
            itemMs = maxOf(itemMs, item.bonusMoveSpeed) // boots move speed doesn't stack infinitely like Dota
            itemAs += item.bonusAttackSpeed
            itemLs += item.bonusLifesteal
            itemSpellLs += item.bonusSpellLifesteal
            itemEva = 1f - (1f - itemEva) * (1f - item.bonusEvasion)
            itemSpellAmp += item.bonusSpellAmp
            itemCdr = 1f - (1f - itemCdr) * (1f - item.bonusCdr)
            if (item.critChance > 0f && item.critMult > bestCritMult) {
                bestCritChance = item.critChance
                bestCritMult = item.critMult
            }
            cleave += item.cleavePct
            radDps = maxOf(radDps, item.radianceDps)
            if (item.id == "desolator") itemDmg += desoStacks
        }

        // Neutral item
        neutralItemId?.let { nid ->
            ItemCatalog.getNeutral(nid)?.let { n ->
                str += n.bonusAllStats
                agi += n.bonusAllStats
                intel += n.bonusAllStats
                itemDmg += n.bonusDamage
                itemArmor += n.bonusArmor
                itemMs += n.bonusMoveSpeed
                itemAs += n.bonusAttackSpeed
                itemLs += n.bonusLifesteal
                itemSpellAmp += n.bonusSpellAmp
                itemCdr = 1f - (1f - itemCdr) * (1f - n.bonusCdr)
                itemHp += n.bonusHp
                itemManaReg += n.bonusManaRegen
            }
        }

        // Facet bonuses
        val facet = def.facets.getOrNull(facetIndex)
        if (facet != null) {
            itemDmg += facet.bonusDamage
            itemArmor += facet.bonusArmor
            itemMs += facet.bonusMoveSpeed
            itemSpellAmp += facet.bonusSpellAmp
            itemCdr = 1f - (1f - itemCdr) * (1f - facet.bonusCdr)
            itemLs += facet.bonusLifesteal
            if (facet.bonusCritChance > 0f) bestCritChance += facet.bonusCritChance
        }

        // Hero innate & passives
        if (def.id == "invoker") {
            itemSpellAmp += level * 0.012f
            itemCdr = 1f - (1f - itemCdr) * (1f - level * 0.008f)
        }
        if (def.id == "pa") {
            itemEva = 1f - (1f - itemEva) * (1f - (0.15f + level * 0.01f))
            val blurLvl = abilityLevels[2]
            if (blurLvl > 0) {
                val blurEva = def.abilities[2].baseValues[blurLvl - 1]
                itemEva = 1f - (1f - itemEva) * (1f - blurEva)
            }
            val coupLvl = abilityLevels[3]
            if (coupLvl > 0) {
                val coupMult = def.abilities[3].baseValues[coupLvl - 1]
                if (coupMult > bestCritMult) {
                    bestCritMult = coupMult
                    bestCritChance = 0.17f + (if (hasTalent("crit_chance_10")) 0.10f else 0f)
                }
            }
        }
        if (def.id == "juggernaut") {
            val bdLvl = abilityLevels[2]
            if (bdLvl > 0) {
                val bdMult = def.abilities[2].baseValues[bdLvl - 1] + (if (hasTalent("crit_50")) 0.50f else 0f)
                if (bdMult > bestCritMult) {
                    bestCritMult = bdMult
                    bestCritChance = 0.35f
                }
            }
        }
        if (def.id == "axe") {
            itemArmor += coatOfBloodStacks * 0.8f
        }
        if (def.id == "antimage" && abilityLevels[2] > 0) {
            val csMr = def.abilities[2].baseValues[abilityLevels[2] - 1]
            itemMr = 1f - (1f - itemMr) * (1f - csMr)
        }
        if (def.id == "chronos") {
            itemCdr = 1f - (1f - itemCdr) * (1f - 0.10f)
        }

        // Talents
        if (hasTalent("hp_150")) itemHp += 150f
        if (hasTalent("hp_200")) itemHp += 200f
        if (hasTalent("hp_250")) itemHp += 250f
        if (hasTalent("hp_280")) itemHp += 280f
        if (hasTalent("hp_300")) itemHp += 300f
        if (hasTalent("hp_350")) itemHp += 350f
        if (hasTalent("hp_450")) itemHp += 450f
        if (hasTalent("hp_500")) itemHp += 500f
        if (hasTalent("mana_200")) itemMana += 200f
        if (hasTalent("mana_220")) itemMana += 220f
        if (hasTalent("mana_300")) itemMana += 300f
        if (hasTalent("dmg_15")) itemDmg += 15
        if (hasTalent("dmg_25")) itemDmg += 25
        if (hasTalent("dmg_30")) itemDmg += 30
        if (hasTalent("dmg_35")) itemDmg += 35
        if (hasTalent("armor_4")) itemArmor += 4f
        if (hasTalent("armor_6")) itemArmor += 6f
        if (hasTalent("armor_20")) itemArmor += 20f
        if (hasTalent("as_18")) itemAs += 18f
        if (hasTalent("as_20")) itemAs += 20f
        if (hasTalent("as_35")) itemAs += 35f
        if (hasTalent("as_180")) itemAs += 180f
        if (hasTalent("ms_20")) itemMs += 20f
        if (hasTalent("ms_25")) itemMs += 25f
        if (hasTalent("ms_30")) itemMs += 30f
        if (hasTalent("ls_12")) itemLs += 0.12f
        if (hasTalent("ls_15")) itemLs += 0.15f
        if (hasTalent("spell_ls_14")) itemSpellLs += 0.14f
        if (hasTalent("spell_ls_20")) itemSpellLs += 0.20f
        if (hasTalent("spell_ls_25")) itemSpellLs += 0.25f
        if (hasTalent("amp_12")) itemSpellAmp += 0.12f
        if (hasTalent("amp_14")) itemSpellAmp += 0.14f
        if (hasTalent("cdr_12")) itemCdr = 1f - (1f - itemCdr) * 0.88f
        if (hasTalent("cdr_15")) itemCdr = 1f - (1f - itemCdr) * 0.85f
        if (hasTalent("eva_15")) itemEva = 1f - (1f - itemEva) * 0.85f
        if (hasTalent("eva_25")) itemEva = 1f - (1f - itemEva) * 0.75f
        if (hasTalent("mr_10")) itemMr = 1f - (1f - itemMr) * 0.90f
        if (hasTalent("crit_25") && bestCritMult < 2.0f) {
            bestCritChance = maxOf(bestCritChance, 0.25f)
            bestCritMult = maxOf(bestCritMult, 1.85f)
        }

        // Status buffs
        if (hasStatus(StatusType.DOUBLE_DAMAGE)) itemDmg += (def.baseDamageMax + primaryBonus(str, agi, intel)).toInt()
        if (hasStatus(StatusType.HASTE)) itemMs += 180f
        if (hasStatus(StatusType.PHASE)) itemMs += 65f
        if (hasStatus(StatusType.MAGIC_IMMUNE)) itemMr = maxOf(itemMr, 0.80f)
        if (hasStatus(StatusType.SHIELD)) itemArmor += 16f
        if (hasStatus(StatusType.SATANIC)) itemLs += 1.75f

        cachedStr = str
        cachedAgi = agi
        cachedInt = intel

        val oldMaxHp = maxHp.coerceAtLeast(1f)
        val oldMaxMana = maxMana.coerceAtLeast(1f)
        val hpRatio = (hp / oldMaxHp).coerceIn(0f, 1f)
        val manaRatio = (mana / oldMaxMana).coerceIn(0f, 1f)

        maxHp = 200f + 22f * str + itemHp
        maxMana = 75f + 12f * intel + itemMana
        if (alive) {
            hp = (hpRatio * maxHp).coerceIn(1f, maxHp)
            mana = (manaRatio * maxMana).coerceIn(0f, maxMana)
        }

        val baseDmg = (def.baseDamageMin + def.baseDamageMax) * 0.5f + primaryBonus(str, agi, intel)
        cachedDamage = baseDmg + itemDmg
        armor = def.baseArmor + agi / 6f + itemArmor - armorCorruption
        magicResist = itemMr
        cachedAttackSpeed = (100f + agi + itemAs).coerceIn(40f, 650f)

        var range = def.attackRange
        if (def.id == "sniper") {
            range += 120f
            if (hasTalent("range_140")) range += 140f
        }
        cachedAttackRange = range

        var ms = def.baseMoveSpeed + itemMs
        if (hasStatus(StatusType.SLOW)) ms *= 0.68f
        if (hasStatus(StatusType.HASTE)) ms = maxOf(ms, 520f)
        cachedMoveSpeed = ms.coerceIn(120f, 550f)
        moveSpeed = cachedMoveSpeed

        var hpReg = 1.5f + 0.10f * str + itemHpReg
        if (hasItem("heart")) hpReg += maxHp * 0.016f
        cachedHpRegen = hpReg

        var mpReg = 1.2f + 0.05f * intel + itemManaReg
        if (def.id == "cm") mpReg *= 1.35f
        cachedManaRegen = mpReg

        cachedSpellAmp = itemSpellAmp + intel * 0.001f
        cachedCdr = itemCdr.coerceIn(0f, 0.55f)
        cachedLifesteal = itemLs
        cachedSpellLifesteal = itemSpellLs
        cachedEvasion = itemEva.coerceIn(0f, 0.75f)
        cachedCritChance = bestCritChance
        cachedCritMult = bestCritMult
        cachedCleave = cleave
        cachedRadianceDps = radDps

        // Update NetWorth estimate
        var invVal = 0
        for (s in inventory) invVal += ItemCatalog.get(s.itemId)?.cost ?: 0
        for (s in stash) invVal += ItemCatalog.get(s.itemId)?.cost ?: 0
        netWorth = gold + invVal + (if (hasScepter) 4200 else 0) + (if (hasShard) 1400 else 0)
    }

    private fun primaryBonus(str: Float, agi: Float, intel: Float): Float = when (def.attribute) {
        PrimaryAttribute.STRENGTH -> str
        PrimaryAttribute.AGILITY -> agi
        PrimaryAttribute.INTELLIGENCE -> intel
        PrimaryAttribute.UNIVERSAL -> (str + agi + intel) * 0.72f
    }

    fun buybackCost(): Int = 200 + netWorth / 13

    fun addXp(amount: Int): Boolean {
        val oldLvl = level
        xp += amount
        level = DotaMath.levelForXp(xp)
        val gained = level - oldLvl
        if (gained > 0) {
            skillPoints += gained
            recalcStats()
            return true
        }
        return false
    }

    fun canLevelAbility(idx: Int): Boolean {
        if (skillPoints <= 0 || idx !in 0..3) return false
        val ab = def.abilities[idx]
        val cur = abilityLevels[idx]
        if (cur >= ab.maxLevel) return false
        if (ab.isUltimate) {
            val reqLvl = when (cur) {
                0 -> 6
                1 -> 12
                else -> 18
            }
            return level >= reqLvl
        }
        val maxAllowed = ((level + 1) / 2).coerceAtMost(4)
        return cur < maxAllowed
    }

    fun availableTalentTier(): Int? {
        for (i in def.talents.indices) {
            if (level >= def.talents[i].level && talentsChosen[i] == -1) return i
        }
        return null
    }
}

class CreepEntity(
    id: String,
    team: Team,
    pos: Vec2,
    val type: CreepType,
    val lane: Int,
    var waypointIndex: Int,
    var attackDamage: Float,
    var attackRange: Float,
    var bountyGold: Int,
    var bountyXp: Int
) : CombatUnit(id, team, pos, if (type == CreepType.SIEGE) 22f else 17f) {
    init {
        moveSpeed = when (type) {
            CreepType.SIEGE -> 240f
            CreepType.SUPER_MELEE, CreepType.MEGA_MELEE -> 280f
            else -> 265f
        }
    }
}

class NeutralCreepEntity(
    id: String,
    pos: Vec2,
    val homePos: Vec2,
    val campId: String,
    val tier: Int,
    var attackDamage: Float,
    var bountyGold: Int,
    var bountyXp: Int
) : CombatUnit(id, Team.NEUTRAL, pos, 20f + tier * 2f) {
    init {
        moveSpeed = 240f
    }
}

class TowerEntity(
    val blueprint: TowerBlueprint
) : CombatUnit(blueprint.id, blueprint.team, blueprint.pos.copyVec(), 44f) {
    var attackDamage: Float = when (blueprint.tier) {
        1 -> 95f
        2 -> 125f
        3 -> 155f
        else -> 185f
    }
    val attackRange: Float = 620f
    var fortifyTimer: Float = 0f

    init {
        maxHp = when (blueprint.tier) {
            1 -> 1800f
            2 -> 2500f
            3 -> 2800f
            else -> 2600f
        }
        hp = maxHp
        armor = when (blueprint.tier) {
            1 -> 12f
            2 -> 16f
            3 -> 18f
            else -> 22f
        }
        moveSpeed = 0f
    }
}

class BarracksEntity(
    id: String,
    team: Team,
    pos: Vec2,
    val lane: Int,
    val isMelee: Boolean
) : CombatUnit(id, team, pos, 36f) {
    init {
        maxHp = if (isMelee) 2200f else 1500f
        hp = maxHp
        armor = if (isMelee) 15f else 10f
        moveSpeed = 0f
    }
}

class AncientEntity(
    team: Team,
    pos: Vec2
) : CombatUnit(if (team == Team.RADIANT) "rad_ancient" else "dire_ancient", team, pos, 62f) {
    init {
        maxHp = 4500f
        hp = maxHp
        armor = 22f
        moveSpeed = 0f
    }
}

class RoshanEntity(pos: Vec2) : CombatUnit("roshan", Team.NEUTRAL, pos, 46f) {
    var deathsCount: Int = 0
    var slamCooldown: Float = 0f
    var respawnTimer: Float = 0f
    var attackDamage: Float = 85f

    init {
        maxHp = 6000f
        hp = maxHp
        armor = 20f
        moveSpeed = 240f
    }

    fun respawnScaled() {
        deathsCount++
        maxHp = 6000f + deathsCount * 2200f
        hp = maxHp
        armor = 20f + deathsCount * 4f
        attackDamage = 85f + deathsCount * 35f
        alive = true
        pos.set(MapLayout.roshanPitPos.x, MapLayout.roshanPitPos.y)
    }
}

class TormentorEntity(
    id: String,
    val homeTeam: Team,
    pos: Vec2
) : CombatUnit(id, Team.NEUTRAL, pos, 38f) {
    var respawnTimer: Float = 0f
    init {
        maxHp = 2500f
        hp = maxHp
        armor = 20f
        magicResist = 0.55f
        alive = false // Spawns after 10 min (or 6 min turbo)
        respawnTimer = 480f
    }
}

class WardEntity(
    val id: String,
    val team: Team,
    val pos: Vec2,
    val isSentry: Boolean,
    var remaining: Float = if (isSentry) 240f else 240f
)

class RuneEntity(
    val id: String,
    val type: RuneType,
    val pos: Vec2,
    var alive: Boolean = true
)

class DroppedItemEntity(
    val id: String,
    val itemId: String, // "divine_rapier", "aegis", "cheese"
    val pos: Vec2,
    var ttl: Float = 180f
)

class CourierEntity(
    val heroId: String,
    val team: Team,
    val homePos: Vec2,
    val pos: Vec2 = homePos.copyVec()
) {
    var delivering: Boolean = false
    var returning: Boolean = false
    val cargo: MutableList<ItemSlot> = mutableListOf()
    var hp: Float = 150f
    var alive: Boolean = true
    var respawnTimer: Float = 0f
    var speed: Float = 450f
}

class Projectile(
    val id: String,
    val team: Team,
    val sourceId: String,
    val pos: Vec2,
    var targetId: String? = null,
    var targetPos: Vec2? = null,
    val velocity: Vec2 = Vec2(),
    val speed: Float,
    val damage: Float,
    val damageType: DamageType,
    val color: Int,
    val radius: Float = 8f,
    var maxRange: Float = 1200f,
    var traveled: Float = 0f,
    val isSkillshot: Boolean = false,
    val kind: String = "basic", // "basic", "hook", "meteor", "blast", "dagger", "tower", "assassinate", "lance"
    val piercesBkb: Boolean = false,
    val stunDur: Float = 0f,
    val slowDur: Float = 0f,
    val hitIds: MutableSet<String> = HashSet(),
    val trail: MutableList<Vec2> = mutableListOf(),
    var alive: Boolean = true
)

class AoEZone(
    val id: String,
    val team: Team,
    val sourceId: String,
    val pos: Vec2,
    val radius: Float,
    var remaining: Float,
    val kind: String, // "shrapnel", "chrono", "healing_ward", "sunstrike", "emp", "fissure", "time_rift", "aether_remnant", "meteor_burn"
    val dps: Float = 0f,
    val burstDamage: Float = 0f,
    val damageType: DamageType = DamageType.MAGICAL,
    val color: Int,
    var tickAccum: Float = 0f,
    var triggered: Boolean = false,
    val endPos: Vec2? = null // Used for Fissure wall line
)

class ParticleEffect(
    val pos: Vec2,
    var vel: Vec2 = Vec2(),
    var color: Int,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    val ring: Boolean = false,
    var height: Float = 26f,
    var zVel: Float = 0f
)

class FloatingText(
    val pos: Vec2,
    val text: String,
    val color: Int,
    var life: Float = 1.1f,
    val scale: Float = 1.0f
)
