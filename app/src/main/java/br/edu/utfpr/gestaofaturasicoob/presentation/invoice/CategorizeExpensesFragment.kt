package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
// import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentCategorizeExpensesBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedInvoiceData
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class CategorizeExpensesFragment : Fragment() {
    
    private var _binding: FragmentCategorizeExpensesBinding? = null
    private val binding get() = _binding!!
    
    private val invoiceController = InvoiceController()
    private val categoryController = CategoryController()
    private val authController = AuthController()
    
    // private val args: CategorizeExpensesFragmentArgs by navArgs()
    
    private lateinit var adapter: ExpenseCategorizeAdapter
    private var extractedInvoice: ExtractedInvoiceData? = null
    
    companion object {
        const val ARG_INVOICE = "invoice"
        
        fun newInstance(invoice: ExtractedInvoiceData): CategorizeExpensesFragment {
            return CategorizeExpensesFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_INVOICE, invoice)
                }
            }
        }
    }
    private val expenseCategoryMap = mutableMapOf<String, String>() // expenseId -> categoryId
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategorizeExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        extractedInvoice = arguments?.getParcelable(ARG_INVOICE)
        
        setupToolbar()
        setupRecyclerView()
        setupUI()
        loadCategories()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Categorizar Despesas"
            val backButton = it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)
            backButton?.visibility = View.VISIBLE
            backButton?.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ExpenseCategorizeAdapter(
            onCategorySelected = { expense, position, categoryId, categoryName ->
                // Chave estável baseada nos campos do item (alinha com geração de id no controller)
                val key = buildExpenseStableKey(expense)
                expenseCategoryMap[key] = categoryId
                updateProgress()
                
                // Salva mapeamento para futuras auto-categorizações
                lifecycleScope.launch { 
                    authController.getCurrentUserId()?.let { uid ->
                        categoryController.saveEstablishmentCategoryMapping(uid, expense.establishment, categoryId)
                    }
                }
            }
        )
        
        binding.recyclerExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CategorizeExpensesFragment.adapter
        }
    }
    
    private fun setupUI() {
        val invoice = extractedInvoice ?: return
        
        // Display invoice summary
        binding.textInvoiceTitle.text = "Fatura de ${invoice.referenceMonth}"
        binding.textInvoiceTotal.text = "Total: ${currencyFormat.format(invoice.totalValue)}"
        binding.textCategorizationProgress.text = "0 de ${invoice.expenses.size} despesas categorizadas"
        
        // Progress
        updateProgress()
        
        // Save button
        binding.buttonSave.setOnClickListener {
            saveInvoice()
        }
        
        // Load expenses
        adapter.submitList(invoice.expenses)
    }
    
    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                
                // Load default categories
                val defaultCategories = categoryController.getDefaultCategories().getOrNull() ?: emptyList()
                
                // Load user categories
                val userCategories = categoryController.getUserCategories(userId).getOrNull() ?: emptyList()
                
                // Provide categories to adapter for dropdown
                val allCategories = (defaultCategories + userCategories).distinctBy { it.id }
                adapter.setAvailableCategories(allCategories)
                
                // Auto-categorize expenses based on saved mappings
                autoCategorizeExpenses(userId)
                
            } catch (e: Exception) {
                showError("Erro ao carregar categorias: ${e.message}")
            }
        }
    }
    
    private fun autoCategorizeExpenses(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val invoice = extractedInvoice ?: return@launch
                
                val result = categoryController.autoCategorizeExpenses(userId, invoice.expenses)
                
                if (result.isSuccess) {
                    val mapping = result.getOrNull() ?: emptyMap()
                    var autoCategorizedCount = 0
                    
                    invoice.expenses.forEachIndexed { index, exp ->
                        // Check if this expense should be auto-categorized
                        val expenseKey = "${index}_${exp.establishment}"
                        mapping[expenseKey]?.let { catId ->
                            val stableKey = buildExpenseStableKey(exp)
                            expenseCategoryMap[stableKey] = catId
                            
                            // Get category name and update adapter
                            val categoryName = getCategoryNameById(catId)
                            if (categoryName != null) {
                                adapter.updateExpenseCategory(index, categoryName)
                                autoCategorizedCount++
                            }
                        }
                    }
                    
                    updateProgress()
                    
                    if (autoCategorizedCount > 0) {
                        showMessage("$autoCategorizedCount despesas categorizadas automaticamente")
                    }
                }
            } catch (e: Exception) {
                // Silent fail - user can categorize manually
            }
        }
    }
    
    /**
     * Get category name by ID
     */
    private suspend fun getCategoryNameById(categoryId: String): String? {
        return try {
            val defaultCategories = categoryController.getDefaultCategories().getOrNull() ?: emptyList()
            val userId = authController.getCurrentUserId() ?: return null
            val userCategories = categoryController.getUserCategories(userId).getOrNull() ?: emptyList()
            
            val allCategories = (defaultCategories + userCategories).distinctBy { it.id }
            allCategories.find { it.id == categoryId }?.name
        } catch (e: Exception) {
            null
        }
    }

    private fun adapterCategoryNameById(categoryId: String): String? {
        // Busca pelo nome nas categorias já setadas no adapter
        // Como o adapter não expõe a lista, usamos o último texto que foi aplicado via updateExpenseCategory
        // e apenas retornamos null aqui para não travar; no fluxo principal, a UI é atualizada por seleção
        return null
    }
    
    private fun showCategoryPicker(expense: ExtractedExpenseData, position: Int) {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                
                // Get all available categories
                val defaultCategories = categoryController.getDefaultCategories().getOrNull() ?: emptyList()
                val userCategories = categoryController.getUserCategories(userId).getOrNull() ?: emptyList()
                
                val allCategories = (defaultCategories + userCategories).distinctBy { it.id }
                
                val categoryNames = allCategories.map { it.name }.toTypedArray()
                
                AlertDialog.Builder(requireContext())
                    .setTitle("Selecione a categoria para ${expense.description}")
                    .setItems(categoryNames) { dialog, which ->
                        val selectedCategory = allCategories[which]
                        expenseCategoryMap[expense.description] = selectedCategory.id
                        
                        // Update adapter
                        adapter.updateExpenseCategory(position, selectedCategory.name)
                        
                        // Save mapping
                        saveCategoryMapping(userId, expense.establishment, selectedCategory.id)
                        
                        updateProgress()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                    
            } catch (e: Exception) {
                showError("Erro ao exibir categorias: ${e.message}")
            }
        }
    }
    
    private fun saveCategoryMapping(userId: String, establishment: String, categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                categoryController.saveEstablishmentCategoryMapping(userId, establishment, categoryId)
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }
    
    private fun updateProgress() {
        val invoice = extractedInvoice ?: return
        val totalExpenses = invoice.expenses.size
        val categorizedCount = countCategorized(invoice)
        
        val remaining = totalExpenses - categorizedCount
        binding.textCategorizationProgress.text = "$categorizedCount de $totalExpenses categorizadas${if (remaining > 0) " • $remaining pendentes" else ""}"
        binding.progressCategorization.max = totalExpenses
        binding.progressCategorization.progress = categorizedCount
        
        // Permite salvar mesmo com pendentes
        binding.buttonSave.isEnabled = true
        binding.buttonSave.text = if (remaining > 0) "Salvar parcialmente" else "Salvar fatura"
    }

    private fun countCategorized(invoice: ExtractedInvoiceData): Int {
        var count = 0
        invoice.expenses.forEachIndexed { index, exp ->
            val key = buildExpenseStableKey(exp)
            if (expenseCategoryMap.containsKey(key)) count++
        }
        return count
    }
    
    private fun saveInvoice() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                val invoice = extractedInvoice
                if (invoice == null) {
                    showError("Dados da fatura inválidos")
                    return@launch
                }
                
                val categorizedCount = countCategorized(invoice)
                if (categorizedCount != invoice.expenses.size) {
                    showMessage("Salvando parcialmente: $categorizedCount/${invoice.expenses.size}")
                }
                
                showLoading(true)
                
                // Monta mapping por chave estável (date|description|city|value|installment)
                val idKeyToCategory = mutableMapOf<String, String>()
                invoice.expenses.forEach { exp ->
                    val key = buildExpenseStableKey(exp)
                    expenseCategoryMap[key]?.let { catId -> idKeyToCategory[key] = catId }
                }
                
                val result = invoiceController.saveInvoice(userId, invoice, idKeyToCategory)
                
                showLoading(false)
                
                if (result.isSuccess) {
                    showMessage("Fatura salva com sucesso!")
                    navigateToDashboard()
                } else {
                    showError("Erro ao salvar fatura: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao salvar fatura: ${e.message}")
            }
        }
    }
    
    private fun navigateToDashboard() {
        try {
            // Navigate to invoices fragment after saving
            findNavController().popBackStack(R.id.invoicesFragment, false)
        } catch (e: Exception) {
            // Fallback - navigate directly to invoices fragment
            try {
                findNavController().navigate(R.id.invoicesFragment)
            } catch (e2: Exception) {
                // Last fallback - just pop back
                findNavController().popBackStack()
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        if (!isAdded || _binding == null) return
        binding.buttonSave.isEnabled = !show
    }

    private fun buildExpenseStableKey(expense: ExtractedExpenseData): String {
        val installmentPart = expense.installment ?: ""
        return "${expense.date}|${expense.description}|${expense.city}|${expense.value}|$installmentPart"
    }
    
    private fun showMessage(message: String) {
        val root = view
        if (isAdded && _binding != null && root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        } else {
            activity?.let { android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    
    private fun showError(message: String) {
        val root = view
        if (isAdded && _binding != null && root != null) {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
        } else {
            activity?.let { android.widget.Toast.makeText(it, message, android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
