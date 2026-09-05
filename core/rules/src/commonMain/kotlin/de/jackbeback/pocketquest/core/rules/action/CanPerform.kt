package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo
import de.jackbeback.pocketquest.core.rules.targeting.affectedBy
import de.jackbeback.pocketquest.core.rules.targeting.findPath
import de.jackbeback.pocketquest.core.rules.targeting.hasLineOfSight
import de.jackbeback.pocketquest.core.rules.targeting.pathCost
import de.jackbeback.pocketquest.core.rules.targeting.rangeInTiles

/**
 * The resource/turn-state checks from [canPerform] that need no [ActionCtx] — no target has to be
 * picked yet to know whether the caster is downed, whether it's even their turn, or whether they
 * have enough mana/AP for anything but a Path-targeted move (that one's true AP cost depends on
 * the actual resolved route to a destination that doesn't exist yet — see [canPerform]'s own
 * comment). Split out for the combat UI to gray out an unaffordable action in the grid, before the
 * player has picked a target at all (docs: "gray out actions... when not enough resources").
 */
fun canAffordAction(state: GameState, caster: EntityId, def: ActionDef, cat: Catalog): List<Rejection> {
    val casterEntity = state.byId[caster] ?: return listOf(Rejection.NoLegalTarget)
    val rejections = mutableListOf<Rejection>()

    // Downed (docs/17-engine-gaps.md 1.5) is an absolute gate — unlike NotYourTurn, it applies
    // even to a Free action or a reaction, so it's checked before everything else, unconditionally.
    if (casterEntity.health?.current == 0) rejections += Rejection.Downed

    if (def.cost.action != ActionCost.Free) {
        val activeId = state.turn.order.getOrNull(state.turn.activeIndex)
        if (activeId != caster) rejections += Rejection.NotYourTurn
    }

    val resources = casterEntity.resources
    if (def.cost.action == ActionCost.Quick && resources?.quickUsed == true) {
        rejections += Rejection.QuickAlreadyUsed
    }

    val mana = resources?.mana ?: 0
    if (mana < def.cost.mana) rejections += Rejection.NotEnoughMana(def.cost.mana, mana)

    // A Path-targeted move prices itself off the actual resolved route (docs/17-engine-gaps.md
    // 1.3), not the ActionDef's static `tiles` — that field only still means something for a
    // flat movement-budget cost with no destination to path to (SelfOnly Movement, e.g. "dash").
    val movement = def.cost.action as? ActionCost.Movement
    // Non-Movement actions charge `Cost.apCost` flat against the same AP pool Movement already
    // draws from (Movement prices itself below instead — see Cost.apCost's own doc comment on why
    // authoring both would double-charge).
    if (movement == null) {
        val ap = resources?.ap ?: 0
        if (ap < def.cost.apCost) rejections += Rejection.NotEnoughAp(def.cost.apCost, ap)
    } else if (def.targeting.mode != TargetMode.Path) {
        val ap = resources?.ap ?: 0
        if (ap < movement.tiles) rejections += Rejection.NotEnoughAp(movement.tiles, ap)
    }

    return rejections
}

/**
 * One function, three consumers: UI button greying + tooltip, AI option
 * filtering, engine execution gating — see docs/05-actions-and-effects.md.
 * Only the checks buildable with what's implemented so far: no
 * ActionAlreadyUsed (no per-turn "main used" gate yet), no BlockedByStatus
 * (no action-blocking status data), no MissingEquipment (no equip()
 * validation yet).
 */
fun canPerform(state: GameState, caster: EntityId, def: ActionDef, ctx: ActionCtx, cat: Catalog): List<Rejection> {
    val casterEntity = state.byId[caster] ?: return listOf(Rejection.NoLegalTarget)
    val rejections = canAffordAction(state, caster, def, cat).toMutableList()

    val movement = def.cost.action as? ActionCost.Movement
    if (movement != null && def.targeting.mode == TargetMode.Path) {
        val origin = casterEntity.pos
        val point = ctx.point
        if (origin != null && point != null) {
            val path = findPath(origin, point, state.map, state.blockingOccupancy)
            if (path == null) {
                rejections += Rejection.Blocked(point)
            } else {
                val ap = casterEntity.resources?.ap ?: 0
                val cost = path.pathCost(state.map)
                if (ap < cost) rejections += Rejection.NotEnoughAp(cost, ap)
            }
        }
    }

    if (def.targeting.mode != TargetMode.SelfOnly) {
        val origin = casterEntity.pos
        val point = ctx.point
        if (origin == null || point == null) {
            rejections += Rejection.NoLegalTarget
        } else {
            val distance = origin.chebyshevDistanceTo(point)
            val maxRange = rangeInTiles(def.targeting.range)
            when {
                distance > maxRange -> rejections += Rejection.OutOfRange(distance, maxRange)
                def.targeting.requiresLoS && !hasLineOfSight(origin, point, state.map) -> rejections += Rejection.NoLineOfSight
                def.targeting.mode == TargetMode.SingleEntity && affectedBy(state, def, caster, point).isEmpty() ->
                    rejections += Rejection.NoLegalTarget
            }
        }
    }

    return rejections
}
