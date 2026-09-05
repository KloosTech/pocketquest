package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.Inventory
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.ShopDef
import de.jackbeback.pocketquest.core.model.ShopEntry
import de.jackbeback.pocketquest.core.model.ShopId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShopTest {

    private fun archetype(id: String, str: Int) = Archetype(
        id = ArchetypeId(id), name = id,
        abilities = AbilityScores(str, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private fun member(id: String, archetype: ArchetypeId) =
        PartyMember(MemberId(id), name = id, archetype = archetype, hp = 20, mana = 5, controller = Controller.Human)

    private fun run(party: List<PartyMember>, gold: Int = 100, inventory: Inventory = Inventory()) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Shop)), start = NodeId("n1")),
        position = NodeId("n1"), party = party, gold = gold, inventory = inventory,
    )

    @Test
    fun carryCapacityIsTheSumOfPartyStr() {
        val cat = Catalog(archetypes = mapOf(ArchetypeId("a") to archetype("a", 14), ArchetypeId("b") to archetype("b", 8)))
        val party = listOf(member("m1", ArchetypeId("a")), member("m2", ArchetypeId("b")))
        assertEquals(22, carryCapacity(party, cat))
    }

    @Test
    fun sellValueIsHalfOfBasePrice() {
        val item = ItemDef(id = ItemId("sword"), name = "Sword", basePrice = 100)
        assertEquals(50, sellValue(item))
    }

    private fun catalogWithHero(str: Int = 12) = Catalog(
        archetypes = mapOf(ArchetypeId("hero") to archetype("hero", str)),
        items = mapOf(ItemId("potion") to ItemDef(id = ItemId("potion"), name = "Potion", basePrice = 20)),
    )

    @Test
    fun buyingDeductsGoldAndAddsTheItem() {
        val cat = catalogWithHero()
        val before = run(listOf(member("m1", ArchetypeId("hero"))), gold = 100)
        val result = buy(before, ShopEntry(ItemId("potion"), price = 30), cat)
        val bought = result as BuyResult.Bought
        assertEquals(70, bought.run.gold)
        assertEquals(listOf(ItemId("potion")), bought.run.inventory.items)
    }

    @Test
    fun buyingIsRejectedWithoutEnoughGold() {
        val cat = catalogWithHero()
        val before = run(listOf(member("m1", ArchetypeId("hero"))), gold = 5)
        val result = buy(before, ShopEntry(ItemId("potion"), price = 30), cat)
        check(result is BuyResult.Rejected)
        assertTrue(result.reasons.any { it is ShopRejection.NotEnoughGold })
    }

    @Test
    fun buyingIsRejectedAtCarryCapacityAndDoesNotMutateState() {
        val cat = catalogWithHero(str = 1) // capacity 1
        val before = run(listOf(member("m1", ArchetypeId("hero"))), gold = 100, inventory = Inventory(listOf(ItemId("junk"))))
        val result = buy(before, ShopEntry(ItemId("potion"), price = 10), cat)
        check(result is BuyResult.Rejected)
        assertTrue(result.reasons.any { it is ShopRejection.CarryCapacityExceeded })
    }

    @Test
    fun sellingAddsGoldAndRemovesOneCopy() {
        val cat = catalogWithHero()
        val before = run(listOf(member("m1", ArchetypeId("hero"))), gold = 0, inventory = Inventory(listOf(ItemId("potion"), ItemId("potion"))))
        val result = sell(before, ItemId("potion"), cat) as SellResult.Sold
        assertEquals(10, result.amount)
        assertEquals(10, result.run.gold)
        assertEquals(listOf(ItemId("potion")), result.run.inventory.items)
    }

    @Test
    fun sellingAnItemNotHeldIsRejected() {
        val cat = catalogWithHero()
        val before = run(listOf(member("m1", ArchetypeId("hero"))))
        val result = sell(before, ItemId("potion"), cat)
        assertTrue(result is SellResult.Rejected)
    }

    @Test
    fun offerShopVisitNeverOffersDuplicatesOrMoreThanStock() {
        val stock = listOf(ShopEntry(ItemId("a"), 1), ShopEntry(ItemId("b"), 2), ShopEntry(ItemId("c"), 3))
        val shop = ShopDef(id = ShopId("s1"), act = 1, stock = stock)
        val (_, offered) = offerShopVisit(shop, n = 2, rng = RngState(seed = 1L))
        assertEquals(2, offered.size)
        assertEquals(offered.size, offered.distinct().size)
        assertTrue(offered.all { it in stock })
    }

    @Test
    fun offerShopVisitCapsAtStockSizeWhenNExceedsIt() {
        val stock = listOf(ShopEntry(ItemId("a"), 1))
        val shop = ShopDef(id = ShopId("s1"), act = 1, stock = stock)
        val (_, offered) = offerShopVisit(shop, n = 5, rng = RngState(seed = 1L))
        assertEquals(1, offered.size)
    }
}
