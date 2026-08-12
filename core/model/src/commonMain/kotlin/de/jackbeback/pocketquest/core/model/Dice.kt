package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DiceSpec(val count: Int, val sides: Int, val modifier: Int = 0)

data class RollResult(val rolls: List<Int>, val modifier: Int) {
    val total: Int get() = rolls.sum() + modifier
}

/**
 * One labeled contribution to a d20 roll's modifier — docs/22's roll-card UI iterates these to
 * render a BG3-style chip row instead of a single opaque total. [dice] is for a future bonus-dice
 * status (docs/22 open question #5, e.g. Guidance's "+1d4") — nothing produces one yet, but the
 * card renderer supports it from the start so that mechanic won't need a UI rework later.
 */
@Serializable
data class RollTerm(val label: String, val flat: Int = 0, val dice: DiceSpec? = null)

/** Every [RollTerm] behind one d20 roll's modifier — docs/22's "cheapest honest version": an ability-modifier term plus one flat catch-all for everything else (items/statuses), not a fully-traced per-source ledger. */
@Serializable
data class RollBreakdown(val terms: List<RollTerm>) {
    val total: Int get() = terms.sumOf { it.flat }
}
