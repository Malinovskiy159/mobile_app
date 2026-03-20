package com.darim.ui.profile


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.darim.R
import com.darim.databinding.FragmentLoginBinding
import com.darim.ui.MainActivity
import com.darim.ui.profile.LoginViewModel
import com.darim.ui.utils.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        (requireActivity() as MainActivity).getViewModelFactory()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
        setupTextWatchers()

        // Если пользователь уже залогинен, сразу переходим на главный экран
        if (SessionManager.isLoggedIn()) {
            navigateToMain()
        }
    }

    private fun setupListeners() {
        binding.buttonLogin.setOnClickListener {
            val phone = binding.editPhone.text.toString()
            val password = binding.editPassword.text.toString()

            if (validateInputs(phone, password)) {
                viewModel.login(phone, password)
            }
        }

        binding.buttonRegister.setOnClickListener {
            navigateToRegister()
        }

        binding.buttonGuest.setOnClickListener {
            viewModel.loginAsGuest()
        }
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is LoginViewModel.LoginResult.Success -> {
                    showSuccess("Добро пожаловать, ${result.user.name}!")
                    navigateToMain()
                }
                is LoginViewModel.LoginResult.Error -> {
                    showError(result.message)
                }
                is LoginViewModel.LoginResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.buttonLogin.isEnabled = false
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.buttonLogin.isEnabled = true
                }
            }
        }
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.buttonLogin.isEnabled = validateInputsForButton()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        binding.editPhone.addTextChangedListener(textWatcher)
        binding.editPassword.addTextChangedListener(textWatcher)
    }

    private fun validateInputs(phone: String, password: String): Boolean {
        var isValid = true

        if (phone.isBlank()) {
            binding.editPhone.error = "Введите номер телефона"
            isValid = false
        } else if (phone.length < 10) {
            binding.editPhone.error = "Некорректный номер"
            isValid = false
        }

        if (password.isBlank()) {
            binding.editPassword.error = "Введите пароль"
            isValid = false
        } else if (password.length < 4) {
            binding.editPassword.error = "Пароль слишком короткий"
            isValid = false
        }

        return isValid
    }

    private fun validateInputsForButton(): Boolean {
        val phone = binding.editPhone.text.toString()
        val password = binding.editPassword.text.toString()

        return phone.isNotBlank() && password.isNotBlank() &&
                phone.length >= 10 && password.length >= 4
    }

    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        binding.progressBar.visibility = View.GONE
        binding.buttonLogin.isEnabled = true
    }

    private fun navigateToMain() {
        // Анимированный переход на главный экран
        lifecycleScope.launch {
            delay(500) // Небольшая задержка для плавности
            (requireActivity() as MainActivity).loadFragment(
                com.darim.ui.list.ListFragment(),
                addToBackStack = false
            )
        }
    }

    private fun navigateToRegister() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RegisterFragment())
            .addToBackStack("register")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}