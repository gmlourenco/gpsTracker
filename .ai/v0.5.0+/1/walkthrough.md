# Walkthrough — Implementações de Melhoria e Segurança (v0.5.0+)

Este documento resume as alterações efetuadas na codebase do projeto **Segurança Rural** no âmbito da reestruturação e correção de problemas críticos (blockers) para a versão **v0.5.0+**.

---

## 🛠️ Alterações Efetuadas

### 1. Ponto 1 — Correções Rápidas (Quick Fixes)
* **DAO de Telemetria (`TelemetryDao.kt`):**
  * Criada a consulta focada `resetSyncingToPendingByIds(ids: List<Long>)` para reverter o estado de sincronização exclusivamente das linhas que falharam, em vez de resetar a tabela toda globalmente.
* **Mecanismo de Sincronização (`SyncEngine.kt`):**
  * Atualizado o fluxo de tratamento de erros para chamar a nova query direcionada.
* **Geração de Código de Convite (`route.ts`):**
  * Removido o gerador inseguro `generateCode()` do endpoint `/api/auth/create-farm` e importada a função `generateInviteCode(8)` centralizada e segura (utilizando `crypto.randomInt()`).

### 2. Ponto 2 — Injeção de Dependências com Koin (Mobile)
* **Gestão de Dependências (`libs.versions.toml`, `shared/build.gradle.kts`, `app/build.gradle.kts`):**
  * Adicionado o Koin Core, Koin Android e Koin Compose.
* **Definição de Módulos (`AppModule.kt`):**
  * Criado o ficheiro de DI centralizando as fábricas e singletons para `NetworkMonitor`, `FarmRepository`, `TelemetryRepository`, `SubmitLocationUseCase`, `FamilyGroupsViewModel`, `AppDatabase`, `TelemetryDao` e `SyncEngine`.
* **Inicialização da Aplicação (`GpsTrackerApplication.kt`):**
  * Inicializado o Koin no ciclo de arranque do Android via `startKoin`.
* **Mitigação do Object Churn (`LocationForegroundService.kt`):**
  * Injetada a instância global de `SubmitLocationUseCase` via Koin (`inject()`), eliminando a alocação redundante de múltiplos repositórios/casos de uso a cada ping de GPS.

### 3. Ponto 3 — Database & Sistema de Roles
* **Migração SQL (`013_role_system_upgrade.sql`):**
  * Adicionado o helper seguro `get_users_metadata(p_user_ids UUID[])` com privilégios `SECURITY DEFINER` para permitir a leitura exclusiva dos metadados dos membros de cada família (Google Name/Email) sem necessidade de consultar globalmente a tabela de autenticação (`auth.users`).

---

## 🚫 Etapas Saltadas / Pendentes

### 1. Ponto 4 — Otimização do Endpoint `GET /api/farms/details`
* **Status:** Ignorado a pedido do utilizador. O endpoint continua a utilizar o método genérico `adminSupabase.auth.admin.listUsers()`.

### 2. Ponto 5 — Implementação de UI Mobile
* **Status:** Verificado. As implementações de UI como `FamilyGroupsScreen.kt`, `FamilyMemberListCard.kt` com suporte a swipe (Gmail-style), `MemberRoleChips.kt` e `FamilyGroupsViewModel.kt` já se encontravam totalmente estruturadas no repositório.

### 3. Testes Funcionais & Execução de Migrações
* **Status:** Saltados a pedido do utilizador. As migrações da base de dados e compilação do APK deverão ser executadas manualmente numa fase posterior.

---

## 🔍 Ficheiros Modificados

1. [TelemetryDao.kt](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/mobile/shared/src/commonMain/kotlin/com/segurancarural/gpstracker/data/db/TelemetryDao.kt) — Nova query direcionada para redefinir estados de sincronização.
2. [SyncEngine.kt](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/mobile/shared/src/commonMain/kotlin/com/segurancarural/gpstracker/sync/SyncEngine.kt) — Uso do reset direcionado de IDs falhados.
3. [route.ts](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/backend/app/api/auth/create-farm/route.ts) — Geração segura de código de convite.
4. [AppModule.kt](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/mobile/app/src/main/java/com/segurancarural/gpstracker/di/AppModule.kt) — Ficheiro de injeção de dependências Koin.
5. [LocationForegroundService.kt](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/mobile/app/src/main/java/com/segurancarural/gpstracker/service/LocationForegroundService.kt) — Injeção de dependências Koin para mitigar object churn.
6. [GpsTrackerApplication.kt](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/mobile/app/src/main/java/com/segurancarural/gpstracker/GpsTrackerApplication.kt) — Inicialização do Koin Core.
7. [013_role_system_upgrade.sql](file:///Users/goncalolourenco/Documents/Github/nextGPStracking/backend/supabase/migrations/013_role_system_upgrade.sql) — Migração da BD contendo a RPC segura de metadados.
