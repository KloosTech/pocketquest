package de.jackbeback.pocketquest.data

import de.jackbeback.pocketquest.core.meta.CURRENT_META_SCHEMA
import de.jackbeback.pocketquest.core.meta.MetaState
import kotlinx.serialization.json.Json

/** No non-primitive map key exists in [MetaState] today, but [SaveRepository]/[RunRepository] both learned this lesson the hard way — set once here so a future field addition can't silently reintroduce it. */
private val SNAPSHOT_JSON = Json { allowStructuredMapKeys = true }

/**
 * The only place a [MetaState] gets turned into bytes and back — mirrors [SaveRepository]'s exact
 * shape, but a singleton (no `id`/`campaignId` parameters, [MetaStateDao.get] always reads row 0).
 */
class MetaRepository(private val dao: MetaStateDao, private val json: Json = SNAPSHOT_JSON) {

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
