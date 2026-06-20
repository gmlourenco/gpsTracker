import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, supabasePublic } from '../../../lib/supabase';

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
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized: Missing or invalid token' }, { status: 401 });
    }

    const token = authHeader.replace('Bearer ', '');
    const supabase = await getSupabaseServerClient(request);
    
    // Verify user token
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);
    
    if (userError || !user) {
      console.error('Auth verification failed:', userError);
      return NextResponse.json({ success: false, error: 'Unauthorized: Invalid token' }, { status: 401 });
    }

    if (user.is_anonymous) {
      return NextResponse.json({ success: false, error: 'Anonymous users cannot create farms. Please log in.' }, { status: 403 });
    }

    const userId = user.id;

    // Read custom name from body if provided
    let farmName = '';
    try {
      const body = await request.json();
      if (body && typeof body.name === 'string' && body.name.trim() !== '') {
        farmName = body.name.trim();
      }
    } catch (e) {
      // Fallback if no body was provided
    }

    if (!farmName) {
      return NextResponse.json({ success: false, error: 'O nome da família é obrigatório.' }, { status: 400 });
    }

    // 2. Create the farm and add user as owner using RPC (bypasses RLS chicken-and-egg problem)
    const { data: newFarmId, error: rpcError } = await supabase
      .rpc('create_farm_with_owner', { farm_name: farmName });

    if (rpcError || !newFarmId) {
      console.error('Farm creation RPC failed:', rpcError);
      return NextResponse.json({ success: false, error: 'Failed to create farm' }, { status: 500 });
    }

    const farm = { id: newFarmId };

    // 4. Generate invite code
    const code = generateCode();
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 7); // 7 days expiry

    const { data: invite, error: inviteError } = await supabase
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
      farmId: farm.id,
      inviteCode: invite.code
    });
  } catch (error) {
    console.error('Create farm exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
