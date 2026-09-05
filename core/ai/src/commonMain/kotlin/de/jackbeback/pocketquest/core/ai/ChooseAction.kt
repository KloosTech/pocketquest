package de.jackbeback.pocketquest.core.ai

import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.AiActionCategory
import de.jackbeback.pocketquest.core.model.AiCondition
import de.jackbeback.pocketquest.core.model.AiGoal
import de.jackbeback.pocketquest.core.model.AiTargetPreference
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo
import de.jackbeback.pocketquest.core.rules.action.allActions
import de.jackbeback.pocketquest.core.rules.action.canPerform
import de.jackbeback.pocketquest.core.rules.action.preview
import de.jackbeback.pocketquest.core.rules.action.tauntedBy
import de.jackbeback.pocketquest.core.rules.stat.stats
import de.jackbeback.pocketquest.core.rules.targeting.affectedBy
import de.jackbeback.pocketquest.core.rules.targeting.findPath
import de.jackbeback.pocketquest.core.rules.targeting.legalTargets
import de.jackbeback.pocketquest.core.rules.targeting.pathCost
import de.jackbeback.pocketquest.core.rules.targeting.rangeInTiles
import de.jackbeback.pocketquest.core.rules.targeting.reachableTiles

/** What the AI decided to do, and why — `score` is exposed for tests/debugging, not used by the caller. Only meaningful for a [defaultScoredChoice] pick; a tier-driven pick carries 0. */
data class AiDecision(val actionId: ActionId, val ctx: ActionCtx, val score: Int)

/**
 * docs/21-ai-behavior-spec.md: walks the archetype's [de.jackbeback.pocketquest.core.model.AiProfileDef]
 * tiers top-down; the first tier whose condition holds AND whose goal resolves to a legal decision
 * wins. A condition holding but resolving to nothing legal falls through to the NEXT tier, not to
 * "do nothing" — [resolveGoal] returning null just continues the loop. Falling off the end
 * (including the zero-config empty-tiers default) uses [defaultScoredChoice], doc05's original
 * "enumerate legal actions, run each in Expected mode, score the resulting event list" — kept
 * verbatim as the fallback, not replaced.
 */
fun chooseAction(state: GameState, entityId: EntityId, cat: Catalog): AiDecision? {
    val entity = state.byId[entityId] ?: return null
    // Fog of war: an entity standing on a tile the party has never revealed does nothing this turn —
    // same "no legal decision" outcome the caller already treats as "pass the turn," not a new code
    // path. `revealedTiles` is only ever non-empty on a map with fogOfWar on (updateRevealedTiles'
    // own no-op rule), so this never fires on a map with fog off without needing a separate check.
    if (entity.pos != null && entity.pos !in state.revealedTiles && state.map.fogOfWar) return null
    val archetype = cat.archetype(entity.archetype)
    val profile = cat.aiProfileOrDefault(archetype.aiProfile)
    for (tier in profile.tiers) {
        if (!holds(tier.condition, state, entityId, cat)) continue
        resolveGoal(tier.goal, state, entityId, cat)?.let { return it }
    }
    return defaultScoredChoice(state, entityId, cat)
}

/**
 * doc05's original `chooseAction`, unchanged behavior — enumerates every action the entity's
 * archetype knows plus anything a level feature granted it, every legal target for each, filters
 * through `canPerform`, scores each candidate with [preview] in `Expected` mode, returns the best.
 * Returns null if nothing is legal — the caller passes the turn.
 *
 * Deliberately out of scope: `TargetMode.Point`/`Direction`/`Path` (movement, ground-targeted AoE)
 * are not enumerated — nothing in the demo catalog uses them, and picking a movement destination is
 * a pathfinding/positioning problem, not a same-shape extension of this one. [AiGoal.Retreat] is
 * the tiered system's own answer to the movement half of that gap.
 */
private fun defaultScoredChoice(state: GameState, entityId: EntityId, cat: Catalog): AiDecision? {
    val entity = state.byId[entityId] ?: return null
    val faction = entity.actor?.faction

    var best: AiDecision? = null
    for (actionId in entity.allActions(cat)) {
        val def = cat.actionDef(actionId)
        for (ctx in candidateContexts(state, entityId, def, cat)) {
            if (canPerform(state, entityId, def, ctx, cat).isNotEmpty()) continue
            val result = preview(state, entityId, actionId, ctx, cat)
            val score = score(result.events, faction, state)
            // A negative score means this candidate nets out hurting the AI's own side (e.g. a
            // TargetFilter left at "Any" let it swing on an ally) — never the best available option,
            // even when nothing else is legal. Passing the turn beats actively harming an ally.
            if (score >= 0 && (best == null || score > best.score)) {
                best = AiDecision(actionId, ctx, score)
            }
        }
    }
    return best
}

