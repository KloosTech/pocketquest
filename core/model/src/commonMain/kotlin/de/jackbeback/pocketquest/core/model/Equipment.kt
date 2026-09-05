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

/**
 * One shared pool per run, not per-member (docs/13-encounters-and-events.md). Capacity (STR-bound)
 * is enforced at the call site that adds an item, not modeled as a stored field here. Lives in
 * `:core:model` (moved from `:core:run`, docs/47-inventory-screen.md) so `:core:meta`'s own
 * between-runs `MetaState.stash` can reuse the exact same shape without `:core:meta` depending on
 * `:core:run`.
 */
@Serializable
data class Inventory(val items: List<ItemId> = emptyList())
