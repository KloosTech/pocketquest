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

fun perform(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): StepResult {
    val def = cat.actionDef(actionId)
    val rejections = canPerform(state, caster, def, ctx, cat)
    if (rejections.isNotEmpty()) return StepResult.Rejected(Resolver(state), rejections)

    val initial = Resolver(state, stack = initialStack(state, caster, actionId, ctx, cat), emitted = listOf(GameEvent.ActionStarted(caster, actionId)))
    return runResolver(initial, cat)
}

/**
 * Because GameState is immutable, previewing is just running the resolver
 * in Expected mode and handing back the result instead of committing it —
 * this structurally rules out preview and execution ever disagreeing. Also
 * the AI's evaluation function: enumerate legal actions, preview each,
 * score the resulting event list.
 */
fun preview(state: GameState, caster: EntityId, actionId: ActionId, ctx: ActionCtx, cat: Catalog): PreviewResult {
    val result = runResolver(Resolver(state, stack = initialStack(state, caster, actionId, ctx, cat)), cat, RngMode.Expected)
    return PreviewResult(result.resolver.state, result.resolver.emitted)
}
