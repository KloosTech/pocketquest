package de.jackbeback.pocketquest.ecs.core

import de.jackbeback.pocketquest.game.loop.TurnPhase

/**
 * Holds the ordered list of [GameSystem]s per [TurnPhase].
 * Systems are executed in registration order within each phase.
 */
class SystemRegistry {
    private val systems = LinkedHashMap<TurnPhase, MutableList<GameSystem>>()

    fun register(phase: TurnPhase, system: GameSystem) {
        systems.getOrPut(phase) { mutableListOf() }.add(system)
    }

    fun systemsFor(phase: TurnPhase): List<GameSystem> = systems[phase] ?: emptyList()
}
