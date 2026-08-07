# 15 — Battle UI

The screen where the engine becomes a game. Everything here lives in `:ui` and
consumes `GameState`, `legalTargets()`, `canPerform()`, `preview()` and the
event stream — never its own copy of a rule.

Visual language (tile size, scaling, highlight styling) is in
[16-art-direction.md](16-art-direction.md). This document is layout, state and
interaction.

## Screen anatomy — portrait phone

```
┌─────────────────────────────┐
│ ◀ turn order strip        ⚙ │   56 dp   who acts next, always visible
├─────────────────────────────┤
│                             │
│                             │
│         board               │   flex    pan + zoom, culled to viewport
│      (one Canvas)           │
│                             │
│                             │
├─────────────────────────────┤
│ party bar                   │   64 dp   3 portraits, HP/mana, controller
├─────────────────────────────┤
│ bottom sheet                │   peek 120 dp / expanded 45 %
└─────────────────────────────┘
```

Three constraints drive this layout:

**The turn order strip is not decoration.** With true interleaved initiative and
up to eight actors, the player cannot plan a turn without seeing the order. It
stays pinned at the top and never scrolls away.

**The bottom third is the thumb zone.** The board's lower portion is covered by
the sheet whenever it expands, so the camera must keep the active character in
the **upper-middle** of the viewport, not centred. Centring puts the character
under the player's own hand.

**The party bar reads from `GameState` during combat, not from `RunState`.**
This is invariant 8 in [11-run-state.md](11-run-state.md) and the most likely
place to violate it — `PartyMember.hp` is stale by design while an encounter is
live.

Landscape moves the party bar and sheet to the right edge and widens the board;
same states, different placement.

## Bottom sheet — three distinct states

The sheet is the primary information surface, and it does three different jobs.
They must not look alike.

### Peek — the default

Active character: name, HP, mana, AP, statuses, and the action bar. Present
whenever it is a human-controlled character's turn. Dismissible only to
half-height, never fully.

### Inspect — the player tapped something

Details of the tapped entity or tile: stats, statuses with remaining duration,
terrain. For an enemy, also its threat range and last action. Read-only.
Dismissed by tapping the board or swiping down; returns to Peek.

### Prompt — the engine is asking

`StepResult.AwaitingInput`. This is a mode change, not a content change, and it
needs to look like one:

- Board dims behind the sheet.
- Sheet expands and **cannot be dismissed** — there is no "away" tap.
- The question is stated plainly with the trigger that caused it
  ("Goblin leaves your reach — attack of opportunity?").
- Two buttons, plus a persistent "don't ask again this fight" affordance that
  writes an `Answerer.Auto` policy (gap 3.8 in
  [17-engine-gaps.md](17-engine-gaps.md)).

Sequencing matters: the prompt appears only after `awaitDrained()` and
`settle()` ([07-animation.md](07-animation.md)). Asking "attack of opportunity?"
while the figure is still visibly three tiles away is disorienting, and it is
easy to get wrong because the resolver returns the request before the animation
has played.

## Targeting state machine

Mobile has no hover, so every commit needs an explicit confirmation step. That
is also what replaces undo — see below.

```
        ┌──────────────────────────────────────────┐
        │                  Idle                    │
        └──┬─────────────┬──────────────┬──────────┘
   tap own │    tap enemy│     tap action
   char/   │      /cell  │       in bar
   empty   ▼             ▼              ▼
        Inspect       Inspect      ActionSelected
                                        │  legalTargets() highlighted
                                   tap a legal tile
                                        ▼
                                   TargetPicked
                                        │  preview() runs
                                        │  affected tiles + expected outcome
                             ┌──────────┴──────────┐
                        Confirm                 Cancel / tap elsewhere
                             ▼                       ▼
                        perform()                  Idle
                             ▼
                     events → animation → Idle
```

Movement is the same path with `ActionCost.Movement`: tap a reachable tile,
the path draws with its cost, tap again (or Confirm) to commit. Re-tapping a
different tile before confirming just re-plans — free, no state change.

Two rules that fall out:

- **Nothing mutates before Confirm.** `preview()` runs the real resolver in
  `Expected` mode against an immutable state and throws the result away, so the
  numbers shown are the numbers the engine will produce
  ([05-actions-and-effects.md](05-actions-and-effects.md)).
- **`canPerform()` drives both the disabled state and the tooltip.** An
  unavailable action is greyed with its `Rejection` rendered as the reason —
  "Not enough mana (need 3, have 1)", not a silent dead button. This is the
  single highest-value use of the rejection list.

### No undo

Confirm-before-commit is the answer. A roguelike with undo has no stakes, and
the immutable state makes undo tempting precisely because it is cheap. Resist
it. What the player actually needs is the ability to *re-plan freely before
committing*, which the state machine above gives them.

## Tap targets and the small-tile problem

At a comfortable zoom a tile is 40–48 dp. Material's minimum touch target is
48 dp, so tiles sit at or below the threshold, and a tactics game punishes
mis-taps harshly.

