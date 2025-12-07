# 📚 GUIA COMPLETO DE ESTUDO - TCC GESTÃO DE FATURA SICOOB

## 🎯 OBJETIVO DO GUIA
Este documento apresenta uma ordem lógica de aprendizagem do sistema, cobrindo aspectos técnicos, lógicos e conceituais para preparação completa do TCC.

---

## 📋 ÍNDICE DE ESTUDO

### **FASE 1: FUNDAÇÕES - Arquitetura e Infraestrutura**
### **FASE 2: MODELOS DE DADOS - Estrutura de Informação**
### **FASE 3: CAMADA DE DADOS - Integrações Externas**
### **FASE 4: SERVIÇOS - Lógica de Negócio**
### **FASE 5: CONTROLLERS - Orquestração de Fluxos**
### **FASE 6: APRESENTAÇÃO - Interface do Usuário**
### **FASE 7: UTILITÁRIOS - Helpers e Extensions**
### **FASE 8: FLUXOS COMPLETOS - Casos de Uso End-to-End**

---

## 📖 FASE 1: FUNDAÇÕES - Arquitetura e Infraestrutura

### **1.1 Arquitetura do Sistema**

**Arquivo:** `DOCUMENTACAO_COMPLETA_SISTEMA.md`

**O que aprender:**
- Arquitetura MVC com Service Layer
- Camadas: Presentation → Controllers → Services → DataSources
- Fluxo de dados unidirecional
- Separação de responsabilidades

**Pontos importantes:**
- **Por que MVC?** Facilita manutenção e testes, separa lógica de apresentação
- **Service Layer:** Centraliza regras de negócio reutilizáveis
- **DataSources:** Isolam integrações externas (Firebase, PDF)

---

### **1.2 Configuração do Projeto**

**Arquivos:**
- `app/build.gradle.kts` - Dependências e configurações
- `app/src/main/AndroidManifest.xml` - Configuração do app

**O que aprender:**
- Dependências principais: Firebase, iText7, Material Design
- Configuração de minSdk/targetSdk
- Permissões necessárias
- ViewBinding habilitado

**Tecnologias utilizadas:**
1. **Kotlin 1.9+** - Linguagem moderna com recursos avançados
2. **Firebase** - Backend as a Service (Auth + Realtime Database)
3. **iText7** - Biblioteca de parsing de PDFs
4. **Material Design 3** - Design system do Google
5. **Navigation Component** - Gerenciamento de navegação
6. **Coroutines** - Programação assíncrona

---

### **1.3 MainActivity e Navegação**

**Arquivo:** `MainActivity.kt`

**O que aprender:**
- Como funciona a navegação principal
- BottomNavigationView sincronizado com Navigation Component
- Gerenciamento de estado de autenticação
- Ciclo de vida da Activity

**Conceitos técnicos:**
- **NavController:** Controla navegação entre fragments
- **setupWithNavController():** Sincroniza BottomNavigationView com NavController
- **AuthStateListener:** Monitora mudanças de autenticação em tempo real
- **ViewBinding:** Acesso type-safe às views

---

## 📊 FASE 2: MODELOS DE DADOS - Estrutura de Informação

### **2.1 Modelos Base**

**Arquivos:**
1. `models/User.kt` - Usuário do sistema
2. `models/Invoice.kt` - Fatura de cartão
3. `models/Expense.kt` - Despesa individual
4. `models/Category.kt` - Categoria de gasto
5. `models/Goal.kt` - Meta de gasto mensal

**O que aprender:**

#### **User.kt**
- Estrutura de dados do usuário
- Campos: id, name, email, nickname, phone, income
- Métodos helper: `getDisplayName()`, `fromMap()`, `toMap()`
- **Parcelable:** Permite passar objeto entre Activities/Fragments

#### **Invoice.kt**
- Representa uma fatura completa
- Lista de despesas (`expenses`)
- Informações de vencimento e pagamento
- Métodos: `getExpensesByCategory()`, `getTotalForCategory()`
- Status de pagamento: `isPaid`, `paidDate`

#### **Expense.kt**
- Despesa individual extraída do PDF
- Campos: date, establishment, city, value, category
- Informações de parcelamento: `installment`, `isInstallment`
- Auto-categorização: `autoCategorized`
- Método: `isCardFee()` - Identifica taxas do cartão

**Conceitos importantes:**
- **Data Classes:** Estruturas imutáveis para dados
- **Parcelable:** Serialização para passagem entre componentes
- **Companion Object:** Factory methods (`fromMap()`)
- **Extension Functions:** Métodos utilitários nos models

