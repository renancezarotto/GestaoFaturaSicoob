package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: Category
 * Represents an expense category
 */
@Parcelize
data class Category(
    val id: String = "",
    val name: String = "",
    val color: String = "#9E9E9E",
    val isRecurring: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: String = ""
) : Parcelable {
    
    companion object {
        /**
         * Default categories - Sem cores individuais
         */
        val DEFAULT_CATEGORIES = listOf(
            Category("cat_food", "Alimentação", "#FF5722", isRecurring = true, isDefault = true),
            Category("cat_transport", "Transporte", "#2196F3", isRecurring = true, isDefault = true),
            Category("cat_health", "Saúde", "#4CAF50", isRecurring = false, isDefault = true),
            Category("cat_leisure", "Lazer", "#FFC107", isRecurring = false, isDefault = true),
            Category("cat_education", "Educação", "#9C27B0", isRecurring = false, isDefault = true),
            Category("cat_housing", "Moradia", "#00BCD4", isRecurring = true, isDefault = true),
            Category("cat_clothing", "Vestuário", "#E91E63", isRecurring = false, isDefault = true),
            Category("cat_fuel", "Combustível", "#FF9800", isRecurring = true, isDefault = true),
            Category("cat_grocery", "Mercado", "#8BC34A", isRecurring = true, isDefault = true),
            Category("cat_restaurant", "Restaurantes", "#F44336", isRecurring = false, isDefault = true),
            Category("cat_fees", "Taxas Cartão", "#607D8B", isRecurring = false, isDefault = true),
            Category("cat_other", "Outros", "#9E9E9E", isRecurring = false, isDefault = true)
        )
        
        fun fromMap(map: Map<String, Any?>): Category {
            return Category(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                color = map["color"] as? String ?: "#9E9E9E",
                isRecurring = map["isRecurring"] as? Boolean ?: false,
                isDefault = map["isDefault"] as? Boolean ?: false,
                createdAt = map["createdAt"] as? String ?: ""
            )
        }
    }
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "color" to color,
            "isRecurring" to isRecurring,
            "isDefault" to isDefault,
            "createdAt" to createdAt
        )
    }
}

