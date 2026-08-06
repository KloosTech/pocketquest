package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DecisionId
import de.jackbeback.pocketquest.core.model.DecisionRequest
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Rejection
import kotlinx.serialization.Serializable

/**
 * Live rolls dice from `state.rng`, advancing it. Expected substitutes a
 * fixed representative value (10.5 for a d20, average-per-die for damage)
 * and never touches `state.rng` — used by preview() so estimating an
 * outcome doesn't consume randomness. Not stored anywhere persisted; it's
 * a call-time parameter to run()/resume(), same as [Catalog].
 */
enum class RngMode { Live, Expected }

/**
 * The whole snapshot needed to resume mid-decision after process death —
 * see docs/04-resolver.md. `depth` guards reaction nesting: rather than
 * tracking depth per individual effect on a flat stack, it's a single
 * resolver-wide counter bumped once per wave of newly-offered reactions —
 * a simplification of doc04's per-OfferReaction depth field, still
 * sufficient to stop a mutual-reaction loop. `reactedTo` is "already
 * offered a reaction to this event" for the life of the run() call.
 */
@Serializable
data class Resolver(
    val state: GameState,
    val stack: List<Effect> = emptyList(),
    val pending: DecisionRequest? = null,
    val answers: Map<DecisionId, Decision> = emptyMap(),
    val emitted: List<GameEvent> = emptyList(),
    val steps: Int = 0,
    val depth: Int = 0,
    val reactedTo: Set<ReactedKey> = emptySet(),
)

sealed interface StepResult {
    val resolver: Resolver
    data class Completed(override val resolver: Resolver) : StepResult
    data class AwaitingInput(override val resolver: Resolver, val request: DecisionRequest) : StepResult
    data class Rejected(override val resolver: Resolver, val reasons: List<Rejection>) : StepResult
}

/** Effect loop guard — generous, only trips on a real bug (e.g. a runaway self-spawning effect). */
const val MAX_STEPS = 10_000

tailrec fun run(r: Resolver, cat: Catalog, mode: RngMode = RngMode.Live): StepResult {
    r.pending?.let { return StepResult.AwaitingInput(r, it) }
    if (r.stack.isEmpty()) return StepResult.Completed(r)
    check(r.steps < MAX_STEPS) { "effect loop exceeded MAX_STEPS: ${r.stack.take(5)}" }

    val head = r.stack.first()
    val rest = r.stack.drop(1)

    if (head is Effect.Ask) {
        return StepResult.AwaitingInput(r.copy(stack = rest, pending = head.request), head.request)
    }

    val out = applyEffect(r.state, head, r.answers, cat, mode)
    val (triggered, reacted) = collectTriggers(out.state, out.events, r.depth, cat, r.reactedTo)
    // triggered goes BEFORE spawn, not after: doc04's prose is explicit ("the opportunity
    // attack pushes in front; MoveAlong(index+1) sits below and resumes afterwards") even
    // though doc04's own shown snippet literally has `spawn + triggered + rest` — that
    // ordering would run a self-continuing effect's own next step before the reaction it
    // just triggered, contradicting the stated intent. Following the prose here.
    return run(
        r.copy(
            state = out.state,
            stack = triggered + out.spawn + rest,
            emitted = r.emitted + out.events,
            steps = r.steps + 1,
            depth = if (triggered.isNotEmpty()) r.depth + 1 else r.depth,
            reactedTo = reacted,
        ),
        cat,
        mode,
    )
}

fun resume(r: Resolver, id: DecisionId, decision: Decision, cat: Catalog, mode: RngMode = RngMode.Live): StepResult {
    require(r.pending?.id == id) { "stale decision answer for $id (pending: ${r.pending?.id})" }
    return run(r.copy(pending = null, answers = r.answers + (id to decision)), cat, mode)
}
