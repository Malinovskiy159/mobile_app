// domain/model/Item.kt
package com.darim.domain.model

import java.io.Serializable
import java.util.Date

data class Item(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val photos: List<String>, // Храним пути к файлам или URI строки
    val location: Location,
    val ownerId: String,
    val ownerName: String? = null,
    val ownerPhone: String? = null,
    val status: ItemStatus,
    val createdAt: Long,
    val bookedBy: String? = null,
    val views: Int = 0
) : Serializable {

    fun isAvailable(): Boolean = status == ItemStatus.AVAILABLE
    fun isBooked(): Boolean = status == ItemStatus.BOOKED
    fun isCompleted(): Boolean = status == ItemStatus.COMPLETED
}