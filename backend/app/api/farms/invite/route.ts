import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';
import { generateInviteCode } from '../../../lib/invite-utils';

export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user || user.is_anonymous) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();
    const { farmId } = body as { farmId: string };

    if (!farmId) {
      return NextResponse.json({ success: false, error: 'farmId is required' }, { status: 400 });
    }

    const adminSupabase = getSupabaseAdmin();

    // Check if user is an admin/master_admin/creator in this farm
    const { data: membership } = await adminSupabase
      .from('farm_members')
      .select('role, is_creator, is_master_admin, is_admin')
      .eq('farm_id', farmId)
      .eq('user_id', user.id)
      .single();

    if (!membership || (!membership.is_admin && !membership.is_master_admin && !membership.is_creator && membership.role !== 'admin' && membership.role !== 'owner')) {
      return NextResponse.json({ success: false, error: 'Sem permissão para gerar código nesta família.' }, { status: 403 });
    }

    // Deactivate existing active invites for this farm
    await adminSupabase
      .from('farm_invites')
      .update({ is_active: false })
      .eq('farm_id', farmId)
      .eq('is_active', true);

    // Generate new invite code
    const code = generateInviteCode(8);
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + 7); // 7 days expiry

    const { data: invite, error: inviteError } = await adminSupabase
      .from('farm_invites')
      .insert({
        farm_id: farmId,
        code,
        expires_at: expiresAt.toISOString(),
        max_uses: 1,
        uses_count: 0,
        is_active: true,
        created_by: user.id
      })
      .select('code, expires_at')
      .single();

    if (inviteError || !invite) {
      console.error('Invite creation failed:', inviteError);
      return NextResponse.json({ success: false, error: 'Erro ao gerar código de convite.' }, { status: 500 });
    }

    return NextResponse.json({
      success: true,
      inviteCode: invite.code,
      expiresAt: invite.expires_at
    });
  } catch (error) {
    console.error('Generate invite exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
