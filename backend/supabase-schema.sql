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
    fcm_token       TEXT,                                          -- FCM push token
    user_id         UUID          REFERENCES auth.users(id) ON DELETE SET NULL
);

COMMENT ON TABLE public.devices IS 'Registered GPS tracker devices identified by stable serialNumber (ANDROID_ID).';

CREATE TABLE public.locations (
    id              BIGSERIAL     PRIMARY KEY,
    device_id       TEXT          NOT NULL REFERENCES public.devices(id) ON DELETE CASCADE,
    lat             NUMERIC(9,6)  NOT NULL,                        -- Latitude
    lng             NUMERIC(9,6)  NOT NULL,                        -- Longitude
    geom            geometry(Point, 4326),                         -- PostGIS Spatial Column
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
    synced_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),           -- Timestamp when received by server
    UNIQUE (device_id, created_at)
);

COMMENT ON TABLE public.locations IS 'Telemetry records and locations sent by registered tracker devices.';

-- ============================================================
-- 3b. POSTGIS AUTO-SYNC TRIGGER
-- ============================================================
CREATE OR REPLACE FUNCTION public.sync_location_geom()
RETURNS trigger AS $$
BEGIN
  IF NEW.lat IS NOT NULL AND NEW.lng IS NOT NULL THEN
    -- PostGIS functions must be prefixed with 'extensions.' if installed in extensions schema
    NEW.geom := extensions.ST_SetSRID(extensions.ST_MakePoint(NEW.lng, NEW.lat), 4326);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sync_location_geom
BEFORE INSERT OR UPDATE OF lat, lng
ON public.locations
FOR EACH ROW
EXECUTE FUNCTION public.sync_location_geom();

-- ============================================================
-- 4. RECREATE INDEXES
-- ============================================================
-- Covering index: avoids heap lookups for "latest position per device" queries
CREATE INDEX IF NOT EXISTS idx_locations_device_created_covering
    ON public.locations(device_id, created_at DESC)
    INCLUDE (lat, lng, accuracy, speed, heading, battery_level, emergency_state);

CREATE INDEX IF NOT EXISTS idx_locations_emergency
    ON public.locations(emergency_state)
    WHERE emergency_state = TRUE;

CREATE INDEX IF NOT EXISTS idx_locations_device_latest
    ON public.locations(device_id, synced_at DESC);

CREATE INDEX IF NOT EXISTS idx_locations_geom 
    ON public.locations USING GIST(geom);

-- ============================================================
-- 4b. OPTIMIZED QUERY: Latest position per device
-- ============================================================
-- Replaces the N+1 anti-pattern of fetching ALL locations then
-- picking the latest per device in application code.
CREATE OR REPLACE FUNCTION public.get_latest_positions()
RETURNS TABLE (
    device_id       TEXT,
    device_label    VARCHAR(50),
    marker_color    VARCHAR(7),
    device_created_at TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ,
    tracking_enabled BOOLEAN,
    app_version     VARCHAR(20),
    loc_id          BIGINT,
    lat             NUMERIC(9,6),
    lng             NUMERIC(9,6),
    accuracy        REAL,
    speed           REAL,
    heading         REAL,
    battery_level   SMALLINT,
    battery_charging BOOLEAN,
    emergency_state BOOLEAN,
    network_type    VARCHAR(10),
    loc_tracking_enabled BOOLEAN,
    loc_app_version VARCHAR(20),
    created_at      TIMESTAMPTZ,
    synced_at       TIMESTAMPTZ
) AS $$
    SELECT DISTINCT ON (l.device_id)
        l.device_id,
        d.label           AS device_label,
        d.marker_color,
        d.created_at      AS device_created_at,
        d.last_seen_at,
        d.tracking_enabled,
        d.app_version,
        l.id              AS loc_id,
        l.lat,
        l.lng,
        l.accuracy,
        l.speed,
        l.heading,
        l.battery_level,
        l.battery_charging,
        l.emergency_state,
        l.network_type,
        l.tracking_enabled AS loc_tracking_enabled,
        l.app_version     AS loc_app_version,
        l.created_at,
        l.synced_at
    FROM public.locations l
    JOIN public.devices d ON l.device_id = d.id
    ORDER BY l.device_id, l.created_at DESC;
$$ LANGUAGE sql STABLE;

COMMENT ON FUNCTION public.get_latest_positions() IS
    'Returns the most recent location per device using DISTINCT ON. O(n_devices) instead of O(n_locations).';

