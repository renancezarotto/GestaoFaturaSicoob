package br.edu.utfpr.gestaofaturasicoob.services

import br.edu.utfpr.gestaofaturasicoob.data.datasource.FirebaseManager
import br.edu.utfpr.gestaofaturasicoob.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * AuthService - Serviço de Autenticação
 * 
 * RESPONSABILIDADES:
 * - Login com Email/Senha
 * - Login com Google
 * - Registro de novos usuários
 * - Logout
 * - Busca de dados do usuário
 * - Atualização de perfil
 * 
 * ARQUITETURA:
 * - Service Layer: Contém regras de negócio de autenticação
 * - Singleton (object): Uma única instância compartilhada
 * - Usa FirebaseManager para operações Firebase
 * - Retorna Result<T> para tratamento elegante de erros
 * 
 * FLUXO TÍPICO:
 * Fragment → Controller → AuthService → FirebaseManager → Firebase
 *                ↓
 * Fragment ← Controller ← AuthService ← FirebaseManager ← Firebase
 */
object AuthService {
    
    // Referência ao FirebaseAuth do FirebaseManager
    // Usa instância singleton compartilhada
    private val auth: FirebaseAuth = FirebaseManager.auth
    
    /**
     * Login com Email e Senha
     * 
     * COMO FUNCIONA:
     * 1. Firebase Auth valida credenciais
     * 2. Se válidas, cria sessão de autenticação
     * 3. Retorna User do sistema (não cria no Database ainda)
     * 
     * OBSERVAÇÕES:
     * - Não cria/atualiza usuário no Realtime Database aqui
     * - Dados do usuário são criados/atualizados separadamente
     * - Em caso de erro, retorna Result.failure com exceção
     * 
     * @param email Email do usuário
     * @param password Senha do usuário
     * @return Result<User> - Success com User se login OK, Failure se erro
     */
    suspend fun loginWithEmail(email: String, password: String): Result<User> {
        return try {
            // signInWithEmailAndPassword(): Firebase Auth valida credenciais
            // await(): Aguarda resultado (coroutine suspend)
            // Retorna AuthResult com FirebaseUser se sucesso
            val result = auth.signInWithEmailAndPassword(email, password).await()
            
            // Extrai FirebaseUser do resultado
            // Se null, significa que autenticação falhou
            val firebaseUser = result.user ?: throw Exception("Usuário não encontrado")
            
            // Converte FirebaseUser para User (model do sistema)
            // toUser() é extension function privada
            Result.success(firebaseUser.toUser())
        } catch (e: Exception) {
            // Qualquer erro (credenciais inválidas, rede, etc.)
            // Retorna como Result.failure para tratamento no Controller
            Result.failure(e)
        }
    }
    
    /**
     * Registro de Novo Usuário com Email e Senha
     * 
     * COMO FUNCIONA:
     * 1. Firebase Auth cria conta de autenticação
     * 2. Cria/atualiza dados do usuário no Realtime Database
     * 3. Retorna User completo
     * 
     * DIFERENÇA DO LOGIN:
     * - Login: Apenas valida credenciais (usuário já existe)
     * - Registro: Cria conta NOVA + salva dados no Database
     * 
     * ESTRUTURA CRIADA:
     * Firebase Auth: Conta de autenticação (email/senha)
     * Realtime Database: users/{uid}/ com name, email, createdAt
     * 
     * @param email Email do novo usuário
     * @param password Senha do novo usuário (mínimo 6 caracteres - Firebase)
     * @param name Nome do novo usuário
     * @return Result<User> - Success com User criado, Failure se erro
     */
    suspend fun registerWithEmail(email: String, password: String, name: String): Result<User> {
        return try {
            // createUserWithEmailAndPassword(): Cria conta no Firebase Auth
            // await(): Aguarda criação da conta
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            
            // Extrai FirebaseUser (não pode ser null após criação bem-sucedida)
            val firebaseUser = result.user ?: throw Exception("Erro ao criar usuário")
            
            // ========== SALVA DADOS NO REALTIME DATABASE ==========
            // Importante: Firebase Auth e Realtime Database são separados
            // - Firebase Auth: Gerencia autenticação (email/senha)
            // - Realtime Database: Armazena dados do app (nome, etc.)
            // 
            // createOrUpdateUser():
            //   - Se usuário não existe → Cria (adiciona createdAt)
            //   - Se usuário já existe → Atualiza (adiciona updatedAt)
            //   - Usa updateChildren() para preservar dados existentes
            FirebaseManager.createOrUpdateUser(
                userId = firebaseUser.uid, // ID único do Firebase Auth
                name = name,
                email = email
            )
            
            // Retorna User completo convertido do FirebaseUser
            // toUser(name): Passa nome customizado (não pega do Firebase Auth)
            Result.success(firebaseUser.toUser(name))
        } catch (e: Exception) {
            // Erros possíveis:
            // - Email já em uso
            // - Senha muito fraca
            // - Email inválido
            // - Erro de rede
            Result.failure(e)
        }
    }
    
