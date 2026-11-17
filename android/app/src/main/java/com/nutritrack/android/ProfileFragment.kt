package com.nutritrack.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.ProfileCalculationRequest
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.model.WeightRequest
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.databinding.DialogLogWeightBinding
import com.nutritrack.android.databinding.FragmentProfileBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private var originalProfile: ProfileCalculationResponse? = null

    private var weightUnitLoaded = false
    private var currentWeightUnitPosition = 0
    private val kgToLb = 2.2046226218

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) saveProfilePhoto(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())

        setupSpinners()
        setupListeners()
        loadProfilePhoto()
        loadProfileData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSpinners() {
        val workouts = listOf("None", "1–2 times / week", "3–4 times / week", "5–6 times / week")
        binding.spinnerWorkouts.adapter = ArrayAdapter(requireContext(), R.layout.spinner_profile_item, workouts).also {
            it.setDropDownViewResource(R.layout.spinner_profile_dropdown)
        }

        val weightUnits = listOf("Kilograms (kg)", "Pounds (lb)")
        binding.spinnerWeightUnit.adapter = ArrayAdapter(requireContext(), R.layout.spinner_profile_item, weightUnits).also {
            it.setDropDownViewResource(R.layout.spinner_profile_dropdown)
        }

        val savedUnit = requireActivity().getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
            .getString("weight_unit", "kg") ?: "kg"
        binding.spinnerWeightUnit.setSelection(if (savedUnit == "lb") 1 else 0)

        binding.spinnerWeightUnit.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (weightUnitLoaded && position != currentWeightUnitPosition) {
                    val oldFactor = if (currentWeightUnitPosition == 1) kgToLb else 1.0
                    val newFactor = if (position == 1) kgToLb else 1.0
                    convertDisplayedWeight(binding.etProfileWeight, oldFactor, newFactor)
                    convertDisplayedWeight(binding.etProfileTargetWeight, oldFactor, newFactor)
                }
                currentWeightUnitPosition = position
                updateWeightLabels()
                if (weightUnitLoaded) {
                    requireActivity().getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
                        .edit().putString("weight_unit", if (position == 1) "lb" else "kg").apply()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.tvProfileEmail.text = session.getEmail().orEmpty()

        binding.profileAvatarFrame.setOnClickListener {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnLogWeightEntry.setOnClickListener {
            showLogWeightDialog()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfileChanges()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadProfileData() {
        binding.profileProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.getProfile()
                val profile = response.body()
                if (response.isSuccessful && profile != null) {
                    originalProfile = profile
                    populateFields(profile)
                } else {
                    Toast.makeText(requireContext(), "Couldn't load profile.", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _binding?.profileProgress?.visibility = View.GONE
            }
        }
    }

    private fun populateFields(profile: ProfileCalculationResponse) {
        binding.etProfileName.setText(profile.name.orEmpty())
        binding.tvProfileName.text = profile.name?.takeIf { it.isNotBlank() } ?: "NutriTrack member"
        binding.etProfileAge.setText(profile.age?.toString().orEmpty())
        binding.etProfileHeight.setText(profile.heightCm?.let { fmt(it) }.orEmpty())
        binding.etProfileWeight.setText(profile.weightKg?.let { formatWeight(it) }.orEmpty())
        binding.etProfileTargetWeight.setText(profile.targetWeightKg?.let { formatWeight(it) }.orEmpty())

        currentWeightUnitPosition = binding.spinnerWeightUnit.selectedItemPosition
        weightUnitLoaded = true
        updateWeightLabels()

        binding.rgSex.check(if (profile.sex.equals("FEMALE", true)) R.id.rbFemale else R.id.rbMale)
        binding.rgActivity.check(when (profile.activityLevel?.uppercase(Locale.US)) {
            "ACTIVE", "VERY_ACTIVE", "EXTREMELY_ACTIVE" -> R.id.rbActive
            "MODERATE", "LIGHT", "LIGHTLY_ACTIVE" -> R.id.rbModerate
            else -> R.id.rbSedentary
        })
        binding.rgGoal.check(when (profile.goal?.uppercase(Locale.US)) {
            "MAINTENANCE", "MAINTAIN_WEIGHT" -> R.id.rbMaintain
            "MUSCLE_GAIN", "BUILD_MUSCLE" -> R.id.rbBuildMuscle
            else -> R.id.rbLoseWeight
        })
        binding.spinnerWorkouts.setSelection(when (profile.workoutFrequency?.uppercase(Locale.US)) {
            "ONE_TWO" -> 1
            "THREE_FOUR" -> 2
            "FIVE_SIX" -> 3
            else -> 0
        })

        displayNutritionTargets(profile)
    }

    private fun displayNutritionTargets(profile: ProfileCalculationResponse) {
        binding.tvTargetCalories.text = profile.calories?.let { "$it kcal" } ?: "— kcal"
        binding.tvMaintenanceCalories.text = profile.tdee?.let { "${it.toInt()} kcal" } ?: "— kcal"

        setMacro(binding.proteinMacro.root, "Protein", profile.proteinG)
        setMacro(binding.carbsMacro.root, "Carbs", profile.carbsG)
        setMacro(binding.fatMacro.root, "Fat", profile.fatG)
    }

    private fun setMacro(container: View, label: String, value: Double?) {
        container.findViewById<android.widget.TextView>(R.id.macroLabel).text = label
        container.findViewById<android.widget.TextView>(R.id.macroValue).text =
            value?.let { "${it.toInt()} g" } ?: "— g"
    }

    // Task 4: Add a weight entry
    private fun showLogWeightDialog() {
        val dialogBinding = DialogLogWeightBinding.inflate(layoutInflater)
        val isLb = binding.spinnerWeightUnit.selectedItemPosition == 1
        val unit = if (isLb) "lb" else "kg"
        dialogBinding.tilLogWeight.hint = "Weight ($unit)"
        dialogBinding.tvWeightDialogSub.text = "Record today's weight measurement in $unit."

        val currentWeight = binding.etProfileWeight.text?.toString()?.trim()
        if (!currentWeight.isNullOrEmpty()) {
            dialogBinding.etLogWeight.setText(currentWeight)
            dialogBinding.etLogWeight.setSelection(currentWeight.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log Weight Entry")
            .setView(dialogBinding.root)
            .setPositiveButton("Log Weight", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = dialogBinding.etLogWeight.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
                val factor = if (isLb) kgToLb else 1.0
                val weightInKg = entered?.div(factor)

                if (entered == null || weightInKg == null || weightInKg !in 25.0..350.0) {
                    dialogBinding.tilLogWeight.error = "Enter a valid weight (25–350 kg)"
                    return@setOnClickListener
                }

                dialog.dismiss()
                logWeight(weightInKg, entered, unit)
            }
        }
        dialog.show()
    }

    private fun logWeight(weightKg: Double, enteredWeight: Double, unit: String) {
        binding.profileProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Call backend POST /api/weight
                val response = ApiClient.api.logWeight(WeightRequest(weightKg = weightKg))
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Weight logged: ${fmt(enteredWeight)} $unit", Toast.LENGTH_SHORT).show()
                    binding.etProfileWeight.setText(fmt(enteredWeight))

                    // Also recalculate profile targets with the new weight (BR-01/BR-05)
                    saveProfileChangesSilently(weightKg)
                } else {
                    Toast.makeText(requireContext(), "Failed to log weight (${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Toast.makeText(requireContext(), "Error logging weight: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _binding?.profileProgress?.visibility = View.GONE
            }
        }
    }

    // Task 4: Edit weight/height/activity/goal (BR-01/BR-05)
    private fun saveProfileChanges() {
        val request = buildCalculationRequest() ?: return

        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = "Saving…"
        binding.profileProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.saveProfile(request)
                val updated = response.body()
                if (response.isSuccessful && updated != null) {
                    originalProfile = updated
                    session.saveProfileSummary(updated.name, updated.calories ?: 0)
                    displayNutritionTargets(updated)
                    Toast.makeText(requireContext(), "Profile & macro targets updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to save profile changes.", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Connection error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _binding?.profileProgress?.visibility = View.GONE
                _binding?.btnSaveProfile?.isEnabled = true
                _binding?.btnSaveProfile?.text = "Save changes"
            }
        }
    }

    private fun saveProfileChangesSilently(newWeightKg: Double) {
        val baseRequest = buildCalculationRequest() ?: return
        val updatedRequest = baseRequest.copy(weightKg = newWeightKg)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.api.saveProfile(updatedRequest)
                val updated = response.body()
                if (response.isSuccessful && updated != null) {
                    originalProfile = updated
                    session.saveProfileSummary(updated.name, updated.calories ?: 0)
                    displayNutritionTargets(updated)
                }
            } catch (_: Exception) {
                // Background update; error handled gracefully
            }
        }
    }

    private fun buildCalculationRequest(): ProfileCalculationRequest? {
        val nameText = binding.etProfileName.text?.toString()?.trim().orEmpty()
        val ageVal = binding.etProfileAge.text?.toString()?.toIntOrNull()
        val heightVal = binding.etProfileHeight.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val weightEntered = binding.etProfileWeight.text?.toString()?.replace(',', '.')?.toDoubleOrNull()
        val targetEntered = binding.etProfileTargetWeight.text?.toString()?.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()

        val factor = if (binding.spinnerWeightUnit.selectedItemPosition == 1) kgToLb else 1.0
        val weightVal = weightEntered?.div(factor)
        val targetVal = targetEntered?.div(factor)

        if (nameText.isBlank() || ageVal == null || ageVal !in 13..100 ||
            heightVal == null || heightVal !in 100.0..250.0 ||
            weightVal == null || weightVal !in 25.0..350.0 ||
            (targetEntered != null && (targetVal == null || targetVal !in 25.0..350.0))) {
            Toast.makeText(requireContext(), "Please verify your age, height and weight values.", Toast.LENGTH_LONG).show()
            return null
        }

        val sexVal = if (binding.rgSex.checkedRadioButtonId == R.id.rbFemale) "FEMALE" else "MALE"
        val activityVal = when (binding.rgActivity.checkedRadioButtonId) {
            R.id.rbModerate -> "MODERATE"
            R.id.rbActive -> "ACTIVE"
            else -> "SEDENTARY"
        }
        val goalVal = when (binding.rgGoal.checkedRadioButtonId) {
            R.id.rbMaintain -> "MAINTENANCE"
            R.id.rbBuildMuscle -> "MUSCLE_GAIN"
            else -> "WEIGHT_LOSS"
        }
        val workoutVal = listOf("NONE", "ONE_TWO", "THREE_FOUR", "FIVE_SIX")
            .getOrElse(binding.spinnerWorkouts.selectedItemPosition) { "NONE" }

        return ProfileCalculationRequest(
            name = nameText,
            sex = sexVal,
            age = ageVal,
            heightCm = heightVal,
            weightKg = weightVal,
            activityLevel = activityVal,
            workoutFrequency = workoutVal,
            goal = goalVal,
            targetWeightKg = targetVal
        )
    }

    private fun logout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log out")
            .setMessage("Are you sure you want to log out of NutriTrack?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                session.clearSession()
                val intent = Intent(requireContext(), OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
            .show()
    }

    private fun profilePhotoFile() =
        File(requireContext().filesDir, "profile_picture_${session.getEmail().orEmpty().lowercase(Locale.US).hashCode()}.jpg")

    private fun loadProfilePhoto() {
        val file = profilePhotoFile()
        if (file.exists()) {
            binding.ivProfilePhoto.load(file) {
                crossfade(true)
                transformations(coil.transform.CircleCropTransformation())
            }
            binding.ivProfilePhoto.visibility = View.VISIBLE
            binding.ivProfilePlaceholder.visibility = View.GONE
        } else {
            binding.ivProfilePhoto.visibility = View.GONE
            binding.ivProfilePlaceholder.visibility = View.VISIBLE
        }
    }

    private fun saveProfilePhoto(uri: Uri) {
        try {
            val input = requireContext().contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open image")
            input.use { source ->
                FileOutputStream(profilePhotoFile()).use { dest -> source.copyTo(dest) }
            }
            loadProfilePhoto()
            Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't save picture: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun fmt(value: Double) = String.format(Locale.US, "%.1f", value)

    private fun formatWeight(valueKg: Double): String =
        fmt(if (binding.spinnerWeightUnit.selectedItemPosition == 1) valueKg * kgToLb else valueKg)

    private fun convertDisplayedWeight(field: TextInputEditText, oldFactor: Double, newFactor: Double) {
        val value = field.text?.toString()?.replace(',', '.')?.toDoubleOrNull() ?: return
        field.setText(fmt(value * newFactor / oldFactor))
    }

    private fun updateWeightLabels() {
        val unit = if (binding.spinnerWeightUnit.selectedItemPosition == 1) "lb" else "kg"
        binding.tvCurrentWeightLabel.text = "Current weight ($unit)"
        binding.tvTargetWeightLabel.text = "Target weight ($unit)"
    }
}
