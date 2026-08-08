package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.ReactionTriggerKind
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PerformTest {

    private fun meleeAttackScenario() = scenario {
        map(10, 10)
        seed(42)
        archetype("dummy") { hp = 20; ap = 2; mana = 5 }
        entity("hero") { archetype("dummy"); at(0, 0); hp(20); ap(2); mana(5) }
        entity("goblin") { archetype("dummy"); at(1, 0); hp(20) }
        initiative("hero", "goblin")
        actionDef("stab") {
            cost(ActionCost.Main, mana = 1)
            targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
            effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 20, damage = DiceSpec(1, 6, 3), damageType = DamageType.Slashing))
        }
    }

    @Test
    fun performSpendsCostFirstThenRunsInstantiatedEffects() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val result = perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        val completed = assertIs<StepResult.Completed>(result)
        val events = completed.resolver.emitted
        assertIs<GameEvent.ActionStarted>(events[0], "ActionStarted is seeded before the resolver even runs")
        assertIs<GameEvent.ResourcesSpent>(events[1], "cost must be spent before any other effect runs")
        assertTrue(events.any { it is GameEvent.AttackRolled })

        val hero = completed.resolver.state.byId.getValue(s.id("hero"))
        assertEquals(4, hero.resources!!.mana) // 5 - 1
    }

    @Test
    fun performSpendsCostApCostForANonMovementAction() {
        // User-reported: the Cost editor had no AP field at all — Main/Quick/Reaction/Free actions
        // spent zero AP no matter what. Cost.apCost is the fix; this pins down that perform() actually
        // deducts it, not just that canPerform() checks it.
        val s = scenario {
            map(10, 10)
            seed(42)
            archetype("dummy") { hp = 20; ap = 2; mana = 0 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20); ap(2) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(20) }
            initiative("hero", "goblin")
            actionDef("heavySwing") {
                cost(ActionCost.Main, apCost = 2)
                targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
                effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Slashing))
            }
        }
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val result = perform(s.state, s.id("hero"), ActionId("heavySwing"), ctx, s.catalog)

        val completed = assertIs<StepResult.Completed>(result)
        val hero = completed.resolver.state.byId.getValue(s.id("hero"))
        assertEquals(0, hero.resources!!.ap) // 2 - 2
    }

    @Test
    fun performRejectsWithoutMutatingStateWhenCanPerformFails() {
        val s = meleeAttackScenario()
        // point out of melee range
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(9, 9))
        val result = perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        val rejected = assertIs<StepResult.Rejected>(result)
        assertTrue(rejected.reasons.isNotEmpty())
        assertEquals(s.state, rejected.resolver.state, "a rejected perform() must not touch state at all")
    }

    @Test
    fun previewNeverTouchesRngStateAndReportsExpectedOutcome() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val result = preview(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)

        assertEquals(s.state.rng, result.state.rng, "preview must never advance RngState")
        assertTrue(result.events.any { it is GameEvent.AttackRolled })
        // attackBonus=20 guarantees a hit in Expected mode regardless of AC, so damage must have landed.
        val goblin = result.state.byId.getValue(s.id("goblin"))
        assertTrue(goblin.health!!.current < 20, "expected-mode preview should still show the hit landing")
    }

    @Test
    fun previewIsPureAndNeverMutatesTheInputState() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val before = s.state
        preview(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog)
        assertEquals(before, s.state, "GameState is immutable — preview() must not be able to mutate the caller's reference")
    }

    @Test
    fun livePerformIsDeterministicForTheSameSeed() {
        val s = meleeAttackScenario()
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val resultA = assertIs<StepResult.Completed>(perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog))
        val resultB = assertIs<StepResult.Completed>(perform(s.state, s.id("hero"), ActionId("stab"), ctx, s.catalog))
        assertEquals(resultA.resolver.emitted, resultB.resolver.emitted)
        assertEquals(resultA.resolver.state, resultB.resolver.state)
    }

    // --- docs/17-engine-gaps.md 1.2/1.3: tap-to-move synthesizes a real MoveAlong from the resolved path ---

    private fun moveScenario() = scenario {
        map(10, 10)
        seed(1)
        archetype("dummy") { hp = 10; ap = 3 }
        entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(3) }
        initiative("hero")
        actionDef("move") {
            cost(ActionCost.Movement(tiles = 999)) // irrelevant once Path-targeted — see CanPerformTest
            targeting(TargetMode.Path, Range.Tiles(5), Shape.Single, requiresLoS = false)
        }
    }

    @Test
    fun performingAPathMoveActuallyRelocatesTheEntityAndSpendsThePathsApNotTheStaticField() {
        val s = moveScenario()
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 0))
        val result = assertIs<StepResult.Completed>(perform(s.state, s.id("hero"), ActionId("move"), ctx, s.catalog))

        val hero = result.resolver.state.byId.getValue(s.id("hero"))
        assertEquals(GridPos(3, 0), hero.pos)
        assertEquals(0, hero.resources!!.ap, "3 AP spent on a real 3-tile route, not the static tiles=999")
        assertEquals(3, result.resolver.emitted.count { it is GameEvent.MoveStepped })
    }

    @Test
    fun previewingAPathMoveShowsTheDestinationWithoutMutatingRealState() {
        val s = moveScenario()
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 0))
        val result = preview(s.state, s.id("hero"), ActionId("move"), ctx, s.catalog)
        assertEquals(GridPos(3, 0), result.state.byId.getValue(s.id("hero")).pos)
        assertEquals(GridPos(0, 0), s.state.byId.getValue(s.id("hero")).pos, "preview must not mutate the input state")
    }

    // --- KNOWN_ISSUES.md #6: ActionStarted must reach collectTriggers ---

    private fun counterspellScenario() = scenario {
        map(10, 10)
        seed(1)
        archetype("caster") { hp = 20; ap = 2; mana = 5; actions("firebolt") }
        archetype("interrupter") { hp = 20; ap = 2; mana = 5; actions("counterspell") }
        entity("caster") { archetype("caster"); at(0, 0); hp(20); ap(2); mana(5) }
        entity("interrupter") { archetype("interrupter"); at(1, 0); hp(20); ap(2); mana(5); ai() }
        initiative("caster", "interrupter")
        actionDef("firebolt") {
            cost(ActionCost.Main, mana = 3)
            targeting(TargetMode.SelfOnly, Range.SelfRange, Shape.Single)
            effect(EffectTemplate.DealDamage(Ref.Caster, amount = 1, damageType = DamageType.Fire)) // a harmless self-tick, just so firebolt has SOME effect
        }
        actionDef("counterspell") {
            cost(ActionCost.Reaction, mana = 3)
            targeting(TargetMode.SingleEntity, Range.Tiles(5), Shape.Single)
            reactionTrigger(ReactionTriggerKind.ActionStarted)
            effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 999, damage = DiceSpec(1, 4, 0), damageType = DamageType.Force))
        }
    }

    @Test
    fun aCounterspellShapedReactionToActionStartedFires() {
        val s = counterspellScenario()
        val ctx = ActionCtx(s.id("caster"), targets = listOf(s.id("caster")), point = GridPos(0, 0))
        val result = perform(s.state, s.id("caster"), ActionId("firebolt"), ctx, s.catalog)

        val completed = assertIs<StepResult.Completed>(result)
        val events = completed.resolver.emitted
        assertTrue(
            events.contains(GameEvent.ReactionTriggered(s.id("interrupter"), ActionId("counterspell"))),
            "the interrupter must be offered a reaction to ActionStarted",
        )
        assertTrue(events.any { it is GameEvent.AttackRolled }, "the counterspell's own effect must actually resolve, not just be offered")

        // The reaction is placed ahead of the interrupted action's own stack — it resolves before
        // the caster even pays firebolt's cost, matching real Counterspell timing (interrupts the
        // instant casting begins).
        val reactionAttackIndex = events.indexOfFirst { it is GameEvent.AttackRolled }
        val casterResourcesSpentIndex = events.indexOfFirst { it is GameEvent.ResourcesSpent && it.who == s.id("caster") }
        assertTrue(reactionAttackIndex < casterResourcesSpentIndex, "the counterspell must resolve before the caster pays firebolt's cost")
    }

    @Test
    fun previewAlsoSeesAnActionStartedReaction() {
        val s = counterspellScenario()
        val ctx = ActionCtx(s.id("caster"), targets = listOf(s.id("caster")), point = GridPos(0, 0))
        val result = preview(s.state, s.id("caster"), ActionId("firebolt"), ctx, s.catalog)
        assertTrue(result.events.any { it is GameEvent.AttackRolled }, "preview must run through the same trigger pipeline as perform()")
    }
}
