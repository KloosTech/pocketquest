package de.jackbeback.pocketquest.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.core.run.EncounterPool
import de.jackbeback.pocketquest.core.run.NodeType
import de.jackbeback.pocketquest.data.MetaRepository
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.RunRepository
import de.jackbeback.pocketquest.ui.run.ContentPools
import de.jackbeback.pocketquest.ui.runDesktopRunApp
import java.io.File

/**
 * The one location both `:designer` (`DesignerFileIo.defaultCatalogFile()`) and this app agree on
 * for authored content — resolved the same way, by walking up from wherever `:app:run`'s working
 * directory happens to be until `assets/` is found.
 */
private fun defaultCatalogFile(): File {
    val repoRoot = listOf(File("."), File(".."), File("../..")).firstOrNull { File(it, "assets").isDirectory } ?: File(".")
    return File(File(repoRoot, "content"), "catalog.json")
}

/**
 * `:designer` authoring tools for `EncounterPool`/`EventPool`/`ShopPool` are Pass 9, not built yet —
 * until then, every authored [EncounterId] is offered as Combat content for every act, plus the
 * Boss slot every run's final node needs (`generateGraph` always forces one there regardless of
 * weights). No events/shops are synthesized: a catalog with none authored just never rolls those
 * node types (`ContentPools.availableNodeTypeWeights` already excludes an empty pool).
 */
private fun placeholderPools(catalog: de.jackbeback.pocketquest.core.model.Catalog): ContentPools {
    val encounterIds = catalog.encounters.keys.toList()
    if (encounterIds.isEmpty()) return ContentPools()
    val combatPools = (1..3).map { act -> EncounterPool(act = act, kind = NodeType.Combat, entries = encounterIds) }
    val bossPool = EncounterPool(act = 3, kind = NodeType.Boss, entries = encounterIds)
    val eventPools = catalog.events.keys.toList().let { ids -> if (ids.isEmpty()) emptyList() else (1..3).map { act -> de.jackbeback.pocketquest.core.run.EventPool(act, ids) } }
    val shopPools = catalog.shops.keys.toList().let { ids -> if (ids.isEmpty()) emptyList() else (1..3).map { act -> de.jackbeback.pocketquest.core.run.ShopPool(act, ids) } }
    return ContentPools(encounters = combatPools + bossPool, events = eventPools, shops = shopPools)
}

fun main() {
    val catalogFile = defaultCatalogFile()
    check(catalogFile.exists()) { "No catalog at ${catalogFile.absolutePath} — author one in :designer first (Save writes exactly here)." }
    val catalog = CatalogLoader.parse(catalogFile.readText())
    CatalogValidator.validate(catalog)
    println("Loaded catalog from ${catalogFile.absolutePath}: ${catalog.archetypes.size} archetypes, ${catalog.actions.size} actions, ${catalog.encounters.size} encounters")
    check(catalog.encounters.isNotEmpty()) { "Catalog has no encounters — author one in :designer first." }

    val dbPath = File("pocketquest.db").absolutePath
    val db = Room.databaseBuilder<PocketQuestDatabase>(name = dbPath)
        .setDriver(BundledSQLiteDriver())
        // No real installs to migrate yet (pre-release) — a stale on-disk db from before the current
        // schema would otherwise throw at open instead of just recreating.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    runDesktopRunApp(
        catalog = catalog,
        metaRepository = MetaRepository(db.metaStateDao()),
        runRepository = RunRepository(db.runSlotDao()),
        pools = placeholderPools(catalog),
    )
}
