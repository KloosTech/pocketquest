package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Health(val current: Int, val temp: Int = 0)

@Serializable
data class Resources(val ap: Int, val mana: Int, val quickUsed: Boolean = false, val reactionUsed: Boolean = false)

/**
 * Player and enemy differ ONLY in actor.faction/actor.controller, never in
 * type. Nullable fields mean "this entity doesn't have this facet" (a wall
 * has no health field... it has health=null; a reserve unit has pos=null).
 */
@Serializable
data class Entity(
    val id: EntityId,
    val archetype: ArchetypeId,
    val pos: GridPos?,
    val health: Health?,
    val resources: Resources?,
    val actor: Actor?,
    val equipment: Equipment = Equipment.EMPTY,
    val statuses: List<ActiveStatus> = emptyList(),
    val blocksMovement: Boolean = true,
)
