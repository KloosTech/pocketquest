package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GridPos

/**
 * docs/38-loot-reveal-screen.md: grants (or loses to capacity) the item [at]'s [PendingLoot] already
 * rolled at `finishEncounter` time — capacity is checked HERE, against `run.inventory.items.size` as
 * it stands at this exact call, not at roll time, so revealing several chests in a row can watch the
 * bag actually fill up in whatever order the player taps them. A no-op if [at] doesn't match any
 * pending entry or that entry is already revealed (repeat taps on a resolved chest do nothing).
 */
fun revealLoot(run: RunState, at: GridPos, cat: Catalog): RunState {
    val pending = run.pendingLootReveal.find { it.at == at && !it.revealed } ?: return run
    val item = pending.item
    if (item == null) {
        // "Nothing" — no capacity question, nothing to add or lose.
        return run.copy(pendingLootReveal = run.pendingLootReveal.map { if (it.at == at) it.copy(revealed = true) else it })
    }
    val fits = run.inventory.items.size < carryCapacity(run.party, cat)
    val updatedRun = if (fits) run.copy(inventory = run.inventory.copy(items = run.inventory.items + item)) else run
    return updatedRun.copy(
        pendingLootReveal = updatedRun.pendingLootReveal.map { if (it.at == at) it.copy(revealed = true, lost = !fits) else it },
    )
}

/** docs/38-loot-reveal-screen.md's "Skip All" shortcut — folds [revealLoot] over every still-unrevealed entry in list order, same capacity-as-you-go semantics as tapping each one by hand. */
fun skipAllLootReveals(run: RunState, cat: Catalog): RunState =
    run.pendingLootReveal.filterNot { it.revealed }.fold(run) { acc, pending -> revealLoot(acc, pending.at, cat) }
