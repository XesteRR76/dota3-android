package com.dota3.moba.data

import com.dota3.moba.model.ItemCategory
import com.dota3.moba.model.ItemDef
import com.dota3.moba.model.NeutralItemDef

object ItemCatalog {

    val allItems: List<ItemDef> = listOf(
        // === CONSUMABLES ===
        ItemDef(
            id = "tango", name = "Tango", category = ItemCategory.CONSUMABLE, cost = 90,
            shortCode = "TNG", color = 0xFF2ECC71.toInt(),
            description = "Consume (or eat nearby tree) to restore 180 HP over 12s.",
            isConsumable = true, maxStacks = 3, isActive = true, cooldown = 1f
        ),
        ItemDef(
            id = "salve", name = "Healing Salve", category = ItemCategory.CONSUMABLE, cost = 100,
            shortCode = "SLV", color = 0xFF27AE60.toInt(),
            description = "Restores 360 HP over 6s (60 HP/s).",
            isConsumable = true, maxStacks = 2, isActive = true, cooldown = 1f
        ),
        ItemDef(
            id = "clarity", name = "Clarity", category = ItemCategory.CONSUMABLE, cost = 50,
            shortCode = "CLR", color = 0xFF3498DB.toInt(),
            description = "Restores 160 Mana over 10s.",
            isConsumable = true, maxStacks = 3, isActive = true, cooldown = 1f
        ),
        ItemDef(
            id = "obs_ward", name = "Observer Ward", category = ItemCategory.CONSUMABLE, cost = 0,
            shortCode = "OBS", color = 0xFFF1C40F.toInt(),
            description = "Places an invisible ward granting 850 vision for 4 min.",
            isConsumable = true, maxStacks = 2, isActive = true, cooldown = 1f
        ),
        ItemDef(
            id = "sentry_ward", name = "Sentry Ward", category = ItemCategory.CONSUMABLE, cost = 50,
            shortCode = "SNT", color = 0xFF38BDF8.toInt(),
            description = "Places a ward granting True Sight (650 radius) to reveal invisibility.",
            isConsumable = true, maxStacks = 2, isActive = true, cooldown = 1f
        ),
        ItemDef(
            id = "dust", name = "Dust of Appearance", category = ItemCategory.CONSUMABLE, cost = 80,
            shortCode = "DST", color = 0xFFA855F7.toInt(),
            description = "Reveals & slows all invisible enemies within 700 radius for 10s.",
            isConsumable = true, maxStacks = 2, isActive = true, cooldown = 10f
        ),
        ItemDef(
            id = "smoke", name = "Smoke of Deceit", category = ItemCategory.CONSUMABLE, cost = 50,
            shortCode = "SMK", color = 0xFF9B59B6.toInt(),
            description = "Turns nearby allied heroes invisible & +15% Move Speed until near enemies!",
            isConsumable = true, maxStacks = 1, isActive = true, cooldown = 1f
        ),

        // === BOOTS & COMPONENTS ===
        ItemDef(
            id = "quelling_blade", name = "Quelling Blade", category = ItemCategory.BOOTS, cost = 100,
            shortCode = "QB", color = 0xFF7F8C8D.toInt(),
            description = "+12 Damage vs Creeps. Active: Chop down a tree!",
            isActive = true, cooldown = 4f, bonusDamage = 8
        ),
        ItemDef(
            id = "blades_of_attack", name = "Blades of Attack", category = ItemCategory.BOOTS, cost = 450,
            shortCode = "BOA", color = 0xFFBDC3C7.toInt(),
            description = "+9 Attack Damage.",
            bonusDamage = 9
        ),
        ItemDef(
            id = "chainmail", name = "Chainmail", category = ItemCategory.BOOTS, cost = 550,
            shortCode = "CHM", color = 0xFF95A5A6.toInt(),
            description = "+4 Armor.",
            bonusArmor = 4f
        ),
        ItemDef(
            id = "ring_of_health", name = "Ring of Health", category = ItemCategory.BOOTS, cost = 700,
            shortCode = "RoH", color = 0xFF2ECC71.toInt(),
            description = "+5.5 HP Regen.",
            bonusHpRegen = 5.5f
        ),
        ItemDef(
            id = "void_stone", name = "Void Stone", category = ItemCategory.BOOTS, cost = 700,
            shortCode = "VD", color = 0xFF38BDF8.toInt(),
            description = "+2.5 Mana Regen.",
            bonusManaRegen = 2.5f
        ),
        ItemDef(
            id = "ogre_axe", name = "Ogre Axe", category = ItemCategory.BOOTS, cost = 1000,
            shortCode = "OAX", color = 0xFFE74C3C.toInt(),
            description = "+10 Strength.",
            bonusStr = 10f
        ),
        ItemDef(
            id = "blade_of_alacrity", name = "Blade of Alacrity", category = ItemCategory.BOOTS, cost = 1000,
            shortCode = "BAL", color = 0xFF2ECC71.toInt(),
            description = "+10 Agility.",
            bonusAgi = 10f
        ),
        ItemDef(
            id = "staff_of_wizardry", name = "Staff of Wizardry", category = ItemCategory.BOOTS, cost = 1000,
            shortCode = "STF", color = 0xFF3498DB.toInt(),
            description = "+10 Intelligence.",
            bonusInt = 10f
        ),
        ItemDef(
            id = "mithril_hammer", name = "Mithril Hammer", category = ItemCategory.BOOTS, cost = 1600,
            shortCode = "MTH", color = 0xFFE67E22.toInt(),
            description = "+24 Attack Damage.",
            bonusDamage = 24
        ),
        ItemDef(
            id = "magic_wand", name = "Magic Wand", category = ItemCategory.BOOTS, cost = 450,
            shortCode = "WND", color = 0xFF1ABC9C.toInt(),
            description = "+3 All Stats. Gains charges from nearby enemy casts. Active: +18 HP & Mana per charge!",
            isActive = true, cooldown = 13f, bonusStr = 3f, bonusAgi = 3f, bonusInt = 3f
        ),
        ItemDef(
            id = "boots", name = "Boots of Speed", category = ItemCategory.BOOTS, cost = 500,
            shortCode = "BTS", color = 0xFFD35400.toInt(),
            description = "+45 Movement Speed.",
            bonusMoveSpeed = 45f
        ),
        ItemDef(
            id = "power_treads", name = "Power Treads", category = ItemCategory.BOOTS, cost = 1400,
            components = listOf("boots"),
            shortCode = "PT", color = 0xFFE74C3C.toInt(),
            description = "+50 Move Speed, +25 Attack Speed, +10 Primary/All Stats (Active: Switch Stat!).",
            isActive = true, cooldown = 0.5f, bonusMoveSpeed = 50f, bonusAttackSpeed = 25f,
            bonusStr = 6f, bonusAgi = 6f, bonusInt = 6f
        ),
        ItemDef(
            id = "phase_boots", name = "Phase Boots", category = ItemCategory.BOOTS, cost = 1500,
            components = listOf("boots", "blades_of_attack", "chainmail"),
            shortCode = "PHS", color = 0xFF9B59B6.toInt(),
            description = "+50 Move Speed, +18 Damage, +4 Armor. Active: +22% Move Speed & Phase for 3s!",
            isActive = true, cooldown = 8f, bonusMoveSpeed = 50f, bonusDamage = 18, bonusArmor = 4f
        ),
        ItemDef(
            id = "arcane_boots", name = "Arcane Boots", category = ItemCategory.BOOTS, cost = 1300,
            components = listOf("boots"),
            shortCode = "ARC", color = 0xFF3498DB.toInt(),
            description = "+45 Move Speed, +2 Mana Regen. Active: Restores 175 Mana to nearby allies!",
            isActive = true, cooldown = 40f, bonusMoveSpeed = 45f, bonusManaRegen = 2.0f, bonusMana = 120f
        ),
        ItemDef(
            id = "boots_of_travel", name = "Boots of Travel", category = ItemCategory.BOOTS, cost = 2500,
            components = listOf("boots"),
            shortCode = "BoT", color = 0xFFF1C40F.toInt(),
            description = "+90 Move Speed! Active: Teleport to any allied unit or tower (25s CD).",
            isActive = true, cooldown = 25f, bonusMoveSpeed = 90f
        ),

        // === CORE & UTILITY ===
        ItemDef(
            id = "morbid_mask", name = "Morbid Mask", category = ItemCategory.CORE, cost = 900,
            shortCode = "MM", color = 0xFF8E44AD.toInt(),
            description = "+16% Lifesteal.",
            bonusLifesteal = 0.16f
        ),
        ItemDef(
            id = "vanguard", name = "Vanguard", category = ItemCategory.CORE, cost = 1700,
            components = listOf("ring_of_health"),
            shortCode = "VG", color = 0xFFC0392B.toInt(),
            description = "+275 HP, +7 HP Regen, +4 Armor (Blocks incoming attack damage).",
            bonusHp = 275f, bonusHpRegen = 7f, bonusArmor = 4f
        ),
        ItemDef(
            id = "mask_of_madness", name = "Mask of Madness", category = ItemCategory.CORE, cost = 1775,
            components = listOf("morbid_mask"),
            shortCode = "MoM", color = 0xFFE74C3C.toInt(),
            description = "+15 Damage, +22% Lifesteal. Active: +110 Attack Speed & +30 Move Speed (-8 Armor, self-silence) for 6s!",
            isActive = true, cooldown = 16f, bonusDamage = 15, bonusLifesteal = 0.22f, bonusAttackSpeed = 10f
        ),
        ItemDef(
            id = "blink_dagger", name = "Blink Dagger", category = ItemCategory.CORE, cost = 2250,
            shortCode = "BLK", color = 0xFF2980B9.toInt(),
            description = "Active: Instant teleport up to 850 units! (3s CD if damaged by enemy hero).",
            isActive = true, cooldown = 13f
        ),
        ItemDef(
            id = "force_staff", name = "Force Staff", category = ItemCategory.CORE, cost = 2200,
            components = listOf("staff_of_wizardry"),
            shortCode = "FRC", color = 0xFF2ECC71.toInt(),
            description = "+12 INT, +200 HP. Active: Pushes hero forward 550 units!",
            isActive = true, cooldown = 16f, bonusInt = 12f, bonusHp = 200f
        ),
        ItemDef(
            id = "euls_scepter", name = "Eul's Scepter", category = ItemCategory.CORE, cost = 2600,
            components = listOf("staff_of_wizardry", "void_stone"),
            shortCode = "EUL", color = 0xFF5DADE2.toInt(),
            description = "+12 INT, +3.5 Mana Regen, +25 Move Speed. Active: Cyclone (2.5s invulnerable/purge)!",
            isActive = true, cooldown = 20f, manaCost = 100, bonusInt = 12f, bonusManaRegen = 3.5f, bonusMoveSpeed = 25f
        ),
        ItemDef(
            id = "shadow_blade", name = "Shadow Blade", category = ItemCategory.CORE, cost = 3000,
            shortCode = "SB", color = 0xFF6C3483.toInt(),
            description = "+25 Damage, +35 Attack Speed. Active: Turn Invisible +25% Speed; attack breaks for +175 bonus damage!",
            isActive = true, cooldown = 22f, manaCost = 75, bonusDamage = 25, bonusAttackSpeed = 35f
        ),
        ItemDef(
            id = "crystalys", name = "Crystalys", category = ItemCategory.CORE, cost = 2000,
            components = listOf("blades_of_attack"),
            shortCode = "CRY", color = 0xFFE67E22.toInt(),
            description = "+32 Damage, 25% chance to Crit for 165% damage.",
            bonusDamage = 32, critChance = 0.25f, critMult = 1.65f
        ),
        ItemDef(
            id = "maelstrom", name = "Maelstrom", category = ItemCategory.CORE, cost = 2700,
            components = listOf("mithril_hammer"),
            shortCode = "ML", color = 0xFF38BDF8.toInt(),
            description = "+25 Damage, +25 Attack Speed. 30% chance on attack to unleash Chain Lightning (135 magic dmg)!",
            bonusDamage = 25, bonusAttackSpeed = 25f
        ),
        ItemDef(
            id = "desolator", name = "Desolator", category = ItemCategory.CORE, cost = 3500,
            components = listOf("mithril_hammer", "mithril_hammer"),
            shortCode = "DES", color = 0xFFB03A2E.toInt(),
            description = "+55 Damage (stacks up to +25 more on kills!). Attacks reduce enemy Armor by 6!",
            bonusDamage = 55
        ),
        ItemDef(
            id = "kaya_sange", name = "Kaya & Sange", category = ItemCategory.CORE, cost = 4000,
            components = listOf("ogre_axe", "staff_of_wizardry"),
            shortCode = "K&S", color = 0xFFAF7AC5.toInt(),
            description = "+18 STR, +18 INT, +18% Spell Amp, +25% Status Resistance & Mana Regen Amp.",
            bonusStr = 18f, bonusInt = 18f, bonusSpellAmp = 0.18f, bonusManaRegen = 2.5f
        ),
        ItemDef(
            id = "orchid", name = "Orchid Malevolence", category = ItemCategory.CORE, cost = 3275,
            components = listOf("staff_of_wizardry", "void_stone"),
            shortCode = "ORC", color = 0xFF8E44AD.toInt(),
            description = "+12 INT, +35 Attack Speed, +20 Damage. Active: Silences enemy for 4s and amplifies damage by 30%!",
            isActive = true, cooldown = 18f, manaCost = 100, bonusInt = 12f, bonusAttackSpeed = 35f, bonusDamage = 20, bonusManaRegen = 3.0f
        ),

        // === LUXURY & LATE ===
        ItemDef(
            id = "bkb", name = "Black King Bar", category = ItemCategory.LUXURY, cost = 4050,
            components = listOf("ogre_axe", "mithril_hammer"),
            shortCode = "BKB", color = 0xFFF1C40F.toInt(),
            description = "+12 STR, +24 Damage. Active: Avatar — 6s Spell Immunity & +60% Magic Resist!",
            isActive = true, cooldown = 55f, bonusStr = 12f, bonusDamage = 24
        ),
        ItemDef(
            id = "aghanims_scepter", name = "Aghanim's Scepter", category = ItemCategory.LUXURY, cost = 4200,
            components = listOf("ogre_axe", "blade_of_alacrity", "staff_of_wizardry"),
            shortCode = "AGH", color = 0xFF2E86C1.toInt(),
            description = "+12 All Attributes, +200 HP, +175 Mana. UPGRADES YOUR HERO ULTIMATE!",
            bonusStr = 12f, bonusAgi = 12f, bonusInt = 12f, bonusHp = 200f, bonusMana = 175f
        ),
        ItemDef(
            id = "aghanims_shard", name = "Aghanim's Shard", category = ItemCategory.LUXURY, cost = 1400,
            shortCode = "SHD", color = 0xFF5DADE2.toInt(),
            description = "Permanently consumes on buy to UPGRADE your Shard Ability!",
            isConsumable = true
        ),
        ItemDef(
            id = "battle_fury", name = "Battle Fury", category = ItemCategory.LUXURY, cost = 4100,
            components = listOf("quelling_blade", "ring_of_health", "void_stone"),
            shortCode = "BF", color = 0xFFE67E22.toInt(),
            description = "+60 Damage, +7 HP Regen, +3 Mana Regen, 50% Melee Cleave!",
            bonusDamage = 60, bonusHpRegen = 7f, bonusManaRegen = 3f, cleavePct = 0.50f
        ),
        ItemDef(
            id = "daedalus", name = "Daedalus", category = ItemCategory.LUXURY, cost = 5150,
            components = listOf("crystalys", "mithril_hammer"),
            shortCode = "DAE", color = 0xFFE74C3C.toInt(),
            description = "+88 Damage, 30% chance to Critical Strike for 225% damage!",
            bonusDamage = 88, critChance = 0.30f, critMult = 2.25f
        ),
        ItemDef(
            id = "butterfly", name = "Butterfly", category = ItemCategory.LUXURY, cost = 4975,
            components = listOf("blade_of_alacrity"),
            shortCode = "BFY", color = 0xFF2ECC71.toInt(),
            description = "+35 Agility, +35% Evasion, +35 Attack Speed, +25 Damage.",
            bonusAgi = 35f, bonusEvasion = 0.35f, bonusAttackSpeed = 35f, bonusDamage = 25
        ),
        ItemDef(
            id = "heart", name = "Heart of Tarrasque", category = ItemCategory.LUXURY, cost = 5100,
            components = listOf("ogre_axe", "ring_of_health"),
            shortCode = "HRT", color = 0xFFC0392B.toInt(),
            description = "+40 Strength, +300 HP, +1.8% Max HP Regen per second!",
            bonusStr = 40f, bonusHp = 300f, bonusHpRegen = 24f
        ),
        ItemDef(
            id = "satanic", name = "Satanic", category = ItemCategory.LUXURY, cost = 5050,
            components = listOf("morbid_mask", "ogre_axe"),
            shortCode = "SAT", color = 0xFF922B21.toInt(),
            description = "+25 STR, +45 Damage, +25% Lifesteal. Active: Unholy Rage (+175% Lifesteal & Dispels)!",
            isActive = true, cooldown = 30f, bonusStr = 25f, bonusDamage = 45, bonusLifesteal = 0.25f
        ),
        ItemDef(
            id = "skadi", name = "Eye of Skadi", category = ItemCategory.LUXURY, cost = 5300,
            components = listOf("ogre_axe", "blade_of_alacrity", "staff_of_wizardry"),
            shortCode = "SKD", color = 0xFF3498DB.toInt(),
            description = "+24 All Attributes, +250 HP, +250 Mana. Attacks slow enemies & cut healing by 40%!",
            bonusStr = 24f, bonusAgi = 24f, bonusInt = 24f, bonusHp = 250f, bonusMana = 250f
        ),
        ItemDef(
            id = "assault_cuirass", name = "Assault Cuirass", category = ItemCategory.LUXURY, cost = 5125,
            components = listOf("chainmail"),
            shortCode = "AC", color = 0xFFBDC3C7.toInt(),
            description = "+15 Armor, +55 Attack Speed (Aura: +5 Armor to allies, -5 Armor to enemies!).",
            bonusArmor = 15f, bonusAttackSpeed = 55f
        ),
        ItemDef(
            id = "shivas_guard", name = "Shiva's Guard", category = ItemCategory.LUXURY, cost = 4850,
            components = listOf("chainmail", "staff_of_wizardry"),
            shortCode = "SHV", color = 0xFF2980B9.toInt(),
            description = "+16 Armor, +25 INT, +15% Spell Amp. Active: Arctic Blast AoE damage + 40% slow!",
            isActive = true, cooldown = 26f, manaCost = 100, bonusArmor = 16f, bonusInt = 25f, bonusSpellAmp = 0.15f
        ),
        ItemDef(
            id = "scythe_of_vyse", name = "Scythe of Vyse", category = ItemCategory.LUXURY, cost = 5200,
            components = listOf("staff_of_wizardry", "void_stone"),
            shortCode = "HEX", color = 0xFF8E44AD.toInt(),
            description = "+10 STR, +10 AGI, +35 INT, +8 Mana Regen. Active: Hexes enemy into a helpless critter for 2.8s!",
            isActive = true, cooldown = 20f, manaCost = 150, bonusStr = 10f, bonusAgi = 10f, bonusInt = 35f, bonusManaRegen = 8f
        ),
        ItemDef(
            id = "radiance", name = "Radiance", category = ItemCategory.LUXURY, cost = 4700,
            components = listOf("mithril_hammer"),
            shortCode = "RAD", color = 0xFFF39C12.toInt(),
            description = "+55 Damage, +20% Evasion. Burn Aura deals 65 Magic DPS to all nearby enemies!",
            bonusDamage = 55, bonusEvasion = 0.20f, radianceDps = 65f
        ),
        ItemDef(
            id = "refresher", name = "Refresher Orb", category = ItemCategory.SECRET, cost = 5000,
            components = listOf("ring_of_health", "void_stone"),
            shortCode = "RFR", color = 0xFF2ECC71.toInt(),
            description = "+12 HP Regen, +10 Mana Regen. Active: RESETS ALL ABILITY & ITEM COOLDOWNS!",
            isActive = true, cooldown = 120f, manaCost = 250, bonusHpRegen = 12f, bonusManaRegen = 10f
        ),
        ItemDef(
            id = "bloodstone", name = "Bloodstone", category = ItemCategory.SECRET, cost = 4400,
            components = listOf("void_stone"),
            shortCode = "BLS", color = 0xFFE74C3C.toInt(),
            description = "+500 HP, +400 Mana, +28% Spell Lifesteal, +15% Spell Amp. Active: Bloodpact doubles spell lifesteal!",
            isActive = true, cooldown = 32f, bonusHp = 500f, bonusMana = 400f, bonusSpellLifesteal = 0.28f, bonusSpellAmp = 0.15f
        ),
        ItemDef(
            id = "divine_rapier", name = "Divine Rapier", category = ItemCategory.SECRET, cost = 5600,
            components = listOf("mithril_hammer"),
            shortCode = "RPR", color = 0xFFFFD700.toInt(),
            description = "+350 Attack Damage & +25% Spell Amp! WARNING: DROPS ON DEATH for anyone to claim!",
            bonusDamage = 350, bonusSpellAmp = 0.25f, dropsOnDeath = true
        )
    )

