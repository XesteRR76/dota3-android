package com.dota3.moba.data

import com.dota3.moba.model.*

object HeroCatalog {

    val allHeroes: List<HeroDef> = listOf(
        // 1. JUGGERNAUT
        HeroDef(
            id = "juggernaut",
            name = "Juggernaut",
            title = "Yurnero, Isle's Last Blade",
            attribute = PrimaryAttribute.AGILITY,
            roles = listOf(HeroRole.CARRY, HeroRole.ASSASSIN),
            primaryColor = 0xFFE67E22.toInt(),
            accentColor = 0xFFFDEBD0.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.4f,
            baseStr = 20f, baseAgi = 34f, baseInt = 14f,
            strGain = 2.2f, agiGain = 2.8f, intGain = 1.4f,
            baseDamageMin = 24, baseDamageMax = 28,
            baseArmor = 3.0f,
            baseMoveSpeed = 305f,
            innateName = "Duelist's Edge",
            innateDesc = "Deals +15% bonus damage to enemies facing him or below 40% HP.",
            lore = "Masked exile of the Isle of Masks, whose blade dances faster than the eye can follow.",
            facets = listOf(
                FacetDef("jugg_bladestorm", "Bladestorm", "Blade Fury can critically strike and grants +15 Move Speed.", bonusMoveSpeed = 15f, bonusCritChance = 0.05f),
                FacetDef("jugg_bladeform", "Bladeform", "Builds +8% Lifesteal & +12 Attack Damage during combat.", bonusDamage = 12, bonusLifesteal = 0.08f)
            ),
            abilities = listOf(
                AbilityDef("jugg_q", "Q", "Blade Fury", "Spins rapidly, becoming Spell Immune and dealing AoE magic damage per second.",
                    TargetType.INSTANT, DamageType.MAGICAL, manaCost = intArrayOf(90, 100, 110, 120),
                    cooldown = floatArrayOf(18f, 16f, 14f, 12f), radius = 210f,
                    baseValues = floatArrayOf(95f, 130f, 165f, 200f), duration = floatArrayOf(4.0f, 4.2f, 4.5f, 5.0f),
                    shardUpgrade = "+100 Radius & +40 Move Speed while spinning."),
                AbilityDef("jugg_w", "W", "Healing Ward", "Summons a Healing Ward that follows you and heals nearby allies.",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(100, 110, 120, 130),
                    cooldown = floatArrayOf(35f, 32f, 29f, 26f), radius = 360f,
                    baseValues = floatArrayOf(3.0f, 4.5f, 6.0f, 7.5f), duration = floatArrayOf(8f, 9f, 10f, 12f)),
                AbilityDef("jugg_e", "E", "Blade Dance", "Passive: Grants a chance on each attack to deal critical damage.",
                    TargetType.PASSIVE, DamageType.PHYSICAL, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0f, 0f, 0f, 0f),
                    baseValues = floatArrayOf(1.6f, 1.8f, 2.0f, 2.2f)),
                AbilityDef("jugg_r", "R", "Omnislash", "Becomes invulnerable and rapidly slashes enemies around target area.",
                    TargetType.UNIT_OR_AUTO, DamageType.PHYSICAL, isUltimate = true,
                    manaCost = intArrayOf(160, 220, 280), cooldown = floatArrayOf(65f, 55f, 45f),
                    castRange = 360f, radius = 320f, baseValues = floatArrayOf(6f, 9f, 12f),
                    duration = floatArrayOf(1.8f, 2.4f, 3.0f), piercesBkb = true,
                    scepterUpgrade = "+3 Slashes, -10s cooldown & grants instant Swiftslash burst.")
            ),
            talents = listOf(
                TalentDef(10, "+150 HP", "+18 Attack Speed", "hp_150", "as_18"),
                TalentDef(15, "+25 Move Speed", "+12% Lifesteal", "ms_25", "ls_12"),
                TalentDef(20, "+35 Damage", "+20% Blade Fury DPS", "dmg_35", "spec_20"),
                TalentDef(25, "+1.0s Omnislash", "+50% Crit Damage", "ult_1", "crit_50")
            )
        ),

