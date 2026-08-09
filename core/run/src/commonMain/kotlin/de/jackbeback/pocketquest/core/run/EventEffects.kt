package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RunEffect
import de.jackbeback.pocketquest.core.model.RunEffectTarget
import de.jackbeback.pocketquest.core.model.forAbility
import de.jackbeback.pocketquest.core.rules.abilityModifier
import de.jackbeback.pocketquest.core.rules.d20
import de.jackbeback.pocketquest.core.rules.rollRange
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * docs/13-encounters-and-events.md: "`RunEffect` is resolved by `:core:run` directly against
 * `RunState`/`PartyMember`, the same narrow style as `applyConsumable`" — never touches the
 * resolver or the combat effect stack. [ForceCombat][RunEffect.ForceCombat] is the one case that
 * hands off elsewhere, calling the same `startEncounter` a Combat node uses.
 */
fun applyRunEffect(run: RunState, effect: RunEffect, cat: Catalog): RunState = when (effect) {
    is RunEffect.GrantCurrency -> run.copy(gold = run.gold + effect.amount)
    is RunEffect.GrantItem -> run.copy(inventory = run.inventory.copy(items = run.inventory.items + effect.item))
    is RunEffect.LoseItem -> run.copy(inventory = run.inventory.copy(items = run.inventory.items - effect.item))
    is RunEffect.DamageParty -> applyToTargets(run, effect.target) { member -> damageMember(member, effect.amount) }
    is RunEffect.HealParty -> applyToTargets(run, effect.target) { member -> healMember(member, effect.amount, cat) }
    is RunEffect.ForceCombat -> startEncounter(run, cat.encounterSpec(effect.encounter), cat)
}

private fun damageMember(member: PartyMember, amount: Int): PartyMember {
    val newHp = (member.hp - amount).coerceAtLeast(0)
    return member.copy(hp = newHp, condition = if (newHp == 0) MemberCondition.Downed else MemberCondition.Healthy)
}

private fun healMember(member: PartyMember, amount: Int, cat: Catalog): PartyMember {
    val maxHp = member.toEntity(cat).stats(cat).maxHp
    val newHp = (member.hp + amount).coerceIn(0, maxHp)
    return member.copy(hp = newHp, condition = if (newHp == 0) MemberCondition.Downed else MemberCondition.Healthy)
}

/**
 * Resolves [target] against [run.party] — [RunEffectTarget.RandomMember] is the only case that
 * consumes [run.rng]; the others are pure, deterministic selections.
 */
private fun resolveTargets(run: RunState, target: RunEffectTarget): Pair<RngState, Set<MemberId>> = when (target) {
    RunEffectTarget.WholeParty -> run.rng to run.party.map { it.memberId }.toSet()
    RunEffectTarget.RandomMember -> {
        val (advanced, index) = run.rng.rollRange(0, run.party.size - 1)
        advanced to setOf(run.party[index].memberId)
    }
    RunEffectTarget.LowestHpMember -> run.rng to setOf(run.party.minByOrNull { it.hp }!!.memberId)
}

private fun applyToTargets(run: RunState, target: RunEffectTarget, transform: (PartyMember) -> PartyMember): RunState {
    val (rng, targetIds) = resolveTargets(run, target)
    val updatedParty = run.party.map { if (it.memberId in targetIds) transform(it) else it }
    return run.copy(party = updatedParty, rng = rng)
}

/** [text] is whichever branch's flavor text applies — [outcomeText] unconditionally, or [successText]/[failureText] once a check resolves. */
data class EventChoiceResolution(val text: String, val run: RunState)

/**
 * A checkless choice ([EventChoice.check] null) applies [EventChoice.effects] unconditionally — the
 * original shape. A checked choice has the party's best-scoring member on [EventCheck.ability] roll
 * `d20 + abilityModifier(derivedScore) >= dc`, the same formula `:core:rules`' RollSave combat
 * handler uses, then applies only the matching branch's effects — either branch may be empty for a
 * choice that "only ever helps" or "only ever hurts" while still being a real roll.
 */
fun resolveEventChoice(run: RunState, choice: EventChoice, cat: Catalog): EventChoiceResolution {
    val check = choice.check
    if (check == null) {
        var updated = run
        for (effect in choice.effects) updated = applyRunEffect(updated, effect, cat)
        return EventChoiceResolution(choice.outcomeText, updated)
    }

    val scoreByMember = run.party.associateWith { it.toEntity(cat).stats(cat).abilities.forAbility(check.ability) }
    val roller = scoreByMember.maxBy { it.value }.key
    val mod = abilityModifier(scoreByMember.getValue(roller))
    val (advancedRng, rollValue) = run.rng.d20()
    val success = rollValue + mod >= check.dc

    val effects = if (success) choice.successEffects else choice.failureEffects
    val text = if (success) choice.successText else choice.failureText
    var updated = run.copy(rng = advancedRng)
    for (effect in effects) updated = applyRunEffect(updated, effect, cat)
    return EventChoiceResolution(text, updated)
}
