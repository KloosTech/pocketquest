package de.jackbeback.pocketquest.ecs.core

class ComponentStore<T : Any> {
    private val data = HashMap<EntityId, T>()

    fun set(id: EntityId, c: T) { data[id] = c }
    fun get(id: EntityId): T? = data[id]
    fun remove(id: EntityId) { data.remove(id) }
    fun has(id: EntityId): Boolean = data.containsKey(id)
    fun all(): Sequence<Pair<EntityId, T>> = data.entries.asSequence().map { it.key to it.value }
    fun removeAll(ids: Collection<EntityId>) { ids.forEach { data.remove(it) } }
}
