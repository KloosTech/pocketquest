package de.jackbeback.pocketquest.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Decision
import de.jackbeback.pocketquest.core.model.DecisionId
import de.jackbeback.pocketquest.core.model.DecisionRequest
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * JVM-only (desktopTest) — an in-memory Room database via BundledSQLiteDriver
 * is the simplest reliable way to exercise real Room read/write without
 * needing a platform file path (Android context, iOS document directory)
 * that only :app can provide. This is the doc06 "process death mid-decision"
 * case end to end: save a Resolver with a pending DecisionRequest, load it
 * back from an actual database, not just Json. Built by hand rather than
 * via :core:rules' fixture DSL — test sourceSets aren't shared across
 * modules without a testFixtures-style setup, not worth adding for one file.
 */
class SaveRepositoryTest {

    private lateinit var db: PocketQuestDatabase
    private lateinit var repository: SaveRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<PocketQuestDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = SaveRepository(db.saveSlotDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private val heroId = EntityId(0)
    private val goblinId = EntityId(1)

    private fun sampleResolver(): Resolver {
        val hero = Entity(
            id = heroId, archetype = ArchetypeId("dummy"), pos = GridPos(0, 0),
            health = Health(15), resources = null, actor = Actor(Faction.Player, Controller.Human),
            statuses = listOf(ActiveStatus(StatusId("bless"), sourceId = heroId, linkId = null, stacks = 2, expiry = Expiry.EndOfRound(4), appliedAtVersion = 0)),
        )
        val goblin = Entity(
            id = goblinId, archetype = ArchetypeId("dummy"), pos = GridPos(1, 0),
            health = Health(10), resources = null, actor = Actor(Faction.Enemy, Controller.Ai(de.jackbeback.pocketquest.core.model.AiProfileId("default"))),
        )
        val state = GameState(
            entities = listOf(hero, goblin),
            map = BattleMap(10, 10),
            turn = TurnState(round = 3, order = listOf(heroId, goblinId), activeIndex = 0, phase = TurnPhase.Main),
            rng = RngState(seed = 42, calls = 3),
        )
        return Resolver(
            state = state,
            stack = listOf(Effect.DealDamage(goblinId, 5, DamageType.Fire)),
            pending = DecisionRequest(DecisionId(7)),
            answers = mapOf(DecisionId(1) to Decision(DecisionId(1), accept = true)),
            emitted = listOf(GameEvent.TurnStarted(heroId, 3)),
            steps = 4,
        )
    }

    @Test
    fun savedResolverLoadsBackIdenticalIncludingAPendingDecision() = runTest {
        val resolver = sampleResolver()
        repository.save(id = "slot-1", campaignId = "camp-1", updatedAt = 1000L, label = "Round 3", resolver = resolver)

        val loaded = repository.load("slot-1")
        assertEquals(resolver, loaded)
        assertEquals(DecisionId(7), loaded?.pending?.id, "the mid-decision process-death case must survive a real Room round-trip")
    }

    @Test
    fun loadingAMissingSlotReturnsNull() = runTest {
        assertNull(repository.load("does-not-exist"))
    }

    @Test
    fun autosaveOverwritesTheSameSlotRatherThanAccumulating() = runTest {
        val id = repository.autosaveId("camp-1")
        repository.save(id = id, campaignId = "camp-1", updatedAt = 1000L, label = "first", resolver = sampleResolver(), autosave = true)
        repository.save(id = id, campaignId = "camp-1", updatedAt = 2000L, label = "second", resolver = sampleResolver(), autosave = true)

        val slots = repository.listSlots("camp-1")
        assertEquals(1, slots.size, "autosaves must overwrite the fixed slot id, not accumulate rows")
        assertEquals("second", slots.single().label)
    }

    @Test
    fun listSlotsOrdersByMostRecentlyUpdatedFirst() = runTest {
        repository.save(id = "a", campaignId = "camp-1", updatedAt = 1000L, label = "older", resolver = sampleResolver())
        repository.save(id = "b", campaignId = "camp-1", updatedAt = 2000L, label = "newer", resolver = sampleResolver())

        val slots = repository.listSlots("camp-1")
        assertEquals(listOf("newer", "older"), slots.map { it.label })
    }

    @Test
    fun deleteRemovesTheSlot() = runTest {
        repository.save(id = "a", campaignId = "camp-1", updatedAt = 1000L, label = "x", resolver = sampleResolver())
        repository.delete("a")
        assertNull(repository.load("a"))
    }

    @Test
    fun loadingASnapshotNewerThanCurrentSchemaFailsLoudly() = runTest {
        repository.save(id = "a", campaignId = "camp-1", updatedAt = 1000L, label = "x", resolver = sampleResolver())
        // Simulate a save written by a future app version.
        val row = db.saveSlotDao().get("a")!!
        db.saveSlotDao().upsert(row.copy(schemaVersion = CURRENT_SCHEMA + 1))

        try {
            repository.load("a")
            throw AssertionError("expected an exception for a snapshot newer than CURRENT_SCHEMA")
        } catch (e: IllegalArgumentException) {
            // expected — SnapshotMigrations.migrate refuses to guess
        }
    }
}
