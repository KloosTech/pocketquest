package de.jackbeback.pocketquest.data

import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import kotlinx.serialization.json.Json

/**
 * `allowStructuredMapKeys` — `BattleMap.terrain: Map<GridPos, TileType>` keys by a data class, not
 * a primitive/enum; the plain default `Json` instance rejects that at encode time (never surfaced
 * until a real save round-tripped a map with actual painted terrain — every fixture/demo state
 * before that used an empty `terrain` map, which never exercises a real map key at all).
 */
private val SNAPSHOT_JSON = Json { allowStructuredMapKeys = true }

/**
 * The only place a [Resolver] gets turned into bytes and back — everything
 * else in :core:rules stays pure/in-memory. `updatedAt` is a caller-supplied
 * epoch-millis timestamp rather than this module reaching for a platform
 * clock itself, keeping :data's own surface minimal.
 */
class SaveRepository(private val dao: SaveSlotDao, private val json: Json = SNAPSHOT_JSON) {

    suspend fun save(
        id: String,
        campaignId: String,
        updatedAt: Long,
        label: String,
        resolver: Resolver,
        thumbnailPath: String? = null,
        autosave: Boolean = false,
    ) {
        val snapshot = json.encodeToString(Resolver.serializer(), resolver).encodeToByteArray()
        dao.upsert(
            SaveSlotRow(
                id = id,
                campaignId = campaignId,
                schemaVersion = CURRENT_SCHEMA,
                updatedAt = updatedAt,
                label = label,
                thumbnailPath = thumbnailPath,
                autosave = autosave,
                snapshot = snapshot,
            ),
        )
    }

    suspend fun load(id: String): Resolver? {
        val row = dao.get(id) ?: return null
        val raw = json.parseToJsonElement(row.snapshot.decodeToString())
        val migrated = SnapshotMigrations.migrate(raw, row.schemaVersion)
        return json.decodeFromJsonElement(Resolver.serializer(), migrated)
    }

    suspend fun listSlots(campaignId: String): List<SaveSlotRow> = dao.listByCampaign(campaignId)

    suspend fun delete(id: String) = dao.deleteById(id)

    /** Autosaves overwrite a fixed slot id rather than accumulating — see docs/06-persistence.md. */
    fun autosaveId(campaignId: String): String = "autosave-$campaignId"
}
