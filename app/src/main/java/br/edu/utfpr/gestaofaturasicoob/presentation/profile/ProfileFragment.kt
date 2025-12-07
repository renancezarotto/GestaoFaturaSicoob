package br.edu.utfpr.gestaofaturasicoob.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private val authController = AuthController()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupListeners()
        loadUserData()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Perfil"
            it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)?.visibility = View.GONE
        }
    }
    
    private fun setupListeners() {
        binding.buttonEditProfile.setOnClickListener {
            openEditProfileDialog()
        }
        
        // FAB removed - no photo functionality
        
        binding.buttonManageCategories.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_manageCategoriesFragment)
        }
        
        binding.buttonManageGoals.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_manageGoalsFragment)
        }
        
        binding.buttonLogout.setOnClickListener {
            confirmLogout()
        }
    }
    
    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val result = authController.getCompleteUserData()
                
                showLoading(false)
                
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    if (user != null) {
                        displayUserData(user)
                    } else {
                        navigateToLogin()
                    }
                } else {
                    showError("Erro ao carregar dados: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar dados: ${e.message}")
            }
        }
    }
    
    private fun displayUserData(user: br.edu.utfpr.gestaofaturasicoob.models.User) {
        // Display name (nickname if available, otherwise name)
        binding.textUserName.text = user.getDisplayName()
        binding.textUserEmail.text = user.email
        
        // Set default profile icon (no photo functionality)
        binding.imageUserPhoto.setImageResource(R.drawable.ic_profile)
        
        // Display "Usuário desde" info
        try {
            val yearFormat = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault())
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .parse(user.createdAt.split("T")[0])
            val year = if (date != null) yearFormat.format(date) else "2025"
            binding.textUserSince.text = "Usuário desde $year"
        } catch (e: Exception) {
            binding.textUserSince.text = "Usuário desde 2025"
        }
        
        // Display financial info
        displayFinancialInfo(user)
    }
    
    private fun displayFinancialInfo(user: br.edu.utfpr.gestaofaturasicoob.models.User) {
        val hasIncome = user.income != null && user.income > 0
        val hasPhone = !user.phone.isNullOrBlank()
        
        // Only show financial info card if there's actual data
        if (hasIncome || hasPhone) {
            binding.cardFinancialInfo.visibility = View.VISIBLE
            binding.textEmptyFinancialInfo.visibility = View.GONE
            
            // Display income
            if (hasIncome) {
                val currencyFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
                binding.textUserIncome.text = currencyFormat.format(user.income)
                binding.layoutIncomeInfo.visibility = View.VISIBLE
            } else {
                // Hide income info if not set
                binding.layoutIncomeInfo.visibility = View.GONE
            }
            
            // Display phone
            if (hasPhone) {
                binding.layoutPhoneInfo.visibility = View.VISIBLE
                binding.textUserPhone.text = user.phone
            } else {
                binding.layoutPhoneInfo.visibility = View.GONE
            }
        } else {
            // Hide everything if no financial info
            binding.cardFinancialInfo.visibility = View.GONE
            binding.textEmptyFinancialInfo.visibility = View.VISIBLE
        }
    }
    
    private fun openEditProfileDialog() {
        val dialog = EditProfileDialog()
        // Set listener to reload data when dialog dismisses
        dialog.onDismissListener = {
            loadUserData()
        }
        dialog.show(childFragmentManager, "EditProfileDialog")
    }
    
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair")
            .setMessage("Deseja realmente sair da sua conta?")
            .setPositiveButton("Sair") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun performLogout() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                authController.logout()
                
                showLoading(false)
                
                navigateToLogin()
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao sair: ${e.message}")
            }
        }
    }
    
    private fun navigateToLogin() {
        try {
            findNavController().navigate(R.id.loginFragment)
        } catch (e: Exception) {
            // Fallback
            findNavController().popBackStack(R.id.loginFragment, false)
        }
    }
    
    private fun showLoading(show: Boolean) {
        // TODO: Add progressBar to layout
        // binding.progressBar?.visibility = if (show) View.VISIBLE else View.GONE
        _binding?.let { binding ->
            binding.buttonLogout.isEnabled = !show
            binding.buttonEditProfile.isEnabled = !show
        }
    }
    
    private fun showMessage(message: String) {
        _binding?.let { binding ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun showError(message: String) {
        _binding?.let { binding ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Note: Data reloading now handled by onDismissListener in openEditProfileDialog()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

