package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Aggregates all data needed for the Dashboard screen
 * Contains invoice summary, spending breakdown, goals, and insights
 */
@Parcelize
data class DashboardData(
    val currentInvoice: Invoice?,
    val invoiceCountdown: InvoiceCountdown?,
    val categorySpending: List<CategorySpending> = emptyList(),
    val activeGoals: List<Goal> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val totalSpentThisMonth: Double = 0.0,
    val totalSpentLastMonth: Double = 0.0,
    val monthOverMonthChange: Double = 0.0 // Percentage change
) : Parcelable {
    val hasInvoice: Boolean
        get() = currentInvoice != null

    val hasGoals: Boolean
        get() = activeGoals.isNotEmpty()

    val hasInsights: Boolean
        get() = insights.isNotEmpty()

    val spendingIncreased: Boolean
        get() = monthOverMonthChange > 0

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "currentInvoice" to currentInvoice?.toMap(),
            "invoiceCountdown" to invoiceCountdown?.toMap(),
            "categorySpending" to categorySpending.map { it.toMap() },
            "activeGoals" to activeGoals.map { it.toMap() },
            "insights" to insights.map { it.toMap() },
            "totalSpentThisMonth" to totalSpentThisMonth,
            "totalSpentLastMonth" to totalSpentLastMonth,
            "monthOverMonthChange" to monthOverMonthChange
        )
    }
}

/**
 * Countdown information for the next invoice due date
 */
@Parcelize
data class InvoiceCountdown(
    val dueDate: String, // ISO format date
    val daysRemaining: Int,
    val isOverdue: Boolean = false,
    val formattedDueDate: String = "" // User-friendly format (e.g., "03/07/2025")
) : Parcelable {
    val isUrgent: Boolean
        get() = daysRemaining <= 3 && !isOverdue

    val statusMessage: String
        get() = when {
            isOverdue -> "Vencida há ${-daysRemaining} dias"
            daysRemaining == 0 -> "Vence hoje"
            daysRemaining == 1 -> "Vence amanhã"
            isUrgent -> "Vence em $daysRemaining dias"
            else -> "Vence em $daysRemaining dias"
        }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "dueDate" to dueDate,
            "daysRemaining" to daysRemaining,
            "isOverdue" to isOverdue,
            "formattedDueDate" to formattedDueDate
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): InvoiceCountdown {
            return InvoiceCountdown(
                dueDate = map["dueDate"] as? String ?: "",
                daysRemaining = (map["daysRemaining"] as? Number)?.toInt() ?: 0,
                isOverdue = map["isOverdue"] as? Boolean ?: false,
                formattedDueDate = map["formattedDueDate"] as? String ?: ""
            )
        }
    }
}

