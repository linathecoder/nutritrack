package com.nutritrack.android

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.nutritrack.android.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    enum class Tab {
        HOME, MEALS, ANALYSIS, PROFILE
    }

    private lateinit var binding: ActivityMainBinding
    private var currentTab: Tab = Tab.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomBar.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        setupBottomNavigation()

        if (savedInstanceState == null) {
            navigateToTab(Tab.HOME)
        } else {
            val savedTabName = savedInstanceState.getString(KEY_CURRENT_TAB, Tab.HOME.name)
            currentTab = runCatching { Tab.valueOf(savedTabName) }.getOrDefault(Tab.HOME)
            updateTabUi(currentTab)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_TAB, currentTab.name)
    }

    private fun setupBottomNavigation() {
        binding.tabHome.setOnClickListener { navigateToTab(Tab.HOME) }
        binding.tabMeals.setOnClickListener { navigateToTab(Tab.MEALS) }
        binding.tabAnalysis.setOnClickListener { navigateToTab(Tab.ANALYSIS) }
        binding.tabProfile.setOnClickListener { navigateToTab(Tab.PROFILE) }

        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
    }

    fun navigateToTab(tab: Tab) {
        currentTab = tab
        updateTabUi(tab)

        val tag = tab.name
        val fragmentManager = supportFragmentManager
        val transaction = fragmentManager.beginTransaction()

        for (t in Tab.values()) {
            val existing = fragmentManager.findFragmentByTag(t.name)
            if (existing != null && existing.isAdded) {
                transaction.hide(existing)
            }
        }

        var targetFragment = fragmentManager.findFragmentByTag(tag)
        if (targetFragment == null) {
            targetFragment = when (tab) {
                Tab.HOME -> HomeFragment()
                Tab.MEALS -> SavedMealsFragment()
                Tab.ANALYSIS -> AnalysisFragment()
                Tab.PROFILE -> ProfileFragment()
            }
            transaction.add(R.id.fragmentContainer, targetFragment, tag)
        } else {
            transaction.show(targetFragment)
        }

        transaction.commit()
    }

    private fun updateTabUi(activeTab: Tab) {
        styleTab(binding.tabHomeIcon, binding.tabHomeLabel, binding.tabHomePill, activeTab == Tab.HOME)
        styleTab(binding.tabMealsIcon, binding.tabMealsLabel, binding.tabMealsPill, activeTab == Tab.MEALS)
        styleTab(binding.tabAnalysisIcon, binding.tabAnalysisLabel, binding.tabAnalysisPill, activeTab == Tab.ANALYSIS)
        styleTab(binding.tabProfileIcon, binding.tabProfileLabel, binding.tabProfilePill, activeTab == Tab.PROFILE)
    }

    private fun styleTab(icon: ImageView, label: TextView, pill: View, active: Boolean) {
        val tint = ContextCompat.getColor(this, if (active) R.color.nav_active else R.color.text_slate_400)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(tint))
        label.setTextColor(tint)
        pill.setBackgroundResource(if (active) R.drawable.bg_nav_pill else 0)
    }

    fun openLogMeal() {
        startActivity(Intent(this, CreateMealActivity::class.java))
    }

    companion object {
        private const val KEY_CURRENT_TAB = "current_nav_tab"
    }
}
