package com.nutritrack.android

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.analysis.AnalysisEngine
import com.nutritrack.android.data.analysis.AnalysisInsight
import com.nutritrack.android.data.analysis.AnalysisReport
import com.nutritrack.android.data.analysis.ChartPoint
import com.nutritrack.android.data.analysis.DayLog
import com.nutritrack.android.data.analysis.InsightLevel
import com.nutritrack.android.data.analysis.Targets
import com.nutritrack.android.data.analysis.WeightPoint
import com.nutritrack.android.data.analysis.WeightStats
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.LogHistoryDayDto
import com.nutritrack.android.data.model.WeightRequest
import com.nutritrack.android.databinding.DialogLogWeightBinding
import com.nutritrack.android.databinding.FragmentAnalysisBinding
import com.nutritrack.android.databinding.ItemNoticeBinding
import com.nutritrack.android.databinding.ItemTopFoodBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Analysis page. Everything shown here (summary, stats, charts, "What we noticed") is computed by
 * [AnalysisEngine] from the user's logs, targets and weight history: fixed rules, no AI.
 */
class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!

    private var loadJob: Job? = null
    private var periodDays = 7
    private var report: AnalysisReport? = null

    private val numberFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val zone = ZoneId.of("Africa/Tunis")

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
        styleLineChart(binding.chartCalories, showLeftAxis = true)
        styleLineChart(binding.chartProtein, showLeftAxis = false)
        styleLineChart(binding.chartWeight, showLeftAxis = true)
        setupListeners()
        styleTabs()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) load()
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
        binding.tabPeriod7.setOnClickListener { selectPeriod(7) }
        binding.tabPeriod30.setOnClickListener { selectPeriod(30) }
        binding.tabPeriod90.setOnClickListener { selectPeriod(90) }
        binding.btnRetry.setOnClickListener { load() }
        binding.btnLogWeightQuick.setOnClickListener { showLogWeightDialog() }
        binding.btnLogMealEmpty.setOnClickListener { (activity as? MainActivity)?.openLogMeal() }
        binding.btnRange.setOnClickListener {
            report?.let { Toast.makeText(requireContext(), it.rangeLabel, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun selectPeriod(days: Int) {
        if (days == periodDays) return
        periodDays = days
        styleTabs()
        load()
    }

    private fun styleTabs() {
        listOf(7 to binding.tabPeriod7, 30 to binding.tabPeriod30, 90 to binding.tabPeriod90)
            .forEach { (days, tab) ->
                val active = days == periodDays
                tab.setBackgroundResource(if (active) R.drawable.bg_period_active else 0)
                tab.setTextColor(col(if (active) R.color.nav_active else R.color.text_slate_500))
                tab.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
    }

    private fun styleLineChart(chart: LineChart, showLeftAxis: Boolean) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.axisRight.isEnabled = false
        chart.setNoDataText("No data yet")
        chart.setNoDataTextColor(col(R.color.text_slate_400))
        chart.setExtraBottomOffset(4f)

        val x = chart.xAxis
        x.position = XAxis.XAxisPosition.BOTTOM
        x.setDrawGridLines(false)
        x.setDrawAxisLine(false)
        x.textColor = col(R.color.text_slate_500)
        x.textSize = 10f
        x.granularity = 1f

        val y = chart.axisLeft
        y.setDrawAxisLine(false)
        y.setDrawLabels(showLeftAxis)
        y.setDrawGridLines(showLeftAxis)
        y.gridColor = col(R.color.slate_100)
        y.textColor = col(R.color.text_slate_400)
        y.textSize = 10f
    }

    // ---------------------------------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------------------------------

    private fun load() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressAnalysis.visibility = View.VISIBLE
            binding.errorCard.visibility = View.GONE
            try {
                val days = periodDays
                // We ask for twice the period: the second half is the previous period (comparison).
                val (historyRes, profileRes, weightRes) = coroutineScope {
                    val history = async { ApiClient.api.getLogHistory(days * 2) }
                    val profile = async { ApiClient.api.getProfile() }
                    val weight = async { ApiClient.api.getWeightHistory(90) }
                    Triple(history.await(), profile.await(), weight.await())
                }

                if (!historyRes.isSuccessful) {
                    if (historyRes.code() != 401) showError("Couldn't load your logs (error ${historyRes.code()}).")
                    return@launch
                }

                val profile = profileRes.body()
                val weightHistory = weightRes.body()

                val logs = historyRes.body()?.days.orEmpty().mapNotNull { toDayLog(it) }
                val targets = Targets(
                    kcal = (profile?.calories ?: 2000).toDouble(),
                    protein = profile?.proteinG ?: 140.0,
                    carbs = profile?.carbsG ?: 220.0,
                    fat = profile?.fatG ?: 65.0
                )
                val weights = weightHistory?.entries.orEmpty().mapNotNull { e ->
                    runCatching { WeightPoint(LocalDate.parse(e.date), e.weightKg) }.getOrNull()
                }
                val goal = weightHistory?.targetWeightKg ?: profile?.targetWeightKg

                val built = AnalysisEngine.build(days, LocalDate.now(zone), logs, targets, weights, goal)
                report = built
                if (_binding != null) render(built)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Unable to load analysis: ${e.localizedMessage ?: "network error"}")
            } finally {
                _binding?.progressAnalysis?.visibility = View.INVISIBLE
            }
        }
    }

    private fun toDayLog(d: LogHistoryDayDto): DayLog? {
        val date = d.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val t = d.totals
        return DayLog(
            date = date,
            kcal = t?.calories ?: 0.0,
            protein = t?.proteinG ?: 0.0,
            carbs = t?.carbsG ?: 0.0,
            fat = t?.fatG ?: 0.0,
            foods = d.items.orEmpty().mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
        )
    }

    private fun showError(message: String) {
        _binding?.let {
            it.tvError.text = message
            it.errorCard.visibility = View.VISIBLE
        }
    }

    // ---------------------------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------------------------

    private fun render(r: AnalysisReport) {
        binding.tvTopFoodsPeriod.text = "Last ${r.periodDays} days"

        if (!r.hasEnoughData) {
            binding.contentGroup.visibility = View.GONE
            binding.emptyGroup.visibility = View.VISIBLE
            binding.tvEmptyProgress.text =
                "${r.loggedDays} of ${AnalysisEngine.MIN_LOGGED_DAYS} days logged so far"
            return
        }
        binding.emptyGroup.visibility = View.GONE
        binding.contentGroup.visibility = View.VISIBLE

        // Summary + stats
        binding.tvSummary.text = r.summary
        binding.tvUpdated.text = "Based on ${r.loggedDays} logged days \u2022 ${r.rangeLabel}"
        binding.tvStatCalValue.text = numberFormat.format(r.avgCalories)
        binding.tvStatCalSub.text = "of ${numberFormat.format(r.targetCalories)}"
        binding.tvStatDaysValue.text = "${r.daysOnTarget}/${r.loggedDays}"
        binding.tvStatDaysSub.text = r.daysOnTargetSub
        binding.tvStatStreakValue.text = if (r.streak == 1) "1 day" else "${r.streak} days"
        binding.tvStatStreakSub.text = if (r.streak > 0) "Keep it going" else "Log today to start"

        renderCalories(r)
        renderMacros(r)
        renderWeight(r.weight)
        renderInsights(r.insights)
        renderTopFoods(r)
    }

    // ---- Calories line chart ----------------------------------------------------------------

    private fun renderCalories(r: AnalysisReport) {
        val chart = binding.chartCalories
        val series = r.calorieSeries
        val goal = r.targetCalories.toFloat()

        binding.tvCaloriesSubtitle.text = "Daily intake vs ${numberFormat.format(r.targetCalories)} kcal target"
        binding.tvCalSummary.text = r.calorieSummary

        val entries = series.mapIndexedNotNull { i, p -> p.value?.let { Entry(i.toFloat(), it.toFloat()) } }
        if (entries.isEmpty()) {
            chart.clear()
            binding.tvCaloriePointBadge.text = "No data"
            return
        }

        val amber = col(R.color.macro_carbs)
        val slate = col(R.color.text_slate_400)
        val primary = col(R.color.primary)
        val circleColors = entries.map { e ->
            when (e.x.toInt()) {
                r.calorieHighIndex -> amber
                r.calorieLowIndex -> slate
                else -> primary
            }
        }

        val set = LineDataSet(entries, "Calories").apply {
            color = col(R.color.nav_active)
            lineWidth = 2.6f
            mode = if (entries.size > 2) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR
            setCircleColors(circleColors)
            circleRadius = 4.5f
            circleHoleColor = Color.WHITE
            circleHoleRadius = 2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = primary
            fillAlpha = 40
            highLightColor = col(R.color.nav_active)
            setDrawHorizontalHighlightIndicator(false)
        }

        val values = entries.map { it.y }
        chart.axisLeft.axisMinimum = (min(values.min(), goal) * 0.75f)
        chart.axisLeft.axisMaximum = (max(values.max(), goal) * 1.15f)
        chart.axisLeft.removeAllLimitLines()
        chart.axisLeft.addLimitLine(
            LimitLine(goal, "Goal ${numberFormat.format(r.targetCalories)}").apply {
                lineColor = amber
                lineWidth = 1.4f
                enableDashedLine(10f, 8f, 0f)
                textColor = amber
                textSize = 10f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            }
        )
        applyXAxis(chart, series)

        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null) return
                val label = series.getOrNull(e.x.toInt())?.label ?: return
                binding.tvCaloriePointBadge.text = "$label: ${numberFormat.format(e.y.roundToInt())} kcal"
            }

            override fun onNothingSelected() = Unit
        })

        chart.data = LineData(set)
        chart.highlightValues(null)
        chart.invalidate()

        // Default badge: the highest point of the period
        val hi = r.calorieHighIndex?.let { series.getOrNull(it) }
        val hiValue = hi?.value
        binding.tvCaloriePointBadge.text =
            if (hi != null && hiValue != null) "${hi.label}: ${numberFormat.format(hiValue.roundToInt())} kcal" else "\u2014"
    }

    private fun applyXAxis(chart: LineChart, series: List<ChartPoint>) {
        val x = chart.xAxis
        x.valueFormatter = IndexAxisValueFormatter(series.map { it.label })
        x.axisMinimum = -0.5f
        x.axisMaximum = series.size - 0.5f
        x.setLabelCount(series.size, false)
    }

    // ---- Macros: donut + bars + protein trend -----------------------------------------------

    private fun renderMacros(r: AnalysisReport) {
        binding.macroDonut.setShares(
            r.protein.calorieSharePct / 100f,
            r.carbs.calorieSharePct / 100f,
            r.fat.calorieSharePct / 100f
        )
        binding.tvDonutKcal.text = numberFormat.format(r.avgCalories)

        binding.tvProteinStat.text = "${r.protein.avgG.roundToInt()} / ${r.protein.targetG.roundToInt()} g"
        binding.tvProteinPct.text = "${r.protein.calorieSharePct}%"
        binding.progressProtein.setProgressCompat(r.protein.barPct, true)

        binding.tvCarbsStat.text = "${r.carbs.avgG.roundToInt()} / ${r.carbs.targetG.roundToInt()} g"
        binding.tvCarbsPct.text = "${r.carbs.calorieSharePct}%"
        binding.progressCarbs.setProgressCompat(r.carbs.barPct, true)

        binding.tvFatStat.text = "${r.fat.avgG.roundToInt()} / ${r.fat.targetG.roundToInt()} g"
        binding.tvFatPct.text = "${r.fat.calorieSharePct}%"
        binding.progressFat.setProgressCompat(r.fat.barPct, true)

        // Protein trend
        val chart = binding.chartProtein
        val series = r.proteinSeries
        binding.tvProteinTarget.text = "Target ${r.protein.targetG.roundToInt()} g"
        val entries = series.mapIndexedNotNull { i, p -> p.value?.let { Entry(i.toFloat(), it.toFloat()) } }
        if (entries.isEmpty()) {
            chart.clear()
            return
        }
        val violet = col(R.color.macro_protein)
        val set = LineDataSet(entries, "Protein").apply {
            color = violet
            lineWidth = 2.2f
            mode = if (entries.size > 2) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR
            setCircleColor(violet)
            circleRadius = 3.4f
            circleHoleColor = Color.WHITE
            circleHoleRadius = 1.8f
            setDrawValues(false)
            setDrawHighlightIndicators(false)
        }
        val target = r.protein.targetG.toFloat()
        val values = entries.map { it.y }
        chart.axisLeft.axisMinimum = min(values.min(), target) * 0.7f
        chart.axisLeft.axisMaximum = max(values.max(), target) * 1.15f
        chart.axisLeft.removeAllLimitLines()
        if (target > 0f) {
            chart.axisLeft.addLimitLine(
                LimitLine(target).apply {
                    lineColor = violet
                    lineWidth = 1.2f
                    enableDashedLine(10f, 8f, 0f)
                }
            )
        }
        applyXAxis(chart, series)
        chart.data = LineData(set)
        chart.invalidate()
    }

    // ---- Weight -----------------------------------------------------------------------------

    private fun renderWeight(w: WeightStats) {
        binding.tvWeightCurrent.text = w.currentKg?.let { "${fmt1(it)} kg" } ?: "\u2014"

        val change = w.changeKg
        if (change != null) {
            val sign = if (change > 0.05) "+" else if (change < -0.05) "\u2212" else ""
            binding.tvWeightDelta.text = "$sign${fmt1(abs(change))} kg in ${periodDays}d"
            val goal = w.targetKg
            val current = w.currentKg
            val towardGoal = goal == null || current == null || (goal - current) * change >= 0
            binding.tvWeightDelta.setBackgroundResource(if (towardGoal) R.drawable.bg_pill_green else R.drawable.bg_pill_amber)
            binding.tvWeightDelta.setTextColor(col(if (towardGoal) R.color.nav_active else R.color.amber_text))
            binding.tvWeightDelta.visibility = View.VISIBLE
        } else {
            binding.tvWeightDelta.visibility = View.GONE
        }

        if (w.chartPoints.isNotEmpty()) {
            binding.chartWeight.visibility = View.VISIBLE
            binding.tvWeightEmpty.visibility = View.GONE
            renderWeightChart(w)
        } else {
            binding.chartWeight.visibility = View.GONE
            binding.tvWeightEmpty.visibility = View.VISIBLE
            binding.tvWeightEmpty.text =
                if (w.currentKg != null) "No weigh-in in this period." else "Log your weight to see your trend."
        }

        binding.tvWeightToGoal.text = w.toGoalKg?.let { "${fmt1(it)} kg" } ?: "\u2014"
        binding.tvWeightPace.text = w.weeklyPaceKg?.let { String.format(Locale.US, "%+.1f kg", it) } ?: "\u2014"
        binding.tvWeightEta.text = w.etaWeeks?.let { "$it weeks" } ?: "\u2014"

        val progress = w.progressPct
        if (progress != null && w.startKg != null && w.targetKg != null) {
            binding.tvWeightProgressLabel.text = "$progress% of the way to your goal"
            binding.progressWeight.setProgressCompat(progress, true)
            binding.tvWeightRange.text = "${fmt1(w.startKg)} \u2192 ${fmt1(w.targetKg)} kg"
        } else {
            binding.tvWeightProgressLabel.text = "Set a target weight in Profile to track your goal"
            binding.progressWeight.setProgressCompat(0, false)
            binding.tvWeightRange.text = ""
        }
    }

    private fun renderWeightChart(w: WeightStats) {
        val chart = binding.chartWeight
        val points = w.chartPoints
        val labelFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)
        val labels = points.map { it.date.format(labelFormat) }
        val entries = points.mapIndexed { i, p -> Entry(i.toFloat(), p.kg.toFloat()) }

        val green = col(R.color.nav_active)
        val primary = col(R.color.primary)
        val set = LineDataSet(entries, "Weight").apply {
            color = green
            lineWidth = 2.6f
            mode = if (entries.size > 2) LineDataSet.Mode.CUBIC_BEZIER else LineDataSet.Mode.LINEAR
            setCircleColor(primary)
            circleRadius = 4.5f
            circleHoleColor = Color.WHITE
            circleHoleRadius = 2.2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = primary
            fillAlpha = 40
            setDrawHighlightIndicators(false)
        }

        val goal = w.targetKg?.toFloat()
        val values = entries.map { it.y }
        val lo = min(values.min(), goal ?: values.min()) - 1f
        val hi = max(values.max(), goal ?: values.max()) + 1f
        chart.axisLeft.axisMinimum = lo
        chart.axisLeft.axisMaximum = hi
        chart.axisLeft.removeAllLimitLines()
        if (goal != null) {
            val amber = col(R.color.macro_carbs)
            chart.axisLeft.addLimitLine(
                LimitLine(goal, "Goal ${fmt1(goal.toDouble())} kg").apply {
                    lineColor = amber
                    lineWidth = 1.4f
                    enableDashedLine(10f, 8f, 0f)
                    textColor = amber
                    textSize = 10f
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                }
            )
        }

        val x = chart.xAxis
        x.valueFormatter = IndexAxisValueFormatter(labels)
        x.axisMinimum = -0.5f
        x.axisMaximum = entries.size - 0.5f
        x.setLabelCount(min(entries.size, 5), false)

        chart.data = LineData(set)
        chart.invalidate()
    }

    // ---- What we noticed --------------------------------------------------------------------

    private fun renderInsights(insights: List<AnalysisInsight>) {
        val container = binding.insightsContainer
        container.removeAllViews()
        if (insights.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "Nothing to flag yet."
                setTextColor(col(R.color.text_slate_500))
                textSize = 13f
                setPadding(0, dp(10), 0, dp(6))
            })
            return
        }
        insights.forEachIndexed { index, insight ->
            val row = ItemNoticeBinding.inflate(layoutInflater, container, false)
            row.tvNoticeTitle.text = insight.title
            row.tvNoticeTip.text = insight.tip
            val dot = if (insight.level == InsightLevel.WATCH) R.color.macro_carbs else R.color.nav_active
            row.noticeDot.backgroundTintList = ColorStateList.valueOf(col(dot))
            row.noticeRoot.setOnClickListener {
                val show = row.tvNoticeTip.visibility != View.VISIBLE
                row.tvNoticeTip.visibility = if (show) View.VISIBLE else View.GONE
                row.noticeChevron.rotation = if (show) 90f else 0f
            }
            container.addView(row.root)
            if (index < insights.lastIndex) {
                container.addView(View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                    setBackgroundColor(col(R.color.slate_200))
                })
            }
        }
    }

    // ---- Most eaten foods -------------------------------------------------------------------

    private fun renderTopFoods(r: AnalysisReport) {
        val container = binding.topFoodsContainer
        container.removeAllViews()
        if (r.topFoods.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = "No foods logged in this period."
                setTextColor(col(R.color.text_slate_500))
                textSize = 13f
                setPadding(0, dp(12), 0, 0)
            })
            return
        }
        val maxCount = r.topFoods.maxOf { it.count }.coerceAtLeast(1)
        r.topFoods.forEach { food ->
            val row = ItemTopFoodBinding.inflate(layoutInflater, container, false)
            row.tvFoodName.text = food.name
            row.tvFoodCount.text = "${food.count}\u00D7"
            row.foodBar.setProgressCompat((food.count * 100f / maxCount).roundToInt(), false)
            container.addView(row.root)
        }
    }

    // ---- Log weight (same dialog as before) -------------------------------------------------

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
                    Toast.makeText(requireContext(), "Weight logged: ${fmt1(weightKg)} kg", Toast.LENGTH_SHORT).show()
                    load()
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

    // ---------------------------------------------------------------------------------------

    private fun col(res: Int) = ContextCompat.getColor(requireContext(), res)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
}