    val neutralItems: List<NeutralItemDef> = listOf(
        NeutralItemDef("trusty_shovel", "Trusty Shovel", 1, "+85 HP, +1.5 Mana Regen", bonusHp = 85f, bonusManaRegen = 1.5f),
        NeutralItemDef("arcane_ring", "Arcane Ring", 1, "+6 All Stats, +2 Armor", bonusAllStats = 6f, bonusArmor = 2f),
        NeutralItemDef("broom_handle", "Broom Handle", 1, "+14 Damage, +4 Armor", bonusDamage = 14, bonusArmor = 4f),
        NeutralItemDef("vambrace", "Vambrace", 2, "+10 All Stats, +15 Attack Speed", bonusAllStats = 10f, bonusAttackSpeed = 15f),
        NeutralItemDef("dragon_scale", "Dragon Scale", 2, "+6 Armor, +150 HP", bonusArmor = 6f, bonusHp = 150f),
        NeutralItemDef("grove_bow", "Grove Bow", 2, "+25 Attack Speed, +12% Spell Amp", bonusAttackSpeed = 25f, bonusSpellAmp = 0.12f),
        NeutralItemDef("paladin_sword", "Paladin Sword", 3, "+20 Damage, +16% Lifesteal", bonusDamage = 20, bonusLifesteal = 0.16f),
        NeutralItemDef("mind_breaker", "Mind Breaker", 3, "+30 Attack Speed, +25 Damage", bonusAttackSpeed = 30f, bonusDamage = 25),
        NeutralItemDef("spell_prism", "Spell Prism", 3, "+15 All Stats, +15% CDR, +4 Mana Regen", bonusAllStats = 15f, bonusCdr = 0.15f, bonusManaRegen = 4f),
        NeutralItemDef("deso_2", "Stygian Desolator", 4, "+75 Damage, +8 Armor", bonusDamage = 75, bonusArmor = 8f),
        NeutralItemDef("apex", "Apex", 4, "+35 All Stats, +30 Move Speed", bonusAllStats = 35f, bonusMoveSpeed = 30f),
        NeutralItemDef("mirror_shield", "Mirror Shield", 4, "+20 All Stats, +10 Armor, +250 HP", bonusAllStats = 20f, bonusArmor = 10f, bonusHp = 250f)
    )

