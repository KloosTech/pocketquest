package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.content.dsl.Dice
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for [Dice] — the core randomness primitive used by all skill effects.
 *
 * Each test verifies that [Dice.roll] always produces a value within the
 * mathematically valid range for the given configuration, regardless of which
 * random values the implementation picks. Tests are repeated 100 times to catch
 * edge cases that a single roll might miss.
 */
class DiceTest {

    // ── Range correctness ─────────────────────────────────────────────────────

    @Test
    fun `1d6 always returns value between 1 and 6`() {
        val dice = Dice(count = 1, sides = 6)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 1..6, "Expected 1..6 but got $result")
        }
    }

    @Test
    fun `2d4 always returns value between 2 and 8`() {
        val dice = Dice(count = 2, sides = 4)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 2..8, "Expected 2..8 but got $result")
        }
    }

    @Test
    fun `3d6 always returns value between 3 and 18`() {
        val dice = Dice(count = 3, sides = 6)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 3..18, "Expected 3..18 but got $result")
        }
    }

    // ── Bonus modifier ────────────────────────────────────────────────────────

    @Test
    fun `1d4 plus 2 bonus always returns value between 3 and 6`() {
        val dice = Dice(count = 1, sides = 4, bonus = 2)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 3..6, "Expected 3..6 but got $result")
        }
    }

    @Test
    fun `1d4 plus 1 bonus matches magic-missile damage range`() {
        // magic_missile uses Dice(1, 4, 1) → range [2, 5]
        val dice = Dice(count = 1, sides = 4, bonus = 1)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 2..5, "Expected 2..5 but got $result")
        }
    }

    @Test
    fun `1d4 plus 2 bonus matches basic-heal range`() {
        // basic_heal uses Dice(1, 4, 2) → range [3, 6]
        val dice = Dice(count = 1, sides = 4, bonus = 2)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 3..6, "Expected 3..6 but got $result")
        }
    }

    @Test
    fun `1d6 plus 2 bonus matches fireball range`() {
        // fireball uses Dice(1, 6, 2) → range [3, 8]
        val dice = Dice(count = 1, sides = 6, bonus = 2)
        repeat(100) {
            val result = dice.roll()
            assertTrue(result in 3..8, "Expected 3..8 but got $result")
        }
    }

    // ── Degenerate cases ──────────────────────────────────────────────────────

    @Test
    fun `1d1 always returns exactly 1`() {
        val dice = Dice(count = 1, sides = 1)
        repeat(20) {
            assertTrue(dice.roll() == 1)
        }
    }

    @Test
    fun `1d1 with bonus 5 always returns exactly 6`() {
        val dice = Dice(count = 1, sides = 1, bonus = 5)
        repeat(20) {
            assertTrue(dice.roll() == 6)
        }
    }

    @Test
    fun `zero bonus is the same as default bonus`() {
        val withDefault = Dice(count = 1, sides = 6)
        val withZero = Dice(count = 1, sides = 6, bonus = 0)
        // Both describe the same distribution — verify they share the same range
        repeat(50) {
            assertTrue(withDefault.roll() in 1..6)
            assertTrue(withZero.roll() in 1..6)
        }
    }

    // ── Data-class equality ───────────────────────────────────────────────────

    @Test
    fun `two dice with same parameters are equal`() {
        val a = Dice(count = 2, sides = 6, bonus = 3)
        val b = Dice(count = 2, sides = 6, bonus = 3)
        assertTrue(a == b, "Identical Dice instances should be equal")
    }

    @Test
    fun `dice with different parameters are not equal`() {
        val a = Dice(count = 1, sides = 6)
        val b = Dice(count = 1, sides = 8)
        assertTrue(a != b, "Dice with different sides should not be equal")
    }
}
