package com.knowapp.android.data

import android.content.Context

// Remembers, per super admin, whether the Market demo has been switched off
class MarketDemoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("know_market_demo", Context.MODE_PRIVATE)

    // The demo is on until the super admin turns it off
    fun isOn(userId: Int): Boolean = !prefs.getBoolean("off_$userId", false)

    fun setOn(userId: Int, on: Boolean) {
        prefs.edit().putBoolean("off_$userId", !on).apply()
    }
}
