package de.jackbeback.pocketquest.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SeenHintEntity::class, PersistentKvEntity::class],
    version = 1,
)
abstract class PocketQuestDatabase : RoomDatabase() {
    abstract fun seenHintDao(): SeenHintDao
    abstract fun persistentKvDao(): PersistentKvDao
}
