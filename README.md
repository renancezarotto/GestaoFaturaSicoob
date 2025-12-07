# 💰 Gestão de Fatura Sicoob

**Sistema Mobile para Gestão da Fatura de Crédito do Sicoob**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)]()
[![Android](https://img.shields.io/badge/Android-API%2026%2B-green)]()
[![Architecture](https://img.shields.io/badge/Architecture-MVC-orange)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

---

## 📋 Sobre o Projeto

Aplicativo Android nativo desenvolvido em Kotlin que automatiza o controle financeiro pessoal através da leitura e interpretação inteligente de faturas de cartão de crédito em formato PDF do Sicoob.

### ✨ Funcionalidades Principais

- 📱 **Upload de Fatura PDF**: Processa automaticamente faturas do Sicoob
- 🏷️ **Categorização Inteligente**: Memoriza estabelecimentos e auto-categoriza despesas
- 📊 **Dashboard Interativo**: Visualização clara dos gastos mensais
- 🎯 **Metas de Gastos**: Defina limites e receba alertas
- 📈 **Relatórios**: Análises mensal e anual com exportação
- 🔐 **Firebase Auth**: Login com Google ou Email/Senha
- ☁️ **Sincronização**: Dados seguros no Firebase Realtime Database

---

## 🏗️ Arquitetura

O projeto utiliza **arquitetura MVC (Model-View-Controller)** para garantir código limpo, organizado e de fácil manutenção.

```
app/
├── models/          # Data classes (User, Invoice, Expense, Category, Goal)
├── services/        # Lógica de negócio (AuthService, InvoiceService, etc)
├── controllers/     # Controllers MVC (AuthController, InvoiceController, etc)
├── presentation/    # Views (Fragments e Adapters)
│   ├── auth/       # Login, Registro
│   ├── dashboard/  # Tela principal
│   ├── invoice/    # Upload e Categorização
│   ├── goals/      # Gestão de Metas
│   ├── reports/    # Relatórios
│   └── profile/    # Perfil do Usuário
└── data/           # Datasources (Firebase, PDF Parser)
```

---

## 🚀 Tecnologias Utilizadas

### Core
- **Kotlin** 1.9+
- **Android SDK** API 26+ (Android 8.0+)
- **Material Design 3**

### Firebase
- Firebase Authentication (Google Sign-In + Email/Password)
- Firebase Realtime Database
- Firebase Cloud Messaging (Notificações)

### Bibliotecas
- **PDF Processing**: iTextPDF para extração de dados
- **UI**: Material Components, ConstraintLayout
- **Navigation**: Navigation Component
- **Coroutines**: Kotlin Coroutines para operações assíncronas
- **Gráficos**: MPAndroidChart (futuramente)

---

## 📦 Instalação

### Pré-requisitos
- Android Studio Arctic Fox ou superior
- JDK 11 ou superior
- Conta Firebase configurada

### Passos

1. **Clone o repositório**
```bash
git clone https://github.com/seu-usuario/gestao-fatura-sicoob.git
cd gestao-fatura-sicoob
```

2. **Configure o Firebase**
   - Crie um projeto no [Firebase Console](https://console.firebase.google.com/)
   - Adicione um app Android com o package name: `br.edu.utfpr.gestaofaturasicoob`
   - Baixe o `google-services.json` e coloque em `app/`
   - Ative Authentication (Google e Email/Password)
   - Ative Realtime Database

3. **Configure as Regras do Firebase**
```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    }
  }
}
```

4. **Build o Projeto**
```bash
./gradlew assembleDebug
```

5. **Execute no Emulador ou Dispositivo**
```bash
./gradlew installDebug
```

---

## 💡 Como Usar

### 1️⃣ **Primeiro Acesso**
- Faça login com Google ou crie uma conta
- Acesse o Dashboard

### 2️⃣ **Processar Fatura**
- Toque em "Nova Fatura"
- Selecione o PDF da fatura do Sicoob
- O sistema extrai automaticamente todas as despesas
- Categorize manualmente na primeira vez
- Salve a fatura

### 3️⃣ **Faturas Futuras**
- Repita o processo de upload
- O sistema auto-categoriza baseado no histórico
- Revise e ajuste se necessário

### 4️⃣ **Criar Metas**
- Acesse "Metas"
- Defina limites mensais por categoria
- Receba alertas aos 80% e 100%

### 5️⃣ **Ver Relatórios**
- Acesse "Relatórios"
- Visualize análises mensais e anuais
- Exporte em PDF (em desenvolvimento)

---

## 📊 Estrutura do Banco de Dados (Firebase)

```
users/
  {userId}/
    ├── name, email, photoUrl, createdAt
    │
    ├── invoices/
    │   └── {month}/
    │       ├── dueDate, totalValue, referenceMonth
    │       └── expenses/
    │           └── {expenseId}/
    │               ├── date, description, value
    │               ├── category, establishment
    │               └── installment, autoCategorized
    │
    ├── savedCategories/
    │   └── "ESTABELECIMENTO": "Categoria"
    │
    ├── customCategories/
    │   └── {categoryId}/
    │       └── name, color, isRecurring
    │
    └── goals/
        └── {goalId}/
            └── category, limitValue, alerts
```

---

## 🧪 Testes

```bash
# Testes unitários
./gradlew test

# Testes de instrumentação
./gradlew connectedAndroidTest
```

---

## 📝 TODOs Futuros

- [ ] Modo escuro
- [ ] Biometria para login
- [ ] Widget para home screen
- [ ] Compartilhamento de relatórios
- [ ] Suporte a múltiplos cartões
- [ ] Exportação PDF de relatórios
- [ ] Notificações push inteligentes
- [ ] Gráficos interativos avançados
- [ ] Backup/Restore local
- [ ] Suporte a outros bancos

---

## 🤝 Contribuindo

Contribuições são bem-vindas! Por favor, siga os passos:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

---

## 📄 Licença

Este projeto é licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.

---

## 👨‍💻 Autor

**Renan G C Matos**  
📧 Email: renan@email.com  
🎓 Trabalho de Conclusão de Curso - UTFPR  

---

## 🙏 Agradecimentos

- Firebase pela infraestrutura backend
- Material Design pela UI/UX
- iTextPDF pela extração de dados PDF
- Comunidade Android/Kotlin

---

## 📞 Suporte

Para questões e suporte:
- 📧 Email: renan@email.com
- 💬 Issues: [GitHub Issues](https://github.com/seu-usuario/gestao-fatura-sicoob/issues)

---

## 🎯 Status do Projeto

**✅ Build Status:** PASSING  
**📱 Versão:** 1.0.0  
**🏗️ Arquitetura:** MVC Completa  
**🧪 Testes:** Em desenvolvimento  
**📊 Cobertura:** TBD  

---

**Desenvolvido com ❤️ usando Kotlin e Android**
