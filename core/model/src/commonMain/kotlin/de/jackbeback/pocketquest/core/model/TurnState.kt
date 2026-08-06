package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/** Start, Main, End of one entity's turn — not Player/Enemy sides. */
@Serializable
enum class TurnPhase { Start, Main, End }

@Serializable
data class TurnState(
    val round: Int,
    val order: List<EntityId>,
    val activeIndex: Int,
    val phase: TurnPhase,
)
