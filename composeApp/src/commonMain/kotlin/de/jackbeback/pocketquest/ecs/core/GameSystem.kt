package de.jackbeback.pocketquest.ecs.core

import de.jackbeback.pocketquest.game.loop.TurnPhase

/** Base interface for all ECS systems. */
interface GameSystem {
    fun update(world: World, deltaMs: Long)
}

/** A system that only runs during specific [TurnPhase]s. */
interface PhaseSystem : GameSystem {
    val validPhases: Set<TurnPhase>
}

/** A system that runs every tick regardless of phase. */
interface TickSystem : GameSystem
