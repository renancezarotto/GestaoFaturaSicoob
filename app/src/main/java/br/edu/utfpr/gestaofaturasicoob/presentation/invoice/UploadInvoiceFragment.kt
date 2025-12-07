package br.edu.utfpr.gestaofaturasicoob.presentation.invoice

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import br.edu.utfpr.gestaofaturasicoob.R
import br.edu.utfpr.gestaofaturasicoob.controllers.AuthController
import br.edu.utfpr.gestaofaturasicoob.controllers.InvoiceController
import br.edu.utfpr.gestaofaturasicoob.databinding.FragmentUploadInvoiceBinding
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedInvoiceData
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * UploadInvoiceFragment - Fragment de Upload de Fatura
 * 
 * RESPONSABILIDADE:
 * Tela para seleção e processamento de PDF de fatura
 * 
 * FLUXO:
 * 1. Usuário seleciona PDF do dispositivo
 * 2. Fragment copia PDF para cache temporário
 * 3. Exibe preview do PDF (nome, tamanho)
 * 4. Processa PDF automaticamente (parsing)
 * 5. Navega para CategorizeExpensesFragment com dados extraídos
 * 
 * COMPONENTES:
 * - selectPDFLauncher: Activity Result Launcher para seleção de PDF
 * - invoiceController: Processa PDF e extrai dados
 * - authController: Obtém ID do usuário para navegação
 * 
 * LIFECYCLE:
 * - onCreateView: Infla layout
 * - onViewCreated: Configura UI e listeners
 * - onDestroyView: Limpa binding (prevenção de memory leak)
 */
class UploadInvoiceFragment : Fragment() {
    private var _binding: FragmentUploadInvoiceBinding? = null
    private val binding get() = _binding!!
    
    /**
     * Controllers para operações de negócio
     */
    private val invoiceController = InvoiceController()
    private val authController = AuthController()
    
    /**
     * Estado do PDF selecionado
     */
    private var selectedPdfUri: Uri? = null
    private var selectedPdfFile: File? = null

    /**
     * Activity Result Launcher para Seleção de PDF
     * 
     * FUNÇÃO:
     * Abre seletor de arquivos do sistema (apenas PDFs)
     * 
     * CALLBACK:
     * Quando usuário seleciona PDF, executa handlePdfSelected()
     * 
     * MIME TYPE:
     * "application/pdf" - Aceita apenas arquivos PDF
     */
    private val selectPDFLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            handlePdfSelected(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupUI()
    }
    
