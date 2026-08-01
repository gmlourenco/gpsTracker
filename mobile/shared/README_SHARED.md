# 🧠 Mobile Shared Core (KMP) — Mapa de Arquitetura

> Este documento explica exaustivamente a camada de domínio partilhado (Kotlin Multiplatform) entre Android e iOS. É aqui que reside 90% da lógica de negócio offline-first, resiliência de GPS, e o motor de sincronização assíncrono.

---

## 1. Fluxo de Ingestão de Telemetria (End-to-End)

Quando o serviço de GPS nativo de uma plataforma (e.g., *Android FusedLocationProvider*) obtém uma coordenada, o processo de "digerir" e enviar/salvar essa coordenada segue um funil estrito através do módulo `shared`.

**Caminho Crítico (Arquivos Afetados):**
1. `SubmitLocationUseCase.kt`: Ponto único de entrada.
2. `GpsLocationFilter.kt`: Filtra lixo de hardware e saltos bizarros.
3. `TelemetryRepository.kt`: Tenta fazer o *push* de rede (via `ApiService`).
4. Se o envio de rede falhar (ou não houver net), cai para `TelemetryDao` (Room DB/SQLDelight local) com `syncState = 0`.

### Tratamento Offline e Condições de Rede
O `TelemetryRepository` acede a `Platform.dependencies.shouldUploadOverCurrentNetwork()` para decidir se pode usar a rede atual. Caso as políticas do trator mandem poupar dados, ou caso não exista cobertura celular, ele desvia o envio imediato e insere o registo localmente na BD (`syncState = 0`). Se tentar o envio web e a resposta falhar (ex: Captive Portal devolvendo 200 OK mas formato HTML, ou erro HTTP), faz fallback para gravação local, não perdendo um único ponto.

---

## 2. GpsLocationFilter (`util/GpsLocationFilter.kt`)

Este motor, invocado a cada ping, lida com hardware ruidoso e flutuações agressivas do sensor. Opera exclusivamente em memória (estado `kalmanLat`, `kalmanLng`, etc).

- **O que faz:**
  - **Stationary Anchor & Wi-Fi Bounce Protection:** Quando o trator está parado (distância < 10m e speed < 2km/h ao longo de 60s), o filtro "ancora" o veículo. Isto impede que anomalias de sensores (como um ping a saltar 200m para uma antena Wi-Fi na rua e a voltar ao mesmo sítio) manchem o percurso, classificando-os como `SuspiciousJumpRecheck`.
  - **Rolling Average Centroid:** Mantém as últimas 5 localizações num histórico circular (janela). Pontos demasiado próximos (< 5m) num trator parado são rejeitados via estado `DiscardRedundant`. Isto ajuda brutalmente a poupar BD local e bateria de transmissões desnecessárias.
  - **Filtro 2D Kalman:** Aplica uma suavização matemática a todos os pontos com base na margem de precisão (`accuracy`). O output final é devolvido via `FilterResult.Accept(smoothedRecord)`.

*(Nota: Posições SOS - `emergencyState = true` - ignoram todas as regras acima e fazem sempre passthrough `Accept`).*

---

## 3. O Motor de Sincronização (`sync/SyncEngine.kt`)

O coração do sistema Offline-First. Invocado pela plataforma nativa (ex: `SyncWorker` no Android) apenas quando o sistema operativo anuncia que a internet voltou ou estabilizou. O motor é protegido por um `Mutex`, o que impede duas execuções em simultâneo de causarem conflitos ou picos de CPU.

Corre sobre um `IoDispatcher` e divide a tarefa numa heurística assimétrica de 3 fases estritas:

- **Fase 1 (Emergência / LIFO):** Procura na BD todos os registos SOS pendentes. Tenta enviar para a rota separada de alta prioridade (`/api/emergency`) de forma individual (um a um), enviando primeiro os mais recentes (LIFO). Se houver uma falha a meio, interrompe o ponto mas tenta os seguintes.
- **Fase 2 (Latest Position / "O Ponto Desbloqueador"):** Pega unicamente no *registo normal não-SOS* mais recente da BD e dispara num array unitário para a `/api/v2/location`. O intuito desta fase é que o mapa Web do familiar do trator seja atualizado **quase imediatamente**, mesmo que o trator tenha estado 8 horas offline e tenha uma fila gigantesca de dados históricos que demorariam minutos a descarregar.
- **Fase 3 (History / FIFO):** Reconstrói o rasto histórico. Começa pelos mais antigos primeiro, agrupa lotes/batches estritos de **25 registos** e lança POSTs consecutivos `/api/v2/location`. Ao encontrar a primeira falha de rede suspende de imediato toda a *Fase 3* (para não abrir buracos temporais).
- **Cleanup:** `cleanupSynced()` remove fisicamente todos os blocos entretanto marcados com o estado `syncState=2` (Enviados com Sucesso).

---

## 4. Clientes de Rede Ktor (`network/ApiClient.kt`)

A arquitetura de segurança exigiu uma separação drástica nos clientes de rede instanciados no Singleton `ApiClient`:

- **`telemetryClient` (M2M Exclusivo):** Usa obrigatoriamente um Interceptor de autenticação que espeta um `BearerTokens(SharedConfig.DEVICE_API_SECRET, "")`. Não depende do estado da conta, e não tem *Refresh Tokens*. **Garante que o SOS ou a ingestão de coordenadas nunca falha porque a sessão do utilizador caducou no background.**
- **`httpClient` (Humano/Dashboard):** É o cliente Ktor "Padrão". Lê o token JWT associado à sessão atual do Supabase. Tem interceptores completos configurados para, caso obtenha `HTTP 401`, invocar a SDK do Supabase de forma invisível, fazer _Refresh_ à sessão e tentar repetir o pedido nativo. Utilizado por repositórios não-vitais (`FarmRepository`, `DeviceConfigRepository`).

---

## 5. Overview de Outros Repositories e Use Cases

- **`FarmRepository` / `FamilyPositionsRepository`:** Responsáveis por consumir APIs standard JSON do Next.js via `httpClient`. Não correm num loop furioso, respondem apenas quando o utilizador abre os painéis de visualização.
- **`GetDeviceSerialNumberUseCase`:** Encapsula de forma limpa o acesso ao UUID persistente ou `ANDROID_ID`, lendo da plataforma via interface injectada (`Platform.dependencies`), usado em todos os payloads emitidos por este módulo.

O módulo todo gira em torno de funções puras ou de Kotlin Coroutines assíncronas com tratamento limpo de exceções, blindado para lidar tanto com o telemóvel perder o sinal na curva da montanha (via Room/SQLDelight) como com um firewall mal configurada do lado do ISP rural (via capturas extensivas das falhas lógicas nos HTTP 200 das APIs Next.js).
