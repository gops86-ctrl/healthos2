package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.NutritionEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionEntryDao {
    @Query("SELECT * FROM nutrition_entries ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<NutritionEntryEntity>>

    @Query("SELECT * FROM nutrition_entries WHERE source = :source ORDER BY recordedAtMillis DESC")
    fun observeBySource(source: String): Flow<List<NutritionEntryEntity>>

    @Query("SELECT * FROM nutrition_entries ORDER BY recordedAtMillis DESC")
    suspend fun getAll(): List<NutritionEntryEntity>

    @Query("SELECT COUNT(*) FROM nutrition_entries")
    suspend fun count(): Int

    @Query("DELETE FROM nutrition_entries WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM nutrition_entries WHERE recordedAtMillis >= :startMillis AND recordedAtMillis < :endMillis")
    suspend fun deleteByDateRange(startMillis: Long, endMillis: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<NutritionEntryEntity>)
}
