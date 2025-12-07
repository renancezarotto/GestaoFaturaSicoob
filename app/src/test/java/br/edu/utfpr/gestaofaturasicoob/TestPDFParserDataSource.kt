package br.edu.utfpr.gestaofaturasicoob

import br.edu.utfpr.gestaofaturasicoob.domain.entities.ExtractedExpense
import br.edu.utfpr.gestaofaturasicoob.domain.entities.ExtractedInvoice
import br.edu.utfpr.gestaofaturasicoob.domain.entities.InvoiceHeader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object TestPDFParserDataSource {
    
    fun parsePDF(file: File): Result<ExtractedInvoice> {
        var document: PDDocument? = null
        try {
            if (!file.name.endsWith(".pdf", true)) {
                return Result.failure(Exception("Arquivo não é PDF válido"))
            }
            
            document = PDDocument.load(file)
            
            if (document.numberOfPages == 0) {
                return Result.failure(Exception("PDF está vazio"))
            }
            
            val textStripper = PDFTextStripper()
            textStripper.startPage = 1
            textStripper.endPage = document.numberOfPages
            val text = textStripper.getText(document)
            
            val extractedInvoice = parseInvoiceText(text)
            
            return Result.success(extractedInvoice)
            
        } catch (e: Exception) {
            return Result.failure(Exception("Erro ao processar PDF: ${e.message}"))
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                // Ignore close errors in tests
            }
        }
    }
    
    private fun parseInvoiceText(text: String): ExtractedInvoice {
        val lines = text.split("\n").map { it.trim() }
        
        // Extrair cabeçalho da fatura
        val dueDate = extractDueDate(lines)
        val totalValue = extractTotalValue(lines)
        val minimumPayment = extractMinimumPayment(lines)
        val referenceMonth = extractReferenceMonth(lines)
        val closingDate = extractClosingDate(lines)
        
        // Extrair despesas
        val expenses = extractExpenses(lines)
        
        return ExtractedInvoice(
            dueDate = dueDate,
            totalValue = totalValue,
            minimumPayment = minimumPayment,
            referenceMonth = referenceMonth,
            closingDate = closingDate,
            expenses = expenses
        )
    }
    
    private fun extractDueDate(lines: List<String>): String {
        val dueDatePatterns = listOf(
            "VENCIMENTO\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENC\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENCIMENTO\\s*:?\\s*(\\d{2}-\\d{2}-\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in dueDatePatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    return convertDateFormat(dateStr)
                }
            }
        }
        
        return ""
    }
    
    private fun extractTotalValue(lines: List<String>): Double {
        val totalPatterns = listOf(
            "TOTAL\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE),
            "VALOR\\s*TOTAL\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE),
            "TOTAL\\s*GERAL\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1].replace(",", ".")
                    return valueStr.toDoubleOrNull() ?: 0.0
                }
            }
        }
        
        return 0.0
    }
    
    private fun extractMinimumPayment(lines: List<String>): Double {
        val minPaymentPatterns = listOf(
            "PAGAMENTO\\s*MÍNIMO\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE),
            "PAG\\s*MÍN\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE),
            "MÍNIMO\\s*:?\\s*R\\$\\s*([\\d.,]+)".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in minPaymentPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1].replace(",", ".")
                    return valueStr.toDoubleOrNull() ?: 0.0
                }
            }
        }
        
        return 0.0
    }
    
    private fun extractReferenceMonth(lines: List<String>): String {
        val referencePatterns = listOf(
            "PERÍODO\\s*:?\\s*(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE),
            "REFERÊNCIA\\s*:?\\s*(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE),
            "(\\d{2}\\s+[A-Z]{3}\\s+A\\s+\\d{2}\\s+[A-Z]{3})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in referencePatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val periodStr = match.groupValues[1]
                    return convertPeriodToMonth(periodStr)
                }
            }
        }
        
        return ""
    }
    
    private fun extractClosingDate(lines: List<String>): String {
        val closingPatterns = listOf(
            "FECHAMENTO\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "FECH\\s*:?\\s*(\\d{2}/\\d{2}/\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "FECHAMENTO\\s*:?\\s*(\\d{2}-\\d{2}-\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in closingPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    return convertDateFormat(dateStr)
                }
            }
        }
        
        return ""
    }
    
    private fun extractExpenses(lines: List<String>): List<ExtractedExpense> {
        val expenses = mutableListOf<ExtractedExpense>()
        var inExpensesSection = false
        
        for (line in lines) {
            // Detectar início da seção de lançamentos - formato específico Sicoob
            if (line.contains("LANÇAMENTOS - SICOOB MASTERCARD", ignoreCase = true) || 
                line.contains("LANÇAMENTOS", ignoreCase = true) || 
                line.contains("COMPRAS", ignoreCase = true) ||
                line.contains("MOVIMENTAÇÃO", ignoreCase = true)) {
                inExpensesSection = true
                continue
            }
            
            // Detectar fim da seção de despesas - parar no "TOTAL R$"
            if (inExpensesSection && line.contains("TOTAL R$", ignoreCase = true)) {
                break
            }
            
            // Também parar em outros indicadores de fim
            if (inExpensesSection && (
                line.contains("RESUMO", ignoreCase = true) ||
                line.contains("PAGAMENTO", ignoreCase = true) ||
                line.contains("CRÉDITO", ignoreCase = true)
            )) {
                break
            }
            
            if (inExpensesSection) {
                val expense = parseExpenseLine(line)
                if (expense != null) {
                    expenses.add(expense)
                }
            }
        }
        
        return expenses
    }
    
        private fun parseExpenseLine(line: String): ExtractedExpense? {
            // Regex específico para formato Sicoob: "08 MAI NEICAR 02/03 CORONEL VIVID R$ 148,00"
            // Primeiro tenta capturar com parcela no meio
            val expenseWithInstallmentRegex = """(\d{2}\s+[A-Z]{3})\s+([A-Z\s\*\-]+?)\s+(\d{2}/\d{2})\s+([A-Z\s]+)\s+R\$\s+([\d\.,]+)""".toRegex()
            val expenseWithoutInstallmentRegex = """(\d{2}\s+[A-Z]{3})\s+([A-Z\s\*\-]+)\s+([A-Z\s]+)\s+R\$\s+([\d\.,]+)""".toRegex()
        
        // Tentar primeiro com parcela
        var match = expenseWithInstallmentRegex.find(line)
        var hasInstallment = true
        
        if (match == null) {
            // Se não encontrou com parcela, tenta sem
            match = expenseWithoutInstallmentRegex.find(line)
            hasInstallment = false
        }
        
        if (match != null) {
            val dateStr = match.groupValues[1].trim()
            val establishment = match.groupValues[2].trim()
            val installment = if (hasInstallment) match.groupValues[3] else null
            val city = if (hasInstallment) match.groupValues[4].trim() else match.groupValues[3].trim()
            val valueStr = if (hasInstallment) match.groupValues[5] else match.groupValues[4]
            
            // Ignorar linhas com valores negativos (pagamentos/créditos)
            val value = convertCurrencyToDouble(valueStr)
            if (value < 0) {
                return null
            }
            
            // Ignorar linhas com PAGAMENTO, CREDITO, -R$
            if (line.contains("PAGAMENTO", ignoreCase = true) ||
                line.contains("CREDITO", ignoreCase = true) ||
                line.contains("-R$", ignoreCase = true)) {
                return null
            }
            
            // Tratar tarifas como despesas especiais (não ignorar mais)
            val isCardFee = establishment.contains("ANUIDADE", ignoreCase = true) ||
                           establishment.contains("PROTEÇÃO", ignoreCase = true) ||
                           establishment.contains("TAXA", ignoreCase = true)
            
            return ExtractedExpense(
                date = convertDateToISO(dateStr),
                description = establishment,
                establishment = establishment,
                city = city,
                value = value,
                installment = installment
            )
        }
        
        return null
    }
    
    private fun convertDateFormat(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateStr
        }
    }
    
    private fun convertPeriodToMonth(periodStr: String): String {
        return try {
            val parts = periodStr.split("\\s+A\\s+".toRegex())
            if (parts.size >= 2) {
                val endPart = parts[1].trim()
                val monthYear = endPart.split("\\s+".toRegex())
                if (monthYear.size >= 2) {
                    val month = monthYear[1]
                    val year = Calendar.getInstance().get(Calendar.YEAR)
                    return "$month/$year"
                }
            }
            periodStr
        } catch (e: Exception) {
            periodStr
        }
    }
    
    private fun convertDateToISO(dateStr: String): String {
        return try {
            val parts = dateStr.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val day = parts[0].padStart(2, '0')
                val month = convertMonthAbbreviation(parts[1])
                val year = Calendar.getInstance().get(Calendar.YEAR)
                return "$year-$month-$day"
            }
            dateStr
        } catch (e: Exception) {
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
     * Parse invoice header information from PDF text
     * Based on Sicoob credit card invoice format
     */
    fun parseHeader(text: String): InvoiceHeader {
        val lines = text.split("\n").map { it.trim() }
        
        val dueDate = extractDueDateFromHeader(lines)
        val totalValue = extractTotalValueFromHeader(lines)
        val minimumPayment = extractMinimumPaymentFromHeader(lines)
        val referenceMonth = extractReferenceMonthFromHeader(lines)
        val closingDate = extractClosingDateFromHeader(lines)
        
        return InvoiceHeader(
            dueDate = dueDate,
            totalValue = totalValue,
            minimumPayment = minimumPayment,
            referenceMonth = referenceMonth,
            closingDate = closingDate
        )
    }
    
    private fun extractDueDateFromHeader(lines: List<String>): String {
        val dueDatePatterns = listOf(
            "VENCIMENTO\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENC\\s+(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE),
            "VENCIMENTO\\s*:?\\s*(\\d{1,2}\\s+[A-Z]{3}\\s+\\d{4})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in dueDatePatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val dateStr = match.groupValues[1]
                    return convertDateToISO(dateStr)
                }
            }
        }
        
        return ""
    }
    
    private fun extractTotalValueFromHeader(lines: List<String>): Double {
        val totalPatterns = listOf(
            "TOTAL\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE),
            "VALOR\\s+TOTAL\\s+R\\$\\s*([\\d\\.]+,\\d{2})".toRegex(RegexOption.IGNORE_CASE)
        )
        
        for (line in lines) {
            for (pattern in totalPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val valueStr = match.groupValues[1]
                    return convertCurrencyToDouble(valueStr)
                }
            }
        }
        
        return 0.0
    }
    
    private fun extractMinimumPaymentFromHeader(lines: List<String>): Double {
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
                    return convertCurrencyToDouble(valueStr)
                }
            }
        }
        
        return 0.0
    }
    
    private fun extractReferenceMonthFromHeader(lines: List<String>): String {
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
                    return convertPeriodToMonth(periodStr)
                }
            }
        }
        
        return ""
    }
    
    private fun extractClosingDateFromHeader(lines: List<String>): String {
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
                    return convertDateToISO(dateStr)
                }
            }
        }
        
        return ""
    }
    
    /**
     * Convert currency string "2.600,35" to Double 2600.35
     * Handles formats like: "0,50", "1.200,50", "50,00", "1.000,00"
     */
    private fun convertCurrencyToDouble(currencyStr: String): Double {
        return try {
            val cleanStr = currencyStr.trim()
                .replace("R$", "")
                .replace(" ", "")
            
            // Se tem vírgula, é formato brasileiro (1.200,50)
            if (cleanStr.contains(",")) {
                // Se tem ponto antes da vírgula, é milhares (1.200,50)
                if (cleanStr.contains(".") && cleanStr.indexOf(".") < cleanStr.indexOf(",")) {
                    // Remove pontos de milhares e substitui vírgula por ponto
                    cleanStr.replace(".", "").replace(",", ".").toDouble()
                } else {
                    // Apenas vírgula decimal (0,50)
                    cleanStr.replace(",", ".").toDouble()
                }
            } else {
                // Sem vírgula, pode ser formato americano (0.50) ou inteiro (50)
                cleanStr.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }
    
    /**
     * Parse expenses from PDF text
     * Based on Sicoob credit card invoice format
     */
    fun parseExpenses(text: String): List<ExtractedExpense> {
        val lines = text.split("\n").map { it.trim() }
        val expenses = mutableListOf<ExtractedExpense>()
        var inExpensesSection = false
        
        for (line in lines) {
            // Detectar início da seção de lançamentos - formato específico Sicoob
            if (line.contains("LANÇAMENTOS - SICOOB MASTERCARD", ignoreCase = true) || 
                line.contains("LANÇAMENTOS", ignoreCase = true)) {
                inExpensesSection = true
                continue
            }
            
            // Detectar fim da seção de despesas - parar no "TOTAL R$"
            if (inExpensesSection && line.contains("TOTAL R$", ignoreCase = true)) {
                break
            }
            
            if (inExpensesSection) {
                val expense = parseExpenseLine(line)
                if (expense != null) {
                    expenses.add(expense)
                }
            }
        }
        
        return expenses
    }
    
}
