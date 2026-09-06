package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Only the 4 primitives docs/09-test-plan.md names for this pass
 * (DealDamage, MoveAlong, SpendCost, ApplyStatus) plus Ask, which the
 * resolver loop itself needs. The other ~10 primitives from
 * docs/05-actions-and-effects.md arrive alongside their handlers in later
 * passes.
 */
@Serializable
sealed interface Effect {
    /** Never reaches a handler — intercepted by run() itself, see :core:rules. */
    @Serializable @SerialName("ask")
    data class Ask(val request: DecisionRequest) : Effect

    /**
     * docs/18-damage-pipeline.md: [target]/[amount]/[damageType] are the RAW request — the actual
     * 8-step pipeline (retarget, prevent, convert, scale, reduce, absorb, apply, after) runs
     * inside the handler, entirely synchronously, with hops tracked as a local variable rather
     * than a field here — nothing about an in-progress retarget chain needs to survive a process
     * death mid-chain, since the whole chain resolves within one handler call. [fromReflect]
     * exists purely so a Reflect step's spawned counter-damage can't itself trigger another
     * Reflect — internal bookkeeping, never set by content authoring (EffectTemplate has no
     * matching field).
     */
    @Serializable @SerialName("dealDamage")
    data class DealDamage(
        val target: EntityId,
        val amount: Int,
        val damageType: DamageType,
        val source: EntityId? = null,
        val tags: Set<DamageTag> = emptySet(),
        val fromReflect: Boolean = false,
    ) : Effect

    /**
     * Self-continuing: the handler re-pushes with index+1 rather than looping — see docs/04-resolver.md.
     * [onWallHit] (docs/29-push-on-wall-hit.md) fires when a step is blocked (wall or occupied
     * tile) instead of just fizzling silently — empty by default, so ordinary movement (walking,
     * the Move action) is unaffected; only [Effect.Push] ever populates it.
     */
    @Serializable @SerialName("moveAlong")
    data class MoveAlong(val who: EntityId, val path: List<GridPos>, val index: Int = 0, val onWallHit: List<Effect> = emptyList()) : Effect

    /**
     * doc17-engine-gaps.md 3.1 / doc05's effect vocabulary. Deliberately does NOT reimplement
     * partial-move-then-stop-at-the-first-blocked-tile logic — it computes the [distance]-tile path
     * in [direction] and spawns [MoveAlong] with it, reusing that primitive's already-correct
     * blocked-tile fizzle-without-continuation behavior wholesale (doc05's own warning against
     * cutting primitives too coarsely: Push composes with MoveAlong rather than duplicating it).
     * [direction] is normalized to a single-tile step (each component clamped to -1..1) before use,
     * so passing something other than a true unit vector can't silently multiply the push distance.
     * [onWallHit] (docs/29-push-on-wall-hit.md) rides along onto the [MoveAlong] this handler
     * spawns — that's what actually detects the blocked tile and fires it.
     */
    @Serializable @SerialName("push")
    data class Push(val target: EntityId, val direction: GridPos, val distance: Int, val onWallHit: List<Effect> = emptyList()) : Effect

    /**
     * doc17-engine-gaps.md 3.1 / doc05. Instant, unlike [MoveAlong] — one handler call, no
     * self-continuation, no intermediate tiles — so it gets its own [GameEvent.Teleported] rather
     * than reusing [GameEvent.MoveStepped]: a teleport should animate as a blink, not a walk, and
     * (per real-world TTRPG precedent, not an oversight) does not provoke the kind of reaction
     * [GameEvent.MoveStepped]'s "left my reach" geometry check exists for — discontinuous movement
     * was never in reach to begin with.
     */
    @Serializable @SerialName("teleport")
    data class Teleport(val who: EntityId, val to: GridPos) : Effect

    /**
     * doc17-engine-gaps.md 3.1 / doc05. Full HP/AP/mana on arrival, derived through `stats(cat)`
     * off a preliminary entity rather than read straight from `Archetype.baseMaxHp` etc — a
     * shortcut there would skip the archetype's own innate modifiers. Joins `turn.order` at the
     * END of the current round (decided with the user, not guessed — docs give no guidance):
     * reinforcements don't cut into a round already in progress, they act from next round on.
     */
    @Serializable @SerialName("spawnEntity")
    data class SpawnEntity(val archetype: ArchetypeId, val pos: GridPos, val faction: Faction, val controller: Controller) : Effect

    /**
     * doc17-engine-gaps.md 3.1 / doc05. Removes [target] from both `entities` and `turn.order`,
     * fixing up `activeIndex` so it keeps pointing at the same logical "next to act" (see the
     * handler for the index-shift cases) — genuinely the primitive's hard part, not the removal
     * itself. Also breaks the destroyed entity's own concentration, if any (mirrors
     * `breakConcentration`'s existing damage-triggered call sites: a dead caster can't keep
     * concentrating). Deliberately does NOT auto-advance the turn if [target] was itself active —
     * that would entangle this with EndTurn's own boundary logic, which nothing has asked for.
     */
    @Serializable @SerialName("destroyEntity")
    data class DestroyEntity(val target: EntityId) : Effect

    /**
     * Deliberately not built from docs/05's Cost/ActionCost — that needs
     * ActionDef, which doesn't exist yet. Amounts are spelled out directly
     * so this primitive stands alone until actions arrive.
     */
    @Serializable @SerialName("spendCost")
    data class SpendCost(
        val who: EntityId,
        val ap: Int = 0,
        val mana: Int = 0,
        val markQuickUsed: Boolean = false,
        val markReactionUsed: Boolean = false,
    ) : Effect

