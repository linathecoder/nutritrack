package com.nutritrack.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("UPDATE users SET name = :name, dailyCalories = :calories, profileCompleted = 1")
    suspend fun updateProfileSummary(name: String?, calories: Int)

    @Query("UPDATE users SET profileCompleted = :completed")
    suspend fun setProfileCompleted(completed: Boolean)

    @Query("DELETE FROM users")
    suspend fun clear()
}

@Dao
abstract class LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY loggedAt ASC")
    abstract fun observeAll(): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: LogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entries: List<LogEntryEntity>)

    @Query("DELETE FROM log_entries WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM log_entries")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(entries: List<LogEntryEntity>) {
        clear()
        upsertAll(entries)
    }
}

@Dao
abstract class WeightDao {
    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    abstract fun observeAll(): Flow<List<WeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entry: WeightEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entries: List<WeightEntity>)

    @Query("DELETE FROM weight_entries WHERE date = :date")
    abstract suspend fun deleteByDate(date: String)

    @Query("DELETE FROM weight_entries")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(entries: List<WeightEntity>) {
        clear()
        upsertAll(entries)
    }
}
