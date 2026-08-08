package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * docs/11-run-state.md's 8 numbered Invariants. Returns violation messages — empty means valid.
 * Same "checked in tests and debug builds" role as [de.jackbeback.pocketquest.core.rules.checkInvariants].
 *
 * Invariant 8 ("during an encounter, member.hp is stale by design; nothing outside
 * finishEncounter may write it") is a call-site discipline rule, not a property of one `RunState`
 * snapshot — there's nothing to check on a single state, so it isn't encoded here.
 */
fun checkRunInvariants(run: RunState, cat: Catalog): List<String> {
    val violations = mutableListOf<String>()

    // 1. party is non-empty and has at most 3 members.
    if (run.party.isEmpty()) violations += "party is empty"
    if (run.party.size > 3) violations += "party has ${run.party.size} members, more than the max of 3"

    // 2. Every MemberId is unique within the run.
    val seenIds = mutableSetOf<MemberId>()
    for (member in run.party) {
        if (!seenIds.add(member.memberId)) violations += "member id ${member.memberId.raw} appears more than once in party"
    }

    // 3/4. hp/mana within derived bounds; condition == Downed iff hp == 0.
    for (member in run.party) {
        val stats = member.toEntity(cat).stats(cat)
        if (member.hp !in 0..stats.maxHp) violations += "member ${member.memberId.raw} hp ${member.hp} outside 0..${stats.maxHp}"
        if (member.mana !in 0..stats.maxMana) violations += "member ${member.memberId.raw} mana ${member.mana} outside 0..${stats.maxMana}"
        val shouldBeDowned = member.hp == 0
        if ((member.condition == MemberCondition.Downed) != shouldBeDowned) {
            violations += "member ${member.memberId.raw} condition ${member.condition} disagrees with hp ${member.hp}"
        }
    }

    // 5. position exists in graph, and every node in visited does too.
    if (run.position !in run.graph.nodes) violations += "position ${run.position.raw} is not a node in graph"
    for (nodeId in run.visited) {
        if (nodeId !in run.graph.nodes) violations += "visited node ${nodeId.raw} is not a node in graph"
    }

    // 6. encounter != null implies every non-downed member has a mapped EntityId.
    run.encounter?.let { handle ->
        for (member in run.party) {
            if (member.condition != MemberCondition.Downed && member.memberId !in handle.memberToEntity) {
                violations += "member ${member.memberId.raw} has no mapped EntityId during an active encounter"
            }
        }
    }

    // 7. outcome != null implies encounter == null.
    if (run.outcome != null && run.encounter != null) {
        violations += "outcome ${run.outcome} is set but encounter is still active"
    }

    return violations
}
