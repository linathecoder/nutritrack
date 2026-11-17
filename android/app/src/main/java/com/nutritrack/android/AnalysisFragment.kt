package com.nutritrack.android

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.AiHealthAnalysis
import com.nutritrack.android.data.model.DailyLogResponse
import com.nutritrack.android.data.model.NutrientsDto
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.model.WeightEntryDto
import com.nutritrack.android.data.model.WeightHistoryResponse
import com.nutritrack.android.data.model.WeightRequest
import com.nutritrack.android.databinding.DialogLogWeightBinding
import com.nutritrack.android.databinding.FragmentAnalysisBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private var loadJob: Job? = null
    private val numberFormat = NumberFormat.getIntegerInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupChartAppearance()
        setupListeners()
        bindAiHealthAnalysisModel()
    }

    override fun onResume() {
        super.onResume()
        loadAnalysisData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener { loadAnalysisData() }
        binding.btnLogWeightQuick.setOnClickListener { showLogWeightDialog() }
    }

    private fun setupChartAppearance() {
        val chart = binding.chartWeight
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_slate_400)
        xAxis.textSize = 10f
        xAxis.granularity = 1f

        val leftAxis = chart.axisLeft
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_slate_400)
        leftAxis.textSize = 10f
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = ContextCompat.getColor(requireContext(), R.color.slate_100)

        chart.axisRight.isEnabled = false
    }

    private fun loadAnalysisData() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressAnalysis.visibility = View.VISIBLE
            binding.errorCard.visibility = View.GONE
            try {
                val (weightRes, logRes, profileRes) = coroutineScope {
                    val weight = async { ApiClient.api.getWeightHistory(90) }
                    val log = async { ApiClient.api.getTodayLog() }
                    val profile = async { ApiClient.api.getProfile() }
                    Triple(weight.await(), log.await(), profile.await())
                }

                val weightHistory = weightRes.body()
                val log = logRes.body()
                val profile = profileRes.body()

                if (weightHistory != null) {
                    renderWeightEvolution(weightHistory, profile)
                }

                val totals = log?.totals ?: NutrientsDto()
                renderMacroComparison(totals, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Unable to load analysis: ${e.localizedMessage ?: "network error"}")
            } finally {
                _binding?.progressAnalysis?.visibility = View.INVISIBLE
            }
        }
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.errorCard.visibility = View.VISIBLE
    }

    // SECTION 1: Weight Evolution Line Chart
    private fun renderWeightEvolution(
        history: WeightHistoryResponse,
        profile: ProfileCalculationResponse?
    ) {
        val entries = history.entries.sortedBy { it.date }
        val targetWeight = history.targetWeightKg ?: profile?.targetWeightKg

        if (targetWeight != null && targetWeight > 0) {
            binding.tvTargetWeightStat.text = "${fmt(targetWeight)} kg"
        } else {
            binding.tvTargetWeightStat.text = "—"
        }

        if (entries.isEmpty()) {
            binding.chartWeight.visibility = View.GONE
            binding.weightEmptyState.visibility = View.VISIBLE
            val current = profile?.weightKg
            binding.tvCurrentWeightStat.text = current?.let { "${fmt(it)} kg" } ?: "—"
            binding.tvWeightChangeStat.text = "—"
            return
        }

        binding.chartWeight.visibility = View.VISIBLE
        binding.weightEmptyState.visibility = View.GONE

        val currentWeight = entries.last().weightKg
        val startWeight = entries.first().weightKg
        val diff = currentWeight - startWeight

        binding.tvCurrentWeightStat.text = "${fmt(currentWeight)} kg"
        binding.tvWeightChangeStat.text = "${if (diff > 0) "+" else ""}${fmt(diff)} kg"
        binding.tvWeightChangeStat.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (diff <= 0) R.color.nav_active else R.color.error_text
            )
        )

        val chartEntries = ArrayList<Entry>()
        val dateLabels = ArrayList<String>()
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        val displayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)

        for ((index, item) in entries.withIndex()) {
            chartEntries.add(Entry(index.toFloat(), item.weightKg.toFloat()))
            val label = runCatching {
                LocalDate.parse(item.date, inputFormatter).format(displayFormatter)
            }.getOrDefault(item.date)
            dateLabels.add(label)
        }

        binding.chartWeight.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return dateLabels.getOrElse(idx) { "" }
            }
        }

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val activeColor = ContextCompat.getColor(requireContext(), R.color.nav_active)

        val dataSet = LineDataSet(chartEntries, "Weight (kg)").apply {
            color = activeColor
            lineWidth = 2.5f
            setCircleColor(primaryColor)
            circleRadius = 4.5f
            circleHoleColor = Color.WHITE
            circleHoleRadius = 2.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 45
        }

        // Target weight line if available
        val leftAxis = binding.chartWeight.axisLeft
        leftAxis.removeAllLimitLines()
        if (targetWeight != null && targetWeight > 0) {
            val limit = LimitLine(targetWeight.toFloat(), "Goal: ${fmt(targetWeight)} kg").apply {
                lineColor = ContextCompat.getColor(requireContext(), R.color.hero_sub)
                lineWidth = 1.2f
                enableDashedLine(10f, 10f, 0f)
                textColor = ContextCompat.getColor(requireContext(), R.color.hero_sub)
                textSize = 9.5f
            }
            leftAxis.addLimitLine(limit)
        }

        binding.chartWeight.data = LineData(dataSet)
        binding.chartWeight.invalidate()
    }

    // SECTION 2: Daily Macro Intake vs Targets
    private fun renderMacroComparison(totals: NutrientsDto, profile: ProfileCalculationResponse?) {
        val targetCalories = profile?.calories ?: 2000
        val targetProtein = profile?.proteinG ?: 140.0
        val targetCarbs = profile?.carbsG ?: 220.0
        val targetFat = profile?.fatG ?: 65.0

        // Calories
        val calConsumed = totals.calories.roundToInt()
        val calPct = if (targetCalories > 0) (totals.calories / targetCalories * 100).roundToInt() else 0
        binding.tvCaloriesComparison.text =
            "${numberFormat.format(calConsumed)} / ${numberFormat.format(targetCalories)} kcal"
        binding.progressCaloriesComp.setProgressCompat(calPct.coerceIn(0, 100), true)
        binding.tvCaloriesPercentComp.text = "$calPct%"

        // Protein
        val protConsumed = totals.proteinG.roundToInt()
        val protPct = if (targetProtein > 0) (totals.proteinG / targetProtein * 100).roundToInt() else 0
        binding.tvProteinComparison.text = "${protConsumed} / ${targetProtein.roundToInt()}g"
        binding.progressProteinComp.setProgressCompat(protPct.coerceIn(0, 100), true)
        binding.tvProteinPercentComp.text = "$protPct%"

        // Carbs
        val carbsConsumed = totals.carbsG.roundToInt()
        val carbsPct = if (targetCarbs > 0) (totals.carbsG / targetCarbs * 100).roundToInt() else 0
        binding.tvCarbsComparison.text = "${carbsConsumed} / ${targetCarbs.roundToInt()}g"
        binding.progressCarbsComp.setProgressCompat(carbsPct.coerceIn(0, 100), true)
        binding.tvCarbsPercentComp.text = "$carbsPct%"

        // Fat
        val fatConsumed = totals.fatG.roundToInt()
        val fatPct = if (targetFat > 0) (totals.fatG / targetFat * 100).roundToInt() else 0
        binding.tvFatComparison.text = "${fatConsumed} / ${targetFat.roundToInt()}g"
        binding.progressFatComp.setProgressCompat(fatPct.coerceIn(0, 100), true)
        binding.tvFatPercentComp.text = "$fatPct%"
    }

    // SECTION 3: AI Health Analysis Data Model Binding (BR-09)
    private fun bindAiHealthAnalysisModel() {
        val sampleAiAnalysis = AiHealthAnalysis(
            productName = "Grilled Salmon & Quinoa Bowl",
            score = 88,
            rating = "Nutrient-Dense • Excellent Balance",
            explanation = "High in omega-3 polyunsaturated fatty acids supporting cardiovascular and brain health. Features complete bioavailable protein with slow-digesting complex carbohydrates.",
            pairingSuggestion = "Pair with steamed broccoli or a fresh spinach salad: vitamin C enhances non-heme iron absorption while dietary fiber promotes a diverse microbiome."
        )

        binding.tvAiProductName.text = sampleAiAnalysis.productName
        binding.tvAiProductRating.text = sampleAiAnalysis.rating
        binding.tvAiScoreBadge.text = "Score: ${sampleAiAnalysis.score}/100"
        binding.tvAiExplanation.text = sampleAiAnalysis.explanation
        binding.tvAiPairingSuggestion.text = sampleAiAnalysis.pairingSuggestion

        // Clear marker that UI is bound to data model awaiting backend endpoint
        binding.tvAiStatusBadge.text =
            "Model: AiHealthAnalysis (Backend endpoint GET /api/analysis/ai pending)"

        // Mandatory Disclaimer under it (BR-09)
        binding.tvMedicalDisclaimer.text =
            "This information is purely informational and does not replace a medical consultation."
    }

    private fun showLogWeightDialog() {
        val dialogBinding = DialogLogWeightBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log Weight Entry")
            .setView(dialogBinding.root)
            .setPositiveButton("Log Weight", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = dialogBinding.etLogWeight.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
                if (entered == null || entered !in 25.0..350.0) {
                    dialogBinding.tilLogWeight.error = "Enter a valid weight between 25 and 350 kg"
                    return@setOnClickListener
                }
                dialog.dismiss()
                logWeight(entered)
            }
        }
        dialog.show()
    }

    private fun logWeight(weightKg: Double) {
        binding.progressAnalysis.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = ApiClient.api.logWeight(WeightRequest(weightKg = weightKg))
                if (res.isSuccessful) {
                    Toast.makeText(requireContext(), "Weight logged: ${fmt(weightKg)} kg", Toast.LENGTH_SHORT).show()
                    loadAnalysisData()
                } else {
                    Toast.makeText(requireContext(), "Failed to log weight: ${res.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _binding?.progressAnalysis?.visibility = View.INVISIBLE
            }
        }
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
}
