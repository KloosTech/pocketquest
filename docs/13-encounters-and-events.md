# 13 — Encounters, events, and shops

The content model for [10-game-loop.md](10-game-loop.md)'s node graph — what a
node actually resolves to, once the player picks it. Node *identity/position*
lives in `RunState.graph`/`position` ([11-run-state.md](11-run-state.md));
this doc is what's behind each node.

## The graph is generated; its content is authored

Two separate things, easy to conflate:

- **Graph shape** — which nodes exist, how they connect, which paths diverge
  and merge — is procedurally generated per run from `run.rng`, seeded like
  everything else in this project. This is what makes every run look
  different while staying reproducible from its seed.
- **Node content** — which specific `EncounterSpec`/`EventDef`/`ShopDef` a
  given `Combat`/`Event`/`Shop` node resolves to — is picked randomly (also
  from `run.rng`) out of a hand-authored **pool**, not generated. This is the
  "groups of encounters which we can pick" idea: an author builds a pool of
  encounters (or events, or shop tables) in `:designer`, and the graph
  generator's job is just picking which pool entry lands on which node.

```kotlin
@Serializable
data class NodeGraph(
    val nodes: Map<NodeId, GraphNode>,
    val start: NodeId,
)

@Serializable
data class GraphNode(
    val id: NodeId,
    val act: Int,                         // 1..3, drives which pool this node draws from
    val type: NodeType,
    val next: List<NodeId>,               // empty only for the Act 3 Boss node
)

@Serializable
enum class NodeType { Combat, Elite, Event, Rest, Shop, Boss }
```

Generation algorithm (branch/merge shape, how many nodes per act, how many
paths) is not designed here — this doc only fixes the shapes `RunState`
serializes and what each `NodeType` resolves to. The generator is free to be
as simple as "a fixed number of parallel paths per act" for a first version.

## Content pools

```kotlin
@Serializable
data class EncounterPool(val act: Int, val kind: NodeType, val entries: List<EncounterId>)
// kind is Combat or Elite — the same pool shape, Elite pools just reference
// harder EncounterSpecs. Picking one entry at random from the act-matching
// pool is the entire "random encounter" mechanic; EncounterScaling's already-
// declared extraEnemiesPerPartySize/extraEnemiesPerAct (core/model/EncounterSpec.kt)
// finally gets a real caller here — scale the picked EncounterSpec by act and
// current party size before calling startEncounter.
```

Event and Shop pools follow the same `act -> List<Id>` shape (see below) —
not repeating the type for each.

## Events

Handcrafted text + choices, per your original ask (1-4 choices, each can
help or hurt).

```kotlin
@Serializable
data class EventDef(
    val id: EventId,
    val title: String,
    val body: String,                     // the flavor text
    val choices: List<EventChoice>,       // 1..4
)

/**
 * A choice either resolves unconditionally (`check` null — `outcomeText`/`effects`, the original
 * shape) or attempts an ability check: the party's best-scoring member on `EventCheck.ability`
 * rolls `d20 + abilityModifier(derivedScore) >= dc`, the exact formula `:core:rules`' RollSave
 * combat handler already uses (`AbilityScores.forAbility` is shared between the two, not
 * reimplemented). Each branch is independently optional — a choice can be "only ever helps" (empty
 * `failureEffects`) or "only ever hurts" (empty `successEffects`) while still being a real roll.
 */
@Serializable
data class EventChoice(
    val label: String,                    // the button text, e.g. "Search the altar"
    val check: EventCheck? = null,
    val outcomeText: String = "",         // used when check == null; shown before effects apply
    val effects: List<RunEffect> = emptyList(),
    val successText: String = "",
    val successEffects: List<RunEffect> = emptyList(),
    val failureText: String = "",
    val failureEffects: List<RunEffect> = emptyList(),
)

@Serializable
data class EventCheck(val ability: Ability, val dc: Int)

/** Deliberately a small sealed interface, same "type dropdown + inline fields"
 * authoring shape as EffectTemplate/Modifier in the combat layer — add a case
 * per new event mechanic as content actually needs it, don't pre-build a
 * general scripting language. */
@Serializable
sealed interface RunEffect {
    @Serializable data class GrantCurrency(val amount: Int) : RunEffect       // negative = a cost/toll
    @Serializable data class GrantItem(val item: ItemId) : RunEffect
    @Serializable data class LoseItem(val item: ItemId) : RunEffect
    @Serializable data class DamageParty(val amount: Int, val target: RunEffectTarget) : RunEffect
    @Serializable data class HealParty(val amount: Int, val target: RunEffectTarget) : RunEffect
    @Serializable data class ForceCombat(val encounter: EncounterId) : RunEffect  // an event escalating into a fight
}

@Serializable
enum class RunEffectTarget { WholeParty, RandomMember, LowestHpMember }
```

