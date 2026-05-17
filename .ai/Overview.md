## 1. Visão Geral e Requisitos de Missão

### Requisitos Funcionais (RF)

- **RF-01: Rastreio de Localização Diferencial:** Capturar e persistir coordenadas geográficas em intervalos inteligentes indexados ao estado de movimento do utilizador.
    
- **RF-02: Mecanismo de SOS de Alta Prioridade:** Disparar de forma instantânea e redundante um estado de emergência global, forçando o bypass de restrições de rede e de bateria.
    
- **RF-03: Sincronização Assíncrona (_Offline-First_):** Armazenar todas as telemetrias localmente na ausência de cobertura de rede e escoar a fila prioritariamente com base no estado crítico dos pacotes (_LIFO_ para SOS, _FIFO_ para histórico).
    
- **RF-04: Consola de Visualização (Dashboard):** Permitir a visualização em tempo real das últimas posições conhecidas e do histórico de rotas num mapa interativo, com indicação visual clara de perdas de sinal e estados de bateria.
    
- **RF-05: Monitorização de Estado Operacional (_Heartbeat_):** Transmitir pacotes mínimos de integridade do dispositivo mesmo quando as coordenadas geográficas não se alteram.
    

### Requisitos Não-Funcionais (RNF)

- **RNF-01: Eficiência Energética Ultra-Restrita:** O consumo energético combinado em segundo plano não deve degradar a autonomia da bateria em mais de 5% a 8% por período de 12 horas de atividade operacional contínua.
    
- **RNF-02: Tolerância a Falhas e Latência em Zonas Brancas:** O sistema deve garantir integridade transacional local de 100% das telemetrias durante períodos prolongados sem conectividade IP.
    
- **RNF-03: Arquitetura Leve e Custo Zero (MVP):** Utilização estrita de tecnologias _Open Source_ e patamares gratuitos (_Free Tiers_) estáveis para suportar a infraestrutura de uma rede familiar alargada (até 10 dispositivos ativos).
    
- **RNF-04: Portabilidade Estrutural:** Core lógico isolado via Kotlin Multiplatform (KMP), permitindo a futura compilação nativa para iOS sem reengenharia do motor de dados, fila de sincronização e regras de negócio.
    

---

## 2. Stack Tecnológica e Racional Técnico

- **Mobile Tier (KMP + Jetpack Compose):** Escolha estratégica para centralizar a lógica de persistência (Room), conectividade, parsing de dados e gestão da fila de sincronização. Reduz o _time-to-market_ para a futura versão iOS, mantendo a performance nativa em Android. O Jetpack Compose oferece uma interface minimalista livre de overheads de renderização complexos.
    
- **Android Architecture Components:**
    
    - **WorkManager:** Agendamento deferível de tarefas de sincronização em background, respeitando restrições do sistema (ex: apenas com conectividade).
        
    - **Foreground Service:** Execução contínua e imutável do processo de recolha de GPS quando a monitorização ativa estiver ligada, mitigando o encerramento agressivo do processo pelo sistema operacional (_Low Memory Killer_).
        
- **Backend & Storage Tier (Next.js + Supabase):** Implementação de endpoints modulares através de _API Routes_ em TypeScript. O deploy na Vercel oferece infraestrutura serverless auto-escalável a custo zero. O Supabase (PostgreSQL) permite o uso futuro da extensão espacial **PostGIS** para indexação e queries de proximidade (_Geofencing_).
    
- **Mapping & Geolocation Services (MapLibre + OpenStreetMap):** Abordagem totalmente independente de APIs proprietárias pagas. Permite a renderização baseada em vetores e implementação simplificada de mapas com _cache offline_ local através de ficheiros em formato `.mbtiles`.
    

---

## 3. Arquitetura do Sistema e Estrutura do Repositório

O projeto adota uma abordagem de mono-repositório para simplificar a gestão de dependências e contratos de dados comuns.

