package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents spending data for a specific category
 * Used for dashboard visualizations and reports
 */
@Parcelize
data class CategorySpending(
    val categoryName: String,
    val totalValue: Double,
    val percentage: Double,
    val color: String, // Hex color code (e.g., "#FF5722")
    val expenseCount: Int = 0
) : Parcelable {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "categoryName" to categoryName,
            "totalValue" to totalValue,
            "percentage" to percentage,
            "color" to color,
            "expenseCount" to expenseCount
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): CategorySpending {
            return CategorySpending(
                categoryName = map["categoryName"] as? String ?: "",
                totalValue = (map["totalValue"] as? Number)?.toDouble() ?: 0.0,
                percentage = (map["percentage"] as? Number)?.toDouble() ?: 0.0,
                color = map["color"] as? String ?: "#000000",
                expenseCount = (map["expenseCount"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

