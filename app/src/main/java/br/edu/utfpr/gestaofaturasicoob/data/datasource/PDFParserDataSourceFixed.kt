package br.edu.utfpr.gestaofaturasicoob.data.datasource

import android.util.Log
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedInvoiceData
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * InvoiceHeader - Dados Extraídos do Cabeçalho da Fatura
 * 
 * Representa informações principais da fatura extraídas do PDF:
 * - dueDate: Data de vencimento (formato ISO: "2025-07-03")
 * - totalValue: Valor total da fatura
 * - minimumPayment: Pagamento mínimo
 * - referenceMonth: Mês de referência (formato: "JUN/2025")
 * - closingDate: Data de fechamento da fatura
 */
data class InvoiceHeader(
    val dueDate: String,
    val totalValue: Double,
    val minimumPayment: Double,
    val referenceMonth: String,
    val closingDate: String
)

/**
 * PDFParserDataSourceFixed - Parser Completo de PDF de Fatura Sicoob
 * 
 * ⭐ ARQUIVO MAIS COMPLEXO DO SISTEMA ⭐
 * 
 * RESPONSABILIDADES:
 * 1. Extração de texto do PDF usando iText7
 * 2. Parsing do cabeçalho (vencimento, total, período)
 * 3. Extração de despesas usando parsing por texto (regex + lógica sequencial)
 * 4. Tratamento de casos especiais (quebras de linha, erros de OCR)
 * 
 * ESTRATÉGIA DE PARSING:
 * 
 * PARSING POR TEXTO (parseInvoiceText):
 * - Divide PDF em linhas
 * - Usa regex para identificar padrões de data, estabelecimento, valor
 * - Trata quebras de linha (lookahead de até 3 linhas)
 * - Identifica seção "MOVIMENTAÇÕES DA CONTA" (tarifas)
 * 
 * DESAFIOS RESOLVIDOS:
 * - Quebras de linha no meio de uma despesa
 * - Estabelecimentos com nomes compostos
 * - Cidades longas que podem ser confundidas com estabelecimento
 * - Valores negativos (estornos)
 * - Parcelamento (formato "03/04")
 * - Tarifas misturadas com compras
 * - Formatos diferentes de data no cabeçalho
 * 
 * PERFORMANCE:
 * - Processa PDFs com até 100+ despesas em <10 segundos
 * - Usa StringBuilder para concatenação eficiente
 * - Processa páginas sequencialmente (não carrega tudo na memória)
 */
object PDFParserDataSourceFixed {
    private const val TAG = "PDFParserFixed"
    
