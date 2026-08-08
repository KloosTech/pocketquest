package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.AiCondition
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EffectTemplate

/** The zero-config default every [de.jackbeback.pocketquest.core.model.Archetype.aiProfile] starts at — always valid even absent from `catalog.aiProfiles`, matching `Catalog.aiProfileOrDefault`'s own leniency. Any OTHER explicitly-authored id must resolve for real. */
private val DEFAULT_AI_PROFILE = AiProfileId("standard")

class CatalogValidationException(val problems: List<String>) : Exception(problems.joinToString("\n"))

/**
 * Referential integrity only — every id one part of the catalog names
 * (an archetype's action list, an effect template's status, a cost's item
 * charges, a target filter's required status, a feature's granted actions)
 * must resolve inside the same catalog. Everything else (stat names,
 * damage types, ability scores) is already enforced by the type system at
 * parse time and needs no separate check here.
 */
object CatalogValidator {

    fun validate(catalog: Catalog) {
        val problems = mutableListOf<String>()

        for (archetype in catalog.archetypes.values) {
            for (actionId in archetype.actions) {
                if (actionId !in catalog.actions) {
                    problems += "Archetype '${archetype.id.raw}' references unknown action '${actionId.raw}'"
                }
            }
            if (archetype.aiProfile != DEFAULT_AI_PROFILE && archetype.aiProfile !in catalog.aiProfiles) {
                problems += "Archetype '${archetype.id.raw}' references unknown AI profile '${archetype.aiProfile.raw}'"
            }
        }

        for (profile in catalog.aiProfiles.values) {
            for (tier in profile.tiers) {
                val condition = tier.condition
                if (condition is AiCondition.HasStatus && condition.status !in catalog.statuses) {
                    problems += "AI profile '${profile.id.raw}' has a tier that checks for unknown status '${condition.status.raw}'"
                }
            }
        }

        for (action in catalog.actions.values) {
            action.cost.charges?.let { itemId ->
                if (itemId !in catalog.items) {
                    problems += "Action '${action.id.raw}' costs unknown item charges '${itemId.raw}'"
                }
            }
            action.targeting.filter.hasStatus?.let { statusId ->
                if (statusId !in catalog.statuses) {
                    problems += "Action '${action.id.raw}' filters on unknown status '${statusId.raw}'"
                }
            }
            action.effects.forEach { checkEffectTemplate(it, "action '${action.id.raw}'", catalog, problems) }
        }

        for (status in catalog.statuses.values) {
            status.onTurnStart.forEach {
                checkEffectTemplate(it, "status '${status.id.raw}'.onTurnStart", catalog, problems)
            }
        }

        for (feature in catalog.features.values) {
            for (actionId in feature.grantsActions) {
                if (actionId !in catalog.actions) {
                    problems += "Feature '${feature.id.raw}' grants unknown action '${actionId.raw}'"
                }
            }
        }

        // docs/16-art-direction.md: "a zone with fewer tiles than needed is a content validation
        // error, caught at load time" — the encounter names WHAT spawns and HOW MANY per role, the
        // map's own spawn zones say WHERE; if the zone can't fit what the encounter asks for,
        // startEncounter would have nowhere to put them.
        for (encounter in catalog.encounters.values) {
            val map = catalog.maps[encounter.mapId]
            if (map == null) {
                problems += "Encounter '${encounter.id.raw}' references unknown map '${encounter.mapId.raw}'"
                continue
            }
            for (spawn in encounter.enemies) {
                if (spawn.archetype !in catalog.archetypes) {
                    problems += "Encounter '${encounter.id.raw}' references unknown archetype '${spawn.archetype.raw}'"
                }
            }
            val neededByRole = encounter.enemies.groupBy { it.role }.mapValues { (_, spawns) -> spawns.sumOf { it.count } }
            for ((role, needed) in neededByRole) {
                val available = map.spawns.filter { it.role == role }.sumOf { it.tiles.size }
                if (available < needed) {
                    problems += "Encounter '${encounter.id.raw}' needs $needed $role spawn tile(s) on map '${map.id.raw}' but only $available are available"
                }
            }
        }

        if (problems.isNotEmpty()) throw CatalogValidationException(problems)
    }

    private fun checkEffectTemplate(template: EffectTemplate, owner: String, catalog: Catalog, problems: MutableList<String>) {
        when (template) {
            is EffectTemplate.ApplyStatus ->
                if (template.status !in catalog.statuses) {
                    problems += "$owner references unknown status '${template.status.raw}'"
                }

            is EffectTemplate.RollSave -> {
                template.onSuccess.forEach { checkEffectTemplate(it, "$owner.onSuccess", catalog, problems) }
                template.onFail.forEach { checkEffectTemplate(it, "$owner.onFail", catalog, problems) }
            }

            is EffectTemplate.SpawnEntity ->
                if (template.archetype !in catalog.archetypes) {
                    problems += "$owner references unknown archetype '${template.archetype.raw}'"
                }

            is EffectTemplate.DealDamage, is EffectTemplate.RollAttack, is EffectTemplate.Push, is EffectTemplate.Teleport,
            is EffectTemplate.DestroyEntity, is EffectTemplate.Heal,
            -> Unit
        }
    }
}