---

## 🔌 FASE 3: CAMADA DE DADOS - Integrações Externas

### **3.1 Firebase - Configuração e Gerenciamento**

**Arquivos:**
1. `data/datasource/FirebaseConfig.kt`
2. `data/datasource/FirebaseManager.kt`

**O que aprender:**

#### **FirebaseConfig.kt**
- URL do banco de dados Realtime Database
- Validação de URLs
- Configuração centralizada

#### **FirebaseManager.kt** ⭐ **ARQUIVO CRÍTICO**
- **Singleton Object:** Única instância compartilhada
- **Inicialização:** `initialize(context)` - Conecta ao Firebase
- **Autenticação:** `auth.currentUser` - Usuário atual
- **Referências:** `usersRef` - Referência ao nó de usuários

**Métodos principais:**
- `createOrUpdateUser()` - Cria/atualiza usuário no Firebase
- `getUserData()` - Busca dados do usuário
- `getCurrentUserRef()` - Referência do usuário atual
- `signOut()` - Logout

**Conceitos técnicos:**
- **Singleton Pattern:** Garante única instância
- **Lazy Initialization:** Inicialização sob demanda
- **Coroutines + await():** Operações assíncronas
- **Result<T>:** Padrão funcional para sucesso/erro

---

### **3.2 Parser de PDF - Extração de Dados**

**Arquivo:** `data/datasource/PDFParserDataSourceFixed.kt` ⭐ **ARQUIVO MAIS COMPLEXO**

**O que aprender:**

#### **Estratégia de Parsing:**
1. **Extração de Texto:** iText7 extrai todo o texto do PDF
2. **Divisão em Linhas:** Regex separa linhas de dados
3. **Parsing por Coordenadas:** X/Y para separar colunas
4. **Regex Robustas:** Identificam padrões de data, estabelecimento, valor
5. **Heurísticas:** Corrigem erros comuns (troca cidade/estabelecimento)

#### **Dados Extraídos:**
- **Cabeçalho:** Vencimento, valor total, período de referência
- **Despesas:** Data, estabelecimento, cidade, valor, parcela
- **Tarifas:** ANUIDADE, PROTEÇÃO PERDA OU ROUBO

**Algoritmos importantes:**
- **Regex para datas:** `(\d{2}\s+[A-Z]{3})` - Captura "08 MAI"
- **Regex para valores:** `R\$\s*(\d+[.,]\d{2})` - Captura valores monetários
- **Parsing de período:** Identifica "REF 26 MAI A 23 JUN"
- **Tratamento de casos especiais:** Nomes compostos, cidades longas

**Conceitos técnicos:**
- **Strategy Pattern:** Diferentes estratégias de parsing
- **Regex Groups:** Captura de grupos específicos
- **Error Handling:** Try-catch para arquivos corrompidos
- **Memory Management:** Processamento em chunks para PDFs grandes

---

## 🎯 FASE 4: SERVIÇOS - Lógica de Negócio

### **4.1 AuthService - Autenticação**

**Arquivo:** `services/AuthService.kt`

**O que aprender:**
- **Login com Email/Senha:** Firebase Authentication
- **Login com Google:** Google Sign-In integrado
- **Registro:** Criação de conta com validação
- **Perfil:** Busca e atualização de dados do usuário

**Fluxo de autenticação:**
1. Usuário preenche credenciais
2. `AuthService` chama Firebase Auth
3. Em caso de sucesso, cria/atualiza usuário no Realtime Database
4. Retorna `Result<User>` para o Controller

---

### **4.2 InvoiceService - Gerenciamento de Faturas**

**Arquivo:** `services/InvoiceService.kt`

**O que aprender:**
- **Salvar Fatura:** Estrutura de dados no Firebase
- **Buscar Faturas:** Por mês, mais recente, histórico
- **Atualizar Status:** Marcar como paga com data
- **Excluir Fatura:** Remove do Firebase

**Estrutura no Firebase:**
```
users/{userId}/
  invoices/
    {referenceMonth}/  # Ex: "JUN/2025"
      invoiceId, dueDate, totalValue, expenses, etc.
```

---

### **4.3 CategoryService - Categorização**

**Arquivo:** `services/CategoryService.kt`

**O que aprender:**
- **CRUD de Categorias:** Criar, ler, atualizar, excluir
- **Mapeamento Estabelecimento→Categoria:** Auto-categorização
- **Categorias Padrão:** Pré-cadastradas no sistema
- **Categorias Personalizadas:** Criadas pelo usuário

