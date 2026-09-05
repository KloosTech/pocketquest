package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.SlotKey
import de.jackbeback.pocketquest.core.model.SlotValue

/**
 * docs/42-status-stack-scaling.md: the well-known [SlotKey] `endTurn`'s `onTurnStart` tick
 * (`Handlers.kt`) populates with the ticking status's own current stack count, as a
 * [SlotValue.IntSlot] — the only producer, and [EffectTemplate.DealDamage.perStack] the only
 * consumer. Absent (and so treated as 0 stacks) in every other `ActionCtx` a regular action ever
 * builds, which is exactly why `perStack` is a safe no-op outside a status's own onTurnStart list.
 */
val STATUS_STACKS_SLOT = SlotKey("statusStacks")

private fun resolveRef(ref: Ref, ctx: ActionCtx): List<EntityId> = when (ref) {
    Ref.Caster -> listOf(ctx.caster)
    // Sorted by EntityId — map/collection iteration order must never leak into determinism.
    Ref.EachTarget -> ctx.targets.sortedBy { it.raw }
    is Ref.Slot -> listOfNotNull((ctx.slots[ref.key] as? SlotValue.EntitySlot)?.value)
}

private fun stackCount(ctx: ActionCtx): Int = (ctx.slots[STATUS_STACKS_SLOT] as? SlotValue.IntSlot)?.value ?: 0

/**
 * Resolves every [Ref] placeholder into a concrete [Effect]. `EachTarget` expands to one effect per
 * target. Takes [state] — [EffectTemplate.Push] is the first template that needs board geometry
 * (positions) rather than just entity ids/slots, to compute "away from" as a direction.
 */
fun EffectTemplate.instantiate(state: GameState, ctx: ActionCtx, cat: Catalog): List<Effect> = when (this) {
    is EffectTemplate.DealDamage ->
        resolveRef(target, ctx).map { Effect.DealDamage(it, amount + perStack * stackCount(ctx), damageType, tags = tags) }

    is EffectTemplate.ApplyStatus -> {
        val sourceId = caster?.let { resolveRef(it, ctx).firstOrNull() }
        resolveRef(target, ctx).map { Effect.ApplyStatus(it, status, stacks, expiry, sourceId = sourceId) }
    }

    is EffectTemplate.RollAttack -> {
        val attackerId = resolveRef(attacker, ctx).firstOrNull() ?: return emptyList()
        resolveRef(target, ctx).map { Effect.RollAttack(attackerId, it, attackBonus, advantage, damage, damageType, tags, ability) }
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
                source = ctx.caster,
                advantage = advantage,
                onSuccess = onSuccess.flatMap { it.instantiate(state, scopedCtx, cat) },
                onFail = onFail.flatMap { it.instantiate(state, scopedCtx, cat) },
            )
        }

    is EffectTemplate.Push -> {
        val awayFromPos = resolveRef(awayFrom, ctx).firstOrNull()?.let { state.byId[it]?.pos }
        if (awayFromPos == null) {
            emptyList()
        } else {
            // The raw (unnormalized) delta is fine to pass straight through — Effect.Push's own
            // handler clamps each component to -1..1 before using it, so there's no need to
            // duplicate that normalization here.
            resolveRef(target, ctx).mapNotNull { targetId ->
                val targetPos = state.byId[targetId]?.pos ?: return@mapNotNull null
                // Same per-target ctx scoping RollSave's onSuccess/onFail already use — see
                // Push.onWallHit's own doc comment.
                val scopedCtx = ctx.copy(targets = listOf(targetId))
                Effect.Push(
                    targetId,
                    GridPos(targetPos.col - awayFromPos.col, targetPos.row - awayFromPos.row),
                    distance,
                    onWallHit = onWallHit.flatMap { it.instantiate(state, scopedCtx, cat) },
                )
            }
        }
    }

    is EffectTemplate.Teleport ->
        ctx.point?.let { destination -> resolveRef(who, ctx).map { Effect.Teleport(it, destination) } } ?: emptyList()

    is EffectTemplate.SpawnEntity ->
        ctx.point?.let { pos -> listOf(Effect.SpawnEntity(archetype, pos, faction, controller)) } ?: emptyList()

    is EffectTemplate.DestroyEntity ->
        resolveRef(target, ctx).map { Effect.DestroyEntity(it) }

    is EffectTemplate.Heal -> {
        val sourceId = source?.let { resolveRef(it, ctx).firstOrNull() }
        resolveRef(target, ctx).map { Effect.Heal(it, amount, source = sourceId) }
    }

    is EffectTemplate.ShowMessage -> listOf(Effect.ShowMessage(text))

    is EffectTemplate.RemoveStatus ->
        resolveRef(target, ctx).map { Effect.RemoveStatus(it, status) }
}
