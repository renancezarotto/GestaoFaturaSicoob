package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

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
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentInvoicesBinding
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Fragment: InvoicesFragment
 * Displays list of all invoices with upload button
 */
class InvoicesFragment : Fragment() {
    
    private var _binding: FragmentInvoicesBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Fragment may have been destroyed.")
    
    private val invoiceController = InvoiceController()
    private val authController = AuthController()
    
    private lateinit var adapter: InvoicesListAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        loadInvoices()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Minhas Faturas"
            it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)?.visibility = View.GONE
        }
    }
    
    private fun setupRecyclerView() {
        adapter = InvoicesListAdapter(
            onInvoiceClick = { invoice -> navigateToInvoiceDetail(invoice) },
            onDeleteClick = { invoice -> confirmDeleteInvoice(invoice) },
            onEditClick = { invoice -> navigateToInvoiceDetail(invoice) }
        )
        
        binding.recyclerInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@InvoicesFragment.adapter
        }
    }
    
    private fun setupListeners() {
        binding.fabUploadInvoice.setOnClickListener {
            navigateToUploadInvoice()
        }
        
        binding.swipeRefreshLayout?.setOnRefreshListener {
            loadInvoices()
        }
    }

    // Removed toolbar and tabs (reverted)
    
    private fun loadInvoices() {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId()
                if (userId == null) {
                    showError("Usuário não autenticado")
                    return@launch
                }
                
                showLoading(true)
                
                val result = invoiceController.getInvoices(userId)
                
                showLoading(false)
                
                if (result.isSuccess) {
                    val invoices = result.getOrNull() ?: emptyList()
                    displayInvoices(invoices)
                } else {
                    showError("Erro ao carregar faturas: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar faturas: ${e.message}")
            }
        }
    }
    
    private fun displayInvoices(invoices: List<Invoice>) {
        if (_binding == null) return // Fragment destroyed
        
        if (invoices.isEmpty()) {
            showEmptyState()
        } else {
            hideEmptyState()
            adapter.submitList(invoices)
        }
    }
    
    private fun confirmDeleteInvoice(invoice: Invoice) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Excluir Fatura")
            .setMessage("Deseja realmente excluir a fatura de ${invoice.referenceMonth}?")
            .setPositiveButton("Excluir") { _, _ ->
                deleteInvoice(invoice)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun deleteInvoice(invoice: Invoice) {
        lifecycleScope.launch {
            try {
                val userId = authController.getCurrentUserId() ?: return@launch
                
                showLoading(true)
                
                val result = invoiceController.deleteInvoice(userId, invoice.id)
                
                showLoading(false)
                
                if (result.isSuccess) {
                    showMessage("Fatura excluída com sucesso")
                    loadInvoices() // Reload list
                } else {
                    showError("Erro ao excluir fatura: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao excluir fatura: ${e.message}")
            }
        }
    }
    
    private fun navigateToUploadInvoice() {
        try {
            findNavController().navigate(R.id.uploadInvoiceFragment)
        } catch (e: Exception) {
            showError("Erro ao navegar: ${e.message}")
        }
    }
    
    private fun navigateToInvoiceDetail(invoice: Invoice) {
        try {
            val bundle = Bundle().apply {
                putString(InvoiceDetailFragment.ARG_INVOICE_ID, invoice.id)
                putString(InvoiceDetailFragment.ARG_REFERENCE_MONTH, invoice.referenceMonth)
            }
            findNavController().navigate(R.id.action_invoices_to_invoiceDetail, bundle)
        } catch (e: Exception) {
            showError("Erro ao navegar: ${e.message}")
        }
    }
    
    private fun showLoading(show: Boolean) {
        _binding?.swipeRefreshLayout?.isRefreshing = show
    }
    
    private fun showEmptyState() {
        _binding?.let { binding ->
            binding.textEmptyState?.visibility = View.VISIBLE
            binding.textEmptyState?.text = "Nenhuma fatura processada.\nToque em + para adicionar"
            binding.recyclerInvoices.visibility = View.GONE
        }
    }
    
    private fun hideEmptyState() {
        _binding?.let { binding ->
            binding.textEmptyState?.visibility = View.GONE
            binding.recyclerInvoices.visibility = View.VISIBLE
        }
    }
    
    private fun showMessage(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun showError(message: String) {
        _binding?.root?.let { root ->
            Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

