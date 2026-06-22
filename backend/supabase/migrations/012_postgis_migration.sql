-- 012_postgis_migration.sql
-- Upgrades the database to support PostGIS spatial geometry for advanced geofencing

-- 1. Enable PostGIS extension (requires Supabase to have extensions enabled, which is default)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Add geometry column to locations table (WGS 84 / EPSG:4326)
ALTER TABLE public.locations 
ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326);

-- 3. Backfill existing data
UPDATE public.locations 
SET geom = extensions.ST_SetSRID(extensions.ST_MakePoint(lng, lat), 4326) 
WHERE geom IS NULL 
  AND lat IS NOT NULL 
  AND lng IS NOT NULL;

-- 4. Create spatial index for blazing-fast geospatial queries
CREATE INDEX IF NOT EXISTS idx_locations_geom ON public.locations USING GIST(geom);

-- 5. Create trigger function to automatically populate geom on insert/update
CREATE OR REPLACE FUNCTION public.sync_location_geom()
RETURNS trigger AS $$
BEGIN
  IF NEW.lat IS NOT NULL AND NEW.lng IS NOT NULL THEN
    NEW.geom := extensions.ST_SetSRID(extensions.ST_MakePoint(NEW.lng, NEW.lat), 4326);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 6. Attach trigger to locations table
DROP TRIGGER IF EXISTS trigger_sync_location_geom ON public.locations;
CREATE TRIGGER trigger_sync_location_geom
BEFORE INSERT OR UPDATE OF lat, lng
ON public.locations
FOR EACH ROW
EXECUTE FUNCTION public.sync_location_geom();
