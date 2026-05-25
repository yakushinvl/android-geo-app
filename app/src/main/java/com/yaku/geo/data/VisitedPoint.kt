package com.yaku.geo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visited_points")
data class VisitedPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)
