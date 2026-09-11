package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.LabResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_results ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<LabResultEntity>>

    @Query("SELECT COUNT(*) FROM lab_results")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<LabResultEntity>)

    @Query("DELETE FROM lab_results WHERE sourceRecordId LIKE 'demo-%'")
    suspend fun deleteDemoResults()

    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE lab_results SET testName = :testName, value = :value, unit = :unit, referenceRange = :referenceRange, recordedAtMillis = :recordedAtMillis WHERE id = :id")
    suspend fun updateById(id: Long, testName: String, value: Double, unit: String, referenceRange: String?, recordedAtMillis: Long)
}
