package com.nutritrack.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.cache.NutriTrackLocalCache
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.databinding.FragmentHomeBinding
import com.nutritrack.android.databinding.ItemMacroCardBinding
import com.nutritrack.android.databinding.ItemMealBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        setupStaticUi()
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
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

        binding.btnAvatar.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(MainActivity.Tab.PROFILE)
        }

        binding.btnLogMeal.setOnClickListener {
            (activity as? MainActivity)?.openLogMeal()
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
        val email = session.getEmail().orEmpty().lowercase(Locale.US)
        val photoFile = File(requireContext().filesDir, "profile_picture_${email.hashCode()}.jpg")
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

    private fun bindMacroStatic(card: ItemMacroCardBinding, emoji: String, name: String, bgColor: Int) {
        card.tvMacroEmoji.text = emoji
        card.tvMacroEmoji.backgroundTintList = ColorStateList.valueOf(color(bgColor))
        card.tvMacroName.text = name
    }

    fun loadDashboard() {
        loadJob?.cancel()
        val cache = NutriTrackLocalCache.getInstance(requireContext())
        val cachedLog = cache.getCachedTodayLog()

        // If cached today's log exists, display immediately (Task 6 Room/SQLite cache)
        if (cachedLog != null) {
            val cachedCalories = session.getDailyCalories()
            renderCalories(cachedLog.totals?.calories ?: 0.0, (if (cachedCalories > 0) cachedCalories else 2000).toDouble())
            renderMeals(cachedLog.entries.orEmpty())
        }

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
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
                        cache.cacheTodayLog(log) // Cache today's log (Task 6)
                        render(profile, log)
                    }
                    profileRes.code() == 401 || logRes.code() == 401 -> Unit
                    else -> {
                        if (cachedLog == null) {
                            showError("Couldn't load today's data (profile ${profileRes.code()}, meals ${logRes.code()}).")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cachedLog == null) {
                    showError("Unable to reach the server: ${e.localizedMessage ?: "connection error"}")
                }
            } finally {
                _binding?.progressLoading?.visibility = View.INVISIBLE
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
                    android.widget.Toast.makeText(requireContext(), "This meal can't be edited because its entry ID is missing.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    startActivity(CreateMealActivity.editIntent(requireContext(), entry))
                }
            }
            item.btnMealDelete.setOnClickListener {
                val id = entry.id
                if (id.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), "This entry can't be deleted because its ID is missing.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteLog(id)
                if (response.isSuccessful) {
                    requireActivity().getSharedPreferences("saved_meal_ingredients", Context.MODE_PRIVATE)
                        .edit().remove("ingredients_$id").apply()
                    android.widget.Toast.makeText(requireContext(), "Meal deleted", android.widget.Toast.LENGTH_SHORT).show()
                    loadDashboard()
                } else if (response.code() != 401) {
                    android.widget.Toast.makeText(requireContext(), "Couldn't delete meal (${response.code()}).", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) android.widget.Toast.makeText(
                    requireContext(), "Unable to delete meal: ${e.localizedMessage ?: "connection error"}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun parseTime(iso: String?): ZonedDateTime? =
        iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
}
