-- 016_allow_co_member_devices.sql
-- Allow members to see devices of any user who shares at least one family with them.

DROP POLICY IF EXISTS "Farm members: view shared devices" ON public.devices;

CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (
            SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid()
        ) OR
        user_id IN (
            SELECT fm2.user_id FROM public.farm_members fm1
            JOIN public.farm_members fm2 ON fm1.farm_id = fm2.farm_id
            WHERE fm1.user_id = auth.uid()
        ) OR
        (
            (user_id IS NULL AND farm_id IS NULL) AND
            NOT EXISTS (SELECT 1 FROM public.farm_members WHERE user_id = auth.uid())
        )
    );
