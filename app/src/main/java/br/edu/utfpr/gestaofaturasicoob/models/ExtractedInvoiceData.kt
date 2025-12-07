package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: ExtractedInvoiceData
 * Temporary data structure for invoice extraction (before categorization)
 */
@Parcelize
data class ExtractedInvoiceData(
    val dueDate: String,
    val totalValue: Double,
    val minimumPayment: Double,
    val referenceMonth: String,
    val closingDate: String,
    val expenses: List<ExtractedExpenseData>
) : Parcelable

/**
 * Model: ExtractedExpenseData
 * Temporary data structure for expense extraction (before categorization)
 */
@Parcelize
data class ExtractedExpenseData(
    val date: String,
    val description: String,
    val establishment: String,
    val city: String,
    val value: Double,
    val installment: String?
) : Parcelable {
    
    /**
     * Convert to categorizable expense
     */
    fun toExpense(category: String? = null, autoCategorized: Boolean = false): Expense {
        return Expense(
            id = "",
            date = date,
            description = description,
            establishment = establishment,
            city = city,
            value = value,
            category = category,
            installment = installment,
            isInstallment = installment != null,
            autoCategorized = autoCategorized,
            createdAt = java.time.Instant.now().toString()
        )
    }
}

/**
 * Model: ExpenseWithCategory
 * Helper for categorization UI
 */
@Parcelize
data class ExpenseWithCategory(
    val expense: ExtractedExpenseData,
    val category: String? = null,
    val isAutoCategorized: Boolean = false,
    val isCategorized: Boolean = false
) : Parcelable {
    
    fun needsManualCategorization(): Boolean = !isCategorized || category == null
}

