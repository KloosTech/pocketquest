# 20 — Desktop designer: detailed spec

[16-art-direction.md](16-art-direction.md#the-desktop-designer) named the 7
editors and the two hard requirements (live validation, real-engine preview).
This doc is the follow-up written after actually building Map/Encounter/
Archetype and playtesting through them: what exists, what's missing per
editor (found empirically, not guessed), and the decisions made to sequence
the rest.

## Status per editor

### Archetype — built

Name, 6 ability scores, HP/AC/speed/AP/mana, and (as of the playtest-movement
bug) an actions toggle row sourced live from `catalog.actions`.

Missing: `innateModifiers`, `innateDamageSteps`, `innateHealSteps`. These need
a `Modifier`/`DamageStep`/`HealStep` sub-editor that Status, Feature, and Item
will also need — building it once, generically, is the actual work item here,
not three archetype-specific fields.

### Map — built, first pass

Terrain (`Floor`/`Wall`/`Difficult`/`Hazard`, ink-hatch rendered), edge walls
(`WallEdge`, doc16's thin room-divider style, additive on top of whole-cell
`Wall`), floor texture (one of 3 real sheets, auto-varied per tile), spawn
zones, props (place/erase from the full 74-prop manifest), pan/zoom matching
`:ui`'s Board.

Missing: prop rotation/flip (`PropPlacement.rotationQuarters`/`flipX` exist in
the model, no UI sets them), `PropLayer` choice (every editor-placed prop is
hardcoded to `Object`), user-chosen floor-texture swatch per tile (currently
picked automatically for visual variety, not authored).

### Stamp — deferred

`RoomStamp`/`Connector` don't exist in `:core:model` at all — deliberately
skipped when `MapDef.kt` was written. Doc16 frames Stamp as "same tools as
Map, plus connectors," for composing reusable rooms across many maps.

**Decision: defer.** The Map editor already supports hand-composed multi-room
layouts (walls, floor texture, props). Stamp's entire value is reuse *across*
maps — worth building once there are enough authored maps to actually reuse
rooms between, not before. Revisit once map count grows.

### Encounter — built, first pass

Name, map reference, enemy roster (archetype + role + count), scaling fields.

Real gap: `EncounterScaling.extraEnemiesPerPartySize`/`extraEnemiesPerAct`
are dead data — nothing in `:core:rules` reads them. No formula for "scaled
by act and party size" exists anywhere (doc11 names the requirement, doc13
that would define the formula doesn't exist). Not blocking today's playtest
loop; blocking before scaling is a real gameplay feature.

### Action — doesn't exist. **Building next.**

`ActionDef` = `Cost` (an `ActionCost` sealed choice: Main/Quick/Reaction/
Movement(tiles)/Free, plus mana/charges/hpCost) + `Targeting` (mode enum:
SelfOnly/SingleEntity/Point/Direction/Path; a `Range` choice; a `Shape`
choice; a `TargetFilter`; requiresLoS; maxTargets) + `List<EffectTemplate>`
(9 polymorphic kinds: DealDamage, ApplyStatus, RollAttack, RollSave, Push,
Teleport, SpawnEntity, DestroyEntity, Heal) + an optional reaction trigger.

This is doc16's own flagged hard problem: "deeply nested polymorphic
structures... miserable in a property inspector." Only `move` exists in any
catalog today (seeded by the designer itself, see 19-below) — every
archetype is otherwise combat-dead.

### Status — doesn't exist

`StatusDef` = `StackPolicy` + modifiers + `onTurnStart` effects + damage/heal
steps. Same polymorphic-list shape as Action's effects/modifiers. Sequenced
after Action because statuses are usually *applied by* an action
(`ApplyStatus` effect template) — building the Status editor first would mean
authoring statuses nothing can apply yet.

### Feature — doesn't exist

`FeatureDef` = modifiers + granted actions + damage/heal steps. Simplest of
the three remaining — mostly reuses whatever Modifier/effect sub-editors
Action and Status need, plus an action-multi-select (already prototyped as
`ArchetypePanel`'s `ActionToggle` row).

### Item — not in doc16's table at all. Real gap.

`ItemDef` = modifiers + `twoHanded` + damage/heal steps. `ItemInstance` (the
equipped, per-character instance) carries `enchantment`/`charges`/`attuned`.
`Equipment.slots: Map<Slot, ItemInstance>` is where a `Slot` actually gets
assigned — **at equip time**, not authored on the item itself. Nothing in
`ItemDef` says which slot(s) an item is valid for.

**Decision: add `validSlots: Set<Slot>` to `ItemDef`.** A real `:core:model`
change, not just a designer field — needs `:core:rules`' equip/`canEquip`
path to actually check it, and a `CatalogValidator` rule (or at minimum the
editor refusing to equip-preview a mismatched slot). Scoped as part of the
Item editor build, after Action/Status/Feature.

## Two cross-cutting decisions

### Polymorphic list UI: type dropdown + inline fields per row

Every `EffectTemplate`/`Modifier` list (Action's effects, Status's modifiers/
onTurnStart, Feature's modifiers, Item's modifiers, Archetype's eventual
innate lists) gets the same interaction: each row starts with a "kind"
`DSelect` (DealDamage / ApplyStatus / RollAttack / ...), and only that kind's
specific fields render below it. Exactly the Map editor's paint-tool selector
pattern, reused rather than inventing a new interaction model. The
alternative (a visual drag-ordered timeline) was considered and rejected for
now — meaningfully more UI work, no existing precedent in this codebase to
build on, revisit only if the dropdown-per-row approach proves unusable in
practice once Action content actually gets authored.

### Build order

**Action → Item → Status → Feature → (Stamp, once map count justifies it).**

Action first because it's the single highest-leverage unlock (nothing acts
without it). Item next since it's the other major "character progression"
axis and needs its own `:core:model` change (`validSlots`) sequenced
deliberately rather than bolted on later. Status and Feature both lean on
whatever generic Modifier/effect sub-editor Action's build produces, so they
get cheaper the more of that scaffolding already exists once Action ships.

## What "building the Action editor" concretely means next

1. A `Cost` sub-editor: `ActionCost` kind dropdown (Main/Quick/Reaction/
   Movement/Free) with Movement's `tiles` field conditionally shown, plus
   mana/charges(`ItemId`, validated against `catalog.items`)/hpCost steppers.
2. A `Targeting` sub-editor: mode dropdown, `Range` kind dropdown (Melee/
   Tiles(n)/SelfRange), `Shape` kind dropdown (Single/Sphere(radius)/
   Cone(length,degrees)/Line(length)/Rect(width,height)), `TargetFilter`
   (faction dropdown incl. null/any, requireAlive/excludeSelf toggles,
   hasStatus picker validated against `catalog.statuses`), requiresLoS
   toggle, maxTargets stepper.
3. The effects list, using the type-dropdown-per-row pattern above, covering
   all 9 `EffectTemplate` kinds — including `Ref` fields (Caster/EachTarget/
   Slot) as their own small dropdown, since every effect template threads
   `Ref`s through for who/what it targets.
4. Wire live `CatalogValidator` feedback the same way `App.kt`'s existing
   banner already does — no new validation UI shape needed, just new problem
   sources as Action content gets authored (dangling `ItemId`/`StatusId`/
   `ArchetypeId` references inside effect templates).
5. doc16's "preview against the real engine" requirement: a numeric preview
   panel calling `:core:rules`' `preview()` in `Expected` mode against a
   sample caster/target, showing the resulting event list/damage numbers —
   the actual payoff line from doc16 for depending on `:core:rules` at all.
