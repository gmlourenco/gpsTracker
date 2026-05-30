-- ============================================================
-- Segurança Rural – GPS Tracker & SOS
-- Supabase PostgreSQL Schema (DDL)
-- Run this in: Supabase Dashboard → SQL Editor → New Query
-- ============================================================

-- Enable geospatial extension (PostGIS) for future Geofencing queries
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- TABLE: devices
-- Registers all family tracker devices.
-- One row per physical device; identified by UUID generated on first setup.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.devices (
    id              UUID          PRIMARY KEY,
    label           VARCHAR(50)   NOT NULL,                        -- Human-readable name (e.g., "Trator-Pai")
    marker_color    VARCHAR(7)    NOT NULL DEFAULT '#16A34A',      -- Map marker hex color (#RRGGBB)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ,                                   -- Updated on every telemetry ingest
    tracking_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    app_version     VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    fcm_token       TEXT                                           -- Firebase Cloud Messaging push token (nullable — registered on first app launch)
);

-- Migration for existing deployments:
-- ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS fcm_token TEXT;


-- ============================================================
-- TABLE: locations
-- Stores every telemetry record transmitted by tracker devices.
-- Cascade-deletes all locations if a device is removed.
-- ============================================================
CREATE TABLE IF NOT EXISTS public.locations (
    id              BIGSERIAL     PRIMARY KEY,
    device_id       UUID          NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    lat             NUMERIC(9,6)  NOT NULL,                        -- Latitude  (e.g., 39.824167)
    lng             NUMERIC(9,6)  NOT NULL,                        -- Longitude (e.g., -7.493056)
    accuracy        REAL          NOT NULL,                        -- GPS accuracy radius in metres
    speed           REAL          NOT NULL DEFAULT 0,             -- Speed in km/h
    heading         REAL          NOT NULL DEFAULT 0,             -- Compass bearing 0–360°
    battery_level   SMALLINT      NOT NULL,                        -- Device battery 0–100%
    battery_charging BOOLEAN      NOT NULL DEFAULT FALSE,
    emergency_state BOOLEAN       NOT NULL DEFAULT FALSE,          -- TRUE when SOS is active
    network_type    VARCHAR(10)   NOT NULL DEFAULT 'UNKNOWN',      -- "WIFI", "4G", "3G", "2G", etc.
    tracking_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    app_version     VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    created_at      TIMESTAMPTZ   NOT NULL,                        -- Timestamp from the device (original)
    synced_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()           -- Timestamp when received by server
);

-- ============================================================
-- INDEXES — optimised for Dashboard queries
-- ============================================================

-- Primary dashboard query: get all locations for a device ordered by time
CREATE INDEX IF NOT EXISTS idx_locations_device_created
    ON public.locations(device_id, created_at DESC);

-- Emergency filter: fast lookup of all active SOS records across all devices
CREATE INDEX IF NOT EXISTS idx_locations_emergency
    ON public.locations(emergency_state)
    WHERE emergency_state = TRUE;

-- Latest known position per device (used by GET /api/devices)
CREATE INDEX IF NOT EXISTS idx_locations_device_latest
    ON public.locations(device_id, synced_at DESC);

-- ============================================================
-- ROW LEVEL SECURITY (RLS)
-- All data is private — only authenticated family members can read/write.
-- Tracker devices write via the service_role key in Next.js API routes.
-- ============================================================

ALTER TABLE public.devices  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

-- Policy: authenticated users (web dashboard) have full access
CREATE POLICY "Authenticated users: full access to devices"
    ON public.devices
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Authenticated users: full access to locations"
    ON public.locations
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

-- ============================================================
-- REALTIME
-- Enable Supabase Realtime for the locations table so the
-- future web dashboard can subscribe to live position updates.
-- ============================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.locations;
