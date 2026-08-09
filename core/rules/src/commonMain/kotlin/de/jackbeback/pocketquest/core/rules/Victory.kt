package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState

enum class CombatOutcome { PlayerVictory, PlayerDefeat }

/**
 * Nothing in the engine ever called this before — the interactive turn loop (`:ui`'s `App`) ran
 * forever regardless of who was left standing until docs/11's run layer needed a real "is this
 * encounter over" answer to hand off to `finishEncounter`. Neutral entities don't factor into
 * either side's win condition. `null` (still ongoing) whenever both sides have at least one entity
 * with `health.current > 0` — an entity with no `health` at all (never spawned with one) doesn't
 * count as alive OR dead, matching how [de.jackbeback.pocketquest.core.model.Health] is only ever
 * absent for design-time placeholders, never a spawned combatant.
 */
fun GameState.combatOutcome(): CombatOutcome? {
    val playerAlive = entities.any { it.actor?.faction == Faction.Player && (it.health?.current ?: 0) > 0 }
    val enemyAlive = entities.any { it.actor?.faction == Faction.Enemy && (it.health?.current ?: 0) > 0 }
    return when {
        !playerAlive -> CombatOutcome.PlayerDefeat
        !enemyAlive -> CombatOutcome.PlayerVictory
        else -> null
    }
}
