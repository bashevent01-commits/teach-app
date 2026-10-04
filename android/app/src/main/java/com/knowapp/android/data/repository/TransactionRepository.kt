package com.knowapp.android.data.repository

import com.google.gson.JsonParser
import com.knowapp.android.data.SessionStore
import com.knowapp.android.data.local.OfflineStore
import com.knowapp.android.data.model.OpeningBalancesOut
import com.knowapp.android.data.model.PendingTransaction
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.UUID

sealed class TransactionsResult {
    data class Success(val transactions: List<TransactionOut>) : TransactionsResult()
    data class Failure(val message: String) : TransactionsResult()
}

data class NewTransaction(
    val type: String,
    val method: String,
    val categoryType: String,
    val category: String?,
    val description: String?,
    val amount: String,
    val stockItemId: Int?,
    val quantity: String?,
    val mpesaCode: String?,
    val mpesaPayerName: String?,
)

sealed class RecordResult {
    object Saved : RecordResult()
    object Queued : RecordResult()
    data class Rejected(val message: String) : RecordResult()
}

data class HomeData(
    val transactions: List<TransactionOut>,
    val pending: List<PendingTransaction>,
    val opening: OpeningBalancesOut,
)

fun errorDetail(body: String?, code: Int): String {
    if (!body.isNullOrBlank()) {
        try {
            val detail = JsonParser.parseString(body).asJsonObject.get("detail")
            if (detail != null && detail.isJsonPrimitive) return detail.asString
        } catch (e: Exception) {
            // fall through to the generic message
        }
    }
    return "The server rejected this (code $code)."
}

private fun NewTransaction.toFields(): Map<String, String> = buildMap {
    put("type", type)
    put("method", method)
    put("category_type", categoryType)
    put("amount", amount)
    category?.takeIf { it.isNotBlank() }?.let { put("category", it) }
    description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
    stockItemId?.let { put("stock_item_id", it.toString()) }
    quantity?.takeIf { it.isNotBlank() }?.let { put("quantity", it) }
    mpesaCode?.takeIf { it.isNotBlank() }?.let { put("mpesa_code", it) }
    mpesaPayerName?.takeIf { it.isNotBlank() }?.let { put("mpesa_payer_name", it) }
}

private fun PendingTransaction.toFields(): Map<String, String> = NewTransaction(
    type, method, categoryType, category, description, amount, stockItemId, quantity, mpesaCode, mpesaPayerName,
).toFields()

class TransactionRepository(
    private val api: ApiService,
    private val store: OfflineStore,
    private val sessionStore: SessionStore,
) {
    private val pendingLock = Mutex()

    private fun userId(): Int? = sessionStore.session.value?.userId

    suspend fun list(institutionId: Int? = null): TransactionsResult = withContext(Dispatchers.IO) {
        try {
            val response = api.listTransactions(institutionId = institutionId)
            if (response.isSuccessful) {
                TransactionsResult.Success(response.body() ?: emptyList())
            } else {
                TransactionsResult.Failure("Couldn't load transactions (code ${response.code()}).")
            }
        } catch (e: IOException) {
            TransactionsResult.Failure("Couldn't reach the server. Check your connection and try again.")
        }
    }

    suspend fun loadHome(): HomeData = withContext(Dispatchers.IO) {
        val uid = userId() ?: return@withContext HomeData(emptyList(), emptyList(), OpeningBalancesOut("0", "0", "0", false))
        var transactions = store.transactions(uid) ?: emptyList()
        var opening = store.opening(uid) ?: OpeningBalancesOut("0", "0", "0", false)
        try {
            val txResponse = api.listTransactions()
            if (txResponse.isSuccessful) {
                transactions = txResponse.body() ?: emptyList()
                store.saveTransactions(uid, transactions)
            }
            val openingResponse = api.getOpeningBalances()
            if (openingResponse.isSuccessful) {
                opening = openingResponse.body() ?: opening
                store.saveOpening(uid, opening)
            }
        } catch (e: IOException) {
            // offline: the cached copy above is what the screen shows
        }
        HomeData(transactions, store.pending(uid), opening)
    }

    suspend fun record(draft: NewTransaction): RecordResult = withContext(Dispatchers.IO) {
        val uid = userId() ?: return@withContext RecordResult.Rejected("You are signed out. Please sign in again.")
        try {
            val response = api.createTransaction(draft.toFields())
            if (response.isSuccessful) {
                RecordResult.Saved
            } else {
                RecordResult.Rejected(errorDetail(response.errorBody()?.string(), response.code()))
            }
        } catch (e: IOException) {
            pendingLock.withLock {
                val queued = PendingTransaction(
                    localId = UUID.randomUUID().toString(),
                    type = draft.type,
                    method = draft.method,
                    categoryType = draft.categoryType,
                    category = draft.category,
                    description = draft.description,
                    amount = draft.amount,
                    stockItemId = draft.stockItemId,
                    quantity = draft.quantity,
                    mpesaCode = draft.mpesaCode,
                    mpesaPayerName = draft.mpesaPayerName,
                    createdAt = Instant.now().toString(),
                )
                store.savePending(uid, store.pending(uid) + queued)
            }
            RecordResult.Queued
        }
    }

    // Sends queued entries in order; stops at the first network failure and marks server-rejected ones as failed
    suspend fun syncPending(): Int = withContext(Dispatchers.IO) {
        val uid = userId() ?: return@withContext 0
        pendingLock.withLock {
            val queue = store.pending(uid)
            if (queue.isEmpty()) return@withLock 0
            val remaining = mutableListOf<PendingTransaction>()
            var synced = 0
            var stopped = false
            for (item in queue) {
                if (stopped || item.failed) {
                    remaining += item
                    continue
                }
                try {
                    val response = api.createTransaction(item.toFields())
                    if (response.isSuccessful) {
                        synced++
                    } else {
                        remaining += item.copy(failed = true, error = errorDetail(response.errorBody()?.string(), response.code()))
                    }
                } catch (e: IOException) {
                    remaining += item
                    stopped = true
                }
            }
            store.savePending(uid, remaining)
            synced
        }
    }

    suspend fun discardPending(localId: String) = withContext(Dispatchers.IO) {
        val uid = userId() ?: return@withContext
        pendingLock.withLock { store.savePending(uid, store.pending(uid).filter { it.localId != localId }) }
    }

    suspend fun setOpening(cash: Double, mpesa: Double, bank: Double): Result<OpeningBalancesOut> = withContext(Dispatchers.IO) {
        val uid = userId() ?: return@withContext Result.failure(Exception("You are signed out."))
        try {
            val response = api.setOpeningBalances(mapOf("cash" to cash, "mpesa" to mpesa, "bank" to bank))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                store.saveOpening(uid, body)
                Result.success(body)
            } else {
                Result.failure(Exception(errorDetail(response.errorBody()?.string(), response.code())))
            }
        } catch (e: IOException) {
            Result.failure(Exception("You need a connection to save starting balances."))
        }
    }
}
