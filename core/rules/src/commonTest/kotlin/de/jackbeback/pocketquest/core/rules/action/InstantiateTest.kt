package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.SlotKey
import de.jackbeback.pocketquest.core.model.SlotValue
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals

class InstantiateTest {

    private val caster = EntityId(1)
    private val cat = Catalog()

    /** No entity/state-derived data needed for most templates — a minimal empty board suffices. */
    private val state = GameState(entities = emptyList(), map = BattleMap(10, 10), turn = TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = TurnPhase.Main), rng = RngState(seed = 1))

    private fun entity(id: EntityId, pos: GridPos) = Entity(
        id = id, archetype = ArchetypeId("dummy"), pos = pos,
        health = Health(10), resources = null, actor = Actor(Faction.Player, Controller.Human),
    )

    @Test
    fun casterRefResolvesToTheActingEntity() {
        val template = EffectTemplate.DealDamage(Ref.Caster, 5, DamageType.Fire)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(listOf(Effect.DealDamage(caster, 5, DamageType.Fire)), template.instantiate(state, ctx, cat))
    }

    @Test
    fun eachTargetExpandsToOneEffectPerTargetSortedByEntityId() {
        val template = EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("burn"), stacks = 1, expiry = Expiry.Permanent)
        // Deliberately out-of-order targets list — instantiate must sort, not preserve list order.
        val ctx = ActionCtx(caster, targets = listOf(EntityId(30), EntityId(10), EntityId(20)))
        val result = template.instantiate(state, ctx, cat)

        assertEquals(
            listOf(
                Effect.ApplyStatus(EntityId(10), StatusId("burn"), 1, Expiry.Permanent),
                Effect.ApplyStatus(EntityId(20), StatusId("burn"), 1, Expiry.Permanent),
                Effect.ApplyStatus(EntityId(30), StatusId("burn"), 1, Expiry.Permanent),
            ),
            result,
        )
    }

    @Test
    fun slotRefResolvesToAnEntitySlotValue() {
        val key = SlotKey("markedTarget")
        val template = EffectTemplate.DealDamage(Ref.Slot(key), 3, DamageType.Poison)
        val ctx = ActionCtx(caster, targets = emptyList(), slots = mapOf(key to SlotValue.EntitySlot(EntityId(99))))
        assertEquals(listOf(Effect.DealDamage(EntityId(99), 3, DamageType.Poison)), template.instantiate(state, ctx, cat))
    }

    @Test
    fun slotRefWithNoMatchingSlotProducesNoEffects() {
        val template = EffectTemplate.DealDamage(Ref.Slot(SlotKey("missing")), 3, DamageType.Poison)
        val ctx = ActionCtx(caster, targets = emptyList())
        assertEquals(emptyList(), template.instantiate(state, ctx, cat))
    }

    @Test
    fun rollAttackResolvesBothAttackerAndTargetRefs() {
        val template = EffectTemplate.RollAttack(
            attacker = Ref.Caster,
            target = Ref.EachTarget,
            attackBonus = 4,
            damage = DiceSpec(1, 8, 0),
            damageType = DamageType.Slashing,
        )
        val ctx = ActionCtx(caster, targets = listOf(EntityId(5)))
        assertEquals(
            listOf(Effect.RollAttack(caster, EntityId(5), 4, emptySet(), DiceSpec(1, 8, 0), DamageType.Slashing)),
            template.instantiate(state, ctx, cat),
        )
    }

    @Test
    fun rollSaveRecursivelyInstantiatesOnSuccessAndOnFailBranches() {
        // The 'half damage on a successful save' example from docs/05, expressed as two
        // fully-instantiated branches rather than a runtime slot lookup.
        val template = EffectTemplate.RollSave(
            target = Ref.EachTarget,
            ability = Ability.Dex,
            dc = 14,
            onSuccess = listOf(EffectTemplate.DealDamage(Ref.EachTarget, 5, DamageType.Fire)),
            onFail = listOf(EffectTemplate.DealDamage(Ref.EachTarget, 10, DamageType.Fire)),
        )
        val ctx = ActionCtx(caster, targets = listOf(EntityId(7)))
        val result = template.instantiate(state, ctx, cat)

        assertEquals(
            listOf(
                Effect.RollSave(
                    target = EntityId(7),
                    ability = Ability.Dex,
                    dc = 14,
                    onSuccess = listOf(Effect.DealDamage(EntityId(7), 5, DamageType.Fire)),
                    onFail = listOf(Effect.DealDamage(EntityId(7), 10, DamageType.Fire)),
                ),
            ),
            result,
        )
    }

    @Test
    fun rollSaveWithMultipleTargetsScopesEachOnFailToOnlyThatTarget() {
        // Regression test: a naive instantiate() would resolve a nested Ref.EachTarget inside
        // onFail against the FULL original targets list, applying the on-fail status to every
        // target regardless of which one actually failed. Each target's RollSave must only see
        // itself when resolving its own onFail/onSuccess templates.
        val template = EffectTemplate.RollSave(
            target = Ref.EachTarget,
            ability = Ability.Dex,
            dc = 14,
            onFail = listOf(EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("restrained"), expiry = Expiry.Permanent)),
        )
        val ctx = ActionCtx(caster, targets = listOf(EntityId(20), EntityId(10)))
        val result = template.instantiate(state, ctx, cat)

        assertEquals(
            listOf(
                Effect.RollSave(
                    target = EntityId(10),
                    ability = Ability.Dex,
                    dc = 14,
                    onFail = listOf(Effect.ApplyStatus(EntityId(10), StatusId("restrained"), expiry = Expiry.Permanent)),
                ),
                Effect.RollSave(
                    target = EntityId(20),
                    ability = Ability.Dex,
                    dc = 14,
                    onFail = listOf(Effect.ApplyStatus(EntityId(20), StatusId("restrained"), expiry = Expiry.Permanent)),
                ),
            ),
            result,
        )
    }

    // --- Push / Teleport (docs/17-engine-gaps.md 3.1) ---

    @Test
    fun pushComputesDirectionAwayFromTheAwayFromRefsPosition() {
        val template = EffectTemplate.Push(target = Ref.EachTarget, awayFrom = Ref.Caster, distance = 2)
        val s = state.copy(entities = listOf(entity(caster, GridPos(5, 5)), entity(EntityId(2), GridPos(6, 5))))
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(
            listOf(Effect.Push(EntityId(2), GridPos(1, 0), 2)),
            template.instantiate(s, ctx, cat),
            "the target is one tile east of the caster, so the raw (unnormalized) delta is already a unit step east",
        )
    }

    @Test
    fun pushDeltaIsNotNormalizedAtInstantiateTimeOnlyAtTheHandler() {
        // Deliberately far apart (5 tiles east, 5 tiles south) — instantiate() must pass the raw
        // delta straight through; only Effect.Push's own handler clamps it to a unit step.
        val template = EffectTemplate.Push(target = Ref.EachTarget, awayFrom = Ref.Caster, distance = 1)
        val s = state.copy(entities = listOf(entity(caster, GridPos(0, 0)), entity(EntityId(2), GridPos(5, 5))))
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(listOf(Effect.Push(EntityId(2), GridPos(5, 5), 1)), template.instantiate(s, ctx, cat))
    }

    @Test
    fun pushWithNoPositionForTheAwayFromRefProducesNoEffects() {
        val template = EffectTemplate.Push(target = Ref.EachTarget, awayFrom = Ref.Caster, distance = 2)
        val s = state.copy(entities = listOf(Entity(caster, ArchetypeId("dummy"), pos = null, health = null, resources = null, actor = null), entity(EntityId(2), GridPos(6, 5))))
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(emptyList(), template.instantiate(s, ctx, cat))
    }

    @Test
    fun teleportResolvesWhoAgainstCtxPoint() {
        val template = EffectTemplate.Teleport(who = Ref.Caster)
        val ctx = ActionCtx(caster, targets = emptyList(), point = GridPos(9, 9))
        assertEquals(listOf(Effect.Teleport(caster, GridPos(9, 9))), template.instantiate(state, ctx, cat))
    }

    @Test
    fun teleportWithNoCtxPointProducesNoEffects() {
        val template = EffectTemplate.Teleport(who = Ref.Caster)
        val ctx = ActionCtx(caster, targets = emptyList(), point = null)
        assertEquals(emptyList(), template.instantiate(state, ctx, cat))
    }

    // --- SpawnEntity / DestroyEntity (docs/17-engine-gaps.md 3.1) ---

    @Test
    fun spawnEntityResolvesPositionAgainstCtxPoint() {
        val template = EffectTemplate.SpawnEntity(ArchetypeId("goblin"), Faction.Enemy, Controller.Human)
        val ctx = ActionCtx(caster, targets = emptyList(), point = GridPos(4, 4))
        assertEquals(
            listOf(Effect.SpawnEntity(ArchetypeId("goblin"), GridPos(4, 4), Faction.Enemy, Controller.Human)),
            template.instantiate(state, ctx, cat),
        )
    }

    @Test
    fun spawnEntityWithNoCtxPointProducesNoEffects() {
        val template = EffectTemplate.SpawnEntity(ArchetypeId("goblin"), Faction.Enemy, Controller.Human)
        val ctx = ActionCtx(caster, targets = emptyList(), point = null)
        assertEquals(emptyList(), template.instantiate(state, ctx, cat))
    }

    @Test
    fun destroyEntityResolvesTargetRef() {
        val template = EffectTemplate.DestroyEntity(target = Ref.EachTarget)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(5)))
        assertEquals(listOf(Effect.DestroyEntity(EntityId(5))), template.instantiate(state, ctx, cat))
    }

    // --- ApplyStatus.caster / Heal (found missing while authoring real Taunt/healer content) ---

    @Test
    fun applyStatusThreadsTheCasterRefIntoSourceId() {
        val template = EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("taunted"), expiry = Expiry.Permanent, caster = Ref.Caster)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(
            listOf(Effect.ApplyStatus(EntityId(2), StatusId("taunted"), 1, Expiry.Permanent, sourceId = caster)),
            template.instantiate(state, ctx, cat),
        )
    }

    @Test
    fun applyStatusWithNoCasterRefLeavesSourceIdNull() {
        val template = EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("burn"), expiry = Expiry.Permanent)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(
            listOf(Effect.ApplyStatus(EntityId(2), StatusId("burn"), 1, Expiry.Permanent, sourceId = null)),
            template.instantiate(state, ctx, cat),
        )
    }

    @Test
    fun healResolvesTargetAmountAndOptionalSource() {
        val template = EffectTemplate.Heal(Ref.EachTarget, 5, source = Ref.Caster)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(listOf(Effect.Heal(EntityId(2), 5, source = caster)), template.instantiate(state, ctx, cat))
    }

    @Test
    fun healWithNoSourceRefLeavesSourceNull() {
        val template = EffectTemplate.Heal(Ref.Caster, 5)
        val ctx = ActionCtx(caster, targets = emptyList())
        assertEquals(listOf(Effect.Heal(caster, 5, source = null)), template.instantiate(state, ctx, cat))
    }
}
