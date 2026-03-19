// MyApplication.kt
package com.darim

import android.app.Application
import com.darim.data.repository.*
import com.darim.domain.repository.*
import com.darim.domain.usecase.item.*
import com.darim.domain.usecase.location.GetUserLocationUseCase
import com.darim.domain.usecase.transfer.*
import com.darim.domain.usecase.user.*
import com.darim.ui.utils.SessionManager
import com.yandex.mapkit.MapKitFactory

class MyApplication : Application() {

    // ============== РЕПОЗИТОРИИ ==============

    private val itemRepository: ItemRepository by lazy {
        ItemRepositoryImpl(this)
    }

    private val userRepository: UserRepository by lazy {
        UserRepositoryImpl(this)
    }

    private val transferRepository: TransferRepository by lazy {
        TransferRepositoryImpl(this)
    }

    private val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(this)
    }

    // ============== USE CASES - ITEM ==============

    val getItemsUseCase: GetItemsUseCase by lazy {
        GetItemsUseCase(itemRepository)
    }

    val getItemDetailsUseCase: GetItemDetailsUseCase by lazy {
        GetItemDetailsUseCase(itemRepository, userRepository)
    }

    val getMyItemsUseCase: GetMyItemsUseCase by lazy {
        GetMyItemsUseCase(itemRepository)
    }

    val getMyBookingsUseCase: GetMyBookingsUseCase by lazy {
        GetMyBookingsUseCase(itemRepository, transferRepository, userRepository)
    }

    val getNearbyItemsUseCase: GetNearbyItemsUseCase by lazy {
        GetNearbyItemsUseCase(itemRepository, locationRepository)
    }

    val publishItemUseCase: PublishItemUseCase by lazy {
        PublishItemUseCase(itemRepository, userRepository, this)
    }

    val bookItemUseCase: BookItemUseCase by lazy {
        BookItemUseCase(itemRepository, userRepository, transferRepository)
    }

    // ============== USE CASES - TRANSFER ==============

    val scheduleTransferUseCase: ScheduleTransferUseCase by lazy {
        ScheduleTransferUseCase(transferRepository, itemRepository, userRepository)
    }

    val completeTransferUseCase: CompleteTransferUseCase by lazy {
        CompleteTransferUseCase(transferRepository, itemRepository, userRepository)
    }

    val markUserNoShowUseCase: MarkUserNoShowUseCase by lazy {
        MarkUserNoShowUseCase(transferRepository, userRepository)
    }

    val cancelTransferUseCase: CancelTransferUseCase by lazy {
        CancelTransferUseCase(transferRepository, itemRepository)
    }

    val updateUserRatingUseCase: UpdateUserRatingUseCase by lazy {
        UpdateUserRatingUseCase(userRepository)
    }

    val addReviewUseCase: AddReviewUseCase by lazy {
        AddReviewUseCase(userRepository, transferRepository)
    }

    // ============== USE CASES - LOCATION ==============

    val getUserLocationUseCase: GetUserLocationUseCase by lazy {
        GetUserLocationUseCase(locationRepository)
    }

    val getUserProfileUseCase: GetUserProfileUseCase by lazy {
        GetUserProfileUseCase(userRepository, itemRepository)
    }
    // Auth

    val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(userRepository)
    }

    val registerUseCase: RegisterUseCase by lazy {
        RegisterUseCase(userRepository)
    }

    // ============== ИНИЦИАЛИЗАЦИЯ ==============

    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        MapKitFactory.setApiKey("958486d0-a6f1-4c2e-9929-121d5c1d7fee")
    }
}