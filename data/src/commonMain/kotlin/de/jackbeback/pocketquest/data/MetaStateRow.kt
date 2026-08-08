package de.jackbeback.pocketquest.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A singleton row — there is only ever one [de.jackbeback.pocketquest.core.meta.MetaState] per
 * install, unlike [SaveSlotRow]'s slot list (docs/12-progression.md).
 */
@Entity(tableName = "meta_state")
data class MetaStateRow(
    @PrimaryKey val id: Int = 0,
    val schemaVersion: Int,
    val updatedAt: Long,
    val snapshot: ByteArray,
) {
    // See SaveSlotRow's identical override — ByteArray breaks data class equals/hashCode by
    // reference, Room still needs a data class for copy() etc.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MetaStateRow) return false
        return id == other.id && schemaVersion == other.schemaVersion &&
            updatedAt == other.updatedAt && snapshot.contentEquals(other.snapshot)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + schemaVersion
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + snapshot.contentHashCode()
        return result
    }
}
