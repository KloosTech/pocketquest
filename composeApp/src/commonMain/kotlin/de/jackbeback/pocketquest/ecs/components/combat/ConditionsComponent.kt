package de.jackbeback.pocketquest.ecs.components.combat

enum class ConditionType {
    // Debuffs — tick each EnvironmentPhase dealing damage
    Burn, Poison, Cold,
    // Buffs — do not tick; consumed on use
    Block,       // Absorbs incoming damage by stack count, consumes 1 stack per hit
    StrengthUp,  // Adds +2 damage per stack on skill use, does not expire automatically
}

/** Tracks active conditions and their stack counts on an entity. */
data class ConditionsComponent(
    val active: Map<ConditionType, Int> = emptyMap()
)
