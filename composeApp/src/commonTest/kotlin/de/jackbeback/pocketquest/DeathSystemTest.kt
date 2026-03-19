package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.components.combat.DeathEvent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.systems.combat.DeathSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [DeathSystem] — the system that detects dead entities (HP ≤ 0),
 * emits [DeathEvent]s, and schedules them for removal via [World.destroyEntity].
 *
 * Note: [DeathSystem] calls [World.destroyEntity] (pending) but does NOT call
 * [World.flushDestroys]. Tests that check for removal must flush explicitly.
 */
class DeathSystemTest {

    private fun setup(): Pair<World, DeathSystem> {
        val world = World()
        val system = DeathSystem()
        return world to system
    }

    private fun setupWithLog(onLog: (String) -> Unit): Pair<World, DeathSystem> {
        val world = World()
        val system = DeathSystem(onLog = onLog)
        return world to system
    }

    // ── Entity with HP ≤ 0 is marked for destroy ──────────────────────────────

    @Test
    fun `entity with HP exactly 0 is scheduled for destroy`() {
        val (world, system) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 0, max = 10))

        system.update(world, 0L)

        assertFalse(world.isAlive(entity), "Entity at 0 HP must be marked dead immediately")
    }

    @Test
    fun `entity with negative HP (clamped to 0) is scheduled for destroy`() {
        // HP is clamped to 0 by CombatSystem; but test DeathSystem directly with HP = 0
        val (world, system) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 0, max = 20))

        system.update(world, 0L)

        assertFalse(world.isAlive(entity))
    }

    @Test
    fun `entity with HP above 0 is NOT marked for destroy`() {
        val (world, system) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 1, max = 10))

        system.update(world, 0L)

        assertTrue(world.isAlive(entity), "Entity at 1 HP must remain alive")
    }

    @Test
    fun `entity at full HP is NOT marked for destroy`() {
        val (world, system) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 25, max = 25))

        system.update(world, 0L)

        assertTrue(world.isAlive(entity))
    }

    // ── Multiple entities in the same update ──────────────────────────────────

    @Test
    fun `only the dead entity is removed when both alive and dead entities are present`() {
        val (world, system) = setup()
        val alive = world.createEntity()
        val dead = world.createEntity()
        world.set(alive, HealthComponent(current = 10, max = 10))
        world.set(dead, HealthComponent(current = 0, max = 10))

        system.update(world, 0L)

        assertTrue(world.isAlive(alive), "Alive entity must not be affected")
        assertFalse(world.isAlive(dead), "Dead entity must be marked for destroy")
    }

    @Test
    fun `multiple dead entities are all scheduled for destroy in one update`() {
        val (world, system) = setup()
        val dead1 = world.createEntity()
        val dead2 = world.createEntity()
        world.set(dead1, HealthComponent(current = 0, max = 10))
        world.set(dead2, HealthComponent(current = 0, max = 10))

        system.update(world, 0L)

        assertFalse(world.isAlive(dead1))
        assertFalse(world.isAlive(dead2))
    }

    // ── DeathEvent is emitted ─────────────────────────────────────────────────

    @Test
    fun `DeathEvent is emitted for each dead entity`() {
        val world = World()
        val system = DeathSystem()

        val dead1 = world.createEntity()
        val dead2 = world.createEntity()
        world.set(dead1, HealthComponent(current = 0, max = 10))
        world.set(dead2, HealthComponent(current = 0, max = 10))

        val deathEvents = mutableListOf<DeathEvent>()
        world.events().on<DeathEvent> { deathEvents += it }

        system.update(world, 0L)
        world.events().flush()

        assertEquals(2, deathEvents.size)
        assertTrue(deathEvents.any { it.entity == dead1 })
        assertTrue(deathEvents.any { it.entity == dead2 })
    }

    @Test
    fun `no DeathEvent is emitted when all entities are alive`() {
        val world = World()
        val system = DeathSystem()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))

        val deathEvents = mutableListOf<DeathEvent>()
        world.events().on<DeathEvent> { deathEvents += it }

        system.update(world, 0L)
        world.events().flush()

        assertEquals(0, deathEvents.size)
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    @Test
    fun `onLog is called with entity display name when entity dies`() {
        val logLines = mutableListOf<String>()
        val (world, system) = setupWithLog { logLines += it }

        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 0, max = 10))
        world.set(entity, NameComponent(displayName = "Grunt", internalId = "grunt"))

        system.update(world, 0L)

        assertEquals(1, logLines.size)
        assertTrue(logLines[0].contains("Grunt"), "Log must mention the entity's display name")
    }

    @Test
    fun `onLog uses fallback name when NameComponent is absent`() {
        val logLines = mutableListOf<String>()
        val (world, system) = setupWithLog { logLines += it }

        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 0, max = 10))
        // No NameComponent

        system.update(world, 0L)

        assertEquals(1, logLines.size)
        assertTrue(logLines[0].isNotBlank(), "Log must still produce output without a name")
    }

    // ── flushDestroys removes entity ──────────────────────────────────────────

    @Test
    fun `entity is fully removed from world after flushDestroys`() {
        val (world, system) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 0, max = 10))

        system.update(world, 0L)
        world.flushDestroys()

        assertFalse(entity in world.allEntities(), "Entity must be absent from allEntities after flush")
    }
}
