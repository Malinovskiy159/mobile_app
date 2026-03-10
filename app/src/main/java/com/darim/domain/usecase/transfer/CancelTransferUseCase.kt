package com.darim.domain.usecase.transfer

import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository

class CancelTransferUseCase(private val transferRepository: TransferRepository) {
    suspend fun execute(transferId: String, reason: String): Result<Unit> {
        return transferRepository.updateTransferStatus(transferId, TransferStatus.CANCELLED)
    }
}