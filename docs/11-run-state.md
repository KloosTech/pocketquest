# 11 — Run state

The layer between Meta and an encounter. Everything in
[10-game-loop.md](10-game-loop.md) needs a home; this is it.

## Why this is not part of `GameState`

`GameState` is deliberately a *battle* state: entities on a grid, initiative,
RNG, an effect stack. It is created when an encounter starts and discarded when
it ends. A run outlives dozens of them and owns things a battle has no concept
of — a map graph, gold, an inventory, XP.

Putting run data into `GameState` would mean serializing the whole inventory
into every mid-combat autosave, and passing gold through the resolver. Keeping
them separate also keeps `:core:rules` free of anything that changes between
fights.

## Module

```
:core:run          RunState, node graph, event resolution, encounter handoff
  ├── :core:rules  starts and finishes encounters
  └── :core:content catalog
```

`:core:run` is pure like `:core:rules` — no coroutines, no IO, no clock. Its RNG
is a `RngState` of its own, so map generation and loot rolls are as reproducible
as combat. `:data` persists it; `:ui` drives it.

`:core:meta` (champions, idle accrual) is a **sibling**, not a parent. It is the
only place allowed to take wall-clock time as an input.

## Shape

```kotlin
@Serializable
data class RunState(
    val runId: RunId,
    val seed: Long,                       // the whole run is reproducible from this
    val rng: RngState,
    val act: Int,                         // 1..3
    val graph: NodeGraph,
    val position: NodeId,
    val visited: Set<NodeId>,
    val party: List<PartyMember>,
    val inventory: Inventory,
    val gold: Int,
    val encounter: EncounterHandle? = null,   // non-null only during a battle
    val outcome: RunOutcome? = null,          // set once, terminal
    val schemaVersion: Int = CURRENT_RUN_SCHEMA,
)

@Serializable
data class PartyMember(
    val memberId: MemberId,
    val name: String,
    val archetype: ArchetypeId,
    val progression: Progression,         // level, XP, chosen features
    val hp: Int,                          // persists across encounters
    val mana: Int,                        // refilled by finishEncounter; stored so a
                                          // mid-encounter save round-trips cleanly
    val equipment: Equipment,
    val controller: Controller,           // the per-character AI/manual toggle
    val condition: MemberCondition,       // Healthy | Downed
)

@Serializable
data class Progression(
    val level: Int,
    val xp: Int,
    val features: List<FeatureId>,        // each contributes Modifiers + granted actions
)
```

### HP and mana live here, not in `Entity`

This is the important structural point. During a battle the authority is
`Entity.health` / `Entity.resources`; between battles it is `PartyMember`. The
handoff below is the only place they are copied, in either direction, and it is
the only place that may do so.

AP is *not* on `PartyMember` — it is a per-turn budget with no meaning outside an
encounter.

## Levelling without breaking `Archetype`

`Archetype` is static per-kind data ([02-state-model.md](02-state-model.md)) and
a level-12 hero cannot be a fixed archetype. `Progression.features` resolves
this without touching that principle:

- A `FeatureId` resolves through the `Catalog` to a `FeatureDef` holding
  `modifiers: List<Modifier>` and `grantsActions: List<ActionId>`.
- Features are just another `ModifierSource`, so `stats()` keeps deriving
  everything and nothing new is stored.

Two engine changes fall out of this:

1. `Entity` needs a `grantedActions: List<ActionId>` alongside
   `Archetype.actions`, since the archetype is no longer the only action source.
2. `stats()` must include feature modifiers in its fixed source order —
   after archetype innate, before equipment.

Both are listed in [17-engine-gaps.md](17-engine-gaps.md).

## The encounter handoff

```kotlin
fun startEncounter(run: RunState, spec: EncounterSpec, cat: Catalog): RunState
fun finishEncounter(run: RunState, final: GameState, cat: Catalog): RunState
```

**Start** builds a `GameState` from the run:

1. One `Entity` per non-downed `PartyMember`, `faction = PLAYER`, `controller`
   copied straight from the member (this is how the toggle reaches the engine).
2. `health.current = member.hp`, `resources.mana = member.mana`, `ap` left to be
   set by the first turn reset.
3. Enemies instantiated from the `EncounterSpec`, scaled by act and party size.
4. Initiative rolled from `run.rng`, so the same run seed yields the same order.
5. `EncounterHandle` stores the `Resolver` and the `MemberId ↔ EntityId` mapping.

