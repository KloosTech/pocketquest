package de.jackbeback.pocketquest.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `snapshot` is the serialized [de.jackbeback.pocketquest.core.rules.resolver.Resolver]
 * — not just GameState, so a half-answered reaction dialog survives
 * process death — see docs/06-persistence.md. Room is a sink here, never
 * a source during play: this row is written at specific moments
 * (AwaitingInput, end of turn, onStop, explicit save), not per event.
 */
@Entity(tableName = "save_slot")
data class SaveSlotRow(
    @PrimaryKey val id: String,
    val campaignId: String,
    val schemaVersion: Int,
    val updatedAt: Long,
    val label: String,
    val thumbnailPath: String?,
    val autosave: Boolean,
    val snapshot: ByteArray,
) {
    // ByteArray breaks data class equals/hashCode by reference — Room needs the class to
    // compile as a data class (for copy() etc.) but correctness here isn't equality-sensitive,
    // this is just to avoid a footgun for anyone who does compare two rows.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SaveSlotRow) return false
        return id == other.id && campaignId == other.campaignId && schemaVersion == other.schemaVersion &&
            updatedAt == other.updatedAt && label == other.label && thumbnailPath == other.thumbnailPath &&
            autosave == other.autosave && snapshot.contentEquals(other.snapshot)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + campaignId.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + (thumbnailPath?.hashCode() ?: 0)
        result = 31 * result + autosave.hashCode()
        result = 31 * result + snapshot.contentHashCode()
        return result
    }
}
