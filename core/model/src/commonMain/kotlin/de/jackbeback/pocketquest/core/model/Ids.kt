package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline @Serializable value class EntityId(val raw: Long)
@JvmInline @Serializable value class ArchetypeId(val raw: String)
@JvmInline @Serializable value class ActionId(val raw: String)
@JvmInline @Serializable value class StatusId(val raw: String)
@JvmInline @Serializable value class ItemId(val raw: String)
@JvmInline @Serializable value class LinkId(val raw: Long)
@JvmInline @Serializable value class AiProfileId(val raw: String)
@JvmInline @Serializable value class DecisionId(val raw: Long)
@JvmInline @Serializable value class SlotKey(val raw: String)
