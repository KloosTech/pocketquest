package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * Carries only a reference to its [StatusDef] plus instance state — not a
 * modifier list. See [ModifierSource] for why.
 */
@Serializable
data class ActiveStatus(
    val def: StatusId,
    val sourceId: EntityId?,
    val linkId: LinkId?,
    val stacks: Int = 1,
    val expiry: Expiry,
    val saveEnds: SaveSpec? = null,
    val appliedAtVersion: Long,
)
