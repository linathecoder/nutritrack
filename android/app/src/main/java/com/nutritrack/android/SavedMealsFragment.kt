package com.nutritrack.android

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.meals.SavedMealScorer
import com.nutritrack.android.data.meals.SavedMealsStore
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.SavedMealDto
import com.nutritrack.android.databinding.FragmentSavedMealsBinding
import com.nutritrack.android.databinding.SheetQuickLogBinding
import com.nutritrack.android.ui.SavedMealAdapter
import com.nutritrack.android.ui.SavedMealUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * "My Meals" page (replaces the Diary tab).
 * Shows every meal the user created with "Create a meal", with search, filter chips, sorting,
 * favorites (heart) and a one-tap "Log" that adds the meal to today's log.
 */
class SavedMealsFragment : Fragment() {

    private enum class Filter(val label: String) {
        ALL("All"),
        FAVORITES("Favorites"),
        RECENT("Recent"),
        HIGH_PROTEIN("High protein"),
        UNDER_500("Under 500 kcal")
    }

    private enum class Sort(val label: String) {
        RECENT("Most recent"),
        PROTEIN("Highest protein"),
        CALORIES("Lowest calories"),
        SCORE("Health score")
    }

    private var _binding: FragmentSavedMealsBinding? = null
    private val binding get() = _binding!!

    private lateinit var store: SavedMealsStore
    private lateinit var adapter: SavedMealAdapter

    private var allMeals: List<SavedMealUi> = emptyList()
    private var query = ""
    private var filter = Filter.ALL
    private var sort = Sort.RECENT
    private var loadJob: Job? = null
    private var logging = false

    private val numberFormat = NumberFormat.getIntegerInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedMealsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = SavedMealsStore(requireContext())

        adapter = SavedMealAdapter(
            onLog = { showQuickLog(it) },
            onMore = { item, anchor -> showMealMenu(item, anchor) },
            onFavorite = { toggleFavorite(it) }
        )
        binding.rvMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMeals.adapter = adapter

