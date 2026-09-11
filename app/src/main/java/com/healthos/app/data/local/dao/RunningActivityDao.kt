package com.healthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthos.app.data.local.entity.RunningActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningActivityDao {

    @Query(
        """
        SELECT *
        FROM running_activities
        ORDER BY startedAt DESC
        """
    )
    fun observeAll(): Flow<List<RunningActivityEntity>>

    @Query(
        """
        SELECT *
        FROM running_activities
        ORDER BY startedAt DESC
        """
    )
    suspend fun getAll(): List<RunningActivityEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM running_activities
        """
    )
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(
        activities: List<RunningActivityEntity>
    ): List<Long>
}
