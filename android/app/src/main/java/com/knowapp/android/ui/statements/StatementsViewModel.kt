package com.knowapp.android.ui.statements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.PendingTransaction
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.model.UserOut
import com.knowapp.android.data.repository.BooksRepository
import com.knowapp.android.data.repository.TransactionRepository
import com.knowapp.android.data.repository.TransactionsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class StatementRow(
    val date: LocalDate,
    val category: String,
    val method: String,
    val type: String,
    val amount: BigDecimal,
    val pending: Boolean,
) {
    val methodLabel: String get() = when (method) { "mpesa" -> "M-Pesa"; "cash" -> "Cash"; "bank" -> "Bank"; else -> method }
}

enum class StatementRange(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_7("Last 7 days"),
    LAST_30("Last 30 days"),
    ALL("All time"),
    CUSTOM("Custom"),
}

val STATEMENT_METHODS = listOf("all" to "All", "cash" to "Cash", "mpesa" to "M-Pesa", "bank" to "Bank")

data class StatementsUiState(
    val loading: Boolean = true,
    val rows: List<StatementRow> = emptyList(),
    val method: String = "all",
    val range: StatementRange = StatementRange.THIS_MONTH,
    val customFrom: LocalDate = LocalDate.now().withDayOfMonth(1),
    val customTo: LocalDate = LocalDate.now(),
    val refreshing: Boolean = false,
    val isAdmin: Boolean = false,
    val staff: List<UserOut> = emptyList(),
    val selectedStaffId: Int? = null,
) {
    val staffLabel: String? get() = staff.firstOrNull { it.id == selectedStaffId }?.fullName

    private fun bounds(today: LocalDate): Pair<LocalDate?, LocalDate?> = when (range) {
        StatementRange.THIS_MONTH -> today.withDayOfMonth(1) to today
        StatementRange.LAST_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            first to first.withDayOfMonth(first.lengthOfMonth())
        }
        StatementRange.LAST_7 -> today.minusDays(6) to today
        StatementRange.LAST_30 -> today.minusDays(29) to today
        StatementRange.ALL -> null to null
        StatementRange.CUSTOM -> customFrom to customTo
    }

    val periodText: String
        get() {
            val (from, to) = bounds(LocalDate.now())
            return if (from == null || to == null) "All time" else "$from to $to"
        }

    val visible: List<StatementRow>
        get() {
            val (from, to) = bounds(LocalDate.now())
            return rows
                .filter { method == "all" || it.method == method }
                .filter { (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
                .sortedBy { it.date }
        }

    val income: BigDecimal get() = visible.filter { it.type == "income" }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
    val expense: BigDecimal get() = visible.filter { it.type == "expense" }.fold(BigDecimal.ZERO) { a, r -> a + r.amount }
    val title: String get() = if (method == "all") "Combined Statement" else "${STATEMENT_METHODS.first { it.first == method }.second} Movement Statement"
}

internal fun dateOf(iso: String): LocalDate = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
} catch (e: Exception) {
    try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (e2: Exception) {
        LocalDate.parse(iso.take(10))
    }
}

private fun TransactionOut.toRow() = StatementRow(dateOf(transactionDate), category, method, type, amount.toBigDecimalOrNull() ?: BigDecimal.ZERO, false)

// Entries still waiting to sync are dated by when they were recorded on the phone
private fun PendingTransaction.toRow() = StatementRow(dateOf(createdAt), category ?: "Stock entry", method, type, amount.toBigDecimalOrNull() ?: BigDecimal.ZERO, true)

class StatementsViewModel(
    private val transactions: TransactionRepository,
    private val books: BooksRepository,
    private val isAdmin: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(StatementsUiState(isAdmin = isAdmin))
    val state: StateFlow<StatementsUiState> = _state

    private fun rowsOf(data: com.knowapp.android.data.repository.HomeData) =
        data.transactions.map { it.toRow() } + data.pending.filter { !it.failed }.map { it.toRow() }

    private suspend fun loadFor(staffId: Int?) {
        _state.value = _state.value.copy(refreshing = true, selectedStaffId = staffId)
        when (val result = transactions.list(staffId = staffId)) {
            is TransactionsResult.Success -> _state.value = _state.value.copy(refreshing = false, loading = false, rows = result.transactions.map { it.toRow() })
            is TransactionsResult.Failure -> _state.value = _state.value.copy(refreshing = false, loading = false)
        }
    }

    fun selectStaff(staffId: Int?) {
        viewModelScope.launch { loadFor(staffId) }
    }

    init {
        if (isAdmin) {
            viewModelScope.launch {
                books.staff().onSuccess { _state.value = _state.value.copy(staff = it) }
                loadFor(null)
            }
        } else viewModelScope.launch {
            // Paint what is on the phone straight away, then update from the server
            val cached = transactions.cachedHome()
            _state.value = _state.value.copy(loading = false, refreshing = true, rows = rowsOf(cached))
            val data = transactions.loadHome()
            _state.value = _state.value.copy(refreshing = false, rows = rowsOf(data))
        }
    }

    fun setCustomFrom(date: LocalDate) {
        _state.value = _state.value.copy(customFrom = date, customTo = if (date.isAfter(_state.value.customTo)) date else _state.value.customTo)
    }

    fun setCustomTo(date: LocalDate) {
        _state.value = _state.value.copy(customTo = date, customFrom = if (date.isBefore(_state.value.customFrom)) date else _state.value.customFrom)
    }

    fun selectMethod(method: String) {
        _state.value = _state.value.copy(method = method)
    }

    fun selectRange(range: StatementRange) {
        _state.value = _state.value.copy(range = range)
    }
}
