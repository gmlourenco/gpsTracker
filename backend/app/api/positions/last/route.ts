/**
 * GET /api/positions/last
 *
 * Returns every device with its most recent known position.
 * Uses the get_latest_positions() PostgreSQL function for efficient lookup.
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { mapRpcRowsToDevicesWithHistory } from '../../../lib/mappers';
import { DeviceWithLatestLocation } from '../../../types/telemetry';
import { createClient } from '@supabase/supabase-js';

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = new URL(request.url);
  const historyParam = searchParams.get('history');
  const historyCount = historyParam ? parseInt(historyParam, 10) : 0;

  const authHeader = request.headers.get('authorization') || '';
  let supabase;

  const jwtMatch = authHeader.match(/^Bearer\s+(eyJ\S+)$/i);
  if (jwtMatch) {
    supabase = createClient(
      process.env.NEXT_PUBLIC_SUPABASE_URL!,
      process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
      {
        global: {
          headers: {
            Authorization: authHeader,
          },
        },
      }
    );
  } else {
    return NextResponse.json({ success: false, error: 'Unauthorized' }, { status: 401 });
  }

  // ── 1. Explicit Backend Access Check (Defense in Depth) ───────────────────
  let allowedDeviceIds: Set<string> | null = null;
  
  if (jwtMatch) {
    const adminSupabase = getSupabaseAdmin();
    // Get user from the JWT to determine their id securely
    const { data: userData, error: authError } = await supabase.auth.getUser();
    if (authError) {
      return NextResponse.json(
        { success: false, error: 'JWT expired or invalid. Please re-authenticate.' },
        { status: 401 }
      );
    }
    if (userData?.user) {
      const user = userData.user;
      allowedDeviceIds = new Set<string>();
      
      // Check if user has any families first
      const { data: userFarms } = await adminSupabase
        .from('farm_members')
        .select('farm_id')
        .eq('user_id', user.id);
        
      const hasFamilies = userFarms && userFarms.length > 0;

      // A. User's own devices (and unassigned ONLY if they have no families)
      let query = adminSupabase.from('devices').select('id');
      if (hasFamilies) {
        query = query.eq('user_id', user.id);
      } else {
        query = query.or(`user_id.eq.${user.id},and(user_id.is.null,farm_id.is.null)`);
      }
      
      const { data: directDevices } = await query;
      if (directDevices) directDevices.forEach(d => allowedDeviceIds!.add(d.id));

      // B. Devices from the user's families (farms) AND devices of any co-member of those families
      if (hasFamilies) {
        const farmIds = userFarms.map(f => f.farm_id);
        
        // 1. Devices synced to any of my families
        const { data: farmDevices } = await adminSupabase
          .from('devices')
          .select('id')
          .in('farm_id', farmIds);
          
        if (farmDevices) farmDevices.forEach(d => allowedDeviceIds!.add(d.id));

        // 2. Devices owned by any member of my families
        const { data: coMembers } = await adminSupabase
          .from('farm_members')
          .select('user_id')
          .in('farm_id', farmIds);
          
        const coMemberUserIds = Array.from(new Set((coMembers ?? []).map(m => m.user_id)));
        if (coMemberUserIds.length > 0) {
          const { data: memberDevices } = await adminSupabase
            .from('devices')
            .select('id')
            .in('user_id', coMemberUserIds);
          if (memberDevices) memberDevices.forEach(d => allowedDeviceIds!.add(d.id));
        }
      }
    }
  }

  // ── 2. Call optimized RPC (RLS applies here as well) ──────────────────────
  const { data: rows, error: rpcError } = await supabase.rpc('get_positions_with_history', {
    p_history: isNaN(historyCount) ? 0 : historyCount
  });

  if (rpcError) {
    console.error('[GET /api/positions/last] RPC error:', rpcError);
    return NextResponse.json(
      { success: false, error: 'Database error' },
      { status: 500 }
    );
  }

  if (!rows || rows.length === 0) {
    return NextResponse.json({
      success: true,
      fetchedAt: new Date().toISOString(),
      devices: [] as DeviceWithLatestLocation[],
    });
  }

  // ── 3. Transform RPC rows ──────────────────────────────────────────────────
  let devices: DeviceWithLatestLocation[] = mapRpcRowsToDevicesWithHistory(rows);

  // ── 4. Apply Explicit Backend Filter ───────────────────────────────────────
  if (allowedDeviceIds !== null) {
    devices = devices.filter(d => allowedDeviceIds!.has(d.id));
  }

  const response = NextResponse.json({
    success: true,
    fetchedAt: new Date().toISOString(),
    devices,
  });
  response.headers.set('Cache-Control', 'private, max-age=10');
  return response;
}