**Finish** writes back:

1. `member.hp = entity.health.current`; `member.mana` is refilled to
   `stats.maxMana` — mana is a per-encounter pool
   ([10-game-loop.md](10-game-loop.md)).
2. Downed members that survived get up at 1 HP
   ([10-game-loop.md](10-game-loop.md)).
3. XP awarded and level-ups queued (resolved at the next `Rest`, so a level-up
   never interrupts a fight).
4. Loot rolled from `run.rng` into the inventory.
5. `encounter = null`.

Mana is refilled here rather than carried over, and this is the *only* place it
refills. Until `ResourcesReset` stops restoring mana every turn, that
distinction does not exist and no spell can be rationed across a fight.

## Between-encounter actions

Healing skills are combat-only ([10-game-loop.md](10-game-loop.md)), because
mana refills at the end of every fight and an out-of-combat cast would be free.
That collapses this section to one case: **consumables**.

`:core:run` applies a potion directly against a `PartyMember` — restore HP, clear
a status, cap at the derived maximum. No `GameState`, no initiative, no grid, no
resolver. A narrow `applyConsumable(member, itemDef, cat)` covers it.

This is worth noticing as a win: the alternative designs all required running
`:core:rules`' action machinery outside an encounter, and `canPerform` gates on
`state.turn`, which does not exist between battles. Making heals combat-only
removed the need for a parallel validation path entirely.

`Rest` nodes are the other recovery route and are a run-layer operation, not an
action: restore a fixed fraction of HP to every member, resolve queued level-ups.

## Persistence

Reuses the machinery from [06-persistence.md](06-persistence.md), one row per
run:

```kotlin
@Entity(tableName = "run_slot")
data class RunSlotRow(
    @PrimaryKey val runId: String,
    val schemaVersion: Int,
    val updatedAt: Long,
    val act: Int,
    val partySummary: String,     // for the resume card, without parsing the blob
    val snapshot: ByteArray,      // serialized RunState, including any live Resolver
)
```

The `Resolver` rides inside the `RunState` blob rather than in its own row —
they are always saved and loaded together, and splitting them creates the
possibility of a run and its encounter disagreeing.

`partySummary` is denormalized on purpose: the resume screen must render without
deserializing a full run.

Snapshot versioning follows doc 06 exactly, with a second, independent chain for
`RunState` (`CURRENT_RUN_SCHEMA`). Combat and run shapes will not change in
lockstep.

Autosave points: node transition, turn end, `AwaitingInput`, `onStop()`.

## Resume

The invariant: **resuming never replays animation.** Load, `settle()`, show
state.

Resume needs to answer three questions in one screen: who is in the party and
how hurt are they, where are we, what is the next decision. That is the recap
card, specified in [14-ui-shell.md](14-ui-shell.md).

Mid-encounter resume additionally restores `pending` — if the app died while a
reaction prompt was open, the prompt comes back. That is the whole reason
`Resolver` is a serializable data object.

## Invariants

Checked in tests and debug builds, in the same style as
[02-state-model.md](02-state-model.md):

1. `party` is non-empty and has at most 3 members.
2. Every `MemberId` is unique within the run.
3. `hp in 0..stats.maxHp` and `mana in 0..stats.maxMana` for every member.
4. `condition == Downed` if and only if `hp == 0`.
5. `position` exists in `graph`, and every node in `visited` does too.
6. `encounter != null` implies every non-downed member has a mapped `EntityId`.
7. `outcome != null` implies `encounter == null` — a finished run has no live battle.
8. During an encounter, `member.hp` is *stale by design*; the mapped `Entity` is
   authoritative. Nothing outside `finishEncounter` may write `member.hp` while
   `encounter != null`.

Invariant 8 is the one most likely to be violated by well-meaning UI code
reading `PartyMember.hp` to draw a health bar mid-fight. The party bar during
combat must read the `GameState`, not the run.

## Champions handoff

On `RunOutcome.Success`, the qualifying member(s) are converted to a
`ChampionRecord` in `:core:meta`: final level, features, equipment, run summary.
This is a one-way copy — a champion is never a live `PartyMember` again; sending
one on a background mission is a Meta-layer operation over wall-clock time.

Whether all surviving members qualify or only the founding character is still
open ([10-game-loop.md](10-game-loop.md)).
