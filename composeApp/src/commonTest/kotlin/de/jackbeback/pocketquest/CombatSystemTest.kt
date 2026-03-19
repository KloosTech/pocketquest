package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.DamageResistancesComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.systems.combat.CombatSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [CombatSystem] — the damage/heal pipeline at the heart of every fight.
 *
 * The system registers event-bus handlers in its `init` block and reacts to
 * [DamageEvent] and [HealEvent]. The three-stage damage pipeline is:
 *   1. **Resistance** — multiply raw damage by a per-type multiplier (0 = immune, 0.5 = 50% resist, 2 = vulnerable)
 *   2. **Block** — subtract current Block-stack count from damage, then consume 1 stack
 *   3. **HP** — clamp the result to [0, max] and store it back
 *
 * Each test creates a fresh [World] + [CombatSystem] pair so handlers don't bleed between tests.
 */
class CombatSystemTest {

    /** Convenience: create a world with CombatSystem already registered. */
    private fun setupWorld(): World {
        val world = World()
        CombatSystem(world)   // registers DamageEvent / HealEvent handlers
        return world
    }

    /** Apply a single event and flush so handlers run. */
    private fun World.applyDamage(
        source: de.jackbeback.pocketquest.ecs.core.EntityId,
        target: de.jackbeback.pocketquest.ecs.core.EntityId,
        amount: Int,
        type: DamageType = DamageType.BLUDGEONING
    ) {
        events().emit(DamageEvent(source = source, target = target, amount = amount, damageType = type))
        events().flush()
    }

    private fun World.applyHeal(
        source: de.jackbeback.pocketquest.ecs.core.EntityId,
        target: de.jackbeback.pocketquest.ecs.core.EntityId,
        amount: Int
    ) {
        events().emit(HealEvent(source = source, target = target, amount = amount))
        events().flush()
    }

    // ── Plain damage — no resistance, no block ────────────────────────────────

    @Test
    fun `plain damage reduces HP by the full amount`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))

        world.applyDamage(entity, entity, 5)

        assertEquals(15, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `damage cannot reduce HP below zero`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 3, max = 20))

        world.applyDamage(entity, entity, 100)

        assertEquals(0, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `zero damage leaves HP unchanged`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 15, max = 20))

        world.applyDamage(entity, entity, 0)

        assertEquals(15, world.get<HealthComponent>(entity)?.current)
    }

    // ── Damage resistance ──────────────────────────────────────────────────────

    @Test
    fun `50 percent fire resistance halves fire damage`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, DamageResistancesComponent(multipliers = mapOf(DamageType.FIRE to 0.5f)))

        world.applyDamage(entity, entity, 10, DamageType.FIRE)

        assertEquals(15, world.get<HealthComponent>(entity)?.current)  // 10 * 0.5 = 5 damage
    }

    @Test
    fun `full immunity zero multiplier deals no damage`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, DamageResistancesComponent(multipliers = mapOf(DamageType.FIRE to 0.0f)))

        world.applyDamage(entity, entity, 10, DamageType.FIRE)

        assertEquals(20, world.get<HealthComponent>(entity)?.current)  // 0 damage taken
    }

    @Test
    fun `damage vulnerability doubles damage taken`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, DamageResistancesComponent(multipliers = mapOf(DamageType.ICE to 2.0f)))

        world.applyDamage(entity, entity, 5, DamageType.ICE)

        assertEquals(10, world.get<HealthComponent>(entity)?.current)  // 5 * 2 = 10 damage
    }

    @Test
    fun `resistance does not apply to unresisted damage type`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        // Has FIRE resistance but takes BLUDGEONING damage
        world.set(entity, DamageResistancesComponent(multipliers = mapOf(DamageType.FIRE to 0.0f)))

        world.applyDamage(entity, entity, 8, DamageType.BLUDGEONING)

        assertEquals(12, world.get<HealthComponent>(entity)?.current)  // full 8 damage
    }

    // ── Block absorption ───────────────────────────────────────────────────────

    @Test
    fun `block absorbs damage equal to its stack count`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 3)))

        world.applyDamage(entity, entity, 8)

        // damage reduced by 3 block stacks → 5 actual damage
        assertEquals(15, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `block stack count decrements by 1 after absorbing a hit`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 3)))

        world.applyDamage(entity, entity, 8)

        val blockStacks = world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block)
        assertEquals(2, blockStacks, "Block stacks should decrease from 3 to 2 after one hit")
    }

    @Test
    fun `block is fully removed when it reaches zero stacks`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 1)))

        world.applyDamage(entity, entity, 5)

        val blockStacks = world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block)
        assertNull(blockStacks, "Block should be removed from conditions when stacks reach 0")
    }

    @Test
    fun `block fully absorbs hit when damage is less than stack count`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 10)))

        world.applyDamage(entity, entity, 4)

        // block absorbs all 4 → HP unchanged, stacks reduced to 9
        assertEquals(20, world.get<HealthComponent>(entity)?.current, "HP should be unchanged when block fully absorbs")
        assertEquals(9, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block))
    }

    @Test
    fun `damage pipeline applies resistance before block`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        // 50% fire resistance, 3 block stacks
        world.set(entity, DamageResistancesComponent(multipliers = mapOf(DamageType.FIRE to 0.5f)))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 3)))

        // Raw 10 → after 50% resist = 5 → after 3 block = 2 actual damage
        world.applyDamage(entity, entity, 10, DamageType.FIRE)

        assertEquals(18, world.get<HealthComponent>(entity)?.current)
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    @Test
    fun `heal restores the specified amount of HP`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 20))

        world.applyHeal(entity, entity, 5)

        assertEquals(15, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `heal cannot exceed max HP`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 18, max = 20))

        world.applyHeal(entity, entity, 10)

        assertEquals(20, world.get<HealthComponent>(entity)?.current, "HP must be clamped to max")
    }

    @Test
    fun `heal on full HP has no effect`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))

        world.applyHeal(entity, entity, 5)

        assertEquals(20, world.get<HealthComponent>(entity)?.current)
    }

    // ── Dead / missing entity handling ────────────────────────────────────────

    @Test
    fun `damage event on destroyed entity is ignored`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 10, max = 10))
        world.destroyEntity(entity)

        // Should not throw; the handler checks isAlive
        world.applyDamage(entity, entity, 5)
    }

    @Test
    fun `heal event on destroyed entity is ignored`() {
        val world = setupWorld()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 5, max = 10))
        world.destroyEntity(entity)

        world.applyHeal(entity, entity, 5)
    }

    @Test
    fun `damage event for entity with no HealthComponent is ignored`() {
        val world = setupWorld()
        val entity = world.createEntity()
        // No HealthComponent — should not throw

        world.applyDamage(entity, entity, 10)
    }
}
