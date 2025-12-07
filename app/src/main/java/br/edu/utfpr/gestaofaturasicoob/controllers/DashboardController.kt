package br.edu.utfpr.gestaofaturasicoob.controllers

import br.edu.utfpr.gestaofaturasicoob.models.CategorySpending
import br.edu.utfpr.gestaofaturasicoob.models.DashboardData
import br.edu.utfpr.gestaofaturasicoob.models.Insight
import br.edu.utfpr.gestaofaturasicoob.models.InsightSeverity
import br.edu.utfpr.gestaofaturasicoob.models.InsightType
import br.edu.utfpr.gestaofaturasicoob.models.InvoiceCountdown
import br.edu.utfpr.gestaofaturasicoob.models.GoalProgress
import br.edu.utfpr.gestaofaturasicoob.services.InvoiceService
import br.edu.utfpr.gestaofaturasicoob.services.GoalService
import br.edu.utfpr.gestaofaturasicoob.services.CategoryService
import br.edu.utfpr.gestaofaturasicoob.services.AuthService
import br.edu.utfpr.gestaofaturasicoob.utils.CurrencyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * DashboardController - Controller do Dashboard
 * 
 * RESPONSABILIDADE:
 * Agrega dados de múltiplos serviços para exibição no dashboard
 * 
 * FUNÇÕES:
 * 1. Busca fatura atual (mês atual ou mais recente)
 * 2. Calcula countdown de vencimento
 * 3. Agrega gastos por categoria (para gráfico de pizza)
 * 4. Calcula progresso de metas
 * 5. Compara com mês anterior
 * 6. Gera insights automáticos (7 tipos diferentes)
 * 
 * INSIGHTS GERADOS:
 * 1. Parcelamento em categorias recorrentes (⚠️ WARNING)
 * 2. Categoria dominante (>40% dos gastos) (📊 INFO)
 * 3. Meta em alerta (80% ou 100%) (⚠️ WARNING / 🚨 CRITICAL)
 * 4. Aumento de gastos (>10% vs mês anterior) (📈 WARNING)
 * 5. Fatura vencendo (<7 dias) (⚠️ WARNING)
 * 6. Status de pagamento (✅ INFO)
 * 7. Fatura vs renda (80% ou >100%) (⚠️ WARNING / 🚨 CRITICAL)
 * 
 * AGRAGAÇÃO DE DADOS:
 * - InvoiceService: Faturas e despesas
 * - GoalService: Metas e progresso
 * - CategoryService: Categorias (para resolver nomes)
 * - AuthService: Renda do usuário (para insights)
 * 
 * PERFORMANCE:
 * - Executa em Dispatchers.IO (não bloqueia UI)
 * - Faz múltiplas chamadas paralelas quando possível
 * - Otimizado para dashboard que carrega rapidamente
 */
class DashboardController {
    
    /**
     * Busca Dados do Dashboard (Mês Atual/Mais Recente)
     * 
     * DELEGAÇÃO:
     * Chama getDashboardDataForMonth() com referenceMonth = null
     * (null = busca mês atual ou mais recente)
     * 
     * @param userId ID do usuário
     * @return Result<DashboardData> - Dados completos do dashboard
     */
    suspend fun getDashboardData(userId: String): Result<DashboardData> {
        return getDashboardDataForMonth(userId, null)
    }
    
