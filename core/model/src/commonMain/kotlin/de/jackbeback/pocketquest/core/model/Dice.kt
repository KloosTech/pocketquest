package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DiceSpec(val count: Int, val sides: Int, val modifier: Int = 0)

data class RollResult(val rolls: List<Int>, val modifier: Int) {
    val total: Int get() = rolls.sum() + modifier
}
