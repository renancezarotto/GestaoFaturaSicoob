package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.databinding.ItemExpenseDetailBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ExpenseDetailAdapter(
    private val onCategorySelected: (Expense, String) -> Unit
) : ListAdapter<Expense, ExpenseDetailAdapter.ViewHolder>(ExpenseDiffCallback()) {

    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val inputDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val outputDateFormat = SimpleDateFormat("dd MMM", Locale("pt", "BR"))
    
    private var availableCategories: List<Category> = emptyList()

    fun submitList(expenses: List<Expense>, categories: List<Category>) {
        this.availableCategories = categories
        submitList(expenses)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseDetailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), availableCategories)
    }

    inner class ViewHolder(
        private val binding: ItemExpenseDetailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense, categories: List<Category>) {
            // Format date
            val formattedDate = try {
                val date = inputDateFormat.parse(expense.date)
                date?.let { outputDateFormat.format(it).uppercase() } ?: expense.date
            } catch (e: Exception) {
                expense.date
            }

            binding.textDate.text = formattedDate
            binding.textEstablishment.text = expense.establishment
            binding.textCity.text = expense.city
            binding.textValue.text = numberFormat.format(expense.value)

            // Show installment if exists
            if (expense.installment != null && expense.isInstallment) {
                binding.textInstallment.text = expense.installment
                binding.textInstallment.visibility = android.view.View.VISIBLE
            } else {
                binding.textInstallment.visibility = android.view.View.GONE
            }

            // Setup category dropdown
            val categoryNames = categories.map { it.name }
            val adapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_dropdown_item_1line,
                categoryNames
            )
            binding.dropdownCategory.setAdapter(adapter)

            // Set current category
            val currentCategory = categories.find { it.id == expense.category }
            binding.dropdownCategory.setText(currentCategory?.name ?: "Selecionar categoria", false)

            // Handle category selection
            binding.dropdownCategory.setOnItemClickListener { _, _, position, _ ->
                val selectedCategory = categories[position]
                onCategorySelected(expense, selectedCategory.id)
            }
        }
    }

    private class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean {
            return oldItem == newItem
        }
    }
}