private fun candidateContexts(state: GameState, caster: EntityId, def: ActionDef, cat: Catalog): List<ActionCtx> {
    val casterEntity = state.byId[caster] ?: return emptyList()
    val casterPos = casterEntity.pos ?: return emptyList()
    return when (def.targeting.mode) {
        TargetMode.SelfOnly -> listOf(ActionCtx(caster, targets = listOf(caster), point = casterPos))
        TargetMode.SingleEntity -> narrowedByTaunt(state, casterEntity, legalTargets(state, caster, def, cat), cat).map { point ->
            ActionCtx(caster, targets = affectedBy(state, def, caster, point), point = point)
        }
        TargetMode.Point, TargetMode.Direction, TargetMode.Path -> emptyList()
    }
}

/**
 * doc10/doc18: Taunt "lives in :core:ai's target selection" — restricts candidates to whichever
 * taunter(s) are among them, when any are. Scoped naturally to enemy-facing actions only: an
 * ally-heal's [legal] set is never going to contain the (enemy-of-the-caster) taunter's position to
 * begin with, so this only ever narrows something that was already enemy-targeting. If the
 * taunter(s) aren't legal targets for THIS action at all — out of range, blocked LoS, wrong weapon
 * — [legal] is returned unchanged rather than narrowing to nothing: taunt binds the choice among
 * reachable options, it doesn't invent a target the action mechanically can't reach.
 */
private fun narrowedByTaunt(state: GameState, caster: Entity, legal: Set<GridPos>, cat: Catalog): Set<GridPos> {
    val tauntedBy = caster.tauntedBy(cat)
    if (tauntedBy.isEmpty()) return legal
    val onlyTaunters = legal.filterTo(mutableSetOf()) { pos -> state.occupancy[pos] in tauntedBy }
    return onlyTaunters.ifEmpty { legal }
}

/**
 * A damage-maximizing heuristic, nothing more: enemy damage/deaths are good,
 * self/ally damage/deaths are bad, everything else (status application,
 * resource spend) is unscored — there is no way to tell a buff from a debuff
 * from the event alone without inspecting `StatusDef.modifiers`, and that is
 * more sophistication than a first pass needs.
 */
private fun score(events: List<GameEvent>, aiFaction: Faction?, state: GameState): Int {
    var total = 0
    for (event in events) {
        when (event) {
            is GameEvent.DamageTaken -> {
                val hostile = state.byId[event.target]?.actor?.faction != aiFaction
                total += if (hostile) event.amount else -event.amount
            }
            is GameEvent.Died -> {
                val hostile = state.byId[event.target]?.actor?.faction != aiFaction
                total += if (hostile) 1000 else -1000
            }
            else -> Unit
        }
    }
    return total
}

// --- docs/21-ai-behavior-spec.md: tiered condition/goal evaluation ---

private fun hpPercent(entity: Entity, cat: Catalog): Int? {
    val hp = entity.health?.current ?: return null
    val max = entity.stats(cat).maxHp
    return if (max <= 0) 0 else (hp * 100) / max
}

/** "Ally"/"enemy" match `defaultScoredChoice`'s own `score()` notion of hostility: any different faction counts as an enemy, same faction (excluding self) counts as an ally. */
private fun holds(condition: AiCondition, state: GameState, entityId: EntityId, cat: Catalog): Boolean {
    val self = state.byId[entityId] ?: return false
    val faction = self.actor?.faction
    return when (condition) {
        AiCondition.Always -> true
        is AiCondition.SelfHpBelow -> (hpPercent(self, cat) ?: 100) < condition.percent
        is AiCondition.AnyAllyHpBelow -> state.entities.any {
            it.id != entityId && it.actor?.faction == faction && (hpPercent(it, cat) ?: 100) < condition.percent
        }
        is AiCondition.AnyEnemyHpBelow -> state.entities.any {
            it.actor?.faction != faction && (hpPercent(it, cat) ?: 100) < condition.percent
        }
        AiCondition.IsTaunted -> self.tauntedBy(cat).isNotEmpty()
        is AiCondition.HasStatus -> self.statuses.any { it.def == condition.status }
        is AiCondition.EnemyCountInRange -> {
            val pos = self.pos ?: return false
            val count = state.entities.count { other ->
                other.actor?.faction != faction && other.pos?.let { it.chebyshevDistanceTo(pos) <= condition.range } == true
            }
            count >= condition.atLeast
        }
    }
}

