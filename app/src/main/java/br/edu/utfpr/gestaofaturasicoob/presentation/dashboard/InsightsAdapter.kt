package br.edu.utfpr.gestaofaturasicoob.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.databinding.ItemInsightCardBinding
import br.edu.utfpr.gestaofaturasicoob.models.Insight

class InsightsAdapter : ListAdapter<Insight, InsightsAdapter.ViewHolder>(InsightDiffCallback()) {
    
    inner class ViewHolder(private val binding: ItemInsightCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(insight: Insight) {
            binding.textInsightIcon.text = getIconForSeverity(insight.severity.name)
            binding.textInsightTitle.text = insight.title
            binding.textInsightMessage.text = insight.description
        }
        
        private fun getIconForSeverity(severity: String): String {
            return when (severity) {
                "CRITICAL" -> "🚨"
                "WARNING" -> "⚠️"
                else -> "💡"
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInsightCardBinding.inflate(
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

class InsightDiffCallback : DiffUtil.ItemCallback<Insight>() {
    override fun areItemsTheSame(oldItem: Insight, newItem: Insight): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: Insight, newItem: Insight): Boolean {
        return oldItem == newItem
    }
}

