package br.edu.utfpr.gestaofaturasicoob.utils

import com.google.firebase.auth.FirebaseAuthException

/**
 * Utility class to convert Firebase Auth errors to user-friendly messages
 */
object AuthErrorUtils {
    
    /**
     * Convert Firebase Auth exception to user-friendly error message
     */
    fun getErrorMessage(exception: Throwable): String {
        return when {
            exception is FirebaseAuthException -> {
                getFirebaseAuthErrorMessage(exception.errorCode)
            }
            exception.message?.contains("network", ignoreCase = true) == true -> {
                "Erro de conexão. Verifique sua internet e tente novamente."
            }
            else -> {
                exception.message ?: "Ocorreu um erro. Tente novamente."
            }
        }
    }
    
    /**
     * Convert Firebase Auth error code to user-friendly message
     */
    private fun getFirebaseAuthErrorMessage(errorCode: String): String {
        return when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "Email inválido. Verifique o formato do email."
            "ERROR_WRONG_PASSWORD" -> "Senha incorreta. Tente novamente ou recupere sua senha."
            "ERROR_USER_NOT_FOUND" -> "Conta não encontrada. Verifique seu email ou crie uma conta."
            "ERROR_USER_DISABLED" -> "Esta conta foi desativada. Entre em contato com o suporte."
            "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Operação não permitida. Entre em contato com o suporte."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Este email já está cadastrado. Tente fazer login."
            "ERROR_WEAK_PASSWORD" -> "Senha muito fraca. Use pelo menos 6 caracteres."
            "ERROR_INVALID_CREDENTIAL" -> "Email ou senha incorretos. Verifique suas credenciais."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Erro de conexão. Verifique sua internet."
            "ERROR_INTERNAL_ERROR" -> "Erro interno do servidor. Tente novamente mais tarde."
            else -> "Erro de autenticação: $errorCode"
        }
    }
}

