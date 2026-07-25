-- Replace obsolete map types with SATELLITE
UPDATE public.devices 
SET default_map_type = 'SATELLITE'
WHERE default_map_type NOT IN ('SATELLITE', 'DARK', 'LIGHT');
