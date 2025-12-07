package br.edu.utfpr.gestaofaturasicoob.data.datasource

import android.content.Context

/**
 * Configuração do Firebase
 * Gerencia URLs e configurações do Firebase
 */
object FirebaseConfig {
    
    // URL padrão do Firebase Realtime Database
    private const val DEFAULT_DATABASE_URL = "https://gestao-fatura-sicoob-default-rtdb.firebaseio.com/"
    
    /**
     * Obtém a URL do Firebase Realtime Database
     * Usa a URL padrão configurada
     * @param context Contexto da aplicação
     * @return URL do Firebase Database
     */
    fun getDatabaseUrl(context: Context): String {
        return DEFAULT_DATABASE_URL
    }
}