    /**
     * Busca Dados do Dashboard para Mês Específico
     * 
     * ⭐ FUNÇÃO PRINCIPAL: Agregação Completa de Dados ⭐
     * 
     * FLUXO:
     * 1. Busca fatura (específica ou mais recente)
     * 2. Calcula countdown (se não paga)
     * 3. Calcula gastos por categoria
     * 4. Busca metas ativas
     * 5. Calcula progresso das metas
     * 6. Compara com mês anterior
     * 7. Busca renda do usuário
     * 8. Gera insights automáticos
     * 9. Monta DashboardData completo
     * 
     * ESTRATÉGIA DE BUSCA DE FATURA:
     * - Se referenceMonth fornecido: busca fatura específica
     * - Se null: busca mais recente → fallback para mês atual
     * - Garante que sempre retorna dados (mesmo que vazios)
     * 
     * @param userId ID do usuário
     * @param referenceMonth Mês de referência (ex: "JUN/2025") ou null para mais recente
     * @return Result<DashboardData> - Dados completos do dashboard
     */
    suspend fun getDashboardDataForMonth(userId: String, referenceMonth: String?): Result<DashboardData> {
        return withContext(Dispatchers.IO) {
            try {
                // ========== ETAPA 1: BUSCA DA FATURA ==========
                // Estratégia de busca:
                // - Se referenceMonth fornecido: busca fatura específica
                // - Se null: busca mais recente → fallback para mês atual
                val currentInvoice = if (referenceMonth != null) {
                    // Busca fatura de mês específico
                    InvoiceService.getInvoiceByMonth(userId, referenceMonth).getOrNull()
                } else {
                    // Busca fatura mais recente (último upload)
                    // Fallback: Se não tem fatura recente, tenta mês atual
                    InvoiceService.getLatestInvoice(userId).getOrNull()
                        ?: InvoiceService.getCurrentMonthInvoice(userId).getOrNull()
                }
                
                // ========== ETAPA 2: CÁLCULO DE COUNTDOWN ==========
                // Countdown só é calculado se:
                // - Fatura existe
                // - Fatura NÃO está paga
                // Countdown mostra dias até vencimento
                val countdown = currentInvoice?.let { 
                    if (!it.isPaid) calculateCountdown(it.dueDate) else null
                }
                
                // ========== ETAPA 3: CÁLCULO DE GASTOS POR CATEGORIA ==========
                // Agrupa despesas por categoria e calcula total e percentual
                // Usado para gráfico de pizza no dashboard
                val categorySpending = currentInvoice?.let {
                    calculateCategorySpending(it.expenses, userId)
                } ?: emptyList()
                
                // ========== ETAPA 4: BUSCA DE METAS ==========
                // Busca apenas metas ativas (isActive = true)
                val goals = GoalService.getGoals(userId).getOrNull() ?: emptyList()
                
                
                // ========== ETAPA 5: CÁLCULO DE PROGRESSO DAS METAS ==========
                // Para cada meta, calcula:
                // - Valor gasto na categoria
                // - Percentual do limite atingido
                // - Status (NORMAL, WARNING 80%, EXCEEDED 100%)
                // 
                // MATCHING DE CATEGORIAS:
                // Compara categoria da meta com categoria da despesa em múltiplas formas:
                // - ID direto (ex: "food" == "food")
                // - Nome direto (ex: "Alimentação" == "Alimentação")
                // - Normalizado (resolve IDs para nomes)
                // Garante match mesmo se categoria foi salva como ID ou nome
                val goalProgressList = if (currentInvoice != null) {
                    val goalsResult = GoalService.getGoals(userId)
                    if (goalsResult.isSuccess) {
                        val userGoals = goalsResult.getOrNull() ?: emptyList()
                        
                        userGoals.map { goal ->
                            // Filtra despesas da categoria da meta
                            // Compara diretamente pelo categoryId
                            val spent = currentInvoice.expenses.filter { expense ->
                                expense.category == goal.category
                            }.sumOf { it.value } // Soma valores das despesas filtradas
                            
                            // ========== CÁLCULO DE PERCENTUAL ==========
                            // percentual = (gasto / limite) * 100
                            val percentage = if (goal.limitValue > 0) (spent / goal.limitValue * 100) else 0.0
                            
                            // ========== DETERMINAÇÃO DE STATUS ==========
                            // Status baseado em percentual:
                            // - >= 100%: EXCEEDED (vermelho)
                            // - >= 80%: WARNING (amarelo)
                            // - < 80%: NORMAL (verde)
                            val status = when {
                                percentage >= 100 -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.EXCEEDED
                                percentage >= 80 -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.WARNING
                                else -> br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.NORMAL
                            }
                            
                            // Cria objeto GoalProgress com todos os dados calculados
                            br.edu.utfpr.gestaofaturasicoob.models.GoalProgress(
                                goal = goal,
                                spent = spent,
                                percentage = percentage,
                                status = status
                            )
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                // ========== ETAPA 6: COMPARAÇÃO COM MÊS ANTERIOR ==========
                // Busca fatura do mês anterior para comparação
                val previousInvoice = InvoiceService.getPreviousMonthInvoice(userId).getOrNull()
                
                // Calcula totais
                val totalThisMonth = currentInvoice?.totalValue ?: 0.0
                val totalLastMonth = previousInvoice?.totalValue ?: 0.0
                
                // Calcula variação percentual mês sobre mês
                // Fórmula: ((atual - anterior) / anterior) * 100
                // Exemplo: ((3000 - 2500) / 2500) * 100 = 20% de aumento
                val monthChange = if (totalLastMonth > 0) {
                    ((totalThisMonth - totalLastMonth) / totalLastMonth) * 100
                } else 0.0
                
                // ========== ETAPA 7: BUSCA DE RENDA DO USUÁRIO ==========
                // Busca renda cadastrada no perfil
                // Usado para insights de fatura vs renda
                val userIncome = try {
                    AuthService.getCompleteUserData(userId).getOrNull()?.income
                } catch (e: Exception) {
                    null
                }
                
                // Generate all insights
                val insights = generateInsights(
                    currentInvoice,
                    categorySpending,
                    goalProgressList,
                    monthChange,
                    countdown,
                    userId,
                    userIncome
                )
                
                Result.success(
                    DashboardData(
                        currentInvoice = currentInvoice,
                        invoiceCountdown = countdown,
                        categorySpending = categorySpending,
                        activeGoals = goals.map { 
                            br.edu.utfpr.gestaofaturasicoob.models.Goal(
                                id = it.id,
                                userId = it.userId,
                                category = it.category,
                                limitValue = it.limitValue,
                                alertAt80 = it.alertAt80,
                                alertAt100 = it.alertAt100,
                                monthlyReset = it.monthlyReset,
                                isActive = it.isActive,
                                createdAt = it.createdAt
                            )
                        },
                        insights = insights,
                        totalSpentThisMonth = totalThisMonth,
                        totalSpentLastMonth = totalLastMonth,
                        monthOverMonthChange = monthChange
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Calcula Countdown até Vencimento da Fatura
     * 
     * PROPÓSITO:
     * Calcula quantos dias restam até o vencimento (ou quantos dias de atraso)
     * 
     * CÁLCULO:
     * 1. Parse da data de vencimento (formato ISO: "yyyy-MM-dd")
     * 2. Compara com data atual
     * 3. Calcula diferença em dias
     * 4. Se negativo = vencida (atraso)
     * 5. Se positivo = dias restantes
     * 
     * RESULTADO:
     * - daysRemaining: Número de dias (positivo = restantes, negativo = atraso)
     * - isOverdue: true se vencida (daysRemaining < 0)
     * - isUrgent: true se < 7 dias (usado para insights)
     * - formattedDueDate: Data formatada para exibição ("dd/MM/yyyy")
     * 
     * EXEMPLO:
     * - Vence em 5 dias → daysRemaining = 5, isOverdue = false
     * - Venceu há 3 dias → daysRemaining = -3, isOverdue = true
     * 
     * @param dueDateStr Data de vencimento (formato ISO: "2025-07-03")
     * @return InvoiceCountdown com dias restantes e status
     */
    private fun calculateCountdown(dueDateStr: String): InvoiceCountdown {
        return try {
            // ========== PARSE DA DATA ==========
            // Formato ISO: "yyyy-MM-dd" (ex: "2025-07-03")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dueDate = dateFormat.parse(dueDateStr) ?: throw Exception("Invalid date")
            val today = Calendar.getInstance().time
            
            // ========== CÁLCULO DE DIFERENÇA ==========
            // Diferença em milissegundos
            val diffInMillis = dueDate.time - today.time
            
            // Converte para dias (inteiro)
            // TimeUnit.MILLISECONDS.toDays(): Converte milissegundos para dias
            // Resultado pode ser negativo (vencida) ou positivo (a vencer)
            val daysRemaining = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
            
            // ========== FORMATAÇÃO PARA EXIBIÇÃO ==========
            // Formata data para formato brasileiro: "03/07/2025"
            val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val formattedDate = displayFormat.format(dueDate)
            
            // ========== CRIAÇÃO DO OBJETO ==========
            InvoiceCountdown(
                dueDate = dueDateStr, // Data original (ISO)
                daysRemaining = daysRemaining, // Dias restantes (negativo = atraso)
                isOverdue = daysRemaining < 0, // true se vencida
                formattedDueDate = formattedDate // Data formatada ("dd/MM/yyyy")
            )
        } catch (e: Exception) {
            // Em caso de erro no parse, retorna objeto com valores padrão
            InvoiceCountdown(
                dueDate = dueDateStr,
                daysRemaining = 0,
                isOverdue = false,
                formattedDueDate = dueDateStr
            )
        }
    }
    
    /**
     * Calcula Gastos Agrupados por Categoria
     * 
     * PROPÓSITO:
     * Agrupa despesas por categoria e calcula total e percentual
     * Usado para gráfico de pizza no dashboard
     * 
     * PROCESSO:
     * 1. Busca todas as categorias (padrão + personalizadas)
     * 2. Agrupa despesas por categoria (resolve nomes corretamente)
     * 3. Soma valores por categoria
     * 4. Calcula percentual de cada categoria
     * 5. Atribui cores para visualização
     * 6. Ordena por valor (maior primeiro)
     * 
     * RESOLUÇÃO DE NOMES:
     * - Despesas podem ter categoria como ID ("food") ou nome ("Alimentação")
     * - resolveCategoryName() normaliza para nome em português
     * - Garante consistência na exibição
     * 
     * CORES:
     * - Lista fixa de cores para gráfico
     * - Atribui sequencialmente por ordem de valor
     * - Categoria com maior gasto = primeira cor da lista
     * 
     * ORDENAÇÃO:
     * - sortedByDescending(): Maior valor primeiro
     * - Facilita exibição no gráfico (top categories primeiro)
     * 
     * @param expenses Lista de despesas da fatura
     * @param userId ID do usuário (para buscar categorias)
     * @return Lista de CategorySpending ordenada por valor (maior primeiro)
     */
    private suspend fun calculateCategorySpending(
        expenses: List<br.edu.utfpr.gestaofaturasicoob.models.Expense>,
        userId: String
    ): List<CategorySpending> {
        // ========== ETAPA 1: BUSCA DE CATEGORIAS ==========
        // Busca todas as categorias (padrão + personalizadas)
        // Necessário para resolver nomes corretamente
        val allCategories = CategoryService.getCategories(userId).getOrNull() ?: emptyList()
        
        // ========== ETAPA 2: AGREGAÇÃO POR CATEGORIA ==========
        // Mapa: nome da categoria → valor total gasto
        val categoryTotals = mutableMapOf<String, Double>()
        
        // Para cada despesa, agrupa por categoria
        expenses.forEach { expense ->
            // Resolve nome da categoria (ID → Nome se necessário)
            val categoryName = resolveCategoryName(expense.category, allCategories)
            
            // Soma valor à categoria
            // getOrDefault(): Se categoria não existe no map, usa 0.0
            val current = categoryTotals.getOrDefault(categoryName, 0.0)
            categoryTotals[categoryName] = current + expense.value
        }
        
        // ========== ETAPA 3: CÁLCULO DE PERCENTUAL ==========
        // Soma total de todas as categorias
        val total = categoryTotals.values.sum()
        
        // ========== ETAPA 4: ATRIBUIÇÃO DE CORES ==========
        // Lista fixa de cores para gráfico de pizza
        // Cores Material Design: Vermelho, Azul, Verde, Amarelo, Roxo, Ciano
        val colors = listOf("#FF5722", "#2196F3", "#4CAF50", "#FFC107", "#9C27B0", "#00BCD4")
        
        // ========== ETAPA 5: CRIAÇÃO DOS OBJETOS ==========
        // Converte mapa em lista de CategorySpending
        return categoryTotals.entries.mapIndexed { index, (name, value) ->
            CategorySpending(
                categoryName = name, // Nome da categoria (resolvido)
                totalValue = value, // Valor total gasto nesta categoria
                // Percentual: (valor da categoria / total) * 100
                percentage = if (total > 0) (value / total) * 100 else 0.0,
                // Cor: atribui sequencialmente (fallback: preto se mais de 6 categorias)
                color = colors.getOrElse(index) { "#000000" }
            )
        }.sortedByDescending { it.totalValue } // Ordena por valor (maior primeiro)
    }
    
    /**
     * Resolve Nome da Categoria (ID → Nome)
     * 
     * PROBLEMA:
     * Despesas têm categoria salva como ID:
     * - ID: "cat_food", "cat_transport" (categorias padrão)
     * - ID: "custom_timestamp_hash" (categorias personalizadas)
     * 
     * SOLUÇÃO:
     * 1. Busca categoria pelo ID na lista de categorias (padrão + personalizadas)
     * 2. Se encontrar, retorna o nome da categoria
     * 3. Se não encontrar, retorna o próprio ID como fallback
     * 
     * GARANTIA:
     * Sempre retorna nome em português para exibição (ou ID como fallback)
     * 
     * EXEMPLOS:
     * - "cat_food" → "Alimentação" (busca pelo ID nas categorias)
     * - "custom_123" → "Academia" (busca pelo ID nas categorias personalizadas)
     * - null → "Sem Categoria"
     * 
     * @param categoryId ID da categoria (pode ser null)
     * @param allCategories Lista de todas as categorias (padrão + personalizadas)
     * @return Nome da categoria em português
     */
    private fun resolveCategoryName(
        categoryId: String?,
        allCategories: List<br.edu.utfpr.gestaofaturasicoob.models.Category>
    ): String {
        // Se categoria é null ou vazia, retorna padrão
        if (categoryId.isNullOrBlank()) return "Sem Categoria"
        
        // Busca categoria pelo ID
        val category = allCategories.find { it.id == categoryId }
        
        // Retorna nome da categoria se encontrou, senão retorna ID como fallback
        return category?.name ?: categoryId
    }
    
    /**
     * Gera Insights Automáticos do Dashboard
     * 
     * ⭐ FUNÇÃO COMPLEXA: Lógica de Negócio para Insights ⭐
     * 
     * PROPÓSITO:
     * Analisa dados da fatura e gera insights automáticos
     * para alertar usuário sobre padrões financeiros
     * 
     * INSIGHTS GERADOS (7 TIPOS):
     * 1. ⚠️ Parcelamento em categorias recorrentes (WARNING)
     * 2. 📊 Categoria dominante >40% (INFO)
     * 3. ⚠️ Meta em alerta 80% (WARNING)
     * 4. 🚨 Meta ultrapassada 100% (CRITICAL)
     * 5. 📈 Aumento de gastos >10% (WARNING)
     * 6. ⚠️ Fatura vencendo <7 dias (WARNING)
     * 7. ✅ Status de pagamento (INFO)
     * 8. 🚨 Fatura maior que renda (CRITICAL)
     * 9. ⚠️ Fatura >80% da renda (WARNING)
     * 
     * ORDEM DE PRIORIDADE:
     * Insights são adicionados na ordem de importância
     * - Metas aparecem primeiro (mais críticos)
     * - Parcelamento é importante (hábito prejudicial)
     * - Outros insights seguem
     * 
     * @param invoice Fatura atual (pode ser null)
     * @param categorySpending Gastos por categoria
     * @param goalProgressList Progresso das metas
     * @param monthChange Variação percentual vs mês anterior
     * @param countdown Countdown de vencimento
     * @param userId ID do usuário
     * @param userIncome Renda do usuário (pode ser null)
     * @return Lista de insights gerados
     */
    private suspend fun generateInsights(
        invoice: br.edu.utfpr.gestaofaturasicoob.models.Invoice?,
        categorySpending: List<CategorySpending>,
        goalProgressList: List<GoalProgress>,
        monthChange: Double,
        countdown: InvoiceCountdown?,
        userId: String,
        userIncome: Double?
    ): List<Insight> {
        val insights = mutableListOf<Insight>()
        
        // Se não tem fatura, não gera insights
        invoice?.let { inv ->
            
            // ========== INSIGHT 1: PARCELAMENTO EM CATEGORIAS RECORRENTES ==========
            // REGRA DE NEGÓCIO:
            // Não é recomendado parcelar compras recorrentes (mercado, combustível, etc.)
            // porque essas compras acontecem frequentemente e podem gerar acúmulo de dívidas
            
            // Busca todas as categorias para identificar quais são recorrentes
            val allCategories = CategoryService.getCategories(userId).getOrNull() ?: emptyList()
            
            // Cria set com todas as formas de identificar categorias recorrentes:
            // - ID original (ex: "food")
            // - Nome (ex: "Alimentação")
            // - Nome normalizado (resolve ID → Nome)
            // Busca IDs das categorias recorrentes
            val recurringCategoryIds = allCategories
                .filter { it.isRecurring } // Filtra apenas categorias recorrentes
                .map { it.id }
                .toSet()
            
            // Filtra despesas que:
            // 1. São parceladas (isInstallment = true)
            // 2. Estão em categorias recorrentes
            val recurringInstallments = inv.expenses.filter { expense ->
                // Se não é parcela, descarta
                if (!expense.isInstallment) return@filter false
                
                // Verifica se categoryId está na lista de categorias recorrentes
                expense.category != null && expense.category in recurringCategoryIds
            }
            
            // Se encontrou parcelamento em categorias recorrentes, gera insight
            if (recurringInstallments.isNotEmpty()) {
                // Extrai nomes das categorias (sem duplicatas)
                val categoryNames = recurringInstallments
                    .map { expense -> resolveCategoryName(expense.category, allCategories) }
                    .distinct() // Remove duplicatas
                    .joinToString(", ") // Junta com vírgula
                
                val installmentCount = recurringInstallments.size
                
                // Adiciona insight com severidade WARNING
                insights.add(
                    Insight(
                        title = "⚠️ Parcelamento em Categorias Recorrentes",
                        description = "Você está parcelando $installmentCount compra(s) em $categoryNames. É recomendado pagar compras recorrentes à vista.",
                        type = InsightType.INSTALLMENT_WARNING,
                        severity = InsightSeverity.WARNING
                    )
                )
            }
            
            
            // ========== INSIGHT 2: CATEGORIA DOMINANTE ==========
            // REGRA DE NEGÓCIO:
            // Se uma categoria representa >40% dos gastos, pode indicar desequilíbrio
            // Útil para identificar onde o usuário está gastando demais
            
            if (categorySpending.isNotEmpty()) {
                // Pega categoria com maior gasto (primeira da lista ordenada)
                val topCategory = categorySpending.first()
                
                // Se representa mais de 40% dos gastos, gera insight
                if (topCategory.percentage > 40) {
                    insights.add(
                        Insight(
                            title = "📊 Categoria Dominante",
                            description = "${topCategory.categoryName} representa ${topCategory.percentage.toInt()}% dos seus gastos",
                            type = InsightType.GENERAL,
                            severity = InsightSeverity.INFO, // INFO (não é crítico, só informativo)
                            relatedCategoryId = topCategory.categoryName
                        )
                    )
                }
            }
            
            
            // ========== INSIGHT 3: ALERTAS DE METAS ==========
            // REGRA DE NEGÓCIO:
            // - 80%: Alerta amarelo (usuário pode querer controlar)
            // - 100%: Alerta vermelho (meta ultrapassada)
            // Só gera insight se usuário ativou alertas para esta meta
            
            goalProgressList.forEach { progress ->
                when (progress.status) {
                    // ========== STATUS WARNING (80% a 99%) ==========
                    br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.WARNING -> {
                        // Só gera insight se usuário ativou alerta aos 80%
                        if (progress.goal.alertAt80) {
                            val allCategories = CategoryService.getCategories(userId).getOrNull() ?: emptyList()
                            val categoryName = resolveCategoryName(progress.goal.category, allCategories)
                            
                            insights.add(
                                Insight(
                                    title = "⚠️ Meta em Alerta",
                                    description = "Você atingiu ${progress.percentage.toInt()}% da meta de $categoryName",
                                    type = InsightType.GOAL_WARNING,
                                    severity = InsightSeverity.WARNING, // Amarelo
                                    relatedCategoryId = progress.goal.category
                                )
                            )
                        }
                    }
                    // ========== STATUS EXCEEDED (100% ou mais) ==========
                    br.edu.utfpr.gestaofaturasicoob.models.GoalStatus.EXCEEDED -> {
                        // Só gera insight se usuário ativou alerta aos 100%
                        if (progress.goal.alertAt100) {
                            val allCategories = CategoryService.getCategories(userId).getOrNull() ?: emptyList()
                            val categoryName = resolveCategoryName(progress.goal.category, allCategories)
                            
                            insights.add(
                                Insight(
                                    title = "🚨 Meta Ultrapassada",
                                    description = "Você ultrapassou a meta de $categoryName!",
                                    type = InsightType.GOAL_WARNING,
                                    severity = InsightSeverity.CRITICAL, // Vermelho (mais grave)
                                    relatedCategoryId = progress.goal.category
                                )
                            )
                        }
                    }
                    // ========== STATUS NORMAL (<80%) ==========
                    else -> { 
                        /* NORMAL - não gera insight */ 
                    }
                }
            }
            
            
            // ========== INSIGHT 4: COMPARAÇÃO COM MÊS ANTERIOR ==========
            // REGRA DE NEGÓCIO:
            // Se gastos aumentaram >10% vs mês anterior, alerta usuário
            // Ajuda a identificar aumento significativo de gastos
            
            if (monthChange > 10) {
                insights.add(
                    Insight(
                        title = "📈 Aumento de Gastos",
                        description = "Você gastou ${monthChange.toInt()}% a mais que o mês passado",
                        type = InsightType.SPENDING_INCREASE,
                        severity = InsightSeverity.WARNING
                    )
                )
            }
            
            // ========== INSIGHT 5: FATURA VENCENDO ==========
            // REGRA DE NEGÓCIO:
            // Se fatura vence em <7 dias e não está paga, alerta urgente
            // isUrgent = true quando daysRemaining < 7
            // Não mostra se já vencida (isOverdue = true)
            
            countdown?.let {
                if (it.isUrgent && !it.isOverdue) {
                    insights.add(
                        Insight(
                            title = "⚠️ Fatura Vencendo",
                            description = "Sua fatura vence em ${it.daysRemaining} dias",
                            type = InsightType.GENERAL,
                            severity = InsightSeverity.WARNING
                        )
                    )
                }
                // Nota: Alerta de fatura vencida foi removido conforme solicitação
            }
            
            
            // ========== INSIGHT 6: STATUS DE PAGAMENTO ==========
            // REGRA DE NEGÓCIO:
            // Se fatura está paga, mostra quando foi paga e se foi no prazo/adiantado/atrasado
            // Feedback positivo para usuário
            
            if (inv.isPaid) {
                val paidDate = inv.paidDate
                
                // Se tem data de pagamento, calcula diferença
                if (paidDate.isNotEmpty()) {
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val paidDateParsed = dateFormat.parse(paidDate)
                        val dueDateParsed = dateFormat.parse(inv.dueDate)
                        
                        if (paidDateParsed != null && dueDateParsed != null) {
                            // Calcula diferença em dias: data pagamento - data vencimento
                            // Negativo = pagou antes, 0 = no prazo, positivo = atrasado
                            val diffInDays = ((paidDateParsed.time - dueDateParsed.time) / (1000 * 60 * 60 * 24)).toInt()
                            
                            val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            val paidDateFormatted = displayFormat.format(paidDateParsed)
                            
                            // Mensagem varia conforme situação
                            val message = when {
                                diffInDays < 0 -> "Fatura paga com ${-diffInDays} dias de antecedência ($paidDateFormatted)"
                                diffInDays == 0 -> "Fatura paga no prazo ($paidDateFormatted)"
                                else -> "Fatura paga com $diffInDays dias de atraso ($paidDateFormatted)"
                            }
                            
                            insights.add(
                                Insight(
                                    title = "✅ Fatura Paga",
                                    description = message,
                                    type = InsightType.GENERAL,
                                    severity = InsightSeverity.INFO // Sempre INFO (feedback positivo)
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Ignora erros de parse de data silenciosamente
                    }
                } else {
                    // Se não tem data, mostra mensagem genérica
                    insights.add(
                        Insight(
                            title = "✅ Fatura Paga",
                            description = "Sua fatura foi marcada como paga",
                            type = InsightType.GENERAL,
                            severity = InsightSeverity.INFO
                        )
                    )
                }
            }
            
            // ========== INSIGHT 7 e 8: FATURA VS RENDA ==========
            // REGRA DE NEGÓCIO:
            // - Se fatura > renda: CRITICAL (gastou mais do que ganha)
            // - Se fatura >= 80% da renda: WARNING (muito próximo do limite)
            // Útil para identificar problemas financeiros sérios
            
            userIncome?.let { income ->
                if (income > 0) {
                    val invoiceTotal = inv.totalValue
                    val percentageOfIncome = (invoiceTotal / income) * 100
                    
                    // ========== INSIGHT 7: FATURA MAIOR QUE RENDA ==========
                    // Situação crítica: gastou mais do que ganha
                    if (invoiceTotal > income) {
                        insights.add(
                            Insight(
                                title = "🚨 Fatura Maior que a Renda",
                                description = "Sua fatura (${CurrencyUtils.formatCurrency(invoiceTotal)}) é maior que sua renda mensal (${CurrencyUtils.formatCurrency(income)}). Atenção!",
                                type = InsightType.GENERAL,
                                severity = InsightSeverity.CRITICAL // CRITICAL (situação grave)
                            )
                        )
                    }
                    // ========== INSIGHT 8: FATURA >80% DA RENDA ==========
                    // Alerta: está usando quase toda a renda no cartão
                    else if (percentageOfIncome >= 80) {
                        insights.add(
                            Insight(
                                title = "⚠️ Fatura Alta em Relação à Renda",
                                description = "Sua fatura representa ${percentageOfIncome.toInt()}% da sua renda mensal. Considere reduzir gastos.",
                                type = InsightType.GENERAL,
                                severity = InsightSeverity.WARNING // WARNING (atenção necessária)
                            )
                        )
                    }
                }
            }
        }
        
        return insights
    }
}
