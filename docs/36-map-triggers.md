# 36 — Map triggers

A trigger is authored content placed on one map cell: when a player-controlled
character enters that cell, it fires a list of effects exactly once, then
never again for the life of that encounter. Built generic on purpose — a
trigger's effect list is the same authoring vocabulary an ability already
uses, so "show tutorial text," "damage the party," "heal the party," and
"spawn an ambush" are all the same mechanism with different effects chosen.

## Decided with the user before implementation

- **Who fires it**: player-controlled entities only (`Controller.Human`).
  Enemy AI movement never fires a trigger — an AI pathing through a story
  beat or an ambush cell would either double-fire it pointlessly or fire it
  for the wrong audience.
- **When it fires**: both pre-combat exploration movement AND combat
  movement. "Craft interesting encounters" needs mid-fight triggers (step
  into the room, reinforcements arrive) not just pre-combat story beats.
- **Message display**: a blocking modal. Pauses on-screen action, shows the
  text, waits for the player to dismiss it — guarantees they actually read
  it, matching the tutorial/storytelling use case.
- **Once only**: no repeat flag exists in v1 — every trigger is one-shot,
  matching the ask exactly ("once a trigger is activated it cannot be
  triggered again"). A `repeatable` flag is easy to add later if some
  content ever wants it; nothing here forecloses that.

## Reusing the ability-effect vocabulary, not inventing a parallel one

The survey before this doc confirmed `EffectTemplate` (`core/model/ActionDef.kt`)
is already exactly "an authored, symbolically-targeted effect list resolved
to concrete `Effect`s at fire time" — `EffectTemplate.instantiate(state, ctx,
cat): List<Effect>` (`core/rules/action/EffectTemplateInstantiate.kt`) already
does this for actions. A trigger fires the same way an action performs:
build an `ActionCtx`, instantiate the template list, push the result onto
the resolver stack. No new effect-authoring type, no new resolution code.

**`Ref.Caster` == "the entity that stepped on the trigger."** Not a stretch
of the existing meaning — `Caster` already means "whoever is the source of
this effect resolution"; for a trigger, that's the entity that walked into
it. `Ref.EachTarget` == "every entity in `ctx.targets`" — set that to the
whole living player party at fire time, and an author gets "heal/damage the
stepping character" (`Ref.Caster`) and "heal/damage the whole party"
(`Ref.EachTarget`) both for free, no new `Ref` case needed.

`ActionCtx.point` (where `EffectTemplate.SpawnEntity`/`Teleport` resolve
their position from) is set to the trigger's own `GridPos`. One trigger's
effect list shares that single point — a trigger wanting enemies to spawn
in three different spots is three `TriggerPlacement`s, not one with three
`SpawnEntity` templates. Same constraint a real spawn/summon action already
lives with; not a new limitation this feature introduces.

### One new primitive: `ShowMessage`

No dialogue/text-box display existed anywhere in the engine before this.
Added as a first-class effect, not a trigger-only special case, so ordinary
ability content could use it too later (a scroll that reads flavor text, a
trap that taunts before it hits):

- `EffectTemplate.ShowMessage(text: String)` (`ActionDef.kt`) — no `Ref`,
  text is static content, nothing to resolve per-target.
- `EffectTemplateInstantiate.kt`: maps straight through to `Effect.ShowMessage(text)`.
- `Effect.ShowMessage(text: String)` (`Effect.kt`) — handler leaves `GameState`
  untouched, emits `GameEvent.MessageShown(text)`. It's a pure "something to
  show the player" effect, same shape as every other effect that only exists
  to drive a `GameEvent`.
- `GameEvent.MessageShown(text: String)` (`GameEvent.kt`).

An author places `ShowMessage` anywhere in a trigger's effect list — before
a `SpawnEntity` for "the ambush announces itself first," after a `Heal` for
"the fountain explains what it just did," or alone for pure tutorial text.

### The blocking modal is a `Beat`, not a resolver pause

The resolver itself never waits on the player — `run()` is synchronous,
same as every other effect. What "pauses the game" is `:ui`'s existing
choreography/beat player, which already sequences every animation
one-at-a-time and already suspends `applyStep` until the whole beat queue
drains (`player.awaitDrained()`) before the caller's next line runs. A
`GameEvent.MessageShown` beat that shows the text-box composable and
suspends on a `CompletableDeferred` completed by the player's dismiss tap
slots into that queue exactly like a dice-roll card's hold or a projectile's
travel time — nothing about the resolver, the reaction/`Ask`-`Decision`
pause-and-resume machinery, or `StepResult.AwaitingInput` needs to change.
This also means it works identically whether the trigger fired mid-`applyStep`
(combat) or from `exploreMoveTo`'s own `applyStep` call (exploration) — both
already funnel through the same beat player.

`Director.kt`: `is GameEvent.MessageShown -> listOf(Beat(Timing.Blocking) {
world -> world.showMessage(event.text) })`. `VisualWorld` gets a
`pendingMessage: MutableState<PendingMessage?>` (text + the
`CompletableDeferred<Unit>` the dismiss button completes) — a new
ink-styled `TextBoxOverlay` composable renders when non-null, matching
`InkComponents.kt`'s existing flat ink-on-parchment look, not a native
Material dialog.

## Model

```kotlin
// Ids.kt
@JvmInline @Serializable value class TriggerId(val raw: String)

