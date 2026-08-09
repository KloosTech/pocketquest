# 12 — Progression (the Meta layer)

The forever-lived layer above a run — [10-game-loop.md](10-game-loop.md)'s
`:core:meta`. Owns the Champions roster, the permanent currency stash, and
unlocks. Knows nothing about `GameState`; the only layer allowed to take
wall-clock time as an input.

No leveling anywhere in this game — see [10-game-loop.md](10-game-loop.md)'s
"No leveling." Progression here means **roster growth and gear**, not
character stats climbing over time.

## Shape

```kotlin
@Serializable
data class MetaState(
    val roster: Map<ChampionId, ChampionRecord>,
    val bank: Int,                        // the one permanent currency stash
    val unlocks: Set<Unlock>,
    val schemaVersion: Int = CURRENT_META_SCHEMA,
)

@Serializable
data class ChampionRecord(
    val id: ChampionId,
    val name: String,
    val archetype: ArchetypeId,
    val equipment: Equipment,             // persists between runs — the only thing that does
    val status: ChampionStatus,
    val lastAccrualAt: Long,              // epoch millis; idle income computed from the delta on app open
)

@Serializable
enum class ChampionStatus { Available, OnRun, OnMission }
// OnMission has no content yet — it's the hook for whatever future
// wall-clock/idle feature sends a Champion off-roster temporarily. Not
// designed further here; Available/OnRun is all doc10's loop needs today.

@Serializable
enum class Unlock { PartyMode }
// A plain enum, not a generic "feature flag" system — deliberately small.
// Add a case per future unlock (new archetype available, new shop tier,
// whatever) rather than generalizing ahead of a second real example.
```

`ChampionId` is stable across a Champion's whole life — it's what `PartyMember.memberId`
carries during a run ([11-run-state.md](11-run-state.md)), so the handoff back
is a lookup, not a name match.

## Bootstrapping the roster: the first character

There is no roster until one exists. Character creation (pick an archetype,
name it) happens once, outside any run, and produces a `ChampionRecord` with
`status = OnRun` the moment its solo run starts — *not* `Available` first,
since it has nowhere to be available yet. This is the one case where a
`ChampionRecord` is created before a run rather than selected from an
existing roster.

Exact character-creation UI (which archetypes are offered at the very start,
whether there's a naming screen) is a [14-ui-shell.md](14-ui-shell.md)
concern, not decided here.

## Unlocking Party mode

The solo run's `RunOutcome`:

- **Success** → the character becomes the roster's first `ChampionRecord`
  (`status = Available`), and `Unlock.PartyMode` is added to `unlocks`. Every
  run after this one picks up to 3 Champions from the roster
  ([10-game-loop.md](10-game-loop.md)).
- **Failure** → permadeath applies same as any other run
  ([10-game-loop.md](10-game-loop.md)) — the character is gone, the roster is
  still empty, `Unlock.PartyMode` is not granted. The player creates a new
  first character and tries again.

## Currency: one bank, two income streams

`bank: Int` is the only permanent currency. It's credited by:

1. **Run completion** — `RunOutcome.Success` deposits that run's entire
   `RunState.gold` in one lump sum ([11-run-state.md](11-run-state.md)'s
   Champions handoff). A `Failure` deposits nothing — the run's gold was
   never banked, so there's nothing to lose *from the bank*, but nothing
   gained either.
2. **Idle accrual** — deferred entirely for now. `ChampionRecord.lastAccrualAt`
   stays on the schema (it's cheap to keep and expensive to add to a shipped
   save later) but nothing reads it yet — no formula, no passive income, only
   the run-completion deposit feeds the bank until this gets its own pass.

What the bank buys: nothing decided here either — likely permanent unlocks
(future `Unlock` cases) and/or a meta-shop distinct from the run's own Shop
nodes ([13-encounters-and-events.md](13-encounters-and-events.md)). Open for
now; the mechanic (one bank, two income streams) is the part that's load-bearing.

## Permadeath, from the Meta side

[10-game-loop.md](10-game-loop.md) owns the *why*; this is the *what changes
here*: on `RunOutcome.Failure`, every `ChampionId` that was in `run.party` is
removed from `roster` entirely. No "recovering" status, no cooldown — gone.
If that empties the roster back to zero (every Champion was on this run and
died), the game is back to "create a first character" — and this time
`Unlock.PartyMode` is revoked along with it. A fresh Champion after a total
wipe goes back through the same solo-gate flow as the very first character,
not straight to roster-eligible: losing the whole roster is meant to sting,
and re-earning `Unlock.PartyMode` is what makes the sting mean something.

This is a **deliberate exception** to `Unlock`'s general monotonicity, not a
loophole in it — `checkUnlockMonotonicity` (`core/meta/Invariants.kt`) today
enforces a strict "never revoked" rule with no carve-out, so the Pass 7 code
path that empties `roster` to zero must revoke `Unlock.PartyMode` as its own
explicit step *outside* whatever calls `checkUnlockMonotonicity` on ordinary
transitions — not by weakening that checker to permit revocation generally,
which would silently allow a real future bug to slip through unnoticed.

## Persistence

One row, reusing [06-persistence.md](06-persistence.md)'s machinery — there's
only ever one `MetaState` per install, no slots:

```kotlin
@Entity(tableName = "meta_state")
data class MetaStateRow(
    @PrimaryKey val id: Int = 0,          // singleton row
    val schemaVersion: Int,
    val updatedAt: Long,
    val snapshot: ByteArray,              // serialized MetaState
)
```

## Invariants

1. Every `ChampionId` in `unlocks`-gated content and every `RunState.party`
   entry resolves to a real `roster` entry while `status == OnRun`.
2. A `ChampionRecord` with `status == OnRun` corresponds to exactly one live
   `RunState.runId` — never two runs claiming the same Champion.
3. `bank >= 0` always — nothing in this layer can spend more than it has.
4. `Unlock.PartyMode in unlocks` is monotonic: once added, never removed by
   any code path, including permadeath wiping the roster to empty.
