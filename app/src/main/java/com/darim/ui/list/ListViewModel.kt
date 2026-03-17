package com.darim.ui.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ListViewModel : ViewModel() {

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _selectedCategories = MutableLiveData<Set<String>>(emptySet())
    val selectedCategories: LiveData<Set<String>> = _selectedCategories

    private val _radius = MutableLiveData(5.0)
    val radius: LiveData<Double> = _radius

    private val _isWholeCity = MutableLiveData(false)
    val isWholeCity: LiveData<Boolean> = _isWholeCity

    private val _sortType = MutableLiveData("date")
    val sortType: LiveData<String> = _sortType

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategories(categories: Set<String>) {
        _selectedCategories.value = categories
    }

    fun updateRadius(radius: Double) {
        _radius.value = radius
    }

    fun updateWholeCity(isChecked: Boolean) {
        _isWholeCity.value = isChecked
    }

    fun updateSortType(type: String) {
        _sortType.value = type
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedCategories.value = emptySet()
        _radius.value = 5.0
        _isWholeCity.value = false
        _sortType.value = "date"
    }
}