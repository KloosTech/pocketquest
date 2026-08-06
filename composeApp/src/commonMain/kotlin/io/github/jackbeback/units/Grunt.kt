package io.github.jackbeback.units

import io.github.jackbeback.data.*
import io.github.jackbeback.ui.*
import io.github.jackbeback.util.*

fun createGrunts(amount: Int = 1): List<Enemy> {
    // create amount distinct Pairs of int with a defined range
    val positions = buildList {
        val taken = mutableSetOf<Pair<Int, Int>>()
        while (size < amount) {
            val pos = (3..7 to 2 .. 2).let { (xRange, yRange) ->
                xRange.random() to yRange.random()
            }
            if (pos !in taken) {
                taken.add(pos)
                add(pos)
            }
        }
    }

    return positions.mapIndexed { index, pos ->
        Enemy(
            name = "Grunt $index",
            pos = pos.toPosition(),
            stats = Stats(
                12,
                8,
                10,
                3,
                3,
                2,
                4
            ),
            resources = Resources(health = createResource(14)),
            resistances = mapOf(DamageType.FIRE to 0.5f)
        )
    }
}
