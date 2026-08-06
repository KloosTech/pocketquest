# 04 — The resolver: effect stack and decisions

This is the backbone. Everything else plugs into it.

## The requirement that shapes the design

A player triggers an opportunity attack, gets a "use your reaction?" dialog,
and puts the phone down. Android kills the process. When they come back, the
half-finished move must continue from exactly where it stopped.

That rules out coroutine suspension, callbacks and continuation lambdas as the
pause mechanism — none of them survive a process death. The paused state must
be **plain serializable data**.

## The resolver is itself a data object

```kotlin
@Serializable
data class Resolver(
    val state: GameState,
    val stack: List<Effect>,                 // LIFO — [0] is next
    val pending: DecisionRequest? = null,
    val answers: Map<DecisionId, Decision> = emptyMap(),
    val emitted: List<GameEvent> = emptyList(),
    val steps: Int = 0,                      // loop guard
    val depth: Int = 0,                      // reaction nesting guard
)
```

Saving mid-reaction is not a special case: the whole `Resolver` is the
snapshot. See [06-persistence.md](06-persistence.md).

## Effects never block — they split

The key trick. An effect that needs an answer does not wait. It pushes an `Ask`
plus a continuation effect that will read the answer later, and returns.

```kotlin
sealed interface Effect

data class Ask(val request: DecisionRequest) : Effect

data class ResolveReaction(
    val id: DecisionId,
    val trigger: GameEvent,
    val who: EntityId,
) : Effect
```

No lambdas in the state, no continuation objects — only data referencing a
`DecisionId`.

## The loop

```kotlin
sealed interface StepResult {
    val resolver: Resolver
    data class Completed(override val resolver: Resolver) : StepResult
    data class AwaitingInput(override val resolver: Resolver,
                             val request: DecisionRequest) : StepResult
    data class Rejected(override val resolver: Resolver,
                        val reasons: List<Rejection>) : StepResult
}

tailrec fun run(r: Resolver, cat: Catalog): StepResult {
    r.pending?.let { return StepResult.AwaitingInput(r, it) }
    if (r.stack.isEmpty()) return StepResult.Completed(r)
    check(r.steps < MAX_STEPS) { "effect loop: ${r.stack.take(5)}" }

    val head = r.stack.first()
    val rest = r.stack.drop(1)

    if (head is Ask) {
        return StepResult.AwaitingInput(
            r.copy(stack = rest, pending = head.request), head.request
        )
    }

    val out = handlerFor(head).apply(r.state, head, r.answers, cat)
    val triggered = collectTriggers(out.state, out.events, r.depth)

    return run(
        r.copy(
            state   = out.state,
            stack   = out.spawn + triggered + rest,   // new work goes to the FRONT
            emitted = r.emitted + out.events,
            steps   = r.steps + 1,
        ),
        cat,
    )
}

fun resume(r: Resolver, id: DecisionId, d: Decision, cat: Catalog): StepResult {
    require(r.pending?.id == id) { "stale decision answer" }   // double-tap guard
    return run(r.copy(pending = null, answers = r.answers + (id to d)), cat)
}
```

`out.spawn + triggered + rest` is the whole semantics of interruption in one
line: reactions run first, the remainder of the interrupted action waits
underneath.

## Self-continuing effects

Movement must not loop inside a handler — a `for` loop cannot be interrupted by
an opportunity attack. Instead the effect re-pushes itself:

```kotlin
data class MoveAlong(
    val who: EntityId,
    val path: List<GridPos>,
    val index: Int,
) : Effect

// handler:
//   move one step, emit MoveStepped
//   spawn = if (index + 1 < path.size) listOf(copy(index = index + 1)) else emptyList()
```

The opportunity attack pushes in front; `MoveAlong(index + 1)` sits below and
resumes afterwards. The same pattern covers multi-target spells, multiattack
and chained saves.

## Reaction windows

