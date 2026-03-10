package com.darim.domain.model

data class Item(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val photos: List<String>,
    val location: Location,
    val ownerId: String,
    val status: ItemStatus,
    val createdAt: Long,
    val bookedBy: String? = null
) {
    fun isAvailable(): Boolean {
        return status == ItemStatus.AVAILABLE
    }
}