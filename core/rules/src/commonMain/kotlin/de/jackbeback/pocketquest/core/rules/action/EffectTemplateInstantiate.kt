package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.SlotValue

private fun resolveRef(ref: Ref, ctx: ActionCtx): List<EntityId> = when (ref) {
    Ref.Caster -> listOf(ctx.caster)
    // Sorted by EntityId — map/collection iteration order must never leak into determinism.
    Ref.EachTarget -> ctx.targets.sortedBy { it.raw }
    is Ref.Slot -> listOfNotNull((ctx.slots[ref.key] as? SlotValue.EntitySlot)?.value)
}

/** Resolves every [Ref] placeholder into a concrete [Effect]. `EachTarget` expands to one effect per target. */
fun EffectTemplate.instantiate(ctx: ActionCtx, cat: Catalog): List<Effect> = when (this) {
    is EffectTemplate.DealDamage ->
        resolveRef(target, ctx).map { Effect.DealDamage(it, amount, type) }

    is EffectTemplate.ApplyStatus ->
        resolveRef(target, ctx).map { Effect.ApplyStatus(it, status, stacks, expiry) }

    is EffectTemplate.RollAttack -> {
        val attackerId = resolveRef(attacker, ctx).firstOrNull() ?: return emptyList()
        resolveRef(target, ctx).map { Effect.RollAttack(attackerId, it, attackBonus, advantage, damage, damageType) }
    }

    is EffectTemplate.RollSave ->
        resolveRef(target, ctx).map { t ->
            // Each target gets its own save, so onSuccess/onFail must be instantiated against a
            // ctx scoped to just THIS target — reusing the full multi-target ctx would make a
            // nested Ref.EachTarget inside onFail apply to every target, not just the one that failed.
            val scopedCtx = ctx.copy(targets = listOf(t))
            Effect.RollSave(
                target = t,
                ability = ability,
                dc = dc,
                advantage = advantage,
                onSuccess = onSuccess.flatMap { it.instantiate(scopedCtx, cat) },
                onFail = onFail.flatMap { it.instantiate(scopedCtx, cat) },
            )
        }
}
