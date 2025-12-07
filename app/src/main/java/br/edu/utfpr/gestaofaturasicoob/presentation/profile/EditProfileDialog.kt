package br.edu.utfpr.gestaofaturasicoob.presentation.profile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.databinding.DialogEditProfileBinding
import br.edu.utfpr.gestaofaturasicoob.models.User
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

/**
 * Dialog for editing user profile information
 * Simplified version without photo functionality
 */
class EditProfileDialog : DialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    private val authController = AuthController()
    private var currentUser: User? = null
    
    var onDismissListener: (() -> Unit)? = null

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_GestaoFaturaSicoob)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadUserData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener {
            dismiss()
        }

        // Setup buttons
        binding.buttonSave.setOnClickListener {
            saveProfile()
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        // Setup input masks
        setupInputMasks()
    }

    private fun setupInputMasks() {
        // Phone mask
        binding.editTextPhone.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return

                val clean = s.toString().replace(Regex("[^\\d]"), "")
                val formatted = when {
                    clean.isEmpty() -> ""
                    clean.length <= 2 -> clean
                    clean.length <= 6 -> "(${clean.substring(0, 2)}) ${clean.substring(2)}"
                    clean.length <= 10 -> "(${clean.substring(0, 2)}) ${clean.substring(2, 6)}-${clean.substring(6)}"
                    else -> "(${clean.substring(0, 2)}) ${clean.substring(2, 6)}-${clean.substring(6, 10)}"
                }

                if (formatted != s.toString()) {
                    isUpdating = true
                    binding.editTextPhone.setText(formatted)
                    binding.editTextPhone.setSelection(formatted.length)
                    isUpdating = false
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Income mask
        binding.editTextIncome.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return

                val clean = s.toString().replace(Regex("[^\\d]"), "")
                
                if (clean.isEmpty()) {
                    if (s.toString() != "") {
                        isUpdating = true
                        binding.editTextIncome.setText("")
                        isUpdating = false
                    }
                    return
                }

                val value = clean.toDoubleOrNull() ?: 0.0
                val formatted = currencyFormat.format(value / 100)

                if (formatted != s.toString()) {
                    isUpdating = true
                    binding.editTextIncome.setText(formatted)
                    binding.editTextIncome.setSelection(formatted.length)
                    isUpdating = false
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = authController.getCompleteUserData()
                if (result.isSuccess) {
                    currentUser = result.getOrNull()
                    currentUser?.let { user ->
                        displayUserData(user)
                    }
                } else {
                    showError("Erro ao carregar dados: ${result.exceptionOrNull()?.message}")
                }

                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar dados: ${e.message}")
            }
        }
    }

    private fun displayUserData(user: User) {
        // Set fields
        binding.editTextNickname.setText(user.nickname ?: user.name)
        binding.editTextEmail.setText(user.email)
        binding.editTextPhone.setText(user.phone ?: "")

        // Set income
        user.income?.let { income ->
            val formatted = String.format(Locale("pt", "BR"), "R$ %.2f", income)
            binding.editTextIncome.setText(formatted)
        }
    }

    private fun saveProfile() {
        if (!validateFields()) {
            return
        }

        lifecycleScope.launch {
            try {
                showLoading(true)

                // Get values
                val nickname = binding.editTextNickname.text.toString().trim()
                val phone = binding.editTextPhone.text.toString().trim().takeIf { it.isNotEmpty() }
                val incomeText = binding.editTextIncome.text.toString()
                    .replace("R$", "")
                    .replace(".", "")
                    .replace(",", ".")
                    .trim()
                val income = incomeText.toDoubleOrNull()

                // Update user data
                val result = authController.updateUserProfile(nickname, phone, income)

                showLoading(false)

                if (result.isSuccess) {
                    showSuccess("Perfil atualizado com sucesso!")
                    
                    // Close dialog after short delay
                    binding.root.postDelayed({
                        dismiss()
                    }, 1000)
                } else {
                    showError("Erro ao salvar: ${result.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao salvar: ${e.message}")
            }
        }
    }

    private fun validateFields(): Boolean {
        var isValid = true

        val nickname = binding.editTextNickname.text.toString().trim()
        if (nickname.isEmpty() || nickname.length < 2) {
            binding.layoutNickname.error = "Nome deve ter pelo menos 2 caracteres"
            isValid = false
        } else {
            binding.layoutNickname.error = null
        }

        val phone = binding.editTextPhone.text.toString().trim()
        if (phone.isNotEmpty() && phone.length < 14) {
            binding.layoutPhone.error = "Telefone inválido"
            isValid = false
        } else {
            binding.layoutPhone.error = null
        }

        return isValid
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonSave.isEnabled = !show
        binding.buttonCancel.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // Notify listener that dialog was dismissed
        onDismissListener?.invoke()
    }
}