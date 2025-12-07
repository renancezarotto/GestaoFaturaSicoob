package br.edu.utfpr.gestaofaturasicoob.controllers

import br.edu.utfpr.gestaofaturasicoob.models.Category
import br.edu.utfpr.gestaofaturasicoob.models.ExtractedExpenseData
import br.edu.utfpr.gestaofaturasicoob.services.CategoryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controller: CategoryController
 * Manages category operations between View and CategoryService
 */
class CategoryController {
    
    suspend fun getCategories(userId: String): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            CategoryService.getCategories(userId)
        }
    }
    
    suspend fun createCategory(userId: String, category: Category): Result<String> {
        return withContext(Dispatchers.IO) {
            if (category.name.isBlank()) {
                return@withContext Result.failure(Exception("Nome da categoria é obrigatório"))
            }
            CategoryService.createCategory(userId, category)
        }
    }
    
    suspend fun deleteCategory(userId: String, categoryId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            CategoryService.deleteCategory(userId, categoryId)
        }
    }
    
    suspend fun updateCategory(userId: String, category: Category): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (category.name.isBlank()) {
                return@withContext Result.failure(Exception("Nome da categoria é obrigatório"))
            }
            CategoryService.updateCategory(userId, category)
        }
    }
    
    suspend fun deleteDefaultCategory(userId: String, categoryId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            CategoryService.deleteDefaultCategory(userId, categoryId)
        }
    }
    
    /**
     * Get default system categories
     */
    suspend fun getDefaultCategories(): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            try {
                val defaultCategories = listOf(
                    Category(id = "cat_food", name = "Alimentação", color = "#FF5722", isRecurring = true, isDefault = true),
                    Category(id = "cat_transport", name = "Transporte", color = "#2196F3", isRecurring = true, isDefault = true),
                    Category(id = "cat_health", name = "Saúde", color = "#4CAF50", isRecurring = false, isDefault = true),
                    Category(id = "cat_leisure", name = "Lazer", color = "#FFC107", isRecurring = false, isDefault = true),
                    Category(id = "cat_education", name = "Educação", color = "#9C27B0", isRecurring = false, isDefault = true),
                    Category(id = "cat_housing", name = "Moradia", color = "#00BCD4", isRecurring = true, isDefault = true),
                    Category(id = "cat_clothing", name = "Vestuário", color = "#E91E63", isRecurring = false, isDefault = true),
                    Category(id = "cat_fuel", name = "Combustível", color = "#FF9800", isRecurring = true, isDefault = true),
                    Category(id = "cat_grocery", name = "Mercado", color = "#8BC34A", isRecurring = true, isDefault = true),
                    Category(id = "cat_restaurant", name = "Restaurantes", color = "#F44336", isRecurring = false, isDefault = true),
                    Category(id = "cat_fees", name = "Taxas Cartão", color = "#607D8B", isRecurring = false, isDefault = true),
                    Category(id = "cat_other", name = "Outros", color = "#9E9E9E", isRecurring = false, isDefault = true)
                )
                Result.success(defaultCategories)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get user-created custom categories
     */
    suspend fun getUserCategories(userId: String): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            CategoryService.getCategories(userId)
        }
    }
    
    /**
     * Auto-Categoriza Despesas e Retorna Mapeamento para Salvamento
     * 
     * PROPÓSITO:
     * Gera mapeamento de despesas para categorias (com IDs)
     * Usado pelo InvoiceController.saveInvoice() para aplicar categorias
     * 
     * PROCESSO:
     * 1. Busca mapeamentos salvos (estabelecimento → categoryId)
     * 2. Para cada despesa:
     *    - Busca categoryId pelo estabelecimento
     *    - Detecta tarifas automaticamente
     *    - Cria chave estável para a despesa
     * 3. Retorna map: chave estável → categoryId
     * 
     * CHAVE ESTÁVEL:
     * Formato: "{index}_{establishment}"
     * Usada para identificar despesa única na lista
     * 
     * @param userId ID do usuário
     * @param expenses Lista de despesas extraídas
     * @return Result<Map<String, String>> - Map: chave estável → categoryId
     */
    suspend fun autoCategorizeExpenses(
        userId: String,
        expenses: List<ExtractedExpenseData>
    ): Result<Map<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                // ========== ETAPA 1: BUSCA MAPEAMENTOS SALVOS ==========
                // Busca mapeamentos: estabelecimento → categoryId
                val savedMappingsResult = CategoryService.getSavedMappings(userId)
                
                if (savedMappingsResult.isFailure) {
                    return@withContext Result.success(emptyMap())
                }
                
                val savedMappings = savedMappingsResult.getOrNull() ?: emptyMap()
                
                // ========== ETAPA 3: GERA MAPEAMENTO ==========
                // Map: chave estável da despesa → ID da categoria
                val expenseCategoryMapping = mutableMapOf<String, String>()
                
                expenses.forEachIndexed { index, expense ->
                    // ========== TENTATIVA 1: BUSCA POR MAPEAMENTO ==========
                    // Busca categoryId pelo nome do estabelecimento
                    // savedMappings retorna categoryId (ex: "cat_food")
                    var categoryId = savedMappings[expense.establishment]
                    
                    // ========== TENTATIVA 2: DETECÇÃO DE TARIFAS ==========
                    // Se não encontrou, verifica se é tarifa do cartão
                    if (categoryId == null && isCardFee(expense.description)) {
                        categoryId = "cat_fees"
                    }
                    
                    // ========== APLICA CATEGORIA ==========
                    // Se encontrou categoryId, adiciona ao mapeamento
                    if (categoryId != null) {
                        // ========== CRIA CHAVE ESTÁVEL ==========
                        // Formato: "0_CAFE DA ANA", "1_MERCADO ABC", etc.
                        // Index garante unicidade mesmo se mesmo estabelecimento aparece 2x
                        val expenseKey = "${index}_${expense.establishment}"
                        
                        // Adiciona ao map: chave estável → categoryId
                        expenseCategoryMapping[expenseKey] = categoryId
                    }
                }
                
                Result.success(expenseCategoryMapping)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Helper: Check if expense is a card fee
     */
    private fun isCardFee(description: String): Boolean {
        val keywords = listOf("ANUIDADE", "PROTEÇÃO", "PERDA", "ROUBO", "TAXA")
        return keywords.any { description.uppercase().contains(it) }
    }
    
    /**
     * Salva Mapeamento Estabelecimento → Categoria ID
     * 
     * FLUXO:
     * 1. Recebe categoryId (ex: "cat_food" ou "custom_123")
     * 2. Salva mapeamento: estabelecimento → categoryId
     * 
     * @param userId ID do usuário
     * @param establishment Nome do estabelecimento
     * @param categoryId ID da categoria (ex: "cat_food", "custom_123")
     * @return Result<Unit> - Success se salvo
     */
    suspend fun saveEstablishmentCategoryMapping(
        userId: String,
        establishment: String,
        categoryId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            CategoryService.saveMapping(userId, establishment, categoryId)
        }
    }
    
    /**
     * Get all categories (default + user custom)
     */
    suspend fun getAllCategories(userId: String): Result<List<Category>> {
        return withContext(Dispatchers.IO) {
            try {
                val defaultCategories = getDefaultCategories().getOrNull() ?: emptyList()
                val userCategories = getUserCategories(userId).getOrNull() ?: emptyList()
                Result.success((defaultCategories + userCategories).distinctBy { it.id })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