private fun resolveGoal(goal: AiGoal, state: GameState, entityId: EntityId, cat: Catalog): AiDecision? = when (goal) {
    is AiGoal.UseAction -> resolveUseAction(goal, state, entityId, cat)
    AiGoal.Retreat -> resolveMoveRelativeToNearestEnemy(state, entityId, cat, toward = false)
    AiGoal.Approach -> resolveMoveRelativeToNearestEnemy(state, entityId, cat, toward = true)
}

/**
 * A filter+preference layered on the same [candidateContexts] enumeration [defaultScoredChoice]
 * uses — narrows to [AiGoal.UseAction.category] (when set), then picks the candidate
 * [AiGoal.UseAction.targetPreference] ranks first, instead of the highest `preview()` score.
 */
private fun resolveUseAction(goal: AiGoal.UseAction, state: GameState, entityId: EntityId, cat: Catalog): AiDecision? {
    val entity = state.byId[entityId] ?: return null
    val faction = entity.actor?.faction

    val candidates = mutableListOf<Pair<ActionId, ActionCtx>>()
    for (actionId in entity.allActions(cat)) {
        val def = cat.actionDef(actionId)
        if (goal.category != null && categoryOf(def, faction, cat) != goal.category) continue
        for (ctx in candidateContexts(state, entityId, def, cat)) {
            if (canPerform(state, entityId, def, ctx, cat).isNotEmpty()) continue
            candidates += actionId to ctx
        }
    }
    val (actionId, ctx) = candidates.maxByOrNull { (_, ctx) -> preferenceScore(goal.targetPreference, ctx, state, cat) } ?: return null
    return AiDecision(actionId, ctx, score = 0)
}

/** Damage/Heal are derivable straight from effect kinds; Buff/Debuff need [de.jackbeback.pocketquest.core.model.StatusDef.beneficial] plus the action's own target faction compared against the caster's, to tell "buffing my own side" from "debuffing the other side." Content that's beneficial-but-targets-the-enemy (or the reverse) doesn't cleanly fit either bucket and returns null rather than guessing. */
private fun categoryOf(def: ActionDef, casterFaction: Faction?, cat: Catalog): AiActionCategory? {
    if (def.effects.any { it is EffectTemplate.DealDamage || it is EffectTemplate.RollAttack }) return AiActionCategory.Damage
    if (def.effects.any { it is EffectTemplate.Heal }) return AiActionCategory.Heal
    val applyStatus = def.effects.filterIsInstance<EffectTemplate.ApplyStatus>().firstOrNull() ?: return null
    val beneficial = cat.statuses[applyStatus.status]?.beneficial ?: true
    val targetFaction = def.targeting.filter.faction
    val targetsOwnSide = targetFaction == null || targetFaction == casterFaction
    return when {
        beneficial && targetsOwnSide -> AiActionCategory.BuffAlly
        !beneficial && !targetsOwnSide -> AiActionCategory.DebuffEnemy
        else -> null
    }
}

private fun preferenceScore(preference: AiTargetPreference, ctx: ActionCtx, state: GameState, cat: Catalog): Int {
    val casterPos = state.byId[ctx.caster]?.pos
    val targetPos = ctx.point
    val targetEntity = ctx.targets.firstOrNull()?.let { state.byId[it] }
    return when (preference) {
        AiTargetPreference.LowestHpPercent -> targetEntity?.let { -(hpPercent(it, cat) ?: 100) } ?: Int.MIN_VALUE
        AiTargetPreference.HighestHpPercent -> targetEntity?.let { hpPercent(it, cat) ?: 0 } ?: Int.MIN_VALUE
        AiTargetPreference.Nearest -> if (casterPos != null && targetPos != null) -casterPos.chebyshevDistanceTo(targetPos) else Int.MIN_VALUE
        AiTargetPreference.Farthest -> if (casterPos != null && targetPos != null) casterPos.chebyshevDistanceTo(targetPos) else Int.MIN_VALUE
    }
}

