package de.jackbeback.pocketquest.game.battle

import de.jackbeback.pocketquest.content.map.TileMap
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import pocketquest.composeapp.generated.resources.Res

/**
 * Loads and caches [TileMap] JSON files from bundled resources.
 * Uses the same [Res.readBytes] pattern as [BattleTileCache] — works on all platforms.
 */
class TileMapRepository {
    private val cache = mutableMapOf<String, TileMap>()
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(mapId: String): TileMap? {
        cache[mapId]?.let { return it }
        return runCatching {
            val bytes = Res.readBytes("files/maps/$mapId.json")
            json.decodeFromString<TileMap>(String(bytes)).also { cache[mapId] = it }
        }.getOrNull()
    }

    fun getCached(mapId: String): TileMap? = cache[mapId]
}
