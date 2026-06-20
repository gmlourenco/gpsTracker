/**
 * GET /api/devices
 *
 * Returns all registered family devices with their latest known location.
 * Uses the get_latest_positions() PostgreSQL function for O(n_devices)
 * performance instead of loading the entire locations table.
 *
 * Response shape: DeviceWithLatestLocation[]
 */

import { NextRequest, NextResponse } from 'next/server';
import { getSupabaseServerClient } from '../../lib/supabase';
import { mapRpcRowsToDevices } from '../../lib/mappers';
import { DeviceWithLatestLocation } from '../../types/telemetry';

export async function GET(request: NextRequest): Promise<NextResponse> {
  const supabase = await getSupabaseServerClient(request);
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });

  // ── 1. Call optimized RPC (DISTINCT ON, single query) ─────────────────────
  const { data: rows, error: rpcError } = await supabase.rpc('get_latest_positions');

  if (rpcError) {
    console.error('[GET /api/devices] RPC error:', rpcError);

    // Fallback: fetch devices without locations if function doesn't exist yet
    const { data: devices, error: devicesError } = await supabase
      .from('devices')
      .select('*')
      .order('last_seen_at', { ascending: false, nullsFirst: false });

    if (devicesError) {
      console.error('[GET /api/devices] Fallback error:', devicesError);
      return NextResponse.json(
        { success: false, error: 'Database error' },
        { status: 500 }
      );
    }

    return NextResponse.json(
      (devices ?? []).map((d) => ({ ...d, latestLocation: null })),
      { status: 200 }
    );
  }

  if (!rows || rows.length === 0) {
    // No locations yet — return devices without locations
    const { data: devices } = await supabase
      .from('devices')
      .select('*')
      .order('last_seen_at', { ascending: false, nullsFirst: false });

    return NextResponse.json(
      (devices ?? []).map((d) => ({
        ...d,
        latestLocation: null,
      })) as DeviceWithLatestLocation[],
      { status: 200 }
    );
  }

  // ── 2. Transform RPC rows into DeviceWithLatestLocation[] ─────────────────
  const result: DeviceWithLatestLocation[] = mapRpcRowsToDevices(rows);

  // Also include devices that have no locations yet
  const deviceIdsWithLocations = new Set(result.map((d) => d.id));
  const { data: allDevices } = await supabase
    .from('devices')
    .select('*')
    .order('last_seen_at', { ascending: false, nullsFirst: false });

  for (const device of (allDevices ?? [])) {
    if (!deviceIdsWithLocations.has(device.id)) {
      result.push({ ...device, latestLocation: null });
    }
  }

  const response = NextResponse.json(result, { status: 200 });
  response.headers.set('Cache-Control', 'private, max-age=10');
  return response;
}
