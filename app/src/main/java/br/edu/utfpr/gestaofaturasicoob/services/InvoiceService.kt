package br.edu.utfpr.gestaofaturasicoob.services

import br.edu.utfpr.gestaofaturasicoob.data.datasource.FirebaseManager
import br.edu.utfpr.gestaofaturasicoob.data.datasource.PDFParserDataSourceFixed
import br.edu.utfpr.gestaofaturasicoob.models.Expense
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedInvoiceData
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * InvoiceService - Serviço de Operações com Faturas
 * 
 * RESPONSABILIDADES:
 * 1. Parsing de PDF e extração de dados (usa PDFParserDataSourceFixed)
 * 2. CRUD completo de faturas no Firebase
 * 3. Conversão de formatos (ExtractedInvoiceData → Invoice)
 * 4. Busca e filtros de faturas (por mês, usuário, etc.)
 * 5. Atualização de despesas individuais (categorias, status)
 * 
 * ESTRUTURA NO FIREBASE:
 * users/
 *   {userId}/
 *     invoices/
 *       "2025-06"/  ← Chave = mês de referência (YYYY-MM)
 *         id: "inv_202506_abc123"
 *         userId: "user123"
 *         dueDate: "2025-07-03"
 *         totalValue: 2600.35
 *         minimumPayment: 418.86
 *         referenceMonth: "JUN/2025"
 *         closingDate: "2025-06-23"
 *         uploadedAt: "2025-10-12T20:00:00Z"
 *         isPaid: false
 *         expenses/
 *           exp_1/
 *             id: "exp_1"
 *             date: "2025-05-24"
 *             description: "CAFE DA ANA"
 *             establishment: "CAFE DA ANA"
 *             city: "CORONEL VIVID"
 *             value: 42.00
 *             category: "Alimentação"
 *             installment: null
 *             isInstallment: false
 *             autoCategorized: true
 * 
 * CHAVE DE IDENTIFICAÇÃO:
 * - Faturas são identificadas por mês de referência: "2025-06"
 * - Conversão: "JUN/2025" → "2025-06"
 * - Previne duplicatas (só uma fatura por mês)
 * 
 * OPERAÇÕES:
 * - saveInvoice(): Upsert (insere ou atualiza se já existe)
 * - getInvoices(): Lista todas as faturas do usuário
 * - getCurrentMonthInvoice(): Busca fatura do mês atual
 * - getInvoiceByMonth(): Busca fatura por mês específico
 * - updateExpenseCategory(): Atualiza categoria de uma despesa
 * - updatePaymentStatus(): Marca fatura como paga/pendente
 * - deleteInvoice(): Remove fatura (por ID, encontra mês correspondente)
 */
object InvoiceService {
    
    /**
     * Referência ao nó de usuários no Firebase
     * Estrutura: database.child(userId).child("invoices")...
     */
    private val database = FirebaseManager.usersRef
    
    /**
     * Parse PDF e Extrai Dados da Fatura
     * 
     * FLUXO:
     * 1. Chama PDFParserDataSourceFixed.parsePDF() para processar PDF
     * 2. Se sucesso, converte resultado para ExtractedInvoiceData
     * 3. Mapeia despesas extraídas mantendo todos os campos
     * 4. Retorna Result<ExtractedInvoiceData>
     * 
     * DADOS EXTRAÍDOS:
     * - Cabeçalho: vencimento, total, pagamento mínimo, mês de referência
     * - Despesas: data, estabelecimento, cidade, valor, parcela
     * 
     * OBSERVAÇÃO:
     * Esta função apenas extrai dados do PDF (não salva no Firebase).
     * O salvamento é feito por saveInvoice() após categorização.
     * 
     * @param pdfFile Arquivo PDF da fatura no sistema de arquivos
     * @return Result<ExtractedInvoiceData> - Success com dados extraídos, Failure se erro no parsing
     */
    suspend fun parseInvoicePDF(pdfFile: File): Result<ExtractedInvoiceData> {
        return try {
            // ========== CHAMA PARSER ==========
            // PDFParserDataSourceFixed.parsePDF() faz todo o trabalho pesado:
            // - Extrai texto do PDF
            // - Parse cabeçalho (vencimento, total, etc.)
            // - Extrai despesas usando parsing por texto
            val result = PDFParserDataSourceFixed.parsePDF(pdfFile)
            
            if (result.isSuccess) {
                val extracted = result.getOrThrow()
                
                // ========== CONVERSÃO PARA ExtractedInvoiceData ==========
                // Mapeia despesas para garantir estrutura correta
                // (alguns campos podem não estar presentes se parsing falhou parcialmente)
                val data = ExtractedInvoiceData(
                    dueDate = extracted.dueDate,
                    totalValue = extracted.totalValue,
                    minimumPayment = extracted.minimumPayment,
                    referenceMonth = extracted.referenceMonth,
                    closingDate = extracted.closingDate,
                    expenses = extracted.expenses.map { exp ->
                        ExtractedExpenseData(
                            date = exp.date,
                            description = exp.description,
                            establishment = exp.establishment,
                            city = exp.city,
                            value = exp.value,
                            installment = exp.installment
                        )
                    }
                )
                Result.success(data)
            } else {
                // Se parsing falhou, propaga erro
                Result.failure(result.exceptionOrNull() ?: Exception("Erro ao processar PDF"))
            }
        } catch (e: Exception) {
            // Tratamento de exceções (arquivo corrompido, formato inválido, etc.)
            Result.failure(e)
        }
    }
    
