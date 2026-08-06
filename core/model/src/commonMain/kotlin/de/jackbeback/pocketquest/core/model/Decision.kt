package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * Minimal for now — no payload beyond the id. Real decision content
 * (what's being asked, what answers are valid) arrives with reactions and
 * actions in a later pass; this is just enough to test the resolver's
 * pause/resume mechanism in isolation.
 */
@Serializable
data class DecisionRequest(val id: DecisionId)

@Serializable
data class Decision(val id: DecisionId)
