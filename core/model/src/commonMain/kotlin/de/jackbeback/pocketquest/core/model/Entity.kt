package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Health(val current: Int, val temp: Int = 0)

@Serializable
data class Resources(val ap: Int, val mana: Int, val quickUsed: Boolean = false, val reactionUsed: Boolean = false)

/**
 * Player and enemy differ ONLY in actor.faction/actor.controller, never in
 * type. Nullable fields mean "this entity doesn't have this facet" (a wall
 * has no health field... it has health=null; a reserve unit has pos=null).
 */
@Serializable
data class Entity(
    val id: EntityId,
    val archetype: ArchetypeId,
    val pos: GridPos?,
    val health: Health?,
    val resources: Resources?,
    val actor: Actor?,
    val equipment: Equipment = Equipment.EMPTY,
    val statuses: List<ActiveStatus> = emptyList(),
    /**
     * doc17-engine-gaps.md 1.6/1.7: which level-up features this entity has — Archetype.actions is
     * no longer the only action source once leveling exists, and stats() must derive feature
     * modifiers too. Just def ids, like statuses/equipment — resolved through Catalog, nothing
     * stored twice. grantedActions() (in :core:rules) derives the action side; stats() derives the
     * modifier side. :core:run's future Progression.features is what would actually populate this
     * during a real leveling flow; the primitive itself doesn't need that layer to exist.
     */
    val features: List<FeatureId> = emptyList(),
    val blocksMovement: Boolean = true,
    /** One LinkId per entity at a time — starting a new one ends the previous, see docs/03-modifiers-and-status.md. */
    val concentrating: LinkId? = null,
    /** A champion's 2-point ability-score point-buy, spent once at creation (see `ChampionRecord.abilityBonuses`). Zero for every non-champion entity. */
    val abilityBonuses: AbilityScores = AbilityScores.ZERO,
)
