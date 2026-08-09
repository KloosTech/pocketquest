package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionStatus
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.meta.Unlock
import de.jackbeback.pocketquest.core.run.RunOutcome
import de.jackbeback.pocketquest.core.run.RunState

/**
 * docs/11-run-state.md's "Champions handoff" + docs/12-progression.md's "Unlocking Party mode" —
 * the one place a finished [RunState] writes back onto [MetaState]. `run.party`'s `MemberId`s are
 * `ChampionId`s by convention (docs/10: "sibling, not parent" — no shared type, matched by `.raw`).
 */
fun resolveRunOutcome(meta: MetaState, run: RunState): MetaState {
    val outcome = requireNotNull(run.outcome) { "resolveRunOutcome called on a run with no outcome yet" }
    return when (outcome) {
        RunOutcome.Success -> {
            var roster = meta.roster
            for (member in run.party) {
                val championId = ChampionId(member.memberId.raw)
                val record = roster.getValue(championId)
                roster = roster + (championId to record.copy(equipment = member.equipment, status = ChampionStatus.Available))
            }
            // Idempotent past the first grant — doc12's "Unlocking Party mode" only actually fires
            // this for the solo pre-roster run, but re-adding an already-present Unlock on every
            // later Success is a harmless no-op, not a special case worth branching on.
            meta.copy(roster = roster, bank = meta.bank + run.gold, unlocks = meta.unlocks + Unlock.PartyMode)
        }

        RunOutcome.Failure -> {
            val fallen = run.party.map { ChampionId(it.memberId.raw) }.toSet()
            val roster = meta.roster - fallen
            // Deliberate exception to Unlock monotonicity (docs/12: "a full roster wipe demotes...")
            // — never call checkUnlockMonotonicity across THIS transition, only around it.
            val unlocks = if (roster.isEmpty()) meta.unlocks - Unlock.PartyMode else meta.unlocks
            meta.copy(roster = roster, unlocks = unlocks)
        }
    }
}
