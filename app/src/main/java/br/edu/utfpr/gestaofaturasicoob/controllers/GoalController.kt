package br.edu.utfpr.gestaofaturasicoob.controllers

import br.edu.utfpr.gestaofaturasicoob.models.Goal
import br.edu.utfpr.gestaofaturasicoob.models.GoalProgress
import br.edu.utfpr.gestaofaturasicoob.models.Invoice
import br.edu.utfpr.gestaofaturasicoob.services.GoalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controller: GoalController
 * Manages goal operations between View and GoalService
 */
class GoalController {
    
    suspend fun getGoals(userId: String): Result<List<Goal>> {
        return withContext(Dispatchers.IO) {
            GoalService.getGoals(userId)
        }
    }
    
    suspend fun createGoal(userId: String, goal: Goal): Result<String> {
        return withContext(Dispatchers.IO) {
            if (goal.category.isBlank()) {
                return@withContext Result.failure(Exception("Categoria é obrigatória"))
            }
            if (goal.limitValue <= 0) {
                return@withContext Result.failure(Exception("Valor limite deve ser maior que zero"))
            }
            GoalService.createGoal(userId, goal)
        }
    }
    
    suspend fun updateGoal(userId: String, goalId: String, goal: Goal): Result<Unit> {
        return withContext(Dispatchers.IO) {
            GoalService.updateGoal(userId, goalId, goal)
        }
    }
    
    suspend fun deleteGoal(userId: String, goalId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            GoalService.deleteGoal(userId, goalId)
        }
    }
    
    suspend fun calculateGoalProgress(userId: String, invoice: Invoice?): Result<List<GoalProgress>> {
        return withContext(Dispatchers.IO) {
            GoalService.calculateGoalProgress(userId, invoice)
        }
    }
}

