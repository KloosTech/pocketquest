package de.jackbeback.pocketquest.game.systems.combat

import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.MovementPointsComponent
import de.jackbeback.pocketquest.ecs.components.core.TurnStateComponent
import de.jackbeback.pocketquest.ecs.core.GameSystem
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.set

/** Resets action flags, restores movement points, and regenerates mana at the start of each phase. */
class TurnResetSystem : GameSystem {
    override fun update(world: World, deltaMs: Long) {
        world.query<TurnStateComponent>()
            .map { (id, _) -> id }.toList()
            .forEach { id -> world.set(id, TurnStateComponent(hasActed = false)) }

        world.query<MovementPointsComponent>()
            .map { (id, mp) -> id to mp }.toList()
            .forEach { (id, mp) -> world.set(id, mp.copy(current = mp.max)) }

        // Regenerate mana by regen amount (capped at max)
        world.query<ManaComponent>()
            .map { (id, mana) -> id to mana }.toList()
            .forEach { (id, mana) ->
                if (mana.regen > 0) {
                    world.set(id, mana.copy(current = (mana.current + mana.regen).coerceAtMost(mana.max)))
                }
            }
    }
}
