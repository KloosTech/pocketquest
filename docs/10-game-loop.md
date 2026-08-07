# 10 — The game loop

Scope: everything above a single battle. The combat core (docs 01–09) is the
innermost layer and is not revisited here, except where the loop demands
something it does not yet have — collected in
[17-engine-gaps.md](17-engine-gaps.md).

## The constraint that drives everything

Sessions are 5–30 minutes, sporadic, on a phone, and frequently interrupted.
A run spans many sessions. Two consequences that are easy to underestimate:

1. **A run is always resumed, never completed in one sitting.** Resume is the
   normal path, not an error path.
2. **The player will forget what they were doing.** Every resume must re-establish
   context in about three seconds: who is in the party, how hurt they are, where
   they are on the map, what is next.

## Three layers

| Layer | Lifetime | Owns | Module |
| --- | --- | --- | --- |
| **Meta** | Forever | Champions roster, unlocks, idle accrual, settings | `:core:meta` |
| **Run** | One roguelike attempt | Party, map graph position, inventory, gold, XP | `:core:run` |
| **Encounter** | One battle | `GameState`, `Resolver`, initiative, grid | `:core:rules` |

Strict containment: Meta knows nothing about a `GameState`. Run owns an
encounter only while one is active. The encounter layer stays timeless and
deterministic — see [11-run-state.md](11-run-state.md) for the exact handoff.

**Wall-clock time exists only in Meta.** Idle accrual for champions takes
elapsed real time as an explicit input, computed once on app open. If a clock
reaches `:core:rules`, determinism and replay go with it.

## Run anatomy

```
Character select ─► Act 1 ─► Act 2 ─► Act 3 ─► Boss ─► Champion
   (new or                │        │        │
    from roster)          └─ node graph, player picks the path
```

Node types on the graph:

| Node | What happens |
| --- | --- |
| `Combat` | Standard encounter, scaled to party size and act |
| `Elite` | Harder encounter, better loot |
| `Event` | Text prompt, 2–3 choices, immediate consequences |
| `Rest` | Safe node: recover, potentially level up |
| `Shop` | Spend gold on potions, gear, mana restoration |
| `Boss` | Act finale; the Act 3 boss ends the run |

Events are **not** combat and must not go through the resolver. An event is a
prompt plus a list of outcomes; only its consequences (heal, grant item, start
an encounter, recruit) touch the layers below. Forcing them through the effect
stack would be the same category error as v1's implicit system ordering.

Run ends on: Act 3 boss defeated (**success** → champion), or the whole party
downed (**failure**), or level 12 reached (**success**, the "god status" cap).

## Locked decisions

### Initiative is true, interleaved D&D initiative

Not side-based phases, not free ordering. `TurnState.order` already models this
correctly. Consequences the UI must absorb:

- A **turn order tracker is mandatory**, not decoration. With 3 heroes and 4
  enemies interleaved, the player cannot plan without seeing who acts next.
- The camera moves between allies and enemies constantly. Follow the active
  entity, but only when it leaves a comfortable inner rectangle
  ([07-animation.md](07-animation.md)).
- "Whose turn is it" is the single most important piece of state on screen.

### HP persists for the whole run

Damage carries between encounters. Recovery costs something:

- **Potions** — consumable, bought or looted, usable in and out of combat.
- **Heal skills** — cost mana.

This is what makes the healer role meaningful. If HP reset between fights, the
role would be worthless and the trinity would collapse into damage-only.

### Mana is a per-encounter pool

Mana refills fully **at the end of every combat**. It is a tactical budget for
one fight, not a strategic budget across the run.

The engine does not do this today. In `Handlers.endTurn`:

```kotlin
resources.copy(ap = stats.maxAp, mana = stats.maxMana, ...)
```

Mana is restored at the start of every *turn*, which makes it indistinguishable
from AP and removes any reason to ration a spell across a fight. The reset has
to move from the turn boundary to the encounter boundary.

| Resource | Refills | Role |
| --- | --- | --- |
| **AP** | Every turn | Tactical budget within a turn |
| **Quick / Reaction** | Every turn | Action economy flags |
| **Mana** | End of each encounter | Tactical budget across a fight |
| **HP** | Never automatically | The run's health bar |

Needs a regression test: *mana is unchanged across a turn boundary, and full
again after `finishEncounter`.* Tracked in
[17-engine-gaps.md](17-engine-gaps.md).

#### The consequence: out-of-combat healing is potions only

If mana is free after every fight, a heal spell cast in the corridor costs
nothing, and HP persisting across the run would mean nothing. So healing skills
are **combat-only**. Between encounters, HP comes back through potions and
`Rest` nodes, both of which are scarce inventory or map resources.

This is coherent, and it puts a real cost on both sides:

- **In combat**, a heal costs mana *and* the action — the healer is not dealing
  damage that turn. The tempo is the price.
- **Out of combat**, a heal costs a potion, which is finite and competes with
  gold spent on gear.

It also simplifies [11-run-state.md](11-run-state.md): the out-of-combat action
subset shrinks to consumables, so no part of `:core:rules`' action machinery
needs to run outside an encounter.

This does mean the healer's value is concentrated inside fights, which is where
it should be for a tactics game — topping the party up afterwards is a shopping
decision, not a tactical one.

### Companions are per-character AI/manual toggleable

Each party member carries `Controller.Human` or `Controller.Ai`, switchable from
the party sheet. The engine already supports this: only `HumanUi` answerers
leave the resolver loop, so an AI-controlled ally's entire turn resolves in one
`run()` call and yields one event list for the animation player.

