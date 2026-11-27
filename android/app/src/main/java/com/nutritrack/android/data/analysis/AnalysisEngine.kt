package com.nutritrack.android.data.analysis

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * ANALYSIS ENGINE  (no AI, no network: only arithmetic and fixed rules)
 *
 * It receives what the user logged (per day), the daily targets computed by the backend
 * (BR-01 / BR-05) and the weight history, and returns everything the Analysis screen shows:
 * stats, chart series, the written summary and the "What we noticed" insights.
 *
 * Fixed rules (easy to explain to the teacher):
 *  - A day is "on target"      : calories between 90% and 110% of the daily goal.
 *  - A macro is "low"          : below 85% of its daily target.
 *  - A day is "over"           : calories above 110% of the goal; "under": below 80%.
 *  - Streak                    : consecutive logged days ending today (or yesterday).
 *  - Not enough data           : fewer than 3 logged days in the selected period.
 */

data class DayLog(
    val date: LocalDate,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val foods: List<String> = emptyList()
) {
    val isLogged: Boolean get() = kcal > 0.0 || foods.isNotEmpty()
}

data class Targets(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

data class WeightPoint(val date: LocalDate, val kg: Double)

data class ChartPoint(val label: String, val value: Double?)

enum class InsightLevel { GOOD, WATCH }

data class AnalysisInsight(val level: InsightLevel, val title: String, val tip: String)

data class TopFood(val name: String, val count: Int)

data class MacroStat(
    val avgG: Double,
    val targetG: Double,
    /** Share of the calories coming from this macro (protein 4, carbs 4, fat 9 kcal/g). */
    val calorieSharePct: Int
) {
    /** Progress bar: average intake against the target, capped at 100. */
    val barPct: Int
        get() = if (targetG > 0) ((avgG / targetG) * 100).roundToInt().coerceIn(0, 100) else 0
}

data class WeightStats(
    val currentKg: Double?,
    val startKg: Double?,
    val targetKg: Double?,
    /** Change inside the selected period (needs at least 2 entries). */
    val changeKg: Double?,
    val toGoalKg: Double?,
    val weeklyPaceKg: Double?,
    val etaWeeks: Int?,
    val progressPct: Int?,
    /** Entries inside the selected period, oldest first (for the chart). */
    val chartPoints: List<WeightPoint>
)

data class AnalysisReport(
    val periodDays: Int,
    val rangeLabel: String,
    val loggedDays: Int,
    val hasEnoughData: Boolean,

    val summary: String,

    val avgCalories: Int,
    val targetCalories: Int,
    val daysOnTarget: Int,
    val daysOnTargetSub: String,
    val streak: Int,

    val calorieSeries: List<ChartPoint>,
    val calorieHighIndex: Int?,
    val calorieLowIndex: Int?,
    val calorieSummary: String,

    val protein: MacroStat,
    val carbs: MacroStat,
    val fat: MacroStat,
    val proteinSeries: List<ChartPoint>,

    val weight: WeightStats,
    val insights: List<AnalysisInsight>,
    val topFoods: List<TopFood>
)

object AnalysisEngine {

    const val ON_TARGET_LOW = 0.90
    const val ON_TARGET_HIGH = 1.10
    const val LOW_MACRO = 0.85
    const val UNDER_GOAL = 0.80
    const val MIN_LOGGED_DAYS = 1

    private val RANGE_START = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val RANGE_END = DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.US)
    private val BUCKET_END = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    private class Bucket(val label: String, val from: LocalDate, val to: LocalDate)

    fun build(
        periodDays: Int,
        today: LocalDate,
        logs: List<DayLog>,
        targets: Targets,
        weights: List<WeightPoint>,
        goalWeightKg: Double?
    ): AnalysisReport {
        val windowStart = today.minusDays((periodDays - 1).toLong())
        val prevEnd = windowStart.minusDays(1)
        val prevStart = prevEnd.minusDays((periodDays - 1).toLong())

        val logged = logs.filter { it.isLogged }
        val cur = logged.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(today) }.sortedBy { it.date }
        val prev = logged.filter { !it.date.isBefore(prevStart) && !it.date.isAfter(prevEnd) }
        val n = cur.size

        fun onTarget(d: DayLog) =
            targets.kcal > 0 && d.kcal >= targets.kcal * ON_TARGET_LOW && d.kcal <= targets.kcal * ON_TARGET_HIGH

        val avgCal = if (n > 0) cur.map { it.kcal }.average() else 0.0
        val on = cur.count { onTarget(it) }
        val prevOn = prev.count { onTarget(it) }
        val streak = streak(logged.map { it.date }.toSet(), today)

        val avgP = if (n > 0) cur.map { it.protein }.average() else 0.0
        val avgC = if (n > 0) cur.map { it.carbs }.average() else 0.0
        val avgF = if (n > 0) cur.map { it.fat }.average() else 0.0
        val macroKcal = avgP * 4 + avgC * 4 + avgF * 9
        fun share(kcalOfMacro: Double) = if (macroKcal > 0) (kcalOfMacro / macroKcal * 100).roundToInt() else 0
        val protein = MacroStat(avgP, targets.protein, share(avgP * 4))
        val carbs = MacroStat(avgC, targets.carbs, share(avgC * 4))
        val fat = MacroStat(avgF, targets.fat, share(avgF * 9))

        // ---- Chart series -------------------------------------------------------------
        val buckets = buckets(periodDays, windowStart)
        fun series(metric: (DayLog) -> Double): List<ChartPoint> = buckets.map { b ->
            val inside = cur.filter { !it.date.isBefore(b.from) && !it.date.isAfter(b.to) }
            ChartPoint(b.label, if (inside.isEmpty()) null else inside.map(metric).average())
        }
        val calSeries = series { it.kcal }
        val protSeries = series { it.protein }

        val present = calSeries.withIndex().filter { it.value.value != null }
        val highIdx = present.maxByOrNull { it.value.value!! }?.index
        val lowIdx = present.minByOrNull { it.value.value!! }?.index
        val calSummary = calorieSummary(periodDays, buckets, calSeries, highIdx, lowIdx)

        // ---- Weight -------------------------------------------------------------------
        val weight = weightStats(weights, windowStart, today, goalWeightKg)

        // ---- Text ---------------------------------------------------------------------
        val summary = summary(n, on, avgCal, targets, protein, carbs, fat, weight)
        val insights = insights(
            periodDays, n, on, cur, prev, targets, avgP, protein, carbs, fat, streak, weight, distinctFoodCount(cur)
        )

        val sub = when {
            prev.isNotEmpty() && periodDays == 7 -> deltaText(on - prevOn) + " vs last wk"
            prev.isNotEmpty() -> deltaText(on - prevOn) + " vs previous"
            n > 0 -> "of logged days"
            else -> "No logs yet"
        }

        return AnalysisReport(
            periodDays = periodDays,
            rangeLabel = "${windowStart.format(RANGE_START)} \u2013 ${today.format(RANGE_END)}",
            loggedDays = n,
            hasEnoughData = n >= MIN_LOGGED_DAYS,
            summary = summary,
            avgCalories = avgCal.roundToInt(),
            targetCalories = targets.kcal.roundToInt(),
            daysOnTarget = on,
            daysOnTargetSub = sub,
            streak = streak,
            calorieSeries = calSeries,
            calorieHighIndex = highIdx,
            calorieLowIndex = lowIdx,
            calorieSummary = calSummary,
            protein = protein,
            carbs = carbs,
            fat = fat,
            proteinSeries = protSeries,
            weight = weight,
            insights = insights,
            topFoods = topFoods(cur)
        )
    }

    // ------------------------------------------------------------------------------------

    private fun streak(loggedDates: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in loggedDates) today else today.minusDays(1)
        var count = 0
        while (d in loggedDates) {
            count++
            d = d.minusDays(1)
        }
        return count
    }

    private fun deltaText(diff: Int) = when {
        diff > 0 -> "+$diff"
        diff < 0 -> "$diff"
        else -> "Same"
    }

    private fun buckets(periodDays: Int, windowStart: LocalDate): List<Bucket> {
        if (periodDays <= 7) {
            return (0 until periodDays).map { i ->
                val d = windowStart.plusDays(i.toLong())
                Bucket(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), d, d)
            }
        }
        val chunks = if (periodDays <= 31) 4 else 3
        val base = periodDays / chunks
        val extra = periodDays % chunks
        val out = ArrayList<Bucket>()
        var from = windowStart
        for (i in 0 until chunks) {
            val size = base + if (i < extra) 1 else 0
            val to = from.plusDays((size - 1).toLong())
            val label = if (chunks == 4) "W${i + 1}" else to.format(BUCKET_END)
            out += Bucket(label, from, to)
            from = to.plusDays(1)
        }
        return out
    }

    private fun calorieSummary(
        periodDays: Int,
        buckets: List<Bucket>,
        series: List<ChartPoint>,
        high: Int?,
        low: Int?
    ): String {
        if (high == null || low == null || series.count { it.value != null } < 2) {
            return "Log a few more days to see your range"
        }
        if (high == low) return "Your intake was steady"
        return if (periodDays <= 7) {
            val h = buckets[high].from.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val l = buckets[low].from.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "Highest on $h, lowest on $l"
        } else {
            "Highest in ${series[high].label}, lowest in ${series[low].label}"
        }
    }

    private fun weightStats(
        weights: List<WeightPoint>,
        windowStart: LocalDate,
        today: LocalDate,
        goalKg: Double?
    ): WeightStats {
        val all = weights.sortedBy { it.date }
        val inWindow = all.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(today) }
        val current = all.lastOrNull()?.kg
        val start = all.firstOrNull()?.kg
        val goal = goalKg?.takeIf { it > 0 }

        val change = if (inWindow.size >= 2) inWindow.last().kg - inWindow.first().kg else null
        val toGoal = if (goal != null && current != null) abs(current - goal) else null

        var pace: Double? = null
        if (inWindow.size >= 2) {
            val span = ChronoUnit.DAYS.between(inWindow.first().date, inWindow.last().date)
            if (span >= 3) pace = (inWindow.last().kg - inWindow.first().kg) / span * 7.0
        }

        var eta: Int? = null
        if (pace != null && goal != null && current != null && toGoal != null && toGoal > 0.05 && abs(pace) >= 0.05) {
            val towardGoal = (goal - current) * pace > 0
            if (towardGoal) {
                val weeks = ceil(toGoal / abs(pace)).toInt()
                if (weeks in 1..104) eta = weeks
            }
        }

        var progress: Int? = null
        if (goal != null && start != null && current != null && abs(goal - start) > 0.01) {
            progress = (((current - start) / (goal - start)) * 100).roundToInt().coerceIn(0, 100)
        }

        return WeightStats(current, start, goal, change, toGoal, pace, eta, progress, inWindow)
    }

    private fun isTowardGoal(w: WeightStats): Boolean {
        val goal = w.targetKg ?: return false
        val current = w.currentKg ?: return false
        val pace = w.weeklyPaceKg ?: return false
        return abs(pace) >= 0.05 && (goal - current) * pace > 0
    }

    private fun summary(
        n: Int,
        on: Int,
        avgCal: Double,
        t: Targets,
        protein: MacroStat,
        carbs: MacroStat,
        fat: MacroStat,
        w: WeightStats
    ): String {
        if (n == 0) return "Nothing logged in this period yet. Log your meals to see your summary."
        val parts = ArrayList<String>()

        parts += "You stayed within 10% of your calorie goal on $on of $n logged days."

        val shortfalls = listOf(
            Triple("Protein", protein.avgG, protein.targetG),
            Triple("Carbs", carbs.avgG, carbs.targetG),
            Triple("Fat", fat.avgG, fat.targetG)
        ).filter { it.third > 0 }.minByOrNull { it.second / it.third }

        parts += when {
            shortfalls != null && shortfalls.second / shortfalls.third < LOW_MACRO ->
                "${shortfalls.first} was your weak spot, averaging ${shortfalls.second.roundToInt()} g " +
                    "against a ${shortfalls.third.roundToInt()} g target."
            t.kcal > 0 && avgCal > t.kcal * ON_TARGET_HIGH ->
                "You averaged ${fmtInt(avgCal)} kcal, above your ${fmtInt(t.kcal)} kcal goal."
            else -> "Your macros were well balanced against your targets."
        }

        val pace = w.weeklyPaceKg
        if (pace != null && w.targetKg != null) {
            val dir = if (pace < 0) "down" else "up"
            parts += when {
                isTowardGoal(w) && w.etaWeeks != null ->
                    "Your weight is $dir ${fmt1(abs(pace))} kg per week, on track to reach your goal in about ${w.etaWeeks} weeks."
                isTowardGoal(w) -> "Your weight is $dir ${fmt1(abs(pace))} kg per week, moving toward your goal."
                abs(pace) < 0.05 -> "Your weight has been steady."
                else -> "Your weight is $dir ${fmt1(abs(pace))} kg per week, moving away from your goal."
            }
        } else if (w.currentKg != null) {
            parts += "Your latest weight is ${fmt1(w.currentKg)} kg. Log it every few days to see your trend."
        } else {
            parts += "Log your weight to track your progress toward your goal."
        }
        return parts.joinToString(" ")
    }

    private fun distinctFoodCount(cur: List<DayLog>): Int =
        cur.flatMap { it.foods }.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet().size

    private fun insights(
        periodDays: Int,
        n: Int,
        on: Int,
        cur: List<DayLog>,
        prev: List<DayLog>,
        t: Targets,
        avgP: Double,
        protein: MacroStat,
        carbs: MacroStat,
        fat: MacroStat,
        streak: Int,
        w: WeightStats,
        distinctFoods: Int
    ): List<AnalysisInsight> {
        if (n == 0) return emptyList()
        val watch = ArrayList<AnalysisInsight>()
        val good = ArrayList<AnalysisInsight>()

        val lowProtein = if (t.protein > 0) cur.count { it.protein < t.protein * LOW_MACRO } else 0
        val over = if (t.kcal > 0) cur.count { it.kcal > t.kcal * ON_TARGET_HIGH } else 0
        val under = if (t.kcal > 0) cur.count { it.kcal < t.kcal * UNDER_GOAL } else 0

        // ---- WATCH ----
        if (n >= MIN_LOGGED_DAYS && lowProtein * 2 >= n) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "Protein was below target on $lowProtein of $n days",
                "Add a protein source such as yogurt, eggs, chicken or lentils to one meal a day."
            )
        }
        if (n >= MIN_LOGGED_DAYS && over * 10 >= n * 3) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "You went over your calorie goal on $over of $n days",
                "Watch portions of energy-dense foods and log snacks as you eat them."
            )
        }
        if (n >= MIN_LOGGED_DAYS && under * 10 >= n * 3) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "You ate well below your goal on $under of $n days",
                "Eating much less than planned is hard to keep up. Add a balanced snack on those days."
            )
        }
        if (fat.calorieSharePct > 40) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "Fat makes up ${fat.calorieSharePct}% of your calories",
                "Swap some fried or creamy foods for grilled, baked or steamed options."
            )
        }
        if (carbs.calorieSharePct > 60) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "Carbs make up ${carbs.calorieSharePct}% of your calories",
                "Pair carbs with protein and vegetables to keep meals balanced."
            )
        }
        if (n.toDouble() / periodDays < 0.6) {
            watch += AnalysisInsight(
                InsightLevel.WATCH, "You logged $n of the last $periodDays days",
                "Logging every day, even imperfect ones, makes these insights more accurate."
            )
        }
        val weekend = cur.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekdays = cur.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
        if (weekend.size >= 2 && weekdays.size >= 2) {
            val we = weekend.map { it.kcal }.average()
            val wd = weekdays.map { it.kcal }.average()
            if (we > wd * 1.15) {
                watch += AnalysisInsight(
                    InsightLevel.WATCH, "Weekends run higher: ${fmtInt(we)} vs ${fmtInt(wd)} kcal",
                    "Plan a lighter breakfast or lunch on weekend days to balance the week."
                )
            }
        }

        // ---- GOOD ----
        if (n >= MIN_LOGGED_DAYS && on * 10 >= n * 7) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "Calories on target on $on of $n logged days",
                "Great consistency. Keep your portions and meal timing the same."
            )
        }
        val cvCur = variation(cur.map { it.kcal })
        val cvPrev = variation(prev.map { it.kcal })
        if (n >= MIN_LOGGED_DAYS && prev.size >= MIN_LOGGED_DAYS && cvPrev > 0 && cvCur < cvPrev) {
            val pct = ((cvPrev - cvCur) / cvPrev * 100).roundToInt()
            if (pct >= 10) {
                good += AnalysisInsight(
                    InsightLevel.GOOD, "Calorie consistency improved by $pct%",
                    "A steady intake makes it easier to reach your goal."
                )
            }
        }
        if (t.protein > 0 && avgP >= t.protein * 0.9) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "Protein goal met on average",
                "Keep spreading protein across your meals."
            )
        }
        if (streak >= 3) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "You are on a $streak-day logging streak",
                "Consistency is what makes your progress visible."
            )
        }
        if (isTowardGoal(w) && abs(w.weeklyPaceKg!!) in 0.1..1.0) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "Your weight is trending toward your goal",
                "A pace under 1 kg per week is steady and sustainable."
            )
        }
        if (distinctFoods >= 8) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "You logged $distinctFoods different foods",
                "A varied diet helps cover your vitamins and minerals."
            )
        }
        if (watch.isEmpty() && good.isEmpty()) {
            good += AnalysisInsight(
                InsightLevel.GOOD, "You are building the habit: $n days logged",
                "Keep logging and more insights will appear here."
            )
        }

        val watchShown = watch.take(3)
        val goodShown = good.take(5 - watchShown.size)
        return watchShown + goodShown
    }

    /** Coefficient of variation (std dev / mean). 0 when there is not enough data. */
    private fun variation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean <= 0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }

    private fun topFoods(cur: List<DayLog>): List<TopFood> {
        val counts = LinkedHashMap<String, Int>()
        val display = HashMap<String, String>()
        for (name in cur.flatMap { it.foods }) {
            val shown = name.trim()
            if (shown.isEmpty()) continue
            val key = shown.lowercase(Locale.ROOT)
            counts[key] = (counts[key] ?: 0) + 1
            display.putIfAbsent(key, shown)
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { TopFood(display[it.key] ?: it.key, it.value) }
    }

    private fun fmtInt(v: Double) = String.format(Locale.US, "%,d", v.roundToInt())
    private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)
}
