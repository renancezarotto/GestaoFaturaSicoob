package br.edu.utfpr.gestaofaturasicoob.services

import br.edu.utfpr.gestaofaturasicoob.data.datasource.FirebaseManager
import br.edu.utfpr.gestaofaturasicoob.models.Goal
import br.edu.utfpr.gestaofaturasicoob.models.GoalProgress
import br.edu.utfpr.gestaofaturasicoob.models.GoalStatus
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import kotlinx.coroutines.tasks.await

/**
 * GoalService - Serviço de Metas de Gastos
 * 
 * RESPONSABILIDADES:
 * 1. CRUD de metas (criar, atualizar, deletar)
 * 2. Cálculo de progresso de metas (percentual, status)
 * 3. Validação de alertas (80% e 100%)
 * 
 * ESTRUTURA NO FIREBASE:
 * users/
 *   {userId}/
 *     goals/
 *       {goalId}/
 *         id: "goal_123"
 *         userId: "user123"
 *         category: "Alimentação"  ← Nome da categoria
 *         limitValue: 500.00       ← Valor limite
 *         alertAt80: true          ← Alerta aos 80%
 *         alertAt100: true         ← Alerta aos 100%
 *         monthlyReset: true       ← Reinicia todo mês
 *         isActive: true           ← Meta ativa
 *         createdAt: "2025-10-12"
 * 
 * REGRAS DE NEGÓCIO:
 * - Uma meta por categoria (não permite duplicatas)
 * - Limite > 0 obrigatório
 * - Metas podem ser ativadas/desativadas (isActive)
 * - monthlyReset = true: Meta reinicia automaticamente todo mês
 * 
 * CÁLCULO DE PROGRESSO:
 * - percentual = (gasto / limite) * 100
 * - Status:
 *   * < 80%: NORMAL (verde)
 *   * 80-99%: WARNING (amarelo)
 *   * >= 100%: EXCEEDED (vermelho)
 * 
 * ALERTAS:
 * - alertAt80: Se true, gera insight quando atinge 80%
 * - alertAt100: Se true, gera insight quando ultrapassa 100%
 */
object GoalService {
    
    /**
     * Referência ao nó de usuários no Firebase
     */
    private val database = FirebaseManager.usersRef
    
