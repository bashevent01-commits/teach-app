package com.knowapp.android.ui.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.local.PhotoStore
import com.knowapp.android.data.model.StockItemOut
import com.knowapp.android.data.model.TransactionOut
import com.knowapp.android.data.repository.DocumentsRepository
import com.knowapp.android.data.repository.StockRepository
import com.knowapp.android.data.repository.StockResult
import com.knowapp.android.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewDocumentUiState(
    val stock: List<StockItemOut> = emptyList(),
    val entries: List<TransactionOut> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

class NewDocumentViewModel(
    private val documents: DocumentsRepository,
    private val photos: PhotoStore,
    private val stockRepository: StockRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NewDocumentUiState())
    val state: StateFlow<NewDocumentUiState> = _state

    init {
        viewModelScope.launch {
            val cached = transactions.cachedHome()
            _state.value = _state.value.copy(entries = cached.transactions.sortedByDescending { it.createdAt }.take(20))
            when (val result = stockRepository.list()) {
                is StockResult.Success -> _state.value = _state.value.copy(stock = result.items)
                is StockResult.Failure -> Unit
            }
        }
    }

    fun save(
        docType: String,
        reference: String,
        date: String,
        party: String,
        stockItemId: Int?,
        quantity: String,
        amount: String,
        notes: String,
        transactionId: Int?,
        photo: Uri?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val path = photo?.let { withContext(Dispatchers.IO) { photos.import(it) } }
            documents.create(docType, reference, date, party, stockItemId, quantity, amount, notes, transactionId, path)
                .onSuccess {
                    _state.value = _state.value.copy(saving = false)
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(saving = false, error = it.message ?: "Couldn't save the document.") }
        }
    }
}
