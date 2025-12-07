package br.edu.utfpr.gestaofaturasicoob.presentation.goals

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.controllers.GoalController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentManageGoalsBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.Goal
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class ManageGoalsFragment : Fragment() {
    
    private var _binding: FragmentManageGoalsBinding? = null
    private val binding get() = _binding!!
    
    private val goalController = GoalController()
    private val authController = AuthController()
    private val categoryController = CategoryController()
    
    private lateinit var goalsAdapter: SimpleGoalsAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadGoals()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(br.edu.utfpr.gestaofaturasicoob.R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(br.edu.utfpr.gestaofaturasicoob.R.id.textToolbarTitle)?.text = "Gerenciar Metas"
            val backButton = it.findViewById<com.google.android.material.button.MaterialButton>(br.edu.utfpr.gestaofaturasicoob.R.id.buttonBack)
            backButton?.visibility = android.view.View.VISIBLE
            backButton?.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }
    
    private fun setupRecyclerView() {
        goalsAdapter = SimpleGoalsAdapter(
            onEditClick = { goal -> showEditGoalDialog(goal) },
            onDeleteClick = { goal -> confirmDeleteGoal(goal) }
        )
        
        binding.recyclerGoals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = goalsAdapter
        }
    }
    
    private fun setupListeners() {
        binding.fabAddGoal?.setOnClickListener {
            showAddGoalDialog()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            loadGoals()
        }
    }
    
    private fun loadGoals() {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                showLoading(true)
                
                val result = goalController.getGoals(userId)
                
                showLoading(false)
                
                if (result.isSuccess) {
                    val goals = result.getOrNull() ?: emptyList()
                    goalsAdapter.submitList(goals)
                    
                    if (goals.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                    }
                } else {
                    showError("Erro ao carregar metas: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar metas: ${e.message}")
            }
        }
    }
    
    private fun showAddGoalDialog() {
        showGoalDialog(null)
    }
    
    private fun showEditGoalDialog(goal: Goal) {
        showGoalDialog(goal)
    }
    
    private fun showGoalDialog(existingGoal: Goal?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_goal, null)
        val dialog = android.app.Dialog(requireContext())
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.textDialogTitle)
        val categorySpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        val limitValueInput = dialogView.findViewById<TextInputEditText>(R.id.editTextLimitValue)
        val alert80Switch = dialogView.findViewById<SwitchMaterial>(R.id.switchAlert80)
        val alert100Switch = dialogView.findViewById<SwitchMaterial>(R.id.switchAlert100)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.buttonSave)
        val cancelButton = dialogView.findViewById<MaterialButton>(R.id.buttonCancel)
        
        // Configurar título
        titleText.text = if (existingGoal == null) "Nova Meta" else "Editar Meta"
        
        // Variável para armazenar categorias e mapear nome → ID
        var categoriesMap: Map<String, String> = emptyMap() // nome → categoryId
        
        // Carregar categorias no Spinner
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId != null) {
                    val result = categoryController.getCategories(userId)
                    if (result.isSuccess) {
                        val categories = result.getOrNull() ?: emptyList()
                        val categoryNames = categories.map { it.name }
                        // Cria mapa nome → ID para conversão
                        categoriesMap = categories.associate { it.name to it.id }
                        
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
                        
                        // Configurar o AutoCompleteTextView
                        categorySpinner.setAdapter(adapter)
                        categorySpinner.threshold = 1 // Mostra sugestões desde a primeira letra
                        
                        // Se for edição, precisa buscar nome da categoria pelo ID
                        if (existingGoal != null) {
                            val category = categories.find { it.id == existingGoal.category }
                            categorySpinner.setText(category?.name ?: existingGoal.category, false)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
        
        // Preencher dados se for edição
        if (existingGoal != null) {
            // Format currency for display
            val formattedValue = currencyFormat.format(existingGoal.limitValue)
            limitValueInput.setText(formattedValue)
            alert80Switch.isChecked = existingGoal.alertAt80
            alert100Switch.isChecked = existingGoal.alertAt100
        }
        
        // Setup currency input mask
        setupCurrencyInputMask(limitValueInput)
        
        // Botões
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
            saveButton.setOnClickListener {
            val categoryName = categorySpinner.text.toString().trim()
            val limitValueText = limitValueInput.text.toString().trim()
            
            if (categoryName.isBlank()) {
                categorySpinner.error = "Categoria é obrigatória"
                return@setOnClickListener
            }
            categorySpinner.error = null
            
            // Converte nome da categoria para categoryId
            val categoryId = categoriesMap[categoryName] ?: categoryName
            
            val limitValue = parseCurrencyInput(limitValueText)
            
            if (limitValue <= 0) {
                limitValueInput.error = "Valor deve ser maior que zero"
                return@setOnClickListener
            }
            limitValueInput.error = null
            
            val goal = Goal(
                id = existingGoal?.id ?: UUID.randomUUID().toString(),
                userId = "", // Será preenchido no saveGoal
                category = categoryId, // Salva categoryId em vez de nome
                limitValue = limitValue,
                alertAt80 = alert80Switch.isChecked,
                alertAt100 = alert100Switch.isChecked,
                monthlyReset = true,
                isActive = true,
                createdAt = existingGoal?.createdAt ?: java.time.Instant.now().toString()
            )
            
            dialog.dismiss()
            saveGoal(goal)
        }
        
        dialog.show()
    }
    
    private fun saveGoal(goal: Goal) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                // Verificar se já existe meta para esta categoria
                val existingGoalsResult = goalController.getGoals(userId)
                if (existingGoalsResult.isSuccess) {
                    val existingGoals = existingGoalsResult.getOrNull() ?: emptyList()
                    val existingGoalForCategory = existingGoals.find { it.category == goal.category && it.id != goal.id }
                    
                    // Se existe meta para esta categoria e NÃO é a mesma meta sendo editada
                    if (existingGoalForCategory != null) {
                        // Deletar a meta antiga
                        goalController.deleteGoal(userId, existingGoalForCategory.id)
                    }
                }
                
                val goalWithUserId = goal.copy(userId = userId)
                val result = if (goal.id.isEmpty() || !goal.id.contains("-")) {
                    // Nova meta
                    goalController.createGoal(userId, goalWithUserId)
                } else {
                    // Meta existente
                    goalController.updateGoal(userId, goal.id, goalWithUserId)
                }
                
                if (result.isSuccess) {
                    val message = if (goal.id.isEmpty() || !goal.id.contains("-")) {
                        "Meta criada!"
                    } else {
                        "Meta atualizada!"
                    }
                    showMessage(message)
                    loadGoals()
                } else {
                    showError("Erro ao salvar meta")
                }
            } catch (e: Exception) {
                showError("Erro: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun confirmDeleteGoal(goal: Goal) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir Meta")
            .setMessage("Deseja realmente excluir esta meta?")
            .setPositiveButton("Excluir") { _, _ ->
                deleteGoal(goal)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun deleteGoal(goal: Goal) {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                
                showLoading(true)
                
                val result = goalController.deleteGoal(userId, goal.id)
                
                showLoading(false)
                
                if (result.isSuccess) {
                    showMessage("Meta excluída com sucesso")
                    loadGoals()
                } else {
                    showError("Erro ao excluir meta: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao excluir meta: ${e.message}")
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerGoals.visibility = if (show) View.GONE else View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }
    
    private fun showEmptyState() {
        binding.emptyStateLayout.visibility = View.VISIBLE
    }
    
    private fun hideEmptyState() {
        binding.emptyStateLayout.visibility = View.GONE
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("OK") { }
            .show()
    }
    
    private fun setupCurrencyInputMask(editText: TextInputEditText) {
        var isUpdating = false
        
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return
                
                val clean = s.toString().replace(Regex("[^\\d]"), "")
                
                if (clean.isEmpty()) {
                    if (s.toString() != "") {
                        isUpdating = true
                        editText.setText("")
                        editText.setSelection(0)
                        isUpdating = false
                    }
                    return
                }
                
                val value = clean.toDoubleOrNull() ?: 0.0
                val formatted = currencyFormat.format(value / 100)
                
                if (formatted != s.toString()) {
                    isUpdating = true
                    editText.setText(formatted)
                    editText.setSelection(formatted.length)
                    isUpdating = false
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun parseCurrencyInput(input: String): Double {
        return try {
            val cleanText = input
                .replace("R$", "")
                .replace(".", "")
                .replace(",", ".")
                .trim()
            
            if (cleanText.isEmpty()) {
                0.0
            } else {
                cleanText.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
