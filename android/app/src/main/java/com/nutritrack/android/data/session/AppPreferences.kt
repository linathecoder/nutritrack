package com.nutritrack.android.data.session

import android.content.Context


class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)

    var weightUnit: String
        get() = prefs.getString("weight_unit", "kg") ?: "kg"
        set(value) {
            prefs.edit().putString("weight_unit", value).apply()
        }
}
