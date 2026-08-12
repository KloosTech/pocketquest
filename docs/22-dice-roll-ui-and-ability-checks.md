# 22 — Dice roll UI and out-of-combat ability checks

## Context

The dice tumble (docs from this session's fog-of-war/exploration work, not yet
its own numbered doc) currently shows only the raw d20 face — no target
number, no sense of what beat what, no modifier breakdown. Baldur's Gate 3's
roll card is the reference point: a DC/AC banner, the die(s) (two side by
side under advantage/disadvantage, higher one highlighted), and a row of
labeled modifier chips underneath (`+4 Charisma`, `+3 Deception Proficiency`,
`+3 Deception Expertise`, `+1d4 Guidance`) that sum to the total.

Two asks, tied together because they share the same roll-card UI:

1. Upgrade the existing combat roll card (attack/save) to show the target
   (AC for attacks, DC for saves) and whatever modifier breakdown is
   available.
2. Build out-of-combat **ability checks** as a real feature — an archetype's
   base ability score plus every modifier the character has earned (items,
   proficiency, statuses) rolled against a DC, outside combat.

This doc is written before any of it is implemented, per the project's
"discuss open questions before building" convention for anything this size.

## What exists today

Traced end to end (not guessed) through `core/rules/resolver/Handlers.kt`,
`core/rules/stat/StatsDerivation.kt`, `core/model/Modifier.kt`,
`core/model/Event.kt`, `core/run/EventEffects.kt`, and the `:ui` dice files.

**Combat rolls** (`rollAttack`/`rollSave`/`concentrationCheck` in
`Handlers.kt`) each collapse to exactly two numbers on their `GameEvent`:
`d20` and a single flat `mod`. Nothing upstream of that event carries a
breakdown:

- **Attack bonus is hand-authored, not derived.** `ActionDef.attackBonus`
  (e.g. `"attackBonus": 5` in a content JSON file) is typed in directly by
  whoever authors the action. It is never computed from the attacker's
  ability score, proficiency, or equipment — there is no live connection
  between "this character is Strong" and "this attack rolls +5." A modifier
  row for an attack today would only ever be able to show one line, the
  flat authored number, because that's all that exists.
- **Save modifier *is* derived** (`abilityModifier(targetStats.abilities.forAbility(effect.ability))`)
  but still collapses to one `Int` before the event — by the time `stats()`
  finishes folding ability score + item modifiers + status modifiers, which
  source contributed how much is already gone.

**The modifier system** (`StatsDerivation.kt`) accumulates every
`Modifier.Add`/`Mul`/`Override` from archetype → features → equipment →
statuses into one running total per `Stat` (Str, Dex, AC, ...). It's a fold,
not a ledger — nothing survives that says "the +1 AC came from this ring."
The **one** exception is `Modifier.Roll(ctx: RollContext, side: AdvSide)`,
which stays an unresolved list (`Stats.rollGrants`) matched against the
actual roll's `RollContext` at roll time — but it only carries advantage/
disadvantage, not a numeric or dice bonus.

**No proficiency model exists at all.** No `Stat.ProficiencyBonus`, no
per-archetype "proficient in Persuasion" set, nothing. `Skill` (the full 5e
list — Persuasion, Deception, Perception, etc.) exists as an enum and
`RollContext.AbilityCheck(skill: Skill)` exists as a type, but grepping the
whole repo turns up zero live callers — nothing has ever constructed one at
an actual roll site.

**No bonus-dice-on-a-roll mechanism exists.** A Guidance-style "+1d4 to your
next check" status is not a gap in surfacing — it's a data shape that has
never been built. `DiceSpec` (the dice-count/sides/modifier type) is only
ever used for weapon/spell *damage*, never wired into a d20 roll's modifier.

**`:core:run`'s Events system already does most of the out-of-combat check
mechanically**, and it's real, not a stub — `EventChoice.check: EventCheck?`
(`EventCheck(ability: Ability, dc: Int)`), resolved in
`resolveEventChoice` (`core/run/EventEffects.kt:69`):

