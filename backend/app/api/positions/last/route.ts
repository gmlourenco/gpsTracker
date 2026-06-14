/**
 * GET /api/positions/last
 *
 * Returns every device with its most recent known position.
 * Uses the get_latest_positions() PostgreSQL function for efficient lookup.
 */

import { NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../../lib/supabase';
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
  const devices: DeviceWithLatestLocation[] = rows.map((r: Record<string, unknown>) => ({
    id: r.device_id as string,
    label: r.device_label as string,
    marker_color: r.marker_color as string,
    created_at: r.device_created_at as string,
    last_seen_at: r.last_seen_at as string | null,
    tracking_enabled: r.tracking_enabled as boolean,
    app_version: r.app_version as string,
    latestLocation: r.loc_id ? {
      id: r.loc_id as number,
      device_id: r.device_id as string,
      lat: r.lat as number,
      lng: r.lng as number,
      accuracy: r.accuracy as number,
      speed: r.speed as number,
      heading: r.heading as number,
      battery_level: r.battery_level as number,
      battery_charging: r.battery_charging as boolean,
      emergency_state: r.emergency_state as boolean,
      network_type: r.network_type as string,
      tracking_enabled: r.loc_tracking_enabled as boolean,
      app_version: r.loc_app_version as string,
      created_at: r.created_at as string,
      synced_at: r.synced_at as string,
    } : null,
  }));

  return NextResponse.json({
    success: true,
    fetchedAt: new Date().toISOString(),
    devices,
  });
}
