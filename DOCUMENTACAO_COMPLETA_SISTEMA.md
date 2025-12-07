# 📚 Documentação Completa do Sistema — Gestão de Fatura Sicoob

## 1. Visão Geral
- **Nome:** Gestão de Fatura Sicoob (Android)
- **Plataforma:** Android nativo (Kotlin)
- **Arquitetura:** MVC com Service Layer (Views/Controllers/Services/DataSources/Models/Utils)
- **Objetivo:** Ler faturas PDF do Sicoob, extrair compras, categorizar, gerar insights/metas, armazenar no Firebase e exibir dashboards e relatórios.

Principais diferenciais:
- Extração robusta de PDF (iText7) com regex + parsing por coordenadas (X/Y) para separar colunas com precisão e tratar casos especiais.
- Auto-categorização baseada em histórico por estabelecimento.
- Metas mensais com alertas (80% e 100%).
- Insights automáticos (crescimento de gastos, parcelamentos em recorrentes, relação fatura/renda, status de pagamento, etc.).
- Persistência do filtro de mês entre telas e sessões.
- Marcação de fatura paga (com data escolhida) e reflexos no Dashboard.

---

## 2. Tecnologias e Bibliotecas

### 2.1 Linguagem e SDK
- Kotlin 1.9+
- Android SDK API 26+ (minSdk 24, targetSdk 35)

### 2.2 UI/UX
- Material Components (`com.google.android.material:material`)
- ConstraintLayout
- ViewBinding (habilitado em `buildFeatures`)

### 2.3 Firebase
- Firebase BOM + Authentication + Realtime Database + Messaging + Storage
- Regras de segurança em `firebase-database-rules.json`

### 2.4 Navegação
- AndroidX Navigation (fragment-ktx, ui-ktx)

### 2.5 Assíncrono
- Kotlin Coroutines (core + android)

### 2.6 PDF
- iText7: `itext7-core`, `kernel`, `io`, `layout`
  - Extração de texto: `PdfTextExtractor` + `SimpleTextExtractionStrategy`
  - Parsing por posição: `PdfCanvasProcessor` + `IEventListener` + `TextRenderInfo`

### 2.7 Outras
- Glide (carregamento de imagens)
- WorkManager (infra para tarefas futuras)

---

## 3. Estrutura do Projeto (Pastas)

```
app/src/main/java/br/edu/utfpr/gestaofaturasicoob/
├─ controllers/           # Orquestram fluxos entre View e Services
├─ data/datasource/       # Integrações externas (Firebase, PDF)
├─ models/                # Data classes de domínio/app
├─ presentation/          # Fragments/Adapters/Dialogos (Views)
├─ services/              # Regras de negócio e coordenação de dados
├─ utils/                 # Utilitários gerais (datas, moeda, etc.)
└─ MainActivity.kt        # Host e navegação
```

### 3.1 Controllers
- `AuthController.kt`: Login/registro/Google Sign-In; expõe helpers de sessão e update de perfil (nickname, phone, income). Trata mensagens de erro amigáveis via `AuthErrorUtils`.
- `CategoryController.kt`: Coordena CRUD de categorias (padrão e personalizadas); permite excluir/atualizar padrões.
- `DashboardController.kt`: Carrega fatura atual/por mês, calcula countdown, consolida gastos por categoria, metas e gera insights (inclui renda >80% e >100%).
- `GoalController.kt`: CRUD de metas com flags de alerta (80%/100%).
- `InvoiceController.kt`: Parsing + salvamento/exclusão/consulta de faturas (por mês, mais recente, mês anterior) e atualização de pagamento.

### 3.2 Data Sources
- `FirebaseConfig.kt`: inicialização/configs utilitárias.
- `FirebaseManager.kt`: encapsula refs, auth, operações comuns (createOrUpdateUser, getUserData, refs para users etc.).
- `PDFParserDataSourceFixed.kt`: parser completo da fatura Sicoob.
  - Estratégias:
    - Leitura de texto por página + divisão em linhas com regex robustas.
    - Parsing de período para mês de referência: extrai mês explícito (“Esta é a fatura de outubro”) com prioridade; fallback no período `REF DD MMM A DD MMM` subtraindo 1 mês do fim.
    - Seção “MOVIMENTAÇÕES DA CONTA”: captura `ANUIDADE` e `PROTEÇÃO PERDA OU ROUBO`; ignora `SALDO ANTERIOR` e `PAGAMENTO-BOLETO BANCARIO`.
    - Valores negativos (estornos) são mantidos como negativos e marcados como refund (isRefund=true na lógica de alto nível quando aplicável).
    - Parsing por coordenadas (X/Y) para separar colunas Data/Estabelecimento/Cidade/Valor; heurística corrige trocas (estabelecimento↔cidade) e nomes compostos.
  - Resultado: `ExtractedInvoiceData` com cabeçalho e lista de `ExtractedExpenseData`.

### 3.3 Models
- `User`: id, name, email, photoUrl, nickname, phone, income, createdAt, updatedAt.
- `Invoice`: dueDate, totalValue, minimumPayment, referenceMonth, closingDate, expenses, isPaid, paidDate, etc.
- `Expense`: date, description, establishment, city, value, category, installment, isInstallment, autoCategorized.
- `Goal`, `Category`, `CategorySpending`, `Insight`, `DashboardData`, etc.

