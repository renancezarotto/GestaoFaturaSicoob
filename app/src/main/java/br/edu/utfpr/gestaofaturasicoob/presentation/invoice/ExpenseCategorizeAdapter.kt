package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ArrayAdapter
import br.edu.utfpr.gestaofaturasicoob.databinding.ItemExpenseToCategorizeBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseCategorizeAdapter(
    private val onCategorySelected: (expense: ExtractedExpenseData, position: Int, categoryId: String, categoryName: String) -> Unit
) : RecyclerView.Adapter<ExpenseCategorizeAdapter.ViewHolder>() {
    
    private val expenses = mutableListOf<ExtractedExpenseData>()
    private val categories = mutableMapOf<Int, String>() // position -> category name
    private var availableCategories: List<Category> = emptyList()
    
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputDateFormat = SimpleDateFormat("dd MMM", Locale("pt", "BR"))
    
    fun submitList(newExpenses: List<ExtractedExpenseData>) {
        expenses.clear()
        expenses.addAll(newExpenses)
        notifyDataSetChanged()
    }
    
    fun setAvailableCategories(categories: List<Category>) {
        this.availableCategories = categories
        notifyDataSetChanged()
    }
    
    fun updateExpenseCategory(position: Int, categoryName: String) {
        categories[position] = categoryName
        notifyItemChanged(position)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseToCategorizeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(expenses[position], position)
    }
    
    override fun getItemCount(): Int = expenses.size
    
    inner class ViewHolder(
        private val binding: ItemExpenseToCategorizeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(expense: ExtractedExpenseData, position: Int) {
            // Format date
            val formattedDate = try {
                val date = inputDateFormat.parse(expense.date)
                date?.let { outputDateFormat.format(it) } ?: expense.date
            } catch (e: Exception) {
                expense.date
            }
            
            binding.textDate.text = formattedDate
            binding.textEstablishment.text = expense.description
            binding.textCity.text = expense.city
            binding.textValue.text = numberFormat.format(expense.value)
            
            // Show installment if exists
            if (expense.installment != null) {
                binding.textInstallment.text = expense.installment
                binding.textInstallment.visibility = android.view.View.VISIBLE
            } else {
                binding.textInstallment.visibility = android.view.View.GONE
            }
            
            // Show selected category
            val categoryName = categories[position]
            val categoryText = categoryName ?: "Selecionar categoria"
            binding.dropdownCategory.setText(categoryText, false)

            // Setup dropdown with categories and handle in-place selection
            if (availableCategories.isNotEmpty()) {
                val names = availableCategories.map { it.name }
                val arrayAdapter = ArrayAdapter(binding.root.context, android.R.layout.simple_dropdown_item_1line, names)
                binding.dropdownCategory.setAdapter(arrayAdapter)
                binding.dropdownCategory.setOnItemClickListener { _, _, which, _ ->
                    val selected = availableCategories[which]
                    categories[position] = selected.name
                    binding.dropdownCategory.setText(selected.name, false)
                    onCategorySelected(expense, position, selected.id, selected.name)
                }
            }
        }
    }
}
