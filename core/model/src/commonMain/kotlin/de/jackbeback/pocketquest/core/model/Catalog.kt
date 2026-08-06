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
)

@Serializable
data class ItemDef(
    val id: ItemId,
    val name: String,
    val modifiers: List<Modifier> = emptyList(),
)

@Serializable
data class Catalog(
    val archetypes: Map<ArchetypeId, Archetype> = emptyMap(),
    val statuses: Map<StatusId, StatusDef> = emptyMap(),
    val items: Map<ItemId, ItemDef> = emptyMap(),
) {
    fun archetype(id: ArchetypeId): Archetype =
        archetypes[id] ?: error("Unknown archetype: ${id.raw}")

    fun statusDef(id: StatusId): StatusDef =
        statuses[id] ?: error("Unknown status: ${id.raw}")

    fun itemDef(id: ItemId): ItemDef =
        items[id] ?: error("Unknown item: ${id.raw}")
}