```kotlin
val scoreByMember = run.party.associateWith { it.toEntity(cat).stats(cat).abilities.forAbility(check.ability) }
val roller = scoreByMember.maxBy { it.value }.key
val mod = abilityModifier(scoreByMember.getValue(roller))
val (advancedRng, rollValue) = run.rng.d20()
val success = rollValue + mod >= check.dc
```

Gaps relative to what BG3's card implies and what combat's own rolls already
do better:

- **Ability-only, not skill-based** — rolls raw Str/Dex/Con/Int/Wis/Cha, never
  a specific skill like Deception or Perception, even though `Skill` exists.
- **No advantage support at all** — plain `run.rng.d20()`, doesn't consult
  `rollGrants`/`Modifier.Roll` the way `rollAttack`/`rollSave` do.
- **Auto-picks "whichever party member scores highest"** rather than letting
  the player choose who attempts it.

**The `:ui` dice card carries almost nothing today.**
`DiceRollOverlay(val id: Long, val result: Int)` and
`DiceRoll(result: Int, trigger: Any, world: VisualWorld, modifier: Modifier)`
only ever see the raw face value. `Director.kt`'s `showDiceRoll` beats for
`AttackRolled`/`SaveRolled` pass `event.d20` and silently drop `event.mod`/
`event.ac`/`event.hit`/`event.dc`/`event.success`, all of which already exist
on the `GameEvent` — that much is pure plumbing, not new data.

## Proposed shape

### A shared roll-breakdown model

A new type, living in `core/model` next to `Modifier`/`RollContext`:

```kotlin
data class RollTerm(val label: String, val flat: Int = 0, val dice: DiceSpec? = null)
data class RollBreakdown(val terms: List<RollTerm>)
```

`GameEvent.AttackRolled`/`SaveRolled`, and a new `AbilityCheckRolled` for
out-of-combat checks, gain a `breakdown: RollBreakdown` field. This is the
one piece every part of this feature depends on — without it there's nothing
for the modifier-chip row to iterate over.

