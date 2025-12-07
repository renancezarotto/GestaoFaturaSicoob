package br.edu.utfpr.gestaofaturasicoob.data.datasource

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * FirebaseManager - Gerenciador Central do Firebase
 * 
 * PADRÃO: Singleton (object)
 * - Única instância compartilhada em todo o app
 * - Garante que Firebase é inicializado uma vez
 * - Acesso global: FirebaseManager.auth, FirebaseManager.usersRef
 * 
 * RESPONSABILIDADES:
 * 1. Inicialização do Firebase (Auth + Realtime Database)
 * 2. Gerenciamento de autenticação (usuário atual, logout)
 * 3. Operações CRUD no Realtime Database (usuários)
 * 4. Referências centralizadas aos nós do banco
 * 
 * ESTRUTURA NO FIREBASE:
 * users/
 *   {userId}/
 *     name, email, nickname, phone, income
 *     invoices/
 *       {referenceMonth}/
 *         expenses, dueDate, totalValue, etc.
 *     categories/
 *     goals/
 * 
 * IMPORTANTE:
 * - Sempre chamar initialize() ANTES de usar
 * - Usar coroutines (suspend) para operações assíncronas
 * - Result<T> para tratamento de erros elegante
 */
object FirebaseManager {
    
    // ========== INSTÂNCIAS FIREBASE ==========
    
    // FirebaseAuth: Gerencia autenticação (login, logout, usuário atual)
    // getInstance(): Obtém instância singleton do Firebase Auth
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    // FirebaseDatabase: Conexão com Realtime Database
    // Nullable: Inicializado apenas quando initialize() é chamado
    private var database: FirebaseDatabase? = null
    
    // ========== REFERÊNCIAS DO BANCO ==========
    
    // Referência privada ao nó "users" (null até inicializar)
    private var _usersRef: DatabaseReference? = null
    
    // Propriedade pública com getter customizado
    // Se não inicializado, lança exceção clara
    // Evita NullPointerException em tempo de execução
    val usersRef: DatabaseReference
        get() = _usersRef ?: throw IllegalStateException("Firebase não foi inicializado. Chame initialize() primeiro.")
    
    /**
     * Inicializa o Firebase Realtime Database
     * 
     * QUANDO CHAMAR:
     * - MainActivity.onCreate() (início do app)
     * - Apenas UMA vez (singleton garante isso)
     * 
     * O QUE FAZ:
     * 1. Obtém URL do banco de dados (FirebaseConfig)
     * 2. Conecta ao Firebase Realtime Database
     * 3. Cria referência ao nó "users" (usersRef)
     * 
     * TRATAMENTO DE ERRO:
     * - Se falhar com URL customizada, tenta URL padrão
     * - Se falhar completamente, app continua mas operações Firebase falharão
     * 
     * @param context Contexto da aplicação (necessário para obter configuração)
     */
    fun initialize(context: Context) {
        try {
            // Verifica se já foi inicializado (proteção contra múltiplas chamadas)
            if (database == null) {
                // Obtém URL do banco configurada no FirebaseConfig
                val databaseUrl = FirebaseConfig.getDatabaseUrl(context)
                
                // Conecta ao Firebase Realtime Database usando a URL
                // getInstance(url): Cria conexão com banco específico
                database = FirebaseDatabase.getInstance(databaseUrl)
                
                // Cria referência ao nó "users"
                // getReference("users"): Acessa/users no banco
                // Toda operação em usuários usará esta referência
                _usersRef = database?.getReference("users")
                
                println("✅ Firebase Database inicializado com URL: $databaseUrl")
            }
        } catch (e: Exception) {
            // FALLBACK: Se falhar, tenta URL padrão
            println("❌ Erro ao inicializar Firebase Database: ${e.message}")
            
            try {
                // Tenta inicializar com URL padrão do Firebase
                // getInstance() sem parâmetros usa URL padrão do projeto
                database = FirebaseDatabase.getInstance()
                _usersRef = database?.getReference("users")
                println("✅ Firebase Database inicializado com URL padrão")
            } catch (e2: Exception) {
                // Se falhar completamente, apenas loga erro
                // App continua funcionando, mas Firebase não funcionará
                println("❌ Erro crítico ao inicializar Firebase: ${e2.message}")
            }
        }
    }
    