```kotlin
fun collectTriggers(s: GameState, events: List<GameEvent>, depth: Int): List<Effect> {
    if (depth >= MAX_REACTION_DEPTH) return emptyList()
    return events.flatMap { ev ->
        s.entities
            .filter { it.canReactTo(ev, s) }
            .sortedWith(compareBy({ s.turn.order.indexOf(it.id) }, { it.id.raw }))
            .map { OfferReaction(ev, it.id, depth + 1) }
    }
}
```

Three things are load-bearing here:

- **Sorting by initiative index then `EntityId`.** Never rely on collection
  iteration order; that is how identical seeds start producing different
  results.
- **Depth limit.** Reactions trigger reactions. Two creatures with *Shield*
  will loop forever without one.
- **One reaction per entity per triggering event.** Track
  `(EntityId, eventId)` pairs in the resolver for the duration of the step.

`OfferReaction` consults the answerer policy *before* pushing an `Ask`,
otherwise the player gets a dialog on every single movement step.

## Who answers a decision

```kotlin
sealed interface Answerer {
    data object HumanUi : Answerer                     // → AwaitingInput
    data class Ai(val profile: AiProfileId) : Answerer // resolved inline
    data class Auto(val rule: AutoRule) : Answerer     // e.g. NeverOfferReactions
}
```

Only `HumanUi` leaves the loop. A full enemy turn therefore resolves in a
single `run()` call and yields one event list — which is exactly what the
animation player consumes.

`Auto` covers a real usability need: a per-reaction toggle ("always use Shield
when it would change the outcome", "never ask about opportunity attacks").

## Turn boundaries

Order is mandatory, and getting it wrong is silent:

```
1. resolve expiries matching StartOfTurnOf(active, round)
2. recompute Stats                         ← after (1), before (3)
3. reset resources to Stats maxima
4. tick start-of-turn statuses (regeneration, damage over time)
5. → Main phase: commands accepted
6. resolve expiries matching EndOfTurnOf(active, round)
7. advance activeIndex, wrap → round + 1, EndOfRound expiries
```

If step 3 runs before step 1, a buff that granted +1 AP and just expired still
pays out for one extra turn. This is precisely the sort of thing the test suite
in [09](09-test-plan.md) pins down.

## Effects must re-validate at execution time

Between being pushed and being popped, the target may have died, moved,
teleported or gone invisible. Every handler re-checks its own preconditions.

Failure emits `Fizzled(effect, reason)` rather than silently doing nothing:

```kotlin
data class Fizzled(val effect: String, val reason: Rejection) : GameEvent
```

Silent no-ops are the single worst debugging experience in v1 — `MovementSystem`
has six separate `return@on` paths, none of which tell the player or the log
anything. A `Fizzled` event is visible in the battle log, animatable as a
"blocked" flash, and greppable in a bug report.

## Guard constants

| Constant | Value | Why |
| --- | --- | --- |
| `MAX_STEPS` | 10 000 | Effect loop; generous, only trips on real bugs |
| `MAX_REACTION_DEPTH` | 8 | Reaction chains |
| `MAX_TARGETS` | 32 | Sanity bound for area effects |

All three throw rather than truncate. A tripped guard is a bug in content or
rules, and failing loudly in a test beats a frozen UI on a phone.

## Wiring to the outside

```kotlin
// :ui — BattleViewModel
fun submit(cmd: Command) = viewModelScope.launch {
    when (val res = engine.submit(cmd)) {
        is Rejected -> showRejection(res.reasons)
        is Completed -> {
            logical = res.resolver.state
            play(res.resolver.emitted)
        }
        is AwaitingInput -> {
            logical = res.resolver.state
            play(res.resolver.emitted)
            pendingRequest = res.request      // dialog only after animations drain
        }
    }
    save(res.resolver)
}
```

The dialog waits for the animation queue. Otherwise you ask "opportunity
attack?" while the figure is still visibly three tiles away — see
[07-animation.md](07-animation.md).
