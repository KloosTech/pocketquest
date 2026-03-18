package de.jackbeback.pocketquest.content.events

import de.jackbeback.pocketquest.content.definitions.gruntTemplate
import de.jackbeback.pocketquest.content.definitions.veteranGruntTemplate

/** All events placed on the KaerMorhen map. Add new ones here. */
val kaerMorhenEvents: List<OverworldEvent> = listOf(
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_patrol_1",
        mapId   = "KaerMorhen",
        x       = 0.55, y = 0.45,
        label   = "Scout",
        enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_patrol_2",
        mapId   = "KaerMorhen",
        x       = 0.70, y = 0.60,
        label   = "Patrol",
        enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_ambush",
        mapId   = "KaerMorhen",
        x       = 0.45, y = 0.65,
        label   = "Ambush",
        enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_grunt_duo",
        mapId   = "KaerMorhen",
        x       = 0.65, y = 0.30,
        label   = "Duo",
        enemies = listOf(gruntTemplate, gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_veteran_encounter",
        mapId   = "KaerMorhen",
        x       = 0.80, y = 0.50,
        label   = "Veteran",
        enemies = listOf(veteranGruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id      = "km_elite_squad",
        mapId   = "KaerMorhen",
        x       = 0.75, y = 0.75,
        label   = "Elite Squad",
        enemies = listOf(veteranGruntTemplate, gruntTemplate),
    ),
)

/** Master list consumed by the Koin module. */
val allOverworldEvents: List<OverworldEvent> = kaerMorhenEvents
