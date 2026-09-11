package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.HealthMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthMetricDao {
    @Query(
        """
        SELECT * FROM health_metrics
        WHERE NOT EXISTS (
            SELECT 1 FROM health_metrics AS newer
            WHERE newer.metricType = health_metrics.metricType
              AND (
                  newer.recordedAtMillis > health_metrics.recordedAtMillis OR
                  (newer.recordedAtMillis = health_metrics.recordedAtMillis AND newer.id > health_metrics.id)
              )
        )
        ORDER BY recordedAtMillis DESC
        """
    )
    fun observeLatestByType(): Flow<List<HealthMetricEntity>>

    @Query("SELECT * FROM health_metrics ORDER BY recordedAtMillis ASC, id ASC")
    fun observeAllHistory(): Flow<List<HealthMetricEntity>>

    @Query("SELECT * FROM health_metrics WHERE metricType = :metricType ORDER BY recordedAtMillis ASC, id ASC")
    fun observeHistory(metricType: String): Flow<List<HealthMetricEntity>>

    @Query("DELETE FROM health_metrics WHERE metricType = :metricType AND source = :source AND sourceRecordId = :sourceRecordId")
    suspend fun deleteByTypeAndSourceRecord(metricType: String, source: String, sourceRecordId: String)

    @Query("DELETE FROM health_metrics WHERE source = :source AND sourceRecordId LIKE 'demo-%'")
    suspend fun deleteDemoMetricsForSource(source: String)

    @Query("SELECT COUNT(*) FROM health_metrics")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<HealthMetricEntity>)
}
