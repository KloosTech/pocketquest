# 47 — Inventory & equipment screen

No UI touches `Equipment` at all today. `core/rules/equipment/Equip.kt`'s `canEquip`/`equip`/
`unequip` are real, tested, and correct — attunement cap, two-handed pairing, slot validation — but
have zero callers outside their own test file. `RunState.inventory` is a real, working, run-scoped
bag already fed by shops/loot/events, but nothing ever moves an item out of it into a slot. This
closes that gap: a screen to equip/unequip/use items, reachable from both the in-run battle menu and
a new Hub menu, plus the one new piece of state needed to make the Hub side make sense at all — items
don't currently survive between runs.

## Decided with the user before implementation

- **Two entry points**: the existing in-run battle-menu dropdown (`App.kt`'s Battle Log/Settings
  menu) gets a third item, and the Hub — which has no context menu at all today — gets one added,
  mirroring the same pattern, both opening the same `InventoryScreen`.
- **Mid-combat equip changes apply next fight, not this one.** A battle's `Entity` is a one-time
  snapshot of `PartyMember.equipment` taken at `startEncounter` (`PartyMember.toEntity`) — nothing
  re-syncs it mid-fight. Equipping/unequipping writes `PartyMember.equipment` (mid-run) or
  `ChampionRecord.equipment` (Hub) same as today's data always has; the live battle just doesn't see
  it until the next `startEncounter`. No new live-`GameState`-patching code.
- **Item use is single-use, no charges.** `ItemDef` gains `useEffects: List<RunEffect>` — using an
  item applies those effects (`applyRunEffect`, same function events already use) and removes one
  copy from wherever it came from. `ItemInstance.charges`/`ActionDef.Cost.charges` stay exactly as
  dangling as they are today — a multi-charge/in-combat-cost system is real scope, deliberately
  deferred.
- **Use is disabled entirely while `state.inCombat`.** `RunEffect` operates on `RunState`/
  `PartyMember.hp`, which is stale-by-design mid-encounter (the live `GameState`/`Entity` is
  authoritative there — same invariant `atFullHealth`/`finishEncounter` already respect). The
  battle-menu `InventoryScreen` still allows equip/unequip/view mid-combat; only its Use button is
  gated off. Consuming an item pre-battle or post-battle works normally.
- **New `MetaState.stash: Inventory`** — a between-runs item bag. `RunState.inventory` is run-scoped
  and today simply evaporates at run end (nothing ever reads it after `finishEncounter`); the Hub
  entry point is meaningless without somewhere persistent to equip from. Synced from
  `run.inventory` into the stash **only on `RunOutcome.Success`**, inside the same
  `resolveRunOutcome` branch that already writes `ChampionRecord.equipment` back — not a new rule,
  the third instance of one already-established one: `RunState.gold`'s own doc comment
  (`RunState.kt:51`) already says "deposited into `:core:meta`'s permanent bank on
  `RunOutcome.Success`, forfeited on `Failure`," and equipment's write-back (`RunResolution.kt:15-29`)
  follows the identical shape. The stash is the same rule applied to the one remaining un-banked
  pool. **No carry-capacity limit on the stash** — home storage is unbounded; only the run-scoped
  `Inventory` keeps the existing STR-summed cap (`carryCapacity`, `Shop.kt:18-19`), since a fixed
  roster-wide STR number doesn't mean anything before a party for the next run is even chosen.
- **Two pools, no runtime crossover.** Mid-run, equip/unequip/use only ever touches
  `RunState.inventory` + `PartyMember.equipment`. At the Hub, only `MetaState.stash` +
  `ChampionRecord.equipment`. A run never reads the stash mid-run — only the end-of-run sync above
  moves items from `run.inventory` into it, one-way, success-gated. Nothing carries the *other*
  direction either: starting a new run does not seed `RunState.inventory` from the stash — only
  already-*equipped* gear (already embedded in each `ChampionRecord`) comes along; stash items must
  be equipped before embarking if you want them on the run.

## New model: `MetaState.stash`

