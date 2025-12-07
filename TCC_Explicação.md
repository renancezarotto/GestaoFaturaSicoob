# Explicação Completa do Funcionamento Interno do Sistema

## Índice
1. [MainActivity - Ponto de Entrada do App](#mainactivity)
2. [LoginFragment - Tela de Autenticação](#loginfragment)
3. [RegisterFragment - Criação de Conta](#registerfragment)
4. [DashboardFragment - Tela Principal](#dashboardfragment)
5. [UploadInvoiceFragment - Upload de Fatura](#uploadinvoicefragment)
6. [CategorizeExpensesFragment - Categorização](#categorizeexpensesfragment)
7. [InvoicesFragment - Histórico de Faturas](#invoicesfragment)
8. [InvoiceDetailFragment - Detalhes da Fatura](#invoicedetailfragment)
9. [ReportsFragment - Relatórios](#reportsfragment)
10. [ProfileFragment - Perfil do Usuário](#profilefragment)
11. [ManageGoalsFragment - Gerenciamento de Metas](#managegoalsfragment)
12. [ManageCategoriesFragment - Gerenciamento de Categorias](#managecategoriesfragment)

---

## MainActivity - Ponto de Entrada do App

### O que acontece quando o app abre

Quando o aplicativo é iniciado, a **MainActivity** é a primeira tela que é carregada. Ela funciona como o "cérebro" central que coordena toda a navegação e autenticação do sistema.

**Inicialização do Firebase:**
- Assim que a Activity é criada, ela inicializa o Firebase Realtime Database através do `FirebaseManager`. Isso conecta o app ao banco de dados na nuvem e prepara todas as referências necessárias para operações futuras.

**Configuração da Navegação:**
- A MainActivity configura o Navigation Component, que é o sistema responsável por gerenciar todas as telas (fragments) do app. Ela conecta o `BottomNavigationView` (a barra inferior com os ícones) ao `NavController`, garantindo que quando você toca em um ícone, a tela correspondente é exibida.

**Verificação de Autenticação:**
- Imediatamente após a inicialização, a Activity verifica se existe um usuário logado no Firebase Auth. Essa verificação acontece de duas formas:
  1. **Verificação inicial**: No momento da criação, checa uma vez se há usuário autenticado.
  2. **Listener contínuo**: Configura um listener que fica "escutando" mudanças de autenticação em tempo real. Se o usuário fizer logout em qualquer lugar do app, o listener detecta e redireciona automaticamente para a tela de login.

**Decisão de Navegação:**
- Se **não há usuário logado**: A barra de navegação inferior é ocultada (não faz sentido mostrar se não está autenticado) e o app navega para o `LoginFragment`.
- Se **há usuário logado**: A barra de navegação é exibida e o app navega automaticamente para o `DashboardFragment`, que é a tela principal do sistema.

**Comportamento da Barra de Navegação:**
- Quando você toca em um item da barra inferior (Dashboard, Faturas, Relatórios, Perfil), a MainActivity limpa a pilha de telas anteriores. Isso significa que você não pode voltar para telas que estavam antes - você sempre vai direto para a tela selecionada, como se estivesse "resetando" a navegação.

---

## LoginFragment - Tela de Autenticação

### O que acontece quando a tela abre

Quando você chega na tela de login, o fragment já está pronto para receber suas credenciais. Não há carregamento de dados aqui - a tela apenas aguarda sua interação.

**Inicialização do Google Sign-In:**
- A tela configura o cliente do Google Sign-In, preparando o sistema para autenticação via Google. Isso envolve configurar o token de autenticação necessário para validar sua conta Google.

### Quando você faz login com email e senha

**Validação dos Campos:**
- Antes de enviar qualquer coisa ao servidor, o app valida localmente os campos:
  - Email deve estar no formato válido (ex: usuario@email.com)
  - Senha deve ter pelo menos 6 caracteres
  - Se algum campo estiver inválido, uma mensagem de erro aparece abaixo do campo correspondente

**Processamento do Login:**
- Quando você toca em "Entrar", o app:
  1. Mostra um overlay de loading (bloqueia os botões para evitar cliques duplos)
  2. Chama o `AuthController.loginWithEmail()`, que internamente usa o `AuthService`
  3. O `AuthService` se comunica com o Firebase Auth para validar suas credenciais
  4. Se o login for bem-sucedido, o Firebase Auth retorna um token de autenticação
  5. O token é armazenado automaticamente pelo Firebase (você não precisa fazer nada)
  6. O `AuthStateListener` na MainActivity detecta a mudança de autenticação
  7. A MainActivity automaticamente navega para o Dashboard e mostra a barra de navegação

**Tratamento de Erros:**
- Se o email ou senha estiverem incorretos, o Firebase retorna um erro específico. O app captura esse erro, traduz para uma mensagem amigável em português (usando o `AuthErrorUtils`) e exibe em um Snackbar vermelho.

### Quando você faz login com Google

**Fluxo do Google Sign-In:**
- Quando você toca em "Entrar com Google":
  1. O app abre a tela de seleção de conta do Google (fora do app)
  2. Você seleciona sua conta Google
  3. O Google retorna um token de autenticação
  4. O app recebe esse token através do `googleSignInLauncher`
  5. O token é enviado para o `AuthController.loginWithGoogle()`
  6. O `AuthService` usa o token para autenticar no Firebase
  7. O Firebase cria ou encontra sua conta e retorna os dados do usuário
  8. Se você é novo, o Firebase cria automaticamente um registro no Realtime Database com seus dados básicos (nome, email, foto)
  9. O app navega para o Dashboard

**Navegação para Registro:**
- Se você toca em "Criar conta", o app navega para o `RegisterFragment` usando o Navigation Component.

---

## RegisterFragment - Criação de Conta

### O que acontece quando a tela abre

A tela de registro é similar à de login - ela apenas aguarda seu preenchimento. Não há carregamento de dados.

### Quando você cria uma conta

**Validação dos Campos:**
- O app valida três campos antes de processar:
  - Nome deve ter pelo menos 3 caracteres
  - Email deve estar no formato válido
  - Senha deve ter pelo menos 6 caracteres
  - Se algum campo falhar, uma mensagem de erro aparece abaixo do campo

**Processamento do Registro:**
- Quando você toca em "Criar conta":
  1. O app mostra um loading
  2. Chama o `AuthController.registerWithEmail()`, que usa o `AuthService`
  3. O `AuthService` se comunica com o Firebase Auth para criar a conta
  4. O Firebase cria o usuário e retorna um token de autenticação
  5. O `AuthService` então cria um registro no Realtime Database com seus dados:
     - Nome completo
     - Email
     - Data de criação (timestamp)
     - Estrutura: `users/{userId}/` com todos os dados do perfil
  6. O token é armazenado automaticamente
  7. O `AuthStateListener` detecta a autenticação
  8. O app navega automaticamente para o Dashboard

**Tratamento de Erros:**
- Se o email já estiver cadastrado, o Firebase retorna um erro específico. O app traduz e exibe a mensagem. Outros erros (senha fraca, email inválido, etc.) também são tratados e exibidos de forma amigável.

---

## DashboardFragment - Tela Principal

### O que acontece quando a tela abre

O Dashboard é a tela mais complexa do sistema. Quando ela é aberta, acontece uma sequência de operações para carregar todos os dados necessários.

**Carregamento Inicial:**
- Assim que o fragment é criado, ele inicia o carregamento dos dados através do `loadDashboardData()`. Este processo é assíncrono (não trava a tela) e envolve múltiplas consultas ao Firebase.

**Processo de Carregamento (em paralelo):**
1. **Busca da Fatura Atual**: O `DashboardController` chama o `InvoiceService` para buscar a fatura mais recente do usuário. Se não houver fatura recente, tenta buscar a fatura do mês atual. Se não houver nenhuma, retorna null (e o dashboard mostra o estado vazio).

2. **Busca de Categorias**: O `CategoryController` busca todas as categorias do usuário (padrão + personalizadas). Isso é necessário para resolver os nomes das categorias nas despesas e para exibir as metas corretamente.

3. **Busca de Metas**: O `GoalController` busca todas as metas ativas do usuário. Metas inativas não são carregadas.

4. **Cálculo de Gastos por Categoria**: Se existe uma fatura, o `DashboardController` agrupa todas as despesas por categoria e calcula:
   - Total gasto em cada categoria
   - Percentual que cada categoria representa do total
   - Esses dados são usados para o gráfico de barras que mostra a distribuição dos gastos

5. **Cálculo de Progresso das Metas**: Para cada meta ativa, o sistema:
   - Filtra as despesas da fatura que pertencem à categoria da meta
   - Soma os valores dessas despesas
   - Calcula o percentual: `(gasto / limite) * 100`
   - Determina o status:
     - **NORMAL** (verde): < 80% do limite
     - **WARNING** (amarelo): >= 80% e < 100%
     - **EXCEEDED** (vermelho): >= 100%

6. **Cálculo do Countdown**: Se a fatura existe e não está paga, o sistema calcula quantos dias restam até o vencimento:
   - Compara a data de vencimento com a data atual
   - Calcula a diferença em dias
   - Se negativo, a fatura está vencida
   - Se positivo, mostra quantos dias restam
   - Se < 7 dias, marca como "urgente"

7. **Comparação com Mês Anterior**: O sistema busca a fatura do mês anterior e calcula a variação percentual:
   - Fórmula: `((gasto_atual - gasto_anterior) / gasto_anterior) * 100`
   - Se positivo, houve aumento
   - Se negativo, houve redução

8. **Geração de Insights**: O `DashboardController` analisa todos os dados coletados e gera insights automáticos:
   - **Parcelamento em categorias recorrentes**: Detecta se você está parcelando compras de categorias que são recorrentes (como mercado ou combustível). Isso é considerado um hábito prejudicial.
   - **Categoria dominante**: Se uma categoria representa mais de 40% dos gastos, gera um insight informativo.
   - **Metas em alerta**: Se alguma meta atingiu 80% ou 100%, gera um insight de alerta ou crítico.
   - **Aumento de gastos**: Se os gastos aumentaram mais de 10% em relação ao mês anterior, alerta.
   - **Fatura vencendo**: Se a fatura vence em menos de 7 dias, alerta.
   - **Status de pagamento**: Se a fatura está paga, mostra quando foi paga e se foi no prazo.
   - **Fatura vs Renda**: Se você cadastrou sua renda no perfil, compara a fatura com a renda e alerta se está muito alta.

**Atualização da Interface:**
- Quando todos os dados são carregados, o `updateUI()` é chamado:
  - Se não há fatura: Mostra o estado vazio com um botão para adicionar a primeira fatura
  - Se há fatura: Exibe todos os cards com os dados:
    - Card da fatura: Mês, valor total, data de vencimento, countdown
    - Lista de insights: Cards horizontais com os insights gerados
    - Lista de metas: Cards mostrando o progresso de cada meta com barra visual
    - Gráfico de barras: Mostra as 8 categorias com maior gasto

**Filtro por Mês:**
- Se você tocar no botão de filtro de mês, o sistema:
  1. Busca todas as faturas do usuário
  2. Extrai os meses disponíveis
  3. Mostra um diálogo com os meses
  4. Quando você seleciona um mês, o `DashboardController` busca especificamente a fatura daquele mês e recalcula todos os dados (gastos por categoria, metas, insights, etc.)
  5. O mês selecionado é salvo localmente (usando `MonthFilterManager`) para persistir entre aberturas do app

**Pull to Refresh:**
- Quando você arrasta a tela para baixo (pull to refresh), o sistema limpa o filtro de mês e recarrega os dados do mês mais recente.

---

## UploadInvoiceFragment - Upload de Fatura

### O que acontece quando a tela abre

A tela de upload é simples - ela apenas mostra um botão para selecionar o PDF. Não há carregamento de dados.

### Quando você seleciona um PDF

**Seleção do Arquivo:**
- Quando você toca em "Selecionar PDF", o app abre o seletor de arquivos do sistema Android (apenas arquivos PDF são aceitos). Isso é feito através do `ActivityResultLauncher`, que é um sistema do Android para receber resultados de outras telas.

**Cópia do Arquivo:**
- Quando você seleciona um PDF, o app recebe um URI (endereço) do arquivo. Como o parser de PDF precisa de um arquivo físico (não apenas um URI), o app:
  1. Copia o PDF do local original para o cache temporário do app
  2. Salva como "temp_invoice.pdf" no diretório de cache
  3. Extrai informações do arquivo (nome, tamanho) para exibir um preview

**Preview do PDF:**
- O app exibe um card mostrando:
  - Nome do arquivo
  - Tamanho em MB
  - Isso confirma que o arquivo foi carregado corretamente

**Processamento Automático:**
- Imediatamente após copiar o arquivo, o app inicia o processamento do PDF (não precisa de ação adicional do usuário). O processamento acontece em uma corrotina (thread separada) para não travar a interface.

### Processamento do PDF (Parsing)

**Extração de Texto:**
- O `InvoiceController` chama o `InvoiceService.processPDF()`, que por sua vez usa o `PDFParserDataSourceFixed.parsePDF()`. O parser:
  1. Abre o PDF usando a biblioteca iText7
  2. Extrai o texto de todas as páginas sequencialmente
  3. Converte o PDF em texto puro (perde formatação, mas mantém ordem)

**Parsing do Cabeçalho:**
- O parser analisa o texto extraído procurando por padrões específicos:
  - **Data de vencimento**: Procura por palavras como "VENCIMENTO" e extrai a data no formato encontrado
  - **Valor total**: Procura por "TOTAL" ou "VALOR TOTAL" e extrai o número
  - **Pagamento mínimo**: Procura por "PAGAMENTO MÍNIMO" e extrai o valor
  - **Período de referência**: Procura por padrões como "26 MAI A 23 JUN" e converte para "JUN/2025"
  - **Data de fechamento**: Extrai a data de fechamento da fatura

**Extração de Despesas:**
- O parser percorre o texto linha por linha procurando por padrões que indicam uma compra:
  - **Data**: Formato "DD MMM" (ex: "24 MAI")
  - **Estabelecimento**: Nome do local (em maiúsculas)
  - **Cidade**: Nome da cidade (em maiúsculas)
  - **Valor**: Formato "R$ X,XX"
  - **Parcela**: Formato opcional "XX/XX" (ex: "03/04")
- Para cada linha que corresponde a esse padrão, cria um objeto `ExtractedExpenseData`

**Filtragem Inteligente:**
- O parser **ignora** automaticamente:
  - Linhas com "PAGAMENTO" (pagamentos recebidos)
  - Linhas com "CREDITO" (créditos na fatura)
  - Valores negativos (indicam créditos)
- O parser **identifica automaticamente** tarifas:
  - "ANUIDADE" → Categoria "Taxas Cartão"
  - "PROTEÇÃO PERDA OU ROUBO" → Categoria "Taxas Cartão"

**Conversão de Datas:**
- Como as datas no PDF vêm sem ano (ex: "24 MAI"), o parser:
  1. Extrai o ano do cabeçalho (da data de vencimento ou fechamento)
  2. Converte "24 MAI" para "2025-05-24" (formato ISO)
  3. Se não conseguir extrair o ano, usa o ano atual como fallback

**Resultado do Parsing:**
- Se o parsing for bem-sucedido, o sistema cria um objeto `ExtractedInvoiceData` contendo:
  - Cabeçalho completo (vencimento, total, período, etc.)
  - Lista de todas as despesas extraídas
- Se houver erro (PDF corrompido, formato inválido, etc.), uma mensagem de erro é exibida.

**Navegação para Categorização:**
- Se o parsing for bem-sucedido, o app navega automaticamente para o `CategorizeExpensesFragment`, passando os dados extraídos através de um Bundle (pacote de dados).

**Limpeza:**
- Após o processamento, o arquivo temporário é mantido até que você saia da tela. Quando você sai, o arquivo é deletado automaticamente para liberar espaço.

---

## CategorizeExpensesFragment - Categorização

### O que acontece quando a tela abre

Quando você chega na tela de categorização, o fragment recebe os dados extraídos do PDF através do Bundle passado na navegação.

**Carregamento Inicial:**
- O fragment extrai o `ExtractedInvoiceData` do Bundle e imediatamente:
  1. Exibe o resumo da fatura (mês, valor total)
  2. Mostra a lista de todas as despesas extraídas
  3. Inicia o carregamento das categorias disponíveis

**Carregamento de Categorias:**
- O sistema busca duas listas de categorias:
  1. **Categorias padrão**: 12 categorias pré-definidas (Alimentação, Transporte, Saúde, etc.)
  2. **Categorias personalizadas**: Categorias criadas pelo usuário
- Essas categorias são combinadas e fornecidas ao adapter da lista para que você possa selecionar ao categorizar cada despesa.

**Auto-Categorização:**
- Enquanto as categorias são carregadas, o sistema executa a **auto-categorização**:
  1. O `CategoryController` busca todos os mapeamentos salvos (estabelecimento → categoria)
  2. Para cada despesa extraída, verifica se o estabelecimento já foi categorizado antes
  3. Se encontrar um mapeamento, aplica automaticamente a categoria à despesa
  4. Atualiza a interface mostrando a categoria já selecionada
  5. Exibe uma mensagem informando quantas despesas foram auto-categorizadas

**Como funciona o mapeamento:**
- O sistema salva no Firebase a associação entre o nome do estabelecimento e a categoria escolhida
- Estrutura: `users/{userId}/savedCategories/{estabelecimento} = {categoryId}`
- Exemplo: "CAFE DA ANA" → "Alimentação"
- Na próxima fatura, quando encontrar "CAFE DA ANA", aplica automaticamente "Alimentação"

### Quando você categoriza uma despesa

**Seleção de Categoria:**
- Quando você toca em uma despesa e seleciona uma categoria:
  1. O sistema atualiza a interface imediatamente (feedback visual)
  2. Salva o mapeamento no Firebase (estabelecimento → categoria)
  3. Atualiza o contador de progresso (ex: "15 de 50 categorizadas")
  4. Atualiza a barra de progresso visual

**Atualização do Mapeamento:**
- Se você alterar a categoria de uma despesa que já tinha categoria, o sistema:
  1. Atualiza o mapeamento no Firebase
  2. Nas próximas faturas, esse estabelecimento será categorizado com a nova categoria escolhida

### Quando você salva a fatura

**Validação:**
- O sistema permite salvar mesmo se nem todas as despesas estiverem categorizadas (salvamento parcial). O botão muda o texto para "Salvar parcialmente" se houver despesas sem categoria.

**Processo de Salvamento:**
- Quando você toca em "Salvar fatura":
  1. O app mostra um loading
  2. O `InvoiceController.saveInvoice()` é chamado, que usa o `InvoiceService`
  3. O `InvoiceService` processa os dados:
     - Converte `ExtractedInvoiceData` para o modelo `Invoice`
     - Para cada despesa, verifica se tem categoria mapeada
     - Cria objetos `Expense` com todas as informações (data, estabelecimento, valor, categoria, parcela)
     - Marca quais despesas foram auto-categorizadas vs manualmente categorizadas
  4. Salva no Firebase na estrutura:
     ```
     users/{userId}/invoices/{referenceMonth}/
       - invoiceId
       - dueDate
       - totalValue
       - referenceMonth
       - expenses/{expenseId}/
         - date, description, establishment, city, value
         - category (ID da categoria)
         - installment (ex: "03/04" ou null)
         - isInstallment (boolean)
         - autoCategorized (boolean)
     ```
  5. Se já existir uma fatura para aquele mês, ela é **sobrescrita** (não duplica)

**Navegação Após Salvamento:**
- Após salvar com sucesso, o app navega de volta para o `InvoicesFragment` (lista de faturas), mostrando a fatura recém-salva.

**Tratamento de Erros:**
- Se houver erro ao salvar (sem conexão, erro no Firebase, etc.), uma mensagem de erro é exibida e você pode tentar novamente.

---

## InvoicesFragment - Histórico de Faturas

### O que acontece quando a tela abre

Quando você acessa a lista de faturas, o fragment imediatamente inicia o carregamento de todas as faturas processadas.

**Carregamento das Faturas:**
- O `InvoiceController.getInvoices()` busca todas as faturas do usuário no Firebase:
  1. Acessa o caminho `users/{userId}/invoices/`
  2. Lê todos os nós (cada nó é um mês de referência)
  3. Para cada mês, lê os dados da fatura e todas as despesas
  4. Constrói objetos `Invoice` completos
  5. Ordena por data (mais recente primeiro)

**Exibição:**
- Se não há faturas: Mostra estado vazio com mensagem "Nenhuma fatura processada"
- Se há faturas: Exibe lista com cards mostrando:
  - Mês de referência (ex: "JUN/2025")
  - Valor total da fatura
  - Status (Paga/Pendente/Vencida)
  - Número de despesas

**Pull to Refresh:**
- Quando você arrasta para baixo, o sistema recarrega todas as faturas do Firebase.

### Quando você toca em uma fatura

**Navegação para Detalhes:**
- O app navega para o `InvoiceDetailFragment`, passando:
  - ID da fatura
  - Mês de referência (usado para buscar a fatura)

### Quando você exclui uma fatura

**Confirmação:**
- O app mostra um diálogo de confirmação antes de excluir.

**Processo de Exclusão:**
- Se você confirmar:
  1. O `InvoiceController.deleteInvoice()` remove a fatura do Firebase
  2. Remove todo o nó `users/{userId}/invoices/{referenceMonth}/` e tudo dentro (fatura + todas as despesas)
  3. Após exclusão, a lista é recarregada automaticamente

**Navegação para Upload:**
- O botão flutuante (+) navega para o `UploadInvoiceFragment` para processar uma nova fatura.

---

## InvoiceDetailFragment - Detalhes da Fatura

### O que acontece quando a tela abre

Quando você acessa os detalhes de uma fatura, o fragment recebe o mês de referência e busca todos os dados daquela fatura específica.

**Carregamento dos Dados:**
- O sistema executa múltiplas operações em paralelo:
  1. **Busca da Fatura**: O `InvoiceController.getInvoiceByMonth()` busca a fatura específica do mês recebido
  2. **Busca de Categorias**: Carrega todas as categorias (padrão + personalizadas) para resolver os nomes nas despesas
  3. **Carregamento de Despesas**: Todas as despesas da fatura já vêm junto com a fatura

**Exibição Inicial:**
- Quando os dados chegam, o fragment exibe:
  - Cabeçalho: Mês, valor total, data de vencimento, número de despesas
  - Status de pagamento: Calcula se está paga, pendente ou vencida
  - Lista de todas as despesas (inicialmente sem filtro)

**Configuração de Tabs:**
- O sistema analisa as despesas e cria tabs dinamicamente:
  - Tab "Todas": Mostra todas as despesas
  - Uma tab para cada categoria que tem despesas (ex: "Alimentação", "Transporte")
  - Tab "Não categorizadas": Mostra despesas sem categoria

### Quando você filtra por categoria

**Filtragem por Tab:**
- Quando você toca em uma tab:
  1. O sistema filtra as despesas pela categoria selecionada
  2. Atualiza a lista imediatamente
  3. Se não houver despesas naquela categoria, mostra estado vazio

### Quando você busca uma despesa

**Busca em Tempo Real:**
- O campo de busca tem um listener que observa cada tecla digitada:
  1. Filtra despesas que contenham o texto digitado em:
     - Nome do estabelecimento
     - Descrição
     - Valor (formato monetário ou número puro)
  2. A busca funciona em conjunto com o filtro de categoria (aplica ambos)
  3. A lista é atualizada instantaneamente enquanto você digita

### Quando você altera a categoria de uma despesa

**Atualização:**
- Quando você seleciona uma nova categoria para uma despesa:
  1. O app mostra um loading
  2. O `InvoiceController.updateExpenseCategory()` atualiza a despesa no Firebase
  3. Atualiza o mapeamento estabelecimento → categoria (para futuras auto-categorizações)
  4. Recarrega a lista localmente (sem buscar do Firebase novamente)
  5. Reaplica os filtros (categoria + busca) para atualizar a exibição

### Quando você marca a fatura como paga

**Marcação de Pagamento:**
- Quando você ativa o switch "Fatura paga":
  1. O app mostra um seletor de data (para você escolher quando pagou)
  2. Quando você seleciona a data, o `InvoiceController.updatePaymentStatus()` é chamado
  3. Atualiza no Firebase:
     - `isPaid = true`
     - `paidDate = data selecionada`
  4. O sistema recalcula o status:
     - Compara data de pagamento com data de vencimento
     - Se pagou antes: "Paga com X dias de antecedência"
     - Se pagou no prazo: "Paga no prazo"
     - Se pagou depois: "Paga com X dias de atraso"
  5. Atualiza a interface mostrando o novo status

**Desmarcação:**
- Se você desativar o switch, a fatura volta a "Pendente" e a data de pagamento é removida.

### Quando você exclui a fatura

- O processo é idêntico ao da lista de faturas: confirmação → exclusão no Firebase → navegação de volta.

---

## ReportsFragment - Relatórios

### O que acontece quando a tela abre

A tela de relatórios é um container que exibe duas abas: Relatório Mensal e Relatório Anual. Ela usa um `ViewPager2` para permitir deslizar entre as duas visualizações.

**Configuração das Abas:**
- O fragment cria um adapter que gerencia dois fragments filhos:
  1. `MonthlyReportFragment` - Relatório mensal
  2. `AnnualReportFragment` - Relatório anual
- As abas são sincronizadas com o ViewPager através do `TabLayoutMediator`.

---

## MonthlyReportFragment - Relatório Mensal

### O que acontece quando a tela abre

Quando você acessa o relatório mensal, o fragment carrega todas as faturas do usuário para construir a lista de meses disponíveis.

**Carregamento Inicial:**
- O sistema:
  1. Busca todas as faturas do usuário
  2. Extrai os meses de referência únicos
  3. Ordena do mais recente para o mais antigo
  4. Preenche um Spinner (dropdown) com os meses
  5. Seleciona automaticamente o mês mais recente (ou o mês salvo no filtro)
  6. Carrega o relatório do mês selecionado

**Carregamento do Relatório:**
- Quando um mês é selecionado:
  1. Busca a fatura específica daquele mês
  2. Carrega todas as categorias (para resolver nomes)
  3. Calcula os dados do relatório:
     - Total gasto no mês
     - Número de despesas
     - Número de categorias distintas
     - Comparação com mês anterior (se existir)

**Exibição dos Dados:**
- O relatório exibe:
  1. **Resumo**: Total, número de despesas, número de categorias
  2. **Comparação com Mês Anterior**:
     - Calcula a diferença percentual: `((atual - anterior) / anterior) * 100`
     - Se positivo: "📈 X% maior que mês anterior"
     - Se negativo: "📉 X% menor que mês anterior"
  3. **Top 5 Categorias**:
     - Agrupa despesas por categoria
     - Soma os valores
     - Ordena do maior para o menor
     - Exibe as 5 primeiras com valor e percentual
  4. **Top 5 Estabelecimentos**:
     - Agrupa despesas por estabelecimento
     - Soma os valores
     - Ordena do maior para o menor
     - Exibe os 5 primeiros

**Filtro por Mês:**
- O mês selecionado é salvo localmente (usando `MonthFilterManager`) e persiste entre aberturas do app.

---

## AnnualReportFragment - Relatório Anual

### O que acontece quando a tela abre

O relatório anual carrega todas as faturas do usuário e faz análises consolidadas de todo o ano.

**Carregamento Inicial:**
- O sistema:
  1. Busca todas as faturas do usuário
  2. Carrega todas as categorias
  3. Calcula os dados consolidados do ano

**Cálculos do Relatório:**
- O sistema calcula:
  1. **Total Anual**: Soma de todas as faturas
  2. **Média Mensal**: Total anual / número de meses
  3. **Melhor Mês**: Mês com menor gasto
  4. **Pior Mês**: Mês com maior gasto
  5. **Evolução Mensal**: Para cada mês, calcula:
     - Valor gasto
     - Diferença percentual em relação à média anual
     - Comparação com mês anterior (se existir)
  6. **Top 5 Categorias do Ano**: Agrupa todas as despesas de todas as faturas por categoria e mostra as 5 com maior gasto

**Exibição dos Dados:**
- O relatório exibe:
  1. **Resumo**: Total anual, média mensal, número de meses
  2. **Melhor e Pior Mês**: Cards destacando os extremos
  3. **Evolução Mensal**: 
     - Lista todos os meses em ordem cronológica
     - Para cada mês, mostra:
       - Valor gasto
       - Diferença percentual da média (ex: "+15%", "-8%")
       - Comparação com mês anterior (ex: "+5% vs mês anterior")
       - Cor indicativa:
         - Vermelho: Muito acima da média (>30%)
         - Laranja: Acima da média (>15%)
         - Verde: Próximo da média (-15% a +15%)
         - Azul: Abaixo da média (<-15%)
  4. **Top 5 Categorias**: Mesma lógica do relatório mensal, mas consolidado de todo o ano

**Cálculo de Tendências:**
- Para cada mês, o sistema compara com o mês anterior:
  - Se aumento > 5%: Marca como tendência de alta (vermelho)
  - Se redução > 5%: Marca como tendência de baixa (verde)
  - Se variação entre -5% e +5%: Marca como estável (laranja)

---

## ProfileFragment - Perfil do Usuário

### O que acontece quando a tela abre

Quando você acessa o perfil, o fragment busca todos os dados do usuário no Firebase.

**Carregamento dos Dados:**
- O `AuthController.getCompleteUserData()` busca:
  1. Dados do Firebase Auth (nome, email, foto)
  2. Dados do Realtime Database (renda, telefone, data de criação)
  3. Combina tudo em um objeto `User` completo

**Exibição:**
- O fragment exibe:
  - Nome do usuário
  - Email
  - Foto (se houver, senão ícone padrão)
  - "Usuário desde [ano]"
  - Informações financeiras (se cadastradas):
    - Renda mensal
    - Telefone
  - Se não houver informações financeiras, mostra mensagem para cadastrar

### Quando você edita o perfil

**Abertura do Dialog:**
- Quando você toca em "Editar perfil", abre um dialog (`EditProfileDialog`) que permite editar:
  - Nome
  - Renda mensal
  - Telefone

**Salvamento:**
- Quando você salva:
  1. O `AuthController` atualiza os dados no Firebase
  2. Atualiza o Firebase Auth (nome)
  3. Atualiza o Realtime Database (renda, telefone)
  4. Após salvar, o fragment recarrega os dados automaticamente

### Quando você faz logout

**Confirmação:**
- O app mostra um diálogo de confirmação.

**Processo de Logout:**
- Se você confirmar:
  1. O `AuthController.logout()` chama o Firebase Auth para fazer logout
  2. O Firebase limpa o token de autenticação
  3. O `AuthStateListener` na MainActivity detecta a mudança
  4. A MainActivity automaticamente:
     - Oculta a barra de navegação
     - Navega para o LoginFragment
     - Limpa qualquer dado temporário

**Navegação para Outras Telas:**
- O perfil tem botões para:
  - Gerenciar Categorias → `ManageCategoriesFragment`
  - Gerenciar Metas → `ManageGoalsFragment`

---

## ManageGoalsFragment - Gerenciamento de Metas

### O que acontece quando a tela abre

Quando você acessa a tela de metas, o fragment busca todas as metas ativas do usuário.

**Carregamento:**
- O `GoalController.getGoals()` busca no Firebase:
  - Caminho: `users/{userId}/goals/`
  - Filtra apenas metas ativas (`isActive = true`)
  - Ordena por data de criação

**Exibição:**
- Se não há metas: Mostra estado vazio
- Se há metas: Lista todas com:
  - Nome da categoria
  - Limite da meta
  - Status dos alertas (80% e 100%)

### Quando você cria uma meta

**Abertura do Dialog:**
- Quando você toca no botão (+), abre um dialog que permite:
  1. Selecionar categoria (dropdown com todas as categorias disponíveis)
  2. Definir valor limite (com máscara de moeda)
  3. Ativar/desativar alerta aos 80%
  4. Ativar/desativar alerta aos 100%

**Validação:**
- O sistema valida:
  - Categoria é obrigatória
  - Valor deve ser maior que zero

**Salvamento:**
- Quando você salva:
  1. O sistema verifica se já existe uma meta para aquela categoria
  2. Se existir, **deleta a meta antiga** (só permite uma meta por categoria)
  3. Cria a nova meta no Firebase:
     ```
     users/{userId}/goals/{goalId}/
       - category (ID da categoria)
       - limitValue
       - alertAt80
       - alertAt100
       - monthlyReset (sempre true)
       - isActive (sempre true)
     ```
  4. Recarrega a lista

**Máscara de Moeda:**
- O campo de valor tem uma máscara que formata automaticamente enquanto você digita:
  - Converte números para formato "R$ X,XX"
  - Remove caracteres não numéricos
  - Divide por 100 para converter centavos em reais

### Quando você edita uma meta

- O processo é idêntico à criação, mas o dialog vem pré-preenchido com os dados da meta existente.

### Quando você exclui uma meta

- Confirmação → Exclusão no Firebase → Recarregamento da lista.

**Pull to Refresh:**
- Arrastar para baixo recarrega as metas do Firebase.

---

## ManageCategoriesFragment - Gerenciamento de Categorias

### O que acontece quando a tela abre

Quando você acessa a tela de categorias, o fragment busca todas as categorias (padrão + personalizadas).

**Carregamento:**
- O `CategoryController.getCategories()` busca:
  1. Categorias padrão (12 categorias pré-definidas)
  2. Categorias personalizadas do usuário no Firebase
  3. Combina e separa em duas listas

**Exibição:**
- O fragment mostra duas seções:
  1. **Categorias Padrão**: Lista as 12 categorias padrão (não podem ser excluídas, podem ser visualizadas)
  2. **Categorias Personalizadas**: Lista categorias criadas pelo usuário (podem ser editadas e excluídas)

### Quando você cria uma categoria personalizada

**Abertura do Dialog:**
- Quando você toca no botão (+), abre um dialog que permite:
  1. Digitar nome da categoria
  2. Marcar como recorrente (sim/não)

**Validação:**
- Nome é obrigatório e não pode estar vazio.

**Salvamento:**
- Quando você salva:
  1. O `CategoryController.createCategory()` salva no Firebase:
     ```
     users/{userId}/customCategories/{categoryId}/
       - name
       - isRecurring
       - isDefault (sempre false)
     ```
  2. A categoria fica disponível imediatamente para uso em categorização e metas

**Categoria Recorrente:**
- Se marcada como recorrente, a categoria é usada nos insights do dashboard para detectar parcelamento em compras recorrentes (hábito prejudicial).

### Quando você edita uma categoria

- Categorias padrão não podem ser editadas (apenas visualizadas).
- Categorias personalizadas podem ser editadas (mesmo processo de criação, mas com dados pré-preenchidos).

### Quando você exclui uma categoria

- Apenas categorias personalizadas podem ser excluídas.
- Confirmação → Exclusão no Firebase → Recarregamento da lista.
- **Atenção**: Se a categoria estava sendo usada em despesas ou metas, ela continua sendo referenciada (mas não aparece mais na lista de categorias disponíveis).

---

## Considerações Finais

### Fluxo de Dados Geral

O sistema segue uma arquitetura em camadas:

1. **Fragment (UI)**: Recebe interações do usuário e exibe dados
2. **Controller**: Coordena operações de negócio
3. **Service**: Implementa lógica específica (autenticação, faturas, categorias, metas)
4. **DataSource**: Acessa fontes de dados (Firebase, PDF Parser)
5. **Firebase**: Armazena todos os dados persistentes

### Processamento Assíncrono

Todas as operações que envolvem:
- Leitura/escrita no Firebase
- Processamento de PDF
- Cálculos complexos (insights, relatórios)

São executadas em **corrotinas** (threads separadas) para não travar a interface. O app sempre mostra feedback visual (loading) durante essas operações.

### Sincronização em Tempo Real

O Firebase Realtime Database mantém os dados sincronizados automaticamente. Se você atualizar uma fatura em um dispositivo, ela será atualizada em todos os outros dispositivos onde você estiver logado.

### Tratamento de Erros

Todos os pontos de falha possíveis são tratados:
- Sem conexão: Mensagem de erro clara
- Dados inválidos: Validação antes de salvar
- Erros do Firebase: Traduzidos para mensagens amigáveis
- PDFs corrompidos: Mensagem específica

O sistema sempre tenta se recuperar graciosamente e nunca deixa o usuário em um estado inconsistente.

