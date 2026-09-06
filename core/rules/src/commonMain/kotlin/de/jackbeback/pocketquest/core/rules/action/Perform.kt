package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.PreviewResult
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.RngMode
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.collectTriggers
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import de.jackbeback.pocketquest.core.rules.targeting.findPath
import de.jackbeback.pocketquest.core.rules.targeting.pathCost

private fun initialStack(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): List<Effect> {
    val def = cat.actionDef(actionId)
    val movement = def.cost.action as? ActionCost.Movement

    // A Path-targeted move has no authored EffectTemplate — it's synthesized here from the
    // resolved route, same as SpendCost is (docs/17-engine-gaps.md 1.2/1.3). canPerform() already
    // guarantees a non-null path before perform() ever reaches this point; preview() bypasses
    // canPerform, so an unreachable point here just degrades to "nothing moves, nothing spent"
    // rather than crashing.
    val path = if (movement != null && def.targeting.mode == TargetMode.Path) {
        val origin = state.byId[caster]?.pos
        val point = ctx.point
        if (origin != null && point != null) findPath(origin, point, state.map, state.blockingOccupancy, openGates = state.openGates) else null
    } else {
        null
    }

    // Cost is the FIRST effect on the stack, not applied before the loop: an action interrupted
    // by e.g. a counterspell must still have paid, and the mana-bar animation drives off the
    // ResourcesSpent event rather than an out-of-band deduction — see docs/05-actions-and-effects.md.
    val spendCost = Effect.SpendCost(
        who = caster,
        ap = path?.pathCost(state.map) ?: (movement?.tiles ?: def.cost.apCost),
        mana = def.cost.mana,
        markQuickUsed = def.cost.action == ActionCost.Quick,
    )
    val moveEffect = if (path != null) listOf(Effect.MoveAlong(caster, path)) else emptyList()
    return listOf(spendCost) + moveEffect + def.effects.flatMap { it.instantiate(state, ctx, cat) }
}

/**
 * `ActionStarted` used to be seeded directly into `Resolver.emitted`, which never ran it through
 * `collectTriggers` — a Counterspell-shaped reaction (`ReactionTriggerKind.ActionStarted`) could
 * never fire even though the rest of the plumbing already existed (KNOWN_ISSUES.md #6). Routed
 * through `collectTriggers` explicitly here, at depth 0, so any matching reaction is offered
 * before the action's own stack (cost + effects) is even pushed — a Counterspell interrupts the
 * instant casting begins, not after the caster has already paid or the spell has resolved.
 */
private fun buildInitial(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): Resolver {
    // docs/24-projectile-travel-animation.md: point/targets ride along on the one event that fires
    // exactly once per cast, so a travel-animation beat has a destination to fly to regardless of
    // how many per-target effects this action's own EachTarget expansion goes on to emit.
    val actionStarted = GameEvent.ActionStarted(caster, actionId, ctx.point, ctx.targets)
    val (triggered, reacted) = collectTriggers(state, listOf(actionStarted), depth = 0, cat = cat, alreadyReacted = emptySet())
    return Resolver(
        state = state,
        stack = triggered + initialStack(state, caster, actionId, ctx, cat),
        emitted = listOf(actionStarted),
        reactedTo = reacted,
    )
}

fun perform(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): StepResult {
    val def = cat.actionDef(actionId)
    val rejections = canPerform(state, caster, def, ctx, cat)
    if (rejections.isNotEmpty()) return StepResult.Rejected(Resolver(state), rejections)

    return runResolver(buildInitial(state, caster, actionId, ctx, cat), cat)
}

/**
 * Because GameState is immutable, previewing is just running the resolver
 * in Expected mode and handing back the result instead of committing it —
 * this structurally rules out preview and execution ever disagreeing. Also
 * the AI's evaluation function: enumerate legal actions, preview each,
 * score the resulting event list.
 */
fun preview(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): PreviewResult {
    val result = runResolver(buildInitial(state, caster, actionId, ctx, cat), cat, RngMode.Expected)
    return PreviewResult(result.resolver.state, result.resolver.emitted)
}
