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
    /** docs/21-ai-behavior-spec.md: the AI can't otherwise tell a buff from a debuff (an `ApplyStatus` effect doesn't say) — needed so `AiActionCategory.BuffAlly`/`DebuffEnemy` are actually derivable. Defaults true so every pre-existing status stays classified as beneficial, matching how they were all authored as buffs/heals-adjacent so far. */
    val beneficial: Boolean = true,
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
    /**
     * doc20: nothing previously stopped equipping a sword into a Ring slot — found while scoping
     * the Item editor. Empty means unconstrained (every pre-existing `ItemDef` keeps working
     * unchanged); non-empty is enforced by `:core:rules`' `canEquip`.
     */
    val validSlots: Set<Slot> = emptySet(),
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
    /** docs/16-art-direction.md's Map editor's output — absent from every catalog until the :designer module (doc17-engine-gaps.md-adjacent, doc16's "desktop designer") started producing them. */
    val maps: Map<MapId, BattleMapDef> = emptyMap(),
    /** docs/16-art-direction.md's Encounter editor's output. */
    val encounters: Map<EncounterId, EncounterSpec> = emptyMap(),
    /** docs/21-ai-behavior-spec.md. */
    val aiProfiles: Map<AiProfileId, AiProfileDef> = emptyMap(),
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

    fun mapDef(id: MapId): BattleMapDef =
        maps[id] ?: error("Unknown map: ${id.raw}")

    fun encounterSpec(id: EncounterId): EncounterSpec =
        encounters[id] ?: error("Unknown encounter: ${id.raw}")

    /**
     * Deliberately non-throwing, unlike every accessor above — [Archetype.aiProfile] defaults to
     * `AiProfileId("standard")` on every archetype whether or not the catalog authors any AI
     * content at all, so a missing lookup here is the normal zero-config case, not a dangling
     * reference. Falls back to an empty-tiers profile, which sends `chooseAction` straight to
     * `defaultScoredChoice` — exactly today's pre-tiered-AI behavior. `CatalogValidator` is what
     * still catches a genuinely mistyped, explicitly-authored profile id.
     */
    fun aiProfileOrDefault(id: AiProfileId): AiProfileDef =
        aiProfiles[id] ?: AiProfileDef(id, id.raw, tiers = emptyList())
}
