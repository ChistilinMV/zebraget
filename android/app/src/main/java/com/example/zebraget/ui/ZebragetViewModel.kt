package com.example.zebraget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zebraget.data.model.Product
import com.example.zebraget.domain.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Content(val products: List<Product>, val isOffline: Boolean = false) : UiState
}

class ZebragetViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _rawProducts = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _rawProducts

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Derived state for UI
    val filteredProducts = combine(_rawProducts, _searchQuery) { state, query ->
        if (state is UiState.Content) {
            if (query.isBlank()) state.products
            else state.products.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _rawProducts.value = UiState.Loading
            try {
                // Try network
                val products = repository.fetchFromNetwork()
                _rawProducts.value = UiState.Content(products, isOffline = false)
            } catch (e: Exception) {
                // Return cached/assets if available
                val offlineProducts = repository.getCachedOrAssets()
                if (offlineProducts.isNotEmpty()) {
                    _rawProducts.value = UiState.Content(offlineProducts, isOffline = true)
                } else {
                    _rawProducts.value = UiState.Error(e.localizedMessage ?: "Connection failed")
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
