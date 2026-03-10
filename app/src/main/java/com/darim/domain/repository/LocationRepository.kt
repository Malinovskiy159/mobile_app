package com.darim.domain.repository

import com.darim.domain.model.Location

interface LocationRepository {
    suspend fun getCurrentLocation(): Location?

    fun isLocationEnabled(): Boolean

    suspend fun requestLocationUpdate(): Location?
}