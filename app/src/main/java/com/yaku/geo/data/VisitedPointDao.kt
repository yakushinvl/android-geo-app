package com.yaku.geo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPointDao {
    @Query("SELECT * FROM visited_points ORDER BY timestamp ASC")
    fun getAllVisitedPoints(): Flow<List<VisitedPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: VisitedPoint)

    @Query("DELETE FROM visited_points")
    suspend fun deleteAll()
}
