import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

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

    if (userError || !user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const isAnonymous = user.is_anonymous === true;
    const adminSupabase = getSupabaseAdmin();

    // Obter todas as farms a que o utilizador pertence using admin client (bypasses RLS issues)
    const { data: memberships } = await adminSupabase
      .from('farm_members')
      .select('farm_id, role, farms(name)')
      .eq('user_id', user.id);

    if (!memberships || memberships.length === 0) {
      return NextResponse.json({ success: true, isAnonymous, farms: [] });
    }

    const farmsData = [];

    for (const membership of memberships) {
      const farmId = membership.farm_id;
      const userRole = membership.role;
      const farmName = (membership.farms as any)?.name;

      // Get members of this farm using admin client
      const { data: membersData } = await adminSupabase
        .from('farm_members')
        .select('user_id, role')
        .eq('farm_id', farmId);

      // Get valid invite code
      let inviteCode = null;
      if (userRole === 'owner' || userRole === 'admin') {
        const { data: inviteData } = await adminSupabase
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

          await adminSupabase.from('farm_invites').insert({
            farm_id: farmId,
            code: newCode,
            expires_at: expiresAt.toISOString(),
            created_by: user.id
          });
          inviteCode = newCode;
        }
      }

      farmsData.push({
        farmId,
        farmName,
        userRole,
        inviteCode,
        members: membersData || []
      });
    }

    return NextResponse.json({
      success: true,
      isAnonymous,
      farms: farmsData
    });

  } catch (error) {
    console.error('Fetch farm details exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}

