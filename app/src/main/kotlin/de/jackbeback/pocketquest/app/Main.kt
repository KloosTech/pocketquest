package de.jackbeback.pocketquest.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.core.rules.content.startEncounter
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.SaveRepository
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.ui.runDesktopApp
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * The one location both `:designer` (`DesignerFileIo.defaultCatalogFile()`) and this app agree on
 * for authored content — resolved the same way, by walking up from wherever `:app:run`'s working
 * directory happens to be until `assets/` is found. Until doc11's `RunState`/node-graph layer
 * exists, there's no real "pick a run, pick a node" flow — this just plays whatever the catalog's
 * first encounter is, same placeholder shape `:designer`'s own `PlaytestPanel` already uses.
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

    val encounter = requireNotNull(catalog.encounters.values.firstOrNull()) { "Catalog has no encounters — author one in :designer first." }
    // doc11's RunState (persistent roster, player-picked loadout) doesn't exist yet — same
    // placeholder party PlaytestPanel.kt already uses, first few archetypes in the catalog.
    val party = catalog.archetypes.keys.take(3)
    val initialState = startEncounter(catalog, encounter, party, seed = 42L)

    // Persistence smoke test against the initial state — the interactive session's own state
    // evolves live inside :ui now, so there's no longer a fixed "final" resolver to round-trip.
    val dbPath = File("pocketquest-demo.db").absolutePath
    val db = Room.databaseBuilder<PocketQuestDatabase>(name = dbPath)
        .setDriver(BundledSQLiteDriver())
        // No real installs to migrate yet (pre-release) — a stale on-disk demo db from before
        // meta_state existed (schema version bump for docs/12-progression.md) would otherwise
        // throw at open instead of just recreating.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    val repository = SaveRepository(db.saveSlotDao())
    runBlocking {
        val resolver = Resolver(initialState)
        repository.save(id = "demo", campaignId = "demo-campaign", updatedAt = System.currentTimeMillis(), label = "Smoke test", resolver = resolver)
        val reloaded = repository.load("demo")
        println(if (reloaded == resolver) "Saved to $dbPath and reloaded byte-identical ✓" else "MISMATCH after reload — saved != reloaded")
    }
    db.close()

    runDesktopApp(initialState, catalog)
}
