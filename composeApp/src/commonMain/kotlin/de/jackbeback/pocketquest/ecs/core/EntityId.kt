package de.jackbeback.pocketquest.ecs.core

@JvmInline
value class EntityId(val id: Int) {
    companion object {
        val INVALID = EntityId(-1)
    }
}
