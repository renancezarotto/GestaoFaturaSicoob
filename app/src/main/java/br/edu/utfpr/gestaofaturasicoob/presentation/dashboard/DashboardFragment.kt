package br.edu.utfpr.gestaofaturasicoob.presentation.dashboard

import android.graphics.Color
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
import br.edu.utfpr.gestaofaturasicoob.controllers.DashboardController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentDashboardBinding
import br.edu.utfpr.gestaofaturasicoob.models.DashboardData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import br.edu.utfpr.gestaofaturasicoob.utils.MonthFilterManager

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Fragment may have been destroyed.")
    
    private val dashboardController = DashboardController()
    private val authController = AuthController()
    private val categoryController = CategoryController()
    
    // Adapters
    private lateinit var insightsAdapter: InsightsAdapter
    private lateinit var goalsAdapter: GoalsAdapter
    
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private var selectedMonth: String? = null
    private var allCategories: List<br.edu.utfpr.gestaofaturasicoob.models.Category> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerViews()
        setupPieChart()
        setupListeners()
        
        // Load saved month filter
        selectedMonth = MonthFilterManager.getSelectedMonth(requireContext())
        loadDashboardData()
    }
    
    private fun setupToolbar() {
        // Dashboard é a tela principal, sem botão voltar
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Dashboard"
            it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)?.visibility = View.GONE
        }
    }
    
    private fun setupRecyclerViews() {
        // Insights RecyclerView
        insightsAdapter = InsightsAdapter()
        binding.recyclerViewInsights.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = insightsAdapter
        }
        
        // Goals RecyclerView
        goalsAdapter = GoalsAdapter(
            onGoalClick = { goal ->
                // Navigate to manage goals
                try {
                    findNavController().navigate(R.id.action_dashboardFragment_to_manageGoalsFragment)
                } catch (e: Exception) {
                    // Already at destination or navigation failed
                }
            },
            allCategories = emptyList() // Will be updated when categories are loaded
        )
        binding.recyclerViewGoals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = goalsAdapter
        }
        
        // Category Legend RecyclerView removed - no longer needed
    }
    
    private fun setupPieChart() {
        // PieChart will be dynamically created and added to containerPieChart
        // Using a simple view instead of MPAndroidChart for simplicity
    }
    
    private fun setupListeners() {
        binding.buttonAddInvoiceEmpty.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboardFragment_to_uploadInvoiceFragment)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erro ao navegar", Snackbar.LENGTH_SHORT).show()
            }
        }
        
        binding.buttonAddGoal.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboardFragment_to_manageGoalsFragment)
            } catch (e: Exception) {
                // Already at destination or navigation failed
            }
        }
        
        binding.buttonFilterMonth.setOnClickListener {
            showMonthFilter()
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            selectedMonth = null
            loadDashboardData()
        }
    }
    
    private fun showMonthFilter() {
        lifecycleScope.launch {
            val userId = authController.getCurrentUserId()
            if (userId == null) {
                showError("Usuário não autenticado")
                return@launch
            }
            
            // Get all invoices to know which months have data
            val invoicesResult = InvoiceController().getInvoices(userId)
            if (invoicesResult.isSuccess) {
                val invoices = invoicesResult.getOrNull() ?: emptyList()
                val availableMonths = invoices.map { it.referenceMonth }.distinct().sorted()
                
                if (availableMonths.isEmpty()) {
                    showMessage("Nenhuma fatura cadastrada ainda")
                    return@launch
                }
                
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle("Selecione o Mês")
                builder.setItems(availableMonths.toTypedArray()) { _, which ->
                    selectedMonth = availableMonths[which]
                    MonthFilterManager.saveSelectedMonth(requireContext(), selectedMonth)
                    loadDashboardData()
                }
                builder.setNeutralButton("Limpar") { _, _ ->
                    selectedMonth = null
                    MonthFilterManager.clearSelectedMonth(requireContext())
                    loadDashboardData()
                }
                builder.show()
            } else {
                showError("Erro ao carregar faturas")
            }
        }
    }
    
    private fun loadDashboardData() {
        lifecycleScope.launch {
            showLoading(true)
            
            val userId = authController.getCurrentUserId()
            if (userId == null) {
                showError("Usuário não autenticado")
                showLoading(false)
                return@launch
            }
            
            // Carregar categorias junto com os dados do dashboard
            val categoriesResult = categoryController.getAllCategories(userId)
            if (categoriesResult.isSuccess) {
                allCategories = categoriesResult.getOrNull() ?: emptyList()
                // Atualizar categorias no GoalsAdapter
                goalsAdapter.updateCategories(allCategories)
            }
            
            val result = if (selectedMonth != null) {
                dashboardController.getDashboardDataForMonth(userId, selectedMonth!!)
            } else {
                dashboardController.getDashboardData(userId)
            }
            
            showLoading(false)
            
            if (result.isSuccess) {
                val dashboardData = result.getOrNull()
                updateUI(dashboardData)
                
                // Update title to show selected month
                _binding?.let { binding ->
                    if (selectedMonth != null) {
                        binding.textCurrentInvoiceTitle.text = "📅 Fatura: $selectedMonth"
                    } else {
                        binding.textCurrentInvoiceTitle.text = "📅 Última Fatura"
                    }
                }
            } else {
                showError(result.exceptionOrNull()?.message ?: "Erro ao carregar dados")
            }
        }
    }
    
    private fun updateUI(data: DashboardData?) {
        if (_binding == null) return // Fragment destroyed
        
        if (data == null) {
            showEmptyState()
            return
        }
        
        val hasInvoice = data.currentInvoice != null
        
        // Show/hide empty state
        binding.emptyStateLayout.visibility = if (hasInvoice) View.GONE else View.VISIBLE
        binding.scrollViewDashboard.visibility = if (hasInvoice) View.VISIBLE else View.GONE
        
        if (!hasInvoice) return
        
        // Update invoice summary
        updateInvoiceSummary(data)
        
        // Update insights
        insightsAdapter.submitList(data.insights)
        binding.textInsightsTitle.visibility = if (data.insights.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recyclerViewInsights.visibility = if (data.insights.isNotEmpty()) View.VISIBLE else View.GONE
        
        // Update goals with progress - calculate from invoice expenses for selected month
        val goalProgressList = data.activeGoals.map { goal ->
            val spent = if (data.currentInvoice != null) {
                // Calculate spent for this category in the current invoice
                // Need to match both Portuguese and English category names
                val matchingExpenses = data.currentInvoice.expenses.filter { expense ->
                    // Compara diretamente pelo categoryId
                    expense.category == goal.category
                }
                
                matchingExpenses.sumOf { it.value }
            } else {
                0.0
            }
            
            val percentage = if (goal.limitValue > 0) (spent / goal.limitValue * 100) else 0.0
            val status = when {
                percentage >= 100 -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.EXCEEDED
                percentage >= 80 -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.WARNING
                else -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.NORMAL
            }
            
            br.edu.utfpr.gestaofaturasicoob.models.GoalProgress(
                goal = goal,
                spent = spent,
                percentage = percentage,
                status = status
            )
        }
        goalsAdapter.submitList(goalProgressList)
        binding.textGoalsTitle.visibility = if (goalProgressList.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recyclerViewGoals.visibility = if (goalProgressList.isNotEmpty()) View.VISIBLE else View.GONE
        
        // Category legend removed - not needed in dashboard
    }
    
    private fun updateInvoiceSummary(data: DashboardData) {
        if (_binding == null) return // Fragment destroyed
        
        val invoice = data.currentInvoice
        val countdown = data.invoiceCountdown
        
        binding.cardCurrentInvoice.visibility = View.VISIBLE
        
        // Update month
        binding.textInvoiceMonth.text = invoice?.referenceMonth ?: ""
        
        // Update total value
        binding.textInvoiceTotal.text = currencyFormat.format(invoice?.totalValue ?: 0.0)
        
        // Update due date
        binding.textInvoiceDueDate.text = countdown?.formattedDueDate ?: ""
        
        // Update countdown
        val (countdownText, countdownColor) = if (countdown != null) {
            val text = countdown.statusMessage
            val color = when {
                countdown.isOverdue -> android.graphics.Color.parseColor("#F44336")
                countdown.isUrgent -> android.graphics.Color.parseColor("#FF9800")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            Pair(text, color)
        } else {
            // Check if invoice is paid
            if (invoice?.isPaid == true) {
                val paidDate = invoice.paidDate
                if (paidDate.isNotEmpty()) {
                    try {
                        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val paidDateParsed = dateFormat.parse(paidDate)
                        val dueDateParsed = dateFormat.parse(invoice.dueDate)
                        
                        if (paidDateParsed != null && dueDateParsed != null) {
                            val diffInDays = ((paidDateParsed.time - dueDateParsed.time) / (1000 * 60 * 60 * 24)).toInt()
                            val displayFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            val paidDateFormatted = displayFormat.format(paidDateParsed)
                            
                            val message = when {
                                diffInDays < 0 -> "Paga com ${-diffInDays} dias de antecedência"
                                diffInDays == 0 -> "Paga no prazo ($paidDateFormatted)"
                                else -> "Paga com $diffInDays dias de atraso"
                            }
                            Pair(message, android.graphics.Color.parseColor("#4CAF50"))
                        } else {
                            Pair("Fatura paga", android.graphics.Color.parseColor("#4CAF50"))
                        }
                    } catch (e: Exception) {
                        Pair("Fatura paga", android.graphics.Color.parseColor("#4CAF50"))
                    }
                } else {
                    Pair("Fatura paga", android.graphics.Color.parseColor("#4CAF50"))
                }
            } else {
                Pair("", android.graphics.Color.GRAY)
            }
        }
        
        binding.textCountdown.text = countdownText
        binding.textCountdown.setTextColor(countdownColor)
    }
    
    private fun updatePieChart(data: DashboardData) {
        // Category chart removed - not needed in dashboard
        
        // Create simple bar chart view
        val barChartContainer = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        val total = data.totalSpentThisMonth
        
        // Create header with total
        val headerRow = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (24 * resources.displayMetrics.density).toInt()
            }
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        
        val totalLabel = android.widget.TextView(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            text = "Total: ${currencyFormat.format(total)}"
            setTextAppearance(android.R.style.TextAppearance_Material_Subhead)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        
        headerRow.addView(totalLabel)
        barChartContainer.addView(headerRow)
        
        data.categorySpending.take(8).forEach { spending ->
            val percentage = if (total > 0) (spending.totalValue / total * 100).toInt() else 0
            
            val row = android.widget.LinearLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (16 * resources.displayMetrics.density).toInt()
                }
                orientation = android.widget.LinearLayout.VERTICAL
            }
            
            // First row: Category name and value (in one line)
            val firstRow = android.widget.LinearLayout(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            val categoryLabel = android.widget.TextView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = spending.categoryName
                setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            
            val valueText = android.widget.TextView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = currencyFormat.format(spending.totalValue)
                setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            
            firstRow.addView(categoryLabel)
            firstRow.addView(valueText)
            
            // Second row: Progress bar only
            val progressBar = android.widget.ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (8 * resources.displayMetrics.density).toInt()
                ).apply {
                    topMargin = (6 * resources.displayMetrics.density).toInt()
                }
                max = 100
                this.progress = percentage
                
                try {
                    progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(spending.color))
                } catch (e: Exception) {
                    progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2196F3"))
                }
            }
            
            row.addView(firstRow)
            row.addView(progressBar)
            
            barChartContainer.addView(row)
        }
        
        // Chart removed - not needed in dashboard
    }
    
    private fun showEmptyState() {
        _binding?.let { binding ->
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.scrollViewDashboard.visibility = View.GONE
        }
    }
    
    private fun showLoading(show: Boolean) {
        _binding?.let { binding ->
            binding.swipeRefresh.isRefreshing = show
        }
    }
    
    private fun showMessage(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun showError(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
