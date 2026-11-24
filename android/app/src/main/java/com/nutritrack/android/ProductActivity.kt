package com.nutritrack.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import com.nutritrack.android.data.food.HealthScoring
import com.nutritrack.android.data.food.HealthTier
import com.nutritrack.android.data.food.MealMath
import com.nutritrack.android.data.food.ScannedMealItem
import com.nutritrack.android.data.food.toFacts
import com.nutritrack.android.ui.HealthCardRenderer
import com.nutritrack.android.ui.InsightRenderer
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.viewmodel.ProductUiState
import com.nutritrack.android.viewmodel.ProductViewModel
import com.nutritrack.android.viewmodel.SaveState
import com.nutritrack.android.viewmodel.appViewModels
import com.nutritrack.android.data.openfoodfacts.ScannedProduct
import com.nutritrack.android.databinding.ActivityProductBinding
import com.nutritrack.android.databinding.ItemDetailRowBinding
import com.nutritrack.android.databinding.ItemInsightBinding
import com.nutritrack.android.databinding.ItemMacroRowBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

class ProductActivity : AppCompatActivity() {

    private data class UnitOption(val label: String, val grams: Double, val isGrams: Boolean = false)

    private lateinit var binding: ActivityProductBinding
    private lateinit var barcode: String
    private var forMeal = false
    private val productViewModel: ProductViewModel by appViewModels()

    private var product: ScannedProduct? = null
    private var ingredientsExpanded = false
    private var insightsExpanded = false
    private var units: List<UnitOption> = listOf(UnitOption("g", 1.0))
    private var updatingQty = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        barcode = intent.getStringExtra(EXTRA_BARCODE).orEmpty()
        forMeal = intent.getBooleanExtra(EXTRA_FOR_MEAL, false)
        if (barcode.isBlank()) {
            finish()
            return
        }

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

        binding.btnBack.setOnClickListener { finish() }
        binding.btnScanAnother.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { load() }

