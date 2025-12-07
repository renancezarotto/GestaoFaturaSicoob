package br.edu.utfpr.gestaofaturasicoob.services

import br.edu.utfpr.gestaofaturasicoob.data.datasource.FirebaseManager
import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import kotlinx.coroutines.tasks.await

/**
 * CategoryService - Serviço de Categorias e Auto-Categorização
 * 
 * RESPONSABILIDADES:
 * 1. CRUD de categorias (padrão + personalizadas)
 * 2. Gerenciamento de mapeamentos (estabelecimento → categoria)
 * 3. Auto-categorização de despesas baseada em histórico
 * 4. Detecção automática de tarifas do cartão
 * 
 * ESTRUTURA NO FIREBASE:
 * users/
 *   {userId}/
 *     savedCategories/  ← Mapeamentos para auto-categorização
 *       "CAFE DA ANA": "Alimentação"
 *       "DELTA CEL CENTRO": "Outros"
 *       "AB SUPERMERCADOS LTD": "Mercado"
 *     
 *     customCategories/  ← Categorias criadas pelo usuário
 *       {categoryId}/
 *         id: "custom_123"
 *         name: "Academia"
 *         color: "#FF5722"
 *         isRecurring: false
 *         isDefault: false
 *         createdAt: "2025-10-12"
 *     
 *     deletedDefaultCategories/  ← Categorias padrão excluídas
 *       "fees": true
 *       "other": true
 * 
 * AUTO-CATEGORIZAÇÃO:
 * - Na primeira vez: Usuário categoriza manualmente
 * - Sistema salva: estabelecimento → categoria (em savedCategories)
 * - Próximas vezes: Sistema auto-categoriza usando mapeamento salvo
 * - Tarifas: Detectadas automaticamente (ANUIDADE, PROTEÇÃO, etc.)
 * 
 * MAPEAMENTO:
 * - Chave: Nome do estabelecimento (ex: "CAFE DA ANA")
 * - Valor: Nome da categoria em português (ex: "Alimentação")
 * - Usado para auto-categorização em faturas futuras
 */
object CategoryService {
    
    /**
     * Referência ao nó de usuários no Firebase
     */
    private val database = FirebaseManager.usersRef
    
