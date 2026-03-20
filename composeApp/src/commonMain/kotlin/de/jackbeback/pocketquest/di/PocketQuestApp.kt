package de.jackbeback.pocketquest.di

import de.jackbeback.pocketquest.data.db.PocketQuestDatabase
import org.koin.core.context.startKoin

/**
 * Call once at application startup (before any Koin injection).
 *
 * @param databaseFactory Platform-specific factory that creates the Room database.
 *   - Android: `Room.databaseBuilder<PocketQuestDatabase>(context, "pocketquest.db").build()`
 *   - Desktop: `Room.databaseBuilder<PocketQuestDatabase>(name = dbFilePath).build()`
 *   - iOS:     same as Desktop with an NSDocumentDirectory-based path
 */
fun initKoin(databaseFactory: () -> PocketQuestDatabase) {
    startKoin {
        modules(gameModule(databaseFactory))
    }
}
