package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * docs/13-encounters-and-events.md's node-graph shape. Lives here (not `:core:run`, where
 * `NodeGraph`/`GraphNode`/`RunState` itself live) so `EncounterPool.kind` — and `Catalog`'s pool
 * lists below — can reference it: `Catalog` is `:core:model`, and `:core:run` depends on
 * `:core:model`, never the reverse.
 */
@Serializable
enum class NodeType { Combat, Elite, Event, Rest, Shop, Boss }

/**
 * docs/13-encounters-and-events.md's Content pools section — hand-authored, not generated. A
 * `Catalog` entry (`Catalog.encounterPools`) so `:designer` can author per-act/per-kind pools
 * instead of the graph generator inventing content; `:core:run`'s `resolveEncounterNode` (not
 * defined here — it needs `RunState`/`RngState` rolling, which this module has no notion of) is
 * the only place a pool actually gets picked from.
 */
@Serializable
data class EncounterPool(val act: Int, val kind: NodeType, val entries: List<EncounterId>)

/** Event pools follow the same `act -> List<Id>` shape as [EncounterPool], minus `kind` — every Event node draws from the same act-matching pool. */
@Serializable
data class EventPool(val act: Int, val entries: List<EventId>)

/** Shop pools follow the same `act -> List<Id>` shape as [EventPool]. */
@Serializable
data class ShopPool(val act: Int, val entries: List<ShopId>)