    private fun setupToolbar() {
        val toolbar = view?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbarCommon)
        toolbar?.let {
            it.findViewById<android.widget.TextView>(R.id.textToolbarTitle)?.text = "Processar Fatura"
            val backButton = it.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonBack)
            backButton?.visibility = View.VISIBLE
            backButton?.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }
    
    private fun setupUI() {
        binding.buttonSelectPDF.setOnClickListener {
            selectPDFLauncher.launch("application/pdf")
        }
    }
    
    /**
     * Trata Seleção de PDF pelo Usuário
     * 
     * PROCESSO:
     * 1. Copia PDF do URI para arquivo temporário no cache
     * 2. Extrai informações do arquivo (nome, tamanho)
     * 3. Exibe preview do PDF na UI
     * 4. Processa PDF automaticamente
     * 
     * ARQUIVO TEMPORÁRIO:
     * - Salvo em cacheDir (limpo automaticamente pelo sistema)
     * - Nome: "temp_invoice.pdf"
     * - Necessário porque PDFParserDataSource precisa de File, não URI
     * 
     * PREVIEW:
     * - Nome do arquivo
     * - Tamanho em MB
     * - Card aparece quando PDF é selecionado
     * 
     * PROCESSAMENTO AUTOMÁTICO:
     * - Após copiar, processa PDF imediatamente
     * - Não requer ação adicional do usuário
     * 
     * @param uri URI do PDF selecionado
     */
    private fun handlePdfSelected(uri: Uri) {
        lifecycleScope.launch {
            try {
                showLoading(true, "Carregando PDF...")
                
                // ========== ETAPA 1: COPIA PDF PARA CACHE ==========
                // PDFParserDataSource precisa de File, não URI
                // Copia PDF do storage do usuário para cache temporário
                val tempFile = File(requireContext().cacheDir, "temp_invoice.pdf")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output) // Copia bytes do input para output
                    }
                }
                
                selectedPdfFile = tempFile
                
                // ========== ETAPA 2: EXTRAI INFORMAÇÕES ==========
                // Nome do arquivo (para exibição)
                val fileName = getFileName(uri)
                
                // Tamanho do arquivo (converte para MB)
                val fileSizeKB = tempFile.length() / 1024 // Tamanho em KB
                val fileSizeMB = String.format("%.2f", fileSizeKB / 1024.0) // Converte para MB
                
                // ========== ETAPA 3: EXIBE PREVIEW ==========
                // Mostra card com informações do PDF
                binding.cardPDFPreview.visibility = View.VISIBLE
                binding.textPDFName.text = fileName
                binding.textPDFSize.text = "$fileSizeMB MB"
                
                showLoading(false)
                
                // ========== ETAPA 4: PROCESSA AUTOMATICAMENTE ==========
                // Processa PDF imediatamente (parsing)
                processPDF(tempFile)
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao carregar PDF: ${e.message}")
            }
        }
    }
    
    /**
     * Processa PDF e Extrai Dados da Fatura
     * 
     * FUNÇÃO PRINCIPAL:
     * Chama InvoiceController para fazer parsing do PDF
     * 
     * PROCESSO:
     * 1. Mostra loading ("Processando fatura...")
     * 2. Chama invoiceController.processPDF() (usa PDFParserDataSource)
     * 3. Se sucesso: Navega para CategorizeExpensesFragment
     * 4. Se erro: Exibe mensagem de erro
     * 
     * PARSING:
     * - Extrai cabeçalho (vencimento, total, período)
     * - Extrai todas as despesas (data, estabelecimento, valor, parcela)
     * - Detecta tarifas automaticamente
     * - Pode levar alguns segundos (PDFs grandes)
     * 
     * NAVEGAÇÃO:
     * - Se parsing bem-sucedido, navega para categorização
     * - Passa ExtractedInvoiceData via Bundle
     * 
     * @param pdfFile Arquivo PDF temporário
     */
    private fun processPDF(pdfFile: File) {
        lifecycleScope.launch {
            try {
                showLoading(true, "Processando fatura...")
                
                // ========== ETAPA 1: PROCESSAMENTO ==========
                // Chama Controller que usa PDFParserDataSource
                // Extrai dados estruturados do PDF
                val result = invoiceController.processPDF(pdfFile)
                
                showLoading(false)
                
                // ========== ETAPA 2: VERIFICAÇÃO DE RESULTADO ==========
                if (result.isSuccess) {
                    val extractedInvoice = result.getOrNull()
                    
                    // Se tem dados extraídos, navega para categorização
                    if (extractedInvoice != null) {
                        navigateToCategorization(extractedInvoice)
                    } else {
                        showError("Erro: dados da fatura vazios")
                    }
                } else {
                    // Se parsing falhou, exibe erro
                    showError("Erro ao processar PDF: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Erro ao processar fatura: ${e.message}")
            }
        }
    }
    
    /**
     * Navega para Tela de Categorização
     * 
     * PROPÓSITO:
     * Passa dados extraídos do PDF para CategorizeExpensesFragment
     * 
     * VALIDAÇÃO:
     * - Verifica se usuário está autenticado
     * - Se não autenticado, exibe erro e aborta navegação
     * 
     * PASSAGEM DE DADOS:
     * - Usa Bundle para passar ExtractedInvoiceData
     * - ExtractedInvoiceData é Parcelable (pode ser passado via Bundle)
     * - CategorizeExpensesFragment recebe via arguments
     * 
     * NAVEGAÇÃO:
     * - Usa Navigation Component
     * - Navega para action_dashboardFragment_to_categorizeExpensesFragment
     * 
     * @param extractedInvoice Dados extraídos do PDF
     */
    private fun navigateToCategorization(extractedInvoice: ExtractedInvoiceData) {
        try {
            // ========== VALIDAÇÃO: USUÁRIO AUTENTICADO ==========
            val userId = authController.getCurrentUserId()
            if (userId == null) {
                showError("Usuário não autenticado")
                return
            }
            
            // ========== PREPARAÇÃO DO BUNDLE ==========
            // Usa Bundle para passar dados (alternativa ao SafeArgs)
            val bundle = Bundle().apply {
                putParcelable(CategorizeExpensesFragment.ARG_INVOICE, extractedInvoice)
            }
            findNavController().navigate(R.id.action_uploadInvoice_to_categorizeExpenses, bundle)
            
        } catch (e: Exception) {
            showError("Erro ao navegar: ${e.message}")
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var fileName = "invoice.pdf"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
    
    private fun showLoading(show: Boolean, message: String = "Carregando...") {
        binding.loadingUpload.visibility = if (show) View.VISIBLE else View.GONE
        binding.textProcessing.visibility = if (show) View.VISIBLE else View.GONE
        binding.textProcessing.text = message
        binding.buttonSelectPDF.isEnabled = !show
    }
    
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("OK") { }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up temp file
        selectedPdfFile?.delete()
        _binding = null
    }
}
