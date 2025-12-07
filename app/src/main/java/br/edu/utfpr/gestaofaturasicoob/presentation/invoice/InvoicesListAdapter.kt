package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import java.text.NumberFormat
import java.util.Locale

class InvoicesListAdapter(
    private val onInvoiceClick: (Invoice) -> Unit,
    private val onDeleteClick: (Invoice) -> Unit,
    private val onEditClick: (Invoice) -> Unit
) : RecyclerView.Adapter<InvoicesListAdapter.InvoiceViewHolder>() {
    
    private val invoices = mutableListOf<Invoice>()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    fun submitList(newInvoices: List<Invoice>) {
        invoices.clear()
        invoices.addAll(newInvoices.sortedByDescending { it.dueDate })
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(32, 24, 32, 24)
        }
        val textContainer = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val text1 = TextView(parent.context).apply { id = View.generateViewId(); textSize = 16f }
        val text2 = TextView(parent.context).apply { id = View.generateViewId(); textSize = 13f }
        textContainer.addView(text1)
        textContainer.addView(text2)
        val chevron = TextView(parent.context).apply {
            id = View.generateViewId()
            text = ">"
            textSize = 18f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(24, 0, 0, 0)
        }
        row.addView(textContainer)
        row.addView(chevron)
        val divider = View(parent.context).apply {
            setBackgroundColor(Color.parseColor("#22000000"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }
        container.addView(row)
        container.addView(divider)
        return InvoiceViewHolder(container, text1, text2, chevron)
    }
    
    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        holder.bind(invoices[position])
    }
    
    override fun getItemCount(): Int = invoices.size
    
    inner class InvoiceViewHolder(
        itemView: View,
        private val title: TextView,
        private val subtitle: TextView,
        private val chevron: TextView
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(invoice: Invoice) {
            title.text = "Fatura ${invoice.referenceMonth}"
            subtitle.text = "${currencyFormat.format(invoice.totalValue)} • ${invoice.expenses.size} despesas"
            
            itemView.setOnClickListener { onInvoiceClick(invoice) }
            itemView.setOnLongClickListener { onDeleteClick(invoice); true }
            chevron.setOnClickListener { onEditClick(invoice) }
        }
    }
}

