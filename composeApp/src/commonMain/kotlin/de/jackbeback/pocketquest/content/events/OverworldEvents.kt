package de.jackbeback.pocketquest.content.events

import de.jackbeback.pocketquest.content.definitions.chieftainTemplate
import de.jackbeback.pocketquest.content.definitions.gruntTemplate
import de.jackbeback.pocketquest.content.definitions.veteranGruntTemplate

private const val MAP_ID = "KaerMorhen"

/**
 * Events laid out in graph layers (y=0.0 top / boss, y=1.0 bottom / start).
 *
 * Layer 0 (y=0.88)  — Start
 * Layer 1 (y=0.70)  — Scout · Patrol · Ambush
 * Layer 2 (y=0.52)  — Duo · Veteran · Campfire
 * Layer 3 (y=0.34)  — Elite Squad · Shrine
 * Layer 4 (y=0.16)  — Boss
 */
val kaerMorhenEvents: List<OverworldEvent> = listOf(

    OverworldEvent.StartNode(
        id = "start", mapId = MAP_ID, x = 0.50, y = 0.88,
    ),

    // ── Layer 1 ──────────────────────────────────────────────────────────────
    OverworldEvent.BattleEncounter(
        id = "km_grunt_patrol_1", mapId = MAP_ID, x = 0.22, y = 0.70,
        label = "Scout", enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id = "km_grunt_patrol_2", mapId = MAP_ID, x = 0.50, y = 0.70,
        label = "Patrol", enemies = listOf(gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id = "km_grunt_ambush", mapId = MAP_ID, x = 0.78, y = 0.70,
        label = "Ambush", enemies = listOf(gruntTemplate),
    ),

    // ── Layer 2 ──────────────────────────────────────────────────────────────
    OverworldEvent.BattleEncounter(
        id = "km_grunt_duo", mapId = MAP_ID, x = 0.28, y = 0.52,
        label = "Duo", enemies = listOf(gruntTemplate, gruntTemplate),
    ),
    OverworldEvent.BattleEncounter(
        id = "km_veteran_encounter", mapId = MAP_ID, x = 0.54, y = 0.52,
        label = "Veteran", enemies = listOf(veteranGruntTemplate),
    ),
    OverworldEvent.RestSite(
        id = "km_rest_campfire", mapId = MAP_ID, x = 0.80, y = 0.52,
        label = "Campfire", healPercent = 0.40f,
    ),

    // ── Layer 3 ──────────────────────────────────────────────────────────────
    OverworldEvent.BattleEncounter(
        id = "km_elite_squad", mapId = MAP_ID, x = 0.34, y = 0.34,
        label = "Elite Squad", enemies = listOf(veteranGruntTemplate, gruntTemplate),
    ),
    OverworldEvent.RestSite(
        id = "km_rest_shrine", mapId = MAP_ID, x = 0.66, y = 0.34,
        label = "Shrine", healPercent = 0.60f,
    ),

    // ── Layer 4 ──────────────────────────────────────────────────────────────
    OverworldEvent.BattleEncounter(
        id = "km_boss", mapId = MAP_ID, x = 0.50, y = 0.16,
        label = "Boss", enemies = listOf(chieftainTemplate, veteranGruntTemplate, gruntTemplate),
        isBoss = true,
    ),
)

/**
 * Directed-acyclic graph for KaerMorhen.
 *
 * start ──► scout, patrol, ambush
 * scout ──► duo, veteran
 * patrol ──► duo, campfire
 * ambush ──► veteran, campfire
 * duo ──► elite_squad
 * veteran ──► elite_squad, shrine
 * campfire ──► shrine
 * elite_squad ──► boss
 * shrine ──► boss
 */
val kaerMorhenGraph = MapGraph(
    mapId       = MAP_ID,
    startNodeId = "start",
    nodes = listOf(
        MapNode("start",                 listOf("km_grunt_patrol_1", "km_grunt_patrol_2", "km_grunt_ambush")),
        MapNode("km_grunt_patrol_1",     listOf("km_grunt_duo", "km_veteran_encounter")),
        MapNode("km_grunt_patrol_2",     listOf("km_grunt_duo", "km_rest_campfire")),
        MapNode("km_grunt_ambush",       listOf("km_veteran_encounter", "km_rest_campfire")),
        MapNode("km_grunt_duo",          listOf("km_elite_squad")),
        MapNode("km_veteran_encounter",  listOf("km_elite_squad", "km_rest_shrine")),
        MapNode("km_rest_campfire",      listOf("km_rest_shrine")),
        MapNode("km_elite_squad",        listOf("km_boss")),
        MapNode("km_rest_shrine",        listOf("km_boss")),
        MapNode("km_boss",               emptyList()),
    ),
)

/** Master list consumed by the Koin module. */
val allOverworldEvents: List<OverworldEvent> = kaerMorhenEvents
