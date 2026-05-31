-- ============================================================
-- Segurança Rural – GPS Tracker & SOS
-- Supabase PostgreSQL Schema (DDL)
-- Run this in: Supabase Dashboard → SQL Editor → New Query
-- ============================================================

-- ============================================================
-- 1. CLEAN BREAK: REMOVE ALL EXISTING OBJECTS
-- ============================================================
-- Drop tables with CASCADE to clean up all constraints, triggers, and views
DROP TABLE IF EXISTS public.locations CASCADE;
DROP TABLE IF EXISTS public.devices CASCADE;

-- Drop PostGIS extension if it exists in public (to move it to extensions schema)
DROP EXTENSION IF EXISTS postgis CASCADE;

-- ============================================================
-- 2. SETUP SCHEMA AND EXTENSIONS
-- ============================================================
-- Ensure the extensions schema exists (pre-configured in Supabase)
CREATE SCHEMA IF NOT EXISTS extensions;

-- Reinstall PostGIS cleanly inside the extensions schema.
-- This keeps public.spatial_ref_sys out of the public schema,
-- completely resolving the PostgREST RLS security warning.
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA extensions;

-- ============================================================
-- 3. RECREATE TABLES (IDENTIFIER CHANGED TO TEXT)
-- ============================================================
CREATE TABLE public.devices (
    id              TEXT          PRIMARY KEY,                     -- Replaces UUID with raw ANDROID_ID (TEXT)
    label           VARCHAR(50)   NOT NULL,                        -- Human-readable name (e.g., "Trator-Pai")
    marker_color    VARCHAR(7)    NOT NULL DEFAULT '#16A34A',      -- Map marker hex color (#RRGGBB)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ,                                   -- Updated on every telemetry ingest
    tracking_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    app_version     VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    fcm_token       TEXT                                           -- FCM push token
);

COMMENT ON TABLE public.devices IS 'Registered GPS tracker devices identified by stable serialNumber (ANDROID_ID).';

CREATE TABLE public.locations (
    id              BIGSERIAL     PRIMARY KEY,
    device_id       TEXT          NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    lat             NUMERIC(9,6)  NOT NULL,                        -- Latitude
    lng             NUMERIC(9,6)  NOT NULL,                        -- Longitude
    accuracy        REAL          NOT NULL,                        -- GPS accuracy radius in metres
    speed           REAL          NOT NULL DEFAULT 0,             -- Speed in km/h
    heading         REAL          NOT NULL DEFAULT 0,             -- Compass bearing 0–360°
    battery_level   SMALLINT      NOT NULL,                        -- Device battery 0–100%
    battery_charging BOOLEAN      NOT NULL DEFAULT FALSE,
    emergency_state BOOLEAN       NOT NULL DEFAULT FALSE,          -- TRUE when SOS is active
    network_type    VARCHAR(10)   NOT NULL DEFAULT 'UNKNOWN',      -- "WIFI", "4G", etc.
    tracking_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    app_version     VARCHAR(20)   NOT NULL DEFAULT '1.0.0',
    created_at      TIMESTAMPTZ   NOT NULL,                        -- Timestamp from the device
    synced_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()           -- Timestamp when received by server
);

COMMENT ON TABLE public.locations IS 'Telemetry records and locations sent by registered tracker devices.';

-- ============================================================
-- 4. RECREATE INDEXES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_locations_device_created
    ON public.locations(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_locations_emergency
    ON public.locations(emergency_state)
    WHERE emergency_state = TRUE;

CREATE INDEX IF NOT EXISTS idx_locations_device_latest
    ON public.locations(device_id, synced_at DESC);

-- ============================================================
-- 5. ROW LEVEL SECURITY (RLS)
-- ============================================================
ALTER TABLE public.devices  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 6. CREATE SECURITY POLICIES (AUTHENTICATED WEB USERS)
-- ============================================================
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
-- 7. RECREATE REALTIME PUBLICATION
-- ============================================================
-- Ensure the locations table receives realtime updates for live mapping
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.locations;
    END IF;
END $$;
