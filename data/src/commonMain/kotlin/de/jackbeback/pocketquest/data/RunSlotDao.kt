package de.jackbeback.pocketquest.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RunSlotDao {
    @Upsert
    suspend fun upsert(row: RunSlotRow)

    @Query("SELECT * FROM run_slot WHERE runId = :runId")
    suspend fun get(runId: String): RunSlotRow?

    @Query("SELECT * FROM run_slot ORDER BY updatedAt DESC")
    suspend fun listAll(): List<RunSlotRow>

    @Delete
    suspend fun delete(row: RunSlotRow)

    @Query("DELETE FROM run_slot WHERE runId = :runId")
    suspend fun deleteById(runId: String)
}
