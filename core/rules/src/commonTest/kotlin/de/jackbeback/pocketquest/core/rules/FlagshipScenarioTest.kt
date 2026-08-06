package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.action.perform
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.RngMode
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The worked example from docs/09-test-plan.md#layer-4, adapted (per
 * discussion, not a literal port): a concentrating caster moves past a
 * reach-enabled enemy, draws its opportunity attack, fails the resulting
 * concentration save, then casts an AoE spell that both goblins must save
 * against. Built in RngMode.Expected with chosen ACs/DCs rather than
 * hunting for a seed that reproduces doc09's literal d20 values — see the
 * pass-6 commit for what's simplified relative to the doc's exact
 * narrative (web isn't itself a concentration spell here, to avoid also
 * having to build Ref-templated Expiry/LinkId-in-Slot machinery nothing
 * else needs yet).
 */
class FlagshipScenarioTest {

    @Test
    fun lyraVsTwoGoblins() {
        val s = scenario {
            map(10, 10)
            seed(1)

            archetype("wizard") { hp = 18; ac = 12; ap = 2; mana = 9; abilities(con = 8, dex = 14) }
            archetype("goblinWarrior") { hp = 7; ac = 15; ap = 2; abilities(dex = 18); actions("oppAttack") }
            archetype("goblinWeak") { hp = 7; ac = 13; abilities(dex = 10) }
            archetype("ally") { hp = 10 }

            entity("lyra") { archetype("wizard"); at(4, 0); hp(18); ap(0); mana(0) }
            entity("gobA") { archetype("goblinWarrior"); at(5, 0); hp(7); ap(2); ai() }
            entity("gobB") { archetype("goblinWeak"); at(5, 2); hp(7); ai() }
            entity("ally1") { archetype("ally"); at(0, 5); hp(10) }
            initiative("lyra", "gobA", "gobB")

            statusDef("hasted") { modifier(de.jackbeback.pocketquest.core.model.Modifier.Add(de.jackbeback.pocketquest.core.model.Stat.MaxAp, 1)) }
            statusDef("bless") {}
            statusDef("marked") {}
            statusDef("restrained") {}

            actionDef("oppAttack") {
                cost(ActionCost.Reaction)
                targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
                reactionTrigger(de.jackbeback.pocketquest.core.model.ReactionTriggerKind.MoveStepped)
                effect(EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = 5, damage = DiceSpec(1, 1, 7), damageType = DamageType.Slashing))
            }
            actionDef("web") {
                cost(ActionCost.Main, mana = 3)
                targeting(TargetMode.Point, Range.Tiles(5), Shape.Sphere(1), maxTargets = 5)
                effect(
                    EffectTemplate.RollSave(
                        target = Ref.EachTarget,
                        ability = Ability.Dex,
                        dc = 14,
                        onFail = listOf(EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("restrained"), expiry = Expiry.Permanent)),
                    ),
                )
            }
        }

        // --- Pre-existing state at the top of the slice: round 2 ending, lyra concentrating on bless (L1). ---
        val link1 = LinkId(1)
        var state = s.state.copy(
            turn = s.state.turn.copy(round = 2, activeIndex = 2), // gobB "was" active; ending gobB's turn wraps to lyra, round 3
            entities = s.state.entities.map { e ->
                when (e.id) {
                    s.id("lyra") -> e.copy(
                        concentrating = link1,
                        statuses = e.statuses + ActiveStatus(StatusId("hasted"), sourceId = null, linkId = null, expiry = Expiry.StartOfTurnOf(s.id("lyra"), 3), appliedAtVersion = 0),
                    )
                    s.id("gobA") -> e.copy(
                        statuses = e.statuses + ActiveStatus(StatusId("marked"), sourceId = null, linkId = null, expiry = Expiry.EndOfTurnOf(s.id("lyra"), 3), appliedAtVersion = 0),
                    )
                    s.id("ally1") -> e.copy(
                        statuses = e.statuses + ActiveStatus(StatusId("bless"), sourceId = s.id("lyra"), linkId = link1, expiry = Expiry.OnConcentrationLost, appliedAtVersion = 0),
                    )
                    else -> e
                }
            },
        )
        val violationsBeforeStart = checkInvariants(state, s.catalog)
        assertTrue(violationsBeforeStart.isEmpty(), "setup must itself be a valid state: $violationsBeforeStart")

        // --- Turn boundary: gobB's turn ends, lyra's turn 3 begins. ---
        val boundary = de.jackbeback.pocketquest.core.rules.resolver.run(Resolver(state, stack = listOf(Effect.EndTurn(s.id("gobB")))), s.catalog)
        val afterBoundary = assertIs<StepResult.Completed>(boundary)
        assertEquals(
            listOf(
                GameEvent.TurnEnded(s.id("gobB")),
                GameEvent.TurnStarted(s.id("lyra"), 3),
                GameEvent.StatusExpired(s.id("lyra"), StatusId("hasted")),
                GameEvent.ResourcesReset(s.id("lyra"), ap = 2, mana = 9),
            ),
            afterBoundary.resolver.emitted,
        )
        state = afterBoundary.resolver.state

        // --- Lyra moves two tiles, drawing gobA's opportunity attack on the step that leaves reach. ---
        val move = Effect.MoveAlong(s.id("lyra"), listOf(GridPos(4, 1), GridPos(4, 2)))
        val afterMove = assertIs<StepResult.Completed>(runResolver(Resolver(state, stack = listOf(move)), s.catalog, RngMode.Expected))
        val moveEvents = afterMove.resolver.emitted

        assertEquals(
            listOf(
                "MoveStepped", "MoveStepped", "ReactionTriggered", "ResourcesSpent", "AttackRolled", "DamageTaken",
                "ConcentrationCheckRolled", "StatusExpired", "ConcentrationBroken",
            ),
            moveEvents.map { it::class.simpleName },
        )
        assertEquals(GridPos(4, 2), afterMove.resolver.state.byId.getValue(s.id("lyra")).pos, "movement must finish after the interruption")
        assertTrue((moveEvents[4] as GameEvent.AttackRolled).hit)
        assertEquals(10, (moveEvents[6] as GameEvent.ConcentrationCheckRolled).dc) // max(10, 8/2)
        assertTrue(!(moveEvents[6] as GameEvent.ConcentrationCheckRolled).success)
        assertEquals(GameEvent.StatusExpired(s.id("ally1"), StatusId("bless")), moveEvents[7])
        assertNull(afterMove.resolver.state.byId.getValue(s.id("lyra")).concentrating)
        state = afterMove.resolver.state

        // --- Lyra casts web at both goblins: gobA (dex +4) saves, gobB (dex +0) fails and is restrained. ---
        val ctx = ActionCtx(s.id("lyra"), targets = listOf(s.id("gobA"), s.id("gobB")), point = GridPos(5, 1))
        val castResult = perform(state, s.id("lyra"), ActionId("web"), ctx, s.catalog)
        val afterCast = assertIs<StepResult.Completed>(castResult)
        val castEvents = afterCast.resolver.emitted

        assertEquals(
            listOf("ActionStarted", "ResourcesSpent", "SaveRolled", "SaveRolled", "StatusApplied"),
            castEvents.map { it::class.simpleName },
        )
        val saveA = castEvents[2] as GameEvent.SaveRolled
        val saveB = castEvents[3] as GameEvent.SaveRolled
        assertEquals(s.id("gobA"), saveA.target)
        assertTrue(saveA.success, "gobA's +4 dex must beat dc 14 in Expected mode (10.5+4=14.5)")
        assertEquals(s.id("gobB"), saveB.target)
        assertTrue(!saveB.success, "gobB's +0 dex must fail dc 14 in Expected mode (10.5+0=10.5)")
        assertEquals(GameEvent.StatusApplied(s.id("gobB"), StatusId("restrained"), 1, Expiry.Permanent), castEvents[4])
        assertTrue(afterCast.resolver.state.byId.getValue(s.id("gobA")).statuses.none { it.def == StatusId("restrained") }, "only the goblin that failed gets restrained")
        state = afterCast.resolver.state

        // --- Lyra's turn ends: her EndOfTurnOf-tagged mark on gobA expires. ---
        val endTurn = assertIs<StepResult.Completed>(de.jackbeback.pocketquest.core.rules.resolver.run(Resolver(state, stack = listOf(Effect.EndTurn(s.id("lyra")))), s.catalog))
        assertTrue(endTurn.resolver.emitted.any { it == GameEvent.StatusExpired(s.id("gobA"), StatusId("marked")) })
        assertTrue(endTurn.resolver.emitted.any { it == GameEvent.TurnEnded(s.id("lyra")) })

        val finalViolations = checkInvariants(endTurn.resolver.state, s.catalog)
        assertTrue(finalViolations.isEmpty(), "final state must still satisfy every invariant: $finalViolations")
    }
}
