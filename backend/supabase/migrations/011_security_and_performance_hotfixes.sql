-- 011_security_and_performance_hotfixes.sql
-- Fixes critical issues identified in the architectural audit (Phase 1)

-- 1. Performance: Add missing index on farm_members(user_id) to prevent O(N) table scans on RLS
CREATE INDEX IF NOT EXISTS idx_farm_members_user ON public.farm_members(user_id);

-- 2. Security: Fix farm_invites RLS policies that were dangerously open
DROP POLICY IF EXISTS "Anyone can insert an invite" ON public.farm_invites;
DROP POLICY IF EXISTS "Anyone can read invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can read invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can create invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can delete invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Users can view invites they created" ON public.farm_invites;

-- Re-create secure policies for farm_invites
CREATE POLICY "Farm owners and admins can read invites"
  ON public.farm_invites FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );

CREATE POLICY "Farm owners and admins can create invites"
  ON public.farm_invites FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );

CREATE POLICY "Farm owners and admins can delete invites"
  ON public.farm_invites FOR DELETE
  USING (
    EXISTS (
      SELECT 1 FROM public.farm_members
      WHERE farm_members.farm_id = farm_invites.farm_id
        AND farm_members.user_id = auth.uid()
        AND farm_members.role IN ('owner', 'admin')
    )
  );
