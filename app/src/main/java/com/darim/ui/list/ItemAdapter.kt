// ui/list/ItemAdapter.kt
package com.darim.ui.list

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.ui.utils.LocationHelper
import com.darim.ui.utils.PhotoManager
import com.darim.ui.utils.UserLocationManager
import java.text.DecimalFormat

class ItemAdapter(
    private var items: List<Item>,
    private val onItemClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    private val distanceFormat = DecimalFormat("#.#")

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imageView: ImageView = itemView.findViewById(R.id.itemImage)
        private val titleTextView: TextView = itemView.findViewById(R.id.itemTitle)
        private val categoryTextView: TextView = itemView.findViewById(R.id.itemCategory)
        private val distanceTextView: TextView = itemView.findViewById(R.id.itemDistance)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val photoCountBadge: TextView? = itemView.findViewById(R.id.photoCountBadge)

        fun bind(item: Item) {
            titleTextView.text = item.title
            categoryTextView.text = item.category

            // Устанавливаем статус
            setupStatusBadge(item.status)

            // Рассчитываем расстояние
            val distanceText = calculateDistanceText(item)
            distanceTextView.text = "📍 $distanceText"

            // Загружаем изображение
            loadImage(item)

            // Показываем количество фото
            photoCountBadge?.text = if (item.photos.size > 1) {
                "${item.photos.size} фото"
            } else {
                null
            }
            photoCountBadge?.visibility = if (item.photos.size > 1) View.VISIBLE else View.GONE
        }

        private fun setupStatusBadge(status: ItemStatus) {
            statusBadge.visibility = View.VISIBLE
            statusBadge.text = when (status) {
                ItemStatus.AVAILABLE -> "Доступно"
                ItemStatus.BOOKED -> "Забронировано"
                ItemStatus.COMPLETED -> "Завершено"
                ItemStatus.CANCELLED -> "Отменено"
            }

            val backgroundColor = when (status) {
                ItemStatus.AVAILABLE -> android.graphics.Color.parseColor("#4CAF50")
                ItemStatus.BOOKED -> android.graphics.Color.parseColor("#FF9800")
                ItemStatus.COMPLETED -> android.graphics.Color.parseColor("#2196F3")
                ItemStatus.CANCELLED -> android.graphics.Color.parseColor("#F44336")
            }

            statusBadge.setBackgroundColor(backgroundColor)
            statusBadge.setTextColor(android.graphics.Color.WHITE)
        }

        private fun calculateDistanceText(item: Item): String {
            val userLocation = UserLocationManager.getLastKnownLocation()

            return if (userLocation != null) {
                val distance = LocationHelper.calculateDistance(
                    userLocation.lat,
                    userLocation.lng,
                    item.location.lat,
                    item.location.lng
                )

                when {
                    distance < 1.0 -> "${(distance * 1000).toInt()} м"
                    distance < 10.0 -> "${String.format("%.1f", distance)} км"
                    else -> "${distance.toInt()} км"
                }
            } else {
                "расчет..."
            }
        }

        private fun loadImage(item: Item) {
            // Показываем заглушку
            imageView.setImageResource(R.drawable.placeholder_image)

            // Загружаем первое фото, если оно есть
            if (item.photos.isNotEmpty()) {
                val photoPath = item.photos[0]
                val uri = PhotoManager.getPhotoUri(itemView.context, photoPath)

                if (uri != null) {
                    // Загружаем фото в отдельном потоке
                    loadImageFromUri(uri)
                } else {
                    // Если фото не найдено, показываем заглушку
                    imageView.setImageResource(R.drawable.placeholder_image)
                }
            }
        }

        private fun loadImageFromUri(uri: Uri) {
            try {
                // Опции для уменьшения размера изображения
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                itemView.context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)

                    // Вычисляем степень сжатия
                    options.inSampleSize = calculateInSampleSize(options, 200, 200)
                    options.inJustDecodeBounds = false

                    // Загружаем сжатое изображение
                    itemView.context.contentResolver.openInputStream(uri)?.use { newStream ->
                        val bitmap = BitmapFactory.decodeStream(newStream, null, options)
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                imageView.setImageResource(R.drawable.placeholder_image)
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grid, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }
}