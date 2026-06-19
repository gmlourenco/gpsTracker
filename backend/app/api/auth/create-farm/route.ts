import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin, supabasePublic } from '../../../lib/supabase';

function generateCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // No I, O, 1, 0
  let result = '';
  for (let i = 0; i < 6; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

export async function POST(request: NextRequest) {
  try {
    // 1. Create anonymous user using supabasePublic
    const { data: authData, error: authError } = await supabasePublic.auth.signInAnonymously();
    if (authError || !authData.user || !authData.session) {
      console.error('Anonymous auth failed:', authError);
      return NextResponse.json({ success: false, error: 'Failed to create anonymous session' }, { status: 500 });
    }

    const userId = authData.user.id;
    const adminClient = getSupabaseAdmin();

    // 2. Create the farm
    const { data: farm, error: farmError } = await adminClient
      .from('farms')
      .insert({ name: 'Família' })
      .select('id')
      .single();

    if (farmError || !farm) {
      console.error('Farm creation failed:', farmError);
      return NextResponse.json({ success: false, error: 'Failed to create farm' }, { status: 500 });
    }

    // 3. Add user as admin
    const { error: memberError } = await adminClient.from('farm_members').insert({
      farm_id: farm.id,
      user_id: userId,
      role: 'admin'
    });

    if (memberError) {
      console.error('Farm member insertion failed:', memberError);
      return NextResponse.json({ success: false, error: 'Failed to add farm member' }, { status: 500 });
    }

    // 4. Generate invite code
    const code = generateCode();
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 7); // 7 days expiry

    const { data: invite, error: inviteError } = await adminClient
      .from('farm_invites')
      .insert({
        farm_id: farm.id,
        code,
        expires_at: expiresAt.toISOString(),
        created_by: userId
      })
      .select('code')
      .single();

    if (inviteError || !invite) {
      console.error('Invite creation failed:', inviteError);
      return NextResponse.json({ success: false, error: 'Failed to create invite code' }, { status: 500 });
    }

    return NextResponse.json({
      success: true,
      session: authData.session,
      farmId: farm.id,
      inviteCode: invite.code
    });
  } catch (error) {
    console.error('Create farm exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
