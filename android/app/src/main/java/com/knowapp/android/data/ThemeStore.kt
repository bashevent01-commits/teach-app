package com.knowapp.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("know_theme", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<ThemeMode> = _mode

    private fun load(): ThemeMode = try {
        ThemeMode.valueOf(prefs.getString("mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    } catch (e: Exception) {
        ThemeMode.SYSTEM
    }

    fun set(mode: ThemeMode) {
        prefs.edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }
}
