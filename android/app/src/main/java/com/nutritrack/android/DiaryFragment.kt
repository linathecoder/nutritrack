package com.nutritrack.android

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.cache.NutriTrackLocalCache
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.databinding.DialogEditQuantityBinding
import com.nutritrack.android.databinding.FragmentDiaryBinding
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
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class DiaryFragment : Fragment() {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate = LocalDate.now()
    private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()
    private var loadJob: Job? = null
    private var cachedProfile: ProfileCalculationResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticUi()
        updateDateDisplay()
    }

    override fun onResume() {
        super.onResume()
        loadDiaryData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }

    private fun setupStaticUi() {
        bindMacroStatic(binding.cardProtein, "🥩", "Protein", R.color.macro_protein_bg)
        bindMacroStatic(binding.cardCarbs, "🌾", "Carbs", R.color.macro_carbs_bg)
        bindMacroStatic(binding.cardFat, "🥑", "Fats", R.color.macro_fat_bg)
        binding.cardProtein.progressMacro.setIndicatorColor(color(R.color.macro_protein))
        binding.cardCarbs.progressMacro.setIndicatorColor(color(R.color.macro_carbs))
        binding.cardFat.progressMacro.setIndicatorColor(color(R.color.macro_fat))

        binding.btnPrevDate.setOnClickListener {
            selectedDate = selectedDate.minusDays(1)
            onDateChanged()
        }

        binding.btnNextDate.setOnClickListener {
            selectedDate = selectedDate.plusDays(1)
            onDateChanged()
        }

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnTodayQuick.setOnClickListener {
            selectedDate = LocalDate.now()
            onDateChanged()
        }

        binding.btnDiaryLogMeal.setOnClickListener {
            (activity as? MainActivity)?.openLogMeal()
        }

        binding.btnRetry.setOnClickListener { loadDiaryData() }
    }

    private fun onDateChanged() {
        updateDateDisplay()
        loadDiaryData()
    }

    private fun updateDateDisplay() {
        val today = LocalDate.now()
        val isToday = selectedDate == today
        val isYesterday = selectedDate == today.minusDays(1)
        val isTomorrow = selectedDate == today.plusDays(1)

        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        val formatted = selectedDate.format(formatter)

        binding.tvSelectedDate.text = when {
            isToday -> "Today, $formatted"
            isYesterday -> "Yesterday, $formatted"
            isTomorrow -> "Tomorrow, $formatted"
            else -> formatted
        }

        binding.btnTodayQuick.visibility = if (isToday) View.GONE else View.VISIBLE
        binding.dateNoticeCard.visibility = if (isToday) View.GONE else View.VISIBLE
        if (!isToday) {
            binding.tvDateNotice.text = "Viewing ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}. Full history for past dates requires backend endpoint GET /api/logs?date={date}."
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        cal.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                onDateChanged()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun bindMacroStatic(card: ItemMacroCardBinding, emoji: String, name: String, bgColor: Int) {
        card.tvMacroEmoji.text = emoji
        card.tvMacroEmoji.backgroundTintList = ColorStateList.valueOf(color(bgColor))
        card.tvMacroName.text = name
    }

    fun loadDiaryData() {
        loadJob?.cancel()
        val isToday = selectedDate == LocalDate.now()
        val cache = NutriTrackLocalCache.getInstance(requireContext())
        val cachedToday = if (isToday) cache.getCachedTodayLog() else null

        if (cachedToday != null) {
            val goalCalories = (cachedProfile?.calories ?: 2000).toDouble()
            renderTotals(cachedToday.totals ?: NutrientsDto(), goalCalories, cachedProfile)
            renderMeals(cachedToday.entries.orEmpty())
        }

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressLoading.visibility = View.VISIBLE
            binding.errorCard.visibility = View.GONE

            if (!isToday) {
                // Backend currently only has /api/logs/today
                // For non-today dates, show empty state with explanatory notice
                binding.progressLoading.visibility = View.INVISIBLE
                renderTotals(NutrientsDto(), (cachedProfile?.calories ?: 2000).toDouble(), cachedProfile)
                renderMeals(emptyList())
                return@launch
            }

            try {
                val (profileRes, logRes) = coroutineScope {
                    val profile = async { ApiClient.api.getProfile() }
                    val log = async { ApiClient.api.getTodayLog() }
                    profile.await() to log.await()
                }

                val profile = profileRes.body()
                val log = logRes.body()
                if (profile != null) cachedProfile = profile

                when {
                    logRes.isSuccessful && log != null -> {
                        cache.cacheTodayLog(log) // Task 6: cache today's log
                        val goalCalories = (profile?.calories ?: cachedProfile?.calories ?: 2000).toDouble()
                        val totals = log.totals ?: NutrientsDto()
                        renderTotals(totals, goalCalories, profile ?: cachedProfile)
                        renderMeals(log.entries.orEmpty())
                    }
                    logRes.code() == 401 -> Unit
                    else -> {
                        if (cachedToday == null) {
                            showError("Couldn't load meals (${logRes.code()}).")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cachedToday == null) {
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

    private fun renderTotals(totals: NutrientsDto, goalCalories: Double, profile: ProfileCalculationResponse?) {
        val eaten = totals.calories.roundToInt()
        val goalInt = goalCalories.roundToInt()
        binding.tvTotalsCaloriesConsumed.text = numberFormat.format(eaten)
        binding.tvTotalsCaloriesGoal.text = "/ ${numberFormat.format(goalInt)} kcal"

        val diff = goalInt - eaten
        binding.tvTotalsCaloriesRemaining.text =
            if (diff >= 0) "${numberFormat.format(diff)} kcal remaining"
            else "${numberFormat.format(-diff)} kcal over goal"

        val percent = if (goalCalories > 0) (totals.calories / goalCalories * 100).roundToInt() else 0
        binding.tvTotalsCaloriesPercent.text = "$percent%"
        binding.progressTotalsCalories.setProgressCompat(percent.coerceIn(0, 100), true)

        renderMacro(binding.cardProtein, totals.proteinG, profile?.proteinG ?: 0.0)
        renderMacro(binding.cardCarbs, totals.carbsG, profile?.carbsG ?: 0.0)
        renderMacro(binding.cardFat, totals.fatG, profile?.fatG ?: 0.0)
    }

    private fun renderMacro(card: ItemMacroCardBinding, consumed: Double, goal: Double) {
        card.tvMacroValue.text = consumed.roundToInt().toString()
        card.tvMacroGoal.text = "Goal ${goal.roundToInt()}g"
        val percent = if (goal > 0) (consumed / goal * 100).roundToInt() else 0
        card.progressMacro.setProgressCompat(percent.coerceIn(0, 100), true)
    }

    private fun renderMeals(entries: List<LogEntryDto>) {
        binding.diaryMealsContainer.removeAllViews()
        binding.tvDiaryMealsCount.text = "${entries.size} logged"

        if (entries.isEmpty()) {
            binding.diaryMealsContainer.visibility = View.GONE
            binding.diaryEmptyState.visibility = View.VISIBLE
            return
        }
        binding.diaryEmptyState.visibility = View.GONE
        binding.diaryMealsContainer.visibility = View.VISIBLE

        val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        for (entry in entries) {
            val item = ItemMealBinding.inflate(layoutInflater, binding.diaryMealsContainer, false)

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
                "${n.calories.roundToInt()} kcal  •  ${n.proteinG.roundToInt()}g P  •  ${n.carbsG.roundToInt()}g C  •  ${n.fatG.roundToInt()}g F"
            item.tvMealTime.text = parseTime(entry.loggedAt)?.format(timeFormat).orEmpty()
            item.tvMealQty.text = "${entry.quantityG.roundToInt()} g"

            // Allow editing and deleting for all entries in Diary
            item.btnMealEdit.visibility = View.VISIBLE
            item.btnMealDelete.visibility = View.VISIBLE

            val revealWidth = dp(110).toFloat()
            var downX = 0f
            var downY = 0f
            var startTranslation = 0f
            item.mealForeground.setOnTouchListener { view, event ->
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
                } else {
                    showEditQuantityDialog(entry)
                }
            }

            item.tvMealQty.setOnClickListener {
                showEditQuantityDialog(entry)
            }

            item.btnMealEdit.setOnClickListener {
                showEditQuantityDialog(entry)
            }

            item.btnMealDelete.setOnClickListener {
                val id = entry.id
                if (id.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "This entry can't be deleted because its ID is missing.", Toast.LENGTH_LONG).show()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete this entry?")
                        .setMessage("${entry.name ?: "This meal"} will be removed from your diary.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> deleteEntry(id) }
                        .show()
                }
            }

            binding.diaryMealsContainer.addView(item.root)
        }
    }

    private fun showEditQuantityDialog(entry: LogEntryDto) {
        val entryId = entry.id ?: return
        val currentQty = entry.quantityG
        val currentNutrients = entry.computed ?: NutrientsDto()

        // Base per 100g calculated proportionally (BR-02)
        val per100Factor = if (currentQty > 0) 100.0 / currentQty else 1.0
        val per100Calories = currentNutrients.calories * per100Factor
        val per100Protein = currentNutrients.proteinG * per100Factor
        val per100Carbs = currentNutrients.carbsG * per100Factor
        val per100Fat = currentNutrients.fatG * per100Factor

        val dialogBinding = DialogEditQuantityBinding.inflate(layoutInflater)
        dialogBinding.tvDialogFoodName.text = entry.name ?: "Food Item"
        dialogBinding.tvDialogOriginalMeta.text =
            "Per 100g: ${per100Calories.roundToInt()} kcal • ${per100Protein.roundToInt()}g protein • ${per100Carbs.roundToInt()}g carbs • ${per100Fat.roundToInt()}g fat"

        dialogBinding.etQuantity.setText(
            if (currentQty % 1.0 == 0.0) currentQty.toInt().toString()
            else String.format(Locale.US, "%.1f", currentQty)
        )
        dialogBinding.etQuantity.setSelection(dialogBinding.etQuantity.text?.length ?: 0)

        // Helper to update live scaled preview (BR-02)
        fun updatePreview(grams: Double) {
            val scale = grams / 100.0
            dialogBinding.tvScaledCalories.text = "${(per100Calories * scale).roundToInt()} kcal"
            dialogBinding.tvScaledProtein.text = String.format(Locale.US, "%.1fg", per100Protein * scale)
            dialogBinding.tvScaledCarbs.text = String.format(Locale.US, "%.1fg", per100Carbs * scale)
            dialogBinding.tvScaledFat.text = String.format(Locale.US, "%.1fg", per100Fat * scale)
        }

        updatePreview(currentQty)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Quantity")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            dialogBinding.etQuantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val raw = s?.toString()?.replace(',', '.')?.toDoubleOrNull()
                    when {
                        raw == null || raw <= 0.0 -> {
                            // BR-07: Quantity must be > 0
                            dialogBinding.tilQuantity.error = "Quantity must be greater than 0g"
                            saveBtn.isEnabled = false
                        }
                        raw > 10000.0 -> {
                            dialogBinding.tilQuantity.error = "Quantity cannot exceed 10,000g"
                            saveBtn.isEnabled = false
                        }
                        else -> {
                            dialogBinding.tilQuantity.error = null
                            saveBtn.isEnabled = true
                            updatePreview(raw)
                        }
                    }
                }
            })

            saveBtn.setOnClickListener {
                val newGrams = dialogBinding.etQuantity.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
                if (newGrams == null || newGrams <= 0.0) {
                    dialogBinding.tilQuantity.error = "Quantity must be greater than 0g"
                    return@setOnClickListener
                }

                dialog.dismiss()
                updateQuantity(entry, newGrams, per100Calories, per100Protein, per100Carbs, per100Fat)
            }
        }

        dialog.show()
    }

    private fun updateQuantity(
        entry: LogEntryDto,
        newGrams: Double,
        per100Cal: Double,
        per100Prot: Double,
        per100Carb: Double,
        per100Fat: Double
    ) {
        val entryId = entry.id ?: return
        val barcode = entry.barcode ?: "meal-${UUID.randomUUID()}"
        val isCustomMeal = barcode.startsWith("meal-")

        val req = LogFoodRequest(
            barcode = barcode,
            name = entry.name,
            ingredients = entry.ingredients,
            imageUrl = entry.imageUrl,
            per100g = NutrientsDto(
                calories = per100Cal,
                proteinG = per100Prot,
                carbsG = per100Carb,
                fatG = per100Fat
            ),
            quantityG = newGrams
        )

        binding.progressLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var success = false
                if (isCustomMeal) {
                    val res = ApiClient.api.updateLog(entryId, req)
                    if (res.isSuccessful) {
                        success = true
                    }
                }

                if (!success) {
                    // For non-custom meals or if PUT returns 400 (only custom meals can be edited),
                    // delete old entry and log with new quantity
                    val delRes = ApiClient.api.deleteLog(entryId)
                    val addRes = ApiClient.api.logFood(req)
                    if (addRes.isSuccessful) {
                        success = true
                    } else if (delRes.isSuccessful && !addRes.isSuccessful) {
                        showError("Failed to update entry: error ${addRes.code()}")
                    }
                }

                if (success) {
                    Toast.makeText(requireContext(), "Updated to ${newGrams.roundToInt()}g", Toast.LENGTH_SHORT).show()
                    loadDiaryData()
                } else {
                    showError("Could not update meal quantity.")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    showError("Error updating quantity: ${e.localizedMessage ?: "network error"}")
                }
            } finally {
                _binding?.progressLoading?.visibility = View.INVISIBLE
            }
        }
    }

    private fun deleteEntry(id: String) {
        binding.progressLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.deleteLog(id)
                if (response.isSuccessful) {
                    requireActivity().getSharedPreferences("saved_meal_ingredients", Context.MODE_PRIVATE)
                        .edit().remove("ingredients_$id").apply()
                    Toast.makeText(requireContext(), "Meal deleted", Toast.LENGTH_SHORT).show()
                    loadDiaryData()
                } else if (response.code() != 401) {
                    Toast.makeText(requireContext(), "Couldn't delete meal (${response.code()}).", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(requireContext(), "Unable to delete meal: ${e.localizedMessage ?: "connection error"}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _binding?.progressLoading?.visibility = View.INVISIBLE
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun parseTime(iso: String?): ZonedDateTime? =
        iso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()) }.getOrNull() }

    private fun color(res: Int) = ContextCompat.getColor(requireContext(), res)
}
