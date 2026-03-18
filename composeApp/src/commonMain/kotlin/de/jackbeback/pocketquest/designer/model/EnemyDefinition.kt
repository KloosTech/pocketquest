package de.jackbeback.pocketquest.designer.model

import kotlinx.serialization.Serializable

@Serializable
data class StatBlock(
    val str: Int = 10,
    val dex: Int = 10,
    val con: Int = 10,
    val intelligence: Int = 10,
    val wis: Int = 10,
    val cha: Int = 10,
    val ac: Int = 10,
)

/** Fully serializable enemy (unit) definition. */
@Serializable
data class EnemyDefinition(
    val id: String,
    val name: String,
    val sprite: String = "grunt",
    val stats: StatBlock = StatBlock(),
    val maxHealth: Int = 20,
    val maxMana: Int = 0,
    val manaRegen: Int = 0,
    val maxAp: Int = 1,
    val movesPerTurn: Int = 1,
    val moveRange: Int = 3,
    val initiative: Int = 8,
    /** One of: Aggressive, Defensive, Passive */
    val aiStrategy: String = "Aggressive",
    val preferredSkillId: String = "basic_attack",
    val spawnCol: Int = 11,
    val spawnRow: Int = 4,
    val skillIds: List<String> = listOf("basic_attack"),
    /** DamageType name → multiplier (e.g. "FIRE" → 0.5). */
    val damageResistances: Map<String, Float> = emptyMap(),
)