`core/meta/.../MetaState.kt` — add one field, reusing `Inventory` (`core/run/RunState.kt:136`,
`data class Inventory(val items: List<ItemId> = emptyList())`) as-is rather than inventing a second
shape:

```kotlin
data class MetaState(
    val roster: Map<ChampionId, ChampionRecord> = emptyMap(),
    val bank: Int = 0,
    val stash: Inventory = Inventory(),
    val unlocks: Set<Unlock> = emptySet(),
    val schemaVersion: Int = CURRENT_META_SCHEMA,
)
```

Default-valued, so it decodes fine from existing saves with no migration step — same reasoning
already established for `ChampionRecord.abilityBonuses`. `Inventory` currently lives in `core/run`;
either move it to a lower module both `core/run` and `core/meta` can depend on (`core/model`, next to
`ItemId`), or duplicate the one-field shape into `core/meta` the same deliberate-duplication call
already made for `GameAssetManifest`/`AssetManifest` — moving it is the less surprising option
(`core/meta` doesn't currently depend on `core/run`, and shouldn't start to just for this one type).

`resolveRunOutcome` (`core/progression/RunResolution.kt:15-29`), `RunOutcome.Success` branch — add
one line alongside the existing per-champion equipment write-back:

```kotlin
meta.copy(roster = roster, stash = meta.stash.copy(items = meta.stash.items + run.inventory.items))
```

## `core/run`: RunState/MetaState-level equip wrapper

`Equip.kt`'s functions operate on `Entity`, not `PartyMember`/`ChampionRecord` — nothing currently
bridges that gap (mirrors the existing `atFullHealth`/`toEntity` pattern already used to borrow
`Entity`-level `stats()` for a `PartyMember` without a real battle). Two new functions in `core/run`,
one per pool, same shape:

```kotlin
sealed interface EquipmentTransactionRejection {
    data class SlotRejected(val reasons: List<EquipRejection>) : EquipmentTransactionRejection
    data class CarryCapacityExceeded(val capacity: Int, val current: Int) : EquipmentTransactionRejection
}

fun equipFromInventory(run: RunState, memberId: MemberId, slot: Slot, item: ItemId, cat: Catalog): Result<RunState, EquipmentTransactionRejection>
fun unequipToInventory(run: RunState, memberId: MemberId, slot: Slot, cat: Catalog): Result<RunState, EquipmentTransactionRejection>
```

(`Result`-shaped return mirroring `PartyFormationResult`'s `Formed`/`Rejected` pattern, not
`kotlin.Result` — same house style.)

`equipFromInventory`: find the `PartyMember`, build a throwaway `Entity` via `member.toEntity(cat)`
(existing function, `PartyMemberEntity.kt:23`), call `equip(entity, slot, ItemInstance(item), cat)` —
`Rejected` maps straight to `SlotRejected`; `Equipped` takes `.entity.equipment` back out, writes it
onto the matching `PartyMember.copy(equipment = ...)` in `run.party`, and removes one `item` from
`run.inventory.items`.

`unequipToInventory`: same `toEntity`/`unequip` round-trip (unconditional, no `EquipRejection` of its
own per `Equip.kt:76-77`), but checks `carryCapacity(run.party, cat)` against
`run.inventory.items.size` **before** committing — `CarryCapacityExceeded` if the bag's already full,
mirroring `canBuy`'s existing check (`Shop.kt:34-40`) exactly, since "does this item fit in the run's
bag" is the identical question a purchase already asks.

