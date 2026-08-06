package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * `entities` is a List, not a Map — JSON object keys must be strings, and
 * this keeps a plain data class serializable without a custom key
 * serializer. [byId] and [occupancy] are derived indices, rebuilt on
 * access from the list; they are never themselves persisted or mutated.
 */
@Serializable
data class GameState(
    val entities: List<Entity>,
    val map: BattleMap,
    val turn: TurnState,
    val rng: RngState,
    val version: Long = 0,
    /** Monotonic source for fresh DecisionIds (e.g. when offering a reaction) — never reused, never reset. */
    val nextDecisionId: Long = 0,
    /** Monotonic source for fresh LinkIds (e.g. when starting concentration) — never reused, never reset. */
    val nextLinkId: Long = 0,
) {
    @Transient
    val byId: Map<EntityId, Entity> = entities.associateBy { it.id }

    @Transient
    val occupancy: Map<GridPos, EntityId> =
        entities.mapNotNull { e -> e.pos?.let { it to e.id } }.toMap()
}
