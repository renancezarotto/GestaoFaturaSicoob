# Diagrama Entidade-Relacionamento (DER)
## Sistema de Gestão de Fatura de Crédito Sicoob

---

## 📊 Visão Geral

Este documento descreve o modelo de dados do Sistema de Gestão de Fatura de Crédito Sicoob, implementado no Firebase Realtime Database. O sistema utiliza uma arquitetura NoSQL hierárquica, onde os dados são organizados por usuário (`userId`) como chave primária de isolamento.

---

## 🗂️ Estrutura Hierárquica no Firebase

```
Firebase Realtime Database
└── users/
    └── {userId}/
        ├── (dados do usuário)
        ├── invoices/
        │   └── {referenceMonth}/
        │       ├── (dados da fatura)
        │       └── expenses/
        │           └── {expenseId}/
        ├── categories/
        │   ├── savedCategories/
        │   └── customCategories/
        │       └── {categoryId}/
        └── goals/
            └── {goalId}/
```

---

## 🔷 Entidades e Atributos

### 1. **USER (Usuário)**

**Descrição**: Representa um usuário autenticado no sistema.

**Chave Primária**: `id` (Firebase Auth UID)

**Atributos**:
- `id` (String, PK, obrigatório): Identificador único do usuário (Firebase Auth UID)
- `name` (String, obrigatório): Nome completo do usuário
- `email` (String, obrigatório, único): Endereço de e-mail (usado para login)
- `photoUrl` (String, opcional): URL da foto de perfil
- `nickname` (String, opcional): Apelido do usuário
- `phone` (String, opcional): Telefone de contato
- `income` (Double, opcional): Renda mensal do usuário
- `createdAt` (String, obrigatório): Data de criação da conta (ISO 8601)
- `updatedAt` (String, opcional): Data da última atualização (ISO 8601)

**Regras de Negócio**:
- Cada usuário tem acesso exclusivo aos seus próprios dados
- O `id` deve corresponder ao `auth.uid` do Firebase Authentication
- O e-mail é validado através de regex no Firebase Security Rules

---

### 2. **INVOICE (Fatura)**

**Descrição**: Representa uma fatura de cartão de crédito processada e armazenada no sistema.

**Chave Primária**: `id` + `userId` (composite)

**Chave Estrangeira**: `userId` → `USER.id`

**Atributos**:
- `id` (String, PK, obrigatório): Identificador único da fatura
- `userId` (String, FK, obrigatório): Referência ao usuário proprietário
- `dueDate` (String, obrigatório): Data de vencimento da fatura (ISO 8601)
- `totalValue` (Double, obrigatório, ≥ 0): Valor total da fatura
- `minimumPayment` (Double, obrigatório, ≥ 0): Valor do pagamento mínimo
- `referenceMonth` (String, obrigatório): Mês de referência (formato: "MMM/YYYY", ex: "JUN/2025")
- `closingDate` (String, obrigatório): Data de fechamento da fatura (ISO 8601)
- `uploadedAt` (String, obrigatório): Data/hora do upload (ISO 8601)
- `isPaid` (Boolean, padrão: false): Indica se a fatura foi paga
- `paidDate` (String, opcional): Data do pagamento (ISO 8601)

**Relacionamentos**:
- **1:N** com `EXPENSE`: Uma fatura possui múltiplas despesas
- **N:1** com `USER`: Uma fatura pertence a um único usuário

**Regras de Negócio**:
- Cada usuário pode ter apenas uma fatura por `referenceMonth`
- O `referenceMonth` serve como chave única dentro do contexto do usuário
- Todos os valores monetários são armazenados em Double (representando reais)

---

### 3. **EXPENSE (Despesa)**

**Descrição**: Representa uma transação individual extraída de uma fatura (compra, tarifa, etc.).

**Chave Primária**: `id` + `invoiceId` (composite)

**Chave Estrangeira**: `invoiceId` → `INVOICE.id` (implícito através da hierarquia)

**Atributos**:
- `id` (String, PK, obrigatório): Identificador único da despesa
- `date` (String, obrigatório): Data da transação (ISO 8601)
- `description` (String, obrigatório): Descrição completa da transação
- `establishment` (String, obrigatório): Nome do estabelecimento comercial
- `city` (String, obrigatório): Cidade onde ocorreu a transação
- `value` (Double, obrigatório, > 0): Valor da despesa
- `category` (String, FK, opcional): Referência ao nome da categoria
- `installment` (String, opcional): Informação de parcelamento (formato: "X/Y", ex: "03/12")
- `isInstallment` (Boolean, padrão: false): Indica se é compra parcelada
- `autoCategorized` (Boolean, padrão: false): Indica se foi categorizada automaticamente
- `createdAt` (String, obrigatório): Data/hora de criação (ISO 8601)

**Relacionamentos**:
- **N:1** com `INVOICE`: Múltiplas despesas pertencem a uma fatura
- **N:1** com `CATEGORY`: Uma despesa pertence a uma categoria (opcional até categorização)

**Regras de Negócio**:
- Despesas com valores negativos representam estornos/créditos
- Se `installment` não for nulo, `isInstallment` deve ser `true`
- A categorização pode ser manual ou automática (baseada em histórico)
- Estabelecimentos como "ANUIDADE" e "PROTEÇÃO PERDA OU ROUBO" são automaticamente categorizados como "Taxas Cartão"

---

### 4. **CATEGORY (Categoria)**

**Descrição**: Representa uma categoria de despesa, podendo ser padrão ou personalizada pelo usuário.

**Tipos**:
1. **Categoria Padrão**: Pré-definida no sistema, não armazenada no Firebase
2. **Categoria Personalizada**: Criada pelo usuário, armazenada em `customCategories`

**Chave Primária**: 
- Padrão: `id` fixo (ex: "food", "transport")
- Personalizada: `id` + `userId` (composite)

**Chave Estrangeira**: `userId` → `USER.id` (apenas para personalizadas)

