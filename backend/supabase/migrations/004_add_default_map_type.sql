-- Add default_map_type and accident_sensor_sensitivity to device_configurations
ALTER TABLE public.device_configurations 
ADD COLUMN IF NOT EXISTS default_map_type TEXT NOT NULL DEFAULT 'SATELLITE',
ADD COLUMN IF NOT EXISTS accident_sensor_sensitivity TEXT NOT NULL DEFAULT 'medium';
