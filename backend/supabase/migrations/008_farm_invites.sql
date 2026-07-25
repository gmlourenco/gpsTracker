-- Farm Invites Table
CREATE TABLE public.farm_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES public.farms(id) ON DELETE CASCADE,
    code TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL REFERENCES auth.users(id)
);

-- RLS for farm_invites
ALTER TABLE public.farm_invites ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can create invites for their farms"
ON public.farm_invites
FOR INSERT
WITH CHECK (
    EXISTS (
        SELECT 1 FROM public.farm_members
        WHERE farm_id = farm_invites.farm_id
        AND user_id = auth.uid()
        AND role = 'admin'
    )
);

CREATE POLICY "Users can view invites for their farms"
ON public.farm_invites
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.farm_members
        WHERE farm_id = farm_invites.farm_id
        AND user_id = auth.uid()
        AND role IN ('admin', 'member')
    )
);

-- We need a function to safely check and redeem an invite code
-- Since redeem happens BEFORE the user is a member, we use SECURITY DEFINER
CREATE OR REPLACE FUNCTION public.redeem_invite_code(p_code TEXT)
RETURNS UUID AS $$
DECLARE
    v_farm_id UUID;
    v_invite_id UUID;
BEGIN
    SELECT farm_id, id INTO v_farm_id, v_invite_id
    FROM public.farm_invites
    WHERE code = p_code
    AND expires_at > now();

    IF v_farm_id IS NULL THEN
        RAISE EXCEPTION 'Invalid or expired invite code';
    END IF;

    -- Add the current user to the farm
    INSERT INTO public.farm_members (farm_id, user_id, role)
    VALUES (v_farm_id, auth.uid(), 'viewer')
    ON CONFLICT (farm_id, user_id) DO NOTHING;

    RETURN v_farm_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
