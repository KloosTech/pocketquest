# 49 — Campaign authoring (pinned nodes, not just pool draws)

docs/Campain_1's 16-location progression is a hand-authored story, not a
random dungeon crawl — each beat is a specific named map with a specific
narrative bridge to the next. `:core:run`'s existing graph system can't
express that today: `generateGraph` (`GraphGeneration.kt`) is the *only*
producer of a `NodeGraph`, its shape is random (weighted node types, fixed
lane count), and every node's actual content is a uniform-random pool draw
(`Pools.kt`'s `resolveEncounterNode`/`resolveEventNode`/`resolveShopNode`) —
there's no way to say "this node is always `EncounterId("mud-pit")`," and no
`:designer` screen authors a graph's *shape* at all (`PoolsPanel.kt` only
authors pool *contents*).

## Decided with the user before implementation

- **Pin content, allow branching.** A hand-authored node names one specific
  `EncounterId`/`EventId`/`ShopId` directly — no pool roll, no RNG consumed.
  Nodes can still fan out to multiple `next` choices (the player picks a
  path, same as a procedural run already does) — branching stays available,
  it's just that every branch points at hand-picked content instead of a
  pool.
- Scope: this is additive to the existing procedural system, not a
  replacement — a random run still works exactly as it does today.

## `GraphNode` gains optional pinned content

```kotlin
// RunState.kt
@Serializable
sealed interface PinnedContent {
    @Serializable data class Encounter(val id: EncounterId) : PinnedContent
    @Serializable data class Event(val id: EventId) : PinnedContent
    @Serializable data class Shop(val id: ShopId) : PinnedContent
}

data class GraphNode(
    val id: NodeId,
    val act: Int,
    val type: NodeType,
    val next: List<NodeId> = emptyList(),
    val pinned: PinnedContent? = null, // NEW
)
```

`null` (default) is every existing/generated node — behavior is completely
unchanged, this is purely additive. A `Rest` node has no `PinnedContent`
case because it already needs none (nothing in `Pools.kt` resolves content
for `Rest` today).

### `Pools.kt`: pinned short-circuits the pool lookup

Each resolver gets one guard clause ahead of its existing pool logic:

```kotlin
fun resolveEncounterNode(run: RunState, node: GraphNode, pools: List<EncounterPool>, cat: Catalog): Pair<EncounterSpec, RngState> {
    (node.pinned as? PinnedContent.Encounter)?.let { return cat.encounterSpec(it.id) to run.rng }
    // ...existing pool lookup, unchanged...
}
```

Same shape for `resolveEventNode`/`resolveShopNode` against their own
`PinnedContent` case. `run.rng` passes through untouched — a pinned node
consumes zero randomness, so a save/replay of a hand-authored campaign is
byte-identical every time by construction, not just by seed. A node whose
`type` doesn't match its own `pinned` case (a `Combat` node carrying
`PinnedContent.Shop`) is an authoring mistake `:designer` should prevent at
save time, not something the resolvers need to guard against defensively.

## `CampaignDef`: a named, reusable authored graph

```kotlin
// Pools.kt (sits next to EncounterPool/EventPool/ShopPool — same "hand-authored, not generated" category)
@JvmInline @Serializable value class CampaignId(val raw: String)

@Serializable
data class CampaignDef(
    val id: CampaignId,
    val name: String,
    val nodes: List<GraphNode>,
    val start: NodeId,
)
```

`Catalog.campaigns: List<CampaignDef> = emptyList()` — a `Catalog` can hold
several named campaigns (this game's, a future DLC's), same plural-list
shape `encounterPools` already has.

### Starting a campaign run

`RunState.kt` gains a sibling to `createRun` rather than overloading it —
a campaign run needs no graph generation step at all:

```kotlin
fun createCampaignRun(runId: RunId, seed: Long, party: List<PartyMember>, campaign: CampaignDef): RunState {
    val graph = NodeGraph(nodes = campaign.nodes.associateBy { it.id }, start = campaign.start)
    return RunState(runId = runId, seed = seed, rng = RngState(seed = seed), act = graph.nodes.getValue(graph.start).act, graph = graph, position = graph.start, party = party)
}
```

`RunState.rng` starts fresh at `seed` (unadvanced) rather than post-graph-
generation like `createRun`'s does — there was no generation roll to
advance past. It still exists and still gets consumed normally by anything
inside an encounter (dice rolls, AI wander's version-seeded pick, etc.) —
only the *graph-shape* roll is absent, matching "pinned content consumes no
randomness" above.

### Nothing changes downstream

`ui/run/RunApp.kt`'s `NodeChoiceScreen` (the "pick which next node" screen)
already operates purely on `GraphNode.next` — confirmed by reading it, it
has no idea whether the graph came from `generateGraph` or was hand-authored,
and needs no change at all. Same for `resolveEncounterNode`'s callers,
`finishEncounter`, loot reveal, save/load — a hand-authored campaign is a
`NodeGraph` like any other from every consumer's point of view except
`Pools.kt`'s three resolvers.

## `:designer` authoring: new `Campaigns` tab

A new `DesignerTab.Campaigns` entry (`App.kt`), backed by a new
`CampaignPanel.kt`. Plain list-based CRUD, matching every other content
tab's style (`PoolsPanel`/`LootPanel`) rather than a spatial node-graph
canvas (`MapEditorPanel` is the one spatial exception in this app, for a
reason that doesn't apply here — a campaign graph has no natural 2D
geometry to place nodes onto):

- A campaign picker (add/rename/delete `CampaignDef`s, same pattern
  `EncounterPanel` uses for `EncounterSpec`s).
- Per campaign, a list of nodes: `id` (text), `act` (stepper), `type`
  (dropdown over `NodeType`), a content picker scoped to `type` (a dropdown
  over `cat.encounters`/`cat.events`/`cat.shops` filtered to the matching
  kind — greyed out entirely for `Rest`), and a multi-select of `next` node
  ids (checkboxes over every other node in this campaign — self-selection
  rejected, same "can't point at yourself" guard a real graph needs).
- `start` — a single-select radio over the node list.

Validation surfaced inline (not a blocking save-time error dialog, matching
this app's existing "author sees problems live" style elsewhere): a node
whose `type` has no matching `pinned` content picked yet, an unreachable
node (nothing's `next` points at it and it isn't `start`), or a `next`
target that doesn't exist in this campaign's own node list.

## Non-goals (v1)

- No visual node-graph canvas — plain list editing, per above.
- No mixing pinned and pool-drawn nodes within the *same* campaign's node
  (a node is one or the other, decided by whether `pinned` is set) — an
  author who wants "mostly fixed, occasionally random" content on one beat
  can still point that node's pool at a single-entry pool, which is already
  expressible with zero new mechanism.
- No campaign-level branching-choice consequences (a choice unlocking a
  different ending, flags carried across nodes) — `next` is purely "which
  node becomes current," nothing more, same as the procedural graph today.
- No UI to convert a procedurally-generated run into an authored
  `CampaignDef` or vice versa — the two stay separate entry points
  (`createRun` vs `createCampaignRun`).
