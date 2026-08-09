package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.ShopDef
import de.jackbeback.pocketquest.core.model.ShopEntry
import de.jackbeback.pocketquest.core.model.ShopPool
import de.jackbeback.pocketquest.core.rules.rollRange

/**
 * docs/13-encounters-and-events.md: capacity bound to the party's Strength — the sum of every
 * active party member's derived STR, checked against the count of unequipped items only
 * (`RunState.inventory`; equipped gear already occupies per-Champion `Equipment` slots, a separate
 * pool this doesn't govern).
 */
fun carryCapacity(party: List<PartyMember>, cat: Catalog): Int =
    party.sumOf { cat.archetype(it.archetype).abilities.str }

/** 50% of `ItemDef.basePrice` (docs/13) — never `ShopEntry.price`, since a looted item never bought from a shop has no `ShopEntry` of its own. */
fun sellValue(item: ItemDef): Int = (item.basePrice * 0.5).toInt()

sealed interface ShopRejection {
    data class NotEnoughGold(val need: Int, val have: Int) : ShopRejection
    data class CarryCapacityExceeded(val capacity: Int, val current: Int) : ShopRejection
}

sealed interface BuyResult {
    data class Bought(val run: RunState) : BuyResult
    data class Rejected(val reasons: List<ShopRejection>) : BuyResult
}

fun canBuy(run: RunState, entry: ShopEntry, cat: Catalog): List<ShopRejection> {
    val rejections = mutableListOf<ShopRejection>()
    if (run.gold < entry.price) rejections += ShopRejection.NotEnoughGold(need = entry.price, have = run.gold)
    val capacity = carryCapacity(run.party, cat)
    if (run.inventory.items.size >= capacity) rejections += ShopRejection.CarryCapacityExceeded(capacity, run.inventory.items.size)
    return rejections
}

/** Buying: `run.gold -= price`, item added to `RunState.inventory` — blocked outright (docs/13) if gold or carry capacity is insufficient, never a partial purchase. */
fun buy(run: RunState, entry: ShopEntry, cat: Catalog): BuyResult {
    val rejections = canBuy(run, entry, cat)
    if (rejections.isNotEmpty()) return BuyResult.Rejected(rejections)
    return BuyResult.Bought(run.copy(gold = run.gold - entry.price, inventory = run.inventory.copy(items = run.inventory.items + entry.item)))
}

sealed interface SellRejection {
    data class ItemNotInInventory(val item: ItemId) : SellRejection
}

sealed interface SellResult {
    data class Sold(val run: RunState, val amount: Int) : SellResult
    data class Rejected(val reasons: List<SellRejection>) : SellResult
}

/** Selling: any item in `RunState.inventory` (looted or bought) sells for [sellValue] — removes exactly one copy. */
fun sell(run: RunState, item: ItemId, cat: Catalog): SellResult {
    if (item !in run.inventory.items) return SellResult.Rejected(listOf(SellRejection.ItemNotInInventory(item)))
    val amount = sellValue(cat.itemDef(item))
    val updated = run.copy(gold = run.gold + amount, inventory = run.inventory.copy(items = run.inventory.items - item))
    return SellResult.Sold(updated, amount)
}

/**
 * "A Shop node picks N entries... at random from the act-matching ShopDef.stock, and offers them
 * for the duration of that visit" (docs/13) — sampling without replacement, capped at [n] or
 * `shop.stock.size`, whichever is smaller. Late-bound like `resolveEncounterNode`/`resolveEventNode`
 * — nothing about "what was offered this visit" is persisted onto `RunState`.
 */
fun offerShopVisit(shop: ShopDef, n: Int, rng: RngState): Pair<RngState, List<ShopEntry>> {
    val remaining = shop.stock.toMutableList()
    var current = rng
    val offered = mutableListOf<ShopEntry>()
    repeat(minOf(n, shop.stock.size)) {
        val (advanced, index) = current.rollRange(0, remaining.size - 1)
        current = advanced
        offered += remaining.removeAt(index)
    }
    return current to offered
}

/** The "enter a Shop node" step, mirroring `resolveEncounterNode`/`resolveEventNode`. */
fun resolveShopNode(run: RunState, node: GraphNode, pools: List<ShopPool>, cat: Catalog): Pair<ShopDef, RngState> {
    val pool = pools.firstOrNull { it.act == node.act } ?: error("no ShopPool for act ${node.act}")
    val (advanced, id) = pickShop(pool, run.rng)
    return cat.shopDef(id) to advanced
}
