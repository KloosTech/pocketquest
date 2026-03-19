package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.has
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.ecs.core.remove
import de.jackbeback.pocketquest.ecs.core.set
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [World] and its extension functions in [de.jackbeback.pocketquest.ecs.core].
 *
 * The World is the ECS in-memory database. These tests verify:
 *  - Entity lifecycle (create, destroy, flush)
 *  - Component CRUD (set, get, has, remove)
 *  - Queries across entity/component combinations
 *  - Pending-destroy semantics (marked dead before flush)
 */
class WorldTest {

    // ── Entity lifecycle ──────────────────────────────────────────────────────

    @Test
    fun `createEntity returns unique ids`() {
        val world = World()
        val a = world.createEntity()
        val b = world.createEntity()
        val c = world.createEntity()
        assertTrue(a != b && b != c && a != c, "Every entity must have a unique id")
    }

    @Test
    fun `newly created entity is alive`() {
        val world = World()
        val entity = world.createEntity()
        assertTrue(world.isAlive(entity))
    }

    @Test
    fun `destroyEntity marks entity as not alive immediately`() {
        val world = World()
        val entity = world.createEntity()
        world.destroyEntity(entity)
        assertFalse(world.isAlive(entity), "Entity marked for destroy should not be alive")
    }

    @Test
    fun `flushDestroys removes entity from allEntities`() {
        val world = World()
        val entity = world.createEntity()
        world.destroyEntity(entity)
        world.flushDestroys()
        assertFalse(entity in world.allEntities(), "Flushed entity must be removed from allEntities")
    }

    @Test
    fun `flushDestroys strips all components from destroyed entity`() {
        val world = World()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))
        world.destroyEntity(entity)
        world.flushDestroys()
        assertNull(world.get<HealthComponent>(entity), "Component must be removed after flush")
    }

    @Test
    fun `surviving entity is unaffected when a sibling is destroyed`() {
        val world = World()
        val survivor = world.createEntity()
        val doomed = world.createEntity()
        world.set(survivor, HealthComponent(current = 5, max = 5))

        world.destroyEntity(doomed)
        world.flushDestroys()

        assertTrue(world.isAlive(survivor))
        assertEquals(5, world.get<HealthComponent>(survivor)?.current)
    }

    @Test
    fun `flushDestroys is a no-op when pendingDestroy is empty`() {
        val world = World()
        val entity = world.createEntity()
        world.flushDestroys()  // nothing pending
        assertTrue(world.isAlive(entity), "Entity must remain alive after a no-op flush")
    }

    // ── Component CRUD ────────────────────────────────────────────────────────

    @Test
    fun `set then get returns the same component`() {
        val world = World()
        val entity = world.createEntity()
        val health = HealthComponent(current = 15, max = 20)
        world.set(entity, health)
        assertEquals(health, world.get<HealthComponent>(entity))
    }

    @Test
    fun `get returns null for missing component`() {
        val world = World()
        val entity = world.createEntity()
        assertNull(world.get<HealthComponent>(entity))
    }

    @Test
    fun `has returns false before set`() {
        val world = World()
        val entity = world.createEntity()
        assertFalse(world.has<HealthComponent>(entity))
    }

    @Test
    fun `has returns true after set`() {
        val world = World()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))
        assertTrue(world.has<HealthComponent>(entity))
    }

    @Test
    fun `remove clears the component`() {
        val world = World()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))
        world.remove<HealthComponent>(entity)
        assertFalse(world.has<HealthComponent>(entity))
    }

    @Test
    fun `set overwrites existing component`() {
        val world = World()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))
        world.set(entity, HealthComponent(current = 3, max = 10))
        assertEquals(3, world.get<HealthComponent>(entity)?.current)
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Test
    fun `query returns entities that have the component`() {
        val world = World()
        val e1 = world.createEntity()
        val e2 = world.createEntity()
        world.set(e1, HealthComponent(current = 10, max = 10))
        // e2 has no HealthComponent

        val result = world.query<HealthComponent>().map { it.first }.toSet()
        assertTrue(e1 in result, "e1 must appear in query results")
        assertFalse(e2 in result, "e2 must not appear in query results")
    }

    @Test
    fun `query excludes entities pending destroy`() {
        val world = World()
        val alive = world.createEntity()
        val dead = world.createEntity()
        world.set(alive, HealthComponent(current = 10, max = 10))
        world.set(dead, HealthComponent(current = 10, max = 10))

        world.destroyEntity(dead)

        val result = world.query<HealthComponent>().map { it.first }.toSet()
        assertTrue(alive in result)
        assertFalse(dead in result, "Pending-destroy entity must be excluded from queries")
    }

    @Test
    fun `two-component query returns only entities with both components`() {
        val world = World()
        val both = world.createEntity()
        val healthOnly = world.createEntity()

        world.set(both, HealthComponent(current = 10, max = 10))
        world.set(both, NameComponent(displayName = "Hero", internalId = "hero"))
        world.set(healthOnly, HealthComponent(current = 5, max = 5))

        val result = world.query<HealthComponent, NameComponent>().map { it.first }.toSet()
        assertTrue(both in result, "Entity with both components must be in result")
        assertFalse(healthOnly in result, "Entity missing NameComponent must not be in result")
    }

    @Test
    fun `allEntities returns set of all alive entity ids`() {
        val world = World()
        val a = world.createEntity()
        val b = world.createEntity()
        val all = world.allEntities()
        assertTrue(a in all && b in all)
    }
}