**Lógica de auto-categorização:**
1. Usuário categoriza manualmente na primeira vez
2. Sistema salva mapeamento: `"CAFE DA ANA" → "Alimentação"`
3. Próximas faturas: Sistema busca mapeamento e aplica automaticamente
4. Usuário pode alterar → Atualiza mapeamento

---

### **4.4 GoalService - Metas de Gastos**

**Arquivo:** `services/GoalService.kt`

**O que aprender:**
- **CRUD de Metas:** Criar, editar, excluir metas
- **Cálculo de Progresso:** % gasto vs limite
- **Alertas:** 80% e 100% do limite
- **Reset Mensal:** Metas reiniciam a cada mês

**Cálculo de progresso:**
```kotlin
val percentage = (spent / limit) * 100
val status = when {
    percentage >= 100 -> EXCEEDED
    percentage >= 80 -> WARNING
    else -> NORMAL
}
```

---

### **4.5 DashboardService e ReportService**

**Arquivos:**
- `services/ReportService.kt`

**O que aprender:**
- **Agregação de Dados:** Soma gastos por categoria
- **Cálculo de Insights:** Crescimento, parcelamentos, etc.
- **Comparação Mensal:** Mês atual vs anterior
- **Geração de Relatórios:** Mensal e anual

---

## 🎮 FASE 5: CONTROLLERS - Orquestração de Fluxos

### **5.1 Padrão Controller**

**Conceito:** Controllers fazem a ponte entre Views (Fragments) e Services

**Fluxo típico:**
```
Fragment → Controller → Service → DataSource → Firebase/PDF
                ↓
Fragment ← Controller ← Service ← DataSource ← Firebase/PDF
```

### **5.2 AuthController**

**Arquivo:** `controllers/AuthController.kt`

**O que aprender:**
- **Orquestra autenticação:** Coordena AuthService
- **Tratamento de erros:** Converte erros Firebase em mensagens amigáveis
- **Sessão:** Mantém estado do usuário logado
- **Perfil:** Atualização de dados do usuário

---

### **5.3 InvoiceController**

**Arquivo:** `controllers/InvoiceController.kt`

**O que aprender:**
- **Upload de PDF:** Recebe arquivo do Fragment
- **Parsing:** Chama PDFParserDataSourceFixed
- **Salvamento:** Coordena InvoiceService
- **Busca:** Carrega faturas do Firebase

**Fluxo completo de upload:**
1. Usuário seleciona PDF → `UploadInvoiceFragment`
2. Fragment chama → `InvoiceController.processInvoice()`
3. Controller chama → `PDFParserDataSourceFixed.extractInvoiceData()`
4. Controller chama → `InvoiceService.saveInvoice()`
5. Retorna → `Result<Invoice>` para Fragment

---

### **5.4 DashboardController**

**Arquivo:** `controllers/DashboardController.kt`

**O que aprender:**
- **Carrega dados do mês:** Fatura atual
- **Calcula countdown:** Dias até vencimento
- **Agrega gastos:** Por categoria
- **Gera insights:** Análises automáticas

---

### **5.5 CategoryController e GoalController**

**Arquivos:**
- `controllers/CategoryController.kt`
- `controllers/GoalController.kt`

**O que aprender:**
- **CRUD completo:** Create, Read, Update, Delete
- **Validações:** Regras de negócio
- **Tratamento de erros:** Mensagens amigáveis

---

## 🎨 FASE 6: APRESENTAÇÃO - Interface do Usuário

### **6.1 BaseFragment e BaseActivity**

**Arquivos:**
- `presentation/common/BaseFragment.kt`
- `presentation/common/BaseActivity.kt`

**O que aprender:**
- **Padrão base:** Funcionalidades comuns
- **ViewBinding:** Acesso type-safe às views
- **Ciclo de vida:** Gerenciamento de binding
- **Helpers:** Snackbar, loading, etc.

---

### **6.2 Fragments de Autenticação**

**Arquivos:**
- `presentation/auth/LoginFragment.kt`
- `presentation/auth/RegisterFragment.kt`

**O que aprender:**
- **Validação de campos:** Email, senha
- **Integração com AuthController:** Login/registro
- **Navegação:** Após login, vai para Dashboard
- **Tratamento de erros:** Exibe mensagens ao usuário

