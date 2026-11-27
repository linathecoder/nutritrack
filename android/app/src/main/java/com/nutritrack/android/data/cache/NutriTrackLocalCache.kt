package com.nutritrack.android.data.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto
import java.time.LocalDate

/**
 * Local offline cache database for Food Search results and Today's Meal Log (Task 6).
 * Provides instantaneous offline display and resilient caching when network is unavailable.
 */
class NutriTrackLocalCache private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_search_cache (
                query TEXT PRIMARY KEY,
                results_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_log_cache (
                log_date TEXT PRIMARY KEY,
                totals_json TEXT,
                entries_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS food_search_cache")
        db.execSQL("DROP TABLE IF EXISTS daily_log_cache")
        onCreate(db)
    }

    // -------------------------------------------------------------
    // Food Search Cache
    // -------------------------------------------------------------

    fun cacheFoodSearch(query: String, results: List<FoodItem>) {
        val normalizedQuery = query.trim().lowercase()
        try {
            val json = gson.toJson(results)
            val cv = ContentValues().apply {
                put("query", normalizedQuery)
                put("results_json", json)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "food_search_cache",
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (_: Exception) {
        }
    }

    fun getCachedFoodSearch(query: String): List<FoodItem>? {
        val normalizedQuery = query.trim().lowercase()
        return try {
            val cursor = readableDatabase.query(
                "food_search_cache",
                arrayOf("results_json"),
                "query = ?",
                arrayOf(normalizedQuery),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val json = it.getString(0)
                    val type = object : TypeToken<List<FoodItem>>() {}.type
                    gson.fromJson<List<FoodItem>>(json, type)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // Today's Log Cache
    // -------------------------------------------------------------

    fun cacheTodayLog(log: DailyLogResponse) {
        val todayStr = log.date ?: LocalDate.now().toString()
        try {
            val totalsJson = gson.toJson(log.totals ?: NutrientsDto())
            val entriesJson = gson.toJson(log.entries.orEmpty())
            val cv = ContentValues().apply {
                put("log_date", todayStr)
                put("totals_json", totalsJson)
                put("entries_json", entriesJson)
                put("updated_at", System.currentTimeMillis())
            }
            writableDatabase.insertWithOnConflict(
                "daily_log_cache",
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (_: Exception) {
        }
    }

    fun getCachedTodayLog(dateStr: String = LocalDate.now().toString()): DailyLogResponse? {
        return try {
            val cursor = readableDatabase.query(
                "daily_log_cache",
                arrayOf("log_date", "totals_json", "entries_json"),
                "log_date = ?",
                arrayOf(dateStr),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val date = it.getString(0)
                    val totalsJson = it.getString(1)
                    val entriesJson = it.getString(2)

                    val totals = gson.fromJson(totalsJson, NutrientsDto::class.java)
                    val entriesType = object : TypeToken<List<LogEntryDto>>() {}.type
                    val entries = gson.fromJson<List<LogEntryDto>>(entriesJson, entriesType)

                    DailyLogResponse(date = date, totals = totals, entries = entries)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val DATABASE_NAME = "nutritrack_cache.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: NutriTrackLocalCache? = null

        fun getInstance(context: Context): NutriTrackLocalCache =
            instance ?: synchronized(this) {
                instance ?: NutriTrackLocalCache(context).also { instance = it }
            }
    }
}