A third pair, `equipFromStash`/`unequipToStash` (`core/meta` or `core/progression`, wherever
`ChampionRecord` mutation already lives), repeats the exact same shape one level up: builds a
throwaway `Entity` from `ChampionRecord.archetype` + `.equipment` (id/pos/health/actor irrelevant to
`equip()`'s own logic), moves the item between `ChampionRecord.equipment` and `MetaState.stash`. No
capacity check on this side (stash is uncapped, per the earlier decision).

## `:ui`: `InventoryScreen`

New file, `ui/src/commonMain/kotlin/de/jackbeback/pocketquest/ui/run/InventoryScreen.kt` (mid-run
usage) — or a single screen taking either a `RunState` or `MetaState`+roster depending on entry
point; simplest is one composable with two thin call sites (`InventoryScreen(members, pool, ...)`
where `members`/`pool` are already resolved to the right shape by the caller, so the screen itself
never needs to know which pool it's looking at).

Layout, plain `InkButton`-list style matching every other screen in this codebase (no drag-and-drop —
"the plain, functional, not pretty, shell" `RunApp.kt`'s own doc comment already commits to):

1. **Party member row** at the top — one button per member (portrait + name if a sprite's loaded,
   text-only otherwise, same missing-asset-is-never-a-crash contract as everywhere else), selects
   whose equipment is shown below. Mid-run: `run.party` (up to 3). Hub: the full roster regardless of
   `ChampionStatus` — gear management isn't run-formation, no reason to filter.
2. **7 slot rows** for the selected member (`Slot.entries` — MainHand, OffHand, Armor, Helm, Ring1,
   Ring2, Amulet), each showing the equipped item's name (or "— empty —") and an "Unequip" button
   when occupied. Tapping an empty/occupied slot opens a picker of pool items whose
   `ItemDef.validSlots` contains (or is empty = unconstrained for) that slot — tapping one calls
   `equipFromInventory`/`equipFromStash`; a `SlotRejected` result surfaces its `EquipRejection`
   reasons inline (attunement cap, two-handed conflict, wrong slot) as plain text, same rejection-
   reasons-as-a-list convention `PartyFormationResult.Rejected`/`ShopRejection` already use.
3. **Pool list** below — every item in the relevant `Inventory.items` not currently equipped
   anywhere, one row per distinct `ItemId` with a count if duplicated. Each row: item name, "Equip"
   (only if `validSlots` is non-empty — the item is gear) and/or "Use" (only if `useEffects` is
   non-empty — the item is a consumable; an item can be both, e.g. a self-buffing ring that's also
   consumable, or neither, e.g. a pure quest item). "Use" is hidden entirely while `state.inCombat`
   (mid-run screen only — the Hub screen is never mid-combat, so this check is a no-op there, not a
   second code path).

## Designer: authoring `useEffects`

`ItemPanel.kt`'s `ItemEditor` (lines 109-158) gains one more section after MODIFIERS, reusing
`RunEffectListEditor` (`designer/RunEffectEditor.kt:46`) verbatim — the exact composable
`EventPanel.kt` already uses for `EventChoice.effects`/`successEffects`/`failureEffects`. Zero new
editor code, just one more call site:

```kotlin
Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("USE EFFECTS") }
RunEffectListEditor(item.useEffects, catalog, onChange = { onChange(item.copy(useEffects = it)) })
```

## `CatalogValidator`

One new loop next to the existing `grantsFeature` check (`CatalogValidator.kt:76-82`), reusing
`checkRunEffect` (`CatalogValidator.kt:176-186`) exactly as `EventPanel`'s effects already do — it's
already generic per-`RunEffect`-variant, not per-owner, so no new logic inside it:

```kotlin
for (item in catalog.items.values) {
    for (effect in item.useEffects) checkRunEffect(effect, "Item '${item.id.raw}'.useEffects", catalog, problems)
}
```

## Non-goals (v1)

- **No charges/stacking system.** `ItemInstance.charges`, `ActionDef.Cost.charges`, and any
  in-combat "use a wand as an action" flow are untouched — a real, separate feature.
- **No immediate mid-combat use.** Consuming a `HealParty` item mid-fight only updates
  `PartyMember.hp`, invisible until the encounter ends — acceptable because Use is simply hidden
  during combat, not because the effect silently does nothing.
- **No drag-and-drop, no comparison tooltips, no "recommended" slot highlighting.** Plain list +
  buttons, matching every other screen.
- **No stash capacity limit, no stash sorting/filtering** — a flat list, same as the run inventory's
  own current UI-less state today (this pass gives it its first UI at all).
- **`equip()`'s attunement cap stays a literal `3`** (`Equip.kt:52`) — not promoted to a named
  constant or made content-configurable, matching how nothing else in this pass touches rules-layer
  numbers that already work.