### 3.4 Presentation (Views)
- `auth/`: `LoginFragment`, `RegisterFragment` — login por email/senha e Google; validação de campos e mensagens amigáveis.
- `dashboard/`: `DashboardFragment`, `InsightsAdapter`, `GoalsAdapter`, `CategoryLegendAdapter` — resumo do mês, countdown/pagamento, insights, metas, pizza simples (ou lista) e UX refinada.
- `invoice/`: `UploadInvoiceFragment`, `InvoicesFragment`, `InvoiceDetailFragment`, `CategorizeExpensesFragment`, `Expense*Adapter` — upload e parsing do PDF, listagem/histórico, detalhamento e categorização.
- `reports/`: `MonthlyReportFragment`, `AnnualReportFragment`, `ReportsFragment` — relatórios mensal/anual, top categorias (estático sem rolagem), evolução mensal redesenhada, sem ícones.
- `categories/`: `ManageCategoriesFragment` — gerenciamento de categorias, sem ícones, com edição/exclusão de padrões.
- `profile/`: `ProfileFragment`, `EditProfileDialog` — perfil sem botões de câmera/config/sobre; permite editar renda, nickname, etc.
- `common/`: `BaseActivity`, `BaseFragment` — utilitários de ciclo de vida/infra.

### 3.5 Services (Regras de Negócio)
- `AuthService`: FirebaseAuth (email/senha, Google), `getCompleteUserData`, `updateUserProfile` (inclui renda). Converte `FirebaseUser` → `User`.
- `InvoiceService`: operações com faturas no Realtime Database; integra com parser; update de status/pagamento.
- `CategoryService`: carrega categorias (padrão + personalizadas), suporta exclusões/atualizações de padrões, resolve nome normalizado.
- `GoalService`: CRUD de metas e leitura para o dashboard.
- `ReportService`: agregações para relatórios (mensal/anual) e exportações futuras.

### 3.6 Utils
- `AuthErrorUtils`: converte `Throwable`/FirebaseAuthException → mensagens amigáveis (email inválido/senha incorreta/conta inexistente, etc.).
- `DateUtils`: formatação e cálculos comuns.
- `CurrencyUtils`: formatação BRL e porcentagens.
- `MonthFilterManager`: persiste o filtro de mês via `SharedPreferences` (usado em Dashboard/Reports).
- `CategoryUtils`: normalização/nomes de categorias (inclui portuguesização e handling de padrões/personalizadas).

---

## 4. Fluxos Principais

### 4.1 Autenticação
1. Usuário faz login (email/senha) ou Google.
2. `LoginFragment` → `AuthController.loginWithEmail/loginWithGoogle()` → `AuthService`.
3. Erros são convertidos por `AuthErrorUtils` e exibidos via Snackbar.
4. Ao registrar, `AuthService.registerWithEmail` cria a conta e persiste dados mínimos em `users/{userId}`.

### 4.2 Upload e Parsing da Fatura (PDF)
1. `UploadInvoiceFragment` seleciona PDF.
2. `InvoiceController.parseInvoicePDF()` → `InvoiceService.parseInvoicePDF()` → `PDFParserDataSourceFixed.parsePDF()`.
3. Parser extrai cabeçalho, despesas e tarifas (com regras de movimentações da conta e estornos).
4. Retorna `ExtractedInvoiceData` para categorização e salvamento.

### 4.3 Categorização
1. Primeira fatura: usuário seleciona categorias manualmente.
2. Sistema aprende mapping estabelecimento → categoria e aplica nas futuras faturas.
3. Categorias padrão + personalizadas; exclusão/edição de padrões suportada.

### 4.4 Pagamento da Fatura
1. Em `InvoiceDetailFragment`, usuário marca `isPaid` e escolhe `paidDate`.
2. Persistido em Firebase; Dashboard exibe status contextual (“paga no prazo/adiantada/atrasada”).

### 4.5 Persistência do Filtro de Mês
1. `MonthFilterManager` salva/recupera a chave do mês escolhido.
2. Dashboard/Reports respeitam o mês persistido entre telas e sessões.

### 4.6 Insights
- Aumento de gastos (>10% vs mês anterior).
- Parcelamento em categorias recorrentes (padrão e personalizadas) — detecta `isRecurring` em todas as categorias do usuário.
- Metas: alertas aos 80% (WARNING) e 100% (CRITICAL).
- Fatura paga (mensagens com base na diferença de dias vs vencimento).
- Relação Fatura x Renda:
  - Fatura > Renda → CRITICAL (🚨)
  - Fatura ≥ 80% da Renda → WARNING (⚠️)

---

## 5. Estrutura do Firebase Realtime Database

```
users/
  {userId}/
    id, name, email, photoUrl, nickname, phone, income, createdAt, updatedAt
    invoices/
      {yyyy-MM}/
        dueDate, totalValue, minimumPayment, referenceMonth, closingDate, uploadedAt, isPaid, paidDate
        expenses/{expenseId}/
          date, description, establishment, city, value, category, installment, isInstallment, autoCategorized, createdAt
    savedCategories/ (mapping estabelecimento→categoria)
    customCategories/{categoryId}/ name, color, isRecurring, createdAt
    goals/{goalId}/ category, limitValue, alertAt80, alertAt100, monthlyReset, createdAt
```

Regras (trecho):
- `users/$userId`: `.read`/`.write` restritos ao próprio usuário.
- `invoices`: valida campos, valores não-negativos (ajustável) — estornos são tratados na camada de parsing/serviço.

---

## 6. Telas e Componentes (Resumo)

- Login/Registro: validações de campos, mensagens de erro amigáveis, Google Sign-In.
- Dashboard: resumo do mês (contagem regressiva ou status pago), pizza/lista por categoria, metas e insights.
- Fatura (Histórico/Detalhe/Upload): fluxo completo de upload → parsing → categorização → salvamento; detalhe com pagamento.
- Relatórios: mensal (top 5 sem rolagem) e anual (evolução mensal redesenhada, 3 linhas fixas + lista restante).
- Categorias: sem ícones, com edição/exclusão de padrões.
- Perfil: edição de renda/nickname/phone; sem câmera/config/sobre.

