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

    private val _hideBalances = MutableStateFlow(prefs.getBoolean("hide_balances", false))
    val hideBalances: StateFlow<Boolean> = _hideBalances

    // The way money was last received or paid, so the next entry starts on it
    var lastMethod: String
        get() = prefs.getString("last_method", "cash") ?: "cash"
        private set(value) { prefs.edit().putString("last_method", value).apply() }

    fun rememberMethod(method: String) {
        lastMethod = method
    }

    fun setHideBalances(hide: Boolean) {
        prefs.edit().putBoolean("hide_balances", hide).apply()
        _hideBalances.value = hide
    }

    fun set(mode: ThemeMode) {
        prefs.edit().putString("mode", mode.name).apply()
        _mode.value = mode
    }
}
