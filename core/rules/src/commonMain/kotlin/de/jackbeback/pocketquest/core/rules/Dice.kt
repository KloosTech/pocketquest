package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.RollMode
import de.jackbeback.pocketquest.core.model.RollResult
import kotlin.random.Random

/**
 * Deterministically derives a fresh, independent [Random] for this exact
 * (seed, calls) pair via a splitmix64-style mix — O(1) regardless of how
 * many rolls came before, unlike replaying `calls` draws from one stream.
 * This is the only place in :core:rules that touches kotlin.random.Random,
 * and it never touches the ambient default instance.
 */
private const val SPLITMIX64_GAMMA = 0x9E3779B97F4A7C15UL
private const val SPLITMIX64_MIX1 = 0xBF58476D1CE4E5B9UL
private const val SPLITMIX64_MIX2 = 0x94D049BB133111EBUL

private fun rngFor(state: RngState): Random {
    var z = (state.seed xor state.calls).toULong() + SPLITMIX64_GAMMA
    z = (z xor (z shr 30)) * SPLITMIX64_MIX1
    z = (z xor (z shr 27)) * SPLITMIX64_MIX2
    return Random((z xor (z shr 31)).toLong())
}

/**
 * docs/22-dice-roll-ui-and-ability-checks.md: the discarded roll under Advantage/Disadvantage —
 * [d20] itself only ever returns [resolved] (the one that actually counted), which is all combat
 * math needs, but a BG3-style dual-die display needs [other] too, purely cosmetic. Null under
 * [RollMode.Normal], where only one die was ever rolled at all.
 */
data class D20Roll(val resolved: Int, val other: Int? = null)

/** Rolls a single d20. [mode] rolls twice and takes the higher/lower, per advantage/disadvantage. */
fun RngState.d20(mode: RollMode = RollMode.Normal): Pair<RngState, Int> {
    val (next, roll) = d20Detailed(mode)
    return next to roll.resolved
}

/** Same roll as [d20], with the discarded Advantage/Disadvantage die preserved — see [D20Roll]. */
fun RngState.d20Detailed(mode: RollMode = RollMode.Normal): Pair<RngState, D20Roll> {
    val rng = rngFor(this)
    val result = if (mode == RollMode.Normal) {
        D20Roll(rng.nextInt(1, 21))
    } else {
        val a = rng.nextInt(1, 21)
        val b = rng.nextInt(1, 21)
        if (mode == RollMode.Advantage) D20Roll(maxOf(a, b), minOf(a, b)) else D20Roll(minOf(a, b), maxOf(a, b))
    }
    return copy(calls = calls + 1) to result
}

fun RngState.roll(dice: DiceSpec): Pair<RngState, RollResult> {
    val rng = rngFor(this)
    val rolls = List(dice.count) { rng.nextInt(1, dice.sides + 1) }
    return copy(calls = calls + 1) to RollResult(rolls, dice.modifier)
}

/** Uniform int in `min..max` inclusive; `min >= max` short-circuits to [min] without consuming the RNG. */
fun RngState.rollRange(min: Int, max: Int): Pair<RngState, Int> {
    if (min >= max) return this to min
    val rng = rngFor(this)
    return copy(calls = calls + 1) to rng.nextInt(min, max + 1)
}

/** A single Bernoulli trial at [probability] (0.0..1.0). */
fun RngState.chance(probability: Double): Pair<RngState, Boolean> {
    val rng = rngFor(this)
    return copy(calls = calls + 1) to (rng.nextDouble() < probability)
}

fun resolveAdvantage(sides: Set<AdvSide>): RollMode = when {
    AdvSide.Advantage in sides && AdvSide.Disadvantage in sides -> RollMode.Normal
    AdvSide.Advantage in sides -> RollMode.Advantage
    AdvSide.Disadvantage in sides -> RollMode.Disadvantage
    else -> RollMode.Normal
}

/** D&D ability modifier: floor((score - 10) / 2). */
fun abilityModifier(score: Int): Int = if (score >= 10) (score - 10) / 2 else -((10 - score + 1) / 2)

/**
 * Whether a granted [RollContext] (from `Modifier.Roll`, via `Stats.rollGrants`) applies to the
 * actual roll being made. `this` is the granted context — e.g. `AttackRoll(vs = Enemy)` — `actual`
 * is the one built at the roll site from the real target/ability. `AttackRoll(vs = null)` means
 * "any attack roll"; a non-null `vs` only matches an attack against that specific faction.
 * `SavingThrow`/`AbilityCheck` have no such wildcard — they match by exact ability/skill.
 */
fun RollContext.matches(actual: RollContext): Boolean = when (this) {
    is RollContext.AttackRoll -> actual is RollContext.AttackRoll && (vs == null || vs == actual.vs)
    is RollContext.SavingThrow -> actual is RollContext.SavingThrow && ability == actual.ability
    is RollContext.AbilityCheck -> actual is RollContext.AbilityCheck && skill == actual.skill
}