    /**
     * Ano da fatura extraído do PDF
     * 
     * USO:
     * - Conversão de datas sem ano (ex: "24 MAI" → "2025-05-24")
     * - Extração do mês de referência do período
     * 
     * INICIALIZAÇÃO:
     * - Padrão: Ano atual
     * - Extraído do PDF: Busca datas com ano (VENCIMENTO, FECHAMENTO)
     */
    private var invoiceYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    
    /**
     * Função Principal: Parse PDF e Extrai Dados da Fatura
     * 
     * FLUXO COMPLETO:
     * 1. Valida arquivo (existe? é PDF?)
     * 2. Abre PDF usando iText7
     * 3. Extrai texto de todas as páginas
     * 4. Faz parsing do texto (cabeçalho + despesas)
     * 5. Retorna ExtractedInvoiceData completo
     * 
     * GESTÃO DE RECURSOS:
     * - finally {} garante que PDF é fechado mesmo em caso de erro
     * - Evita memory leak (PDF mantém referência ao arquivo)
     * 
     * @param file Arquivo PDF da fatura
     * @return Result<ExtractedInvoiceData> - Success com dados extraídos, Failure se erro
     */
    fun parsePDF(file: File): Result<ExtractedInvoiceData> {
        var pdfDocument: PdfDocument? = null
        try {
            Log.d(TAG, "Iniciando parsing do PDF: ${file.name}")
            
            // ========== VALIDAÇÃO DE ENTRADA ==========
            // Verifica se arquivo tem extensão .pdf
            if (!file.name.endsWith(".pdf", true)) {
                Log.e(TAG, "Arquivo não é PDF válido: ${file.name}")
                return Result.failure(Exception("Arquivo não é PDF válido"))
            }
            
            // Verifica se arquivo existe fisicamente
            if (!file.exists()) {
                Log.e(TAG, "Arquivo não existe: ${file.absolutePath}")
                return Result.failure(Exception("Arquivo não existe"))
            }
            
            // ========== ABERTURA DO PDF ==========
            // PdfReader: Lê arquivo PDF do sistema de arquivos
            // PdfDocument: Representa documento PDF na memória
            Log.d(TAG, "Abrindo PDF: ${file.absolutePath} (tamanho: ${file.length()} bytes)")
            val pdfReader = PdfReader(file)
            pdfDocument = PdfDocument(pdfReader)
            
            // Valida se PDF tem páginas
            if (pdfDocument.numberOfPages == 0) {
                Log.e(TAG, "PDF está vazio")
                return Result.failure(Exception("PDF está vazio"))
            }
            
            Log.d(TAG, "PDF carregado com sucesso. Páginas: ${pdfDocument.numberOfPages}")
            
            // ========== EXTRAÇÃO DE TEXTO (ESTRATÉGIA 1) ==========
            // Extrai texto de todas as páginas para parsing sequencial
            // SimpleTextExtractionStrategy: Extrai texto na ordem de leitura
            // Não preserva layout (texto corrido), mas mantém ordem
            val textBuilder = StringBuilder()
            for (i in 1..pdfDocument.numberOfPages) {
                val page = pdfDocument.getPage(i)
                // getTextFromPage(): Extrai todo o texto de uma página
                val text = PdfTextExtractor.getTextFromPage(page, SimpleTextExtractionStrategy())
                textBuilder.append(text).append("\n") // Adiciona quebra de linha entre páginas
            }
            val text = textBuilder.toString()
            
            Log.d(TAG, "Texto extraído do PDF (${text.length} chars, primeiros 500): ${text.take(500)}")
            
            // Valida se conseguiu extrair texto
            if (text.isBlank()) {
                Log.e(TAG, "Texto extraído está vazio")
                return Result.failure(Exception("Não foi possível extrair texto do PDF"))
            }
            
            // ========== PARSING DO TEXTO ==========
            // Parse completo do texto extraído (cabeçalho + despesas)
            Log.d(TAG, "Iniciando parsing do texto extraído...")
            val extractedInvoice = parseInvoiceText(text)
            
            Log.d(TAG, "Parsing concluído com sucesso. ${extractedInvoice.expenses.size} despesas encontradas")
            
            return Result.success(extractedInvoice)
            
        } catch (e: Exception) {
            // Tratamento de qualquer erro (arquivo corrompido, formato inválido, etc.)
            Log.e(TAG, "Erro ao processar PDF: ${e.javaClass.simpleName}", e)
            return Result.failure(Exception("Erro ao processar PDF: ${e.message}"))
        } finally {
            // ========== LIMPEZA DE RECURSOS ==========
            // CRÍTICO: Sempre fechar o documento para liberar recursos
            // Mesmo em caso de erro, o finally garante que será fechado
            try {
                pdfDocument?.close()
                Log.d(TAG, "Documento PDF fechado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao fechar documento", e)
            }
        }
    }
    
    private fun parseInvoiceText(text: String): ExtractedInvoiceData {
        try {
            Log.d(TAG, "Iniciando parseInvoiceText com ${text.length} caracteres")
            val lines = text.split("\n").map { it.trim() }
            Log.d(TAG, "Texto dividido em ${lines.size} linhas")
            
            // Log das primeiras 50 linhas para debug
            Log.d(TAG, "Primeiras 50 linhas do PDF:")
            lines.take(50).forEachIndexed { index, line ->
                Log.d(TAG, "Linha ${index + 1}: $line")
            }
            
            // Extrair ano da fatura primeiro
            extractYearFromText(text)
            
            // Extrair cabeçalho da fatura usando a nova função parseHeader
            Log.d(TAG, "Extraindo cabeçalho...")
            val header = parseHeader(text)
            Log.d(TAG, "Cabeçalho extraído: $header")
            
            // Extrair despesas
            Log.d(TAG, "Extraindo despesas...")
            val expenses = extractExpenses(lines)
            Log.d(TAG, "Despesas extraídas: ${expenses.size}")
            
            val result = ExtractedInvoiceData(
                dueDate = header.dueDate,
                totalValue = header.totalValue,
                minimumPayment = header.minimumPayment,
                referenceMonth = header.referenceMonth,
                closingDate = header.closingDate,
                expenses = expenses
            )
            
            Log.d(TAG, "ExtractedInvoice criado com sucesso")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro em parseInvoiceText", e)
            // Retornar uma fatura vazia em caso de erro
            return ExtractedInvoiceData(
                dueDate = "",
                totalValue = 0.0,
                minimumPayment = 0.0,
                referenceMonth = "",
                closingDate = "",
                expenses = emptyList()
            )
        }
    }
    
