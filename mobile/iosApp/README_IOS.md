# 🍏 Aplicação iOS Nativ (iOS App Module) — Mapa Arquitetural

> Este documento analisa exclusivamente a camada nativa iOS do ecossistema **Segurança Rural**. O objetivo é demonstrar como a UI (SwiftUI), os serviços core da Apple (CoreLocation, CoreMotion, APNs) e as tarefas de background interagem com a lógica de negócio central escrita em Kotlin Multiplatform (KMP).

---

## 📱 1. Ecrãs (SwiftUI Views) e Navegação

A aplicação segue a arquitetura declarativa e moderna da Apple usando **SwiftUI**. A raiz do projeto está em `iOSApp.swift` (que serve de `App` _entry-point_ e aloja o `AppDelegate` tradicional para gerir *hooks* de ciclo de vida e *Push Notifications*).

**Arquitetura de Navegação:**
- **`ContentView.swift`**: É o *Router* principal da app. Usa uma `TabView` padrão do iOS com *bottom bar*.
- **`HomeView.swift`**: Ecrã principal de controlo. Possui o botão gigante de SOS e permite ao utilizador iniciar ou parar a monitorização GPS.
- **`MapView.swift`**: Visualização do mapa recorrendo a MapLibre (via `NativeMapView.swift` / `UIViewRepresentable`), com capacidade de ler o estado (posições dos membros) injetado a partir dos *ViewModels* partilhados.
- **`SettingsView.swift`**: Ecrã de configuração de parâmetros locais (intervalos, raio de distância). 
- **`FamilyView.swift`**: Interface de visualização da família e dos *roles*.

**Integração com KMP:**
As `Views` SwiftUI invocam Kotlin quer através da injeção de dependências exposta no Koin (`KoinIOSKt`), quer observando *StateFlows* do Kotlin convertidos para Async/Await (`for await ... in streamCFlow(flow)`).

---

## 🔒 2. Permissões e Background Modes (`Info.plist`)

Dado o perfil agressivo de proteção de privacidade da Apple, o *Info.plist* está rigorosamente configurado:

### Permissões Exigidas (Privacy Descriptions):
1. **Localização**: `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysUsageDescription` e `NSLocationAlwaysAndWhenInUseUsageDescription`. Justificadas no ecrã como vitais para o envio contínuo da posição do trator para a fazenda.
2. **Movimento (Acelerómetro)**: `NSMotionUsageDescription` - Exigida para que o `AccidentDetector` nativo consiga registar capotamentos acentuados.

### Modos de Background Ativos (`UIBackgroundModes`):
- `location`: Permite que o `CLLocationManager` mantenha a app viva indefinidamente, desde que o tracking esteja ativo e a indicação azul surja na *Dynamic Island/Notch*.
- `fetch` e `processing`: Ativam o escalonamento do `BGTaskScheduler` para rotinas agendadas (ex: sincronização).
- `remote-notification`: Permite ser acordado via *Silent Pushes* ou Alertas Firebase.

---

## 📡 3. Rastreio Nativo (`LocationService.swift`)

Devido à impossibilidade de delegar código puramente nativo e assíncrono perfeitamente para Kotlin no que toca ao ciclo de vida de hardware da Apple, o motor de GPS é **100% Nativo (Swift)**:

- **Configuração:** O `LocationService.swift` implementa o `CLLocationManagerDelegate`.
- **Estratégia de Bateria:** Usa `allowsBackgroundLocationUpdates = true` e desativa `pausesLocationUpdatesAutomatically` para impedir que o iOS mate a app quando o trator para no campo.
- **Filtros e Qualidade:** Se o cabo de isqueiro/carregador estiver ligado ou a bateria > 20%, usa o perfil `kCLLocationAccuracyBestForNavigation` (altíssimo consumo/precisão). Abaixo disso, recorre ao método nativo `startMonitoringSignificantLocationChanges()` para poupar bateria.
- **O Handoff (Swift ➔ Kotlin):** Quando o método `didUpdateLocations` apanha uma nova latitude/longitude, instancia um `TelemetryRecord` e envia-o para o KMP executando de imediato:
  `KoinIOSKt.getSubmitLocationUseCase().invoke(record: record)`
  (Aqui, o motor KMP executa o *Kalman Filter* matemático e decide se guarda no *Room/SQLDelight* ou se ignora pontos fixos).

---

## 🔄 4. BGTasks e Sincronização Offline (`SyncCoordinator`)

Sincronizar a base de dados SQL local com a *Cloud* num iPhone em repouso exige *finesse* devido à natureza hostil do sistema operativo.

1. **Agendamento Estrito:** A app declara a tarefa `com.segurancarural.gpstracker.sync` no *Info.plist* e no `AppDelegate` usando `BGTaskScheduler`.
2. **Execução Opportunística (`BGAppRefreshTask`):** Quando o iOS decide dar uns segundos de "despertar" à App (por norma ligado à rede Wi-Fi/Carregador), o `AppDelegate` despacha imediatamente:
   `KoinIOSKt.getSyncEngine().flush()`
3. **Mecanismo Dinâmico Foreground (`SyncCoordinator.swift`):** Se a App estiver no ecrã (foreground), usa o `NWPathMonitor`. Assim que este deteta que o telemóvel restabeleceu rede (ex: saiu de um buraco sem cobertura na quinta), executa o `flush()` massivo imediato para o KMP.

---

## 🚀 5. "Como implementar uma Nova Feature KMP no iOS?" (Guia Rápido)

1. **Desenhar a Lógica (*Shared*):** Escrevees a arquitetura (Room, Ktor, Lógica) no módulo Kotlin.
2. **Exportar Instância (Koin):** No Kotlin, exportas a variável globalmente (`fun getMyUseCase() = ...`).
3. **Ouvir em SwiftUI:** Nas Views `.swift`, inicializas os fluxos usando `KoinIOSKt.getMyUseCase()` e envolves num `Task` assíncrono para atualizar variáveis `@Published`.
4. **Respeitar o Nativo:** Se a funcionalidade envolver Câmara, Sensores (G-Force) ou Ficheiros pesados, programa isso **aqui em Swift**, e envia apenas o *output* resultante para os métodos Kotlin do KMP, exatamente como o `LocationService` faz com os registos de GPS.

---

## 🗺️ 6. Comportamento do Mapa Nativo (iOS)

A renderização cartográfica na app iOS (gerida em `MapView.swift` e `NativeMapView.swift` via MapLibre) segue regras rígidas para clareza visual e gestão de memória:

1. **Marcador Principal:** Apenas a localização mais recente exibe o círculo de precisão ao redor do marcador.
2. **Histórico e Trajetos:** As localizações antigas são ligadas sequencialmente por uma linha contínua, não se desenhando círculos de precisão em pontos passados.
3. **Limpeza de Memória (Memory Scoop):** O modelo subjacente apaga silenciosamente localizações offline com mais de 10 dias de idade da BD local sempre que a trajetória histórica é solicitada (através do view model / KMP associado).
