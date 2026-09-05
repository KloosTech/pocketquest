package de.jackbeback.pocketquest.core.rules.content

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EnemySpawn
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartEncounterTest {

    private fun archetype(id: String) = Archetype(
        id = ArchetypeId(id), name = id,
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 10, baseAc = 10, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 0,
    )

    private val hero = archetype("hero")
    private val goblin = archetype("goblin")

    private fun catalog(map: BattleMapDef, encounter: EncounterSpec) = Catalog(
        archetypes = mapOf(hero.id to hero, goblin.id to goblin),
        maps = mapOf(map.id to map),
        encounters = mapOf(encounter.id to encounter),
    )

    @Test
    fun spawnsPartyIntoPartyTilesAndEnemiesIntoTheirRoleTiles() {
        val map = BattleMapDef(
            id = MapId("room"), width = 5, height = 5,
            spawns = listOf(
                SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0), GridPos(1, 0))),
                SpawnZone(SpawnRole.Enemy, listOf(GridPos(4, 4), GridPos(3, 4))),
            ),
        )
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id, enemies = listOf(EnemySpawn(goblin.id, SpawnRole.Enemy, count = 2)))
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id, hero.id))

        assertEquals(4, state.entities.size)
        val party = state.entities.filter { it.actor?.faction == Faction.Player }
        val enemies = state.entities.filter { it.actor?.faction == Faction.Enemy }
        assertEquals(setOf(GridPos(0, 0), GridPos(1, 0)), party.map { it.pos }.toSet())
        assertEquals(setOf(GridPos(4, 4), GridPos(3, 4)), enemies.map { it.pos }.toSet())
    }

    @Test
    fun everySpawnedEntityHasFullHpApFromItsDerivedStats() {
        val map = BattleMapDef(id = MapId("room"), width = 3, height = 3, spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0)))))
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id)
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id))
        val entity = state.entities.single()
        assertEquals(hero.baseMaxHp, entity.health?.current)
        assertEquals(hero.baseMaxAp, entity.resources?.ap)
    }

    @Test
    fun tooFewSpawnTilesTruncatesRatherThanCrashing() {
        val map = BattleMapDef(id = MapId("room"), width = 3, height = 3, spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0)))))
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id)
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id, hero.id, hero.id))
        assertEquals(1, state.entities.size, "only one party tile existed, the other two party members are silently dropped")
    }

    @Test
    fun turnOrderContainsEveryEntityExactlyOnce() {
        val map = BattleMapDef(
            id = MapId("room"), width = 5, height = 5,
            spawns = listOf(
                SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0), GridPos(1, 0))),
                SpawnZone(SpawnRole.Enemy, listOf(GridPos(4, 4), GridPos(3, 4), GridPos(2, 4))),
            ),
        )
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id, enemies = listOf(EnemySpawn(goblin.id, SpawnRole.Enemy, count = 3)))
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id, hero.id))
        assertEquals(state.entities.map { it.id }.toSet(), state.turn.order.toSet())
        assertEquals(5, state.turn.order.size)
    }

    @Test
    fun nextEntityIdIsAdvancedPastEverySpawnedEntity() {
        val map = BattleMapDef(id = MapId("room"), width = 3, height = 3, spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0), GridPos(1, 0)))))
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id)
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id, hero.id))
        assertTrue(state.nextEntityId >= 2, "future SpawnEntity calls must not collide with ids already used here")
    }

    @Test
    fun fogOfWarMapStartsWithThePartysOwnVisibilityAlreadyRevealed() {
        // Without this, every enemy — even one standing right next to the party's own spawn —
        // would skip its very first turn, since a fresh fogOfWar map otherwise starts fully dark.
        val map = BattleMapDef(id = MapId("room"), width = 5, height = 5, spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0)))))
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id)
        val state = startEncounter(catalog(map, encounter), encounter, party = listOf(hero.id))
        assertTrue(state.revealedTiles.isNotEmpty())
        assertTrue(GridPos(1, 0) in state.revealedTiles, "adjacent to the party's own spawn tile")
    }

    @Test
    fun startEncounterWithPartyRefillsApForEntitiesArrivingWithZero() {
        // The real gameplay path (PartyMember.toEntity, :core:run) hands startEncounterWithParty
        // Entities with ap=0 hardcoded, relying on this to seed full AP the same way the enemy
        // loop above already does — unlike the plain startEncounter(catalog, encounter, party:
        // List<ArchetypeId>) overload this file's other tests use, which builds already-full-AP
        // Entities itself and so never exercised this gap.
        val map = BattleMapDef(id = MapId("room"), width = 3, height = 3, spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0)))))
        val encounter = EncounterSpec(EncounterId("e1"), "E1", map.id)
        val zeroApMember = Entity(
            EntityId(0), hero.id, pos = null, health = Health(hero.baseMaxHp),
            resources = Resources(ap = 0, mana = hero.baseMaxMana), actor = Actor(Faction.Player, Controller.Human),
        )
        val (state, spawnIds) = startEncounterWithParty(catalog(map, encounter), encounter, party = listOf(zeroApMember), rng = RngState(0L))
        val spawned = state.entities.single { it.id == spawnIds.single() }
        assertEquals(hero.baseMaxAp, spawned.resources?.ap)
    }
}