```
seguranca-rural/
├── .github/                   # Workflows de CI/CD (GitHub Actions)
├── backend/                   # Projeto Next.js / API Backend
│   ├── src/
│   │   ├── app/               # Next.js App Router (EndPoints da API)
│   │   │   ├── api/
│   │   │   │   ├── location/  # POST /api/location (Ingestão)
│   │   │   │   ├── emergency/ # POST /api/emergency (SOS)
│   │   │   │   └── devices/   # GET /api/devices (Dashboard Data)
│   │   ├── lib/               # Inicialização do cliente Supabase
│   │   └── types/             # Definições TypeScript / Contratos de payload
│   ├── package.json
│   └── vercel.json
└── mobile/                    # Projeto Kotlin Multiplatform
    ├── androidApp/            # Módulo Android (UI Jetpack Compose, Services)
    ├── iosApp/                # Módulo iOS (Futuro)
    └── shared/                # Código partilhado KMP
        ├── src/
        │   ├── commonMain/    # Lógica de Negócio, Room DB, Sync Engine
        │   ├── androidMain/   # Implementações nativas Android (Location Bridge)
        │   └── iosMain/       # Implementações nativas iOS (Futuro)
```

---
Python

```
import os

# Conteúdo com formatação Markdown rigorosa e sem quebras de blocos de código
markdown_content = """# Plano Arquitetural de Engenharia: Sistema de Geolocalização Rural e Segurança Agrícola (MVP)

Este documento estabelece a especificação técnica formal para o desenvolvimento de uma solução de geolocalização otimizada, resiliente, de baixo consumo e *offline-first*. O projeto surge de uma necessidade crítica de segurança familiar em contextos agrícolas e florestais isolados (como a operação de tratores e maquinaria pesada), garantindo o rastreio fiável de familiares e a emissão imediata de alertas de emergência (SOS), salvaguardando a privacidade e mitigando os custos operacionais de infraestrutura.

---

## 1. Visão Geral e Requisitos de Missão

### Requisitos Funcionais (RF)
* **RF-01: Rastreio de Localização Diferencial:** Capturar e persistir coordenadas geográficas em intervalos inteligentes indexados ao estado de movimento do utilizador.
* **RF-02: Mecanismo de SOS de Alta Prioridade:** Disparar de forma instantânea e redundante um estado de emergência global, forçando o bypass de restrições de rede e de bateria.
* **RF-03: Sincronização Assíncrona (*Offline-First*):** Armazenar todas as telemetrias localmente na ausência de cobertura de rede e escoar a fila prioritariamente com base no estado crítico dos pacotes (*LIFO* para SOS, *FIFO* para histórico).
* **RF-04: Consola de Visualização (Dashboard):** Permitir a visualização em tempo real das últimas posições conhecidas e do histórico de rotas num mapa interativo, com indicação visual clara de perdas de sinal e estados de bateria.
* **RF-05: Monitorização de Estado Operacional (*Heartbeat*):** Transmitir pacotes mínimos de integridade do dispositivo mesmo quando as coordenadas geográficas não se alteram.

### Requisitos Não-Funcionais (RNF)
* **RNF-01: Eficiência Energética Ultra-Restrita:** O consumo energético combinado em segundo plano não deve degradar a autonomia da bateria em mais de 5% a 8% por período de 12 horas de atividade operacional contínua.
* **RNF-02: Tolerância a Falhas e Latência em Zonas Brancas:** O sistema deve garantir integridade transacional local de 100% das telemetrias durante períodos prolongados sem conectividade IP.
* **RNF-03: Arquitetura Leve e Custo Zero (MVP):** Utilização estrita de tecnologias *Open Source* e patamares gratuitos (*Free Tiers*) estáveis para suportar a infraestrutura de uma rede familiar alargada (até 10 dispositivos ativos).
* **RNF-04: Portabilidade Estrutural:** Core lógico isolado via Kotlin Multiplatform (KMP), permitindo a futura compilação nativa para iOS sem reengenharia do motor de dados, fila de sincronização e regras de negócio.

---

## 2. Stack Tecnológica e Racional Técnico

* **Mobile Tier (KMP + Jetpack Compose):** Escolha estratégica para centralizar a lógica de persistência (Room), conectividade, parsing de dados e gestão da fila de sincronização. Reduz o *time-to-market* para a futura versão iOS, mantendo a performance nativa em Android. O Jetpack Compose oferece uma interface minimalista livre de overheads de renderização complexos.
* **Android Architecture Components:**
  * **WorkManager:** Agendamento deferível de tarefas de sincronização em background, respeitando restrições do sistema (ex: apenas com conectividade).
  * **Foreground Service:** Execução contínua e imutável do processo de recolha de GPS quando a monitorização ativa estiver ligada, mitigando o encerramento agressivo do processo pelo sistema operacional (*Low Memory Killer*).
* **Backend & Storage Tier (Next.js + Supabase):** Implementação de endpoints modulares através de *API Routes* em TypeScript. O deploy na Vercel oferece infraestrutura serverless auto-escalável a custo zero. O Supabase (PostgreSQL) permite o uso futuro da extensão espacial **PostGIS** para indexação e queries de proximidade (*Geofencing*).
* **Mapping & Geolocation Services (MapLibre + OpenStreetMap):** Abordagem totalmente independente de APIs proprietárias pagas. Permite a renderização baseada em vetores e implementação simplificada de mapas com *cache offline* local através de ficheiros em formato `.mbtiles`.

---

## 3. Arquitetura do Sistema e Estrutura do Repositório

O projeto adota uma abordagem de mono-repositório para simplificar a gestão de dependências e contratos de dados comuns.

```