    /**
     * Busca Todas as Categorias (Padrão + Personalizadas)
     * 
     * AGRUPAMENTO:
     * - Categorias padrão: 12 categorias do sistema (Alimentação, Transporte, etc.)
     * - Categorias personalizadas: Criadas pelo usuário
     * - Exclui categorias padrão que foram deletadas pelo usuário
     * 
     * FLUXO:
     * 1. Busca categorias personalizadas do usuário
     * 2. Busca lista de categorias padrão deletadas
     * 3. Filtra categorias padrão (remove as deletadas)
     * 4. Combina padrão + personalizadas
     * 
     * EXCLUSÃO:
     * - Se usuário deletou uma categoria padrão, ela não aparece mais
     * - Deletar padrão = marca em "deletedDefaultCategories"
     * - Não remove do sistema (outros usuários ainda têm acesso)
     * 
     * ORDEM:
     * - Categorias padrão aparecem primeiro
     * - Categorias personalizadas aparecem depois
     * 
     * @param userId ID do usuário
     * @return Result<List<Category>> - Lista completa de categorias disponíveis
     */
    suspend fun getCategories(userId: String): Result<List<Category>> {
        return try {
            // ========== ETAPA 1: BUSCA CATEGORIAS PERSONALIZADAS ==========
            // Busca categorias criadas pelo usuário
            val customCategories = getCustomCategories(userId).getOrNull() ?: emptyList()
            
            // ========== ETAPA 2: BUSCA CATEGORIAS PADRÃO DELETADAS ==========
            // Busca lista de IDs de categorias padrão que o usuário deletou
            val deletedDefaults = getDeletedDefaultCategories(userId).getOrNull() ?: emptySet()
            
            // ========== ETAPA 3: FILTRA CATEGORIAS PADRÃO ==========
            // Remove categorias padrão que foram deletadas pelo usuário
            // Category.DEFAULT_CATEGORIES: Lista de 12 categorias padrão do sistema
            val availableDefaults = Category.DEFAULT_CATEGORIES.filter { !deletedDefaults.contains(it.id) }
            
            // ========== ETAPA 4: COMBINA ==========
            // Combina categorias padrão (filtradas) + personalizadas
            val allCategories = availableDefaults + customCategories
            
            Result.success(allCategories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get custom categories only
     */
    suspend fun getCustomCategories(userId: String): Result<List<Category>> {
        return try {
            val snapshot = database.child(userId).child("customCategories").get().await()
            val categories = mutableListOf<Category>()
            
            snapshot.children.forEach { child ->
                val categoryMap = child.value as? Map<*, *>
                if (categoryMap != null) {
                    @Suppress("UNCHECKED_CAST")
                    categories.add(Category.fromMap(categoryMap as Map<String, Any?>))
                }
            }
            
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create custom category
     */
    suspend fun createCategory(userId: String, category: Category): Result<String> {
        return try {
            val categoryId = category.id.ifEmpty {
                // Gera ID no formato "custom_" + timestamp + hash curto
                // Exemplo: "custom_1697123456_a1b2"
                val timestamp = System.currentTimeMillis()
                val hash = (timestamp.toString().hashCode() and 0xFFFF).toString(16)
                "custom_${timestamp}_${hash}"
            }
            
            val categoryWithId = category.copy(
                id = categoryId,
                isDefault = false,
                createdAt = java.time.Instant.now().toString()
            )
            
            database.child(userId).child("customCategories").child(categoryId).setValue(categoryWithId.toMap()).await()
            Result.success(categoryId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update custom category
     */
    suspend fun updateCategory(userId: String, category: Category): Result<Unit> {
        return try {
            database.child(userId).child("customCategories").child(category.id).setValue(category.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete custom category
     */
    suspend fun deleteCategory(userId: String, categoryId: String): Result<Unit> {
        return try {
            database.child(userId).child("customCategories").child(categoryId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete default category (converts to custom and marks as deleted)
     */
    suspend fun deleteDefaultCategory(userId: String, categoryId: String): Result<Unit> {
        return try {
            // Store deleted default categories to prevent them from appearing
            database.child(userId).child("deletedDefaultCategories").child(categoryId).setValue(true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get deleted default categories
     */
    suspend fun getDeletedDefaultCategories(userId: String): Result<Set<String>> {
        return try {
            val snapshot = database.child(userId).child("deletedDefaultCategories").get().await()
            val deletedIds = mutableSetOf<String>()
            
            snapshot.children.forEach { child ->
                child.key?.let { deletedIds.add(it) }
            }
            
            Result.success(deletedIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Busca Mapeamentos Salvos (Estabelecimento → Categoria ID)
     * 
     * PROPÓSITO:
     * Busca mapeamentos salvos para auto-categorização
     * 
     * ESTRUTURA NO FIREBASE:
     * savedCategories/
     *   "CAFE DA ANA": "cat_food"
     *   "MERCADO ABC": "cat_grocery"
     *   "POSTO XYZ": "cat_fuel"
     * 
     * FORMATO:
     * - Chave: Nome do estabelecimento (exatamente como aparece na fatura)
     * - Valor: ID da categoria (ex: "cat_food", "custom_123")
     * 
     * USO:
     * - Auto-categorização: Busca estabelecimento no map → aplica categoryId
     * - Memorização: Após categorização manual, salva para futuras faturas
     * 
     * @param userId ID do usuário
     * @return Result<Map<String, String>> - Mapa de estabelecimento → categoryId
     */
    suspend fun getSavedMappings(userId: String): Result<Map<String, String>> {
        return try {
            // Busca nó "savedCategories" do usuário
            val snapshot = database.child(userId).child("savedCategories").get().await()
            val mappings = mutableMapOf<String, String>()
            
            // Converte dados do Firebase em Map
            snapshot.children.forEach { child ->
                val establishment = child.key // Chave = nome do estabelecimento
                val categoryId = child.value as? String // Valor = ID da categoria
                
                // Adiciona ao mapa se ambos estão presentes
                if (establishment != null && categoryId != null) {
                    mappings[establishment] = categoryId
                }
            }
            
            Result.success(mappings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Salva Mapeamento (Estabelecimento → Categoria ID)
     * 
     * PROPÓSITO:
     * Salva associação estabelecimento → categoria ID para auto-categorização futura
     * 
     * FLUXO:
     * 1. Usuário categoriza despesa manualmente
     * 2. Sistema salva mapeamento: estabelecimento → categoryId
     * 3. Próximas faturas: Despesas do mesmo estabelecimento são auto-categorizadas
     * 
     * IMPORTANTE:
     * - Chave = nome exato do estabelecimento (case-sensitive)
     * - Valor = ID da categoria (ex: "cat_food", "custom_123")
     * - Sobrescreve mapeamento anterior se estabelecimento já existe
     * 
     * EXEMPLO:
     * saveMapping("user123", "CAFE DA ANA", "cat_food")
     * → Firebase: savedCategories/CAFE DA ANA = "cat_food"
     * → Próxima fatura: "CAFE DA ANA" será auto-categorizada como "cat_food"
     * 
     * @param userId ID do usuário
     * @param establishment Nome do estabelecimento (ex: "CAFE DA ANA")
     * @param categoryId ID da categoria (ex: "cat_food", "custom_123")
     * @return Result<Unit> - Success se salvo, Failure se erro
     */
    suspend fun saveMapping(userId: String, establishment: String, categoryId: String): Result<Unit> {
        return try {
            // Salva mapeamento no Firebase
            // Caminho: users/{userId}/savedCategories/{establishment}
            // Valor: ID da categoria
            database.child(userId).child("savedCategories").child(establishment).setValue(categoryId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
}

