package com.darim.ui.utils


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darim.MyApplication
import com.darim.ui.detail.DetailViewModel
import com.darim.ui.list.ListViewModel
import com.darim.ui.map.MapViewModel
import com.darim.ui.myitems.MyItemsViewModel
/*import com.darim.ui.myitems.MyItemsViewModel*/
/*import com.darim.ui.profile.ProfileViewModel*/
/*import com.darim.ui.transfer.TransferViewModel*/
import com.darim.ui.profile.LoginViewModel
import com.darim.ui.profile.ProfileViewModel
import com.darim.ui.profile.RegisterViewModel
import com.darim.ui.publish.PublishViewModel
import com.darim.ui.transfer.TransferViewModel

class ViewModelFactory(private val application: MyApplication) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            // Auth
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(application.loginUseCase) as T
            }
            modelClass.isAssignableFrom(RegisterViewModel::class.java) -> {
                RegisterViewModel(application.registerUseCase) as T
            }

            // List
            modelClass.isAssignableFrom(ListViewModel::class.java) -> {
                ListViewModel(
                    application.getItemsUseCase,
                    application.getUserLocationUseCase
                ) as T
            }

            // Detail
            modelClass.isAssignableFrom(DetailViewModel::class.java) -> {
                DetailViewModel(
                    application.getItemDetailsUseCase,
                    application.bookItemUseCase
                ) as T
            }

            // Map
            modelClass.isAssignableFrom(MapViewModel::class.java) -> {
                MapViewModel(
                    application.getItemsUseCase,
                    application.getUserLocationUseCase
                ) as T
            }

            // MyItems
            modelClass.isAssignableFrom(MyItemsViewModel::class.java) -> {
                MyItemsViewModel(
                    application.getMyItemsUseCase,
                    application.getMyBookingsUseCase
                ) as T
            }

            // Profile
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(
                    application.getUserProfileUseCase
                ) as T
            }

            // Publish
            modelClass.isAssignableFrom(PublishViewModel::class.java) -> {
                PublishViewModel(
                    application.publishItemUseCase
                ) as T
            }

            // Transfer
            modelClass.isAssignableFrom(TransferViewModel::class.java) -> {
                TransferViewModel(
                    application.scheduleTransferUseCase,
                    application.completeTransferUseCase,
                    application.markUserNoShowUseCase,
                    application.cancelTransferUseCase
                ) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}