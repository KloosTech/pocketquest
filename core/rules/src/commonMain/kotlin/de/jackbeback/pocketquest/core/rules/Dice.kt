package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RollMode
import kotlin.random.Random

data class DiceSpec(val count: Int, val sides: Int, val modifier: Int = 0)

data class RollResult(val rolls: List<Int>, val modifier: Int) {
    val total: Int get() = rolls.sum() + modifier
}

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

/** Rolls a single d20. [mode] rolls twice and takes the higher/lower, per advantage/disadvantage. */
fun RngState.d20(mode: RollMode = RollMode.Normal): Pair<RngState, Int> {
    val rng = rngFor(this)
    val result = if (mode == RollMode.Normal) {
        rng.nextInt(1, 21)
    } else {
        val a = rng.nextInt(1, 21)
        val b = rng.nextInt(1, 21)
        if (mode == RollMode.Advantage) maxOf(a, b) else minOf(a, b)
    }
    return copy(calls = calls + 1) to result
}

fun RngState.roll(dice: DiceSpec): Pair<RngState, RollResult> {
    val rng = rngFor(this)
    val rolls = List(dice.count) { rng.nextInt(1, dice.sides + 1) }
    return copy(calls = calls + 1) to RollResult(rolls, dice.modifier)
}

fun resolveAdvantage(sides: Set<AdvSide>): RollMode = when {
    AdvSide.Advantage in sides && AdvSide.Disadvantage in sides -> RollMode.Normal
    AdvSide.Advantage in sides -> RollMode.Advantage
    AdvSide.Disadvantage in sides -> RollMode.Disadvantage
    else -> RollMode.Normal
}
