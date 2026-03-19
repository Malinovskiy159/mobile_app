package com.darim.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.darim.R
import com.darim.domain.model.Item
import com.darim.domain.model.ItemStatus
import com.darim.ui.utils.FavoritesManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DetailFragment : Fragment() {

    private lateinit var viewPagerPhotos: ViewPager2
    private lateinit var sellerPhone: TextView
    private lateinit var favoriteButton: FloatingActionButton
    private var currentItem: Item? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Привязка к твоим ID из XML
        viewPagerPhotos = view.findViewById(R.id.viewPagerPhotos)
        sellerPhone = view.findViewById(R.id.sellerPhone)
        favoriteButton = view.findViewById(R.id.favoriteButton)
        val bookButton = view.findViewById<MaterialButton>(R.id.bookButton)

        loadData()

        favoriteButton.setOnClickListener {
            currentItem?.let {
                val isFav = FavoritesManager.toggleFavorite(requireContext(), it.id)
                updateFavIcon(isFav)
            }
        }

        sellerPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentItem?.ownerPhone}"))
            startActivity(intent)
        }
    }

    private fun loadData() {
        val item = arguments?.getSerializable("item") as? Item
        currentItem = item

        // ИСПОЛЬЗУЕМ view? (с вопросительным знаком)
        item?.let {
            view?.findViewById<TextView>(R.id.detailTitle)?.text = it.title
            view?.findViewById<TextView>(R.id.detailDescription)?.text = it.description
            sellerPhone.text = it.ownerPhone
            updateFavIcon(FavoritesManager.isFavorite(requireContext(), it.id))
        }
    }

    private fun updateFavIcon(isFav: Boolean) {
        favoriteButton.setImageResource(if (isFav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
    }

    companion object {
        fun newInstance(item: Item) = DetailFragment().apply {
            arguments = Bundle().apply { putSerializable("item", item) }
        }
    }
}