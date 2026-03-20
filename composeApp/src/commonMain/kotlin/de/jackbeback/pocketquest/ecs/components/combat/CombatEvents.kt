package de.jackbeback.pocketquest.ecs.components.combat

import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.GameEvent

enum class DamageType {
    FIRE, WATER, ELECTRIC, FORCE, ICE, POISON, PIERCING, SLICING, BLUDGEONING
}

data class DamageEvent(
    val source: EntityId,
    val target: EntityId,
    val amount: Int,
    val damageType: DamageType
) : GameEvent

data class HealEvent(
    val source: EntityId,
    val target: EntityId,
    val amount: Int
) : GameEvent

data class DeathEvent(val entity: EntityId) : GameEvent

data class MoveEvent(
    val entity: EntityId,
    val to: PositionComponent
) : GameEvent

data class SkillUsedEvent(
    val user: EntityId,
    val skillId: String,
    val target: EntityId
) : GameEvent

data class TurnEndedEvent(val entity: EntityId) : GameEvent

data class MissEvent(
    val user: EntityId,
    val target: EntityId,
) : GameEvent

data class ConditionAppliedEvent(
    val target: EntityId,
    val type: ConditionType,
    val stacks: Int
) : GameEvent