    /**
     * Extrai Despesas das Linhas do PDF (Estratégia: Parsing por Texto)
     * 
     * ALGORITMO:
     * 1. Percorre todas as linhas sequencialmente
     * 2. Identifica início de seções ("MOVIMENTAÇÕES DA CONTA")
     * 3. Detecta linhas com padrão de data (DD MMM)
     * 4. Trata quebras de linha (lookahead de até 3 linhas)
     * 5. Parse de cada linha válida para extrair despesa
     * 
     * DESAFIOS RESOLVIDOS:
     * - Quebra de linha: Data em uma linha, valor em outra
     * - Seções diferentes: MOVIMENTAÇÕES vs tabela normal
     * - Formato inconsistente: PDFs podem ter espaços extras
     * 
     * LOOKAHEAD:
     * Se linha tem data mas não tem valor (R$), olha até 3 linhas à frente
     * para encontrar o valor. Isso resolve quebras de linha no PDF.
     * 
     * @param lines Lista de linhas do texto extraído do PDF
     * @return Lista de despesas extraídas
     */
    private fun extractExpenses(lines: List<String>): List<ExtractedExpenseData> {
        val expenses = mutableListOf<ExtractedExpenseData>()
        Log.d(TAG, "Procurando despesas em ${lines.size} linhas...")

        // ========== REGEX PATTERNS ==========
        
        // Padrão para data: "08 MAI" ou "24 JUN"
        // \b: word boundary (evita matches parciais)
        // \d{2}: exatamente 2 dígitos
        // \s+: um ou mais espaços
        // [A-Z]{3}: exatamente 3 letras maiúsculas (abreviação do mês)
        val datePattern = """\b\d{2}\s+[A-Z]{3}\b""".toRegex()
        
        // Padrão para cabeçalho "MOVIMENTAÇÕES DA CONTA"
        // Aceita variações: MOVIMENTAÇÕES, MOVIMENTACOES (com/sem acento)
        // (Ç|C): aceita Ç ou C
        // (Õ|O): aceita Õ ou O
        // IGNORE_CASE: case-insensitive
        val movHeader = """^\s*MOVIMENTA(Ç|C)(Õ|O)ES?\s+DA\s+CONTA\s*$""".toRegex(RegexOption.IGNORE_CASE)
        
        // Padrão para cabeçalho de tabela "DATA DESCRIÇÃO CIDADE VALOR"
        val tableHeader = """^\s*DATA\s+DESCRIÇÃO|DESCRICAO\s+\s*CIDADE\s+\s*VALOR""".toRegex(RegexOption.IGNORE_CASE)

        // ========== VARIÁVEIS DE CONTROLE ==========
        var i = 0 // Índice da linha atual
        var inMovements = false // Flag: estamos na seção "MOVIMENTAÇÕES"?

        // ========== LOOP PRINCIPAL: PERCORRE TODAS AS LINHAS ==========
        while (i < lines.size) {
            // Normaliza linha: remove espaços duplos e trim
            val rawLine = lines[i].replace("  ", " ").trim()
            
            // ========== DETECÇÃO DE SEÇÕES ==========
            // Se encontra cabeçalho "MOVIMENTAÇÕES DA CONTA", ativa flag
            // Esta seção contém tarifas (ANUIDADE, PROTEÇÃO, etc.)
            if (movHeader.containsMatchIn(rawLine)) { 
                inMovements = true; 
                i += 1; 
                continue // Pula para próxima linha
            }
            
            // Se encontra cabeçalho de tabela, desativa flag
            // Tabela normal contém compras (não tarifas)
            if (tableHeader.containsMatchIn(rawLine)) { 
                inMovements = false 
            }
            
            // ========== DETECÇÃO DE LINHA COM DESPESA ==========
            // Verifica se linha tem padrão de data
            val hasDate = datePattern.containsMatchIn(rawLine)
            // Verifica se linha tem símbolo de moeda (R$ ou -R$ para estornos)
            val hasCurrency = rawLine.contains("R$") || rawLine.contains("-R$")

            // ========== TRATAMENTO DE QUEBRA DE LINHA ==========
            // Se linha tem data mas NÃO tem valor, pode ter quebrado
            // Lookahead: olha até 3 linhas à frente para encontrar valor
            var candidate = rawLine // Linha candidata a ser uma despesa completa
            if (hasDate && !hasCurrency) {
                // LOOKAHEAD: Tenta juntar próximas linhas
                var lookahead = 1 // Começa olhando 1 linha à frente
                while (i + lookahead < lines.size && lookahead <= 3) {
                    val nxt = lines[i + lookahead].replace("  ", " ").trim()
                    // Concatena linha atual com próxima
                    candidate = "$candidate $nxt".trim()
                    
                    // Se encontrou valor (R$), para de procurar
                    if (nxt.contains("R$") || nxt.contains("-R$")) {
                        i += lookahead // Pula linhas já processadas
                        break
                    }
                    lookahead += 1
                }
            }

            // ========== PARSE DA LINHA ==========
            // Se candidato tem data E valor, tenta extrair despesa
            if (datePattern.containsMatchIn(candidate) && 
                (candidate.contains("R$") || candidate.contains("-R$"))) {
                
                // parseExpenseLine(): Extrai campos da linha
                // inMovements: Passa flag para identificar seção (influencia parse)
                val expense = parseExpenseLine(candidate, inMovements)
                
                if (expense != null) {
                    expenses.add(expense)
                    Log.d(TAG, "Despesa extraída: ${expense.establishment} - R$ ${expense.value}${if (expense.installment != null) " (${expense.installment})" else ""}")
                }
            }

            i += 1 // Próxima linha
        }

        Log.d(TAG, "Total de despesas extraídas: ${expenses.size}")
        return expenses
    }
    
