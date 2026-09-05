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
    /** docs/40-status-icons.md: a manifest sprite id (`kind = "status"`), drawn in a row above any entity currently carrying this status. Null falls back to no icon (that status just doesn't appear in the row) — same missing-asset-is-never-a-crash contract every other sprite-id field already follows. */
    val icon: String? = null,
    /**
     * docs/42-status-stack-scaling.md: how many stacks this status loses at the END of its bearer's
     * own turn (Bleed's "2 per turn, then loses a stack" shape) — 0 (default) means it never decays
     * on its own, matching every pre-existing status. A DIFFERENT axis from [stackPolicy] (which only
     * governs what happens when this status is APPLIED again while already active) — decay fires
     * every turn regardless of whether the status was ever reapplied. Reaching 0 stacks removes the
     * status entirely, same as any other `StatusExpired`.
     */
    val decayStacksPerTurn: Int = 0,
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
    /** doc13-encounters-and-events.md: a shop's sell price is a fraction of this, independent of whatever `ShopEntry.price` a specific shop listing set — needed so a looted (never-bought) item still has a sell value. Defaults 0, matching every pre-existing `ItemDef` (nothing sellable until authored otherwise). */
    val basePrice: Int = 0,
    /** doc11-run-state.md: how an equipped item grants actions/modifiers beyond its own `modifiers` list — resolves through the same `Entity.grantedActions()`/`stats()` pipeline a `FeatureDef` already feeds (doc17 1.6/1.7). Null means the item is passive-only. */
    val grantsFeature: FeatureId? = null,
    /** docs/38-loot-reveal-screen.md: a manifest sprite id (`kind = "item"`), resolved through the same `AssetManifest.prop`/`GameAssetManifest.prop` flat lookup every other sprite-id field already uses. Null falls back to a text-only chip wherever an item needs a visual (the loot-reveal reel, the editor). */
    val icon: String? = null,
    /**
     * docs/47-inventory-screen.md: empty means the item isn't consumable (pure gear, or a quest
     * item) — non-empty means "Use" applies these via `applyRunEffect` (the same function events
     * already use) and removes one copy of the item from wherever it came from. No charges/stacks —
     * single-use only; `ItemInstance.charges` stays a separate, unrelated, still-unimplemented
     * concept. An item can have both `validSlots` and `useEffects` (equippable AND consumable).
     */
    val useEffects: List<RunEffect> = emptyList(),
)

/**
 * A reusable bundle of modifiers + granted actions, attached to an entity via any
 * `ModifierSource` — an archetype's innate features, or (doc11-run-state.md) an equipped
 * `ItemDef.grantsFeature`. No leveling in this game (docs/10-game-loop.md "No leveling"), so
 * there is no level-up flow that assigns these; they're purely a content-authoring building block.
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
    /** docs/13-encounters-and-events.md's Events section. */
    val events: Map<EventId, EventDef> = emptyMap(),
    /** docs/13-encounters-and-events.md's Shops section. */
    val shops: Map<ShopId, ShopDef> = emptyMap(),
    /** docs/13-encounters-and-events.md's Content pools section — which authored content a generated node of a given act/kind may resolve to. */
    val encounterPools: List<EncounterPool> = emptyList(),
    val eventPools: List<EventPool> = emptyList(),
    val shopPools: List<ShopPool> = emptyList(),
    /** docs/37-lootable-containers.md's Loot editor output — reusable lootable-container definitions, placed via rarity-tier `SpawnZone`s and referenced by `EncounterSpec.lootSpawns`. */
    val loot: Map<LootId, LootDef> = emptyMap(),
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

    fun eventDef(id: EventId): EventDef =
        events[id] ?: error("Unknown event: ${id.raw}")

    fun shopDef(id: ShopId): ShopDef =
        shops[id] ?: error("Unknown shop: ${id.raw}")

    fun lootDef(id: LootId): LootDef =
        loot[id] ?: error("Unknown loot: ${id.raw}")

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
