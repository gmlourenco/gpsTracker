-- 010_fix_rls_policies.sql
-- 1. Drop old policies
DROP POLICY IF EXISTS "Farm members: view shared devices" ON public.devices;
DROP POLICY IF EXISTS "Farm members: view shared device locations" ON public.locations;

-- 2. Create corrected policy for devices:
-- Users can view devices that belong to them, OR devices linked to any farms they are members of,
-- OR legacy/unassigned devices (where farm_id is NULL and user_id is NULL) to prevent breaking production.
CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (
            SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid()
        ) OR
        (user_id IS NULL AND farm_id IS NULL) -- Safe legacy fallback
    );

-- 3. Create corrected policy for locations:
-- Users can view locations for any devices they are allowed to see.
CREATE POLICY "Farm members: view shared device locations"
    ON public.locations FOR SELECT TO authenticated
    USING (
        device_id IN (
            SELECT id FROM public.devices
        )
    );
