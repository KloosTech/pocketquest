package de.jackbeback.pocketquest.game.battle

import androidx.compose.ui.graphics.ImageBitmap
import de.jackbeback.pocketquest.content.map.MapConfig
import de.jackbeback.pocketquest.util.toImageBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import pocketquest.composeapp.generated.resources.Res

/**
 * Pre-loads all tiles for a battle background map and exposes them as an [ImageBitmap] cache.
 *
 * Loading runs once on a background coroutine when the singleton is first created.
 * The [tiles] flow starts as an empty map and is populated when loading completes,
 * causing the battle canvas to switch from placeholder colours to actual tile images.
 */
class BattleTileCache(private val config: MapConfig) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _tiles = MutableStateFlow<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap())
    /** Map of (col, row) → ImageBitmap, keyed by tile grid position. Empty while loading. */
    val tiles: StateFlow<Map<Pair<Int, Int>, ImageBitmap>> = _tiles

    init {
        loadAll()
    }

    @OptIn(ExperimentalResourceApi::class)
    private fun loadAll() {
        scope.launch {
            val result = (0 until config.rowCount).flatMap { r ->
                (0 until config.colCount).map { c ->
                    async {
                        runCatching {
                            val bytes = Res.readBytes("files/tiles/${config.id}/$c-$r.png")
                            Pair(c, r) to bytes.toImageBitmap()
                        }.getOrNull()
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .toMap()
            _tiles.value = result
        }
    }
}
