package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Quick is NOT a cheaper Main — see docs/05-actions-and-effects.md. Each is a categorical per-turn gate, not an amount. */
@Serializable
sealed interface ActionCost {
    @Serializable @SerialName("main") data object Main : ActionCost
    @Serializable @SerialName("quick") data object Quick : ActionCost
    @Serializable @SerialName("reaction") data object Reaction : ActionCost
    @Serializable @SerialName("movement") data class Movement(val tiles: Int) : ActionCost
    @Serializable @SerialName("free") data object Free : ActionCost
}

/**
 * [apCost] is the AP the entity's shared [Resources.ap] pool is actually charged — orthogonal to
 * [action]'s slot (Main/Quick/Reaction/Free still gate *whether* this can be used this turn,
 * [apCost] is just its price). Ignored for [ActionCost.Movement], which already prices itself off
 * tiles/path cost (`canPerform`/`Perform.kt`) — authoring both would double-charge the same pool.
 * Defaults to 0, matching every action authored before this field existed.
 */
@Serializable
data class Cost(val action: ActionCost, val mana: Int = 0, val charges: ItemId? = null, val hpCost: Int = 0, val apCost: Int = 0)

/**
 * The authored, Ref-templated counterpart to [Effect] — resolved to concrete
 * effects by `EffectTemplate.instantiate()` in :core:rules. Only the
 * primitives with a handler exist as templates; `requirements`/`behavior`
 * from docs/05 are omitted — no concrete need for them yet, and nothing
 * checks them.
 */
@Serializable
sealed interface EffectTemplate {
    /**
     * docs/42-status-stack-scaling.md: [perStack] is a per-stack multiplier resolved at instantiate
     * time against `ActionCtx.slots[STATUS_STACKS_SLOT]` — the ticking status's own current stack
     * count, threaded in only by `endTurn`'s `onTurnStart` tick (`Handlers.kt`). Zero everywhere else
     * (a regular action's `DealDamage` has no such slot), so [perStack] is a pure no-op outside a
     * status's own onTurnStart list — the same "meaningless outside its one real context" shape
     * [Push.onWallHit] already has. [amount] stays a flat additive base on top (0 for a pure
     * per-stack tick, non-zero for "X plus Y per stack").
     */
    @Serializable @SerialName("dealDamage")
    data class DealDamage(val target: Ref, val amount: Int, val damageType: DamageType, val tags: Set<DamageTag> = emptySet(), val perStack: Int = 0) : EffectTemplate

    /**
     * [caster] threads through to [Effect.ApplyStatus.sourceId] — found missing while authoring
     * real Taunt content (doc17-engine-gaps.md 2.3): without it, every content-authored status
     * applies with `sourceId = null`, and anything reading who granted a status (Taunt's
     * `tauntedBy()`, a doc18 ward's `StepRef.StatusSource`) silently can't find them. Optional and
     * defaulted to null — most statuses (a self-buff, `burning`'s own DoT) never needed a source at
     * all, so this doesn't force every existing/future template to specify one.
     */
    @Serializable @SerialName("applyStatus")
    data class ApplyStatus(val target: Ref, val status: StatusId, val stacks: Int = 1, val expiry: Expiry, val caster: Ref? = null) : EffectTemplate

    /**
     * docs/22-dice-roll-ui-and-ability-checks.md: [attackBonus] is no longer the whole attack
     * modifier — the resolver derives `abilityModifier(attacker's [ability] score)` and adds
     * [attackBonus] on top as an extra/magic-weapon bonus (default 0, an ordinary weapon). [ability]
     * defaults to Str (melee-typical); a Dex finesse weapon or a spell attack authors it explicitly,
     * same pattern [RollSave] already uses for its own [Ability].
     */
    @Serializable @SerialName("rollAttack")
    data class RollAttack(
        val attacker: Ref,
        val target: Ref,
        val attackBonus: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val damage: DiceSpec,
        val damageType: DamageType,
        val tags: Set<DamageTag> = emptySet(),
        val ability: Ability = Ability.Str,
    ) : EffectTemplate

    @Serializable @SerialName("rollSave")
    data class RollSave(
        val target: Ref,
        val ability: Ability,
        val dc: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val onSuccess: List<EffectTemplate> = emptyList(),
        val onFail: List<EffectTemplate> = emptyList(),
    ) : EffectTemplate