**Fluxo de login:**
1. Usuário preenche email/senha
2. Fragment valida campos
3. Chama `AuthController.login()`
4. Em sucesso → Navega para Dashboard
5. Em erro → Exibe Snackbar com mensagem

---

### **6.3 DashboardFragment**

**Arquivo:** `presentation/dashboard/DashboardFragment.kt` ⭐ **TELA PRINCIPAL**

**O que aprender:**
- **Carregamento de dados:** Chama DashboardController
- **Exibição:** Fatura atual, countdown, gráfico pizza
- **Metas:** Cards de progresso
- **Insights:** Lista de insights gerados
- **Filtro de mês:** Persiste seleção

**Componentes visuais:**
- **PieChart:** Gráfico de pizza (gastos por categoria)
- **RecyclerView:** Lista de metas e insights
- **CardView:** Cards de informações

---

### **6.4 Invoice Fragments**

**Arquivos:**
- `presentation/invoice/UploadInvoiceFragment.kt`
- `presentation/invoice/InvoicesFragment.kt`
- `presentation/invoice/InvoiceDetailFragment.kt`
- `presentation/invoice/CategorizeExpensesFragment.kt`

**O que aprender:**

#### **UploadInvoiceFragment**
- **Seleção de arquivo:** ActivityResultContracts.GetContent()
- **Preview do PDF:** Mostra nome e tamanho
- **Parsing:** Chama InvoiceController
- **Navegação:** Após parsing, vai para CategorizeExpensesFragment

#### **CategorizeExpensesFragment**
- **Lista de despesas:** RecyclerView com categorização
- **Auto-categorização:** Sistema sugere categoria baseado em histórico
- **Salvamento:** Após categorizar todas, salva fatura

#### **InvoiceDetailFragment**
- **Detalhes da fatura:** Informações completas
- **Edição de categorias:** Alterar categoria de despesas
- **Marcação de pagamento:** Switch para marcar como paga
- **Exclusão:** Remove fatura do Firebase

---

### **6.5 Adapters**

**O que aprender:**
- **RecyclerView.Adapter:** Exibe listas
- **ViewHolder Pattern:** Reutilização de views
- **Click Listeners:** Interação com itens

**Adapters principais:**
- `ExpenseDetailAdapter` - Lista de despesas
- `GoalsAdapter` - Lista de metas
- `InsightsAdapter` - Lista de insights
- `InvoicesListAdapter` - Lista de faturas

---

## 🛠️ FASE 7: UTILITÁRIOS - Helpers e Extensions

### **7.1 Utils**

**Arquivos:**
- `utils/DateUtils.kt` - Formatação de datas
- `utils/CurrencyUtils.kt` - Formatação monetária
- `utils/CategoryUtils.kt` - Helpers de categorias
- `utils/AuthErrorUtils.kt` - Conversão de erros Firebase
- `utils/MonthFilterManager.kt` - Persistência de filtro

**O que aprender:**
- **Funções reutilizáveis:** Evitam código duplicado
- **Extensions:** Adicionam métodos a tipos existentes
- **Formatação:** Padronização de exibição

---

## 🔄 FASE 8: FLUXOS COMPLETOS - Casos de Uso End-to-End

### **8.1 Fluxo: Primeira Fatura**

**Passos:**
1. Usuário faz login → `LoginFragment` → `AuthController`
2. Vai para Dashboard (vazio)
3. Toca em "+ Nova Fatura" → `UploadInvoiceFragment`
4. Seleciona PDF → `ActivityResultContracts`
5. Fragment chama → `InvoiceController.processInvoice()`
6. Controller chama → `PDFParserDataSourceFixed.extractInvoiceData()`
7. Parser extrai dados → Retorna `ExtractedInvoiceData`
8. Controller chama → `InvoiceService.saveInvoice()` (ainda sem categorias)
9. Navega para → `CategorizeExpensesFragment`
10. Usuário categoriza cada despesa manualmente
11. Sistema salva mapeamento: `"ESTABELECIMENTO" → "CATEGORIA"`
12. Salva fatura completa → `InvoiceService.saveInvoice()`
13. Retorna para Dashboard → Exibe dados da fatura

---

### **8.2 Fluxo: Fatura Subsequente**

**Diferenças:**
- Passo 10: Sistema auto-categoriza 90%+ das despesas
- Usuário apenas revisa/ajusta algumas categorias

---

### **8.3 Fluxo: Criar Meta**

