-- 017_fix_farm_members_rls.sql
-- Fixes RLS policies on farm_members, farms, and devices to eliminate recursion and allow co-member visibility.

-- 1. Create a SECURITY DEFINER helper function to safely get the current user's farm IDs without RLS recursion
CREATE OR REPLACE FUNCTION public.get_user_farm_ids()
RETURNS SETOF uuid
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
    SELECT farm_id FROM public.farm_members WHERE user_id = auth.uid();
$$;

-- 2. Ensure RLS is enabled
ALTER TABLE public.farm_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.farms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

-- 3. Fix farm_members SELECT policy (allow viewing own membership and any co-members in shared farms)
DROP POLICY IF EXISTS "Users can view their memberships" ON public.farm_members;
DROP POLICY IF EXISTS "Farm members: view co-members" ON public.farm_members;

CREATE POLICY "Farm members: view co-members"
    ON public.farm_members FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (SELECT public.get_user_farm_ids())
    );

-- 4. Fix farms SELECT policy
DROP POLICY IF EXISTS "Farm members can view their farms" ON public.farms;

CREATE POLICY "Farm members can view their farms"
    ON public.farms FOR SELECT TO authenticated
    USING (
        id IN (SELECT public.get_user_farm_ids())
    );

-- 5. Fix devices SELECT policy using the fast helper function
DROP POLICY IF EXISTS "Farm members: view shared devices" ON public.devices;

CREATE POLICY "Farm members: view shared devices"
    ON public.devices FOR SELECT TO authenticated
    USING (
        user_id = auth.uid() OR
        farm_id IN (SELECT public.get_user_farm_ids()) OR
        user_id IN (
            SELECT fm.user_id FROM public.farm_members fm WHERE fm.farm_id IN (SELECT public.get_user_farm_ids())
        ) OR
        (
            (user_id IS NULL AND farm_id IS NULL) AND
            NOT EXISTS (SELECT 1 FROM public.get_user_farm_ids())
        )
    );
