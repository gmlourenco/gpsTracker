CREATE OR REPLACE FUNCTION public.get_positions_with_history(p_history INT DEFAULT 0)
RETURNS TABLE (
    device_id TEXT,
    label TEXT,
    marker_color TEXT,
    app_version TEXT,
    tracking_interval_ms INT,
    tracking_distance_m INT,
    default_map_type TEXT,
    accident_sensor_sensitivity TEXT,
    config_updated_at BIGINT,
    lat REAL,
    lng REAL,
    accuracy REAL,
    speed REAL,
    heading REAL,
    battery_level INT,
    battery_charging BOOLEAN,
    emergency_state BOOLEAN,
    network_type TEXT,
    created_at TIMESTAMPTZ,
    row_num BIGINT
) AS $$
  SELECT 
    d.id AS device_id, 
    d.label, 
    d.marker_color,
    d.app_version,
    d.tracking_interval_ms,
    d.tracking_distance_m,
    d.default_map_type,
    d.accident_sensor_sensitivity,
    d.config_updated_at,
    l.lat, 
    l.lng, 
    l.accuracy, 
    l.speed, 
    l.heading,
    l.battery_level,
    l.battery_charging,
    l.emergency_state, 
    l.network_type,
    l.created_at,
    l.row_num
  FROM public.devices d
  CROSS JOIN LATERAL (
      SELECT *, ROW_NUMBER() OVER (ORDER BY created_at DESC) AS row_num
      FROM public.locations
      WHERE device_id = d.id
      ORDER BY created_at DESC
      LIMIT GREATEST(p_history, 0) + 1
  ) l
  ORDER BY d.id, l.created_at DESC;
$$ LANGUAGE sql STABLE;
