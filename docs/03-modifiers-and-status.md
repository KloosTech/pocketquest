# 03 — Modifiers, equipment and status effects

## They are the same mechanism

From the rules' point of view, a ring of protection and a *bless* spell are
both "a source of modifiers and hooks attached to an entity". Modelling them
separately means writing every stat calculation twice — and then finding, six
months later, that armour is counted in one path and the buff in the other.

```kotlin
interface ModifierSource {
    val modifiers: List<Modifier>
    val behavior: BehaviorId?
}

@Serializable
sealed interface Modifier {
    @Serializable data class Add(val stat: Stat, val value: Int) : Modifier
    @Serializable data class Mul(val stat: Stat, val factor: Float) : Modifier
    @Serializable data class Override(val stat: Stat, val value: Int) : Modifier
    @Serializable data class Grant(val flag: Flag) : Modifier
    @Serializable data class Resist(val type: DamageType, val level: Resistance) : Modifier
    @Serializable data class Roll(val ctx: RollContext, val side: AdvSide) : Modifier
}
```

## Derivation order is fixed

```
base (archetype)
  → Add          summed
  → Mul          multiplied, applied to the Add result
  → Override     last writer wins, sorted by source priority
  → clamp        to legal ranges
```

Sources are collected in a fixed order so `Override` ties resolve
deterministically:

```kotlin
fun Entity.stats(cat: Catalog): Stats {
    val sources: List<ModifierSource> =
        cat[archetype].innate +
        equipment.wornInSlotOrder() +          // MainHand, OffHand, Armor, Ring1, Ring2…
        statuses.sortedWith(compareBy({ it.def.raw }, { it.appliedAtVersion }))
    …
}
```

Never iterate a `Set` or `HashMap` here. Iteration order differences produce
different `Override` outcomes, which breaks the determinism guarantee from
[02](02-state-model.md).

## Advantage is not a number

The most common way to get D&D wrong. Advantage does not stack, does not equal
`+5`, and any single source of disadvantage cancels any number of advantages.
So collect sources and resolve categorically:

```kotlin
enum class AdvSide { Advantage, Disadvantage }
enum class RollMode { Normal, Advantage, Disadvantage }

fun resolveAdvantage(sides: Set<AdvSide>): RollMode = when {
    AdvSide.Advantage in sides && AdvSide.Disadvantage in sides -> RollMode.Normal
    AdvSide.Advantage in sides -> RollMode.Advantage
    AdvSide.Disadvantage in sides -> RollMode.Disadvantage
    else -> RollMode.Normal
}
```

`RollContext` narrows where a modifier applies: `AttackRoll(vs = Faction?)`,
`SavingThrow(ability)`, `AbilityCheck(skill)`. A `Roll` modifier without a
context would apply to everything, which is almost never what content wants.

## Status effects: expiry timestamps, not countdowns

A `roundsLeft` counter that every system decrements produces off-by-one bugs,
because D&D durations attach to *moments*, not to a number of rounds. "Until
the end of your next turn" and "until the end of the caster's turn" are
different things, and a shared counter cannot tell them apart.

```kotlin
@Serializable
data class ActiveStatus(
    val def: StatusId,
    val sourceId: EntityId?,
    val linkId: LinkId?,                 // concentration group — ends together
    val stacks: Int = 1,
    val expiry: Expiry,
    val saveEnds: SaveSpec? = null,      // repeat save at a given moment
    val appliedAtVersion: Long,          // tie-break for Override ordering
) : ModifierSource

@Serializable
sealed interface Expiry {
    @Serializable data object Permanent : Expiry
    @Serializable data class EndOfTurnOf(val who: EntityId, val round: Int) : Expiry
    @Serializable data class StartOfTurnOf(val who: EntityId, val round: Int) : Expiry
    @Serializable data class EndOfRound(val round: Int) : Expiry
    @Serializable data object OnConcentrationLost : Expiry
}
```

At each turn boundary the engine asks "which expiries match *this* moment"
rather than decrementing anything. Extending a duration means writing a new
`Expiry`, never arithmetic on a counter.

### Stacking policy is per-definition

Declare it once in `StatusDef`, or you will re-litigate it at twenty call
sites:

```kotlin
enum class StackPolicy {
    Refresh,        // reapply → new expiry, stacks stay 1 (most buffs)
    AddStacks,      // stacks += 1, expiry refreshed (poison, exhaustion)
    KeepStrongest,  // higher potency wins, weaker is dropped
    Independent,    // multiple instances coexist (different sources)
}
```

### Concentration

`LinkId` groups everything a single concentration effect created — including
statuses on *other* entities. When concentration breaks, one lookup removes all
of them:

```kotlin
fun breakConcentration(state: GameState, link: LinkId): List<Effect>
```

Rules to encode:
- One `LinkId` per entity at a time. Starting a new one ends the previous.
- Damage triggers a CON save at `DC = max(10, damage / 2)`.
- Falling unconscious or dying breaks it unconditionally.

## Equipment

```kotlin
@Serializable
data class Equipment(val slots: Map<Slot, ItemInstance>) {
    companion object { val EMPTY = Equipment(emptyMap()) }
}

@Serializable
data class ItemInstance(
    val def: ItemId,
    val enchantment: Int = 0,
    val charges: Int? = null,
    val attuned: Boolean = false,
) : ModifierSource

enum class Slot { MainHand, OffHand, Armor, Helm, Ring1, Ring2, Amulet }
```

- Two-handed weapons occupy `MainHand` **and** `OffHand`. Model this as a
  validation rule in `equip()`, not as a special case in the renderer.
- Inventory is stored separately from `Equipment`; only worn items feed
  `stats()`.
- Attunement limit (3) is a validation rule, checked on `equip()`.

## The escape hatch

Pure data cannot express "when you take damage, teleport 3 tiles". Content that
needs code gets a `BehaviorId` that resolves against a registry:

```kotlin
fun interface Behavior {
    /** Returns effects to push. MUST NOT mutate anything. */
    fun onEvent(ctx: RuleCtx, event: GameEvent): List<Effect>
}

object BehaviorRegistry { val all: Map<BehaviorId, Behavior> }
```

Two hard constraints:

1. Behaviors **return effects**, they never mutate state. A behaviour that
   writes directly would break determinism, replay and preview at once.
2. Behaviours are keyed by a string that lives in JSON. Content authors pick a
   behaviour; they do not write one. A missing key is a load-time error, not a
   runtime crash.

Expect roughly 90% of items and statuses to be pure data.

## Where `Resources` gets its maximum

Closing the loop with [02](02-state-model.md): `Entity.resources.mana` is the
*current* value; `Stats.maxMana` is derived. A ring granting +1 max mana is
therefore just `Add(Stat.MaxMana, 1)` with no special path anywhere. The turn
reset reads `stats().maxAp`, which is exactly why expiry must run before the
reset — see [04-resolver.md](04-resolver.md#turn-boundaries).
