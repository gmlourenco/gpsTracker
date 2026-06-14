/**
 * GET /api/devices
 *
 * Returns all registered family devices with their latest known location.
 * Uses the get_latest_positions() PostgreSQL function for O(n_devices)
 * performance instead of loading the entire locations table.
 *
 * Response shape: DeviceWithLatestLocation[]
 */

import { NextResponse } from 'next/server';
import { getSupabaseAdmin } from '../../lib/supabase';
import { DeviceWithLatestLocation } from '../../types/telemetry';

export async function GET(): Promise<NextResponse> {
  const supabase = getSupabaseAdmin();

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
  const result: DeviceWithLatestLocation[] = rows.map((r: Record<string, unknown>) => ({
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

  return NextResponse.json(result, { status: 200 });
}