// MapDef.kt
@Serializable
data class TriggerPlacement(
    val id: TriggerId,
    val at: GridPos,
    val effects: List<EffectTemplate> = emptyList(),
)
```

- `BattleMapDef.triggers: List<TriggerPlacement> = emptyList()` — authored,
  same sibling shape as `props`/`spawns`.
- `BattleMap.triggers: List<TriggerPlacement> = emptyList()` — carried
  through by `toBattleMap()`. Unlike `floorTexture`/`wallStyle` (rendering-only),
  this one IS a rules-engine consumer: both the exploration hop loop and the
  combat `MoveAlong` handler read it, same category as `fogOfWar`.
- `GameState.firedTriggers: Set<TriggerId> = emptySet()` — runtime-only,
  same "monotonic, only grows, lives on the snapshot that already persists
  mid-encounter" shape as `revealedTiles`. A fresh `GameState` (a new
  encounter, a replay of the same map) naturally starts with an empty set —
  no separate reset logic needed, it falls out of `GameState` being rebuilt
  per encounter.

`id` is generated once at placement time in `:designer` (a UUID string —
`:designer` is desktop/JVM-only, `java.util.UUID` is fine there), never
author-typed. It exists purely so `firedTriggers` has something stable to
track; nothing about it is ever shown to a player.

## Firing: one shared function, two call sites

`core/rules/.../Triggers.kt`:

```kotlin
fun fireTriggerIfAny(state: GameState, entityId: EntityId, at: GridPos, cat: Catalog): Pair<GameState, List<Effect>>?
```

Returns `null` if: no trigger at `at`, it's already in `state.firedTriggers`,
or `entityId`'s `Actor.controller` isn't `Controller.Human`. Otherwise
returns `state` with the id added to `firedTriggers` (marked fired **before**
instantiation, so if the trigger's own effects move the stepper back onto
the same cell nothing re-fires) paired with the instantiated `List<Effect>`
(`ctx = ActionCtx(caster = entityId, targets = livingPlayerParty(state),
point = at)`).

**Exploration** (`ui/App.kt`, `exploreMoveTo`'s per-hop loop): after each
hop's `moveEntityTo`/`updateRevealedTiles`/`updateEngagedEnemies`, call
`fireTriggerIfAny`. If non-null, `applyStep(runResolver(Resolver(newState,
stack = effects), catalog))` — the exact call shape `endTurn` already uses.
Re-check `inCombat` right after (a spawned enemy can put the party into
combat immediately), same early-return-from-the-walk the existing
enemy-spotted check already does.

**Combat** (`core/rules/resolver/Handlers.kt`, `moveAlong`): after computing
`newState` for the hop (right where the handler already builds it, line
~370), call `fireTriggerIfAny`. If non-null, prepend its effects onto
`spawn` ahead of the `MoveAlong(index+1)` continuation — the exact pattern
`onWallHit` (docs/29) already established for "something extra happens
mid-path, then the walk resumes." `moveAlong` gains a `cat: Catalog`
parameter (`applyEffect` already has one in scope to pass down).

## `:designer` authoring

`MapEditorPanel.kt`'s existing `PaintTool` sealed interface gets a `Trigger`
case, following the exact click-to-place pattern `Spawn`/`Prop` already use.
Clicking an empty cell creates a `TriggerPlacement(id = fresh UUID, at =
cell, effects = emptyList())`; clicking an existing trigger cell opens its
inline editor instead of placing a second one. The editor is:

```kotlin
EffectTemplateListEditor(trigger.effects, catalog, onChange = { ... })
```

— the exact same composable `ActionDef.effects` and `StatusDef.onTurnStart`
already use, reused verbatim. `EffectTemplateEditor.kt`'s private
`EffectKind` enum/`kind()`/`defaultFor()` gain one new case, `ShowMessage`,
with an `InkTextField` row for the text — every other `EffectKind` case
needs no change at all, since a trigger's `effects` field is typed exactly
`List<EffectTemplate>`, nothing trigger-specific about it.

Trigger cells render on the canvas as a small ink glyph (distinct from a
Spawn zone's marker and a Prop's sprite) at the cell center, in both `:ui`'s
Board (so an author previews exactly what a player would walk into, matching
this project's established "author sees what the player sees" precedent)
and `:designer`'s own Map editor canvas.

## Non-goals (v1)

- No conditional/branching triggers (flags, "only if the party has item X") —
  a trigger fires unconditionally the first time it's stepped on. `RollSave`'s
  `onSuccess`/`onFail` already gives limited in-effect branching if content
  needs it (e.g. a trap trigger that rolls a save before deciding whether to
  damage).
- No re-arming/`repeatable` flag — see "Decided with the user" above.
- No multi-tile trigger zones — one `GridPos` per `TriggerPlacement`, matching
  "a trigger gets placed in a cell" exactly as asked. A room-sized trigger is
  N placements today; a `SpawnZone`-style tile-list shape is a trivial follow-up
  if that turns out to matter.
- No trigger-authored spawn point beyond the trigger's own cell (see
  `ActionCtx.point` above).
