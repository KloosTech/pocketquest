package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * Which GameEvent kind offers this reaction — matched generically against
 * any event kind. The extra geometric condition for a given kind (e.g. "did
 * the mover leave my reach" for MoveStepped) isn't data-driven here; it's
 * kind-specific logic in :core:rules, not a full condition-expression
 * language content authors write themselves.
 */
@Serializable
enum class ReactionTriggerKind { MoveStepped, DamageTaken, Died, StatusApplied, AttackRolled, SaveRolled, ResourcesSpent, Fizzled }

@Serializable
data class ReactionTrigger(val kind: ReactionTriggerKind)
