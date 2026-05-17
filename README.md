# 🚜 Segurança Rural — Tractor GPS & SOS

> **Production-grade, offline-first GPS tracking and emergency alerting system for agricultural environments.** Built for families operating tractors and heavy machinery in isolated rural areas with limited network coverage.

[![Next.js](https://img.shields.io/badge/Backend-Next.js_15-black?logo=next.js)](https://nextjs.org/)
[![Kotlin](https://img.shields.io/badge/Mobile-Kotlin_Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Supabase](https://img.shields.io/badge/Database-Supabase_PostgreSQL-3ECF8E?logo=supabase)](https://supabase.com/)
[![MapLibre](https://img.shields.io/badge/Maps-MapLibre_GL-396CB2?logo=maplibre)](https://maplibre.org/)
[![Vercel](https://img.shields.io/badge/Deploy-Vercel-000?logo=vercel)](https://vercel.com/)

---

## 📐 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        FAMILY DEVICES (Android)                      │
│                                                                       │
│  ┌──────────────────────────────────────────────────┐               │
│  │           Kotlin Multiplatform Core (shared/)    │               │
│  │  ┌─────────────┐  ┌──────────┐  ┌────────────┐  │               │
│  │  │ Room DB     │  │SyncEngine│  │  Models    │  │               │
│  │  │ (Offline Q) │  │(3-Phase) │  │(TelRecord) │  │               │
│  │  └─────────────┘  └──────────┘  └────────────┘  │               │
│  └──────────────────────────────────────────────────┘               │
│                                                                       │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────┐  │
│  │  Foreground  │  │  WorkManager     │  │   Jetpack Compose    │  │
│  │  Service     │  │  SyncWorker      │  │   UI (3 screens)     │  │
│  │  (FusedGPS)  │  │  (Network gate)  │  │  Home/Map/Config     │  │
│  └──────────────┘  └──────────────────┘  └──────────────────────┘  │
└──────────────┬──────────────────────────────────────────────────────┘
               │ HTTPS (telemetry + SOS)
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│               BACKEND (Next.js App Router — Vercel)                  │
│                                                                       │
│   POST /api/location   POST /api/emergency   GET /api/devices        │
│        │                      │                    │                  │
│        └──────────────────────┴────────────────────┘                 │
│                               │                                       │
│                    Supabase PostgreSQL                                │
│             (devices + locations tables, PostGIS, RLS)               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🗂️ Repository Structure

```
nextGPStracking/
├── .ai/
│   ├── Overview.md          # Full architecture specification (PT)
│   └── plan.md              # Development status tracker
├── .github/
│   └── workflows/
│       └── ci.yml           # CI: lint + build validation
├── backend/                 # Next.js API backend
│   ├── src/
│   │   ├── app/
│   │   │   └── api/
│   │   │       ├── location/    # POST – telemetry ingest
│   │   │       ├── emergency/   # POST – SOS handler
│   │   │       └── devices/     # GET  – dashboard data
│   │   ├── lib/
│   │   │   └── supabase.ts      # Supabase singleton client
│   │   └── types/
│   │       └── telemetry.ts     # TypeScript payload contracts
│   ├── supabase-schema.sql      # Full DDL (run in Supabase SQL editor)
│   ├── vercel.json
│   └── .env.local.example
└── mobile/                  # Kotlin Multiplatform project
    ├── shared/              # KMP shared logic (Android + iOS stub)
    │   └── src/
    │       ├── commonMain/  # Room DB, SyncEngine, models
    │       ├── androidMain/ # Android-specific Room driver
    │       └── iosMain/     # iOS stubs (future)
    └── app/                 # Android app module
        └── src/main/
            ├── java/com/seguranca/rural/
            │   ├── LocationForegroundService.kt
            │   ├── BootReceiver.kt
            │   ├── SyncWorker.kt
            │   ├── MainActivity.kt
            │   └── ui/screens/
            │       ├── HomeScreen.kt   # SOS button + status
            │       ├── MapScreen.kt    # MapLibre offline map
            │       └── ConfigScreen.kt # Settings
            └── AndroidManifest.xml
```

---

## 🚀 Developer Setup

### Prerequisites
- **Node.js** ≥ 20 + npm
- **Android Studio** Meerkat+ (for Kotlin 2.x)
- **Java** 17 (set `JAVA_HOME`)
- A **Supabase** project (free tier is sufficient)

### 1. Clone & Root Setup

```bash
git clone https://github.com/your-org/nextGPStracking.git
cd nextGPStracking
```

### 2. Backend Setup

```bash
cd backend
cp .env.local.example .env.local
# Edit .env.local with your Supabase credentials

npm install
npm run dev          # Starts on http://localhost:3000
```

**Environment variables required in `.env.local`:**
```
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
DEVICE_API_SECRET=your-shared-device-secret
```

### 3. Database Setup

Run the SQL schema in your Supabase project's **SQL Editor**:
```bash
# Copy contents of backend/supabase-schema.sql
# Paste and run in https://app.supabase.com/project/YOUR_PROJECT/sql
```

### 4. Mobile Setup

```bash
cd mobile
# Open in Android Studio or build via CLI:
./gradlew :app:assembleDebug
```

Configure the backend URL in the app's `ConfigScreen` or set it via `local.properties`:
```properties
backend.base.url=https://your-deployment.vercel.app
```

### 5. Deploy Backend to Vercel

```bash
cd backend
npx vercel --prod
# Set env vars in Vercel dashboard → Settings → Environment Variables
```

---

## 🔑 Key Features

| Feature | Implementation |
|---|---|
| **Offline-First Queue** | Room DB in KMP shared module, 100% local integrity |
| **3-Phase Sync** | SOS LIFO → Latest Point → FIFO history (batches of 25) |
| **Adaptive GPS Sampling** | Static: 45-60min cellular / Moving: 5-15min balanced / SOS: 15s pure GPS |
| **2-Second SOS Button** | LongPress with continuous haptic feedback, prevents pocket activation |
| **Auto-Restart on Boot** | `BootReceiver` reads `EncryptedSharedPreferences` |
| **Offline Maps** | MapLibre `.mbtiles` packages for agricultural regions |
| **Zero Auth Friction** | Device UUID in `EncryptedSharedPreferences`, no login required on tracker |
| **PostGIS Ready** | Geofencing support enabled via `CREATE EXTENSION postgis` |

---

## 🛡️ Security Model

- **Tracker devices**: No login. One-time registration generates a UUID stored in `EncryptedSharedPreferences`. Every HTTP request signs the `Authorization: Bearer <DEVICE_API_SECRET>` header.
- **Web Dashboard** (Phase 3): Supabase Auth Magic Links (email-based, no passwords).
- **Row Level Security**: All Supabase tables enforce RLS — only authenticated users can read/write.
- **Service Role Key**: Never exposed to client-side code. Only used in Next.js API routes (server-side).

---

## 📱 Screens

| Screen | Description |
|---|---|
| **Home** | Giant SOS button (40% screen), tracking toggle, connectivity badge, battery + GPS precision tiles |
| **Map** | MapLibre native view, historical polyline with age gradient, SOS pulsing marker, time filters |
| **Config** | Tracking interval selector, data/motion policies, emergency contact with native dialer fallback |

---

## 🔋 Battery Optimization Strategy

- **Hardware FIFO Batching**: GPS chip retains coordinates internally, CPU wakes in bulk
- **Accuracy Filtering**: Locations > 80m uncertainty are flagged; replaced if better fix arrives within 10s
- **Doze Mode Mitigation**: Foreground Service with visible notification prevents system termination
- **Target**: < 5-8% battery drain over 12 hours continuous operation

---

## 📋 Development Roadmap

See [`.ai/plan.md`](./.ai/plan.md) for the detailed component status tracker.

**Phase 1** (Weeks 1-2): Core infrastructure ✅  
**Phase 2** (Weeks 3-4): Offline resilience + energy optimization ✅  
**Phase 3** (Weeks 5-6): Crash detection, last-gasp alert, iOS port ⬜

---

## 📄 License

Private — Family use only. Not for public distribution.
