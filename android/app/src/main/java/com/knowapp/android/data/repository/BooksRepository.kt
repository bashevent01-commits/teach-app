package com.knowapp.android.data.repository

import com.knowapp.android.data.model.SummaryOut
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.model.TrialBalanceOut
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class BooksData(
    val summary: SummaryOut,
    val trialBalance: TrialBalanceOut,
    val transactions: List<TransactionOut>,
)

// The sub admin's collective books, optionally narrowed to one staff member
class BooksRepository(private val api: ApiService) {
    suspend fun staff(): Result<List<UserOut>> = withContext(Dispatchers.IO) {
        try {
            val response = api.listUsers()
            val body = response.body()
            if (response.isSuccessful && body != null) Result.success(body.filter { it.role == "staff" })
            else Result.failure(Exception("Couldn't load staff (code ${response.code()})."))
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection."))
        }
    }

    suspend fun load(staffId: Int?): Result<BooksData> = withContext(Dispatchers.IO) {
        try {
            val summary = api.getSummary(staffId)
            val trial = api.getTrialBalance(staffId)
            val txns = api.listTransactions(staffId = staffId)
            val s = summary.body()
            val t = trial.body()
            val x = txns.body()
            if (summary.isSuccessful && trial.isSuccessful && txns.isSuccessful && s != null && t != null && x != null) {
                Result.success(BooksData(s, t, x))
            } else {
                Result.failure(Exception("Couldn't load the books."))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Couldn't reach the server. Check your connection."))
        }
    }
}
