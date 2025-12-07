package br.edu.utfpr.gestaofaturasicoob

import android.os.Bundle
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.NavOptions
import br.edu.utfpr.gestaofaturasicoob.data.datasource.FirebaseManager
import br.edu.utfpr.gestaofaturasicoob.databinding.ActivityMainBinding
import br.edu.utfpr.gestaofaturasicoob.presentation.common.BaseActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Main Activity - Gerenciadora Principal da Navegação
 * 
 * RESPONSABILIDADES:
 * 1. Inicializar Firebase no início do app
 * 2. Configurar Navigation Component (navegação entre telas)
 * 3. Sincronizar BottomNavigationView com os fragments
 * 4. Monitorar estado de autenticação (login/logout)
 * 5. Controlar visibilidade da barra de navegação inferior
 * 
 * ARQUITETURA:
 * - Herda de BaseActivity para funcionalidades comuns
 * - Usa ViewBinding para acesso type-safe às views
 * - Navigation Component gerencia navegação entre fragments
 * 
 * CICLO DE VIDA:
 * - onCreate: Inicializa Firebase e navegação
 * - onDestroy: Remove listener de autenticação (evita memory leak)
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {
    
    // NavController: Controla navegação entre fragments
    // lateinit: Será inicializado no setupNavigation()
    private lateinit var navController: NavController
    
    // AuthStateListener: Escuta mudanças de autenticação em tempo real
    // Nullable: Pode ser removido no onDestroy
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    
    /**
     * Cria o binding da Activity usando ViewBinding
     * ViewBinding gera código type-safe automaticamente a partir do layout XML
     * 
     * @return ActivityMainBinding - Binding gerado do layout activity_main.xml
     */
    override fun createBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }
    
    /**
     * Configura a UI inicial da Activity
     * Chamado automaticamente pelo BaseActivity após onCreate
     * 
     * ORDEM DE EXECUÇÃO:
     * 1. Inicializa Firebase (conecta ao banco de dados)
     * 2. Configura navegação (NavController + BottomNavigationView)
     */
    override fun setupUI() {
        // Inicializar Firebase: Conecta ao Realtime Database
        initializeFirebase()
        
        // Setup navigation: Configura NavController e BottomNavigationView
        setupNavigation()
    }
    
    override fun observeData() {
        // Observe authentication state and navigate accordingly
        observeAuthState()
        
        // Listen for authentication state changes
        setupAuthStateListener()
    }
    
    /**
     * Configura a Navegação Principal do App
     * 
     * COMO FUNCIONA:
     * 1. Obtém NavHostFragment do layout (container dos fragments)
     * 2. Extrai NavController do NavHostFragment
     * 3. Sincroniza BottomNavigationView com NavController
     * 4. Configura comportamento customizado: limpa back stack ao navegar
     * 
     * NAVIGATION COMPONENT:
     * - NavHostFragment: Container que exibe os fragments
     * - NavController: Gerencia navegação e back stack
     * - nav_graph.xml: Define destinos e ações de navegação
     * 
     * BOTTOM NAVIGATION:
     * - setupWithNavController(): Sincroniza automaticamente item selecionado
     * - setOnItemSelectedListener(): Override para limpar back stack
     * - setPopUpTo(): Limpa fragments anteriores ao navegar
     * 
     * POR QUE LIMPAR BACK STACK?
     * Quando usuário toca em "Dashboard" no bottom nav, não quer voltar
     * para a tela anterior, mas sim ir direto para o Dashboard limpo.
     */
    private fun setupNavigation() {
        // Obtém o NavHostFragment do layout (definido em activity_main.xml)
        // NavHostFragment é um Fragment especial que gerencia outros Fragments
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        
        // Extrai o NavController do NavHostFragment
        // NavController é quem realmente controla a navegação
        navController = navHostFragment.navController
        
        // Sincroniza BottomNavigationView com NavController
        // Isso faz com que o item selecionado seja atualizado automaticamente
        // quando navegamos programaticamente (ex: após login)
        binding.bottomNavigation.setupWithNavController(navController)
        
        // Override do comportamento padrão: limpa back stack ao navegar
        // Isso evita acumulação de fragments e melhora performance
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destinationId = item.itemId // ID do fragment de destino
            
            // Cria NavOptions para configurar comportamento de navegação
            // setPopUpTo(destinationId, true):
            //   - Remove todos os fragments até chegar no destinationId
            //   - true = inclui o destinationId também (limpa tudo)
            val navOptions = NavOptions.Builder()
                .setPopUpTo(destinationId, true) // Limpa back stack
                .build()
            
            // Navega para o destino com as opções configuradas
            // null = sem argumentos extras
            navController.navigate(destinationId, null, navOptions)
            
            true // Retorna true para indicar que o evento foi tratado
        }
    }
    
    /**
     * Observa o Estado Inicial de Autenticação
     * 
     * CHAMADO QUANDO: Activity é criada (onCreate)
     * 
     * LÓGICA:
     * - Se usuário NÃO está logado → Vai para LoginFragment (oculta bottom nav)
     * - Se usuário ESTÁ logado → Vai para DashboardFragment (mostra bottom nav)
     * 
     * IMPORTANTE:
     * Esta função verifica apenas o estado INICIAL. Para mudanças em tempo real
     * (logout durante uso), usa-se setupAuthStateListener().
     * 
     * TRATAMENTO DE ERRO:
     * Em caso de erro, assume que usuário não está autenticado (fail-safe)
     */
    private fun observeAuthState() {
        try {
            // Busca usuário atual do Firebase Auth
            // Retorna null se não há usuário logado
            val currentUser = FirebaseManager.getCurrentUser()
            
            if (currentUser == null) {
                // CASO 1: Usuário NÃO autenticado
                // Oculta barra de navegação inferior (não faz sentido sem login)
                binding.bottomNavigation.visibility = View.GONE
                // Navega para tela de login
                navController.navigate(R.id.loginFragment)
            } else {
                // CASO 2: Usuário autenticado
                // Mostra barra de navegação inferior (usuário pode navegar)
                binding.bottomNavigation.visibility = View.VISIBLE
                // Navega para Dashboard (tela principal)
                navController.navigate(R.id.dashboardFragment)
            }
        } catch (e: Exception) {
            // TRATAMENTO DE ERRO: Assume que usuário não está autenticado
            // Isso garante que o app sempre tenha uma tela válida
            println("❌ Erro ao verificar autenticação: ${e.message}")
            
            // Em caso de erro, vai para login e oculta navegação
            binding.bottomNavigation.visibility = View.GONE
            navController.navigate(R.id.loginFragment)
        }
    }
    
    /**
     * Configura Listener para Mudanças de Autenticação em Tempo Real
     * 
     * DIFERENÇA DE observeAuthState():
     * - observeAuthState(): Verifica estado APENAS uma vez (onCreate)
     * - setupAuthStateListener(): Escuta mudanças CONTÍNUAS (login/logout durante uso)
     * 
     * QUANDO É CHAMADO:
     * - Usuário faz logout (em qualquer lugar do app)
     * - Usuário faz login (em qualquer lugar do app)
     * - Sessão expira automaticamente
     * 
     * PROTEÇÕES CONTRA MEMORY LEAK:
     * 1. Verifica se Activity foi destruída antes de executar
     * 2. Verifica se binding está disponível (pode estar null após onDestroyView)
     * 3. Remove listener no onDestroy()
     * 
     * LÓGICA:
     * - Usuário deslogou → Oculta bottom nav + vai para LoginFragment
     * - Usuário logou → Mostra bottom nav + vai para DashboardFragment (se estiver em login)
     */
    private fun setupAuthStateListener() {
        // Cria o listener que será chamado quando autenticação mudar
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            // PROTEÇÃO 1: Verifica se Activity ainda está ativa
            // Se Activity foi destruída, não faz nada (evita crash)
            if (isDestroyed || isFinishing) {
                return@AuthStateListener
            }
            
            try {
                // Obtém usuário atual do Firebase Auth
                val currentUser = firebaseAuth.currentUser
                
                // PROTEÇÃO 2: Verifica se binding está disponível
                // Após onDestroyView, binding pode estar null
                val bottomNav = try {
                    binding.bottomNavigation
                } catch (e: IllegalStateException) {
                    // Binding não disponível, ignora evento
                    return@AuthStateListener
                }
                
                if (currentUser == null) {
                    // CASO 1: Usuário DESLOGOU
                    // Oculta barra de navegação (sem login, não pode navegar)
                    bottomNav.visibility = View.GONE
                    
                    // Navega para login apenas se não já estiver lá
                    // Evita navegação desnecessária e loops
                    if (::navController.isInitialized && 
                        navController.currentDestination?.id != R.id.loginFragment) {
                        navController.navigate(R.id.loginFragment)
                    }
                } else {
                    // CASO 2: Usuário LOGOU
                    // Mostra barra de navegação (pode navegar agora)
                    bottomNav.visibility = View.VISIBLE
                    
                    // Se estava na tela de login, vai para Dashboard
                    // Se já estava em outra tela, mantém onde está
                    if (::navController.isInitialized && 
                        navController.currentDestination?.id == R.id.loginFragment) {
                        navController.navigate(R.id.dashboardFragment)
                    }
                }
            } catch (e: Exception) {
                // TRATAMENTO DE ERRO: Ignora se Activity foi destruída
                // Se Activity ainda está ativa, loga o erro
                if (!isDestroyed && !isFinishing) {
                    println("❌ Erro no auth state listener: ${e.message}")
                }
            }
        }
        
        // Registra o listener no Firebase Auth
        // Firebase chamará este listener sempre que autenticação mudar
        authStateListener?.let {
            FirebaseManager.auth.addAuthStateListener(it)
        }
    }
    
    /**
     * Inicializa o Firebase no Início do App
     * 
     * O QUE FAZ:
     * - Conecta ao Firebase Realtime Database usando URL configurada
     * - Configura referências para acesso aos dados
     * - Deve ser chamado ANTES de qualquer operação Firebase
     * 
     * POR QUE NO MainActivity?
     * - MainActivity é a primeira Activity a ser criada
     * - Garante que Firebase está pronto antes de usar
     * - Singleton FirebaseManager mantém conexão durante toda execução do app
     */
    private fun initializeFirebase() {
        try {
            // Inicializa FirebaseManager (singleton)
            // Conecta ao Realtime Database e configura referências
            FirebaseManager.initialize(this)
            println("✅ Firebase inicializado com sucesso!")
        } catch (e: Exception) {
            // Log de erro para debug
            // App continua funcionando, mas operações Firebase podem falhar
            println("❌ Erro ao inicializar Firebase: ${e.message}")
        }
    }
    
    /**
     * Limpeza ao Destruir Activity
     * 
     * CRÍTICO: Remove listener de autenticação
     * 
     * POR QUE É IMPORTANTE?
     * - Listener mantém referência à Activity
     * - Se não remover, Activity nunca é coletada pelo Garbage Collector
     * - Resultado: MEMORY LEAK (app consome cada vez mais memória)
     * 
     * BOA PRÁTICA:
     * Sempre remover listeners registrados quando componente é destruído
     */
    override fun onDestroy() {
        super.onDestroy()
        
        // Remove listener de autenticação do Firebase
        // Isso evita memory leak
        authStateListener?.let {
            FirebaseManager.auth.removeAuthStateListener(it)
        }
        
        // Limpa referência (não é estritamente necessário, mas é uma boa prática)
        authStateListener = null
    }
}
