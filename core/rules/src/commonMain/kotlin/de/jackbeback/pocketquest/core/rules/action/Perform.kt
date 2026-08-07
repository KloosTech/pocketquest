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
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.RngMode
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.collectTriggers
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver

private fun initialStack(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): List<Effect> {
    val def = cat.actionDef(actionId)
    // Cost is the FIRST effect on the stack, not applied before the loop: an action interrupted
    // by e.g. a counterspell must still have paid, and the mana-bar animation drives off the
    // ResourcesSpent event rather than an out-of-band deduction — see docs/05-actions-and-effects.md.
    val spendCost = Effect.SpendCost(
        who = caster,
        ap = (def.cost.action as? ActionCost.Movement)?.tiles ?: 0,
        mana = def.cost.mana,
        markQuickUsed = def.cost.action == ActionCost.Quick,
    )
    return listOf(spendCost) + def.effects.flatMap { it.instantiate(ctx, cat) }
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
    val actionStarted = GameEvent.ActionStarted(caster, actionId)
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
