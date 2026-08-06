package io.github.jackbeback.util

import kotlin.random.*

enum class DiceType {
    D20,
    D100,
    D10,
    D4,
    D6,
    D8,
    D12
}

fun diceRollCheck(target: Int, type: DiceType = DiceType.D20): Boolean {
    val roll = when (type) {
        DiceType.D20 -> Random.nextInt(1, 21)
        DiceType.D100 -> Random.nextInt(1, 101)
        DiceType.D10 -> Random.nextInt(1, 11)
        DiceType.D4 -> Random.nextInt(1, 5)
        DiceType.D6 -> Random.nextInt(1, 7)
        DiceType.D8 -> Random.nextInt(1, 9)
        DiceType.D12 -> Random.nextInt(1, 13)
    }
    return roll >= target
}

fun roll(max: Int): Int = Random.nextInt(1, max + 1)

fun roll(type: DiceType, amount: Int = 1): Int{
    var total = 0
    for (i in 1..amount) {
        total += when (type) {
            DiceType.D20 -> roll(20)
            DiceType.D100 -> roll(100)
            DiceType.D10 -> roll(10)
            DiceType.D4 -> roll(4)
            DiceType.D6 -> roll(6)
            DiceType.D8 -> roll(8)
            DiceType.D12 -> roll(12)
        }
    }
    return total
}
