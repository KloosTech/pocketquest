package de.jackbeback.pocketquest.ecs.components.combat

enum class ConditionType { Burn, Poison, Cold }

/** Tracks active conditions and their stack counts on an entity. */
data class ConditionsComponent(
    val active: Map<ConditionType, Int> = emptyMap()
)