---

## 7. Códigos e Responsabilidades (por arquivo)

### 7.1 Controllers
- `AuthController`: valida entradas; chama `AuthService`; converte erros com `AuthErrorUtils`; busca/atualiza perfil.
- `InvoiceController`: parsing (PDF) via `InvoiceService` e operações CRUD de faturas.
- `CategoryController`: coordena `CategoryService` para listar/atualizar/excluir categorias.
- `GoalController`: coordena metas via `GoalService`.
- `DashboardController`: agrega dados de fatura/categorias/metas; calcula countdown; chama `AuthService.getCompleteUserData` para obter renda; gera insights (incluindo renda).

### 7.2 Services
- `AuthService`: integra com FirebaseAuth e Database (persistência de perfil); `updateUserProfile` salva `income`/`nickname`/`phone`.
- `InvoiceService`: salva faturas por `{yyyy-MM}`; `updatePaymentStatus` atualiza `isPaid/paidDate`; consulta fatura corrente/anterior/mais recente.
- `CategoryService`: carrega categorias finais (padrão + custom − deletadas), e permite alterar/deletar padrões.
- `GoalService`: CRUD; cálculo das metas em `DashboardController` usa `GoalService.getGoals`.
- `ReportService`: prepara dados para telas de relatório (mensal/anual) e exportação futura.

### 7.3 Data Sources
- `FirebaseManager`: `auth`, `usersRef`, `createOrUpdateUser`, `getUserData`, utilitários.
- `PDFParserDataSourceFixed`: vide seção 3.2 — regras de parsing e robustez.

### 7.4 Models
- Enfoque em data classes simples; `User.fromMap/toMap`; `Invoice` inclui `isPaid` e `paidDate`.

### 7.5 Utils
- `AuthErrorUtils.getErrorMessage(Throwable)`: mapeia códigos FirebaseAuth → mensagens.
- `CurrencyUtils.formatCurrency`, `formatPercentage`, `parseCurrency`.
- `MonthFilterManager`: `saveSelectedMonth/getSelectedMonth/clearSelectedMonth`.
- `CategoryUtils`: nomes normalizados e consistentes.

---

## 8. Regras de Negócio Importantes

- PDF: ignorar linhas com `SALDO ANTERIOR`, `PAGAMENTO-BOLETO BANCARIO`; considerar `ANUIDADE` e `PROTEÇÃO PERDA OU ROUBO` (movimentações) como despesas.
- Estornos: valores negativos entram como despesas negativas; `isRefund=true` em alto nível quando necessário (exibição/relatórios devem tratar o sinal).
- Mês de referência: prioriza frase “Esta é a fatura de {mês}”; fallback no período (mês final − 1).
- Filtro de mês: persistido e respeitado entre telas.
- Insights: renda vs fatura (>100% CRITICAL, ≥80% WARNING), metas (80/100), parcelamento em recorrentes (padrão e custom), aumento >10%, pagamento no prazo/antecipado/atrasado.

---

## 9. Segurança e Permissões

- Regras Firebase garantindo isolamento por `auth.uid`.
- Permissões Android: `INTERNET`, `READ/WRITE_EXTERNAL_STORAGE` (para seleção PDF), `ACCESS_NETWORK_STATE`, `CAMERA` (provider já configurado; botão removido do perfil).

---

## 10. Build/Dependências

Configurações principais em `app/build.gradle.kts`:
- ViewBinding habilitado; minify desabilitado (sem ProGuard)
- Dependências listadas por BOM/versions.toml

Removidos para limpeza:
- Jacoco/Detekt/Ktlint/Sonar (não utilizados)
- ProGuard rules (não aplicadas com minify desabilitado)
- Documentações auxiliares .md/.txt não essenciais

---

## 11. Execução e Testes

### 11.1 Executar
```
./gradlew assembleDebug
./gradlew installDebug
```

### 11.2 Testes
```
./gradlew test
./gradlew connectedAndroidTest
```

---

## 12. Roadmap Futuro (Sugerido)
- Exportação PDF dos relatórios
- Modo escuro
- Biometria
- Múltiplos cartões
- Compartilhamento/backup/sync entre dispositivos
- Gráficos interativos

---

## 13. Perguntas Frequentes (FAQ)
- Como definir renda? Perfil > editar (income). Usado nos insights de renda.
- Como marcar fatura paga? Em Detalhe da Fatura (switch + seleção de data).
- Como persistir o mês escolhido? O sistema usa `MonthFilterManager` com `SharedPreferences`.
- Como tratar estorno? O valor vem negativo e é somado ao total; relatórios somam sinais corretamente.

---

## 14. Conclusão
O sistema está organizado em camadas simples (Views → Controllers → Services → DataSources), com forte separação de responsabilidades, parser de PDF robusto, integração com Firebase segura e UX orientada a insights/metas. A documentação acima cobre todas as partes funcionais e técnicas, fluxos, classes e integrações.

---

## 15. Detalhamento por Camada, Classe e Função

Esta seção documenta cada classe do projeto, seus métodos públicos e responsabilidades. Referências cruzadas indicam como os fluxos se encadeiam.

### 15.1 Controllers (orquestração View → Service)

