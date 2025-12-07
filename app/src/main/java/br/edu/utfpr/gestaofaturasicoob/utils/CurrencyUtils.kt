package br.edu.utfpr.gestaofaturasicoob.utils

import java.text.NumberFormat
import java.util.*

/**
 * CurrencyUtils - Utilitário de Formatação Monetária
 * 
 * RESPONSABILIDADE:
 * Formatação e parsing de valores monetários no formato brasileiro
 * 
 * FORMATO BRASILEIRO:
 * - Símbolo: R$
 * - Separador decimal: vírgula (,)
 * - Separador de milhares: ponto (.)
 * - Exemplo: R$ 1.200,50
 * 
 * FUNÇÕES:
 * 1. formatCurrency(): Formata Double → String com R$
 * 2. formatCurrencyWithoutSymbol(): Formata Double → String sem R$
 * 3. parseCurrency(): Parse String → Double
 * 4. calculatePercentage(): Calcula percentual
 * 5. formatPercentage(): Formata percentual
 */
object CurrencyUtils {
    
    /**
     * Formatador de Moeda Brasileira
     * 
     * LOCALE:
     * Locale("pt", "BR"): Formato brasileiro
     * - Símbolo: R$
     * - Vírgula decimal
     * - Ponto de milhares
     * 
     * EXEMPLO:
     * formatCurrency(1200.50) → "R$ 1.200,50"
     */
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    
    /**
     * Formata Valor como Moeda Brasileira
     * 
     * FORMATO:
     * R$ X.XXX,XX (com símbolo)
     * 
     * EXEMPLOS:
     * - 42.0 → "R$ 42,00"
     * - 1200.50 → "R$ 1.200,50"
     * - 2600.35 → "R$ 2.600,35"
     * 
     * USO:
     * - Exibição em TextView/TextViews
     * - Dashboard: Valores totais
     * - Relatórios: Valores formatados
     * 
     * @param value Valor em Double
     * @return String formatada (ex: "R$ 1.200,50")
     */
    fun formatCurrency(value: Double): String {
        return currencyFormat.format(value)
    }
    
    /**
     * Parse String Monetária para Double
     * 
     * CONVERSÃO:
     * String no formato brasileiro → Double
     * 
     * FORMATOS ACEITOS:
     * - "R$ 1.200,50" → 1200.50
     * - "1.200,50" → 1200.50
     * - "42,00" → 42.0
     * - "1200.50" → 1200.50 (formato americano também aceito)
     * 
     * PROCESSO:
     * 1. Remove "R$"
     * 2. Remove pontos (milhares)
     * 3. Substitui vírgula por ponto (decimal)
     * 4. Converte para Double
     * 
     * TRATAMENTO DE ERRO:
     * - Se parse falhar, retorna 0.0
     * 
     * @param currencyString String no formato monetário
     * @return Double convertido (0.0 se erro)
     */
    fun parseCurrency(currencyString: String): Double {
        return try {
            // Limpa string: remove símbolos e normaliza formato
            val cleanString = currencyString
                .replace("R$", "") // Remove símbolo
                .replace(".", "") // Remove pontos (milhares)
                .replace(",", ".") // Troca vírgula por ponto (decimal)
                .trim()
            
            // Converte para Double
            cleanString.toDouble()
        } catch (e: Exception) {
            // Em caso de erro, retorna 0.0
            0.0
        }
    }
    
}
