package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.RollMode
import de.jackbeback.pocketquest.core.model.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiceTest {

    @Test
    fun sameSeedSameSequence() {
        var a = RngState(seed = 42)
        var b = RngState(seed = 42)
        repeat(20) {
            val (nextA, rollA) = a.d20()
            val (nextB, rollB) = b.d20()
            assertEquals(rollA, rollB)
            a = nextA
            b = nextB
        }
    }

    @Test
    fun callsAdvancesOnEveryRoll() {
        var state = RngState(seed = 1)
        repeat(10) {
            val before = state.calls
            val (next, _) = state.d20()
            assertEquals(before + 1, next.calls)
            assertNotEquals(state, next)
            state = next
        }
    }

    @Test
    fun differentCallsProduceIndependentRolls() {
        val results = (0 until 200).map { RngState(seed = 7, calls = it.toLong()).d20().second }
        // Not every value need differ, but a d20 stream of 200 draws should not be constant/degenerate.
        assertTrue(results.toSet().size > 5)
    }

    @Test
    fun advantageSkewsHighDisadvantageSkewsLow() {
        val sampleSize = 500
        fun average(mode: RollMode): Double =
            (0 until sampleSize).map { RngState(seed = 3, calls = it.toLong()).d20(mode).second }.average()

        val normalAvg = average(RollMode.Normal)
        val advAvg = average(RollMode.Advantage)
        val disAvg = average(RollMode.Disadvantage)

        // Expected means: normal 10.5, advantage ~13.825, disadvantage ~7.175 (two-d20 order statistics).
        assertTrue(advAvg > normalAvg, "advantage avg $advAvg should exceed normal avg $normalAvg")
        assertTrue(normalAvg > disAvg, "normal avg $normalAvg should exceed disadvantage avg $disAvg")
    }

    @Test
    fun advantageAndDisadvantageTableFromDoc03() {
        assertEquals(RollMode.Normal, resolveAdvantage(emptySet()))
        assertEquals(RollMode.Advantage, resolveAdvantage(setOf(AdvSide.Advantage)))
        assertEquals(RollMode.Disadvantage, resolveAdvantage(setOf(AdvSide.Disadvantage)))
        assertEquals(RollMode.Normal, resolveAdvantage(setOf(AdvSide.Advantage, AdvSide.Disadvantage)))
    }

    @Test
    fun rollSumsAllDiceAndModifier() {
        val (_, result) = RngState(seed = 5).roll(DiceSpec(count = 4, sides = 6, modifier = 3))
        assertEquals(4, result.rolls.size)
        assertTrue(result.rolls.all { it in 1..6 })
        assertEquals(result.rolls.sum() + 3, result.total)
    }

    // --- RollContext.matches() — KNOWN_ISSUES.md #11 ---

    @Test
    fun attackRollWithNullVsMatchesAnyFaction() {
        val granted = RollContext.AttackRoll(vs = null)
        assertTrue(granted.matches(RollContext.AttackRoll(vs = Faction.Enemy)))
        assertTrue(granted.matches(RollContext.AttackRoll(vs = Faction.Player)))
        assertTrue(granted.matches(RollContext.AttackRoll(vs = null)))
    }

    @Test
    fun attackRollWithASpecificVsOnlyMatchesThatFaction() {
        val granted = RollContext.AttackRoll(vs = Faction.Enemy)
        assertTrue(granted.matches(RollContext.AttackRoll(vs = Faction.Enemy)))
        assertFalse(granted.matches(RollContext.AttackRoll(vs = Faction.Player)))
        assertFalse(granted.matches(RollContext.AttackRoll(vs = null)), "a wildcard actual roll isn't a match for a specific grant")
    }

    @Test
    fun savingThrowMatchesOnlyTheSameAbility() {
        val granted = RollContext.SavingThrow(Ability.Dex)
        assertTrue(granted.matches(RollContext.SavingThrow(Ability.Dex)))
        assertFalse(granted.matches(RollContext.SavingThrow(Ability.Con)))
    }

    @Test
    fun abilityCheckMatchesOnlyTheSameSkill() {
        val granted = RollContext.AbilityCheck(Skill.Stealth)
        assertTrue(granted.matches(RollContext.AbilityCheck(Skill.Stealth)))
        assertFalse(granted.matches(RollContext.AbilityCheck(Skill.Perception)))
    }

    @Test
    fun differentRollContextKindsNeverMatch() {
        assertFalse(RollContext.AttackRoll(vs = null).matches(RollContext.SavingThrow(Ability.Dex)))
        assertFalse(RollContext.SavingThrow(Ability.Dex).matches(RollContext.AbilityCheck(Skill.Stealth)))
    }
}
