package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.rules.stat.stats

/** docs/11-run-state.md: "a run-layer operation, not an action: restore a fixed fraction of HP to every member." Exact fraction wasn't fixed by any doc — 50%, tunable. */
private const val REST_HEAL_FRACTION = 0.5

/** A `Rest` node: heals every party member (including a currently-Downed one) by a fraction of their derived max HP, capped at that max. */
fun applyRest(run: RunState, cat: Catalog, fraction: Double = REST_HEAL_FRACTION): RunState {
    val updated = run.party.map { member ->
        val maxHp = member.toEntity(cat).stats(cat).maxHp
        val healed = (member.hp + (maxHp * fraction).toInt()).coerceAtMost(maxHp)
        member.copy(hp = healed, condition = if (healed > 0) MemberCondition.Healthy else MemberCondition.Downed)
    }
    return run.copy(party = updated)
}

/** Marks `run.position` visited — the moment a node's content finishes resolving, before offering the next-node choice. */
fun RunState.markVisited(): RunState = copy(visited = visited + position)
