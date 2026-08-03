# 🚜 Ecossistema Segurança Rural — Mapa de Arquitetura Global

> **Documentação de Contexto Global (Master README)**
> Este ficheiro serve como o mapa de alto nível para todo o ecossistema. Qualquer programador (ou IA) que inicie trabalhos neste repositório deve ler este documento para compreender a topologia, propósito e o fluxo de dados do projeto.

## 🎯 Propósito Global
O **Segurança Rural** é um sistema *production-grade* e *offline-first* para monitorização GPS (telemetria) e emissão de alertas SOS. Foi desenhado especificamente para famílias que operam tratores e maquinaria pesada em zonas rurais e agrícolas isoladas, frequentemente com conetividade celular fraca ou inexistente. A integridade dos dados e a persistência em cenários sem rede são prioridades máximas.

---

## 🗂️ Estrutura de Pastas, Componentes e Documentação

O projeto é um monorepo que agrega o backend cloud e as aplicações móveis baseadas em tecnologia partilhada. Cada módulo tem o seu próprio `README` detalhado que deves consultar ao trabalhar nessa área específica:

- **`backend/`** (Lê o `backend/README.md`): Código Next.js que expõe as APIs REST de telemetria e o dashboard web. Contém a documentação sobre a base de dados Supabase, autenticação M2M vs JWT e rotas API.
- **`mobile/`**: Ecossistema Mobile.
  - **`mobile/shared/`** (Lê o `mobile/shared/README_SHARED.md`): Módulo KMP (Kotlin Multiplatform) que contém toda a lógica core (Base de Dados Local, Sync Engine, Filtro Kalman). É a fonte da verdade da lógica de negócio offline-first.
  - **`mobile/app/`** (Lê o `mobile/app/README_ANDROID.md`): App Android nativa. Contém a documentação sobre UI em Jetpack Compose, serviços em background contínuos, permissões e renderização nativa do mapa.
  - **`mobile/iosApp/`** (Lê o `mobile/iosApp/README_IOS.md`): App iOS nativa. Contém a documentação sobre arquitetura SwiftUI, rastreio CoreLocation nativo, tarefas de background (BGTasks) e renderização nativa do mapa no iOS.

---

## 🧩 Tecnologias Base Usadas

| Camada | Tecnologias |
| :--- | :--- |
| **Backend & API** | **Next.js 15** (App Router), TypeScript, Vercel |
| **Base de Dados** | **Supabase (PostgreSQL)**, PostGIS (geofencing espacial), RLS (Row Level Security) |
| **Mobile Core (Shared)**| **Kotlin Multiplatform (KMP)**, **Room DB** (fila offline no dispositivo) |
| **Mobile (Android)** | **Jetpack Compose** (UI), WorkManager, FusedLocationProvider |
| **Mobile (iOS)** | **SwiftUI** (UI) |
| **Mapas Offline** | **MapLibre GL** (leitura local de `.mbtiles` sem internet) |

---

## 📐 Arquitetura Global e Comunicação

**Como é que o mobile comunica com o backend?**
A aplicação móvel atua essencialmente como um sensor/cliente pesado que comunica com a API (Next.js) através de chamadas **HTTPS (REST)** standard.

1. **Recolha e Armazenamento Local:** O hardware móvel lê coordenadas (e.g., a cada poucos minutos ou a cada 15 segundos em SOS). Estas não vão diretamente para a rede, mas são primeiro escritas e encriptadas numa Base de Dados Room local (KMP `shared/`). Isto garante que num vale sem cobertura de rede, não há perda de dados.
2. **Motor de Sincronização (SyncEngine):** Um worker em background (`WorkManager` no Android) deteta quando há rede e esvazia a fila offline em lotes. Envia um *POST payload* em JSON para os endpoints do Next.js.
3. **Backend Middleware:** As rotas de API no Next.js validam a autenticidade do dispositivo e atuam como proxy/middleware de validação.
4. **Base de Dados:** Usando a *Supabase Service Role Key*, o Next.js insere os registos de modo seguro na base de dados PostgreSQL alojada no Supabase.

---

## 🔄 Fluxo Principal de Dados (Data Flow)

O caminho de vida de uma coordenada de localização (ou de um evento de pânico SOS) segue rigidamente este canal unidirecional:

`1. Sensor (GPS no telemóvel)`
⬇️
`2. Fila Offline (Room DB no módulo KMP shared)`
⬇️
`3. Motor de Sincronização (SyncEngine avalia estado da rede)`
⬇️
`4. Envio HTTPS POST (Carga JSON encriptada)`
⬇️
`5. API Next.js Middleware (/api/location ou /api/emergency)`
⬇️
`6. Supabase (PostgreSQL / PostGIS)`

---

## 🛡️ Segurança e Autenticação

O ecossistema divide-se em dois contextos de autenticação:

- **Trackers/Tratores (Aplicações Móveis):** Para evitar complexidade e fricção com os utilizadores idosos/rurais, os trackers não exigem login manual. É gerado um UUID e injetado o segredo do sistema aquando do emparelhamento inicial. Estas credenciais viajam nos *headers* HTTP (e.g. `Authorization: Bearer <DEVICE_API_SECRET>`). O Next.js valida o request antes de inserir no Supabase.
- **Familiares/Administradores (Dashboard Web/Supabase):** Quando um utilizador pretende visualizar os dados via Web (Dashboard Next.js), utiliza a Autenticação oficial do Supabase (e.g., Magic Links). Ao aceder às tabelas do Supabase, aplicam-se Políticas RLS (Row Level Security) que garantem que aquele familiar só vê a telemetria dos tratores que lhe pertencem.