    /**
     * Busca Todas as Metas do Usuário (Apenas Ativas)
     * 
     * FILTRO:
     * - Retorna apenas metas com isActive = true
     * - Metas desativadas não aparecem (mas permanecem no Firebase)
     * 
     * FLUXO:
     * 1. Busca nó "goals" do usuário
     * 2. Converte cada meta do Firebase para objeto Goal
     * 3. Filtra apenas metas ativas
     * 
     * USO:
     * - Dashboard: Exibe apenas metas ativas
     * - Cálculo de progresso: Calcula apenas para metas ativas
     * - Fragment de metas: Lista todas as metas (ativas e inativas)
     * 
     * @param userId ID do usuário
     * @return Result<List<Goal>> - Lista de metas ativas
     */
    suspend fun getGoals(userId: String): Result<List<Goal>> {
        return try {
            // Busca nó "goals" do usuário
            val snapshot = database.child(userId).child("goals").get().await()
            val goals = mutableListOf<Goal>()
            
            // Converte cada meta do Firebase para objeto Goal
            snapshot.children.forEach { child ->
                val goalMap = child.value as? Map<*, *>
                if (goalMap != null) {
                    @Suppress("UNCHECKED_CAST")
                    // Goal.fromMap() reconstrói objeto Goal
                    goals.add(Goal.fromMap(goalMap as Map<String, Any?>))
                }
            }
            
            // Filtra apenas metas ativas (isActive = true)
            Result.success(goals.filter { it.isActive })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create goal
     */
    suspend fun createGoal(userId: String, goal: Goal): Result<String> {
        return try {
            val goalId = goal.id.ifEmpty {
                database.child(userId).child("goals").push().key ?: throw Exception("Erro ao gerar ID")
            }
            
            val goalWithId = goal.copy(
                id = goalId,
                userId = userId,
                createdAt = java.time.Instant.now().toString()
            )
            
            database.child(userId).child("goals").child(goalId).setValue(goalWithId.toMap()).await()
            Result.success(goalId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update goal with goalId parameter
     */
    suspend fun updateGoal(userId: String, goalId: String, goal: Goal): Result<Unit> {
        return try {
            database.child(userId).child("goals").child(goalId).setValue(goal.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete goal
     */
    suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> {
        return try {
            database.child(userId).child("goals").child(goalId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calcula Progresso das Metas para o Mês Atual
     * 
     * PROPÓSITO:
     * Calcula quanto foi gasto em cada categoria que tem meta
     * e determina o status (NORMAL, WARNING, EXCEEDED)
     * 
     * FLUXO:
     * 1. Verifica se tem fatura (se não tem, retorna lista vazia)
     * 2. Busca todas as metas ativas do usuário
     * 3. Agrupa despesas da fatura por categoria
     * 4. Para cada meta:
     *    - Busca total gasto na categoria da meta
     *    - Calcula percentual: (gasto / limite) * 100
     *    - Determina status baseado no percentual
     * 5. Retorna lista de GoalProgress
     * 
     * MATCHING DE CATEGORIA:
     * - Usa goal.category (nome da categoria) para buscar em expensesByCategory
     * - invoice.getExpensesByCategory() retorna Map: categoria → total gasto
     * - Se categoria não tem despesas, spent = 0.0
     * 
     * STATUS:
     * - NORMAL: percentual < 80% (verde)
     * - WARNING: 80% <= percentual < 100% (amarelo)
     * - EXCEEDED: percentual >= 100% (vermelho)
     * 
     * @param userId ID do usuário
     * @param invoice Fatura do mês atual (pode ser null)
     * @return Result<List<GoalProgress>> - Lista de progresso de cada meta
     */
    suspend fun calculateGoalProgress(userId: String, invoice: Invoice?): Result<List<GoalProgress>> {
        return try {
            // Se não tem fatura, não tem como calcular progresso
            if (invoice == null) {
                return Result.success(emptyList())
            }
            
            // Busca metas ativas do usuário
            val goals = getGoals(userId).getOrThrow()
            
            // Agrupa despesas da fatura por categoria
            // Retorna: Map<String, Double> (categoria → total gasto)
            // Exemplo: {"Alimentação": 750.50, "Transporte": 320.00}
            val expensesByCategory = invoice.getExpensesByCategory()
            
            // Calcula progresso para cada meta
            val progressList = goals.map { goal ->
                // ========== CÁLCULO DO GASTO ==========
                // Busca total gasto na categoria da meta
                // Se categoria não tem despesas, usa 0.0
                val spent = expensesByCategory[goal.category] ?: 0.0
                
                // ========== CÁLCULO DO PERCENTUAL ==========
                // percentual = (gasto / limite) * 100
                // Exemplo: (400 / 500) * 100 = 80%
                val percentage = (spent / goal.limitValue) * 100
                
                // ========== DETERMINAÇÃO DE STATUS ==========
                // Status baseado em percentual:
                // - >= 100%: EXCEEDED (vermelho) - ultrapassou limite
                // - >= 80%: WARNING (amarelo) - próximo do limite
                // - < 80%: NORMAL (verde) - dentro do limite
                val status = when {
                    percentage >= 100 -> GoalStatus.EXCEEDED
                    percentage >= 80 -> GoalStatus.WARNING
                    else -> GoalStatus.NORMAL
                }
                
                // Cria objeto GoalProgress com todos os dados calculados
                GoalProgress(
                    goal = goal, // Meta original
                    spent = spent, // Valor gasto na categoria
                    percentage = percentage, // Percentual do limite usado
                    status = status // Status (NORMAL, WARNING, EXCEEDED)
                )
            }
            
            Result.success(progressList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
}

