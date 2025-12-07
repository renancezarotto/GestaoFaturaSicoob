package br.edu.utfpr.gestaofaturasicoob.presentation.goals

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.models.Goal
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

class SimpleGoalsAdapter(
    private val onEditClick: (Goal) -> Unit,
    private val onDeleteClick: (Goal) -> Unit
) : RecyclerView.Adapter<SimpleGoalsAdapter.GoalViewHolder>() {
    
    private val goals = mutableListOf<Goal>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    fun submitList(newGoals: List<Goal>) {
        goals.clear()
        goals.addAll(newGoals)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_goal_manage, parent, false)
        return GoalViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        holder.bind(goals[position])
    }
    
    override fun getItemCount(): Int = goals.size
    
    inner class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        val textCategoryName: TextView = itemView.findViewById(R.id.textCategoryName)
        val textLimitValue: TextView = itemView.findViewById(R.id.textLimitValue)
        val textAlertStatus: TextView = itemView.findViewById(R.id.textAlertStatus)
        val buttonEdit: MaterialButton = itemView.findViewById(R.id.buttonEdit)
        val buttonDelete: MaterialButton = itemView.findViewById(R.id.buttonDelete)
        val indicatorStatus: View = itemView.findViewById(R.id.indicatorStatus)
        
        fun bind(goal: Goal) {
            // Nome da categoria
            textCategoryName.text = goal.category
            
            // Valor limite formatado
            textLimitValue.text = currencyFormat.format(goal.limitValue)
            
            // Mostrar status de alerta se ativo
            if (goal.alertAt80 || goal.alertAt100) {
                textAlertStatus.visibility = View.VISIBLE
                textAlertStatus.text = if (goal.alertAt80 && goal.alertAt100) {
                    "⚠️ Alertas 80% e 100% ativos"
                } else if (goal.alertAt80) {
                    "⚠️ Alerta aos 80%"
                } else {
                    "⚠️ Alerta aos 100%"
                }
            } else {
                textAlertStatus.visibility = View.GONE
            }
            
            // Indicador de status (sempre ativo por enquanto)
            if (goal.isActive) {
                indicatorStatus.setBackgroundResource(R.color.md_theme_light_primary)
            } else {
                indicatorStatus.setBackgroundResource(R.color.md_theme_light_onSurfaceVariant)
            }
            
            // Botões
            buttonEdit.setOnClickListener {
                onEditClick(goal)
            }
            
            buttonDelete.setOnClickListener {
                onDeleteClick(goal)
            }
        }
    }
}