/**
 * The genuinely new decision kinds — not `preview()`-scored picks. [toward]`=false` (Retreat) moves
 * to whichever reachable tile is FARTHEST from the nearest enemy — pure straight-line ranking is
 * fine here, "farther" doesn't need to detour around anything. [toward]`=true` (Approach) instead
 * follows an actual pathfound route toward the enemy (see [approachDestination]) rather than ranking
 * reachable tiles by straight-line distance — a wall between the two can make a dead-end tile look
 * "closest" by Chebyshev distance while being no closer at all along any walkable route, which
 * pinned the AI against the wall and made it alternate one tile down/one tile up forever (found
 * live). Both use the entity's own Path-mode move action exactly as a human's Move works
 * (`ActionCtx.point` = destination, `canPerform`/`perform` resolve the actual route). Returns null
 * if there's no enemy to react to, no move action, no route exists at all, or the destination is
 * where it already is (a no-op move isn't a decision — falls through to the next tier instead).
 */
private fun resolveMoveRelativeToNearestEnemy(state: GameState, entityId: EntityId, cat: Catalog, toward: Boolean): AiDecision? {
    val entity = state.byId[entityId] ?: return null
    val pos = entity.pos ?: return null
    val faction = entity.actor?.faction

    val nearestEnemy = state.entities
        .filter { it.actor?.faction != faction }
        .mapNotNull { it.pos }
        .minByOrNull { it.chebyshevDistanceTo(pos) } ?: return null

    val moveActionId = entity.allActions(cat).firstOrNull { cat.actionDef(it).targeting.mode == TargetMode.Path } ?: return null
    // Budget must mirror `legalTargets`' own Path-mode formula exactly (`minOf(maxRange, ap)`) — not
    // reinvent it from `speedTiles`. A move action's authored `range` is itself a ceiling (same as any
    // other action), so an entity with plenty of speed/AP left can still be capped short by a small
    // range; computing budget from `speedTiles` instead let the AI aim at a destination `canPerform`
    // would always reject, returning null every turn no matter how much closer it should be able to
    // get (found live: a goblin whose move action's range was smaller than its speed never moved at
    // all, since every candidate destination past that range was rejected).
    val moveDef = cat.actionDef(moveActionId)
    val budget = minOf(rangeInTiles(moveDef.targeting.range), entity.resources?.ap ?: 0)
    val destination = if (toward) {
        approachDestination(pos, nearestEnemy, budget, state)
    } else {
        val reachable = reachableTiles(pos, budget, state.map, state.blockingOccupancy)
        (reachable + pos).maxByOrNull { it.chebyshevDistanceTo(nearestEnemy) }
    } ?: return null
    if (destination == pos) return null

    val ctx = ActionCtx(entityId, targets = emptyList(), point = destination)
    if (canPerform(state, entityId, moveDef, ctx, cat).isNotEmpty()) return null
    return AiDecision(moveActionId, ctx, score = 0)
}

private val ADJACENT_OFFSETS = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0, -1 to -1, -1 to 1, 1 to -1, 1 to 1)

/**
 * Finds the cheapest actual route (via [findPath], which already respects walls/`WallEdge`/
 * occupancy) from [pos] to any tile genuinely adjacent to [target] — [target]'s own tile is always
 * occupied by the enemy itself, never a legal destination — then returns the farthest point along
 * that route [budget] can afford this turn. "Adjacent" requires `canCross(candidate, target)`, not
 * just Chebyshev distance 1: a tile can sit right next to the enemy by coordinates alone while a
 * wall still blocks the actual step between them, which is exactly the tile the AI started this
 * calculation pinned against — treating it as "already adjacent" made [findPath] return the trivial
 * zero-cost `from == to` route and the AI kept re-selecting whichever wall-adjacent tile it already
 * occupied instead of the real detour. Null if no genuinely adjacent tile has any route at all
 * (fully walled off).
 */
private fun approachDestination(pos: GridPos, target: GridPos, budget: Int, state: GameState): GridPos? {
    val route = ADJACENT_OFFSETS
        .map { (dc, dr) -> GridPos(target.col + dc, target.row + dr) }
        .filter { state.map.inBounds(it) && state.map.canCross(it, target) }
        .mapNotNull { findPath(pos, it, state.map, state.blockingOccupancy) }
        .minByOrNull { it.pathCost(state.map) } ?: return null

    var spent = 0
    var reached: GridPos? = null
    for (step in route) {
        val stepCost = state.map.moveCost(step)
        if (spent + stepCost > budget) break
        spent += stepCost
        reached = step
    }
    return reached
}
