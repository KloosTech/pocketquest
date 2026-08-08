package de.jackbeback.pocketquest.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionRecord
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.meta.Unlock
import de.jackbeback.pocketquest.core.model.ArchetypeId
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Same in-memory-Room shape as [SaveRepositoryTest] — real Room read/write, not just Json. */
class MetaRepositoryTest {

    private lateinit var db: PocketQuestDatabase
    private lateinit var repository: MetaRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<PocketQuestDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repository = MetaRepository(db.metaStateDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun sampleState() = MetaState(
        roster = mapOf(
            ChampionId("champ1") to ChampionRecord(ChampionId("champ1"), name = "Lyra", archetype = ArchetypeId("fighter")),
        ),
        bank = 100,
        unlocks = setOf(Unlock.PartyMode),
    )

    @Test
    fun loadingBeforeAnySaveReturnsNull() = runTest {
        assertNull(repository.load())
    }

    @Test
    fun savedMetaStateLoadsBackIdentical() = runTest {
        val state = sampleState()
        repository.save(updatedAt = 1000L, state = state)
        assertEquals(state, repository.load())
    }

    @Test
    fun savingTwiceOverwritesTheSingletonRowRatherThanFailing() = runTest {
        repository.save(updatedAt = 1000L, state = sampleState())
        val updated = sampleState().copy(bank = 250)
        repository.save(updatedAt = 2000L, state = updated)
        assertEquals(updated, repository.load())
    }
}