**Atributos**:
- `id` (String, PK, obrigatório): Identificador único da categoria
- `name` (String, obrigatório): Nome da categoria (ex: "Alimentação")
- `color` (String, padrão: "#9E9E9E"): Cor hexadecimal para visualização
- `isRecurring` (Boolean, padrão: false): Indica se é despesa recorrente
- `isDefault` (Boolean, padrão: false): Indica se é categoria padrão do sistema
- `createdAt` (String, obrigatório): Data de criação (ISO 8601)

**Categorias Padrão**:
1. Alimentação (recorrente: SIM)
2. Transporte (recorrente: SIM)
3. Saúde (recorrente: NÃO)
4. Lazer (recorrente: NÃO)
5. Educação (recorrente: NÃO)
6. Moradia (recorrente: SIM)
7. Vestuário (recorrente: NÃO)
8. Combustível (recorrente: SIM)
9. Mercado (recorrente: SIM)
10. Restaurantes (recorrente: NÃO)
11. Taxas Cartão (recorrente: NÃO)
12. Outros (recorrente: NÃO)

**Relacionamentos**:
- **1:N** com `EXPENSE`: Uma categoria pode ter múltiplas despesas
- **N:1** com `USER`: Categorias personalizadas pertencem a um usuário
- **1:N** com `GOAL`: Uma categoria pode ter uma meta associada
- **1:N** com `SAVED_CATEGORY_MAPPING`: Múltiplos estabelecimentos podem mapear para a mesma categoria

**Regras de Negócio**:
- Categorias padrão não podem ser excluídas, apenas personalizadas
- O campo `color` é válido apenas para categorias personalizadas
- Categorias recorrentes são usadas para detectar parcelamento inadequado

---

### 5. **SAVED_CATEGORY_MAPPING (Mapeamento Estabelecimento-Categoria)**

**Descrição**: Armazena o mapeamento entre estabelecimentos comerciais e categorias para auto-categorização.

**Chave Primária**: `establishment` + `userId` (composite)

**Chave Estrangeira**: `userId` → `USER.id`

**Estrutura no Firebase**:
```
users/{userId}/categories/savedCategories/
  "CAFE DA ANA": "Alimentação"
  "DELTA CEL CENTRO": "Outros"
  "AB SUPERMERCADOS LTD": "Mercado"
```

**Atributos**:
- `establishment` (String, PK, obrigatório): Nome do estabelecimento (normalizado em uppercase)
- `category` (String, obrigatório): Nome da categoria associada

**Relacionamentos**:
- **N:1** com `USER`: Mapeamentos pertencem a um usuário
- **N:1** com `CATEGORY`: Um estabelecimento mapeia para uma categoria

**Regras de Negócio**:
- Quando uma despesa é categorizada manualmente pela primeira vez, o mapeamento é criado automaticamente
- Em faturas futuras, despesas do mesmo estabelecimento são auto-categorizadas
- O mapeamento é atualizado se o usuário alterar a categoria de uma despesa
- O nome do estabelecimento é armazenado em uppercase para comparação case-insensitive

---

### 6. **GOAL (Meta de Gastos)**

**Descrição**: Representa uma meta de limite de gasto mensal para uma categoria específica.

**Chave Primária**: `id` + `userId` (composite)

**Chave Estrangeira**: 
- `userId` → `USER.id`
- `category` → `CATEGORY.name`

**Atributos**:
- `id` (String, PK, obrigatório): Identificador único da meta
- `userId` (String, FK, obrigatório): Referência ao usuário
- `category` (String, FK, obrigatório): Nome da categoria alvo da meta
- `limitValue` (Double, obrigatório, > 0): Valor limite mensal em reais
- `alertAt80` (Boolean, padrão: true): Enviar alerta ao atingir 80% do limite
- `alertAt100` (Boolean, padrão: true): Enviar alerta ao atingir/exceder 100% do limite
- `monthlyReset` (Boolean, padrão: true): Indica se a meta reinicia mensalmente
- `isActive` (Boolean, padrão: true): Indica se a meta está ativa
- `createdAt` (String, obrigatório): Data de criação (ISO 8601)

**Relacionamentos**:
- **N:1** com `USER`: Múltiplas metas pertencem a um usuário
- **N:1** com `CATEGORY`: Uma meta está associada a uma categoria
- **1:1** (lógica): Uma categoria pode ter no máximo uma meta ativa por usuário

**Regras de Negócio**:
- Cada categoria pode ter apenas uma meta ativa por vez
- O progresso é calculado em tempo real somando despesas da categoria no mês atual
- Alertas são enviados quando `spent >= limitValue * 0.8` (80%) ou `spent >= limitValue` (100%)
- Se `monthlyReset = true`, o progresso é zerado no início de cada mês
- Metas desativadas não geram alertas, mas podem ser reativadas

---

### 7. **INSIGHT (Insight Financeiro)**

**Descrição**: Representa um insight automático gerado pelo sistema para alertar o usuário sobre padrões de gasto.

**Observação**: Insights são gerados dinamicamente e **não são persistidos** no Firebase. São calculados em tempo real com base nos dados existentes.

**Atributos** (lógicos, não armazenados):
- `id` (String, gerado): Identificador único do insight
- `title` (String): Título do insight
- `description` (String): Descrição detalhada
- `type` (InsightType, enum): Tipo do insight
- `severity` (InsightSeverity, enum): Nível de severidade
- `relatedCategoryId` (String, opcional): Categoria relacionada (se aplicável)
- `createdAt` (Long): Timestamp de criação

**Tipos de Insights** (InsightType):
- `GOAL_WARNING`: Meta atingindo 80% ou excedida
- `SPENDING_INCREASE`: Aumento significativo de gastos (>10% vs mês anterior)
- `SPENDING_DECREASE`: Redução significativa de gastos
- `INSTALLMENT_WARNING`: Parcelamento detectado em categoria recorrente
- `HIGH_EXPENSE`: Despesa individual acima de threshold
- `GENERAL`: Insight genérico

