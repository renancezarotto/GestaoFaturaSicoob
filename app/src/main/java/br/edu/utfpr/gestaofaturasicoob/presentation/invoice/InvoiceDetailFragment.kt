package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentInvoiceDetailBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.Expense
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class InvoiceDetailFragment : Fragment() {

    private var _binding: FragmentInvoiceDetailBinding? = null
    private val binding get() = _binding!!

    private val invoiceController = InvoiceController()
    private val categoryController = CategoryController()
    private val authController = AuthController()

    private lateinit var adapter: ExpenseDetailAdapter
    private var invoice: Invoice? = null
    private var allExpenses: List<Expense> = emptyList()
    private var filteredExpenses: List<Expense> = emptyList()
    private var availableCategories: List<Category> = emptyList()
    private var searchQuery: String = ""

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormatDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    companion object {
        const val ARG_INVOICE_ID = "invoiceId"
        const val ARG_REFERENCE_MONTH = "referenceMonth"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupTabLayout()
        setupSearchField()
        loadData()

        binding.buttonDeleteInvoice.setOnClickListener {
            confirmDeleteInvoice()
        }
        
        // Setup payment status switch
        binding.switchPaymentStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show date selection when marking as paid
                binding.layoutPaymentDateSelection.visibility = View.VISIBLE
                // Don't update immediately, wait for date selection
            } else {
                // Hide date selection and mark as unpaid immediately
                binding.layoutPaymentDateSelection.visibility = View.GONE
                updatePaymentStatus(false, null)
            }
        }
        
        // Setup payment date selection
        binding.buttonSelectPaymentDate.setOnClickListener {
            showDatePicker()
        }
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Detalhes da Fatura"
            val backButton = it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)
            backButton?.visibility = View.VISIBLE
            backButton?.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ExpenseDetailAdapter(
            onCategorySelected = { expense, categoryId ->
                updateExpenseCategory(expense, categoryId)
            }
        )

        binding.recyclerExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InvoiceDetailFragment.adapter
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                applyFilters()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearchField() {
        binding.editTextSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun loadData() {
        lifecycleScope.launch {
            showLoading(true)
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }

                val referenceMonth = arguments?.getString(ARG_REFERENCE_MONTH) ?: ""

                // Load invoice
                val invoiceResult = invoiceController.getInvoiceByMonth(userId, referenceMonth)
                if (invoiceResult.isFailure) {
                    showError("Erro ao carregar fatura: ${invoiceResult.exceptionOrNull()?.message}")
                    showLoading(false)
                    return@launch
                }

                invoice = invoiceResult.getOrNull()
                allExpenses = invoice?.expenses ?: emptyList()
                filteredExpenses = allExpenses

                // Load categories
                val categoriesResult = categoryController.getAllCategories(userId)
                availableCategories = categoriesResult.getOrNull() ?: emptyList()

                // Update UI
                displayInvoiceData(invoice)
                adapter.submitList(filteredExpenses, availableCategories)
                updateTabsWithCategories()

                showLoading(false)

            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar dados: ${e.message}")
            }
        }
    }

    private fun displayInvoiceData(invoice: Invoice?) {
        if (invoice == null) return

        binding.textInvoiceTitle.text = "Fatura de ${invoice.referenceMonth}"
        binding.textInvoiceTotal.text = "Total: ${currencyFormat.format(invoice.totalValue)}"
        
        try {
            val dueDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(invoice.dueDate)
            binding.textDueDate.text = "Vencimento: ${dateFormatDisplay.format(dueDateParsed)}"
        } catch (e: Exception) {
            binding.textDueDate.text = "Vencimento: ${invoice.dueDate}"
        }

        binding.textExpenseCount.text = "${invoice.expenses.size} despesas"
        
        // Update payment status UI
        updatePaymentStatusUI(invoice)
    }
    
    private fun updatePaymentStatusUI(invoice: Invoice) {
        val today = java.util.Calendar.getInstance()
        val dueDate = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(invoice.dueDate)
            val cal = java.util.Calendar.getInstance()
            cal.time = parsed
            cal
        } catch (e: Exception) {
            null
        }
        
        // Update switch state
        binding.switchPaymentStatus.isChecked = invoice.isPaid
        
        if (invoice.isPaid) {
            // Invoice is paid
            binding.textPaymentStatus.text = "Paga"
            binding.textPaymentStatus.setBackgroundResource(R.drawable.status_background_paid)
            
            if (invoice.paidDate.isNotEmpty()) {
                try {
                    val paidDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(invoice.paidDate)
                    val paidDateFormatted = dateFormatDisplay.format(paidDateParsed)
                    
                    if (dueDate != null) {
                        val diffInDays = ((paidDateParsed.time - dueDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                        when {
                            diffInDays < 0 -> binding.textPaymentDate.text = "Paga com ${-diffInDays} dias de antecedência"
                            diffInDays == 0 -> binding.textPaymentDate.text = "Paga no prazo ($paidDateFormatted)"
                            else -> binding.textPaymentDate.text = "Paga com $diffInDays dias de atraso ($paidDateFormatted)"
                        }
                    } else {
                        binding.textPaymentDate.text = "Paga em: $paidDateFormatted"
                    }
                } catch (e: Exception) {
                    binding.textPaymentDate.text = "Paga em: ${invoice.paidDate}"
                }
            } else {
                binding.textPaymentDate.text = "Fatura paga"
            }
        } else {
            // Invoice is not paid
            binding.textPaymentStatus.text = "Pendente"
            binding.textPaymentStatus.setBackgroundResource(R.drawable.status_background_pending)
            
            if (dueDate != null) {
                val diffInDays = ((dueDate.timeInMillis - today.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
                when {
                    diffInDays < 0 -> {
                        binding.textPaymentStatus.text = "Vencida"
                        binding.textPaymentStatus.setBackgroundResource(R.drawable.status_background_overdue)
                        binding.textPaymentDate.text = "Vencida há ${-diffInDays} dias"
                    }
                    diffInDays == 0 -> {
                        binding.textPaymentDate.text = "Vence hoje"
                    }
                    else -> {
                        binding.textPaymentDate.text = "Pagar até: ${dateFormatDisplay.format(dueDate.time)}"
                    }
                }
            } else {
                binding.textPaymentDate.text = "Pagar até: ${invoice.dueDate}"
            }
        }
    }

    private fun updateTabsWithCategories() {
        binding.tabLayout.removeAllTabs()
        // Sempre adiciona "Todas"
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Todas"))

        // Agrupa por categoria e adiciona tabs para cada categoria com despesas
        val categoriesWithExpenses = allExpenses
            .mapNotNull { expense -> availableCategories.find { it.id == expense.category } }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }

        categoriesWithExpenses.forEach { category ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(category.name))
        }

        // Sempre adiciona "Não categorizadas"
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Não categorizadas"))
    }

    private fun applyFilters() {
        val tabPosition = binding.tabLayout.selectedTabPosition
        
        // First, filter by category
        var expenses = if (tabPosition == 0) {
            // Tab "Todas"
            allExpenses
        } else if (tabPosition == binding.tabLayout.tabCount - 1) {
            // Última aba: Não categorizadas
            allExpenses.filter { it.category == null || it.category?.isBlank() == true }
        } else {
            // Filter by category
            val categoryName = binding.tabLayout.getTabAt(tabPosition)?.text?.toString() ?: ""
            val category = availableCategories.find { it.name.equals(categoryName, ignoreCase = true) }
            allExpenses.filter { exp ->
                val cat = exp.category
                cat != null && cat == category?.id
            }
        }

        // Then, apply search filter if there's a search query
        if (searchQuery.isNotEmpty()) {
            expenses = expenses.filter { expense ->
                val matchesName = expense.establishment.contains(searchQuery, ignoreCase = true) ||
                                  expense.description.contains(searchQuery, ignoreCase = true)
                
                val matchesValue = try {
                    val formattedValue = currencyFormat.format(expense.value)
                    val plainValue = expense.value.toString()
                    formattedValue.contains(searchQuery, ignoreCase = true) ||
                    plainValue.contains(searchQuery, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
                
                matchesName || matchesValue
            }
        }

        filteredExpenses = expenses
        adapter.submitList(filteredExpenses, availableCategories)
        
        if (filteredExpenses.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerExpenses.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerExpenses.visibility = View.VISIBLE
        }
    }

    private fun updateExpenseCategory(expense: Expense, newCategoryId: String) {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                val invoiceId = invoice?.id ?: return@launch

                showLoading(true)

                val result = invoiceController.updateExpenseCategory(userId, invoiceId, expense.id, newCategoryId)

                showLoading(false)

                if (result.isSuccess) {
                    showMessage("Categoria atualizada com sucesso!")
                    
                    // Update local expense
                    allExpenses = allExpenses.map {
                        if (it.id == expense.id) it.copy(category = newCategoryId) else it
                    }
                    
                    // Refilter
                    applyFilters()
                    
                    // Save mapping for future auto-categorization
                    categoryController.saveEstablishmentCategoryMapping(userId, expense.establishment, newCategoryId)
                    
                } else {
                    showError("Erro ao atualizar categoria: ${result.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao atualizar categoria: ${e.message}")
            }
        }
    }
    
    private fun updatePaymentStatus(isPaid: Boolean, paidDate: String? = null) {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                val inv = invoice ?: return@launch
                
                val finalPaidDate = if (isPaid) {
                    paidDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Calendar.getInstance().time)
                } else {
                    null
                }
                
                val result = invoiceController.updatePaymentStatus(userId, inv.id, isPaid, finalPaidDate)
                
                if (result.isSuccess) {
                    // Update local invoice data
                    invoice = inv.copy(isPaid = isPaid, paidDate = finalPaidDate ?: "")
                    updatePaymentStatusUI(invoice!!)
                    
                    // Hide date selection after successful update
                    binding.layoutPaymentDateSelection.visibility = View.GONE
                    
                    showMessage(if (isPaid) "Fatura marcada como paga" else "Fatura marcada como pendente")
                } else {
                    // Revert switch state on error
                    binding.switchPaymentStatus.isChecked = !isPaid
                    binding.layoutPaymentDateSelection.visibility = View.GONE
                    showError("Erro ao atualizar status: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                // Revert switch state on error
                binding.switchPaymentStatus.isChecked = !isPaid
                binding.layoutPaymentDateSelection.visibility = View.GONE
                showError("Erro ao atualizar status: ${e.message}")
            }
        }
    }
    
    private fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        
        // Set default date to invoice due date or today
        val defaultDate = try {
            val dueDateParsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(invoice?.dueDate ?: "")
            if (dueDateParsed != null) {
                val cal = java.util.Calendar.getInstance()
                cal.time = dueDateParsed
                cal
            } else {
                calendar
            }
        } catch (e: Exception) {
            calendar
        }
        
        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = java.util.Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDate.time)
                updatePaymentStatus(true, dateString)
            },
            defaultDate.get(java.util.Calendar.YEAR),
            defaultDate.get(java.util.Calendar.MONTH),
            defaultDate.get(java.util.Calendar.DAY_OF_MONTH)
        )
        
        // Set max date to today (can't pay in the future)
        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        
        datePickerDialog.show()
    }

    // Quick delete option from detail screen
    private fun confirmDeleteInvoice() {
        val current = invoice ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir Fatura")
            .setMessage("Deseja realmente excluir a fatura de ${current.referenceMonth}?")
            .setPositiveButton("Excluir") { _, _ -> deleteInvoice() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteInvoice() {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                val inv = invoice ?: return@launch
                showLoading(true)
                val result = invoiceController.deleteInvoice(userId, inv.id)
                showLoading(false)
                if (result.isSuccess) {
                    showMessage("Fatura excluída")
                    findNavController().popBackStack()
                } else {
                    showError("Erro ao excluir: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao excluir: ${e.message}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.let { binding ->
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            binding.recyclerExpenses.visibility = if (show) View.GONE else View.VISIBLE
        }
    }

    private fun showMessage(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

