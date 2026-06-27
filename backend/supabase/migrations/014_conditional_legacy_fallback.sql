-- 014_conditional_legacy_fallback.sql
-- 1. Drop old policy
DROP POLICY IF EXISTS "Farm members: view shared devices" ON public.devices;

-- 2. Create conditional policy for devices:
-- Users can view devices that belong to them, OR devices linked to any farms they are members of.
-- If the user is NOT in any family, they can also view legacy unassigned devices.
CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (
            SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid()
        ) OR
        (
            (user_id IS NULL AND farm_id IS NULL) AND
            NOT EXISTS (SELECT 1 FROM public.farm_members WHERE user_id = auth.uid())
        )
    );
