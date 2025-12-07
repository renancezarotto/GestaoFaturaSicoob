package br.edu.utfpr.gestaofaturasicoob

import br.edu.utfpr.gestaofaturasicoob.domain.entities.ExtractedExpense
import br.edu.utfpr.gestaofaturasicoob.domain.entities.ExtractedInvoice
import br.edu.utfpr.gestaofaturasicoob.domain.entities.InvoiceHeader
import org.junit.Test
import org.junit.Assert.*

class PDFParserTest {
    
    @Test
    fun `test parsePDF with invalid file extension`() {
        // Given
        val invalidFile = java.io.File("test.txt")
        
        // When
        val result = TestPDFParserDataSource.parsePDF(invalidFile)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("não é PDF válido") == true)
    }
    
    @Test
    fun `test parsePDF with non-existent file`() {
        // Given
        val nonExistentFile = java.io.File("non_existent.pdf")
        
        // When
        val result = TestPDFParserDataSource.parsePDF(nonExistentFile)
        
        // Then
        assertTrue(result.isFailure)
        // O PDFBox pode lançar diferentes tipos de exceções para arquivos inexistentes
        val exceptionMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Expected error message about PDF processing, but got: $exceptionMessage",
            exceptionMessage.contains("Erro ao processar PDF") || 
            exceptionMessage.contains("não é PDF válido") ||
            exceptionMessage.contains("FileNotFoundException") ||
            exceptionMessage.contains("IOException")
        )
    }
    
    @Test
    fun `test ExtractedInvoice data class`() {
        // Given
        val expenses = listOf(
            ExtractedExpense(
                date = "2025-05-24",
                description = "CAFE DA ANA",
                establishment = "CAFE DA ANA",
                city = "CORONEL VIVID",
                value = 42.00,
                installment = null
            )
        )
        
        // When
        val invoice = ExtractedInvoice(
            dueDate = "2025-07-03",
            totalValue = 2600.35,
            minimumPayment = 418.86,
            referenceMonth = "JUN/2025",
            closingDate = "2025-06-23",
            expenses = expenses
        )
        
        // Then
        assertEquals("2025-07-03", invoice.dueDate)
        assertEquals(2600.35, invoice.totalValue, 0.01)
        assertEquals(418.86, invoice.minimumPayment, 0.01)
        assertEquals("JUN/2025", invoice.referenceMonth)
        assertEquals("2025-06-23", invoice.closingDate)
        assertEquals(1, invoice.expenses.size)
        assertEquals("CAFE DA ANA", invoice.expenses[0].establishment)
        assertEquals(42.00, invoice.expenses[0].value, 0.01)
    }
    
    @Test
    fun `test ExtractedExpense data class`() {
        // Given & When
        val expense = ExtractedExpense(
            date = "2025-05-24",
            description = "CAFE DA ANA",
            establishment = "CAFE DA ANA",
            city = "CORONEL VIVID",
            value = 42.00,
            installment = "03/04"
        )
        
        // Then
        assertEquals("2025-05-24", expense.date)
        assertEquals("CAFE DA ANA", expense.description)
        assertEquals("CAFE DA ANA", expense.establishment)
        assertEquals("CORONEL VIVID", expense.city)
        assertEquals(42.00, expense.value, 0.01)
        assertEquals("03/04", expense.installment)
    }
    
    @Test
    fun `test ExtractedExpense without installment`() {
        // Given & When
        val expense = ExtractedExpense(
            date = "2025-05-24",
            description = "CAFE DA ANA",
            establishment = "CAFE DA ANA",
            city = "CORONEL VIVID",
            value = 42.00,
            installment = null
        )
        
        // Then
        assertEquals("2025-05-24", expense.date)
        assertEquals("CAFE DA ANA", expense.description)
        assertEquals("CAFE DA ANA", expense.establishment)
        assertEquals("CORONEL VIVID", expense.city)
        assertEquals(42.00, expense.value, 0.01)
        assertNull(expense.installment)
    }
    
    @Test
    fun `test InvoiceHeader data class`() {
        // Given & When
        val header = InvoiceHeader(
            dueDate = "2025-07-03",
            totalValue = 2600.35,
            minimumPayment = 418.86,
            referenceMonth = "JUN/2025",
            closingDate = "2025-06-23"
        )
        
        // Then
        assertEquals("2025-07-03", header.dueDate)
        assertEquals(2600.35, header.totalValue, 0.01)
        assertEquals(418.86, header.minimumPayment, 0.01)
        assertEquals("JUN/2025", header.referenceMonth)
        assertEquals("2025-06-23", header.closingDate)
    }
    
    @Test
    fun `test parseHeader with Sicoob format`() {
        // Given - Simulando texto de fatura Sicoob
        val sicoobInvoiceText = """
            FATURA DE CARTÃO DE CRÉDITO
            VENCIMENTO 3 JUL 2025
            TOTAL R$ 2.600,35
            PAGAMENTO MÍNIMO R$ 418,86
            REF 26 MAI A 23 JUN
            DATA DE FECHAMENTO DA FATURA 23 JUN 2025
            LANÇAMENTOS
            24 MAI CAFE DA ANA CORONEL VIVID R$ 42,00
            25 MAI AB SUPERMERCADOS LTD CORONEL VIVID R$ 156,78
        """.trimIndent()
        
        // When
        val header = TestPDFParserDataSource.parseHeader(sicoobInvoiceText)
        
        // Then
        assertEquals("2025-07-03", header.dueDate)
        assertEquals(2600.35, header.totalValue, 0.01)
        assertEquals(418.86, header.minimumPayment, 0.01)
        assertEquals("JUN/2025", header.referenceMonth)
        assertEquals("2025-06-23", header.closingDate)
    }
    
    @Test
    fun `test parseHeader with alternative formats`() {
        // Given - Testando formatos alternativos
        val alternativeInvoiceText = """
            FATURA DE CARTÃO DE CRÉDITO
            VENC 15 AGO 2025
            VALOR TOTAL R$ 1.500,50
            PAG MÍN R$ 250,00
            REFERÊNCIA 15 JUL A 14 AGO
            FECHAMENTO 14 AGO 2025
        """.trimIndent()
        
        // When
        val header = TestPDFParserDataSource.parseHeader(alternativeInvoiceText)
        
        // Then
        assertEquals("2025-08-15", header.dueDate)
        assertEquals(1500.50, header.totalValue, 0.01)
        assertEquals(250.00, header.minimumPayment, 0.01)
        assertEquals("AGO/2025", header.referenceMonth)
        assertEquals("2025-08-14", header.closingDate)
    }
    
    @Test
    fun `test parseHeader with missing fields`() {
        // Given - Texto com campos faltando
        val incompleteInvoiceText = """
            FATURA DE CARTÃO DE CRÉDITO
            VENCIMENTO 3 JUL 2025
            TOTAL R$ 2.600,35
            LANÇAMENTOS
        """.trimIndent()
        
        // When
        val header = TestPDFParserDataSource.parseHeader(incompleteInvoiceText)
        
        // Then
        assertEquals("2025-07-03", header.dueDate)
        assertEquals(2600.35, header.totalValue, 0.01)
        assertEquals(0.0, header.minimumPayment, 0.01) // Campo não encontrado
        assertEquals("", header.referenceMonth) // Campo não encontrado
        assertEquals("", header.closingDate) // Campo não encontrado
    }
    
    @Test
    fun `test parseExpenses with Sicoob format`() {
        // Given - Simulando texto de fatura Sicoob com despesas
        val sicoobInvoiceText = """
            FATURA DE CARTÃO DE CRÉDITO
            VENCIMENTO 3 JUL 2025
            TOTAL R$ 2.600,35
            LANÇAMENTOS - SICOOB MASTERCARD
            08 MAI NEICAR 02/03 CORONEL VIVID R$ 148,00
            10 MAI DECATHLON 03/04 CORONEL VIVID R$ 89,50
            12 MAI CAFE DA ANA CORONEL VIVID R$ 42,00
            15 MAI ANUIDADE MASTERCARD CORONEL VIVID R$ 45,00
            18 MAI AB SUPERMERCADOS LTD CORONEL VIVID R$ 156,78
            TOTAL R$ 2.600,35
        """.trimIndent()
        
        // When
        val expenses = TestPDFParserDataSource.parseExpenses(sicoobInvoiceText)
        
        // Then
        assertEquals(5, expenses.size)
        
        // Verificar primeira despesa com parcela
        val firstExpense = expenses[0]
        assertEquals("2025-05-08", firstExpense.date)
        assertEquals("NEICAR", firstExpense.establishment)
        assertEquals("CORONEL VIVID", firstExpense.city)
        assertEquals(148.00, firstExpense.value, 0.01)
        assertEquals("02/03", firstExpense.installment)
        
        // Verificar segunda despesa com parcela
        val secondExpense = expenses[1]
        assertEquals("2025-05-10", secondExpense.date)
        assertEquals("DECATHLON", secondExpense.establishment)
        assertEquals("CORONEL VIVID", secondExpense.city)
        assertEquals(89.50, secondExpense.value, 0.01)
        assertEquals("03/04", secondExpense.installment)
        
        // Verificar terceira despesa sem parcela
        val thirdExpense = expenses[2]
        assertEquals("2025-05-12", thirdExpense.date)
        assertEquals("CAFE DA ANA", thirdExpense.establishment)
        assertEquals("CORONEL VIVID", thirdExpense.city)
        assertEquals(42.00, thirdExpense.value, 0.01)
        assertNull(thirdExpense.installment)
        
        // Verificar tarifa (deve ser incluída)
        val fourthExpense = expenses[3]
        assertEquals("2025-05-15", fourthExpense.date)
        assertEquals("ANUIDADE MASTERCARD", fourthExpense.establishment)
        assertEquals("CORONEL VIVID", fourthExpense.city)
        assertEquals(45.00, fourthExpense.value, 0.01)
        assertNull(fourthExpense.installment)
    }
    
    @Test
    fun `test parseExpenses with installment detection`() {
        // Given - Testando detecção de parcelas no nome do estabelecimento
        val invoiceText = """
            LANÇAMENTOS - SICOOB MASTERCARD
            20 MAI LOJA AMERICANA 01/06 CORONEL VIVID R$ 299,90
            22 MAI POSTO SHELL CORONEL VIVID R$ 89,50
            25 MAI MAGAZINE LUIZA 05/10 CORONEL VIVID R$ 450,00
            TOTAL R$ 839,40
        """.trimIndent()
        
        // When
        val expenses = TestPDFParserDataSource.parseExpenses(invoiceText)
        
        // Then
        assertEquals(3, expenses.size)
        
        // Verificar primeira despesa com parcela
        assertEquals("LOJA AMERICANA", expenses[0].establishment)
        assertEquals("01/06", expenses[0].installment)
        
        // Verificar segunda despesa sem parcela
        assertEquals("POSTO SHELL", expenses[1].establishment)
        assertNull(expenses[1].installment)
        
        // Verificar terceira despesa com parcela
        assertEquals("MAGAZINE LUIZA", expenses[2].establishment)
        assertEquals("05/10", expenses[2].installment)
    }
    
    @Test
    fun `test parseExpenses ignores payments and credits`() {
        // Given - Testando que pagamentos e créditos são ignorados
        val invoiceText = """
            LANÇAMENTOS - SICOOB MASTERCARD
            10 MAI CAFE DA ANA CORONEL VIVID R$ 42,00
            PAGAMENTO RECEBIDO R$ 2.600,35
            12 MAI AB SUPERMERCADOS LTD CORONEL VIVID R$ 156,78
            CREDITO APLICADO R$ 50,00
            15 MAI POSTO SHELL CORONEL VIVID R$ 89,50
            TOTAL R$ 2.600,35
        """.trimIndent()
        
        // When
        val expenses = TestPDFParserDataSource.parseExpenses(invoiceText)
        
        // Then
        assertEquals(3, expenses.size) // Apenas as despesas, não os pagamentos/créditos
        
        // Verificar que não há despesas com PAGAMENTO ou CREDITO
        expenses.forEach { expense ->
            assertFalse(expense.establishment.contains("PAGAMENTO", ignoreCase = true))
            assertFalse(expense.establishment.contains("CREDITO", ignoreCase = true))
        }
    }
    
    @Test
    fun `test parseExpenses with card fees`() {
        // Given - Testando tarifas do cartão
        val invoiceText = """
            LANÇAMENTOS - SICOOB MASTERCARD
            10 MAI CAFE DA ANA CORONEL VIVID R$ 42,00
            12 MAI ANUIDADE MASTERCARD CORONEL VIVID R$ 45,00
            15 MAI PROTEÇÃO PERDA OU ROUBO CORONEL VIVID R$ 12,50
            18 MAI AB SUPERMERCADOS LTD CORONEL VIVID R$ 156,78
            TOTAL R$ 256,28
        """.trimIndent()
        
        // When
        val expenses = TestPDFParserDataSource.parseExpenses(invoiceText)
        
        // Then
        assertEquals(4, expenses.size) // Incluindo as tarifas
        
        // Debug: imprimir as despesas encontradas
        expenses.forEach { expense ->
            println("Despesa: '${expense.establishment}' - Valor: ${expense.value}")
        }
        
        // Verificar que as tarifas são incluídas
        val cardFees = expenses.filter { 
            it.establishment.contains("ANUIDADE", ignoreCase = true) ||
            it.establishment.contains("PROTEÇÃO", ignoreCase = true)
        }
        assertEquals(2, cardFees.size)
        
        // Verificar anuidade
        val anuidade = expenses.find { it.establishment.contains("ANUIDADE", ignoreCase = true) }
        assertNotNull(anuidade)
        assertEquals(45.00, anuidade!!.value, 0.01)
        
        // Verificar proteção
        val protecao = expenses.find { it.establishment.contains("PROTEÇÃO", ignoreCase = true) }
        assertNotNull(protecao)
        assertEquals(12.50, protecao!!.value, 0.01)
    }
    
    @Test
    fun `test debug expense parsing`() {
        // Given - Teste simples para debugar
        val invoiceText = """
            LANÇAMENTOS - SICOOB MASTERCARD
            12 MAI CAFE DA ANA CORONEL VIVID R$ 42,00
            TOTAL R$ 42,00
        """.trimIndent()
        
        // When
        val expenses = TestPDFParserDataSource.parseExpenses(invoiceText)
        
        // Then
        assertEquals(1, expenses.size)
        println("Despesa encontrada: '${expenses[0].establishment}'")
        assertEquals("CAFE DA ANA", expenses[0].establishment)
    }
}
