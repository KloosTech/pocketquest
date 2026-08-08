package de.jackbeback.pocketquest.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per run, per docs/11-run-state.md's Persistence section. The `Resolver` rides inside
 * `snapshot` (serialized [de.jackbeback.pocketquest.core.run.RunState], including any live
 * `EncounterHandle.resolver`) rather than in its own row — they're always saved and loaded
 * together, splitting them risks a run and its encounter disagreeing. `partySummary` is
 * denormalized on purpose: a resume screen must render without deserializing a full run.
 */
@Entity(tableName = "run_slot")
data class RunSlotRow(
    @PrimaryKey val runId: String,
    val schemaVersion: Int,
    val updatedAt: Long,
    val act: Int,
    val partySummary: String,
    val snapshot: ByteArray,
) {
    // See SaveSlotRow's identical override — ByteArray breaks data class equals/hashCode by
    // reference, Room still needs a data class for copy() etc.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RunSlotRow) return false
        return runId == other.runId && schemaVersion == other.schemaVersion && updatedAt == other.updatedAt &&
            act == other.act && partySummary == other.partySummary && snapshot.contentEquals(other.snapshot)
    }

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + act
        result = 31 * result + partySummary.hashCode()
        result = 31 * result + snapshot.contentHashCode()
        return result
    }
}
