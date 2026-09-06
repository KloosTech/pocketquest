package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
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

/**
 * docs/49-campaign-authoring.md: [GraphNode]/[NodeGraph] moved here from `:core:run`'s
 * `RunState.kt` — a hand-authored [CampaignDef] needs to live in [Catalog] (so `:designer` can
 * author/save/load it exactly like every other content type), and [Catalog] is `:core:model`,
 * which cannot depend on `:core:run`. Same reasoning [NodeType] above already documents for
 * exactly this situation. `:core:run`'s `generateGraph`/`RngState`-consuming pick functions
 * (`Pools.kt`/`Shop.kt` in that module) still own all the *behavior* around these shapes — this is
 * pure data, same split `NodeType` already has.
 */
@Serializable
data class NodeGraph(
    val nodes: Map<NodeId, GraphNode>,
    val start: NodeId,
)

@Serializable
data class GraphNode(
    val id: NodeId,
    val act: Int,
    val type: NodeType,
    /** Empty only for the Act 3 Boss node — the run's one and only success condition. */
    val next: List<NodeId> = emptyList(),
    /**
     * docs/49-campaign-authoring.md: null (default) is every existing/procedurally-generated node —
     * behavior is completely unchanged, `:core:run`'s `Pools.kt`/`Shop.kt` resolvers still roll from
     * a pool. Non-null short-circuits that roll entirely: the resolver returns this exact content,
     * consuming zero `RngState`. A node's `pinned` case is expected to match its own [type] (a
     * `Combat` node carrying [PinnedContent.Shop] is an authoring mistake `:designer` should
     * prevent at save time) — the resolvers don't defensively guard against a mismatch.
     */
    val pinned: PinnedContent? = null,
)

/** docs/49-campaign-authoring.md: what a hand-authored [GraphNode] is pinned to, one case per [NodeType] that ever resolves content — [NodeType.Rest] needs none, matching the existing pool resolvers. */
@Serializable
sealed interface PinnedContent {
    @Serializable @SerialName("encounter") data class Encounter(val id: EncounterId) : PinnedContent
    @Serializable @SerialName("event") data class Event(val id: EventId) : PinnedContent
    @Serializable @SerialName("shop") data class Shop(val id: ShopId) : PinnedContent
}

/** docs/49-campaign-authoring.md: a named, reusable, hand-authored [NodeGraph] — `Catalog.campaigns` sits alongside `encounterPools`/`eventPools`/`shopPools` as "hand-authored, not generated" content, but produces a whole graph directly (via `:core:run`'s `createCampaignRun`) instead of a pool a generated graph's node draws from. */
@Serializable
data class CampaignDef(
    val id: CampaignId,
    val name: String,
    val nodes: List<GraphNode>,
    val start: NodeId,
)