-- ============================================================
-- 5. ROW LEVEL SECURITY (RLS)
-- ============================================================
ALTER TABLE public.devices  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 6. MULTI-TENANCY: Farm-based access control
-- ============================================================
-- These tables enable per-farm device isolation. Until populated,
-- the fallback policies below grant full access to authenticated users.

CREATE TABLE IF NOT EXISTS public.farms (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.farm_members (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id     UUID        NOT NULL REFERENCES public.farms(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role        TEXT        NOT NULL DEFAULT 'viewer'
                            CHECK (role IN ('owner', 'admin', 'viewer')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (farm_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_farm_members_user ON public.farm_members(user_id);

ALTER TABLE public.farms        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.farm_members  ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.farm_invites (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  farm_id UUID NOT NULL REFERENCES public.farms(id) ON DELETE CASCADE,
  code TEXT NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by UUID NOT NULL REFERENCES auth.users(id)
);
ALTER TABLE public.farm_invites ENABLE ROW LEVEL SECURITY;

-- Link devices to farms (nullable for backwards compatibility during migration)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'farm_id'
    ) THEN
        ALTER TABLE public.devices ADD COLUMN farm_id UUID REFERENCES public.farms(id);
        CREATE INDEX idx_devices_farm ON public.devices(farm_id);
    END IF;
END $$;

-- ============================================================
-- 7. CREATE SECURITY POLICIES
-- ============================================================
-- Phase 1: Authenticated users get full access (current behaviour preserved).
-- When farms are populated, these policies should be replaced with
-- farm-scoped variants. The policies below are intentionally broad
-- so the migration does NOT break the existing single-family setup.

-- Devices: Farm members can view devices in their farm(s) or their own
CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        user_id IN (
            SELECT fm2.user_id 
            FROM public.farm_members fm1
            JOIN public.farm_members fm2 ON fm1.farm_id = fm2.farm_id
            WHERE fm1.user_id = auth.uid()
        ) OR
        user_id IS NULL -- Legacy fallback
    );

-- Devices: Only device owner can manage their own device (or claim legacy devices)
CREATE POLICY "Users: manage their own devices"
    ON public.devices FOR ALL TO authenticated
    USING (user_id = auth.uid() OR user_id IS NULL)
    WITH CHECK (user_id = auth.uid());

-- Locations: Farm members can view locations for shared devices
CREATE POLICY "Farm members: view shared device locations"
    ON public.locations FOR SELECT TO authenticated
    USING (
        device_id IN (
            SELECT id FROM public.devices
            WHERE user_id = auth.uid() OR
                  user_id IN (
                      SELECT fm2.user_id 
                      FROM public.farm_members fm1
                      JOIN public.farm_members fm2 ON fm1.farm_id = fm2.farm_id
                      WHERE fm1.user_id = auth.uid()
                  ) OR
                  user_id IS NULL
        )
    );

-- Locations: Service role can insert locations (via API routes)
CREATE POLICY "Service role: insert locations"
    ON public.locations FOR INSERT TO service_role
    WITH CHECK (true);

-- Locations: Authenticated users can insert locations for their own devices
CREATE POLICY "Users: insert locations for their devices"
    ON public.locations FOR INSERT TO authenticated
    WITH CHECK (device_id IN (
        SELECT id FROM public.devices WHERE user_id = auth.uid()
    ));

-- Farms: members can see their own farms
CREATE POLICY "Farm members can view their farms"
    ON public.farms FOR SELECT TO authenticated
    USING (
        id IN (SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid())
    );

CREATE POLICY "Farm owners can manage their farms"
    ON public.farms FOR ALL TO authenticated
    USING (
        id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role = 'owner'
        )
    )
    WITH CHECK (
        id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role = 'owner'
        )
    );

-- Farm members: users can see their own memberships
CREATE POLICY "Users can view their memberships"
    ON public.farm_members FOR SELECT TO authenticated
    USING (user_id = auth.uid());

CREATE POLICY "Farm owners can manage memberships"
    ON public.farm_members FOR ALL TO authenticated
    USING (
        farm_id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role = 'owner'
        )
    )
    WITH CHECK (
        farm_id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role = 'owner'
        )
    );

CREATE POLICY "Farm owners and admins can read invites"
  ON public.farm_invites FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );

