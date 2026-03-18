package de.jackbeback.pocketquest.ecs.core

import kotlin.reflect.KClass

/**
 * The in-memory source of truth during gameplay.
 *
 * Entities are created/destroyed here; components are stored in per-type [ComponentStore]s.
 * Compose never reads the World directly — ViewModels query it into immutable UiState
 * snapshots via [snapshotBattle] after each phase.
 */
class World {
    private var nextId = 0
    private val alive = mutableSetOf<EntityId>()
    private val pendingDestroy = mutableSetOf<EntityId>()
    private val stores = HashMap<KClass<*>, ComponentStore<*>>()
    private val eventBus = EventBus()

    fun createEntity(): EntityId {
        val id = EntityId(nextId++)
        alive.add(id)
        return id
    }

    /** Mark entity for destruction — removed on next [flushDestroys]. */
    fun destroyEntity(entityId: EntityId) {
        pendingDestroy.add(entityId)
    }

    /** Returns true only if alive AND not yet flushed for destroy. */
    fun isAlive(entityId: EntityId): Boolean =
        entityId in alive && entityId !in pendingDestroy

    fun allEntities(): Set<EntityId> = alive.toSet()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> storeOf(klass: KClass<T>): ComponentStore<T> =
        stores.getOrPut(klass) { ComponentStore<T>() } as ComponentStore<T>

    fun events(): EventBus = eventBus

    /** Remove all entities that were marked via [destroyEntity], stripping their components. */
    fun flushDestroys() {
        if (pendingDestroy.isEmpty()) return
        val toDestroy = pendingDestroy.toSet()
        pendingDestroy.clear()
        toDestroy.forEach { id ->
            alive.remove(id)
            @Suppress("UNCHECKED_CAST")
            stores.values.forEach { store -> (store as ComponentStore<Any>).remove(id) }
        }
    }
}