**Níveis de Severidade** (InsightSeverity):
- `INFO`: Informativo, neutro
- `WARNING`: Requer atenção
- `CRITICAL`: Ação urgente necessária

**Relacionamentos** (lógicos):
- **N:1** com `USER`: Insights são gerados para um usuário
- **N:1** com `INVOICE`: Insights podem referenciar uma fatura específica
- **N:1** com `CATEGORY`: Insights podem referenciar uma categoria
- **N:1** com `GOAL`: Insights podem referenciar uma meta

**Regras de Negócio**:
- Insights são recalculados toda vez que o dashboard é carregado
- Alertas de meta são gerados apenas para metas ativas
- O insight de parcelamento em recorrente só aparece se `isRecurring = true` e `isInstallment = true`

---

## 🔗 Diagrama Entidade-Relacionamento (Diagrama Conceitual)

```
┌─────────────────────────────────────────────────────────────────────┐
│                        DIAGRAMA ENTIDADE-RELACIONAMENTO              │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────┐
│    USER     │
├─────────────┤
│ PK id       │
│    name     │
│    email    │
│    photoUrl │
│    nickname │
│    phone    │
│    income   │
│    createdAt│
│    updatedAt│
└──────┬──────┘
       │
       │ 1
       │
       │ N
┌──────┴──────────────────────────────────────────────────────────────┐
│                                                                      │
│  ┌─────────────────┐          ┌──────────────────┐                 │
│  │    INVOICE      │          │   EXPENSE        │                 │
│  ├─────────────────┤          ├──────────────────┤                 │
│  │ PK id           │◄────1:N──┤ PK id            │                 │
│  │ FK userId       │          │    date          │                 │
│  │    dueDate      │          │    description   │                 │
│  │    totalValue   │          │    establishment │                 │
│  │    minPayment   │          │    city          │                 │
│  │    refMonth     │          │    value         │                 │
│  │    closingDate  │          │ FK category      │─────┐           │
│  │    uploadedAt   │          │    installment   │     │           │
│  │    isPaid       │          │    isInstallment │     │           │
│  │    paidDate     │          │    autoCat       │     │           │
│  └─────────────────┘          │    createdAt     │     │           │
│                               └──────────────────┘     │           │
│                                                        │           │
│  ┌─────────────────────────────────────────────────────┴──────┐    │
│  │                 CATEGORY                                   │    │
│  ├────────────────────────────────────────────────────────────┤    │
│  │ PK id                                                      │    │
│  │    name                                                    │    │
│  │    color                                                   │    │
│  │    isRecurring                                             │    │
│  │    isDefault                                               │    │
│  │    createdAt                                               │    │
│  └─────┬──────────────────────────────────────────────────────┘    │
│        │                                                            │
│        │ 1                                                          │
│        │                                                            │
│        │ N                                                          │
│  ┌─────┴────────────────────────────────────────────────────────┐  │
│  │        SAVED_CATEGORY_MAPPING                                │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │ PK establishment + userId                                    │  │
│  │    category                                                  │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─────────────────┐                                               │
│  │     GOAL        │                                               │
│  ├─────────────────┤                                               │
│  │ PK id           │                                               │
│  │ FK userId       │                                               │
│  │ FK category     │──────────────┐                                │
│  │    limitValue   │              │                                │
│  │    alertAt80    │              │                                │
│  │    alertAt100   │              │                                │
│  │    monthlyReset │              │                                │
│  │    isActive     │              │                                │
│  │    createdAt    │              │                                │
│  └─────────────────┘              │                                │
│                                   │                                │
│                                   │ N:1                            │
│                                   └────────────────────────────┐   │
│                                                                │   │
│  ┌────────────────────────────────────────────────────────────┴─┐ │
│  │                     INSIGHT                                  │ │
│  │              (Calculado em tempo real,                       │ │
│  │               não persistido no Firebase)                    │ │
│  ├──────────────────────────────────────────────────────────────┤ │
│  │    id (gerado)                                               │ │
│  │    title                                                     │ │
│  │    description                                               │ │
│  │    type                                                      │ │
│  │    severity                                                  │ │
│  │    relatedCategoryId                                         │ │
│  │    createdAt                                                 │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

---

## 📋 Tabela de Relacionamentos

| Entidade 1 | Cardinalidade | Entidade 2 | Tipo de Relacionamento | Descrição |
|------------|---------------|------------|------------------------|-----------|
| USER | 1 | N | INVOICE | Um usuário possui múltiplas faturas |
| USER | 1 | N | EXPENSE | Um usuário possui múltiplas despesas (através de faturas) |
| USER | 1 | N | CATEGORY | Um usuário pode criar múltiplas categorias personalizadas |
| USER | 1 | N | GOAL | Um usuário pode definir múltiplas metas |
| USER | 1 | N | SAVED_CATEGORY_MAPPING | Um usuário possui múltiplos mapeamentos estabelecimento→categoria |
| INVOICE | 1 | N | EXPENSE | Uma fatura contém múltiplas despesas |
| CATEGORY | 1 | N | EXPENSE | Uma categoria pode ser aplicada a múltiplas despesas |
| CATEGORY | 1 | N | GOAL | Uma categoria pode ter múltiplas metas (mas apenas uma ativa por vez) |
| CATEGORY | 1 | N | SAVED_CATEGORY_MAPPING | Múltiplos estabelecimentos podem mapear para a mesma categoria |
| EXPENSE | N | 1 | INVOICE | Múltiplas despesas pertencem a uma fatura |
| EXPENSE | N | 1 | CATEGORY | Múltiplas despesas podem pertencer a uma categoria |
| GOAL | N | 1 | USER | Múltiplas metas pertencem a um usuário |
| GOAL | N | 1 | CATEGORY | Múltiplas metas podem referenciar uma categoria |
| SAVED_CATEGORY_MAPPING | N | 1 | USER | Múltiplos mapeamentos pertencem a um usuário |
| SAVED_CATEGORY_MAPPING | N | 1 | CATEGORY | Múltiplos estabelecimentos mapeiam para uma categoria |

---

## 🔐 Regras de Integridade e Validação

### Integridade Referencial
- **USER → INVOICE**: Ao excluir um usuário, todas as suas faturas são excluídas (CASCADE)
- **INVOICE → EXPENSE**: Ao excluir uma fatura, todas as suas despesas são excluídas (CASCADE)
- **USER → GOAL**: Ao excluir um usuário, todas as suas metas são excluídas (CASCADE)
- **CATEGORY → EXPENSE**: Categorias podem ser excluídas, mas despesas mantêm referência (`category` pode ficar nulo)

### Validações de Domínio
1. **USER**:
   - `email` deve ser válido (regex: `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`)
   - `name` não pode ser vazio

2. **INVOICE**:
   - `totalValue` e `minimumPayment` devem ser ≥ 0
   - `referenceMonth` deve seguir formato "MMM/YYYY"
   - Apenas uma fatura por `referenceMonth` por usuário

3. **EXPENSE**:
   - `value` deve ser > 0
   - `description` e `establishment` não podem ser vazios
   - Se `installment` não for nulo, deve seguir formato "X/Y"

4. **GOAL**:
   - `limitValue` deve ser > 0
   - Apenas uma meta ativa por categoria por usuário

5. **CATEGORY**:
   - `name` não pode ser vazio
   - `color` deve ser hexadecimal válido (formato: `^#[0-9A-Fa-f]{6}$`)

