package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * docs/13-encounters-and-events.md's Shops section. [id] isn't in doc13's original snippet — added
 * so a `ShopDef` can be a `Catalog` entry and a `ShopPool` entry, the same requirement `EncounterSpec`/
 * `EventDef` already have. [act] stays (doc13's own field) as a content-authoring cross-check: a
 * `ShopPool` only ever references `ShopDef`s whose own `act` agrees with the pool's.
 */
@Serializable
data class ShopDef(val id: ShopId, val act: Int, val stock: List<ShopEntry>)

@Serializable
data class ShopEntry(val item: ItemId, val price: Int)
