package com.nutritrack.android

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_ACTIVITY
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_GOAL
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_NAME
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_RESULTS
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_SEX
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.STEP_WORKOUTS
import com.nutritrack.android.ProfileSetupPagerAdapter.Companion.TOTAL_STEPS
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.ProfileCalculationRequest
import com.nutritrack.android.data.model.ProfileCalculationResponse
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.databinding.ActivityProfileSetupBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var adapter: ProfileSetupPagerAdapter
    private lateinit var sessionManager: SessionManager

    private var latestResult: ProfileCalculationResponse? = null

    private val currentStep get() = binding.viewPager.currentItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        setupPager()
        setupListeners()
        updateUiForStep(0)
    }

    private fun setupPager() {
        adapter = ProfileSetupPagerAdapter(this, layoutInflater)
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = TOTAL_STEPS

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUiForStep(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            if (currentStep == 0) finish() else binding.viewPager.currentItem = currentStep - 1
        }

        binding.btnNext.setOnClickListener {
            when {
                currentStep == STEP_RESULTS -> goToHome()

                !adapter.hasValidSelection(currentStep) -> showValidationHint(currentStep)

                currentStep == STEP_GOAL -> submitProfile()

                else -> binding.viewPager.currentItem = currentStep + 1
            }
        }
    }

    private fun buttonLabel(step: Int) = when (step) {
        STEP_GOAL    -> "Calculate my plan"
        STEP_RESULTS -> "Start"
        else         -> "Next"
    }

    private fun updateUiForStep(step: Int) {
        binding.progressBarStep.setProgress(step + 1, true)

        binding.tvCategory.text = if (step == STEP_RESULTS) "Your plan" else "Basics"

        val isResults = step == STEP_RESULTS
        binding.btnNext.text = buttonLabel(step)
        binding.btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isResults) 20f else 17f)
        binding.btnNext.layoutParams = binding.btnNext.layoutParams.apply {
            height = dp(if (isResults) 64 else 56)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showValidationHint(step: Int) {
        val msg = when (step) {
            STEP_NAME     -> "Please enter your name"
            STEP_SEX      -> "Please select your biological sex"
            STEP_ACTIVITY -> "Please select your activity level"
            STEP_WORKOUTS -> "Please select how often you work out"
            STEP_GOAL     -> "Please select your goal"
            else          -> "Please make a selection to continue"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun submitProfile() {
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val request = ProfileCalculationRequest(
            name          = adapter.getName(),
            sex           = adapter.getSex(),
            age           = adapter.getAge(),
            heightCm      = adapter.getHeightCm(),
            weightKg      = adapter.getWeightKg(),
            activityLevel = adapter.getActivity(),
            workoutFrequency = adapter.getWorkouts(),
            goal          = adapter.getGoal()
        )

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.saveProfile(request)
                setLoading(false)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    latestResult = body
                    sessionManager.saveProfileSummary(body.name, body.calories ?: 0)
                    adapter.setResults(body)
                    binding.viewPager.currentItem = STEP_RESULTS
                } else {
                    Toast.makeText(
                        this@ProfileSetupActivity,
                        "Failed to save profile. Please try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@ProfileSetupActivity,
                    "Connection error: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun goToHome() {
        val result = latestResult
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("USER_NAME", result?.name ?: adapter.getName())
            putExtra("DAILY_CALORIES", result?.calories ?: 0)
        }
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnNext.isEnabled = !loading
        binding.btnNext.text      = if (loading) "" else buttonLabel(currentStep)
        binding.progressBarLoading.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
