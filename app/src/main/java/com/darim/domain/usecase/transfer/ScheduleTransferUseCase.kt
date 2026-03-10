package com.darim.domain.usecase.transfer

import com.darim.domain.model.Location
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import java.util.UUID

class ScheduleTransferUseCase(private val transferRepository: TransferRepository) {
    suspend fun execute(
        itemId: String, giverId: String, takerId: String, time: Long, location: Location
    ): Result<String> {
        val transfer = Transfer(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            giverId = giverId,
            takerId = takerId,
            status = TransferStatus.SCHEDULED,
            scheduledTime = time,
            meetingPoint = location
        )
        return transferRepository.createTransfer(transfer)
    }
}