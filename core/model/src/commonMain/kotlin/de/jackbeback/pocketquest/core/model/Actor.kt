package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Faction { Player, Enemy, Neutral }

@Serializable
sealed interface Controller {
    @Serializable data object Human : Controller
    @Serializable data class Ai(val profile: AiProfileId) : Controller
}

@Serializable
data class Actor(val faction: Faction, val controller: Controller)
