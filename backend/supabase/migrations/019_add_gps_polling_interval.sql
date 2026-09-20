-- Migration 019: Add GPS Polling Interval (y) config to devices

ALTER TABLE devices 
ADD COLUMN gps_polling_interval_ms BIGINT DEFAULT 60000;
