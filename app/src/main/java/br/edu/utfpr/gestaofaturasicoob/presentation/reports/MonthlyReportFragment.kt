package br.edu.utfpr.gestaofaturasicoob.presentation.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import br.edu.utfpr.gestaofaturasicoob.utils.MonthFilterManager
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MonthlyReportFragment : Fragment() {
    
    private val invoiceController = InvoiceController()
    private val authController = AuthController()
    private val categoryController = CategoryController()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormat = SimpleDateFormat("MMMM/yyyy", Locale("pt", "BR"))
    
    private var allInvoices: List<Invoice> = emptyList()
    private var availableMonths: List<String> = emptyList()
    private var selectedMonthKey: String = ""
    private var allCategories: List<br.edu.utfpr.gestaofaturasicoob.models.Category> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monthly_report, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Load saved month filter
        val savedMonth = MonthFilterManager.getSelectedMonth(requireContext())
        if (savedMonth != null) {
            selectedMonthKey = savedMonth
        }
        
        loadAllInvoices()
    }
    
    private fun loadAllInvoices() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showEmptyState("Usuário não autenticado")
                    return@launch
                }
                
                // Carregar categorias junto com as faturas
                val categoriesResult = categoryController.getAllCategories(userId)
                if (categoriesResult.isSuccess) {
                    allCategories = categoriesResult.getOrNull() ?: emptyList()
                }
                
                val result = invoiceController.getInvoices(userId)
                
                if (result.isSuccess) {
                    allInvoices = result.getOrNull() ?: emptyList()
                    if (allInvoices.isNotEmpty()) {
                        setupMonthSpinner()
                    } else {
                        showEmptyState("Nenhuma fatura disponível")
                    }
                } else {
                    showEmptyState("Erro ao carregar faturas")
                }
            } catch (e: Exception) {
                showEmptyState("Erro: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun setupMonthSpinner() {
        // Criar lista de meses disponíveis (ordenado do mais recente para o mais antigo)
        availableMonths = allInvoices
            .map { it.referenceMonth }
            .distinct()
            .sortedDescending()
        
        val spinner = view?.findViewById<Spinner>(R.id.spinnerMonth) ?: return
        
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_month,
            availableMonths
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_month)
        spinner.adapter = adapter
        
        // Selecionar o mês salvo ou o mais recente por padrão
        if (availableMonths.isNotEmpty()) {
            val savedMonth = MonthFilterManager.getSelectedMonth(requireContext())
            val monthIndex = if (savedMonth != null && availableMonths.contains(savedMonth)) {
                availableMonths.indexOf(savedMonth)
            } else {
                0 // Most recent month
            }
            
            selectedMonthKey = availableMonths[monthIndex]
            spinner.setSelection(monthIndex)
        }
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedMonthKey = availableMonths[position]
                MonthFilterManager.saveSelectedMonth(requireContext(), selectedMonthKey)
                loadMonthlyReport(selectedMonthKey)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Carregar o primeiro mês
        if (availableMonths.isNotEmpty()) {
            loadMonthlyReport(selectedMonthKey)
        }
    }
    
    private fun loadMonthlyReport(monthKey: String) {
        val invoice = allInvoices.find { it.referenceMonth == monthKey }
        if (invoice != null) {
            displayReport(invoice)
        } else {
            showEmptyState("Fatura não encontrada")
        }
    }
    
    private fun displayReport(invoice: Invoice) {
        view?.apply {
            // Mostrar cards
            findViewById<View>(R.id.cardSummary)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardCategories)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardEstablishments)?.visibility = View.VISIBLE
            findViewById<View>(R.id.layoutEmptyState)?.visibility = View.GONE
            
            // Título
            findViewById<TextView>(R.id.textReportTitle)?.text = 
                "Relatório - ${invoice.referenceMonth}"
            
            // Total gasto
            findViewById<TextView>(R.id.textTotalValue)?.text = 
                currencyFormat.format(invoice.totalValue)
            
            // Número de despesas
            findViewById<TextView>(R.id.textExpenseCount)?.text = 
                "${invoice.expenses.size}"
            
            // Número de categorias
            val categoryCount = invoice.expenses.map { it.category }.distinct().size
            findViewById<TextView>(R.id.textCategoryCount)?.text = 
                "$categoryCount"
            
            // Comparação com mês anterior
            displayComparison(invoice)
            
            // Top 5 Categorias
            displayTopCategories(invoice)
            
            // Top 5 Estabelecimentos
            displayTopEstablishments(invoice)
        }
    }
    
    private fun displayComparison(invoice: Invoice) {
        val textComparison = view?.findViewById<TextView>(R.id.textComparison) ?: return
        
        // Encontrar o índice do mês atual
        val currentIndex = availableMonths.indexOf(invoice.referenceMonth)
        
        // Se não há mês anterior, esconder a comparação
        if (currentIndex == -1 || currentIndex >= availableMonths.size - 1) {
            textComparison.visibility = View.GONE
            return
        }
        
        // Pegar o mês anterior
        val previousMonthKey = availableMonths[currentIndex + 1]
        val previousInvoice = allInvoices.find { it.referenceMonth == previousMonthKey }
        
        if (previousInvoice != null) {
            val difference = invoice.totalValue - previousInvoice.totalValue
            val percentChange = (difference / previousInvoice.totalValue) * 100
            
            val emoji = if (difference > 0) "📈" else "📉"
            val direction = if (difference > 0) "maior" else "menor"
            val color = if (difference > 0) "#F44336" else "#4CAF50"
            
            textComparison.text = "$emoji ${String.format("%.1f", kotlin.math.abs(percentChange))}% $direction que $previousMonthKey"
            textComparison.setTextColor(android.graphics.Color.parseColor(color))
            textComparison.visibility = View.VISIBLE
        } else {
            textComparison.visibility = View.GONE
        }
    }
    
    private fun displayTopCategories(invoice: Invoice) {
        // Agrupar por categoria e somar valores
        val categoryTotals = invoice.expenses
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.value } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        
        // Define as views estáticas
        val views = listOf(
            view?.findViewById<View>(R.id.item1),
            view?.findViewById<View>(R.id.item2),
            view?.findViewById<View>(R.id.item3),
            view?.findViewById<View>(R.id.item4),
            view?.findViewById<View>(R.id.item5)
        )
        
        // Oculta todas as views
        views.forEach { it?.visibility = View.GONE }
        
        // Preenche as views visíveis com os dados
        val totalValue = categoryTotals.sumOf { it.second }
        categoryTotals.forEachIndexed { index, (categoryIdOrName, total) ->
            if (index < views.size) {
                val itemView = views[index] ?: return@forEachIndexed
                itemView.visibility = View.VISIBLE
                
                // Encontra os TextViews dentro do include
                val categoryNameView = itemView.findViewById<android.widget.TextView>(R.id.textLegendCategory)
                val valueView = itemView.findViewById<android.widget.TextView>(R.id.textLegendValue)
                val percentageView = itemView.findViewById<android.widget.TextView>(R.id.textLegendPercentage)
                
                val percentage = if (totalValue > 0) ((total / totalValue) * 100).toInt() else 0
                
                // Busca categoria pelo ID
                val categoryName = if (categoryIdOrName != null) {
                    val category = allCategories.find { it.id == categoryIdOrName }
                    category?.name ?: categoryIdOrName
                } else {
                    "Sem Categoria"
                }
                
                categoryNameView?.text = categoryName
                valueView?.text = currencyFormat.format(total)
                percentageView?.text = "($percentage%)"
            }
        }
    }
    
    private fun displayTopEstablishments(invoice: Invoice) {
        val recycler = view?.findViewById<RecyclerView>(R.id.recyclerTopEstablishments) ?: return
        
        // Agrupar por estabelecimento e somar valores
        val establishmentTotals = invoice.expenses
            .groupBy { it.establishment }
            .mapValues { entry -> entry.value.sumOf { it.value } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
        
        val items = establishmentTotals.map { (establishment, total) ->
            "$establishment - ${currencyFormat.format(total)}"
        }
        
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = SimpleStringAdapter(items)
    }
    
    private fun showEmptyState(message: String) {
        view?.apply {
            findViewById<View>(R.id.cardSummary)?.visibility = View.GONE
            findViewById<View>(R.id.cardCategories)?.visibility = View.GONE
            findViewById<View>(R.id.cardEstablishments)?.visibility = View.GONE
            findViewById<View>(R.id.layoutEmptyState)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.textEmptyMessage)?.text = message
        }
    }
    
    private fun showLoading(show: Boolean) {
        view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = 
            if (show) View.VISIBLE else View.GONE
    }
    
    // Adapter simples para listas de strings
    private class SimpleStringAdapter(private val items: List<String>) : 
        RecyclerView.Adapter<SimpleStringAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text1: TextView = view.findViewById(android.R.id.text1)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.text1.text = "${position + 1}. ${items[position]}"
            holder.text1.setPadding(16, 12, 16, 12)
        }
        
        override fun getItemCount() = items.size
    }
}
