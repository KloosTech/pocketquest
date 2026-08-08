package de.jackbeback.pocketquest.core.rules.content

import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.rules.d20
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * docs/11-run-state.md specifies `startEncounter(run: RunState, spec: EncounterSpec, cat: Catalog):
 * RunState` — `RunState` (persistent party roster, run progress) doesn't exist yet, so this takes
 * an explicit [party] roster instead of sourcing one from a run. Everything downstream (spawning
 * into zones, initiative, the returned [GameState]) is the real primitive doc11 describes; only the
 * "where does the party come from" question is deferred, same "primitive without the layer that
 * drives it yet" shape as `RefillMana`/`MapExpansion` before it.
 *
 * Spawns [party] into the map's `Party` [de.jackbeback.pocketquest.core.model.SpawnZone] tiles in
 * order, then each [EncounterSpec.enemies] entry's `count` copies into its `role`'s zone tiles,
 * both truncating silently if there aren't enough tiles — `CatalogValidator` is what's supposed to
 * catch that at content-authoring time, this just never crashes if it wasn't caught. Initiative is
 * a live `d20` per spawned entity, highest first, ties broken by spawn order (stable sort).
 */
fun startEncounter(catalog: Catalog, encounter: EncounterSpec, party: List<ArchetypeId>, seed: Long = 0L): GameState {
    val mapDef = catalog.mapDef(encounter.mapId)
    val battleMap = mapDef.toBattleMap()
    val tilesByRole: Map<SpawnRole, MutableList<GridPos>> =
        mapDef.spawns.groupBy({ it.role }, { it.tiles }).mapValues { (_, lists) -> lists.flatten().toMutableList() }

    var nextId = 0L
    var rng = RngState(seed = seed)
    val entities = mutableListOf<Entity>()
    val initiative = mutableMapOf<EntityId, Int>()

    fun spawn(archetype: ArchetypeId, pos: GridPos, faction: Faction, controller: Controller) {
        val id = EntityId(nextId++)
        val preliminary = Entity(id, archetype, pos, health = null, resources = null, actor = Actor(faction, controller))
        val stats = preliminary.stats(catalog)
        entities += preliminary.copy(health = Health(stats.maxHp), resources = Resources(ap = stats.maxAp, mana = stats.maxMana))
        val (advanced, roll) = rng.d20()
        rng = advanced
        initiative[id] = roll
    }

    val partyTiles = tilesByRole[SpawnRole.Party] ?: mutableListOf()
    party.forEachIndexed { i, archetype ->
        val pos = partyTiles.getOrNull(i) ?: return@forEachIndexed
        spawn(archetype, pos, Faction.Player, Controller.Human)
    }

    for (enemySpawn in encounter.enemies) {
        val tiles = tilesByRole[enemySpawn.role] ?: mutableListOf()
        // docs/21-ai-behavior-spec.md: "different enemies, different AIs" is per-archetype — this
        // used to hardcode every enemy to the same profile regardless of archetype, which meant
        // Archetype.aiProfile was write-only until this read it back.
        val profile = catalog.archetype(enemySpawn.archetype).aiProfile
        repeat(enemySpawn.count) {
            val pos = tiles.removeFirstOrNull() ?: return@repeat
            spawn(enemySpawn.archetype, pos, Faction.Enemy, Controller.Ai(profile))
        }
    }

    val order = entities.map { it.id }.sortedByDescending { initiative.getValue(it) }
    return GameState(
        entities = entities,
        map = battleMap,
        turn = TurnState(round = 1, order = order, activeIndex = 0, phase = TurnPhase.Start),
        rng = rng,
        nextEntityId = nextId,
    )
}
