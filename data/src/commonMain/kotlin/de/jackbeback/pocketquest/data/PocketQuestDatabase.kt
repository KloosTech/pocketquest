package de.jackbeback.pocketquest.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [SaveSlotRow::class], version = 1)
@ConstructedBy(PocketQuestDatabaseConstructor::class)
abstract class PocketQuestDatabase : RoomDatabase() {
    abstract fun saveSlotDao(): SaveSlotDao
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
