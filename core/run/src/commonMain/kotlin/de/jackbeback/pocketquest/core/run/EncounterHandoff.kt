package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.rules.chance
import de.jackbeback.pocketquest.core.rules.content.startEncounterWithParty
import de.jackbeback.pocketquest.core.rules.rollRange
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * docs/11-run-state.md's "The encounter handoff" — the only place `PartyMember`/`Entity` HP and
 * mana are copied in either direction (see `RunState.kt`'s "HP and mana live here, not in Entity").
 */
fun startEncounter(run: RunState, spec: EncounterSpec, cat: Catalog): RunState {
    val active = run.party.filter { it.condition != MemberCondition.Downed }
    val partyEntities = active.map { it.toEntity(cat) }
    val (state, spawnIds) = startEncounterWithParty(cat, spec, partyEntities, run.rng)

    val memberToEntity = active.indices.mapNotNull { i -> spawnIds[i]?.let { active[i].memberId to it } }.toMap()
    val handle = EncounterHandle(resolver = Resolver(state = state), memberToEntity = memberToEntity, spec = spec)
    return run.copy(encounter = handle, rng = state.rng)
}

/**
 * Wipe short-circuit: if every mapped member ends downed, doc11 says there's nothing to write HP/
 * loot back *to* — `run.outcome` becomes [RunOutcome.Failure] directly, skipping steps 1-3 entirely
 * (permadeath itself is `:core:meta`'s concern, Pass 7, not this function's).
 */
fun finishEncounter(run: RunState, final: GameState, cat: Catalog): RunState {
    val handle = requireNotNull(run.encounter) { "finishEncounter called with no active encounter" }
    val entitiesById = final.entities.associateBy { it.id }

    val outcomes = handle.memberToEntity.mapValues { (_, entityId) -> entitiesById.getValue(entityId).health!!.current }
    val wiped = outcomes.isNotEmpty() && outcomes.values.all { it <= 0 }

    if (wiped) {
        return run.copy(outcome = RunOutcome.Failure, encounter = null, rng = final.rng)
    }

    val updatedParty = run.party.map { member ->
        val entityId = handle.memberToEntity[member.memberId] ?: return@map member
        val entity = entitiesById.getValue(entityId)
        val maxMana = entity.stats(cat).maxMana
        if (entity.health!!.current <= 0) {
            member.copy(hp = 1, mana = maxMana, condition = MemberCondition.Healthy)
        } else {
            member.copy(hp = entity.health!!.current, mana = maxMana, condition = MemberCondition.Healthy)
        }
    }

    var rng = final.rng
    val capacity = carryCapacity(updatedParty, cat)
    var carried = run.inventory.items.size
    val lootedItems = mutableListOf<ItemId>()
    for (entry in handle.spec.loot) {
        val (advanced, hit) = rng.chance(entry.chance)
        rng = advanced
        // docs/13: capacity "blocks the pickup outright" whenever an item would be added — loot is
        // no exception, it just never enters the inventory rather than failing the whole encounter.
        if (hit && carried < capacity) {
            lootedItems += entry.item
            carried++
        }
    }
    val (advancedRng, goldReward) = rng.rollRange(handle.spec.goldMin, handle.spec.goldMax)
    rng = advancedRng

    // docs/10-game-loop.md: "Run ends on: Act 3 boss defeated (success...)" — winning the encounter
    // at the graph's Boss node (`run.position`, unchanged since `startEncounter`) IS the run's win
    // condition; nothing else sets RunOutcome.Success.
    val wonTheBoss = run.graph.nodes.getValue(run.position).type == NodeType.Boss

    return run.copy(
        party = updatedParty,
        inventory = run.inventory.copy(items = run.inventory.items + lootedItems),
        gold = run.gold + goldReward,
        encounter = null,
        outcome = if (wonTheBoss) RunOutcome.Success else null,
        rng = rng,
    )
}
