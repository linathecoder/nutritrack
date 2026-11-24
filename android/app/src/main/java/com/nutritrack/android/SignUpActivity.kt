package com.nutritrack.android

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nutritrack.android.navigation.AuthNavigator
import com.nutritrack.android.databinding.ActivitySignUpBinding
import com.nutritrack.android.viewmodel.AuthUiState
import com.nutritrack.android.viewmodel.AuthViewModel
import com.nutritrack.android.viewmodel.appViewModels

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private val authViewModel: AuthViewModel by appViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
        setupListeners()
        observeViewModel()
    }

    private fun observeViewModel() {
        authViewModel.state.observe(this) { state ->
            setLoading(state is AuthUiState.Loading)
            if (state is AuthUiState.Error) showError(state.message)
        }
        authViewModel.navigation.observe(this) { event ->
            event.getContentIfNotHandled()?.let { nav ->
                nav.message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                AuthNavigator.open(this, nav.destination)
            }
        }
    }

    private fun setupViews() {
        binding.tvLogin.text = HtmlCompat.fromHtml(
            getString(R.string.signup_has_account),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnSignUp.setOnClickListener {
            attemptSignUp()
        }
    }

    private fun attemptSignUp() {
        binding.tvError.visibility = View.GONE
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        if (email.isEmpty()) {
            binding.tilEmail.error = "Email is required"
            return
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Please enter a valid email"
            return
        } else {
            binding.tilEmail.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            return
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            return
        } else {
            binding.tilPassword.error = null
        }

        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            return
        } else {
            binding.tilConfirmPassword.error = null
        }

        authViewModel.signUp(email, password)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSignUp.isEnabled = !isLoading
        binding.btnSignUp.text = if (isLoading) "" else getString(R.string.signup_btn)
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