- AuthController
  - loginWithEmail(email, password): Result<User>
    - Valida entradas; chama AuthService.loginWithEmail.
    - Em falha, converte Throwable → mensagem com AuthErrorUtils.
  - registerWithEmail(email, password, name): Result<User>
    - Valida entradas; chama AuthService.registerWithEmail; trata erro amigável.
  - loginWithGoogle(idToken): Result<User>
    - Encaminha para AuthService.loginWithGoogle; trata erro amigável.
  - logout(), getCurrentUser(), isAuthenticated(), getCurrentUserId()
  - getCompleteUserData(): Result<User>
  - updateUserProfile(nickname, phone, income): Result<Unit>

- InvoiceController
  - parseInvoicePDF(pdfFile): Result<ExtractedInvoiceData>
    - Encaminha para InvoiceService.parseInvoicePDF (que usa o parser iText7).
  - saveInvoice(userId, invoice): Result<String>
  - getInvoices(userId): Result<List<Invoice>>
  - getCurrentMonthInvoice(userId): Result<Invoice?>
  - deleteInvoice(userId, invoiceId): Result<Unit>
  - updatePaymentStatus(userId, invoiceId, isPaid, paidDate?): Result<Unit>

- CategoryController
  - getCategories(userId): Result<List<Category>>
  - saveCategory(userId, category): Result<String>
  - updateCategory(userId, category): Result<Unit>
  - updateDefaultCategory(category): Result<Unit>
  - deleteDefaultCategory(categoryId): Result<Unit>

- GoalController
  - getGoals(userId): Result<List<Goal>>
  - saveGoal(userId, goal): Result<String>
  - updateGoal(userId, goal): Result<Unit>
  - deleteGoal(userId, goalId): Result<Unit>

- DashboardController
  - getDashboardData(userId): Result<DashboardData>
  - getDashboardDataForMonth(userId, referenceMonth): Result<DashboardData>
  - calculateCountdown(dueDate): InvoiceCountdown
  - calculateCategorySpending(expenses): List<CategorySpending>
  - generateInsights(invoice, categorySpending, goalProgressList, monthChange, countdown, userId, userIncome): List<Insight>
    - Verifica: metas (80/100), faturamento vs mês anterior (>10%), parcelamento em recorrentes, status de pagamento, relação fatura/renda (≥80% WARNING, >100% CRITICAL), aviso de vencimento próximo.

### 15.2 Services (regras de negócio + acesso a dados)

- AuthService
  - loginWithEmail, registerWithEmail (cria user no DB), loginWithGoogle (atualiza perfil), logout
  - getCurrentUser(), isAuthenticated(), getCurrentUserId()
  - getCompleteUserData(userId): Result<User>
  - updateUserProfile(userId, nickname, phone, income): Result<Unit>

- InvoiceService
  - parseInvoicePDF(pdfFile): Result<ExtractedInvoiceData> (usa PDFParserDataSourceFixed)
  - saveInvoice(userId, invoice): Result<String>
  - getInvoices(userId), getInvoiceByMonth(userId, yyyy-MM), getLatestInvoice(userId), getCurrentMonthInvoice(userId), getPreviousMonthInvoice(userId)
  - deleteInvoice(userId, invoiceId): Result<Unit>
  - updatePaymentStatus(userId, invoiceId, isPaid, paidDate?): Result<Unit>

- CategoryService
  - getCategories(userId): Result<List<Category>> (merge padrão + custom − deletadas)
  - saveCategory(userId, category), updateCategory(userId, category)
  - updateDefaultCategory(category), deleteDefaultCategory(categoryId)
  - getDeletedDefaultCategories(userId): Result<Set<String>>

- GoalService
  - getGoals(userId), saveGoal(userId, goal), updateGoal(userId, goal), deleteGoal(userId, goalId)

- ReportService
  - Agregações para relatórios (mensal/anual) e base para exportação PDF (futuro).

### 15.3 Data Sources (infra/integrações)

- FirebaseManager
  - auth, usersRef; helpers: createOrUpdateUser(uid, name, email, photoUrl), getUserData(userId), referências para invoices/goals/categories etc.

- PDFParserDataSourceFixed
  - parsePDF(file): Result<ExtractedInvoiceData>
    - Leitura por página; extração de texto com `PdfTextExtractor(SIMPLE)`; fallback/merge com extração posicional (`PdfCanvasProcessor(IEventListener)`).
    - Estratégia por linhas:
      - Detecta cabeçalho, período de referência, vencimento, valores (total/mínimo), fechamento.
      - Seção “MOVIMENTAÇÕES DA CONTA”: aceita variações de acentuação; coleta `ANUIDADE` e `PROTEÇÃO PERDA OU ROUBO`; ignora `SALDO ANTERIOR`/`PAGAMENTO-BOLETO BANCARIO`.
      - Consolida linhas quebradas (até 3 seguintes) até encontrar o valor `R$`.
      - Regex principais (exemplos):
        - Data: `\b\d{2}\s+[A-Z]{3}\b`
        - Valor: `^-?R\$\s*\d{1,3}(?:\.\d{3})*,\d{2}$`
        - Parcela: `\b\d{2}/\d{2}\b`
    - Estratégia por posição (X/Y):
      - Coleta chunks de texto (x, y) e agrupa por y (com threshold ~3) e por ordenação de x.
      - Define faixas de coluna (data, estabelecimento, cidade, valor) e preenche campos robustamente.
      - Heurística para nomes compostos e cidades detectadas incorretamente.
    - Mês de referência: prioriza frase “Esta é a fatura de {mês}”; fallback período (mês fim − 1).
    - Estornos: valores negativos mantidos (refund); não são descartados.

---

## 16. Modelos de Dados (Campos e Semântica)

- User
  - id, name, email, photoUrl?, nickname?, phone?, income?, createdAt, updatedAt?
  - income (Double?): usado nos insights de renda.