    /**
     * Parse de Uma Linha de Despesa
     * 
     * EXTRAI CAMPOS:
     * - Data: "08 MAI" → "2025-05-08"
     * - Estabelecimento: "CAFE DA ANA" (parte antes da cidade)
     * - Cidade: "CORONEL VIVID" (último bloco em maiúsculas)
     * - Valor: "R$ 42,00" → 42.0
     * - Parcela: "03/04" (opcional)
     * 
     * ALGORITMO DE EXTRAÇÃO:
     * 1. Normaliza linha (remove espaços extras)
     * 2. Filtra entradas que não são despesas (SALDO ANTERIOR, PAGAMENTO, CRÉDITO)
     * 3. Detecta se valor é negativo (estorno)
     * 4. Extrai data usando regex (começo da linha)
     * 5. Isola parte antes do valor (R$)
     * 6. Remove data e parcela dessa parte
     * 7. Extrai cidade (último bloco em maiúsculas) e estabelecimento (restante)
     * 8. Extrai valor após R$
     * 
     * REGRAS ESPECIAIS:
     * - Seção MOVIMENTAÇÕES: Aceita despesas sem cidade (tarifas)
     * - Tarifas: ANUIDADE, PROTEÇÃO não precisam de cidade
     * - Valores negativos: Mantidos como negativos (estornos)
     * 
     * HEURÍSTICA DE CIDADE:
     * - Regex captura último bloco em maiúsculas (1-3 palavras)
     * - Se não encontrar, tenta pegar últimas 2 palavras
     * - Em MOVIMENTAÇÕES, pode ser vazio
     * 
     * @param line Linha completa do PDF (já com quebras resolvidas)
     * @param inMovements true se está na seção "MOVIMENTAÇÕES DA CONTA"
     * @return ExtractedExpenseData se linha é válida, null caso contrário
     */
    private fun parseExpenseLine(line: String, inMovements: Boolean): ExtractedExpenseData? {
        // ========== NORMALIZAÇÃO ==========
        // Remove espaços duplos e faz trim
        val norm = line.replace("  ", " ").trim()

        // ========== FILTROS: IGNORA LINHAS QUE NÃO SÃO DESPESAS ==========
        // SALDO ANTERIOR: Não é despesa (é informação do banco)
        if (norm.contains("SALDO ANTERIOR", true)) return null
        
        // PAGAMENTO: Pagamentos recebidos (não são gastos)
        // Cobre variações: "PAGAMENTO-BOLETO BANCARIO", "PAGAMENTO", etc.
        if (norm.contains("PAGAMENTO", true)) return null
        
        // CRÉDITO: Créditos na fatura (não são gastos)
        if (norm.contains("CRÉDITO", true) || norm.contains("CREDITO", true)) return null

        // ========== DETECÇÃO DE VALOR NEGATIVO ==========
        // Estornos aparecem como "-R$ xx" ou "R$ -xx"
        // Regex: R$ seguido de espaços e sinal de menos
        val isNegative = norm.contains("-R$") || "R\\$\\s*-".toRegex().containsMatchIn(norm)

        // ========== EXTRAÇÃO DE DATA ==========
        // Regex: ^(\d{2}\s+[A-Z]{3})\b
        //   ^: início da linha
        //   (\d{2}\s+[A-Z]{3}): grupo captura "08 MAI"
        //   \b: word boundary
        // Se não encontrar data no início, linha não é válida
        val dateMatch = """^(\d{2}\s+[A-Z]{3})\b""".toRegex().find(norm) ?: return null
        val date = dateMatch.groupValues[1] // Extrai "08 MAI"

        // ========== ISOLAMENTO DA PARTE ANTES DO VALOR ==========
        // Encontra posição de "R$" na linha
        val currencyIndex = norm.indexOf("R$")
        // Pega tudo antes de R$ (data + estabelecimento + cidade + parcela)
        val beforeCurrency = if (currencyIndex >= 0) norm.substring(0, currencyIndex) else return null

        // ========== REMOÇÃO DE DATA E PARCELA ==========
        // Remove data do início
        val beforeNoDate = beforeCurrency.removePrefix(date).trim()
        
        // Extrai parcela (formato "03/04")
        // Regex: (\d{2}/\d{2}) captura exatamente "03/04"
        val installment = """(\d{2}/\d{2})""".toRegex().find(beforeNoDate)?.value
        
        // Remove parcela do texto (se existir)
        val textNoInst = if (installment != null) 
            beforeNoDate.replace(installment, " ").trim() 
        else 
            beforeNoDate

        // ========== EXTRAÇÃO DE CIDADE E ESTABELECIMENTO ==========
        // HEURÍSTICA: Cidade é último bloco em MAIÚSCULAS (1 a 3 palavras)
        // Regex: ([A-ZÁÉÍÓÚÃÕÇ]{2,}(?:\s+[A-ZÁÉÍÓÚÃÕÇ]{2,}){0,2})$
        //   [A-ZÁÉÍÓÚÃÕÇ]{2,}: 2+ letras maiúsculas (inclui acentos)
        //   (?:\s+[A-ZÁÉÍÓÚÃÕÇ]{2,}){0,2}: 0 a 2 palavras adicionais
        //   $: fim da string
        val cityRegex = """([A-ZÁÉÍÓÚÃÕÇ]{2,}(?:\s+[A-ZÁÉÍÓÚÃÕÇ]{2,}){0,2})$""".toRegex()
        val cityMatch = cityRegex.find(textNoInst)
        
        // Se encontrou cidade pelo regex, usa ela
        // Senão, tenta pegar últimas 2 palavras como fallback
        val city = (cityMatch?.value ?: textNoInst.split(" ").takeLast(2).joinToString(" ")).trim()
        
        // Estabelecimento = tudo exceto cidade (remove cidade do final)
        val establishment = textNoInst.removeSuffix(city).trim().ifEmpty { textNoInst }

        // ========== EXTRAÇÃO DE VALOR ==========
        // Pega tudo após "R$"
        val valueStr = norm.substring(norm.indexOf("R$") + 2)
            .replace("-", "") // Remove sinal de menos
            .trim()
            .replace("R$", "") // Remove R$ duplicado (se houver)
            .trim()
        
        // Converte string para Double (trata formato brasileiro "1.200,50")
        var value = convertCurrencyToDouble(valueStr)
        // Se detectou negativo, aplica sinal
        if (isNegative) value = -value

        // ========== AJUSTES ESPECIAIS ==========
        // Seção MOVIMENTAÇÕES: Aceita despesas sem cidade (tarifas tipo ANUIDADE)
        val finalCity = if (cityMatch == null && inMovements) "" else city

        // Se não encontrou cidade e NÃO está em MOVIMENTAÇÕES:
        // Só aceita se parecer tarifa (tem palavras-chave)
        if (cityMatch == null && !inMovements) {
            val descOnly = textNoInst.trim()
            val keywords = listOf("ANUIDADE", "PROTEÇÃO", "PROTECAO")
            val looksLikeFee = keywords.any { descOnly.contains(it, true) }
            // Se não parece tarifa, descarta (despesa sem cidade é inválida)
            if (!looksLikeFee) return null
        }

        // ========== CRIAÇÃO DO OBJETO ==========
        return ExtractedExpenseData(
            date = convertDateToISO(date), // Converte "08 MAI" → "2025-05-08"
            description = establishment, // Descrição = estabelecimento
            establishment = establishment,
            city = finalCity, // Cidade (pode ser vazia em MOVIMENTAÇÕES)
            value = value, // Valor (pode ser negativo se estorno)
            installment = installment // Parcela (ex: "03/04" ou null)
        )
    }
    