### Regras de Negócio
1. **Auto-categorização**:
   - Despesas de estabelecimentos já mapeados são categorizadas automaticamente
   - Tarifas (ANUIDADE, PROTEÇÃO) são sempre categorizadas como "Taxas Cartão"

2. **Metas**:
   - Progresso é calculado somando despesas do mês atual da categoria
   - Alertas são disparados aos 80% e 100% do limite

3. **Insights**:
   - Calculados dinamicamente com base nos dados atuais
   - Não persistem no banco de dados

---

## 📦 Estrutura de Armazenamento no Firebase

### Caminho Completo das Entidades

```
Firebase Realtime Database/
├── users/
│   └── {userId}/                          # USER
│       ├── name, email, photoUrl, ...
│       │
│       ├── invoices/                      # INVOICE collection
│       │   └── {referenceMonth}/          # Ex: "JUN/2025"
│       │       ├── id
│       │       ├── userId
│       │       ├── dueDate
│       │       ├── totalValue
│       │       ├── minimumPayment
│       │       ├── referenceMonth
│       │       ├── closingDate
│       │       ├── uploadedAt
│       │       ├── isPaid
│       │       ├── paidDate
│       │       └── expenses/              # EXPENSE collection
│       │           └── {expenseId}/
│       │               ├── id
│       │               ├── date
│       │               ├── description
│       │               ├── establishment
│       │               ├── city
│       │               ├── value
│       │               ├── category
│       │               ├── installment
│       │               ├── isInstallment
│       │               ├── autoCategorized
│       │               └── createdAt
│       │
│       ├── categories/                    # CATEGORY data
│       │   ├── savedCategories/           # SAVED_CATEGORY_MAPPING
│       │   │   └── "{ESTABLISHMENT}": "Categoria"
│       │   └── customCategories/          # Categorias personalizadas
│       │       └── {categoryId}/
│       │           ├── id
│       │           ├── name
│       │           ├── color
│       │           ├── isRecurring
│       │           ├── isDefault
│       │           └── createdAt
│       │
│       └── goals/                         # GOAL collection
│           └── {goalId}/
│               ├── id
│               ├── userId
│               ├── category
│               ├── limitValue
│               ├── alertAt80
│               ├── alertAt100
│               ├── monthlyReset
│               ├── isActive
│               └── createdAt
```

---

## 🔄 Fluxo de Dados Principal

### 1. Upload e Processamento de Fatura
```
PDF Upload → PDFParser → ExtractedInvoiceData → Categorização → Invoice → Firebase
                                                      ↓
                                            SAVED_CATEGORY_MAPPING
```

### 2. Categorização de Despesas
```
EXPENSE (sem categoria)
    ↓
Verifica SAVED_CATEGORY_MAPPING
    ↓
[Encontrado] → Auto-categoriza → Atualiza EXPENSE.category
[Não encontrado] → Usuário categoriza manualmente → Cria/atualiza SAVED_CATEGORY_MAPPING
```

### 3. Cálculo de Progresso de Meta
```
GOAL (active) + EXPENSE (mesmo mês, mesma categoria)
    ↓
Soma valores → Calcula porcentagem
    ↓
[≥80%] → Gera INSIGHT (WARNING)
[≥100%] → Gera INSIGHT (CRITICAL)
```

---

## 📝 Observações Técnicas

1. **Firebase Realtime Database**: Utiliza estrutura hierárquica NoSQL, não SQL relacional
2. **Segurança**: Regras de segurança do Firebase garantem que cada usuário só acesse seus próprios dados
3. **Normalização**: Dados são parcialmente normalizados (categorias personalizadas separadas de padrões)
4. **Desnormalização**: Algumas informações são duplicadas para otimizar consultas (ex: `userId` em GOAL)
5. **Insights**: Não são persistidos, são calculados em tempo real no dispositivo

---

## 🎯 Considerações de Performance

1. **Indexação**: 
   - `users/{userId}/invoices/{referenceMonth}` permite busca rápida por mês
   - `users/{userId}/categories/savedCategories/{establishment}` permite lookup O(1) para auto-categorização

2. **Otimizações**:
   - Categorias padrão não são armazenadas no Firebase
   - Mapeamentos estabelecimento→categoria permitem categorização instantânea
   - Insights calculados sob demanda, não ocupam espaço no banco

3. **Limitações**:
   - Firebase Realtime Database tem limite de profundidade de 32 níveis (atual: ~5 níveis)
   - Tamanho máximo de string: 10MB (não é problema para este sistema)

