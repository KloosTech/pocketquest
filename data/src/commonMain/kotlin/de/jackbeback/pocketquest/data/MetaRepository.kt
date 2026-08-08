package de.jackbeback.pocketquest.data

import de.jackbeback.pocketquest.core.meta.CURRENT_META_SCHEMA
import de.jackbeback.pocketquest.core.meta.MetaState
import kotlinx.serialization.json.Json

/**
 * The only place a [MetaState] gets turned into bytes and back — mirrors [SaveRepository]'s exact
 * shape, but a singleton (no `id`/`campaignId` parameters, [MetaStateDao.get] always reads row 0).
 */
class MetaRepository(private val dao: MetaStateDao, private val json: Json = Json) {

    suspend fun save(updatedAt: Long, state: MetaState) {
        val snapshot = json.encodeToString(MetaState.serializer(), state).encodeToByteArray()
        dao.upsert(MetaStateRow(schemaVersion = CURRENT_META_SCHEMA, updatedAt = updatedAt, snapshot = snapshot))
    }

    /** Null means no [MetaState] has ever been saved — callers should treat that as a fresh, empty roster, not an error. */
    suspend fun load(): MetaState? {
        val row = dao.get() ?: return null
        return json.decodeFromString(MetaState.serializer(), row.snapshot.decodeToString())
    }
}
