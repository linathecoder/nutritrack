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
import androidx.lifecycle.lifecycleScope
import com.nutritrack.android.data.api.ApiClient
import com.nutritrack.android.data.model.LoginRequest
import com.nutritrack.android.data.session.SessionManager
import com.nutritrack.android.navigation.AuthNavigator
import com.nutritrack.android.databinding.ActivityLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        binding.tvSignUp.text = HtmlCompat.fromHtml(
            getString(R.string.login_no_account),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }
    }

    private fun attemptLogin() {
        binding.tvError.visibility = View.GONE
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

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
        } else {
            binding.tilPassword.error = null
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val auth = response.body()!!
                    val token = auth.token
                    if (token.isNullOrEmpty()) {
                        showError("Login failed: the server did not return a session token.")
                        return@launch
                    }
                    sessionManager.saveUser(auth.userId ?: "", email, token)

                    Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()

                    AuthNavigator.routeAfterLogin(this@LoginActivity, sessionManager)
                } else {
                    val errorMsg = parseErrorMessage(response.errorBody()?.string())
                    showError(errorMsg ?: "Invalid email or password")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("Unable to connect to backend: ${e.localizedMessage ?: "Connection error"}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "" else getString(R.string.login_btn)
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun parseErrorMessage(jsonStr: String?): String? {
        if (jsonStr.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(jsonStr)
            json.optString("message", null)
        } catch (_: Exception) {
            null
        }
    }
}