CREATE POLICY "Farm owners and admins can create invites"
  ON public.farm_invites FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );

CREATE POLICY "Farm owners and admins can delete invites"
  ON public.farm_invites FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );

-- RPC for bootstrapping farms with RLS bypass
CREATE OR REPLACE FUNCTION public.create_farm_with_owner(farm_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  new_farm_id UUID;
BEGIN
  INSERT INTO public.farms (name) VALUES (farm_name) RETURNING id INTO new_farm_id;
  INSERT INTO public.farm_members (farm_id, user_id, role) VALUES (new_farm_id, auth.uid(), 'owner');
  RETURN new_farm_id;
END;
$$;

-- ============================================================
-- 7b. POSTGIS SPATIAL COLUMN & GEOFENCING
-- ============================================================
-- Add a PostGIS GEOGRAPHY column for native spatial queries.
-- The existing lat/lng NUMERIC columns are kept for backward compatibility.
ALTER TABLE public.locations ADD COLUMN IF NOT EXISTS geom GEOGRAPHY(Point, 4326);

-- Backfill existing rows
UPDATE public.locations
SET geom = extensions.ST_SetSRID(extensions.ST_MakePoint(lng::double precision, lat::double precision), 4326)::geography
WHERE geom IS NULL;

-- Auto-populate geom on INSERT/UPDATE via trigger
CREATE OR REPLACE FUNCTION public.locations_set_geom()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geom := extensions.ST_SetSRID(
        extensions.ST_MakePoint(NEW.lng::double precision, NEW.lat::double precision), 4326
    )::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_locations_set_geom ON public.locations;
CREATE TRIGGER trg_locations_set_geom
    BEFORE INSERT OR UPDATE ON public.locations
    FOR EACH ROW EXECUTE FUNCTION public.locations_set_geom();

-- Spatial index for geofence and proximity queries
CREATE INDEX IF NOT EXISTS idx_locations_geom
    ON public.locations USING GIST (geom);

-- ============================================================
-- 7c. GEOFENCES TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS public.geofences (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id     UUID            NOT NULL REFERENCES public.farms(id) ON DELETE CASCADE,
    name        TEXT            NOT NULL,
    boundary    GEOGRAPHY(Polygon, 4326) NOT NULL,
    alert_on_exit BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_geofences_boundary
    ON public.geofences USING GIST (boundary);

ALTER TABLE public.geofences ENABLE ROW LEVEL SECURITY;

-- Geofence members inherit access through farm membership
CREATE POLICY "Farm members can view geofences"
    ON public.geofences FOR SELECT TO authenticated
    USING (
        farm_id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid()
        )
    );

CREATE POLICY "Farm admins can manage geofences"
    ON public.geofences FOR ALL TO authenticated
    USING (
        farm_id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role IN ('owner', 'admin')
        )
    )
    WITH CHECK (
        farm_id IN (
            SELECT farm_id FROM public.farm_members
            WHERE user_id = auth.uid() AND role IN ('owner', 'admin')
        )
    );

-- ============================================================
-- 7d. GEOFENCE BOUNDARY CHECK (SECURITY INVOKER)
-- ============================================================
CREATE OR REPLACE FUNCTION public.check_geofence_violation(
    p_device_id TEXT,
    p_lat NUMERIC,
    p_lng NUMERIC
)
RETURNS TABLE (
    geofence_id UUID,
    geofence_name TEXT,
    is_inside BOOLEAN
)
LANGUAGE plpgsql
SECURITY INVOKER
AS $$
BEGIN
    RETURN QUERY
    SELECT
        g.id      AS geofence_id,
        g.name    AS geofence_name,
        extensions.ST_Covers(
            g.boundary,
            extensions.ST_SetSRID(
                extensions.ST_MakePoint(p_lng::double precision, p_lat::double precision),
                4326
            )::geography
        ) AS is_inside
    FROM public.geofences g
    JOIN public.devices d ON d.farm_id = g.farm_id
    WHERE d.id = p_device_id
      AND g.alert_on_exit = true;
END;
$$;

COMMENT ON FUNCTION public.check_geofence_violation IS
    'Checks if a device location is inside/outside its farm geofences. Runs as INVOKER — RLS policies apply for direct RPC callers.';

-- ============================================================
-- 8. RECREATE REALTIME PUBLICATION
-- ============================================================
-- Ensure the locations table receives realtime updates for live mapping
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.locations;
    END IF;
END $$;
