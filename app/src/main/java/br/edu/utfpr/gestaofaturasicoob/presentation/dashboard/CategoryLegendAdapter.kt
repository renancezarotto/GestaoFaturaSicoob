package br.edu.utfpr.gestaofaturasicoob.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.databinding.ItemCategoryLegendBinding
import br.edu.utfpr.gestaofaturasicoob.models.CategorySpending
import java.text.NumberFormat
import java.util.Locale

class CategoryLegendAdapter : ListAdapter<CategorySpending, CategoryLegendAdapter.ViewHolder>(CategorySpendingDiffCallback()) {
    
    inner class ViewHolder(private val binding: ItemCategoryLegendBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        
        fun bind(categorySpending: CategorySpending) {
            binding.textLegendCategory.text = categorySpending.categoryName
            binding.textLegendValue.text = currencyFormat.format(categorySpending.totalValue)
            binding.textLegendPercentage.text = "(${categorySpending.percentage.toInt()}%)"
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryLegendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class CategorySpendingDiffCallback : DiffUtil.ItemCallback<CategorySpending>() {
    override fun areItemsTheSame(oldItem: CategorySpending, newItem: CategorySpending): Boolean {
        return oldItem.categoryName == newItem.categoryName
    }
    
    override fun areContentsTheSame(oldItem: CategorySpending, newItem: CategorySpending): Boolean {
        return oldItem.totalValue == newItem.totalValue && oldItem.percentage == newItem.percentage
    }
}