Mitigations, all three needed:

- **Snap to nearest tile centre** within a tolerance, rather than strict
  hit-testing. A tap 6 dp outside a tile still resolves to it.
- **Prefer legal targets.** When a tap is ambiguous between two tiles and only
  one is a legal target for the current action, pick that one.
- **Confirm step** absorbs the rest. A mis-tap costs a second tap, not a turn.

## Highlight modes

Colour alone is insufficient: the board is parchment, the props are black ink,
and roughly one player in twelve has a colour vision deficiency. Every mode
carries a **shape or pattern** as well as a hue.

| Mode | Treatment |
| --- | --- |
| Reachable | Dotted outline, faint warm tint |
| Path | Dotted line along the route, cost pips at the destination |
| Single target | Solid outline plus crosshair on the tile |
| AoE | Hatched fill across every affected tile |
| Affected entity | Ring around the token, inside the AoE fill |
| Enemy threat | Diagonal hatch, only while the threat overlay is on |
| Invalid | No highlight at all — never a red "you can't" state |

Full styling in [16-art-direction.md](16-art-direction.md).

## Threat overlay, and the intent question

A toggle that hatches every tile an enemy could reach and attack next turn.
Computable today from pathfinding plus `legalTargets()`. In tactics games this
is the highest-value quality-of-life feature there is, and it is cheap.

There is a stronger version worth considering and **not** adopting silently:
because `:core:ai` is a deterministic consumer of `preview()`, we could show
each enemy's *actual intended action*, Into the Breach style — arrows showing
exactly who attacks whom.

The catch is real: it only works if the AI commits. The intent has to be decided
at the end of the previous round and then honoured, even after the player moves
and invalidates it. That is a design constraint on the AI, not a UI feature, and
it changes the game's character — from "read the threat" to "solve the puzzle".
Worth deciding deliberately. The plain threat overlay needs no such commitment
and should ship first.

## Camera

- Follows the active entity, but only when it leaves a comfortable inner
  rectangle — continuous centring is nauseating
  ([07-animation.md](07-animation.md)).
- **Never moves while the player is in `ActionSelected` or `TargetPicked`.**
  Moving the board under a targeting gesture is the fastest way to cause a
  mis-tap.
- During AI turns, pans to keep both the actor and its target on screen; if they
  do not both fit, prioritise the target.
- A "centre on active" button in the turn strip for when the player has panned
  away.

## Controller toggle and the grace window

Each party portrait carries an AI/manual toggle. Flipping it goes through a
command, not a side channel — it mutates `GameState`
([10-game-loop.md](10-game-loop.md)).

When an AI-controlled companion's turn begins, do not resolve instantly. Show a
short, skippable window (~1.5 s) in the turn strip offering "take over". Two
reasons: the player often wants manual control for exactly one turn, and an
instant resolve makes the companion's turn feel like something that happened
*to* them rather than in their party.

## Things the engine emits that need a visual

Easy to forget, because they have no obvious home:

- **`DamageRedirected`** — an arc from the original target to the tank, then the
  number lands on the tank. Without it the player sees damage on a character
  nobody attacked ([18-damage-pipeline.md](18-damage-pipeline.md)).
- **`Fizzled`** — a blocked flash on the affected tile plus a log line. Silent
  no-ops are the v1 failure mode this whole architecture exists to avoid.
- **Downed** — token drops to a low-contrast, face-down state. It still occupies
  its tile, which the player must be able to see, so it cannot simply vanish.
- **`ReactionTriggered`** — a brief marker on the reactor before its attack
  animation, so an interruption reads as an interruption.
- **Status application and expiry** — small icons on the token, with the full
  list and durations in Inspect.

## Battle log

A scrollable log, reachable from the turn strip, rendered from the `GameEvent`
list. Cheap to build, and it is the only way a player can answer "why did that
happen" — especially with reactions, redirects and saves firing without their
input. Include the dice: `AttackRolled` already carries `d20`, `mod`, `ac` and
`hit`.

## Rendering

Per [07-animation.md](07-animation.md): the board is **one `Canvas`**, not a
composable per tile. On a 44×32 map that is 1408 tiles and a composable each is
not viable.

- Terrain, props, grid and highlights draw in `drawBehind`.
- **Cull to the viewport** — draw only visible tiles plus one row of margin.
- Only entities get their own layer, because only they animate independently;
  they position through the lambda form of `graphicsLayer` so movement never
  triggers recomposition.
- Static terrain and props can be pre-composited into a cached layer that is
  invalidated only when the map changes, which is never during a battle.

## Open questions

- **Zoom levels**: free pinch, or snapped steps? Snapped steps keep pixel art on
  integer scale factors (see doc 16) but feel stiffer.
- **Enemy inspect depth**: full stat block, or only what the player has observed?
  Hiding numbers adds mystery and costs clarity in a game built on numbers.
- **Portrait vs. landscape as primary**: this document assumes portrait
  (one-handed, commute). Landscape gives a far better board.