Events never touch the resolver or the effect stack — same reasoning
doc10 already gives: forcing a text-prompt outcome through the combat effect
system would be the same category error v1 made with implicit ordering.
`RunEffect` is resolved by `:core:run` directly against `RunState`/`PartyMember`,
the same narrow style as `applyConsumable` ([11-run-state.md](11-run-state.md)).

`ForceCombat` is the one effect that hands off to the encounter layer — it
calls the same `startEncounter` a `Combat` node does, just triggered from an
event's consequence instead of the player picking a Combat node directly.

Extensibility: this list is intentionally not exhaustive. `GrantStatus` (a
persistent run-scoped buff/curse) is an obvious next case once an event
actually needs one — not added speculatively.

## Shops

```kotlin
@Serializable
data class ShopDef(val act: Int, val stock: List<ShopEntry>)

@Serializable
data class ShopEntry(val item: ItemId, val price: Int)
```

A Shop node picks **N entries** (N configurable, not hardcoded — a run-
balance constant, tunable without a schema change) at random from the
act-matching `ShopDef.stock`, and offers them for the duration of that visit.
No restock/reroll in this pass — a shop's offered stock is fixed for that
visit; revisiting the same node (if the graph ever allows that) is out of
scope until it comes up.

Buying: `run.gold -= price`, item added to `RunState.inventory`, blocked if
gold or [carry capacity](#inventory-and-carry-capacity) is insufficient.

Selling: any item in `RunState.inventory` (looted or bought) can be sold back
for **50% of `ItemDef.basePrice`** (`core/model/Catalog.kt`, added Pass 0),
not `ShopEntry.price` — a looted item was never bought from a shop and has no
`ShopEntry` of its own, so `basePrice` is the only value that exists for
every item unconditionally. `sellValue(item) = (item.basePrice * 0.5).toInt()`.

## Inventory and carry capacity

```kotlin
@Serializable
data class Inventory(val items: List<ItemId>)
```

One shared `Inventory` per run (`RunState.inventory`), not per-Champion —
loot and shop purchases go into a common pool the party manages together,
matching `RunState`'s existing shape in [11-run-state.md](11-run-state.md).

Capacity is bound to the party's Strength: **the sum of every active party
member's STR score**, checked whenever an item would be added (loot pickup
or a shop purchase). Exceeding capacity blocks the pickup/purchase outright
rather than auto-dropping something — the player chooses what to sell/discard
to make room, same "no invented data, no silent side effects" pattern the
rest of this project follows.

```kotlin
fun carryCapacity(party: List<PartyMember>, cat: Catalog): Int =
    party.sumOf { cat.archetype(it.archetype).abilities.str }
```

Only `RunState.inventory` (unequipped items) counts against this — equipped
items already occupy per-Champion `Equipment` slots, a separate pool that
this capacity doesn't govern. Summing (not "highest STR carries it all")
rewards a balanced party, matching this project's general "no single stat
should trivialize a whole system" bias.

## What this doc doesn't cover

- The graph generation algorithm itself (branch counts, merge rules) —
  tunable content-side work, not a data-shape decision.
- `:designer` authoring UI for `EventDef`/`ShopDef`/pools — a future Designer
  tab, same deferred status as every other not-yet-built editor.
- Meta-shop (spending the permanent bank) — [12-progression.md](12-progression.md)
  flags it as open, distinct from the run-scoped `ShopDef` here.
