package com.nutritrack.android.viewmodel

sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Saved(val updated: Boolean) : SaveState()
    data class Failed(val message: String) : SaveState()
}
