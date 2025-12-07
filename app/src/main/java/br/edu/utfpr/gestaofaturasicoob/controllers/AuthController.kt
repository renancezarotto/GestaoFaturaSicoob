package br.edu.utfpr.gestaofaturasicoob.controllers

import br.edu.utfpr.gestaofaturasicoob.models.User
import br.edu.utfpr.gestaofaturasicoob.services.AuthService
import br.edu.utfpr.gestaofaturasicoob.utils.AuthErrorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthController - Controller de Autenticação
 * 
 * PADRÃO: Controller (MVC)
 * - Faz ponte entre View (Fragments) e Service (AuthService)
 * - Adiciona camada de validação e tratamento de erros
 * - Converte erros técnicos em mensagens amigáveis
 * 
 * RESPONSABILIDADES:
 * 1. Validação de entrada (campos obrigatórios, formato)
 * 2. Orquestração de chamadas ao AuthService
 * 3. Conversão de erros Firebase em mensagens amigáveis
 * 4. Execução em background (Dispatchers.IO)
 * 
 * FLUXO:
 * LoginFragment → AuthController → AuthService → FirebaseManager → Firebase
 *                ↓ (validação)     ↓ (conversão erro)
 * LoginFragment ← AuthController ← AuthService ← FirebaseManager ← Firebase
 * 
 * DIFERENÇA DO SERVICE:
 * - Service: Lógica pura de negócio (Firebase)
 * - Controller: Validação + Orquestração + Tratamento de erros
 */
class AuthController {
    
    /**
     * Login com Email e Senha
     * 
     * VALIDAÇÕES:
     * - Email e senha não podem estar vazios
     * - Campos são trimados antes de validar
     * 
     * TRATAMENTO DE ERRO:
     * - Erros Firebase são convertidos em mensagens amigáveis
     * - Usa AuthErrorUtils para tradução de erros
     * 
     * THREAD:
     * - Executa em Dispatchers.IO (background thread)
     * - Não bloqueia UI thread
     * 
     * @param email Email do usuário
     * @param password Senha do usuário
     * @return Result<User> - Success com User se login OK, Failure com mensagem amigável
     */
    suspend fun loginWithEmail(email: String, password: String): Result<User> {
        // withContext(Dispatchers.IO): Executa em thread de I/O
        // Não bloqueia UI thread (main thread)
        return withContext(Dispatchers.IO) {
            // ========== VALIDAÇÃO DE ENTRADA ==========
            // Verifica se campos obrigatórios estão preenchidos
            if (email.isBlank() || password.isBlank()) {
                return@withContext Result.failure(Exception("Email e senha são obrigatórios"))
            }
            
            // ========== CHAMA SERVICE ==========
            // AuthService faz a lógica real de autenticação
            val result = AuthService.loginWithEmail(email, password)
            
            // ========== TRATAMENTO DE ERRO ==========
            // Se falhou, converte erro técnico em mensagem amigável
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    // AuthErrorUtils.getErrorMessage(): Converte erro Firebase em mensagem legível
                    // Ex: "ERROR_WRONG_PASSWORD" → "Senha incorreta"
                    return@withContext Result.failure(Exception(AuthErrorUtils.getErrorMessage(exception)))
                }
            }
            
            // Se sucesso, retorna resultado original
            result
        }
    }
    
    /**
     * Registro de Novo Usuário
     * 
     * VALIDAÇÕES:
     * - Todos os campos obrigatórios (email, senha, name)
     * - Senha com mínimo de 6 caracteres (exigência do Firebase)
     * 
     * REGRAS DE NEGÓCIO:
     * - Senha < 6 caracteres → Erro antes de chamar Firebase
     * - Campos vazios → Erro antes de chamar Firebase
     * 
     * @param email Email do novo usuário
     * @param password Senha (mínimo 6 caracteres)
     * @param name Nome do usuário
     * @return Result<User> - Success com User criado, Failure com mensagem amigável
     */
    suspend fun registerWithEmail(email: String, password: String, name: String): Result<User> {
        return withContext(Dispatchers.IO) {
            // ========== VALIDAÇÃO DE CAMPOS OBRIGATÓRIOS ==========
            if (email.isBlank() || password.isBlank() || name.isBlank()) {
                return@withContext Result.failure(Exception("Todos os campos são obrigatórios"))
            }
            
            // ========== VALIDAÇÃO DE SENHA ==========
            // Firebase exige senha com mínimo 6 caracteres
            // Melhor validar aqui antes de chamar Firebase (economiza requisição)
            if (password.length < 6) {
                return@withContext Result.failure(Exception("Senha deve ter pelo menos 6 caracteres"))
            }
            
            // ========== CHAMA SERVICE ==========
            // AuthService cria conta no Firebase Auth + Realtime Database
            val result = AuthService.registerWithEmail(email, password, name)
            
            // ========== TRATAMENTO DE ERRO ==========
            // Converte erros Firebase em mensagens amigáveis
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    // Ex: "ERROR_EMAIL_ALREADY_IN_USE" → "Este email já está cadastrado"
                    return@withContext Result.failure(Exception(AuthErrorUtils.getErrorMessage(exception)))
                }
            }
            
            result
        }
    }
    
    suspend fun loginWithGoogle(idToken: String): Result<User> {
        return withContext(Dispatchers.IO) {
            val result = AuthService.loginWithGoogle(idToken)
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    return@withContext Result.failure(Exception(AuthErrorUtils.getErrorMessage(exception)))
                }
            }
            result
        }
    }
    
    fun logout() {
        AuthService.logout()
    }
    
    fun getCurrentUser(): User? {
        return AuthService.getCurrentUser()
    }
    
    fun isAuthenticated(): Boolean {
        return AuthService.isAuthenticated()
    }
    
    fun getCurrentUserId(): String? {
        return AuthService.getCurrentUserId()
    }
    
    /**
     * Busca Dados Completos do Usuário Atual
     * 
     * VALIDAÇÃO:
     * - Verifica se usuário está autenticado
     * - Se não autenticado, retorna erro antes de chamar Service
     * 
     * USO:
     * - ProfileFragment: Exibir dados do perfil
     * - EditProfileDialog: Carregar dados para edição
     * - Dashboard: Buscar renda para cálculos
     * 
     * @return Result<User> - Success com User completo, Failure se não autenticado ou erro
     */
    suspend fun getCompleteUserData(): Result<br.edu.utfpr.gestaofaturasicoob.models.User> {
        return withContext(Dispatchers.IO) {
            // ========== VERIFICA AUTENTICAÇÃO ==========
            // Busca ID do usuário atual (null se não autenticado)
            val userId = getCurrentUserId()
            if (userId == null) {
                // Falha rápido: Não chama Service se não autenticado
                return@withContext Result.failure(Exception("Usuário não autenticado"))
            }
            
            // ========== CHAMA SERVICE ==========
            // AuthService busca dados completos do Realtime Database
            AuthService.getCompleteUserData(userId)
        }
    }
    
    /**
     * Update user profile data
     */
    suspend fun updateUserProfile(
        nickname: String?,
        phone: String?,
        income: Double?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val userId = getCurrentUserId()
            if (userId == null) {
                return@withContext Result.failure(Exception("Usuário não autenticado"))
            }
            AuthService.updateUserProfile(userId, nickname, phone, income)
        }
    }
}

