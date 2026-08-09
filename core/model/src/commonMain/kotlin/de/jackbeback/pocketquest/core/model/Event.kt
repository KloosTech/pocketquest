package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * docs/13-encounters-and-events.md's Events section — "handcrafted text + choices... 1-4 choices,
 * each can help or hurt". Authored content, loaded via [Catalog] like [EncounterSpec]/[ItemDef];
 * resolving a [RunEffect] against a live run is `:core:run`'s job, not this module's (this module
 * has no notion of a `RunState` to apply one against).
 */
@Serializable
data class EventDef(
    val id: EventId,
    val title: String,
    val body: String,
    val choices: List<EventChoice>,
)

/**
 * A choice either resolves unconditionally ([check] null — [outcomeText]/[effects], the original
 * shape) or attempts an ability check against [EventCheck.dc]: the party's best-scoring member on
 * [EventCheck.ability] rolls `d20 + abilityModifier(score)`, same formula `:core:rules`' RollSave
 * combat handler already uses. Each branch is independently optional — a choice can be "only ever
 * helps" (empty [failureEffects]) or "only ever hurts" (empty [successEffects]) while still being a
 * real roll, not just a guaranteed outcome dressed up as one.
 */
@Serializable
data class EventChoice(
    val label: String,
    val check: EventCheck? = null,
    val outcomeText: String = "",
    val effects: List<RunEffect> = emptyList(),
    val successText: String = "",
    val successEffects: List<RunEffect> = emptyList(),
    val failureText: String = "",
    val failureEffects: List<RunEffect> = emptyList(),
)

@Serializable
data class EventCheck(val ability: Ability, val dc: Int)

/**
 * Deliberately a small sealed interface, same "type dropdown + inline fields" authoring shape as
 * `EffectTemplate`/`Modifier` in the combat layer — add a case per new event mechanic as content
 * actually needs one, don't pre-build a general scripting language. `GrantStatus` (a persistent
 * run-scoped buff/curse) is an obvious next case once an event actually needs it — not added
 * speculatively.
 */
@Serializable
sealed interface RunEffect {
    /** Negative = a cost/toll. */
    @Serializable data class GrantCurrency(val amount: Int) : RunEffect
    @Serializable data class GrantItem(val item: ItemId) : RunEffect
    @Serializable data class LoseItem(val item: ItemId) : RunEffect
    @Serializable data class DamageParty(val amount: Int, val target: RunEffectTarget) : RunEffect
    @Serializable data class HealParty(val amount: Int, val target: RunEffectTarget) : RunEffect
    /** An event escalating into a fight — hands off to the same `startEncounter` a Combat node uses. */
    @Serializable data class ForceCombat(val encounter: EncounterId) : RunEffect
}

@Serializable
enum class RunEffectTarget { WholeParty, RandomMember, LowestHpMember }
