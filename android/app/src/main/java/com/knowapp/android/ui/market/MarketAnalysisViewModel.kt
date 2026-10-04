package com.knowapp.android.ui.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.model.CategoryDetailOut
import com.knowapp.android.data.model.CategoryInsightOut
import com.knowapp.android.data.model.ProductCategoryOut
import com.knowapp.android.data.repository.CategoryDetailResult
import com.knowapp.android.data.repository.CategoryInsightsResult
import com.knowapp.android.data.repository.MarketAnalysisRepository
import com.knowapp.android.data.repository.ProductCategoriesRepository
import com.knowapp.android.data.repository.ProductCategoriesResult
import kotlinx.coroutines.launch

data class MarketUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryInsightOut> = emptyList(),
    val errorMessage: String? = null,
    val manageCategories: List<ProductCategoryOut> = emptyList(),
    val manageError: String? = null,
)

class MarketAnalysisViewModel(
    private val marketRepository: MarketAnalysisRepository,
    private val categoriesRepository: ProductCategoriesRepository,
) : ViewModel() {
    var uiState by mutableStateOf(MarketUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = marketRepository.listCategoryInsights()) {
                is CategoryInsightsResult.Success -> uiState = uiState.copy(isLoading = false, categories = result.categories)
                is CategoryInsightsResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun loadManageableCategories() {
        viewModelScope.launch {
            when (val result = categoriesRepository.list()) {
                is ProductCategoriesResult.Success -> uiState = uiState.copy(manageCategories = result.categories, manageError = null)
                is ProductCategoriesResult.Failure -> uiState = uiState.copy(manageError = result.message)
            }
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            when (val result = categoriesRepository.create(name)) {
                is ProductCategoriesResult.Success -> uiState = uiState.copy(manageCategories = result.categories, manageError = null)
                is ProductCategoriesResult.Failure -> uiState = uiState.copy(manageError = result.message)
            }
        }
    }

    fun deleteCategory(categoryId: Int) {
        viewModelScope.launch {
            when (val result = categoriesRepository.delete(categoryId)) {
                is ProductCategoriesResult.Success -> uiState = uiState.copy(manageCategories = result.categories, manageError = null)
                is ProductCategoriesResult.Failure -> uiState = uiState.copy(manageError = result.message)
            }
        }
    }
}

data class CategoryDetailUiState(
    val isLoading: Boolean = true,
    val detail: CategoryDetailOut? = null,
    val errorMessage: String? = null,
)

class CategoryDetailViewModel(private val repository: MarketAnalysisRepository, private val categoryId: Int) : ViewModel() {
    var uiState by mutableStateOf(CategoryDetailUiState())
        private set

    init {
        viewModelScope.launch {
            when (val result = repository.categoryDetail(categoryId)) {
                is CategoryDetailResult.Success -> uiState = uiState.copy(isLoading = false, detail = result.detail)
                is CategoryDetailResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