- Invoice
  - dueDate (yyyy-MM-dd), totalValue, minimumPayment, referenceMonth (MMM/YYYY ou YYYY-MM), closingDate
  - isPaid (Boolean), paidDate (yyyy-MM-dd)
  - expenses: List<Expense>

- Expense
  - date (yyyy-MM-dd ou convertido), description, establishment, city, value (Double, pode ser negativo p/ estorno), category?, installment?, isInstallment (Boolean), autoCategorized (Boolean)

- Goal
  - category (nome/id), limitValue (Double), alertAt80, alertAt100, monthlyReset, createdAt

- Category
  - id, name, color, isRecurring, createdAt

- DashboardData
  - currentInvoice, invoiceCountdown?, categorySpending[], insights[], goals[]

- Insight
  - title, description, type (enum), severity (INFO/WARNING/CRITICAL), relatedCategoryId?

---

## 17. Fluxos End-to-End

### 17.1 Upload → Parsing → Categorização → Salvamento
1. UploadInvoiceFragment seleciona PDF.
2. InvoiceController.parseInvoicePDF → InvoiceService.parseInvoicePDF → PDFParserDataSourceFixed.parsePDF.
3. Retorno de ExtractedInvoiceData exibido para categorização (manual/auto).
4. Ao salvar, InvoiceService.saveInvoice persiste em `users/{userId}/invoices/{yyyy-MM}`.

### 17.2 Dashboard
1. DashboardFragment captura userId e (opcional) mês persistido pelo MonthFilterManager.
2. DashboardController.getDashboardDataForMonth
   - Busca fatura (mês escolhido, mais recente ou corrente), goals, categorias e renda do usuário.
   - Calcula countdown e gastos por categoria.
   - Gera insights (metas, variação mensal, parcelamento recorrente, pagamento, renda vs fatura, vencimento próximo).

### 17.3 Pagamento da Fatura
- Em InvoiceDetailFragment, o usuário liga o switch “Paga” e seleciona a data.
- InvoiceController.updatePaymentStatus → InvoiceService.updatePaymentStatus (atualiza `isPaid` e `paidDate`).
- Dashboard reflete “paga no prazo/adiantada/atrasada” conforme diferença de dias.

---

## 18. Navegação e Estado

- Navigation Component com `NavHostFragment` e BottomNavigation.
- Ao clicar na BottomNavigation, a pilha é limpa para levar à raiz de cada seção.
- MonthFilterManager persiste o mês selecionado via SharedPreferences; aplicado em Dashboard e Reports.
- Fragments utilizam ViewBinding e checks de nulidade de `_binding` para evitar NPE.

---

## 19. Tratamento de Erros e Mensagens

- AuthErrorUtils
  - Recebe Throwable (inclui FirebaseAuthException) e retorna mensagens amigáveis.
  - Códigos cobertos: INVALID_EMAIL, WRONG_PASSWORD, USER_NOT_FOUND, EMAIL_ALREADY_IN_USE, WEAK_PASSWORD, TOO_MANY_REQUESTS, NETWORK_REQUEST_FAILED, etc.
- Em Login/Register, erros são mostrados via Snackbar (mensagens claras).
- Em Dashboard/Relatórios, erros são exibidos via `showError` e UI de estado vazio.

---

## 20. Desempenho e Otimizações

- Parsing otimizado por página; logs de 50 primeiras linhas para diagnóstico (desativável em produção).
- Extração por posição apenas quando necessário (mescla de resultados e de-duplicação por (data|estab|valor)).
- RecyclerViews com adapters leves (lista horizontal de insights; metas em coluna).
- Cálculos de agregação simples e locais.

---

## 21. Segurança, Privacidade e Regras

- Firebase Realtime Database Rules: leitura/escrita restritas a `auth.uid`.
- PDF não é armazenado após parsing; apenas dados extraídos são salvos.
- Tráfego HTTPS (Firebase default).
- Logout limpa sessão.

---

## 22. Testes (Recomendado)

- Unit tests
  - PDFParserDataSourceFixed: datas, valores negativos, períodos, seção de movimentações, parcelamento, multi-linha.
  - AuthErrorUtils: mapeamento de códigos de erro.
  - DashboardController: geração de insights (metas, renda, variação, parcelamento).
- Integração
  - Upload → Parsing → Salvamento Firebase
  - Categorização → Auto-cat em fatura seguinte
  - Metas (80/100) → insights

---

## 23. Operação e Manutenção

- Logs de parsing podem ser reduzidos para produção.
- Atualizações de categorias padrão e exclusões persistem em `deletedDefaultCategories`.
- Evolução: adicionar exportação PDF em ReportService, gráficos, dark mode.

---

## 24. Glossário Rápido
- Fatura: documento PDF mensal do cartão Sicoob.
- Despesa: linha de compra (ou tarifa) extraída da fatura.
- Estorno: valor negativo (crédito na fatura) tratado como despesa negativa.
- Categoria recorrente: marcada com isRecurring (inclui padrões e customizadas do usuário).
- Insight: mensagem derivada de análise (metas, renda, parcelamentos, etc.).

---

## 25. FAQ Técnico
- Q: Como o mês de referência é determinado?
  - A: Primeiro tenta frase “Esta é a fatura de {mês}”; se não houver, usa o período `DD MMM A DD MMM` e subtrai 1 mês do fim.
- Q: Como separar estabelecimento e cidade corretamente?
  - A: Parsing por coordenadas X/Y com faixas de coluna + heurísticas de correção.
- Q: Como detectar parcelamento?
  - A: Padrões `\b\d{2}/\d{2}\b` e/ou tokens na descrição; `isInstallment` no Expense.
- Q: Como são calculados os insights de renda?
  - A: Busca `income` do usuário; compara `totalValue` com `income` (≥80% WARNING; >100% CRITICAL).

