package com.darim.data.repository

import android.content.Context
import com.darim.domain.model.Location
import com.darim.domain.repository.LocationRepository
import com.darim.ui.utils.UserLocationManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepositoryImpl(
    private val context: Context
) : LocationRepository {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun getCurrentLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val result = Location(
                            lat = location.latitude,
                            lng = location.longitude,
                            address = "Текущее местоположение"
                        )
                        UserLocationManager.updateLocation(result)
                        continuation.resume(result)
                    } else {
                        requestFreshLocation { newLocation ->
                            continuation.resume(newLocation)
                        }
                    }
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            }
        }
    }

    override suspend fun requestLocationUpdate(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                    priority = com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                    interval = 1000
                    numUpdates = 1
                }

                val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                        val location = locationResult.lastLocation
                        if (location != null) {
                            val result = Location(
                                lat = location.latitude,
                                lng = location.longitude,
                                address = "Текущее местоположение"
                            )
                            continuation.resume(result)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                ).addOnCompleteListener {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }
            } catch (e: SecurityException) {
                continuation.resume(null)
            }
        }
    }

    private fun requestFreshLocation(callback: (Location?) -> Unit) {
        try {
            val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                priority = com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
                numUpdates = 1
            }

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val result = Location(
                            lat = location.latitude,
                            lng = location.longitude,
                            address = "Текущее местоположение"
                        )
                        UserLocationManager.updateLocation(result)
                        callback(result)
                    } else {
                        callback(null)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            ).addOnCompleteListener {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    override fun isLocationEnabled(): Boolean {
        return UserLocationManager.hasLocationPermission(context)
    }
}