package com.nutritrack.android.viewmodel

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.nutritrack.android.NutriTrackApp


inline fun <reified VM : ViewModel> ComponentActivity.appViewModels(): Lazy<VM> =
    viewModels<VM> { (application as NutriTrackApp).container.viewModelFactory }
