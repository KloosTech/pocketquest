package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.ReactionTriggerKind
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReactionsTest {

    private fun opportunityAttackScenario(reactorAi: Boolean = true, reactionManaCost: Int = 0, reactorMana: Int = 0) = scenario {
        map(10, 10)
        seed(1)
        archetype("fighter") { hp = 20; ap = 2; mana = reactorMana; actions("oppAttack") }
        archetype("runner") { hp = 20 }
        entity("fighter") {
            archetype("fighter"); at(0, 0); hp(20); ap(2); mana(reactorMana)
            if (reactorAi) ai() // else default Human
        }
        entity("runner") { archetype("runner"); at(1, 0); hp(20) }
        initiative("fighter", "runner")
        actionDef("oppAttack") {
            cost(ActionCost.Reaction, mana = reactionManaCost)
            targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
            reactionTrigger(ReactionTriggerKind.MoveStepped)
            effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 999, damage = DiceSpec(1, 6, 0), damageType = DamageType.Slashing))
        }
    }

    // --- matchingReaction (pure logic) ---

    @Test
    fun matchingReactionFiresWhenMoverLeavesReach() {
        val s = opportunityAttackScenario()
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(2, 0))
        val actionId = matchingReaction(s.entity("fighter"), event, s.catalog)
        assertEquals(ActionId("oppAttack"), actionId)
    }

    @Test
    fun matchingReactionDoesNotFireWhenMoverStaysInReach() {
        val s = opportunityAttackScenario()
        // e.g. moving around within melee range the whole time
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(1, 1))
        assertNull(matchingReaction(s.entity("fighter"), event, s.catalog))
    }

    @Test
    fun matchingReactionDoesNotFireWhenMoverWasNeverInReach() {
        val s = opportunityAttackScenario()
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(5, 5), GridPos(6, 5))
        assertNull(matchingReaction(s.entity("fighter"), event, s.catalog))
    }

    @Test
    fun matchingReactionDoesNotFireForTheMoverReactingToItself() {
        val s = opportunityAttackScenario()
        val event = GameEvent.MoveStepped(s.id("fighter"), GridPos(0, 0), GridPos(5, 5))
        assertNull(matchingReaction(s.entity("fighter"), event, s.catalog))
    }

    @Test
    fun matchingReactionDoesNotFireWhenReactionAlreadyUsed() {
        val s = opportunityAttackScenario()
        val usedUp = s.entity("fighter").let { it.copy(resources = it.resources!!.copy(reactionUsed = true)) }
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(2, 0))
        assertNull(matchingReaction(usedUp, event, s.catalog))
    }

    @Test
    fun matchingReactionDoesNotFireWhenReactorIsDead() {
        val s = opportunityAttackScenario()
        val dead = s.entity("fighter").let { it.copy(health = it.health!!.copy(current = 0)) }
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(2, 0))
        assertNull(matchingReaction(dead, event, s.catalog))
    }

    // --- collectTriggers (ordering, depth guard, dedup) ---

    @Test
    fun collectTriggersSortsByInitiativeThenEntityId() {
        val s = scenario {
            archetype("fighter") { hp = 10; actions("oppAttack") }
            archetype("runner") { hp = 10 }
            // Registered in reverse-of-initiative order, on purpose, to prove sort isn't by registration/id order.
            entity("second") { archetype("fighter"); at(2, 0); hp(10); ai() }
            entity("first") { archetype("fighter"); at(0, 2); hp(10); ai() }
            entity("runner") { archetype("runner"); at(1, 0); hp(10) }
            initiative("first", "second", "runner")
            actionDef("oppAttack") {
                cost(ActionCost.Reaction)
                targeting(TargetMode.SingleEntity, Range.Tiles(3), Shape.Single)
                reactionTrigger(ReactionTriggerKind.MoveStepped)
                effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 999, damage = DiceSpec(1, 4, 0), damageType = DamageType.Fire))
            }
        }
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(5, 5))
        val (offers, _) = collectTriggers(s.state, listOf(event), depth = 0, cat = s.catalog, alreadyReacted = emptySet())
        val order = offers.map { (it as Effect.OfferReaction).who }
        assertEquals(listOf(s.id("first"), s.id("second")), order)
    }

    @Test
    fun collectTriggersReturnsNothingAtMaxDepth() {
        val s = opportunityAttackScenario()
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(2, 0))
        val (offers, _) = collectTriggers(s.state, listOf(event), depth = MAX_REACTION_DEPTH, cat = s.catalog, alreadyReacted = emptySet())
        assertTrue(offers.isEmpty())
    }

    @Test
    fun collectTriggersNeverOffersTheSameEntityTwiceForTheSameEvent() {
        val s = opportunityAttackScenario()
        val event = GameEvent.MoveStepped(s.id("runner"), GridPos(1, 0), GridPos(2, 0))
        val alreadyReacted = setOf(ReactedKey(s.id("fighter"), event))
        val (offers, _) = collectTriggers(s.state, listOf(event), depth = 0, cat = s.catalog, alreadyReacted = alreadyReacted)
        assertTrue(offers.isEmpty())
    }

    // --- full resolver integration ---

    @Test
    fun aiReactorRespondsInlineWithoutAwaitingInput() {
        val s = opportunityAttackScenario(reactorAi = true)
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0)))
        val result = run(Resolver(s.state, stack = listOf(move)), s.catalog)

        val completed = assertCompleted(result)
        assertTrue(completed.resolver.emitted.any { it is GameEvent.AttackRolled }, "AI must resolve the opportunity attack inline")
        assertTrue(
            completed.resolver.emitted.contains(GameEvent.ReactionTriggered(s.id("fighter"), ActionId("oppAttack"))),
            "ReactionTriggered must fire regardless of who answers",
        )
        val fighter = completed.resolver.state.byId.getValue(s.id("fighter"))
        assertTrue(fighter.resources!!.reactionUsed, "using the reaction must mark it used")
        val runner = completed.resolver.state.byId.getValue(s.id("runner"))
        assertTrue(runner.health!!.current < 20, "attackBonus=999 guarantees a hit")
    }

    @Test
    fun opportunityAttackRunsBeforeRestOfTheOriginalMoveContinues() {
        val s = opportunityAttackScenario(reactorAi = true)
        // Three-step path: (1,0)->(2,0) leaves reach and should trigger the reaction there;
        // steps to (3,0) and (4,0) must complete AFTER the reaction, not before.
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0), GridPos(3, 0), GridPos(4, 0)))
        val result = run(Resolver(s.state, stack = listOf(move)), s.catalog)
        val completed = assertCompleted(result)

        val kinds = completed.resolver.emitted.map { it::class.simpleName }
        val attackIndex = kinds.indexOf("AttackRolled")
        val moveIndices = kinds.withIndex().filter { it.value == "MoveStepped" }.map { it.index }
        assertTrue(attackIndex in moveIndices[0]..moveIndices[1], "the reaction must fire between the triggering step and the next one")
        assertEquals(GridPos(4, 0), completed.resolver.state.byId.getValue(s.id("runner")).pos, "movement must still finish after the interruption")
    }

    @Test
    fun humanReactorPausesForADecisionThenAppliesOnAccept() {
        val s = opportunityAttackScenario(reactorAi = false)
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0)))
        val paused = run(Resolver(s.state, stack = listOf(move)), s.catalog)

        val awaiting = assertIsAwaiting(paused)
        val resumed = resume(awaiting.resolver, awaiting.request.id, Decision(awaiting.request.id, accept = true), s.catalog)
        val completed = assertCompleted(resumed)

        assertTrue(completed.resolver.emitted.any { it is GameEvent.AttackRolled })
        assertTrue(completed.resolver.state.byId.getValue(s.id("fighter")).resources!!.reactionUsed)
    }

    @Test
    fun humanReactorDecliningSkipsTheReactionEntirely() {
        val s = opportunityAttackScenario(reactorAi = false)
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0)))
        val paused = run(Resolver(s.state, stack = listOf(move)), s.catalog)

        val awaiting = assertIsAwaiting(paused)
        val resumed = resume(awaiting.resolver, awaiting.request.id, Decision(awaiting.request.id, accept = false), s.catalog)
        val completed = assertCompleted(resumed)

        assertTrue(completed.resolver.emitted.none { it is GameEvent.AttackRolled })
        assertTrue(!completed.resolver.state.byId.getValue(s.id("fighter")).resources!!.reactionUsed, "a declined reaction must not mark reactionUsed")
    }

    @Test
    fun reactionUsedPreventsASecondOpportunityAttackInTheSameRun() {
        val s = opportunityAttackScenario(reactorAi = true)
        // Runner leaves reach, comes back, and leaves again — only the first departure should draw an attack.
        val move = Effect.MoveAlong(
            s.id("runner"),
            listOf(GridPos(2, 0), GridPos(1, 0), GridPos(2, 0)),
        )
        val result = run(Resolver(s.state, stack = listOf(move)), s.catalog)
        val completed = assertCompleted(result)
        val attackCount = completed.resolver.emitted.count { it is GameEvent.AttackRolled }
        assertEquals(1, attackCount, "the reaction can only fire once before reactionUsed blocks it")
    }

    // --- KNOWN_ISSUES.md #3: a reaction resolving its effects for free when it can't be paid for ---

    @Test
    fun acceptReactionFizzlesWithoutRunningEffectsWhenReactorCannotAffordTheCost() {
        val s = opportunityAttackScenario(reactorAi = true, reactionManaCost = 3, reactorMana = 0)
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0)))
        val result = run(Resolver(s.state, stack = listOf(move)), s.catalog)
        val completed = assertCompleted(result)

        assertTrue(completed.resolver.emitted.none { it is GameEvent.AttackRolled }, "an unaffordable reaction must not resolve its effects")
        assertTrue(completed.resolver.emitted.any { it is GameEvent.Fizzled }, "the failed cost must be visible as a Fizzled event")
        val fighter = completed.resolver.state.byId.getValue(s.id("fighter"))
        assertEquals(0, fighter.resources!!.mana, "mana must not be deducted when the reaction couldn't be paid for")
        assertFalse(fighter.resources!!.reactionUsed, "a reaction that never actually happened must not consume the reaction")
        val runner = completed.resolver.state.byId.getValue(s.id("runner"))
        assertEquals(20, runner.health!!.current, "the runner must take no damage from a reaction that fizzled on cost")
    }

    @Test
    fun acceptReactionStillRunsWhenReactorCanAffordTheCost() {
        val s = opportunityAttackScenario(reactorAi = true, reactionManaCost = 3, reactorMana = 3)
        val move = Effect.MoveAlong(s.id("runner"), listOf(GridPos(2, 0)))
        val result = run(Resolver(s.state, stack = listOf(move)), s.catalog)
        val completed = assertCompleted(result)

        assertTrue(completed.resolver.emitted.any { it is GameEvent.AttackRolled }, "an affordable reaction must still resolve normally")
        val fighter = completed.resolver.state.byId.getValue(s.id("fighter"))
        assertEquals(0, fighter.resources!!.mana, "the cost must actually be spent when it's paid")
        assertTrue(fighter.resources!!.reactionUsed)
    }

    private fun assertCompleted(result: StepResult): StepResult.Completed {
        assertNotNull(result as? StepResult.Completed, "expected Completed but was $result")
        return result
    }

    private fun assertIsAwaiting(result: StepResult): StepResult.AwaitingInput {
        assertNotNull(result as? StepResult.AwaitingInput, "expected AwaitingInput but was $result")
        return result
    }
}
