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
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.fixture.walls
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

    // --- docs/17-engine-gaps.md 1.5: Downed is an absolute gate, even for a Free action ---

    @Test
    fun downedCasterCannotActEvenViaAFreeAction() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(0) }
            initiative("hero")
            actionDef("shout") { cost(ActionCost.Free) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("shout")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertEquals(listOf(Rejection.Downed), rejections)
    }

    @Test
    fun aLivingCasterIsNeverRejectedAsDowned() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            initiative("hero")
            actionDef("shout") { cost(ActionCost.Free) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("shout")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(Rejection.Downed !in rejections)
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
    fun canAffordActionMatchesCanPerformsResourceRejectionsWithNoCtxNeeded() {
        // docs: the combat UI grays out an unaffordable action before a target is even picked —
        // canAffordAction must agree with canPerform's own resource checks without an ActionCtx.
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 3 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(3) }
            initiative("hero")
            actionDef("spell") { cost(ActionCost.Main, mana = 5) }
        }
        val def = s.catalog.actionDef(actionId("spell"))
        assertEquals(listOf(Rejection.NotEnoughMana(5, 3)), canAffordAction(s.state, s.id("hero"), def, s.catalog))
        assertEquals(
            canAffordAction(s.state, s.id("hero"), def, s.catalog),
            canPerform(s.state, s.id("hero"), def, ActionCtx(s.id("hero"), emptyList()), s.catalog),
        )
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

    // --- Cost.apCost: the flat AP price for non-Movement actions (user-reported: the Cost editor
    // had no AP field at all, because canPerform never checked one for Main/Quick/Reaction/Free) ---

    @Test
    fun notEnoughApForANonMovementActionsFlatCost() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 1 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(1) }
            initiative("hero")
            actionDef("heavySwing") { cost(ActionCost.Main, apCost = 2) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("heavySwing")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertEquals(listOf(Rejection.NotEnoughAp(2, 1)), rejections)
    }

    @Test
    fun enoughApForANonMovementActionsFlatCostProducesNoApRejection() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2) }
            initiative("hero")
            actionDef("stab") { cost(ActionCost.Main, apCost = 2) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(rejections.none { it is Rejection.NotEnoughAp })
    }

    @Test
    fun anUnauthoredApCostDefaultsToZeroAndNeverRejects() {
        // Backward compatibility: every action authored before Cost.apCost existed must keep costing
        // 0 AP, exactly as it did when Main/Quick/Reaction/Free had no AP check at all.
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 0 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(0) }
            initiative("hero")
            actionDef("stab") { cost(ActionCost.Main) }
        }
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("stab")), ActionCtx(s.id("hero"), emptyList()), s.catalog)
        assertTrue(rejections.none { it is Rejection.NotEnoughAp })
    }

    // --- docs/17-engine-gaps.md 1.3: a Path-targeted move prices itself off the resolved route ---

    @Test
    fun pathModeMovementPricesOffTheActualRouteNotAStaticTileCount() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10; ap = 3 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(3) }
            initiative("hero")
            actionDef("move") {
                cost(ActionCost.Movement(tiles = 999)) // static field is irrelevant once Path-targeted
                targeting(TargetMode.Path, Range.Tiles(5), Shape.Single, requiresLoS = false)
            }
        }
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 0))
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("move")), ctx, s.catalog)
        assertTrue(rejections.isEmpty(), "3 AP must cover a real 3-tile route, regardless of the static tiles=999")
    }

    @Test
    fun pathModeMovementRejectsWhenApIsLessThanTheResolvedRouteLength() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2) }
            initiative("hero")
            actionDef("move") {
                cost(ActionCost.Movement(tiles = 0)) // static field is irrelevant once Path-targeted
                targeting(TargetMode.Path, Range.Tiles(5), Shape.Single, requiresLoS = false)
            }
        }
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 0))
        val rejections = canPerform(s.state, s.id("hero"), s.catalog.actionDef(actionId("move")), ctx, s.catalog)
        assertEquals(listOf(Rejection.NotEnoughAp(3, 2)), rejections)
    }

    @Test
    fun pathModeMovementIsBlockedWhenNoRouteExists() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10; ap = 5 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(5) }
            initiative("hero")
            actionDef("move") {
                cost(ActionCost.Movement(tiles = 5))
                targeting(TargetMode.Path, Range.Tiles(5), Shape.Single, requiresLoS = false)
            }
        }
        val walled = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(3, 0))))
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 0))
        val rejections = canPerform(walled, s.id("hero"), s.catalog.actionDef(actionId("move")), ctx, s.catalog)
        assertEquals(listOf(Rejection.Blocked(GridPos(3, 0))), rejections)
    }

    @Test
    fun pathModeMovementCostReflectsDifficultTerrainNotJustTileCount() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10; ap = 3 }
            entity("hero") { archetype("dummy"); at(0, 5); hp(10); ap(3) }
            initiative("hero")
            actionDef("move") {
                cost(ActionCost.Movement(tiles = 0))
                targeting(TargetMode.Path, Range.Tiles(5), Shape.Single, requiresLoS = false)
            }
        }
        // 3 tiles away, but the middle one is Difficult (moveCost=2): real cost is 1+2+1=4, not 3.
        // Walls above/below it close off any cheaper diagonal detour around the difficult tile.
        val difficult = s.state.copy(
            map = s.state.map.copy(
                terrain = mapOf(
                    GridPos(1, 4) to TileType.Wall,
                    GridPos(1, 5) to TileType.Difficult,
                    GridPos(1, 6) to TileType.Wall,
                ),
            ),
        )
        val ctx = ActionCtx(s.id("hero"), emptyList(), point = GridPos(3, 5))
        val rejections = canPerform(difficult, s.id("hero"), s.catalog.actionDef(actionId("move")), ctx, s.catalog)
        assertEquals(listOf(Rejection.NotEnoughAp(4, 3)), rejections)
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
        val walledState = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(2, 0))))
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
