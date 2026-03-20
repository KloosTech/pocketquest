package de.jackbeback.pocketquest.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersistentKvDao {
    @Query("SELECT value FROM persistent_kv WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: PersistentKvEntity)

    @Query("DELETE FROM persistent_kv WHERE `key` = :key")
    suspend fun delete(key: String)
}
