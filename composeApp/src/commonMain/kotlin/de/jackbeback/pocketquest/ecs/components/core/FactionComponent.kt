package de.jackbeback.pocketquest.ecs.components.core

enum class Faction { PLAYER, ENEMY, NEUTRAL }

data class FactionComponent(val faction: Faction)
