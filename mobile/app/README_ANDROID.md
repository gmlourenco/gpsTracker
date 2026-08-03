# 🤖 Aplicação Android (App Module) — Mapa Arquitetural

> Este documento disseca exclusivamente a camada nativa Android do sistema **Segurança Rural**. O seu objetivo é expor o ciclo de vida da UI, os serviços persistentes em background, a gestão de permissões e as notificações *Push* via Firebase.

---

## 📱 1. Ecrãs e Árvore de Navegação

A aplicação segue uma arquitetura *Single-Activity* onde a `MainActivity` alberga o ciclo de vida inicial (pedidos de permissão, alertas de atualização e _deep-linking_ de Notificações) e providencia o `Scaffold` com uma *Bottom Navigation Bar*. A UI é 100% desenhada em **Jetpack Compose**.

**Árvore de Navegação (`AppScreen`):**
- **`HOME` (`HomeScreen.kt`)**: O ecrã principal para o tratorista. Contém o botão gigante de SOS (que exige pressão longa de 2 segundos com feedback háptico - `VIBRATE`) e o _toggle_ de rastreio contínuo. É daqui que partem as *Intents* para gerir o `LocationForegroundService`.
- **`MAP` (`MapScreen.kt`)**: Motor de mapas *offline-ready* usando *MapLibre GL*. Subscreve aos repositórios partilhados para ler e desenhar o histórico e os familiares. 
- **`FAMILY` (`FamilyGroupsScreen.kt`)**: Painel de gestão da quinta. Permite aceitar códigos de convite, ver membros (via Supabase) e as suas roles.
- **`CONFIG` (`ConfigScreen.kt`)**: Menu para configurar políticas locais (intervalo de ping, distância, sensibilidade do detetor de quedas) e verificação manual de atualizações OTA (Over-The-Air).

**Fluxo UI ➔ Shared Module:**
*Activity/Composable* ➔ *ViewModel* ➔ *Shared Repositories/UseCases*.
(e.g., O clique no Mapa chama funções no `MapViewModel`, que por sua vez subscreve ao `FamilyPositionsRepository` instanciado no módulo KMP).

---

## ⚙️ 2. Serviços em Background e Ciclo de Vida

A app nativa é responsável por manter o sistema vivo mesmo com o ecrã bloqueado, interagindo estritamente com os componentes Android antes de entregar os dados ao módulo KMP.

### `LocationForegroundService` (GPS Contínuo)
- **Ciclo de vida:** Iniciado pelo clique no botão da `HomeScreen` ou pelo `BootReceiver` (se o telemóvel reiniciar com o rastreio ativo). 
- **Como Funciona:** É um `Service` de Android persistente ("Foreground Service") que mostra uma notificação in-removível, impedindo o Android de matar o processo por falta de memória. Usa o `FusedLocationProviderClient` da Google.
- **Lógica de Polling:** Totalmente adaptativa. Se o trator estiver parado (< 1km/h), pede pings a cada 2 minutos. Se for a direito na estrada (> 20km/h), acelera para 15 segundos. Se o botão SOS for premido, força imediatamente os pings para precisão máxima de 15s contínuos. Possui um "Heartbeat" via `AlarmManager` para garantir um ping a cada 30 minutos em caso de *Doze Mode* profundo.
- **Ligação ao Shared:** Para cada coordenada obtida da antena, constrói um `TelemetryRecord` e injeta-o diretamente no `SubmitLocationUseCase` (que vive no `mobile/shared/`).

### `AccidentDetector` & `AccidentReceiver` (Deteção de Capotamento)
- **Como Funciona:** Executado juntamente com o serviço de GPS, lê constantemente o acelerómetro (`HIGH_SAMPLING_RATE_SENSORS`).
- **Comunicação:** Ao detetar um pico G-Force anómalo, alerta o `TrackingStateRepository.setPreSosActive(true)`. Isto muda a notificação de sistema para um ecrã vermelho gigante ("Possível Acidente") e dá 15 segundos (`AccidentReceiver`) ao utilizador para cancelar. Se não cancelar, lança a intenção `ACTION_SOS_TRIGGER`.

### `SyncWorker` (Sincronização Offline ➔ Online)
- **Como Funciona:** Tarefa agendada via `WorkManager`. Configurada para tentar correr a cada 15 minutos, mas restrita estritamente a alturas em que o OS declare `NetworkType.CONNECTED`.
- **Ligação ao Shared:** Assim que acorda e há rede, inicializa o `SyncEngine(dao, httpClient)` do módulo partilhado e chama o `flush()`, delegando o envio massivo para a rede.

