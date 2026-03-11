package com.darim.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.darim.R
import com.darim.data.repository.LocationRepositoryImpl
import com.darim.domain.model.Location
import com.darim.ui.utils.UserLocationManager
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var addPhotoText: TextView
    private lateinit var userNameEdit: EditText
    private lateinit var locationText: TextView
    private lateinit var specifyLocationText: TextView
    private lateinit var autoDetectButton: Button
    private lateinit var manualLocationEdit: EditText
    private lateinit var manualLocationButton: Button
    private lateinit var manualLocationLayout: View

    private val locationRepository by lazy {
        LocationRepositoryImpl(requireContext())
    }

    // Для выбора фото из галереи
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            profileImageView.setImageURI(it)
            addPhotoText.isVisible = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadCurrentLocation()
    }

    private fun initViews(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
        addPhotoText = view.findViewById(R.id.addPhotoText)
        userNameEdit = view.findViewById(R.id.userNameEdit)
        locationText = view.findViewById(R.id.locationText)
        specifyLocationText = view.findViewById(R.id.specifyLocationText)
        autoDetectButton = view.findViewById(R.id.autoDetectButton)
        manualLocationEdit = view.findViewById(R.id.manualLocationEdit)
        manualLocationButton = view.findViewById(R.id.manualLocationButton)
        manualLocationLayout = view.findViewById(R.id.manualLocationLayout)
    }

    private fun setupListeners() {
        // Выбор фото
        profileImageView.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        addPhotoText.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Показать поле для ручного ввода
        specifyLocationText.setOnClickListener {
            manualLocationLayout.isVisible = !manualLocationLayout.isVisible
        }

        // Автоопределение
        autoDetectButton.setOnClickListener {
            checkLocationPermissionAndDetect()
        }

        // Ручной ввод
        manualLocationButton.setOnClickListener {
            val address = manualLocationEdit.text.toString()
            if (address.isNotBlank()) {
                locationText.text = address
                manualLocationLayout.isVisible = false
                manualLocationEdit.text.clear()
                Toast.makeText(requireContext(), "Геолокация указана: $address", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Введите адрес", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurrentLocation() {
        val currentLocation = UserLocationManager.userLocation.value
        if (currentLocation != null) {
            updateLocationDisplay(currentLocation)
        } else {
            locationText.text = "Геолокация не определена"
        }
    }

    private fun updateLocationDisplay(location: Location) {
        lifecycleScope.launch {
            try {
                val geocoder = Geocoder(requireContext(), Locale("ru"))
                val addresses = geocoder.getFromLocation(location.lat, location.lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val addressText = listOfNotNull(
                        address.thoroughfare,
                        address.locality,
                        address.adminArea
                    ).joinToString(", ")
                    locationText.text = addressText
                } else {
                    locationText.text = "${location.lat}, ${location.lng}"
                }
            } catch (e: Exception) {
                locationText.text = "${location.lat}, ${location.lng}"
            }
        }
    }

    private fun checkLocationPermissionAndDetect() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                detectLocation()
            }
            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun detectLocation() {
        lifecycleScope.launch {
            Toast.makeText(requireContext(), "Определяем местоположение...", Toast.LENGTH_SHORT).show()
            val location = locationRepository.requestLocationUpdate()
            if (location != null) {
                updateLocationDisplay(location)
                Toast.makeText(requireContext(), "Местоположение определено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_SHORT).show()
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
                    detectLocation()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Разрешение не получено",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
    }
}