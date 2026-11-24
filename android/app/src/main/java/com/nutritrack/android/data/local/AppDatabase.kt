package com.nutritrack.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, LogEntryEntity::class, WeightEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun weightDao(): WeightDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "nutritrack.db")
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
