-- 009_device_user_mapping_and_roles.sql
-- 1. Add user_id to devices
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'devices' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE public.devices ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;
        CREATE INDEX idx_devices_user ON public.devices(user_id);
    END IF;
END $$;

-- 2. Replace RLS Policies on devices
DROP POLICY IF EXISTS "Farm members: view their devices" ON public.devices;
DROP POLICY IF EXISTS "Farm admins: manage devices" ON public.devices;

-- Users can view devices that belong to them, OR devices that belong to other users in the same farm,
-- OR devices that have no user_id (legacy devices fallback so they aren't hidden from older app versions).
CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        user_id IN (
            SELECT fm2.user_id 
            FROM public.farm_members fm1
            JOIN public.farm_members fm2 ON fm1.farm_id = fm2.farm_id
            WHERE fm1.user_id = auth.uid()
        ) OR
        user_id IS NULL -- Legacy fallback
    );

-- Only device owner can manage their own device
CREATE POLICY "Users: manage their own devices"
    ON public.devices FOR ALL TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- 3. Replace RLS Policies on locations
DROP POLICY IF EXISTS "Farm members: view device locations" ON public.locations;

CREATE POLICY "Farm members: view shared device locations"
    ON public.locations FOR SELECT TO authenticated
    USING (
        device_id IN (
            SELECT id FROM public.devices
            WHERE user_id = auth.uid() OR
                  user_id IN (
                      SELECT fm2.user_id 
                      FROM public.farm_members fm1
                      JOIN public.farm_members fm2 ON fm1.farm_id = fm2.farm_id
                      WHERE fm1.user_id = auth.uid()
                  ) OR
                  user_id IS NULL
        )
    );
