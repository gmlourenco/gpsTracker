import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

type Action = 'kick' | 'promote_admin' | 'demote_admin' | 'promote_master_admin' | 'demote_master_admin';

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
    const { farmId, targetUserId, action } = body as { farmId: string; targetUserId: string; action: Action };

    if (!farmId || !targetUserId || !action) {
      return NextResponse.json({ success: false, error: 'Missing parameters' }, { status: 400 });
    }

    if (targetUserId === user.id) {
      return NextResponse.json({ success: false, error: 'Não podes modificar o teu próprio papel.' }, { status: 400 });
    }

    const adminSupabase = getSupabaseAdmin();

    // Get both members in a single query
    const { data: bothMembers } = await adminSupabase
      .from('farm_members')
      .select('user_id, role, is_creator, is_master_admin, is_admin')
      .eq('farm_id', farmId)
      .in('user_id', [user.id, targetUserId]);

    const requester = bothMembers?.find(m => m.user_id === user.id);
    const target = bothMembers?.find(m => m.user_id === targetUserId);

    if (!requester) {
      return NextResponse.json({ success: false, error: 'Forbidden' }, { status: 403 });
    }
    if (!target) {
      return NextResponse.json({ success: false, error: 'Membro não encontrado nesta família.' }, { status: 404 });
    }

    // ── Permission matrix ────────────────────────────────────────
    const canKick = () => {
      // master_admin can kick anyone except creator
      if (requester.is_master_admin && !target.is_creator) return true;
      // admin can kick non-admin members
      if (requester.is_admin && !target.is_admin && !target.is_master_admin && !target.is_creator) return true;
      return false;
    };

    const canPromoteAdmin = () => {
      // admin+ can promote members to admin
      return requester.is_admin || requester.is_master_admin;
    };

    const canDemoteAdmin = () => {
      // master_admin can demote admins
      if (requester.is_master_admin) return true;
      // admin cannot demote another admin
      return false;
    };

    const canPromoteMaster = () => {
      // only master_admin can promote to master_admin
      return requester.is_master_admin;
    };

    const canDemoteMaster = () => {
      // only master_admin can demote master_admin
      return requester.is_master_admin;
    };

    // ── Execute action ───────────────────────────────────────────
    switch (action) {
      case 'kick': {
        if (!canKick()) {
          return NextResponse.json({ success: false, error: 'Sem permissão para expulsar este membro.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .delete()
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro removido.' });
      }

      case 'promote_admin': {
        if (!canPromoteAdmin()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        if (target.is_admin) {
          return NextResponse.json({ success: false, error: 'O membro já é admin.' }, { status: 400 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_admin: true, role: 'admin' }) // Update legacy field too
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro promovido a Admin.' });
      }

      case 'demote_admin': {
        if (!canDemoteAdmin()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_admin: false, role: 'viewer' })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Admin rebaixado a Membro.' });
      }

      case 'promote_master_admin': {
        if (!canPromoteMaster()) {
          return NextResponse.json({ success: false, error: 'Apenas Master Admins podem promover a Master Admin.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_master_admin: true, is_admin: true, role: 'admin' })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Membro promovido a Master Admin.' });
      }

      case 'demote_master_admin': {
        if (!canDemoteMaster()) {
          return NextResponse.json({ success: false, error: 'Sem permissão.' }, { status: 403 });
        }
        const { error } = await adminSupabase
          .from('farm_members')
          .update({ is_master_admin: false })
          .eq('farm_id', farmId)
          .eq('user_id', targetUserId);
        if (error) throw error;
        return NextResponse.json({ success: true, message: 'Master Admin rebaixado a Admin.' });
      }

      default:
        return NextResponse.json({ success: false, error: 'Ação inválida.' }, { status: 400 });
    }

  } catch (error) {
    console.error('Manage member exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}
