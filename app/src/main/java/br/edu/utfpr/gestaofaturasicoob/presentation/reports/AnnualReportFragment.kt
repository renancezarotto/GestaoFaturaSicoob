package br.edu.utfpr.gestaofaturasicoob.presentation.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.CategoryController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class AnnualReportFragment : Fragment() {
    
    private val invoiceController = InvoiceController()
    private val authController = AuthController()
    private val categoryController = CategoryController()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private var allCategories: List<br.edu.utfpr.gestaofaturasicoob.models.Category> = emptyList()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_annual_report, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAnnualReport()
    }
    
    private fun loadAnnualReport() {
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
                    val invoices = result.getOrNull() ?: emptyList()
                    if (invoices.isNotEmpty()) {
                        displayReport(invoices)
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
    
    private fun displayReport(invoices: List<Invoice>) {
        view?.apply {
            // Mostrar cards
            findViewById<View>(R.id.cardSummary)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardBestWorst)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardChart)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardTopCategories)?.visibility = View.VISIBLE
            findViewById<View>(R.id.layoutEmptyState)?.visibility = View.GONE
            
            // Calcular totais
            val total = invoices.sumOf { it.totalValue }
            val average = if (invoices.isNotEmpty()) total / invoices.size else 0.0
            
            // Extrair ano do primeiro mês de referência
            val year = extractYear(invoices)
            
            // Título
            findViewById<TextView>(R.id.textReportTitle)?.text = 
                "📊 Relatório Anual $year"
            
            // Total anual
            findViewById<TextView>(R.id.textTotalValue)?.text = 
                currencyFormat.format(total)
            
            // Média mensal
            findViewById<TextView>(R.id.textAverageValue)?.text = 
                currencyFormat.format(average)
            
            // Número de faturas
            findViewById<TextView>(R.id.textInvoiceCount)?.text = 
                "${invoices.size} ${if (invoices.size == 1) "mês" else "meses"}"
            
            // Melhor e pior mês
            displayBestAndWorstMonths(invoices)
            
            // Evolução mensal
            displayMonthlyEvolution(invoices)
            
            // Top categorias do ano
            displayTopCategories(invoices)
        }
    }
    
    private fun extractYear(invoices: List<Invoice>): String {
        // Pegar o ano da primeira fatura (formato "MÊS/ANO")
        return invoices.firstOrNull()?.referenceMonth?.split("/")?.lastOrNull() ?: 
            Calendar.getInstance().get(Calendar.YEAR).toString()
    }
    
    private fun displayBestAndWorstMonths(invoices: List<Invoice>) {
        if (invoices.isEmpty()) return
        
        val sorted = invoices.sortedBy { it.totalValue }
        val bestMonth = sorted.first()
        val worstMonth = sorted.last()
        
        view?.findViewById<TextView>(R.id.textBestMonth)?.text = 
            "${bestMonth.referenceMonth} - ${currencyFormat.format(bestMonth.totalValue)}"
        
        view?.findViewById<TextView>(R.id.textWorstMonth)?.text = 
            "${worstMonth.referenceMonth} - ${currencyFormat.format(worstMonth.totalValue)}"
    }
    
    private fun displayMonthlyEvolution(invoices: List<Invoice>) {
        // Calcular resumo geral
        val totalYear = invoices.sumOf { it.totalValue }
        val averageMonth = if (invoices.isNotEmpty()) totalYear / invoices.size else 0.0
        
        view?.findViewById<TextView>(R.id.textTotalYear)?.text = currencyFormat.format(totalYear)
        view?.findViewById<TextView>(R.id.textAverageMonth)?.text = currencyFormat.format(averageMonth)
        
        // Ordenar por mês (mais antigo primeiro para visualização cronológica)
        val sortedInvoices = invoices.sortedBy { it.referenceMonth }
        
        // Calcular valor máximo para porcentagem
        val maxValue = sortedInvoices.maxOfOrNull { it.totalValue } ?: 1.0
        
        val averageYear = if (invoices.isNotEmpty()) totalYear / invoices.size else 0.0
        
        val items = sortedInvoices.map { invoice ->
            val diffFromAverage = ((invoice.totalValue / averageYear) - 1) * 100
            MonthlyEvolutionData(
                month = invoice.referenceMonth,
                value = invoice.totalValue,
                percentage = diffFromAverage.toInt()
            )
        }
        
        // Preencher as 3 primeiras linhas visíveis
        val staticViews = listOf(
            view?.findViewById<View>(R.id.month1),
            view?.findViewById<View>(R.id.month2),
            view?.findViewById<View>(R.id.month3)
        )
        
        staticViews.forEach { it?.visibility = View.GONE }
        
        items.forEachIndexed { index, item ->
            if (index < staticViews.size) {
                val itemView = staticViews[index] ?: return@forEachIndexed
                itemView.visibility = View.VISIBLE
                
                val layoutTrend = itemView.findViewById<android.widget.LinearLayout>(R.id.layoutTrend)
                val textMonthName = itemView.findViewById<TextView>(R.id.textMonthName)
                val textMonthValue = itemView.findViewById<TextView>(R.id.textMonthValue)
                val textMonthComparison = itemView.findViewById<TextView>(R.id.textMonthComparison)
                val textMonthPercentage = itemView.findViewById<TextView>(R.id.textMonthPercentage)
                
                textMonthName?.text = item.month
                textMonthValue?.text = currencyFormat.format(item.value)
                
                // Mostrar diferença da média (ex: "+15%", "-8%")
                val percentageText = when {
                    item.percentage > 0 -> "+${item.percentage}%"
                    item.percentage < 0 -> "${item.percentage}%"
                    else -> "0%"
                }
                textMonthPercentage?.text = percentageText
                
                // Determinar tendência comparando com mês anterior
                val hasPrevious = index > 0
                if (hasPrevious) {
                    val previousValue = items[index - 1].value
                    val diff = ((item.value - previousValue) / previousValue) * 100
                    
                    when {
                        diff > 5 -> {
                            layoutTrend?.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                            textMonthComparison?.text = "+${diff.toInt()}% vs mês anterior"
                            textMonthComparison?.setTextColor(android.graphics.Color.parseColor("#F44336"))
                            textMonthComparison?.visibility = View.VISIBLE
                        }
                        diff < -5 -> {
                            layoutTrend?.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                            textMonthComparison?.text = "${diff.toInt()}% vs mês anterior"
                            textMonthComparison?.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                            textMonthComparison?.visibility = View.VISIBLE
                        }
                        else -> {
                            layoutTrend?.setBackgroundColor(android.graphics.Color.parseColor("#FFA726"))
                            textMonthComparison?.text = "Estável vs mês anterior"
                            textMonthComparison?.setTextColor(android.graphics.Color.parseColor("#FFA726"))
                            textMonthComparison?.visibility = View.VISIBLE
                        }
                    }
                } else {
                    layoutTrend?.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                    textMonthComparison?.visibility = View.GONE
                }
                
                // Cor baseada na diferença da média
                val color = when {
                    item.percentage > 30 -> "#F44336" // Muito acima da média
                    item.percentage > 15 -> "#FF9800" // Acima da média
                    item.percentage > -15 -> "#4CAF50" // Próximo da média
                    else -> "#2196F3" // Abaixo da média
                }
                
                textMonthPercentage?.setTextColor(android.graphics.Color.parseColor(color))
            }
        }
        
        // Preencher o RecyclerView com o restante (a partir do 4º item)
        val remainingItems = items.drop(3)
        if (remainingItems.isNotEmpty()) {
            val recycler = view?.findViewById<RecyclerView>(R.id.recyclerMonthlyEvolution)
            recycler?.layoutManager = LinearLayoutManager(requireContext())
            recycler?.adapter = MonthlyEvolutionAdapter(remainingItems, maxValue)
        } else {
            view?.findViewById<RecyclerView>(R.id.recyclerMonthlyEvolution)?.visibility = View.GONE
        }
    }
    
    private fun displayTopCategories(invoices: List<Invoice>) {
        // Consolidar todas as despesas de todas as faturas
        val allExpenses = invoices.flatMap { it.expenses }
        
        // Agrupar por categoria e somar valores
        val categoryTotals = allExpenses
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
    
    private fun showEmptyState(message: String) {
        view?.apply {
            findViewById<View>(R.id.cardSummary)?.visibility = View.GONE
            findViewById<View>(R.id.cardBestWorst)?.visibility = View.GONE
            findViewById<View>(R.id.cardChart)?.visibility = View.GONE
            findViewById<View>(R.id.cardTopCategories)?.visibility = View.GONE
            findViewById<View>(R.id.layoutEmptyState)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.textEmptyMessage)?.text = message
        }
    }
    
    private fun showLoading(show: Boolean) {
        view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = 
            if (show) View.VISIBLE else View.GONE
    }
    
    // Data class para evolução mensal
    private data class MonthlyEvolutionData(
        val month: String,
        val value: Double,
        val percentage: Int
    )
    
    // Adapter para evolução mensal
    private class MonthlyEvolutionAdapter(
        private val items: List<MonthlyEvolutionData>,
        private val maxValue: Double
    ) : RecyclerView.Adapter<MonthlyEvolutionAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val layoutTrend: android.widget.LinearLayout = view.findViewById(R.id.layoutTrend)
            val textMonthName: TextView = view.findViewById(R.id.textMonthName)
            val textMonthValue: TextView = view.findViewById(R.id.textMonthValue)
            val textMonthComparison: TextView = view.findViewById(R.id.textMonthComparison)
            val textMonthPercentage: TextView = view.findViewById(R.id.textMonthPercentage)
        }
        
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        private val previousValues = mutableListOf<Double>()
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monthly_evolution, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            // Preencher dados básicos
            holder.textMonthName.text = item.month
            holder.textMonthValue.text = currencyFormat.format(item.value)
            
            // Mostrar diferença da média (ex: "+15%", "-8%")
            val percentageText = when {
                item.percentage > 0 -> "+${item.percentage}%"
                item.percentage < 0 -> "${item.percentage}%"
                else -> "0%"
            }
            holder.textMonthPercentage.text = percentageText
            
            // Determinar tendência comparando com mês anterior
            val hasPrevious = position > 0
            if (hasPrevious) {
                val previousValue = items[position - 1].value
                val diff = ((item.value - previousValue) / previousValue) * 100
                
                when {
                    diff > 5 -> {
                        holder.layoutTrend.setBackgroundColor(android.graphics.Color.parseColor("#F44336"))
                        holder.textMonthComparison.text = "+${diff.toInt()}% vs mês anterior"
                        holder.textMonthComparison.setTextColor(android.graphics.Color.parseColor("#F44336"))
                        holder.textMonthComparison.visibility = View.VISIBLE
                    }
                    diff < -5 -> {
                        holder.layoutTrend.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                        holder.textMonthComparison.text = "${diff.toInt()}% vs mês anterior"
                        holder.textMonthComparison.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        holder.textMonthComparison.visibility = View.VISIBLE
                    }
                    else -> {
                        holder.layoutTrend.setBackgroundColor(android.graphics.Color.parseColor("#FFA726"))
                        holder.textMonthComparison.text = "Estável vs mês anterior"
                        holder.textMonthComparison.setTextColor(android.graphics.Color.parseColor("#FFA726"))
                        holder.textMonthComparison.visibility = View.VISIBLE
                    }
                }
            } else {
                holder.layoutTrend.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
                holder.textMonthComparison.visibility = View.GONE
            }
            
            // Cor baseada na diferença da média
            val color = when {
                item.percentage > 30 -> "#F44336" // Vermelho - muito acima da média
                item.percentage > 15 -> "#FF9800" // Laranja - acima da média
                item.percentage > -15 -> "#4CAF50" // Verde - próximo da média
                else -> "#2196F3" // Azul - abaixo da média
            }
            
            holder.textMonthPercentage.setTextColor(android.graphics.Color.parseColor(color))
        }
        
        override fun getItemCount() = items.size
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