---

## 26. Índice de Arquivos (Resumo)

- controllers/: 5 arquivos (Auth, Category, Dashboard, Goal, Invoice)
- data/datasource/: 3 arquivos (FirebaseConfig, FirebaseManager, PDFParserDataSourceFixed)
- models/: 9 arquivos (User, Invoice, Expense, Category, Goal, Insight, DashboardData, CategorySpending, ExtractedInvoiceData)
- presentation/: auth, dashboard, invoice, reports, categories, profile, common (Fragments/Adapters/Dialogs)
- services/: 5 arquivos (Auth, Category, Goal, Invoice, Report)
- utils/: 5 arquivos (AuthErrorUtils, CategoryUtils, CurrencyUtils, DateUtils, MonthFilterManager)

---

## 27. Encerramento

Este documento consolida todo o conhecimento técnico e funcional do sistema, com foco em clareza e profundidade. Ele pode servir como base de TCC, onboarding e manutenção evolutiva. Para qualquer dúvida, consulte os controladores e serviços conforme os fluxos descritos nas seções 15–17.

---

## 28. Guia Para Leigos (Passo a Passo Simples)

Esta seção explica tudo em linguagem simples, para qualquer pessoa entender e usar.

### 28.1 O que é este app?
- Um aplicativo que lê sua fatura do cartão Sicoob (arquivo PDF), entende suas compras automaticamente e mostra seus gastos por categoria (como Alimentação, Transporte etc.).
- Ele aprende como você categoriza para fazer isso sozinho no futuro.
- Ele avisa quando seus gastos estão altos ou quando você está perto de estourar metas.

### 28.2 O que eu preciso para usar?
- Uma conta do Google ou um e‑mail e senha para entrar.
- A fatura do Sicoob em PDF no seu celular.

### 28.3 Como usar (primeira vez)
1) Abra o app e faça login (Google ou e‑mail/senha).
2) Na tela inicial, toque em “Nova Fatura”.
3) Escolha o PDF da sua fatura.
4) Aguarde alguns segundos: o app lê sua fatura e mostra as compras na tela.
5) Categorize as compras (ex.: “CAFE DA ANA” → Alimentação). Na próxima fatura, ele lembrará disso sozinho.
6) Salve a fatura.
7) Volte ao “Dashboard” para ver o resumo do mês.

### 28.4 Como usar (faturas seguintes)
- Repita os passos. Agora o app já identifica a maioria das categorias automaticamente. Você só revisa e salva.

### 28.5 Como marcar minha fatura como paga
- Abra a fatura (detalhes), ative o botão “Paga” e escolha a data que você pagou.
- O app mostra no Dashboard se foi paga antes do prazo, no dia ou atrasada.

### 28.6 Como ver relatórios
- Na aba “Relatórios”, veja:
  - Mensal: quanto gastou por categoria, top 5 categorias (sem precisar rolar). 
  - Anual: evolução dos meses, comparação com a média anual.

### 28.7 Como criar metas de gastos
- Vá em “Metas”, crie uma meta por categoria (ex.: R$ 500 Alimentação).
- O app alerta ao chegar em 80% (atenção) e 100% (ultrapassou).

### 28.8 Como o app me ajuda com alertas (insights)
- “Você gastou X% a mais que mês passado”.
- “Meta de Alimentação em 80%/100%”.
- “Parcelamento em categorias recorrentes (não recomendado)”.
- “Sua fatura está acima de 80% da sua renda” ou “Sua fatura passou da sua renda”.
- “Fatura vence em X dias”.

Dica: informe sua renda no seu perfil. Assim os alertas de renda funcionam.

### 28.9 O que aparece no Dashboard (tela inicial)
- Valor da fatura deste mês.
- Quantos dias faltam para vencer (ou se já está paga, como foi o pagamento).
- Gráfico/lista de gastos por categoria.
- Cartões com avisos importantes (insights) e metas.

### 28.10 O que acontece com o meu PDF?
- Ele é lido e descartado. O app salva apenas os dados necessários (compras, valores, categorias) com sua conta no Firebase, de forma segura.

---

## 29. Exemplos Visuais (Texto) e Casos Reais

### 29.1 Exemplo de compra normal
- Linha do PDF: “25 MAI CAFE DA ANA CORONEL VIVIDA R$ 42,00”
- O app entende: Data=25/05, Estabelecimento=CAFE DA ANA, Cidade=CORONEL VIVIDA, Valor=42,00

### 29.2 Exemplo de estorno (valor negativo)
- Linha do PDF: “29 MAI MP *TICPAYMCV CAMPINAS -R$ 49,00”
- O app entende como um gasto negativo (um crédito que reduz o total).

### 29.3 Exemplo de tarifa na seção “MOVIMENTAÇÕES DA CONTA”
- “26 MAI ANUIDADE MASTERCARD (8784) 01/12 R$ 24,58” → entra como “Taxas Cartão”.
- “28 MAI PROTEÇÃO PERDA OU ROUBO R$ 3,20” → entra como “Taxas Cartão”.
- “SALDO ANTERIOR …” e “PAGAMENTO-BOLETO BANCARIO …” → ignorados (não entram como gasto).

### 29.4 Exemplo de parcela
- “03/10” próximo do item → o app marca como compra parcelada (3ª de 10 parcelas).

---

## 30. Explicando Termos Importantes (Glossário Simples)
- Fatura: documento mensal com suas compras do cartão.
- Despesa: cada compra (ou tarifa) na fatura.
- Categoria: tipo do gasto (Alimentação, Transporte, etc.).
- Estorno: valor negativo que reduz a fatura.
- Meta: limite mensal por categoria.
- Insight: aviso/alerta que o app mostra com base nos seus gastos.
- Renda: quanto você ganha por mês (informe no Perfil para alertas de renda).

