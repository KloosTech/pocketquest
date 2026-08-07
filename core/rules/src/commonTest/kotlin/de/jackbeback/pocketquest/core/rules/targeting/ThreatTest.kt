package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** docs/15-battle-ui.md's threat overlay: "every tile an enemy could reach and attack next turn." */
class ThreatTest {

    @Test
    fun threatenedTilesIncludesAnAllyTheEnemyCouldMoveWithinMeleeRangeOfNextTurn() {
        val s = scenario {
            archetype("goblin") { hp = 10; speed = 2; actions("strike") }
            archetype("hero") { hp = 10 }
            actionDef("strike") { targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Player)) }
            entity("goblin") { archetype("goblin"); at(0, 0); hp(10); faction(Faction.Enemy) }
            entity("nearAlly") { archetype("hero"); at(2, 0); hp(10) } // 2 tiles away — goblin can move to (1,0) then melee it
            entity("farAlly") { archetype("hero"); at(9, 9); hp(10) } // far out of reach
        }
        val threatened = threatenedTiles(s.state, s.id("goblin"), s.catalog)
        assertEquals(setOf(GridPos(2, 0)), threatened)
    }

    @Test
    fun threatenedTilesIncludesAnAllyAlreadyAdjacentWithoutNeedingToMove() {
        val s = scenario {
            archetype("goblin") { hp = 10; speed = 0; actions("strike") }
            archetype("hero") { hp = 10 }
            actionDef("strike") { targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Player)) }
            entity("goblin") { archetype("goblin"); at(0, 0); hp(10); faction(Faction.Enemy) }
            entity("ally") { archetype("hero"); at(1, 0); hp(10) }
        }
        val threatened = threatenedTiles(s.state, s.id("goblin"), s.catalog)
        assertEquals(setOf(GridPos(1, 0)), threatened, "zero speed still threatens its own current melee range")
    }

    @Test
    fun threatenedTilesExcludesActionsThatDoNotTargetAnOpposingFaction() {
        val s = scenario {
            archetype("goblin") { hp = 10; speed = 2; actions("selfHeal") }
            archetype("hero") { hp = 10 }
            actionDef("selfHeal") { targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Enemy)) }
            entity("goblin") { archetype("goblin"); at(0, 0); hp(10); faction(Faction.Enemy) }
            entity("ally") { archetype("hero"); at(1, 0); hp(10) }
        }
        assertTrue(threatenedTiles(s.state, s.id("goblin"), s.catalog).isEmpty())
    }

    @Test
    fun threatenedTilesExcludesReactionCostActions() {
        val s = scenario {
            archetype("goblin") { hp = 10; speed = 2; actions("opportunity") }
            archetype("hero") { hp = 10 }
            actionDef("opportunity") {
                cost(ActionCost.Reaction)
                targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Player))
            }
            entity("goblin") { archetype("goblin"); at(0, 0); hp(10); faction(Faction.Enemy) }
            entity("ally") { archetype("hero"); at(1, 0); hp(10) }
        }
        assertTrue(threatenedTiles(s.state, s.id("goblin"), s.catalog).isEmpty(), "a reaction is about the PLAYER's turn, not the threat's own upcoming turn")
    }

    @Test
    fun threatenedTilesForAnEntityWithNoPositionIsEmpty() {
        val s = scenario {
            archetype("goblin") { hp = 10; actions("strike") }
            actionDef("strike") { targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Player)) }
            entity("reserve") { archetype("goblin"); hp(10); faction(Faction.Enemy) } // no at() -> pos == null
        }
        assertTrue(threatenedTiles(s.state, s.id("reserve"), s.catalog).isEmpty())
    }

    @Test
    fun allThreatenedTilesUnionsAcrossEveryLivingEntityOfTheGivenFaction() {
        val s = scenario {
            archetype("goblin") { hp = 10; speed = 0; actions("strike") }
            archetype("hero") { hp = 10 }
            actionDef("strike") { targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(faction = Faction.Player)) }
            entity("goblinA") { archetype("goblin"); at(0, 0); hp(10); faction(Faction.Enemy) }
            entity("goblinB") { archetype("goblin"); at(9, 9); hp(0); faction(Faction.Enemy) } // dead — must not contribute
            entity("ally") { archetype("hero"); at(1, 0); hp(10) }
        }
        assertEquals(setOf(GridPos(1, 0)), allThreatenedTiles(s.state, Faction.Enemy, s.catalog))
    }
}
