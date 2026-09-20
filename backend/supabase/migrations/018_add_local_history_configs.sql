-- Add new configuration columns to devices table
ALTER TABLE public.devices 
ADD COLUMN IF NOT EXISTS local_history_days INT DEFAULT 14,
ADD COLUMN IF NOT EXISTS local_history_max_gb NUMERIC DEFAULT 1.0;
