package com.darim.domain.model

data class Transfer(
    val id: String,
    val itemId: String,
    val giverId: String,
    val takerId: String,
    val status: TransferStatus,
    val scheduledTime: Long,
    val meetingPoint: Location
) {
    fun isCompleted(): Boolean {
        return status == TransferStatus.COMPLETED
    }
}