        // 2. PUDGE
        HeroDef(
            id = "pudge",
            name = "Pudge",
            title = "The Butcher of Fields",
            attribute = PrimaryAttribute.STRENGTH,
            roles = listOf(HeroRole.INITIATOR, HeroRole.OFFLANE),
            primaryColor = 0xFF8E44AD.toInt(),
            accentColor = 0xFF82E0AA.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.7f,
            baseStr = 25f, baseAgi = 14f, baseInt = 16f,
            strGain = 3.2f, agiGain = 1.4f, intGain = 1.8f,
            baseDamageMin = 38, baseDamageMax = 44,
            baseArmor = 1.5f,
            baseMoveSpeed = 285f,
            innateName = "Flesh Heap",
            innateDesc = "Gains permanent +1.5 STR for every nearby enemy hero death.",
            lore = "In the Fields of Endless Carnage, Pudge grows ever larger with every hook and feast.",
            facets = listOf(
                FacetDef("pudge_fresh_meat", "Fresh Meat", "Dismember grants +3 STR during channel and +120 HP.", bonusDamage = 10, bonusLifesteal = 0.06f),
                FacetDef("pudge_flayer", "Flayer's Hook", "Meat Hook travels faster and gains +15% Spell Amp.", bonusSpellAmp = 0.15f, bonusMoveSpeed = 10f)
            ),
            abilities = listOf(
                AbilityDef("pudge_q", "Q", "Meat Hook", "Launches a bloody hook in a line. Drags the first hero or creep hit back and deals Pure damage.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.PURE, manaCost = intArrayOf(110, 120, 130, 140),
                    cooldown = floatArrayOf(14f, 12f, 10f, 8.5f), castRange = 860f, radius = 58f,
                    baseValues = floatArrayOf(160f, 240f, 320f, 420f), piercesBkb = true),
                AbilityDef("pudge_w", "W", "Rot", "Toggle: Emits a toxic cloud slowing and damaging nearby enemies (and slightly damaging self).",
                    TargetType.TOGGLE, DamageType.MAGICAL, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f), radius = 210f,
                    baseValues = floatArrayOf(40f, 70f, 100f, 130f),
                    scepterUpgrade = "+100 Rot Radius, +75 DPS & reduces enemy healing by 25%."),
                AbilityDef("pudge_e", "E", "Meat Shield", "Covers Pudge in extra fat, granting a Damage Barrier and Magic Resistance.",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(45, 55, 65, 75),
                    cooldown = floatArrayOf(14f, 13f, 12f, 11f),
                    baseValues = floatArrayOf(150f, 240f, 330f, 440f), duration = floatArrayOf(5f, 5f, 5f, 5f)),
                AbilityDef("pudge_r", "R", "Dismember", "Chomps and stuns nearby enemy target, dealing Pure/Magic damage and healing Pudge for 100%.",
                    TargetType.UNIT_OR_AUTO, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(100, 130, 160), cooldown = floatArrayOf(24f, 20f, 16f),
                    castRange = 200f, baseValues = floatArrayOf(140f, 220f, 320f),
                    duration = floatArrayOf(2.2f, 2.6f, 3.0f), piercesBkb = true,
                    shardUpgrade = "Increases cast range and heals extra 10% max HP.")
            ),
            talents = listOf(
                TalentDef(10, "+4 Armor", "+16% Rot Slow", "armor_4", "spec_16"),
                TalentDef(15, "+14% Spell Lifesteal", "+250 Health", "spell_ls_14", "hp_250"),
                TalentDef(20, "-2s Meat Hook CD", "+120 Meat Hook Pure Dmg", "cdr_12", "spec_hook"),
                TalentDef(25, "+1.8x Flesh Heap Stacks", "+450 Health", "heap_2x", "hp_450")
            )
        ),

        // 3. INVOKER
        HeroDef(
            id = "invoker",
            name = "Invoker",
            title = "Kael, Arsenal Magus",
            attribute = PrimaryAttribute.UNIVERSAL,
            roles = listOf(HeroRole.MID, HeroRole.NUKER),
            primaryColor = 0xFFF39C12.toInt(),
            accentColor = 0xFFAB47BC.toInt(),
            isRanged = true,
            attackRange = 540f,
            projectileSpeed = 950f,
            baseAttackTime = 1.7f,
            baseStr = 19f, baseAgi = 16f, baseInt = 22f,
            strGain = 2.4f, agiGain = 1.9f, intGain = 3.8f,
            baseDamageMin = 22, baseDamageMax = 28,
            baseArmor = 1.5f,
            baseMoveSpeed = 290f,
            innateName = "Quas-Wex-Exort",
            innateDesc = "Every hero level grants +1.2% Spell Amplification and +0.8% Cooldown Reduction.",
            lore = "In the earliest ages of magic, Kael mastered ten thousand incantations.",
            facets = listOf(
                FacetDef("inv_elitist", "Elitist", "Attacks & spells grant +14% Spell Amp and +8% Lifesteal.", bonusSpellAmp = 0.14f, bonusLifesteal = 0.08f),
                FacetDef("inv_agnostic", "Scholar of Koryx", "Reduces all spell cooldowns by 12% and adds +12 Damage.", bonusCdr = 0.12f, bonusDamage = 12)
            ),
            abilities = listOf(
                AbilityDef("inv_q", "Q", "Sun Strike", "Calls a delayed solar beam anywhere in long range dealing massive Pure damage.",
                    TargetType.POINT_AOE, DamageType.PURE, manaCost = intArrayOf(110, 125, 140, 155),
                    cooldown = floatArrayOf(16f, 14f, 12f, 10f), castRange = 1400f, radius = 155f,
                    baseValues = floatArrayOf(190f, 285f, 380f, 500f), piercesBkb = true,
                    scepterUpgrade = "Cataclysm: Drops 2 extra Sun Strikes near enemy heroes!"),
                AbilityDef("inv_w", "W", "Chaos Meteor", "Drops a burning meteor that rolls forward leaving fiery AoE damage.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.MAGICAL, manaCost = intArrayOf(130, 145, 160, 175),
                    cooldown = floatArrayOf(18f, 16f, 14f, 12f), castRange = 760f, radius = 185f,
                    baseValues = floatArrayOf(140f, 210f, 280f, 360f), duration = floatArrayOf(2.6f, 2.8f, 3.0f, 3.2f)),
                AbilityDef("inv_e", "E", "Deafening Blast", "Sends a sonic shockwave knocking enemies back, disarming and damaging them.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.MAGICAL, manaCost = intArrayOf(120, 130, 140, 150),
                    cooldown = floatArrayOf(18f, 16f, 14f, 12f), castRange = 680f, radius = 160f,
                    baseValues = floatArrayOf(120f, 180f, 240f, 320f), duration = floatArrayOf(1.6f, 2.0f, 2.4f, 2.8f)),
                AbilityDef("inv_r", "R", "EMP Nova", "Detonates an arcane pulse around target point burning Mana, dealing Pure+Magic burst and stunning.",
                    TargetType.POINT_AOE, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(175, 225, 275), cooldown = floatArrayOf(42f, 34f, 26f),
                    castRange = 780f, radius = 340f, baseValues = floatArrayOf(250f, 400f, 560f),
                    duration = floatArrayOf(1.8f, 2.2f, 2.6f),
                    shardUpgrade = "EMP pulls enemies toward the detonation center!")
            ),
            talents = listOf(
                TalentDef(10, "+25 Move Speed", "+12% Spell Amp", "ms_25", "amp_12"),
                TalentDef(15, "+220 Mana", "+35 Attack Speed", "mana_220", "as_35"),
                TalentDef(20, "+15% Cooldown Reduction", "+300 Health", "cdr_15", "hp_300"),
                TalentDef(25, "Radial Deafening Blast", "+180 Sun Strike Damage", "spec_blast", "spec_ss")
            )
        ),

        // 4. PHANTOM ASSASSIN
        HeroDef(
            id = "pa",
            name = "Phantom Assassin",
            title = "Mortred, Sisters' Veil",
            attribute = PrimaryAttribute.AGILITY,
            roles = listOf(HeroRole.CARRY, HeroRole.ASSASSIN),
            primaryColor = 0xFF2980B9.toInt(),
            accentColor = 0xFFE74C3C.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.7f,
            baseStr = 19f, baseAgi = 23f, baseInt = 15f,
            strGain = 2.0f, agiGain = 3.4f, intGain = 1.4f,
            baseDamageMin = 28, baseDamageMax = 32,
            baseArmor = 3.0f,
            baseMoveSpeed = 305f,
            innateName = "Immaterial",
            innateDesc = "Innate +15% Evasion plus +1% Evasion per hero level.",
            lore = "Through the Veil of Sisters, Mortred strikes once—and the target is already severed.",
            facets = listOf(
                FacetDef("pa_methodical", "Methodical", "+10% Base Crit Chance & +12 Attack Damage.", bonusDamage = 12, bonusCritChance = 0.10f),
                FacetDef("pa_veiled", "Veiled One", "Blur grants +25 Move Speed and +8% Lifesteal.", bonusMoveSpeed = 25f, bonusLifesteal = 0.08f)
            ),
            abilities = listOf(
                AbilityDef("pa_q", "Q", "Stifling Dagger", "Throws a deadly dagger slowing the enemy and applying a physical strike that can Crit!",
                    TargetType.UNIT_OR_AUTO, DamageType.PHYSICAL, manaCost = intArrayOf(30, 28, 25, 22),
                    cooldown = floatArrayOf(5.5f, 4.8f, 4.2f, 3.5f), castRange = 760f,
                    baseValues = floatArrayOf(65f, 85f, 105f, 130f), duration = floatArrayOf(2.0f, 2.4f, 2.8f, 3.2f)),
                AbilityDef("pa_w", "W", "Phantom Strike", "Teleports behind an enemy and grants massive Attack Speed & Lifesteal.",
                    TargetType.UNIT_OR_AUTO, DamageType.PHYSICAL, manaCost = intArrayOf(40, 45, 50, 55),
                    cooldown = floatArrayOf(10f, 8.5f, 7f, 5.5f), castRange = 720f,
                    baseValues = floatArrayOf(80f, 115f, 150f, 190f), duration = floatArrayOf(2.5f, 2.8f, 3.1f, 3.5f)),
                AbilityDef("pa_e", "E", "Blur", "Turns semi-invisible until near enemy heroes and boosts Evasion.",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(40, 40, 40, 40),
                    cooldown = floatArrayOf(24f, 20f, 16f, 12f),
                    baseValues = floatArrayOf(0.25f, 0.35f, 0.45f, 0.55f), duration = floatArrayOf(10f, 12f, 14f, 16f),
                    scepterUpgrade = "Kills refresh Blur & Stifling Dagger cooldowns!"),
                AbilityDef("pa_r", "R", "Coup de Grâce", "Passive: Deadly mastery giving a 17% chance to inflict devastating Critical Damage.",
                    TargetType.PASSIVE, DamageType.PHYSICAL, isUltimate = true,
                    manaCost = intArrayOf(0, 0, 0), cooldown = floatArrayOf(0f, 0f, 0f),
                    baseValues = floatArrayOf(2.5f, 3.5f, 4.5f),
                    shardUpgrade = "Active Fan of Knives: Releases blades dealing 16% max HP Pure+Physical damage!")
            ),
            talents = listOf(
                TalentDef(10, "+250 Health", "+18 Attack Speed", "hp_250", "as_18"),
                TalentDef(15, "+15% Lifesteal", "+25 Move Speed", "ls_15", "ms_25"),
                TalentDef(20, "+35 Damage", "+15% Evasion", "dmg_35", "eva_15"),
                TalentDef(25, "+10% Coup Crit Chance", "Triple Stifling Dagger", "crit_chance_10", "spec_dagger3")
            )
        ),

        // 5. AXE
        HeroDef(
            id = "axe",
            name = "Axe",
            title = "Mogul Khan, Red Mist",
            attribute = PrimaryAttribute.STRENGTH,
            roles = listOf(HeroRole.INITIATOR, HeroRole.OFFLANE),
            primaryColor = 0xFFC0392B.toInt(),
            accentColor = 0xFFF39C12.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.7f,
            baseStr = 26f, baseAgi = 20f, baseInt = 18f,
            strGain = 2.9f, agiGain = 1.8f, intGain = 1.6f,
            baseDamageMin = 30, baseDamageMax = 34,
            baseArmor = 3.5f,
            baseMoveSpeed = 310f,
            innateName = "Coat of Blood",
            innateDesc = "Gains +0.8 permanent Armor per enemy kill or Culling Blade execute.",
            lore = "One-man army of the Red Mist, Axe charges straight into five foes and laughs.",
            facets = listOf(
                FacetDef("axe_one_man", "One Man Army", "+8 Armor and +15 Damage when fighting heroes.", bonusArmor = 8f, bonusDamage = 15),
                FacetDef("axe_blood_fury", "Red Mist Fury", "+25 Move Speed and +10% Lifesteal.", bonusMoveSpeed = 25f, bonusLifesteal = 0.10f)
            ),
            abilities = listOf(
                AbilityDef("axe_q", "Q", "Berserker's Call", "Taunts nearby enemies to attack Axe while granting massive bonus Armor.",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(80, 90, 100, 110),
                    cooldown = floatArrayOf(15f, 13.5f, 12f, 10.5f), radius = 250f,
                    baseValues = floatArrayOf(14f, 18f, 22f, 28f), duration = floatArrayOf(2.0f, 2.4f, 2.8f, 3.2f),
                    piercesBkb = true,
                    scepterUpgrade = "Applies Battle Hunger to all taunted foes & reduces Call cooldown by 3s!"),
                AbilityDef("axe_w", "W", "Battle Hunger", "Enrages a target, slowing it and dealing damage over time while speeding up Axe.",
                    TargetType.UNIT_OR_AUTO, DamageType.PHYSICAL, manaCost = intArrayOf(50, 60, 70, 80),
                    cooldown = floatArrayOf(14f, 12f, 10f, 8f), castRange = 650f,
                    baseValues = floatArrayOf(22f, 32f, 44f, 58f), duration = floatArrayOf(6f, 7f, 8f, 9f)),
                AbilityDef("axe_e", "E", "Counter Helix", "Passive: When attacked, Axe has a 22% chance to spin and deal Pure AoE damage.",
                    TargetType.PASSIVE, DamageType.PURE, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f), radius = 210f,
                    baseValues = floatArrayOf(80f, 115f, 150f, 195f), piercesBkb = true,
                    shardUpgrade = "+10% Helix chance and stacks -15% enemy attack damage!"),
                AbilityDef("axe_r", "R", "Culling Blade", "Executes low-HP enemies instantly in Pure damage, resetting cooldown and boosting team speed!",
                    TargetType.UNIT_OR_AUTO, DamageType.PURE, isUltimate = true,
                    manaCost = intArrayOf(80, 100, 120), cooldown = floatArrayOf(55f, 45f, 35f),
                    castRange = 220f, baseValues = floatArrayOf(300f, 450f, 650f),
                    piercesBkb = true)
            ),
            talents = listOf(
                TalentDef(10, "+12% Move Speed", "+6 Armor", "ms_30", "armor_6"),
                TalentDef(15, "+350 Health", "+40 Counter Helix Dmg", "hp_350", "spec_helix"),
                TalentDef(20, "+60 Radius Call", "+150 Culling Threshold", "spec_call", "spec_cull"),
                TalentDef(25, "+20 Armor", "+500 Health", "armor_20", "hp_500")
            )
        ),

        // 6. CRYSTAL MAIDEN
        HeroDef(
            id = "cm",
            name = "Crystal Maiden",
            title = "Rylai, Icewrack Archmage",
            attribute = PrimaryAttribute.INTELLIGENCE,
            roles = listOf(HeroRole.SUPPORT, HeroRole.NUKER),
            primaryColor = 0xFF38BDF8.toInt(),
            accentColor = 0xFFE0F2FE.toInt(),
            isRanged = true,
            attackRange = 540f,
            projectileSpeed = 900f,
            baseAttackTime = 1.7f,
            baseStr = 17f, baseAgi = 16f, baseInt = 22f,
            strGain = 2.0f, agiGain = 1.6f, intGain = 3.4f,
            baseDamageMin = 22, baseDamageMax = 28,
            baseArmor = 1.5f,
            baseMoveSpeed = 285f,
            innateName = "Blueheart Floe",
            innateDesc = "Amplifies Mana Regen of Crystal Maiden and nearby allies by +35%.",
            lore = "Born in the temperate realm and exiled to the Blueheart Glacier, Rylai commands lethal frost.",
            facets = listOf(
                FacetDef("cm_glacial", "Frozen Expanse", "Increases Spell Amp by +15% and Armor by +4.", bonusSpellAmp = 0.15f, bonusArmor = 4f),
                FacetDef("cm_overflow", "Arcane Overflow", "Reduces all spell cooldowns by 12% and +20 Move Speed.", bonusCdr = 0.12f, bonusMoveSpeed = 20f)
            ),
            abilities = listOf(
                AbilityDef("cm_q", "Q", "Crystal Nova", "Blasts an area with ice, dealing magic damage and slowing movement & attacks.",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(100, 115, 130, 145),
                    cooldown = floatArrayOf(10f, 9f, 8f, 7f), castRange = 680f, radius = 290f,
                    baseValues = floatArrayOf(130f, 195f, 260f, 330f), duration = floatArrayOf(3.5f, 3.8f, 4.1f, 4.5f)),
                AbilityDef("cm_w", "W", "Frostbite", "Encases an enemy in ice, rooting and disarming them while dealing frost damage.",
                    TargetType.UNIT_OR_AUTO, DamageType.MAGICAL, manaCost = intArrayOf(100, 110, 120, 130),
                    cooldown = floatArrayOf(9f, 8f, 7f, 6f), castRange = 560f,
                    baseValues = floatArrayOf(160f, 220f, 280f, 350f), duration = floatArrayOf(1.6f, 2.0f, 2.4f, 2.8f)),
                AbilityDef("cm_e", "E", "Arcane Aura", "Passive: Globally restores Mana/s for all allies. Active: Frost slide forward & mana burst!",
                    TargetType.INSTANT, DamageType.MAGICAL, manaCost = intArrayOf(40, 40, 40, 40),
                    cooldown = floatArrayOf(16f, 14f, 12f, 10f),
                    baseValues = floatArrayOf(2.5f, 4.0f, 5.5f, 7.5f),
                    shardUpgrade = "Crystal Clone: Leaves an icy decoy that explodes into Frostbite!"),
                AbilityDef("cm_r", "R", "Freezing Field", "Unleashes a deadly blizzard around Rylai, granting +20 Armor and raining ice shards.",
                    TargetType.INSTANT, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(180, 260, 340), cooldown = floatArrayOf(65f, 55f, 45f),
                    radius = 480f, baseValues = floatArrayOf(140f, 210f, 290f),
                    duration = floatArrayOf(4.5f, 5.0f, 5.5f),
                    scepterUpgrade = "Allows moving while Freezing Field is active and applies Frostbite!")
            ),
            talents = listOf(
                TalentDef(10, "+250 Health", "+25 Move Speed", "hp_250", "ms_25"),
                TalentDef(15, "+14% Spell Amp", "-12% Cooldowns", "amp_14", "cdr_12"),
                TalentDef(20, "+120 Crystal Nova Dmg", "+180 Attack Speed", "spec_nova", "as_180"),
                TalentDef(25, "+1.2s Frostbite Duration", "+140 Freezing Field DPS", "spec_fb", "spec_ff")
            )
        ),

        // 7. SNIPER
        HeroDef(
            id = "sniper",
            name = "Sniper",
            title = "Kardel Sharpeye, Keen Marksman",
            attribute = PrimaryAttribute.AGILITY,
            roles = listOf(HeroRole.CARRY, HeroRole.MID),
            primaryColor = 0xFFD35400.toInt(),
            accentColor = 0xFFF1C40F.toInt(),
            isRanged = true,
            attackRange = 720f,
            projectileSpeed = 1600f,
            baseAttackTime = 1.7f,
            baseStr = 19f, baseAgi = 27f, baseInt = 15f,
            strGain = 2.0f, agiGain = 3.2f, intGain = 2.2f,
            baseDamageMin = 20, baseDamageMax = 26,
            baseArmor = 1.5f,
            baseMoveSpeed = 285f,
            innateName = "Keen Scope",
            innateDesc = "Starts with extreme +180 bonus Attack Range and widens Vision.",
            lore = "Exiled by his own Keen clan for fulfilling the ancient sniper prophecy with too much accuracy.",
            facets = listOf(
                FacetDef("snp_ghillie", "Ghillie Suit", "+20 Move Speed and +10% Evasion while standing at range.", bonusMoveSpeed = 20f, bonusDamage = 8),
                FacetDef("snp_scatter", "Scattershot", "+18 Damage and Shrapnel deals instant burst.", bonusDamage = 18, bonusSpellAmp = 0.08f)
            ),
            abilities = listOf(
                AbilityDef("snp_q", "Q", "Shrapnel", "Launches an artillery shell that rains shrapnel over an area, slowing and damaging.",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(50, 60, 70, 80),
                    cooldown = floatArrayOf(12f, 11f, 10f, 9f), castRange = 950f, radius = 290f,
                    baseValues = floatArrayOf(35f, 55f, 75f, 100f), duration = floatArrayOf(6f, 6f, 6f, 6f), maxCharges = 3),
                AbilityDef("snp_w", "W", "Headshot", "Passive: 40% chance per shot to deal bonus physical damage and knock enemies back.",
                    TargetType.PASSIVE, DamageType.PHYSICAL, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0f, 0f, 0f, 0f),
                    baseValues = floatArrayOf(35f, 65f, 95f, 125f)),
                AbilityDef("snp_e", "E", "Take Aim", "Active: Grants +160 Attack Range, +8 Armor, and 100% Headshot procs for 4s!",
                    TargetType.INSTANT, DamageType.PHYSICAL, manaCost = intArrayOf(50, 50, 50, 50),
                    cooldown = floatArrayOf(16f, 14f, 12f, 10f),
                    baseValues = floatArrayOf(140f, 180f, 220f, 260f), duration = floatArrayOf(4.0f, 4.0f, 4.0f, 4.0f),
                    shardUpgrade = "Concussive Grenade: Knocks nearby enemies away and disarms them!"),
                AbilityDef("snp_r", "R", "Assassinate", "Locks onto a distant enemy and fires a supersonic round dealing massive damage + mini-stun.",
                    TargetType.UNIT_OR_AUTO, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(150, 200, 250), cooldown = floatArrayOf(18f, 14f, 10f),
                    castRange = 1600f, baseValues = floatArrayOf(320f, 490f, 680f),
                    scepterUpgrade = "Instant cast, stuns for 1.5s, and adds an automatic Critical Hit!")
            ),
            talents = listOf(
                TalentDef(10, "+18 Attack Speed", "+20 Move Speed", "as_18", "ms_20"),
                TalentDef(15, "+35 Shrapnel DPS", "+30 Attack Damage", "spec_shrap", "dmg_30"),
                TalentDef(20, "+35 Knockback Headshot", "+280 Health", "spec_hs", "hp_280"),
                TalentDef(25, "+140 Attack Range", "+6 Shrapnel Charges / +25% Crit", "range_140", "crit_25")
            )
        ),

        // 8. EARTHSHAKER
        HeroDef(
            id = "earthshaker",
            name = "Earthshaker",
            title = "Raigor Stonehoof, Earthborn",
            attribute = PrimaryAttribute.STRENGTH,
            roles = listOf(HeroRole.INITIATOR, HeroRole.SUPPORT),
            primaryColor = 0xFFA04000.toInt(),
            accentColor = 0xFF5DADE2.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.7f,
            baseStr = 24f, baseAgi = 12f, baseInt = 18f,
            strGain = 3.4f, agiGain = 1.4f, intGain = 2.0f,
            baseDamageMin = 29, baseDamageMax = 35,
            baseArmor = 3.0f,
            baseMoveSpeed = 310f,
            innateName = "Spirit Cairn",
            innateDesc = "Leaves an echoing fissure pulse after each cast, amplifying AoE damage.",
            lore = "Forged from the living stone of Nishai mountains, Raigor splits armies with a single slam.",
            facets = listOf(
                FacetDef("es_tectonic", "Tectonic Buildup", "+4 Armor and +12% Spell Amp.", bonusArmor = 4f, bonusSpellAmp = 0.12f),
                FacetDef("es_slugger", "Slugger", "+20 Attack Damage and +15 Move Speed.", bonusDamage = 20, bonusMoveSpeed = 15f)
            ),
            abilities = listOf(
                AbilityDef("es_q", "Q", "Fissure", "Slams a mighty totem creating an impassable ridge of stone that stuns enemies in a line.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.MAGICAL, manaCost = intArrayOf(110, 120, 130, 140),
                    cooldown = floatArrayOf(15f, 13.5f, 12f, 10.5f), castRange = 860f, radius = 95f,
                    baseValues = floatArrayOf(120f, 185f, 250f, 320f), duration = floatArrayOf(1.2f, 1.4f, 1.6f, 1.8f)),
                AbilityDef("es_w", "W", "Enchant Totem", "Empowers Raigor's totem, multiplying his next melee attack damage by +200% to +400%!",
                    TargetType.INSTANT, DamageType.PHYSICAL, manaCost = intArrayOf(40, 45, 50, 55),
                    cooldown = floatArrayOf(6f, 5.5f, 5f, 4.5f),
                    baseValues = floatArrayOf(2.0f, 2.75f, 3.5f, 4.5f), duration = floatArrayOf(8f, 8f, 8f, 8f),
                    scepterUpgrade = "Leaps into the air to any location within 650 range and slams down!"),
                AbilityDef("es_e", "E", "Aftershock", "Passive: Whenever Raigor casts any spell, the ground shakes, stunning & damaging nearby enemies.",
                    TargetType.PASSIVE, DamageType.MAGICAL, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0f, 0f, 0f, 0f), radius = 240f,
                    baseValues = floatArrayOf(75f, 110f, 145f, 180f), duration = floatArrayOf(0.8f, 1.0f, 1.2f, 1.4f)),
                AbilityDef("es_r", "R", "Echo Slam", "Shockwaves rip through the earth—every enemy hero & creep hit unleashes bonus Echo damage!",
                    TargetType.INSTANT, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(145, 205, 265), cooldown = floatArrayOf(80f, 70f, 60f),
                    radius = 450f, baseValues = floatArrayOf(150f, 230f, 320f),
                    shardUpgrade = "Fissure triggers Aftershock along its entire length!")
            ),
            talents = listOf(
                TalentDef(10, "+15 Damage", "+200 Mana", "dmg_15", "mana_200"),
                TalentDef(15, "+25 Move Speed", "+15% Cooldown Reduction", "ms_25", "cdr_15"),
                TalentDef(20, "+80 Echo Slam Base Dmg", "+350 Health", "spec_echo", "hp_350"),
                TalentDef(25, "-2s Enchant Totem CD", "+12% Spell Amp", "spec_totem", "amp_12")
            )
        ),

        // 9. ANTI-MAGE
        HeroDef(
            id = "antimage",
            name = "Anti-Mage",
            title = "Magina, Turstarkuri Monk",
            attribute = PrimaryAttribute.AGILITY,
            roles = listOf(HeroRole.CARRY, HeroRole.ASSASSIN),
            primaryColor = 0xFF9B59B6.toInt(),
            accentColor = 0xFF1ABC9C.toInt(),
            isRanged = false,
            attackRange = 135f,
            projectileSpeed = 0f,
            baseAttackTime = 1.4f,
            baseStr = 19f, baseAgi = 25f, baseInt = 12f,
            strGain = 1.8f, agiGain = 3.0f, intGain = 1.8f,
            baseDamageMin = 29, baseDamageMax = 33,
            baseArmor = 2.5f,
            baseMoveSpeed = 315f,
            innateName = "Persecutor",
            innateDesc = "Attacks slow enemies based on how much Mana they are missing.",
            lore = "Sole survivor of the Turstarkuri monastery, sworn to eradicate every trace of sorcery.",
            facets = listOf(
                FacetDef("am_magebane", "Magebane's Mirror", "+12 Attack Damage & Counterspell reflects spells stronger.", bonusDamage = 12, bonusArmor = 3f),
                FacetDef("am_mana_thirst", "Mana Thirst", "+20 Move Speed and +8% Lifesteal against casters.", bonusMoveSpeed = 20f, bonusLifesteal = 0.08f)
            ),
            abilities = listOf(
                AbilityDef("am_q", "Q", "Mana Break", "Passive: Every attack burns enemy Mana and deals bonus Physical damage.",
                    TargetType.PASSIVE, DamageType.PHYSICAL, manaCost = intArrayOf(0, 0, 0, 0),
                    cooldown = floatArrayOf(0f, 0f, 0f, 0f),
                    baseValues = floatArrayOf(30f, 45f, 60f, 75f)),
                AbilityDef("am_w", "W", "Blink", "Short-cooldown instant teleport across cliffs, trees, and battlefields.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.PURE, manaCost = intArrayOf(45, 45, 45, 45),
                    cooldown = floatArrayOf(11f, 9f, 7f, 5.2f), castRange = 800f,
                    baseValues = floatArrayOf(800f, 800f, 800f, 800f),
                    scepterUpgrade = "Blink Fragment: Leaves an attacking illusion & cuts Blink cooldown!"),
                AbilityDef("am_e", "E", "Counterspell", "Passive +25% Magic Resist. Active: Grants 85% Magic Resistance & Spell Shield for 2.5s.",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(40, 40, 40, 40),
                    cooldown = floatArrayOf(14f, 12f, 10f, 8f),
                    baseValues = floatArrayOf(0.20f, 0.25f, 0.30f, 0.38f), duration = floatArrayOf(2.5f, 2.5f, 2.5f, 2.5f),
                    shardUpgrade = "Generates a Counterspell Ally Aura reducing enemy Spell Amp!"),
                AbilityDef("am_r", "R", "Mana Void", "Stuns target and explodes their missing Mana into massive AoE Magic damage!",
                    TargetType.UNIT_OR_AUTO, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(125, 200, 275), cooldown = floatArrayOf(55f, 48f, 40f),
                    castRange = 540f, radius = 360f, baseValues = floatArrayOf(0.85f, 1.0f, 1.2f),
                    duration = floatArrayOf(0.7f, 0.9f, 1.1f))
            ),
            talents = listOf(
                TalentDef(10, "+18 Attack Speed", "+200 Health", "as_18", "hp_200"),
                TalentDef(15, "+15 Agility", "+12% Lifesteal", "agi_15", "ls_12"),
                TalentDef(20, "-1.2s Blink Cooldown", "+10% Magic Resistance", "spec_blink", "mr_10"),
                TalentDef(25, "+0.25 Mana Void Multiplier", "+25% Evasion", "spec_mv", "eva_25")
            )
        ),

        // 10. ZEUS
        HeroDef(
            id = "zeus",
            name = "Zeus",
            title = "Lord of Heaven",
            attribute = PrimaryAttribute.INTELLIGENCE,
            roles = listOf(HeroRole.MID, HeroRole.NUKER),
            primaryColor = 0xFF5DADE2.toInt(),
            accentColor = 0xFFF7DC6F.toInt(),
            isRanged = true,
            attackRange = 460f,
            projectileSpeed = 1200f,
            baseAttackTime = 1.7f,
            baseStr = 19f, baseAgi = 11f, baseInt = 24f,
            strGain = 2.1f, agiGain = 1.2f, intGain = 3.5f,
            baseDamageMin = 28, baseDamageMax = 34,
            baseArmor = 2.0f,
            baseMoveSpeed = 305f,
            innateName = "Static Field",
            innateDesc = "Every spell Zeus casts shocks nearby enemies for 6% of their current HP.",
            lore = "Having surrendered immortality to prove his worth to mortal eyes, Zeus still commands all thunder.",
            facets = listOf(
                FacetDef("zeus_livewire", "Livewire", "+14% Spell Amp and +15 Move Speed.", bonusSpellAmp = 0.14f, bonusMoveSpeed = 15f),
                FacetDef("zeus_thunder", "Divine Rampage", "12% Cooldown Reduction and +15 Attack Damage.", bonusCdr = 0.12f, bonusDamage = 15)
            ),
            abilities = listOf(
                AbilityDef("zeus_q", "Q", "Arc Lightning", "Hurls a bolt of lightning that leaps through up to 6 nearby enemies.",
                    TargetType.UNIT_OR_AUTO, DamageType.MAGICAL, manaCost = intArrayOf(65, 75, 85, 95),
                    cooldown = floatArrayOf(1.9f, 1.7f, 1.5f, 1.3f), castRange = 680f, radius = 320f,
                    baseValues = floatArrayOf(85f, 115f, 145f, 180f)),
                AbilityDef("zeus_w", "W", "Lightning Bolt", "Strikes an enemy with a bolt from heaven, dealing heavy damage, mini-stun & True Sight!",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(110, 120, 130, 140),
                    cooldown = floatArrayOf(6.5f, 6f, 5.5f, 5f), castRange = 720f, radius = 220f,
                    baseValues = floatArrayOf(140f, 210f, 290f, 370f), duration = floatArrayOf(0.4f, 0.5f, 0.6f, 0.7f)),
                AbilityDef("zeus_e", "E", "Heavenly Jump", "Zeus leaps through the air, shocking and slowing nearby enemies.",
                    TargetType.INSTANT, DamageType.MAGICAL, manaCost = intArrayOf(50, 55, 60, 65),
                    cooldown = floatArrayOf(18f, 15f, 12f, 9.5f), radius = 340f,
                    baseValues = floatArrayOf(70f, 110f, 150f, 190f), duration = floatArrayOf(2.0f, 2.3f, 2.6f, 3.0f),
                    shardUpgrade = "Grants passive Lightning Hands: Attacks trigger mini Arc Lightning!"),
                AbilityDef("zeus_r", "R", "Thundergod's Wrath", "GLOBAL: Strikes EVERY enemy hero on the entire map with thunder & True Sight!",
                    TargetType.INSTANT, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(225, 325, 425), cooldown = floatArrayOf(80f, 70f, 60f),
                    baseValues = floatArrayOf(300f, 440f, 600f),
                    scepterUpgrade = "Nimbus: Summons a thundercloud above target area periodically casting Lightning Bolt!")
            ),
            talents = listOf(
                TalentDef(10, "+250 Health", "+25 Move Speed", "hp_250", "ms_25"),
                TalentDef(15, "+12% Spell Amp", "-15% Cooldowns", "amp_12", "cdr_15"),
                TalentDef(20, "+80 Lightning Bolt Dmg", "+300 Mana", "spec_bolt", "mana_300"),
                TalentDef(25, "+180 Thundergod's Wrath", "+25% Spell Lifesteal", "spec_tgw", "spell_ls_25")
            )
        ),

        // 11. VOID SPIRIT
        HeroDef(
            id = "void_spirit",
            name = "Void Spirit",
            title = "Inai, Hidden One",
            attribute = PrimaryAttribute.UNIVERSAL,
            roles = listOf(HeroRole.MID, HeroRole.ASSASSIN),
            primaryColor = 0xFF7D3C98.toInt(),
            accentColor = 0xFFE8DAEF.toInt(),
            isRanged = false,
            attackRange = 165f,
            projectileSpeed = 0f,
            baseAttackTime = 1.7f,
            baseStr = 21f, baseAgi = 20f, baseInt = 24f,
            strGain = 2.6f, agiGain = 2.3f, intGain = 3.0f,
            baseDamageMin = 26, baseDamageMax = 30,
            baseArmor = 2.5f,
            baseMoveSpeed = 300f,
            innateName = "Intrinsic Edge",
            innateDesc = "Gains bonus Spell Amp and Magic Resistance from his Universal mastery.",
            lore = "Oldest of the four Celestial Spirits, stepping out from the hidden plane to reshape the third war.",
            facets = listOf(
                FacetDef("vs_sanctuary", "Sanctuary", "Resonant Pulse shield is +35% stronger and +5 Armor.", bonusArmor = 5f, bonusDamage = 8),
                FacetDef("vs_symmetry", "Symmetry", "+14% Spell Amp and +15 Move Speed.", bonusSpellAmp = 0.14f, bonusMoveSpeed = 15f)
            ),
            abilities = listOf(
                AbilityDef("vs_q", "Q", "Aether Remnant", "Creates a void remnant that pulls and stuns the first enemy entering its gaze.",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(75, 85, 95, 105),
                    cooldown = floatArrayOf(13f, 11.5f, 10f, 8.5f), castRange = 640f, radius = 190f,
                    baseValues = floatArrayOf(110f, 165f, 220f, 280f), duration = floatArrayOf(1.2f, 1.5f, 1.8f, 2.1f)),
                AbilityDef("vs_w", "W", "Dissimilate", "Temporarily vanishes into the void, then erupts in a burst of AoE Magic damage!",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(100, 110, 120, 130),
                    cooldown = floatArrayOf(16f, 14f, 12f, 10f), castRange = 460f, radius = 260f,
                    baseValues = floatArrayOf(150f, 220f, 290f, 370f),
                    shardUpgrade = "Leaves outer astral rings dealing +100 bonus damage!"),
                AbilityDef("vs_e", "E", "Resonant Pulse", "Emits a protective wave damaging nearby foes and granting Inai a Barrier Shield!",
                    TargetType.INSTANT, DamageType.MAGICAL, manaCost = intArrayOf(90, 100, 110, 120),
                    cooldown = floatArrayOf(14f, 12.5f, 11f, 9.5f), radius = 320f,
                    baseValues = floatArrayOf(120f, 180f, 240f, 320f), duration = floatArrayOf(5f, 5f, 5f, 5f),
                    scepterUpgrade = "Silences all enemies hit for 2.0s & grants 2 Charges!"),
                AbilityDef("vs_r", "R", "Astral Step", "Charge-based dash slicing through enemies, applying a delayed void mark detonation!",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.MAGICAL, isUltimate = true,
                    manaCost = intArrayOf(90, 100, 110), cooldown = floatArrayOf(14f, 11f, 8f),
                    castRange = 760f, radius = 120f, baseValues = floatArrayOf(180f, 280f, 400f),
                    maxCharges = 2)
            ),
            talents = listOf(
                TalentDef(10, "+25 Attack Damage", "+200 Mana", "dmg_25", "mana_200"),
                TalentDef(15, "+14% Spell Amp", "+25 Move Speed", "amp_14", "ms_25"),
                TalentDef(20, "-2.5s Astral Step CD", "+350 Health", "cdr_15", "hp_350"),
                TalentDef(25, "Astral Step Crits 160%", "+20% Spell Lifesteal", "spec_step", "spell_ls_20")
            )
        ),

        // 12. CHRONOS PRIME (DOTA 3 ORIGINAL HERO!)
        HeroDef(
            id = "chronos",
            name = "Chronos Prime",
            title = "The Temporal Sovereign (Dota 3)",
            attribute = PrimaryAttribute.UNIVERSAL,
            roles = listOf(HeroRole.INITIATOR, HeroRole.CARRY),
            primaryColor = 0xFF16A085.toInt(),
            accentColor = 0xFFFFD700.toInt(),
            isRanged = false,
            attackRange = 155f,
            projectileSpeed = 0f,
            baseAttackTime = 1.55f,
            baseStr = 23f, baseAgi = 23f, baseInt = 21f,
            strGain = 2.7f, agiGain = 2.8f, intGain = 2.5f,
            baseDamageMin = 28, baseDamageMax = 34,
            baseArmor = 3.5f,
            baseMoveSpeed = 305f,
            innateName = "Temporal Echo",
            innateDesc = "Dota 3 Time-Warden: Gains +10% Cooldown Reduction and attacks deal bonus Pure echo damage.",
            lore = "Born from the shattering of the First and Second Ancients, Chronos Prime bends time itself in Dota 3.",
            facets = listOf(
                FacetDef("chr_paradox", "Time-Walk Paradox", "+25 Move Speed and +12% Lifesteal.", bonusMoveSpeed = 25f, bonusLifesteal = 0.12f),
                FacetDef("chr_singularity", "Singularity", "+14% Cooldown Reduction and +16 Damage.", bonusCdr = 0.14f, bonusDamage = 16)
            ),
            abilities = listOf(
                AbilityDef("chr_q", "Q", "Time Rift", "Tears a temporal rift at target spot, pulling enemies inward, damaging & slowing time.",
                    TargetType.POINT_AOE, DamageType.MAGICAL, manaCost = intArrayOf(85, 95, 105, 115),
                    cooldown = floatArrayOf(12f, 10.5f, 9f, 7.5f), castRange = 680f, radius = 250f,
                    baseValues = floatArrayOf(120f, 180f, 240f, 310f), duration = floatArrayOf(2.0f, 2.4f, 2.8f, 3.2f)),
                AbilityDef("chr_w", "W", "Phase Lance", "Dashes forward or hurls a temporal lance piercing enemies and healing Chronos.",
                    TargetType.DIRECTION_SKILLSHOT, DamageType.PURE, manaCost = intArrayOf(60, 70, 80, 90),
                    cooldown = floatArrayOf(10f, 8.5f, 7f, 5.5f), castRange = 640f, radius = 110f,
                    baseValues = floatArrayOf(100f, 150f, 200f, 260f), piercesBkb = true,
                    shardUpgrade = "Restores 15% of recent damage taken as HP on cast!"),
                AbilityDef("chr_e", "E", "Paradox Aura", "Passive: Chance on hit to Time-Lock target (0.6s stun + Pure damage). Active: +80 Attack Speed!",
                    TargetType.INSTANT, DamageType.PURE, manaCost = intArrayOf(50, 50, 50, 50),
                    cooldown = floatArrayOf(14f, 12f, 10f, 8f),
                    baseValues = floatArrayOf(40f, 65f, 90f, 120f), duration = floatArrayOf(4.0f, 4.0f, 4.0f, 4.0f)),
                AbilityDef("chr_r", "R", "Chrono-Singularity", "Creates a golden Time Dome freezing ALL enemies inside while Chronos gains Haste & +60 Attack Speed!",
                    TargetType.POINT_AOE, DamageType.PURE, isUltimate = true,
                    manaCost = intArrayOf(175, 250, 325), cooldown = floatArrayOf(75f, 62f, 50f),
                    castRange = 540f, radius = 340f, baseValues = floatArrayOf(150f, 250f, 375f),
                    duration = floatArrayOf(3.2f, 3.8f, 4.5f), piercesBkb = true,
                    scepterUpgrade = "+90 Radius, +1.0s Duration & deals 90 Pure DPS inside the dome!")
            ),
            talents = listOf(
                TalentDef(10, "+20 Attack Speed", "+200 Health", "as_20", "hp_200"),
                TalentDef(15, "+25 Damage", "+25 Move Speed", "dmg_25", "ms_25"),
                TalentDef(20, "+15% Lifesteal", "+15% Cooldown Reduction", "ls_15", "cdr_15"),
                TalentDef(25, "+1.2s Chrono-Singularity", "+25% Crit Strike", "ult_12", "crit_25")
            )
        )
    )

    private val byId = allHeroes.associateBy { it.id }

    fun get(id: String): HeroDef = byId[id] ?: allHeroes.first()
}