Resultado do código

```
Versão v2 gerada com sucesso em: Plano_Arquitetura_App_Seguranca_Rural_v2.md

```

seguranca-rural/

├── .github/ # Workflows de CI/CD (GitHub Actions)

├── backend/ # Projeto Next.js / API Backend

│ ├── src/

│ │ ├── app/ # Next.js App Router (EndPoints da API)

│ │ │ ├── api/

│ │ │ │ ├── location/ # POST /api/location (Ingestão)

│ │ │ │ ├── emergency/ # POST /api/emergency (SOS)

│ │ │ │ └── devices/ # GET /api/devices (Dashboard Data)

│ │ ├── lib/ # Inicialização do cliente Supabase

│ │ └── types/ # Definições TypeScript / Contratos de payload

│ ├── package.json

│ └── vercel.json

└── mobile/ # Projeto Kotlin Multiplatform

├── androidApp/ # Módulo Android (UI Jetpack Compose, Services)

├── iosApp/ # Módulo iOS (Futuro)

└── shared/ # Código partilhado KMP

├── src/

│ ├── commonMain/ # Lógica de Negócio, Room DB, Sync Engine

│ ├── androidMain/ # Implementações nativas Android (Location Bridge)

│ └── iosMain/ # Implementações nativas iOS (Futuro)

````

---

## 4. Design de Interface (UI) e Fluxo de Utilização

A interface é desenhada para operar sob condições de stress, luvas de trabalho ou visibilidade reduzida no campo (luminosidade solar extrema).

### 4.1. HomeScreen (Ecrã Principal)
* **Botão SOS Embebido:** Área de toque correspondente a 40% do ecrã vertical. Cor vermelha saturada (`#DC2626`). Requer um gesto de *Long Press* (2 segundos) com feedback hático contínuo para evitar ativações acidentais no bolso.
* **Painel de Estado de Alta Visibilidade:**
  * **Estado de Rastreio:** Toggle switch de grandes dimensões para ligar/desligar o serviço de localização em background.
  * **Indicador de Conetividade Dinâmico:** Badge textual e cromático indicando `ONLINE` em verde, ou `OFFLINE` em amarelo, acompanhado do número de registos pendentes na fila de sincronização (ex: `[32 posições retidas]`).
  * **Métricas de Sistema Reduzidas:** Informação em tempo real do nível de bateria local e precisão atual do sensor GPS em metros.

