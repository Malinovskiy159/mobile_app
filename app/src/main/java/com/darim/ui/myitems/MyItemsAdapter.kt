// ui/myitems/MyItemsAdapter.kt
package com.darim.ui.myitems

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.darim.R
import com.darim.databinding.ItemMyItemBinding
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus

class MyItemsAdapter(
    private var items: List<Item>,
    private val onItemClick: (Item) -> Unit,
    private val onEditClick: (Item) -> Unit,
    private val onDeleteClick: (Item) -> Unit,
    private val onManageClick: (Item) -> Unit
) : RecyclerView.Adapter<MyItemsAdapter.ItemViewHolder>() {

    class ItemViewHolder(
        private val binding: ItemMyItemBinding,
        private val onItemClick: (Item) -> Unit,
        private val onEditClick: (Item) -> Unit,
        private val onDeleteClick: (Item) -> Unit,
        private val onManageClick: (Item) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.apply {
                textTitle.text = item.title
                textCategory.text = item.category
                textStatus.text = when (item.status) {
                    ItemStatus.AVAILABLE -> "Доступно"
                    ItemStatus.BOOKED -> "Забронировано"
                    ItemStatus.COMPLETED -> "Завершено"
                    ItemStatus.CANCELLED -> "Отменено"
                }

                val statusColor = when (item.status) {
                    ItemStatus.AVAILABLE -> android.graphics.Color.parseColor("#4CAF50")
                    ItemStatus.BOOKED -> android.graphics.Color.parseColor("#FF9800")
                    ItemStatus.COMPLETED -> android.graphics.Color.parseColor("#2196F3")
                    ItemStatus.CANCELLED -> android.graphics.Color.parseColor("#F44336")
                }
                textStatus.setTextColor(statusColor)

                root.setOnClickListener { onItemClick(item) }
                buttonEdit.setOnClickListener { onEditClick(item) }
                buttonDelete.setOnClickListener { onDeleteClick(item) }

                // Для ImageButton нельзя установить текст, только иконку
                // Вместо этого меняем иконку в зависимости от статуса
                buttonManage.setOnClickListener { onManageClick(item) }

                // Показываем/скрываем кнопку управления в зависимости от статуса
                if (item.status == ItemStatus.AVAILABLE) {
                    buttonManage.visibility = View.GONE
                } else {
                    buttonManage.visibility = View.VISIBLE
                    // Меняем иконку в зависимости от статуса
                    val iconRes = when (item.status) {
                        ItemStatus.BOOKED -> android.R.drawable.ic_menu_edit
                        ItemStatus.COMPLETED -> android.R.drawable.ic_menu_info_details
                        else -> android.R.drawable.ic_menu_more
                    }
                    buttonManage.setImageResource(iconRes)

                    // Можно добавить tooltip для пояснения
                    buttonManage.contentDescription = when (item.status) {
                        ItemStatus.BOOKED -> "Управление бронированием"
                        ItemStatus.COMPLETED -> "Просмотр деталей"
                        else -> "Действия"
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemMyItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItemViewHolder(binding, onItemClick, onEditClick, onDeleteClick, onManageClick)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }
}