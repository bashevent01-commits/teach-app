package com.knowapp.android.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.knowapp.android.data.model.OpeningBalancesOut
import com.knowapp.android.data.model.PendingTransaction
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.model.TransactionOut
import java.io.File
import java.lang.reflect.Type

// Per-user JSON files so a shared phone never shows or syncs another person's data
class OfflineStore(context: Context) {
    private val gson = Gson()
    private val dir: File = context.applicationContext.filesDir

    @Synchronized
    private fun <T> read(name: String, type: Type): T? {
        val file = File(dir, name)
        if (!file.exists()) return null
        return try {
            gson.fromJson<T>(file.readText(), type)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    private fun write(name: String, value: Any) {
        File(dir, name).writeText(gson.toJson(value))
    }

    fun pending(userId: Int): List<PendingTransaction> =
        read("pending_$userId.json", object : TypeToken<List<PendingTransaction>>() {}.type) ?: emptyList()

    fun savePending(userId: Int, items: List<PendingTransaction>) = write("pending_$userId.json", items)

    fun transactions(userId: Int): List<TransactionOut>? =
        read("transactions_$userId.json", object : TypeToken<List<TransactionOut>>() {}.type)

    fun saveTransactions(userId: Int, items: List<TransactionOut>) = write("transactions_$userId.json", items)

    fun opening(userId: Int): OpeningBalancesOut? =
        read("opening_$userId.json", object : TypeToken<OpeningBalancesOut>() {}.type)

    fun saveOpening(userId: Int, value: OpeningBalancesOut) = write("opening_$userId.json", value)

    fun stock(userId: Int): List<StockItemOut>? =
        read("stock_$userId.json", object : TypeToken<List<StockItemOut>>() {}.type)

    fun saveStock(userId: Int, items: List<StockItemOut>) = write("stock_$userId.json", items)
}