---

## 31. Solução de Problemas (FAQ Simples)
- “O app diz que o PDF é inválido”: verifique se é a fatura oficial do Sicoob, em PDF.
- “Algumas compras não apareceram”: reabra o PDF e tente novamente; se persistir, nos envie o texto que o app extraiu (há logs no desenvolvimento). Compras em multi-linhas são tratadas, mas alguns formatos exigem ajuste.
- “O app não reconheceu a cidade/estabelecimento corretamente”: o app usa posições da página (X/Y). Em casos raros, a fatura pode ter layout diferente; basta ajustar manualmente a categoria, o app aprende para a próxima.
- “Os insights de renda não aparecem”: edite seu Perfil e informe sua renda mensal.
- “O alerta de parcelamento em recorrentes não apareceu na minha categoria personalizada”: marque a categoria personalizada como recorrente ao criá-la/editar.
- “Quero mudar o mês em todas as telas”: selecione o mês (Dashboard/Relatórios). O app guarda essa escolha para as outras telas e para a próxima vez que abrir.

---

## 32. Como o App Garante Privacidade
- Somente você (o dono da conta) tem acesso aos seus dados no Firebase (regras por usuário).
- O PDF não é salvo após a leitura, apenas os dados necessários (compras, valores e categorias).
- O tráfego é criptografado (HTTPS do Firebase).

---

## 33. Guia de Ação Rápida (Cheat Sheets)

### 33.1 Entrar
- Login com Google → pronto!
- Ou crie conta com e‑mail/senha.

### 33.2 Processar Fatura
- Início → Nova Fatura → Selecione PDF → Categorize → Salvar.

### 33.3 Marcar Pagamento
- Detalhe da Fatura → “Paga” → selecione a data.

### 33.4 Criar Meta
- Metas → Nova Meta → Categoria + Valor → Salvar.

### 33.5 Ver Relatórios
- Relatórios → Mensal ou Anual → Filtre por mês (o app lembra sua escolha).

---

## 34. Para Usuários Um Pouco Mais Técnicos (Sem Código)
- O app usa um “leitor de PDF” que converte o conteúdo em linhas de texto; depois aplica regras para entender cada coluna (data, loja, cidade, valor).
- Se o PDF estiver em um layout um pouco diferente, há uma segunda etapa que usa a posição de cada palavra na página para acertar as colunas.
- O cálculo dos avisos (insights) é feito comparando gastos deste mês com metas, com mês anterior e com sua renda.
- As categorias personalizadas também podem ser “recorrentes”, e o app alerta caso você parcele compras que são do dia a dia (como supermercado).

---

## 35. Dicas de Uso Inteligente
- Categorize direitinho na primeira fatura: isso economiza muito tempo nas próximas.
- Informe sua renda para ter alertas mais úteis.
- Crie metas realistas nas categorias em que você mais gasta.
- Use o relatório anual para ver como seus gastos variam ao longo do ano.

---

## 36. O Que Fazer Se… (Cenários Comuns)
- Perdi minha fatura: baixe novamente no Sicoob e reenvie ao app.
- Categorizei errado: edite a categoria; o app aprende a nova associação.
- Gastei em parcelas: o app marca como parcelado; isso aparece nos avisos se for numa categoria recorrente.
- Mudei de mês e não voltou: o app lembra sua última escolha; selecione manualmente o mês desejado.

---

## 37. Resumo Final em 1 Página (Para Apresentação)
- Objetivo: transformar o PDF da fatura em informações claras em minutos.
- Como faz: lê PDF → extrai compras → classifica por categoria → mostra resumo, metas e alertas.
- O que precisa: login + PDF da fatura.
- Por que é bom: economiza tempo, aumenta controle financeiro e evita surpresas.
- Segurança: dados só para o dono da conta; PDF descartado.
- Diferenciais: aprendizado de categorias, alertas úteis (metas, renda, parcelamento), relatórios mensais/anuais, persistência do mês, status de pagamento com data.

---

Este apêndice torna o documento acessível a qualquer pessoa, sem perder a precisão técnica. Se quiser, podemos adicionar imagens e setas explicativas nas telas (mockups) para apresentação/relatório do TCC.

---

## 38. TL;DR (Resumo Ultra-Rápido de Tudo)
- Problema: entender faturas Sicoob em PDF e gerar controle financeiro.
- Solução: app Android que lê PDF, extrai compras, categoriza, mostra insights, metas e relatórios.
- Como funciona: PDF → Parser (texto + posição) → Dados → Firebase → Dashboard/Relatórios.
- Diferenciais: aprendizado de categorias, insights úteis (metas, renda, parcelamento), mês persistido, pagamento com data.
- Segurança: dados por usuário; PDF descartado; HTTPS.

## 39. Tabelas-Resumo (Para Skim Rápido)

### 39.1 Funcionalidades x Telas
| Tela | Funções Principais |
|------|---------------------|
| Login/Registro | Entrar com Google/E-mail; erros amigáveis |
| Dashboard | Resumo do mês, countdown/pagamento, insights, metas |
| Faturas | Upload PDF, histórico, detalhes, categorização |
| Relatórios | Mensal (top 5 sem rolagem), Anual (evolução vs média) |
| Metas | Criar/editar; alertas 80%/100% |
| Categorias | Gerenciar padrão/personalizadas; sem ícones |
| Perfil | Editar renda/nickname/phone |

