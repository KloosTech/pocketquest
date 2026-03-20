package de.jackbeback.pocketquest.data.db

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** High-level persistence facade used by HintManager and RunStateHolder. */
class PersistenceRepository(private val db: PocketQuestDatabase) {

    // ── Hints ────────────────────────────────────────────────────────────────

    /** Returns all hint IDs that have already been shown (for in-memory preload). */
    suspend fun loadSeenHintIds(): Set<String> =
        db.seenHintDao().allIds().toSet()

    /** Persists a hint as seen. No-op if already stored (IGNORE conflict strategy). */
    suspend fun markHintSeen(hintId: String) =
        db.seenHintDao().markSeen(SeenHintEntity(hintId))

    // ── Generic key-value helpers ─────────────────────────────────────────────

    suspend fun getString(key: String): String? =
        db.persistentKvDao().get(key)

    suspend fun putString(key: String, value: String) =
        db.persistentKvDao().put(PersistentKvEntity(key, value))

    // ── Typed helpers for known keys ──────────────────────────────────────────

    suspend fun loadUnlockedCharacters(): List<String> {
        val raw = getString(KEY_UNLOCKED_CHARACTERS) ?: return listOf("wizard")
        return json.decodeFromString<List<String>>(raw)
    }

    suspend fun saveUnlockedCharacters(ids: List<String>) =
        putString(KEY_UNLOCKED_CHARACTERS, json.encodeToString(ids))

    companion object {
        private const val KEY_UNLOCKED_CHARACTERS = "unlocked_characters"
    }
}
