// ui/MainActivity.kt
package com.darim.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.darim.R
import com.darim.data.repository.LocationRepositoryImpl
import com.darim.databinding.ActivityMainBinding
import com.darim.MyApplication
import com.darim.ui.list.ListFragment
import com.darim.ui.map.MapFragment
import com.darim.ui.myitems.MyItemsFragment
import com.darim.ui.profile.LoginFragment
import com.darim.ui.profile.ProfileFragment
import com.darim.ui.publish.PublishFragment
import com.darim.ui.utils.UserLocationManager
import com.darim.ui.utils.SessionManager
import com.darim.ui.publish.PublishViewModel
import com.darim.ui.utils.ViewModelFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomNavigationView: BottomNavigationView

    private val locationRepository by lazy {
        LocationRepositoryImpl(this)
    }

    // Получаем Application для доступа к UseCase
    private val app by lazy {
        application as MyApplication
    }

    // Создаем фабрику ViewModel
    val viewModelFactory by lazy {
        ViewModelFactory(app)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bottomNavigationView = binding.bottomNavigationView

        // Инициализируем SessionManager
        SessionManager.init(this)

        // Проверяем, залогинен ли пользователь
        if (savedInstanceState == null) {
            if (SessionManager.isLoggedIn()) {
                loadFragment(ListFragment(), addToBackStack = false)
            } else {
                loadFragment(LoginFragment(), addToBackStack = false)
            }
        }

        checkLocationPermission()

        setupBottomNavigation()

        supportFragmentManager.addOnBackStackChangedListener {
            updateBottomNavigationSelection()
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_list -> {
                    loadFragment(ListFragment(), addToBackStack = false)
                    true
                }
                R.id.navigation_map -> {
                    loadFragment(MapFragment(), addToBackStack = false)
                    true
                }
                R.id.navigation_add -> {
                    if (SessionManager.isLoggedIn()) {
                        loadFragment(PublishFragment(), addToBackStack = true)
                    } else {
                        Toast.makeText(this, "Войдите, чтобы добавить объявление", Toast.LENGTH_SHORT).show()
                        loadFragment(LoginFragment(), addToBackStack = true)
                    }
                    true
                }
                R.id.navigation_myitems -> {
                    if (SessionManager.isLoggedIn()) {
                        loadFragment(MyItemsFragment(), addToBackStack = false)
                    } else {
                        Toast.makeText(this, "Войдите, чтобы увидеть свои вещи", Toast.LENGTH_SHORT).show()
                        loadFragment(LoginFragment(), addToBackStack = true)
                    }
                    true
                }
                R.id.navigation_profile -> {
                    if (SessionManager.isLoggedIn()) {
                        loadFragment(ProfileFragment(), addToBackStack = false)
                    } else {
                        loadFragment(LoginFragment(), addToBackStack = true)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun loadFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }

        transaction.commit()
    }

    private fun updateBottomNavigationSelection() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        val menuItemId = when (currentFragment) {
            is ListFragment -> R.id.navigation_list
            is MapFragment -> R.id.navigation_map
            is MyItemsFragment -> R.id.navigation_myitems
            is ProfileFragment -> R.id.navigation_profile
            else -> null
        }

        menuItemId?.let {
            bottomNavigationView.menu.findItem(it).isChecked = true
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getUserLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionDialog()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun showLocationPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Разрешение на геолокацию")
            .setMessage("Для отображения объявлений рядом с вами необходимо разрешение на определение местоположения")
            .setPositiveButton("Разрешить") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Отмена") { _, _ ->
                Toast.makeText(this, "Без геолокации некоторые функции могут быть недоступны", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun getUserLocation() {
        lifecycleScope.launch {
            try {
                val location = locationRepository.getCurrentLocation()
                location?.let {
                    UserLocationManager.updateLocation(it)
                }
            } catch (e: SecurityException) {
                // Игнорируем
            } catch (e: Exception) {
                // Игнорируем
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getUserLocation()
                    Toast.makeText(this, "Геолокация доступна", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Для отображения объявлений рядом включите геолокацию в настройках", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            showExitConfirmationDialog()
        }
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Выход из приложения")
            .setMessage("Вы действительно хотите выйти?")
            .setPositiveButton("Да") { _, _ ->
                finish()
            }
            .setNegativeButton("Нет", null)
            .show()
    }

    @JvmName("getViewModelFactoryProvider")
    fun getViewModelFactory(): ViewModelFactory = viewModelFactory
}