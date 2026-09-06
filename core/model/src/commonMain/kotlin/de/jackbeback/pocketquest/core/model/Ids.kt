package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline @Serializable value class EntityId(val raw: Long)
@JvmInline @Serializable value class ArchetypeId(val raw: String)
@JvmInline @Serializable value class ActionId(val raw: String)
@JvmInline @Serializable value class StatusId(val raw: String)
@JvmInline @Serializable value class ItemId(val raw: String)
@JvmInline @Serializable value class FeatureId(val raw: String)
@JvmInline @Serializable value class LinkId(val raw: Long)
@JvmInline @Serializable value class AiProfileId(val raw: String)
@JvmInline @Serializable value class DecisionId(val raw: Long)
@JvmInline @Serializable value class SlotKey(val raw: String)
@JvmInline @Serializable value class MapId(val raw: String)
@JvmInline @Serializable value class PropId(val raw: String)
@JvmInline @Serializable value class EncounterId(val raw: String)
@JvmInline @Serializable value class EventId(val raw: String)
@JvmInline @Serializable value class ShopId(val raw: String)
@JvmInline @Serializable value class TriggerId(val raw: String)
@JvmInline @Serializable value class LootId(val raw: String)
@JvmInline @Serializable value class GateId(val raw: String)
@JvmInline @Serializable value class CampaignId(val raw: String)
/** docs/52-organic-decoration-placement.md: generated once in `:designer` at placement time, never author-typed — same "stable authoring handle, never shown to the player" contract every other placement id already has. */
@JvmInline @Serializable value class DecorationId(val raw: String)
/** docs/49-campaign-authoring.md: moved here from `:core:run` (was defined alongside `GraphNode`/`NodeGraph` there) so `Catalog.campaigns` can reference it — same "Catalog is :core:model, :core:run depends on it, never the reverse" reasoning [NodeType] itself already documents in Pools.kt. */
@JvmInline @Serializable value class NodeId(val raw: String)
