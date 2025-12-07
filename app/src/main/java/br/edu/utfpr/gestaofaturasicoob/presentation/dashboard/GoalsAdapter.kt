package br.edu.utfpr.gestaofaturasicoob.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.databinding.ItemGoalCardBinding
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.GoalProgress
import br.edu.utfpr.gestaofaturasicoob.models.GoalStatus
import java.text.NumberFormat
import java.util.Locale

class GoalsAdapter(
    private val onGoalClick: (br.edu.utfpr.gestaofaturasicoob.models.Goal) -> Unit,
    private var allCategories: List<Category> = emptyList()
) : ListAdapter<GoalProgress, GoalsAdapter.ViewHolder>(GoalProgressDiffCallback()) {
    
    fun updateCategories(categories: List<Category>) {
        this.allCategories = categories
        notifyDataSetChanged()
    }
    
    private fun resolveCategoryName(categoryId: String?): String {
        if (categoryId.isNullOrBlank()) return "Sem Categoria"
        
        // Busca categoria pelo ID
        val category = allCategories.find { it.id == categoryId }
        
        // Retorna nome da categoria se encontrou, senão retorna ID como fallback
        return category?.name ?: categoryId
    }
    
    inner class ViewHolder(private val binding: ItemGoalCardBinding) : RecyclerView.ViewHolder(binding.root) {
        
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        
        fun bind(goalProgress: GoalProgress) {
            val goal = goalProgress.goal
            val spent = goalProgress.spent
            val limit = goal.limitValue
            val percentage = goalProgress.percentage.toInt()
            
            // Resolver nome da categoria
            val categoryName = resolveCategoryName(goal.category)
            binding.textCategoryName.text = categoryName
            binding.textGoalPercentage.text = "$percentage%"
            binding.textGoalSpent.text = currencyFormat.format(spent)
            binding.textGoalLimit.text = currencyFormat.format(limit)
            
            binding.progressBarGoal.progress = percentage.coerceIn(0, 100)
            
            // Determinar cor baseada no status
            val (textColor, progressColor, bgColor) = when (goalProgress.status) {
                GoalStatus.NORMAL -> Triple(
                    android.graphics.Color.parseColor("#4CAF50"),
                    android.graphics.Color.parseColor("#4CAF50"),
                    android.graphics.Color.parseColor("#E8F5E9")
                )
                GoalStatus.WARNING -> Triple(
                    android.graphics.Color.parseColor("#FF9800"),
                    android.graphics.Color.parseColor("#FF9800"),
                    android.graphics.Color.parseColor("#FFF3E0")
                )
                GoalStatus.EXCEEDED -> Triple(
                    android.graphics.Color.parseColor("#F44336"),
                    android.graphics.Color.parseColor("#F44336"),
                    android.graphics.Color.parseColor("#FFEBEE")
                )
            }
            
            binding.textGoalPercentage.setTextColor(textColor)
            binding.progressBarGoal.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
            
            // Mostrar alerta se > 80%
            binding.layoutAlert.visibility = if (percentage >= 80) ViewGroup.VISIBLE else ViewGroup.GONE
            
            val alertMessage = when {
                percentage >= 100 -> "Meta ultrapassada! Você excedeu o limite de $categoryName."
                percentage >= 80 -> "Atenção! Você atingiu ${percentage}% da meta de $categoryName."
                else -> ""
            }
            binding.textAlertMessage.text = alertMessage
            
            // Click listener
            itemView.setOnClickListener {
                onGoalClick(goal)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGoalCardBinding.inflate(
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

class GoalProgressDiffCallback : DiffUtil.ItemCallback<GoalProgress>() {
    override fun areItemsTheSame(oldItem: GoalProgress, newItem: GoalProgress): Boolean {
        return oldItem.goal.id == newItem.goal.id
    }
    
    override fun areContentsTheSame(oldItem: GoalProgress, newItem: GoalProgress): Boolean {
        return oldItem.percentage == newItem.percentage && oldItem.status == newItem.status
    }
}

