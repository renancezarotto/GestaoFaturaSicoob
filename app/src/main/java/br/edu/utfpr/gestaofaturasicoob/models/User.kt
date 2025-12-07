package br.edu.utfpr.gestaofaturasicoob.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model: User
 * Represents an authenticated user in the system
 */
@Parcelize
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val nickname: String? = null,
    val phone: String? = null,
    val income: Double? = null,
    val createdAt: String = "",
    val updatedAt: String? = null
) : Parcelable {
    
    companion object {
        fun fromMap(map: Map<String, Any?>): User {
            return User(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                email = map["email"] as? String ?: "",
                nickname = map["nickname"] as? String,
                phone = map["phone"] as? String,
                income = when (val value = map["income"]) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull()
                    else -> null
                },
                createdAt = map["createdAt"] as? String ?: "",
                updatedAt = map["updatedAt"] as? String
            )
        }
    }
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "email" to email,
            "nickname" to nickname,
            "phone" to phone,
            "income" to income,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }
    
    /**
     * Returns the display name (nickname if available, otherwise name)
     */
    fun getDisplayName(): String {
        return nickname?.takeIf { it.isNotBlank() } ?: name
    }
}

