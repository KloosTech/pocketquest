package de.jackbeback.pocketquest.ui.run

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterPool
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.EventDef
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.core.model.EventPool
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.ShopDef
import de.jackbeback.pocketquest.core.model.ShopId
import de.jackbeback.pocketquest.core.model.ShopPool
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression coverage for the crash chain: `pickUniform`'s `require(entries.isNotEmpty())`
 * (Pools.kt) throws the instant a run's generated graph reaches a node whose pool has zero
 * entries — reported live as "no EncounterPool for act 3/Boss" and later "event pool for act 1
 * has no entries to pick from", the latter hit by the SHIPPED content/catalog.json itself
 * (its eventPools/shopPools are each authored as `[{act: 1, entries: []}]`).
 */
class ResolvePoolsTest {

    private val event1 = EventDef(EventId("event1"), "Rest", "You Rest", choices = listOf(EventChoice("Eat")))
    private val encounter1 = EncounterSpec(EncounterId("encounter1"), "E1", MapId("room"))
    private val shop1 = ShopDef(ShopId("shop1"), act = 1, stock = emptyList())

    @Test
    fun anAuthoredButEmptyEventPoolIsBackfilledLikeAMissingOne() {
        val catalog = Catalog(events = mapOf(event1.id to event1), eventPools = listOf(EventPool(act = 1, entries = emptyList())))
        val pools = resolvePools(catalog)
        val act1 = pools.events.filter { it.act == 1 }
        assertTrue(act1.any { it.entries.isNotEmpty() }, "the empty authored pool must not shadow a filler pool for the same act")
        assertTrue(act1.all { it.entries.isEmpty() || event1.id in it.entries }, "filler must offer the catalog's real event content")
    }

    @Test
    fun anAuthoredButEmptyShopPoolIsBackfilledLikeAMissingOne() {
        val catalog = Catalog(shops = mapOf(shop1.id to shop1), shopPools = listOf(ShopPool(act = 1, entries = emptyList())))
        val pools = resolvePools(catalog)
        assertTrue(pools.shops.filter { it.act == 1 }.any { it.entries.isNotEmpty() })
    }

    @Test
    fun everyActGetsABossPoolEvenWhenOnlyAct1WasAuthored() {
        // The original crash: generateGraph always forces a Boss node on the final act
        // (RUN_ACTS = 3) regardless of what's authored — a catalog with only an act-1 Boss pool
        // (matching content/catalog.json's real shape) must still resolve act 3.
        val catalog = Catalog(
            encounters = mapOf(encounter1.id to encounter1),
            encounterPools = listOf(EncounterPool(act = 1, kind = NodeType.Boss, entries = listOf(encounter1.id))),
        )
        val pools = resolvePools(catalog)
        assertTrue(pools.encounters.any { it.act == 3 && it.kind == NodeType.Boss && it.entries.isNotEmpty() })
    }

    @Test
    fun aNonEmptyAuthoredPoolIsUsedAsIsNotDuplicated() {
        val catalog = Catalog(events = mapOf(event1.id to event1), eventPools = listOf(EventPool(act = 1, entries = listOf(event1.id))))
        val pools = resolvePools(catalog)
        assertTrue(pools.events.count { it.act == 1 } == 1, "an already-covered act shouldn't also get a filler pool")
    }
}