How deep the breakdown goes is an open question (#1 below) — the cheapest
version that's still honest is: one term for the ability modifier, one for
proficiency (once that exists), one flat catch-all term for "everything else
from items/statuses" rather than fully unwinding `StatsDerivation`'s entire
fold per source. Fully tracing every individual item/status contributor
would mean turning that fold into a ledger everywhere, not just at roll
sites — a much bigger change than this feature needs.

### DC/AC target + success framing

The roll card shows the number being beaten (AC for attacks, DC for saves
and checks) up front, before or alongside the die, matching BG3's banner.
Once the roll resolves, the card can tint/mark hit-vs-miss or success-vs-
failure the same way the existing pulse/flash beats already signal outcomes
elsewhere.

### Advantage/disadvantage

`rollGrants`/`Modifier.Roll`/`AdvSide` already exist and already drive
`rollAttack`/`rollSave`. The UI side just needs `DiceRollOverlay` to carry
both d20 results when advantage/disadvantage applied, and `DiceRoll` to lay
out two dice with the used one highlighted (second BG3 screenshot) — no new
engine mechanism needed, only wiring the existing one through to the event
and the card.

### Out-of-combat ability checks

Builds on `EventCheck`/`resolveEventChoice` rather than replacing it:

- `EventCheck` gains a skill (open question #6 covers whether that's
  mandatory or ability checks without a specific skill stay legal, matching
  5e's own "sometimes you just roll raw Wisdom").
- `resolveEventChoice`'s roll site starts constructing a real
  `RollContext.AbilityCheck(skill)`/`SavingThrow`-shaped context and
  consulting `rollGrants`, so advantage from items/statuses actually applies
  out of combat the same as it does in combat.
- The roll produces a `RollBreakdown` the same shape combat rolls do, so one
  card renderer serves both contexts.

### Manual roll trigger for checks

Combat rolls stay fully automatic — Timing.Blocking beats already pace
combat, and making the player tap through every attack would be a real
pacing regression. An out-of-combat check is a different moment: the player
already chose to attempt something deliberate. BG3's own card shows the DC
and modifiers *before* the die lands, with an explicit "click dice to roll."
That implies `resolveEventChoice`-equivalent logic needs to split into a
**preview** step (compute DC + breakdown, no roll yet, no state mutation)
and a **commit** step (actually roll, apply the branch) — open question #4.

## Open design questions

**1. How deep does the breakdown go — and does this fix attack-bonus
authoring too?**
Saves and checks can get a real breakdown today (ability mod is already
derived live). Attacks can't, without also changing how `attackBonus` is
authored — right now it's one flat number typed into content JSON, wired to
nothing. Fixing that so an attack's modifier row shows real terms (ability +
proficiency + weapon bonus) means deriving `attackBonus` from the attacker's
stats instead of authoring it directly, which touches every existing action
in `content/catalog.json` and is a combat-balance change, not just a UI one.
Options: (a) leave attacks with their one flat authored term forever — the
card still shows the AC target either way, just a shorter modifier row; (b)
derive attack bonus properly, as its own follow-up pass, separate from this
feature; (c) do it as part of this same feature.

**2. Proficiency model.**
BG3 shows Proficiency and Expertise (double proficiency) as separate chips,
both scaled by character level. PocketQuest has no character-level concept
tied to archetypes today. Options: (a) full 5e-shaped proficiency (a
level-derived bonus + per-skill proficient/expert flags) — the most
"authentic" but needs a level system this project may not want yet; (b) a
flat, simpler per-skill bonus authored directly on the archetype (no level
math, just "this archetype gets +3 to Deception checks") — much smaller, but
diverges from 5e math; (c) skip proficiency entirely for v1 and only ever
show ability modifier + item/status terms.

**3. Who attempts an out-of-combat check?**
Today `resolveEventChoice` silently picks whichever party member scores
highest. Options: (a) keep that — simplest, already built; (b) let the
player choose who attempts it (more agency, closer to how a tabletop game
actually plays, and to BG3's own "whoever's in the conversation" framing).

**4. Manual roll trigger — build the preview/commit split, or keep checks
auto-resolving like today?**
Ties directly to "Manual roll trigger for checks" above. The BG3 reference
images specifically show a moment where the DC/modifiers are visible and the
player clicks to roll — matching that means real API surface changes to
`resolveEventChoice`. Keeping it auto-resolving (roll happens the instant a
choice is picked, card just narrates it after the fact) is a smaller change
that still gets the visual upgrade, just without the "click to roll" beat.

**5. Bonus-dice modifiers (Guidance-style).**
No status like this exists anywhere in current content, and nothing wires
`DiceSpec` into a roll modifier today. Options: (a) build the real mechanism
now (a new `Modifier` case, a status that grants it, wiring at every roll
site) even though nothing uses it yet; (b) design `RollTerm.dice` into the
data model now (so the UI never needs rework later) but don't author any
content that produces one yet — ships the visual capability without the
gameplay mechanic attached.

**6. Scope boundary — Events only, or freestanding checks too?**
Every DC roll that exists or is discussed here is `EventChoice.check`. There
is no mechanism today for a check that isn't attached to an Event (e.g. a
free-standing "pick this lock" interaction during exploration, not gated
behind dialogue). Confirming this feature's scope is "checks as they already
exist inside Events" and not a broader "any exploration action can demand a
roll" system.

## Non-goals

- No changes to combat's core hit/damage math — this is purely about what
  gets *shown*, plus the new out-of-combat check mechanic. Attack-bonus
  derivation (question #1) is explicitly a fork, not an assumed yes.
- No new persistent run-scoped status/buff system. `RunEffect.GrantStatus`
  (flagged as "an obvious next case" in `Event.kt`'s own doc comment) stays
  out of scope unless a bonus-dice status (question #5) specifically needs
  it to exist.
