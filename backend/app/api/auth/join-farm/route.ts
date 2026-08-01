import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin, getSupabaseServerClient, supabasePublic } from '../../../lib/supabase';
import { getAuthenticatedUser } from '../../../lib/auth-utils';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const inviteCode = body.inviteCode;
    
    if (!inviteCode || typeof inviteCode !== 'string') {
      return NextResponse.json({ success: false, error: 'Invalid invite code' }, { status: 400 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user } } = await getAuthenticatedUser(request, supabase);
    
    let sessionToReturn = null;
    let userId = user?.id;

    if (!userId) {
      // 1. Create anonymous user since there is no authenticated user
      const { data: authData, error: authError } = await supabasePublic.auth.signInAnonymously();
      if (authError || !authData.user || !authData.session) {
        console.error('Anonymous auth failed:', authError);
        return NextResponse.json({ success: false, error: 'Failed to create anonymous session' }, { status: 500 });
      }
      userId = authData.user.id;
      sessionToReturn = authData.session;
    }
    const adminClient = getSupabaseAdmin();

    // 2. Validate invite
    const { data: invite, error: inviteError } = await adminClient
      .from('farm_invites')
      .select('id, farm_id, expires_at, max_uses, uses_count, is_active')
      .eq('code', inviteCode.toUpperCase())
      .single();

    if (inviteError || !invite || !invite.is_active) {
      return NextResponse.json({ success: false, error: 'Código de convite inválido ou inativo' }, { status: 400 });
    }

    if (new Date(invite.expires_at) < new Date()) {
      return NextResponse.json({ success: false, error: 'O código de convite expirou' }, { status: 400 });
    }

    if (invite.max_uses !== -1 && (invite.uses_count || 0) >= invite.max_uses) {
      return NextResponse.json({ success: false, error: 'O código de convite já atingiu o limite de utilizações' }, { status: 400 });
    }

    // 3. Add user to farm_members
    const { error: memberError } = await adminClient.from('farm_members').insert({
      farm_id: invite.farm_id,
      user_id: userId,
      role: 'viewer'
    });

    if (memberError && memberError.code !== '23505') { // 23505 is unique_violation
      console.error('Farm member insertion failed:', memberError);
      return NextResponse.json({ success: false, error: 'Failed to join farm' }, { status: 500 });
    }

    // 4. Update invite uses_count and check if it should be deactivated
    const newUsesCount = (invite.uses_count || 0) + 1;
    const newIsActive = invite.max_uses === -1 ? true : (newUsesCount < invite.max_uses);

    await adminClient
      .from('farm_invites')
      .update({ uses_count: newUsesCount, is_active: newIsActive })
      .eq('id', invite.id);

    return NextResponse.json({
      success: true,
      session: sessionToReturn,
      farmId: invite.farm_id
    });
  } catch (error) {
    console.error('Join farm exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
