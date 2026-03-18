package de.jackbeback.pocketquest.ecs.components.core

/**
 * Tracks how many move actions an entity has remaining this turn.
 *
 * @param current   Move actions remaining this turn (decremented on each hop).
 * @param max       Move actions per turn (restored by TurnResetSystem).
 * @param range     Maximum Chebyshev distance allowed per single hop.
 */
data class MovementPointsComponent(val current: Int, val max: Int, val range: Int)
