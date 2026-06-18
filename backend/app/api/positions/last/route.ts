/**
 * GET /api/positions/last
 *
 * Returns every device with its most recent known position.
 * Uses the get_latest_positions() PostgreSQL function for efficient lookup.
 */

import { NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
import { mapRpcRowsToDevices } from '../../../lib/mappers';
import { DeviceWithLatestLocation } from '../../../types/telemetry';

export async function GET(): Promise<NextResponse> {
  const supabase = getSupabaseAdmin();

  // ── 1. Call optimized RPC ──────────────────────────────────────────────────
  const { data: rows, error: rpcError } = await supabase.rpc('get_latest_positions');

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

  // ── 2. Transform RPC rows ──────────────────────────────────────────────────
  const devices: DeviceWithLatestLocation[] = mapRpcRowsToDevices(rows);

  const response = NextResponse.json({
    success: true,
    fetchedAt: new Date().toISOString(),
    devices,
  });
  response.headers.set('Cache-Control', 'private, max-age=10');
  return response;
}

