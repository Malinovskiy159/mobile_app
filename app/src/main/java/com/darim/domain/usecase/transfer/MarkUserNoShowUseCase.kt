package com.darim.domain.usecase.transfer

import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository

class MarkUserNoShowUseCase(
    private val transferRepository: TransferRepository,
    private val userRepository: UserRepository
) {
    suspend fun execute(transferId: String, reporterId: String): Result<Unit> {
        val transfer = transferRepository.getTransfer(transferId)
            ?: return Result.failure(Exception("Встреча не найдена"))

        val noShowUserId = if (transfer.giverId == reporterId) transfer.takerId else transfer.giverId

        val updateResult = transferRepository.updateTransferStatus(transferId, TransferStatus.NO_SHOW)

        if (updateResult.isSuccess) {
            penalizeUser(noShowUserId)
        }
        return updateResult
    }

    private suspend fun penalizeUser(userId: String) {
        // TODO: write logic for penalizing
        userRepository.updateRating(userId, 1)
    }
}