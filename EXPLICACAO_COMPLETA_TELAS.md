# Explicação Completa do Funcionamento Interno das Telas

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
10. [MonthlyReportFragment - Relatório Mensal](#monthlyreportfragment)
11. [AnnualReportFragment - Relatório Anual](#annualreportfragment)
12. [ProfileFragment - Perfil do Usuário](#profilefragment)
13. [ManageGoalsFragment - Gerenciamento de Metas](#managegoalsfragment)
14. [ManageCategoriesFragment - Gerenciamento de Categorias](#managecategoriesfragment)

---

## MainActivity - Ponto de Entrada do App

### O que acontece quando o app abre

A **MainActivity** é a primeira tela que é carregada quando o aplicativo inicia. Ela funciona como o "cérebro central" que coordena toda a navegação e autenticação do sistema.

**Inicialização do Firebase:**
Quando a Activity é criada, ela inicializa o Firebase Realtime Database através do `FirebaseManager`. Isso conecta o app ao banco de dados na nuvem e prepara todas as referências necessárias para operações futuras. O Firebase é inicializado como um singleton, então a conexão permanece ativa durante toda a execução do app.

**Configuração da Navegação:**
A MainActivity configura o Navigation Component, que é o sistema responsável por gerenciar todas as telas (fragments) do app. Ela conecta o `BottomNavigationView` (a barra inferior com os ícones) ao `NavController`, garantindo que quando você toca em um ícone, a tela correspondente é exibida. O Navigation Component usa o arquivo `nav_graph.xml` para saber quais telas existem e como navegar entre elas.

**Por que limpar o back stack?**
Quando o usuário toca em "Dashboard" no bottom nav, não quer voltar para a tela anterior, mas sim ir direto para o Dashboard limpo. Por isso, a MainActivity configura o comportamento para limpar o back stack ao navegar pelos itens do bottom navigation, evitando acumulação de fragments e melhorando a performance.

**Verificação de Autenticação:**
Imediatamente após a inicialização, a Activity verifica se existe um usuário logado no Firebase Auth. Essa verificação acontece de duas formas:

1. **Verificação inicial (observeAuthState):** Quando a Activity é criada, ela verifica uma única vez o estado de autenticação. Se não há usuário logado, oculta a barra de navegação inferior e navega para o LoginFragment. Se há usuário logado, mostra a barra de navegação e navega para o DashboardFragment.

2. **Listener contínuo (setupAuthStateListener):** Além da verificação inicial, a Activity registra um listener que escuta mudanças de autenticação em tempo real. Isso significa que se o usuário fizer logout em qualquer lugar do app, o listener detecta automaticamente e navega para a tela de login, ocultando a barra de navegação. Da mesma forma, se o usuário fizer login, o listener mostra a barra de navegação e navega para o Dashboard.

**Proteções contra Memory Leak:**
O listener de autenticação mantém uma referência à Activity. Se não for removido quando a Activity é destruída, pode causar memory leak. Por isso, a MainActivity remove o listener no método `onDestroy()`, garantindo que a Activity possa ser coletada pelo Garbage Collector quando não for mais necessária.

---

## LoginFragment - Tela de Autenticação

### O que a tela mostra

A tela de login exibe dois campos de entrada (email e senha), um botão para fazer login com email/senha, um botão para fazer login com Google, e um link para criar uma nova conta.

### Como funciona internamente

**Quando a tela abre:**
O Fragment infla o layout usando ViewBinding, que gera código type-safe automaticamente a partir do XML. Isso significa que o código tem acesso direto às views sem precisar usar `findViewById`. O Fragment também inicializa o Google Sign-In Client, que é necessário para o login com Google.

**Validação de campos:**
Quando o usuário toca no botão "Entrar", o Fragment primeiro valida os campos. Ele verifica se o email não está vazio, se tem formato válido de email, se a senha não está vazia e se tem pelo menos 6 caracteres. Se algum campo estiver inválido, exibe uma mensagem de erro abaixo do campo correspondente usando o `TextInputLayout.error`.

**Login com Email/Senha:**
Se a validação passar, o Fragment chama o `AuthController.loginWithEmail()`. O Controller delega para o `AuthService`, que usa o Firebase Auth para autenticar o usuário. Durante o processo, o Fragment mostra um overlay de loading que bloqueia os botões para evitar múltiplos cliques.

O resultado vem como um `Result<T>`, que é um padrão usado no app para tratar sucesso e erro de forma consistente. Se o login for bem-sucedido, o Fragment exibe uma Snackbar de sucesso e navega para o DashboardFragment usando o Navigation Component. Se houver erro, exibe uma Snackbar com a mensagem de erro retornada pelo Firebase Auth.

**Login com Google:**
Quando o usuário toca no botão "Entrar com Google", o Fragment usa um `ActivityResultLauncher` para abrir a tela de seleção de conta do Google. O Google Sign-In Client gerencia todo o fluxo de autenticação. Quando o usuário seleciona uma conta, o Google retorna um token de ID. O Fragment extrai esse token e chama o `AuthController.loginWithGoogle()`, que usa o Firebase Auth para autenticar com o token do Google.

**Navegação após login:**
Após um login bem-sucedido (seja com email ou Google), o Fragment navega para o DashboardFragment. A navegação usa o Navigation Component, que automaticamente atualiza o BottomNavigationView na MainActivity para mostrar que o Dashboard está selecionado. A navegação também limpa o back stack, então o usuário não pode voltar para a tela de login usando o botão voltar.

**Por que usar Fragment e não Activity?**
Fragments são mais leves e permitem melhor reutilização de código. Além disso, o Navigation Component funciona melhor com Fragments, permitindo transições suaves entre telas e gerenciamento automático do back stack.

---

## RegisterFragment - Criação de Conta

### O que a tela mostra

A tela de registro exibe três campos de entrada (nome, email e senha), um botão para criar conta, e um link para voltar ao login.

### Como funciona internamente

**Quando a tela abre:**
Assim como o LoginFragment, o RegisterFragment infla o layout usando ViewBinding e configura os listeners dos botões.

**Validação de campos:**
Quando o usuário toca no botão "Criar Conta", o Fragment valida todos os campos:
- Nome: não pode estar vazio e deve ter pelo menos 3 caracteres
- Email: não pode estar vazio e deve ter formato válido
- Senha: não pode estar vazia e deve ter pelo menos 6 caracteres

Se algum campo estiver inválido, exibe uma mensagem de erro abaixo do campo usando o `TextInputLayout.error`.

**Criação de conta:**
Se a validação passar, o Fragment chama o `AuthController.registerWithEmail()`. O Controller delega para o `AuthService`, que usa o Firebase Auth para criar a conta. O Firebase Auth automaticamente cria o usuário e faz login, então não é necessário fazer login separadamente após o registro.

Durante o processo, o Fragment mostra um overlay de loading. Se o registro for bem-sucedido, exibe uma Snackbar de sucesso e navega para o DashboardFragment. Se houver erro (por exemplo, email já cadastrado ou senha fraca), exibe uma Snackbar com a mensagem de erro.

**Navegação de volta:**
O link "Voltar ao login" usa `findNavController().popBackStack()`, que remove o RegisterFragment do back stack e volta para o LoginFragment que estava abaixo dele.

**Por que o registro já faz login?**
O Firebase Auth automaticamente autentica o usuário após criar a conta, então não é necessário fazer login separadamente. Isso melhora a experiência do usuário, evitando um passo extra.

---

## DashboardFragment - Tela Principal

### O que a tela mostra

O Dashboard é a tela principal do app. Ela exibe um resumo da fatura atual (mês, valor total, data de vencimento), um countdown até o vencimento, uma lista de insights automáticos, cards de progresso das metas, e um botão para adicionar nova fatura.

### Como funciona internamente

**Quando a tela abre:**
O Fragment configura os RecyclerViews para insights e metas, configura listeners dos botões, e carrega os dados do dashboard chamando `loadDashboardData()`.

**Carregamento de dados:**
O Fragment chama o `DashboardController.getDashboardData()`, que é responsável por agregar dados de múltiplos serviços. O Controller faz várias chamadas em paralelo:

1. **Busca a fatura atual:** Chama `InvoiceService.getLatestInvoice()` para buscar a fatura mais recente. Se não houver fatura recente, tenta buscar a fatura do mês atual.

2. **Calcula o countdown:** Se a fatura existe e não está paga, calcula quantos dias restam até o vencimento. O cálculo compara a data de vencimento com a data atual e converte a diferença em dias. Se a diferença for negativa, significa que a fatura está vencida.

3. **Agrega gastos por categoria:** Agrupa todas as despesas da fatura por categoria e calcula o total e percentual de cada categoria. Isso é usado para exibir informações sobre onde o dinheiro está sendo gasto.

4. **Busca metas ativas:** Chama `GoalService.getGoals()` para buscar todas as metas do usuário que estão ativas.

5. **Calcula progresso das metas:** Para cada meta, calcula quanto foi gasto na categoria correspondente na fatura atual. Compara o valor gasto com o limite da meta e determina o status (NORMAL, WARNING aos 80%, ou EXCEEDED aos 100%).

6. **Compara com mês anterior:** Busca a fatura do mês anterior e calcula a variação percentual. Isso é usado para gerar insights sobre aumento ou redução de gastos.

7. **Gera insights automáticos:** O Controller tem uma função complexa `generateInsights()` que analisa todos os dados e gera insights automáticos. Os insights incluem:
   - Parcelamento em categorias recorrentes (alerta de hábito prejudicial)
   - Categoria dominante (se uma categoria representa mais de 40% dos gastos)
   - Alertas de metas (80% ou 100% atingidos)
   - Aumento de gastos (se aumentou mais de 10% vs mês anterior)
   - Fatura vencendo (se vence em menos de 7 dias)
   - Status de pagamento (se a fatura está paga)
   - Fatura vs renda (se a fatura é maior que a renda ou representa mais de 80% da renda)

**Exibição dos dados:**
Após receber os dados do Controller, o Fragment atualiza a UI:
- Exibe o mês da fatura, valor total e data de vencimento
- Exibe o countdown com cor diferente dependendo do status (verde se ainda tem tempo, amarelo se está urgente, vermelho se está vencida)
- Atualiza o RecyclerView de insights com a lista de insights gerados
- Atualiza o RecyclerView de metas com os cards de progresso
- Se não há fatura, exibe um estado vazio com um botão para adicionar a primeira fatura

**Filtro de mês:**
O Dashboard tem um botão de filtro que permite selecionar um mês específico para visualizar. Quando o usuário toca no botão, o Fragment busca todas as faturas disponíveis e exibe um dialog com a lista de meses. Quando o usuário seleciona um mês, o Fragment salva a seleção usando `MonthFilterManager` (que persiste no SharedPreferences) e recarrega os dados do dashboard para aquele mês específico.

**Pull to refresh:**
O Dashboard tem um SwipeRefreshLayout que permite atualizar os dados puxando a tela para baixo. Quando o usuário faz isso, o Fragment limpa o filtro de mês e recarrega os dados do dashboard.

**Por que usar um Controller?**
O Dashboard precisa agregar dados de múltiplos serviços (InvoiceService, GoalService, CategoryService, AuthService). O Controller centraliza essa lógica de agregação, mantendo o Fragment focado apenas na exibição. Isso facilita testes e manutenção.

**Por que gerar insights automaticamente?**
Os insights ajudam o usuário a entender padrões financeiros sem precisar analisar os dados manualmente. Eles são gerados automaticamente baseados em regras de negócio definidas, como "parcelamento em categorias recorrentes é prejudicial" ou "aumento de mais de 10% é significativo".

---

## UploadInvoiceFragment - Upload de Fatura

### O que a tela mostra

A tela de upload exibe um botão para selecionar um arquivo PDF, e após a seleção, mostra um card com preview do PDF (nome e tamanho) e um indicador de processamento.

### Como funciona internamente

**Seleção de PDF:**
Quando o usuário toca no botão "Selecionar PDF", o Fragment usa um `ActivityResultLauncher` com `ActivityResultContracts.GetContent()` para abrir o seletor de arquivos do sistema. O launcher está configurado para aceitar apenas arquivos PDF (MIME type "application/pdf").

**Tratamento do PDF selecionado:**
Quando o usuário seleciona um PDF, o callback do launcher recebe uma URI do arquivo. O Fragment então copia o PDF do storage do usuário para um arquivo temporário no cache do app. Isso é necessário porque o `PDFParserDataSource` precisa de um objeto `File`, não uma URI.

O processo de cópia acontece em uma coroutine para não bloquear a UI. Durante a cópia, o Fragment mostra um loading com a mensagem "Carregando PDF...". Após copiar, o Fragment extrai o nome do arquivo (usando um ContentResolver para consultar metadados) e calcula o tamanho em MB, exibindo essas informações em um card de preview.

**Processamento automático:**
Após copiar o PDF, o Fragment automaticamente inicia o processamento chamando `processPDF()`. Isso mostra um loading com a mensagem "Processando fatura..." e chama o `InvoiceController.processPDF()`.

O Controller delega para o `PDFParserDataSource`, que usa a biblioteca Apache PDFBox para fazer parsing do PDF. O parser lê o PDF linha por linha, identifica a seção de lançamentos, e extrai:
- Cabeçalho: vencimento, valor total, pagamento mínimo, período de referência, data de fechamento
- Cada compra: data, estabelecimento, cidade, valor, indicador de parcela (se houver)
- Tarifas: anuidade, proteção, outros encargos

O parser ignora completamente pagamentos recebidos e créditos (valores negativos), conforme especificado no PRD.

**Navegação para categorização:**
Se o parsing for bem-sucedido, o Fragment recebe um objeto `ExtractedInvoiceData` com todos os dados extraídos. O Fragment então navega para o `CategorizeExpensesFragment`, passando os dados via Bundle. O Bundle é necessário porque o Navigation Component não suporta SafeArgs neste projeto, então os dados são passados manualmente.

**Limpeza:**
Quando o Fragment é destruído, ele deleta o arquivo temporário do PDF para liberar espaço no cache.

**Por que copiar para cache?**
O PDFParserDataSource precisa de um objeto `File` para fazer parsing, mas o sistema retorna uma URI. Copiar para o cache permite ter um arquivo físico que pode ser processado. Além disso, o cache é limpo automaticamente pelo sistema quando necessário.

**Por que processar automaticamente?**
O processamento automático melhora a experiência do usuário, evitando um passo extra. O usuário só precisa selecionar o PDF e aguardar o processamento, que geralmente leva alguns segundos.

---

## CategorizeExpensesFragment - Categorização

### O que a tela mostra

A tela de categorização exibe um resumo da fatura (mês e valor total), uma barra de progresso mostrando quantas despesas foram categorizadas, uma lista de todas as despesas extraídas do PDF, e um botão para salvar a fatura.

### Como funciona internamente

**Quando a tela abre:**
O Fragment recebe os dados da fatura extraída via Bundle (passado pelo UploadInvoiceFragment). Ele configura o RecyclerView com um adapter especial (`ExpenseCategorizeAdapter`) que exibe cada despesa com um campo para selecionar a categoria.

**Carregamento de categorias:**
O Fragment carrega todas as categorias disponíveis (padrão e personalizadas) chamando `CategoryController.getDefaultCategories()` e `CategoryController.getUserCategories()`. As categorias são passadas para o adapter, que as usa para preencher um Spinner quando o usuário toca em uma despesa.

**Auto-categorização:**
Antes de exibir as despesas, o Fragment tenta auto-categorizar baseado em mapeamentos salvos anteriormente. Ele chama `CategoryController.autoCategorizeExpenses()`, que busca no Firebase os mapeamentos de estabelecimento → categoria que o usuário criou em faturas anteriores.

Para cada despesa, o sistema verifica se o estabelecimento já foi categorizado antes. Se sim, aplica automaticamente a mesma categoria. Por exemplo, se "CAFE DA ANA" foi categorizado como "Alimentação" na primeira fatura, nas próximas faturas ele será automaticamente categorizado como "Alimentação".

O Fragment exibe uma mensagem informando quantas despesas foram auto-categorizadas, para que o usuário saiba que o sistema está aprendendo.

**Categorização manual:**
Quando o usuário toca em uma despesa que não foi auto-categorizada (ou quer alterar a categoria), o adapter exibe um Spinner com todas as categorias disponíveis. Quando o usuário seleciona uma categoria, o adapter atualiza a UI e o Fragment salva o mapeamento no Firebase chamando `CategoryController.saveEstablishmentCategoryMapping()`. Esse mapeamento será usado para auto-categorizar futuras faturas.

**Atualização de progresso:**
A cada categoria selecionada, o Fragment atualiza a barra de progresso e o texto mostrando quantas despesas foram categorizadas. O botão de salvar permanece habilitado mesmo se nem todas as despesas foram categorizadas, permitindo salvar parcialmente.

**Salvamento da fatura:**
Quando o usuário toca em "Salvar fatura", o Fragment monta um mapa de chaves estáveis (baseadas em data, descrição, cidade, valor e parcela) para categoria. Isso garante que cada despesa seja identificada de forma única, mesmo se houver despesas similares.

O Fragment então chama `InvoiceController.saveInvoice()`, que:
1. Cria um objeto `Invoice` com todos os dados da fatura
2. Associa cada despesa à sua categoria usando o mapa
3. Salva tudo no Firebase Realtime Database na estrutura `users/{userId}/invoices/{referenceMonth}/`

Após salvar com sucesso, o Fragment navega de volta para o InvoicesFragment (histórico de faturas).

**Por que usar chaves estáveis?**
As chaves estáveis garantem que cada despesa seja identificada de forma única, mesmo se houver múltiplas despesas do mesmo estabelecimento na mesma data. A chave combina data, descrição, cidade, valor e parcela, criando um identificador único.

**Por que permitir salvar parcialmente?**
Permitir salvar parcialmente melhora a experiência do usuário. Se o usuário categorizou a maioria das despesas mas esqueceu algumas, não precisa recomeçar. As despesas não categorizadas podem ser categorizadas depois na tela de detalhes da fatura.

---

## InvoicesFragment - Histórico de Faturas

### O que a tela mostra

A tela de histórico exibe uma lista de todas as faturas processadas, ordenadas do mais recente para o mais antigo. Cada item da lista mostra o mês da fatura, o valor total, e botões para visualizar detalhes, editar ou excluir.

### Como funciona internamente

**Quando a tela abre:**
O Fragment configura o RecyclerView com um adapter (`InvoicesListAdapter`) e chama `loadInvoices()` para carregar as faturas do Firebase.

**Carregamento de faturas:**
O Fragment chama `InvoiceController.getInvoices()`, que delega para o `InvoiceService`. O Service busca no Firebase todas as faturas do usuário na estrutura `users/{userId}/invoices/`. As faturas são retornadas como uma lista de objetos `Invoice`.

O Service ordena as faturas por data de upload (mais recente primeiro) antes de retornar. Se não houver faturas, o Fragment exibe um estado vazio com uma mensagem e o botão de adicionar fatura.

**Exibição da lista:**
O adapter recebe a lista de faturas e exibe cada uma em um card. Cada card mostra:
- Mês de referência (ex: "JUN/2025")
- Valor total formatado em reais
- Status (paga/pendente) se disponível

**Ações na lista:**
Cada item da lista tem três ações:
1. **Visualizar detalhes:** Navega para o `InvoiceDetailFragment` passando o ID da fatura e o mês de referência via Bundle
2. **Editar:** Também navega para o `InvoiceDetailFragment` (que permite editar categorias)
3. **Excluir:** Exibe um dialog de confirmação e, se confirmado, chama `InvoiceController.deleteInvoice()` para remover a fatura do Firebase

**Pull to refresh:**
A tela tem um SwipeRefreshLayout que permite atualizar a lista puxando para baixo. Quando o usuário faz isso, o Fragment recarrega as faturas do Firebase.

**Botão de adicionar:**
A tela tem um FAB (Floating Action Button) que navega para o `UploadInvoiceFragment` quando tocado.

**Por que usar RecyclerView?**
RecyclerView é mais eficiente que ListView para listas grandes, pois reutiliza views. Isso melhora a performance, especialmente se o usuário tiver muitas faturas.

---

## InvoiceDetailFragment - Detalhes da Fatura

### O que a tela mostra

A tela de detalhes exibe informações completas da fatura: mês, valor total, data de vencimento, status de pagamento, número de despesas, e uma lista de todas as despesas com opções de filtro e busca.

### Como funciona internamente

**Quando a tela abre:**
O Fragment recebe o ID da fatura e o mês de referência via Bundle (passado pelo InvoicesFragment). Ele então chama `loadData()` para carregar os dados completos da fatura.

**Carregamento de dados:**
O Fragment chama `InvoiceController.getInvoiceByMonth()`, que busca a fatura específica no Firebase. Ele também carrega todas as categorias disponíveis, que são necessárias para exibir os nomes das categorias nas despesas (já que as despesas têm apenas o ID da categoria).

**Exibição da fatura:**
Após carregar os dados, o Fragment atualiza a UI com:
- Título da fatura (mês de referência)
- Valor total formatado
- Data de vencimento formatada
- Status de pagamento (calculado dinamicamente)
- Número de despesas

**Status de pagamento:**
O Fragment calcula o status de pagamento comparando a data de vencimento com a data atual:
- Se está paga: mostra quando foi paga e se foi no prazo, adiantado ou atrasado
- Se está pendente: mostra quantos dias restam até o vencimento, ou quantos dias de atraso se já venceu
- Cores diferentes: verde se paga, amarelo se vence em breve, vermelho se vencida

**Lista de despesas:**
O Fragment exibe todas as despesas da fatura em um RecyclerView com um adapter (`ExpenseDetailAdapter`). Cada item mostra:
- Data da compra
- Estabelecimento
- Cidade
- Valor
- Categoria (com opção de alterar)
- Indicador de parcela (se houver)

**Filtros e busca:**
A tela tem um TabLayout que permite filtrar despesas por categoria. O Fragment cria uma aba para cada categoria que tem despesas, mais uma aba "Todas" e uma "Não categorizadas". Quando o usuário seleciona uma aba, o Fragment filtra a lista de despesas mostrando apenas as da categoria selecionada.

A tela também tem um campo de busca que permite buscar despesas por estabelecimento, descrição ou valor. A busca funciona em tempo real enquanto o usuário digita, usando um `TextWatcher`.

**Edição de categoria:**
Quando o usuário toca em uma despesa para alterar a categoria, o adapter exibe um Spinner com todas as categorias disponíveis. Quando o usuário seleciona uma nova categoria, o Fragment chama `InvoiceController.updateExpenseCategory()` para atualizar no Firebase. Após atualizar, o Fragment também salva o mapeamento estabelecimento → categoria para futuras auto-categorizações.

**Marcação de pagamento:**
A tela tem um Switch que permite marcar a fatura como paga ou pendente. Quando o usuário ativa o switch, o Fragment exibe um seletor de data para escolher quando a fatura foi paga. Quando o usuário confirma a data, o Fragment chama `InvoiceController.updatePaymentStatus()` para atualizar no Firebase.

**Exclusão de fatura:**
A tela tem um botão para excluir a fatura. Quando tocado, exibe um dialog de confirmação. Se confirmado, chama `InvoiceController.deleteInvoice()` e navega de volta após excluir.

**Por que ter filtros e busca?**
Faturas podem ter muitas despesas (50+). Filtros e busca ajudam o usuário a encontrar despesas específicas rapidamente, melhorando a usabilidade.

---

## ReportsFragment - Relatórios

### O que a tela mostra

A tela de relatórios é um container que exibe dois tipos de relatórios em abas: Relatório Mensal e Relatório Anual.

### Como funciona internamente

**Quando a tela abre:**
O Fragment configura um ViewPager2 com um adapter customizado (`ReportsPagerAdapter`) que gerencia os dois fragments de relatório. Ele também configura um TabLayout que sincroniza com o ViewPager2, mostrando as abas "MENSAL" e "ANUAL".

**ViewPager2:**
O ViewPager2 permite deslizar horizontalmente entre os dois tipos de relatório. O adapter cria os fragments conforme necessário:
- Posição 0: `MonthlyReportFragment` (Relatório Mensal)
- Posição 1: `AnnualReportFragment` (Relatório Anual)

**Sincronização com TabLayout:**
O TabLayoutMediator sincroniza o TabLayout com o ViewPager2, garantindo que quando o usuário desliza entre relatórios, a aba correspondente é selecionada, e vice-versa.

**Por que usar ViewPager2?**
ViewPager2 permite uma navegação suave entre os dois tipos de relatório com gestos de deslize, melhorando a experiência do usuário. Além disso, ele gerencia automaticamente o ciclo de vida dos fragments, criando e destruindo conforme necessário.

---

## MonthlyReportFragment - Relatório Mensal

### O que a tela mostra

O relatório mensal exibe um resumo detalhado de uma fatura específica: total gasto, número de despesas, número de categorias, comparação com o mês anterior, top 5 categorias, e top 5 estabelecimentos.

### Como funciona internamente

**Quando a tela abre:**
O Fragment carrega todas as faturas do usuário chamando `InvoiceController.getInvoices()`. Ele também carrega todas as categorias para resolver nomes corretamente.

**Seletor de mês:**
O Fragment cria um Spinner com todos os meses que têm faturas disponíveis, ordenados do mais recente para o mais antigo. O Spinner usa o `MonthFilterManager` para salvar e restaurar a seleção do usuário, mantendo consistência com outras telas que usam o mesmo filtro.

Quando o usuário seleciona um mês, o Fragment busca a fatura correspondente e exibe o relatório.

**Resumo da fatura:**
O Fragment exibe:
- Total gasto no mês (formatado em reais)
- Número de despesas
- Número de categorias distintas usadas

**Comparação com mês anterior:**
O Fragment busca a fatura do mês anterior (se existir) e calcula a diferença percentual. Se os gastos aumentaram, exibe em vermelho com um ícone de aumento. Se diminuíram, exibe em verde com um ícone de redução.

**Top 5 Categorias:**
O Fragment agrupa todas as despesas por categoria, soma os valores, ordena do maior para o menor, e exibe as 5 primeiras. Para cada categoria, mostra:
- Nome da categoria (resolvido do ID)
- Valor total gasto
- Percentual em relação ao total

**Top 5 Estabelecimentos:**
Similar às categorias, o Fragment agrupa despesas por estabelecimento, soma os valores, ordena, e exibe os 5 estabelecimentos onde mais foi gasto.

**Por que mostrar top 5?**
Limitar a 5 itens mantém o relatório focado e fácil de ler, destacando os principais gastos sem sobrecarregar o usuário com informações.

---

## AnnualReportFragment - Relatório Anual

### O que a tela mostra

O relatório anual exibe uma visão consolidada de todas as faturas do ano: total anual, média mensal, número de meses, melhor e pior mês, evolução mensal, e top 5 categorias do ano.

### Como funciona internamente

**Quando a tela abre:**
O Fragment carrega todas as faturas do usuário e todas as categorias. Ele então consolida os dados de todas as faturas para gerar o relatório anual.

**Resumo anual:**
O Fragment calcula:
- Total gasto no ano (soma de todas as faturas)
- Média mensal (total dividido pelo número de meses)
- Número de meses com faturas

**Melhor e pior mês:**
O Fragment ordena as faturas por valor total e identifica:
- Melhor mês: fatura com menor valor (menos gastos)
- Pior mês: fatura com maior valor (mais gastos)

**Evolução mensal:**
O Fragment exibe a evolução dos gastos mês a mês. Para cada mês, mostra:
- Nome do mês
- Valor gasto
- Diferença percentual em relação à média anual (ex: "+15%" ou "-8%")
- Comparação com o mês anterior (se aumentou, diminuiu ou estável)

As cores variam conforme a situação:
- Vermelho: muito acima da média ou aumento significativo
- Laranja: acima da média ou aumento moderado
- Verde: próximo da média ou estável
- Azul: abaixo da média

Os primeiros 3 meses são exibidos em views estáticas no layout, e os demais em um RecyclerView.

**Top 5 Categorias do Ano:**
O Fragment consolida todas as despesas de todas as faturas, agrupa por categoria, soma os valores, ordena, e exibe as 5 categorias onde mais foi gasto no ano inteiro.

**Por que consolidar todas as faturas?**
O relatório anual precisa de uma visão ampla dos gastos ao longo do ano. Consolidar todas as faturas permite identificar padrões anuais, como "gastos mais altos em dezembro" ou "categoria que mais consome ao longo do ano".

---

## ProfileFragment - Perfil do Usuário

### O que a tela mostra

A tela de perfil exibe informações do usuário: nome, email, foto (ícone padrão), data de criação da conta, renda mensal (se cadastrada), telefone (se cadastrado), e botões para editar perfil, gerenciar categorias, gerenciar metas, e fazer logout.

### Como funciona internamente

**Quando a tela abre:**
O Fragment chama `loadUserData()` que busca os dados completos do usuário chamando `AuthController.getCompleteUserData()`. O Controller delega para o `AuthService`, que busca no Firebase Auth (nome, email, foto) e no Firebase Realtime Database (renda, telefone, data de criação).

**Exibição dos dados:**
O Fragment exibe:
- Nome do usuário (ou apelido se disponível)
- Email
- Ícone de perfil padrão (não há funcionalidade de foto no MVP)
- "Usuário desde [ano]" (extraído da data de criação)
- Renda mensal (se cadastrada, formatada em reais)
- Telefone (se cadastrado)

Se não houver renda nem telefone cadastrados, o Fragment exibe uma mensagem indicando que essas informações podem ser adicionadas.

**Edição de perfil:**
Quando o usuário toca em "Editar Perfil", o Fragment abre um dialog (`EditProfileDialog`) que permite editar nome, apelido, renda e telefone. O dialog salva as alterações no Firebase e, ao fechar, o Fragment recarrega os dados do usuário para exibir as atualizações.

**Gerenciamento de categorias:**
O botão "Gerenciar Categorias" navega para o `ManageCategoriesFragment`.

**Gerenciamento de metas:**
O botão "Gerenciar Metas" navega para o `ManageGoalsFragment`.

**Logout:**
Quando o usuário toca em "Sair", o Fragment exibe um dialog de confirmação. Se confirmado, chama `AuthController.logout()`, que faz logout no Firebase Auth. Após logout, o Fragment navega para o LoginFragment. O listener de autenticação na MainActivity detecta o logout automaticamente e atualiza a UI.

**Por que buscar dados de múltiplas fontes?**
O Firebase Auth armazena dados básicos (nome, email, foto), mas dados adicionais (renda, telefone) são armazenados no Realtime Database. O Service agrega dados de ambas as fontes para exibir o perfil completo.

---

## ManageGoalsFragment - Gerenciamento de Metas

### O que a tela mostra

A tela de gerenciamento de metas exibe uma lista de todas as metas cadastradas, cada uma mostrando categoria, valor limite, e opções para editar ou excluir. Há também um FAB para adicionar nova meta.

### Como funciona internamente

**Quando a tela abre:**
O Fragment configura o RecyclerView e chama `loadGoals()` para carregar as metas do Firebase.

**Carregamento de metas:**
O Fragment chama `GoalController.getGoals()`, que busca todas as metas do usuário no Firebase. As metas são exibidas em um RecyclerView com um adapter (`SimpleGoalsAdapter`).

**Adicionar meta:**
Quando o usuário toca no FAB, o Fragment exibe um dialog customizado. O dialog tem:
- Um AutoCompleteTextView (Spinner) para selecionar a categoria
- Um campo de texto para o valor limite (com máscara de moeda)
- Switches para ativar alertas aos 80% e 100%

O dialog carrega todas as categorias disponíveis (padrão e personalizadas) e preenche o Spinner. Quando o usuário preenche os campos e confirma, o Fragment valida:
- Categoria deve estar selecionada
- Valor deve ser maior que zero

Se válido, cria um objeto `Goal` e chama `GoalController.createGoal()` para salvar no Firebase.

**Regra de negócio: uma meta por categoria:**
Antes de salvar, o Fragment verifica se já existe uma meta para a categoria selecionada. Se existir, deleta a meta antiga antes de criar a nova. Isso garante que cada categoria tenha apenas uma meta ativa.

**Edição de meta:**
Quando o usuário toca em "Editar" em uma meta, o Fragment exibe o mesmo dialog, mas pré-preenchido com os dados da meta. O processo de salvamento é similar, mas chama `GoalController.updateGoal()` em vez de `createGoal()`.

**Exclusão de meta:**
Quando o usuário toca em "Excluir", o Fragment exibe um dialog de confirmação. Se confirmado, chama `GoalController.deleteGoal()` para remover do Firebase.

**Máscara de moeda:**
O campo de valor limite usa um `TextWatcher` que formata o texto automaticamente como moeda (R$ 0,00) enquanto o usuário digita. Isso melhora a experiência, mostrando o valor formatado em tempo real.

**Por que uma meta por categoria?**
Ter apenas uma meta por categoria simplifica o gerenciamento e evita confusão. O usuário não precisa decidir qual meta usar quando há múltiplas metas para a mesma categoria.

---

## ManageCategoriesFragment - Gerenciamento de Categorias

### O que a tela mostra

A tela de gerenciamento de categorias exibe duas listas: categorias padrão (que não podem ser excluídas) e categorias personalizadas (que podem ser editadas e excluídas). Há também um FAB para adicionar nova categoria personalizada.

### Como funciona internamente

**Quando a tela abre:**
O Fragment configura dois RecyclerViews (um para categorias padrão, outro para personalizadas) e chama `loadCategories()` para carregar as categorias do Firebase.

**Carregamento de categorias:**
O Fragment chama `CategoryController.getCategories()`, que busca todas as categorias do usuário (padrão e personalizadas). O Controller retorna uma lista única, e o Fragment separa em duas listas:
- Categorias padrão: `isDefault = true` (não podem ser excluídas)
- Categorias personalizadas: `isDefault = false` (podem ser editadas e excluídas)

**Categorias padrão:**
As categorias padrão são pré-cadastradas no sistema (Alimentação, Transporte, Saúde, etc.). Elas são exibidas em um RecyclerView separado e não têm botão de excluir. O botão de editar também está oculto, pois categorias padrão não podem ser editadas.

**Categorias personalizadas:**
As categorias personalizadas são criadas pelo usuário. Elas são exibidas em outro RecyclerView e têm botões para editar e excluir.

**Adicionar categoria:**
Quando o usuário toca no FAB, o Fragment exibe um dialog customizado. O dialog tem:
- Campo de texto para o nome da categoria
- Switch para marcar como recorrente (se compras nesta categoria são frequentes)

Quando o usuário preenche e confirma, o Fragment valida que o nome não está vazio e chama `CategoryController.createCategory()` para salvar no Firebase.

**Edição de categoria:**
Quando o usuário toca em "Editar" em uma categoria personalizada, o Fragment exibe o mesmo dialog, mas pré-preenchido. O processo de salvamento chama `CategoryController.createCategory()` novamente (que atualiza se o ID já existe).

**Exclusão de categoria:**
Quando o usuário toca em "Excluir", o Fragment exibe um dialog de confirmação. Se confirmado, chama `CategoryController.deleteCategory()` para remover do Firebase.

**Por que separar categorias padrão e personalizadas?**
Separar visualmente ajuda o usuário a entender quais categorias são do sistema (que sempre estarão disponíveis) e quais são suas próprias (que podem ser gerenciadas livremente).

**Por que categorias recorrentes?**
A flag `isRecurring` é usada para gerar insights. Por exemplo, se o usuário parcelar uma compra em uma categoria recorrente (como Mercado), o sistema gera um alerta informando que não é recomendado parcelar compras recorrentes.

---

## Conclusão

Este documento explica o funcionamento interno de todas as telas do aplicativo, mostrando como cada interação do usuário é processada internamente, desde a UI até o Firebase. Cada tela segue o padrão MVC, onde o Fragment (View) chama o Controller, que delega para os Services, que interagem com o Firebase através dos DataSources.

O sistema foi projetado para ser modular e fácil de manter, com separação clara de responsabilidades entre as camadas. Isso facilita testes, manutenção e futuras expansões do sistema.

