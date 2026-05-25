package com.yaku.geo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VisitedPoint::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun visitedPointDao(): VisitedPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geo_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