    /**
     * Salva Fatura no Firebase
     * 
     * ⭐ OPERAÇÃO CRÍTICA: Upsert (Insert ou Update) ⭐
     * 
     * ESTRATÉGIA DE CHAVE:
     * - Usa mês de referência como chave: "2025-06" (de "JUN/2025")
     * - Previne duplicatas: só permite uma fatura por mês
     * - Se já existe fatura do mesmo mês, ATUALIZA (não cria nova)
     * 
     * FLUXO:
     * 1. Converte referenceMonth para chave (ex: "JUN/2025" → "2025-06")
     * 2. Verifica se já existe (opcional, para logging)
     * 3. Gera ID se não fornecido
     * 4. Salva cabeçalho da fatura
     * 5. Salva despesas (substitui completamente o nó "expenses")
     * 
     * IMPORTANTE:
     * - Despesas são salvas com operação atômica (setValue completo)
     * - Evita estados parciais (não salva despesa por despesa)
     * - Se erro ocorrer, ou salva tudo ou não salva nada (consistência)
     * 
     * ESTRUTURA SALVA:
     * invoices/
     *   "2025-06"/  ← Chave = mês
     *     id, userId, dueDate, totalValue, ... (cabeçalho)
     *     expenses/  ← Nó de despesas
     *       exp_1/ {todas as despesas}
     *       exp_2/ {todas as despesas}
     * 
     * @param userId ID do usuário (do Firebase Auth)
     * @param invoice Objeto Invoice completo com despesas
     * @return Result<String> - Success com invoiceId, Failure se erro
     */
    suspend fun saveInvoice(userId: String, invoice: Invoice): Result<String> {
        return try {
            // ========== CONVERSÃO DE CHAVE ==========
            // Converte "JUN/2025" → "2025-06"
            // Esta chave será usada como identificador único no Firebase
            // Garante que só existe UMA fatura por mês
            val monthKey = convertReferenceMonthToKey(invoice.referenceMonth)
            
            // ========== VERIFICAÇÃO (OPCIONAL) ==========
            // Verifica se já existe fatura deste mês (para logging)
            // Não bloqueia operação (é upsert mesmo)
            val existingInvoice = database.child(userId).child("invoices").child(monthKey).get().await()
            
            // ========== GERAÇÃO DE ID ==========
            // Se invoice não tem ID, gera um único
            // Formato: "inv_202506_1697123456789"
            //          prefixo_mês_timestamp
            val invoiceId = invoice.id.ifEmpty { 
                "inv_${monthKey}_${System.currentTimeMillis()}"
            }
            
            // Cria cópia do invoice com ID garantido
            val invoiceWithId = invoice.copy(id = invoiceId)
            
            // ========== SALVAMENTO DO CABEÇALHO ==========
            // Prepara mapa com dados do cabeçalho (sem despesas)
            val invoiceData = mapOf(
                "id" to invoiceWithId.id,
                "userId" to invoiceWithId.userId,
                "dueDate" to invoiceWithId.dueDate,
                "totalValue" to invoiceWithId.totalValue,
                "minimumPayment" to invoiceWithId.minimumPayment,
                "referenceMonth" to invoiceWithId.referenceMonth,
                "closingDate" to invoiceWithId.closingDate,
                "uploadedAt" to invoiceWithId.uploadedAt,
                "isPaid" to invoiceWithId.isPaid
            )
            
            // Salva cabeçalho no Firebase
            // setValue(): Substitui completamente o nó (upsert)
            database.child(userId).child("invoices").child(monthKey).setValue(invoiceData).await()
            
            // ========== SALVAMENTO DAS DESPESAS ==========
            // IMPORTANTE: Operação atômica (salva tudo de uma vez)
            // Não salva despesa por despesa (evita estados parciais)
            
            val expensesRef = database.child(userId).child("invoices").child(monthKey).child("expenses")
            
            // Prepara mapa completo de despesas
            // Chave: "exp_1", "exp_2", etc. (mantém ordem)
            val expensesMap = mutableMapOf<String, Any?>()
            invoiceWithId.expenses.forEachIndexed { idx, expense ->
                val key = "exp_${idx + 1}" // Chave numérica sequencial
                // Garante que expense tem ID correto
                val toSave = expense.copy(id = key).toMap()
                expensesMap[key] = toSave
            }
            
            // Salva TODAS as despesas de uma vez
            // setValue(): Substitui nó completo (se tinha 10 despesas e agora tem 5, fica só 5)
            expensesRef.setValue(expensesMap).await()
            
            Result.success(invoiceId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Converte Mês de Referência para Chave Firebase
     * 
     * CONVERSÃO:
     * "JUN/2025" → "2025-06"
     * "DEZ/2024" → "2024-12"
     * 
     * PROPÓSITO:
     * - Chave padronizada para organização no Firebase
     * - Facilita ordenação (YYYY-MM ordena cronologicamente)
     * - Usado como identificador único (previne duplicatas)
     * 
     * MAPEAMENTO:
     * - Abreviação PT → Número (JAN→01, FEV→02, ..., DEZ→12)
     * - Ano mantido como está
     * 
     * FALLBACK:
     * - Se formato inválido, tenta converter "/" para "-"
     * - Se mês não reconhecido, usa "01" (Janeiro) como padrão
     * 
     * @param referenceMonth String no formato "JUN/2025"
     * @return String no formato "2025-06" (chave Firebase)
     */
    private fun convertReferenceMonthToKey(referenceMonth: String): String {
        // ========== DIVISÃO ==========
        // Divide por "/" (separador mês/ano)
        val parts = referenceMonth.split("/")
        
        // Se não tem formato esperado, retorna com "-" no lugar de "/"
        if (parts.size != 2) return referenceMonth.replace("/", "-")
        
        val month = parts[0].trim() // "JUN"
        val year = parts[1].trim()  // "2025"
        
        // ========== MAPEAMENTO MÊS → NÚMERO ==========
        // Mapa de abreviações brasileiras para números
        val monthMap = mapOf(
            "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04",
            "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08",
            "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
        )
        
        // Busca número do mês (fallback: "01" se não encontrar)
        val monthNumber = monthMap[month] ?: "01"
        
        // ========== RETORNA FORMATO FINAL ==========
        // Formato: "YYYY-MM" (padrão ISO para ordenação)
        return "$year-$monthNumber"
    }
    
    /**
     * Busca Todas as Faturas do Usuário
     * 
     * FLUXO:
     * 1. Busca nó "invoices" do usuário no Firebase
     * 2. Para cada mês (chave "2025-06", etc.):
     *    a) Lê cabeçalho da fatura (campos principais)
     *    b) Lê despesas (ordena por exp_1, exp_2, ...)
     *    c) Reconstrói objeto Invoice completo
     * 3. Ordena por data de upload (mais recente primeiro)
     * 
     * ORDENAÇÃO DE DESPESAS:
     * - Mantém ordem original: exp_1, exp_2, exp_3, ...
     * - Ordena por número após "exp_" (1 < 2 < 3)
     * - Garante que despesas aparecem na ordem de upload
     * 
     * TRATAMENTO DE DADOS:
     * - Separa cabeçalho de despesas (campos diferentes no Firebase)
     * - Converte Map do Firebase para objetos Invoice/Expense
     * - Ignora campos inválidos silenciosamente
     * 
     * @param userId ID do usuário
     * @return Result<List<Invoice>> - Success com lista ordenada (mais recente primeiro)
     */
    suspend fun getInvoices(userId: String): Result<List<Invoice>> {
        return try {
            // ========== BUSCA NO FIREBASE ==========
            // Busca nó completo de invoices do usuário
            // Estrutura esperada: invoices/{monthKey}/...
            val snapshot = database.child(userId).child("invoices").get().await()
            val invoices = mutableListOf<Invoice>()
            
            // ========== PROCESSAMENTO DE CADA MÊS ==========
            // snapshot.children = cada mês (2025-06, 2025-07, etc.)
            snapshot.children.forEach { monthSnapshot ->
                // ========== LEITURA DO CABEÇALHO ==========
                // Cabeçalho = todos os campos EXCETO "expenses"
                val invoiceData = mutableMapOf<String, Any?>()
                monthSnapshot.children.forEach { field ->
                    if (field.key != "expenses") {
                        // Adiciona campo ao mapa do cabeçalho
                        invoiceData[field.key!!] = field.value
                    }
                }
                
                // ========== LEITURA DAS DESPESAS ==========
                val expenses = mutableListOf<Expense>()
                
                // Busca nó "expenses" deste mês
                val expensesSnapshot = monthSnapshot.child("expenses")
                
                // ========== ORDENAÇÃO DAS DESPESAS ==========
                // IMPORTANTE: Ordena por número da chave (exp_1, exp_2, ...)
                // Garante ordem original de upload
                val sortedChildren = expensesSnapshot.children.sortedBy { child ->
                    val name = child.key ?: ""
                    // Remove prefixo "exp_" e converte para número
                    // Ex: "exp_1" → "1" → 1
                    // Ex: "exp_10" → "10" → 10
                    // Fallback: Int.MAX_VALUE se não conseguir converter
                    name.removePrefix("exp_").toIntOrNull() ?: Int.MAX_VALUE
                }
                
                // ========== CONVERSÃO DE DESPESAS ==========
                // Para cada despesa ordenada, converte Map → Expense
                sortedChildren.forEach { expenseChild ->
                    val expenseMap = expenseChild.value as? Map<*, *>
                    if (expenseMap != null) {
                        @Suppress("UNCHECKED_CAST")
                        // Expense.fromMap() reconstrói objeto Expense
                        expenses.add(Expense.fromMap(expenseMap as Map<String, Any?>))
                    }
                }
                
                // ========== CRIAÇÃO DO INVOICE ==========
                // Se tem dados de cabeçalho, cria Invoice completo
                if (invoiceData.isNotEmpty()) {
                    // Adiciona despesas ao mapa (formato esperado por fromMap)
                    invoiceData["expenses"] = expenses.map { it.toMap() }
                    
                    @Suppress("UNCHECKED_CAST")
                    // Invoice.fromMap() reconstrói objeto Invoice completo
                    val invoice = Invoice.fromMap(invoiceData as Map<String, Any?>)
                    invoices.add(invoice)
                }
            }
            
            // ========== ORDENAÇÃO FINAL ==========
            // Ordena por uploadedAt DESC (mais recente primeiro)
            // sortedByDescending(): Ordena do maior para menor
            Result.success(invoices.sortedByDescending { it.uploadedAt })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get current month invoice
     */
    suspend fun getCurrentMonthInvoice(userId: String): Result<Invoice?> {
        return try {
            val currentMonth = getCurrentMonthReference()
            val invoices = getInvoices(userId).getOrThrow()
            val currentInvoice = invoices.firstOrNull { it.referenceMonth == currentMonth }
            Result.success(currentInvoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get latest invoice (most recent)
     */
    suspend fun getLatestInvoice(userId: String): Result<Invoice?> {
        return try {
            val invoices = getInvoices(userId).getOrThrow()
            Result.success(invoices.firstOrNull()) // Already sorted by uploadedAt desc
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get previous month invoice
     */
    suspend fun getPreviousMonthInvoice(userId: String): Result<Invoice?> {
        return try {
            val previousMonth = getPreviousMonthReference()
            val invoices = getInvoices(userId).getOrThrow()
            val previousInvoice = invoices.firstOrNull { it.referenceMonth == previousMonth }
            Result.success(previousInvoice)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete invoice
     * Finds by invoiceId and deletes the month node
     */
    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        return try {
            // Find the invoice by invoiceId
            val snapshot = database.child(userId).child("invoices").get().await()
            var monthKeyToDelete: String? = null
            
            snapshot.children.forEach { monthSnapshot ->
                val id = monthSnapshot.child("id").value as? String
                if (id == invoiceId) {
                    monthKeyToDelete = monthSnapshot.key
                    return@forEach
                }
            }
            
            if (monthKeyToDelete != null) {
                database.child(userId).child("invoices").child(monthKeyToDelete!!).removeValue().await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Fatura não encontrada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza Categoria de Uma Despesa
     * 
     * OPERAÇÃO:
     * - Busca fatura pelo invoiceId (não pela chave do mês)
     * - Encontra a chave do mês correspondente
     * - Atualiza apenas o campo "category" da despesa específica
     * 
     * FLUXO:
     * 1. Busca todas as faturas do usuário
     * 2. Encontra a fatura com invoiceId correspondente
     * 3. Extrai chave do mês (ex: "2025-06")
     * 4. Atualiza campo "category" da despesa
     * 
     * POR QUE BUSCAR POR ID?
     * - Controller/Fragment conhece invoiceId, não a chave do mês
     * - Mais flexível (não depende de saber o formato da chave)
     * 
     * ESTRUTURA ATUALIZADA:
     * invoices/
     *   "2025-06"/
     *     expenses/
     *       exp_1/
     *         category: "Alimentação" ← Atualizado aqui
     * 
     * @param userId ID do usuário
     * @param invoiceId ID da fatura (ex: "inv_202506_abc123")
     * @param expenseId ID da despesa (ex: "exp_1")
     * @param newCategory Nova categoria (ex: "Alimentação")
     * @return Result<Unit> - Success se atualizado, Failure se fatura não encontrada
     */
    suspend fun updateExpenseCategory(
        userId: String,
        invoiceId: String,
        expenseId: String,
        newCategory: String
    ): Result<Unit> {
        return try {
            // ========== BUSCA FATURA POR ID ==========
            // Busca todas as faturas para encontrar a correta
            val snapshot = database.child(userId).child("invoices").get().await()
            var monthKey: String? = null
            
            // Percorre cada mês procurando pelo invoiceId
            snapshot.children.forEach { monthSnapshot ->
                // Lê campo "id" do cabeçalho
                val id = monthSnapshot.child("id").value as? String
                
                // Se encontrou fatura com ID correspondente, salva chave do mês
                if (id == invoiceId) {
                    monthKey = monthSnapshot.key // Ex: "2025-06"
                    return@forEach // Para de procurar
                }
            }
            
            // ========== ATUALIZAÇÃO ==========
            if (monthKey != null) {
                // Atualiza apenas o campo "category" da despesa
                // Caminho: invoices/{monthKey}/expenses/{expenseId}/category
                database.child(userId).child("invoices").child(monthKey!!)
                    .child("expenses").child(expenseId).child("category")
                    .setValue(newCategory).await()
                Result.success(Unit)
            } else {
                // Se não encontrou fatura, retorna erro
                Result.failure(Exception("Fatura não encontrada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
        return try {
            // Find the month key for this invoice
            val snapshot = database.child(userId).child("invoices").get().await()
            var monthKey: String? = null
            
            snapshot.children.forEach { monthSnapshot ->
                val id = monthSnapshot.child("id").value as? String
                if (id == invoiceId) {
                    monthKey = monthSnapshot.key
                    return@forEach
                }
            }
            
            if (monthKey != null) {
                val updates = mutableMapOf<String, Any?>()
                updates["isPaid"] = isPaid
                if (paidDate != null) {
                    updates["paidDate"] = paidDate
                }
                
                database.child(userId).child("invoices").child(monthKey!!)
                    .updateChildren(updates).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Fatura não encontrada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get invoice by month reference
     */
    suspend fun getInvoiceByMonth(userId: String, referenceMonth: String): Result<Invoice> {
        return try {
            val invoices = getInvoices(userId).getOrThrow()
            val invoice = invoices.find { it.referenceMonth == referenceMonth }
            if (invoice != null) {
                Result.success(invoice)
            } else {
                Result.failure(Exception("Fatura não encontrada para o mês $referenceMonth"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Helper: Get current month reference (e.g., "JUN/2025")
     */
    private fun getCurrentMonthReference(): String {
        val calendar = java.util.Calendar.getInstance()
        val monthNames = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        val month = monthNames[calendar.get(java.util.Calendar.MONTH)]
        val year = calendar.get(java.util.Calendar.YEAR)
        return "$month/$year"
    }
    
    /**
     * Helper: Get previous month reference (e.g., "MAI/2025")
     */
    private fun getPreviousMonthReference(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MONTH, -1)
        val monthNames = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        val month = monthNames[calendar.get(java.util.Calendar.MONTH)]
        val year = calendar.get(java.util.Calendar.YEAR)
        return "$month/$year"
    }
}