    private val byId = allItems.associateBy { it.id }
    private val neutralById = neutralItems.associateBy { it.id }

    fun get(id: String): ItemDef? = byId[id]
    fun getNeutral(id: String): NeutralItemDef? = neutralById[id]

    fun getBuiltInto(itemId: String): List<ItemDef> =
        allItems.filter { itemId in it.components }

    data class RecipeNode(
        val item: ItemDef,
        val owned: Boolean,
        val children: List<RecipeNode>
    )

    fun buildRecipeTree(targetItemId: String, ownedItemIds: List<String>): RecipeNode? {
        val remaining = ownedItemIds.toMutableList()
        fun build(id: String): RecipeNode? {
            val def = get(id) ?: return null
            val isOwned = remaining.remove(id)
            val kids = if (isOwned) emptyList() else def.components.mapNotNull { build(it) }
            return RecipeNode(def, isOwned, kids)
        }
        return build(targetItemId)
    }

    /**
     * Returns ordered list of missing components (leaves first, then final item) for Craft Tree Quick-Buy.
     */
    fun getQuickBuyQueue(targetItemId: String, ownedItemIds: List<String>): List<String> {
        val target = get(targetItemId) ?: return emptyList()
        val remainingOwned = ownedItemIds.toMutableList()
        val queue = mutableListOf<String>()

        fun collect(itemId: String) {
            if (remainingOwned.remove(itemId)) return
            val def = get(itemId) ?: return
            if (def.components.isEmpty()) {
                queue.add(itemId)
            } else {
                for (comp in def.components) {
                    collect(comp)
                }
                queue.add(itemId)
            }
        }
        collect(target.id)
        return queue.ifEmpty { listOf(target.id) }
    }
}