        setupStaticSections()
        setupDockControls()
        observeViewModel()
        if (productViewModel.state.value == null) load()
    }

    private fun load() {
        productViewModel.load(barcode)
    }

    private fun observeViewModel() {
        productViewModel.state.observe(this) { state ->
            when (state) {
                ProductUiState.Loading -> showLoading()
                is ProductUiState.Found -> showProduct(state.product)
                ProductUiState.NotFound -> showState(
                    title = "Product not found",
                    message = "Sorry, we couldn't find barcode $barcode in the Open Food Facts database.",
                    canRetry = false
                )
                is ProductUiState.Failure -> showState(
                    title = "Couldn't load product",
                    message = state.message,
                    canRetry = true
                )
            }
        }
        productViewModel.saveState.observe(this) { state ->
            when (state) {
                SaveState.Idle -> setSaving(false)
                SaveState.Saving -> {
                    setSaving(true)
                    binding.tvDockError.visibility = View.GONE
                }
                is SaveState.Saved -> {
                    setSaving(false)
                    Toast.makeText(this, "Logged ${totalGrams().roundToInt()}g to your diary", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
                is SaveState.Failed -> {
                    setSaving(false)
                    showDockError(state.message)
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingPanel.visibility = View.VISIBLE
        binding.statePanel.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.dock.visibility = View.GONE
    }

    private fun showState(title: String, message: String, canRetry: Boolean) {
        binding.loadingPanel.visibility = View.GONE
        binding.scrollContent.visibility = View.GONE
        binding.dock.visibility = View.GONE
        binding.statePanel.visibility = View.VISIBLE
        binding.tvStateTitle.text = title
        binding.tvStateMessage.text = message
        binding.btnRetry.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun showProduct(p: ScannedProduct) {
        product = p
        ingredientsExpanded = false

        binding.loadingPanel.visibility = View.GONE
        binding.statePanel.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        binding.dock.visibility = View.VISIBLE
        binding.scrollContent.scrollTo(0, 0)

        renderHero(p)
        renderIngredients()
        renderQuality(p)
        setupUnits(p)
    }

    private fun setupStaticSections() {
        binding.btnInsightsToggle.setOnClickListener {
            insightsExpanded = !insightsExpanded
            TransitionManager.beginDelayedTransition(binding.scrollContent.getChildAt(0) as ViewGroup)
            binding.insightsDrawer.visibility = if (insightsExpanded) View.VISIBLE else View.GONE
            binding.tvInsightsToggle.text = if (insightsExpanded) "Show less" else "View breakdown"
            binding.ivInsightsChevron.rotation = if (insightsExpanded) 180f else 0f
        }

        setupMacroRow(binding.rowProtein, "Protein", R.color.macro_protein, R.color.macro_protein_bg)
        setupMacroRow(binding.rowCarbs, "Carbs", R.color.macro_carbs, R.color.macro_carbs_bg)
        setupMacroRow(binding.rowFat, "Fat", R.color.macro_fat, R.color.macro_fat_bg)

        binding.tvIngredientsToggle.setOnClickListener {
            ingredientsExpanded = !ingredientsExpanded
            TransitionManager.beginDelayedTransition(binding.scrollContent.getChildAt(0) as ViewGroup)
            renderIngredients()
        }
    }

    private fun setupMacroRow(row: ItemMacroRowBinding, name: String, strongColor: Int, softColor: Int) {
        row.tvMacroName.text = name
        row.dotMacro.backgroundTintList = ColorStateList.valueOf(color(strongColor))
        row.tvMacroPct.setTextColor(color(strongColor))
        row.progressMacro.setIndicatorColor(color(strongColor))
        row.macroRowRoot.backgroundTintList = ColorStateList.valueOf(color(softColor))
    }

    private fun renderHero(p: ScannedProduct) {
        binding.tvName.text = p.name ?: "Unknown product"
        binding.tvBrand.visibility = if (p.brand == null) View.GONE else View.VISIBLE
        binding.tvBrand.text = p.brand
        binding.tvBarcode.text = "Barcode ${p.barcode}"
        binding.tvPackagePill.visibility = if (p.quantity == null) View.GONE else View.VISIBLE
        binding.tvPackagePill.text = p.quantity

        binding.naPhoto.tvNaMessage.text = "No photo for this product."
        if (p.imageUrl == null) {
            showPhotoUnavailable()
        } else {
            binding.naPhoto.root.visibility = View.GONE
            binding.ivProduct.visibility = View.VISIBLE
            binding.ivProduct.load(p.imageUrl) {
                crossfade(true)
                listener(onError = { _, _ -> showPhotoUnavailable() })
            }
        }
    }

    private fun showPhotoUnavailable() {
        binding.ivProduct.visibility = View.GONE
        binding.naPhoto.root.visibility = View.VISIBLE
    }

    private fun amountValues(p: ScannedProduct) = p.per100g.scaled(totalGrams() / 100)

    private fun amountLabel() = "${qtyText(totalGrams())}g"

    private fun renderFacts() {
        val p = product ?: return
        binding.tvBasisSubtitle.text = "Values for ${amountLabel()}"

        val v = amountValues(p)
        if (!v.hasMain) {
            binding.factsContent.visibility = View.GONE
            binding.naFacts.root.visibility = View.VISIBLE
            binding.naFacts.tvNaMessage.text = "This product has no nutrition information."
            return
        }
        binding.factsContent.visibility = View.VISIBLE
        binding.naFacts.root.visibility = View.GONE

        binding.tvHeroKcal.text = v.kcal?.roundToInt()?.toString() ?: "—"

        val proteinKcal = (v.proteinG ?: 0.0) * 4
        val carbsKcal = (v.carbsG ?: 0.0) * 4
        val fatKcal = (v.fatG ?: 0.0) * 9
        val total = proteinKcal + carbsKcal + fatKcal
        fun share(kcal: Double) = if (total > 0) kcal / total else 0.0

        binding.macroDonut.setShares(share(proteinKcal).toFloat(), share(carbsKcal).toFloat(), share(fatKcal).toFloat())
        bindMacroRow(binding.rowProtein, v.proteinG, share(proteinKcal))
        bindMacroRow(binding.rowCarbs, v.carbsG, share(carbsKcal))
        bindMacroRow(binding.rowFat, v.fatG, share(fatKcal))
    }

    private fun bindMacroRow(row: ItemMacroRowBinding, grams: Double?, share: Double) {
        row.tvMacroGrams.text = grams?.let { "${num1(it)}g" } ?: "—"
        val percent = (share * 100).roundToInt()
        row.tvMacroPct.text = if (grams == null) "" else "$percent%"
        row.progressMacro.setProgressCompat(if (grams == null) 0 else percent.coerceIn(0, 100), true)
    }

    private fun renderDetails() {
        val p = product ?: return
        val v = amountValues(p)
        binding.tvDetailBasis.text = "For ${amountLabel()}"

        val rows = listOf(
            "Saturated Fat" to v.saturatedFatG?.let { "${num1(it)}g" },
            "Dietary Fiber" to v.fiberG?.let { "${num1(it)}g" },
            "Total Sugars" to v.sugarsG?.let { "${num1(it)}g" },
            "Sodium" to v.sodiumMg?.let { "${it.roundToInt()}mg" },
            "Salt Equivalent" to v.saltG?.let { "${num2(it)}g" }
        )
        binding.detailRows.removeAllViews()
        if (rows.all { it.second == null }) {
            binding.detailRows.visibility = View.GONE
            binding.naDetails.root.visibility = View.VISIBLE
            binding.naDetails.tvNaMessage.text = "No detailed nutrient information for this product."
            return
        }
        binding.detailRows.visibility = View.VISIBLE
        binding.naDetails.root.visibility = View.GONE
        rows.forEachIndexed { index, (label, value) ->
            val row = ItemDetailRowBinding.inflate(layoutInflater, binding.detailRows, false)
            row.tvDetailLabel.text = label
            row.tvDetailValue.text = value ?: "—"
            row.tvDetailValue.setTextColor(color(if (value == null) R.color.text_slate_400 else R.color.text_slate_900))
            row.divider.visibility = if (index == rows.lastIndex) View.GONE else View.VISIBLE
            binding.detailRows.addView(row.root)
        }
    }

    private fun renderIngredients() {
        val p = product ?: return
        val list = p.ingredients

        if (list.isEmpty()) {
            binding.tvIngredients.visibility = View.GONE
            binding.tvIngredientCount.visibility = View.GONE
            binding.tvIngredientsToggle.visibility = View.GONE
            binding.naIngredients.root.visibility = View.VISIBLE
            binding.naIngredients.tvNaMessage.text = "The ingredient list isn't available for this product."
        } else {
            binding.naIngredients.root.visibility = View.GONE
            binding.tvIngredients.visibility = View.VISIBLE
            binding.tvIngredientCount.visibility = View.VISIBLE
            binding.tvIngredientCount.text = if (list.size == 1) "1 ingredient" else "${list.size} ingredients"

            val collapsible = list.size > COLLAPSED_INGREDIENTS
            binding.tvIngredients.text =
                if (collapsible && !ingredientsExpanded) list.take(COLLAPSED_INGREDIENTS).joinToString(", ") + "…"
                else list.joinToString(", ")
            binding.tvIngredientsToggle.visibility = if (collapsible) View.VISIBLE else View.GONE
            binding.tvIngredientsToggle.text = if (ingredientsExpanded) "Show less" else "Show full list"
        }

        if (p.allergens.isEmpty()) {
            binding.tvAllergenPill.visibility = View.GONE
        } else {
            binding.tvAllergenPill.visibility = View.VISIBLE
            binding.tvAllergenPill.text = "⚠️ Allergens: ${p.allergens.joinToString(", ")}"
        }
        binding.ingredientsFooter.visibility =
            if (binding.tvAllergenPill.visibility == View.VISIBLE || binding.tvIngredientsToggle.visibility == View.VISIBLE)
                View.VISIBLE else View.GONE
    }

    private fun renderQuality(p: ScannedProduct) {
        if (p.nutriScore == null && p.novaGroup == null) {
            binding.qualityRow.visibility = View.GONE
            binding.naQuality.root.visibility = View.VISIBLE
            binding.naQuality.tvNaMessage.text = "No Nutri-Score or processing level for this product."
            return
        }
        binding.qualityRow.visibility = View.VISIBLE
        binding.naQuality.root.visibility = View.GONE

        binding.nutriScoreRow.removeAllViews()
        if (p.nutriScore == null) {
            binding.nutriScoreRow.visibility = View.GONE
            binding.tvNutriScoreNa.visibility = View.VISIBLE
        } else {
            binding.nutriScoreRow.visibility = View.VISIBLE
            binding.tvNutriScoreNa.visibility = View.GONE
            val colors = mapOf("A" to "#038141", "B" to "#85BB2F", "C" to "#FECB02", "D" to "#EE8100", "E" to "#E63E11")
            for ((letter, hex) in colors) {
                val active = letter == p.nutriScore
                val tile = TextView(this).apply {
                    text = letter
                    gravity = android.view.Gravity.CENTER
                    textSize = if (active) 13f else 10f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(if (active) color(R.color.white) else color(R.color.text_slate_700))
                    setBackgroundResource(R.drawable.bg_rounded_square)
                    val base = android.graphics.Color.parseColor(hex)
                    backgroundTintList = ColorStateList.valueOf(if (active) base else (base and 0x00FFFFFF) or 0x59000000)
                }
                val lp = LinearLayout.LayoutParams(if (active) dp(26) else dp(18), if (active) dp(30) else dp(22))
                if (binding.nutriScoreRow.childCount > 0) lp.marginStart = dp(4)
                binding.nutriScoreRow.addView(tile, lp)
            }
        }

        if (p.novaGroup == null) {
            binding.novaRow.visibility = View.GONE
            binding.tvNovaNa.visibility = View.VISIBLE
        } else {
            binding.novaRow.visibility = View.VISIBLE
            binding.tvNovaNa.visibility = View.GONE
            val (hex, label) = when (p.novaGroup) {
                1 -> "#059669" to "Minimally processed"
                2 -> "#65A30D" to "Culinary ingredient"
                3 -> "#EE8100" to "Processed"
                else -> "#E63E11" to "Ultra-processed"
            }
            binding.tvNovaBox.text = p.novaGroup.toString()
            binding.tvNovaBox.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor(hex))
            binding.tvNovaLabel.text = label
        }
    }

    private fun setupDockControls() {
        binding.btnMinus.setOnClickListener { adjustQuantity(-1) }
        binding.btnPlus.setOnClickListener { adjustQuantity(1) }
        binding.etQty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!updatingQty) {
                    binding.tvDockError.visibility = View.GONE
                    updateDock()
                }
            }
        })
        binding.btnLog.setOnClickListener { logProduct() }
    }

    private fun setupUnits(p: ScannedProduct) {
        val options = mutableListOf(UnitOption("g", 1.0, isGrams = true))
        p.servingGrams?.let { options += UnitOption("serving (${qtyText(it)}g)", it) }
        options += UnitOption("tablespoon (~${qtyText(TABLESPOON_GRAMS)}g)", TABLESPOON_GRAMS)
        options += UnitOption("teaspoon (~${qtyText(TEASPOON_GRAMS)}g)", TEASPOON_GRAMS)
        p.packageGrams?.let { options += UnitOption("package (${qtyText(it)}g)", it) }
        units = options

        binding.spinnerUnit.adapter = ArrayAdapter(this, R.layout.item_unit, options.map { it.label }).also {
            it.setDropDownViewResource(R.layout.item_unit)
        }
        binding.spinnerUnit.setSelection(0, false)
        binding.spinnerUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                setQty(if (units.getOrNull(position)?.isGrams == true) 100.0 else 1.0)
                updateDock()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        setQty(100.0)
        binding.tvDockError.visibility = View.GONE
        setSaving(false)
        updateDock()
    }

    private fun selectedUnit(): UnitOption =
        units.getOrElse(binding.spinnerUnit.selectedItemPosition) { units.first() }

    private fun quantity(): Double =
        binding.etQty.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

    private fun totalGrams(): Double = quantity() * selectedUnit().grams

    private fun setQty(value: Double) {
        updatingQty = true
        binding.etQty.setText(qtyText(value))
        binding.etQty.setSelection(binding.etQty.text.length)
        updatingQty = false
    }

    private fun adjustQuantity(direction: Int) {
        hideKeyboard()
        val isGrams = selectedUnit().isGrams
        val next = if (isGrams) {
            maxOf(10.0, (quantity() + direction * 25).roundToInt().toDouble())
        } else {
            maxOf(0.5, ((quantity() + direction * 0.5) * 10).roundToInt() / 10.0)
        }
        setQty(next)
        binding.tvDockError.visibility = View.GONE
        updateDock()
    }

    private fun updateDock() {
        renderFacts()
        renderDetails()
        renderAnalysis()
    }

    private fun renderAnalysis() {
        val p = product ?: return
        val hasAmount = totalGrams() > 0.0
        val facts = (if (hasAmount) amountValues(p) else p.per100g).toFacts()
        healthCard.render(HealthScoring.score(facts))

        val ins = MealMath.productInsights(facts, p.novaGroup, p.nutriScore, if (hasAmount) amountLabel() else "100g")
        binding.tvInsightsSummary.text = ins.summary
        binding.goodContainer.removeAllViews()
        binding.watchContainer.removeAllViews()
        ins.good.forEach { InsightRenderer.add(layoutInflater, binding.goodContainer, it, isGood = true) }
        ins.watch.forEach { InsightRenderer.add(layoutInflater, binding.watchContainer, it, isGood = false) }

        binding.tvGoodHeading.visibility = if (ins.good.isEmpty()) View.GONE else View.VISIBLE
        binding.tvWatchHeading.visibility = if (ins.watch.isEmpty()) View.GONE else View.VISIBLE
        val hasBreakdown = ins.good.isNotEmpty() || ins.watch.isNotEmpty()
        binding.btnInsightsToggle.visibility = if (hasBreakdown) View.VISIBLE else View.GONE
        if (!hasBreakdown) {
            insightsExpanded = false
            binding.tvInsightsToggle.text = "View breakdown"
            binding.ivInsightsChevron.rotation = 0f
        }
        binding.insightsDrawer.visibility = if (hasBreakdown && insightsExpanded) View.VISIBLE else View.GONE
    }

    private fun logProduct() {
        val p = product ?: return
        val grams = totalGrams()
        if (grams <= 0.0) {
            showDockError("Enter a valid quantity to log.")
            return
        }
        if (grams > MAX_LOG_GRAMS) {
            showDockError("That's more than 10 kg. Please check the amount.")
            return
        }
        hideKeyboard()

        if (forMeal) {
            if (!p.per100g.hasMain) {
                showDockError("This product has no nutrition information, so it cannot be added to a meal.")
                return
            }
            setResult(RESULT_OK, ScannedMealItem.from(p, grams).toIntent())
            finish()
            return
        }

        val per100 = p.per100g
        val request = LogFoodRequest(
            barcode = p.barcode,
            name = p.name,
            ingredients = p.ingredients.joinToString(", ").ifEmpty { null },
            imageUrl = p.imageUrl,
            per100g = NutrientsDto(
                calories = per100.kcal ?: 0.0,
                proteinG = per100.proteinG ?: 0.0,
                carbsG = per100.carbsG ?: 0.0,
                fatG = per100.fatG ?: 0.0
            ),
            quantityG = grams
        )

        productViewModel.logProduct(request)
    }

    private fun setSaving(saving: Boolean) {
        binding.btnLog.isEnabled = !saving
        binding.btnLog.text = when {
            saving -> "Logging…"
            forMeal -> "Add to Meal"
            else -> "Log Product"
        }
    }

    private fun showDockError(message: String) {
        binding.tvDockError.text = message
        binding.tvDockError.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }

    private fun num1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun num2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun qtyText(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else num1(v)

    private fun color(res: Int) = ContextCompat.getColor(this, res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_BARCODE = "barcode"
        private const val EXTRA_FOR_MEAL = "for_meal"
        private const val COLLAPSED_INGREDIENTS = 6
        private const val MAX_LOG_GRAMS = 10_000.0
        private const val TABLESPOON_GRAMS = 15.0
        private const val TEASPOON_GRAMS = 5.0

        fun intent(context: Context, barcode: String, forMeal: Boolean = false): Intent =
            Intent(context, ProductActivity::class.java)
                .putExtra(EXTRA_BARCODE, barcode)
                .putExtra(EXTRA_FOR_MEAL, forMeal)
    }
}
