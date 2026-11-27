package com.dota3.moba.model

enum class Team {
    RADIANT,
    DIRE,
    NEUTRAL;

    fun enemy(): Team = when (this) {
        RADIANT -> DIRE
        DIRE -> RADIANT
        NEUTRAL -> NEUTRAL
    }
}

enum class PrimaryAttribute(val shortLabel: String, val colorHex: Int) {
    STRENGTH("STR", 0xFFE74C3C.toInt()),
    AGILITY("AGI", 0xFF2ECC71.toInt()),
    INTELLIGENCE("INT", 0xFF3498DB.toInt()),
    UNIVERSAL("UNI", 0xFFF39C12.toInt())
}

enum class HeroRole(val label: String) {
    CARRY("Carry"),
    MID("Mid"),
    OFFLANE("Offlane"),
    SUPPORT("Support"),
    INITIATOR("Initiator"),
    NUKER("Nuker"),
    ASSASSIN("Assassin")
}

enum class GameMode(val title: String, val subtitle: String, val goldMult: Float, val xpMult: Float) {
    ALL_PICK_5V5("5v5 All Pick", "Classic Dota 3: 3 Lanes, Roshan, Wards & Runes", 1.0f, 1.0f),
    TURBO_5V5("5v5 Turbo", "2x Gold & XP, Fast Courier, Intense 10-min Matches", 2.0f, 2.0f),
    MID_1V1("1v1 Mid Duel", "Pure Mid Skill Matchup: First to 2 Kills or T1 Tower", 1.25f, 1.35f),
    SANDBOX("Hero Sandbox", "Free Gold, Level Up, Dummies & Cooldown Reset", 5.0f, 2.0f)
}

enum class BotDifficulty(val label: String, val reactionMult: Float, val goldBonusPerMin: Int) {
    EASY("Passive / Easy", 0.6f, 0),
    NORMAL("Normal", 0.85f, 30),
    HARD("Hard", 1.1f, 90),
    UNFAIR("Unfair AI", 1.35f, 180)
}

enum class DamageType {
    PHYSICAL,
    MAGICAL,
    PURE
}

enum class TargetType {
    INSTANT,
    POINT_AOE,
    DIRECTION_SKILLSHOT,
    UNIT_OR_AUTO,
    PASSIVE,
    TOGGLE
}

enum class RuneType(val label: String, val color: Int) {
    DOUBLE_DAMAGE("Double Damage", 0xFF38BDF8.toInt()),
    HASTE("Haste", 0xFFEF4444.toInt()),
    REGENERATION("Regeneration", 0xFF22C55E.toInt()),
    ARCANE("Arcane", 0xFFA855F7.toInt()),
    INVISIBILITY("Invisibility", 0xFFCBD5E1.toInt()),
    BOUNTY("Bounty Rune", 0xFFF59E0B.toInt()),
    WISDOM("Wisdom Rune", 0xFF6366F1.toInt()),
    WATER("Water Rune", 0xFF06B6D4.toInt()),
    LOTUS("Healing Lotus", 0xFFF472B6.toInt())
}

enum class StatusType {
    STUN,
    ROOT,
    SILENCE,
    DISARM,
    SLOW,
    TAUNT,
    HEX,
    CYCLONE,
    CHRONO_FREEZE,
    INVISIBLE,
    MAGIC_IMMUNE,
    PHASE,
    HASTE,
    DOUBLE_DAMAGE,
    REGEN,
    ARCANE,
    BLADE_FURY,
    BLUR,
    SMOKE,
    SHIELD,
    SATANIC,
    CORRUPTION,
    BATTLE_HUNGER,
    TRUE_SIGHT_REVEAL
}

data class FacetDef(
    val id: String,
    val name: String,
    val description: String,
    val bonusDamage: Int = 0,
    val bonusArmor: Float = 0f,
    val bonusMoveSpeed: Float = 0f,
    val bonusSpellAmp: Float = 0f,
    val bonusCdr: Float = 0f,
    val bonusLifesteal: Float = 0f,
    val bonusCritChance: Float = 0f
)

