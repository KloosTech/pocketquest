# 21 — AI behavior: tiered profiles

## Context

`:core:ai`'s `ChooseAction.kt` (built early this project) is a single global
behavior: enumerate every legal action/target via `legalTargets`/`canPerform`,
score each with `preview()` in `Expected` mode against a flat "enemy damage
good, own damage bad" heuristic, pick the best. Every enemy plays identically
regardless of archetype. `Controller.Ai(profile: AiProfileId)` already exists
on every entity (doc02) but `profile` is **never read anywhere** —
`startEncounter` hardcodes `AiProfileId("standard")` for every spawned enemy,
and `chooseAction` doesn't take a profile parameter at all. This spec is what
turns that unread tag into a real, per-archetype-configurable system: tiered
priority rules with the existing scorer kept as the fallback, not replaced.

## Architecture: priority tiers + scored fallback

An `AiProfileDef` is an ordered list of tiers. Each turn, `chooseAction` walks
the list top-down; the first tier whose condition holds AND produces a legal
decision wins. If a tier's condition holds but its goal can't resolve to a
legal action/target (e.g. "heal the lowest-HP ally" but no heal action
exists), that's not a hard stop — evaluation falls through to the **next**
tier, not to "do nothing." If no tier produces a decision, `chooseAction`
falls back to today's existing global scorer, extracted unchanged as
`defaultScoredChoice()`. An enemy with no authored profile (or an empty
`tiers` list) behaves exactly as every enemy does today — this is additive,
not a breaking change to existing content.

```kotlin
fun chooseAction(state: GameState, entityId: EntityId, cat: Catalog): AiDecision? {
    val profile = profileFor(state, entityId, cat)  // Controller.Ai.profile -> cat.aiProfiles lookup
    for (tier in profile.tiers) {
        if (!tier.condition.holds(state, entityId, cat)) continue
        resolveGoal(tier.goal, state, entityId, cat)?.let { return it }
    }
    return defaultScoredChoice(state, entityId, cat)  // today's chooseAction, renamed, unchanged
}
```

## Data shapes (new `core:model` types)

```kotlin
@Serializable
data class AiProfileDef(val id: AiProfileId, val name: String, val tiers: List<AiTier> = emptyList())

@Serializable
data class AiTier(val condition: AiCondition, val goal: AiGoal)
```

### `AiCondition` — small fixed vocabulary (doc20's own "type dropdown + inline
fields" pattern applies here too — same reasoning: nested boolean expression
trees are the "miserable in a property inspector" problem doc16 already
flagged, worse for something this authoring-heavy)

```kotlin
sealed interface AiCondition {
    data class SelfHpBelow(val percent: Int) : AiCondition
    data class AnyAllyHpBelow(val percent: Int) : AiCondition
    data class AnyEnemyHpBelow(val percent: Int) : AiCondition
    data object IsTaunted : AiCondition
    data class HasStatus(val status: StatusId) : AiCondition
    data class EnemyCountInRange(val range: Int, val atLeast: Int) : AiCondition
    data object Always : AiCondition   // an explicit catch-all tier, e.g. "always attack" as the last authored tier before the default fallback
}
```

Every check here is a direct query against primitives that already exist —
`Entity.health`/`stats(cat)` for HP%, `Entity.tauntedBy(cat)` (already built
for the existing taunt-narrowing logic) for `IsTaunted`, `GridPos.chebyshevDistanceTo`
for range counts. No new engine capability needed to evaluate a condition,
only to author one.

### `AiGoal` — what a matching tier does

```kotlin
sealed interface AiGoal {
    /** Use the best legal action in [category] (or any category if null), targeting whichever legal candidate [targetPreference] ranks first. */
    data class UseAction(val category: AiActionCategory? = null, val targetPreference: AiTargetPreference) : AiGoal
    data object Retreat : AiGoal
    data object Approach : AiGoal
}

enum class AiActionCategory { Damage, Heal, BuffAlly, DebuffEnemy }
enum class AiTargetPreference { LowestHpPercent, HighestHpPercent, Nearest, Farthest }
```

