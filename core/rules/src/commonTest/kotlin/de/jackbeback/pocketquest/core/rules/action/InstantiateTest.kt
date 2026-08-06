package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.SlotKey
import de.jackbeback.pocketquest.core.model.SlotValue
import de.jackbeback.pocketquest.core.model.StatusId
import kotlin.test.Test
import kotlin.test.assertEquals

class InstantiateTest {

    private val caster = EntityId(1)
    private val cat = Catalog()

    @Test
    fun casterRefResolvesToTheActingEntity() {
        val template = EffectTemplate.DealDamage(Ref.Caster, 5, DamageType.Fire)
        val ctx = ActionCtx(caster, targets = listOf(EntityId(2)))
        assertEquals(listOf(Effect.DealDamage(caster, 5, DamageType.Fire)), template.instantiate(ctx, cat))
    }

    @Test
    fun eachTargetExpandsToOneEffectPerTargetSortedByEntityId() {
        val template = EffectTemplate.ApplyStatus(Ref.EachTarget, StatusId("burn"), stacks = 1, expiry = Expiry.Permanent)
        // Deliberately out-of-order targets list — instantiate must sort, not preserve list order.
        val ctx = ActionCtx(caster, targets = listOf(EntityId(30), EntityId(10), EntityId(20)))
        val result = template.instantiate(ctx, cat)

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
        assertEquals(listOf(Effect.DealDamage(EntityId(99), 3, DamageType.Poison)), template.instantiate(ctx, cat))
    }

    @Test
    fun slotRefWithNoMatchingSlotProducesNoEffects() {
        val template = EffectTemplate.DealDamage(Ref.Slot(SlotKey("missing")), 3, DamageType.Poison)
        val ctx = ActionCtx(caster, targets = emptyList())
        assertEquals(emptyList(), template.instantiate(ctx, cat))
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
            template.instantiate(ctx, cat),
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
        val result = template.instantiate(ctx, cat)

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
}