    @Serializable @SerialName("applyStatus")
    data class ApplyStatus(
        val target: EntityId,
        val status: StatusId,
        val stacks: Int = 1,
        val expiry: Expiry,
        val sourceId: EntityId? = null,
        val linkId: LinkId? = null,
    ) : Effect

    /**
     * d20 + `abilityModifier(attacker's [ability] score)` + [attackBonus] (an extra/magic-weapon
     * bonus on top, default 0) vs target's AC — see [ActionDef.EffectTemplate.RollAttack]'s doc
     * comment (docs/22). On hit, rolls [damage] itself and spawns DealDamage — dice never roll
     * outside a handler.
     */
    @Serializable @SerialName("rollAttack")
    data class RollAttack(
        val attacker: EntityId,
        val target: EntityId,
        val attackBonus: Int,
        val advantage: Set<AdvSide> = emptySet(),
        val damage: DiceSpec,
        val damageType: DamageType,
        val tags: Set<DamageTag> = emptySet(),
        val ability: Ability = Ability.Str,
    ) : Effect

    /** d20 + ability modifier vs dc. Spawns whichever branch wins — see docs/05's Slot example; this is the simpler direct-branch shape. */
    @Serializable @SerialName("rollSave")
    data class RollSave(
        val target: EntityId,
        val ability: Ability,
        val dc: Int,
        /** Whoever forced this save (the caster of the action that spawned it) — null for a save with no clear instigator (e.g. a status's own onTurnStart tick). Purely presentational (docs: roll-card "who vs whom" portraits), never read by resolution logic itself. */
        val source: EntityId? = null,
        val advantage: Set<AdvSide> = emptySet(),
        val onSuccess: List<Effect> = emptyList(),
        val onFail: List<Effect> = emptyList(),
    ) : Effect

    /** Consults the reactor's Answerer before pushing an Ask — see docs/04-resolver.md's collectTriggers. */
    @Serializable @SerialName("offerReaction")
    data class OfferReaction(val trigger: GameEvent, val who: EntityId, val actionId: ActionId) : Effect

    /** The continuation pushed alongside Ask when a human must decide; reads the answer and either spawns the reaction's effects or does nothing. */
    @Serializable @SerialName("resolveReaction")
    data class ResolveReaction(val decisionId: DecisionId, val trigger: GameEvent, val who: EntityId, val actionId: ActionId) : Effect

    /** Doc04's 7-step turn boundary, done atomically: no player decision happens mid-transition, so nothing needs a separate stack entry. */
    @Serializable @SerialName("endTurn")
    data class EndTurn(val who: EntityId) : Effect

    /** Ends [caster]'s previous concentration (if any) before starting this one — "one LinkId per entity at a time". */
    @Serializable @SerialName("startConcentration")
    data class StartConcentration(val caster: EntityId, val linkId: LinkId) : Effect

    /** Auto-spawned by DealDamage when its target is concentrating — never authored directly. */
    @Serializable @SerialName("concentrationCheck")
    data class ConcentrationCheck(val who: EntityId, val dc: Int) : Effect

    /** DealDamage's inverse — clamps at derived maxHp rather than 0. */
    @Serializable @SerialName("heal")
    data class Heal(val target: EntityId, val amount: Int, val source: EntityId? = null) : Effect

    /** No-op (not a Fizzled precondition failure) if the status isn't present — mirrors ApplyStatus's KeepStrongest drop case. */
    @Serializable @SerialName("removeStatus")
    data class RemoveStatus(val target: EntityId, val status: StatusId) : Effect

    /** Pure authoring convenience: unpacks into its effects with no state change or event of its own. */
    @Serializable @SerialName("composite")
    data class Composite(val effects: List<Effect>) : Effect

    /**
     * Mana is a per-encounter pool (docs/10-game-loop.md), not a per-turn one — `endTurn` no
     * longer touches it. This is the only thing that refills it, pushed by whoever decides an
     * encounter is over (`:app` today; `:core:run`'s `finishEncounter` once that module exists —
     * see KNOWN_ISSUES.md 1.1). Refill everyone at once via `Composite(entities.map { RefillMana(it.id) })`.
     */
    @Serializable @SerialName("refillMana")
    data class RefillMana(val who: EntityId) : Effect

    /**
     * docs/36-map-triggers.md: no state change of its own — exists purely to drive
     * [GameEvent.MessageShown], which `:ui`'s Director turns into a blocking modal Beat. First
     * consumer is trigger authoring (tutorial/story text), but not trigger-specific itself, same as
     * any other effect that only exists to emit an event — a scroll or trap could use it later.
     */
    @Serializable @SerialName("showMessage")
    data class ShowMessage(val text: String) : Effect

    /** docs/48-gates-and-wander-ai.md: adds [gate] to [GameState.openGates] and drives [GameEvent.GateOpened] — the only thing that ever flips a gate open, whether authored directly or synthesized by `Triggers.kt` for a multi-trigger unlock. */
    @Serializable @SerialName("openGate")
    data class OpenGate(val gate: GateId) : Effect

    /** docs/50-terrain-mutation.md: overwrites `BattleMap.terrain[at]` with [tile] and drives [GameEvent.TerrainChanged] — no consequence for whoever's standing there, see the doc's "behavior notes." */
    @Serializable @SerialName("setTerrain")
    data class SetTerrain(val at: GridPos, val tile: TileType) : Effect
}