---

## 📖 DESCRIÇÃO COMPLETA DO DIAGRAMA E RELACIONAMENTOS

### Visão Geral do Modelo de Dados

O sistema utiliza uma arquitetura NoSQL hierárquica no Firebase Realtime Database, onde **USER** é a entidade raiz que isola completamente os dados de cada usuário. Todas as demais entidades estão aninhadas dentro do nó do usuário, garantindo segurança e organização por tenant (multi-tenant).

### 1. USER (Entidade Raiz) - O Centro de Tudo

**Como funciona:**
- Cada usuário autenticado no Firebase Authentication possui um `uid` único que se torna o `id` da entidade USER
- O USER não é apenas um registro, mas sim um **container hierárquico** que agrupa todos os dados do usuário
- Quando um usuário faz login (Google ou email/senha), o sistema cria ou atualiza seu registro em `users/{userId}/`
- O Firebase Security Rules garantem que cada usuário só pode ler/escrever em `users/{userId}/`, nunca em outros usuários

**Relacionamentos diretos:**
- **1:N com INVOICE**: Um usuário pode ter N faturas (ilimitadas historicamente)
- **1:N com GOAL**: Um usuário pode criar N metas de gastos (uma por categoria, mas pode ter múltiplas categorias)
- **1:N com CATEGORY (personalizadas)**: Um usuário pode criar N categorias personalizadas
- **1:N com SAVED_CATEGORY_MAPPING**: Um usuário possui N mapeamentos estabelecimento→categoria

**Fluxo operacional:**
1. Usuário faz login → Firebase Auth retorna `uid`
2. Sistema verifica se `users/{uid}/` existe
3. Se não existe, cria registro básico (name, email, photoUrl, createdAt)
4. Se existe, atualiza `updatedAt` (login não cria novo registro)
5. Todas as operações subsequentes usam este `uid` como chave raiz

---

### 2. INVOICE (Fatura) - Armazenamento Hierárquico por Mês

**Como funciona:**
- Uma fatura é armazenada em `users/{userId}/invoices/{monthKey}/`, onde `monthKey` é convertido de "JUN/2025" para "2025-06"
- O sistema implementa **upsert**: se já existir uma fatura para aquele mês, ela é substituída completamente (não duplica)
- A chave de identificação é o `referenceMonth`, não um ID sequencial
- Cada fatura possui um `id` único interno, mas o Firebase usa `monthKey` como chave do nó

**Relacionamentos:**
- **N:1 com USER**: Múltiplas faturas pertencem a um único usuário
  - Busca: `database.child(userId).child("invoices").get()` retorna todas as faturas
  - Busca por mês: `database.child(userId).child("invoices").child("2025-06").get()`
  
- **1:N com EXPENSE**: Uma fatura contém N despesas
  - As despesas são armazenadas como sub-nó: `invoices/{monthKey}/expenses/{expenseId}/`
  - Cada despesa tem ID no formato `exp_1`, `exp_2`, etc. (baseado no índice)
  - Ao salvar, todas as despesas são gravadas em uma única operação atômica

**Fluxo operacional completo:**
1. Usuário faz upload do PDF → `PDFParserDataSourceFixed` extrai dados
2. Retorna `ExtractedInvoiceData` com lista de `ExtractedExpenseData` (sem categorias)
3. Sistema busca mapeamentos salvos do usuário → `CategoryService.getSavedMappings(userId)`
4. Auto-categoriza despesas baseado em `savedCategories` → `CategoryService.autoCategorizeExpenses()`
5. Usuário revisa/ajusta categorias na interface `CategorizeExpensesFragment`
6. Ao salvar:
   ```kotlin
   // Converte ExtractedInvoiceData para Invoice com categorias
   val expenses = extractedInvoice.expenses.map { extractedExpense ->
       Expense(
           category = categoryMappings[stableKey], // Mapeamento feito pelo usuário
           autoCategorized = categoryMappings.containsKey(...),
           // ... outros campos
       )
   }
   val invoice = Invoice(expenses = expenses, ...)
   InvoiceService.saveInvoice(userId, invoice)
   ```
7. Sistema salva em:
   ```
   users/{userId}/invoices/{monthKey}/
     ├── id, userId, dueDate, totalValue, ...
     └── expenses/
         ├── exp_1/ { date, establishment, value, category, ... }
         ├── exp_2/ { ... }
         └── ...
   ```

**Regras importantes:**
- Se o usuário enviar outra fatura do mesmo mês, a anterior é completamente substituída (não há histórico de versões)
- Ao excluir uma fatura, o sistema busca pelo `invoiceId` em todas as faturas, encontra o `monthKey` correspondente e remove o nó inteiro
- O campo `referenceMonth` no formato "JUN/2025" é mantido para exibição, mas a chave Firebase usa "2025-06"

---

### 3. EXPENSE (Despesa) - Filha da Fatura

**Como funciona:**
- Despesas **não existem independentemente** de uma fatura
- São sempre armazenadas dentro de `invoices/{monthKey}/expenses/{expenseId}/`
- Cada despesa representa uma linha de compra extraída do PDF da fatura
- Possui referência implícita à fatura através da hierarquia (não há FK explícita)

**Relacionamentos:**
- **N:1 com INVOICE**: Múltiplas despesas pertencem a uma única fatura
  - A relação é **hierárquica**, não referencial
  - Não há campo `invoiceId` na despesa, pois a hierarquia já estabelece a relação
  
- **N:1 com CATEGORY**: Uma despesa pode ter uma categoria (opcional)
  - O campo `category` armazena o **nome da categoria** (não o ID)
  - Pode ser `null` se não foi categorizada ainda
  - Quando categorizada, o nome é armazenado diretamente (ex: "Alimentação", "Mercado")

