package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY recordedAtMillis DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE source = :source AND activityType = :activityType AND recordedAtMillis BETWEEN :startMillis AND :endMillis ORDER BY recordedAtMillis")
    suspend fun findBySourceAndTypeBetween(source: String, activityType: String, startMillis: Long, endMillis: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE source = :source AND recordedAtMillis BETWEEN :startMillis AND :endMillis ORDER BY recordedAtMillis")
    suspend fun findBySourceBetween(source: String, startMillis: Long, endMillis: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE source = :source AND sourceRecordId = :sourceRecordId LIMIT 1")
    suspend fun findBySourceRecordId(source: String, sourceRecordId: String): ActivityEntity?

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(activities: List<ActivityEntity>): List<Long>

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM activities WHERE source = :source AND activityType = :activityType")
    suspend fun deleteBySourceAndActivityType(source: String, activityType: String)
}
