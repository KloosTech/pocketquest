package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.AbsorbPool
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DamageStep
import de.jackbeback.pocketquest.core.model.DamageTag
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.HealStep
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.StepCondition
import de.jackbeback.pocketquest.core.model.StepRef
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo

/** [statusSourceId]/[statusDefId] are only non-null when this step came from an ActiveStatus — resolving [StepRef.StatusSource] or [de.jackbeback.pocketquest.core.model.GameEvent.DamageRedirected.by] against an archetype/feature/item-sourced step has no meaningful referent. */
internal data class SourcedDamageStep(val step: DamageStep, val statusSourceId: EntityId?, val statusDefId: StatusId?)
internal data class SourcedHealStep(val step: HealStep, val statusSourceId: EntityId?, val statusDefId: StatusId?)

/** docs/18-damage-pipeline.md: "Collection order mirrors stats() exactly" — archetype innate, features, equipment by Slot order, statuses sorted by StatusId then appliedAtVersion. */
internal fun collectDamageSteps(entity: Entity, cat: Catalog): List<SourcedDamageStep> {
    val result = mutableListOf<SourcedDamageStep>()
    cat.archetype(entity.archetype).innateDamageSteps.forEach { result += SourcedDamageStep(it, null, null) }
    for (featureId in entity.features) {
        cat.featureDef(featureId).damageSteps.forEach { result += SourcedDamageStep(it, null, null) }
    }
    for (slot in Slot.entries) {
        val item = entity.equipment.slots[slot] ?: continue
        cat.itemDef(item.def).damageSteps.forEach { result += SourcedDamageStep(it, null, null) }
    }
    for (status in entity.statuses.sortedWith(compareBy({ it.def.raw }, { it.appliedAtVersion }))) {
        val def = cat.statusDef(status.def)
        def.damageSteps.forEach { result += SourcedDamageStep(it, status.sourceId, status.def) }
    }
    return result
}

internal fun collectHealSteps(entity: Entity, cat: Catalog): List<SourcedHealStep> {
    val result = mutableListOf<SourcedHealStep>()
    cat.archetype(entity.archetype).innateHealSteps.forEach { result += SourcedHealStep(it, null, null) }
    for (featureId in entity.features) {
        cat.featureDef(featureId).healSteps.forEach { result += SourcedHealStep(it, null, null) }
    }
    for (slot in Slot.entries) {
        val item = entity.equipment.slots[slot] ?: continue
        cat.itemDef(item.def).healSteps.forEach { result += SourcedHealStep(it, null, null) }
    }
    for (status in entity.statuses.sortedWith(compareBy({ it.def.raw }, { it.appliedAtVersion }))) {
        val def = cat.statusDef(status.def)
        def.healSteps.forEach { result += SourcedHealStep(it, status.sourceId, status.def) }
    }
    return result
}

internal fun resolveStepRef(ref: StepRef, statusSourceId: EntityId?, attackerId: EntityId?): EntityId? = when (ref) {
    StepRef.StatusSource -> statusSourceId
    StepRef.Attacker -> attackerId
    is StepRef.Fixed -> ref.id
}

/**
 * [refId]/[refCenterPos] are null for steps with no referenced entity (Prevent, Reflect) — only
 * the tag/maxPerRound checks apply then. `maxPerRound` is intentionally never checked here — see
 * [StepCondition]'s own doc comment.
 */
internal fun matchesStepCondition(condition: StepCondition, state: GameState, refId: EntityId?, refCenterPos: GridPos?, tags: Set<DamageTag>): Boolean {
    if (condition.requiresTags.isNotEmpty() && !tags.containsAll(condition.requiresTags)) return false
    if (tags.any { it in condition.excludesTags }) return false
    if (refId != null) {
        val ref = state.byId[refId] ?: return false
        if (condition.refMustBeHealthy && (ref.health?.current ?: 0) <= 0) return false
        condition.refWithinTiles?.let { maxDist ->
            val refPos = ref.pos ?: return false
            val center = refCenterPos ?: return false
            if (refPos.chebyshevDistanceTo(center) > maxDist) return false
        }
    }
    return true
}

/** Consumes [AbsorbPool.TargetTemp] (the only pool that exists) — see [AbsorbPool]'s doc comment for why. */
internal fun AbsorbPool.consume(available: Int, damage: Int): Pair<Int, Int> = when (this) {
    AbsorbPool.TargetTemp -> {
        val consumed = minOf(available, damage)
        (available - consumed) to (damage - consumed)
    }
}
