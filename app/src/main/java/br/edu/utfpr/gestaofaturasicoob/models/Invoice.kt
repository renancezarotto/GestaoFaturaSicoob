package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: Invoice
 * Represents a credit card invoice with all expenses
 */
@Parcelize
data class Invoice(
    val id: String = "",
    val userId: String = "",
    val dueDate: String = "",
    val totalValue: Double = 0.0,
    val minimumPayment: Double = 0.0,
    val referenceMonth: String = "",
    val closingDate: String = "",
    val expenses: List<Expense> = emptyList(),
    val uploadedAt: String = "",
    val isPaid: Boolean = false,
    val paidDate: String = "" // Data do pagamento (ISO format)
) : Parcelable {
    
    /**
     * Get expenses grouped by category
     */
    fun getExpensesByCategory(): Map<String, Double> {
        return expenses
            .groupBy { it.category ?: "Não categorizado" }
            .mapValues { (_, exps) -> exps.sumOf { it.value } }
    }
    
    companion object {
        fun fromMap(map: Map<String, Any?>): Invoice {
            val expensesList = (map["expenses"] as? List<*>)?.mapNotNull { 
                (it as? Map<*, *>)?.let { expMap ->
                    @Suppress("UNCHECKED_CAST")
                    Expense.fromMap(expMap as Map<String, Any?>)
                }
            } ?: emptyList()
            
            return Invoice(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                dueDate = map["dueDate"] as? String ?: "",
                totalValue = (map["totalValue"] as? Number)?.toDouble() ?: 0.0,
                minimumPayment = (map["minimumPayment"] as? Number)?.toDouble() ?: 0.0,
                referenceMonth = map["referenceMonth"] as? String ?: "",
                closingDate = map["closingDate"] as? String ?: "",
                expenses = expensesList,
                uploadedAt = map["uploadedAt"] as? String ?: "",
                isPaid = map["isPaid"] as? Boolean ?: false,
                paidDate = map["paidDate"] as? String ?: ""
            )
        }
    }
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "dueDate" to dueDate,
            "totalValue" to totalValue,
            "minimumPayment" to minimumPayment,
            "referenceMonth" to referenceMonth,
            "closingDate" to closingDate,
            "expenses" to expenses.map { it.toMap() },
            "uploadedAt" to uploadedAt,
            "isPaid" to isPaid,
            "paidDate" to paidDate
        )
    }
}

