package de.jackbeback.pocketquest.core.rules.stat

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.core.model.RollTerm
import de.jackbeback.pocketquest.core.model.forAbility
import de.jackbeback.pocketquest.core.rules.abilityModifier

/**
 * docs/22-dice-roll-ui-and-ability-checks.md's "cheapest honest version": rather than fully
 * unwinding [stats]' fold to trace every individual item/status source, this diffs the archetype's
 * raw base ability score against the fully-derived one and reports the difference as a single
 * "Items & Effects" catch-all term. [extra]/[extraLabel] is for a flat bonus authored on top of the
 * ability modifier (e.g. [de.jackbeback.pocketquest.core.model.Effect.RollAttack.attackBonus], a
 * magic-weapon bonus) — omitted entirely when zero, same as the catch-all term when it's zero (an
 * unmodified ability score), so an ordinary roll's card doesn't show a padded-out row of zeroes.
 *
 * The three terms always sum to exactly the same total the roll itself used (`abilityModifier`
 * of the fully-derived score, plus [extra]) — this is what keeps the card honest rather than just
 * decorative.
 */
fun Entity.rollBreakdown(cat: Catalog, ability: Ability, extra: Int = 0, extraLabel: String = "Weapon"): RollBreakdown {
    val baseScore = cat.archetype(archetype).abilities.forAbility(ability)
    val derivedScore = stats(cat).abilities.forAbility(ability)
    val baseMod = abilityModifier(baseScore)
    val itemsAndEffects = abilityModifier(derivedScore) - baseMod

    val terms = mutableListOf(RollTerm(ability.name, baseMod))
    if (itemsAndEffects != 0) terms += RollTerm("Items & Effects", itemsAndEffects)
    if (extra != 0) terms += RollTerm(extraLabel, extra)
    return RollBreakdown(terms)
}
