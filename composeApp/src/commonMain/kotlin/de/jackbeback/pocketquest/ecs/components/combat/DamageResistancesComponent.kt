package de.jackbeback.pocketquest.ecs.components.combat

/** Per-damage-type damage multipliers (1.0 = no resistance, 0.5 = 50% resistance, 2.0 = vulnerability). */
data class DamageResistancesComponent(
    val multipliers: Map<DamageType, Float> = emptyMap()
)
