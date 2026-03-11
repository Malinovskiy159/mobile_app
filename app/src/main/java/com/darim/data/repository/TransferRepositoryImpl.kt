// data/repository/TransferRepositoryImpl.kt
package com.darim.data.repository

import android.content.Context
import com.darim.domain.model.Location
import com.darim.domain.model.Transfer
import com.darim.domain.model.TransferStatus
import com.darim.domain.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TransferRepositoryImpl(private val context: Context) : TransferRepository {

    private val transfersFile: File by lazy {
        File(context.filesDir, "transfers.json")
    }

    private val transfers: MutableList<Transfer> by lazy {
        loadTransfersFromJson().toMutableList()
    }

    override suspend fun createTransfer(transfer: Transfer): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val newTransfer = transfer.copy(
                id = UUID.randomUUID().toString()
            )
            transfers.add(newTransfer)
            saveTransfersToJson()
            Result.success(newTransfer.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTransfer(id: String): Transfer? = withContext(Dispatchers.IO) {
        return@withContext transfers.find { it.id == id }
    }

    override suspend fun getTransfersForItem(itemId: String): List<Transfer> = withContext(Dispatchers.IO) {
        return@withContext transfers.filter { it.itemId == itemId }
    }

    override suspend fun updateTransferStatus(id: String, status: TransferStatus): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = transfers.indexOfFirst { it.id == id }
            if (index >= 0) {
                val updated = transfers[index].copy(status = status)
                transfers[index] = updated
                saveTransfersToJson()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Transfer not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeTransfer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val index = transfers.indexOfFirst { it.id == id }
            if (index >= 0) {
                val updated = transfers[index].copy(status = TransferStatus.COMPLETED)
                transfers[index] = updated
                saveTransfersToJson()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Transfer not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadTransfersFromJson(): List<Transfer> {
        return try {
            if (!transfersFile.exists()) {
                return emptyList()
            }

            val jsonString = transfersFile.readText()
            val jsonArray = JSONArray(jsonString)

            (0 until jsonArray.length()).map { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                jsonToTransfer(jsonObject)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveTransfersToJson() {
        try {
            val jsonArray = JSONArray()
            transfers.forEach { transfer ->
                jsonArray.put(transferToJson(transfer))
            }
            transfersFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun transferToJson(transfer: Transfer): JSONObject {
        return JSONObject().apply {
            put("id", transfer.id)
            put("itemId", transfer.itemId)
            put("giverId", transfer.giverId)
            put("takerId", transfer.takerId)
            put("status", transfer.status.name)
            put("scheduledTime", transfer.scheduledTime)
            put("meetingPoint", JSONObject().apply {
                put("lat", transfer.meetingPoint.lat)
                put("lng", transfer.meetingPoint.lng)
                put("address", transfer.meetingPoint.address)
            })
        }
    }

    private fun jsonToTransfer(json: JSONObject): Transfer {
        val meetingPointJson = json.getJSONObject("meetingPoint")

        return Transfer(
            id = json.getString("id"),
            itemId = json.getString("itemId"),
            giverId = json.getString("giverId"),
            takerId = json.getString("takerId"),
            status = TransferStatus.valueOf(json.getString("status")),
            scheduledTime = json.getLong("scheduledTime"),
            meetingPoint = Location(
                lat = meetingPointJson.getDouble("lat"),
                lng = meetingPointJson.getDouble("lng"),
                address = meetingPointJson.getString("address")
            )
        )
    }
}