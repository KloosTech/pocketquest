package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterPool
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EventDef
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.core.model.EventPool
import de.jackbeback.pocketquest.core.model.PinnedContent
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.ShopId
import de.jackbeback.pocketquest.core.model.ShopPool
import de.jackbeback.pocketquest.core.rules.rollRange

/**
 * [EncounterPool]/[EventPool]/[ShopPool] themselves live in `:core:model` (a `Catalog` entry, same
 * as `EncounterSpec`/`EventDef`/`ShopDef`) — picking FROM one needs [RngState] rolling and a live
 * `RunState.rng` to advance, which is `:core:run`'s job, not `:core:model`'s.
 */
private fun <T> pickUniform(entries: List<T>, rng: RngState, ownerDescription: String): Pair<RngState, T> {
    require(entries.isNotEmpty()) { "$ownerDescription has no entries to pick from" }
    val (advanced, index) = rng.rollRange(0, entries.size - 1)
    return advanced to entries[index]
}

fun pickContent(pool: EncounterPool, rng: RngState): Pair<RngState, EncounterId> =
    pickUniform(pool.entries, rng, "pool for act ${pool.act}/${pool.kind}")

fun pickEvent(pool: EventPool, rng: RngState): Pair<RngState, EventId> =
    pickUniform(pool.entries, rng, "event pool for act ${pool.act}")

/**
 * The "enter an Event node" step, mirroring [resolveEncounterNode] — pool pick happens here, right
 * before the event's choices are shown, not at graph-generation time.
 *
 * docs/49-campaign-authoring.md: a [GraphNode.pinned] `Event` short-circuits the pool lookup
 * entirely, consuming zero `RngState` — see [PinnedContent]'s own doc comment.
 */
fun resolveEventNode(run: RunState, node: GraphNode, pools: List<EventPool>, cat: Catalog): Pair<EventDef, RngState> {
    (node.pinned as? PinnedContent.Event)?.let { return cat.eventDef(it.id) to run.rng }
    val pool = pools.firstOrNull { it.act == node.act } ?: error("no EventPool for act ${node.act}")
    val (advanced, id) = pickEvent(pool, run.rng)
    return cat.eventDef(id) to advanced
}

fun pickShop(pool: ShopPool, rng: RngState): Pair<RngState, ShopId> =
    pickUniform(pool.entries, rng, "shop pool for act ${pool.act}")

/**
 * docs/11-run-state.md: enemies "scaled by act and party size" — flat additive counts
 * (`EncounterScaling`'s own doc comment, not a curve/multiplier system), distributed round-robin
 * across the spec's existing `enemies` entries rather than inventing a new enemy type to hold them.
 */
fun applyScaling(spec: EncounterSpec, act: Int, partySize: Int): EncounterSpec {
    if (spec.enemies.isEmpty()) return spec
    val extra = spec.scaling.extraEnemiesPerAct * (act - 1) + spec.scaling.extraEnemiesPerPartySize * partySize
    if (extra <= 0) return spec
    val enemies = spec.enemies.toMutableList()
    repeat(extra) { i ->
        val idx = i % enemies.size
        enemies[idx] = enemies[idx].copy(count = enemies[idx].count + 1)
    }
    return spec.copy(enemies = enemies)
}

/**
 * The "enter a Combat/Elite/Boss node" step — pool pick + scaling happen here, right before
 * `startEncounter`, not at graph-generation time (docs/13's graph-shape-vs-content split).
 *
 * docs/49-campaign-authoring.md: a [GraphNode.pinned] `Encounter` short-circuits the pool lookup —
 * scaling still applies (an authored campaign node can still want act/party-size scaling; pinning
 * WHICH encounter and scaling ITS enemy count are independent concerns).
 */
fun resolveEncounterNode(run: RunState, node: GraphNode, pools: List<EncounterPool>, cat: Catalog): Pair<EncounterSpec, RngState> {
    (node.pinned as? PinnedContent.Encounter)?.let {
        return applyScaling(cat.encounterSpec(it.id), run.act, run.party.size) to run.rng
    }
    val pool = pools.firstOrNull { it.act == node.act && it.kind == node.type }
        ?: error("no EncounterPool for act ${node.act}/${node.type}")
    val (advanced, id) = pickContent(pool, run.rng)
    val scaled = applyScaling(cat.encounterSpec(id), run.act, run.party.size)
    return scaled to advanced
}
