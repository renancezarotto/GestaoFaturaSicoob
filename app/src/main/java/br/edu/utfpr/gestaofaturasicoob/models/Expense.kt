package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: Expense
 * Represents a single expense from an invoice
 */
@Parcelize
data class Expense(
    val id: String = "",
    val date: String = "",
    val description: String = "",
    val establishment: String = "",
    val city: String = "",
    val value: Double = 0.0,
    val category: String? = null,
    val installment: String? = null,
    val isInstallment: Boolean = false,
    val autoCategorized: Boolean = false,
    val createdAt: String = ""
) : Parcelable {
    
    /**
     * Check if this is a card fee (ANUIDADE, PROTEÇÃO, etc.)
     */
    fun isCardFee(): Boolean {
        val keywords = listOf("ANUIDADE", "PROTEÇÃO", "PERDA", "ROUBO", "TAXA")
        return keywords.any { description.uppercase().contains(it) }
    }
    
    companion object {
        fun fromMap(map: Map<String, Any?>): Expense {
            return Expense(
                id = map["id"] as? String ?: "",
                date = map["date"] as? String ?: "",
                description = map["description"] as? String ?: "",
                establishment = map["establishment"] as? String ?: "",
                city = map["city"] as? String ?: "",
                value = (map["value"] as? Number)?.toDouble() ?: 0.0,
                category = map["category"] as? String,
                installment = map["installment"] as? String,
                isInstallment = map["isInstallment"] as? Boolean ?: false,
                autoCategorized = map["autoCategorized"] as? Boolean ?: false,
                createdAt = map["createdAt"] as? String ?: ""
            )
        }
    }
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "date" to date,
            "description" to description,
            "establishment" to establishment,
            "city" to city,
            "value" to value,
            "category" to category,
            "installment" to installment,
            "isInstallment" to isInstallment,
            "autoCategorized" to autoCategorized,
            "createdAt" to createdAt
        )
    }
}

