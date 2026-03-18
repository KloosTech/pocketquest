package de.jackbeback.pocketquest.game.loop

sealed class TurnPhase {
    object PlayerPhase : TurnPhase()
    object EnemyPhase : TurnPhase()
    object EnvironmentPhase : TurnPhase()
}