**Fluxo operacional:**
1. PDF é parseado → Extrai `ExtractedExpenseData` (sem categoria)
2. Sistema busca `savedCategories/{establishment}` do usuário
3. Se encontrado → `category = savedCategories[establishment]`, `autoCategorized = true`
4. Se não encontrado mas é tarifa → `category = "Taxas Cartão"`, `autoCategorized = true`
5. Se não encontrado → `category = null`, usuário categoriza manualmente
6. Ao categorizar manualmente → Sistema salva em `savedCategories/{establishment} = categoria`
7. Despesa salva com `category` preenchido

**Exemplo prático:**
```
Despesa 1: establishment = "CAFE DA ANA"
  → Busca em savedCategories["CAFE DA ANA"]
  → Encontra: "Alimentação"
  → Salva: category = "Alimentação", autoCategorized = true

Despesa 2: establishment = "LOJA NOVA"
  → Busca em savedCategories["LOJA NOVA"]
  → Não encontrado
  → Usuário categoriza como "Vestuário"
  → Salva em savedCategories["LOJA NOVA"] = "Vestuário"
  → Salva despesa: category = "Vestuário", autoCategorized = false
```

**Atualização de categoria:**
- Se o usuário edita a categoria de uma despesa já salva:
  1. Sistema atualiza: `invoices/{monthKey}/expenses/{expenseId}/category = novaCategoria`
  2. Sistema atualiza: `savedCategories/{establishment} = novaCategoria` (para futuras faturas)
  3. Todas as despesas futuras deste estabelecimento serão auto-categorizadas com a nova categoria

---

### 4. CATEGORY (Categoria) - Dualidade Padrão vs Personalizada

**Como funciona:**
- O sistema possui **dois tipos de categorias** com comportamentos diferentes:

#### 4.1 Categorias Padrão (Predefinidas)
- **Não são armazenadas no Firebase** (hardcoded no código)
- Definidas em `Category.DEFAULT_CATEGORIES` (12 categorias pré-configuradas)
- Têm IDs fixos: "food", "transport", "health", etc.
- Podem ser "ocultadas" (marcadas como deletadas) mas não removidas do código
- Quando ocultadas, são salvas em `users/{userId}/deletedDefaultCategories/{categoryId} = true`

#### 4.2 Categorias Personalizadas
- **São armazenadas** em `users/{userId}/customCategories/{categoryId}/`
- Usuário cria através da interface "Gerenciar Categorias"
- Têm `id` gerado pelo Firebase (`push().key`)
- Possuem `color` personalizada (hexadecimal)
- Podem ser editadas e excluídas livremente

**Relacionamentos:**
- **1:N com EXPENSE**: Uma categoria pode ter múltiplas despesas
  - A relação é **por nome**, não por ID
  - `Expense.category` armazena o nome (ex: "Alimentação")
  - Sistema resolve: busca categoria pelo nome para obter `isRecurring`, `color`, etc.
  
- **1:N com GOAL**: Uma categoria pode ter uma meta associada
  - `Goal.category` armazena o nome da categoria
  - Sistema permite apenas uma meta ativa por categoria
  
- **1:N com SAVED_CATEGORY_MAPPING**: Múltiplos estabelecimentos podem mapear para a mesma categoria
  - `savedCategories["CAFE DA ANA"] = "Alimentação"`
  - `savedCategories["PADARIA DO JOÃO"] = "Alimentação"`
  - Ambos apontam para a mesma categoria "Alimentação"

**Fluxo de busca de categorias:**
```kotlin
// Quando o sistema precisa de todas as categorias do usuário:
1. Busca categorias padrão: Category.DEFAULT_CATEGORIES
2. Busca categorias deletadas: deletedDefaultCategories
3. Filtra padrões: remove as deletadas
4. Busca personalizadas: customCategories
5. Retorna: (padrões filtrados) + personalizadas
```

**Resolução de nome:**
- Quando uma despesa tem `category = "Alimentação"`, o sistema precisa descobrir se é padrão ou personalizada
- Busca primeiro em `customCategories` pelo nome
- Se não encontrar, busca em `DEFAULT_CATEGORIES` pelo nome ou ID
- Usa `CategoryUtils.getCategoryName()` para normalizar nomes/IDs

---

### 5. SAVED_CATEGORY_MAPPING (Mapeamento Estabelecimento→Categoria)

**Como funciona:**
- É a **chave da auto-categorização** do sistema
- Armazenado em `users/{userId}/savedCategories/{establishment} = categoria`
- Funciona como um **dicionário/Map**: estabelecimento (chave) → categoria (valor)
- Não é uma entidade estruturada, é um mapa simples no Firebase

**Estrutura real no Firebase:**
```json
{
  "users": {
    "userId123": {
      "savedCategories": {
        "CAFE DA ANA": "Alimentação",
        "DELTA CEL CENTRO": "Outros",
        "AB SUPERMERCADOS LTD": "Mercado",
        "POSTO SHELL": "Combustível"
      }
    }
  }
}
```

**Relacionamentos:**
- **N:1 com USER**: Todos os mapeamentos pertencem a um usuário
- **N:1 com CATEGORY**: Múltiplos estabelecimentos podem mapear para a mesma categoria

**Fluxo de criação/atualização:**
1. Usuário categoriza uma despesa manualmente na interface
2. Sistema salva: `saveMapping(userId, establishment.toUpperCase(), categoriaNome)`
3. Estabelecimento é normalizado para UPPERCASE para comparação case-insensitive
4. Em faturas futuras, quando encontrar o mesmo estabelecimento:
   ```kotlin
   val categoryName = savedCategories[expense.establishment.toUpperCase()]
   if (categoryName != null) {
       expense.category = categoryName
       expense.autoCategorized = true
   }
   ```

**Importante:**
- Se o usuário alterar a categoria de uma despesa, o mapeamento é **atualizado**, não criado novo
- Se excluir uma categoria personalizada que está em uso, o sistema mantém as despesas mas pode perder o mapeamento (dependendo da implementação)
- Tarifas (ANUIDADE, PROTEÇÃO) são auto-categorizadas sem salvar mapeamento (regra hardcoded)

