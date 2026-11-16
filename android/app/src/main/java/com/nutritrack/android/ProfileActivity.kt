package com.nutritrack.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.ProfileCalculationRequest
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.session.SessionManager
import com.google.android.material.textfield.TextInputEditText
import coil.load
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var name: TextInputEditText
    private lateinit var age: TextInputEditText
    private lateinit var height: TextInputEditText
    private lateinit var weight: TextInputEditText
    private lateinit var targetWeight: TextInputEditText
    private lateinit var sex: RadioGroup
    private lateinit var activity: RadioGroup
    private lateinit var goal: RadioGroup
    private lateinit var workouts: android.widget.Spinner
    private lateinit var weightUnit: android.widget.Spinner
    private var weightUnitLoaded = false
    private var currentWeightUnitPosition = 0
    private val kgToLb = 2.2046226218
    private lateinit var saveButton: android.widget.Button
    private lateinit var progress: View
    private var original: ProfileCalculationResponse? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) saveProfilePhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        session = SessionManager(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        name = findViewById(R.id.etProfileName)
        age = findViewById(R.id.etProfileAge)
        height = findViewById(R.id.etProfileHeight)
        weight = findViewById(R.id.etProfileWeight)
        targetWeight = findViewById(R.id.etProfileTargetWeight)
        sex = findViewById(R.id.rgSex)
        activity = findViewById(R.id.rgActivity)
        goal = findViewById(R.id.rgGoal)
        workouts = findViewById(R.id.spinnerWorkouts)
        weightUnit = findViewById(R.id.spinnerWeightUnit)
        saveButton = findViewById(R.id.btnSaveProfile)
        progress = findViewById(R.id.profileProgress)

        findViewById<android.widget.TextView>(R.id.tvProfileEmail).text = session.getEmail().orEmpty()
        setupSpinner(workouts, listOf("None", "1–2 times / week", "3–4 times / week", "5–6 times / week"))
        setupSpinner(weightUnit, listOf("Kilograms (kg)", "Pounds (lb)"))
        val savedUnit = getSharedPreferences("profile_preferences", MODE_PRIVATE)
            .getString("weight_unit", "kg") ?: "kg"
        weightUnit.setSelection(if (savedUnit == "lb") 1 else 0)
        weightUnit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (weightUnitLoaded && position != currentWeightUnitPosition) {
                    val oldFactor = if (currentWeightUnitPosition == 1) kgToLb else 1.0
                    val newFactor = if (position == 1) kgToLb else 1.0
                    convertDisplayedWeight(weight, oldFactor, newFactor)
                    convertDisplayedWeight(targetWeight, oldFactor, newFactor)
                }
                currentWeightUnitPosition = position
                updateWeightLabels()
                if (weightUnitLoaded) getSharedPreferences("profile_preferences", MODE_PRIVATE)
                    .edit().putString("weight_unit", if (position == 1) "lb" else "kg").apply()
            }
        }

        findViewById<View>(R.id.btnProfileBack).setOnClickListener { finish() }
        findViewById<View>(R.id.profileAvatarFrame).setOnClickListener { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        loadProfilePhoto()
        findViewById<View>(R.id.btnLogout).setOnClickListener { logout() }
        saveButton.setOnClickListener { saveProfile() }

        val content = findViewById<View>(R.id.profileContent)
        content.alpha = 0f
        content.translationY = dp(16).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(360)
            .setInterpolator(DecelerateInterpolator()).start()

        loadProfile()
    }

    private fun profilePhotoFile() = File(filesDir, "profile_picture_${session.getEmail().orEmpty().lowercase(Locale.US).hashCode()}.jpg")

    private fun loadProfilePhoto() {
        val file = profilePhotoFile()
        val photo = findViewById<android.widget.ImageView>(R.id.ivProfilePhoto)
        val placeholder = findViewById<android.widget.ImageView>(R.id.ivProfilePlaceholder)
        if (file.exists()) {
            photo.load(file) {
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
            }
            photo.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
        } else {
            photo.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
        }
    }

    private fun saveProfilePhoto(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected image")
            input.use { source ->
                FileOutputStream(profilePhotoFile()).use { destination -> source.copyTo(destination) }
            }
            loadProfilePhoto()
            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save this picture. Please try another image.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSpinner(spinner: android.widget.Spinner, items: List<String>) {
        spinner.adapter = ArrayAdapter(this, R.layout.spinner_profile_item, items).also {
            it.setDropDownViewResource(R.layout.spinner_profile_dropdown)
        }
    }

    private fun loadProfile() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.getProfile()
                val profile = response.body()
                if (response.isSuccessful && profile != null) {
                    original = profile
                    name.setText(profile.name.orEmpty())
                    findViewById<android.widget.TextView>(R.id.tvProfileName).text =
                        profile.name?.takeIf { it.isNotBlank() } ?: "NutriTrack member"
                    age.setText(profile.age?.toString().orEmpty())
                    height.setText(profile.heightCm?.let { fmt(it) }.orEmpty())
                    weight.setText(profile.weightKg?.let { formatWeight(it) }.orEmpty())
                    targetWeight.setText(profile.targetWeightKg?.let { formatWeight(it) }.orEmpty())
                    currentWeightUnitPosition = weightUnit.selectedItemPosition
                    weightUnitLoaded = true
                    updateWeightLabels()

                    sex.check(if (profile.sex.equals("FEMALE", true)) R.id.rbFemale else R.id.rbMale)
                    activity.check(when (profile.activityLevel?.uppercase(Locale.US)) {
                        "ACTIVE", "VERY_ACTIVE", "EXTREMELY_ACTIVE" -> R.id.rbActive
                        "MODERATE", "LIGHT", "LIGHTLY_ACTIVE" -> R.id.rbModerate
                        else -> R.id.rbSedentary
                    })
                    goal.check(when (profile.goal?.uppercase(Locale.US)) {
                        "MAINTENANCE", "MAINTAIN_WEIGHT" -> R.id.rbMaintain
                        "MUSCLE_GAIN", "BUILD_MUSCLE" -> R.id.rbBuildMuscle
                        else -> R.id.rbLoseWeight
                    })
                    select(workouts, when (profile.workoutFrequency?.uppercase(Locale.US)) {
                        "ONE_TWO" -> 1
                        "THREE_FOUR" -> 2
                        "FIVE_SIX" -> 3
                        else -> 0
                    })
                    displayNutrition(profile)
                } else {
                    Toast.makeText(this@ProfileActivity, "Couldn't load your profile.", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun displayNutrition(profile: ProfileCalculationResponse) {
        findViewById<android.widget.TextView>(R.id.tvTargetCalories).text =
            profile.calories?.let { "${it} kcal" } ?: "— kcal"
        findViewById<android.widget.TextView>(R.id.tvMaintenanceCalories).text =
            profile.tdee?.let { "${it.toInt()} kcal" } ?: "— kcal"

        setMacro(R.id.proteinMacro, "Protein", profile.proteinG)
        setMacro(R.id.carbsMacro, "Carbs", profile.carbsG)
        setMacro(R.id.fatMacro, "Fat", profile.fatG)
    }

    private fun setMacro(containerId: Int, label: String, value: Double?) {
        val container = findViewById<View>(containerId)
        container.findViewById<android.widget.TextView>(R.id.macroLabel).text = label
        container.findViewById<android.widget.TextView>(R.id.macroValue).text =
            value?.let { "${it.toInt()} g" } ?: "— g"
    }

    private fun saveProfile() {
        val ageValue = age.text?.toString()?.toIntOrNull()
        val heightValue = height.text?.toString()?.toDoubleOrNull()
        val enteredWeight = weight.text?.toString()?.toDoubleOrNull()
        val enteredTarget = targetWeight.text?.toString()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val factor = if (weightUnit.selectedItemPosition == 1) kgToLb else 1.0
        val weightValue = enteredWeight?.div(factor)
        val targetValue = enteredTarget?.div(factor)

        if (name.text.isNullOrBlank() || ageValue == null || ageValue !in 13..100 ||
            heightValue == null || heightValue !in 100.0..250.0 ||
            weightValue == null || weightValue !in 25.0..350.0 ||
            (targetWeight.text?.isNotBlank() == true && (targetValue == null || targetValue !in 25.0..350.0))) {
            Toast.makeText(this, "Check your name, age, height and weight values.", Toast.LENGTH_LONG).show()
            return
        }

        val sexValue = if (sex.checkedRadioButtonId == R.id.rbFemale) "FEMALE" else "MALE"
        val activityValue = when (activity.checkedRadioButtonId) {
            R.id.rbModerate -> "MODERATE"
            R.id.rbActive -> "ACTIVE"
            else -> "SEDENTARY"
        }
        val goalValue = when (goal.checkedRadioButtonId) {
            R.id.rbMaintain -> "MAINTENANCE"
            R.id.rbBuildMuscle -> "MUSCLE_GAIN"
            else -> "WEIGHT_LOSS"
        }
        val workoutValue = listOf("NONE", "ONE_TWO", "THREE_FOUR", "FIVE_SIX")
            .getOrElse(workouts.selectedItemPosition) { "NONE" }

        val request = ProfileCalculationRequest(
            name = name.text.toString().trim(),
            sex = sexValue,
            age = ageValue,
            heightCm = heightValue,
            weightKg = weightValue,
            activityLevel = activityValue,
            workoutFrequency = workoutValue,
            goal = goalValue,
            targetWeightKg = targetValue
        )

        saveButton.isEnabled = false
        saveButton.text = "Saving…"
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.api.saveProfile(request)
                val updated = response.body()
                if (response.isSuccessful && updated != null) {
                    original = updated
                    session.saveProfileSummary(updated.name, updated.calories ?: 0)
                    Toast.makeText(this@ProfileActivity, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ProfileActivity, "Couldn't save changes. Please try again.", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                progress.visibility = View.GONE
                saveButton.isEnabled = true
                saveButton.text = "Save changes"
            }
        }
    }

    private fun logout() {
        session.clearSession()
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(R.anim.profile_exit, R.anim.home_stay)
        finish()
    }

    private fun select(spinner: android.widget.Spinner, index: Int) {
        spinner.setSelection(index.coerceIn(0, spinner.adapter.count - 1))
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.1f", value)

    private fun formatWeight(valueKg: Double): String =
        fmt(if (weightUnit.selectedItemPosition == 1) valueKg * kgToLb else valueKg)

    private fun convertDisplayedWeight(field: TextInputEditText, oldFactor: Double, newFactor: Double) {
        val value = field.text?.toString()?.toDoubleOrNull() ?: return
        field.setText(fmt(value * newFactor / oldFactor))
    }

    private fun updateWeightLabels() {
        val unit = if (weightUnit.selectedItemPosition == 1) "lb" else "kg"
        findViewById<android.widget.TextView>(R.id.tvCurrentWeightLabel).text = "Current weight ($unit)"
        findViewById<android.widget.TextView>(R.id.tvTargetWeightLabel).text = "Target weight ($unit)"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.home_stay, R.anim.profile_exit)
    }
}