    /**
     * Login com Google Sign-In
     * 
     * COMO FUNCIONA:
     * 1. Fragment obtém ID Token do Google (via Google Sign-In)
     * 2. Service converte token em credencial Firebase
     * 3. Firebase Auth autentica com Google
     * 4. Se primeiro login → Cria usuário no Database
     * 5. Se login subsequente → Atualiza dados no Database
     * 
     * FLUXO COMPLETO:
     * Fragment → Google Sign-In API → idToken → AuthService → Firebase Auth → Realtime DB
     * 
     * DIFERENÇA DO LOGIN EMAIL:
     * - Email: Valida apenas credenciais (não salva no DB aqui)
     * - Google: Autentica + sempre salva/atualiza no DB (primeira vez ou não)
     * 
     * @param idToken Token de autenticação do Google (obtido via Google Sign-In)
     * @return Result<User> - Success com User, Failure se erro
     */
    suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            // GoogleAuthProvider.getCredential(): Converte token Google em credencial Firebase
            // idToken: Token obtido do Google Sign-In
            // null: Access token (não necessário para login básico)
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            
            // signInWithCredential(): Autentica com credencial do Google
            // Se for primeira vez, cria conta automaticamente no Firebase Auth
            // await(): Aguarda resultado da autenticação
            val result = auth.signInWithCredential(credential).await()
            
            // Extrai FirebaseUser após autenticação bem-sucedida
            val firebaseUser = result.user ?: throw Exception("Usuário não encontrado")
            
            // ========== SALVA/ATUALIZA NO REALTIME DATABASE ==========
            // Sempre chama createOrUpdateUser():
            //   - Se primeiro login: Cria novo usuário (createdAt)
            //   - Se login subsequente: Atualiza dados (updatedAt)
            // 
            // Dados obtidos do Google:
            //   - displayName: Nome do perfil Google
            //   - email: Email da conta Google
            FirebaseManager.createOrUpdateUser(
                userId = firebaseUser.uid,
                name = firebaseUser.displayName ?: "Usuário", // Fallback se não tiver nome
                email = firebaseUser.email ?: "" // Fallback se não tiver email
            )
            
