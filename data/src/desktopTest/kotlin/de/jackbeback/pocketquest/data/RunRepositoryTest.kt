package de.jackbeback.pocketquest.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.run.MemberId
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId
import de.jackbeback.pocketquest.core.run.PartyMember
import de.jackbeback.pocketquest.core.run.RunId
import de.jackbeback.pocketquest.core.run.RunState
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Same in-memory-Room shape as [SaveRepositoryTest]/[MetaRepositoryTest] — real Room read/write. */
class RunRepositoryTest {

    private lateinit var db: PocketQuestDatabase
    private lateinit var repository: RunRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<PocketQuestDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = RunRepository(db.runSlotDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun sampleRun() = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Combat)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = listOf(PartyMember(MemberId("m1"), name = "Lyra", archetype = ArchetypeId("fighter"), hp = 20, mana = 0, controller = Controller.Human)),
    )

    @Test
    fun loadingAMissingRunReturnsNull() = runTest {
        assertNull(repository.load("does-not-exist"))
    }

    @Test
    fun savedRunLoadsBackIdentical() = runTest {
        val run = sampleRun()
        repository.save(runId = "run1", updatedAt = 1000L, partySummary = "Lyra", run = run)
        assertEquals(run, repository.load("run1"))
    }

    @Test
    fun savingTwiceOverwritesTheSameRunRatherThanAccumulating() = runTest {
        repository.save(runId = "run1", updatedAt = 1000L, partySummary = "Lyra", run = sampleRun())
        val advanced = sampleRun().copy(act = 2)
        repository.save(runId = "run1", updatedAt = 2000L, partySummary = "Lyra", run = advanced)

        val runs = repository.listRuns()
        assertEquals(1, runs.size, "saving the same runId again must overwrite, not accumulate")
        assertEquals(2, runs.single().act)
    }

    @Test
    fun deleteRemovesTheRun() = runTest {
        repository.save(runId = "run1", updatedAt = 1000L, partySummary = "Lyra", run = sampleRun())
        repository.delete("run1")
        assertNull(repository.load("run1"))
    }
}
