// ui/viewmodel/PublishViewModelFactory.kt
package com.darim.ui.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darim.domain.usecase.item.PublishItemUseCase
import com.darim.ui.publish.PublishViewModel

class PublishViewModelFactory(
    private val publishItemUseCase: PublishItemUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PublishViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PublishViewModel(publishItemUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}