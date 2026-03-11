package com.darim.ui.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.domain.model.Item
import com.darim.ui.utils.UserLocationManager
import java.text.DecimalFormat

class ItemAdapter(
    private var items: List<Item>,
    private val onItemClick: (Item) -> Unit  // ← передаем Item, а не String
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.itemImage)
        private val titleTextView: TextView = itemView.findViewById(R.id.itemTitle)
        private val categoryTextView: TextView = itemView.findViewById(R.id.itemCategory)
        private val distanceTextView: TextView = itemView.findViewById(R.id.itemDistance)

        private val distanceFormat = DecimalFormat("#.#")

        fun bind(item: Item) {
            titleTextView.text = item.title
            categoryTextView.text = item.category

            val userLocation = UserLocationManager.userLocation.value
            val distanceText = if (userLocation != null) {
                val distance = userLocation.distanceTo(item.location)
                when {
                    distance < 1.0 -> "${(distance * 1000).toInt()} м"
                    distance < 10.0 -> "${distanceFormat.format(distance)} км"
                    else -> "${distance.toInt()} км"
                }
            } else {
                "расчет..."
            }

            distanceTextView.text = "📍 $distanceText"
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item)  // ← передаем весь объект
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }
}