package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.set
import de.jackbeback.pocketquest.game.systems.combat.CombatSystem
import de.jackbeback.pocketquest.game.systems.combat.ConditionTickSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [ConditionTickSystem] — the EnvironmentPhase system that ticks
 * all active DoT (damage-over-time) conditions and decrements their stacks.
 *
 * Tick rules:
 *  - **Burn** (stacks N)  → emits N×2 FIRE damage, decrements stacks by 1
 *  - **Poison** (stacks N) → emits N×1 POISON damage, decrements stacks by 1
 *  - **Cold** (stacks N)  → emits N×1 FORCE damage, decrements stacks by 1
 *  - **Block** / **StrengthUp** → NOT ticked; stacks are preserved unchanged
 *
 * Because [ConditionTickSystem] emits [DamageEvent]s rather than mutating HP
 * directly, a [CombatSystem] must also be registered on the same [World] for
 * the damage to actually land.
 */
class ConditionTickSystemTest {

    /**
     * Standard setup: World + CombatSystem (handles DamageEvent) + ConditionTickSystem.
     * Returns the tick system so callers can invoke [ConditionTickSystem.update] explicitly.
     */
    private fun setup(): Pair<World, ConditionTickSystem> {
        val world = World()
        CombatSystem(world)          // registers DamageEvent handler
        val tickSystem = ConditionTickSystem()
        return world to tickSystem
    }

    /**
     * Run one full tick cycle: call update (which emits damage events), then flush the bus.
     */
    private fun World.tick(system: ConditionTickSystem) {
        system.update(this, 0L)
        events().flush()
    }

    // ── Burn ──────────────────────────────────────────────────────────────────

    @Test
    fun `burn 1 stack deals 2 fire damage`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 1)))

        world.tick(tick)

        // 1 stack × 2 = 2 damage → HP 18
        assertEquals(18, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `burn 3 stacks deals 6 fire damage`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 3)))

        world.tick(tick)

        // 3 stacks × 2 = 6 damage → HP 14
        assertEquals(14, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `burn decrements stacks by 1 per tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 3)))

        world.tick(tick)

        assertEquals(2, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Burn))
    }

    @Test
    fun `burn is removed when stacks reach 0`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 1)))

        world.tick(tick)

        val burnStacks = world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Burn)
        assertNull(burnStacks, "Burn should be removed after its last stack ticks")
    }

    @Test
    fun `burn deals damage on multiple consecutive ticks`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 2)))

        // Tick 1: 2 stacks × 2 = 4 damage → HP 96; stacks → 1
        world.tick(tick)
        assertEquals(96, world.get<HealthComponent>(entity)?.current)

        // Tick 2: 1 stack × 2 = 2 damage → HP 94; stacks removed
        world.tick(tick)
        assertEquals(94, world.get<HealthComponent>(entity)?.current)

        // Tick 3: no burn left → no damage
        world.tick(tick)
        assertEquals(94, world.get<HealthComponent>(entity)?.current)
    }

    // ── Poison ────────────────────────────────────────────────────────────────

    @Test
    fun `poison 1 stack deals 1 poison damage`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Poison to 1)))

        world.tick(tick)

        assertEquals(19, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `poison 4 stacks deals 4 poison damage`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Poison to 4)))

        world.tick(tick)

        // 4 stacks × 1 = 4 damage → HP 16
        assertEquals(16, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `poison decrements stacks by 1 per tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Poison to 2)))

        world.tick(tick)

        assertEquals(1, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Poison))
    }

    // ── Cold ──────────────────────────────────────────────────────────────────

    @Test
    fun `cold 2 stacks deals 2 force damage`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Cold to 2)))

        world.tick(tick)

        // 2 stacks × 1 = 2 damage → HP 18
        assertEquals(18, world.get<HealthComponent>(entity)?.current)
    }

    @Test
    fun `cold decrements stacks by 1 per tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Cold to 3)))

        world.tick(tick)

        assertEquals(2, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Cold))
    }

    // ── Buff conditions are NOT ticked ────────────────────────────────────────

    @Test
    fun `block stacks are preserved unchanged after a tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Block to 5)))

        world.tick(tick)

        // No damage, no stack reduction
        assertEquals(20, world.get<HealthComponent>(entity)?.current, "Block must not deal damage on tick")
        assertEquals(5, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block),
            "Block stacks must be unchanged after tick")
    }

    @Test
    fun `strengthUp stacks are preserved unchanged after a tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.StrengthUp to 2)))

        world.tick(tick)

        assertEquals(20, world.get<HealthComponent>(entity)?.current)
        assertEquals(2, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.StrengthUp))
    }

    // ── Mixed conditions ──────────────────────────────────────────────────────

    @Test
    fun `multiple conditions tick independently in the same pass`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 100, max = 100))
        world.set(
            entity, ConditionsComponent(
                active = mapOf(
                    ConditionType.Burn to 2,    // 2×2 = 4 damage
                    ConditionType.Poison to 3,  // 3×1 = 3 damage
                    ConditionType.Block to 4    // not ticked
                )
            )
        )

        world.tick(tick)

        // Total damage: 4 (burn) + 3 (poison) = 7 → HP 93
        assertEquals(93, world.get<HealthComponent>(entity)?.current)
        assertEquals(1, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Burn))
        assertEquals(2, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Poison))
        assertEquals(4, world.get<ConditionsComponent>(entity)?.active?.get(ConditionType.Block))
    }

    // ── Entity without conditions ─────────────────────────────────────────────

    @Test
    fun `entity with empty conditions map is unaffected by tick`() {
        val (world, tick) = setup()
        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 20, max = 20))
        world.set(entity, ConditionsComponent(active = emptyMap()))

        world.tick(tick)

        assertEquals(20, world.get<HealthComponent>(entity)?.current)
    }

    // ── Logging callback ──────────────────────────────────────────────────────

    @Test
    fun `onLog callback is invoked for each ticking condition`() {
        val world = World()
        CombatSystem(world)
        val logLines = mutableListOf<String>()
        val tickSystem = ConditionTickSystem(onLog = { logLines += it })

        val entity = world.createEntity()
        world.set(entity, HealthComponent(current = 50, max = 50))
        world.set(entity, ConditionsComponent(active = mapOf(ConditionType.Burn to 1, ConditionType.Poison to 1)))

        world.tick(tickSystem)

        assertEquals(2, logLines.size, "One log entry per ticking condition expected")
        assertTrue(logLines.any { it.contains("Burn") }, "Burn must be mentioned in log")
        assertTrue(logLines.any { it.contains("Poison") }, "Poison must be mentioned in log")
    }
}