### 4.2. MapScreen (Ecrã de Visualização e Monitorização)
* **Camada de Renderização Offline:** Utilização do MapLibre SDK configurado para ler azulejos (*tiles*) pré-descarregados da região de atuação agrícola da família.
* **Representação de Rota Histórica:** Linha vetorial polilinha (*polyline*) com gradiente de cor indexada à antiguidade do ponto (ponto mais recente em azul brilhante, pontos antigos a desvanecer para cinzento).
* **Filtros Rápidos Temporais:** Segmented Control na base do ecrã permitindo alternar instantaneamente entre visões de: `Hoje`, `Últimas 24 Horas`, e `Histórico Semanal`.
* **Tratamento de Alertas no Mapa:** Caso um dispositivo remoto ative o modo SOS, o marcador correspondente adquire uma animação de pulsação radial vermelha permanente, centrando o mapa automaticamente nessa coordenada.

### 4.3. ConfigScreen (Ecrã de Parametrização Técnica)
* **Ajuste de Granularidade Temporal:** Dropdown seletor para intervalo de atualização padrão (`5 min`, `15 min`, `30 min`, `Modo Inteligente Adaptativo`).
* **Políticas de Restrição de Dados:** Switches independentes para permitir sincronização em dados móveis e pausar rastreio se o dispositivo estiver estático.
* **Campos de Emergência:** Input de texto validado para contacto telefónico de salvaguarda (integração com o discador nativo do sistema operacional em caso de falha de rede).

---

## 5. Engenharia de Dados: Contratos de Comunicação e Esquema Relacional

### 5.1. Contrato do Payload de Telemetria (JSON)
Este objeto de dados é gerado pelo core do KMP e enviado para o endpoint `/api/location`.

```json
{
  "deviceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "deviceLabel": "Trator-Pai",
  "timestamp": "2026-05-17T14:23:00.123Z",
  "batteryLevel": 88,
  "batteryCharging": false,
  "gps": {
    "lat": 39.824167,
    "lng": -7.493056,
    "accuracy": 4.8,
    "speed": 6.1,
    "heading": 184.5
  },
  "emergencyState": false,
  "trackingEnabled": true,
  "networkType": "3G",
  "appVersion": "1.0.0-release"
}
````

### 5.2. Esquema da Base de Dados (DML SQL - Supabase PostgreSQL)

O modelo de dados implementa integridade referencial estrita e índices compostos focados em performance de ordenação cronológica.

SQL

```
-- Ativação da extensão geográfica (PostGIS) para uso futuro em Geofencing
CREATE EXTENSION IF NOT EXISTS postgis;

-- Tabela de Registo de Dispositivos Familiares
CREATE TABLE public.devices (
    id UUID PRIMARY KEY,
    label VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ,
    tracking_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    app_version VARCHAR(20) NOT NULL
);

