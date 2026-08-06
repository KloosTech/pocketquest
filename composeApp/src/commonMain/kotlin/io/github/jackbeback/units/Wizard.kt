package io.github.jackbeback.units

import io.github.jackbeback.data.*
import io.github.jackbeback.ui.*
import io.github.jackbeback.util.*

val wizard = Player(
    name = "Wizard",
    pos = (5 to 5).toPosition(),
    stats = Stats(
        6,
        8,
        10,
        14,
        12,
        10,
        8
    ),
    resources = Resources(health = createResource(8))
)
