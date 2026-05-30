-- Add map marker color to devices (synced from mobile app / dashboard)
ALTER TABLE public.devices
  ADD COLUMN IF NOT EXISTS marker_color VARCHAR(7) NOT NULL DEFAULT '#16A34A';

COMMENT ON COLUMN public.devices.marker_color IS 'Hex color for map marker (#RRGGBB)';
