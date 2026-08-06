package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanPerformTest {

    @Test
    fun freeActionIgnoresTurnOrder() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
            initiative("goblin", "hero") // hero is NOT active
            actionDef("shout") { cost(ActionCost.Free) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("shout")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(Rejection.NotYourTurn !in rejections)
    }

    @Test
    fun notYourTurnWhenSomeoneElseIsActive() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2); mana(0) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
            initiative("goblin", "hero")
            actionDef("stab") { cost(ActionCost.Main) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(Rejection.NotYourTurn in rejections)
    }

    @Test
    fun quickAlreadyUsedWhenFlagIsSet() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2); mana(0) }
            initiative("hero")
            actionDef("quickJab") { cost(ActionCost.Quick) }
        }
        val quickUsedState = s.state.copy(
            entities = s.state.entities.map { if (it.id == s.id("hero")) it.copy(resources = it.resources!!.copy(quickUsed = true)) else it },
        )
        val rejections = canPerform(quickUsedState, s.id("hero"), s.catalog.actionDef(actionId("quickJab")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(Rejection.QuickAlreadyUsed in rejections)
    }

    @Test
    fun notEnoughManaWhenCostExceedsCurrentMana() {
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 3 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(3) }
            initiative("hero")
            actionDef("spell") { cost(ActionCost.Main, mana = 5) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("spell")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertEquals(listOf(Rejection.NotEnoughMana(5, 3)), rejections)
    }

    @Test
    fun notEnoughApForAMovementCost() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2) }
            initiative("hero")
            actionDef("dash") { cost(ActionCost.Movement(tiles = 5)) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("dash")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertEquals(listOf(Rejection.NotEnoughAp(5, 2)), rejections)
    }

    @Test
    fun outOfRangeWhenPointExceedsTargetingRange() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            initiative("hero")
            actionDef("bolt") {
                cost(ActionCost.Main)
                targeting(TargetMode.Point, Range.Tiles(3), Shape.Single)
                effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire))
            }
        }
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(10, 0))
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("bolt")), ctx, s.catalog)
        assertTrue(rejections.any { it is Rejection.OutOfRange })
    }

    @Test
    fun noLineOfSightWhenPointIsBehindAWall() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            initiative("hero")
            actionDef("bolt") {
                cost(ActionCost.Main)
                targeting(TargetMode.Point, Range.Tiles(5), Shape.Single)
                effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire))
            }
        }
        val walledState = s.state.copy(map = s.state.map.copy(blockedTiles = setOf(GridPos(2, 0))))
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(4, 0))
        val rejections = canPerform(walledState, s.id("hero"), s.catalog.actionDef(actionId("bolt")), ctx, s.catalog)
        assertTrue(Rejection.NoLineOfSight in rejections)
    }

    @Test
    fun noLegalTargetWhenPointIsMissingOrEmpty() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            initiative("hero")
            actionDef("stab") {
                cost(ActionCost.Main)
                targeting(TargetMode.SingleEntity, Range.Tiles(3), Shape.Single)
                effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire))
            }
        }
        // no ctx.point at all
        val noPoint = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(Rejection.NoLegalTarget in noPoint)

        // point in range but nothing standing there
        val emptyPoint = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ActionCtx(s.id("hero"), emptyList(), point = GridPos(1, 0)), s.catalog)
        assertTrue(Rejection.NoLegalTarget in emptyPoint)
    }

    @Test
    fun validActionProducesNoRejections() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2; mana = 5 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2); mana(5) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
            initiative("hero")
            actionDef("stab") {
                cost(ActionCost.Main)
                targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single)
                effect(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire))
            }
        }
        val ctx = ActionCtx(s.id("hero"), targets = listOf(s.id("goblin")), point = GridPos(1, 0))
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ctx, s.catalog)
        assertEquals(emptyList(), rejections)
    }

    private fun actionId(name: String) = ActionId(name)
}
