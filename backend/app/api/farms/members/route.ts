import { NextRequest, NextResponse } from 'next/server';
import { getAuthenticatedUser } from '../../../lib/auth-utils';
import { getSupabaseServerClient, getSupabaseAdmin } from '../../../lib/supabase';

export async function POST(request: NextRequest) {
  try {
    const authHeader = request.headers.get('authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const token = authHeader.replace('Bearer ', '');
    const supabase = await getSupabaseServerClient(request);
    const { data: { user }, error: userError } = await getAuthenticatedUser(request, supabase);

    if (userError || !user || user.is_anonymous) {
      return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
    }

    const body = await request.json();
    const { farmId, targetUserId, action } = body;
    // action: 'promote_admin', 'demote_viewer', 'kick'

    if (!farmId || !targetUserId || !action) {
      return NextResponse.json({ success: false, error: 'Missing parameters' }, { status: 400 });
    }

    if (targetUserId === user.id) {
      return NextResponse.json({ success: false, error: 'Cannot modify your own role' }, { status: 400 });
    }

    const adminSupabase = getSupabaseAdmin();

    // Check requester's role using admin client (bypasses RLS issues)
    const { data: requesterMember } = await adminSupabase
      .from('farm_members')
      .select('role')
      .eq('farm_id', farmId)
      .eq('user_id', user.id)
      .single();

    if (!requesterMember || (requesterMember.role !== 'owner' && requesterMember.role !== 'admin')) {
      return NextResponse.json({ success: false, error: 'Forbidden' }, { status: 403 });
    }

    // Check target's role using admin client
    const { data: targetMember } = await adminSupabase
      .from('farm_members')
      .select('role')
      .eq('farm_id', farmId)
      .eq('user_id', targetUserId)
      .single();

    if (!targetMember) {
      return NextResponse.json({ success: false, error: 'Target user not in farm' }, { status: 404 });
    }

    if (targetMember.role === 'owner') {
      return NextResponse.json({ success: false, error: 'Cannot modify owner' }, { status: 403 });
    }

    if (requesterMember.role === 'admin' && targetMember.role === 'admin') {
      return NextResponse.json({ success: false, error: 'Admins cannot modify other admins' }, { status: 403 });
    }

    // Execute action using admin client
    if (action === 'kick') {
      const { error: deleteError } = await adminSupabase
        .from('farm_members')
        .delete()
        .eq('farm_id', farmId)
        .eq('user_id', targetUserId);
      if (deleteError) throw deleteError;
      return NextResponse.json({ success: true, message: 'User removed from farm' });
    } 
    
    let newRole = '';
    if (action === 'promote_admin') newRole = 'admin';
    if (action === 'demote_viewer') newRole = 'viewer';

    if (newRole) {
      const { error: updateError } = await adminSupabase
        .from('farm_members')
        .update({ role: newRole })
        .eq('farm_id', farmId)
        .eq('user_id', targetUserId);
      if (updateError) throw updateError;
      return NextResponse.json({ success: true, message: `User role updated to ${newRole}` });
    }

    return NextResponse.json({ success: false, error: 'Invalid action' }, { status: 400 });

  } catch (error) {
    console.error('Manage member exception:', error);
    return NextResponse.json({ success: false, error: 'Internal server error' }, { status: 500 });
  }
}

