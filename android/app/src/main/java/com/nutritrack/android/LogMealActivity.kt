package com.nutritrack.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.cache.NutriTrackLocalCache
import com.nutritrack.android.data.food.FoodItem
import com.nutritrack.android.data.food.FoodRepository
import com.nutritrack.android.data.model.LogFoodRequest
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.openfoodfacts.ProductRepository
import com.nutritrack.android.data.openfoodfacts.ProductResult
import com.nutritrack.android.databinding.ActivityLogMealBinding
import com.nutritrack.android.databinding.DialogEditQuantityBinding
import com.nutritrack.android.databinding.DialogManualProductEntryBinding
import com.nutritrack.android.ui.FoodResultAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class LogMealActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogMealBinding
    private lateinit var adapter: FoodResultAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLogMealBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.logMealRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupRecyclerView()
        setupSearch()
        setupButtons()

        lifecycleScope.launch {
            FoodRepository.ensureLoaded(applicationContext)
            scheduleSearch("", immediate = true)
        }
    }

    private fun setupRecyclerView() {
        adapter = FoodResultAdapter { food ->
            showQuantityDialog(food)
        }
        binding.rvFoodResults.layoutManager = LinearLayoutManager(this)
        binding.rvFoodResults.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        binding.btnEnterBarcodeDirect.setOnClickListener {
            showManualBarcodeLookupDialog()
        }

        binding.btnNotFoundManualBarcode.setOnClickListener {
            showManualBarcodeLookupDialog()
        }

        binding.btnNotFoundCreateMeal.setOnClickListener {
            startActivity(Intent(this, CreateMealActivity::class.java))
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            scheduleSearch("", immediate = true)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(query, immediate = false)
            }
        })
    }

    private fun scheduleSearch(query: String, immediate: Boolean) {
        searchJob?.cancel()

        // Check local cache first (Task 6 Room/SQLite cache)
        val cache = NutriTrackLocalCache.getInstance(this@LogMealActivity)
        val cachedResults = cache.getCachedFoodSearch(query)
        if (!cachedResults.isNullOrEmpty()) {
            adapter.submitList(cachedResults)
            binding.rvFoodResults.visibility = View.VISIBLE
            binding.notFoundLayout.visibility = View.GONE
            binding.tvSectionTitle.text = if (query.isBlank()) "Popular foods" else "Search results (${cachedResults.size})"
        }

        searchJob = lifecycleScope.launch {
            if (!immediate) delay(250)
            binding.progressSearch.visibility = View.VISIBLE
            try {
                // Call FoodRepository.search. Note: Backend currently lacks GET /api/foods/search?query=...
                val results = FoodRepository.search(query)
                cache.cacheFoodSearch(query, results) // Save to cache (Task 6)
                adapter.submitList(results)

                if (results.isEmpty() && query.isNotBlank()) {
                    binding.rvFoodResults.visibility = View.GONE
                    binding.notFoundLayout.visibility = View.VISIBLE
                    binding.tvNotFoundTitle.text = "No results for \"$query\""
                    binding.tvSectionTitle.text = "Search results (0)"
                } else {
                    binding.rvFoodResults.visibility = View.VISIBLE
                    binding.notFoundLayout.visibility = View.GONE
                    binding.tvSectionTitle.text = if (query.isBlank()) "Popular foods" else "Search results (${results.size})"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cachedResults == null) {
                    Toast.makeText(this@LogMealActivity, "Search error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                binding.progressSearch.visibility = View.INVISIBLE
            }
        }
    }

    private fun showQuantityDialog(food: FoodItem) {
        val dialogBinding = DialogEditQuantityBinding.inflate(layoutInflater)
        dialogBinding.tvDialogFoodName.text = "${food.icon} ${food.name}"
        val per100 = food.per100g
        dialogBinding.tvDialogOriginalMeta.text =
            "Per 100g: ${per100.kcal.roundToInt()} kcal • ${per100.protein.roundToInt()}g protein • ${per100.carbs.roundToInt()}g carbs • ${per100.fat.roundToInt()}g fat"

        var currentGrams = 100.0
        dialogBinding.etQuantity.setText("100")
        dialogBinding.etQuantity.setSelection(3)

        fun updatePreview(grams: Double) {
            val scale = grams / 100.0
            dialogBinding.tvScaledCalories.text = "${(per100.kcal * scale).roundToInt()} kcal"
            dialogBinding.tvScaledProtein.text = String.format(Locale.US, "%.1fg", per100.protein * scale)
            dialogBinding.tvScaledCarbs.text = String.format(Locale.US, "%.1fg", per100.carbs * scale)
            dialogBinding.tvScaledFat.text = String.format(Locale.US, "%.1fg", per100.fat * scale)
        }

        updatePreview(100.0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add to Today's Log")
            .setView(dialogBinding.root)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val addBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            dialogBinding.etQuantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val raw = s?.toString()?.replace(',', '.')?.toDoubleOrNull()
                    when {
                        raw == null || raw <= 0.0 -> {
                            // BR-07: quantity must be > 0
                            dialogBinding.tilQuantity.error = "Quantity must be greater than 0g"
                            addBtn.isEnabled = false
                        }
                        raw > 10000.0 -> {
                            dialogBinding.tilQuantity.error = "Quantity cannot exceed 10,000g"
                            addBtn.isEnabled = false
                        }
                        else -> {
                            dialogBinding.tilQuantity.error = null
                            addBtn.isEnabled = true
                            currentGrams = raw
                            updatePreview(raw) // BR-02: values scale proportionally
                        }
                    }
                }
            })

            addBtn.setOnClickListener {
                if (currentGrams <= 0.0) {
                    dialogBinding.tilQuantity.error = "Quantity must be greater than 0g"
                    return@setOnClickListener
                }
                dialog.dismiss()
                logFoodItem(food, currentGrams)
            }
        }

        dialog.show()
    }

    private fun logFoodItem(food: FoodItem, grams: Double) {
        hideKeyboard()
        binding.progressSearch.visibility = View.VISIBLE

        val per100 = food.per100g
        val req = LogFoodRequest(
            barcode = if (food.id.isNotBlank()) food.id else "food-${UUID.randomUUID()}",
            name = food.name,
            ingredients = food.category,
            imageUrl = null,
            per100g = NutrientsDto(
                calories = per100.kcal,
                proteinG = per100.protein,
                carbsG = per100.carbs,
                fatG = per100.fat
            ),
            quantityG = grams
        )

        lifecycleScope.launch {
            try {
                val res = ApiClient.api.logFood(req)
                if (res.isSuccessful) {
                    Toast.makeText(
                        this@LogMealActivity,
                        "Added ${food.name} (${grams.roundToInt()}g) to today's log",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@LogMealActivity,
                        "Failed to log food (error ${res.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(
                        this@LogMealActivity,
                        "Network error: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                binding.progressSearch.visibility = View.INVISIBLE
            }
        }
    }

    // Manual barcode flow (BR-08)
    private fun showManualBarcodeLookupDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_manual_barcode, null)
        val til = content.findViewById<TextInputLayout>(R.id.tilBarcode)
        val input = content.findViewById<TextInputEditText>(R.id.etBarcode)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Enter Barcode")
            .setView(content)
            .setPositiveButton("Look up", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text?.toString()?.trim().orEmpty()
                if (code.length in 8..14 && code.all { it.isDigit() }) {
                    til.error = null
                    dialog.dismiss()
                    lookupBarcode(code)
                } else {
                    til.error = "Enter 8–14 barcode digits"
                }
            }
        }
        dialog.show()
    }

    private fun lookupBarcode(barcode: String) {
        binding.progressSearch.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                when (val result = ProductRepository.lookup(barcode)) {
                    is ProductResult.Found -> {
                        startActivity(ProductActivity.intent(this@LogMealActivity, barcode))
                        finish()
                    }
                    ProductResult.NotFound -> {
                        // Product not found: show manual barcode product form (BR-08)
                        showManualProductForm(barcode)
                    }
                    is ProductResult.Failure -> {
                        Toast.makeText(this@LogMealActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(this@LogMealActivity, "Lookup error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                binding.progressSearch.visibility = View.INVISIBLE
            }
        }
    }

    // BR-08: Manual barcode product entry form when not found in database
    private fun showManualProductForm(barcode: String) {
        val dialogBinding = DialogManualProductEntryBinding.inflate(layoutInflater)
        dialogBinding.tvManualBarcodeHeading.text = "Product Not Found (Barcode: $barcode)"

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Manual Product Entry")
            .setView(dialogBinding.root)
            .setPositiveButton("Log Product", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.etManualName.text?.toString()?.trim().orEmpty()
                val calories = dialogBinding.etManualCalories.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val protein = dialogBinding.etManualProtein.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val carbs = dialogBinding.etManualCarbs.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val fat = dialogBinding.etManualFat.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val quantity = dialogBinding.etManualQuantity.text?.toString()?.replace(',', '.')?.toDoubleOrNull()

                if (name.isBlank()) {
                    dialogBinding.tilManualName.error = "Please enter product name"
                    return@setOnClickListener
                } else {
                    dialogBinding.tilManualName.error = null
                }

                if (quantity == null || quantity <= 0.0) {
                    dialogBinding.tilManualQuantity.error = "Quantity must be greater than 0g"
                    return@setOnClickListener
                } else {
                    dialogBinding.tilManualQuantity.error = null
                }

                dialog.dismiss()

                val req = LogFoodRequest(
                    barcode = barcode,
                    name = name,
                    ingredients = "Manual barcode entry",
                    imageUrl = null,
                    per100g = NutrientsDto(calories = calories, proteinG = protein, carbsG = carbs, fatG = fat),
                    quantityG = quantity
                )

                binding.progressSearch.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        val res = ApiClient.api.logFood(req)
                        if (res.isSuccessful) {
                            Toast.makeText(
                                this@LogMealActivity,
                                "Logged $name (${quantity.roundToInt()}g) to today's log",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        } else {
                            Toast.makeText(this@LogMealActivity, "Failed to log: ${res.code()}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@LogMealActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    } finally {
                        binding.progressSearch.visibility = View.INVISIBLE
                    }
                }
            }
        }
        dialog.show()
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }
}
