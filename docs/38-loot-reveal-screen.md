# 38 — Post-encounter loot reveal screen

After winning a battle, every chest the player opened during it (docs/37's `openedLoot`) gets
revealed one at a time on a new screen between the battle ending and the run map's node picker —
stacked chest icons, tap one to spin a slot-machine reel down to the item it actually rolled.

## Decided with the user before implementation

- **Roll semantics changed**: a container's `table` used to roll each `LootEntry` independently
  (0..N items possible). Switched to a **single weighted pick** — one item, or "nothing" if the
  weights don't sum to 1.0 — the only shape that maps cleanly onto "one reel, one result."
  `LootEntry.chance` is renamed `weight` to match (docs/37's only real content entry migrated).
- **Reveal, not risk**: the item was already secured the moment the chest was opened in combat.
  Tapping a chest plays the animation for its own sake; a **"Skip All"** shortcut instantly
  resolves every remaining chest with no animation, for players who don't want the flourish. Both
  paths grant the same items.
- **Capacity checked at reveal time, not roll time**: the item a chest *rolled* is decided once,
  deterministically, when the encounter finishes — but whether it *fits* depends on carry capacity
  at the moment each chest is individually revealed, which depends on reveal order (player-
  controlled) and whatever else has already been added. A chest that doesn't fit still shows the
  real item it rolled, tagged "bag full — lost," matching docs/13's existing capacity-blocks-the-
  pickup policy rather than a special case.

## Roll once at `finishEncounter`, grant per-reveal

Two separate moments, on purpose:

1. **`finishEncounter`** (`core/run/EncounterHandoff.kt`) still runs synchronously the instant
   combat ends, same as every other write-back (HP, mana, gold) — but for loot it now only *rolls*
   (`RngState.pickWeighted`, `core/rules/Dice.kt`, new), producing one `ItemId?` per opened
   placement, and stores the results as `RunState.pendingLootReveal: List<PendingLoot>` instead of
   merging anything into `run.inventory` directly. The roll is fixed at this point — re-composition,
   reordering reveals, or the run being persisted/reloaded mid-reveal can't change what a chest
   contains, only whether/when the player has acknowledged it.
2. **Granting** happens per-chest, via a new `revealLoot(run, at, cat): RunState` (`core/run`),
   called when that chest's reel animation finishes (or by "Skip All" folding it over every
   still-unrevealed entry in list order). This is where the capacity check actually runs, against
   `run.inventory.items.size` *as it stands at that moment* — so revealing three chests in a row can
   watch the bag fill up and the third one land on "lost," exactly as it would in real time.

```kotlin
data class PendingLoot(val at: GridPos, val loot: LootId, val item: ItemId?, val revealed: Boolean = false, val lost: Boolean = false)
```

`at` (the placement's `GridPos`, already unique per encounter) is the stable key the UI taps
against — not an index, so re-ordering or partial reveals never point at the wrong row.

## `LootEntry`: independent rolls → single weighted pick

```kotlin
data class LootEntry(val item: ItemId, val weight: Double = 1.0)
```

`RngState.pickWeighted(entries: List<LootEntry>): Pair<RngState, ItemId?>` (`core/rules/Dice.kt`,
alongside `chance`/`rollRange`) draws one `nextDouble()` and walks the entries' cumulative weight;
landing past the last entry's cumulative sum (i.e. the table's weights don't add up to 1.0) returns
`null` — "nothing," the table's own unclaimed probability mass, not an error. Weights aren't
normalized: a table author who wants a guaranteed drop sums to 1.0 exactly; one who wants a chance
of nothing leaves headroom. A table whose weights sum past 1.0 makes the tail entries partially or
fully unreachable — a content-authoring mistake `CatalogValidator` doesn't currently flag (same
"authoring can be silently wrong, not a crash" tier as most of this project's numeric fields).

## Reel population: honest about the real odds

The reel a chest's tap animates isn't a generic spinner — it's built from that container's own
`LootDef.table`, one symbol per entry (item icon + name) plus a synthetic "Nothing" symbol if the
weights leave headroom, so what's visually possible to land on matches what's actually possible to
roll. The symbol list repeats several times to give the reel scroll length, ending exactly on the
pre-decided result (`PendingLoot.item`) — the spin is choreographed around an already-known
destination, the same "animate toward a fixed known outcome" shape `Dice3D.kt`'s `DiceRoll` already
uses for attack/save rolls (`Animatable<Float>`, seeded jitter, `CubicBezierEasing` overshoot-then-
settle), just a vertical scroll instead of a 3D tumble. New file `ui/LootReel.kt`, mirroring
`Dice3D.kt`'s "one file owns one animated primitive" precedent.

## New content: `ItemDef.icon`

Items had no visual at all before this — `ItemPanel.kt`'s rows are plain text, and nothing else in
the engine ever needed to draw one. A slot-machine reel genuinely needs an icon per possible symbol,
so `ItemDef` gains `icon: String?` (a manifest sprite id, new `kind = "item"` — mirrors every other
sprite-id field's "just a manifest id, resolved through the same `AssetManifest.prop`/
`GameAssetManifest.prop` flat lookup" convention, no new accessor code). `ItemPanel.kt` gets an icon
picker identical in shape to `LootPanel.kt`'s closed/open sprite pickers (including its own
"Import…" button), just pointed at `AssetManifest.itemSprites` instead of `characterSprites`. No
icon set falls back to a text-only chip in both the reel and the editor — same missing-asset-is-
never-a-crash contract every other sprite lookup in this codebase already follows.

## `:ui`: a new screen between battle-end and the node picker

`RunApp.kt`'s `RunScreen` currently does, in order: check `run.outcome` (whole-run end) → check
`run.position in run.visited` (already-resolved node → `NodeChoiceScreen`) → dispatch on node type.
The new check goes FIRST, ahead of even the outcome check — the boss fight that ends the run is
still a combat node that can drop loot, and the player should see what they found before the
"Victory!" screen, not have it silently skipped because `run.outcome` was already set the same step
`pendingLootReveal` was populated:

```kotlin
if (run.pendingLootReveal.isNotEmpty()) {
    LootRevealScreen(run, catalog) { updated -> onRunUpdated(updated) }
    return
}
```

New `LootRevealScreen` (`ui/run/LootRevealScreen.kt`): the chests stacked top to bottom in
`pendingLootReveal` order, each an ink-framed row (closed/open `LootDef` sprite, matching the board's
own container art) — tap an unrevealed one to spin its reel and call `revealLoot`; an already-
revealed row just shows its result (item icon + name, "Nothing," or "<item> — bag full, lost").
"Skip All" calls a matching `skipAllLootReveals(run, cat)` (`core/run`, folds `revealLoot` over every
still-unrevealed entry). "Continue" only appears once every entry is `revealed`, and just clears the
list (`run.copy(pendingLootReveal = emptyList())`) — by then every grant/loss has already happened,
this is purely "stop showing the screen."

## Non-goals (v1)

- **No gold/HP summary on this screen.** Gold is still granted unconditionally and silently inside
  `finishEncounter`, unchanged — genuinely zero acknowledgment of it anywhere today, but that's a
  separate, un-asked-for gap this pass doesn't touch.
- **No reroll/reopen.** Once revealed, a `PendingLoot` stays revealed for the life of that
  `RunState` — there's no "spin again."
- **No `CatalogValidator` check for a `LootDef.table` whose weights sum past 1.0** — an authoring
  footgun, not an engine-safety one; can be added later without touching the roll/reveal mechanism.
