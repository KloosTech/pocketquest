package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.components.combat.ConditionAppliedEvent
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.systems.combat.ConditionApplySystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [ConditionApplySystem] — the event handler that applies conditions
 * (Burn, Poison, Block, …) to entities.
 *
 * The system listens for [ConditionAppliedEvent] on the bus and merges the new
 * stacks into the target's [ConditionsComponent], creating the component if absent.
 * Multiple events of the same type accumulate (stacks are additive).
 */
class ConditionApplySystemTest {

    private fun setup(): World {
        val world = World()
        ConditionApplySystem(world)   // registers ConditionAppliedEvent handler
        return world
    }

    private fun World.applyCondition(
        target: de.jackbeback.pocketquest.ecs.core.EntityId,
        type: ConditionType,
        stacks: Int
    ) {
        events().emit(ConditionAppliedEvent(target = target, type = type, stacks = stacks))
        events().flush()
    }

    // ── Initial application ───────────────────────────────────────────────────

    @Test
    fun `applying a condition to entity without ConditionsComponent creates the component`() {
        val world = setup()
        val entity = world.createEntity()
        // No ConditionsComponent set

        world.applyCondition(entity, ConditionType.Burn, 2)

        val stacks = world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Burn)
        assertEquals(2, stacks)
    }

    @Test
    fun `applying block adds block stacks to entity`() {
        val world = setup()
        val entity = world.createEntity()

        world.applyCondition(entity, ConditionType.Block, 3)

        assertEquals(3, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block))
    }

    @Test
    fun `applying poison with 1 stack works`() {
        val world = setup()
        val entity = world.createEntity()

        world.applyCondition(entity, ConditionType.Poison, 1)

        assertEquals(1, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Poison))
    }

    // ── Stack accumulation ────────────────────────────────────────────────────

    @Test
    fun `applying same condition twice accumulates stacks`() {
        val world = setup()
        val entity = world.createEntity()

        world.applyCondition(entity, ConditionType.Burn, 2)
        world.applyCondition(entity, ConditionType.Burn, 3)

        assertEquals(5, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Burn))
    }

    @Test
    fun `applying different conditions coexist on the same entity`() {
        val world = setup()
        val entity = world.createEntity()

        world.applyCondition(entity, ConditionType.Burn, 2)
        world.applyCondition(entity, ConditionType.Poison, 1)
        world.applyCondition(entity, ConditionType.Block, 3)

        val conditions = world.get<ConditionsComponent>(entity)?.active
        assertEquals(2, conditions?.get(ConditionType.Burn))
        assertEquals(1, conditions?.get(ConditionType.Poison))
        assertEquals(3, conditions?.get(ConditionType.Block))
    }

    @Test
    fun `adding stacks to entity that already has a ConditionsComponent merges correctly`() {
        val world = setup()
        val entity = world.createEntity()
        // Pre-existing conditions
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Cold to 2)))

        // Add burn via event
        world.applyCondition(entity, ConditionType.Burn, 1)

        val conditions = world.get<ConditionsComponent>(entity)?.active
        assertEquals(2, conditions?.get(ConditionType.Cold), "Cold stacks must be preserved")
        assertEquals(1, conditions?.get(ConditionType.Burn), "Burn stacks must be added")
    }

    @Test
    fun `incrementing existing stacks with event accumulates on top of pre-existing`() {
        val world = setup()
        val entity = world.createEntity()
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Poison to 3)))

        world.applyCondition(entity, ConditionType.Poison, 2)

        assertEquals(5, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Poison))
    }

    // ── Dead entity handling ──────────────────────────────────────────────────

    @Test
    fun `condition event on destroyed entity is ignored`() {
        val world = setup()
        val entity = world.createEntity()
        world.destroyEntity(entity)

        // Should not throw
        world.applyCondition(entity, ConditionType.Burn, 2)

        // Component should not have been created since entity is dead
        assertNull(world.get<ConditionsComponent>(entity))
    }

}
