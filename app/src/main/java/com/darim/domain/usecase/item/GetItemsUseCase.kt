package com.darim.domain.usecase.item

import com.darim.domain.model.Filters
import com.darim.domain.model.Item
import com.darim.domain.model.SortType
import com.darim.domain.repository.ItemRepository

class GetItemsUseCase(private val repository: ItemRepository) {
    // Добавьте "= null" здесь
    suspend fun execute(category: String? = null): List<Item> {
        // Передаем категорию в репозиторий
        return repository.getItems(category)
    }
}