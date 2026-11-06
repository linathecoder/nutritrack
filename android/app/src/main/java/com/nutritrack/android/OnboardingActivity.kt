package com.nutritrack.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nutritrack.android.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.cardContent.setPadding(
                binding.cardContent.paddingLeft,
                binding.cardContent.paddingTop,
                binding.cardContent.paddingRight,
                systemBars.bottom + 16
            )
            insets
        }

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.tvTitle.text = HtmlCompat.fromHtml(
            getString(R.string.onboarding_title),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        binding.tvLogin.text = HtmlCompat.fromHtml(
            getString(R.string.onboarding_already_account),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun setupListeners() {
        binding.btnGetStarted.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}

