package de.jackbeback.pocketquest.ecs.components.core

/** Per-turn action flags. Movement is tracked separately by [MovementPointsComponent]. */
data class TurnStateComponent(val hasActed: Boolean = false)
