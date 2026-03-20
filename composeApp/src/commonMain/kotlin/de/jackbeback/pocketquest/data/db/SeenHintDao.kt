package de.jackbeback.pocketquest.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SeenHintDao {
    @Query("SELECT hintId FROM seen_hints")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(entity: SeenHintEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM seen_hints WHERE hintId = :id)")
    suspend fun isSeen(id: String): Boolean
}