            // Retorna User convertido do FirebaseUser
            Result.success(firebaseUser.toUser())
        } catch (e: Exception) {
            // Erros possíveis:
            // - Token inválido/expirado
            // - Erro de rede
            // - Conta Google desabilitada
            Result.failure(e)
        }
    }
    
    /**
     * Logout
     */
    fun logout() {
        auth.signOut()
    }
    
    /**
     * Get current user
     */
    fun getCurrentUser(): User? {
        return auth.currentUser?.toUser()
    }
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return auth.currentUser != null
    }
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Busca Dados Completos do Usuário do Realtime Database
     * 
     * DIFERENÇA DE getCurrentUser():
     * - getCurrentUser(): Retorna apenas dados do Firebase Auth (name, email básicos)
     * - getCompleteUserData(): Busca dados COMPLETOS do Database (nickname, phone, income, etc.)
     * 
     * QUANDO USAR:
     * - PerfilFragment: Precisar exibir todos os dados
     * - Edição de perfil: Buscar dados atuais para editar
     * - Dashboard: Buscar renda para cálculos
     * 
     * ESTRUTURA BUSCADA:
     * users/{userId}/
     *   name, email, nickname, phone, income, createdAt, updatedAt
     * 
     * @param userId ID do usuário (Firebase Auth UID)
     * @return Result<User> - Success com User completo, Failure se erro
     */
    suspend fun getCompleteUserData(userId: String): Result<User> {
        return try {
            // Busca dados do Realtime Database (não do Firebase Auth)
            val result = FirebaseManager.getUserData(userId)
            
            if (result.isSuccess) {
                // Result.isSuccess: Operação foi bem-sucedida
                // getOrNull()!!: Extrai Map<String, Any> do Result
                // !!: Safe aqui porque já verificamos isSuccess
                val userData = result.getOrNull()!!
                
                // Converte Map do Firebase para User (model do sistema)
                // fromMap(): Factory method que cria User a partir do Map
                val user = User.fromMap(userData)
                
                Result.success(user)
            } else {
                // Se falhou, extrai exceção ou cria nova
                Result.failure(result.exceptionOrNull() ?: Exception("Erro ao carregar dados"))
            }
        } catch (e: Exception) {
            // Qualquer exceção (conversão, rede, etc.)
            Result.failure(e)
        }
    }
    
    /**
     * Atualiza Dados do Perfil do Usuário
     * 
     * CAMPOS ATUALIZÁVEIS:
     * - nickname: Apelido/nome de exibição
     * - phone: Telefone (opcional)
     * - income: Renda mensal (opcional, usado para insights)
     * 
     * COMPORTAMENTO:
     * - Apenas campos não-null são atualizados
     * - Sempre adiciona/atualiza updatedAt
     * - Preserva outros campos (name, email não são alterados aqui)
     * 
     * USO DE updateChildren():
     * - Atualiza apenas campos especificados
     * - Não sobrescreve campos não mencionados
     * - Mais seguro que setValue() completo
     * 
     * @param userId ID do usuário
     * @param nickname Novo apelido (null = não atualizar)
     * @param phone Novo telefone (null = não atualizar)
     * @param income Nova renda (null = não atualizar)
     * @return Result<Unit> - Success se atualizou, Failure se erro
     */
    suspend fun updateUserProfile(
        userId: String,
        nickname: String?,
        phone: String?,
        income: Double?
    ): Result<Unit> {
        return try {
            // Mapa de atualizações (sempre adiciona updatedAt)
            val updates = mutableMapOf<String, Any?>(
                "updatedAt" to java.time.Instant.now().toString()
            )
            
            // Adiciona apenas campos não-null ao mapa
            // let {}: Executa apenas se valor não for null
            nickname?.let { updates["nickname"] = it }
            phone?.let { updates["phone"] = it }
            income?.let { updates["income"] = it }
            
            // Atualiza no Firebase usando updateChildren()
            // child(userId): Acessa nó do usuário
            // updateChildren(): Atualiza apenas campos do mapa
            // await(): Aguarda confirmação
            FirebaseManager.usersRef.child(userId).updateChildren(updates).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extension Function: Converte FirebaseUser para User (Model do Sistema)
     * 
     * POR QUE EXTENSION FUNCTION?
     * - Adiciona método a FirebaseUser sem modificar classe original
     * - Sintaxe limpa: firebaseUser.toUser()
     * - Encapsula lógica de conversão
     * 
     * DADOS CONVERTIDOS:
     * - uid → id
     * - displayName → name (ou customName se fornecido)
     * - email → email
     * - createdAt: Timestamp atual (não vem do Firebase Auth)
     * 
     * LIMITAÇÕES:
     * - FirebaseUser não tem nickname, phone, income
     * - Para dados completos, usar getCompleteUserData()
     * 
     * @param customName Nome customizado (usado no registro)
     * @return User - Model do sistema
     */
    private fun FirebaseUser.toUser(customName: String? = null): User {
        return User(
            id = uid, // ID único do Firebase Auth
            name = customName ?: displayName ?: "Usuário", // Nome: custom > display > fallback
            email = email ?: "", // Email: pode ser null (fallback vazio)
            createdAt = java.time.Instant.now().toString() // Timestamp atual
        )
    }
}

