package com.nutritrack.android

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.databinding.ActivityMainBinding
import com.nutritrack.android.databinding.ItemMacroCardBinding
import com.nutritrack.android.databinding.ItemMealBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: SessionManager
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomBar.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        setupStaticUi()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    private fun setupStaticUi() {
        binding.tvDate.text = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        setGreeting(session.getUserName())

        bindMacroStatic(binding.cardProtein, "🥩", "Protein", R.color.macro_protein_bg)
        bindMacroStatic(binding.cardCarbs, "🌾", "Carbs", R.color.macro_carbs_bg)
        bindMacroStatic(binding.cardFat, "🥑", "Fats", R.color.macro_fat_bg)
        binding.cardProtein.progressMacro.setIndicatorColor(color(R.color.macro_protein))
        binding.cardCarbs.progressMacro.setIndicatorColor(color(R.color.macro_carbs))
        binding.cardFat.progressMacro.setIndicatorColor(color(R.color.macro_fat))

        styleTab(binding.tabHomeIcon, binding.tabHomeLabel, active = true)
        binding.tabHomePill.setBackgroundResource(R.drawable.bg_nav_pill)
        styleTab(binding.tabDiaryIcon, binding.tabDiaryLabel, active = false)
        styleTab(binding.tabAnalysisIcon, binding.tabAnalysisLabel, active = false)
        styleTab(binding.tabProfileIcon, binding.tabProfileLabel, active = false)

        binding.tabHome.setOnClickListener {  }
        binding.tabDiary.setOnClickListener {  }
        binding.tabAnalysis.setOnClickListener {  }
        binding.tabProfile.setOnClickListener {  }
        binding.btnScan.setOnClickListener {  }
        binding.btnLogMeal.setOnClickListener {  }

        binding.btnRetry.setOnClickListener { loadDashboard() }
    }

    private fun setGreeting(name: String?) {
        binding.tvGreeting.text =
            if (name.isNullOrBlank()) "Hello 👋" else "Hello, ${name.trim().substringBefore(' ')} 👋"
    }

    private fun styleTab(icon: android.widget.ImageView, label: android.widget.TextView, active: Boolean) {
        val tint = color(if (active) R.color.nav_active else R.color.text_slate_400)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint))
        label.setTextColor(tint)
    }

    private fun bindMacroStatic(card: ItemMacroCardBinding, emoji: String, name: String, bgColor: Int) {
        card.tvMacroEmoji.text = emoji
        card.tvMacroEmoji.backgroundTintList = ColorStateList.valueOf(color(bgColor))
        card.tvMacroName.text = name
    }

    private fun loadDashboard() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            binding.progressLoading.visibility = View.VISIBLE
            binding.errorCard.visibility = View.GONE
            try {
                val (profileRes, logRes) = coroutineScope {
                    val profile = async { ApiClient.api.getProfile() }
                    val log = async { ApiClient.api.getTodayLog() }
                    profile.await() to log.await()
                }
                val profile = profileRes.body()
                val log = logRes.body()
                when {
                    profileRes.isSuccessful && profile != null && logRes.isSuccessful && log != null -> {
                        session.saveProfileSummary(profile.name, profile.calories ?: 0)
                        render(profile, log)
                    }
                    profileRes.code() == 401 || logRes.code() == 401 -> Unit
                    else -> showError(
                        "Couldn't load today's data (profile ${profileRes.code()}, meals ${logRes.code()})."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Unable to reach the server: ${e.localizedMessage ?: "connection error"}")
            } finally {
                binding.progressLoading.visibility = View.INVISIBLE
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.errorCard.visibility = View.VISIBLE
    }

    private fun render(profile: ProfileCalculationResponse, log: DailyLogResponse) {
        setGreeting(profile.name)
        val totals = log.totals ?: NutrientsDto()

        renderCalories(consumed = totals.calories, goal = (profile.calories ?: 0).toDouble())
        renderMacro(binding.cardProtein, totals.proteinG, profile.proteinG ?: 0.0)
        renderMacro(binding.cardCarbs, totals.carbsG, profile.carbsG ?: 0.0)
        renderMacro(binding.cardFat, totals.fatG, profile.fatG ?: 0.0)

        renderMeals(log.entries.orEmpty())
    }

    private fun renderCalories(consumed: Double, goal: Double) {
        val eaten = consumed.roundToInt()
        val goalInt = goal.roundToInt()
        binding.tvCaloriesConsumed.text = numberFormat.format(eaten)
        binding.tvCaloriesGoal.text = "/ ${numberFormat.format(goalInt)} kcal"

        val diff = goalInt - eaten
        binding.tvCaloriesRemaining.text =
            if (diff >= 0) "${numberFormat.format(diff)} kcal remaining"
            else "${numberFormat.format(-diff)} kcal over goal"

        val percent = if (goal > 0) (consumed / goal * 100).roundToInt() else 0
        binding.tvCaloriesPercent.text = "$percent%"
        binding.progressCalories.setProgressCompat(percent.coerceIn(0, 100), true)
    }

    private fun renderMacro(card: ItemMacroCardBinding, consumed: Double, goal: Double) {
        card.tvMacroValue.text = consumed.roundToInt().toString()
        card.tvMacroGoal.text = "Goal ${goal.roundToInt()}g"
        val percent = if (goal > 0) (consumed / goal * 100).roundToInt() else 0
        card.progressMacro.setProgressCompat(percent.coerceIn(0, 100), true)
    }

    private fun renderMeals(entries: List<LogEntryDto>) {
        binding.mealsContainer.removeAllViews()
        binding.tvMealsCount.text = "${entries.size} logged"

        if (entries.isEmpty()) {
            binding.mealsContainer.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            return
        }
        binding.emptyState.visibility = View.GONE
        binding.mealsContainer.visibility = View.VISIBLE

        val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        for (entry in entries) {
            val item = ItemMealBinding.inflate(layoutInflater, binding.mealsContainer, false)
            val time = parseTime(entry.loggedAt)
            val period = MealPeriod.of(time)

            item.tvMealEmoji.text = period.emoji
            item.tvMealEmoji.backgroundTintList = ColorStateList.valueOf(color(period.bgColor))
            item.tvMealType.text = period.label
            item.tvMealTime.text = time?.format(timeFormat).orEmpty()
            item.tvMealName.text = entry.name?.takeIf { it.isNotBlank() } ?: "Unnamed food"

            val n = entry.computed ?: NutrientsDto()
            item.tvMealMeta.text =
                "${n.calories.roundToInt()} kcal  •  ${n.proteinG.roundToInt()}g protein"
            item.tvMealQty.text = "${entry.quantityG.roundToInt()} g"

            binding.mealsContainer.addView(item.root)
        }
    }

    private fun parseTime(iso: String?): ZonedDateTime? =
        iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() }

    private fun color(res: Int) = ContextCompat.getColor(this, res)

    private enum class MealPeriod(val label: String, val emoji: String, val bgColor: Int) {
        BREAKFAST("Breakfast", "🥣", R.color.meal_breakfast_bg),
        LUNCH("Lunch", "🥗", R.color.meal_lunch_bg),
        SNACK("Snack", "🍎", R.color.meal_snack_bg),
        DINNER("Dinner", "🍽️", R.color.meal_dinner_bg),
        OTHER("Meal", "🍴", R.color.meal_snack_bg);

        companion object {
            fun of(time: ZonedDateTime?): MealPeriod = when (time?.hour) {
                null -> OTHER
                in 5..10 -> BREAKFAST
                in 11..14 -> LUNCH
                in 15..17 -> SNACK
                in 18..23 -> DINNER
                else -> SNACK
            }
        }
    }
}
