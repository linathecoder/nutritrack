package com.nutritrack.android

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nutritrack.android.data.local.WeightEntity
import com.nutritrack.android.databinding.ActivityWeightBinding
import com.nutritrack.android.databinding.ItemWeightBinding
import com.nutritrack.android.viewmodel.WeightStats
import com.nutritrack.android.viewmodel.WeightViewModel
import com.nutritrack.android.viewmodel.appViewModels
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Weight tracking: full CRUD on the weight history + calculated progress (trend, pace, ETA to goal). */
class WeightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeightBinding
    private val weightViewModel: WeightViewModel by appViewModels()

    private var selectedDate: LocalDate = LocalDate.now()
    private var editingDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWeightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.weightRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnWeightBack.setOnClickListener { finish() }
        binding.btnPickDate.setOnClickListener { pickDate() }
        binding.btnSaveWeight.setOnClickListener { save() }
        binding.btnCancelEdit.setOnClickListener { resetForm() }
        updateDateButton()
        observeViewModel()
        weightViewModel.refresh()
    }

    private fun observeViewModel() {
        weightViewModel.entries.observe(this) { renderHistory(it) }
        weightViewModel.targetKg.observe(this) {
            binding.weightChart.setData(weightViewModel.entries.value.orEmpty(), it)
        }
        weightViewModel.stats.observe(this) { renderStats(it) }
        weightViewModel.loading.observe(this) { loading ->
            binding.weightProgress.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSaveWeight.isEnabled = !loading
        }
        weightViewModel.message.observe(this) { event ->
            event.getContentIfNotHandled()?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
        weightViewModel.saved.observe(this) { event ->
            event.getContentIfNotHandled()?.let { resetForm() }
        }
    }

    private fun save() {
        val raw = binding.etWeight.text?.toString()?.trim()?.replace(',', '.').orEmpty()
        val value = raw.toDoubleOrNull()
        if (value == null || value < 20.0 || value > 400.0) {
            binding.tvWeightError.text = "Enter a weight between 20 and 400 kg."
            binding.tvWeightError.visibility = View.VISIBLE
            return
        }
        binding.tvWeightError.visibility = View.GONE

        val editing = editingDate
        if (editing != null) {
            weightViewModel.update(editing, value)
        } else {
            val date = if (selectedDate == LocalDate.now()) null else selectedDate.toString()
            weightViewModel.add(value, date)
        }
    }

    private fun resetForm() {
        editingDate = null
        selectedDate = LocalDate.now()
        binding.etWeight.setText("")
        binding.tvWeightError.visibility = View.GONE
        binding.tvFormTitle.text = "Log your weight"
        binding.btnSaveWeight.text = "Save weight"
        binding.btnCancelEdit.visibility = View.GONE
        binding.btnPickDate.isEnabled = true
        updateDateButton()
    }

    private fun startEditing(entry: WeightEntity) {
        editingDate = entry.date
        binding.etWeight.setText(fmt(entry.weightKg))
        binding.etWeight.setSelection(binding.etWeight.text?.length ?: 0)
        binding.tvFormTitle.text = "Edit entry"
        binding.btnSaveWeight.text = "Update weight"
        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.btnPickDate.isEnabled = false
        binding.btnPickDate.text = entry.date
    }

    private fun confirmDelete(entry: WeightEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this entry?")
            .setMessage("${fmt(entry.weightKg)} kg on ${entry.date} will be removed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> weightViewModel.delete(entry.date) }
            .show()
    }

    private fun pickDate() {
        val today = LocalDate.now()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                updateDateButton()
            },
            selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
        )
        dialog.datePicker.maxDate = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 86_399_000L
        dialog.datePicker.minDate = today.minusYears(2).plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dialog.show()
    }

    private fun updateDateButton() {
        binding.btnPickDate.text = if (selectedDate == LocalDate.now()) "Today" else selectedDate.toString()
    }

    private fun renderHistory(entries: List<WeightEntity>) {
        binding.weightChart.setData(entries, weightViewModel.targetKg.value)
        binding.historyContainer.removeAllViews()
        binding.tvHistoryEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val sorted = entries.sortedBy { it.date }
        val newestFirst = sorted.asReversed().take(MAX_ROWS)
        newestFirst.forEachIndexed { index, entry ->
            val item = ItemWeightBinding.inflate(layoutInflater, binding.historyContainer, false)
            item.tvEntryWeight.text = "${fmt(entry.weightKg)} kg"
            item.tvEntryDate.text = entry.date

            val previous = sorted.getOrNull(sorted.size - 2 - index)
            if (previous != null) {
                val delta = entry.weightKg - previous.weightKg
                item.tvEntryDelta.text = when {
                    abs(delta) < 0.05 -> "="
                    delta > 0 -> "▲ ${fmt(delta)}"
                    else -> "▼ ${fmt(-delta)}"
                }
            } else {
                item.tvEntryDelta.text = ""
            }
            item.btnEntryEdit.setOnClickListener { startEditing(entry) }
            item.btnEntryDelete.setOnClickListener { confirmDelete(entry) }
            binding.historyContainer.addView(item.root)
        }
    }

    private fun renderStats(stats: WeightStats?) {
        if (stats == null) {
            binding.tvCurrentWeight.text = "—"
            binding.tvChange.text = "—"
            binding.tvToGoal.text = "—"
            binding.tvPace.text = "—"
            binding.progressGoal.visibility = View.GONE
            binding.tvInsight.visibility = View.GONE
            binding.tvGoalProgress.text = "Log your first weight to see your progress."
            return
        }

        binding.tvCurrentWeight.text = "${fmt(stats.currentKg)} kg"
        binding.tvChange.text = signed(stats.changeKg) + " kg"
        binding.tvPace.text = stats.weeklyRateKg?.let { signed(it) + " kg/wk" } ?: "Need 7+ days"

        val remaining = stats.remainingKg
        binding.tvToGoal.text = when {
            stats.targetKg == null -> "No goal"
            remaining == null -> "—"
            abs(remaining) < 0.05 -> "Reached 🎉"
            remaining > 0 -> "${fmt(remaining)} kg to lose"
            else -> "${fmt(-remaining)} kg to gain"
        }

        val progress = stats.progressPercent
        if (progress != null) {
            binding.progressGoal.visibility = View.VISIBLE
            binding.progressGoal.setProgressCompat(progress, true)
            binding.tvGoalProgress.text = "$progress% of the way to your goal of ${fmt(stats.targetKg ?: 0.0)} kg"
        } else {
            binding.progressGoal.visibility = View.GONE
            binding.tvGoalProgress.text = "Set a target weight in your profile to track your goal."
        }

        val insight = when {
            stats.estimatedWeeksToGoal != null ->
                "At your current pace you'll reach your goal in about ${stats.estimatedWeeksToGoal} week(s)."
            remaining != null && abs(remaining) >= 0.05 && stats.weeklyRateKg != null &&
                stats.weeklyRateKg != 0.0 && (remaining > 0) != (stats.weeklyRateKg < 0) ->
                "Your recent trend is moving away from your goal."
            else -> null
        }
        binding.tvInsight.visibility = if (insight == null) View.GONE else View.VISIBLE
        binding.tvInsight.text = insight.orEmpty()
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun signed(v: Double) = (if (v > 0.049) "+" else "") + fmt(v)

    private companion object {
        const val MAX_ROWS = 60
    }
}
