# 37 — Lootable containers (rarity-tier loot spawns)

Replaces the old per-encounter `loot: List<LootEntry>` (an item+chance list, auto-granted the
instant combat ends, with no world presence at all) with physical, placeable loot containers —
authored once as reusable `LootDef` content, placed on a map at rarity-tier spawn tiles the same
way enemies are, and opened by walking onto them, same as a docs/36 trigger.

## Decided with the user before implementation

- **Opened by walking onto it**, no separate interact button — same input model docs/36's triggers
  already established, no new UX pattern.
- **Lootable during both exploration and combat** — a chest in a room the party hasn't cleared yet
  is reachable once the fight moves there, same reasoning docs/36 gave for firing triggers mid-combat.
- **Fully replaces** the old auto-granted `EncounterSpec.loot` list — one coherent loot system, not
  two overlapping ones.
- **Unopened loot is lost.** No safety-net grant at `finishEncounter` for a chest the player never
  reached — walking past it is a real, intended cost, not a bug to patch over.

## Content shape: `LootDef`, a reusable container definition

```kotlin
@JvmInline @Serializable value class LootId(val raw: String)

@Serializable
data class LootDef(
    val id: LootId,
    val name: String = "",
    val closedSprite: String? = null,
    val openSprite: String? = null,
    val table: List<LootEntry> = emptyList(),
)
```

`table` reuses `LootEntry(item: ItemId, chance: Double)` verbatim — the exact same independent-
Bernoulli-roll shape the old `EncounterSpec.loot` already used, just now owned by a `LootDef`
instead of an `EncounterSpec`, so the same container (e.g. "battered strongbox") can be reused
across many encounters/maps rather than re-authoring its contents every time. `closedSprite`/
`openSprite` are manifest prop ids (docs/23), resolved through the exact same `AssetManifest.prop`/
`GameAssetManifest.prop` lookup `PropPlacement` rendering already uses — no new asset-pipeline
concept.

New `Catalog.loot: Map<LootId, LootDef>` + `Catalog.lootDef(id)` accessor, following the exact
boilerplate every other catalog type (`ItemDef`/`StatusDef`/...) already uses.

## Placement: 4 new `SpawnRole` values, zero changes to the map schema

```kotlin
enum class SpawnRole { Party, Enemy, Elite, Boss, Objective, LootCommon, LootRare, LootEpic, LootLegendary }
```

`SpawnZone`/`BattleMapDef`/`BattleMap` need no changes at all — a loot spawn tile is authored and
stored exactly like an Enemy/Elite/Boss spawn tile already is, through the same `SpawnZone(role,
tiles)` shape. `:designer`'s Map editor toolbar already iterates `SpawnRole.entries` generically for
its spawn-tool swatches, so the 4 new roles appear there for free; only the token-drawing `when` and
the tooltip `when` (both already `SpawnRole`-exhaustive) need a new branch each.

## Encounter authoring: `EncounterSpec.lootSpawns` replaces `EncounterSpec.loot`

```kotlin
data class LootSpawn(val loot: LootId, val role: SpawnRole, val count: Int = 1)
```

Exactly mirrors `EnemySpawn(archetype, role, count)` — "this many copies of this def, filling this
role's pooled tiles." `role` stays typed as the full `SpawnRole` enum rather than a narrower
loot-only type, same looseness `EnemySpawn.role` already has (nothing stops authoring
`EnemySpawn(archetype, role = SpawnRole.Party)` today either) — not a new problem this introduces.
`CatalogValidator`'s existing "needed vs available tiles per role" check (already generic over
`SpawnRole`) folds `lootSpawns` into the same `neededByRole` computation `enemies` already feeds,
rather than a separate parallel check.

## Runtime: NOT an `Entity` — a parallel list + a one-shot set, mirroring docs/36's triggers exactly

A loot container has no HP, no turn, no faction, doesn't fight — folding it into `entities` would
mean every combat loop that assumes "every Entity is a combatant" (targeting, AI, turn order) now
has to defensively skip it. Same reasoning docs/36 already established for triggers not being
entities either. Instead:

