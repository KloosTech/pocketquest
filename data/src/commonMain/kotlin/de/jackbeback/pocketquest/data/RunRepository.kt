package de.jackbeback.pocketquest.data

import de.jackbeback.pocketquest.core.run.CURRENT_RUN_SCHEMA
import de.jackbeback.pocketquest.core.run.RunState
import kotlinx.serialization.json.Json

/**
 * The only place a [RunState] gets turned into bytes and back — mirrors [SaveRepository]'s shape,
 * with its own independent [RunSnapshotMigrations] chain (docs/11-run-state.md).
 */
class RunRepository(private val dao: RunSlotDao, private val json: Json = Json) {

    suspend fun save(runId: String, updatedAt: Long, partySummary: String, run: RunState) {
        val snapshot = json.encodeToString(RunState.serializer(), run).encodeToByteArray()
        dao.upsert(
            RunSlotRow(
                runId = runId,
                schemaVersion = CURRENT_RUN_SCHEMA,
                updatedAt = updatedAt,
                act = run.act,
                partySummary = partySummary,
                snapshot = snapshot,
            ),
        )
    }

    suspend fun load(runId: String): RunState? {
        val row = dao.get(runId) ?: return null
        val raw = json.parseToJsonElement(row.snapshot.decodeToString())
        val migrated = RunSnapshotMigrations.migrate(raw, row.schemaVersion)
        return json.decodeFromJsonElement(RunState.serializer(), migrated)
    }

    suspend fun listRuns(): List<RunSlotRow> = dao.listAll()

    suspend fun delete(runId: String) = dao.deleteById(runId)
}
