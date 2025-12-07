package br.edu.utfpr.gestaofaturasicoob.controllers

import br.edu.utfpr.gestaofaturasicoob.models.Expense
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedInvoiceData
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import br.edu.utfpr.gestaofaturasicoob.services.InvoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * InvoiceController - Controller de Faturas
 * 
 * RESPONSABILIDADE:
 * Camada intermediária entre View (Fragment) e Service
 * 
 * FUNÇÕES:
 * 1. Validação de entrada (arquivo existe? é PDF?)
 * 2. Execução em thread de I/O (Dispatchers.IO)
 * 3. Tratamento básico de erros
 * 4. Delegação para InvoiceService
 * 
 * POR QUE EXISTE?
 * - Separação de responsabilidades (View não chama Service diretamente)
 * - Validações centralizadas
 * - Threading consistente (sempre I/O para operações Firebase)
 * - Facilita testes (pode mockar Controller)
 * 
 * PADRÃO:
 * View → Controller → Service → DataSource → Firebase
 */
class InvoiceController {
    
    /**
     * Parse PDF da Fatura
     * 
     * VALIDAÇÕES:
     * 1. Arquivo existe no sistema de arquivos?
     * 2. Arquivo tem extensão .pdf?
     * 
     * THREADING:
     * - Executa em Dispatchers.IO (thread de I/O)
     * - Não bloqueia thread principal (UI)
     * - Parsing pode demorar (10+ segundos para PDFs grandes)
     * 
     * FLUXO:
     * 1. Valida arquivo
     * 2. Delega parsing para InvoiceService
     * 3. Service chama PDFParserDataSourceFixed
     * 4. Retorna dados extraídos ou erro
     * 
     * @param pdfFile Arquivo PDF no sistema de arquivos
     * @return Result<ExtractedInvoiceData> - Success com dados extraídos, Failure se erro
     */
    suspend fun parseInvoicePDF(pdfFile: File): Result<ExtractedInvoiceData> {
        return withContext(Dispatchers.IO) {
            // ========== VALIDAÇÃO 1: ARQUIVO EXISTE? ==========
            if (!pdfFile.exists()) {
                return@withContext Result.failure(Exception("Arquivo não encontrado"))
            }
            
            // ========== VALIDAÇÃO 2: É PDF? ==========
            if (!pdfFile.name.endsWith(".pdf", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Arquivo deve ser PDF"))
            }
            
            // ========== DELEGAÇÃO PARA SERVICE ==========
            // Service faz o parsing real
            InvoiceService.parseInvoicePDF(pdfFile)
        }
    }
    
    suspend fun saveInvoice(userId: String, invoice: Invoice): Result<String> {
        return withContext(Dispatchers.IO) {
            InvoiceService.saveInvoice(userId, invoice)
        }
    }
    
    suspend fun getInvoices(userId: String): Result<List<Invoice>> {
        return withContext(Dispatchers.IO) {
            InvoiceService.getInvoices(userId)
        }
    }
    
    suspend fun getCurrentMonthInvoice(userId: String): Result<Invoice?> {
        return withContext(Dispatchers.IO) {
            InvoiceService.getCurrentMonthInvoice(userId)
        }
    }
    
    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            InvoiceService.deleteInvoice(userId, invoiceId)
        }
    }
    
    suspend fun getInvoiceByMonth(userId: String, referenceMonth: String): Result<Invoice> {
        return withContext(Dispatchers.IO) {
            InvoiceService.getInvoiceByMonth(userId, referenceMonth)
        }
    }
    
    suspend fun updateExpenseCategory(
        userId: String,
        invoiceId: String,
        expenseId: String,
        newCategory: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            InvoiceService.updateExpenseCategory(userId, invoiceId, expenseId, newCategory)
        }
    }
    
    /**
     * Update payment status of an invoice
     */
    suspend fun updatePaymentStatus(
        userId: String,
        invoiceId: String,
        isPaid: Boolean,
        paidDate: String? = null
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            InvoiceService.updatePaymentStatus(userId, invoiceId, isPaid, paidDate)
        }
    }
    
    /**
     * Process a PDF file and return extracted invoice data
     */
    suspend fun processPDF(pdfFile: File): Result<ExtractedInvoiceData> {
        return parseInvoicePDF(pdfFile)
    }
    
    /**
     * Salva Fatura a Partir de Dados Extraídos
     * 
     * CONVERSÃO:
     * ExtractedInvoiceData + CategoryMappings → Invoice
     * 
     * PROCESSO:
     * 1. Converte ExtractedExpenseData → Expense (com categorias)
     * 2. Aplica mapeamento de categorias (estabelecimento → categoria)
     * 3. Marca despesas auto-categorizadas
     * 4. Cria Invoice completo
     * 5. Salva no Firebase via Service
     * 
     * CATEGORIZAÇÃO:
     * - categoryMappings: Map<String, String>
     *   - Chave: stableKeyFor(expense) = "date|description|city|value|installment"
     *   - Valor: nome da categoria (ex: "Alimentação")
     * - Se chave existe no map → categoria aplicada (pode ser auto ou manual)
     * - Se chave não existe → category = null (sem categoria ainda)
     * 
     * AUTO-CATEGORIZAÇÃO:
     * - autoCategorized = true se categoria veio do mapeamento salvo
     * - false se foi categorizada manualmente agora
     * 
     * CHAVE ESTÁVEL:
     * Usa stableKeyFor() para gerar chave única baseada em:
     * - Data + Descrição + Cidade + Valor + Parcela
     * Garante que mesmo estabelecimento em momentos diferentes seja tratado corretamente
     * 
     * @param userId ID do usuário
     * @param extractedInvoice Dados extraídos do PDF (sem categorias)
     * @param categoryMappings Mapa de categorias (chave estável → nome categoria)
     * @return Result<String> - Success com invoiceId, Failure se erro
     */
    suspend fun saveInvoice(
        userId: String, 
        extractedInvoice: ExtractedInvoiceData, 
        categoryMappings: Map<String, String>
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // ========== CONVERSÃO DE DESPESAS ==========
                // Para cada despesa extraída, cria Expense com categoria
                val expenses = extractedInvoice.expenses.mapIndexed { index, extractedExpense ->
                    Expense(
                        id = "exp_${index + 1}", // ID sequencial (exp_1, exp_2, ...)
                        date = extractedExpense.date,
                        description = extractedExpense.description,
                        establishment = extractedExpense.establishment,
                        city = extractedExpense.city,
                        value = extractedExpense.value,
                        
                        // ========== APLICAÇÃO DE CATEGORIA ==========
                        // Busca categoria usando chave estável
                        // stableKeyFor(): Gera chave única para esta despesa
                        // categoryMappings[chave]: Retorna categoria se existir, null se não
                        category = categoryMappings[stableKeyFor(extractedExpense)],
                        
                        installment = extractedExpense.installment,
                        isInstallment = extractedExpense.installment != null, // true se tem parcela
                        
                        // ========== MARCAÇÃO DE AUTO-CATEGORIZAÇÃO ==========
                        // Se chave existe no map, foi auto-categorizada
                        // (categoria veio de mapeamento salvo anteriormente)
                        autoCategorized = categoryMappings.containsKey(stableKeyFor(extractedExpense)),
                        
                        createdAt = System.currentTimeMillis().toString()
                    )
                }
                
                
                // ========== CRIAÇÃO DO INVOICE ==========
                val invoice = Invoice(
                    id = java.util.UUID.randomUUID().toString(), // Gera ID único
                    userId = userId,
                    dueDate = extractedInvoice.dueDate,
                    totalValue = extractedInvoice.totalValue,
                    minimumPayment = extractedInvoice.minimumPayment,
                    referenceMonth = extractedInvoice.referenceMonth,
                    closingDate = extractedInvoice.closingDate,
                    expenses = expenses, // Lista de despesas categorizadas
                    uploadedAt = System.currentTimeMillis().toString()
                )
                
                // ========== SALVAMENTO NO FIREBASE ==========
                InvoiceService.saveInvoice(userId, invoice)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Gera Chave Estável Simples (String, não hash)
     * 
     * FORMATO:
     * Format: "date|description|city|value|installment"
     * 
     * EXEMPLO:
     * "2025-05-24|CAFE DA ANA|CORONEL VIVID|42.0|"
     * "2025-05-24|CAFE DA ANA|CORONEL VIVID|42.0|03/04" (com parcela)
     * 
     * @param exp Despesa extraída
     * @return String chave legível
     */
    private fun stableKeyFor(exp: br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData): String {
        val installmentPart = exp.installment ?: ""
        return "${exp.date}|${exp.description}|${exp.city}|${exp.value}|$installmentPart"
    }
}

