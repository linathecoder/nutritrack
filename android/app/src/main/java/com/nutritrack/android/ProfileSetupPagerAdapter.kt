package com.nutritrack.android

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import kotlin.math.roundToInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.databinding.StepActivityBinding
import com.nutritrack.android.databinding.StepBirthdateBinding
import com.nutritrack.android.databinding.StepGoalBinding
import com.nutritrack.android.databinding.StepHeightBinding
import com.nutritrack.android.databinding.StepNameBinding
import com.nutritrack.android.databinding.StepResultsBinding
import com.nutritrack.android.databinding.StepSexBinding
import com.nutritrack.android.databinding.StepWeightBinding
import com.nutritrack.android.databinding.StepWorkoutsBinding

class ProfileSetupPagerAdapter(
    private val context: Context,
    private val inflater: LayoutInflater
) : RecyclerView.Adapter<ProfileSetupPagerAdapter.StepViewHolder>() {

    companion object {
        const val STEP_NAME = 0
        const val STEP_SEX = 1
        const val STEP_BIRTHDATE = 2
        const val STEP_HEIGHT = 3
        const val STEP_WEIGHT = 4
        const val STEP_ACTIVITY = 5
        const val STEP_WORKOUTS = 6
        const val STEP_GOAL = 7
        const val STEP_RESULTS = 8
        const val TOTAL_STEPS = 9

        private val MONTHS = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
    }

    sealed class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun hasValidSelection(): Boolean

        class NameHolder(val binding: StepNameBinding) : StepViewHolder(binding.root) {
            override fun hasValidSelection() = binding.etName.text?.isNotBlank() == true
            fun getName() = binding.etName.text?.toString()?.trim() ?: ""
        }

        class SexHolder(val binding: StepSexBinding) : StepViewHolder(binding.root) {
            var selectedSex: String? = null
            override fun hasValidSelection() = selectedSex != null
            fun getSex() = selectedSex ?: ""
        }

        class BirthdateHolder(val binding: StepBirthdateBinding) : StepViewHolder(binding.root) {
            override fun hasValidSelection() = true
            fun getMonth() = binding.pickerMonth.value + 1
            fun getDay()   = binding.pickerDay.value
            fun getYear()  = binding.pickerYear.value
        }

        class HeightHolder(val binding: StepHeightBinding) : StepViewHolder(binding.root) {
            var useImperial = true
            override fun hasValidSelection() = true
            fun getHeightCm(): Double {
                return if (useImperial) {
                    val feet   = binding.pickerFeet.value
                    val inches = binding.pickerInches.value
                    (feet * 30.48) + (inches * 2.54)
                } else {
                    binding.pickerCm.value.toDouble()
                }
            }
        }

        class WeightHolder(val binding: StepWeightBinding) : StepViewHolder(binding.root) {
            var useImperial = true
            override fun hasValidSelection() = true
            fun getWeightKg(): Double {
                return if (useImperial) {
                    binding.pickerLb.value / 2.20462
                } else {
                    binding.pickerKg.value.toDouble()
                }
            }
        }

        class ActivityHolder(val binding: StepActivityBinding) : StepViewHolder(binding.root) {
            var selectedActivity: String? = null
            override fun hasValidSelection() = selectedActivity != null
            fun getActivity() = selectedActivity ?: ""
        }

        class WorkoutsHolder(val binding: StepWorkoutsBinding) : StepViewHolder(binding.root) {
            var selectedWorkouts: String? = null
            override fun hasValidSelection() = selectedWorkouts != null
            fun getWorkouts() = selectedWorkouts ?: ""
        }

        class GoalHolder(val binding: StepGoalBinding) : StepViewHolder(binding.root) {
            var selectedGoal: String? = null
            override fun hasValidSelection() = selectedGoal != null
            fun getGoal() = selectedGoal ?: ""
        }

        class ResultsHolder(val binding: StepResultsBinding) : StepViewHolder(binding.root) {
            override fun hasValidSelection() = true
        }
    }

    private val holders = arrayOfNulls<StepViewHolder>(TOTAL_STEPS)

    private var results: ProfileCalculationResponse? = null

    override fun getItemCount() = TOTAL_STEPS

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        return when (viewType) {
            STEP_NAME -> {
                val b = StepNameBinding.inflate(inflater, parent, false)
                StepViewHolder.NameHolder(b)
            }
            STEP_SEX -> {
                val b = StepSexBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.SexHolder(b)
                bindSexStep(h)
                h
            }
            STEP_BIRTHDATE -> {
                val b = StepBirthdateBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.BirthdateHolder(b)
                bindBirthdateStep(h)
                h
            }
            STEP_HEIGHT -> {
                val b = StepHeightBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.HeightHolder(b)
                bindHeightStep(h)
                h
            }
            STEP_WEIGHT -> {
                val b = StepWeightBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.WeightHolder(b)
                bindWeightStep(h)
                h
            }
            STEP_ACTIVITY -> {
                val b = StepActivityBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.ActivityHolder(b)
                bindActivityStep(h)
                h
            }
            STEP_WORKOUTS -> {
                val b = StepWorkoutsBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.WorkoutsHolder(b)
                bindWorkoutsStep(h)
                h
            }
            STEP_GOAL -> {
                val b = StepGoalBinding.inflate(inflater, parent, false)
                val h = StepViewHolder.GoalHolder(b)
                bindGoalStep(h)
                h
            }
            else -> {
                val b = StepResultsBinding.inflate(inflater, parent, false)
                StepViewHolder.ResultsHolder(b)
            }
        }
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holders[position] = holder
        if (holder is StepViewHolder.ResultsHolder) bindResultsStep(holder)
    }

    fun setResults(response: ProfileCalculationResponse) {
        results = response
        notifyItemChanged(STEP_RESULTS)
    }

    fun hasValidSelection(step: Int) = holders[step]?.hasValidSelection() ?: false
    fun getName()     = (holders[STEP_NAME]     as? StepViewHolder.NameHolder)?.getName()     ?: ""
    fun getSex()      = (holders[STEP_SEX]      as? StepViewHolder.SexHolder)?.getSex()      ?: ""
    fun getBirthMonth()= (holders[STEP_BIRTHDATE] as? StepViewHolder.BirthdateHolder)?.getMonth() ?: 1
    fun getBirthDay()  = (holders[STEP_BIRTHDATE] as? StepViewHolder.BirthdateHolder)?.getDay()   ?: 1
    fun getBirthYear() = (holders[STEP_BIRTHDATE] as? StepViewHolder.BirthdateHolder)?.getYear()  ?: 2000
    fun getAge() = computeAge(getBirthYear(), getBirthMonth(), getBirthDay())
    fun getHeightCm() = (holders[STEP_HEIGHT]   as? StepViewHolder.HeightHolder)?.getHeightCm() ?: 170.0
    fun getWeightKg() = (holders[STEP_WEIGHT]   as? StepViewHolder.WeightHolder)?.getWeightKg() ?: 70.0
    fun getActivity() = (holders[STEP_ACTIVITY] as? StepViewHolder.ActivityHolder)?.getActivity() ?: ""
    fun getWorkouts() = (holders[STEP_WORKOUTS] as? StepViewHolder.WorkoutsHolder)?.getWorkouts() ?: ""
    fun getGoal()     = (holders[STEP_GOAL]     as? StepViewHolder.GoalHolder)?.getGoal()     ?: ""

    private fun bindSexStep(h: StepViewHolder.SexHolder) {
        val b = h.binding
        fun selectFemale() {
            h.selectedSex = "FEMALE"
            b.cardFemale.setBackgroundResource(R.drawable.bg_card_selected)
            b.ivRadioFemale.setImageResource(R.drawable.ic_radio_selected)
            b.cardMale.setBackgroundResource(R.drawable.bg_card_unselected)
            b.ivRadioMale.setImageResource(R.drawable.ic_radio_unselected)
        }
        fun selectMale() {
            h.selectedSex = "MALE"
            b.cardMale.setBackgroundResource(R.drawable.bg_card_selected)
            b.ivRadioMale.setImageResource(R.drawable.ic_radio_selected)
            b.cardFemale.setBackgroundResource(R.drawable.bg_card_unselected)
            b.ivRadioFemale.setImageResource(R.drawable.ic_radio_unselected)
        }
        b.cardFemale.setOnClickListener { selectFemale() }
        b.cardMale.setOnClickListener   { selectMale()   }
    }

    private fun bindBirthdateStep(h: StepViewHolder.BirthdateHolder) {
        val b = h.binding
        b.pickerMonth.minValue = 0
        b.pickerMonth.maxValue = 11
        b.pickerMonth.displayedValues = MONTHS
        b.pickerMonth.value = 5

        b.pickerDay.minValue = 1
        b.pickerDay.maxValue = 31
        b.pickerDay.value = 15

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        b.pickerYear.minValue = currentYear - 100
        b.pickerYear.maxValue = currentYear - 5
        b.pickerYear.value = currentYear - 28

        b.pickerMonth.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        b.pickerDay.descendantFocusability   = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        b.pickerYear.descendantFocusability  = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        val listener = NumberPicker.OnValueChangeListener { _, _, _ -> refreshBirthdate(h) }
        b.pickerMonth.setOnValueChangedListener(listener)
        b.pickerDay.setOnValueChangedListener(listener)
        b.pickerYear.setOnValueChangedListener(listener)
        refreshBirthdate(h)
    }

    private fun refreshBirthdate(h: StepViewHolder.BirthdateHolder) {
        val b = h.binding
        val year  = b.pickerYear.value
        val month = b.pickerMonth.value + 1
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
        if (b.pickerDay.maxValue != daysInMonth) {
            b.pickerDay.maxValue = daysInMonth
        }
        val age = computeAge(year, month, b.pickerDay.value)
        b.tvAgeDisplay.text = if (age == 1) "1 year old" else "$age years old"
    }

    private fun computeAge(year: Int, month: Int, day: Int): Int {
        val safeDay = day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth())
        return Period.between(LocalDate.of(year, month, safeDay), LocalDate.now()).years
    }

    private fun bindHeightStep(h: StepViewHolder.HeightHolder) {
        val b = h.binding

        b.pickerFeet.minValue = 3
        b.pickerFeet.maxValue = 8
        b.pickerFeet.value = 5
        b.pickerFeet.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        b.pickerInches.minValue = 0
        b.pickerInches.maxValue = 11
        b.pickerInches.value = 7
        b.pickerInches.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        b.pickerCm.minValue = 100
        b.pickerCm.maxValue = 250
        b.pickerCm.value = 170
        b.pickerCm.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        b.layoutImperial.visibility = View.VISIBLE
        b.layoutCm.visibility       = View.GONE

        b.btnFeetInches.setOnClickListener {
            if (!h.useImperial) {
                h.useImperial = true
                b.layoutImperial.visibility = View.VISIBLE
                b.layoutCm.visibility       = View.GONE
                b.btnFeetInches.setBackgroundResource(R.drawable.bg_unit_toggle_active)
                b.btnCentimeters.setBackgroundResource(R.drawable.bg_unit_toggle_bg)
                b.btnFeetInches.setTextColor(context.getColor(R.color.text_slate_900))
                b.btnCentimeters.setTextColor(context.getColor(R.color.text_slate_500))
            }
        }
        b.btnCentimeters.setOnClickListener {
            if (h.useImperial) {
                h.useImperial = false
                b.layoutImperial.visibility = View.GONE
                b.layoutCm.visibility       = View.VISIBLE
                b.btnCentimeters.setBackgroundResource(R.drawable.bg_unit_toggle_active)
                b.btnFeetInches.setBackgroundResource(R.drawable.bg_unit_toggle_bg)
                b.btnCentimeters.setTextColor(context.getColor(R.color.text_slate_900))
                b.btnFeetInches.setTextColor(context.getColor(R.color.text_slate_500))
            }
        }
    }

    private fun bindWeightStep(h: StepViewHolder.WeightHolder) {
        val b = h.binding

        b.pickerLb.minValue = 50
        b.pickerLb.maxValue = 600
        b.pickerLb.value = 183
        b.pickerLb.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        b.pickerKg.minValue = 20
        b.pickerKg.maxValue = 300
        b.pickerKg.value = 83
        b.pickerKg.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        b.layoutPounds.visibility = View.VISIBLE
        b.layoutKg.visibility     = View.GONE

        b.btnPounds.setOnClickListener {
            if (!h.useImperial) {
                h.useImperial = true
                b.layoutPounds.visibility = View.VISIBLE
                b.layoutKg.visibility     = View.GONE
                b.btnPounds.setBackgroundResource(R.drawable.bg_unit_toggle_active)
                b.btnKilograms.setBackgroundResource(R.drawable.bg_unit_toggle_bg)
                b.btnPounds.setTextColor(context.getColor(R.color.text_slate_900))
                b.btnKilograms.setTextColor(context.getColor(R.color.text_slate_500))
            }
        }
        b.btnKilograms.setOnClickListener {
            if (h.useImperial) {
                h.useImperial = false
                b.layoutPounds.visibility = View.GONE
                b.layoutKg.visibility     = View.VISIBLE
                b.btnKilograms.setBackgroundResource(R.drawable.bg_unit_toggle_active)
                b.btnPounds.setBackgroundResource(R.drawable.bg_unit_toggle_bg)
                b.btnKilograms.setTextColor(context.getColor(R.color.text_slate_900))
                b.btnPounds.setTextColor(context.getColor(R.color.text_slate_500))
            }
        }
    }

    private fun bindActivityStep(h: StepViewHolder.ActivityHolder) {
        val b = h.binding
        fun select(value: String, active: ConstraintLayout, others: List<ConstraintLayout>,
                   activeRadio: ImageView, otherRadios: List<ImageView>) {
            h.selectedActivity = value
            active.setBackgroundResource(R.drawable.bg_card_selected)
            activeRadio.setImageResource(R.drawable.ic_radio_selected)
            others.forEach { it.setBackgroundResource(R.drawable.bg_card_unselected) }
            otherRadios.forEach { it.setImageResource(R.drawable.ic_radio_unselected) }
        }
        b.cardSedentary.setOnClickListener {
            select("SEDENTARY", b.cardSedentary,
                listOf(b.cardModerate, b.cardActive),
                b.ivRadioSedentary, listOf(b.ivRadioModerate, b.ivRadioActive))
        }
        b.cardModerate.setOnClickListener {
            select("MODERATE", b.cardModerate,
                listOf(b.cardSedentary, b.cardActive),
                b.ivRadioModerate, listOf(b.ivRadioSedentary, b.ivRadioActive))
        }
        b.cardActive.setOnClickListener {
            select("ACTIVE", b.cardActive,
                listOf(b.cardSedentary, b.cardModerate),
                b.ivRadioActive, listOf(b.ivRadioSedentary, b.ivRadioModerate))
        }
    }

    private fun bindWorkoutsStep(h: StepViewHolder.WorkoutsHolder) {
        val b = h.binding
        val options = listOf(
            Triple(b.cardWorkoutNone,      b.ivRadioWorkoutNone,      "NONE"),
            Triple(b.cardWorkoutOneTwo,    b.ivRadioWorkoutOneTwo,    "ONE_TWO"),
            Triple(b.cardWorkoutThreeFour, b.ivRadioWorkoutThreeFour, "THREE_FOUR"),
            Triple(b.cardWorkoutFiveSix,   b.ivRadioWorkoutFiveSix,   "FIVE_SIX")
        )
        options.forEach { (card, _, value) ->
            card.setOnClickListener {
                h.selectedWorkouts = value
                options.forEach { (otherCard, otherRadio, _) ->
                    val selected = otherCard === card
                    otherCard.setBackgroundResource(
                        if (selected) R.drawable.bg_card_selected else R.drawable.bg_card_unselected
                    )
                    otherRadio.setImageResource(
                        if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected
                    )
                }
            }
        }
    }

    private fun bindGoalStep(h: StepViewHolder.GoalHolder) {
        val b = h.binding
        fun select(value: String, active: ConstraintLayout, others: List<ConstraintLayout>,
                   activeRadio: ImageView, otherRadios: List<ImageView>) {
            h.selectedGoal = value
            active.setBackgroundResource(R.drawable.bg_card_selected)
            activeRadio.setImageResource(R.drawable.ic_radio_selected)
            others.forEach { it.setBackgroundResource(R.drawable.bg_card_unselected) }
            otherRadios.forEach { it.setImageResource(R.drawable.ic_radio_unselected) }
        }
        b.cardWeightLoss.setOnClickListener {
            select("WEIGHT_LOSS", b.cardWeightLoss,
                listOf(b.cardMaintain, b.cardMuscleGain),
                b.ivRadioWeightLoss, listOf(b.ivRadioMaintain, b.ivRadioMuscleGain))
        }
        b.cardMaintain.setOnClickListener {
            select("MAINTENANCE", b.cardMaintain,
                listOf(b.cardWeightLoss, b.cardMuscleGain),
                b.ivRadioMaintain, listOf(b.ivRadioWeightLoss, b.ivRadioMuscleGain))
        }
        b.cardMuscleGain.setOnClickListener {
            select("MUSCLE_GAIN", b.cardMuscleGain,
                listOf(b.cardWeightLoss, b.cardMaintain),
                b.ivRadioMuscleGain, listOf(b.ivRadioWeightLoss, b.ivRadioMaintain))
        }
    }

    private fun bindResultsStep(h: StepViewHolder.ResultsHolder) {
        val r = results ?: return
        val b = h.binding

        val calories = r.calories ?: 0
        val protein  = r.proteinG ?: 0.0
        val carbs    = r.carbsG ?: 0.0
        val fat      = r.fatG ?: 0.0

        b.tvResultsTitle.text =
            if (r.name.isNullOrBlank()) "Your plan is ready" else "You're all set, ${r.name}!"

        b.tvCaloriesValue.text = NumberFormat.getIntegerInstance().format(calories)
        b.tvProteinValue.text  = "${protein.roundToInt()} g"
        b.tvCarbsValue.text    = "${carbs.roundToInt()} g"
        b.tvFatValue.text      = "${fat.roundToInt()} g"

        if (calories > 0) {
            b.tvProteinPct.text = "${(protein * 4 * 100 / calories).roundToInt()}% of kcal"
            b.tvCarbsPct.text   = "${(carbs * 4 * 100 / calories).roundToInt()}% of kcal"
            b.tvFatPct.text     = "${(fat * 9 * 100 / calories).roundToInt()}% of kcal"
        }

        b.tvExplanation.text = buildExplanation(r)
    }

    private fun buildExplanation(r: ProfileCalculationResponse): String {
        val nf = NumberFormat.getIntegerInstance()
        val bmr  = (r.bmr ?: 0.0).roundToInt()
        val tdee = (r.tdee ?: 0.0).roundToInt()

        val workoutText = when (r.workoutFrequency) {
            "ONE_TWO"    -> "1–2 workouts a week"
            "THREE_FOUR" -> "3–4 workouts a week"
            "FIVE_SIX"   -> "5–6 workouts a week"
            else         -> "no workouts"
        }

        val burn = "At rest, your body burns about ${nf.format(bmr)} kcal a day (your BMR). " +
                "Adding your daily activity and your training ($workoutText), " +
                "you burn about ${nf.format(tdee)} kcal in total."

        val goalText = when (r.goal) {
            "WEIGHT_LOSS" -> {
                val floorNote = if (tdee - 500 < 1200) " We never go below 1,200 kcal a day." else ""
                "Because your goal is weight loss, we subtract 500 kcal for a steady, " +
                        "sustainable deficit.$floorNote"
            }
            "MUSCLE_GAIN" -> "Because your goal is muscle gain, we add 350 kcal to fuel growth."
            else          -> "Because your goal is maintenance, we match your calories to what you burn."
        }

        val weight = r.weightKg ?: 0.0
        val proteinPerKg = if (weight > 0) (r.proteinG ?: 0.0) / weight else 0.0
        val proteinText = if (proteinPerKg > 0) {
            "about ${String.format(Locale.US, "%.1f", proteinPerKg)} g per kg of body weight " +
                    "(the more you train, the more you need)"
        } else {
            "based on your body weight and training"
        }
        val macros = "Protein is set at $proteinText to support your muscles, " +
                "fat provides 25% of your calories, and carbs fill the rest to give you energy."

        val disclaimer = "These are estimates, not medical advice. You can adjust them as you track your progress."

        return listOf(burn, goalText, macros, disclaimer).joinToString("\n\n")
    }
}
