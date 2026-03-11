package com.darim.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.io.Serializable  // ← Добавляем импорт для Serializable

enum class ItemStatus {
    AVAILABLE,
    BOOKED,
    COMPLETED,
    CANCELLED
}

data class Location(
    val lat: Double,
    val lng: Double,
    val address: String
) : Serializable {  // ← Добавляем Serializable
    fun distanceTo(other: Location): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(other.lat - this.lat)
        val dLng = Math.toRadians(other.lng - this.lng)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(this.lat)) * cos(Math.toRadians(other.lat)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}

data class Item(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val photos: List<String>,
    val location: Location,
    val ownerId: String,
    val phone: String,
    val status: ItemStatus,
    val createdAt: Long,
    val bookedBy: String? = null
) : Serializable {  // ← Добавляем Serializable
    fun isAvailable(): Boolean {
        return status == ItemStatus.AVAILABLE
    }
}