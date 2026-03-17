// ui/utils/PhotoManager.kt
package com.darim.ui.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object PhotoManager {

    private const val TAG = "PhotoManager"
    private const val MAX_IMAGE_SIZE = 1024
    private const val JPEG_QUALITY = 80

    /**
     * Сохраняет фото из Uri во внутреннее хранилище приложения
     */
    fun savePhotoToInternalStorage(context: Context, photoUri: Uri, itemId: String): String? {
        return try {
            val photosDir = File(context.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${itemId}_${timeStamp}.jpg"
            val file = File(photosDir, fileName)

            context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)

                val compressedBitmap = compressBitmap(bitmap, MAX_IMAGE_SIZE)

                FileOutputStream(file).use { outputStream ->
                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                }

                "photos/$fileName"
            } ?: run {
                Log.e(TAG, "Failed to open input stream for $photoUri")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving photo", e)
            null
        }
    }

    /**
     * Сохраняет фото из файла камеры во внутреннее хранилище
     */
    fun saveCameraPhotoToInternalStorage(context: Context, photoFile: File, itemId: String): String? {
        return try {
            val photosDir = File(context.filesDir, "photos")
            if (!photosDir.exists()) {
                photosDir.mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${itemId}_${timeStamp}.jpg"
            val destFile = File(photosDir, fileName)

            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            val compressedBitmap = compressBitmap(bitmap, MAX_IMAGE_SIZE)

            FileOutputStream(destFile).use { outputStream ->
                compressedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            }

            photoFile.delete()

            "photos/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Error saving camera photo", e)
            null
        }
    }

    /**
     * Получает URI для отображения в ImageView
     */
    fun getPhotoUri(context: Context, photoPath: String): Uri? {
        val file = File(context.filesDir, photoPath)
        return if (file.exists()) {
            Uri.fromFile(file)
        } else {
            Log.e(TAG, "Photo not found: $photoPath")
            null
        }
    }

    /**
     * Удаляет фото
     */
    fun deletePhoto(context: Context, photoPath: String): Boolean {
        return try {
            val file = File(context.filesDir, photoPath)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting photo", e)
            false
        }
    }

    /**
     * Сжимает битмап до нужного размера
     */
    private fun compressBitmap(source: Bitmap, maxSize: Int): Bitmap {
        var width = source.width
        var height = source.height

        if (width <= maxSize && height <= maxSize) {
            return source
        }

        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            width = maxSize
            height = (maxSize / ratio).toInt()
        } else {
            height = maxSize
            width = (maxSize * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * Получает битмап из URI
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bitmap from URI", e)
            null
        }
    }

    /**
     * Проверяет существование фото
     */
    fun photoExists(context: Context, photoPath: String): Boolean {
        val file = File(context.filesDir, photoPath)
        return file.exists()
    }
}