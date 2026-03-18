package de.jackbeback.pocketquest.ecs.core

/** Set (or replace) a component on [entity]. */
inline fun <reified T : Any> World.set(entity: EntityId, component: T) =
    storeOf(T::class).set(entity, component)

/** Get a component from [entity], or null if absent. */
inline fun <reified T : Any> World.get(entity: EntityId): T? =
    storeOf(T::class).get(entity)

/** Returns true if [entity] has a component of type [T]. */
inline fun <reified T : Any> World.has(entity: EntityId): Boolean =
    storeOf(T::class).has(entity)

/** Remove a component from [entity] (no-op if absent). */
inline fun <reified T : Any> World.remove(entity: EntityId) =
    storeOf(T::class).remove(entity)

/** Query all alive entities that have component [A]. */
@JvmName("queryOne")
inline fun <reified A : Any> World.query(): Sequence<Pair<EntityId, A>> =
    storeOf(A::class).all().filter { (id, _) -> isAlive(id) }

/** Query all alive entities that have BOTH [A] and [B] components. */
@JvmName("queryTwo")
inline fun <reified A : Any, reified B : Any> World.query(): Sequence<Triple<EntityId, A, B>> =
    storeOf(A::class).all()
        .filter { (id, _) -> isAlive(id) }
        .mapNotNull { (id, a) ->
            val b = storeOf(B::class).get(id) ?: return@mapNotNull null
            Triple(id, a, b)
        }

/** Query all alive entities that have [A], [B], and [C] components. */
@JvmName("queryThree")
inline fun <reified A : Any, reified B : Any, reified C : Any> World.query3():
        Sequence<Quadruple<EntityId, A, B, C>> =
    storeOf(A::class).all()
        .filter { (id, _) -> isAlive(id) }
        .mapNotNull { (id, a) ->
            val b = storeOf(B::class).get(id) ?: return@mapNotNull null
            val c = storeOf(C::class).get(id) ?: return@mapNotNull null
            Quadruple(id, a, b, c)
        }

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
