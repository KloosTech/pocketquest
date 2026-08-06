package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SlotValue {
    @Serializable @SerialName("intSlot") data class IntSlot(val value: Int) : SlotValue
    @Serializable @SerialName("boolSlot") data class BoolSlot(val value: Boolean) : SlotValue
    @Serializable @SerialName("entitySlot") data class EntitySlot(val value: EntityId) : SlotValue
}

/** How an [EffectTemplate] resolves an entity placeholder — see docs/05-actions-and-effects.md. */
@Serializable
sealed interface Ref {
    @Serializable @SerialName("caster") data object Caster : Ref

    /** Expands to one effect per target at instantiation time, sorted by EntityId for determinism. */
    @Serializable @SerialName("eachTarget") data object EachTarget : Ref
    @Serializable @SerialName("slot") data class Slot(val key: SlotKey) : Ref
}

data class ActionCtx(
    val caster: EntityId,
    val targets: List<EntityId>,
    val point: GridPos? = null,
    val slots: Map<SlotKey, SlotValue> = emptyMap(),
)
