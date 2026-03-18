package de.jackbeback.pocketquest.ecs.components.core

enum class Direction { NORTH, SOUTH, EAST, WEST }

data class FacingComponent(val direction: Direction)
