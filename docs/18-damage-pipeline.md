# 18 — The damage pipeline

Motivating case: a tank casts a ward on an ally. When that ally is attacked, the
damage lands on the tank instead.

The engine cannot express this. It is worth spelling out why, because the reason
generalises to a whole family of effects we will want.

## Why reactions cannot do this

`DealDamage` writes HP and emits `DamageTaken` in one step:

```kotlin
val newCurrent = (health.current - finalAmount).coerceAtLeast(0)
```

Reactions fire from `collectTriggers`, which runs on *emitted* events. By the
time anything can react to `DamageTaken`, the HP is already gone. `GameEvent` is
past tense by design ([README](README.md#vocabulary)), and inventing a
present-tense "about to take damage" event would break that distinction for
every consumer, including the animation director.

What is missing is not a new trigger. It is an **interception point inside the
damage step**, before HP is written.

## The pipeline

`DealDamage` stops being one operation and becomes: build an instance, run it
through an ordered chain, then apply the result.

```kotlin
data class DamageInstance(
    val source: EntityId?,
    val target: EntityId,
    val amount: Int,
    val type: DamageType,
    val tags: Set<DamageTag> = emptySet(),   // Melee, Ranged, Spell, Aoe, Critical
    val hops: List<EntityId> = emptyList(),  // entities already redirected through
)
```

Steps, in fixed order:

| # | Step | Does |
| --- | --- | --- |
| 1 | **Retarget** | Changes `target` — ward, guard, "share the pain" |
| 2 | **Prevent** | Cancels entirely — immunity, "ignore the first hit each round" |
| 3 | **Convert** | Changes `type` — fire becomes cold |
| 4 | **Scale** | Multiplies — resistance, vulnerability |
| 5 | **Reduce** | Flat subtraction — armour, damage thresholds |
| 6 | **Absorb** | Consumes a pool before HP — temporary HP, shields |
| 7 | **Apply** | Writes HP, emits `DamageTaken` / `Died` |
| 8 | **After** | Spawns follow-ups — reflect, on-hit riders |

### Ordering is the whole design

**Retarget is first, and this is not arbitrary.** Everything downstream must be
computed against the *new* target. If a fire attack is redirected onto a
fire-resistant tank, the tank's resistance applies — not the ally's. Putting
retarget after scaling would silently use the wrong creature's defences, and it
is the kind of bug nobody notices until a player reports odd numbers.

**Absorb is last before apply**, so a shield soaks the already-reduced number
rather than the raw one. Otherwise resistance and shields multiply together and
a resistant character with a shield becomes untouchable.

**Scale before Reduce.** Halve, then subtract armour — the reverse makes flat
reduction scale with resistance, which is a much stronger effect than intended.

Steps 4 and 6 already have homes in the model that are currently unused or
hardcoded, which is a good sign the shape is right:

- Resistance is baked into `dealDamage` as a `when`. It becomes a Scale step.
- `Health.temp` exists in `Entity` and is **never read anywhere**. It becomes the
  Absorb pool.

## Where steps come from

The same `ModifierSource` machinery as everything else — statuses, equipment and
features contribute steps through their catalog definitions. Nothing new is
stored on the entity.

```kotlin
@Serializable
sealed interface DamageStep {
    @Serializable @SerialName("retarget")
    data class Retarget(val to: StepRef, val condition: StepCondition) : DamageStep

    @Serializable @SerialName("split")
    data class Split(val to: StepRef, val fraction: Float, val condition: StepCondition) : DamageStep

    @Serializable @SerialName("prevent")
    data class Prevent(val condition: StepCondition) : DamageStep

    @Serializable @SerialName("convert")
    data class Convert(val from: DamageType?, val to: DamageType) : DamageStep

    @Serializable @SerialName("scale")
    data class Scale(val factor: Float, val onlyType: DamageType? = null) : DamageStep

    @Serializable @SerialName("reduce")
    data class Reduce(val flat: Int, val onlyType: DamageType? = null) : DamageStep

    @Serializable @SerialName("absorb")
    data class Absorb(val pool: AbsorbPool) : DamageStep

    @Serializable @SerialName("reflect")
    data class Reflect(val fraction: Float, val type: DamageType? = null) : DamageStep
}

/** Resolved against the status that contributed the step. */
@Serializable
sealed interface StepRef {
    @Serializable data object StatusSource : StepRef   // whoever applied the ward
    @Serializable data object Attacker : StepRef
    @Serializable data class Fixed(val id: EntityId) : StepRef
}

@Serializable
data class StepCondition(
    val refWithinTiles: Int? = null,     // ward only works if the tank is close
    val refMustBeHealthy: Boolean = true, // a downed tank protects nobody
    val requiresTags: Set<DamageTag> = emptySet(),
    val excludesTags: Set<DamageTag> = emptySet(),
    val maxPerRound: Int? = null,
)
```

Collection order mirrors `stats()` exactly — archetype innate, equipment in
`Slot` order, statuses sorted by `StatusId` then `appliedAtVersion` — so two
competing Retargets resolve deterministically instead of by map iteration.

## The guardian ward, end to end

```jsonc
{
  "id": "guardian_ward",
  "stackPolicy": "Refresh",
  "damageSteps": [
    { "type": "retarget",
      "to": "statusSource",
      "condition": { "refWithinTiles": 3, "refMustBeHealthy": true,
                     "excludesTags": ["Aoe"] } }
  ]
}
```

The tank casts it on an ally; the resulting `ActiveStatus` carries
`sourceId = tank`. When the ally is hit, step 1 finds the Retarget, resolves
`StatusSource` to the tank, checks the tank is within 3 tiles and not downed, and
rewrites `target`.

Events emitted:

```
AttackRolled(goblin -> ally, hit = true)
DamageRedirected(from = ally, to = tank, by = guardian_ward)
DamageTaken(tank, 6, slashing)
```

`DamageRedirected` is new and matters for the UI as much as the rules: without
it the animation director sees damage appear on a character nobody attacked, and
the player has no idea why.

### Passive, not a prompt

The ward is a status, not a reaction. It does not cost the tank's reaction and
does not ask the player anything.

That is deliberate. With true interleaved initiative, a prompt on every incoming
hit would interrupt the player several times per enemy turn — the single most
annoying pattern available. An active "intercept this hit?" reaction is
expressible later through the existing reaction machinery, but the default
should be a fire-and-forget status the player set up on their own turn.

### Loop protection

Two tanks warding each other is an obvious infinite redirect. `hops` records
every entity the instance has passed through; a Retarget onto an entity already
in `hops` is skipped, and the chain is capped at 4 hops regardless. Exceeding
the cap emits `Fizzled` rather than throwing — it is reachable through ordinary
content, not only through a bug.

## What else the pipeline buys

Once it exists, these are content, not code:

| Effect | Steps |
| --- | --- |
| Shield / barrier | `Absorb(pool)` |
| Stone skin | `Scale(0.5)` on physical tags |
| Damage threshold | `Reduce(flat = 10)` |
| Share the pain | `Split(to = statusSource, fraction = 0.5)` |
| Thorns | `Reflect(fraction = 0.25)` |
| Elemental attunement | `Convert(from = Fire, to = Cold)` |
| Sanctuary | `Prevent(condition = { excludesTags: ["Aoe"] })` |

The tank's kit becomes composable: a taunt to pull attention, a ward to
intercept what it cannot avoid, a shield to soften what lands.

## Taunt is a different mechanism — do not conflate them

Both make a tank tanky, and they work at opposite ends of the turn:

- **Taunt** constrains the *enemy's choice*. It lives in `:core:ai`'s target
  selection: a `Flag.Taunted` narrows the candidate set before an action is
  chosen. Nothing about damage is involved.
- **Ward** intercepts the *outcome* after a choice was made. It lives in this
  pipeline and never influences what the enemy decides.

A good tank wants both, and they fail differently: taunt fails against an enemy
that has no valid alternative target anyway, ward fails when the tank is out of
range or already down. Implementing one and calling it "tanking" will feel
broken in exactly the situations the other covers.

Taunt is tracked separately in [17-engine-gaps.md](17-engine-gaps.md).

## Healing

A `HealInstance` chain in the same shape, but much shorter: `Prevent` (wounds
that cannot be healed), `Scale` (healing amplification), `Apply`. No retarget —
redirecting healing is a mechanic nobody asks for, and adding it symmetrically
would double the surface for no gain.

## Tests

Layer 2 in [09-test-plan.md](09-test-plan.md), one per step plus ordering:

- Retarget moves the damage; **the new target's resistance applies, not the
  original's** — the ordering test that matters most.
- A ward with the tank 4 tiles away (limit 3) does not fire.
- A ward whose source is downed does not fire.
- Two mutually warding tanks terminate and emit `Fizzled`.
- Scale then Reduce: 20 damage, resistance, `Reduce(3)` → 7, not 8.
- Absorb consumes `Health.temp` before HP and carries the remainder through.
- Reflect spawns a `DealDamage` back at the source that does **not** itself
  reflect (tagged to skip step 8).
- A golden event list containing `DamageRedirected`, since the director depends
  on the event's position in the sequence.
