package de.jackbeback.pocketquest.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

// version 3: added run_slot (docs/11-run-state.md) — no migration path from 2, no real installs
// exist yet to migrate; Room.databaseBuilder callers use fallbackToDestructiveMigration.
@Database(entities = [SaveSlotRow::class, MetaStateRow::class, RunSlotRow::class], version = 3)
@ConstructedBy(PocketQuestDatabaseConstructor::class)
abstract class PocketQuestDatabase : RoomDatabase() {
    abstract fun saveSlotDao(): SaveSlotDao
    abstract fun metaStateDao(): MetaStateDao
    abstract fun runSlotDao(): RunSlotDao
}

/**
 * Required for non-Android targets (docs/01-modules.md's iOS/desktop
 * scope) — Room's KSP compiler generates the `actual` implementation per
 * target; nothing to write here beyond the declaration.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object PocketQuestDatabaseConstructor : RoomDatabaseConstructor<PocketQuestDatabase> {
    override fun initialize(): PocketQuestDatabase
}