        buildChips()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadMeals()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // MainActivity shows/hides tab fragments: refresh when the tab becomes visible again.
        if (!hidden && _binding != null) loadMeals()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    private fun setupListeners() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                applyFilters()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
        binding.btnSort.setOnClickListener { showSortMenu(it) }
        binding.btnNewMeal.setOnClickListener { openCreateMeal() }
        binding.btnCreateFirst.setOnClickListener { openCreateMeal() }
        binding.btnRetry.setOnClickListener { loadMeals() }
        binding.btnResetFilters.setOnClickListener {
            filter = Filter.ALL
            binding.etSearch.setText("")
            styleChips()
            applyFilters()
        }
    }

    private fun openCreateMeal() {
        startActivity(Intent(requireContext(), CreateMealActivity::class.java))
    }

    private fun buildChips() {
        val row = binding.chipRow
        row.removeAllViews()
        Filter.values().forEach { f ->
            val chip = TextView(requireContext()).apply {
                text = f.label
                textSize = 12.5f
                gravity = Gravity.CENTER
                tag = f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    filter = f
                    styleChips()
                    applyFilters()
                }
            }
            row.addView(chip)
        }
        styleChips()
    }

    private fun styleChips() {
        val row = binding.chipRow
        for (i in 0 until row.childCount) {
            val chip = row.getChildAt(i) as TextView
            val active = chip.tag == filter
            chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip_inactive)
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (active) R.color.nav_active else R.color.text_slate_700)
            )
            chip.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------

    private fun loadMeals() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressLoading.visibility = View.VISIBLE
            binding.errorCard.visibility = View.GONE
            try {
                val res = ApiClient.api.getSavedMeals()
                if (res.isSuccessful) {
                    val hidden = store.hidden()
                    val favorites = store.favorites()
                    allMeals = res.body().orEmpty()
                        .filter { it.barcode != null && it.barcode !in hidden }
                        .map { dto ->
                            SavedMealUi(
                                meal = dto,
                                ingredientsText = SavedMealScorer.readableIngredients(dto.ingredients),
                                score = SavedMealScorer.score(dto.ingredients),
                                favorite = dto.barcode in favorites
                            )
                        }
                    applyFilters()
                } else if (res.code() != 401) {
                    showError("Couldn't load your meals (error ${res.code()}).")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Unable to reach the server: ${e.localizedMessage ?: "connection error"}")
            } finally {
                _binding?.progressLoading?.visibility = View.INVISIBLE
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.errorCard.visibility = View.VISIBLE
    }

    /** Search + chip filter + sort, then show the right state (list / no match / empty). */
    private fun applyFilters() {
        if (_binding == null) return
        val favorites = store.favorites()
        var list = allMeals.map { it.copy(favorite = it.meal.barcode in favorites) }

        val q = query.trim().lowercase()
        if (q.isNotEmpty()) {
            list = list.filter {
                (it.meal.name ?: "").lowercase().contains(q) || it.ingredientsText.lowercase().contains(q)
            }
        }

        list = when (filter) {
            Filter.ALL -> list
            Filter.FAVORITES -> list.filter { it.favorite }
            Filter.RECENT -> list.sortedByDescending { it.meal.lastLoggedAt ?: "" }.take(4)
            Filter.HIGH_PROTEIN -> list.filter { (it.meal.totals?.proteinG ?: 0.0) >= 30.0 }
            Filter.UNDER_500 -> list.filter { (it.meal.totals?.calories ?: 0.0) < 500.0 }
        }

        list = when (sort) {
            Sort.RECENT -> list.sortedByDescending { it.meal.lastLoggedAt ?: "" }
            Sort.PROTEIN -> list.sortedByDescending { it.meal.totals?.proteinG ?: 0.0 }
            Sort.CALORIES -> list.sortedBy { it.meal.totals?.calories ?: 0.0 }
            Sort.SCORE -> list.sortedByDescending { it.score ?: -1 }
        }

        val hasMeals = allMeals.isNotEmpty()
        binding.searchRow.visibility = if (hasMeals) View.VISIBLE else View.GONE
        binding.chipScroll.visibility = if (hasMeals) View.VISIBLE else View.GONE
        binding.btnSort.visibility = if (hasMeals) View.VISIBLE else View.INVISIBLE
        binding.btnNewMeal.visibility = if (hasMeals) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasMeals) View.GONE else View.VISIBLE
        binding.noMatchCard.visibility = if (hasMeals && list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMeals.visibility = if (hasMeals && list.isNotEmpty()) View.VISIBLE else View.GONE

        adapter.submitList(list)
    }

    // ---------------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------------

    private fun toggleFavorite(item: SavedMealUi) {
        val barcode = item.meal.barcode ?: return
        val now = store.toggleFavorite(barcode)
        Toast.makeText(
            requireContext(),
            if (now) "Added to Favorites" else "Removed from Favorites",
            Toast.LENGTH_SHORT
        ).show()
        applyFilters()
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        Sort.values().forEachIndexed { index, s ->
            val title = if (s == sort) "\u2713  ${s.label}" else s.label
            popup.menu.add(0, index, index, title)
        }
        popup.setOnMenuItemClickListener { menuItem ->
            sort = Sort.values()[menuItem.itemId]
            applyFilters()
            true
        }
        popup.show()
    }

    private fun showMealMenu(item: SavedMealUi, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_EDIT, 0, "Edit")
        popup.menu.add(0, MENU_REMOVE, 1, "Remove")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                MENU_EDIT -> editMeal(item.meal)
                MENU_REMOVE -> confirmRemove(item.meal)
            }
            true
        }
        popup.show()
    }

    private fun editMeal(meal: SavedMealDto) {
        val entryId = meal.lastEntryId
        if (entryId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "This meal can't be edited.", Toast.LENGTH_SHORT).show()
            return
        }
        val entry = LogEntryDto(
            id = entryId,
            barcode = meal.barcode,
            name = meal.name,
            quantityG = meal.quantityG,
            computed = meal.totals,
            loggedAt = meal.lastLoggedAt,
            imageUrl = meal.imageUrl,
            ingredients = meal.ingredients
        )
        startActivity(CreateMealActivity.editIntent(requireContext(), entry))
    }

    private fun confirmRemove(meal: SavedMealDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove \"${meal.name ?: "this meal"}\"?")
            .setMessage("It disappears from My Meals. Your logged history and totals are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                meal.barcode?.let { store.hide(it) }
                allMeals = allMeals.filter { it.meal.barcode != meal.barcode }
                applyFilters()
                Toast.makeText(requireContext(), "Removed from My Meals", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** Quick log: shows the meal's energy and macros, one button logs it to today. */
    private fun showQuickLog(item: SavedMealUi) {
        val meal = item.meal
        val sheet = SheetQuickLogBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheet.root)
        (sheet.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val totals = meal.totals ?: NutrientsDto()
        sheet.tvSheetName.text = meal.name?.takeIf { it.isNotBlank() } ?: "Meal"
        sheet.tvSheetKcal.text = "${numberFormat.format(totals.calories.roundToInt())} kcal"
        sheet.tvSheetWeight.text = "${meal.quantityG.roundToInt()} g total"
        sheet.tvSheetProtein.text = "${totals.proteinG.roundToInt()}g"
        sheet.tvSheetCarbs.text = "${totals.carbsG.roundToInt()}g"
        sheet.tvSheetFat.text = "${totals.fatG.roundToInt()}g"

        val url = meal.imageUrl
        if (!url.isNullOrBlank()) {
            sheet.ivSheetThumb.setPadding(0, 0, 0, 0)
            sheet.ivSheetThumb.load(url) {
                placeholder(R.drawable.ic_meal_bowl)
                error(R.drawable.ic_meal_bowl)
            }
        }

        sheet.btnSheetClose.setOnClickListener { dialog.dismiss() }
        sheet.btnLogToday.setOnClickListener { logMeal(meal, sheet, dialog) }
        dialog.show()
    }

    private fun logMeal(meal: SavedMealDto, sheet: SheetQuickLogBinding, dialog: BottomSheetDialog) {
        if (logging) return
        val barcode = meal.barcode ?: return
        val per100g = meal.per100g ?: return
        val quantity = if (meal.quantityG > 0.0) meal.quantityG else 100.0

        val request = LogFoodRequest(
            barcode = barcode,
            name = meal.name,
            ingredients = meal.ingredients,
            imageUrl = meal.imageUrl,
            per100g = per100g,
            quantityG = quantity
        )

        logging = true
        sheet.tvLogToday.text = "Logging…"
        sheet.tvSheetError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = ApiClient.api.logFood(request)
                if (res.isSuccessful) {
                    val kcal = (meal.totals?.calories ?: 0.0).roundToInt()
                    Toast.makeText(
                        requireContext(),
                        "Logged ${meal.name ?: "meal"} ($kcal kcal) to today",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    loadMeals()
                } else if (res.code() != 401) {
                    sheet.tvSheetError.text = "Couldn't log this meal (error ${res.code()})."
                    sheet.tvSheetError.visibility = View.VISIBLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sheet.tvSheetError.text = "Unable to reach the server: ${e.localizedMessage ?: "connection error"}"
                sheet.tvSheetError.visibility = View.VISIBLE
            } finally {
                logging = false
                sheet.tvLogToday.text = "Log to today"
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_REMOVE = 2
    }
}
