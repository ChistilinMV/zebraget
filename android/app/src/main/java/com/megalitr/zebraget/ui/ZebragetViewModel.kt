package com.megalitr.zebraget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.megalitr.zebraget.data.model.Product
import com.megalitr.zebraget.data.model.ProductGroup
import com.megalitr.zebraget.domain.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Content(
        val products: List<Product>,
        val groups: List<ProductGroup>,
        val isOffline: Boolean = false
    ) : UiState
}

class ZebragetViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    private val _rawState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _rawState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadData()
    }

    fun loadData(fromSwipe: Boolean = false) {
        viewModelScope.launch {
            if (fromSwipe) {
                _isRefreshing.value = true
            } else {
                _rawState.value = UiState.Loading
            }
            try {
                // Try network
                val (products, groups) = repository.fetchFromNetwork()
                _rawState.value = UiState.Content(products, groups, isOffline = false)
            } catch (e: Exception) {
                // Return cached/assets if available
                val (offlineProducts, offlineGroups) = repository.getCachedOrAssets()
                if (offlineProducts.isNotEmpty() || offlineGroups.isNotEmpty()) {
                    _rawState.value = UiState.Content(offlineProducts, offlineGroups, isOffline = true)
                } else {
                    _rawState.value = UiState.Error(e.localizedMessage ?: "Connection failed")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    // Helper to get group name for a product
    fun getGroupName(groupId: Long?): String? {
        val state = _rawState.value
        if (state is UiState.Content && groupId != null) {
            return state.groups.find { it.id == groupId }?.name
        }
        return null
    }
}
