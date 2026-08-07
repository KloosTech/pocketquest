package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * Static content definitions. Minimal, data-only for now — a real
 * JSON-backed loader arrives with :core:content later and will produce
 * the same types, so nothing here needs to change shape when it does.
 */
@Serializable
data class StatusDef(
    val id: StatusId,
    val name: String,
    val stackPolicy: StackPolicy,
    /** Applied once per stack — see Entity.stats() in :core:rules. */
    val modifiers: List<Modifier> = emptyList(),
    /** Executed once when the affected entity's turn starts (regeneration, damage over time). */
    val onTurnStart: List<EffectTemplate> = emptyList(),
    /** docs/18-damage-pipeline.md — collected the same way as [modifiers], through the entity carrying this status. */
    val damageSteps: List<DamageStep> = emptyList(),
    val healSteps: List<HealStep> = emptyList(),
)

@Serializable
data class ItemDef(
    val id: ItemId,
    val name: String,
    val modifiers: List<Modifier> = emptyList(),
    /** Occupies MainHand and OffHand — see equip()/canEquip() in :core:rules and docs/03-modifiers-and-status.md. */
    val twoHanded: Boolean = false,
    val damageSteps: List<DamageStep> = emptyList(),
    val healSteps: List<HealStep> = emptyList(),
)

/**
 * doc11-run-state.md: a level-up choice. `Progression.features: List<FeatureId>` (the run layer,
 * not built yet) is what a real leveling flow would populate; the engine-level primitive this
 * catalog entry feeds — Entity.features resolved through stats() and grantedActions() — doesn't
 * need that layer to exist, same as every other ModifierSource here.
 */
@Serializable
data class FeatureDef(
    val id: FeatureId,
    val name: String,
    val modifiers: List<Modifier> = emptyList(),
    val grantsActions: List<ActionId> = emptyList(),
    val damageSteps: List<DamageStep> = emptyList(),
    val healSteps: List<HealStep> = emptyList(),
)

@Serializable
data class Catalog(
    val archetypes: Map<ArchetypeId, Archetype> = emptyMap(),
    val statuses: Map<StatusId, StatusDef> = emptyMap(),
    val items: Map<ItemId, ItemDef> = emptyMap(),
    val actions: Map<ActionId, ActionDef> = emptyMap(),
    val features: Map<FeatureId, FeatureDef> = emptyMap(),
) {
    fun archetype(id: ArchetypeId): Archetype =
        archetypes[id] ?: error("Unknown archetype: ${id.raw}")

    fun statusDef(id: StatusId): StatusDef =
        statuses[id] ?: error("Unknown status: ${id.raw}")

    fun itemDef(id: ItemId): ItemDef =
        items[id] ?: error("Unknown item: ${id.raw}")

    fun actionDef(id: ActionId): ActionDef =
        actions[id] ?: error("Unknown action: ${id.raw}")

    fun featureDef(id: FeatureId): FeatureDef =
        features[id] ?: error("Unknown feature: ${id.raw}")
}
