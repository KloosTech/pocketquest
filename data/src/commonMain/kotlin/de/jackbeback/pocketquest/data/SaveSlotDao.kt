package de.jackbeback.pocketquest.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SaveSlotDao {
    @Upsert
    suspend fun upsert(row: SaveSlotRow)

    @Query("SELECT * FROM save_slot WHERE id = :id")
    suspend fun get(id: String): SaveSlotRow?

    @Query("SELECT * FROM save_slot WHERE campaignId = :campaignId ORDER BY updatedAt DESC")
    suspend fun listByCampaign(campaignId: String): List<SaveSlotRow>

    @Delete
    suspend fun delete(row: SaveSlotRow)

    @Query("DELETE FROM save_slot WHERE id = :id")
    suspend fun deleteById(id: String)
}
