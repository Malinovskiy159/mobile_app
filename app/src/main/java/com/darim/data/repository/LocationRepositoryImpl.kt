// data/repository/LocationRepositoryImpl.kt
package com.darim.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import com.darim.domain.model.Location
import com.darim.domain.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationRepositoryImpl(
    private val context: Context
) : LocationRepository {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val geocoder: Geocoder by lazy {
        Geocoder(context, Locale.getDefault())
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Проверяем, включена ли геолокация
            if (!isLocationEnabled()) {
                return@withContext null
            }

            // Пытаемся получить последнюю известную локацию
            val locationTask = fusedLocationClient.lastLocation
            val androidLocation = Tasks.await(locationTask)

            if (androidLocation != null) {
                // Получаем адрес из координат
                val address = getAddressFromLocation(androidLocation.latitude, androidLocation.longitude)

                Location(
                    lat = androidLocation.latitude,
                    lng = androidLocation.longitude,
                    address = address ?: "Неизвестный адрес"
                )
            } else {
                // Если последняя локация неизвестна, запрашиваем обновление
                requestNewLocation()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestNewLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
                interval = 0
            }

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)

                    val androidLocation = locationResult.lastLocation
                    if (androidLocation != null && !continuation.isCompleted) {
                        val address = getAddressFromLocation(androidLocation.latitude, androidLocation.longitude)
                        val location = Location(
                            lat = androidLocation.latitude,
                            lng = androidLocation.longitude,
                            address = address ?: "Неизвестный адрес"
                        )
                        continuation.resume(location)
                    } else if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

            // Отменяем запрос, если корутина отменяется
            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }

        } catch (e: Exception) {
            if (!continuation.isCompleted) {
                continuation.resumeWithException(e)
            }
        }
    }

    override fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun requestLocationUpdate(): Location? = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!isLocationEnabled()) {
                return@withContext null
            }

            // Запрашиваем единоразовое обновление локации
            val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
                numUpdates = 1
                interval = 0
            }

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    // Обработка будет через Tasks.await
                }
            }

            // Используем suspendCancellableCoroutine для преобразования callback в suspend функцию
            return@withContext suspendCancellableCoroutine { continuation ->
                try {
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                        .addOnSuccessListener {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { androidLocation ->
                                    if (androidLocation != null) {
                                        val address = getAddressFromLocation(androidLocation.latitude, androidLocation.longitude)
                                        val location = Location(
                                            lat = androidLocation.latitude,
                                            lng = androidLocation.longitude,
                                            address = address ?: "Неизвестный адрес"
                                        )
                                        continuation.resume(location)
                                    } else {
                                        continuation.resume(null)
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    continuation.resumeWithException(exception)
                                }
                        }
                        .addOnFailureListener { exception ->
                            continuation.resumeWithException(exception)
                        }

                    continuation.invokeOnCancellation {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Получение адреса по координатам через Geocoder
     */
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildAddressString(address)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Формирование строки адреса из объекта Address
     */
    private fun buildAddressString(address: Address): String {
        val parts = mutableListOf<String>()

        // Добавляем название улицы
        address.thoroughfare?.let { parts.add(it) }

        // Добавляем номер дома
        address.subThoroughfare?.let { parts.add(it) }

        // Добавляем город
        address.locality?.let { parts.add(it) }

        // Если нет улицы и дома, добавляем доступные данные
        if (parts.isEmpty()) {
            address.adminArea?.let { parts.add(it) }
            address.countryName?.let { parts.add(it) }
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "Адрес не найден"
        }
    }

    /**
     * Получение названия города по координатам
     */
    suspend fun getCityFromLocation(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.locality
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Получение списка ближайших городов (для фильтрации)
     */
    suspend fun getNearbyCities(latitude: Double, longitude: Double, radius: Int): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Здесь можно реализовать запрос к API или базе данных городов
            // Для примера возвращаем пустой список
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Проверка точности текущей локации
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocationAccuracy(): Float? = withContext(Dispatchers.IO) {
        return@withContext try {
            val locationTask = fusedLocationClient.lastLocation
            val androidLocation = Tasks.await(locationTask)
            androidLocation?.accuracy
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}