**Passos:**
1. Usuário vai para Dashboard → Toca em "Gerenciar Metas"
2. `ManageGoalsFragment` → `GoalController`
3. Toca em "+ Nova Meta" → Dialog
4. Seleciona categoria e define valor limite
5. Controller chama → `GoalService.createGoal()`
6. Salva no Firebase
7. Retorna para Dashboard → Exibe card de meta

---

### **8.4 Fluxo: Gerar Relatório**

**Passos:**
1. Usuário vai para "Relatórios"
2. Seleciona período (Mensal/Anual)
3. `ReportsFragment` → `ReportService`
4. Service busca faturas do período
5. Agrega dados por categoria
6. Calcula comparações (mês anterior)
7. Gera insights
8. Fragment exibe gráficos e tabelas

---

## 📝 PONTOS TÉCNICOS IMPORTANTES

### **1. Coroutines e Assíncrono**
- **lifecycleScope:** Scope vinculado ao ciclo de vida
- **suspend functions:** Funções assíncronas
- **await():** Aguarda resultado de Tasks do Firebase
- **try-catch:** Tratamento de erros em operações assíncronas

### **2. Result Pattern**
```kotlin
Result<T> = Success(T) | Failure(Exception)
```
- Padrão funcional para sucesso/erro
- Evita exceções desnecessárias
- Permite tratamento elegante de erros

### **3. ViewBinding**
- Acesso type-safe às views
- Null safety
- Performance melhor que findViewById

### **4. Navigation Component**
- Type-safe navigation
- Deep linking
- Back stack management

### **5. Firebase Realtime Database**
- Estrutura JSON em tempo real
- Listeners para mudanças
- Offline support

---

## 🎓 CONCEITOS PARA O TCC

### **Arquitetura**
- **MVC com Service Layer:** Por que essa escolha?
- **Separação de Responsabilidades:** Cada camada tem papel específico
- **Testabilidade:** Arquitetura facilita testes unitários

### **Tecnologias**
- **Kotlin:** Vantagens sobre Java
- **Firebase:** BaaS vs Backend próprio
- **iText7:** Por que não PDFBox?
- **Material Design 3:** Consistência visual

### **Algoritmos**
- **Parsing de PDF:** Desafios e soluções
- **Auto-categorização:** Machine Learning básico
- **Agregação de dados:** Performance com grandes volumes

### **UX/UI**
- **Feedback visual:** Loading, erros, sucesso
- **Navegação intuitiva:** Bottom navigation
- **Estados vazios:** Mensagens amigáveis

---

## ✅ CHECKLIST DE ESTUDO

### **Fundamentos**
- [ ] Entender arquitetura MVC com Service Layer
- [ ] Conhecer todas as tecnologias utilizadas
- [ ] Entender fluxo de dados do sistema

### **Modelos**
- [ ] Estudar todos os data classes
- [ ] Entender estrutura do Firebase
- [ ] Saber converter Map ↔ Model

### **Data Sources**
- [ ] Entender FirebaseManager completamente
- [ ] Estudar parser de PDF em detalhes
- [ ] Saber como tratar erros

### **Services**
- [ ] Conhecer todos os serviços
- [ ] Entender regras de negócio
- [ ] Saber fluxo de cada operação

### **Controllers**
- [ ] Entender papel dos controllers
- [ ] Saber como orquestrar fluxos
- [ ] Tratamento de erros amigáveis

### **Presentation**
- [ ] Estudar todos os fragments
- [ ] Entender adapters
- [ ] Saber navegação entre telas

### **Fluxos**
- [ ] Saber explicar cada fluxo completo
- [ ] Entender edge cases
- [ ] Saber como testar cada fluxo

---

## 🚀 PRÓXIMOS PASSOS

1. **Comece pela Fase 1:** Leia a documentação completa
2. **Estude os Models:** Entenda estrutura de dados
3. **Profundize em Firebase:** Entenda integração
4. **Analise o Parser:** Entenda algoritmo de extração
5. **Percorra os Services:** Entenda lógica de negócio
6. **Estude os Controllers:** Entenda orquestração
7. **Analise as Views:** Entenda interface
8. **Pratique fluxos:** Execute mentalmente cada caso de uso

---

## 📞 RECURSOS ADICIONAIS

- **Documentação Firebase:** https://firebase.google.com/docs
- **Documentação Kotlin:** https://kotlinlang.org/docs/home.html
- **Material Design 3:** https://m3.material.io
- **iText7:** https://itextpdf.com/en/products/itext-7

---

**Boa sorte com seus estudos! 🎓**

