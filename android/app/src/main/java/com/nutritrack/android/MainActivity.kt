package com.nutritrack.android

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.databinding.ActivityMainBinding
import com.nutritrack.android.databinding.ItemMacroCardBinding
import com.nutritrack.android.databinding.ItemMealBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.NumberFormat
import java.io.File
import java.time.Instant
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
        setGreeting(session.getUserName())
        loadProfilePhoto()

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
        styleTab(binding.tabSettingsIcon, binding.tabSettingsLabel, active = false)

        binding.tabHome.setOnClickListener {  }
        binding.tabDiary.setOnClickListener {  }
        binding.tabAnalysis.setOnClickListener {  }
        binding.tabSettings.setOnClickListener {  }
        binding.btnAvatar.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(R.anim.profile_enter, R.anim.home_stay)
        }
        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
        binding.btnLogMeal.setOnClickListener {
            startActivity(Intent(this, CreateMealActivity::class.java))
        }

        binding.btnRetry.setOnClickListener { loadDashboard() }
    }

    private fun setGreeting(name: String?) {
        val first = name?.trim()?.substringBefore(' ').orEmpty()
        binding.tvUserName.text = if (first.isEmpty()) "Hello there " else "Hello, $first "
        binding.tvAvatarInitial.text = if (first.isEmpty()) "👤" else first.first().uppercaseChar().toString()
        loadProfilePhoto()
    }

    private fun loadProfilePhoto() {
        val photoFile = File(filesDir, "profile_picture_${session.getEmail().orEmpty().lowercase(Locale.US).hashCode()}.jpg")
        if (photoFile.exists()) {
            binding.ivAvatarPhoto.load(photoFile) {
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
            }
            binding.ivAvatarPhoto.visibility = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
        } else {
            binding.ivAvatarPhoto.visibility = View.GONE
            binding.tvAvatarInitial.visibility = View.VISIBLE
        }
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
                        setGreeting(profile.name)
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

            if (entry.barcode?.startsWith("meal-") == true) {
                item.ivMealImage.setImageResource(R.drawable.ic_meal_bowl)
            } else if (entry.imageUrl.isNullOrBlank()) {
                item.ivMealImage.setImageResource(R.drawable.img_not_available)
            } else {
                item.ivMealImage.load(entry.imageUrl) {
                    crossfade(true)
                    error(R.drawable.img_not_available)
                }
            }

            item.tvMealName.text = entry.name?.takeIf { it.isNotBlank() } ?: "Unnamed food"

            val n = entry.computed ?: NutrientsDto()
            item.tvMealMeta.text =
                "${n.calories.roundToInt()} kcal  •  ${n.proteinG.roundToInt()}g protein"
            item.tvMealTime.text = parseTime(entry.loggedAt)?.format(timeFormat).orEmpty()
            item.tvMealQty.text = "${entry.quantityG.roundToInt()} g"

            val isCustomMeal = entry.barcode?.startsWith("meal-") == true
            item.btnMealEdit.visibility = if (isCustomMeal) View.VISIBLE else View.GONE
            item.btnMealDelete.visibility = if (isCustomMeal) View.VISIBLE else View.GONE
            val revealWidth = dp(110).toFloat()
            var downX = 0f
            var downY = 0f
            var startTranslation = 0f
            if (isCustomMeal) item.mealForeground.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startTranslation = view.translationX
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (abs(dx) > abs(dy) && (abs(dx) > dp(5) || startTranslation != 0f)) {
                            view.translationX = (startTranslation + dx).coerceIn(-revealWidth, 0f)
                            true
                        } else false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val dx = event.rawX - downX
                        val target = when {
                            abs(dx) > dp(24) && view.translationX < -revealWidth / 2f -> -revealWidth
                            startTranslation != 0f -> 0f
                            else -> view.translationX
                        }
                        view.animate().translationX(target).setDuration(180).start()
                        true
                    }
                    else -> false
                }
            }
            item.mealForeground.setOnClickListener {
                if (item.mealForeground.translationX != 0f) {
                    item.mealForeground.animate().translationX(0f).setDuration(160).start()
                }
            }
            item.btnMealEdit.setOnClickListener {
                val id = entry.id
                if (id.isNullOrBlank()) {
                    android.widget.Toast.makeText(this, "This meal can't be edited because its entry ID is missing.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    startActivity(CreateMealActivity.editIntent(this, entry))
                }
            }
            item.btnMealDelete.setOnClickListener {
                val id = entry.id
                if (id.isNullOrBlank()) {
                    android.widget.Toast.makeText(this, "This entry can't be deleted because its ID is missing.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Delete this meal?")
                        .setMessage("${entry.name ?: "This meal"} will be removed from today's diary.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> deleteMeal(id) }
                        .show()
                }
            }
            binding.mealsContainer.addView(item.root)
        }
    }

    private fun deleteMeal(id: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteLog(id)
                if (response.isSuccessful) {
                    getSharedPreferences("saved_meal_ingredients", MODE_PRIVATE).edit().remove("ingredients_$id").apply()
                    android.widget.Toast.makeText(this@MainActivity, "Meal deleted", android.widget.Toast.LENGTH_SHORT).show()
                    loadDashboard()
                } else if (response.code() != 401) {
                    android.widget.Toast.makeText(this@MainActivity, "Couldn't delete meal (${response.code()}).", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) android.widget.Toast.makeText(
                    this@MainActivity, "Unable to delete meal: ${e.localizedMessage ?: "connection error"}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun parseTime(iso: String?): ZonedDateTime? =
        iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() }

    private fun color(res: Int) = ContextCompat.getColor(this, res)
}
