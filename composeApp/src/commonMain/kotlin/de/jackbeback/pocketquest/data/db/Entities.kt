package de.jackbeback.pocketquest.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records which tutorial hints have already been shown to the player. */
@Entity(tableName = "seen_hints")
data class SeenHintEntity(
    @PrimaryKey val hintId: String,
)

/**
 * Generic key-value store for persistent game state (character unlocks, etc.).
 * Values are JSON-encoded strings.
 */
@Entity(tableName = "persistent_kv")
data class PersistentKvEntity(
    @PrimaryKey val key: String,
    val value: String,
)
