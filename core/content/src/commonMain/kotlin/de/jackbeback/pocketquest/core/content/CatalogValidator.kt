package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EffectTemplate

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

            is EffectTemplate.DealDamage, is EffectTemplate.RollAttack, is EffectTemplate.Push, is EffectTemplate.Teleport -> Unit
        }
    }
}
