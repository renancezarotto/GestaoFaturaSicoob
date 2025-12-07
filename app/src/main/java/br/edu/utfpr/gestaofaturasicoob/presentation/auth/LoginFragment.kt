package br.edu.utfpr.gestaofaturasicoob.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class  LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val controller = AuthController()
    private lateinit var googleSignInClient: GoogleSignInClient

    // Activity result launcher for Google Sign-In
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken != null) {
                loginWithGoogle(idToken)
            } else {
                _binding?.let { binding ->
                    Snackbar.make(binding.root, "Erro ao obter token do Google", Snackbar.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            _binding?.let { binding ->
                Snackbar.make(binding.root, "Login com Google cancelado", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        // Initialize Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
    }

    private fun setupListeners() {
        // Email login button
        binding.buttonEmailLogin.setOnClickListener {
            val email = binding.inputEmail.text.toString().trim()
            val password = binding.inputPassword.text.toString()

            if (validateInputs(email, password)) {
                loginWithEmail(email, password)
            }
        }

        // Google login button
        binding.buttonGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // Create account link
        binding.linkCreateAccount.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null

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

    private fun loginWithEmail(email: String, password: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = controller.loginWithEmail(email, password)
            showLoading(false)

            if (result.isSuccess) {
                _binding?.let { binding ->
                    Snackbar.make(binding.root, "Login realizado com sucesso!", Snackbar.LENGTH_SHORT).show()
                }
                navigateToDashboard()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Erro ao fazer login"
                _binding?.let { binding ->
                    Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loginWithGoogle(idToken: String) {
        showLoading(true)

        lifecycleScope.launch {
            val result = controller.loginWithGoogle(idToken)
            showLoading(false)

            if (result.isSuccess) {
                _binding?.let { binding ->
                    Snackbar.make(binding.root, "Login realizado com sucesso!", Snackbar.LENGTH_SHORT).show()
                }
                navigateToDashboard()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Erro ao fazer login com Google"
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
                findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
            }
        } catch (e: Exception) {
            // Fallback - try to navigate anyway
            try {
                findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
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
            binding.buttonEmailLogin.isEnabled = !visible
            binding.buttonGoogleLogin.isEnabled = !visible
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
