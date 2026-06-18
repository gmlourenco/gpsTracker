-- Drop the duplicated device_configurations table if it was created
DROP TABLE IF EXISTS public.device_configurations CASCADE;

-- Add configurations columns directly to public.devices
ALTER TABLE public.devices 
ADD COLUMN IF NOT EXISTS tracking_interval_ms BIGINT NOT NULL DEFAULT 60000,
ADD COLUMN IF NOT EXISTS tracking_distance_m REAL NOT NULL DEFAULT 200,
ADD COLUMN IF NOT EXISTS default_map_type TEXT NOT NULL DEFAULT 'SATELLITE',
ADD COLUMN IF NOT EXISTS accident_sensor_sensitivity TEXT NOT NULL DEFAULT 'medium',
ADD COLUMN IF NOT EXISTS config_updated_at BIGINT NOT NULL DEFAULT -1;