    /**
     * doc17-engine-gaps.md 3.1: [direction] is computed at instantiate() time as [target] minus
     * [awayFrom]'s position, not authored directly — "push away from me" is the actual
     * content-authoring shape (doc05's Thunderwave example), a raw vector isn't.
     * [onWallHit] (docs/29-push-on-wall-hit.md): fires when the push is stopped early by a wall or
     * another entity — instantiated against a ctx scoped to just the one pushed target (same
     * per-target scoping [RollSave.onSuccess]/[onFail] already use), so `Ref.EachTarget` inside it
     * means "the thing that hit the wall," not every target of the whole action.
     */
    @Serializable @SerialName("push")
    data class Push(val target: Ref, val awayFrom: Ref, val distance: Int, val onWallHit: List<EffectTemplate> = emptyList()) : EffectTemplate

    /** doc17-engine-gaps.md 3.1: the destination is the action's own targeted point (`ActionCtx.point`) — a Point-targeted teleport action's whole reason for existing, not a separate ref/slot. */
    @Serializable @SerialName("teleport")
    data class Teleport(val who: Ref) : EffectTemplate

    /** doc17-engine-gaps.md 3.1: position comes from `ActionCtx.point`, same reasoning as [Teleport] — a "summon" action is Point-targeted by nature. */
    @Serializable @SerialName("spawnEntity")
    data class SpawnEntity(val archetype: ArchetypeId, val faction: Faction, val controller: Controller) : EffectTemplate

    @Serializable @SerialName("destroyEntity")
    data class DestroyEntity(val target: Ref) : EffectTemplate

    /**
     * [Effect.Heal] (pass 8) never got a content-authoring template — found missing while
     * authoring a real healer archetype, the same way [ApplyStatus.caster] was. [source] is
     * optional for the same reason: a self-heal has no separate source worth naming.
     */
    @Serializable @SerialName("heal")
    data class Heal(val target: Ref, val amount: Int, val source: Ref? = null) : EffectTemplate

    /** docs/36-map-triggers.md: [Effect.ShowMessage]'s template — no [Ref], static authored text has nothing to resolve per-target. */
    @Serializable @SerialName("showMessage")
    data class ShowMessage(val text: String) : EffectTemplate

    /** docs/41-status-duration-and-ability-mods.md: [Effect.RemoveStatus]'s template — [Effect.RemoveStatus] itself existed since the resolver's earliest pass, but had no content-authoring template until now, the same "primitive without the authoring layer" gap [Heal] had above. Lets a healer/cleanse action actually be authored. */
    @Serializable @SerialName("removeStatus")
    data class RemoveStatus(val target: Ref, val status: StatusId) : EffectTemplate

    /** docs/48-gates-and-wander-ai.md: [Effect.OpenGate]'s template — no [Ref], [gate] is static authored content like [ShowMessage.text]. One-way (no `CloseGate` exists — see the doc's "decided with the user" section). */
    @Serializable @SerialName("openGate")
    data class OpenGate(val gate: GateId) : EffectTemplate

    /** docs/50-terrain-mutation.md: [Effect.SetTerrain]'s template — [at] is a literal authored position, NOT resolved from `ActionCtx.point` (unlike [SpawnEntity]/[Teleport]), so one trigger's effect list can reshape several different cells in one placement. */
    @Serializable @SerialName("setTerrain")
    data class SetTerrain(val at: GridPos, val tile: TileType) : EffectTemplate
}

/** A pure declaration — no logic. Performing it pushes SpendCost then its instantiated effects onto the resolver stack. */
@Serializable
data class ActionDef(
    val id: ActionId,
    val name: String,
    val cost: Cost,
    val targeting: Targeting,
    val effects: List<EffectTemplate>,
    /** Only meaningful when cost.action is Reaction — which GameEvent kind offers this reaction. */
    val reactionTrigger: ReactionTrigger? = null,
    /**
     * docs/24-projectile-travel-animation.md: a `kind = "projectile"` manifest id (docs/23) — the
     * sprite that flies from caster to target when this action resolves. `null` keeps today's plain
     * attacker-pulse with no travel, a real permanent fallback for actions with no art drawn yet,
     * not a migration shim.
     */
    val projectileSprite: String? = null,
    /** docs/25-action-selection-ui.md: authored flavor/tactical text, shown once this action is picked in the battle UI. Independent of the auto-generated mechanical effect text the Details view builds from [effects]. */
    val description: String = "",
)

data class PreviewResult(val state: GameState, val events: List<GameEvent>)
