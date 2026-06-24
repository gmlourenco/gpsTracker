-- ============================================================
-- 013 — Role System Upgrade: Additive Tags + Single-Use Invites
-- ============================================================

-- ── 1. Tag columns on farm_members ─────────────────────────────
ALTER TABLE public.farm_members
  ADD COLUMN IF NOT EXISTS is_creator BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_master_admin BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_authenticated BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS display_name TEXT;

-- ── 2. Migrate existing role data ──────────────────────────────
-- Owners become creator + master_admin + admin
UPDATE public.farm_members 
SET is_creator = TRUE, is_master_admin = TRUE, is_admin = TRUE
WHERE role = 'owner' AND is_creator = FALSE;

-- Admins keep admin tag
UPDATE public.farm_members 
SET is_admin = TRUE
WHERE role = 'admin' AND is_admin = FALSE;

-- All existing members are authenticated (they used Google login or anonymous with session)
UPDATE public.farm_members 
SET is_authenticated = TRUE
WHERE is_authenticated = FALSE;

-- ── 3. Constraints ─────────────────────────────────────────────
-- Only one creator per farm (enforced at DB level)
CREATE UNIQUE INDEX IF NOT EXISTS idx_farm_one_creator
  ON public.farm_members(farm_id) WHERE is_creator = TRUE;

-- ── 4. Upgrade farm_invites for single-use codes ───────────────
ALTER TABLE public.farm_invites
  ADD COLUMN IF NOT EXISTS max_uses INT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS uses_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Mark all existing invites as single-use and active
UPDATE public.farm_invites 
SET max_uses = 1, is_active = TRUE 
WHERE max_uses = 1 AND uses_count = 0;

-- ── 5. Atomic invite redemption function ───────────────────────
CREATE OR REPLACE FUNCTION public.redeem_invite(p_code TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  v_invite RECORD;
  v_farm_id UUID;
  v_is_anon BOOLEAN;
BEGIN
  -- Find and lock the invite row (prevents race conditions)
  SELECT * INTO v_invite
  FROM public.farm_invites
  WHERE code = UPPER(p_code)
    AND is_active = TRUE
    AND expires_at > NOW()
    AND uses_count < max_uses
  FOR UPDATE;

  IF v_invite IS NULL THEN
    RAISE EXCEPTION 'Código de convite inválido, expirado ou já utilizado.';
  END IF;

  v_farm_id := v_invite.farm_id;

  -- Increment usage and deactivate if fully used
  UPDATE public.farm_invites
  SET uses_count = uses_count + 1,
      is_active = CASE WHEN uses_count + 1 >= max_uses THEN FALSE ELSE TRUE END
  WHERE id = v_invite.id;

  -- Detect if user is anonymous
  SELECT COALESCE(is_anonymous, FALSE) INTO v_is_anon
  FROM auth.users WHERE id = auth.uid();

  -- Add user as basic member (no special tags)
  INSERT INTO public.farm_members (farm_id, user_id, role, is_authenticated, is_creator, is_master_admin, is_admin)
  VALUES (v_farm_id, auth.uid(), 'viewer', NOT v_is_anon, FALSE, FALSE, FALSE)
  ON CONFLICT (farm_id, user_id) DO NOTHING;

  RETURN v_farm_id;
END;
$$;

-- ── 6. Update create_farm_with_owner to set new tags ───────────
CREATE OR REPLACE FUNCTION public.create_farm_with_owner(farm_name TEXT)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
  new_farm_id UUID;
BEGIN
  INSERT INTO public.farms (name) VALUES (farm_name) RETURNING id INTO new_farm_id;
  INSERT INTO public.farm_members (farm_id, user_id, role, is_creator, is_master_admin, is_admin, is_authenticated)
  VALUES (new_farm_id, auth.uid(), 'owner', TRUE, TRUE, TRUE, TRUE);
  RETURN new_farm_id;
END;
$$;

-- ── 7. Updated RLS for farm_invites ────────────────────────────
DROP POLICY IF EXISTS "Farm owners and admins can read invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can create invites" ON public.farm_invites;
DROP POLICY IF EXISTS "Farm owners and admins can delete invites" ON public.farm_invites;

CREATE POLICY "Privileged members can read invites"
  ON public.farm_invites FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can create invites"
  ON public.farm_invites FOR INSERT TO authenticated
  WITH CHECK (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can manage invites"
  ON public.farm_invites FOR UPDATE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));

CREATE POLICY "Privileged members can delete invites"
  ON public.farm_invites FOR DELETE TO authenticated
  USING (EXISTS (
    SELECT 1 FROM public.farm_members fm
    WHERE fm.farm_id = farm_invites.farm_id
      AND fm.user_id = auth.uid()
      AND (fm.is_admin OR fm.is_master_admin OR fm.is_creator)
  ));