-- Tabela de Histórico de Telemetrias e Localizações
CREATE TABLE public.locations (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    lat NUMERIC(9,6) NOT NULL,
    lng NUMERIC(9,6) NOT NULL,
    accuracy REAL NOT NULL,
    speed REAL NOT NULL,
    heading REAL NOT NULL,
    battery_level SMALLINT NOT NULL,
    emergency_state BOOLEAN NOT NULL DEFAULT FALSE,
    network_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Criação de Índices Otimizados para Consultas do Dashboard
CREATE INDEX idx_locations_device_created ON public.locations(device_id, created_at DESC);
CREATE INDEX idx_locations_emergency ON public.locations(emergency_state) WHERE emergency_state = TRUE;

-- Habilitação de Segurança ao Nível de Linha (Row Level Security - RLS)
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

-- Políticas de Acesso Restrito Baseadas em Autenticação (Apenas utilizadores logados da família)
CREATE POLICY "Acesso total para utilizadores autenticados" 
ON public.devices FOR ALL TO authenticated USING (true);

CREATE POLICY "Acesso total a localizações para utilizadores autenticados" 
ON public.locations FOR ALL TO authenticated USING (true);
```

---

## 6. Motor Offline-First e Estratégia de Sincronização

A fiabilidade da aplicação baseia-se na autonomia do armazenamento local gerido pelo **Room Database**.

### Regras de Escoamento da Fila (Engine de Sincronização)

Quando a conectividade à internet é restabelecida, o subsistema de sincronização aplica uma estratégia de escoamento assimétrico:

1. **Fase 1: Alertas Críticos (Prioridade Máxima - LIFO):** Procura na base de dados local por registos onde `emergency_state == true`. Estes registos são transmitidos imediatamente de forma isolada.
    
2. **Fase 2: Estado Atual (Prioridade Média - Último Ponto):** Transmite a localização mais recente capturada no tempo presente, garantindo que os familiares sabem onde o utilizador se encontra _agora_, antes de processar o histórico antigo.
    
3. **Fase 3: Reconstituição de Rota (Prioridade Baixa - FIFO):** Processa cronologicamente os registos normais acumulados durante o período offline. O envio é realizado em lotes (_batching_) de 25 registos por chamada para poupar overhead de handshake HTTP.
    

---

## 7. Estratégias de Conservação Crítica de Bateria

O maior desafio técnico deste projeto é evitar que o sistema operacional Android termine a recolha de localização e otimizar o uso do rádio GPS, que apresenta um consumo energético elevado.

### 7.1. Amostragem Dinâmica Adaptativa

O intervalo de amostragem altera-se autonomamente baseado nas variáveis de telemetria analisadas no próprio dispositivo:

- **Estático / Parado (Acelerómetro sem variação substantiva OU velocidade < 1 km/h):** Intervalo de captura de 45 a 60 minutos. Utiliza sensores baseados em redes móveis (Cell ID).
    
- **Em Movimento (Transição via Activity Recognition API OU velocidade > 3 km/h):** Intervalo de captura de 5 a 15 minutos. Utiliza o `FusedLocationProviderClient` balanceado.
    
- **Modo Emergência (SOS Ativo):** Intervalo imediato e contínuo (cada 15 segundos). GPS Puro de alta precisão ativa e desativação de qualquer tipo de batching.
    

### 7.2. Otimizações de Rádio e Hardware

- **Batching de Hardware:** Utilizar a capacidade do chip GPS de reter coordenadas na sua memória interna (hardware FIFO) e recolhê-las em bloco, evitando acordar o processador principal continuamente.
    
- **Filtro de Precisão (Accuracy Filtering):** Localizações com raio de imprecisão superior a 80 metros são registadas localmente mas marcadas com flag de degradação. Se uma nova coordenada com melhor precisão for obtida nos 10 segundos seguintes, a coordenada má é descartada.
    

---

## 8. Persistência em Background e Resiliência do Sistema Operacional

Para mitigar o fecho agressivo da aplicação pelas políticas de energia do Android (Doze Mode e App Standby), o projeto implementa dois mecanismos essenciais:

### 8.1. Implementação de Boot Receiver (Persistência a Reinicializações)

Se o telemóvel do familiar for reiniciado, o rastreio inicia automaticamente sem intervenção humana através de um `BroadcastReceiver` nativo.

Kotlin

```
package com.seguranca.rural

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPrefs = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
            val isTrackingEnabled = sharedPrefs.getBoolean("tracking_active", false)
            
            if (isTrackingEnabled) {
                val serviceIntent = Intent(context, LocationForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
```

### 8.2. Monitorização Ativa de Estado Operacional (Heartbeat)

- A cada 30 minutos, se nenhuma nova localização geográfica for gerada (utilizador parado), a app força o envio de um pacote mínimo contendo apenas `deviceId`, `timestamp`, `batteryLevel` e o estado da rede.
    
- Se o servidor Next.js não receber qualquer pacote (localização ou heartbeat) de um dispositivo num período superior a 60 minutos, o estado do utilizador muda automaticamente no dashboard para `Desconectado / Alerta de Inatividade`.
    

---

## 9. Segurança, Autenticação e Privacidade dos Dados Familiares

Tratando-se de uma aplicação privada de distribuição familiar, o foco reside no isolamento total dos dados contra acessos externos não autorizados, sem introduzir fricção complexa de login.

- **Acesso Administrador/Monitor (Web Dashboard):** Autenticação no painel Next.js feita através de **Magic Links** enviados por Email através do Supabase Auth. Elimina a necessidade de gerir palavras-passe complexas.
    
- **Acesso nos Dispositivos de Rastreio (App Mobile):** O APK instalado nos telemóveis dos familiares não exige autenticação interativa contínua. Aquando da primeira configuração, o dispositivo é registado na tabela `devices` e é gerado um `UUID` único de identificação armazenado de forma segura no **EncryptedSharedPreferences** do Android. Cada pedido HTTP subsequente assina o cabeçalho com um token estático de autorização do dispositivo.
    

---

## 10. Roadmap de Implementação por Fases

### Fase 1: Infraestrutura Base e Funcionalidades Core (Semanas 1-2)

- [ ] Inicialização do mono-repositório Git e criação do projeto Next.js com deploy automático na Vercel.
    
- [ ] Provisionamento da instância gratuita do Supabase e execução do script DDL de criação de tabelas e índices.
    
- [ ] Implementação do endpoint API `/api/location` para receção de telemetrias.
    
- [ ] Desenvolvimento da interface gráfica do HomeScreen em Jetpack Compose com o botão de SOS funcional.
    
- [ ] Acoplamento do `Foreground Service` básico para captura de coordenadas GPS em tempo real.
    

### Fase 2: Otimização Energética e Resiliência Offline (Semanas 3-4)

- [ ] Integração do `WorkManager` para monitorização do estado de rede e gestão do escoamento assíncrono da fila.
    
- [ ] Implementação do algoritmo de amostragem adaptativa ligado aos sensores de movimento do telemóvel.
    
- [ ] Integração do MapLibre SDK na visualização móvel com suporte a mapas offline regionais em formato `.mbtiles`.
    
- [ ] Criação do `BootReceiver` e rotina de _Heartbeat_ em segundo plano.
    

### Fase 3: Funcionalidades Avançadas de Segurança e Fecho (Semanas 5-6)

- [ ] **Detetor Automático de Acidentes (Crash Detection):** Subscrição dos sensores do acelerómetro e giroscópio. Se for registado um vetor de aceleração abrupta (impacto/capotamento de trator) seguido de imobilidade por mais de 60 segundos, a app dispara autonomamente o SOS.
    
- [ ] **Alerta de Bateria Crítica (Last Gasp):** Se a bateria descer abaixo de 10%, a app efetua uma transmissão prioritária final de alta precisão indicando o ponto de paragem eminente antes de desligar.
    
- [ ] Geração do pacote de compilação final (`.apk` otimizado em modo Release) e instalação direta nos dispositivos da família.
    
    """
    

output_path = "Plano_Arquitetura_App_Seguranca_Rural_v2.md"

with open(output_path, "w", encoding="utf-8") as f:

f.write(markdown_content)

print(f"Versão v2 gerada com sucesso em: {output_path}")

```
Peço desculpa pela formatação anterior. Algumas quebras de linha e blocos de código extensos geraram ruído visual na leitura. 

Efetuei uma limpeza completa na estrutura e gerei uma versão estritamente organizada. Os blocos de texto foram limpos de ruídos de renderização e as tabelas complexas foram convertidas em estruturas de tópicos técnicos de leitura imediata.

O seu ficheiro Markdown (MD) limpo e corrigido está pronto
[file-tag: code-generated-file-0-1779027842420664667]

### O que foi corrigido na formatação desta versão (`v2`):
* **Normalização de Blocos:** Eliminação de sobreposições de diagramas ASCII dentro de secções que quebravam em ecrãs móveis ou visualizadores Markdown comuns.
* **Simplificação da Tabela de Amostragem:** Transposta para uma listagem técnica limpa na secção 7.1, garantindo que qualquer interpretador Markdown converta o texto sem desalinhar colunas.
* **Contratos de Código Limpos:** Os blocos de JSON, SQL e Kotlin foram isolados com delimitadores limpos para facilitar o "Copy-Paste" direto para o seu ambiente de desenvolvimento (IDE).

Pode utilizar diretamente este novo ficheiro no ficheiro `README.md` do seu repositório Git para servir de documentação mestre do projeto.
```