---

### 6. GOAL (Meta de Gastos) - Relacionamento com Categoria e Cálculo Dinâmico

**Como funciona:**
- Armazenadas em `users/{userId}/goals/{goalId}/`
- Cada meta está associada a uma categoria específica
- O progresso **não é armazenado**, é **calculado em tempo real** somando despesas
- Permite apenas uma meta ativa por categoria (validação lógica, não de banco)

**Relacionamentos:**
- **N:1 com USER**: Múltiplas metas pertencem a um usuário
  - Busca: `database.child(userId).child("goals").get()`
  - Retorna apenas metas com `isActive = true`
  
- **N:1 com CATEGORY**: Uma meta está associada a uma categoria
  - `Goal.category` armazena o **nome da categoria** (não ID)
  - Para calcular progresso, busca despesas da fatura atual com `expense.category == goal.category`

**Fluxo de cálculo de progresso:**
```kotlin
// Quando o dashboard é carregado:
1. Busca todas as metas ativas do usuário
2. Busca fatura atual do mês
3. Para cada meta:
   a. Soma todas as despesas da fatura onde expense.category == goal.category
   b. Calcula: spent / goal.limitValue * 100
   c. Determina status:
      - percentage >= 100 → EXCEEDED (vermelho)
      - percentage >= 80 → WARNING (amarelo)
      - else → NORMAL (verde)
4. Gera insights se necessário (80% ou 100%)
```

**Exemplo prático:**
```
Meta: category = "Alimentação", limitValue = 500.00
Fatura atual tem despesas:
  - CAFE DA ANA: 42.00, category = "Alimentação"
  - RESTAURANTE X: 89.50, category = "Alimentação"
  - PADARIA Y: 25.00, category = "Alimentação"

Total gasto: 156.50
Progresso: 156.50 / 500.00 * 100 = 31.3%
Status: NORMAL (verde)
```

**Regras importantes:**
- Metas com `monthlyReset = true` teoricamente reiniciam no mês seguinte (implementação futura)
- Metas desativadas (`isActive = false`) não aparecem no dashboard e não geram alertas
- Se não houver fatura do mês atual, o progresso é 0% para todas as metas
- A comparação de categoria é feita por nome, com normalização para lidar com variações

---

### 7. INSIGHT (Insight Financeiro) - Entidade Virtual

**Como funciona:**
- **NÃO é uma entidade persistida** no Firebase
- É uma entidade **lógica/calculada** gerada em tempo real
- Criada toda vez que o dashboard é carregado
- Baseada em análise dos dados existentes (faturas, metas, despesas)

**Relacionamentos (lógicos):**
- **N:1 com USER**: Insights são gerados para um usuário específico
- **N:1 com INVOICE**: Insights podem referenciar a fatura atual
- **N:1 com CATEGORY**: Insights podem mencionar uma categoria
- **N:1 com GOAL**: Insights podem alertar sobre uma meta

**Tipos de insights gerados:**
1. **Meta em Alerta (80%)**: 
   - Quando: `goalProgress.percentage >= 80 && goalProgress.percentage < 100`
   - Gera: `Insight(type=GOAL_WARNING, severity=WARNING)`
   
2. **Meta Ultrapassada (100%)**:
   - Quando: `goalProgress.percentage >= 100`
   - Gera: `Insight(type=GOAL_WARNING, severity=CRITICAL)`
   
3. **Parcelamento em Recorrente**:
   - Quando: `expense.isInstallment == true && expense.category.isRecurring == true`
   - Gera: `Insight(type=INSTALLMENT_WARNING, severity=WARNING)`
   
4. **Aumento de Gastos**:
   - Quando: `(totalAtual - totalAnterior) / totalAnterior * 100 > 10`
   - Gera: `Insight(type=SPENDING_INCREASE, severity=WARNING)`
   
5. **Fatura vs Renda**:
   - Quando: `invoiceTotal > userIncome` ou `invoiceTotal / userIncome * 100 >= 80`
   - Gera: `Insight(type=GENERAL, severity=CRITICAL ou WARNING)`

**Fluxo de geração:**
```kotlin
// No DashboardController.generateInsights():
1. Analisa fatura atual
2. Calcula progresso de todas as metas
3. Compara com mês anterior
4. Verifica parcelamentos em categorias recorrentes
5. Compara fatura com renda do usuário
6. Verifica status de pagamento
7. Gera lista de Insights
8. Retorna para exibição no dashboard
```

---

## 🔄 FLUXOS OPERACIONAIS COMPLETOS

### Fluxo 1: Upload e Processamento de Fatura (End-to-End)

```
1. Usuário seleciona PDF
   ↓
2. UploadInvoiceFragment → InvoiceController.parseInvoicePDF()
   ↓
3. InvoiceService.parseInvoicePDF() → PDFParserDataSourceFixed.parsePDF()
   ↓
4. Retorna ExtractedInvoiceData:
   - Cabeçalho: dueDate, totalValue, referenceMonth, etc.
   - Lista de ExtractedExpenseData (sem categorias)
   ↓
5. CategorizeExpensesFragment carrega
   ↓
6. CategoryController.autoCategorizeExpenses():
   a. Busca savedCategories do usuário
   b. Para cada despesa:
      - Busca savedCategories[establishment]
      - Se encontrado → category = mapeamento, autoCategorized = true
      - Se não encontrado mas é tarifa → category = "Taxas Cartão"
      - Se não encontrado → category = null (manual)
   ↓
7. Usuário revisa/ajusta categorias na interface
   ↓
8. Ao salvar → InvoiceController.saveInvoice():
   a. Converte ExtractedExpenseData → Expense (com categorias)
   b. Cria Invoice com lista de Expenses
   c. InvoiceService.saveInvoice() salva no Firebase:
      - users/{userId}/invoices/{monthKey}/ (cabeçalho)
      - users/{userId}/invoices/{monthKey}/expenses/ (despesas)
   ↓
9. Para cada despesa categorizada manualmente:
   → CategoryService.saveMapping(establishment, category)
   → Salva em users/{userId}/savedCategories/{establishment} = category
   ↓
10. Fatura salva e disponível para visualização
```

