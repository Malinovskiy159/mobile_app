package com.darim.domain.usecase.transfer

import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import com.darim.domain.repository.UserRepository

class CompleteTransferUseCase(
    private val transferRepository: TransferRepository,
    private val userRepository: UserRepository
) {
    suspend fun execute(transferId: String, rating: Int?): Result<Unit> {
        val result = transferRepository.completeTransfer(transferId)

        if (result.isSuccess && rating != null) {
            val transfer = transferRepository.getTransfer(transferId)
            if (transfer != null) {
                updateRatings(transfer.giverId, transfer.takerId, rating)
            }
        }
        return result
    }

    private suspend fun updateRatings(giverId: String, takerId: String, rating: Int) {
        // Update rating for Giver
        userRepository.updateRating(giverId, rating)
    }
}