This is a comfort feature, not a fix for turn bloat — a fully manual party of
three already fits the session budget. See the budget below.

Two implementation notes:

- Toggling the controller **mutates `GameState`**, so it must go through a
  command, not a side channel. Otherwise replay and the command log diverge.
- The toggle must be reachable mid-battle, and taking manual control should be
  possible on the character's own turn before it auto-resolves. Give the
  auto-turn a short, skippable "taking over?" grace window rather than resolving
  instantly.

## Party

Starts at one character. Grows to a maximum of **three** (see the time budget —
four does not fit the session constraint). Recruitment is a run reward: an
`Event` or `Rest` node offering a companion.

Target roles are the standard trinity, but they must be expressible on a grid:

**Tank.** There is no aggro table in a tactics game. Tanking works through three
mechanisms, two of which already exist:

- Body-blocking — `blocksMovement` ✓
- Opportunity attacks — reactions ✓
- **Taunt** — a status flag that constrains AI target selection ✗

Without the third, a tank is just a character with more HP that every enemy
walks around. `:core:ai`'s `ChooseAction` must read a `Flag.Taunted` and
restrict its target set accordingly.

**Damage.** Needs no new mechanism.

**Healer.** Needs `Heal` ✓ plus scarce mana (above) plus out-of-combat casting,
which means actions must be performable outside an encounter — currently
`canPerform` gates on `state.turn`, which does not exist between battles.

### Downed, not dead

With a party, one character at 0 HP must not end the run. The model has no such
distinction today — `health.current == 0` emits `Died` and that is all.

Required:

- `Downed` state at 0 HP: cannot act, cannot be targeted by most actions, still
  occupies its tile.
- Revival: an ally action or a potion restores it at low HP.
- The run fails only when **all** party members are downed simultaneously.
- Downed characters that survive the encounter get up at 1 HP afterwards.

Death saves are deliberately out of scope for v1 — they add a per-turn ritual
that costs session time and gives the player nothing to decide.

## Time budget

**Enemies are always AI-controlled.** The player never drives them; there is no
mode in which they do. Only party members are ever manual, and only those with
`Controller.Human`.

```
turns per round      = party size + enemy count
manual hero turn     ≈ 10–25 s, depending on how tricky the board is
auto-resolved turn   ≈ 3 s (decision is instant; the time is animation)
```

At 3 heroes and 4 enemies, **every hero manual**:
3 × 18 + 4 × 3 ≈ 66 s per round, four rounds ≈ **4.4 minutes**. That fits a
session comfortably.

With two heroes on AI: 18 + 6 + 12 ≈ 36 s per round ≈ **2.4 minutes**.

Two things follow, and the first corrects an earlier assumption in this
document:

**The companion toggle is not load-bearing for session length.** A fully manual
party of three already fits. The toggle is a pacing and convenience feature —
one-handed play on a commute, less decision fatigue in trash fights, a gentler
on-ramp for new players — not the thing that makes the session constraint work.
It should be designed as a comfort option, not defended as a necessity.

**The real variable is the player's thinking time, not the turn count.** 18 s is
a guess and the spread is wide: a trivial turn is 8 s, a turn where three
characters must be sequenced around an AoE is 40 s. Board complexity costs more
minutes than entity count does.

Design rules:

- Cap encounters at **4–5 enemies**, target **3–4 rounds**. The limit is screen
  readability on a phone and the size of the tactical problem, not the clock.
- Party cap of **3**. Also not a time constraint: it is what fits in a party bar
  next to an initiative tracker, and what keeps a turn's decision space
  tractable on a small screen.
- Auto-resolved turns are animation-bound, so the `speed` scale from
  [07-animation.md](07-animation.md) is what actually shortens them. AI turns run
  at 2–3× and are skippable.
- Reaction prompts pull the player into enemy turns. With true interleaved
  initiative and opportunity attacks this is not rare, and it is not in the model
  above. Watch it during playtesting before adding enemies.

## Session boundaries

The player quits mid-anything. Two categories:

- **Safe points** — between nodes, at a `Rest`, in a shop. Cheap to save, trivial
  to resume, and the UI can show a full recap.
- **Mid-encounter** — including mid-turn and mid-reaction-prompt. The serialized
  `Resolver` covers this exactly ([06-persistence.md](06-persistence.md)); this
  is the payoff for having built it that way.

There is no manual save. Autosave at: node transitions, end of each turn, on
`AwaitingInput`, and in `onStop()`.

Resume rules:

- Resuming mid-encounter replays no animation. Load the `Resolver`, call
  `settle()`, show whose turn it is.
- Resuming shows a one-screen recap: party portraits with HP, current act and
  node, and the next decision.

## What a run produces

On success, the character joins the **Champions** roster with its final level,
gear and a record of the run. Champions are a Meta-layer resource that generates
passive income over wall-clock time and can be sent on background missions.

Open decision: whether the whole surviving party is promoted or only the
character the run started with. Promoting all three is more generous and makes
recruitment more valuable; promoting one keeps the roster meaningful and matches
the "champion" framing. Not decided here.

## Next documents

- [11-run-state.md](11-run-state.md) — the data model for all of the above
- [13-encounters-and-events.md](13-encounters-and-events.md) — node graph and event schema
- [17-engine-gaps.md](17-engine-gaps.md) — the engine work this document implies
