package de.jackbeback.pocketquest.game.hint

import de.jackbeback.pocketquest.data.db.PersistenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tracks which hints have been shown to the player.
 *
 * Seen-hint IDs are persisted to Room so they survive app restarts.
 * An in-memory cache avoids DB round-trips during gameplay.
 *
 * Call [preload] once at startup (suspending) to populate the cache.
 */
class HintManager(
    private val repo: PersistenceRepository,
    private val ioScope: CoroutineScope,
) {
    private val seenIds = mutableSetOf<String>()

    /** Loads all previously seen hint IDs from the database into the in-memory cache. */
    suspend fun preload() {
        seenIds += repo.loadSeenHintIds()
    }

    /**
     * Returns [HintDefinition] for [id] if it has never been shown before, and marks
     * it as seen (both in-memory and in Room). Returns null if already seen.
     */
    fun tryShow(id: String): HintDefinition? {
        if (id in seenIds) return null
        seenIds += id
        ioScope.launch(Dispatchers.Default) { repo.markHintSeen(id) }
        return allHints.find { it.id == id }
    }
}
