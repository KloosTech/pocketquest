# 05 — Actions and effects

An action is a **declaration**. It contains no logic; performing it pushes an
initial list of effects onto the resolver stack.

## Three questions, one definition

The UI always asks the same three things, and all three must be answered from
the same source — otherwise the interface eventually shows something different
from what the engine does. v1 has exactly this defect: `snapshotBattle()`
computes `attackableTiles` with its own range and line-of-sight logic, while
`SkillResolverSystem` independently decides whether the skill actually lands.

1. Which actions can I take at all? → cost and requirement check
2. Where may I target? → targeting
3. What would happen? → preview

```kotlin
data class ActionDef(
    val id: ActionId,
    val name: String,
    val cost: Cost,
    val targeting: Targeting,
    val requirements: List<Requirement>,
    val effects: List<EffectTemplate>,
    val behavior: BehaviorId? = null,
)
```

## Action economy is categorical

```kotlin
sealed interface ActionCost {
    data object Main : ActionCost              // the action
    data object Quick : ActionCost             // bonus action
    data object Reaction : ActionCost
    data class Movement(val tiles: Int) : ActionCost
    data object Free : ActionCost
}

data class Cost(
    val action: ActionCost,
    val mana: Int = 0,
    val charges: ItemId? = null,
    val hpCost: Int = 0,
)
```

`Quick` is **not** a cheaper `Main`. Modelling the economy as a single integer
pool ("costs 1 AP instead of 2") makes "one bonus action per turn" impossible
to express, and that constraint is not optional in D&D. `Resources` therefore
carries `quickUsed: Boolean` separately from `ap`.

## Targeting

```kotlin
data class Targeting(
    val mode: TargetMode,          // SelfOnly | SingleEntity | Point | Direction | Path
    val range: Range,              // Melee | Tiles(n) | Self
    val shape: Shape,              // Single | Sphere(r) | Cone(len, deg) | Line(len) | Rect(w, h)
    val filter: TargetFilter,      // faction, alive, hasStatus, notSelf…
    val requiresLoS: Boolean = true,
    val maxTargets: Int = 1,
)

fun legalTargets(state: GameState, caster: EntityId, def: ActionDef, cat: Catalog): Set<GridPos>
fun affectedBy(state: GameState, def: ActionDef, at: GridPos): List<EntityId>
```

`legalTargets` feeds the tile highlighting directly. There is no second code
path for rendering. `affectedBy` drives the area preview when the player hovers
a tile.

## Effect primitives: keep the vocabulary small

Twelve to fifteen primitives cover a surprising amount of content:

```
DealDamage   Heal        ApplyStatus     RemoveStatus
MoveAlong    Push        Teleport        SpendCost
RollAttack   RollSave    StartConcentration
SpawnEntity  DestroyEntity   Ask   Composite
```

Everything else is composition. *Eldritch Blast* is `RollAttack → DealDamage`.
*Thunderwave* is `RollSave → DealDamage + Push`. If you catch yourself writing
`CastFireballEffect`, a primitive is cut too coarsely.

## Templates, instances and bindings

`EffectTemplate` is the authored definition; `Effect` is the concrete instance
on the stack. A context resolves the placeholders in between.

```kotlin
data class ActionCtx(
    val caster: EntityId,
    val targets: List<EntityId>,
    val point: GridPos?,
    val slots: Map<SlotKey, SlotValue>,     // intermediate results
)

sealed interface Ref {
    data object Caster : Ref
    data object EachTarget : Ref            // expands to one effect per target
    data class Slot(val key: SlotKey) : Ref
}

fun EffectTemplate.instantiate(ctx: ActionCtx, cat: Catalog): List<Effect>
```

- `EachTarget` expands at instantiation time, **sorted by `EntityId`**. Map
  iteration order here would break seed determinism.
- `Slot` chains effects: `RollSave` writes its outcome into a slot, and the
  following `DealDamage` reads that slot to halve on a success. This is how
  "half damage on a successful save" stays data rather than code.

## Validation returns reasons, not a boolean

```kotlin
sealed interface Rejection {
    data object NotYourTurn : Rejection
    data object ActionAlreadyUsed : Rejection
    data object QuickAlreadyUsed : Rejection
    data class NotEnoughMana(val need: Int, val have: Int) : Rejection
    data class OutOfRange(val distance: Int, val max: Int) : Rejection
    data object NoLineOfSight : Rejection
    data class BlockedByStatus(val status: StatusId) : Rejection
    data class MissingEquipment(val slot: Slot) : Rejection
    data object NoLegalTarget : Rejection
}

fun canPerform(state: GameState, caster: EntityId, def: ActionDef,
               ctx: ActionCtx, cat: Catalog): List<Rejection>
```

One function, three consumers: the UI greys out the button *and* shows the
reason as a tooltip; the AI filters its options; the engine gates execution.
Returning `Boolean` would force each consumer to reimplement the "why".

## Preview runs through the same machine

Because `GameState` is immutable, previewing is running the resolver and
throwing the result away.

```kotlin
sealed interface RngMode {
    data class Live(val seed: RngState) : RngMode
    data object Expected : RngMode     // expected values, saves by probability
}

fun preview(state: GameState, caster: EntityId, actionId: ActionId,
            ctx: ActionCtx, cat: Catalog): PreviewResult
```

This structurally rules out the "it said 12 damage and dealt 8" class of bug,
because the preview *is* the execution with a different RNG mode. And it hands
the AI its evaluation function for free: enumerate legal actions, run each in
`Expected` mode, score the resulting event list.

## Execution

```kotlin
fun perform(state: GameState, caster: EntityId, actionId: ActionId,
            ctx: ActionCtx, cat: Catalog): StepResult {
    val def = cat.action(actionId)
    val rejections = canPerform(state, caster, def, ctx, cat)
    if (rejections.isNotEmpty()) return StepResult.Rejected(Resolver(state), rejections)

    val initial = buildList {
        add(SpendCost(caster, def.cost))
        addAll(def.effects.flatMap { it.instantiate(ctx, cat) })
    }
    return run(Resolver(state, stack = initial), cat)
}
```

Cost is the **first effect on the stack**, not a mutation applied before the
loop. Two reasons: a spell interrupted by a counterspell must still have paid,
and the UI's mana bar animates off the `ResourcesSpent` event — an out-of-band
deduction would never appear in the event list.

## Content authoring

`ActionDef`, `Archetype`, `ItemDef` and `StatusDef` are JSON under
`composeResources/files/`, loaded once into a `Catalog` at startup. The existing
desktop designer (`ui/designer/`) keeps writing that JSON; only the schema and
the loader change.

The Kotlin DSL in `content/dsl/SkillDsl.kt` stays useful as a *test fixture*
builder — expressing a one-off action inline in a test is much nicer than
loading a JSON file. It should not remain the production content path.
