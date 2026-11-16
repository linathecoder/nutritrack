package com.nutritrack.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.food.Facts
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.food.FoodRepository
import com.nutritrack.android.data.food.HealthTier
import com.nutritrack.android.data.food.Insight
import com.nutritrack.android.data.food.MealLine
import com.nutritrack.android.data.food.MealMath
import com.nutritrack.android.data.food.Nutrition
import com.nutritrack.android.data.food.ScannedMealItem
import com.nutritrack.android.data.openfoodfacts.ProductRepository
import com.nutritrack.android.data.openfoodfacts.ProductResult
import com.nutritrack.android.data.food.shortName
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.model.LogEntryDto
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.databinding.ActivityCreateMealBinding
import com.nutritrack.android.databinding.ItemDetailRowBinding
import com.nutritrack.android.databinding.ItemInsightBinding
import com.nutritrack.android.databinding.ItemMacroRowBinding
import com.nutritrack.android.databinding.ItemMealIngredientBinding
import com.nutritrack.android.ui.FoodResultAdapter
import com.nutritrack.android.ui.HealthCardRenderer
import com.nutritrack.android.ui.InsightRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class CreateMealActivity : AppCompatActivity() {

    private class CardHolder(val binding: ItemMealIngredientBinding, var suppress: Boolean = false)

    private lateinit var binding: ActivityCreateMealBinding
    private lateinit var resultsAdapter: FoodResultAdapter

    private val lines = mutableListOf<MealLine>()
    private val cards = mutableMapOf<Long, CardHolder>()
    private var nextUid = 1L
    private var searchJob: Job? = null
    private var insightsExpanded = false
    private var detailsExpanded = false
    private var saving = false
    private var editingEntryId: String? = null
    private var editingBarcode: String? = null
    private var editingEntryName: String? = null
    private var editingIngredients: String? = null
    private var editingQuantityG: Double = 0.0

    private data class SavedIngredient(
        val foodId: String,
        val qty: Double,
        val unitIndex: Int = 0,
        val food: FoodItem? = null
    )

    private val healthCard by lazy {
        HealthCardRenderer(
            this, binding.healthGauge, binding.tvHealthScore, binding.tvHealthRating, binding.tvHealthExplanation,
            mapOf(
                HealthTier.POOR to binding.tierBarPoor, HealthTier.MODERATE to binding.tierBarModerate,
                HealthTier.GOOD to binding.tierBarGood, HealthTier.EXCELLENT to binding.tierBarExcellent
            ),
            mapOf(
                HealthTier.POOR to binding.tierLabelPoor, HealthTier.MODERATE to binding.tierLabelModerate,
                HealthTier.GOOD to binding.tierLabelGood, HealthTier.EXCELLENT to binding.tierLabelExcellent
            )
        )
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                ScannedMealItem.fromIntent(result.data)?.let { addScannedProduct(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            v.setPadding(bars.left, bars.top, bars.right, 0)
            binding.dock.setPadding(
                binding.dock.paddingLeft, binding.dock.paddingTop,
                binding.dock.paddingRight, bottom + dp(12)
            )
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        binding.btnBack.setOnClickListener { handleBack() }

        setupStaticSections()
        setupMealName()
        setupSearch()
        refreshAll()
        scheduleSearch("", immediate = true)
        readEditIntent()
        if (!editingEntryId.isNullOrBlank()) loadMealForEditing()
    }

    private fun readEditIntent() {
        editingEntryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        editingBarcode = intent.getStringExtra(EXTRA_ENTRY_BARCODE)
        editingEntryName = intent.getStringExtra(EXTRA_ENTRY_NAME)
        editingIngredients = intent.getStringExtra(EXTRA_ENTRY_INGREDIENTS)
        editingQuantityG = intent.getDoubleExtra(EXTRA_ENTRY_QUANTITY, 0.0)
        if (!editingEntryId.isNullOrBlank()) {
            binding.btnSaveMeal.text = "Save Changes"
            binding.btnSaveTop.text = "Save Changes"
            binding.etMealName.setText(editingEntryName.orEmpty())
        }
    }

    private fun loadMealForEditing() {
        lifecycleScope.launch {
            try {
                FoodRepository.ensureLoaded(applicationContext)
                val raw = editingIngredients
                val parsed = raw?.let { runCatching {
                    val type = object : TypeToken<List<SavedIngredient>>() {}.type
                    Gson().fromJson<List<SavedIngredient>>(it, type)
                }.getOrNull() }
                if (!parsed.isNullOrEmpty()) {
                    parsed.forEach { saved ->
                        var food = FoodRepository.findById(saved.foodId) ?: saved.food
                        if (food == null && saved.foodId.startsWith("scan-")) {
                            val barcode = saved.foodId.removePrefix("scan-")
                            food = when (val result = ProductRepository.lookup(barcode)) {
                                is ProductResult.Found -> ScannedMealItem.from(result.product, saved.qty).toFoodItem()
                                else -> null
                            }
                        }
                        if (food != null) {
                            val units = MealMath.unitsFor(food)
                            val index = saved.unitIndex.coerceIn(0, units.lastIndex)
                            val line = MealLine(nextUid++, food, saved.qty, units, index)
                            lines += line
                            addCard(line)
                        }
                    }
                } else if (!raw.isNullOrBlank()) {
                    val pattern = Regex("(.+?)\\s*\\(([0-9]+(?:[.,][0-9]+)?)\\s*g\\)")
                    pattern.findAll(raw).forEach { match ->
                        val label = match.groupValues[1].trim()
                        val grams = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return@forEach
                        val food = FoodRepository.search(label, limit = 10).firstOrNull() ?: return@forEach
                        val units = MealMath.unitsFor(food)
                        val line = MealLine(nextUid++, food, grams, units)
                        lines += line
                        addCard(line)
                    }
                }
                resultsAdapter.setAdded(lines.map { it.food.id }.toSet())
                refreshAll()
                if (lines.isEmpty()) {
                    showDockError("The saved ingredient details aren't available for this meal. You can still add ingredients, but its original ingredients couldn't be restored.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showDockError("Couldn't load this meal's ingredients: ${e.localizedMessage ?: "unknown error"}")
            }
        }
    }

    private fun setupStaticSections() {
        setupMacroRow(binding.rowProtein, "Protein", R.color.macro_protein, R.color.macro_protein_bg)
        setupMacroRow(binding.rowCarbs, "Carbs", R.color.macro_carbs, R.color.macro_carbs_bg)
        setupMacroRow(binding.rowFat, "Fat", R.color.macro_fat, R.color.macro_fat_bg)

        binding.btnDetailsToggle.setOnClickListener {
            detailsExpanded = !detailsExpanded
            TransitionManager.beginDelayedTransition(binding.contentLayout)
            binding.detailRows.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            binding.tvDetailsToggle.text = if (detailsExpanded) "Hide" else "Show"
            binding.ivDetailsChevron.rotation = if (detailsExpanded) 180f else 0f
        }
        binding.btnInsightsToggle.setOnClickListener {
            insightsExpanded = !insightsExpanded
            TransitionManager.beginDelayedTransition(binding.contentLayout)
            binding.insightsDrawer.visibility = if (insightsExpanded) View.VISIBLE else View.GONE
            binding.tvInsightsToggle.text = if (insightsExpanded) "Show less" else "View breakdown"
            binding.ivInsightsChevron.rotation = if (insightsExpanded) 180f else 0f
        }

        binding.btnScanProduct.setOnClickListener {
            hideKeyboard()
            scanLauncher.launch(ScannerActivity.intentForMeal(this))
        }
        binding.btnSaveTop.setOnClickListener { saveMeal() }
        binding.btnSaveMeal.setOnClickListener { saveMeal() }
    }

    private fun setupMacroRow(row: ItemMacroRowBinding, name: String, strongColor: Int, softColor: Int) {
        row.tvMacroName.text = name
        row.dotMacro.backgroundTintList = ColorStateList.valueOf(color(strongColor))
        row.tvMacroPct.setTextColor(color(strongColor))
        row.progressMacro.setIndicatorColor(color(strongColor))
        row.macroRowRoot.backgroundTintList = ColorStateList.valueOf(color(softColor))
    }

    private fun setupMealName() {
        binding.etMealName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearName.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.tvDockError.visibility = View.GONE
                updateSaveEnabled()
            }
        })
        binding.btnClearName.setOnClickListener {
            binding.etMealName.setText("")
            binding.etMealName.requestFocus()
        }
        binding.etMealName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard()
            false
        }
    }

    private fun setupSearch() {
        resultsAdapter = FoodResultAdapter { food -> addFood(food) }
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = resultsAdapter
        binding.rvResults.itemAnimator = null

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(s?.toString().orEmpty())
            }
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.etSearch.requestFocus()
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard()
            true
        }
    }

    private fun scheduleSearch(query: String, immediate: Boolean = false) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (!immediate) delay(120)
            if (!FoodRepository.isLoaded) showSearchState("Loading ingredients…")
            try {
                FoodRepository.ensureLoaded(applicationContext)
                val results = FoodRepository.search(query)
                resultsAdapter.submitList(results) { binding.rvResults.scrollToPosition(0) }
                if (results.isEmpty()) {
                    showSearchState("No matching ingredients found\nTry searching chicken, rice, eggs, lentils or salmon")
                } else {
                    binding.tvSearchState.visibility = View.GONE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                resultsAdapter.submitList(emptyList())
                showSearchState("Couldn't load the ingredient list.")
            }
        }
    }

    private fun showSearchState(message: String) {
        binding.tvSearchState.text = message
        binding.tvSearchState.visibility = View.VISIBLE
    }

    private fun addFood(food: FoodItem) {
        val existing = lines.firstOrNull { it.food.id == food.id }
        if (existing != null) {
            existing.qty = MealMath.nextQty(existing.qty, existing.unit, +1)
            syncCard(existing, resetQtyText = true)
            Toast.makeText(this, "Increased ${food.shortName()} to ${qtyText(existing.qty)} ${unitShort(existing)}", Toast.LENGTH_SHORT).show()
        } else {
            val units = MealMath.unitsFor(food)
            val line = MealLine(nextUid++, food, MealMath.defaultQty(food, units.first()), units)
            lines += line
            addCard(line)
            Toast.makeText(this, "Added ${food.shortName()}", Toast.LENGTH_SHORT).show()
        }
        resultsAdapter.setAdded(lines.map { it.food.id }.toSet())
        binding.tvDockError.visibility = View.GONE
        refreshAll()
    }

    private fun addScannedProduct(item: ScannedMealItem) {
        if (item.grams <= 0.0) return
        val food = item.toFoodItem()
        val existing = lines.firstOrNull { it.food.id == food.id }
        val line: MealLine
        if (existing != null) {
            existing.qty = ((existing.qty + item.grams / existing.unit.gramsPerUnit) * 10).roundToInt() / 10.0
            syncCard(existing, resetQtyText = true)
            line = existing
        } else {
            val units = MealMath.unitsFor(food)
            line = MealLine(nextUid++, food, item.grams, units)
            lines += line
            addCard(line)
        }
        binding.tvDockError.visibility = View.GONE
        refreshAll()
        Toast.makeText(this, "Added ${food.shortName()} to your meal", Toast.LENGTH_SHORT).show()

        binding.scrollContent.post {
            val card = cards[line.uid]?.binding?.root ?: return@post
            binding.scrollContent.smoothScrollTo(0, binding.ingredientsContainer.top + card.top - dp(8))
        }
    }

    private fun removeLine(line: MealLine) {
        cards.remove(line.uid)?.let { binding.ingredientsContainer.removeView(it.binding.root) }
        lines.remove(line)
        resultsAdapter.setAdded(lines.map { it.food.id }.toSet())
        Toast.makeText(this, "Removed ${line.food.shortName()}", Toast.LENGTH_SHORT).show()
        refreshAll()
    }

    private fun addCard(line: MealLine) {
        val b = ItemMealIngredientBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        val holder = CardHolder(b)
        cards[line.uid] = holder

        b.tvIngEmoji.text = line.food.icon
        b.tvIngEmoji.backgroundTintList = ColorStateList.valueOf(FoodRepository.tintFor(line.food.category))
        b.tvIngName.text = line.food.name

        b.spinnerIngUnit.adapter = ArrayAdapter(this, R.layout.item_unit_compact, line.units.map { it.label }).also {
            it.setDropDownViewResource(R.layout.item_unit)
        }
        b.spinnerIngUnit.setSelection(line.unitIndex, false)
        b.spinnerIngUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == line.unitIndex) return
                line.unitIndex = position
                line.qty = MealMath.defaultQty(line.food, line.unit)
                syncCard(line, resetQtyText = true)
                refreshAll()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        b.btnIngMinus.setOnClickListener { stepQty(line, -1) }
        b.btnIngPlus.setOnClickListener { stepQty(line, +1) }
        b.btnIngRemove.setOnClickListener { removeLine(line) }
        b.btnIngEdit.setOnClickListener {
            b.etIngQty.requestFocus()
            b.etIngQty.selectAll()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(b.etIngQty, InputMethodManager.SHOW_IMPLICIT)
        }
        b.etIngQty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (holder.suppress) return
                line.qty = s?.toString()?.replace(',', '.')?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                binding.tvDockError.visibility = View.GONE
                syncCard(line, resetQtyText = false)
                refreshAll()
            }
        })

        binding.ingredientsContainer.addView(b.root)
        syncCard(line, resetQtyText = true)
    }

    private fun stepQty(line: MealLine, direction: Int) {
        hideKeyboard()
        line.qty = MealMath.nextQty(line.qty, line.unit, direction)
        syncCard(line, resetQtyText = true)
        binding.tvDockError.visibility = View.GONE
        refreshAll()
    }

    private fun syncCard(line: MealLine, resetQtyText: Boolean) {
        val holder = cards[line.uid] ?: return
        val b = holder.binding
        if (resetQtyText) {
            holder.suppress = true
            b.etIngQty.setText(qtyText(line.qty))
            holder.suppress = false
        }
        val n = line.nutrition
        b.tvIngMacros.text = "${n.kcal.roundToInt()} kcal  •  P ${num1(n.protein)}g · C ${num1(n.carbs)}g · F ${num1(n.fat)}g"
        if (line.unit.isMass) {
            b.tvIngGrams.visibility = View.GONE
        } else {
            b.tvIngGrams.visibility = View.VISIBLE
            b.tvIngGrams.text = "≈ ${line.grams.roundToInt()} g"
        }
    }

    private fun refreshAll() {
        val totals = MealMath.total(lines)
        val facts = MealMath.facts(lines)
        renderSummary(totals)
        healthCard.render(MealMath.healthScore(facts, lines.size))
        renderInsights(totals, facts)
        binding.emptyIngredients.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
        binding.tvEmptyHint.text = "Search above and tap \"+ Add\", or scan a packaged product."
        updateSaveEnabled()
    }

    private fun renderSummary(t: Nutrition) {
        val count = lines.size
        val weight = t.grams.roundToInt()
        binding.tvIngredientCount.text = if (count == 1) "1 ingredient" else "$count ingredients"
        binding.tvIngredientsBadge.text = count.toString()
        binding.tvTotalWeight.text = "Total weight: ${weight}g"
        binding.tvNutritionSubtitle.text = "Combined values for entire meal (${weight}g)"
        binding.tvHeroKcal.text = t.kcal.roundToInt().toString()

        val pK = t.protein * 4
        val cK = t.carbs * 4
        val fK = t.fat * 9
        val sum = pK + cK + fK
        fun share(k: Double) = if (sum > 0) k / sum else 0.0
        val pPct = if (sum > 0) (share(pK) * 100).roundToInt() else 0
        val cPct = if (sum > 0) (share(cK) * 100).roundToInt() else 0
        val fPct = if (sum > 0) maxOf(0, 100 - pPct - cPct) else 0

        binding.macroDonut.setShares(share(pK).toFloat(), share(cK).toFloat(), share(fK).toFloat())
        bindMacroRow(binding.rowProtein, t.protein, pPct)
        bindMacroRow(binding.rowCarbs, t.carbs, cPct)
        bindMacroRow(binding.rowFat, t.fat, fPct)

        binding.tvDockKcal.text = t.kcal.roundToInt().toString()
        binding.tvDockProtein.text = "P ${num1(t.protein)}g"
        binding.tvDockCarbs.text = "C ${num1(t.carbs)}g"
        binding.tvDockFat.text = "F ${num1(t.fat)}g"

        val rows = listOf(
            "Dietary Fiber" to "${num1(t.fiber)}g",
            "Total Sugar" to "${num1(t.sugar)}g",
            "Saturated Fat" to "${num1(t.satFat)}g",
            "Sodium" to "${t.sodiumMg.roundToInt()}mg"
        )
        binding.detailRows.removeAllViews()
        rows.forEachIndexed { index, (label, value) ->
            val row = ItemDetailRowBinding.inflate(layoutInflater, binding.detailRows, false)
            row.tvDetailLabel.text = label
            row.tvDetailValue.text = value
            row.divider.visibility = if (index == rows.lastIndex) View.GONE else View.VISIBLE
            binding.detailRows.addView(row.root)
        }
    }

    private fun bindMacroRow(row: ItemMacroRowBinding, grams: Double, percent: Int) {
        row.tvMacroGrams.text = "${num1(grams)}g"
        row.tvMacroPct.text = "$percent%"
        row.progressMacro.setProgressCompat(percent.coerceIn(0, 100), true)
    }

    private fun renderInsights(t: Nutrition, facts: Facts) {
        val ins = MealMath.insights(lines, t, facts)
        binding.tvInsightsSummary.text = ins.summary
        binding.goodContainer.removeAllViews()
        binding.watchContainer.removeAllViews()

        if (!ins.hasData) {
            binding.btnInsightsToggle.visibility = View.GONE
            binding.insightsDrawer.visibility = View.GONE
            insightsExpanded = false
            binding.tvInsightsToggle.text = "View breakdown"
            binding.ivInsightsChevron.rotation = 0f
            return
        }
        binding.btnInsightsToggle.visibility = View.VISIBLE
        binding.insightsDrawer.visibility = if (insightsExpanded) View.VISIBLE else View.GONE
        ins.good.forEach { InsightRenderer.add(layoutInflater, binding.goodContainer, it, isGood = true) }
        ins.watch.forEach { InsightRenderer.add(layoutInflater, binding.watchContainer, it, isGood = false) }
    }

    private fun mealName() = binding.etMealName.text?.toString()?.trim().orEmpty()

    private fun updateSaveEnabled() {
        val ok = mealName().isNotEmpty() && lines.isNotEmpty() && !saving
        binding.btnSaveTop.isEnabled = ok
        binding.btnSaveMeal.isEnabled = ok
    }

    private fun saveMeal() {
        if (saving) return
        val name = mealName()
        if (name.isEmpty() || lines.isEmpty()) return
        hideKeyboard()

        lines.firstOrNull { it.grams <= 0.0 }?.let {
            showDockError("Set an amount for ${it.food.shortName()} (or remove it).")
            return
        }
        val totals = MealMath.total(lines)
        if (totals.grams > MAX_MEAL_GRAMS) {
            showDockError("That's more than 10 kg. Please check the amounts.")
            return
        }

        val per100 = 100.0 / totals.grams
        val request = LogFoodRequest(
            barcode = editingBarcode ?: "meal-${UUID.randomUUID()}",
            name = name,
            ingredients = Gson().toJson(lines.map {
                SavedIngredient(
                    foodId = it.food.id,
                    qty = it.qty,
                    unitIndex = it.unitIndex,
                    food = it.food
                )
            }),
            imageUrl = null,
            per100g = NutrientsDto(
                calories = totals.kcal * per100,
                proteinG = totals.protein * per100,
                carbsG = totals.carbs * per100,
                fatG = totals.fat * per100
            ),
            quantityG = totals.grams
        )

        setSaving(true)
        binding.tvDockError.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val response = if (!editingEntryId.isNullOrBlank()) {
                    ApiClient.api.updateLog(editingEntryId!!, request)
                } else {
                    ApiClient.api.logFood(request)
                }
                if (response.isSuccessful) {
                    val savedId = editingEntryId ?: response.body()?.id
                    if (!savedId.isNullOrBlank()) {
                        getSharedPreferences(MEAL_PREFS, MODE_PRIVATE).edit()
                            .putString("ingredients_$savedId", request.ingredients).apply()
                    }
                    val action = if (editingEntryId.isNullOrBlank()) "Saved" else "Updated"
                    Toast.makeText(this@CreateMealActivity, "$action \"$name\" (${totals.kcal.roundToInt()} kcal)", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@CreateMealActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                } else if (response.code() != 401) {
                    showDockError(
                        parseErrorMessage(response.errorBody()?.string())
                            ?: "Couldn't save this meal (error ${response.code()})."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showDockError("Unable to reach the server: ${e.localizedMessage ?: "connection error"}")
            } finally {
                setSaving(false)
            }
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        binding.btnSaveMeal.text = if (value) "Saving…" else if (editingEntryId.isNullOrBlank()) "Save Meal" else "Save Changes"
        updateSaveEnabled()
    }

    private fun showDockError(message: String) {
        binding.tvDockError.text = message
        binding.tvDockError.visibility = View.VISIBLE
    }

    private fun parseErrorMessage(json: String?): String? {
        if (json.isNullOrEmpty()) return null
        return try {
            JSONObject(json).optString("message", "").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleBack() {
        if (lines.isEmpty() && mealName().isEmpty()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard this meal?")
            .setMessage("Your ingredients and amounts haven't been saved.")
            .setNegativeButton("Keep editing", null)
            .setPositiveButton("Discard") { _, _ -> finish() }
            .show()
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun unitShort(line: MealLine) = line.unit.label.substringBefore(" (")
    private fun num1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun qtyText(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else num1(v)

    private fun color(res: Int) = ContextCompat.getColor(this, res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_ENTRY_ID = "entry_id"
        private const val EXTRA_ENTRY_BARCODE = "entry_barcode"
        private const val EXTRA_ENTRY_NAME = "entry_name"
        private const val EXTRA_ENTRY_INGREDIENTS = "entry_ingredients"
        private const val EXTRA_ENTRY_QUANTITY = "entry_quantity"
        private const val MEAL_PREFS = "saved_meal_ingredients"
        private const val MAX_MEAL_GRAMS = 10_000.0

        fun editIntent(context: Context, entry: LogEntryDto): Intent =
            Intent(context, CreateMealActivity::class.java)
                .putExtra(EXTRA_ENTRY_ID, entry.id)
                .putExtra(EXTRA_ENTRY_BARCODE, entry.barcode)
                .putExtra(EXTRA_ENTRY_NAME, entry.name)
                .putExtra(EXTRA_ENTRY_INGREDIENTS, entry.ingredients)
                .putExtra(EXTRA_ENTRY_QUANTITY, entry.quantityG)
    }
}
