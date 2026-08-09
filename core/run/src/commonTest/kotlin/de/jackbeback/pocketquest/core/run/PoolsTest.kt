package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterScaling
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EnemySpawn
import de.jackbeback.pocketquest.core.model.EventDef
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.RngState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoolsTest {

    private val goblin = ArchetypeId("goblin")

    @Test
    fun pickContentIsDeterministicForTheSameRngState() {
        val pool = EncounterPool(act = 1, kind = NodeType.Combat, entries = listOf(EncounterId("a"), EncounterId("b"), EncounterId("c")))
        val (_, first) = pickContent(pool, RngState(seed = 5L))
        val (_, second) = pickContent(pool, RngState(seed = 5L))
        assertEquals(first, second)
    }

    @Test
    fun pickContentOnlyEverReturnsAPoolEntry() {
        val entries = listOf(EncounterId("a"), EncounterId("b"), EncounterId("c"))
        val pool = EncounterPool(act = 1, kind = NodeType.Combat, entries = entries)
        var rng = RngState(seed = 1L)
        repeat(20) {
            val (advanced, picked) = pickContent(pool, rng)
            assertTrue(picked in entries)
            rng = advanced
        }
    }

    @Test
    fun pickContentRejectsAnEmptyPool() {
        val pool = EncounterPool(act = 1, kind = NodeType.Combat, entries = emptyList())
        assertFailsWith<IllegalArgumentException> { pickContent(pool, RngState(seed = 1L)) }
    }

    private fun spec(scaling: EncounterScaling, enemyCount: Int = 2) = EncounterSpec(
        id = EncounterId("e1"), name = "E1", mapId = MapId("m1"), scaling = scaling,
        enemies = listOf(EnemySpawn(goblin, count = enemyCount), EnemySpawn(goblin, count = enemyCount)),
    )

    @Test
    fun applyScalingIsANoOpAtBaseline() {
        val base = spec(EncounterScaling(extraEnemiesPerPartySize = 0, extraEnemiesPerAct = 0))
        assertEquals(base, applyScaling(base, act = 1, partySize = 3))
    }

    @Test
    fun applyScalingAddsPerActEnemiesDistributedRoundRobin() {
        val base = spec(EncounterScaling(extraEnemiesPerAct = 2))
        val scaled = applyScaling(base, act = 3, partySize = 0) // act 3 -> (3-1)*2 = 4 extra
        assertEquals(base.enemies.sumOf { it.count } + 4, scaled.enemies.sumOf { it.count })
        assertEquals(2, scaled.enemies.size, "extras distribute across existing entries, never add new ones")
    }

    @Test
    fun applyScalingAddsPerPartySizeEnemies() {
        val base = spec(EncounterScaling(extraEnemiesPerPartySize = 1))
        val scaled = applyScaling(base, act = 1, partySize = 3)
        assertEquals(base.enemies.sumOf { it.count } + 3, scaled.enemies.sumOf { it.count })
    }

    @Test
    fun applyScalingLeavesEncounterWithNoEnemiesAlone() {
        val base = EncounterSpec(id = EncounterId("e1"), name = "E1", mapId = MapId("m1"), scaling = EncounterScaling(extraEnemiesPerAct = 5))
        assertEquals(base, applyScaling(base, act = 3, partySize = 3))
    }

    private fun hero() = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private fun catalog(encounter: EncounterSpec) = Catalog(
        archetypes = mapOf(hero().id to hero()),
        encounters = mapOf(encounter.id to encounter),
    )

    private fun run() = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = listOf(PartyMember(MemberId("m1"), "Lyra", hero().id, hp = 20, mana = 5, controller = Controller.Human)),
    )

    @Test
    fun resolveEncounterNodePicksAndScalesFromTheMatchingPool() {
        val encounter = spec(EncounterScaling(extraEnemiesPerPartySize = 1))
        val other = encounter.copy(id = EncounterId("other"))
        val cat = Catalog(archetypes = mapOf(hero().id to hero()), encounters = mapOf(encounter.id to encounter, other.id to other))
        val node = GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)
        // Two (identical-shape) entries, not one, so `pickContent`'s roll isn't short-circuited — a
        // single-entry pool doesn't need to consume the rng at all, which would make the "did this
        // advance the rng" assertion below vacuous.
        val pools = listOf(EncounterPool(act = 1, kind = NodeType.Combat, entries = listOf(encounter.id, other.id)))

        val (resolved, advancedRng) = resolveEncounterNode(run(), node, pools, cat)

        assertEquals(encounter.enemies.sumOf { it.count } + 1, resolved.enemies.sumOf { it.count })
        assertTrue(advancedRng.calls > run().rng.calls)
    }

    @Test
    fun resolveEncounterNodeFailsLoudlyWhenNoPoolMatches() {
        val encounter = spec(EncounterScaling())
        val cat = catalog(encounter)
        val node = GraphNode(NodeId("n1"), act = 1, type = NodeType.Shop)
        assertFailsWith<IllegalStateException> { resolveEncounterNode(run(), node, emptyList(), cat) }
    }

    @Test
    fun pickEventIsDeterministicForTheSameRngState() {
        val pool = EventPool(act = 1, entries = listOf(EventId("a"), EventId("b"), EventId("c")))
        val (_, first) = pickEvent(pool, RngState(seed = 5L))
        val (_, second) = pickEvent(pool, RngState(seed = 5L))
        assertEquals(first, second)
    }

    @Test
    fun pickEventRejectsAnEmptyPool() {
        assertFailsWith<IllegalArgumentException> { pickEvent(EventPool(act = 1, entries = emptyList()), RngState(seed = 1L)) }
    }

    @Test
    fun resolveEventNodePicksFromTheMatchingActPool() {
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = emptyList())
        val cat = Catalog(archetypes = mapOf(hero().id to hero()), events = mapOf(event.id to event))
        val node = GraphNode(NodeId("n1"), act = 1, type = NodeType.Event)
        val pools = listOf(EventPool(act = 1, entries = listOf(event.id)))

        val (resolved, advancedRng) = resolveEventNode(run(), node, pools, cat)

        assertEquals(event, resolved)
        assertTrue(advancedRng.calls >= run().rng.calls)
    }

    @Test
    fun resolveEventNodeFailsLoudlyWhenNoPoolMatchesTheAct() {
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = emptyList())
        val cat = Catalog(archetypes = mapOf(hero().id to hero()), events = mapOf(event.id to event))
        val node = GraphNode(NodeId("n1"), act = 2, type = NodeType.Event)
        assertFailsWith<IllegalStateException> { resolveEventNode(run(), node, listOf(EventPool(act = 1, entries = listOf(event.id))), cat) }
    }

    @Test
    fun resolveShopNodePicksFromTheMatchingActPool() {
        val shop = de.jackbeback.pocketquest.core.model.ShopDef(id = de.jackbeback.pocketquest.core.model.ShopId("s1"), act = 1, stock = emptyList())
        val cat = Catalog(archetypes = mapOf(hero().id to hero()), shops = mapOf(shop.id to shop))
        val node = GraphNode(NodeId("n1"), act = 1, type = NodeType.Shop)
        val pools = listOf(ShopPool(act = 1, entries = listOf(shop.id)))

        val (resolved, _) = resolveShopNode(run(), node, pools, cat)

        assertEquals(shop, resolved)
    }

    @Test
    fun resolveShopNodeFailsLoudlyWhenNoPoolMatchesTheAct() {
        val shop = de.jackbeback.pocketquest.core.model.ShopDef(id = de.jackbeback.pocketquest.core.model.ShopId("s1"), act = 1, stock = emptyList())
        val cat = Catalog(archetypes = mapOf(hero().id to hero()), shops = mapOf(shop.id to shop))
        val node = GraphNode(NodeId("n1"), act = 2, type = NodeType.Shop)
        assertFailsWith<IllegalStateException> { resolveShopNode(run(), node, listOf(ShopPool(act = 1, entries = listOf(shop.id))), cat) }
    }
}