---

## 🔄 3. Mecanismo de Atualização OTA (Over-The-Air)

Como o sistema pode ser distribuído fora da Play Store (sideloading em tablets ou telemóveis de trabalho), a app possui um motor próprio de autoupdate.

- **Check no Arranque:** Logo na `MainActivity`, um `LaunchedEffect` invoca o `AppUpdateChecker.checkForUpdate()`.
- **Check Manual:** No ecrã `ConfigScreen`, o utilizador pode forçar a procura pela versão mais recente.
- **Fluxo com o Backend:** A app faz um `GET /api/app/version?current=<versão-atual>`. O backend lê as variáveis de ambiente (ex: `APP_LATEST_VERSION` e `APP_DOWNLOAD_URL`) e responde com um `AppUpdateOffer`.
- **Instalação:** Se houver update, o utilizador vê a `AppUpdateDialog`. Ao aceitar, o `ApkUpdateInstaller` faz download transparente do `.apk` novo usando o `DownloadManager` nativo do Android e invoca o _Package Installer_ (daí necessitar da permissão `REQUEST_INSTALL_PACKAGES`).

---

## 🔔 4. Notificações Push (Firebase Cloud Messaging)

Para além do envio contínuo, a app é capaz de ser acordada passivamente pelo servidor (por exemplo, quando a filha clica no SOS, o trator do pai tem de apitar).

- **`FcmService`**: Herda de `FirebaseMessagingService`.
  - **`onNewToken`**: Quando a Google gera uma chave push para o aparelho, esta função agarra-a e faz _upload_ para o backend (`PATCH /api/devices/fcm-token`) usando o `DEVICE_API_SECRET`.
  - **`onMessageReceived`**: Trata dos pushes quando a app está aberta. Aciona um canal de alta prioridade (fazendo o ecrã acender e o telefone vibrar agressivamente) e expõe um `PendingIntent` que, ao ser clicado, abre a `MainActivity` forçando o ecrã a mudar para o `AppScreen.MAP` e focando a câmara no acidentado.

---

## 🛡️ 5. Permissões Requeridas (`AndroidManifest.xml`)

| Permissão | Justificação / Ecrã |
| :--- | :--- |
| `ACCESS_FINE/COARSE_LOCATION` | **Obrigatório.** Pedido no arranque (`MainActivity`). Para o FusedLocationClient ler o GPS. |
| `ACCESS_BACKGROUND_LOCATION` | Pedido no arranque (Android 10+). Sem isto, quando o condutor desliga o ecrã e mete o telemóvel no bolso, o rastreio cessa ao fim de minutos. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_LOCATION` | Exigência da Play Store/Android 14 para executar o serviço com notificação fixa. |
| `POST_NOTIFICATIONS` | Para poder exibir os alertas SOS Firebase e o ícone residente do rastreio. |
| `RECEIVE_BOOT_COMPLETED` | Acorda o `BootReceiver` para ligar o GPS sozinho se o telemóvel for abaixo por falta de bateria e depois carregado e ligado. |
| `SCHEDULE_EXACT_ALARM` | Exigido para que o "Heartbeat" de 30minutos consiga furar a poupança de bateria nativa (*Doze mode*). |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Apresenta um ecrã nativo (se necessário) a pedir ao condutor para não otimizar a App, de modo a evitar pausas indesejadas na telemetria. |
| `REQUEST_INSTALL_PACKAGES` | Usado pelo `ApkUpdateInstaller` para conseguir aplicar a instalação de um novo `.apk` quando há updates da plataforma. |
| `HIGH_SAMPLING_RATE_SENSORS` | Usado pelo `AccidentDetector` para ler o acelerómetro a alta frequência de forma a detetar impactos instantâneos. |

---

## 🗺️ 6. Comportamento do Mapa Nativo (Android)

A renderização cartográfica na app Android, construída usando `MapLibreHelper` em Kotlin, segue regras rígidas para clareza visual e gestão de memória:

1. **Marcador Principal:** Apenas a localização mais recente exibe o círculo de precisão ao redor do marcador.
2. **Histórico e Trajetos:** As localizações antigas (histórico do dia/semana) são ligadas sequencialmente por uma linha, sem desenhar círculos de precisão individuais.
3. **Limpeza de Memória (Memory Scoop):** Ao construir os dados para renderização da View (`MapViewModel.kt`), o KMP DAO é instruído a eliminar silenciosamente todas as localizações offline guardadas com mais de 10 dias, prevenindo inflação da BD local (Room).
