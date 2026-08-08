package de.jackbeback.pocketquest.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MetaStateDao {
    @Upsert
    suspend fun upsert(row: MetaStateRow)

    @Query("SELECT * FROM meta_state WHERE id = 0")
    suspend fun get(): MetaStateRow?
}
