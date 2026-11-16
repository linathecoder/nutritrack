package com.nutritrack.android.data.food

import android.content.Context
import android.graphics.Color
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer

object FoodRepository {

    private const val ASSET_NAME = "ingredients_full.json"
    private const val DEFAULT_TINT = "#F0F7F3"

    private class Entry(val food: FoodItem, val nameNorm: String, val haystack: String)

    private val mutex = Mutex()
    @Volatile private var loaded = false
    private var entries: List<Entry> = emptyList()
    private var tints: Map<String, Int> = emptyMap()

    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlphaNum = Regex("[^a-z0-9]+")

    val isLoaded: Boolean get() = loaded

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return@withLock
            val appContext = context.applicationContext
            withContext(Dispatchers.IO) {
                val dataset = appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use {
                    Gson().fromJson(it, FoodDataset::class.java)
                }
                val categoriesById = dataset.categories.associateBy { it.id }
                tints = dataset.categories.associate { it.id to parseColor(it.tint) }
                entries = dataset.ingredients.map { food ->
                    val cat = categoriesById[food.category]
                    val nameNorm = normalize(food.name)
                    val haystack = listOf(nameNorm, normalize(food.nameFr.orEmpty()), normalize(cat?.name.orEmpty()), normalize(cat?.nameFr.orEmpty()))
                        .joinToString(" ")
                    Entry(food, nameNorm, haystack)
                }
            }
            loaded = true
        }
    }

    fun findById(id: String): FoodItem? = entries.firstOrNull { it.food.id == id }?.food

    fun tintFor(categoryId: String): Int = tints[categoryId] ?: parseColor(DEFAULT_TINT)

    suspend fun search(query: String, limit: Int = 60): List<FoodItem> = withContext(Dispatchers.Default) {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return@withContext entries.asSequence().filter { it.food.popular }.take(limit).map { it.food }.toList()
        }
        val scored = ArrayList<Pair<Int, FoodItem>>()
        for (e in entries) {
            if (!tokens.all { e.haystack.contains(it) }) continue
            var score = 0
            if (e.food.popular) score += 1000
            for (t in tokens) {
                score += when {
                    e.nameNorm.startsWith(t) -> 400
                    e.nameNorm.contains(" $t") -> 200
                    else -> 0
                }
            }
            if (e.nameNorm.contains(" raw")) score += 60
            score -= e.nameNorm.length / 2
            scored += score to e.food
        }
        scored.sortByDescending { it.first }
        scored.asSequence().take(limit).map { it.second }.toList()
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(nonAlphaNum, " ")
            .trim()

    private fun parseColor(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: IllegalArgumentException) {
        Color.parseColor(DEFAULT_TINT)
    }
}
