package de.jackbeback.pocketquest.androidapp

import android.app.Application
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.data.MetaRepository
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.RunRepository
import de.jackbeback.pocketquest.ui.assets.loadBundledCatalogJson
import de.jackbeback.pocketquest.ui.run.ContentPools
import de.jackbeback.pocketquest.ui.run.resolvePools
import kotlinx.coroutines.runBlocking

/**
 * The Android counterpart to `:app`'s desktop `Main.kt` — same bootstrap shape (load catalog, build
 * pools, open the Room DB, build repositories), just Context-shaped instead of a bare `main()`, and
 * reading the bundled catalog snapshot instead of the live file (see `loadBundledCatalogJson`'s own
 * doc comment). Plain `lateinit var`s exposed to `MainActivity`, no DI framework — same "no
 * unnecessary abstraction" the rest of this project already follows.
 */
class PocketQuestApplication : Application() {
    lateinit var catalog: Catalog
    lateinit var pools: ContentPools
    lateinit var metaRepository: MetaRepository
    lateinit var runRepository: RunRepository

    override fun onCreate() {
        super.onCreate()

        // One-time synchronous read of a small bundled JSON file at process start — the direct
        // Android equivalent of desktop Main.kt's blocking File.readText().
        val json = runBlocking { loadBundledCatalogJson() }
        catalog = CatalogLoader.parse(json)
        CatalogValidator.validate(catalog)
        pools = resolvePools(catalog)

        val db = Room.databaseBuilder<PocketQuestDatabase>(context = applicationContext, name = "pocketquest.db")
            .setDriver(BundledSQLiteDriver())
            // No real installs to migrate yet (pre-release) — a stale on-disk db from before the
            // current schema would otherwise throw at open instead of just recreating.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        metaRepository = MetaRepository(db.metaStateDao())
        runRepository = RunRepository(db.runSlotDao())
    }
}
