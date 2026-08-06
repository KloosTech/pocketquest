package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Slot { MainHand, OffHand, Armor, Helm, Ring1, Ring2, Amulet }

/**
 * Carries only a reference to its [ItemDef] plus instance state — not a
 * modifier list. See [ModifierSource] for why.
 */
@Serializable
data class ItemInstance(
    val def: ItemId,
    val enchantment: Int = 0,
    val charges: Int? = null,
    val attuned: Boolean = false,
)

@Serializable
data class Equipment(val slots: Map<Slot, ItemInstance> = emptyMap()) {
    companion object { val EMPTY = Equipment() }
}
