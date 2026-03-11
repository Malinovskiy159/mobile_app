package com.darim.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.darim.R
import com.darim.ui.list.ListFragment
import com.darim.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottomNavigationView)

        // Загружаем начальный фрагмент (Объявления)
        if (savedInstanceState == null) {
            loadFragment(ListFragment())
        }

        // Обработка нажатий на иконки
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_list -> {
                    loadFragment(ListFragment())
                    true
                }
                R.id.navigation_favorites -> {
                    // Показываем заглушку
                    showPlaceholder("Избранное")
                    true
                }
                R.id.navigation_add -> {
                    showPlaceholder("Добавить объявление")
                    true
                }
                R.id.navigation_messages -> {
                    showPlaceholder("Сообщения")
                    true
                }
                R.id.navigation_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showPlaceholder(title: String) {
        android.widget.Toast.makeText(this, "$title (в разработке)", android.widget.Toast.LENGTH_SHORT).show()
    }
}