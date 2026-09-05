package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.LootPlacement

/**
 * docs/37-lootable-containers.md: shared by both firing sites — `:ui`'s `exploreMoveTo`
 * (pre-combat) and `Handlers.kt`'s `moveAlong` (combat) — mirroring `Triggers.kt`'s
 * `fireTriggerIfAny` exactly. Pure `GameState` transform, no `Effect`/resolver involvement: opening
 * a container doesn't touch combat state at all, it only flips a visibility bit for rendering and
 * records the placement as collectible — the actual item grant is deferred to `finishEncounter`
 * (docs/37: "unopened loot is lost," and even opened loot isn't granted until then).
 */
fun openLootIfAny(state: GameState, entityId: EntityId, at: GridPos): Pair<GameState, LootPlacement>? {
    val stepper = state.byId[entityId] ?: return null
    if (stepper.actor?.controller != Controller.Human) return null
    val placement = state.lootPlacements.firstOrNull { it.at == at } ?: return null
    if (placement.at in state.openedLoot) return null

    return state.copy(openedLoot = state.openedLoot + placement.at) to placement
}