### Fluxo 2: Auto-Categorização em Faturas Futuras

```
1. Usuário faz upload de nova fatura
   ↓
2. Sistema extrai despesas (ExtractedExpenseData)
   ↓
3. CategoryService.autoCategorizeExpenses():
   a. Busca savedCategories do usuário
   b. Para cada despesa:
      - Se savedCategories[establishment] existe:
        → category = savedCategories[establishment]
        → autoCategorized = true
      - Se não existe mas description contém "ANUIDADE" ou "PROTEÇÃO":
        → category = "Taxas Cartão"
        → autoCategorized = true
      - Se não existe:
        → category = null
        → autoCategorized = false
   ↓
4. Interface exibe despesas:
   - Auto-categorizadas: já com categoria, usuário pode alterar
   - Não categorizadas: usuário deve categorizar manualmente
   ↓
5. Ao categorizar manualmente:
   → Salva mapeamento em savedCategories
   → Próxima fatura já vem auto-categorizada
```

### Fluxo 3: Cálculo de Metas e Geração de Insights

```
1. Dashboard carrega → DashboardController.getDashboardDataForMonth()
   ↓
2. Busca fatura atual (ou do mês selecionado)
   ↓
3. Busca todas as metas ativas do usuário (GoalService.getGoals())
   ↓
4. Para cada meta:
   a. Filtra despesas da fatura: expense.category == goal.category
   b. Soma valores: spent = sum(expense.value)
   c. Calcula: percentage = (spent / goal.limitValue) * 100
   d. Determina status:
      - percentage >= 100 → EXCEEDED
      - percentage >= 80 → WARNING
      - else → NORMAL
   ↓
5. Gera GoalProgress para cada meta
   ↓
6. DashboardController.generateInsights():
   a. Para metas em WARNING (80%):
      → Gera Insight(type=GOAL_WARNING, severity=WARNING)
   b. Para metas em EXCEEDED (100%):
      → Gera Insight(type=GOAL_WARNING, severity=CRITICAL)
   c. Verifica parcelamento em recorrentes:
      → Filtra despesas com isInstallment == true
      → Verifica se categoria é recorrente
      → Gera Insight(type=INSTALLMENT_WARNING)
   d. Compara com mês anterior:
      → Calcula variação percentual
      → Se > 10% → Gera Insight(type=SPENDING_INCREASE)
   e. Verifica fatura vs renda:
      → Se fatura > renda → Gera Insight(severity=CRITICAL)
      → Se fatura >= 80% renda → Gera Insight(severity=WARNING)
   ↓
7. Dashboard exibe:
   - Fatura atual com countdown
   - Gastos por categoria (gráfico pizza)
   - Metas com barras de progresso
   - Lista de insights gerados
```

### Fluxo 4: Edição de Categoria de Despesa

```
1. Usuário visualiza detalhes da fatura
   ↓
2. Seleciona despesa para editar categoria
   ↓
3. InvoiceController.updateExpenseCategory():
   a. Busca fatura por invoiceId (procura em todas as faturas)
   b. Encontra monthKey correspondente
   c. Atualiza: users/{userId}/invoices/{monthKey}/expenses/{expenseId}/category
   ↓
4. Atualiza mapeamento:
   → CategoryService.saveMapping(establishment, novaCategoria)
   → Salva: users/{userId}/savedCategories/{establishment} = novaCategoria
   ↓
5. Próximas faturas com mesmo estabelecimento serão auto-categorizadas com nova categoria
```

---

## 🎯 RESUMO DOS RELACIONAMENTOS CRÍTICOS

### Hierarquia de Dados (Firebase)
```
USER (raiz)
├── invoices/
│   └── {monthKey}/
│       ├── (cabeçalho da fatura)
│       └── expenses/
│           └── {expenseId}/
│               └── category → referencia CATEGORY.name
├── goals/
│   └── {goalId}/
│       └── category → referencia CATEGORY.name
├── savedCategories/
│   └── {establishment} → CATEGORY.name (valor)
└── customCategories/
    └── {categoryId}/
        └── (dados da categoria)
```

### Chaves de Relacionamento
- **USER → INVOICE**: `userId` (implícito na hierarquia)
- **INVOICE → EXPENSE**: `monthKey` + `expenseId` (hierarquia)
- **EXPENSE → CATEGORY**: `expense.category` (nome da categoria)
- **GOAL → CATEGORY**: `goal.category` (nome da categoria)
- **SAVED_CATEGORY_MAPPING → CATEGORY**: valor do mapeamento (nome da categoria)
- **SAVED_CATEGORY_MAPPING → EXPENSE**: chave do mapeamento = `expense.establishment`

### Integridade Referencial
- **Não há FKs explícitas** (Firebase NoSQL)
- Integridade mantida por:
  1. **Hierarquia**: Despesas sempre dentro de faturas
  2. **Regras de segurança**: Usuário só acessa seus próprios dados
  3. **Validação lógica**: Sistema valida que categoria existe antes de salvar
  4. **Cascata manual**: Ao excluir categoria, sistema pode atualizar despesas

### Normalização vs Desnormalização
- **Normalizado**: Categorias personalizadas (tabela separada)
- **Desnormalizado**: Nome da categoria duplicado em Expense, Goal, SavedCategoryMapping
  - Motivo: Performance (evita joins)
  - Trade-off: Se categoria mudar nome, não atualiza automaticamente em despesas antigas
  - Solução: Sistema resolve nome dinamicamente usando `CategoryUtils.getCategoryName()`

---

**Versão do Documento**: 2.0  
**Data de Criação**: 12/01/2025  
**Última Atualização**: 12/01/2025  
**Autor**: Sistema de Gestão de Fatura Sicoob - Documentação Técnica Completa

