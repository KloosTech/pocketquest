# 24 — Projectile travel animation

## Context

Combat has no travel visual today — a ranged attack gets the same generic
attacker-pulse as melee, nothing crosses the board. `AttackRolled`'s beat is
just `world.pulse(event.attacker, ...)`. Goal (docs/23's explicitly deferred
non-goal, now in scope): every attack sends a sprite flying from caster to
target — a fist for melee, a projectile orb for ranged/spells — rotated to
face the direction of travel. An area attack sends exactly one projectile to
the targeted point, not one per cell/entity hit, followed by a ripple
highlighting every affected tile.

## Design (resolved this session)

**Sprite lives on the action, not the archetype.**
`ActionDef.projectileSprite: String? = null` — a `kind = "projectile"`
manifest id (docs/23's `fireball`/`iceball`/`earthball`/`lightningball`, plus
a future `fist` sprite for melee). `null` keeps today's plain
attacker-pulse, no travel — a real, permanent fallback for actions nobody's
drawn art for, same pattern every other optional field in this project uses.

**One event anchors the whole animation, regardless of how many targets are
hit.** `GameEvent.ActionStarted(who, actionId)` already fires exactly once
per cast, *before* the action's effects expand per-target
(`EffectTemplate.RollAttack`/`RollSave` with `Ref.EachTarget` become one
`Effect` per resolved entity at instantiation time — an AoE spell hitting 5
goblins already produces 5 independent `AttackRolled`/`DamageTaken` events
today). Anchoring the travel beat on `ActionStarted` instead of the
per-target events means one flight no matter how many things get hit —
`ActionStarted` needs two new fields, `point: GridPos?` and
`targets: List<EntityId>`, both trivially available where `perform()`
already constructs this event from the full `ActionCtx` it's holding.

**Destination**: `ctx.point` if the action set one (point/AoE-targeted),
else the first target's tile (plain single-entity melee/ranged) — "the one
that got selected," not each individual hit.

**Ripple reuses existing targeting math** — `tilesInShape(origin, point,
shape, map)` is the same function `affectedBy` already calls to resolve AoE
hits, so highlighting the footprint on impact is not new geometry, just a
new consumer of it. **Resolved: simultaneous flash**, not a staggered wave —
every affected tile highlights together the instant the projectile lands,
close to the existing `Fizzled`/`Rejection.Blocked` tile-flash mechanism
already in `Director.kt`.

**Rotation**: computed once at launch from the origin→destination angle
(`atan2`), held fixed for the whole flight — not a tumble like the dice
card's die. Source art is assumed to face east/rightward by default; if that
turns out wrong for a given sprite it's a fixed, obvious, cheap-to-fix
offset, not a design problem. The 4 existing orb sprites are round/symmetric
so rotation won't visibly matter for them yet — this is groundwork for the
fist and any future directional sprite, not idle work.

**Sequencing**: cast → projectile flies → roll resolves → hit plays the
existing impact/damage-number beats, miss plays a small whiff/fade instead
of the projectile just silently vanishing. The projectile always launches
regardless of outcome — the player sees the attack happen, then learns
whether it landed, never the reverse.

**Travel duration scales with distance** (clamped to a sane min/max) rather
than being fixed — a fixed duration makes either a 1-tile fist punch feel
sluggish or an 8-tile fireball feel instant, whichever value is picked.

## Non-goals

- Per-frame tumbling/spin during flight (dice-card territory, not this).
- Arcing/parabolic trajectories — straight-line travel only.
- New art beyond what already exists — a `fist` sprite is referenced as the
  motivating melee example but isn't required to land this pass; melee
  actions simply stay on the `null`/no-travel fallback until one's drawn.

## Implementation plan

1. **`core:model`** — `ActionDef.projectileSprite: String? = null`;
   `GameEvent.ActionStarted` gains `point: GridPos? = null` and
   `targets: List<EntityId> = emptyList()`.
2. **`core:rules`** — `buildInitial` (`Perform.kt`) passes `ctx.point`/
   `ctx.targets` into the `ActionStarted` it already constructs. No other
   engine change — targeting/hit/damage math is completely untouched, this
   is presentation layered on existing events.
3. **`:ui`** — new `VisualWorld` overlay type for an in-flight projectile
   (position `Animatable`, fixed rotation, sprite bitmap, distance-scaled
   duration); `Director.kt`'s `ActionStarted` beat launches it toward the
   resolved destination, then (once landed) fires the simultaneous ripple
   flash across `tilesInShape`'s footprint; a miss whiff beat on the
   existing `AttackRolled`/`SaveRolled` `hit`/`success` fields.
4. **`:designer`** — a projectile-sprite picker on the Action editor,
   mirroring the Archetype editor's sprite picker from docs/23.

Verification per pass: full cross-module regression sweep, `v1` untouched,
live check in the designer (a ranged action with a projectile sprite
actually flies across the board in Playtest; an AoE action fires exactly
one projectile plus a ripple, not N).
