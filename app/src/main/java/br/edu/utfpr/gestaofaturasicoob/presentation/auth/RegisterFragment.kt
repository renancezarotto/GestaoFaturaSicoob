package br.edu.utfpr.gestaofaturasicoob.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentRegisterBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val controller = AuthController()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupListeners()
    }

    private fun setupListeners() {
        // Create Account button
        binding.buttonCreateAccount.setOnClickListener {
            val name = binding.inputName.text.toString().trim()
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString()
            
            if (validateInputs(name, email, password)) {
                registerUser(name, email, password)
            }
        }
        
        // Back to login link
        binding.linkBackToLogin.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun validateInputs(name: String, email: String, password: String): Boolean {
        var isValid = true
        
        // Clear previous errors
        binding.nameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        
        // Validate name
        if (name.isBlank()) {
            binding.nameInputLayout.error = "Nome é obrigatório"
            isValid = false
        } else if (name.length < 3) {
            binding.nameInputLayout.error = "Nome deve ter pelo menos 3 caracteres"
            isValid = false
        }
        
        // Validate email
        if (email.isBlank()) {
            binding.emailInputLayout.error = "Email é obrigatório"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Email inválido"
            isValid = false
        }
        
        // Validate password
        if (password.isBlank()) {
            binding.passwordInputLayout.error = "Senha é obrigatória"
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Senha deve ter pelo menos 6 caracteres"
            isValid = false
        }
        
        return isValid
    }

    private fun registerUser(name: String, email: String, password: String) {
        showLoading(true)
        
        lifecycleScope.launch {
            val result = controller.registerWithEmail(email, password, name)
            showLoading(false)
            
            if (result.isSuccess) {
                _binding?.let { binding ->
                    Snackbar.make(binding.root, "Conta criada com sucesso!", Snackbar.LENGTH_SHORT).show()
                }
                navigateToDashboard()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Erro ao criar conta"
                _binding?.let { binding ->
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToDashboard() {
        try {
            val currentDestination = findNavController().currentDestination?.id
            if (currentDestination != R.id.dashboardFragment) {
                findNavController().navigate(R.id.action_registerFragment_to_dashboardFragment)
            }
        } catch (e: Exception) {
            // Fallback - try to navigate anyway
            try {
                findNavController().navigate(R.id.action_registerFragment_to_dashboardFragment)
            } catch (e2: Exception) {
                // If navigation fails, just finish the activity or restart
                requireActivity().finish()
                requireActivity().startActivity(requireActivity().intent)
            }
        }
    }

    private fun showLoading(visible: Boolean) {
        _binding?.let { binding ->
            binding.loadingOverlay.visibility = if (visible) View.VISIBLE else View.GONE
            binding.buttonCreateAccount.isEnabled = !visible
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
