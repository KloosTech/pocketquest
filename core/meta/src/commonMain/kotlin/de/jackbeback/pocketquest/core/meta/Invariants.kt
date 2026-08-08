package de.jackbeback.pocketquest.core.meta

/**
 * docs/12-progression.md's Invariants section. Returns violation messages — empty means valid.
 * Same "checked in tests and debug builds" role as [de.jackbeback.pocketquest.core.rules.checkInvariants].
 *
 * Two of doc12's four invariants (a `RunState.party` entry resolving to a real roster entry while
 * `status == OnRun`, and exactly one live run per `OnRun` Champion) are cross-module — they need
 * `RunState` to exist to check, and belong with `:core:run`'s own invariant checks (Pass 2/3 of
 * this feature's implementation plan), not here.
 */
fun checkMetaInvariants(state: MetaState): List<String> {
    val violations = mutableListOf<String>()

    // 3. bank >= 0 always.
    if (state.bank < 0) violations += "bank ${state.bank} is negative"

    // Every roster id is its own key — Map<ChampionId, ChampionRecord> already guarantees
    // uniqueness by construction, but a mismatched key/record.id would still be a real bug.
    for ((id, record) in state.roster) {
        if (record.id != id) violations += "roster key ${id.raw} maps to a record with mismatched id ${record.id.raw}"
    }

    return violations
}

/**
 * 4. `Unlock.PartyMode` is monotonic: once granted, no code path may remove it — checked across a
 * transition (before/after), not on a single snapshot, since monotonicity is a property of change
 * over time.
 */
fun checkUnlockMonotonicity(before: MetaState, after: MetaState): List<String> {
    val violations = mutableListOf<String>()
    val revoked = before.unlocks - after.unlocks
    if (revoked.isNotEmpty()) violations += "unlock(s) $revoked were revoked — unlocks must never be removed"
    return violations
}
