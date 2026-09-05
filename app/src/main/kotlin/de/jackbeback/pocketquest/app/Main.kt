package de.jackbeback.pocketquest.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.data.MetaRepository
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.RunRepository
import de.jackbeback.pocketquest.ui.run.resolvePools
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
        pools = resolvePools(catalog),
    )
}
