package de.jackbeback.pocketquest.ui

import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.WallHatchOsrParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WallHatchOsrGeneratorTest {

    // A 6x6 block of wall (cols/rows 2..7) surrounded by floor on every side, inside a 10x10 region.
    private fun ringedWallBlock(): (GridPos) -> Boolean = { pos -> pos.col in 2..7 && pos.row in 2..7 }

    @Test
    fun sameSeedAndLayoutProducesIdenticalGeometry() {
        val isWall = ringedWallBlock()
        val a = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 42L)
        val b = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 42L)
        assertEquals(a, b, "same wall layout + same seed must reproduce the identical bake — a Save must never reshuffle an already-baked map")
    }

    @Test
    fun aWallMassTouchingFloorProducesNonEmptyGeometry() {
        val lines = generateWallHatchOsr(ringedWallBlock(), 0 until 10, 0 until 10, seed = 1L)
        assertTrue(lines.isNotEmpty(), "a wall block bordered by floor on every side should generate real hatch strokes")
    }

    @Test
    fun aSolidRegionWithNoFloorAnywhereProducesNoGeometry() {
        // Every cell is a wall, none is ever eligible (docs/32's fade rule — density is 0 more than
        // FADE_DISTANCE_CELLS deep with no floor cell anywhere in range to measure distance from).
        val lines = generateWallHatchOsr({ true }, 0 until 10, 0 until 10, seed = 1L)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun emptyBoundsProduceNoGeometry() {
        val lines = generateWallHatchOsr(ringedWallBlock(), 0 until 0, 0 until 10, seed = 1L)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun generatedLineEndpointsStayWithinTheGeneratedBounds() {
        val lines = generateWallHatchOsr(ringedWallBlock(), 0 until 10, 0 until 10, seed = 7L)
        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertTrue(line.x0 in -0.5f..10.5f && line.x1 in -0.5f..10.5f, "line x-coordinates must stay near the requested column bounds")
            assertTrue(line.y0 in -0.5f..10.5f && line.y1 in -0.5f..10.5f, "line y-coordinates must stay near the requested row bounds")
        }
    }

    @Test
    fun differentSeedsCanProduceDifferentGeometry() {
        val isWall = ringedWallBlock()
        val a = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 1L)
        val b = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 2L)
        assertTrue(a != b, "the whole point of a seed is that Regenerate Hatch's reroll actually changes something")
    }

    // --- docs/34-wall-hatch-osr-configurable-params.md ---

    @Test
    fun largerGroupSizeProducesMoreLinesForTheSameFirstAttempt() {
        // Coverage set near-zero so the loop stops right after its first successful attempt —
        // otherwise a bigger group claims the target area in fewer attempts overall, which can
        // net out to a similar or even smaller total line count despite each attempt placing more.
        val isWall = ringedWallBlock()
        val tinyCoverage = WallHatchOsrParams(targetCoverage = 0.001f)
        val single = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 3L, params = tinyCoverage.copy(minGroupSize = 1, maxGroupSize = 1))
        val grouped = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 3L, params = tinyCoverage.copy(minGroupSize = 4, maxGroupSize = 4))
        assertTrue(grouped.size > single.size, "the one attempt placing a 4-line group must produce more strokes than the one placing a single line")
    }

    @Test
    fun angleJitterZeroKeepsEveryLineExactlyGridAligned() {
        val lines = generateWallHatchOsr(ringedWallBlock(), 0 until 10, 0 until 10, seed = 5L, params = WallHatchOsrParams(angleJitterDegrees = 0f))
        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            val dx = line.x1 - line.x0
            val dy = line.y1 - line.y0
            // Every growth direction is one of 0/45/90/135 degrees — |dx| and |dy| must be equal
            // (a 45-family line) or one of them exactly zero (an axis-aligned line), with no jitter
            // to rotate it off that lattice.
            val axisAligned = kotlin.math.abs(dx) < 1e-4f || kotlin.math.abs(dy) < 1e-4f
            val diagonal = kotlin.math.abs(kotlin.math.abs(dx) - kotlin.math.abs(dy)) < 1e-4f
            assertTrue(axisAligned || diagonal, "line ($dx, $dy) is off the 0/45/90/135 lattice despite zero angle jitter")
        }
    }

    @Test
    fun higherTargetCoverageGenerallyClaimsMoreOfTheEligibleArea() {
        val isWall = ringedWallBlock()
        val low = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 9L, params = WallHatchOsrParams(targetCoverage = 0.2f))
        val high = generateWallHatchOsr(isWall, 0 until 10, 0 until 10, seed = 9L, params = WallHatchOsrParams(targetCoverage = 0.95f))
        assertTrue(high.size >= low.size, "a higher coverage target must not generate less geometry than a lower one for the same seed/layout")
    }
}
