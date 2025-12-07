package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: Goal
 * Represents a spending goal for a category
 */
@Parcelize
data class Goal(
    val id: String = "",
    val userId: String = "",
    val category: String = "",
    val limitValue: Double = 0.0,
    val alertAt80: Boolean = true,
    val alertAt100: Boolean = true,
    val monthlyReset: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: String = ""
) : Parcelable {
    
    companion object {
        fun fromMap(map: Map<String, Any?>): Goal {
            return Goal(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                category = map["category"] as? String ?: "",
                limitValue = (map["limitValue"] as? Number)?.toDouble() ?: 0.0,
                alertAt80 = map["alertAt80"] as? Boolean ?: true,
                alertAt100 = map["alertAt100"] as? Boolean ?: true,
                monthlyReset = map["monthlyReset"] as? Boolean ?: true,
                isActive = map["isActive"] as? Boolean ?: true,
                createdAt = map["createdAt"] as? String ?: ""
            )
        }
    }
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "userId" to userId,
            "category" to category,
            "limitValue" to limitValue,
            "alertAt80" to alertAt80,
            "alertAt100" to alertAt100,
            "monthlyReset" to monthlyReset,
            "isActive" to isActive,
            "createdAt" to createdAt
        )
    }
}

/**
 * Model: GoalProgress
 * Represents progress towards a goal
 */
@Parcelize
data class GoalProgress(
    val goal: Goal,
    val spent: Double,
    val percentage: Double,
    val status: GoalStatus
) : Parcelable {
    val remaining: Double get() = goal.limitValue - spent
    val isExceeded: Boolean get() = spent > goal.limitValue
}

/**
 * Enum: GoalStatus
 */
enum class GoalStatus {
    NORMAL,    // < 80%
    WARNING,   // >= 80%
    EXCEEDED   // >= 100%
}

