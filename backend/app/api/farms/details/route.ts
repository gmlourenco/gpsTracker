import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient } from '../../../lib/supabase';

export async function GET(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const token = authHeader.replace('Bearer ', '');
    console.log("DEBUG /api/farms/details -> Before getSupabaseServerClient");
    const supabase = await getSupabaseServerClient(request);
    console.log("DEBUG /api/farms/details -> Before getUser");
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    console.log("DEBUG /api/farms/details -> userError:", userError, "user:", user);

    if (userError || !user || user.is_anonymous) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    // Get the first farm the user is in
    const { data: membership } = await supabase
      .from('farm_members')
      .select('farm_id, role, farms(name)')
      .eq('user_id', user.id)
      .limit(1)
      .single();

    if (!membership) {
      return NextResponse.json({ success: false, error: 'User has no farm' }, { status: 404 });
    }

    const farmId = membership.farm_id;
    const userRole = membership.role;
    const farmName = (membership.farms as any)?.name;

    // Get members
    const { data: membersData } = await supabase
      .from('farm_members')
      .select('user_id, role')
      .eq('farm_id', farmId);

    // Get valid invite code
    let inviteCode = null;
    if (userRole === 'owner' || userRole === 'admin') {
      const { data: inviteData } = await supabase
        .from('farm_invites')
        .select('code, expires_at')
        .eq('farm_id', farmId)
        .gte('expires_at', new Date().toISOString())
        .order('expires_at', { ascending: false })
        .limit(1)
        .single();
      
      inviteCode = inviteData?.code;

      // If no valid invite code, generate one
      if (!inviteCode) {
        const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        let newCode = '';
        for (let i = 0; i < 6; i++) {
          newCode += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        const expiresAt = new Date();
        expiresAt.setDate(expiresAt.getDate() + 7);

        await supabase.from('farm_invites').insert({
          farm_id: farmId,
          code: newCode,
          expires_at: expiresAt.toISOString(),
          created_by: user.id
        });
        inviteCode = newCode;
      }
    }

    return NextResponse.json({
      success: true,
      farmId,
      farmName,
      userRole,
      inviteCode,
      members: membersData || []
    });

  } catch (error) {
    console.error('Fetch farm details exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
