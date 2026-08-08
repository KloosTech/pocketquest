package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * docs/21-ai-behavior-spec.md: an ordered list of hard-priority rules, checked top-down. The first
 * [AiTier] whose [AiCondition] holds AND whose [AiGoal] resolves to a legal action wins; a
 * condition that holds but produces nothing legal falls through to the NEXT tier, not to doing
 * nothing. Falling off the end of [tiers] entirely (including the zero-config empty list) means
 * `:core:ai`'s `chooseAction` uses its own pre-existing global scorer — this system layers on top
 * of that, it doesn't replace it.
 */
@Serializable
data class AiProfileDef(val id: AiProfileId, val name: String, val tiers: List<AiTier> = emptyList())

@Serializable
data class AiTier(val condition: AiCondition, val goal: AiGoal)

/**
 * A small fixed vocabulary, not a general boolean-expression tree — doc16 already flagged nested
 * polymorphic structures as painful in a property inspector; this is that risk again, so it stays
 * closed. Every check here queries a primitive [de.jackbeback.pocketquest.core.rules] already
 * exposes (HP%, `tauntedBy`, Chebyshev distance) — no new engine capability, just a new vocabulary
 * for referencing them from authored content.
 */
@Serializable
sealed interface AiCondition {
    @Serializable @SerialName("selfHpBelow") data class SelfHpBelow(val percent: Int) : AiCondition
    @Serializable @SerialName("anyAllyHpBelow") data class AnyAllyHpBelow(val percent: Int) : AiCondition
    @Serializable @SerialName("anyEnemyHpBelow") data class AnyEnemyHpBelow(val percent: Int) : AiCondition
    @Serializable @SerialName("isTaunted") data object IsTaunted : AiCondition
    @Serializable @SerialName("hasStatus") data class HasStatus(val status: StatusId) : AiCondition
    @Serializable @SerialName("enemyCountInRange") data class EnemyCountInRange(val range: Int, val atLeast: Int) : AiCondition

    /** An explicit catch-all — e.g. the last authored tier before falling off the end into the default scorer, made visible in content rather than implicit. */
    @Serializable @SerialName("always") data object Always : AiCondition
}

/**
 * What a matching tier does. [UseAction] is a filter+preference layered on `:core:ai`'s existing
 * `legalTargets`/`canPerform`/`preview` enumeration — it narrows candidate actions to [category]
 * (when set) and ranks legal targets by [targetPreference] instead of by `preview()` score; it does
 * not reimplement legality or targeting. [Retreat]/[Approach] are the genuinely new decision kinds —
 * pure movement choices toward/away from the nearest enemy, not `preview()`-scored ones. Without
 * [Approach], an entity that starts out of range of every action had no way to close the distance —
 * a real gap found the first time someone actually authored a melee-only profile.
 */
@Serializable
sealed interface AiGoal {
    @Serializable @SerialName("useAction") data class UseAction(val category: AiActionCategory? = null, val targetPreference: AiTargetPreference) : AiGoal
    @Serializable @SerialName("retreat") data object Retreat : AiGoal
    @Serializable @SerialName("approach") data object Approach : AiGoal
}

/** Derived from an action's effects at evaluation time — not authored on the action itself. Buff/Debuff need [StatusDef.beneficial] to disambiguate; Damage/Heal are derivable straight from the effect template kinds already present. */
@Serializable
enum class AiActionCategory { Damage, Heal, BuffAlly, DebuffEnemy }

@Serializable
enum class AiTargetPreference { LowestHpPercent, HighestHpPercent, Nearest, Farthest }
