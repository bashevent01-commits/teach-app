package com.knowapp.android.ui.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knowapp.android.data.MarketDemoData
import com.knowapp.android.data.MarketDemoStore
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
    val demoOn: Boolean = false,
)

class MarketAnalysisViewModel(
    private val marketRepository: MarketAnalysisRepository,
    private val categoriesRepository: ProductCategoriesRepository,
    private val demoStore: MarketDemoStore,
    private val userId: Int,
) : ViewModel() {
    var uiState by mutableStateOf(MarketUiState(demoOn = demoStore.isOn(userId)))
        private set

    init {
        refresh()
    }

    // Turning the demo off makes the screen use real institution data
    fun setDemo(on: Boolean) {
        demoStore.setOn(userId, on)
        uiState = uiState.copy(demoOn = on)
        refresh()
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        if (uiState.demoOn) {
            uiState = uiState.copy(isLoading = false, categories = MarketDemoData.categories())
            return
        }
        viewModelScope.launch {
            when (val result = marketRepository.listCategoryInsights()) {
                is CategoryInsightsResult.Success -> uiState = uiState.copy(isLoading = false, categories = result.categories)
                is CategoryInsightsResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    // Builds the report for one category and opens the print dialog
    fun download(context: android.content.Context, categoryId: Int, onFail: (String) -> Unit) {
        viewModelScope.launch {
            if (MarketDemoData.isDemoId(categoryId)) {
                val demo = MarketDemoData.detail(categoryId)
                if (demo != null) MarketReportPdf.print(context, demo, true) else onFail("This demo category no longer exists.")
                return@launch
            }
            when (val result = marketRepository.categoryDetail(categoryId)) {
                is CategoryDetailResult.Success -> MarketReportPdf.print(context, result.detail, false)
                is CategoryDetailResult.Failure -> onFail(result.message)
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
    val isDemo: Boolean = false,
)

class CategoryDetailViewModel(private val repository: MarketAnalysisRepository, private val categoryId: Int) : ViewModel() {
    var uiState by mutableStateOf(CategoryDetailUiState())
        private set

    init {
        viewModelScope.launch {
            if (MarketDemoData.isDemoId(categoryId)) {
                val demo = MarketDemoData.detail(categoryId)
                uiState = if (demo != null) uiState.copy(isLoading = false, detail = demo, isDemo = true) else uiState.copy(isLoading = false, errorMessage = "This demo category no longer exists.")
                return@launch
            }
            when (val result = repository.categoryDetail(categoryId)) {
                is CategoryDetailResult.Success -> uiState = uiState.copy(isLoading = false, detail = result.detail)
                is CategoryDetailResult.Failure -> uiState = uiState.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
