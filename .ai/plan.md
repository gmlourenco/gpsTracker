# Segurança Rural – GPS Tracker & SOS: Development Status

> Last updated: 2026-05-17 | Status: 🟡 In Progress

---

## Legend
| Symbol | Meaning |
|---|---|
| ✅ | Done |
| 🟡 | In Progress |
| ⬜ | Pending |
| ❌ | Blocked |

---

## Phase 1 – Infraestrutura Base (Weeks 1-2)

| ID | Component | Status | Notes |
|---|---|---|---|
| 1.1 | Monorepo structure | ✅ | `.ai/`, `backend/`, `mobile/` created |
| 1.2 | Root README | ✅ | Architecture diagram included |
| 1.3 | CI Workflow (GitHub Actions) | ✅ | Lint + build check |
| 1.4 | Supabase DDL schema | ✅ | PostGIS, RLS, indexes |
| 1.5 | API `/api/location` | ✅ | Payload validation + upsert |
| 1.6 | API `/api/emergency` | ✅ | High-priority SOS handler |
| 1.7 | API `/api/devices` | ✅ | Latest state + join |
| 1.8 | Vercel deployment config | ✅ | Region: lhr1 |
| 1.9 | HomeScreen (Compose) | ✅ | SOS button + status tiles |
| 1.10 | LocationForegroundService | ✅ | FusedLocationProviderClient |

## Phase 2 – Offline Resilience & Energy Optimization (Weeks 3-4)

| ID | Component | Status | Notes |
|---|---|---|---|
| 2.1 | Room Database (shared KMP) | ✅ | TelemetryRecord, DAO |
| 2.2 | SyncEngine (3-phase flush) | ✅ | LIFO SOS → Latest → FIFO batches |
| 2.3 | WorkManager SyncWorker | ✅ | Network-constrained |
| 2.4 | Adaptive sampling algorithm | ✅ | Static/Moving/SOS intervals |
| 2.5 | MapLibre integration (Android) | ✅ | Offline .mbtiles support |
| 2.6 | BootReceiver | ✅ | Auto-restart on reboot |
| 2.7 | Heartbeat mechanism | ✅ | 30-min fallback packet |

## Phase 3 – Advanced Security Features (Weeks 5-6)

| ID | Component | Status | Notes |
|---|---|---|---|
| 3.1 | Crash Detection (accelerometer) | ⬜ | Gyroscope + accel vectors |
| 3.2 | Low Battery Last Gasp alert | ⬜ | <10% battery priority TX |
| 3.3 | Web Dashboard (MapLibre) | ⬜ | Real-time map, route history |
| 3.4 | Magic Link Auth (Supabase) | ⬜ | Dashboard login |
| 3.5 | iOS KMP compilation | ⬜ | Placeholder actuals exist |
| 3.6 | Release APK build | ⬜ | Minify + sign |

---

## Component Status Detail

### Backend (`/backend`)
| File | Status |
|---|---|
| `supabase-schema.sql` | ✅ |
| `src/lib/supabase.ts` | ✅ |
| `src/types/telemetry.ts` | ✅ |
| `src/app/api/location/route.ts` | ✅ |
| `src/app/api/emergency/route.ts` | ✅ |
| `src/app/api/devices/route.ts` | ✅ |
| `vercel.json` | ✅ |
| `.env.local.example` | ✅ |

### Shared KMP Module (`/mobile/shared`)
| File | Status |
|---|---|
| `build.gradle.kts` | ✅ |
| `TelemetryRecord.kt` | ✅ |
| `AppDatabase.kt` | ✅ |
| `TelemetryDao.kt` | ✅ |
| `SyncEngine.kt` | ✅ |
| `DatabaseDriverFactory.kt` (Android) | ✅ |
| `DatabaseDriverFactory.kt` (iOS stub) | ✅ |

### Android App (`/mobile/app`)
| File | Status |
|---|---|
| `AndroidManifest.xml` | ✅ |
| `LocationForegroundService.kt` | ✅ |
| `BootReceiver.kt` | ✅ |
| `SyncWorker.kt` | ✅ |
| `MainActivity.kt` | ✅ |
| `HomeScreen.kt` | ✅ |
| `MapScreen.kt` | ✅ |
| `ConfigScreen.kt` | ✅ |
| `ui/theme/Color.kt` | ✅ |

---

## Known Constraints & Architecture Decisions
- `minSdk = 26` (Android 8.0) — minimum for reliable foreground services + FusedLocation
- Package: `com.seguranca.rural`
- Supabase credentials stored in `.env.local` (never committed)
- Device UUID stored in `EncryptedSharedPreferences` — no interactive login on tracker devices
- iOS `actual` implementations are `TODO()` stubs — iOS port deferred to Phase 3
