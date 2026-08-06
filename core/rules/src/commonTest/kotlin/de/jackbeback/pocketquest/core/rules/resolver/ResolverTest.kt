package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DecisionId
import de.jackbeback.pocketquest.core.model.DecisionRequest
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResolverTest {

    private fun bareScenario() = scenario {
        archetype("dummy") { hp = 10 }
        entity("hero") { archetype("dummy"); at(0, 0) }
    }

    @Test
    fun runReturnsCompletedWhenStackIsEmpty() {
        val s = bareScenario()
        val result = run(Resolver(s.state), s.catalog)
        assertIs<StepResult.Completed>(result)
    }

    @Test
    fun askPausesWithAwaitingInputAndResumeCompletes() {
        val s = bareScenario()
        val request = DecisionRequest(DecisionId(1))
        val paused = run(Resolver(s.state, stack = listOf(Effect.Ask(request))), s.catalog)

        val awaiting = assertIs<StepResult.AwaitingInput>(paused)
        assertEquals(request, awaiting.request)
        assertEquals(request, awaiting.resolver.pending)
        assertTrue(awaiting.resolver.stack.isEmpty(), "Ask is popped off the stack once it becomes the pending decision")

        val resumed = resume(awaiting.resolver, request.id, Decision(request.id), s.catalog)
        val completed = assertIs<StepResult.Completed>(resumed)
        assertEquals(null, completed.resolver.pending)
        assertEquals(Decision(request.id), completed.resolver.answers[request.id])
    }

    @Test
    fun workQueuedBehindAnAskResumesAfterTheAnswer() {
        val s = bareScenario()
        val request = DecisionRequest(DecisionId(7))
        val moveAfterAsk = Effect.MoveAlong(s.id("hero"), listOf(GridPos(1, 0)))
        val paused = run(Resolver(s.state, stack = listOf(Effect.Ask(request), moveAfterAsk)), s.catalog)

        val awaiting = assertIs<StepResult.AwaitingInput>(paused)
        assertEquals(listOf(moveAfterAsk), awaiting.resolver.stack)

        val resumed = resume(awaiting.resolver, request.id, Decision(request.id), s.catalog)
        val completed = assertIs<StepResult.Completed>(resumed)
        assertEquals(GridPos(1, 0), completed.resolver.state.byId.getValue(s.id("hero")).pos)
    }

    @Test
    fun resumeWithStaleDecisionIdThrows() {
        val s = bareScenario()
        val request = DecisionRequest(DecisionId(1))
        val paused = run(Resolver(s.state, stack = listOf(Effect.Ask(request))), s.catalog)
        val awaiting = assertIs<StepResult.AwaitingInput>(paused)

        val staleId = DecisionId(999)
        assertFailsWith<IllegalArgumentException> {
            resume(awaiting.resolver, staleId, Decision(staleId), s.catalog)
        }
    }

    @Test
    fun resumeWithNoPendingDecisionThrows() {
        val s = bareScenario()
        assertFailsWith<IllegalArgumentException> {
            resume(Resolver(s.state), DecisionId(1), Decision(DecisionId(1)), s.catalog)
        }
    }

    @Test
    fun runawaySelfSpawningEffectTripsMaxSteps() {
        val s = bareScenario()
        // Oscillate between two adjacent walkable tiles, well past MAX_STEPS, to exercise the loop guard.
        val path = List(MAX_STEPS + 5) { if (it % 2 == 0) GridPos(1, 0) else GridPos(0, 0) }
        val runaway = Effect.MoveAlong(s.id("hero"), path)

        assertFailsWith<IllegalStateException> {
            run(Resolver(s.state, stack = listOf(runaway)), s.catalog)
        }
    }
}