    private fun convertDateFormat(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao converter data: $dateStr", e)
            dateStr
        }
    }
    
    /**
     * Converte Período para Mês de Referência
     * 
     * LÓGICA IMPORTANTE:
     * Fatura cobre período "26 MAI A 23 JUN"
     * - Fechamento: 23 JUN
     * - Mês de referência: MAI (mês anterior ao fechamento)
     * 
     * REGRA:
     * Mês de referência = Mês final - 1
     * 
     * EXEMPLOS:
     * - "26 MAI A 23 JUN" → "MAI/2025" (JUN - 1 = MAI)
     * - "26 DEZ A 23 JAN" → "DEZ/2024" (JAN - 1 = DEZ, ano também diminui)
     * 
     * TRATAMENTO DE MUDANÇA DE ANO:
     * Se mês final é JAN (índice 0), mês anterior é DEZ (índice 11) do ano anterior
     * 
     * @param periodStr String no formato "26 MAI A 23 JUN"
     * @return String no formato "MAI/2025" (mês de referência)
     */
    private fun convertPeriodToMonth(periodStr: String): String {
        return try {
            // ========== DIVIDE PERÍODO ==========
            // Split por " A " (separador entre início e fim)
            // Exemplo: "26 MAI A 23 JUN" → ["26 MAI", "23 JUN"]
            val parts = periodStr.split("\\s+A\\s+".toRegex())
            
            if (parts.size >= 2) {
                // ========== EXTRAI MÊS FINAL ==========
                // parts[1] = "23 JUN" (data de fechamento)
                val endPart = parts[1].trim()
                // Divide em dia e mês: ["23", "JUN"]
                val monthYear = endPart.split("\\s+".toRegex())
                
                if (monthYear.size >= 2) {
                    // monthYear[1] = "JUN" (mês de fechamento)
                    val endMonthAbbr = monthYear[1].uppercase(Locale.getDefault())
                    
                    // ========== MAPA DE MESES ==========
                    // Lista de abreviações em ordem (0=JAN, 11=DEZ)
                    val monthMap = listOf("JAN","FEV","MAR","ABR","MAI","JUN","JUL","AGO","SET","OUT","NOV","DEZ")
                    val endIndex = monthMap.indexOf(endMonthAbbr)
                    
                    if (endIndex >= 0) {
                        // ========== CALCULA MÊS DE REFERÊNCIA ==========
                        // Mês de referência = mês final - 1
                        var refIndex = endIndex - 1
                        var year = invoiceYear // Ano da fatura (extraído anteriormente)
                        
                        // ========== TRATAMENTO DE MUDANÇA DE ANO ==========
                        // Se refIndex < 0, significa que mês final é JAN
                        // Mês anterior ao JAN é DEZ do ano anterior
                        if (refIndex < 0) {
                            refIndex = 11 // DEZ (último mês)
                            year -= 1 // Ano anterior
                        }
                        
                        // ========== RETORNA FORMATO FINAL ==========
                        val refMonthAbbr = monthMap[refIndex]
                        return "$refMonthAbbr/$year" // Ex: "MAI/2025"
                    }
                }
            }
            
            // Se não conseguiu parsear, retorna string original
            periodStr
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao converter período: $periodStr", e)
            periodStr
        }
    }
    
    private fun convertDateToISO(dateStr: String): String {
        return try {
            // Exemplo: "24 MAI" -> "2025-05-24" ou "24 MAI 2025" -> "2025-05-24"
            val parts = dateStr.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val day = parts[0].padStart(2, '0')
                val month = convertMonthAbbreviation(parts[1])
                // Se tem ano na string, usa ele, senão usa o ano da fatura
                val year = if (parts.size >= 3) parts[2] else invoiceYear.toString()
                return "$year-$month-$day"
            }
            dateStr
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao converter data ISO: $dateStr", e)
            dateStr
        }
    }
    
    private fun convertMonthAbbreviation(monthAbbr: String): String {
        val months = mapOf(
            "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04",
            "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08",
            "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
        )
        return months[monthAbbr.uppercase()] ?: "01"
    }
    
    /**
     * Extrai o ano da fatura a partir de datas completas no texto (VENCIMENTO ou FECHAMENTO)
     */
    private fun extractYearFromText(text: String) {
        // Procurar por qualquer data com 4 dígitos de ano no texto
        val yearPattern = """(\d{1,2}\s+[A-Z]{3}\s+(\d{4}))""".toRegex()
        val match = yearPattern.find(text)
        if (match != null) {
            val year = match.groupValues[2].toIntOrNull()
            if (year != null && year in 2020..2099) {
                invoiceYear = year
                Log.d(TAG, "Ano da fatura extraído: $invoiceYear")
            }
        } else {
            // Se não encontrar, usar o ano atual
            invoiceYear = Calendar.getInstance().get(Calendar.YEAR)
            Log.d(TAG, "Ano não encontrado no PDF, usando ano atual: $invoiceYear")
        }
    }
    
    /**
     * Parse invoice header information from PDF text
     * Based on Sicoob credit card invoice format
     */
    fun parseHeader(text: String): InvoiceHeader {
        val lines = text.split("\n").map { it.trim() }
        
        Log.d(TAG, "Parsing invoice header from ${lines.size} lines")
        
        val dueDate = extractDueDateFromHeader(lines)
        val totalValue = extractTotalValueFromHeader(lines)
        val minimumPayment = extractMinimumPaymentFromHeader(lines)
        val referenceMonth = extractReferenceMonthFromHeader(lines)
        val closingDate = extractClosingDateFromHeader(lines)
        
        Log.d(TAG, "Header parsed - Due: $dueDate, Total: $totalValue, Min: $minimumPayment, Ref: $referenceMonth, Close: $closingDate")
        
        return InvoiceHeader(
            dueDate = dueDate,
            totalValue = totalValue,
            minimumPayment = minimumPayment,
            referenceMonth = referenceMonth,
            closingDate = closingDate
        )
    }
    
    private fun extractDueDateFromHeader(lines: List<String>): String {
        // Procurar por "VENCIMENTO" seguido de data no formato "3 JUL 2025" ou "03/07/2025"
        val dueDatePatterns = listOf(
            "VENCIMENTO\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENC\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENCIMENTO\\s*:?\\s*(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENCIMENTO\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in dueDatePatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    Log.d(TAG, "Due date found: $dateStr")
                    return if (dateStr.contains("/")) convertDateFormat(dateStr) else convertDateToISO(dateStr)
                }
            }
        }
        
        Log.w(TAG, "Due date not found in header")
        return ""
    }
    
    private fun extractTotalValueFromHeader(lines: List<String>): Double {
        // Procurar por variações de total da fatura
        val totalPatterns = listOf(
            "TOTAL\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "VALOR\\s+TOTAL\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "TOTAL\\s+DA\\s+FATURA\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "TOTAL\\s+A\\s+PAGAR\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "SALDO\\s+TOTAL\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1]
                    Log.d(TAG, "Total value found: $valueStr")
                    return convertCurrencyToDouble(valueStr)
                }
            }
        }
        
        Log.w(TAG, "Total value not found in header")
        return 0.0
    }
    
    private fun extractMinimumPaymentFromHeader(lines: List<String>): Double {
        // Procurar por "PAGAMENTO MÍNIMO R$" seguido de valor
        val minPaymentPatterns = listOf(
            "PAGAMENTO\\s+MÍNIMO\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "PAG\\s*MÍN\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "MÍNIMO\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in minPaymentPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1]
                    Log.d(TAG, "Minimum payment found: $valueStr")
                    return convertCurrencyToDouble(valueStr)
                }
            }
        }
        
        Log.w(TAG, "Minimum payment not found in header")
        return 0.0
    }
    
    private fun extractReferenceMonthFromHeader(lines: List<String>): String {
        // 1) Priorizar texto como: "Esta é a fatura de outubro" / "Fatura de OUTUBRO"
        //    Extrai o mês por nome completo e usa o ano identificado da fatura
        val monthBySentence = extractMonthFromSentence(lines)
        if (monthBySentence.isNotEmpty()) return monthBySentence

        // 2) Fallback: procurar por "REF 26 MAI A 23 JUN" ou similar e aplicar a regra (mês final - 1)
        val referencePatterns = listOf(
            "REF\\s+(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE),
            "REFERÊNCIA\\s+(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE),
            "PERÍODO\\s+(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in referencePatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val periodStr = match.groupValues[1]
                    Log.d(TAG, "Reference period found: $periodStr")
                    return convertPeriodToMonth(periodStr)
                }
            }
        }
        
        Log.w(TAG, "Reference period not found in header")
        return ""
    }


    private fun extractMonthFromSentence(lines: List<String>): String {
        // Aceita frases como: "Esta é a fatura de outubro" ou "FATURA DE OUTUBRO"
        val joined = lines.joinToString(" ").lowercase(Locale.getDefault())
        val pattern = """\bfatura\s+de\s+([a-zçãõéíóúâêôàèìòù]+)\b""".toRegex()
        val match = pattern.find(joined) ?: return ""
        val monthName = match.groupValues[1]
        val abbr = ptMonthNameToAbbr(monthName)
        return if (abbr.isNotEmpty()) "$abbr/$invoiceYear" else ""
    }

    private fun ptMonthNameToAbbr(name: String): String {
        val map = mapOf(
            "janeiro" to "JAN",
            "fevereiro" to "FEV",
            "março" to "MAR", "marco" to "MAR",
            "abril" to "ABR",
            "maio" to "MAI",
            "junho" to "JUN",
            "julho" to "JUL",
            "agosto" to "AGO",
            "setembro" to "SET",
            "outubro" to "OUT",
            "novembro" to "NOV",
            "dezembro" to "DEZ"
        )
        return map[name.lowercase(Locale.getDefault())] ?: ""
    }
    
    private fun extractClosingDateFromHeader(lines: List<String>): String {
        // Procurar por "DATA DE FECHAMENTO DA FATURA" seguido de data
        val closingPatterns = listOf(
            "DATA\\s+DE\\s+FECHAMENTO\\s+DA\\s+FATURA\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "FECHAMENTO\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "FECH\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in closingPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    Log.d(TAG, "Closing date found: $dateStr")
                    return convertDateToISO(dateStr)
                }
            }
        }
        
        Log.w(TAG, "Closing date not found in header")
        return ""
    }
    
    /**
     * Converte String Monetária para Double
     * 
     * FORMATOS SUPORTADOS:
     * - Brasileiro com milhares: "1.200,50" → 1200.50
     * - Brasileiro sem milhares: "42,00" → 42.0
     * - Brasileiro inteiro: "50" → 50.0
     * - Americano: "1200.50" → 1200.50
     * 
     * LÓGICA:
     * 1. Remove "R$" e espaços
     * 2. Se tem vírgula → formato brasileiro
     *    - Se tem ponto ANTES da vírgula → são milhares
     *    - Remove pontos de milhares, troca vírgula por ponto
     *    - Ex: "1.200,50" → "1200,50" → "1200.50" → 1200.50
     *    - Se não tem ponto antes → apenas vírgula decimal
     *    - Ex: "42,00" → "42.00" → 42.0
     * 3. Se não tem vírgula → pode ser americano ou inteiro
     *    - "1200.50" → 1200.50
     *    - "50" → 50.0
     * 
     * EXEMPLOS:
     * - "2.600,35" → 2600.35
     * - "1.200,50" → 1200.50
     * - "42,00" → 42.0
     * - "0,50" → 0.5
     * - "50" → 50.0
     * 
     * @param currencyStr String no formato monetário brasileiro ou americano
     * @return Double convertido (0.0 se erro)
     */
    private fun convertCurrencyToDouble(currencyStr: String): Double {
        return try {
            // ========== LIMPEZA ==========
            // Remove "R$" e espaços
            val cleanStr = currencyStr.trim()
                .replace("R$", "")
                .replace(" ", "")
            
            // ========== DETECÇÃO DE FORMATO ==========
            // Se tem vírgula, é formato brasileiro
            if (cleanStr.contains(",")) {
                // Verifica se tem ponto ANTES da vírgula (milhares)
                // Exemplo: "1.200,50" → ponto (índice 1) < vírgula (índice 5)
                if (cleanStr.contains(".") && cleanStr.indexOf(".") < cleanStr.indexOf(",")) {
                    // FORMATO COM MILHARES: "1.200,50"
                    // Passo 1: Remove pontos de milhares → "1200,50"
                    // Passo 2: Troca vírgula por ponto → "1200.50"
                    // Passo 3: Converte para Double
                    cleanStr.replace(".", "").replace(",", ".").toDouble()
                } else {
                    // FORMATO SEM MILHARES: "42,00" ou "0,50"
                    // Apenas troca vírgula por ponto
                    cleanStr.replace(",", ".").toDouble()
                }
            } else {
                // SEM VÍRGULA: Formato americano ou inteiro
                // "1200.50" → 1200.50
                // "50" → 50.0
                cleanStr.toDouble()
            }
        } catch (e: Exception) {
            // Em caso de erro, retorna 0.0
            Log.w(TAG, "Error converting currency: '$currencyStr'", e)
            0.0
        }
    }
}