- `GameState.lootPlacements: List<LootPlacement> = emptyList()` — `LootPlacement(at: GridPos, loot:
  LootId)`, resolved ONCE in `startEncounter`'s `buildEncounterState` (`core/rules/content/
  StartEncounter.kt`), the exact same "pop `count` tiles off this role's pooled list" loop
  `EnemySpawn` already runs, just producing a placement instead of an `Entity`.
- `GameState.openedLoot: Set<GridPos> = emptySet()` — monotonic, one-shot, same shape as
  `firedTriggers`. A fresh `GameState` (new encounter) naturally starts empty; no reset logic needed.
- `fun openLootIfAny(state: GameState, entityId: EntityId, at: GridPos): Pair<GameState,
  LootPlacement>?` (`core/rules/Loot.kt`) — player-controlled check, unopened check, marks opened.
  Pure `GameState` transform, no `Effect`/resolver involvement needed: opening a chest doesn't touch
  combat state (HP, statuses, positions) at all, it just flips a visibility bit for rendering and
  records that this placement is now collectible. Hooked into the exact same two call sites
  `fireTriggerIfAny` already is: `exploreMoveTo`'s per-hop loop (`:ui` App.kt) and `moveAlong`'s
  per-hop handler (`Handlers.kt`) — a `GameEvent.LootOpened(at, loot)` gives it a log line and a
  ReactionTriggerKind case, same as every other event.

**Why not reuse `Effect`/`Trigger` verbatim?** A trigger's effects run through the combat resolver
because they can deal damage, heal, spawn enemies — real combat-state mutations `Effect` already
models. Granting an item goes into `RunState.inventory`, which lives in `:core:run`, entirely
outside combat `GameState` — there is no existing `Effect` case for it and adding one would blur a
layer boundary docs/13's `RunEffect.GrantItem` already owns correctly. Deferred to `finishEncounter`
instead (below) rather than granted the instant a chest opens.

## Granting: resolved at `finishEncounter`, gated on `openedLoot`

`core/run/EncounterHandoff.kt`'s `finishEncounter` replaces its `for (entry in handle.spec.loot)`
loop with:

```kotlin
for (placement in final.lootPlacements) {
    if (placement.at !in final.openedLoot) continue
    for (entry in cat.lootDef(placement.loot).table) { /* same Bernoulli roll + capacity check as before */ }
}
```

Same roll/capacity logic as today, just gated per-container on whether it was actually opened —
this is the entire mechanism behind "unopened loot is lost": a chest that's never in `openedLoot`
never contributes to `lootedItems` at all.

## Rendering: closed/open sprite, same pattern as a `Prop`

`:ui`'s Board draws each `state.lootPlacements` entry using `catalog.lootDef(placement.loot)`'s
`closedSprite` normally, or `openSprite` once `placement.at` is in `state.openedLoot` — resolved
through the same `AssetManifest.prop`/`SpriteLoader.load` call `PropPlacement` rendering already
uses. No animation beat needed (a sprite swap is a pure function of `openedLoot` membership, redrawn
on the next recomposition same as any other state-driven visual) — the `GameEvent.LootOpened` log
line is the only player feedback beyond the sprite itself.

## `:designer` authoring

- **New "Loot" tab** (`LootPanel.kt`, registered in `DesignerTab`): a `LootDef` list + editor —
  name, closed/open sprite pickers (same `InkSelect` over `AssetManifest.placeableProps` the Map
  editor's Prop tool already uses), and a loot table editor reusing `LootEntryRow` verbatim (moved
  out of `EncounterPanel.kt`, unchanged) — the exact row UI the old per-encounter loot list already
  used, just attached to a `LootDef` now instead of an `EncounterSpec`.
- **Encounter tab**: the old "LOOT" section (item + chance rows) is replaced by a "LOOT SPAWNS"
  section — `LootSpawn` rows (`InkSelect` over `catalog.loot.values` + `InkSelect` restricted to the
  4 `Loot*` roles + a count stepper), mirroring `EnemySpawnRow` exactly.
- **Map editor**: no new tool/UX — the 4 new roles ride the existing generic `SpawnRole.entries`
  toolbar loop. Only `drawSpawnToken`'s and `descriptionFor(role)`'s `when` blocks gain 4 new
  branches (a small square token, color-coded by rarity — common/rare/epic/legendary — distinct from
  Objective's orange diamond).

## Non-goals (v1)

- No pooled/random choice of loot entity per spawn tile — `LootSpawn` names one fixed `LootId` for
  `count` tiles, exactly like `EnemySpawn` names one fixed archetype. A random-pick-from-a-pool
  layer is a trivial follow-up (mirroring `EncounterPool`) if content ever wants it.
- No re-locking/respawning a looted container — `openedLoot` only ever grows, same as every other
  monotonic `GameState` set.
- No mid-combat item usage from a just-opened chest — the item only actually enters
  `run.inventory` at `finishEncounter`, same timing every other loot source (gold, the old
  auto-grant list) already used.