`UseAction` is a **filter + preference layered on the existing enumeration**,
not a reimplementation: it still calls `legalTargets`/`canPerform`/`preview`
exactly as `defaultScoredChoice` does, just narrows candidate actions to
`category` first and sorts candidates by `targetPreference` instead of by
`preview()` score. Taunt narrowing (`narrowedByTaunt`, already built) still
runs unconditionally underneath this — a tier's target preference ranks
whatever taunt already left as legal, it doesn't bypass a hard mechanical
constraint. `Retreat`/`Approach` are the genuinely new movement decisions (not
`preview()`-scored ones) — move away from/toward the nearest enemy via the
existing `reachableTiles`/pathfinding, no new pathfinding primitive needed.
`Approach` was added after the first real profile authored against this spec
turned out to need it: a melee-only enemy that starts out of range of every
action had no way to close the distance without it, since `UseAction` only
ever picks among *already-legal* targets.

### Classifying an action into a category — the one real new field

`AiActionCategory.Damage`/`Heal` are derivable today (an `EffectTemplate` list
containing `DealDamage`/`RollAttack` vs `Heal`). `BuffAlly`/`DebuffEnemy`
are **not** — `ApplyStatus` doesn't say whether the status it applies is
beneficial or harmful, the exact same gap `ChooseAction.kt`'s own `score()`
comment already calls out ("no way to tell a buff from a debuff from the
event alone"). Fixing it now, since the tiered system is the first thing that
actually needs the answer:

```kotlin
// StatusDef gains one field:
val beneficial: Boolean = true
```

An action's category becomes derivable in full: `ApplyStatus` targeting an
ally with a `beneficial` status → `BuffAlly`; targeting an enemy with a
non-beneficial status → `DebuffEnemy`.

### Assignment — per-archetype, not per-spawn

"Different enemies, different AIs" means the natural authoring granularity is
the **archetype**, not the individual spawned entity. `Archetype` gains:

```kotlin
val aiProfile: AiProfileId = AiProfileId("standard")  // a built-in always-Always/default-fallback profile ships as the zero-config default
```

`startEncounter` (`core/rules/.../StartEncounter.kt`) currently hardcodes
`Controller.Ai(AiProfileId("standard"))` for every enemy spawn — real wiring
gap, fixed alongside this spec: it reads `archetype.aiProfile` instead.

### `Catalog` gains

```kotlin
val aiProfiles: Map<AiProfileId, AiProfileDef> = emptyMap()
```

Same pattern as every other catalog map (`archetypes`, `statuses`, ...), plus
an `aiProfile(id)` accessor matching the existing `archetypeDef`/`statusDef` style.

### `CatalogValidator` additions

- `Archetype.aiProfile` must resolve in `catalog.aiProfiles` (falls back to
  the built-in default profile only when the field is left at its default
  value — an explicitly-authored dangling id is still a real error).
- `AiTier.condition`'s `HasStatus.status` must resolve in `catalog.statuses`,
  same shape as every other status reference check already in place.

## Non-goals for this pass

- **No AI Profile editor yet** — profiles are authored as test fixtures/hand-
  written content for now, same deliberate deferral as Stamp and the
  DamageStep/HealStep sub-editors. Revisit once the framework's proven out in
  actual play.
- **No behavior-tree/GOAP-style planning** — a tier's goal is a single
  filter+preference over one turn's legal actions, not a multi-turn plan.
- **No learning/adaptation** — profiles are static authored content, not
  tuned at runtime.
- **`EnemyCountInRange`'s "range" is Chebyshev tile distance**, not a
  targeting-mode-aware threat radius — good enough for "are they swarming
  me," not meant to model a specific action's real reach.

## Verification plan (once implementation starts)

- Unit tests per `AiCondition` variant (holds/doesn't-hold against a `scenario{}` fixture).
- `defaultScoredChoice` regression: every existing `ChooseActionTest.kt` case
  must still pass unchanged — this is a pure rename/extraction, not a behavior change.
- A tiered-profile integration test: an archetype with a "heal below 30% HP,
  else attack" profile actually heals when an ally is hurt and attacks
  otherwise, exercised through `chooseAction` end-to-end.
- `CatalogValidatorTest.kt` cases for both new reference checks.
- Live playtest: two archetypes with visibly different authored profiles
  (e.g. one aggressive, one that flees at low HP) behave differently in the
  same encounter — the actual proof this was worth building.
