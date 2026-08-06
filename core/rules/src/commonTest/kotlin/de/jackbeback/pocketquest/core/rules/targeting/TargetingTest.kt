package de.jackbeback.pocketquest.core.rules.targeting

import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetingTest {

    // --- legalTargets ---

    @Test
    fun legalTargetsSelfOnlyIsJustTheCastersOwnTile() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(3, 3); hp(10) }
        }
        val def = actionDefWith(Targeting(TargetMode.SelfOnly, Range.SelfRange, Shape.Single))
        assertEquals(setOf(GridPos(3, 3)), legalTargets(s.state, s.id("hero"), def, s.catalog))
    }

    @Test
    fun legalTargetsSingleEntityOnlyReturnsOccupiedTilesMatchingFilter() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("ally") { archetype("dummy"); at(1, 0); hp(10); faction(Faction.Player) }
            entity("goblin") { archetype("dummy"); at(2, 0); hp(10); faction(Faction.Enemy) }
        }
        val def = actionDefWith(Targeting(TargetMode.SingleEntity, Range.Tiles(5), Shape.Single, filter = TargetFilter(faction = Faction.Enemy)))
        val legal = legalTargets(s.state, s.id("hero"), def, s.catalog)
        assertEquals(setOf(GridPos(2, 0)), legal, "only the enemy-occupied tile qualifies, not the empty tile at (3,0) or the ally at (1,0)")
    }

    @Test
    fun legalTargetsExcludesTilesOutOfRange() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("farGoblin") { archetype("dummy"); at(9, 0); hp(10) }
        }
        // excludeSelf so the caster's own (trivially in-range) tile doesn't count as a legal target too.
        val def = actionDefWith(Targeting(TargetMode.SingleEntity, Range.Tiles(3), Shape.Single, filter = TargetFilter(excludeSelf = true)))
        assertTrue(legalTargets(s.state, s.id("hero"), def, s.catalog).isEmpty())
    }

    @Test
    fun legalTargetsExcludesTilesBehindAWallWhenLoSRequired() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val blocked = s.state.copy(map = s.state.map.copy(blockedTiles = setOf(GridPos(2, 0))))
        val def = actionDefWith(Targeting(TargetMode.Point, Range.Tiles(5), Shape.Single, requiresLoS = true))
        val legal = legalTargets(blocked, s.id("hero"), def, s.catalog)
        assertFalse(GridPos(4, 0) in legal, "tile behind the wall must be excluded when LoS is required")
        assertTrue(GridPos(1, 0) in legal, "tile before the wall must still be legal")
    }

    @Test
    fun legalTargetsPathModeUsesReachabilityNotJustRange() {
        val s = scenario {
            map(10, 10)
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        // Wall directly east forces any path around it to cost more than 2 straight-line tiles.
        val walled = s.state.copy(
            map = s.state.map.copy(blockedTiles = setOf(GridPos(1, 0), GridPos(1, 1), GridPos(1, -1)).filter { s.state.map.inBounds(it) }.toSet()),
        )
        val def = actionDefWith(Targeting(TargetMode.Path, Range.Tiles(2), Shape.Single, requiresLoS = false))
        val legal = legalTargets(walled, s.id("hero"), def, s.catalog)
        assertFalse(GridPos(2, 0) in legal, "directly behind the wall is farther than 2 actual steps away")
    }

    // --- affectedBy ---

    @Test
    fun affectedBySphereHitsMultipleEntitiesWithinRadius() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("a") { archetype("dummy"); at(5, 4); hp(10) }
            entity("b") { archetype("dummy"); at(5, 6); hp(10) }
            entity("farAway") { archetype("dummy"); at(9, 9); hp(10) }
        }
        val def = actionDefWith(Targeting(TargetMode.Point, Range.Tiles(10), Shape.Sphere(1), maxTargets = 5))
        val hit = affectedBy(s.state, def, s.id("hero"), GridPos(5, 5)).toSet()
        assertEquals(setOf(s.id("a"), s.id("b")), hit)
    }

    @Test
    fun affectedByRespectsMaxTargetsCap() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("a") { archetype("dummy"); at(4, 5); hp(10) }
            entity("b") { archetype("dummy"); at(5, 4); hp(10) }
            entity("c") { archetype("dummy"); at(6, 5); hp(10) }
        }
        val def = actionDefWith(Targeting(TargetMode.Point, Range.Tiles(10), Shape.Sphere(2), maxTargets = 2))
        val hit = affectedBy(s.state, def, s.id("hero"), GridPos(5, 5))
        assertEquals(2, hit.size)
    }

    @Test
    fun affectedByExcludesDeadEntitiesWhenFilterRequiresAlive() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("corpse") { archetype("dummy"); at(1, 0); hp(0) }
        }
        val def = actionDefWith(Targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, filter = TargetFilter(requireAlive = true)))
        assertTrue(affectedBy(s.state, def, s.id("hero"), GridPos(1, 0)).isEmpty())
    }

    @Test
    fun affectedByExcludesSelfWhenFilterSaysSo() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val def = actionDefWith(Targeting(TargetMode.Point, Range.SelfRange, Shape.Sphere(1), filter = TargetFilter(excludeSelf = true)))
        assertTrue(affectedBy(s.state, def, s.id("hero"), GridPos(0, 0)).isEmpty())
    }

    @Test
    fun affectedByFiltersByHasStatus() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            statusDef("marked") {}
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("markedGoblin") { archetype("dummy"); at(1, 0); hp(10) }
            entity("plainGoblin") { archetype("dummy"); at(2, 0); hp(10) }
            status("markedGoblin", "marked")
        }
        val def = actionDefWith(Targeting(TargetMode.Point, Range.Tiles(5), Shape.Sphere(2), filter = TargetFilter(hasStatus = StatusId("marked")), maxTargets = 5))
        val hit = affectedBy(s.state, def, s.id("hero"), GridPos(1, 0))
        assertEquals(listOf(s.id("markedGoblin")), hit)
    }

    // --- consistency property from docs/09-test-plan.md ---

    @Test
    fun everyLegalTargetTileIsInsideItsOwnAffectedSet() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("a") { archetype("dummy"); at(1, 0); hp(10) }
            entity("b") { archetype("dummy"); at(2, 0); hp(10) }
            entity("c") { archetype("dummy"); at(3, 0); hp(10) }
        }
        val def = actionDefWith(Targeting(TargetMode.SingleEntity, Range.Tiles(5), Shape.Single))
        for (tile in legalTargets(s.state, s.id("hero"), def, s.catalog)) {
            val occupant = s.state.occupancy[tile]
            assertTrue(occupant != null && occupant in affectedBy(s.state, def, s.id("hero"), tile), "legal tile $tile must hit its own occupant")
        }
    }

    // --- MAX_TARGETS guard ---

    @Test
    fun affectedByThrowsRatherThanSilentlyTruncatingWhenCandidatesExceedMaxTargets() {
        val s = scenario {
            map(60, 60)
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            // MAX_TARGETS+1 entities in a row, all within a generous blast radius of the center point.
            for (i in 0..MAX_TARGETS) {
                entity("e$i") { archetype("dummy"); at(i, 25); hp(10) }
            }
        }
        val def = actionDefWith(Targeting(TargetMode.Point, Range.Tiles(60), Shape.Sphere(radius = 40), maxTargets = 999))
        assertFailsWith<IllegalStateException> {
            affectedBy(s.state, def, s.id("hero"), GridPos(MAX_TARGETS / 2, 25))
        }
    }

    private fun actionDefWith(targeting: Targeting) = de.jackbeback.pocketquest.core.model.ActionDef(
        id = de.jackbeback.pocketquest.core.model.ActionId("test"),
        name = "test",
        cost = de.jackbeback.pocketquest.core.model.Cost(de.jackbeback.pocketquest.core.model.ActionCost.Main),
        targeting = targeting,
        effects = emptyList(),
    )
}
