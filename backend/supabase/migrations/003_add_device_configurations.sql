-- Create device configurations table
CREATE TABLE IF NOT EXISTS public.device_configurations (
    id TEXT PRIMARY KEY REFERENCES public.devices(id) ON DELETE CASCADE, -- Matches device serialNumber (ANDROID_ID)
    device_label TEXT NOT NULL DEFAULT 'Dispositivo',
    marker_color VARCHAR(7) NOT NULL DEFAULT '#16A34A',
    emergency_contact TEXT,
    sync_on_mobile_data BOOLEAN NOT NULL DEFAULT TRUE,
    tracking_interval_ms BIGINT NOT NULL DEFAULT 60000, -- Default 1 minute
    tracking_distance_m REAL NOT NULL DEFAULT 200,      -- Default 200 meters
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE public.device_configurations IS 'Configurations persisted for each device, allowing settings restoration after app reinstall.';

-- Enable Row Level Security (RLS)
ALTER TABLE public.device_configurations ENABLE ROW LEVEL SECURITY;

-- Policy allowing full access for authenticated clients/API
DROP POLICY IF EXISTS "Authenticated users: full access to device_configurations" ON public.device_configurations;
CREATE POLICY "Authenticated users: full access to device_configurations"
    ON public.device_configurations
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);