data class TalentDef(
    val level: Int,
    val leftLabel: String,
    val rightLabel: String,
    val leftKey: String,
    val rightKey: String
)

data class AbilityDef(
    val id: String,
    val keyLabel: String, // Q, W, E, R
    val name: String,
    val description: String,
    val targetType: TargetType,
    val damageType: DamageType,
    val isUltimate: Boolean = false,
    val maxLevel: Int = if (isUltimate) 3 else 4,
    val manaCost: IntArray,
    val cooldown: FloatArray,
    val castRange: Float = 0f,
    val radius: Float = 0f,
    val baseValues: FloatArray = floatArrayOf(0f),
    val duration: FloatArray = floatArrayOf(0f),
    val maxCharges: Int = 1,
    val piercesBkb: Boolean = false,
    val scepterUpgrade: String = "",
    val shardUpgrade: String = ""
)

data class HeroDef(
    val id: String,
    val name: String,
    val title: String,
    val attribute: PrimaryAttribute,
    val roles: List<HeroRole>,
    val primaryColor: Int,
    val accentColor: Int,
    val isRanged: Boolean,
    val attackRange: Float,
    val projectileSpeed: Float,
    val baseAttackTime: Float,
    val baseStr: Float,
    val baseAgi: Float,
    val baseInt: Float,
    val strGain: Float,
    val agiGain: Float,
    val intGain: Float,
    val baseDamageMin: Int,
    val baseDamageMax: Int,
    val baseArmor: Float,
    val baseMoveSpeed: Float,
    val dayVision: Float = 900f,
    val nightVision: Float = 520f,
    val innateName: String,
    val innateDesc: String,
    val lore: String,
    val facets: List<FacetDef>,
    val abilities: List<AbilityDef>, // Q, W, E, R
    val talents: List<TalentDef>     // Lv 10, 15, 20, 25
)

enum class ItemCategory(val title: String) {
    CONSUMABLE("Consumables"),
    BOOTS("Boots & Early"),
    CORE("Core & Utility"),
    LUXURY("Luxury & Late"),
    SECRET("Secret & Ancient")
}

data class ItemDef(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val cost: Int,
    val components: List<String> = emptyList(),
    val shortCode: String,
    val color: Int,
    val description: String,
    val isConsumable: Boolean = false,
    val maxStacks: Int = 1,
    val isActive: Boolean = false,
    val cooldown: Float = 0f,
    val manaCost: Int = 0,
    val bonusStr: Float = 0f,
    val bonusAgi: Float = 0f,
    val bonusInt: Float = 0f,
    val bonusDamage: Int = 0,
    val bonusArmor: Float = 0f,
    val bonusMagicResist: Float = 0f,
    val bonusHp: Float = 0f,
    val bonusMana: Float = 0f,
    val bonusHpRegen: Float = 0f,
    val bonusManaRegen: Float = 0f,
    val bonusMoveSpeed: Float = 0f,
    val bonusAttackSpeed: Float = 0f,
    val bonusLifesteal: Float = 0f,
    val bonusSpellLifesteal: Float = 0f,
    val bonusEvasion: Float = 0f,
    val bonusSpellAmp: Float = 0f,
    val bonusCdr: Float = 0f,
    val critChance: Float = 0f,
    val critMult: Float = 1.0f,
    val cleavePct: Float = 0f,
    val radianceDps: Float = 0f,
    val dropsOnDeath: Boolean = false
)

data class NeutralItemDef(
    val id: String,
    val name: String,
    val tier: Int,
    val description: String,
    val bonusAllStats: Float = 0f,
    val bonusDamage: Int = 0,
    val bonusArmor: Float = 0f,
    val bonusMoveSpeed: Float = 0f,
    val bonusAttackSpeed: Float = 0f,
    val bonusLifesteal: Float = 0f,
    val bonusSpellAmp: Float = 0f,
    val bonusCdr: Float = 0f,
    val bonusHp: Float = 0f,
    val bonusManaRegen: Float = 0f
)