    /**
     * Obtém o usuário atual autenticado
     * @return FirebaseUser atual ou null se não estiver autenticado
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Cria ou Atualiza Usuário no Firebase
     * 
     * LÓGICA:
     * - Se usuário NÃO existe → Cria novo (adiciona createdAt)
     * - Se usuário JÁ existe → Atualiza (adiciona updatedAt)
     * - Usa updateChildren() para preservar dados existentes
     * 
     * ESTRUTURA SALVA:
     * users/{userId}/
     *   name: "Nome"
     *   email: "email@exemplo.com"
     *   createdAt: "1234567890" (apenas se novo)
     *   updatedAt: "1234567890" (apenas se atualização)
     * 
     * POR QUE updateChildren()?
     * - Não sobrescreve campos não mencionados
     * - Preserva dados como nickname, phone, income
     * - Mais seguro que setValue() completo
     * 
     * @param userId ID único do usuário (Firebase Auth UID)
     * @param name Nome do usuário
     * @param email Email do usuário
     * @return Result<Unit> - Success se salvou, Failure com erro se falhou
     */
    suspend fun createOrUpdateUser(
        userId: String,
        name: String,
        email: String
    ): Result<Unit> {
        return try {
            // ========== VERIFICA SE USUÁRIO EXISTE ==========
            // child(userId): Acessa nó específico do usuário
            // get(): Busca dados do nó (operação assíncrona)
            // await(): Aguarda resultado da operação (coroutine)
            val userSnapshot = usersRef.child(userId).get().await()
            val userExists = userSnapshot.exists() // true se dados existem
            
            println("🔍 FirebaseManager: Verificando usuário $userId - Existe: $userExists")
            
            if (userExists) {
                // Se existe, mostra dados atuais (debug)
                val existingData = userSnapshot.value as? Map<String, Any>
                println("📊 FirebaseManager: Dados existentes: $existingData")
            }
            
            // ========== PREPARA DADOS PARA SALVAR ==========
            // MutableMap permite adicionar campos condicionalmente
            val userData = mutableMapOf<String, Any>(
                "name" to name,
                "email" to email
            )
            
            // ========== ADICIONA TIMESTAMP APROPRIADO ==========
            if (!userExists) {
                // USUÁRIO NOVO: Adiciona data de criação
                userData["createdAt"] = Date().time.toString()
                println("🆕 FirebaseManager: Criando novo usuário")
            } else {
                // USUÁRIO EXISTENTE: Adiciona data de atualização
                userData["updatedAt"] = Date().time.toString()
                println("🔄 FirebaseManager: Atualizando usuário existente")
            }
            
            // ========== SALVA NO FIREBASE ==========
            // updateChildren(): Atualiza apenas campos especificados
            // Preserva outros campos (nickname, phone, income, etc.)
            // await(): Aguarda confirmação de salvamento
            usersRef.child(userId).updateChildren(userData).await()
            
            // ========== VERIFICA SE SALVOU CORRETAMENTE ==========
            // Busca dados recém-salvos para confirmar
            val savedSnapshot = usersRef.child(userId).get().await()
            val savedData = savedSnapshot.value as? Map<String, Any>
            println("✅ FirebaseManager: Dados salvos: $savedData")
            
            // Retorna sucesso
            Result.success(Unit)
        } catch (e: Exception) {
            // Tratamento de erro: retorna Failure com exceção
            println("❌ FirebaseManager: Erro ao salvar usuário: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Obtém dados do usuário do Firebase
     * @param userId ID do usuário
     * @return Result<Map<String, Any>> com os dados do usuário
     */
    suspend fun getUserData(userId: String): Result<Map<String, Any>> {
        return try {
            val snapshot = usersRef.child(userId).get().await()
            val userData = snapshot.value as? Map<String, Any>
            
            if (userData != null) {
                Result.success(userData)
            } else {
                Result.failure(Exception("Usuário não encontrado"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
}
