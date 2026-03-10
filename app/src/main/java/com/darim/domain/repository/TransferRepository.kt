package com.darim.domain.repository

import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus

interface TransferRepository {
    suspend fun createTransfer(transfer: Transfer): Result<String>

    suspend fun getTransfer(id: String): Transfer?

    suspend fun getTransfersForItem(itemId: String): List<Transfer>

    suspend fun updateTransferStatus(id: String, status: TransferStatus): Result<Unit>

    suspend fun completeTransfer(id: String): Result<Unit>
}