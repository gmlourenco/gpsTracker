import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

export async function GET(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const isAnonymous = user.is_anonymous === true;
    const adminSupabase = getSupabaseAdmin();

    // ── 1. Get all farm memberships for this user ────────────────
    const { data: memberships } = await adminSupabase
      .from('farm_members')
      .select('farm_id, role, is_creator, is_master_admin, is_admin, is_authenticated, farms(name)')
      .eq('user_id', user.id);

    if (!memberships || memberships.length === 0) {
      return NextResponse.json({ success: true, isAnonymous, currentUserId: user.id, farms: [] });
    }

    const farmIds = memberships.map(m => m.farm_id);

    // ── 2. Batch: get ALL members for ALL farms ──────────────────
    const { data: allMembers } = await adminSupabase
      .from('farm_members')
      .select('farm_id, user_id, role, is_creator, is_master_admin, is_admin, is_authenticated, display_name')
      .in('farm_id', farmIds);

    // ── 3. Batch: get ALL active invites for ALL farms ───────────
    const { data: allInvites } = await adminSupabase
      .from('farm_invites')
      .select('farm_id, code, expires_at, max_uses, uses_count, is_active')
      .in('farm_id', farmIds)
      .eq('is_active', true)
      .gte('expires_at', new Date().toISOString())
      .order('expires_at', { ascending: false });

    // ── 4. Batch: get display info from auth.users ───────────────
    // Collect all unique user IDs across all farms
    const allUserIds = [...new Set((allMembers ?? []).map(m => m.user_id))];
    
    // Get user metadata (Google name, etc.) from auth.users via admin
    const { data: authUsers } = await adminSupabase.auth.admin.listUsers();
    const userMetaMap = new Map<string, { googleName?: string | null; email?: string | null }>();
    if (authUsers?.users) {
      for (const au of authUsers.users) {
        if (allUserIds.includes(au.id)) {
          const meta = au.user_metadata || {};
          userMetaMap.set(au.id, {
            googleName: meta.full_name || meta.name || null,
            email: au.email || null,
          });
        }
      }
    }

    // ── 5. Batch: get device labels for display name fallback ────
    const { data: allDevices } = await adminSupabase
      .from('devices')
      .select('user_id, label')
      .in('user_id', allUserIds);

    const deviceLabelMap = new Map<string, string>();
    (allDevices ?? []).forEach(d => {
      if (d.user_id && !deviceLabelMap.has(d.user_id)) {
        deviceLabelMap.set(d.user_id, d.label);
      }
    });

    // ── 6. Group data by farm ────────────────────────────────────
    const membersByFarm = new Map<string, typeof allMembers>();
    (allMembers ?? []).forEach(m => {
      const list = membersByFarm.get(m.farm_id) || [];
      list.push(m);
      membersByFarm.set(m.farm_id, list);
    });

    const invitesByFarm = new Map<string, { code: string; expiresAt: string; maxUses: number; usesCount: number }>();
    (allInvites ?? []).forEach(inv => {
      if (!invitesByFarm.has(inv.farm_id)) {
        invitesByFarm.set(inv.farm_id, {
          code: inv.code,
          expiresAt: inv.expires_at,
          maxUses: inv.max_uses,
          usesCount: inv.uses_count,
        });
      }
    });

    // ── 7. Build response ────────────────────────────────────────
    const farmsData = memberships.map(membership => {
      const farmId = membership.farm_id;
      const farmName = (membership.farms as { name?: string } | null)?.name;
      const canSeeInvite = membership.is_admin || membership.is_master_admin || membership.is_creator;
      const invite = canSeeInvite ? invitesByFarm.get(farmId) : null;

      const members = (membersByFarm.get(farmId) || []).map(m => {
        const meta = userMetaMap.get(m.user_id);
        const deviceLabel = deviceLabelMap.get(m.user_id);
        
        // Display name: "GoogleName - DeviceLabel" with fallbacks
        let displayName = m.display_name; // explicit override first
        if (!displayName) {
          const parts = [meta?.googleName, deviceLabel].filter(Boolean);
          displayName = parts.length > 0 ? parts.join(' - ') : null;
        }

        return {
          userId: m.user_id,
          displayName,
          isCreator: m.is_creator ?? false,
          isMasterAdmin: m.is_master_admin ?? false,
          isAdmin: m.is_admin ?? false,
          isAuthenticated: m.is_authenticated ?? true,
          role: m.role, // Keep legacy field for backward compat
        };
      });

      return {
        farmId,
        farmName,
        userRole: membership.role, // Legacy compat
        myTags: {
          isCreator: membership.is_creator ?? false,
          isMasterAdmin: membership.is_master_admin ?? false,
          isAdmin: membership.is_admin ?? false,
          isAuthenticated: membership.is_authenticated ?? true,
        },
        inviteCode: invite?.code ?? null,
        inviteExpiresAt: invite?.expiresAt ?? null,
        inviteUsesRemaining: invite ? (invite.maxUses - invite.usesCount) : null,
        members,
      };
    });

    return NextResponse.json({
      success: true,
      isAnonymous,
      currentUserId: user.id,
      farms: farmsData,
    });

  } catch (error) {
    console.error('Fetch farm details exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
