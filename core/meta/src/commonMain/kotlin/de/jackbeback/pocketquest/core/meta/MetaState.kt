package de.jackbeback.pocketquest.core.meta

import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Equipment
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Schema version for the serialized [MetaState] blob — independent of [de.jackbeback.pocketquest.data]'s own Room table version, same split doc06/doc11 already draw between blob-content versioning and SQL-schema versioning. */
const val CURRENT_META_SCHEMA = 1

@JvmInline
@Serializable
value class ChampionId(val raw: String)

/**
 * docs/12-progression.md — the forever-lived layer above a run. There is only ever one
 * [MetaState] per install (no slots, unlike save games), so nothing here needs a run/slot id.
 */
@Serializable
data class MetaState(
    val roster: Map<ChampionId, ChampionRecord> = emptyMap(),
    /** The one permanent currency stash — credited by run completion and idle accrual, see doc12. */
    val bank: Int = 0,
    val unlocks: Set<Unlock> = emptySet(),
    val schemaVersion: Int = CURRENT_META_SCHEMA,
)

/**
 * A persistent character. `equipment` is the only thing that changes over a Champion's life —
 * there is no leveling (docs/10-game-loop.md "No leveling"), so power comes entirely from gear.
 */
@Serializable
data class ChampionRecord(
    val id: ChampionId,
    val name: String,
    val archetype: ArchetypeId,
    val equipment: Equipment = Equipment.EMPTY,
    val status: ChampionStatus = ChampionStatus.Available,
    /** Epoch millis; idle-accrual income is computed from the elapsed time since this, once on app open — never a live clock read inside a deterministic layer. */
    val lastAccrualAt: Long = 0L,
)

@Serializable
enum class ChampionStatus {
    Available,
    OnRun,
    /** No content yet — the hook for a future wall-clock/idle mission feature (docs/12-progression.md). */
    OnMission,
}

/**
 * A plain enum, not a generic feature-flag system — deliberately small (docs/12-progression.md).
 * Add a case per future unlock as content actually needs one, don't generalize ahead of a second
 * real example.
 */
@Serializable
enum class Unlock { PartyMode }
