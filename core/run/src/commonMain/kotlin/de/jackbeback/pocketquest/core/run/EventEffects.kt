package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EventCheck
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.RunEffect
import de.jackbeback.pocketquest.core.model.RunEffectTarget
import de.jackbeback.pocketquest.core.rules.d20Detailed
import de.jackbeback.pocketquest.core.rules.matches
import de.jackbeback.pocketquest.core.rules.resolveAdvantage
import de.jackbeback.pocketquest.core.rules.rollRange
import de.jackbeback.pocketquest.core.rules.stat.rollBreakdown
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

/** docs/22: the actual roll a checked [EventChoice] produced — null for a checkless choice, otherwise what the roll card renders after the "click to roll" moment. [otherD20] mirrors [de.jackbeback.pocketquest.core.model.GameEvent.AttackRolled.otherD20] — the discarded Advantage/Disadvantage die, for the dual-die display. */
data class EventCheckOutcome(val d20: Int, val otherD20: Int?, val breakdown: RollBreakdown, val dc: Int, val success: Boolean)

/** [text] is whichever branch's flavor text applies — [outcomeText] unconditionally, or [successText]/[failureText] once a check resolves. [checkOutcome] is null for a checkless choice. */
data class EventChoiceResolution(val text: String, val run: RunState, val checkOutcome: EventCheckOutcome? = null)

/**
 * docs/22-dice-roll-ui-and-ability-checks.md: what attempting [check] as [roller] would look like —
 * the DC and this member's roll breakdown — computed without touching [RunState.rng] or applying
 * anything. Lets the roll card show the DC/modifiers and let the player actually choose who
 * attempts it (previously [resolveEventChoice] silently auto-picked the party's best-scoring
 * member) before committing to the "click to roll" moment [resolveEventChoice] performs.
 */
fun previewEventCheck(run: RunState, check: EventCheck, roller: MemberId, cat: Catalog): RollBreakdown {
    val member = run.party.first { it.memberId == roller }
    return member.toEntity(cat).rollBreakdown(cat, check.ability)
}

/**
 * A checkless choice ([EventChoice.check] null) applies [EventChoice.effects] unconditionally — the
 * original shape. A checked choice rolls `d20 + breakdown.total >= check.dc` for the explicitly
 * chosen [roller] (docs/22: the player picks, this no longer auto-selects the best-scoring member),
 * consulting [de.jackbeback.pocketquest.core.model.Stats.rollGrants] for
 * [RollContext.AbilityCheck]-matched advantage/disadvantage the same way `:core:rules`' combat
 * handlers already do for attacks/saves — a gap out-of-combat checks had until now. Advantage only
 * applies when [EventCheck.skill] is set: [RollContext.AbilityCheck] has no "any skill" wildcard
 * (unlike [RollContext.AttackRoll]'s `vs = null`), so a skill-less raw-ability check can't match a
 * skill-specific grant. [roller] is ignored for a checkless choice. Either effects branch may be
 * empty for a choice that "only ever helps" or "only ever hurts" while still being a real roll.
 */
fun resolveEventChoice(run: RunState, choice: EventChoice, roller: MemberId, cat: Catalog): EventChoiceResolution {
    val check = choice.check
    if (check == null) {
        var updated = run
        for (effect in choice.effects) updated = applyRunEffect(updated, effect, cat)
        return EventChoiceResolution(choice.outcomeText, updated)
    }

    val member = run.party.first { it.memberId == roller }
    val entity = member.toEntity(cat)
    val breakdown = entity.rollBreakdown(cat, check.ability)
    val derivedAdvantage = check.skill?.let { skill ->
        entity.stats(cat).rollGrants
            .filter { it.ctx.matches(RollContext.AbilityCheck(skill)) }
            .map { it.side }
            .toSet()
    } ?: emptySet()
    val advantageMode = resolveAdvantage(derivedAdvantage)
    val (advancedRng, roll) = run.rng.d20Detailed(advantageMode)
    val success = roll.resolved + breakdown.total >= check.dc

    val effects = if (success) choice.successEffects else choice.failureEffects
    val text = if (success) choice.successText else choice.failureText
    var updated = run.copy(rng = advancedRng)
    for (effect in effects) updated = applyRunEffect(updated, effect, cat)
    val outcome = EventCheckOutcome(roll.resolved, roll.other, breakdown, check.dc, success)
    return EventChoiceResolution(text, updated, outcome)
}
