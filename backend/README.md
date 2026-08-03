# 🎛️ Backend: Next.js API & Supabase

> Este documento mapeia a infraestrutura de Backend do sistema **Segurança Rural**. O objetivo é compreender exatamente a topologia da base de dados, a superfície da API REST e os mecanismos de segurança e rate limiting que protegem a ingestão da telemetria.

---

## 🗄️ Base de Dados (Supabase PostgreSQL)

A base de dados é estruturada para alta performance de escrita temporal (Time-Series) e com suporte a *Multitenancy* (múltiplas quintas/famílias).
O schema tira partido de **PostGIS** para análise geo-espacial e usa funções RPC (Stored Procedures) nativas para evitar o anti-pattern *N+1* na leitura dos dados.

### Tabelas Principais
1. **`devices`**: Lista dos tratores/trackers registados. Usa o `ANDROID_ID` como chave primária para estabilidade e aloja configurações (cor, token FCM, farm associada).
2. **`locations`**: Registo histórico de telemetria. Contém as coordenadas (`lat`, `lng`), estado da bateria, estado SOS (`emergency_state`) e uma coluna `geom` (PostGIS) atualizada via trigger.
3. **`farms` & `farm_members`**: Controlo de acessos *multi-tenant*. Associa utilizadores a quintas com níveis de permissões (`owner`, `admin`, `viewer`).
4. **`farm_invites`**: Códigos de convite efémeros para integrar familiares na quinta.
5. **`geofences`**: Polígonos espaciais associados a uma quinta para disparar alertas quando um trator sai da zona segura.

### RPCs e Triggers Chave (Stored Procedures)
- **`get_latest_positions()`**: Em vez de ler todo o histórico de `locations`, usa `DISTINCT ON` no PostgreSQL para obter em tempo `O(n_devices)` a última posição absoluta de cada trator. Usado massivamente pelo Dashboard para desenhar os marcadores em tempo real.
- **`check_geofence_violation()`**: Função executada com `SECURITY INVOKER` que verifica (via `ST_Covers` do PostGIS) se a coordenada recebida está fora de todos os polígonos da `farm`.
- **`sync_location_geom()` / `locations_set_geom()`**: Triggers que automaticamente convertem `lat/lng` em colunas `GEOGRAPHY/GEOMETRY` na inserção, aliviando o motor NodeJS desse trabalho.

---

## 📡 Superfície de API (Next.js App Router)

A API divide-se em duas grandes categorias: **Ingestão (M2M)** via Device Secret, e **Dashboard (Humanos)** via JWT.

### Endpoints de Telemetria e Dispositivos (Autenticação M2M)
Estes endpoints são chamados exclusivamente pela app Android que corre no Trator. Exigem um token fixo `DEVICE_API_SECRET` no Header `Authorization`.

| Método | Rota | Propósito | Auth |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/location` | Ingestão primária da fila offline de coordenadas. Faz upsert ao `device` e insert no histórico. | `Device Secret` |
| **POST** | `/api/v2/location` | Nova versão estruturada da ingestão de coordenadas. | `Device Secret` |
| **POST** | `/api/emergency` | **Rota de Alta Prioridade**. Disparada quando o utilizador preme o botão SOS. Bypassa qualquer batching e força `emergency_state = TRUE`. Preparado para disparar Webhooks/Push imediatos. | `Device Secret` |
| **PATCH**| `/api/devices/fcm-token`| Guarda o token de push do Firebase Cloud Messaging para aquele `ANDROID_ID`, permitindo contactar o trator a partir do dashboard. | `Device Secret` |
| **GET** | `/api/app/version` | Endpoint público lido no arranque da app para validar se há atualizações OTA (Over-The-Air) disponíveis. | Pública |

### Endpoints do Dashboard (Autenticação JWT/User)
Estes endpoints servem a interface Web (onde os familiares consultam os tratores). Exigem Sessão JWT válida emitida pelo Supabase.

| Método | Rota | Propósito | Auth |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/devices` | Lista todos os tratores associados às quintas do utilizador e as respetivas posições mais recentes. | `JWT` |
| **GET** | `/api/positions/last`| Agregação rápida de últimas posições delegada no RPC `get_latest_positions()`. | `JWT` |
| **GET/POST**| `/api/devices/config`| Lê ou escreve a configuração remota de um trator (e.g. intervalo de envio, cores do mapa). | `JWT / Secret` |
| **POST** | `/api/auth/create-farm`| Inicializa um ambiente segregado para uma nova família usando `create_farm_with_owner`. | `JWT` |
| **POST** | `/api/auth/join-farm` | Aceita um código de convite para associar o utilizador a uma quinta existente. | `JWT` |
| **POST** | `/api/farms/invite` | Gera os referidos códigos temporários para adicionar familiares. | `JWT` |
| **POST** | `/api/farms/members` | Gestão de membros (promover a admin, expulsar). | `JWT` |

---

## 🛡️ Segurança, Middleware e Rate Limiting (`proxy.ts`)

O tráfego passa por um guarda-costas global robusto no ficheiro `proxy.ts`, que aplica 3 camadas de defesa antes que as rotas de ingestão acordem:

1. **Separação Arquitetural Restrita:** O middleware verifica o Header `Authorization`. Se a rota pertencer a telemetria (`/api/v2/location` ou `/api/emergency`), **rejeita** JWTs de utilizadores web e exige o `DEVICE_API_SECRET`.
2. **Prevenção de Timing Attacks:** O `DEVICE_API_SECRET` é validado usando a função custom `timingSafeEqual()`, que compara os tokens byte-a-byte usando operação bitwise `XOR`, camuflando o comprimento de onde a string diverge e impedindo ataques de temporização (side-channel).
3. **Rate Limiting em Memória (Sliding Window):**
   - Limite estabelecido: **60 requests por minuto**.
   - Identificação baseada no Header `x-device-serial`, caindo para IP de rede caso falhe.
   - Entradas "stale" são purgadas a cada 5 minutos (prevenindo memory-leaks na Vercel Cloud).
   - Impede DDOS ou falhas catastróficas em que a app Android fique presa num loop e esgote os limites do Supabase.

---

## 🗺️ Comportamento do Mapa Web (Dashboard)

A renderização cartográfica segue regras rígidas para garantir clareza visual:
- **Timestamps:** A hora apresentada ("Último sinal") reflete o momento exato em que a coordenada foi captada pelo chip GPS do dispositivo (`created_at`), garantindo precisão temporal mesmo que o trator tenha sincronizado dados antigos muito tempo depois. Não confundir com `last_seen_at`, que regista o momento em que o servidor web recebeu a última comunicação.

---

## ⚡ Integrações e Serviços Assíncronos Previstos

- **FCM (Firebase Cloud Messaging):** A tabela `devices` e a rota `PATCH /api/devices/fcm-token` já estabelecem a base para *Push Notifications* inversas. No futuro, isto servirá para o Dashboard enviar um "Ping" remoto, forçando o trator adormecido a ligar o hardware de GPS.
- **Supabase Realtime:** A tabela `locations` está adicionada à publicação `supabase_realtime` nativa no esquema. Isto permite que o Dashboard desenhe o trator a mexer-se no mapa usando WebSockets, sem necessidade de polling intensivo à API Next.js.
