package de.jackbeback.pocketquest.content.events

import de.jackbeback.pocketquest.content.definitions.gruntTemplate

/** All events placed on the KaerMorhen map. Add new ones here. */
val kaerMorhenEvents: List<OverworldEvent> = listOf(
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_patrol_1",
        mapId   = "KaerMorhen",
        x       = 0.60, y = 0.50,
        label   = "Grunt Patrol",
        enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_ambush",
        mapId   = "KaerMorhen",
        x       = 0.75, y = 0.35,
        label   = "Grunt Ambush",
        enemies = listOf(gruntTemplate),
    ),
)

/** Master list consumed by the Koin module. */
val allOverworldEvents: List<OverworldEvent> = kaerMorhenEvents