### 39.2 Insights Disponíveis
| Insight | Quando aparece | Severidade |
|--------|-----------------|------------|
| Aumento de Gastos | >10% vs mês passado | WARNING |
| Meta em 80% | Atingiu 80% da meta | WARNING |
| Meta em 100% | Ultrapassou a meta | CRITICAL |
| Parcelamento Recorrente | Parcelas em categoria recorrente | WARNING |
| Fatura > Renda | totalValue > income | CRITICAL |
| Fatura ≥ 80% da Renda | (total/income) ≥ 80% | WARNING |
| Fatura Vencendo | <7 dias (não paga) | WARNING |
| Fatura Paga | Paga (adiantada/no prazo/atrasada) | INFO |

### 39.3 Principais Entidades
| Entidade | Campos-Chave |
|---------|---------------|
| User | id, email, name, income |
| Invoice | dueDate, totalValue, referenceMonth, isPaid, paidDate, expenses[] |
| Expense | date, establishment, city, value, category, installment |
| Goal | category, limitValue, alertAt80/100 |
| Category | id, name, color, isRecurring |

## 40. Drill-Down: Parser (Curva A/B Detalhe + Exemplos)
- A (Texto): divide em linhas, usa regex para Data, Valor, Parcela, ignora “Saldo Anterior/Pagamento”.
- B (Posição): agrupa por Y (linhas) com threshold ≈3, ordena por X (colunas), mapeia: Data | Estabelecimento | Cidade | Valor.
- Composição: mescla resultados, remove duplicatas por (date|estab|value).
- Mês de Referência: prioriza frase “Esta é a fatura de {mês}”; fallback período (fim−1 mês).
- Estornos: mantém negativos; exibidos/somados corretamente nos relatórios.

Exemplo Regex (ilustrativo):
- Data: `\b\d{2}\s+(JAN|FEV|MAR|ABR|MAI|JUN|JUL|AGO|SET|OUT|NOV|DEZ)\b`
- Valor: `^-?R\$\s*\d{1,3}(?:\.\d{3})*,\d{2}$`
- Parcela: `\b\d{2}/\d{2}\b`

## 41. Drill-Down: Insights (Regras Claras)
- Metas: percentage = spent/limit*100 → 80% WARNING; 100% CRITICAL (se flags ativadas).
- Renda: income>0 → totalValue>income → CRITICAL; else if ≥80% → WARNING.
- Parcelamento Recorrente: expense.isInstallment && category.isRecurring (inclui customizadas marcadas).
- Aumento Mensal: (this−prev)/prev ≥ 10% → WARNING.
- Pagamento: difDias(paidDate, dueDate) → mensagem amigável.

## 42. Drill-Down: Persistência (Firebase) e Regras
- users/{userId}: perfil e coleções (invoices/goals/categories).
- invoices/{yyyy-MM}: cabeçalho + expenses/* (validações numéricas e de tipos).
- Regras `.read/.write`: usuário só acessa os próprios dados.
- PDF: não armazenado; apenas dados estruturados.

## 43. Drill-Down: UI/UX (Padrões e Decisões)
- Sem ícones redundantes (categorias, top 5) para foco na informação.
- Top 5 sempre visível (sem rolagem) para comparação rápida.
- Evolução mensal: 3 linhas fixas visíveis + lista restante; percentual vs média anual.
- Tela de fatura totalmente rolável (toolbar fixa).
- Cores consistentes com Material 3.

## 44. Drill-Down: Fluxos de Erros (Tratamento Amigável)
- Autenticação: AuthErrorUtils mapeia códigos Firebase → mensagens (email inválido, senha incorreta, conta inexistente, etc.).
- Parsing vazio/corrompido: mensagem clara (“PDF inválido/corrompido”).
- Sem conexão: mensagens orientando verificar internet.

## 45. Drill-Down: Performance e Estabilidade
- Parsing em I/O com coroutines; fechamento seguro do documento.
- Evita NPE em Fragments (checa `_binding` antes de interagir com a UI).
- Reuso de adapters; listas horizontais/verticais com LayoutManagers adequados.

## 46. Checklists (Auditoria Rápida)
- Segurança: [x] Regras por usuário; [x] PDF descartado; [x] HTTPS.
- UX: [x] Top 5 sem rolagem; [x] Tela rolável; [x] Erros amigáveis; [x] Mês persistido.
- Parser: [x] Estornos; [x] Mês referência; [x] Movimentações (Anuidade/Proteção); [x] Multi-linha; [x] X/Y colunas.
- Insights: [x] Metas; [x] Renda; [x] Parcelamento; [x] Variação mensal; [x] Pagamento; [x] Vencimento.

## 47. Roteiro de Apresentação (Pitch de 2–3 min)
1) Problema: fatura é longa e manual. 2) Solução: app que lê PDF e explica seus gastos.
3) Como: PDF→Parser→Dados→Firebase→Dashboard/Relatórios. 4) Valor: Economia de tempo + Consciência financeira.
5) Extras: aprende categorias; avisos úteis (metas/renda/parcelas); mês persistido; pagamento com data.

## 48. Plano de Evolução (Resumo + Detalhe)
- Exportação PDF de relatórios: gerar PDF do relatório mensal/anual.
- Modo escuro: temas night/.
- Biometria: autenticação rápida.
- Múltiplos cartões: `cardId` em invoice; filtros por cartão.
- Compartilhamento e backup: exportações/restore; sync multi-dispositivo.

## 49. Conclusão Executiva + Técnica
- Executivo: solução prática e segura que traduz faturas em insights acionáveis, com learning de categorias e metas claras.
- Técnico: arquitetura simples e sólida (MVC + Services), parser robusto (texto+posição), Firebase seguro, UX focada em clareza e velocidade.

---
